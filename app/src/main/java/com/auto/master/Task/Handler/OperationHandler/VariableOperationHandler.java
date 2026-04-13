package com.auto.master.Task.Handler.OperationHandler;

import android.text.TextUtils;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import java.util.Map;

/**
 * 兼容历史上的变量节点：
 * - 变量设置 和 变量脚本长期共用 type=11
 * - 老数据里仅凭 type 无法区分，需要根据 inputMap 分流
 */
public class VariableOperationHandler extends OperationHandler {

    private final VariableSetOperationHandler setHandler = new VariableSetOperationHandler();
    private final VariableScriptOperationHandler scriptHandler = new VariableScriptOperationHandler();

    VariableOperationHandler() {
        this.setType(11);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (obj == null || ctx == null) {
            return false;
        }
        return looksLikeScriptOperation(obj)
                ? scriptHandler.handle(obj, ctx)
                : setHandler.handle(obj, ctx);
    }

    private boolean looksLikeScriptOperation(MetaOperation obj) {
        Map<String, Object> inputMap = obj.getInputMap();
        if (inputMap == null) {
            return false;
        }
        Object rawScript = inputMap.get(MetaOperation.VAR_SCRIPT_CODE);
        return rawScript != null && !TextUtils.isEmpty(String.valueOf(rawScript).trim());
    }
}
