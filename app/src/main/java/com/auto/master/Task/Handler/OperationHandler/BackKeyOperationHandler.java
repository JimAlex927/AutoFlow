package com.auto.master.Task.Handler.OperationHandler;

import android.accessibilityservice.AccessibilityService;
import android.util.Log;

import com.auto.master.Task.Operation.BackKeyOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.auto.AutoAccessibilityService;

import java.util.HashMap;
import java.util.Map;

/**
 * 返回按键操作处理器
 * 模拟按下系统返回键
 */
public class BackKeyOperationHandler extends OperationHandler {

    private static final String TAG = "BackKeyOperationHandler";

    BackKeyOperationHandler() {
        this.setType(17);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        BackKeyOperation backKeyOperation = (BackKeyOperation) obj;
        AutoAccessibilityService svc = AutoAccessibilityService.get();

        if (svc == null) {
            Log.e(TAG, "AutoAccessibilityService is null");
            return false;
        }

        Log.d(TAG, "执行返回按键操作");

        // 执行返回键操作
        boolean success = svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);

        if (!success) {
            Log.e(TAG, "返回按键执行失败");
            return false;
        }

        Log.d(TAG, "返回按键执行成功");

        // 短暂延时，等待返回动画完成
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        ctx.currentOperation = obj;
        ctx.lastOperation = obj;
        Map<String, Object> res = new HashMap<>();
        res.put("BACK_KEY_PRESSED", true);
        ctx.currentResponse = res;
        return true;
    }
}
