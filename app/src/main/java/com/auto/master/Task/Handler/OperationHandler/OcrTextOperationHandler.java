package com.auto.master.Task.Handler.OperationHandler;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OcrTextOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Operation.OperationType;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.capture.ScreenCapture;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OcrTextOperationHandler extends OperationHandler {

    private static final String TAG = "OcrTextHandler";
    private static final Object TESS_LOCK = new Object();
    private static TessBaseAPI cachedApi;
    private static String cachedLanguage = "";
    private static String cachedDataPath = "";

    OcrTextOperationHandler() {
        this.setType(OperationType.OCR_TEXT.getCode());
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (ctx == null) return false;
        if (ctx.variables == null) ctx.variables = new HashMap<>();
        ctx.currentOperation = obj;

        OcrTextOperation operation = (OcrTextOperation) obj;
        Map<String, Object> inputMap = operation.getInputMap();
        List<Integer> bbox = parseBbox(inputMap == null ? null : inputMap.get(MetaOperation.BBOX));
        if (bbox == null) {
            putFailure(ctx, obj, "missing_bbox", "OCR 区域为空", "");
            return true;
        }

        String language = getString(inputMap, MetaOperation.OCR_LANGUAGE, "chi_sim+eng");
        String textVar = getString(inputMap, MetaOperation.OCR_TEXT_VAR, "");
        String confidenceVar = getString(inputMap, MetaOperation.OCR_CONFIDENCE_VAR, "");
        int minConfidence = parseInt(inputMap == null ? null : inputMap.get(MetaOperation.OCR_MIN_CONFIDENCE), 0, 0, 100);
        int threshold = parseInt(inputMap == null ? null : inputMap.get(MetaOperation.OCR_THRESHOLD), 0, 0, 255);
        int pageSegMode = parsePageSegMode(inputMap == null ? null : inputMap.get(MetaOperation.OCR_PAGE_SEG_MODE));
        float scaleFactor = parseFloat(inputMap == null ? null : inputMap.get(MetaOperation.OCR_SCALE_FACTOR), 2.0f, 1.0f, 4.0f);
        long timeoutMs = parseLong(inputMap == null ? null : inputMap.get(MetaOperation.MATCHTIMEOUT),
                MetaOperation.DEFAULT_MATCH_TIMEOUT_MS, 1L, 60_000L);
        long preDelayMs = inputMap != null && inputMap.containsKey(MetaOperation.NODE_PRE_DELAY_MS)
                ? 0L
                : parseLong(inputMap == null ? null : inputMap.get(MetaOperation.MATCH_PRE_DELAY_MS),
                0L, 0L, MetaOperation.MAX_MATCH_DELAY_MS);

        Context appContext = getAppContext();
        if (appContext == null) {
            putFailure(ctx, obj, "no_context", "无障碍服务未连接，无法初始化 OCR", "");
            return true;
        }
        if (preDelayMs > 0L) {
            SystemClock.sleep(preDelayMs);
        }

        OcrPreviewResult preview = recognize(appContext, bbox, language, scaleFactor, threshold,
                pageSegMode, minConfidence, timeoutMs);
        String lastText = preview.text;
        int lastConfidence = preview.confidence;
        boolean success = preview.success;
        String reason = preview.reason;

        if (!TextUtils.isEmpty(textVar)) {
            ctx.variables.put(textVar, lastText);
        }
        if (!TextUtils.isEmpty(confidenceVar)) {
            ctx.variables.put(confidenceVar, (long) lastConfidence);
        }

        Rect roi = rectFromBbox(bbox);
        if (success && !ctx.suppressVisualFeedback) {
            AutoAccessibilityService svc = AutoAccessibilityService.get();
            if (svc != null && getMainHandler() != null) {
                getMainHandler().post(() -> svc.showRectFeedback(
                        roi.left, roi.top, roi.width(), roi.height(),
                        420, 0x00000000, 0, 0x6638BDF8));
            }
        }

        HashMap<String, Object> response = new HashMap<>();
        response.put(MetaOperation.MATCHED, success);
        response.put(MetaOperation.RESULT, lastText);
        response.put(MetaOperation.OCR_TEXT, lastText);
        response.put(MetaOperation.OCR_CONFIDENCE, (long) lastConfidence);
        response.put(MetaOperation.BBOX, bbox);
        response.put("reason", reason);
        response.put("language", language);
        ctx.currentResponse = response;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    public static OcrPreviewResult recognizeOnce(Context context,
                                                 List<Integer> bbox,
                                                 String language,
                                                 float scaleFactor,
                                                 int threshold,
                                                 String pageSegMode) {
        int psm = parsePageSegMode(pageSegMode);
        return recognize(context, bbox, language, scaleFactor, threshold, psm, 0, 2_000L);
    }

    private static OcrPreviewResult recognize(Context context,
                                              List<Integer> bbox,
                                              String language,
                                              float scaleFactor,
                                              int threshold,
                                              int pageSegMode,
                                              int minConfidence,
                                              long timeoutMs) {
        if (context == null) {
            return OcrPreviewResult.failure("no_context", "无可用 Context", "", 0);
        }
        List<Integer> normalizedBbox = parseBbox(bbox);
        if (normalizedBbox == null) {
            return OcrPreviewResult.failure("missing_bbox", "OCR 区域为空", "", 0);
        }
        String safeLanguage = TextUtils.isEmpty(language) ? "chi_sim+eng" : language.trim();
        TessDataResult dataResult = prepareTessData(context.getApplicationContext(), safeLanguage);
        if (!dataResult.ready) {
            return OcrPreviewResult.failure("missing_tessdata", dataResult.message, "", 0);
        }

        Rect roi = rectFromBbox(normalizedBbox);
        long start = SystemClock.uptimeMillis();
        String lastText = "";
        int lastConfidence = 0;
        String reason = "timeout";
        String message = "";

        while (SystemClock.uptimeMillis() - start <= timeoutMs) {
            Bitmap bitmap = null;
            Bitmap prepared = null;
            try {
                bitmap = ScreenCapture.captureLatestBitmap(roi, 500L, 40L);
                if (bitmap == null || bitmap.isRecycled()) {
                    reason = "capture_failed";
                    message = "截图失败";
                    SystemClock.sleep(180L);
                    continue;
                }
                prepared = preprocess(bitmap, scaleFactor, threshold);
                OcrResult result = runOcr(dataResult.dataPath, safeLanguage, prepared, pageSegMode);
                lastText = normalizeOcrText(result.text);
                lastConfidence = result.confidence;
                boolean success = lastConfidence >= minConfidence;
                return success
                        ? OcrPreviewResult.success(lastText, lastConfidence, "recognized")
                        : OcrPreviewResult.failure("low_confidence", "OCR 置信度低于阈值", lastText, lastConfidence);
            } catch (Exception e) {
                reason = "ocr_failed";
                message = e.getMessage() == null ? "OCR 识别失败" : e.getMessage();
                Log.w(TAG, "OCR failed", e);
            } finally {
                recycleIfNeeded(prepared);
                recycleIfNeeded(bitmap);
            }
            SystemClock.sleep(300L);
        }
        return OcrPreviewResult.failure(reason, message, lastText, lastConfidence);
    }

    private static Rect rectFromBbox(List<Integer> bbox) {
        return new Rect(bbox.get(0), bbox.get(1), bbox.get(0) + bbox.get(2), bbox.get(1) + bbox.get(3));
    }

    private static OcrResult runOcr(String dataPath, String language, Bitmap bitmap, int pageSegMode) {
        synchronized (TESS_LOCK) {
            TessBaseAPI api = getOrCreateApi(dataPath, language);
            api.setPageSegMode(pageSegMode);
            api.setImage(bitmap);
            String text = api.getUTF8Text();
            int confidence = api.meanConfidence();
            api.clear();
            return new OcrResult(text == null ? "" : text, Math.max(0, Math.min(100, confidence)));
        }
    }

    private static TessBaseAPI getOrCreateApi(String dataPath, String language) {
        if (cachedApi != null
                && TextUtils.equals(cachedLanguage, language)
                && TextUtils.equals(cachedDataPath, dataPath)) {
            return cachedApi;
        }
        if (cachedApi != null) {
            cachedApi.recycle();
            cachedApi = null;
        }
        TessBaseAPI api = new TessBaseAPI();
        if (!api.init(dataPath, language)) {
            api.recycle();
            throw new IllegalStateException("Tesseract 初始化失败: " + language);
        }
        cachedApi = api;
        cachedLanguage = language;
        cachedDataPath = dataPath;
        return api;
    }

    private static Bitmap preprocess(Bitmap source, float scaleFactor, int threshold) {
        Bitmap scaled = source;
        if (scaleFactor > 1.01f) {
            int w = Math.max(1, Math.round(source.getWidth() * scaleFactor));
            int h = Math.max(1, Math.round(source.getHeight() * scaleFactor));
            scaled = Bitmap.createScaledBitmap(source, w, h, true);
        }
        Bitmap out = Bitmap.createBitmap(scaled.getWidth(), scaled.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(scaled, 0, 0, paint);
        if (scaled != source) {
            recycleIfNeeded(scaled);
        }
        if (threshold > 0) {
            applyThreshold(out, threshold);
        }
        return out;
    }

    private static void applyThreshold(Bitmap bitmap, int threshold) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            int gray = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3;
            pixels[i] = gray >= threshold ? Color.WHITE : Color.BLACK;
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static TessDataResult prepareTessData(Context context, String language) {
        File internalRoot = new File(context.getFilesDir(), "tesseract");
        File internalDir = new File(internalRoot, "tessdata");
        if (!internalDir.exists() && !internalDir.mkdirs()) {
            return TessDataResult.missing("无法创建 tessdata 目录: " + internalDir.getAbsolutePath(), internalRoot.getAbsolutePath());
        }
        copyBundledTrainedData(context, internalDir);
        List<String> missing = missingLanguages(internalDir, language);
        if (missing.isEmpty()) {
            return TessDataResult.ready(internalRoot.getAbsolutePath());
        }

        File externalBase = context.getExternalFilesDir(null);
        if (externalBase != null) {
            File externalRoot = new File(externalBase, "tesseract");
            File externalDir = new File(externalRoot, "tessdata");
            if (externalDir.exists() && missingLanguages(externalDir, language).isEmpty()) {
                return TessDataResult.ready(externalRoot.getAbsolutePath());
            }
        }
        String message = "缺少 OCR 训练数据: " + TextUtils.join(", ", missing)
                + "。请将对应 *.traineddata 放入 assets/tessdata 后重新打包，或放到 "
                + internalDir.getAbsolutePath();
        return TessDataResult.missing(message, internalRoot.getAbsolutePath());
    }

    private static void copyBundledTrainedData(Context context, File targetDir) {
        try {
            AssetManager assets = context.getAssets();
            String[] names = assets.list("tessdata");
            if (names == null) return;
            for (String name : names) {
                if (TextUtils.isEmpty(name) || !name.endsWith(".traineddata")) {
                    continue;
                }
                File out = new File(targetDir, name);
                if (out.exists() && out.length() > 0) {
                    continue;
                }
                try (InputStream is = assets.open("tessdata/" + name);
                     FileOutputStream fos = new FileOutputStream(out, false)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, n);
                    }
                    fos.flush();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "copy bundled tessdata failed", e);
        }
    }

    private static List<String> missingLanguages(File tessdataDir, String language) {
        List<String> missing = new ArrayList<>();
        for (String lang : splitLanguages(language)) {
            File file = new File(tessdataDir, lang + ".traineddata");
            if (!file.exists() || file.length() <= 0) {
                missing.add(lang + ".traineddata");
            }
        }
        return missing;
    }

    private static List<String> splitLanguages(String language) {
        if (TextUtils.isEmpty(language)) {
            return Arrays.asList("eng");
        }
        String[] parts = language.split("\\+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String lang = part == null ? "" : part.trim();
            if (!lang.isEmpty()) {
                result.add(lang);
            }
        }
        return result.isEmpty() ? Arrays.asList("eng") : result;
    }

    private static String normalizeOcrText(String raw) {
        if (raw == null) return "";
        return raw.replace("\r", "\n").replaceAll("[ \\t]+", " ").trim();
    }

    private static Context getAppContext() {
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        return svc == null ? null : svc.getApplicationContext();
    }

    private void putFailure(OperationContext ctx, MetaOperation obj, String reason, String message, String text) {
        HashMap<String, Object> response = new HashMap<>();
        response.put(MetaOperation.MATCHED, false);
        response.put(MetaOperation.RESULT, text == null ? "" : text);
        response.put(MetaOperation.OCR_TEXT, text == null ? "" : text);
        response.put(MetaOperation.OCR_CONFIDENCE, 0L);
        response.put("reason", reason);
        response.put("error", message == null ? "" : message);
        ctx.currentResponse = response;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
    }

    private static List<Integer> parseBbox(Object raw) {
        if (!(raw instanceof List)) return null;
        List<?> values = (List<?>) raw;
        if (values.size() < 4) return null;
        try {
            int x = toInt(values.get(0));
            int y = toInt(values.get(1));
            int w = Math.max(1, toInt(values.get(2)));
            int h = Math.max(1, toInt(values.get(3)));
            return Arrays.asList(x, y, w, h);
        } catch (Exception e) {
            return null;
        }
    }

    private static int toInt(Object raw) {
        if (raw instanceof Number) return ((Number) raw).intValue();
        return Integer.parseInt(String.valueOf(raw).trim());
    }

    private static String getString(Map<String, Object> inputMap, String key, String def) {
        if (inputMap == null || inputMap.get(key) == null) return def;
        String text = String.valueOf(inputMap.get(key)).trim();
        return TextUtils.isEmpty(text) ? def : text;
    }

    private static int parseInt(Object raw, int def, int min, int max) {
        int value = def;
        if (raw instanceof Number) {
            value = ((Number) raw).intValue();
        } else if (raw instanceof String) {
            try {
                value = Integer.parseInt(((String) raw).trim());
            } catch (Exception ignored) {
            }
        }
        return Math.max(min, Math.min(max, value));
    }

    private static long parseLong(Object raw, long def, long min, long max) {
        long value = def;
        if (raw instanceof Number) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof String) {
            try {
                value = Long.parseLong(((String) raw).trim());
            } catch (Exception ignored) {
            }
        }
        return Math.max(min, Math.min(max, value));
    }

    private static float parseFloat(Object raw, float def, float min, float max) {
        float value = def;
        if (raw instanceof Number) {
            value = ((Number) raw).floatValue();
        } else if (raw instanceof String) {
            try {
                value = Float.parseFloat(((String) raw).trim());
            } catch (Exception ignored) {
            }
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int parsePageSegMode(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(text) || "auto".equals(text)) return 3;
        if ("block".equals(text) || "single_block".equals(text)) return 6;
        if ("line".equals(text) || "single_line".equals(text)) return 7;
        if ("word".equals(text) || "single_word".equals(text)) return 8;
        if ("sparse".equals(text) || "sparse_text".equals(text)) return 11;
        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return 3;
        }
    }

    private static void recycleIfNeeded(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private static final class OcrResult {
        final String text;
        final int confidence;

        OcrResult(String text, int confidence) {
            this.text = text;
            this.confidence = confidence;
        }
    }

    public static final class OcrPreviewResult {
        public final boolean success;
        public final String text;
        public final int confidence;
        public final String reason;
        public final String message;

        private OcrPreviewResult(boolean success, String text, int confidence, String reason, String message) {
            this.success = success;
            this.text = text == null ? "" : text;
            this.confidence = confidence;
            this.reason = reason == null ? "" : reason;
            this.message = message == null ? "" : message;
        }

        static OcrPreviewResult success(String text, int confidence, String reason) {
            return new OcrPreviewResult(true, text, confidence, reason, "");
        }

        static OcrPreviewResult failure(String reason, String message, String text, int confidence) {
            return new OcrPreviewResult(false, text, confidence, reason, message);
        }
    }

    private static final class TessDataResult {
        final boolean ready;
        final String dataPath;
        final String message;

        private TessDataResult(boolean ready, String dataPath, String message) {
            this.ready = ready;
            this.dataPath = dataPath;
            this.message = message;
        }

        static TessDataResult ready(String dataPath) {
            return new TessDataResult(true, dataPath, "");
        }

        static TessDataResult missing(String message, String dataPath) {
            return new TessDataResult(false, dataPath, message);
        }
    }
}
