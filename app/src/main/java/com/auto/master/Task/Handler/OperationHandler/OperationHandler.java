package com.auto.master.Task.Handler.OperationHandler;

import android.os.Handler;
import android.os.Looper;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 任务：执行的最小单元的处理函数，策略模式。每一步可以看成是一个operationHandler
 */
public abstract class OperationHandler {

    private static final Handler MAIN = createMainHandler();

    private static Handler createMainHandler() {
        try {
            Looper looper = Looper.getMainLooper();
            return looper == null ? null : new Handler(looper);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Integer type = -1;
    public  Handler getMainHandler(){
        return MAIN;
    }

    /**
     * 任何一个继承的类都必须返回tupe表明这是什么类型的 operation
     * @return
     */
    public int getType(){

        return this.type;
    }

    public void setType(Integer type){
        this.type = type;
    }

    public abstract boolean  handle(MetaOperation obj, OperationContext ctx);

    public static long getConfiguredNodePreDelayMs(MetaOperation operation) {
        Map<String, Object> inputMap = operation == null ? null : operation.getInputMap();
        if (isNodePreDelayRandom(operation)) {
            return getNodePreDelayMaxMs(operation);
        }
        return parseDelayMs(
                inputMap == null ? null : inputMap.get(MetaOperation.NODE_PRE_DELAY_MS),
                MetaOperation.DEFAULT_NODE_PRE_DELAY_MS,
                MetaOperation.MAX_NODE_PRE_DELAY_MS);
    }

    public static long getNodePreDelayMinMs(MetaOperation operation) {
        Map<String, Object> inputMap = operation == null ? null : operation.getInputMap();
        long fallback = parseDelayMs(
                inputMap == null ? null : inputMap.get(MetaOperation.NODE_PRE_DELAY_MS),
                MetaOperation.DEFAULT_NODE_PRE_DELAY_MS,
                MetaOperation.MAX_NODE_PRE_DELAY_MS);
        return parseDelayMs(
                inputMap == null ? null : inputMap.get(MetaOperation.NODE_PRE_DELAY_MIN_MS),
                0L,
                MetaOperation.MAX_NODE_PRE_DELAY_MS);
    }

    public static long getNodePreDelayMaxMs(MetaOperation operation) {
        Map<String, Object> inputMap = operation == null ? null : operation.getInputMap();
        long fallback = parseDelayMs(
                inputMap == null ? null : inputMap.get(MetaOperation.NODE_PRE_DELAY_MS),
                MetaOperation.DEFAULT_NODE_PRE_DELAY_MS,
                MetaOperation.MAX_NODE_PRE_DELAY_MS);
        return parseDelayMs(
                inputMap == null ? null : inputMap.get(MetaOperation.NODE_PRE_DELAY_MAX_MS),
                fallback,
                MetaOperation.MAX_NODE_PRE_DELAY_MS);
    }

    public static boolean isNodePreDelayRandom(MetaOperation operation) {
        Map<String, Object> inputMap = operation == null ? null : operation.getInputMap();
        if (inputMap == null) {
            return false;
        }
        Object raw = inputMap.get(MetaOperation.NODE_PRE_DELAY_RANDOM);
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw instanceof String) {
            String text = ((String) raw).trim();
            return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
        }
        return false;
    }

    public static long resolveNodePreDelayMs(MetaOperation operation) {
        long configuredMs = getConfiguredNodePreDelayMs(operation);
        if (configuredMs <= 0L) {
            return 0L;
        }
        if (!isNodePreDelayRandom(operation)) {
            return configuredMs;
        }
        long minMs = getNodePreDelayMinMs(operation);
        long maxMs = getNodePreDelayMaxMs(operation);
        if (maxMs < minMs) {
            long tmp = minMs;
            minMs = maxMs;
            maxMs = tmp;
        }
        if (maxMs <= 0L) {
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(minMs, maxMs + 1L);
    }

    private static long parseDelayMs(Object raw, long def, long max) {
        long value = def;
        if (raw instanceof Number) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof String) {
            try {
                value = Long.parseLong(((String) raw).trim());
            } catch (Exception ignored) {
                value = def;
            }
        }
        return Math.max(0L, Math.min(value, max));
    }

}
