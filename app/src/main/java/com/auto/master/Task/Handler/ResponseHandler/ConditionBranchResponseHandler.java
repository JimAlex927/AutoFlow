package com.auto.master.Task.Handler.ResponseHandler;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.auto.ScriptExecuteContext;

import java.util.Map;

public class ConditionBranchResponseHandler extends DefaultResponseHandler {
    @Override
    public void process(Object response, ScriptExecuteContext scriptExecuteContext) {
        if (scriptExecuteContext == null || scriptExecuteContext.sharedContext == null) {
            return;
        }
        OperationContext ctx = scriptExecuteContext.sharedContext;
        if (ctx.lastOperation == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }

        String nextId = "";
        if (response instanceof Map) {
            Object next = ((Map<?, ?>) response).get(MetaOperation.BRANCH_NEXT_ID);
            if (next instanceof String) {
                nextId = ((String) next).trim();
            }
        }
        if (nextId.isEmpty()) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }

        Project project = ctx.anchorProject;
        if (project == null || project.getTaskMap() == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        Task task = project.getTaskMap().get(ctx.lastOperation.taskId);
        if (task == null) {
            task = resolveTaskByOperationId(project.getTaskMap(), ctx.lastOperation.getId());
        }
        if (task == null || task.getOperationMap() == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        scriptExecuteContext.tobeHandledOperation = task.getOperationMap().get(nextId);
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
