package com.auto.master.Task.Handler.OperationHandler;

import android.text.TextUtils;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Operation.VariableSetOperation;

import java.util.HashMap;

public class VariableSetOperationHandler extends OperationHandler {
    VariableSetOperationHandler() {
        this.setType(11);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (!(obj instanceof VariableSetOperation) || ctx == null) {
            return false;
        }
        String varName = VariableRuntimeUtils.getString(obj.getInputMap(), MetaOperation.VAR_NAME, "").trim();
        if (TextUtils.isEmpty(varName)) {
            return false;
        }
        if (ctx.variables == null) {
            ctx.variables = new HashMap<>();
        }
        Object value = VariableRuntimeUtils.resolveByMode(
                ctx,
                obj.getInputMap(),
                MetaOperation.VAR_SOURCE_MODE,
                MetaOperation.VAR_SOURCE_VALUE,
                MetaOperation.VAR_TYPE
        );
        ctx.variables.put(varName, value);
        VariableRuntimeUtils.putCommonResult(ctx, obj, value);
        return true;
    }
}
