package com.auto.master.Task.Handler.ResponseHandler;


import android.os.SystemClock;
import android.util.Log;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.auto.ScriptExecuteContext;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

//@TYPE = 1    这个只是跳转到 inputMap 里面的下一个operation  所以算是一个通用的类型
public class JumpToNextOperationResponseHandler extends DefaultResponseHandler{
    private static final String TAG = "JumpToNextHandler";

    public JumpToNextOperationResponseHandler() {
        this.type = 1;
    }

    @Override
    public void process(Object response, ScriptExecuteContext scriptExecuteContext) {
        OperationContext ctx = scriptExecuteContext.sharedContext;
        if (ctx == null || ctx.lastOperation == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        Map<String, Object> inputMap = ctx.lastOperation.getInputMap();
        if (inputMap == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        Object nextObj = inputMap.get(MetaOperation.NEXT_OPERATION_ID);
        String nextOpId = nextObj instanceof String ? ((String) nextObj).trim() : "";
        if (!StringUtils.isNotEmpty(nextOpId)) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        if (nextOpId.equals(ctx.lastOperation.getId())) {
            Log.e(TAG, "nextOperationId 指向自身，终止以避免循环: " + nextOpId);
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }

        Project anchorProject = ctx.anchorProject;
        if (anchorProject == null || anchorProject.getTaskMap() == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        Map<String, Task> taskMap = anchorProject.getTaskMap();
        Task task = taskMap.get(ctx.lastOperation.taskId);
        if (task == null) {
            task = resolveTaskByOperationId(taskMap, ctx.lastOperation.getId());
        }
        if (task == null || task.getOperationMap() == null) {
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }

        MetaOperation metaOperation = task.getOperationMap().get(nextOpId);
        if (metaOperation == null) {
            Log.e(TAG, "nextOperationId 未找到节点，终止: " + nextOpId);
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        scriptExecuteContext.tobeHandledOperation = metaOperation;
        SystemClock.sleep(10);
    }

}
