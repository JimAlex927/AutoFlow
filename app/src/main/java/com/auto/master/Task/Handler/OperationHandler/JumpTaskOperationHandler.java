package com.auto.master.Task.Handler.OperationHandler;

import static org.opencv.android.NativeCameraView.TAG;

import android.util.Log;

import com.auto.master.Task.Operation.JumpTaskOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;

import java.util.Map;

/**
 * 跳转 Task 操作处理器
 * 
 * 功能：
 * 1. 跳转到指定 Task 的指定 Operation
 * 2. 支持执行完子 Task 后返回原 Task 继续执行（通过 responseType 控制）
 * 
 * 执行流程：
 * - 第一次执行：检查参数，保存现场到 response，跳转到子 Task
 * - 子 Task 执行完后返回：再次执行此 handler，此时 response 中有 __RETURN_FROM_SUBTASK__ 标记
 * - 返回后：根据 NEXT_OPERATION_ID 决定下一步（继续执行或结束）
 * 
 * inputMap 参数：
 * - TARGET_TASK_ID: 目标 Task ID（必填）
 * - TARGET_OPERATION_ID: 目标 Operation ID（必填）
 * - RETURN_AFTER_COMPLETE: 执行完是否返回原 Task（可选，默认 false）
 * - NEXT_OPERATION_ID: 返回后跳转到原 Task 的哪个 Operation（可选，如果没有则结束）
 */
public class JumpTaskOperationHandler extends OperationHandler {

    JumpTaskOperationHandler() {
        this.setType(8);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        JumpTaskOperation jumpTaskOperation = (JumpTaskOperation) obj;
        Map<String, Object> inputMap = jumpTaskOperation.getInputMap();
        
        Project anchorProject = ctx.anchorProject;
        if (anchorProject == null) {
            Log.e(TAG, "JumpTaskOperation: anchorProject 为空");
            return false;
        }

        // 检查是否刚从子 Task 返回
        // 注意：返回后的处理在 JumpTaskResponseHandler 中完成
        if (ctx.currentResponse != null && Boolean.TRUE.equals(ctx.currentResponse.get("__RETURN_FROM_SUBTASK__"))) {
            // 刚从子 Task 返回，只需返回 true
            // JumpTaskResponseHandler 会根据 NEXT_OPERATION_ID 决定下一步
            Log.d(TAG, "JumpTaskOperation: 刚从子 Task 返回，让 ResponseHandler 处理下一步");
            ctx.lastOperation = obj;
            return true;
        }

        // 第一次执行：准备跳转到子 Task
        String targetTaskId = getStringSafe(inputMap, MetaOperation.TARGET_TASK_ID, "");
        String targetOperationId = getStringSafe(inputMap, MetaOperation.TARGET_OPERATION_ID, "");

        if (targetTaskId.isEmpty() || targetOperationId.isEmpty()) {
            Log.e(TAG, "JumpTaskOperation: 目标 Task ID 或 Operation ID 为空");
            return false;
        }

        boolean returnAfterComplete = getBooleanSafe(inputMap, MetaOperation.RETURN_AFTER_COMPLETE, false);

        Map<String, Task> taskMap = anchorProject.getTaskMap();
        Task targetTask = taskMap.get(targetTaskId);

        if (targetTask == null) {
            Log.e(TAG, "JumpTaskOperation: 未找到目标 Task: " + targetTaskId);
            return false;
        }

        Map<String, MetaOperation> operationMap = targetTask.getOperationMap();
        MetaOperation targetOperation = operationMap.get(targetOperationId);

        if (targetOperation == null) {
            Log.e(TAG, "JumpTaskOperation: 未找到目标 Operation: " + targetOperationId);
            return false;
        }

        // 保存跳转信息到 response（供 JumpTaskResponseHandler 使用）
        Map<String, Object> res = new java.util.HashMap<>();
        res.put(MetaOperation.RESULT, true);
        res.put(MetaOperation.TARGET_TASK_ID, targetTaskId);
        res.put(MetaOperation.TARGET_OPERATION_ID, targetOperationId);
        res.put(MetaOperation.RETURN_AFTER_COMPLETE, returnAfterComplete);
        
        ctx.currentResponse = res;
        ctx.lastOperation = obj;

        Log.d(TAG, "JumpTaskOperation: 准备跳转到 Task[" + targetTaskId + "] Operation[" + targetOperationId + "]");
        
        return true;
    }

    private String getStringSafe(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v instanceof String) ? (String) v : def;
    }

    private boolean getBooleanSafe(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof String) {
            return Boolean.parseBoolean((String) v);
        }
        return def;
    }
}
