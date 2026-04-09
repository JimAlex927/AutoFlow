package com.auto.master.Task.Handler.ResponseHandler;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.auto.ScriptExecuteContext;

import java.util.Map;

public class ColorMatchResponseHandler extends DefaultResponseHandler {

    private static final String TAG = "ColorMatchResponse";

    public ColorMatchResponseHandler() {
        this.type = 1;
    }

    @Override
    public void process(Object response, ScriptExecuteContext scriptExecuteContext) {
        scriptExecuteContext.tobeHandledOperation = null;
        OperationContext ctx = scriptExecuteContext.sharedContext;
        if (ctx == null || ctx.lastOperation == null) {
            return;
        }
        Map<String, Object> inputMap = ctx.lastOperation.getInputMap();
        if (inputMap == null) {
            return;
        }

        boolean matched = false;
        if (response instanceof Map) {
            Object matchedObj = ((Map<?, ?>) response).get(MetaOperation.MATCHED);
            matched = matchedObj instanceof Boolean
                    ? (Boolean) matchedObj
                    : matchedObj instanceof Number && ((Number) matchedObj).intValue() != 0;
        }

        String targetId = matched
                ? getString(inputMap.get(MetaOperation.NEXT_OPERATION_ID))
                : getString(inputMap.get(MetaOperation.FALLBACKOPERATIONID));
        if (TextUtils.isEmpty(targetId)) {
            return;
        }
        if (TextUtils.equals(targetId, ctx.lastOperation.getId())) {
            Log.e(TAG, "颜色匹配 next/fallback 指向自身，终止: " + targetId);
            return;
        }

        Project anchorProject = ctx.anchorProject;
        if (anchorProject == null || anchorProject.getTaskMap() == null) {
            return;
        }
        Map<String, Task> taskMap = anchorProject.getTaskMap();
        Task task = taskMap.get(ctx.lastOperation.taskId);
        if (task == null) {
            task = resolveTaskByOperationId(taskMap, ctx.lastOperation.getId());
        }
        if (task == null || task.getOperationMap() == null) {
            return;
        }

        com.auto.master.Task.Operation.MetaOperation next = task.getOperationMap().get(targetId);
        if (next == null) {
            Log.e(TAG, "颜色匹配下一节点不存在: " + targetId);
            return;
        }
        scriptExecuteContext.tobeHandledOperation = next;
        SystemClock.sleep(10);
    }

    private String getString(Object raw) {
        if (raw == null) {
            return "";
        }
        String value = String.valueOf(raw).trim();
        return TextUtils.isEmpty(value) ? "" : value;
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
