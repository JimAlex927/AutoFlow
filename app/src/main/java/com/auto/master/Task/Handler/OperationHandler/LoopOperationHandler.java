package com.auto.master.Task.Handler.OperationHandler;

import android.text.TextUtils;

import com.auto.master.Task.Operation.LoopOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class LoopOperationHandler extends OperationHandler {

    // 编译后的正则表达式缓存，避免每次循环迭代重新编译 Pattern（Pattern.compile 有锁和反射开销）
    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>(8);

    LoopOperationHandler() {
        this.setType(16);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (!(obj instanceof LoopOperation) || ctx == null) {
            return false;
        }
        Map<String, Object> inputMap = obj.getInputMap();
        String varName = getStr(inputMap, MetaOperation.LOOP_CONDITION_VAR, "");
        String operator = getStr(inputMap, MetaOperation.LOOP_OPERATOR, "is_true");
        String operand = getStr(inputMap, MetaOperation.LOOP_OPERAND, "");
        String bodyNext = getStr(inputMap, MetaOperation.LOOP_BODY_NEXT, "");
        String exitNext = getStr(inputMap, MetaOperation.LOOP_EXIT_NEXT, "");

        Object varValue = null;
        if (ctx.variables != null && !TextUtils.isEmpty(varName)) {
            varValue = ctx.variables.get(varName);
        }

        boolean conditionMet = evaluate(varValue, operand, operator);
        String selectedNext = conditionMet ? bodyNext : exitNext;

        Map<String, Object> res = new HashMap<>();
        res.put(MetaOperation.BRANCH_NEXT_ID, selectedNext);
        res.put(MetaOperation.RESULT, varValue);
        ctx.currentResponse = res;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    private boolean evaluate(Object actual, String expected, String operator) {
        String op = operator == null ? "is_true" : operator.toLowerCase();
        switch (op) {
            case "is_true":  return toBoolean(actual);
            case "is_false": return !toBoolean(actual);
            case "empty":    return TextUtils.isEmpty(strOf(actual));
            case "not_empty":return !TextUtils.isEmpty(strOf(actual));
        }
        if (isNumberOp(op)) {
            Double a = toDouble(actual);
            Double b = toDouble(expected);
            if (a == null || b == null) return false;
            switch (op) {
                case "gt":  return a > b;
                case "gte": return a >= b;
                case "lt":  return a < b;
                case "lte": return a <= b;
                case "neq": return !a.equals(b);
                default:    return a.equals(b);
            }
        }
        String a = strOf(actual);
        String b = expected == null ? "" : expected;
        switch (op) {
            case "contains": return a.contains(b);
            case "neq":      return !a.equals(b);
            case "regex":
                try {
                    Pattern pat = PATTERN_CACHE.computeIfAbsent(b, k -> Pattern.compile(k, Pattern.DOTALL));
                    return pat.matcher(a).find();
                } catch (Exception e) { return false; }
            default:         return a.equals(b);
        }
    }

    private boolean isNumberOp(String op) {
        return "gt".equals(op) || "gte".equals(op) || "lt".equals(op) || "lte".equals(op);
    }

    private String strOf(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String getStr(Map<String, Object> map, String key, String def) {
        if (map == null) return def;
        Object v = map.get(key);
        return v instanceof String ? (String) v : def;
    }

    private Double toDouble(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try { return Double.parseDouble(((String) v).trim()); } catch (Exception e) { return null; }
        }
        return null;
    }

    private boolean toBoolean(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number)  return ((Number) v).intValue() != 0;
        if (v instanceof String) {
            String s = ((String) v).trim().toLowerCase();
            return "true".equals(s) || "1".equals(s) || "yes".equals(s);
        }
        return false;
    }
}
