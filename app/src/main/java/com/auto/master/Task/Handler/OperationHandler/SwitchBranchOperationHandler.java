package com.auto.master.Task.Handler.OperationHandler;

import android.text.TextUtils;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Operation.SwitchBranchOperation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwitchBranchOperationHandler extends OperationHandler {

    SwitchBranchOperationHandler() {
        this.setType(15);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (!(obj instanceof SwitchBranchOperation) || ctx == null) {
            return false;
        }
        Map<String, Object> inputMap = obj.getInputMap();
        String varName = getStr(inputMap, MetaOperation.SWITCH_VAR_NAME, "");
        String defaultNext = getStr(inputMap, MetaOperation.BRANCH_DEFAULT_NEXT, "");

        Object varValue = null;
        if (ctx.variables != null && !TextUtils.isEmpty(varName)) {
            varValue = ctx.variables.get(varName);
        }
        String strValue = varValue == null ? "" : String.valueOf(varValue);

        String selectedNext = "";
        Object rulesRaw = inputMap == null ? null : inputMap.get(MetaOperation.BRANCH_RULES);
        if (rulesRaw instanceof List) {
            List<?> rules = (List<?>) rulesRaw;
            for (Object item : rules) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> rule = (Map<?, ?>) item;
                String caseValue = strOf(rule.get("value"), "");
                String nextId = strOf(rule.get("nextOperationId"), "");
                if (TextUtils.isEmpty(nextId)) continue;
                if (strValue.equals(caseValue)) {
                    selectedNext = nextId;
                    break;
                }
            }
        }

        if (TextUtils.isEmpty(selectedNext)) {
            selectedNext = defaultNext;
        }

        Map<String, Object> res = new HashMap<>();
        res.put(MetaOperation.BRANCH_NEXT_ID, selectedNext);
        res.put(MetaOperation.RESULT, varValue);
        ctx.currentResponse = res;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    private String getStr(Map<String, Object> map, String key, String def) {
        if (map == null) return def;
        Object v = map.get(key);
        return v instanceof String ? (String) v : def;
    }

    private String strOf(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }
}
