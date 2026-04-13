package com.auto.master.Task.Handler.OperationHandler;

import com.auto.master.Task.Operation.DynamicDelayOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 动态延时处理器：从运行时变量中读取延时时长（毫秒），然后执行等待。
 * 若变量不存在或无法转换为数字，则延时 0ms。
 */
public class DynamicDelayOperationHandler extends OperationHandler {

    DynamicDelayOperationHandler() {
        this.setType(21);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        DynamicDelayOperation op = (DynamicDelayOperation) obj;

        Map<String, Object> inputMap = op.getInputMap();
        String varName = VariableRuntimeUtils.getString(inputMap, MetaOperation.DYNAMIC_DELAY_VAR_NAME, "").trim();

        long duration = 0L;
        if (!varName.isEmpty() && ctx != null && ctx.variables != null) {
            Object varValue = ctx.variables.get(varName);
            if (varValue instanceof Number) {
                duration = ((Number) varValue).longValue();
            } else if (varValue instanceof String) {
                try {
                    duration = Long.parseLong(((String) varValue).trim());
                } catch (Exception ignored) {
                    duration = 0L;
                }
            }
        }

        if (duration < 0L) {
            duration = 0L;
        }

        // 读取是否显示倒计时标志
        boolean showCountdown = true;
        if (inputMap != null) {
            Object cdRaw = inputMap.get(MetaOperation.DELAY_SHOW_COUNTDOWN);
            if (cdRaw instanceof Boolean) {
                showCountdown = (Boolean) cdRaw;
            } else if (cdRaw instanceof Number) {
                showCountdown = ((Number) cdRaw).intValue() != 0;
            } else if (cdRaw instanceof String) {
                String s = ((String) cdRaw).trim();
                showCountdown = "true".equalsIgnoreCase(s) || "1".equals(s);
            }
        }

        // sleep 前通知 Service 启动倒计时覆盖层
        if (ctx != null && ctx.delayCountdownNotifier != null) {
            ctx.delayCountdownNotifier.onDynamicDelayStarting(obj.getId(), duration, showCountdown);
        }

        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        if (ctx != null) {
            Map<String, Object> res = new HashMap<>();
            res.put("DELAYED", true);
            res.put(MetaOperation.SLEEP_DURATION, duration);
            res.put(MetaOperation.DYNAMIC_DELAY_VAR_NAME, varName);
            ctx.currentResponse = res;
            ctx.lastOperation = obj;
        }

        return true;
    }
}
