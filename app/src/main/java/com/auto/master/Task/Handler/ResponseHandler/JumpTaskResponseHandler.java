package com.auto.master.Task.Handler.ResponseHandler;

import android.os.SystemClock;
import android.util.Log;

import com.auto.master.Task.Operation.JumpTaskOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.auto.ScriptExecuteContext;
import com.auto.master.auto.ScriptRunner;

import java.util.List;
import java.util.Map;

/**
 * 跳转 Task 响应处理器
 * 
 * 功能：
 * 1. 处理跳转到目标 Task 的指定 Operation
 * 2. 如果设置了 RETURN_AFTER_COMPLETE=true，则在子 Task 执行完毕后返回原 Task
 *    - 把当前的 JumpTaskOperation 压栈（保存现场）
 *    - 子 Task 执行完后，恢复到 JumpTaskOperation，让它决定下一步
 * 
 * responseType:
 * - 1: 跳转到目标 Task，不返回（子 Task 结束后脚本结束）
 * - 2: 跳转到目标 Task，执行完后返回原 Task 继续（类似函数调用）
 */
public class JumpTaskResponseHandler extends DefaultResponseHandler {

    private static final String TAG = "JumpTaskResponseHandler";

    public JumpTaskResponseHandler() {
        this.type = 1;
    }

    @Override
    public void process(Object response, ScriptExecuteContext scriptExecuteContext) {
        OperationContext ctx = scriptExecuteContext.sharedContext;
        
        if (!(response instanceof Map)) {
            Log.e(TAG, "response 不是 Map 类型");
            return;
        }

        Map<String, Object> result = (Map<String, Object>) response;
        
        Project anchorProject = ctx.anchorProject;
        if (anchorProject == null) {
            Log.e(TAG, "anchorProject 为空");
            return;
        }

        // 检查是否是刚从子 Task 返回（通过 justReturnedFromSubTask 标记）
        // 这个检查必须在获取 targetTaskId 之前，因为返回后的 response 可能没有这些信息
        if (scriptExecuteContext.justReturnedFromSubTask) {
            // 刚从子 Task 返回，此时应该处理 NEXT_OPERATION_ID
            scriptExecuteContext.justReturnedFromSubTask = false;
            
            Map<String, Object> inputMap = ctx.lastOperation.getInputMap();
            String nextOpId = (String) inputMap.get(MetaOperation.NEXT_OPERATION_ID);
            
            if (nextOpId != null && !nextOpId.isEmpty()) {
                Task originTask = anchorProject.getTaskMap().get(ctx.lastOperation.taskId);
                if (originTask == null) {
                    originTask = resolveTaskByOperationId(anchorProject.getTaskMap(), ctx.lastOperation.getId());
                }
                if (originTask != null) {
                    MetaOperation nextOp = originTask.getOperationMap().get(nextOpId);
                    if (nextOp != null) {
                        Log.d(TAG, "返回后跳转到原 Task 的 Operation[" + nextOpId + "]");
                        scriptExecuteContext.tobeHandledOperation = nextOp;
                        SystemClock.sleep(10);
                        return;
                    }
                }
            }
            
            // 没有下一个 operation，设置为 null（结束）
            Log.d(TAG, "返回后没有下一个 operation，结束");
            scriptExecuteContext.tobeHandledOperation = null;
            return;
        }
        
        // 第一次执行：需要跳转到子 Task
        String targetTaskId = (String) result.get(MetaOperation.TARGET_TASK_ID);
        String targetOperationId = (String) result.get(MetaOperation.TARGET_OPERATION_ID);
        Boolean returnAfterComplete = (Boolean) result.get(MetaOperation.RETURN_AFTER_COMPLETE);
        
        if (targetTaskId == null || targetOperationId == null) {
            Log.e(TAG, "目标 Task ID 或 Operation ID 为空");
            return;
        }

        Map<String, Task> taskMap = anchorProject.getTaskMap();
        Task targetTask = taskMap.get(targetTaskId);
        
        if (targetTask == null) {
            Log.e(TAG, "未找到目标 Task: " + targetTaskId);
            return;
        }

        Map<String, MetaOperation> operationMap = targetTask.getOperationMap();
        MetaOperation targetOperation = operationMap.get(targetOperationId);

        if (targetOperation == null) {
            Log.e(TAG, "未找到目标 Operation: " + targetOperationId);
            return;
        }

        // 如果需要返回，把当前的 JumpTaskOperation 压栈
        if (returnAfterComplete != null && returnAfterComplete) {
            MetaOperation currentOp = ctx.lastOperation;
            if (currentOp != null && currentOp.getType() == 8) {
                scriptExecuteContext.returnStack.push(currentOp);
                Log.d(TAG, "压栈 JumpTaskOperation[" + currentOp.getId() + "]，子 Task 执行完后将恢复");
            }
        }

        // 通知 UI 更新 Task 信息
        notifyTaskSwitch(targetTask);

        // 设置下一个要执行的 operation（子 Task 的目标 operation）
        scriptExecuteContext.tobeHandledOperation = targetOperation;
        
        Log.d(TAG, "已跳转到 Task[" + targetTaskId + "] Operation[" + targetOperationId + "]");
        
        // 降低 CPU 占用
        SystemClock.sleep(10);
    }

    /**
     * 通知 UI 当前 Task 已切换
     */
    private void notifyTaskSwitch(Task targetTask) {
        ScriptRunner.ScriptExecutionListener listener = ScriptRunner.getCurrentListener();
        if (listener != null) {
            // 构建 operations 列表
            List<com.auto.master.floatwin.OperationItem> operationItems =
                new java.util.ArrayList<>();

            if (targetTask.getOperationMap() != null) {
                int index = 0;
                for (MetaOperation op : targetTask.getOperationMap().values()) {
                    String typeName = getOperationTypeName(op.getType());
                    operationItems.add(new com.auto.master.floatwin.OperationItem(
                        op.getName(),
                        op.getId(),
                        typeName,
                        index++,
                        com.auto.master.floatwin.FloatWindowService.extractDelayDurationMs(op),
                        com.auto.master.floatwin.FloatWindowService.extractDelayShowCountdown(op)
                    ));
                }
            }
            
            listener.onTaskSwitch(
                targetTask.getId(),
                targetTask.getName(),
                operationItems
            );
        }
    }

    private String getOperationTypeName(Integer type) {
        if (type == null) return "未知";
        switch (type) {
            case 1: return "点击";
            case 2: return "延时";
            case 3: return "截图";
            case 4: return "加载图片";
            case 5: return "手势";
            case 6: return "模板匹配";
            case 7: return "多模板匹配";
            case 8: return "跳转Task";
            case 9: return "OCR识别(已弃用)";
            case 10: return "条件分支";
            case 11: return "变量脚本";
            case 12: return "变量运算";
            case 13: return "变量模板";
            default: return "未知";
        }
    }
}
