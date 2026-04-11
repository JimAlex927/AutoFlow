package com.auto.master.Task.Handler.OperationHandler;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OcrOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.capture.ScreenCapture;
import com.auto.master.ocr.OcrEngine;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OcrOperationHandler extends OperationHandler {
    private static final String TAG = "OcrOperationHandler";

    OcrOperationHandler() {
        this.setType(9);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (!(obj instanceof OcrOperation) || ctx == null) {
            return false;
        }
        ctx.currentOperation = obj;

        Map<String, Object> inputMap = obj.getInputMap();
        long timeoutMs = getLongSafe(inputMap, MetaOperation.OCR_TIMEOUT, 5000L);
        if (timeoutMs <= 0) {
            timeoutMs = 5000L;
        }
        String engine = getStringSafe(inputMap, MetaOperation.OCR_ENGINE, "paddle");
        boolean accurateMode = !"fast".equalsIgnoreCase(engine);

        List<Integer> bbox = parseBbox(inputMap == null ? null : inputMap.get(MetaOperation.BBOX));
        Rect cropRect = null;
        if (bbox != null && bbox.size() == 4) {
            cropRect = new Rect(bbox.get(0), bbox.get(1), bbox.get(0) + bbox.get(2), bbox.get(1) + bbox.get(3));
        }

        String recognized = recognizeOnce(cropRect, timeoutMs, accurateMode, engine);
        if (recognized == null) {
            return false;
        }

        Map<String, Object> res = new HashMap<>();
        res.put(MetaOperation.OCR_TEXT, recognized);
        res.put(MetaOperation.RESULT, recognized);
        res.put(MetaOperation.MATCHED, true);
        ctx.currentResponse = res;
        ctx.lastOperation = obj;

        String outVar = getStringSafe(inputMap, MetaOperation.OCR_OUTPUT_VAR, "");
        if (!TextUtils.isEmpty(outVar) && ctx.variables != null) {
            ctx.variables.put(outVar, recognized);
        }

        SystemClock.sleep(10);
        return true;
    }

    private String recognizeOnce(Rect cropRect, long timeoutMs, boolean accurateMode, String engine) {
        if (!OcrEngine.isAvailable()) {
            Log.i(TAG, "OCR engine unavailable, skip bitmap conversion");
            return "";
        }

        Mat mat = cropRect == null
                ? ScreenCapture.getSingleBitMapWhileInContinous(false)
                : ScreenCapture.getSingleBitMapRoiWhileInContinous(cropRect, false);
        if (mat == null || mat.empty()) {
            Log.w(TAG, "OCR截图失败，screenMat为空");
            return null;
        }

        Bitmap target = com.auto.master.utils.OpenCVHelper.getInstance().matToBitmap(mat);
        if (target == null || target.isRecycled()) {
            return null;
        }

        try {
            AutoAccessibilityService service = AutoAccessibilityService.get();
            if (service == null) {
                return OcrEngine.recognize(null, target, engine, accurateMode, timeoutMs);
            }
            return OcrEngine.recognize(service, target, engine, accurateMode, timeoutMs);
        } finally {
            if (!target.isRecycled()) {
                target.recycle();
            }
        }
    }

    private String getStringSafe(Map<String, Object> map, String key, String def) {
        if (map == null) {
            return def;
        }
        Object value = map.get(key);
        return value instanceof String ? (String) value : def;
    }

    private long getLongSafe(Map<String, Object> map, String key, long def) {
        if (map == null) {
            return def;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private List<Integer> parseBbox(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            if (list.size() < 4) {
                return null;
            }
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Object item = list.get(i);
                if (item instanceof Number) {
                    out.add(((Number) item).intValue());
                } else if (item instanceof String) {
                    try {
                        out.add((int) Double.parseDouble(((String) item).trim()));
                    } catch (Exception e) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            return out;
        }
        return null;
    }
}
