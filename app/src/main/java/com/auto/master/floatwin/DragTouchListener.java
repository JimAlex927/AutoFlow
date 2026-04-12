package com.auto.master.floatwin;

import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

class DragTouchListener implements View.OnTouchListener {
    private static final long FRAME_INTERVAL_MS = 16L;

    private final WindowManager.LayoutParams lp;
    private final WindowManager wm;
    private final View targetView;
    private final FloatWindowService service;
    private final boolean keepInScreen;

    private float downRawX;
    private float downRawY;
    private int startX;
    private int startY;
    private boolean moved = false;
    private boolean longPressFired = false;
    private final int slopPx;
    private long lastUpdateMs;
    private int pendingX;
    private int pendingY;

    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private final long longPressTimeoutMs = ViewConfiguration.getLongPressTimeout();

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
                longPressFired = false;
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startX = lp.x;
                startY = lp.y;
                pendingX = lp.x;
                pendingY = lp.y;
                lastUpdateMs = 0L;
                setDraggingVisualState(true);
                longPressRunnable = () -> {
                    if (!moved) {
                        longPressFired = true;
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        onLongPress();
                    }
                };
                longPressHandler.postDelayed(longPressRunnable, longPressTimeoutMs);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - downRawX;
                float dy = e.getRawY() - downRawY;

                if (!moved && (Math.abs(dx) > slopPx || Math.abs(dy) > slopPx)) {
                    moved = true;
                    cancelLongPress();
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
                    pendingX = nextX;
                    pendingY = nextY;
                    long now = android.os.SystemClock.uptimeMillis();
                    if (lastUpdateMs == 0L || now - lastUpdateMs >= FRAME_INTERVAL_MS) {
                        applyPendingPosition();
                        lastUpdateMs = now;
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelLongPress();
                if (moved) {
                    applyPendingPosition();
                    onDragEnd(lp.x, lp.y);
                }
                setDraggingVisualState(false);
                if (!moved && !longPressFired) {
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

    protected void onDragEnd(int finalX, int finalY) {
    }

    protected void onLongPress() {
    }

    private void applyPendingPosition() {
        if (lp.x == pendingX && lp.y == pendingY) {
            return;
        }
        lp.x = pendingX;
        lp.y = pendingY;
        wm.updateViewLayout(targetView, lp);
    }

    private void setDraggingVisualState(boolean dragging) {
        targetView.setLayerType(dragging ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
        View header = targetView.findViewById(R.id.drag_header);
        if (header != null) {
            header.setAlpha(dragging ? 0.94f : 1f);
        }
        RecyclerView rv = targetView.findViewById(R.id.rv_content);
        if (rv != null) {
            rv.suppressLayout(dragging);
        }
    }
}
