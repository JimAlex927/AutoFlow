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
import com.auto.master.utils.AdaptivePollingController;
import com.auto.master.utils.MatchResult;
import com.auto.master.utils.OpenCVHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;

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
    private static final long MAX_PRE_DELAY_MS = 5000L;
    private static final double DEFAULT_SIMILARITY = 0.88d;
    private static final int CAPTURE_ROI_PADDING_PX = 12;
    private static final long RECT_FEEDBACK_DELAY_MS = 24L;
    private static final int MAX_PLAN_CACHE_ENTRIES = 32;
    private static final int POOL_SIZE = 2;

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
        double duration = parseDouble(inputMap.get(MetaOperation.MATCHTIMEOUT), 5000d);
        long preDelayMs = parseDelayMs(inputMap.get(MetaOperation.MATCH_PRE_DELAY_MS));

        CompiledMatchPlan plan = getOrCreateCompiledPlan(inputMap.get(MetaOperation.MATCHMAP));
        if (plan == null || plan.groups.isEmpty()) {
            return createTimeoutResponse(ctx, obj);
        }

        List<MatchTask> taskList = buildTaskList(plan, projectName, taskName);
        if (taskList.isEmpty()) {
            return createTimeoutResponse(ctx, obj);
        }

        if (preDelayMs > 0) {
            SystemClock.sleep(preDelayMs);
        }

        AtomicReference<MatchTaskResult> winnerRef = new AtomicReference<>(null);
        AdaptivePollingController pollingController = AdaptivePollingController.forMatchMap();
        long startedAt = System.currentTimeMillis();

        while ((duration - (System.currentTimeMillis() - startedAt)) > 0) {
            long loopStart = SystemClock.uptimeMillis();
            Mat screenMat = pollingController.acquireFrame(plan.captureRoi);
            if (screenMat == null || screenMat.empty()) {
                pollingController.onMiss();
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
            roi = safeSubmat(screen, toLocalRect(task.region, captureRoi));
            if (roi == null || roi.empty()) {
                return new MatchTaskResult(false, null, task);
            }
            Point positionInRoi = OpenCVHelper.getInstance()
                    .fastSingleMatch(roi, task.info.mat, null, task.info.similarity);
            if (positionInRoi != null && positionInRoi.x >= 0) {
                Point positionGlobal = new Point(
                        positionInRoi.x + task.region.x,
                        positionInRoi.y + task.region.y);
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
                                          String taskName) {
        List<MatchTask> tasks = new ArrayList<>();
        Map<String, Mat> loadedTemplates = new HashMap<>();
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
                tasks.add(new MatchTask(
                        priority++,
                        rule.templateName,
                        group.region,
                        new TemplateInfo(templateMat, rule.similarity)));
            }
        }
        return tasks;
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
            File imgFile = new File(
                    svc.getApplicationContext().getExternalFilesDir(null),
                    "projects" + File.separator + projectName
                            + File.separator + taskName
                            + File.separator + "img"
                            + File.separator + templateName);
            if (!imgFile.exists()) {
                Log.w(TAG, "模板文件不存在: " + imgFile.getPath());
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
            Template.putTaskSingleMatCache(projectName, taskName, templateName, mat);
            return mat;
        } catch (Exception e) {
            Log.e(TAG, "tryLoadTemplate error: " + e.getMessage());
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
            List<Integer> matchedBbox = Arrays.asList(
                    (int) result.position.x,
                    (int) result.position.y,
                    result.task.info.width,
                    result.task.info.height);
            resMap.put(MetaOperation.RESULT, matchResult);
            resMap.put(MetaOperation.BBOX, matchedBbox);
            resMap.put(MetaOperation.MATCHED, true);

            MAIN_HANDLER.postDelayed(() -> svc.showRectFeedback(
                    (int) result.position.x,
                    (int) result.position.y,
                    result.task.info.width,
                    result.task.info.height,
                    120,
                    0xFFCD0C0C,
                    1.5f,
                    0x00000000), RECT_FEEDBACK_DELAY_MS);
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
            return Math.max(0L, Math.min(((Number) raw).longValue(), MAX_PRE_DELAY_MS));
        }
        if (raw instanceof String) {
            try {
                return Math.max(0L, Math.min(Long.parseLong(((String) raw).trim()), MAX_PRE_DELAY_MS));
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

    private static Rect toLocalRect(Rect region, android.graphics.Rect captureRoi) {
        if (captureRoi == null) {
            return region;
        }
        return new Rect(
                region.x - captureRoi.left,
                region.y - captureRoi.top,
                region.width,
                region.height);
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
        final double similarity;
        final int width;
        final int height;

        TemplateInfo(Mat mat, double similarity) {
            this.mat = mat;
            this.similarity = similarity;
            this.width = mat.width();
            this.height = mat.height();
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
