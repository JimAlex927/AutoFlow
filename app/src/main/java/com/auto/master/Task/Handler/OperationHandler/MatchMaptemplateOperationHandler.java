package com.auto.master.Task.Handler.OperationHandler;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;

import com.auto.master.Task.Operation.MatchMapTemplateOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Template.Template;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.capture.CaptureScaleHelper;
import com.auto.master.capture.ScreenCaptureManager;
import com.auto.master.utils.AdaptivePollingController;
import com.auto.master.utils.AppStorage;
import com.auto.master.utils.MatchResult;
import com.auto.master.utils.OpenCVHelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MatchMaptemplateOperationHandler extends OperationHandler {

    private static final String TAG = "MatchMapOp";
    private static final double DEFAULT_SIMILARITY = 0.88d;
    private static final int CAPTURE_ROI_PADDING_PX = 12;
    private static final long RECT_FEEDBACK_DELAY_MS = 24L;
    private static final int MAX_PLAN_CACHE_ENTRIES = 32;
    private static final int POOL_SIZE = 2;
    private static final int MIN_USEFUL_MASK_PIXELS = 16;
    private static final double MIN_USEFUL_MASK_COVERAGE = 0.03d;
    private static final double FULL_MASK_CROP_RATIO = 0.92d;
    private static final double DENSE_CROPPED_MASK_RATIO = 0.86d;

    private static final android.os.Handler MAIN_HANDLER =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static final ExecutorService SHARED_POOL = Executors.newFixedThreadPool(POOL_SIZE);
    private static final Gson GSON = new Gson();
    private static final Map<String, CompiledMatchPlan> MATCH_PLAN_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<String, CompiledMatchPlan>(
                    MAX_PLAN_CACHE_ENTRIES + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CompiledMatchPlan> eldest) {
                    return size() > MAX_PLAN_CACHE_ENTRIES;
                }
            });

    /**
     * 清空 MatchMap 编译计划缓存。
     * CAPTURE_SCALE 切换时由 SetCaptureScaleOperationHandler 调用：
     * 编译计划本身（bbox 区域 + 模板名）是 scale 无关的，模板 Mat 由 Template 缓存管理，
     * 但为了安全起见，在切换 scale 时一并清空以避免缓存污染。
     */
    public static void clearMatchPlanCache() {
        MATCH_PLAN_CACHE.clear();
    }

    MatchMaptemplateOperationHandler() {
        this.setType(7);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        ctx.currentOperation = obj;
        MatchMapTemplateOperation op = (MatchMapTemplateOperation) obj;

        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            return false;
        }

        Map<String, Object> inputMap = op.getInputMap();
        if (inputMap == null || inputMap.isEmpty()) {
            return createTimeoutResponse(ctx, obj);
        }

        String projectName = getStringSafe(inputMap, MetaOperation.PROJECT, "fallback");
        String taskName = getStringSafe(inputMap, MetaOperation.TASK, "");
        double duration = parseDouble(inputMap.get(MetaOperation.MATCHTIMEOUT), MetaOperation.DEFAULT_MATCH_TIMEOUT_MS);
        boolean useGray = parseBoolean(inputMap.get(MetaOperation.MATCHUSEGRAY), false);
        boolean useMask = parseBoolean(inputMap.get(MetaOperation.MATCHUSEMASK), true);
        long preDelayMs = inputMap.containsKey(MetaOperation.NODE_PRE_DELAY_MS)
                ? 0L
                : parseDelayMs(inputMap.get(MetaOperation.MATCH_PRE_DELAY_MS));

        CompiledMatchPlan plan = getOrCreateCompiledPlan(inputMap.get(MetaOperation.MATCHMAP));
        if (plan == null || plan.groups.isEmpty()) {
            return createTimeoutResponse(ctx, obj);
        }

        List<MatchTask> taskList = buildTaskList(plan, projectName, taskName, useGray, useMask);
        if (taskList.isEmpty()) {
            return createTimeoutResponse(ctx, obj);
        }

        if (preDelayMs > 0) {
            SystemClock.sleep(preDelayMs);
        }

        AtomicReference<MatchTaskResult> winnerRef = new AtomicReference<>(null);
        AdaptivePollingController pollingController = AdaptivePollingController.forMatchMap(inputMap);
        long startedAt = System.currentTimeMillis();

        while ((duration - (System.currentTimeMillis() - startedAt)) > 0) {
            long loopStart = SystemClock.uptimeMillis();
            Mat screenMat = pollingController.acquireFrame(plan.captureRoi);
            try {
                if (screenMat == null || screenMat.empty()) {
                    pollingController.onMiss();
                    pollingController.sleepUntilNextIteration(loopStart);
                    continue;
                }
                if (!pollingController.hasFreshFrame()) {
                    pollingController.sleepUntilNextIteration(loopStart);
                    continue;
                }

                MatchTask firstTask = taskList.get(0);
                MatchTaskResult firstResult = performMatchOnSubmat(screenMat, firstTask, plan.captureRoi);
                if (firstResult.matched) {
                    winnerRef.set(firstResult);
                } else if (taskList.size() > 1) {
                    long remainingMs = (long) duration - (System.currentTimeMillis() - startedAt);
                    if (remainingMs > 0) {
                        runRemainingMatches(screenMat, taskList, plan.captureRoi, remainingMs, winnerRef);
                    }
                }

                MatchTaskResult winner = winnerRef.get();
                if (winner != null) {
                    pollingController.onHit();
                    return handleMatchSuccess(obj, ctx, svc, winner);
                }

                pollingController.onMiss();
                pollingController.sleepUntilNextIteration(loopStart);
            } finally {
                pollingController.releaseFrameIfOwned(screenMat);
            }
        }

        return createTimeoutResponse(ctx, obj);
    }

    private void runRemainingMatches(Mat screen,
                                     List<MatchTask> tasks,
                                     android.graphics.Rect captureRoi,
                                     long remainingMs,
                                     AtomicReference<MatchTaskResult> winnerRef) {
        int startIndex = 1;
        int remainingTaskCount = tasks.size() - startIndex;
        if (remainingTaskCount <= 0) {
            return;
        }

        if (remainingTaskCount <= 2 || remainingMs < 80L) {
            MatchTaskResult best = winnerRef.get();
            for (int i = startIndex; i < tasks.size(); i++) {
                MatchTaskResult result = performMatchOnSubmat(screen, tasks.get(i), captureRoi);
                if (result.matched && (best == null || result.priority < best.priority)) {
                    best = result;
                    break;
                }
            }
            if (best != null) {
                winnerRef.compareAndSet(null, best);
            }
            return;
        }

        List<Future<MatchTaskResult>> futures = new ArrayList<>(remainingTaskCount);
        for (int i = startIndex; i < tasks.size(); i++) {
            MatchTask task = tasks.get(i);
            futures.add(SHARED_POOL.submit(() -> performMatchOnSubmat(screen, task, captureRoi)));
        }

        MatchTaskResult best = winnerRef.get();
        long deadline = System.currentTimeMillis() + remainingMs;
        for (int i = 0; i < futures.size(); i++) {
            Future<MatchTaskResult> future = futures.get(i);
            long waitMs = deadline - System.currentTimeMillis();
            if (waitMs <= 0) {
                cancelPendingFutures(futures, i);
                break;
            }
            try {
                MatchTaskResult result = future.get(waitMs, TimeUnit.MILLISECONDS);
                if (result.matched && (best == null || result.priority < best.priority)) {
                    best = result;
                    cancelPendingFutures(futures, i + 1);
                    break;
                }
            } catch (java.util.concurrent.TimeoutException ignored) {
                cancelPendingFutures(futures, i);
                break;
            } catch (Exception e) {
                Log.w(TAG, "parallel match error: " + e.getMessage());
            }
        }

        if (best != null) {
            winnerRef.compareAndSet(null, best);
        }
    }

    private void cancelPendingFutures(List<Future<MatchTaskResult>> futures, int startIndex) {
        for (int i = Math.max(0, startIndex); i < futures.size(); i++) {
            Future<MatchTaskResult> future = futures.get(i);
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private static MatchTaskResult performMatchOnSubmat(Mat screen,
                                                        MatchTask task,
                                                        android.graphics.Rect captureRoi) {
        Mat roi = null;
        try {
            android.graphics.Rect captureRegion = toCaptureRect(task.region);
            if (captureRegion == null) {
                return new MatchTaskResult(false, null, task);
            }
            roi = safeSubmat(screen, toLocalRectCapture(task.region, captureRoi));
            if (roi == null || roi.empty()) {
                return new MatchTaskResult(false, null, task);
            }
            Point positionInRoi = OpenCVHelper.getInstance()
                    .fastSingleMatchWithOptions(roi, task.info.mat, null, task.info.similarity, task.info.mask, task.info.useGray);
            if (positionInRoi != null && positionInRoi.x >= 0) {
                double fullCaptureX = positionInRoi.x - task.info.matchOffsetX;
                double fullCaptureY = positionInRoi.y - task.info.matchOffsetY;
                if (fullCaptureX < 0
                        || fullCaptureY < 0
                        || fullCaptureX + task.info.width > roi.cols()
                        || fullCaptureY + task.info.height > roi.rows()) {
                    return new MatchTaskResult(false, null, task);
                }
                ScreenCaptureManager mgr = ScreenCaptureManager.getInstance();
                float invScaleX = mgr.getActualInvScaleX();
                float invScaleY = mgr.getActualInvScaleY();
                Point positionGlobal = new Point(
                        Math.round(fullCaptureX * invScaleX) + task.region.x,
                        Math.round(fullCaptureY * invScaleY) + task.region.y);
                return new MatchTaskResult(true, positionGlobal, task);
            }
        } catch (Exception e) {
            Log.w(TAG, "submat match error: " + e.getMessage());
        } finally {
            if (roi != null) {
                roi.release();
            }
        }
        return new MatchTaskResult(false, null, task);
    }

    private List<MatchTask> buildTaskList(CompiledMatchPlan plan,
                                          String projectName,
                                          String taskName,
                                          boolean useGray,
                                          boolean useMask) {
        List<MatchTask> tasks = new ArrayList<>();
        Map<String, Mat> loadedTemplates = new HashMap<>();
        Map<String, Mat> loadedMasks = new HashMap<>();
        int priority = 0;

        for (CompiledMatchGroup group : plan.groups) {
            for (TemplateRule rule : group.templateRules) {
                Mat templateMat = loadedTemplates.get(rule.templateName);
                if (templateMat == null || templateMat.empty()) {
                    templateMat = getOrLoadTemplateMat(projectName, taskName, rule.templateName);
                    if (templateMat == null || templateMat.empty()) {
                        Log.w(TAG, "模板加载失败: " + rule.templateName);
                        continue;
                    }
                    loadedTemplates.put(rule.templateName, templateMat);
                }
                Mat templateMask = null;
                if (useMask) {
                    if (!loadedMasks.containsKey(rule.templateName)) {
                        templateMask = getOrLoadTemplateMaskMat(projectName, taskName, rule.templateName, templateMat);
                        loadedMasks.put(rule.templateName, templateMask);
                    } else {
                        templateMask = loadedMasks.get(rule.templateName);
                    }
                }
                TemplateInfo templateInfo = buildTemplateInfo(
                        projectName,
                        taskName,
                        rule.templateName,
                        templateMat,
                        templateMask,
                        rule.similarity,
                        useGray);
                tasks.add(new MatchTask(
                        priority++,
                        rule.templateName,
                        group.region,
                        templateInfo));
            }
        }
        return tasks;
    }

    private TemplateInfo buildTemplateInfo(String projectName,
                                           String taskName,
                                           String templateName,
                                           Mat templateMat,
                                           Mat templateMask,
                                           double similarity,
                                           boolean useGray) {
        if (templateMask == null || templateMask.empty()) {
            return new TemplateInfo(templateMat, null, similarity, useGray,
                    templateMat.width(), templateMat.height(), 0, 0);
        }

        MaskStats stats = analyzeMask(templateMask);
        if (!stats.useful) {
            return new TemplateInfo(templateMat, null, similarity, useGray,
                    templateMat.width(), templateMat.height(), 0, 0);
        }

        int fullArea = Math.max(1, templateMat.width() * templateMat.height());
        int cropArea = Math.max(1, stats.bounds.width * stats.bounds.height);
        double cropRatio = cropArea / (double) fullArea;
        if (cropRatio >= FULL_MASK_CROP_RATIO && stats.densityInBounds >= DENSE_CROPPED_MASK_RATIO) {
            return new TemplateInfo(templateMat, null, similarity, useGray,
                    templateMat.width(), templateMat.height(), 0, 0);
        }

        Mat optimizedTemplate = getOrCreateSubmatCache(
                projectName,
                taskName,
                optimizedTemplateCacheKey(templateName, templateMat, templateMask, stats.bounds),
                templateMat,
                stats.bounds);
        if (optimizedTemplate == null || optimizedTemplate.empty()) {
            return new TemplateInfo(templateMat, null, similarity, useGray,
                    templateMat.width(), templateMat.height(), 0, 0);
        }

        Mat optimizedMask = null;
        boolean keepMask = stats.densityInBounds < DENSE_CROPPED_MASK_RATIO;
        if (keepMask) {
            optimizedMask = getOrCreateSubmatCache(
                    projectName,
                    taskName,
                    optimizedMaskCacheKey(templateName, templateMat, templateMask, stats.bounds),
                    templateMask,
                    stats.bounds);
            if (optimizedMask == null || optimizedMask.empty()) {
                keepMask = false;
            }
        }

        // 有 mask 时强制灰度路径，避免退到 4 通道 masked matchTemplate 慢路径。
        return new TemplateInfo(
                optimizedTemplate,
                keepMask ? optimizedMask : null,
                similarity,
                useGray || keepMask,
                templateMat.width(),
                templateMat.height(),
                stats.bounds.x,
                stats.bounds.y);
    }

    private static Mat getOrCreateSubmatCache(String projectName,
                                              String taskName,
                                              String key,
                                              Mat source,
                                              Rect bounds) {
        Mat cached = Template.getTaskSingleMutCache(projectName, taskName, key);
        if (cached != null && !cached.empty()) {
            return cached;
        }
        try {
            Mat cropped = source.submat(bounds).clone();
            if (cropped.empty()) {
                cropped.release();
                return null;
            }
            Template.putTaskSingleMatCache(projectName, taskName, key, cropped);
            return cropped;
        } catch (Throwable t) {
            Log.w(TAG, "创建优化 mask 模板失败: " + t.getMessage());
            return null;
        }
    }

    private static String optimizedTemplateCacheKey(String templateName,
                                                    Mat templateMat,
                                                    Mat maskMat,
                                                    Rect bounds) {
        return templateName + "#maskopt_tpl#"
                + templateMat.nativeObj + "#"
                + maskMat.nativeObj + "#"
                + bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height;
    }

    private static String optimizedMaskCacheKey(String templateName,
                                                Mat templateMat,
                                                Mat maskMat,
                                                Rect bounds) {
        return templateName + "#maskopt_mask#"
                + templateMat.nativeObj + "#"
                + maskMat.nativeObj + "#"
                + bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height;
    }

    private static MaskStats analyzeMask(Mat mask) {
        if (mask == null || mask.empty() || mask.channels() != 1) {
            return MaskStats.notUseful();
        }
        int rows = mask.rows();
        int cols = mask.cols();
        int total = rows * cols;
        if (total <= 0) {
            return MaskStats.notUseful();
        }
        byte[] bytes = new byte[total];
        int read = mask.get(0, 0, bytes);
        if (read <= 0) {
            return MaskStats.notUseful();
        }

        int minX = cols;
        int minY = rows;
        int maxX = -1;
        int maxY = -1;
        int white = 0;
        for (int y = 0; y < rows; y++) {
            int rowBase = y * cols;
            for (int x = 0; x < cols; x++) {
                if ((bytes[rowBase + x] & 0xFF) == 0) {
                    continue;
                }
                white++;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }
        if (white < MIN_USEFUL_MASK_PIXELS || white / (double) total < MIN_USEFUL_MASK_COVERAGE) {
            return MaskStats.notUseful();
        }
        Rect bounds = new Rect(minX, minY, maxX - minX + 1, maxY - minY + 1);
        double densityInBounds = white / (double) Math.max(1, bounds.width * bounds.height);
        return new MaskStats(true, bounds, densityInBounds);
    }

    private Mat getOrLoadTemplateMat(String projectName, String taskName, String templateName) {
        Mat cached = Template.getTaskSingleMutCache(projectName, taskName, templateName);
        if (cached != null && !cached.empty()) {
            return cached;
        }
        return tryLoadTemplate(projectName, taskName, templateName);
    }

    private static Mat tryLoadTemplate(String projectName, String taskName, String templateName) {
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            return null;
        }
        try {
            File imgDir = new File(
                    AppStorage.getProjectsRoot(svc.getApplicationContext()),
                    projectName
                            + File.separator + taskName
                            + File.separator + "img");

            // scale-aware 路径：先查 scale_{key}/ 子目录，scale=1.0 时回退到平级目录
            File imgFile = CaptureScaleHelper.resolveTemplateFile(
                    imgDir, templateName, ScreenCaptureManager.CAPTURE_SCALE);
            if (imgFile == null) {
                Log.w(TAG, "模板在当前倍率(" + ScreenCaptureManager.CAPTURE_SCALE
                        + ")下不存在，请在相同倍率下重新制作: " + templateName);
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            if (bitmap == null) {
                Log.w(TAG, "模板解码失败: " + imgFile.getPath());
                return null;
            }
            Mat mat = new Mat();
            boolean converted = false;
            try {
                Utils.bitmapToMat(bitmap, mat);
                converted = true;
            } finally {
                bitmap.recycle();
                if (!converted) {
                    mat.release();
                }
            }
            if (mat.empty()) {
                mat.release();
                return null;
            }
            // 注意：不再做 resize。模板已在制作时以当前 CAPTURE_SCALE 保存，
            // 直接与同倍率截图进行匹配，保持最高匹配精度。
            Template.putTaskSingleMatCache(projectName, taskName, templateName, mat);
            return mat;
        } catch (Exception e) {
            Log.e(TAG, "tryLoadTemplate error: " + e.getMessage());
            return null;
        }
    }

    private static Mat getOrLoadTemplateMaskMat(String projectName,
                                                String taskName,
                                                String templateName,
                                                Mat templateMat) {
        String maskName = CaptureScaleHelper.getTemplateMaskFileName(templateName);
        if (maskName.isEmpty()) {
            return null;
        }
        Mat cached = Template.getTaskSingleMutCache(projectName, taskName, maskName);
        if (cached != null && !cached.empty()) {
            return cached;
        }
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            return null;
        }
        try {
            File imgDir = new File(
                    AppStorage.getProjectsRoot(svc.getApplicationContext()),
                    projectName
                            + File.separator + taskName
                            + File.separator + "img");
            File maskFile = CaptureScaleHelper.resolveTemplateMaskFile(
                    imgDir, templateName, ScreenCaptureManager.CAPTURE_SCALE);
            if (maskFile == null) {
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeFile(maskFile.getAbsolutePath());
            if (bitmap == null) {
                return null;
            }
            Mat rgba = new Mat();
            Mat gray = new Mat();
            boolean converted = false;
            try {
                Utils.bitmapToMat(bitmap, rgba);
                int code = rgba.channels() == 3 ? Imgproc.COLOR_BGR2GRAY : Imgproc.COLOR_RGBA2GRAY;
                Imgproc.cvtColor(rgba, gray, code);
                Imgproc.threshold(gray, gray, 1, 255, Imgproc.THRESH_BINARY);
                converted = true;
            } finally {
                bitmap.recycle();
                rgba.release();
                if (!converted) {
                    gray.release();
                }
            }
            if (gray.empty()
                    || templateMat == null
                    || templateMat.empty()
                    || gray.cols() != templateMat.cols()
                    || gray.rows() != templateMat.rows()) {
                gray.release();
                return null;
            }
            Template.putTaskSingleMatCache(projectName, taskName, maskName, gray);
            return gray;
        } catch (Exception e) {
            Log.w(TAG, "加载模板 mask 失败: " + e.getMessage());
            return null;
        }
    }

    private CompiledMatchPlan getOrCreateCompiledPlan(Object rawMatchMap) {
        String cacheKey = buildPlanCacheKey(rawMatchMap);
        if (cacheKey == null) {
            return null;
        }

        CompiledMatchPlan cached = MATCH_PLAN_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, Map<String, Double>> matchMap = parseMatchMap(rawMatchMap);
        if (matchMap == null || matchMap.isEmpty()) {
            return null;
        }

        CompiledMatchPlan built = buildCompiledPlan(matchMap);
        if (built == null || built.groups.isEmpty()) {
            return null;
        }
        MATCH_PLAN_CACHE.put(cacheKey, built);
        return built;
    }

    private String buildPlanCacheKey(Object rawMatchMap) {
        if (rawMatchMap == null) {
            return null;
        }
        if (rawMatchMap instanceof String) {
            String raw = ((String) rawMatchMap).trim();
            return raw.isEmpty() ? null : raw;
        }
        try {
            return GSON.toJson(rawMatchMap);
        } catch (Exception ignored) {
            return String.valueOf(rawMatchMap);
        }
    }

    private CompiledMatchPlan buildCompiledPlan(Map<String, Map<String, Double>> matchMap) {
        List<CompiledMatchGroup> groups = new ArrayList<>();
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;

        for (Map.Entry<String, Map<String, Double>> entry : matchMap.entrySet()) {
            ParsedBbox parsedBbox = parseBboxSpec(entry.getKey());
            if (parsedBbox == null) {
                continue;
            }
            List<TemplateRule> rules = new ArrayList<>();
            Map<String, Double> templates = entry.getValue();
            if (templates != null) {
                for (Map.Entry<String, Double> templateEntry : templates.entrySet()) {
                    String templateName = templateEntry.getKey();
                    if (templateName == null || templateName.trim().isEmpty()) {
                        continue;
                    }
                    double similarity = templateEntry.getValue() == null
                            ? DEFAULT_SIMILARITY
                            : templateEntry.getValue();
                    rules.add(new TemplateRule(templateName, similarity));
                }
            }
            if (rules.isEmpty()) {
                continue;
            }

            groups.add(new CompiledMatchGroup(parsedBbox.region, rules));
            left = Math.min(left, parsedBbox.region.x);
            top = Math.min(top, parsedBbox.region.y);
            right = Math.max(right, parsedBbox.region.x + parsedBbox.region.width);
            bottom = Math.max(bottom, parsedBbox.region.y + parsedBbox.region.height);
        }

        android.graphics.Rect captureRoi = null;
        if (!groups.isEmpty() && left < right && top < bottom) {
            captureRoi = new android.graphics.Rect(
                    left - CAPTURE_ROI_PADDING_PX,
                    top - CAPTURE_ROI_PADDING_PX,
                    right + CAPTURE_ROI_PADDING_PX,
                    bottom + CAPTURE_ROI_PADDING_PX);
        }
        return new CompiledMatchPlan(groups, captureRoi);
    }

    private boolean handleMatchSuccess(MetaOperation obj,
                                       OperationContext ctx,
                                       AutoAccessibilityService svc,
                                       MatchTaskResult result) {
        Map<String, Object> resMap = new HashMap<>();
        MatchResult matchResult = new MatchResult(result.position, 1.0);
        Integer responseType = obj.getResponseType();
        if (responseType != null && responseType == 1) {
            ScreenCaptureManager mgr = ScreenCaptureManager.getInstance();
            int screenW = Math.round(result.task.info.width  * mgr.getActualInvScaleX());
            int screenH = Math.round(result.task.info.height * mgr.getActualInvScaleY());
            List<Integer> matchedBbox = Arrays.asList(
                    (int) result.position.x,
                    (int) result.position.y,
                    screenW,
                    screenH);
            resMap.put(MetaOperation.RESULT, matchResult);
            resMap.put(MetaOperation.BBOX, matchedBbox);
            resMap.put(MetaOperation.MATCHED, true);

            if (ctx == null || !ctx.suppressVisualFeedback) {
                MAIN_HANDLER.postDelayed(() -> svc.showRectFeedback(
                        (int) result.position.x,
                        (int) result.position.y,
                        screenW,
                        screenH,
                        120,
                        0xFFCD0C0C,
                        1.5f,
                        0x00000000), RECT_FEEDBACK_DELAY_MS);
            }
        }

        ctx.currentResponse = resMap;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    private boolean createTimeoutResponse(OperationContext ctx, MetaOperation obj) {
        HashMap<String, Object> resMap = new HashMap<>();
        resMap.put(MetaOperation.RESULT, null);
        resMap.put(MetaOperation.BBOX, null);
        resMap.put(MetaOperation.MATCHED, false);
        ctx.currentResponse = resMap;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Double>> parseMatchMap(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map) {
            return (Map<String, Map<String, Double>>) raw;
        }
        if (raw instanceof String) {
            try {
                Type type = new TypeToken<Map<String, Map<String, Double>>>() {}.getType();
                return GSON.fromJson((String) raw, type);
            } catch (Exception e) {
                Log.e(TAG, "解析 MATCHMAP JSON 失败: " + e.getMessage());
            }
        }
        return null;
    }

    private static ParsedBbox parseBboxSpec(String raw) {
        int[] values = parseBboxArray(raw);
        if (values == null) {
            return null;
        }
        return new ParsedBbox(new Rect(values[0], values[1], values[2], values[3]));
    }

    private static int[] parseBboxArray(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        int[] values = new int[4];
        int count = 0;
        int sign = 1;
        int current = 0;
        boolean readingNumber = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '-') {
                if (readingNumber) {
                    return null;
                }
                sign = -1;
                readingNumber = true;
                current = 0;
                continue;
            }
            if (ch >= '0' && ch <= '9') {
                if (!readingNumber) {
                    sign = 1;
                    current = 0;
                    readingNumber = true;
                }
                current = current * 10 + (ch - '0');
                continue;
            }
            if (readingNumber) {
                if (count >= 4) {
                    return null;
                }
                values[count++] = current * sign;
                readingNumber = false;
                sign = 1;
                current = 0;
            }
        }
        if (readingNumber) {
            if (count >= 4) {
                return null;
            }
            values[count++] = current * sign;
        }
        if (count != 4 || values[2] <= 0 || values[3] <= 0) {
            return null;
        }
        return values;
    }

    public static List<Integer> parseBbox(String raw) {
        int[] values = parseBboxArray(raw);
        if (values == null) {
            return List.of();
        }
        return Arrays.asList(values[0], values[1], values[2], values[3]);
    }

    private String getStringSafe(Map<String, Object> map, String key, String def) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : def;
    }

    private long parseDelayMs(Object raw) {
        if (raw instanceof Number) {
            return Math.max(0L, Math.min(((Number) raw).longValue(), MetaOperation.MAX_MATCH_DELAY_MS));
        }
        if (raw instanceof String) {
            try {
                return Math.max(0L, Math.min(Long.parseLong(((String) raw).trim()), MetaOperation.MAX_MATCH_DELAY_MS));
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private double parseDouble(Object raw, double def) {
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        if (raw instanceof String) {
            try {
                return Double.parseDouble(((String) raw).trim());
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private boolean parseBoolean(Object raw, boolean def) {
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw instanceof String) {
            String text = ((String) raw).trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return def;
    }

    private static Mat safeSubmat(Mat screen, Rect region) {
        int x = Math.max(0, region.x);
        int y = Math.max(0, region.y);
        int w = Math.min(region.width, screen.width() - x);
        int h = Math.min(region.height, screen.height() - y);
        if (w <= 0 || h <= 0) {
            return null;
        }
        return screen.submat(new Rect(x, y, w, h));
    }

    /**
     * 将 screen 坐标系的 region 转换为 capture 坐标系的本地 Rect，用于在 capture-scale Mat 中取子矩阵。
     * 使用实际轴向缩放系数，修正 16-byte 对齐导致的 X/Y 轴缩放不一致问题。
     */
    private static Rect toLocalRectCapture(Rect region, android.graphics.Rect captureRoi) {
        ScreenCaptureManager mgr = ScreenCaptureManager.getInstance();
        android.graphics.Rect captureRegion = toCaptureRect(region);
        if (captureRegion == null) {
            return new Rect(0, 0, 0, 0);
        }
        if (captureRoi == null) {
            return new Rect(
                    captureRegion.left,
                    captureRegion.top,
                    captureRegion.width(),
                    captureRegion.height());
        }
        android.graphics.Rect captureBase = mgr.toCaptureRect(captureRoi);
        if (captureBase == null) {
            return new Rect(0, 0, 0, 0);
        }
        // (region - captureRoi偏移) 后缩放到 capture 坐标
        return new Rect(
                captureRegion.left - captureBase.left,
                captureRegion.top - captureBase.top,
                captureRegion.width(),
                captureRegion.height());
    }

    private static android.graphics.Rect toCaptureRect(Rect region) {
        return ScreenCaptureManager.getInstance().toCaptureRect(new android.graphics.Rect(
                region.x,
                region.y,
                region.x + region.width,
                region.y + region.height));
    }

    private static final class MatchTask {
        final int priority;
        final String templateName;
        final Rect region;
        final TemplateInfo info;

        MatchTask(int priority, String templateName, Rect region, TemplateInfo info) {
            this.priority = priority;
            this.templateName = templateName;
            this.region = region;
            this.info = info;
        }
    }

    private static final class TemplateInfo {
        final Mat mat;
        final Mat mask;
        final double similarity;
        final boolean useGray;
        final int width;
        final int height;
        final int matchOffsetX;
        final int matchOffsetY;

        TemplateInfo(Mat mat,
                     Mat mask,
                     double similarity,
                     boolean useGray,
                     int width,
                     int height,
                     int matchOffsetX,
                     int matchOffsetY) {
            this.mat = mat;
            this.mask = mask;
            this.similarity = similarity;
            this.useGray = useGray;
            this.width = width;
            this.height = height;
            this.matchOffsetX = matchOffsetX;
            this.matchOffsetY = matchOffsetY;
        }
    }

    private static final class MaskStats {
        final boolean useful;
        final Rect bounds;
        final double densityInBounds;

        MaskStats(boolean useful, Rect bounds, double densityInBounds) {
            this.useful = useful;
            this.bounds = bounds;
            this.densityInBounds = densityInBounds;
        }

        static MaskStats notUseful() {
            return new MaskStats(false, new Rect(0, 0, 0, 0), 0d);
        }
    }

    private static final class MatchTaskResult {
        final boolean matched;
        final Point position;
        final MatchTask task;
        final int priority;

        MatchTaskResult(boolean matched, Point position, MatchTask task) {
            this.matched = matched;
            this.position = position;
            this.task = task;
            this.priority = task.priority;
        }
    }

    private static final class ParsedBbox {
        final Rect region;

        ParsedBbox(Rect region) {
            this.region = region;
        }
    }

    private static final class TemplateRule {
        final String templateName;
        final double similarity;

        TemplateRule(String templateName, double similarity) {
            this.templateName = templateName;
            this.similarity = similarity;
        }
    }

    private static final class CompiledMatchGroup {
        final Rect region;
        final List<TemplateRule> templateRules;

        CompiledMatchGroup(Rect region, List<TemplateRule> templateRules) {
            this.region = region;
            this.templateRules = templateRules;
        }
    }

    private static final class CompiledMatchPlan {
        final List<CompiledMatchGroup> groups;
        final android.graphics.Rect captureRoi;

        CompiledMatchPlan(List<CompiledMatchGroup> groups, android.graphics.Rect captureRoi) {
            this.groups = groups;
            this.captureRoi = captureRoi;
        }
    }
}
