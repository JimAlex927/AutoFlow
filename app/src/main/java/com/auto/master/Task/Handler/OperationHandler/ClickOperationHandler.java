package com.auto.master.Task.Handler.OperationHandler;

import android.util.Log;

import com.auto.master.Task.Operation.ClickOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.model.Point;
import com.auto.master.auto.AutoAccessibilityService;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * click handler.
 * Default behavior now prefers a fast path: continue after the gesture is accepted
 * and a short settle delay, instead of blocking until the full accessibility callback.
 */
public class ClickOperationHandler extends OperationHandler {
    private static final String CLICK_TAG = "ClickOperationHandler";
    private static final long FAST_DISPATCH_TIMEOUT_MS = 250L;
    private static final long FAST_SETTLE_MS = 32L;
    private static final long STRICT_WAIT_TIMEOUT_MS = 3000L;
    private static final int MAX_CLICK_RETRY_COUNT = 5;
    // 静态缓存：避免 extractNumbers() 每次调用都重新编译正则
    private static final Pattern COORD_PATTERN = Pattern.compile("(-?\\d+)\\D+(-?\\d+)");

    private static class ClickConfig {
        final boolean fastMode;
        final long settleMs;
        final long timeoutMs;

        ClickConfig(boolean fastMode, long settleMs, long timeoutMs) {
            this.fastMode = fastMode;
            this.settleMs = settleMs;
            this.timeoutMs = timeoutMs;
        }
    }

    private static class ClickResult {
        final Object lock = new Object();
        final boolean[] dispatched = {false};
        final boolean[] accepted = {false};
        final boolean[] completed = {false};
        final boolean[] ok = {false};
    }

    ClickOperationHandler() {
        this.setType(1);
    }

    /**
     * Parse click target strings like "(100,200)", "100,200", "x:100,y:200".
     */
    public static int[] extractNumbers(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = COORD_PATTERN.matcher(str.trim());

        if (matcher.find()) {
            try {
                int num1 = Integer.parseInt(matcher.group(1));
                int num2 = Integer.parseInt(matcher.group(2));
                return new int[]{num1, num2};
            } catch (NumberFormatException e) {
                Log.w(CLICK_TAG, "click target parse failed: " + e.getMessage());
                return null;
            }
        } else {
            Log.w(CLICK_TAG, "click target format invalid, expected forms like 100,200 or (100,200)");
            return null;
        }
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        ClickOperation clickOperation = (ClickOperation) obj;
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            return false;
        }

        Map<String, Object> inputMap = clickOperation.getInputMap();
        Point p = resolvePoint(inputMap == null ? null : inputMap.get(MetaOperation.CLICK_TARGET));
        if (p == null) {
            return false;
        }

        ClickResult result = new ClickResult();
        ClickConfig config = resolveClickConfig(inputMap);

        getMainHandler().post(() -> {
            svc.showClickFeedback((int) p.x, (int) p.y, 280);
            boolean accepted = svc.clickWithRetry((int) p.x, (int) p.y,
                    () -> {
                        notifyCompletion(result, true);
                        Log.d(CLICK_TAG, "click completed");
                    },
                    () -> {
                        notifyCompletion(result, false);
                        Log.w(CLICK_TAG, "click cancelled");
                    },
                    MAX_CLICK_RETRY_COUNT);
            notifyDispatch(result, accepted);
        });

        boolean ok = config.fastMode
                ? waitForFastResult(result, config.settleMs)
                : waitForStrictResult(result, config.timeoutMs);

        Integer responseType = obj.getResponseType();
        if (responseType != null && responseType == 1) {
            Map<String, Object> res = new HashMap<>();
            res.put(MetaOperation.RESULT, result);
            ctx.currentResponse = res;
            ctx.lastOperation = obj;
        }

        return ok;
    }

    private Point resolvePoint(Object target) {
        if (target instanceof Point) {
            return (Point) target;
        }
        if (target instanceof String) {
            int[] cords = extractNumbers((String) target);
            if (cords == null || cords.length != 2) {
                return null;
            }
            return new Point(cords[0], cords[1]);
        }
        return null;
    }

    private ClickConfig resolveClickConfig(Map<String, Object> inputMap) {
        String mode = stringValue(inputMap == null ? null : inputMap.get(MetaOperation.CLICK_EXECUTION_MODE));
        boolean fastMode = !MetaOperation.CLICK_MODE_STRICT.equalsIgnoreCase(mode);
        long settleMs = positiveLong(inputMap == null ? null : inputMap.get(MetaOperation.CLICK_SETTLE_MS), FAST_SETTLE_MS);
        long timeoutMs = positiveLong(inputMap == null ? null : inputMap.get(MetaOperation.CLICK_WAIT_TIMEOUT_MS), STRICT_WAIT_TIMEOUT_MS);
        return new ClickConfig(fastMode, settleMs, timeoutMs);
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private long positiveLong(Object raw, long fallback) {
        if (raw instanceof Number) {
            long value = ((Number) raw).longValue();
            return value > 0 ? value : fallback;
        }
        if (raw instanceof String) {
            try {
                long value = Long.parseLong(((String) raw).trim());
                return value > 0 ? value : fallback;
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private void notifyDispatch(ClickResult result, boolean accepted) {
        synchronized (result.lock) {
            if (result.dispatched[0]) {
                return;
            }
            result.dispatched[0] = true;
            result.accepted[0] = accepted;
            result.lock.notifyAll();
        }
    }

    private void notifyCompletion(ClickResult result, boolean success) {
        synchronized (result.lock) {
            if (result.completed[0]) {
                return;
            }
            result.ok[0] = success;
            result.completed[0] = true;
            result.lock.notifyAll();
        }
    }

    private boolean waitForFastResult(ClickResult result, long settleMs) {
        if (!waitForDispatch(result, FAST_DISPATCH_TIMEOUT_MS) || !result.accepted[0]) {
            return false;
        }
        boolean completed = waitForCompletion(result, settleMs);
        if (completed) {
            return result.ok[0];
        }
        return true;
    }

    private boolean waitForStrictResult(ClickResult result, long timeoutMs) {
        if (!waitForDispatch(result, timeoutMs) || !result.accepted[0]) {
            return false;
        }
        return waitForCompletion(result, timeoutMs) && result.ok[0];
    }

    private boolean waitForDispatch(ClickResult result, long timeoutMs) {
        long start = System.currentTimeMillis();
        synchronized (result.lock) {
            while (!result.dispatched[0]) {
                if (Thread.currentThread().isInterrupted()) {
                    Log.d(CLICK_TAG, "click dispatch wait interrupted");
                    return false;
                }
                long left = timeoutMs - (System.currentTimeMillis() - start);
                if (left <= 0) {
                    break;
                }
                try {
                    result.lock.wait(left);
                } catch (InterruptedException e) {
                    Log.d(CLICK_TAG, "click dispatch wait interrupted", e);
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return result.dispatched[0];
    }

    private boolean waitForCompletion(ClickResult result, long timeoutMs) {
        long start = System.currentTimeMillis();
        synchronized (result.lock) {
            while (!result.completed[0]) {
                if (Thread.currentThread().isInterrupted()) {
                    Log.d(CLICK_TAG, "click completion wait interrupted");
                    return false;
                }
                long left = timeoutMs - (System.currentTimeMillis() - start);
                if (left <= 0) {
                    break;
                }
                try {
                    result.lock.wait(left);
                } catch (InterruptedException e) {
                    Log.d(CLICK_TAG, "click completion wait interrupted", e);
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return result.completed[0];
    }
}
