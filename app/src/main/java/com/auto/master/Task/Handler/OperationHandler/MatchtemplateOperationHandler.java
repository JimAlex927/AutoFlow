package com.auto.master.Task.Handler.OperationHandler;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;

import com.auto.master.Task.Operation.MatchTemplateOperation;
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class MatchtemplateOperationHandler extends OperationHandler {

    private static final String TAG = "MatchTemplateOp";
    private static final int MIN_RANDOM_SAMPLE_POINTS = 32;
    private static final int MIN_RANDOM_ROI_SIDE_PX = 4;
    private static final int RANDOM_ROI_CACHE_VARIANTS = 8;
    private static final int MAX_RANDOM_ROI_CACHE_ENTRIES = 64;
    private static final Map<String, List<RandomRoiPlan>> RANDOM_ROI_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<String, List<RandomRoiPlan>>(
                    MAX_RANDOM_ROI_CACHE_ENTRIES + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<RandomRoiPlan>> eldest) {
                    if (size() <= MAX_RANDOM_ROI_CACHE_ENTRIES) {
                        return false;
                    }
                    releaseRandomRoiPlans(eldest.getValue());
                    return true;
                }
            });

    MatchtemplateOperationHandler() {
        this.setType(6);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        ctx.currentOperation = obj;
        MatchTemplateOperation operation = (MatchTemplateOperation) obj;

        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            return false;
        }

        Map<String, Object> inputMap = operation.getInputMap();
        if (inputMap == null) {
            return createResponse(ctx, obj, false, null, null, null);
        }

        String projectName = getStringSafe(inputMap, MetaOperation.PROJECT, "fallback");
        String taskName = getStringSafe(inputMap, MetaOperation.TASK, "");
        String templateName = getStringSafe(
                inputMap,
                MetaOperation.SAVEFILENAME,
                "gesture_" + System.currentTimeMillis() + ".json");
        double similarity = parseDouble(inputMap.get(MetaOperation.MATCHSIMILARITY), 0.8d);
        double duration = parseDouble(inputMap.get(MetaOperation.MATCHTIMEOUT), MetaOperation.DEFAULT_MATCH_TIMEOUT_MS);
        long preDelayMs = inputMap.containsKey(MetaOperation.NODE_PRE_DELAY_MS)
                ? 0L
                : parseDelayMs(inputMap.get(MetaOperation.MATCH_PRE_DELAY_MS));
        int matchMethod = (int) parseDouble(inputMap.get(MetaOperation.MATCHMETHOD), 5d);
        double sampleRatio = parseDouble(inputMap.get(MetaOperation.MATCH_SAMPLE_RATIO), 0.1d);
        sampleRatio = Math.max(0.001d, Math.min(1.0d, sampleRatio));
        double scaleFactor = parseDouble(inputMap.get(MetaOperation.MATCHSCALEFACTOR), 1.0d);
        scaleFactor = Math.max(0.1d, Math.min(1.0d, scaleFactor));

        List<Integer> bbox = getOrLoadTemplateBbox(projectName, taskName, templateName);
        Mat templateMat = getOrLoadTemplateMat(projectName, taskName, templateName);
        if (templateMat == null || templateMat.empty()) {
            Log.w(TAG, "模板加载失败: " + templateName);
            return createResponse(ctx, obj, false, null, bbox, null);
        }
        Mat templateMask = getOrLoadTemplateMaskMat(projectName, taskName, templateName, templateMat);
//        NOTE: 这里只有method是随机roi的时候 才会有randomSampleMask 否则是 null
        Mat randomSampleMask = createRandomSampleMaskIfNeeded(templateMat, matchMethod, sampleRatio);
//        这里会尝试进行合并 前提条件是 method 是 随机roi 才会合并 否则走默认mask
        Mat matchMask = combineMasksIfNeeded(templateMask, randomSampleMask);

        android.graphics.Rect captureRoi = null;
        if (bbox != null && bbox.size() >= 4) {
            captureRoi = new android.graphics.Rect(
                    bbox.get(0),
                    bbox.get(1),
                    bbox.get(0) + bbox.get(2),
                    bbox.get(1) + bbox.get(3));
        }
        RandomRoiPlan randomRoiPlan = getRandomRoiPlan(
                projectName, taskName, templateName, captureRoi, templateMat, templateMask, matchMethod, sampleRatio);
        if (randomRoiPlan != null) {
            captureRoi = randomRoiPlan.captureRoi;
        }

        if (preDelayMs > 0) {
            SystemClock.sleep(preDelayMs);
        }

        boolean matched = false;
        MatchResult firstMatch = null;
        List<Integer> matchedBbox = null;
        AdaptivePollingController pollingController = AdaptivePollingController.forTemplateMatch(inputMap);
        long startedAt = System.currentTimeMillis();

        while (duration > System.currentTimeMillis() - startedAt) {
            long loopStartMs = SystemClock.uptimeMillis();
            Mat screenMat = pollingController.acquireFrame(captureRoi);
            try {
                if (screenMat == null || screenMat.empty()) {
                    pollingController.onMiss();
                    pollingController.sleepUntilNextIteration(loopStartMs);
                    continue;
                }
                if (!pollingController.hasFreshFrame()) {
                    pollingController.sleepUntilNextIteration(loopStartMs);
                    continue;
                }
                Point position = matchTemplate(screenMat, templateMat, similarity, scaleFactor,
                        matchMethod, sampleRatio, matchMask, randomRoiPlan);
                if (position == null || position.x < 0 || position.y < 0) {
                    pollingController.onMiss();
                    pollingController.sleepUntilNextIteration(loopStartMs);
                    continue;
                }
                if (randomRoiPlan != null) {
                    firstMatch = new MatchResult(new Point(randomRoiPlan.fullRoi.left, randomRoiPlan.fullRoi.top), 1);
                    matchedBbox = java.util.Arrays.asList(
                            randomRoiPlan.fullRoi.left,
                            randomRoiPlan.fullRoi.top,
                            randomRoiPlan.fullRoi.width(),
                            randomRoiPlan.fullRoi.height());
                    matched = true;
                    pollingController.onHit();
                    break;
                }
                ScreenCaptureManager captureManager = ScreenCaptureManager.getInstance();
                float invScaleX = captureManager.getActualInvScaleX();
                float invScaleY = captureManager.getActualInvScaleY();
                Point screenPosition = new Point(
                        Math.round(position.x * invScaleX),
                        Math.round(position.y * invScaleY));
                if (captureRoi != null) {
                    screenPosition.x += captureRoi.left;
                    screenPosition.y += captureRoi.top;
                }
                firstMatch = new MatchResult(screenPosition, 1);
                matchedBbox = java.util.Arrays.asList(
                        (int) screenPosition.x,
                        (int) screenPosition.y,
                        Math.round(templateMat.width() * invScaleX),
                        Math.round(templateMat.height() * invScaleY));
                matched = true;
                pollingController.onHit();
                break;
            } catch (Exception e) {
                Log.w(TAG, "模板匹配失败: " + e.getMessage());
                pollingController.onMiss();
            } finally {
                pollingController.releaseFrameIfOwned(screenMat);
            }
            pollingController.sleepUntilNextIteration(loopStartMs);
        }
        if (randomSampleMask != null) {
            randomSampleMask.release();
        }
        if (matchMask != null && matchMask != templateMask && matchMask != randomSampleMask) {
            matchMask.release();
        }
        if (randomRoiPlan != null) {
            randomRoiPlan.release();
        }

        return createResponse(ctx, obj, matched, firstMatch, matched ? matchedBbox : bbox, svc);
    }

    private Point matchTemplate(Mat screenMat,
                                Mat templateMat,
                                double similarity,
                                double scaleFactor,
                                int matchMethod,
                                double sampleRatio,
                                Mat mask,
                                RandomRoiPlan randomRoiPlan) {
        if (randomRoiPlan != null && randomRoiPlan.templateRoiMat != null && !randomRoiPlan.templateRoiMat.empty()) {
            return OpenCVHelper.getInstance().fastSingleMatch(
                    screenMat,
                    randomRoiPlan.templateRoiMat,
                    null,
                    similarity,
                    randomRoiPlan.maskRoiMat);
        }
        if (mask != null && !mask.empty()) {
            return OpenCVHelper.getInstance().fastSingleMatch(screenMat, templateMat, null, similarity, mask);
        }
        return OpenCVHelper.getInstance().fastSingleMatch(screenMat, templateMat, null, similarity, scaleFactor);
    }

    public static void clearRandomRoiCache() {
        synchronized (RANDOM_ROI_CACHE) {
            for (List<RandomRoiPlan> plans : RANDOM_ROI_CACHE.values()) {
                releaseRandomRoiPlans(plans);
            }
            RANDOM_ROI_CACHE.clear();
        }
    }

    private static void releaseRandomRoiPlans(List<RandomRoiPlan> plans) {
        if (plans == null) {
            return;
        }
        for (RandomRoiPlan plan : plans) {
            if (plan != null) {
                plan.forceRelease();
            }
        }
    }

    private RandomRoiPlan getRandomRoiPlan(String projectName,
                                           String taskName,
                                           String templateName,
                                           android.graphics.Rect fullRoi,
                                           Mat templateMat,
                                           Mat templateMask,
                                           int matchMethod,
                                           double sampleRatio) {
        if (matchMethod != MetaOperation.MATCH_METHOD_RANDOM_ROI
                || sampleRatio >= 1.0d
                || fullRoi == null
                || fullRoi.isEmpty()
                || templateMat == null
                || templateMat.empty()) {
            return null;
        }
        String cacheKey = buildRandomRoiCacheKey(projectName, taskName, templateName,
                fullRoi, templateMat, templateMask, sampleRatio);
        synchronized (RANDOM_ROI_CACHE) {
            List<RandomRoiPlan> cached = RANDOM_ROI_CACHE.get(cacheKey);
            if (hasUsableRandomRoiPlans(cached)) {
                return cached.get(ThreadLocalRandom.current().nextInt(cached.size()));
            }
            releaseRandomRoiPlans(cached);
            cached = new ArrayList<>(RANDOM_ROI_CACHE_VARIANTS);
            for (int i = 0; i < RANDOM_ROI_CACHE_VARIANTS; i++) {
                RandomRoiPlan plan = createRandomRoiPlan(fullRoi, templateMat, templateMask, sampleRatio, true);
                if (plan != null) {
                    cached.add(plan);
                }
            }
            if (cached.isEmpty()) {
                RANDOM_ROI_CACHE.remove(cacheKey);
                return null;
            }
            RANDOM_ROI_CACHE.put(cacheKey, cached);
            return cached.get(ThreadLocalRandom.current().nextInt(cached.size()));
        }
    }

    private boolean hasUsableRandomRoiPlans(List<RandomRoiPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return false;
        }
        for (RandomRoiPlan plan : plans) {
            if (plan == null || plan.templateRoiMat == null || plan.templateRoiMat.empty()) {
                return false;
            }
            if (plan.maskRoiMat != null && plan.maskRoiMat.empty()) {
                return false;
            }
        }
        return true;
    }

    private String buildRandomRoiCacheKey(String projectName,
                                          String taskName,
                                          String templateName,
                                          android.graphics.Rect fullRoi,
                                          Mat templateMat,
                                          Mat templateMask,
                                          double sampleRatio) {
        long ratioKey = Math.round(Math.max(0.001d, Math.min(1.0d, sampleRatio)) * 10000d);
        return String.valueOf(projectName) + '|'
                + taskName + '|'
                + templateName + '|'
                + System.identityHashCode(templateMat) + '|'
                + templateMat.cols() + 'x' + templateMat.rows() + '|'
                + (templateMask == null ? "no-mask" : System.identityHashCode(templateMask)
                + ":" + templateMask.cols() + "x" + templateMask.rows()) + '|'
                + ScreenCaptureManager.CAPTURE_SCALE + '|'
                + fullRoi.left + ',' + fullRoi.top + ',' + fullRoi.right + ',' + fullRoi.bottom + '|'
                + ratioKey;
    }

    private RandomRoiPlan createRandomRoiPlan(android.graphics.Rect fullRoi,
                                              Mat templateMat,
                                              Mat templateMask,
                                              double sampleRatio,
                                              boolean cached) {

        ScreenCaptureManager mgr = ScreenCaptureManager.getInstance();
        android.graphics.Rect fullCaptureRoi = mgr.toCaptureRect(fullRoi);
        if (fullCaptureRoi == null || fullCaptureRoi.isEmpty()) {
            return null;
        }

        int templateW = templateMat.cols();
        int templateH = templateMat.rows();
        if (templateW <= 0 || templateH <= 0) {
            return null;
        }

        double sideRatio = Math.sqrt(Math.max(0.001d, Math.min(1.0d, sampleRatio)));
        int roiW = Math.max(1, Math.min(templateW, (int) Math.round(templateW * sideRatio)));
        int roiH = Math.max(1, Math.min(templateH, (int) Math.round(templateH * sideRatio)));
        roiW = Math.min(templateW, Math.max(Math.min(templateW, MIN_RANDOM_ROI_SIDE_PX), roiW));
        roiH = Math.min(templateH, Math.max(Math.min(templateH, MIN_RANDOM_ROI_SIDE_PX), roiH));

        int localX = templateW == roiW ? 0 : ThreadLocalRandom.current().nextInt(templateW - roiW + 1);
        int localY = templateH == roiH ? 0 : ThreadLocalRandom.current().nextInt(templateH - roiH + 1);

        android.graphics.Rect requestedCaptureRoi = new android.graphics.Rect(
                fullCaptureRoi.left + localX,
                fullCaptureRoi.top + localY,
                fullCaptureRoi.left + localX + roiW,
                fullCaptureRoi.top + localY + roiH);
        android.graphics.Rect screenRoi = mgr.toScreenRect(requestedCaptureRoi);
        android.graphics.Rect effectiveCaptureRoi = mgr.toCaptureRect(screenRoi);
        if (screenRoi == null || effectiveCaptureRoi == null || effectiveCaptureRoi.isEmpty()) {
            return null;
        }

        int templateX = effectiveCaptureRoi.left - fullCaptureRoi.left;
        int templateY = effectiveCaptureRoi.top - fullCaptureRoi.top;
        int templateRight = effectiveCaptureRoi.right - fullCaptureRoi.left;
        int templateBottom = effectiveCaptureRoi.bottom - fullCaptureRoi.top;
        templateX = Math.max(0, Math.min(templateX, templateW - 1));
        templateY = Math.max(0, Math.min(templateY, templateH - 1));
        templateRight = Math.max(templateX + 1, Math.min(templateRight, templateW));
        templateBottom = Math.max(templateY + 1, Math.min(templateBottom, templateH));

        Mat templateRoiMat = templateMat.submat(templateY, templateBottom, templateX, templateRight);
        Mat maskRoiMat = null;
        if (templateMask != null && !templateMask.empty()
                && templateMask.cols() == templateMat.cols()
                && templateMask.rows() == templateMat.rows()) {
            maskRoiMat = templateMask.submat(templateY, templateBottom, templateX, templateRight);
        }
        return new RandomRoiPlan(new android.graphics.Rect(fullRoi), screenRoi, templateRoiMat, maskRoiMat, cached);
    }

    private boolean shouldUseRandomSample(Mat screenMat, Mat templateMat, int matchMethod, double sampleRatio, Mat randomSampleMask) {
        return matchMethod == MetaOperation.MATCH_METHOD_RANDOM_SAMPLE
                && sampleRatio < 1.0d
                && randomSampleMask != null
                && !randomSampleMask.empty()
                && screenMat != null
                && templateMat != null
                && !screenMat.empty()
                && !templateMat.empty()
                && screenMat.rows() == templateMat.rows()
                && screenMat.cols() == templateMat.cols();
    }

    private Mat createRandomSampleMaskIfNeeded(Mat templateMat, int matchMethod, double sampleRatio) {
        if (matchMethod != MetaOperation.MATCH_METHOD_RANDOM_SAMPLE
                || sampleRatio >= 1.0d
                || templateMat == null
                || templateMat.empty()) {
            return null;
        }

        int rows = templateMat.rows();
        int cols = templateMat.cols();
        long totalPixelsLong = (long) rows * cols;
        if (totalPixelsLong <= 0 || totalPixelsLong > Integer.MAX_VALUE) {
            return null;
        }

        int totalPixels = (int) totalPixelsLong;
        int sampleCount = (int) Math.ceil(totalPixels * sampleRatio);
        sampleCount = Math.max(MIN_RANDOM_SAMPLE_POINTS, Math.min(totalPixels, sampleCount));
        byte[] maskBytes = new byte[totalPixels];
        long seed = sampleSeed(rows, cols, templateMat.channels(), sampleCount);

        for (int i = 0; i < sampleCount; i++) {
            int pixelIndex = pickStratifiedPixel(seed, i, sampleCount, totalPixels);
            maskBytes[pixelIndex] = (byte) 255;
        }

        Mat mask = Mat.zeros(rows, cols, CvType.CV_8UC1);
        mask.put(0, 0, maskBytes);
        return mask;
    }

    private Mat combineMasksIfNeeded(Mat templateMask, Mat randomSampleMask) {
        if (templateMask == null || templateMask.empty()) {
            return randomSampleMask;
        }
        if (randomSampleMask == null || randomSampleMask.empty()) {
            return templateMask;
        }
        if (templateMask.rows() != randomSampleMask.rows()
                || templateMask.cols() != randomSampleMask.cols()) {
            return randomSampleMask;
        }
        Mat combined = new Mat();
        Core.bitwise_and(templateMask, randomSampleMask, combined);
        return combined;
    }

    private int pickStratifiedPixel(long seed, int index, int sampleCount, int totalPixels) {
        int start = (int) Math.floor(index * (double) totalPixels / sampleCount);
        int end = (int) Math.floor((index + 1) * (double) totalPixels / sampleCount) - 1;
        if (end < start) {
            end = start;
        }
        int span = end - start + 1;
        long mixed = mix64(seed + 0x9E3779B97F4A7C15L * (index + 1L));
        int offset = (int) Long.remainderUnsigned(mixed, span);
        return Math.min(totalPixels - 1, start + offset);
    }

    private long sampleSeed(int rows, int cols, int channels, int sampleCount) {
        long seed = 0xD1B54A32D192ED03L;
        seed ^= (long) rows * 0x9E3779B185EBCA87L;
        seed ^= (long) cols * 0xC2B2AE3D27D4EB4FL;
        seed ^= (long) channels << 32;
        seed ^= sampleCount;
        return mix64(seed);
    }

    private long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private boolean createResponse(OperationContext ctx,
                                   MetaOperation obj,
                                   boolean matched,
                                   MatchResult result,
                                   List<Integer> bbox,
                                   AutoAccessibilityService svc) {
        Map<String, Object> response = new HashMap<>();
        Integer responseType = obj.getResponseType();
        if (responseType != null && responseType == 1) {
            response.put(MetaOperation.RESULT, result);
            response.put(MetaOperation.BBOX, bbox);
            response.put(MetaOperation.MATCHED, matched);
            if (matched && result != null && bbox != null && bbox.size() >= 4 && svc != null
                    && (ctx == null || !ctx.suppressVisualFeedback)) {
                Point point = result.getLocation();
                List<Integer> finalBbox = bbox;
                getMainHandler().post(() ->
                        svc.showRectFeedback(
                                (int) point.x,
                                (int) point.y,
                                finalBbox.get(2),
                                finalBbox.get(3),
                                500,
                                0x00000000,
                                0,
                                0x44CD0C0C));
            }
        }
        ctx.currentResponse = response;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    private Mat getOrLoadTemplateMat(String projectName, String taskName, String templateName) {
        Mat cached = Template.getTaskSingleMutCache(projectName, taskName, templateName);
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
            Log.e(TAG, "精准加载模板失败: " + e.getMessage());
            return null;
        }
    }

    private Mat getOrLoadTemplateMaskMat(String projectName,
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
                Log.w(TAG, "模板 mask 尺寸不匹配，已忽略: " + maskName);
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

    private List<Integer> getOrLoadTemplateBbox(String projectName, String taskName, String templateName) {
        List<Integer> cached = Template.getManifestSingleCache(projectName, taskName, templateName);
        if (cached != null && cached.size() >= 4) {
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
            File manifestFile = CaptureScaleHelper.resolveTemplateManifestFile(
                    imgDir, ScreenCaptureManager.CAPTURE_SCALE);
            if (manifestFile == null || !manifestFile.exists()) {
                return null;
            }
            String content = new String(Files.readAllBytes(manifestFile.toPath()));
            JSONArray array = new JSONObject(content).optJSONArray(templateName);
            if (array == null || array.length() < 4) {
                return null;
            }
            List<Integer> bbox = new ArrayList<>(4);
            for (int i = 0; i < 4; i++) {
                bbox.add(array.getInt(i));
            }
            Template.putTaskSingleManifestCache(projectName, taskName, templateName, bbox);
            return bbox;
        } catch (Exception e) {
            Log.w(TAG, "读取模板 bbox 失败: " + e.getMessage());
            return null;
        }
    }

    private String getStringSafe(Map<String, Object> map, String key, String def) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : def;
    }

    private long parseDelayMs(Object raw) {
        if (raw instanceof Number) {
            long value = ((Number) raw).longValue();
            return Math.max(0L, Math.min(value, MetaOperation.MAX_MATCH_DELAY_MS));
        }
        if (raw instanceof String) {
            try {
                long value = Long.parseLong(((String) raw).trim());
                return Math.max(0L, Math.min(value, MetaOperation.MAX_MATCH_DELAY_MS));
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

    private static final class RandomRoiPlan {
        final android.graphics.Rect fullRoi;
        final android.graphics.Rect captureRoi;
        final Mat templateRoiMat;
        final Mat maskRoiMat;
        final boolean cached;

        RandomRoiPlan(android.graphics.Rect fullRoi,
                      android.graphics.Rect captureRoi,
                      Mat templateRoiMat,
                      Mat maskRoiMat,
                      boolean cached) {
            this.fullRoi = fullRoi;
            this.captureRoi = captureRoi;
            this.templateRoiMat = templateRoiMat;
            this.maskRoiMat = maskRoiMat;
            this.cached = cached;
        }

        void release() {
            if (!cached && templateRoiMat != null) {
                templateRoiMat.release();
            }
            if (!cached && maskRoiMat != null) {
                maskRoiMat.release();
            }
        }

        void forceRelease() {
            if (templateRoiMat != null) {
                templateRoiMat.release();
            }
            if (maskRoiMat != null) {
                maskRoiMat.release();
            }
        }
    }
}
