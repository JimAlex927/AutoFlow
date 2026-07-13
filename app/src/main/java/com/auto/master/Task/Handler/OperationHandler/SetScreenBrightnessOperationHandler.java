package com.auto.master.Task.Handler.OperationHandler;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.auto.AutoAccessibilityService;

import java.util.HashMap;
import java.util.Map;

public class SetScreenBrightnessOperationHandler extends OperationHandler {

    private static final String TAG = "SetBrightnessOp";

    SetScreenBrightnessOperationHandler() {
        this.setType(31);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        Map<String, Object> inputMap = obj.getInputMap();
        int percent = MetaOperation.DEFAULT_SCREEN_BRIGHTNESS_PERCENT;
        if (inputMap != null) {
            Object v = inputMap.get(MetaOperation.BRIGHTNESS_PERCENT);
            if (v != null) {
                try {
                    percent = Integer.parseInt(v.toString().trim());
                } catch (Exception ignored) {
                }
            }
        }

        boolean success = applyBrightness(getContext(), percent);

        if (ctx != null) {
            Map<String, Object> res = new HashMap<>();
            res.put(MetaOperation.MATCHED, success);
            res.put("brightnessPercent", percent);
            ctx.currentResponse = res;
            ctx.lastOperation = obj;
        }
        return true;
    }

    private static boolean applyBrightness(Context context, int percent) {
        if (context == null) {
            Log.w(TAG, "无可用 Context，跳过亮度设置");
            return false;
        }
        int clamped = Math.max(1, Math.min(100, percent));
        if (!Settings.System.canWrite(context)) {
            Log.w(TAG, "缺少修改系统设置权限(WRITE_SETTINGS)，无法设置亮度");
            return false;
        }
        try {
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            int brightness = Math.round(clamped / 100f * 255f);
            brightness = Math.max(1, Math.min(255, brightness));
            Settings.System.putInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, brightness);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "设置屏幕亮度失败", e);
            return false;
        }
    }

    private static Context getContext() {
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        return svc == null ? null : svc.getApplicationContext();
    }
}
