package com.auto.master.auto;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;

public final class InputHelper {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private InputHelper() {}

    /** 将文本复制到剪贴板，并对“当前焦点节点”执行粘贴 */
    public static boolean pasteToFocused(String text) {
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) return false;

        // 1) 复制到剪贴板
        ClipboardManager cm = (ClipboardManager) svc.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return false;
        cm.setPrimaryClip(ClipData.newPlainText("auto", text));

        // 2) 找到当前焦点输入框（兼容性比 “按节点 setText” 更好）
        final boolean[] ok = {false};
        final Object lock = new Object();

        MAIN.post(() -> {
            try {
                AccessibilityNodeInfo root = svc.getRootInActiveWindow();
                if (root == null) {
                    signal(lock, ok, false);
                    return;
                }
                AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focus == null) focus = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
                if (focus == null) {
                    signal(lock, ok, false);
                    return;
                }

                // 粘贴
                boolean pasted = focus.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                focus.recycle();
                root.recycle();
                signal(lock, ok, pasted);
            } catch (Exception e) {
                signal(lock, ok, false);
            }
        });

        synchronized (lock) {
            try { lock.wait(800); } catch (InterruptedException ignored) {}
        }
        return ok[0];
    }

    private static void signal(Object lock, boolean[] ok, boolean value) {
        synchronized (lock) {
            ok[0] = value;
            lock.notifyAll();
        }
    }
}