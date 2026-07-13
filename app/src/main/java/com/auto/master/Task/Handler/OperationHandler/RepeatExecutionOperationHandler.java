package com.auto.master.Task.Handler.OperationHandler;

import android.text.TextUtils;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Operation.RepeatExecutionOperation;

import java.util.HashMap;
import java.util.Map;

public class RepeatExecutionOperationHandler extends OperationHandler {

    RepeatExecutionOperationHandler() {
        this.setType(32);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (!(obj instanceof RepeatExecutionOperation) || ctx == null) {
            return false;
        }
        Map<String, Object> input = obj.getInputMap();
        String startId = stringValue(input, MetaOperation.REPEAT_START_OPERATION_ID);
        boolean infinite = booleanValue(input, MetaOperation.REPEAT_INFINITE);
        int count = positiveInt(input, MetaOperation.REPEAT_COUNT, 2);
        if (TextUtils.isEmpty(startId)) {
            return false;
        }

        Map<String, Object> response = new HashMap<>();
        response.put(MetaOperation.REPEAT_START_OPERATION_ID, startId);
        response.put(MetaOperation.REPEAT_INFINITE, infinite);
        response.put(MetaOperation.REPEAT_COUNT, count);
        ctx.currentResponse = response;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    private static String stringValue(Map<String, Object> input, String key) {
        if (input == null || input.get(key) == null) {
            return "";
        }
        return String.valueOf(input.get(key)).trim();
    }

    private static boolean booleanValue(Map<String, Object> input, String key) {
        if (input == null || input.get(key) == null) {
            return false;
        }
        Object value = input.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static int positiveInt(Map<String, Object> input, String key, int fallback) {
        if (input == null || input.get(key) == null) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(input.get(key)).trim()));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
