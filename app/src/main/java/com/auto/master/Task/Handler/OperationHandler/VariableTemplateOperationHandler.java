package com.auto.master.Task.Handler.OperationHandler;

import android.text.TextUtils;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Operation.VariableTemplateOperation;

import java.util.HashMap;

public class VariableTemplateOperationHandler extends OperationHandler {
    VariableTemplateOperationHandler() {
        this.setType(13);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (!(obj instanceof VariableTemplateOperation) || ctx == null) {
            return false;
        }
        String varName = VariableRuntimeUtils.getString(obj.getInputMap(), MetaOperation.VAR_NAME, "").trim();
        if (TextUtils.isEmpty(varName)) {
            return false;
        }
        if (ctx.variables == null) {
            ctx.variables = new HashMap<>();
        }
        String template = VariableRuntimeUtils.getString(obj.getInputMap(), MetaOperation.VAR_TEMPLATE, "");
        String value = VariableRuntimeUtils.applyTemplate(template, ctx.variables);
        ctx.variables.put(varName, value);
        VariableRuntimeUtils.putCommonResult(ctx, obj, value);
        return true;
    }
}
