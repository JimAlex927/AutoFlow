package com.auto.master.Task.Handler.ResponseHandler;

import android.os.SystemClock;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.auto.ScriptExecuteContext;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class OcrDynamicJumpResponseHandler extends DefaultResponseHandler {

    @Override
    public void process(Object response, ScriptExecuteContext scriptExecuteContext) {
        if (scriptExecuteContext == null || scriptExecuteContext.sharedContext == null) {
            return;
        }
        OperationContext ctx = scriptExecuteContext.sharedContext;
        if (ctx.lastOperation == null || ctx.anchorProject == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }

        Map<String, Object> inputMap = ctx.lastOperation.getInputMap();
        if (inputMap == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }

        boolean matched = false;
        if (response instanceof Map) {
            Object m = ((Map<?, ?>) response).get(MetaOperation.MATCHED);
            if (m instanceof Boolean) {
                matched = (Boolean) m;
            }
        }

        String targetOpId;
        if (matched) {
            Object next = inputMap.get(MetaOperation.NEXT_OPERATION_ID);
            targetOpId = next instanceof String ? (String) next : "";
        } else {
            Object fallback = inputMap.get(MetaOperation.FALLBACKOPERATIONID);
            targetOpId = fallback instanceof String ? (String) fallback : "";
        }

        if (!StringUtils.isNotEmpty(targetOpId)) {
            scriptExecuteContext.tobeHandledOperation = null;
            SystemClock.sleep(10);
            return;
        }

        Project anchorProject = ctx.anchorProject;
        Map<String, Task> taskMap = anchorProject.getTaskMap();
        Task task = taskMap == null ? null : taskMap.get(ctx.lastOperation.taskId);
        if (task == null) {
            task = resolveTaskByOperationId(taskMap, ctx.lastOperation.getId());
        }
        if (task == null || task.getOperationMap() == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }

        scriptExecuteContext.tobeHandledOperation = task.getOperationMap().get(targetOpId);
        SystemClock.sleep(10);
    }

    private Task resolveTaskByOperationId(Map<String, Task> taskMap, String operationId) {
        if (taskMap == null || operationId == null) {
            return null;
        }
        for (Task t : taskMap.values()) {
            if (t != null && t.getOperationMap() != null && t.getOperationMap().containsKey(operationId)) {
                return t;
            }
        }
        return null;
    }
}
