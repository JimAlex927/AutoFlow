package com.auto.master.floatwin;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

class DragTouchListener implements View.OnTouchListener {
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
    private final int slopPx;

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
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                moved = false;
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startX = lp.x;
                startY = lp.y;
                v.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.92f).setDuration(120).start();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;

                if (!moved && (Math.abs(dx) > slopPx || Math.abs(dy) > slopPx)) {
                    moved = true;
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
                v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                if (moved) {
                    onDragEnd(lp.x, lp.y);
                } else {
                    v.performClick();
                }
                return true;

            default:
                return false;
        }
    }

    /**
     * Called when the user finishes a drag gesture.
     * Override to react to the final position (e.g., persist it).
     */
    protected void onDragEnd(int finalX, int finalY) {}
}
