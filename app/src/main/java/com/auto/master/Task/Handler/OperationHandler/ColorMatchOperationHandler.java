package com.auto.master.Task.Handler.OperationHandler;

import android.graphics.Color;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.auto.master.Task.Operation.ColorMatchOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.capture.ScreenCaptureManager;
import com.auto.master.utils.AdaptivePollingController;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ColorMatchOperationHandler extends OperationHandler {

    private static final String TAG = "ColorMatchHandler";
    // 单点颜色读取与区域找色统一走 byte[]，避免不同 JNI 路径造成行为不一致
    private static final ThreadLocal<byte[]> sPixelBuf = new ThreadLocal<byte[]>() {
        @Override protected byte[] initialValue() { return new byte[4]; }
    };
    ColorMatchOperationHandler() {
        this.setType(18);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        ctx.currentOperation = obj;
        ColorMatchOperation operation = (ColorMatchOperation) obj;
        Map<String, Object> inputMap = operation.getInputMap();
        List<PointRule> rules = parseRules(inputMap == null ? null : inputMap.get(MetaOperation.COLOR_POINTS));
        if (rules.isEmpty()) {
            ctx.currentResponse = buildResult(false, "no_rules", null, new ArrayList<>());
            ctx.lastOperation = obj;
            return true;
        }

        long timeoutMs = parseLong(inputMap == null ? null : inputMap.get(MetaOperation.MATCHTIMEOUT), 5000L, 1L, 60_000L);
        long preDelayMs = parseLong(inputMap == null ? null : inputMap.get(MetaOperation.MATCH_PRE_DELAY_MS), 0L, 0L, 5000L);
        String matchMode = getString(inputMap, MetaOperation.COLOR_MATCH_MODE, MetaOperation.COLOR_MATCH_MODE_ALL);
        boolean anyMode = MetaOperation.COLOR_MATCH_MODE_ANY.equalsIgnoreCase(matchMode);

        if (preDelayMs > 0L) {
            SystemClock.sleep(preDelayMs);
        }

        boolean matched = false;
        List<Integer> matchedPoint = null;
        // 在轮询循环外预分配结果数组，避免每次迭代在内层循环调用 toMap() 分配 HashMap。
        // toMap() 只在循环退出后调用一次，将最后一次评估结果转为响应 Map。
        final int ruleCount = rules.size();
        PointMatchResult[] lastResults = new PointMatchResult[ruleCount];
        // Always use the full frame — single-point ROIs collapse at scale<1.0 causing
        // sanitizeRoi() to return null while captureRoi is still non-null, which makes
        // evaluate() subtract the wrong offset and read pixel (0,0) instead of the target.
        long start = System.currentTimeMillis();
        AdaptivePollingController pollingController = AdaptivePollingController.forColorCheck();
        while (System.currentTimeMillis() - start < timeoutMs) {
            long loopStartMs = SystemClock.uptimeMillis();
            Mat screenMat = pollingController.acquireFrame();
            if (screenMat == null || screenMat.empty()) {
                pollingController.onMiss();
                pollingController.sleepUntilNextIteration(loopStartMs);
                continue;
            }
            if (!pollingController.hasFreshFrame()) {
                pollingController.sleepUntilNextIteration(loopStartMs);
                continue;
            }

            int matchedCount = 0;
            List<Integer> firstMatchedPoint = null;
            for (int i = 0; i < ruleCount; i++) {
                PointRule rule = rules.get(i);
                PointMatchResult result = evaluate(screenMat, rule);
                lastResults[i] = result;  // 保存本轮结果，用于循环结束后构建响应
                if (result.matched) {
                    matchedCount++;
                    if (firstMatchedPoint == null) {
                        firstMatchedPoint = new ArrayList<>(2);
                        firstMatchedPoint.add(rule.x);
                        firstMatchedPoint.add(rule.y);
                    }
                } else if (!anyMode) {
                    firstMatchedPoint = null;
                }
            }

            if ((anyMode && matchedCount > 0) || (!anyMode && matchedCount == ruleCount)) {
                matched = true;
                matchedPoint = firstMatchedPoint;
                pollingController.onHit();
                break;
            }
            pollingController.onMiss();
            pollingController.sleepUntilNextIteration(loopStartMs);
        }

        // 循环结束后一次性构建 pointResults（避免在热路径中反复 new HashMap）
        List<Map<String, Object>> pointResults = new ArrayList<>(ruleCount);
        for (PointMatchResult r : lastResults) {
            if (r != null) pointResults.add(r.toMap());
        }

        if (matchedPoint != null) {
            AutoAccessibilityService svc = AutoAccessibilityService.get();
            if (svc != null && matchedPoint.size() >= 2) {
                int x = matchedPoint.get(0);
                int y = matchedPoint.get(1);
                getMainHandler().post(() -> svc.showClickFeedback(x, y, 220));
            }
        }

        ctx.currentResponse = buildResult(matched, matched ? "matched" : "timeout", matchedPoint, pointResults);
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    private Map<String, Object> buildResult(boolean matched, String reason, List<Integer> matchedPoint, List<Map<String, Object>> pointResults) {
        Map<String, Object> result = new HashMap<>();
        result.put(MetaOperation.MATCHED, matched);
        result.put("reason", reason);
        if (matchedPoint != null) {
            result.put(MetaOperation.RESULT, matchedPoint);
            result.put(MetaOperation.CLICK_TARGET, matchedPoint.get(0) + "," + matchedPoint.get(1));
        } else {
            result.put(MetaOperation.RESULT, null);
        }
        result.put("pointResults", pointResults);
        return result;
    }

    private PointMatchResult evaluate(Mat screenMat, PointRule rule) {
        // rule.x/y are screen coordinates; screenMat is the full capture-scale frame.
        // Use actual per-axis scale (corrects 16-byte alignment offset vs CAPTURE_SCALE).
        ScreenCaptureManager mgr = ScreenCaptureManager.getInstance();
        int localX = (int)(rule.x * mgr.getActualScaleX());
        int localY = (int)(rule.y * mgr.getActualScaleY());
        if (localX < 0 || localY < 0 || localX >= screenMat.cols() || localY >= screenMat.rows()) {
            return new PointMatchResult(rule, false, 255, Color.TRANSPARENT);
        }
        byte[] buf = sPixelBuf.get();
        int got = screenMat.get(localY, localX, buf);
        if (got < 3) {
            return new PointMatchResult(rule, false, 255, Color.TRANSPARENT);
        }
        int actualColor = Color.rgb(
                clampColor(buf[0] & 0xFF),
                clampColor(buf[1] & 0xFF),
                clampColor(buf[2] & 0xFF));
        int diff = computeMaxChannelDiff(rule.color, actualColor);
        return new PointMatchResult(rule, diff <= rule.tolerance, diff, actualColor);
    }

    private int computeMaxChannelDiff(int expected, int actual) {
        int dr = Math.abs(Color.red(expected) - Color.red(actual));
        int dg = Math.abs(Color.green(expected) - Color.green(actual));
        int db = Math.abs(Color.blue(expected) - Color.blue(actual));
        return Math.max(dr, Math.max(dg, db));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private List<PointRule> parseRules(Object raw) {
        List<PointRule> rules = new ArrayList<>();
        if (!(raw instanceof List)) {
            return rules;
        }
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;
            int x = parseInt(map.get("x"), -1);
            int y = parseInt(map.get("y"), -1);
            int tolerance = parseInt(map.get(MetaOperation.COLOR_TOLERANCE), 12);
            String colorText = map.get(MetaOperation.COLOR_VALUE) == null ? "" : String.valueOf(map.get(MetaOperation.COLOR_VALUE));
            Integer color = parseColor(colorText);
            if (x < 0 || y < 0 || color == null) {
                continue;
            }
            rules.add(new PointRule(x, y, color, Math.max(0, Math.min(255, tolerance))));
        }
        return rules;
    }

    private Integer parseColor(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        try {
            return Color.parseColor(raw.trim());
        } catch (Exception e) {
            Log.w(TAG, "parseColor failed: " + raw, e);
            return null;
        }
    }

    private int parseInt(Object raw, int def) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            try {
                return Integer.parseInt(((String) raw).trim());
            } catch (Exception ignored) {
            }
        }
        return def;
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

    private String getString(Map<String, Object> map, String key, String def) {
        if (map == null) {
            return def;
        }
        Object value = map.get(key);
        if (value == null) {
            return def;
        }
        String text = String.valueOf(value).trim();
        return TextUtils.isEmpty(text) ? def : text;
    }

    private static class PointRule {
        final int x;
        final int y;
        final int color;
        final int tolerance;

        PointRule(int x, int y, int color, int tolerance) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.tolerance = tolerance;
        }
    }

    private static class PointMatchResult {
        final PointRule rule;
        final boolean matched;
        final int diff;
        final int actualColor;

        PointMatchResult(PointRule rule, boolean matched, int diff, int actualColor) {
            this.rule = rule;
            this.matched = matched;
            this.diff = diff;
            this.actualColor = actualColor;
        }

        Map<String, Object> toMap() {
            Map<String, Object> item = new HashMap<>();
            item.put("x", rule.x);
            item.put("y", rule.y);
            item.put(MetaOperation.COLOR_VALUE, String.format("#%06X", 0xFFFFFF & rule.color));
            item.put("actualColor", String.format("#%06X", 0xFFFFFF & actualColor));
            item.put(MetaOperation.COLOR_TOLERANCE, rule.tolerance);
            item.put("diff", diff);
            item.put(MetaOperation.MATCHED, matched);
            return item;
        }
    }
}
