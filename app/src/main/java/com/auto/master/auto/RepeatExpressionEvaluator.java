package com.auto.master.auto;

import android.text.TextUtils;
import android.util.Log;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Evaluates the lightweight boolean condition used by a repeat-execution node. */
public final class RepeatExpressionEvaluator {
    private static final String TAG = "RepeatExpression";
    private static final long TIMEOUT_MS = 250L;
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{\\s*([^}]+?)\\s*}");
    private static final Pattern JS_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final ExpressionContextFactory CONTEXT_FACTORY = new ExpressionContextFactory();

    private RepeatExpressionEvaluator() {
    }

    public static boolean shouldContinue(String expression, Map<String, Object> variables) {
        String source = normalizeExpression(expression);
        if (TextUtils.isEmpty(source)) {
            return false;
        }
        Context js = CONTEXT_FACTORY.enterContextWithTimeout(TIMEOUT_MS);
        try {
            Scriptable scope = js.initStandardObjects();
            Scriptable vars = js.newObject(scope);
            if (variables != null) {
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    String key = entry.getKey();
                    if (TextUtils.isEmpty(key)) {
                        continue;
                    }
                    Object value = Context.javaToJS(entry.getValue(), scope);
                    ScriptableObject.putProperty(vars, key, value);
                    if (JS_IDENTIFIER.matcher(key).matches()) {
                        ScriptableObject.putProperty(scope, key, value);
                    }
                }
            }
            ScriptableObject.putProperty(scope, "vars", vars);
            return Context.toBoolean(js.evaluateString(scope, source, "repeat_expression", 1, null));
        } catch (Exception e) {
            Log.w(TAG, "Repeat expression evaluated as false: " + expression, e);
            return false;
        } finally {
            Context.exit();
            CONTEXT_FACTORY.clearDeadline();
        }
    }

    private static String normalizeExpression(String expression) {
        if (expression == null) {
            return "";
        }
        Matcher matcher = TEMPLATE_VARIABLE.matcher(expression.trim());
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim().replace("\\", "\\\\").replace("\"", "\\\"");
            matcher.appendReplacement(output, Matcher.quoteReplacement("vars[\"" + key + "\"]"));
        }
        matcher.appendTail(output);
        return output.toString();
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
                throw new RuntimeException("repeat expression timeout");
            }
        }

        void clearDeadline() {
            DEADLINE.remove();
        }
    }
}
