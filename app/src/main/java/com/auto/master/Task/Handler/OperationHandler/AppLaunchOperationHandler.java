package com.auto.master.Task.Handler.OperationHandler;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.auto.master.Task.Operation.AppLaunchOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.auto.AutoAccessibilityService;

import java.util.HashMap;
import java.util.Map;

public class AppLaunchOperationHandler extends OperationHandler {

    private static final String TAG = "AppLaunchOpHandler";

    AppLaunchOperationHandler() {
        this.setType(14);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        AppLaunchOperation operation = (AppLaunchOperation) obj;
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            return false;
        }

        Map<String, Object> inputMap = operation.getInputMap();
        String packageName = asString(inputMap.get(MetaOperation.APP_PACKAGE));
        String appLabel = asString(inputMap.get(MetaOperation.APP_LABEL));
        boolean skipIfForeground = asBoolean(inputMap.get(MetaOperation.APP_SKIP_IF_FOREGROUND), true);
        long launchDelayMs = asLong(inputMap.get(MetaOperation.APP_LAUNCH_DELAY_MS), 1500L);

        if (TextUtils.isEmpty(packageName)) {
            Log.w(TAG, "Missing app package");
            return false;
        }

        if (skipIfForeground && packageName.equals(getCurrentPackageName(svc))) {
            fillResponse(ctx, obj, packageName, appLabel, true, true);
            return sleepSafely(launchDelayMs);
        }

        PackageManager packageManager = svc.getPackageManager();
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            Log.w(TAG, "No launcher intent for package: " + packageName);
            return false;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        final boolean[] started = {false};
        final Throwable[] error = {null};
        Object lock = new Object();
        getMainHandler().post(() -> {
            try {
                svc.startActivity(launchIntent);
                started[0] = true;
            } catch (Throwable t) {
                error[0] = t;
            }
            synchronized (lock) {
                lock.notifyAll();
            }
        });

        synchronized (lock) {
            try {
                lock.wait(1500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        if (!started[0]) {
            Log.w(TAG, "Launch app failed", error[0]);
            return false;
        }

        fillResponse(ctx, obj, packageName, appLabel, true, false);
        return sleepSafely(launchDelayMs);
    }

    private void fillResponse(OperationContext ctx, MetaOperation obj, String packageName, String appLabel,
                              boolean success, boolean skipped) {
        Map<String, Object> response = new HashMap<>();
        response.put(MetaOperation.RESULT, success);
        response.put(MetaOperation.APP_PACKAGE, packageName);
        response.put(MetaOperation.APP_LABEL, appLabel);
        response.put("skipped", skipped);
        ctx.currentResponse = response;
        ctx.lastOperation = obj;
    }

    private String getCurrentPackageName(AutoAccessibilityService svc) {
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) {
            return "";
        }
        return String.valueOf(root.getPackageName());
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("1".equals(text)) {
                return true;
            }
            if ("0".equals(text)) {
                return false;
            }
            if (!text.isEmpty()) {
                return Boolean.parseBoolean(text);
            }
        }
        return defaultValue;
    }

    private long asLong(Object value, long defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (Exception ignored) {
            }
        }
        return defaultValue;
    }

    private boolean sleepSafely(long delayMs) {
        long safeDelay = Math.max(0L, delayMs);
        if (safeDelay == 0L) {
            return true;
        }
        try {
            Thread.sleep(safeDelay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
