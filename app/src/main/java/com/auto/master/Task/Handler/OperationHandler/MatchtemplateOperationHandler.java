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
import com.auto.master.capture.ScreenCaptureManager;
import com.auto.master.utils.AdaptivePollingController;
import com.auto.master.utils.MatchResult;
import com.auto.master.utils.OpenCVHelper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatchtemplateOperationHandler extends OperationHandler {

    private static final String TAG = "MatchTemplateOp";
    private static final long MAX_PRE_DELAY_MS = 5000L;

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
        double duration = parseDouble(inputMap.get(MetaOperation.MATCHTIMEOUT), 5000d);
        long preDelayMs = parseDelayMs(inputMap.get(MetaOperation.MATCH_PRE_DELAY_MS));

        List<Integer> bbox = getOrLoadTemplateBbox(projectName, taskName, templateName);
        Mat templateMat = getOrLoadTemplateMat(projectName, taskName, templateName);
        if (templateMat == null || templateMat.empty()) {
            Log.w(TAG, "模板加载失败: " + templateName);
            return createResponse(ctx, obj, false, null, bbox, null);
        }

        android.graphics.Rect captureRoi = null;
        if (bbox != null && bbox.size() >= 4) {
            captureRoi = new android.graphics.Rect(
                    bbox.get(0),
                    bbox.get(1),
                    bbox.get(0) + bbox.get(2),
                    bbox.get(1) + bbox.get(3));
        }

        if (preDelayMs > 0) {
            SystemClock.sleep(preDelayMs);
        }

        boolean matched = false;
        MatchResult firstMatch = null;
        List<Integer> matchedBbox = null;
        AdaptivePollingController pollingController = AdaptivePollingController.forTemplateMatch();
        long startedAt = System.currentTimeMillis();

        while (duration > System.currentTimeMillis() - startedAt) {
            long loopStartMs = SystemClock.uptimeMillis();
            Mat screenMat = pollingController.acquireFrame(captureRoi);
            if (screenMat == null || screenMat.empty()) {
                pollingController.onMiss();
                pollingController.sleepUntilNextIteration(loopStartMs);
                continue;
            }
            if (!pollingController.hasFreshFrame()) {
                pollingController.sleepUntilNextIteration(loopStartMs);
                continue;
            }
            try {
                Point position = OpenCVHelper.getInstance().fastSingleMatch(screenMat, templateMat, null, similarity);
                if (position == null || position.x < 0 || position.y < 0) {
                    pollingController.onMiss();
                    pollingController.sleepUntilNextIteration(loopStartMs);
                    continue;
                }
                // capture 坐标 → screen 坐标
                float invScale = 1.0f / ScreenCaptureManager.CAPTURE_SCALE;
                position.x = (int)(position.x * invScale);
                position.y = (int)(position.y * invScale);
                if (captureRoi != null) {
                    position.x += captureRoi.left;
                    position.y += captureRoi.top;
                }
                firstMatch = new MatchResult(position, 1);
                // templateMat 已缩放为 capture 分辨率，宽高换算回 screen 分辨率
                matchedBbox = java.util.Arrays.asList(
                        (int) position.x,
                        (int) position.y,
                        (int)(templateMat.width()  * invScale),
                        (int)(templateMat.height() * invScale));
                matched = true;
                pollingController.onHit();
                break;
            } catch (Exception e) {
                Log.w(TAG, "模板匹配失败: " + e.getMessage());
                pollingController.onMiss();
            }
            pollingController.sleepUntilNextIteration(loopStartMs);
        }

        return createResponse(ctx, obj, matched, firstMatch, matched ? matchedBbox : bbox, svc);
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
            if (matched && result != null && bbox != null && bbox.size() >= 4 && svc != null) {
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
            File imgFile = new File(
                    svc.getApplicationContext().getExternalFilesDir(null),
                    "projects" + File.separator + projectName
                            + File.separator + taskName
                            + File.separator + "img"
                            + File.separator + templateName);
            if (!imgFile.exists()) {
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
            // 缩放到 capture 分辨率，与 VirtualDisplay 采集尺寸一致
            if (ScreenCaptureManager.CAPTURE_SCALE != 1.0f) {
                Mat scaled = new Mat();
                Imgproc.resize(mat, scaled, new Size(),
                        ScreenCaptureManager.CAPTURE_SCALE,
                        ScreenCaptureManager.CAPTURE_SCALE,
                        Imgproc.INTER_LINEAR);
                mat.release();
                mat = scaled;
            }
            Template.putTaskSingleMatCache(projectName, taskName, templateName, mat);
            return mat;
        } catch (Exception e) {
            Log.e(TAG, "精准加载模板失败: " + e.getMessage());
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
            File manifestFile = new File(
                    svc.getApplicationContext().getExternalFilesDir(null),
                    "projects" + File.separator + projectName
                            + File.separator + taskName
                            + File.separator + "img"
                            + File.separator + "manifest.json");
            if (!manifestFile.exists()) {
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
            return Math.max(0L, Math.min(value, MAX_PRE_DELAY_MS));
        }
        if (raw instanceof String) {
            try {
                long value = Long.parseLong(((String) raw).trim());
                return Math.max(0L, Math.min(value, MAX_PRE_DELAY_MS));
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
}
