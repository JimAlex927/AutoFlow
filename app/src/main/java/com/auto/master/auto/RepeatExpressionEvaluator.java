package com.auto.master.auto;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluates the lightweight boolean condition used by a repeat-execution node. */
public final class RepeatExpressionEvaluator {
    private static final long TIMEOUT_MS = 250L;
    private static final ExpressionContextFactory CONTEXT_FACTORY = new ExpressionContextFactory();

    private RepeatExpressionEvaluator() {
    }

    public static boolean shouldContinue(String expression, Map<String, Object> variables) {
        return evaluate(expression, variables).shouldContinue;
    }

    public static EvaluationResult evaluate(String expression, Map<String, Object> variables) {
        String rawExpression = expression == null ? "" : expression.trim();
        if (rawExpression.isEmpty()) {
            return EvaluationResult.failure("表达式为空", "");
        }
        String missingPath = findMissingTemplatePath(rawExpression, variables);
        if (missingPath != null) {
            return EvaluationResult.failure("找不到变量: " + missingPath,
                    normalizeExpression(rawExpression, variables));
        }
        String source = normalizeExpression(rawExpression, variables);
        Context js = CONTEXT_FACTORY.enterContextWithTimeout(TIMEOUT_MS);
        try {
            Scriptable scope = js.initStandardObjects();
            Scriptable vars = js.newObject(scope);
            if (variables != null) {
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    String key = entry.getKey();
                    if (key == null || key.isEmpty()) {
                        continue;
                    }
                    Object value = toScriptValue(js, scope, entry.getValue());
                    ScriptableObject.putProperty(vars, key, value);
                    if (isJsIdentifier(key)) {
                        ScriptableObject.putProperty(scope, key, value);
                    }
                }
            }
            ScriptableObject.putProperty(scope, "vars", vars);
            boolean result = Context.toBoolean(
                    js.evaluateString(scope, source, "repeat_expression", 1, null));
            return EvaluationResult.success(result, source);
        } catch (Exception error) {
            return EvaluationResult.failure(compactError(error), source);
        } finally {
            Context.exit();
            CONTEXT_FACTORY.clearDeadline();
        }
    }

    /** Returns an empty string when the expression can be compiled. */
    public static String validateSyntax(String expression) {
        String rawExpression = expression == null ? "" : expression.trim();
        if (rawExpression.isEmpty()) {
            return "表达式为空";
        }
        String source = normalizeExpression(rawExpression, null);
        Context js = CONTEXT_FACTORY.enterContextWithTimeout(TIMEOUT_MS);
        try {
            js.compileString(source, "repeat_expression", 1, null);
            return "";
        } catch (Exception error) {
            return compactError(error);
        } finally {
            Context.exit();
            CONTEXT_FACTORY.clearDeadline();
        }
    }

    /** Executes the optional update that runs immediately before the next round starts. */
    public static UpdateResult executeNextRoundUpdate(
            String expression, Map<String, Object> variables) {
        String rawExpression = expression == null ? "" : expression.trim();
        if (rawExpression.isEmpty()) {
            return UpdateResult.success(null);
        }
        if (variables == null) {
            return UpdateResult.failure("运行变量池不可用");
        }
        String missingPath = findMissingTemplatePath(rawExpression, variables);
        if (missingPath != null) {
            return UpdateResult.failure("找不到变量: " + missingPath);
        }
        String source = normalizeExpression(rawExpression, variables);
        Context js = CONTEXT_FACTORY.enterContextWithTimeout(TIMEOUT_MS);
        try {
            Scriptable scope = js.initStandardObjects();
            Scriptable vars = js.newObject(scope);
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isEmpty()) {
                    continue;
                }
                ScriptableObject.putProperty(vars, key,
                        toScriptValue(js, scope, entry.getValue()));
            }
            ScriptableObject.putProperty(scope, "vars", vars);
            Object result = js.evaluateString(scope,
                    "with (vars) {\n" + source + "\n}",
                    "repeat_next_round_expression", 1, null);
            syncVarsObjectBack(variables, vars);
            return UpdateResult.success(toPlainJava(result));
        } catch (Exception error) {
            return UpdateResult.failure(compactError(error));
        } finally {
            Context.exit();
            CONTEXT_FACTORY.clearDeadline();
        }
    }

    /** Returns an empty string when the next-round update can be compiled. */
    public static String validateNextRoundUpdateSyntax(String expression) {
        String rawExpression = expression == null ? "" : expression.trim();
        if (rawExpression.isEmpty()) {
            return "";
        }
        String source = normalizeExpression(rawExpression, null);
        Context js = CONTEXT_FACTORY.enterContextWithTimeout(TIMEOUT_MS);
        try {
            js.compileString("with (vars) {\n" + source + "\n}",
                    "repeat_next_round_expression", 1, null);
            return "";
        } catch (Exception error) {
            return compactError(error);
        } finally {
            Context.exit();
            CONTEXT_FACTORY.clearDeadline();
        }
    }

    private static void syncVarsObjectBack(Map<String, Object> variables, Scriptable vars) {
        List<String> previousKeys = new ArrayList<>(variables.keySet());
        for (String key : previousKeys) {
            Object value = ScriptableObject.getProperty(vars, key);
            if (value == Scriptable.NOT_FOUND || value == Undefined.instance) {
                variables.remove(key);
            } else {
                variables.put(key, toPlainJava(value));
            }
        }
        for (Object id : vars.getIds()) {
            String key = String.valueOf(id);
            Object value = ScriptableObject.getProperty(vars, key);
            if (value != Scriptable.NOT_FOUND && value != Undefined.instance) {
                variables.put(key, toPlainJava(value));
            }
        }
    }

    private static Object toPlainJava(Object value) {
        if (value == null || value == Undefined.instance || value == Scriptable.NOT_FOUND) {
            return null;
        }
        if (value instanceof NativeJavaObject) {
            return toPlainJava(((NativeJavaObject) value).unwrap());
        }
        if (value instanceof NativeArray) {
            NativeArray array = (NativeArray) value;
            List<Object> items = new ArrayList<>();
            for (int index = 0; index < array.getLength(); index++) {
                items.add(toPlainJava(array.get(index, array)));
            }
            return items;
        }
        if (value instanceof NativeObject) {
            NativeObject object = (NativeObject) value;
            Map<String, Object> map = new LinkedHashMap<>();
            for (Object id : object.getIds()) {
                String key = String.valueOf(id);
                map.put(key, toPlainJava(object.get(key, object)));
            }
            return map;
        }
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            long integer = (long) number;
            if (Math.abs(number - integer) < 0.0000001d) {
                return Long.valueOf(integer);
            }
            return Double.valueOf(number);
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        return String.valueOf(value);
    }

    private static Object toScriptValue(Context js, Scriptable scope, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?>) {
            Scriptable object = js.newObject(scope);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                ScriptableObject.putProperty(object, String.valueOf(entry.getKey()),
                        toScriptValue(js, scope, entry.getValue()));
            }
            return object;
        }
        if (value instanceof Iterable<?>) {
            List<Object> items = new ArrayList<>();
            for (Object item : (Iterable<?>) value) {
                items.add(toScriptValue(js, scope, item));
            }
            return js.newArray(scope, items.toArray());
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] items = new Object[length];
            for (int i = 0; i < length; i++) {
                items[i] = toScriptValue(js, scope, Array.get(value, i));
            }
            return js.newArray(scope, items);
        }
        return Context.javaToJS(value, scope);
    }

    private static String normalizeExpression(String expression, Map<String, Object> variables) {
        String source = expression == null ? "" : expression.trim();
        StringBuilder output = new StringBuilder(source.length() + 16);
        int cursor = 0;
        while (cursor < source.length()) {
            int start = source.indexOf("${", cursor);
            if (start < 0) {
                output.append(source, cursor, source.length());
                break;
            }
            output.append(source, cursor, start);
            int end = source.indexOf('}', start + 2);
            if (end < 0) {
                output.append(source, start, source.length());
                break;
            }
            String path = source.substring(start + 2, end).trim();
            output.append(toJsVariableAccess(path, variables));
            cursor = end + 1;
        }
        return output.toString();
    }

    private static String toJsVariableAccess(String path, Map<String, Object> variables) {
        if (variables != null && variables.containsKey(path)) {
            return "vars[\"" + escapeJsString(path) + "\"]";
        }
        List<Object> tokens = parsePath(path);
        if (tokens == null || tokens.isEmpty()) {
            return "vars[\"" + escapeJsString(path) + "\"]";
        }
        StringBuilder output = new StringBuilder("vars");
        for (Object token : tokens) {
            if (token instanceof Integer) {
                output.append('[').append(token).append(']');
            } else {
                output.append("[\"").append(escapeJsString(String.valueOf(token))).append("\"]");
            }
        }
        return output.toString();
    }

    private static String findMissingTemplatePath(String expression, Map<String, Object> variables) {
        int cursor = 0;
        while (cursor < expression.length()) {
            int start = expression.indexOf("${", cursor);
            if (start < 0) {
                return null;
            }
            int end = expression.indexOf('}', start + 2);
            if (end < 0) {
                return null;
            }
            String path = expression.substring(start + 2, end).trim();
            if (!containsPath(variables, path)) {
                return path;
            }
            cursor = end + 1;
        }
        return null;
    }

    private static boolean isJsIdentifier(String value) {
        if (value == null || value.isEmpty() || !isPathSegmentStart(value.charAt(0))) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!isPathSegmentStart(current) && (current < '0' || current > '9')) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsPath(Map<String, Object> variables, String path) {
        if (variables == null) {
            return false;
        }
        if (variables.containsKey(path)) {
            return true;
        }
        List<Object> tokens = parsePath(path);
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }
        Object current = variables;
        for (Object token : tokens) {
            if (token instanceof Integer) {
                int index = (Integer) token;
                if (current instanceof List<?>) {
                    List<?> list = (List<?>) current;
                    if (index < 0 || index >= list.size()) {
                        return false;
                    }
                    current = list.get(index);
                } else if (current != null && current.getClass().isArray()) {
                    if (index < 0 || index >= Array.getLength(current)) {
                        return false;
                    }
                    current = Array.get(current, index);
                } else {
                    return false;
                }
            } else {
                if (!(current instanceof Map<?, ?>)) {
                    return false;
                }
                Map<?, ?> map = (Map<?, ?>) current;
                String key = String.valueOf(token);
                if (!map.containsKey(key)) {
                    return false;
                }
                current = map.get(key);
            }
        }
        return true;
    }

    private static List<Object> parsePath(String path) {
        if (path == null) {
            return null;
        }
        String source = path.trim();
        if (source.isEmpty()) {
            return null;
        }
        List<Object> tokens = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            if (source.charAt(index) == '[') {
                int digitStart = index + 1;
                int end = digitStart;
                while (end < source.length() && Character.isDigit(source.charAt(end))) {
                    end++;
                }
                if (end == digitStart || end >= source.length() || source.charAt(end) != ']') {
                    return null;
                }
                try {
                    tokens.add(Integer.parseInt(source.substring(digitStart, end)));
                } catch (NumberFormatException ignored) {
                    return null;
                }
                index = end + 1;
                if (index < source.length() && source.charAt(index) == '.') {
                    index++;
                    if (index >= source.length() || !isPathSegmentStart(source.charAt(index))) {
                        return null;
                    }
                } else if (index < source.length() && source.charAt(index) != '[') {
                    return null;
                }
                continue;
            }
            if (!isPathSegmentStart(source.charAt(index))) {
                return null;
            }
            int end = index + 1;
            while (end < source.length() && isPathSegmentPart(source.charAt(end))) {
                end++;
            }
            tokens.add(source.substring(index, end));
            index = end;
            if (index < source.length() && source.charAt(index) == '.') {
                index++;
                if (index >= source.length() || !isPathSegmentStart(source.charAt(index))) {
                    return null;
                }
            } else if (index < source.length() && source.charAt(index) != '[') {
                return null;
            }
        }
        return tokens;
    }

    private static boolean isPathSegmentStart(char value) {
        return value == '_' || value == '$'
                || (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z');
    }

    private static boolean isPathSegmentPart(char value) {
        return isPathSegmentStart(value)
                || (value >= '0' && value <= '9')
                || value == '-';
    }

    private static String escapeJsString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String compactError(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error == null ? "未知错误" : error.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() <= 180 ? message : message.substring(0, 177) + "...";
    }

    public static final class EvaluationResult {
        public final boolean shouldContinue;
        public final String normalizedExpression;
        public final String errorMessage;

        private EvaluationResult(boolean shouldContinue,
                                 String normalizedExpression,
                                 String errorMessage) {
            this.shouldContinue = shouldContinue;
            this.normalizedExpression = normalizedExpression;
            this.errorMessage = errorMessage;
        }

        public boolean hasError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }

        private static EvaluationResult success(boolean value, String normalizedExpression) {
            return new EvaluationResult(value, normalizedExpression, "");
        }

        private static EvaluationResult failure(String error, String normalizedExpression) {
            return new EvaluationResult(false, normalizedExpression, error == null ? "未知错误" : error);
        }
    }

    public static final class UpdateResult {
        public final boolean success;
        public final Object resultValue;
        public final String errorMessage;

        private UpdateResult(boolean success, Object resultValue, String errorMessage) {
            this.success = success;
            this.resultValue = resultValue;
            this.errorMessage = errorMessage;
        }

        private static UpdateResult success(Object resultValue) {
            return new UpdateResult(true, resultValue, "");
        }

        private static UpdateResult failure(String errorMessage) {
            return new UpdateResult(false, null,
                    errorMessage == null ? "未知错误" : errorMessage);
        }
    }

    private static final class ExpressionContextFactory extends ContextFactory {
        private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();

        Context enterContextWithTimeout(long timeoutMs) {
            DEADLINE.set(System.currentTimeMillis() + timeoutMs);
            Context context = enterContext();
            context.setOptimizationLevel(-1);
            context.setInstructionObserverThreshold(10_000);
            return context;
        }

        @Override
        protected void observeInstructionCount(Context context, int instructionCount) {
            Long deadline = DEADLINE.get();
            if (deadline != null && System.currentTimeMillis() > deadline) {
                throw new RuntimeException("表达式执行超时");
            }
        }

        void clearDeadline() {
            DEADLINE.remove();
        }
    }
}
