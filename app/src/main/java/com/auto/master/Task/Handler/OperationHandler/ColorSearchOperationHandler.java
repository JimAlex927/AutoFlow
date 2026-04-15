package com.auto.master.Task.Handler.OperationHandler;

import android.graphics.Color;
import android.os.SystemClock;
import android.text.TextUtils;

import com.auto.master.Task.Operation.ColorSearchOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.capture.ScreenCapture;
import com.auto.master.capture.ScreenCaptureManager;
import com.auto.master.utils.AdaptivePollingController;

import org.opencv.core.Mat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ColorSearchOperationHandler extends OperationHandler {

    // 在类顶部添加：复用 buffer，避免 GC
    private static final ThreadLocal<byte[]> sPixelBuf = new ThreadLocal<>();

    ColorSearchOperationHandler() {
        this.setType(19);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        ctx.currentOperation = obj;
        ColorSearchOperation operation = (ColorSearchOperation) obj;
        Map<String, Object> inputMap = operation.getInputMap();

        List<Integer> bbox = parseBbox(inputMap == null ? null : inputMap.get(MetaOperation.BBOX));
        Integer targetColor = parseColor(inputMap == null ? null : inputMap.get(MetaOperation.COLOR_VALUE));
        int tolerance = parseInt(inputMap == null ? null : inputMap.get(MetaOperation.COLOR_TOLERANCE), 18, 0, 255);
        int minPixels = parseInt(inputMap == null ? null : inputMap.get(MetaOperation.COLOR_SEARCH_MIN_PIXELS), 60, 1, Integer.MAX_VALUE);
        long timeoutMs = parseLong(inputMap == null ? null : inputMap.get(MetaOperation.MATCHTIMEOUT), 5000L, 1L, 60_000L);
        long preDelayMs = parseLong(inputMap == null ? null : inputMap.get(MetaOperation.MATCH_PRE_DELAY_MS), 0L, 0L, 5000L);

        if (bbox == null || targetColor == null) {
            ctx.currentResponse = buildResult(false, 0, null, null);
            ctx.lastOperation = obj;
            return true;
        }

        if (preDelayMs > 0L) {
            SystemClock.sleep(preDelayMs);
        }

        boolean matched = false;
        int matchedPixelCount = 0;
        List<Integer> matchedBbox = null;
        android.graphics.Rect captureRoi = new android.graphics.Rect(
                bbox.get(0),
                bbox.get(1),
                bbox.get(0) + bbox.get(2),
                bbox.get(1) + bbox.get(3));
        List<Integer> localBbox = Arrays.asList(0, 0, captureRoi.width(), captureRoi.height());

        long start = System.currentTimeMillis();
        AdaptivePollingController pollingController = AdaptivePollingController.forColorCheck();
        while (System.currentTimeMillis() - start < timeoutMs) {
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

            SearchResult result = scanRegion(screenMat, localBbox, targetColor, tolerance, minPixels,
                    captureRoi.left, captureRoi.top);
            matchedPixelCount = result.matchedPixels;
            matchedBbox = result.matchedBbox;
            if (result.matched) {
                matched = true;
                pollingController.onHit();
                break;
            }
            pollingController.onMiss();
            pollingController.sleepUntilNextIteration(loopStartMs);
        }

        if (matched && matchedBbox != null) {
            AutoAccessibilityService svc = AutoAccessibilityService.get();
            if (svc != null && matchedBbox.size() >= 4) {
                int x = matchedBbox.get(0);
                int y = matchedBbox.get(1);
                int w = matchedBbox.get(2);
                int h = matchedBbox.get(3);
                getMainHandler().post(() -> svc.showRectFeedback(x, y, w, h, 420, 0x00000000, 0, 0x44D84315));
            }
        }

        ctx.currentResponse = buildResult(matched, matchedPixelCount, bbox, matchedBbox);
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    private Map<String, Object> buildResult(boolean matched, int matchedPixels, List<Integer> searchBbox, List<Integer> matchedBbox) {
        Map<String, Object> result = new HashMap<>();
        result.put(MetaOperation.MATCHED, matched);
        result.put("matchedPixels", matchedPixels);
        if (searchBbox != null) {
            result.put("searchBbox", searchBbox);
        }
        if (matchedBbox != null) {
            result.put(MetaOperation.BBOX, matchedBbox);
            result.put(MetaOperation.RESULT, matchedBbox);
        } else {
            result.put(MetaOperation.RESULT, null);
        }
        return result;
    }



    // ★ 核心优化：整个 ROI 一次性读入，然后纯算术扫描
    private SearchResult scanRegion(Mat screenMat, List<Integer> rawBbox,
                                    int targetColor, int tolerance, int minPixels,
                                    int offsetX, int offsetY) {
        // ① 边界夹紧
        int x = Math.max(0, rawBbox.get(0));
        int y = Math.max(0, rawBbox.get(1));
        int w = Math.max(1, rawBbox.get(2));
        int h = Math.max(1, rawBbox.get(3));
        if (x >= screenMat.cols() || y >= screenMat.rows()) {
            return new SearchResult(false, 0, null);
        }
        w = Math.min(w, screenMat.cols() - x);
        h = Math.min(h, screenMat.rows() - y);
        if (w <= 0 || h <= 0) return new SearchResult(false, 0, null);

        int ch = screenMat.channels();          // 通常 3(BGR) 或 4(BGRA)
        int totalBytes = w * h * ch;

        // ② 复用 byte 数组，避免每帧 new
        byte[] buf = sPixelBuf.get();
        if (buf == null || buf.length < totalBytes) {
            buf = new byte[totalBytes];
            sPixelBuf.set(buf);
        }

        // ③ 关键：整个 ROI 只做 1 次 JNI 调用
        screenMat.submat(y, y + h, x, x + w).get(0, 0, buf);

        // ④ 提取目标色三通道（OpenCV 默认 BGR 顺序）
        // Color.parseColor 返回 ARGB；OpenCV 存 BGR(A)
        int tR = (targetColor >> 16) & 0xFF;
        int tG = (targetColor >> 8)  & 0xFF;
        int tB =  targetColor        & 0xFF;

        // ⑤ 纯 byte[] 算术扫描，完全无对象分配
        int matchedPixels = 0;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = -1, maxY = -1;

        // 使用实际轴向缩放系数（修正 16-byte 对齐后 captureSize ≠ screenSize × CAPTURE_SCALE）
        ScreenCaptureManager mgr = ScreenCaptureManager.getInstance();
        float scaleX   = mgr.getActualScaleX();
        float scaleY   = mgr.getActualScaleY();
        float invScaleX = scaleX > 0f ? 1.0f / scaleX : 1.0f;
        float invScaleY = scaleY > 0f ? 1.0f / scaleY : 1.0f;
        int capOriginX = (int)(offsetX * scaleX);   // 与 sanitizeRoi 的 left 计算一致
        int capOriginY = (int)(offsetY * scaleY);   // 与 sanitizeRoi 的 top 计算一致

        for (int row = 0; row < h; row++) {
            int rowBase = row * w * ch;
            for (int col = 0; col < w; col++) {
                int base = rowBase + col * ch;
                // OpenCV BGR → 对应 R=base+2, G=base+1, B=base+0
                int dr = Math.abs(tR - (buf[base    ] & 0xFF));  // base+0 = R
                if (dr > tolerance) continue;           // 提前剪枝 R 通道
                int dg = Math.abs(tG - (buf[base + 1] & 0xFF));  // base+1 = G
                if (dg > tolerance) continue;           // 提前剪枝 G 通道
                int db = Math.abs(tB - (buf[base + 2] & 0xFF));  // base+2 = B
                // max-channel-diff 判定
                int diff = dr > dg ? (dr > db ? dr : db) : (dg > db ? dg : db);
                if (diff > tolerance) continue;

                matchedPixels++;
                // (x+col, y+row) 是相对于 submat 的 capture 偏移，
                // 加上 capture 起点后除以 scale 得到绝对 screen 坐标。
                int px = (int)((capOriginX + x + col) * invScaleX);
                int py = (int)((capOriginY + y + row) * invScaleY);
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;

                // ⑥ 提前退出：一旦满足 minPixels 且不需要精确 bbox 可解注释
                // if (matchedPixels >= minPixels && /* bbox not needed */ false) break outer;
            }
        }

        boolean matched = matchedPixels >= minPixels;
        List<Integer> matchedBbox = null;
        if (matchedPixels > 0 && minX <= maxX && minY <= maxY) {
            matchedBbox = Arrays.asList(minX, minY, maxX - minX + 1, maxY - minY + 1);
        }
        return new SearchResult(matched, matchedPixels, matchedBbox);
    }
    private List<Integer> parseBbox(Object raw) {
        if (!(raw instanceof List)) {
            return null;
        }
        List<?> values = (List<?>) raw;
        if (values.size() < 4) {
            return null;
        }
        try {
            return Arrays.asList(
                    toInt(values.get(0)),
                    toInt(values.get(1)),
                    toInt(values.get(2)),
                    toInt(values.get(3)));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseColor(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        try {
            return Color.parseColor(text.startsWith("#") ? text : "#" + text);
        } catch (Exception e) {
            return null;
        }
    }

    private int toInt(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        return Integer.parseInt(String.valueOf(raw).trim());
    }

    private int parseInt(Object raw, int def, int min, int max) {
        int value = def;
        if (raw instanceof Number) {
            value = ((Number) raw).intValue();
        } else if (raw instanceof String) {
            try {
                value = Integer.parseInt(((String) raw).trim());
            } catch (Exception ignored) {
            }
        }
        value = Math.max(min, value);
        value = Math.min(max, value);
        return value;
    }

    private long parseLong(Object raw, long def, long min, long max) {
        long value = def;
        if (raw instanceof Number) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof String) {
            try {
                value = Long.parseLong(((String) raw).trim());
            } catch (Exception ignored) {
            }
        }
        value = Math.max(min, value);
        value = Math.min(max, value);
        return value;
    }

    private static class SearchResult {
        final boolean matched;
        final int matchedPixels;
        final List<Integer> matchedBbox;

        SearchResult(boolean matched, int matchedPixels, List<Integer> matchedBbox) {
            this.matched = matched;
            this.matchedPixels = matchedPixels;
            this.matchedBbox = matchedBbox;
        }
    }
}
