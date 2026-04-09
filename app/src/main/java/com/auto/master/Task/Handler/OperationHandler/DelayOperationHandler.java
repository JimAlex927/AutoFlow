package com.auto.master.Task.Handler.OperationHandler;

import com.auto.master.Task.Operation.DelayOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 这是一个 click 的处理器
 * 它的作用就只是调用无障碍然后点击那个点
 */
public class DelayOperationHandler extends OperationHandler {




     DelayOperationHandler() {
         this.setType(2);
    }



    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        DelayOperation delayOperation = (DelayOperation) obj;

        Map<String, Object> inputMap = delayOperation.getInputMap();
        long duration = 0L;
        if (inputMap != null) {
            Object value = inputMap.get(MetaOperation.SLEEP_DURATION);
            if (value instanceof Number) {
                duration = ((Number) value).longValue();
            } else if (value instanceof String) {
                try {
                    duration = Long.parseLong(((String) value).trim());
                } catch (Exception ignored) {
                    duration = 0L;
                }
            }
        }
        if (duration < 0L) {
            duration = 0L;
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
            ctx.currentResponse = res;
            ctx.lastOperation = obj;
        }

        return true;
    }


}
