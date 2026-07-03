package com.auto.master.Task.Handler.OperationHandler;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.auto.master.Task.Handler.ResponseHandler.DefaultResponseHandler;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.auto.ScriptExecuteContext;
import com.auto.master.auto.ScriptRunner;

import java.util.HashMap;
import java.util.Map;

public class MtryOperationHandler extends OperationHandler {

    private static final String TAG = "MtryOperationHandler";
    private static final int DEFAULT_ATTEMPTS = 1;
    private static final int MAX_ATTEMPTS = 1000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    MtryOperationHandler() {
        this.setType(25);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (obj == null || ctx == null) {
            return false;
        }

        Map<String, Object> inputMap = obj.getInputMap();
        String wrappedOperationId = getString(inputMap, MetaOperation.MTRY_WRAPPED_OPERATION_ID, "");
        int attempts = parseAttempts(inputMap == null ? null : inputMap.get(MetaOperation.MTRY_ATTEMPTS));
        boolean runWrappedResponseHandler = parseBoolean(
                inputMap == null ? null : inputMap.get(MetaOperation.MTRY_RUN_RESPONSE_HANDLER),
                true);
        long retryDelayMs = parseLong(inputMap == null ? null : inputMap.get(MetaOperation.MTRY_RETRY_DELAY_MS), 0L);
        boolean retryShowCountdown = parseBoolean(
                inputMap == null ? null : inputMap.get(MetaOperation.MTRY_RETRY_SHOW_COUNTDOWN),
                false);
        final String mtryOpId = obj.getId();
        if (TextUtils.isEmpty(wrappedOperationId)) {
            putMtryResponse(ctx, obj, false, 0, "未选择被包裹节点", null);
            return true;
        }
        if (TextUtils.equals(wrappedOperationId, obj.getId())) {
            putMtryResponse(ctx, obj, false, 0, "多次尝试节点不能包裹自身", null);
            return true;
        }

        MetaOperation wrapped = findWrappedOperation(ctx.anchorProject, obj, wrappedOperationId);
        if (wrapped == null) {
            putMtryResponse(ctx, obj, false, 0, "被包裹节点不存在: " + wrappedOperationId, null);
            return true;
        }
        if (wrapped.getType() != null && wrapped.getType() == 25) {
            putMtryResponse(ctx, obj, false, 0, "多次尝试节点不能包裹另一个多次尝试节点", null);
            return true;
        }

        OperationHandler wrappedHandler = OperationHandlerManager.getOperationHandler(wrapped.getType());
        if (wrappedHandler == null) {
            putMtryResponse(ctx, obj, false, 0, "被包裹节点没有处理器: " + wrappedOperationId, null);
            return true;
        }

        DefaultResponseHandler responseHandler =
                OperationHandlerManager.createResponseHandler(wrapped.getClass(), wrapped.getResponseType());

        boolean matched = false;
        int usedAttempts = 0;
        Map<String, Object> lastWrappedResponse = null;
        String lastError = "";

        for (int i = 0; i < attempts; i++) {
            usedAttempts = i + 1;

            // 通知 UI：当前尝试序号
            final int curAttempt = usedAttempts;
            final ScriptRunner.ScriptExecutionListener listenerAttempt = ScriptRunner.getCurrentListener();
            final String sessionId = ScriptRunner.getCurrentSessionId();
            if (listenerAttempt != null) {
                MAIN.post(() -> listenerAttempt.onMtryAttempt(sessionId, mtryOpId, curAttempt, attempts));
            }

            boolean ok;
            try {
                ok = wrappedHandler.handle(wrapped, ctx);
            } catch (Throwable t) {
                Log.w(TAG, "wrapped handler failed: " + wrappedOperationId, t);
                ok = false;
                lastError = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            }

            lastWrappedResponse = copyResponse(ctx.currentResponse);
            MetaOperation internalTarget = null;
            if (runWrappedResponseHandler && ok && responseHandler != null) {
                internalTarget = runWrappedResponseSideEffects(responseHandler, lastWrappedResponse, ctx);
            }

            matched = isWrappedSuccess(ok, lastWrappedResponse, wrapped, internalTarget);
            if (matched) {
                break;
            }

            if (TextUtils.isEmpty(lastError)) {
                lastError = "第 " + usedAttempts + " 次未命中";
            }

            // 重试延时（最后一次失败后不需要等待）
            if (i < attempts - 1 && retryDelayMs > 0) {
                if (retryShowCountdown) {
                    final ScriptRunner.ScriptExecutionListener listenerDelay = ScriptRunner.getCurrentListener();
                    final String retrySessionId = ScriptRunner.getCurrentSessionId();
                    final long dl = retryDelayMs;
                    if (listenerDelay != null) {
                        MAIN.post(() -> listenerDelay.onMtryRetryDelay(retrySessionId, mtryOpId, dl));
                    }
                }
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                Thread.yield();
            }
        }

        // 清除 UI 尝试次数徽章
        ScriptRunner.ScriptExecutionListener listenerFinal = ScriptRunner.getCurrentListener();
        final String sessionId = ScriptRunner.getCurrentSessionId();
        if (listenerFinal != null) {
            MAIN.post(() -> listenerFinal.onMtryAttempt(sessionId, mtryOpId, 0, 0));
        }

        putMtryResponse(ctx, obj, matched, usedAttempts, lastError, lastWrappedResponse);
        return true;
    }

    private MetaOperation runWrappedResponseSideEffects(DefaultResponseHandler responseHandler,
                                                        Map<String, Object> response,
                                                        OperationContext ctx) {
        ScriptExecuteContext inner = new ScriptExecuteContext();
        inner.sharedContext = ctx;
        inner.running = true;
        inner.tobeHandledOperation = null;
        try {
            responseHandler.process(response, inner);
        } catch (Throwable t) {
            Log.w(TAG, "wrapped response handler failed: " + t.getMessage(), t);
        }
        return inner.tobeHandledOperation;
    }

    private boolean isWrappedSuccess(boolean handlerOk,
                                     Map<String, Object> response,
                                     MetaOperation wrapped,
                                     MetaOperation internalTarget) {
        if (!handlerOk) {
            return false;
        }
        Boolean matched = getMatchedValue(response);
        if (matched != null) {
            if (!matched) {
                return false;
            }
            String fallbackId = getString(wrapped.getInputMap(), MetaOperation.FALLBACKOPERATIONID, "");
            if (internalTarget != null && !TextUtils.isEmpty(fallbackId)
                    && TextUtils.equals(internalTarget.getId(), fallbackId)) {
                return false;
            }
            return true;
        }
        return true;
    }

    private Boolean getMatchedValue(Map<String, Object> response) {
        if (response == null || !response.containsKey(MetaOperation.MATCHED)) {
            return null;
        }
        Object raw = response.get(MetaOperation.MATCHED);
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw instanceof String) {
            String text = ((String) raw).trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return null;
    }

    private void putMtryResponse(OperationContext ctx,
                                 MetaOperation obj,
                                 boolean matched,
                                 int attemptsUsed,
                                 String error,
                                 Map<String, Object> wrappedResponse) {
        Map<String, Object> response = new HashMap<>();
        response.put(MetaOperation.MATCHED, matched);
        response.put(MetaOperation.RESULT, matched);
        response.put("MTRY_ATTEMPTS_USED", (long) attemptsUsed);
        if (!TextUtils.isEmpty(error)) {
            response.put("MTRY_LAST_ERROR", error);
        }
        if (wrappedResponse != null) {
            response.put("MTRY_WRAPPED_RESPONSE", wrappedResponse);
        }
        ctx.currentResponse = response;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
    }

    private MetaOperation findWrappedOperation(Project project, MetaOperation wrapper, String wrappedOperationId) {
        if (project == null || project.getTaskMap() == null || TextUtils.isEmpty(wrappedOperationId)) {
            return null;
        }
        Task task = null;
        if (wrapper != null && !TextUtils.isEmpty(wrapper.taskId)) {
            task = project.getTaskMap().get(wrapper.taskId);
        }
        if (task == null && wrapper != null) {
            task = resolveTaskByOperationId(project.getTaskMap(), wrapper.getId());
        }
        if (task != null && task.getOperationMap() != null) {
            MetaOperation op = task.getOperationMap().get(wrappedOperationId);
            if (op != null) {
                return op;
            }
        }
        for (Task candidate : project.getTaskMap().values()) {
            if (candidate != null && candidate.getOperationMap() != null) {
                MetaOperation op = candidate.getOperationMap().get(wrappedOperationId);
                if (op != null) {
                    return op;
                }
            }
        }
        return null;
    }

    private Task resolveTaskByOperationId(Map<String, Task> taskMap, String operationId) {
        if (taskMap == null || TextUtils.isEmpty(operationId)) {
            return null;
        }
        for (Task task : taskMap.values()) {
            if (task != null && task.getOperationMap() != null
                    && task.getOperationMap().containsKey(operationId)) {
                return task;
            }
        }
        return null;
    }

    private int parseAttempts(Object raw) {
        long value = DEFAULT_ATTEMPTS;
        if (raw instanceof Number) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof String) {
            try {
                value = Long.parseLong(((String) raw).trim());
            } catch (Exception ignored) {
                value = DEFAULT_ATTEMPTS;
            }
        }
        return (int) Math.max(1, Math.min(value, MAX_ATTEMPTS));
    }

    private boolean parseBoolean(Object raw, boolean def) {
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw instanceof String) {
            String text = ((String) raw).trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return def;
    }

    private String getString(Map<String, Object> map, String key, String def) {
        if (map == null || key == null) {
            return def;
        }
        Object value = map.get(key);
        return value == null ? def : String.valueOf(value).trim();
    }

    private long parseLong(Object raw, long def) {
        if (raw instanceof Number) return ((Number) raw).longValue();
        if (raw instanceof String) {
            try { return Long.parseLong(((String) raw).trim()); } catch (Exception ignored) {}
        }
        return def;
    }

    private Map<String, Object> copyResponse(Map<String, Object> response) {
        return response == null ? null : new HashMap<>(response);
    }
}
