package com.auto.master.Task.Handler.OperationHandler;

import android.text.TextUtils;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VariableRuntimeUtils {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_]+)\\}");

    private VariableRuntimeUtils() {
    }

    static String getString(Map<String, Object> inputMap, String key, String def) {
        if (inputMap == null) {
            return def;
        }
        Object value = inputMap.get(key);
        if (value == null) {
            return def;
        }
        return String.valueOf(value);
    }

    static Object resolveByMode(OperationContext ctx, Map<String, Object> inputMap, String modeKey,
                                String valueKey, String typeKey) {
        String mode = getString(inputMap, modeKey, "literal").trim().toLowerCase();
        String raw = getString(inputMap, valueKey, "");
        switch (mode) {
            case "variable":
                return ctx == null || ctx.variables == null ? null : ctx.variables.get(raw);
            case "response":
                return ctx == null || ctx.currentResponse == null ? null : ctx.currentResponse.get(raw);
            case "literal":
            default:
                return coerceLiteral(raw, getString(inputMap, typeKey, "auto"));
        }
    }

    static Object coerceLiteral(String raw, String type) {
        String safeType = type == null ? "auto" : type.trim().toLowerCase();
        String safeRaw = raw == null ? "" : raw;
        switch (safeType) {
            case "string":
                return safeRaw;
            case "number":
                Double num = toDouble(safeRaw);
                return num == null ? 0L : normalizeNumber(num);
            case "boolean":
                return toBoolean(safeRaw);
            case "auto":
            default:
                if (TextUtils.isEmpty(safeRaw)) {
                    return "";
                }
                Double parsed = toDouble(safeRaw);
                if (parsed != null) {
                    return normalizeNumber(parsed);
                }
                if ("true".equalsIgnoreCase(safeRaw) || "false".equalsIgnoreCase(safeRaw)) {
                    return toBoolean(safeRaw);
                }
                return safeRaw;
        }
    }

    static Double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0d;
        }
        if (value instanceof String) {
            String v = ((String) value).trim().toLowerCase();
            return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v);
        }
        return false;
    }

    static Object normalizeNumber(double value) {
        long longValue = (long) value;
        if (Math.abs(value - longValue) < 0.0000001d) {
            return longValue;
        }
        return value;
    }

    static String applyTemplate(String template, Map<String, Object> variables) {
        if (template == null) {
            return "";
        }
        // Fast-path: no placeholder marker → return as-is
        if (template.indexOf("${") < 0) {
            return template;
        }
        if (variables == null || variables.isEmpty()) {
            // Reuse static pattern instead of recompiling via String.replaceAll
            return TEMPLATE_PATTERN.matcher(template).replaceAll("");
        }
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    static void putCommonResult(OperationContext ctx, MetaOperation obj, Object result) {
        java.util.HashMap<String, Object> response = new java.util.HashMap<>();
        response.put(MetaOperation.RESULT, result);
        ctx.currentResponse = response;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
    }
}
