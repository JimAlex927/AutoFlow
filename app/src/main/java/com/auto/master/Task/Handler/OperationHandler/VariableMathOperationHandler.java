package com.auto.master.Task.Handler.OperationHandler;

import android.text.TextUtils;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Operation.VariableMathOperation;

import java.util.HashMap;

public class VariableMathOperationHandler extends OperationHandler {
    VariableMathOperationHandler() {
        this.setType(12);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (!(obj instanceof VariableMathOperation) || ctx == null) {
            return false;
        }
        String varName = VariableRuntimeUtils.getString(obj.getInputMap(), MetaOperation.VAR_NAME, "").trim();
        if (TextUtils.isEmpty(varName)) {
            return false;
        }
        if (ctx.variables == null) {
            ctx.variables = new HashMap<>();
        }
        String action = VariableRuntimeUtils.getString(obj.getInputMap(), MetaOperation.VAR_ACTION, "add").trim().toLowerCase();
        Double currentValue = VariableRuntimeUtils.toDouble(ctx.variables.get(varName));
        if (currentValue == null) {
            currentValue = 0d;
        }
        Double operand = VariableRuntimeUtils.toDouble(VariableRuntimeUtils.resolveByMode(
                ctx,
                obj.getInputMap(),
                MetaOperation.VAR_OPERAND_MODE,
                MetaOperation.VAR_OPERAND_VALUE,
                MetaOperation.VAR_OPERAND_TYPE
        ));

        double result;
        switch (action) {
            case "set":
                result = operand == null ? 0d : operand;
                break;
            case "sub":
                result = currentValue - safeOperand(operand);
                break;
            case "mul":
                result = currentValue * safeOperand(operand);
                break;
            case "div":
                if (operand == null || Math.abs(operand) < 0.0000001d) {
                    return false;
                }
                result = currentValue / operand;
                break;
            case "mod":
                if (operand == null || Math.abs(operand) < 0.0000001d) {
                    return false;
                }
                result = currentValue % operand;
                break;
            case "inc":
                result = currentValue + 1d;
                break;
            case "dec":
                result = currentValue - 1d;
                break;
            case "negate":
                result = -currentValue;
                break;
            case "abs":
                result = Math.abs(currentValue);
                break;
            case "add":
            default:
                result = currentValue + safeOperand(operand);
                break;
        }

        Object normalized = VariableRuntimeUtils.normalizeNumber(result);
        ctx.variables.put(varName, normalized);
        VariableRuntimeUtils.putCommonResult(ctx, obj, normalized);
        return true;
    }

    private double safeOperand(Double operand) {
        return operand == null ? 0d : operand;
    }
}
