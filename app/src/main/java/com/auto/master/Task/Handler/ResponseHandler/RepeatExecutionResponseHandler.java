package com.auto.master.Task.Handler.ResponseHandler;

import android.text.TextUtils;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.auto.ScriptExecuteContext;

import java.util.Map;

public class RepeatExecutionResponseHandler extends DefaultResponseHandler {

    @Override
    public void process(Object response, ScriptExecuteContext scriptExecuteContext) {
        if (scriptExecuteContext == null || scriptExecuteContext.sharedContext == null) {
            return;
        }
        OperationContext context = scriptExecuteContext.sharedContext;
        MetaOperation controller = context.lastOperation;
        if (controller == null || controller.getInputMap() == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        if (scriptExecuteContext.isRepeatExecutionActiveFor(controller.getId())) {
            scriptExecuteContext.tobeHandledOperation =
                    scriptExecuteContext.advanceRepeatExecutionAtTaskEnd();
            return;
        }
        String startId = text(controller.getInputMap().get(MetaOperation.REPEAT_START_OPERATION_ID));
        if (TextUtils.isEmpty(startId) || startId.equals(controller.getId())) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        Project project = context.anchorProject;
        if (project == null || project.getTaskMap() == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        Task task = project.getTaskMap().get(controller.taskId);
        if (task == null) {
            task = resolveTaskByOperationId(project.getTaskMap(), controller.getId());
        }
        MetaOperation startOperation = task == null || task.getOperationMap() == null
                ? null : task.getOperationMap().get(startId);
        if (startOperation == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }

        boolean infinite = bool(controller.getInputMap().get(MetaOperation.REPEAT_INFINITE));
        int count = positiveInt(controller.getInputMap().get(MetaOperation.REPEAT_COUNT), 2);
        scriptExecuteContext.beginRepeatExecution(startOperation, controller.getId(), infinite, count);
        scriptExecuteContext.tobeHandledOperation = startOperation;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = text(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static int positiveInt(Object value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(text(value)));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
