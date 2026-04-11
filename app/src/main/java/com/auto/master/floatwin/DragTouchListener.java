package com.auto.master.floatwin;

import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

public class DragTouchListener implements View.OnTouchListener {

    private final WindowManager.LayoutParams lp;
    private final WindowManager wm;
    private final View targetView;
    private final FloatWindowService service;
    private final boolean keepInScreen;

    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;
    private boolean moved;
    private boolean longPressFired;
    private final int slopPx;

    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private final long longPressTimeoutMs;

    DragTouchListener(WindowManager.LayoutParams lp, WindowManager wm, View targetView, FloatWindowService service) {
        this(lp, wm, targetView, service, false);
    }

    DragTouchListener(
            WindowManager.LayoutParams lp,
            WindowManager wm,
            View targetView,
            FloatWindowService service,
            boolean keepInScreen
    ) {
        this.lp = lp;
        this.wm = wm;
        this.targetView = targetView;
        this.service = service;
        this.keepInScreen = keepInScreen;
        this.slopPx = ViewConfiguration.get(service).getScaledTouchSlop();
        this.longPressTimeoutMs = ViewConfiguration.getLongPressTimeout();
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                moved = false;
                longPressFired = false;
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startX = lp.x;
                startY = lp.y;
                v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.92f).setDuration(120).start();
                // 启动长按计时
                longPressRunnable = () -> {
                    if (!moved) {
                        longPressFired = true;
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        onLongPress();
                    }
                };
                longPressHandler.postDelayed(longPressRunnable, longPressTimeoutMs);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;

                if (!moved && (Math.abs(dx) > slopPx || Math.abs(dy) > slopPx)) {
                    moved = true;
                    cancelLongPress(); // 拖动时取消长按
                }

                if (moved) {
                    int nextX = startX + (int) dx;
                    int nextY = startY + (int) dy;
                    if (keepInScreen) {
                        int[] screen = service.getScreenSizePx();
                        int margin = service.dp(6);
                        int viewW = Math.max(targetView.getWidth(), 1);
                        int viewH = Math.max(targetView.getHeight(), 1);
                        int maxX = Math.max(margin, screen[0] - viewW - margin);
                        int maxY = Math.max(margin, screen[1] - viewH - margin);
                        nextX = Math.max(margin, Math.min(nextX, maxX));
                        nextY = Math.max(margin, Math.min(nextY, maxY));
                    }
                    lp.x = nextX;
                    lp.y = nextY;
                    wm.updateViewLayout(targetView, lp);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelLongPress();
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                if (moved) {
                    onDragEnd(lp.x, lp.y);
                } else if (!longPressFired) {
                    // 只有既没拖动、也没触发长按，才算普通点击
                    v.performClick();
                }
                return true;

            default:
                return false;
        }
    }

    private void cancelLongPress() {
        if (longPressRunnable != null) {
            longPressHandler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    /**
     * Called when the user finishes a drag gesture.
     * Override to react to the final position (e.g., persist it).
     */
    protected void onDragEnd(int finalX, int finalY) {}

    /**
     * Called when a long-press is detected (no drag movement).
     * Override in anonymous subclass to handle the long-press action.
     */
    protected void onLongPress() {}
}
