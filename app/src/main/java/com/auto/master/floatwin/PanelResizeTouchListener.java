package com.auto.master.floatwin;

import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

class PanelResizeTouchListener implements View.OnTouchListener {
    private static final long FRAME_INTERVAL_MS = 16L;

    private final WindowManager.LayoutParams lp;
    private final WindowManager wm;
    private final View targetView;
    private final FloatWindowService service;
    private final int minWidthDp;
    private final int minHeightDp;
    private final float landscapeMaxHeightRatio;

    private float downRawX;
    private float downRawY;
    private int startW;
    private int startH;
    private int pendingW;
    private int pendingH;
    private long lastUpdateMs;

    PanelResizeTouchListener(
            WindowManager.LayoutParams lp,
            WindowManager wm,
            View targetView,
            FloatWindowService service,
            int minWidthDp,
            int minHeightDp
    ) {
        this(lp, wm, targetView, service, minWidthDp, minHeightDp, 1f);
    }

    PanelResizeTouchListener(
            WindowManager.LayoutParams lp,
            WindowManager wm,
            View targetView,
            FloatWindowService service,
            int minWidthDp,
            int minHeightDp,
            float landscapeMaxHeightRatio
    ) {
        this.lp = lp;
        this.wm = wm;
        this.targetView = targetView;
        this.service = service;
        this.minWidthDp = minWidthDp;
        this.minHeightDp = minHeightDp;
        this.landscapeMaxHeightRatio = landscapeMaxHeightRatio;
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startW = lp.width;
                startH = lp.height;
                pendingW = lp.width;
                pendingH = lp.height;
                lastUpdateMs = 0L;
                setResizingVisualState(true);
                return true;

            case MotionEvent.ACTION_MOVE: {
                int[] screen = service.getScreenSizePx();
                int minW = service.dp(minWidthDp);
                int minH = service.dp(minHeightDp);
                int newW = startW + (int) (e.getRawX() - downRawX);
                int newH = startH + (int) (e.getRawY() - downRawY);
                int maxH = screen[1] - service.dp(24);
                if (landscapeMaxHeightRatio > 0f && screen[0] > screen[1]) {
                    maxH = Math.min(maxH, Math.max(minH, (int) (screen[1] * landscapeMaxHeightRatio)));
                }
                pendingW = Math.max(minW, Math.min(newW, screen[0]));
                pendingH = Math.max(minH, Math.min(newH, maxH));
                long now = android.os.SystemClock.uptimeMillis();
                if (lastUpdateMs == 0L || now - lastUpdateMs >= FRAME_INTERVAL_MS) {
                    applyPendingSize();
                    lastUpdateMs = now;
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                applyPendingSize();
                setResizingVisualState(false);
                return true;

            default:
                return false;
        }
    }

    private void applyPendingSize() {
        if (lp.width == pendingW && lp.height == pendingH) {
            return;
        }
        lp.width = pendingW;
        lp.height = pendingH;
        int[] screen = service.getScreenSizePx();
        int margin = service.dp(8);
        int panelWidth = Math.max(lp.width, service.dp(minWidthDp));
        int panelHeight = Math.max(lp.height, service.dp(minHeightDp));
        int maxX = Math.max(margin, screen[0] - panelWidth - margin);
        int maxY = Math.max(margin, screen[1] - panelHeight - margin);
        lp.x = Math.max(margin, Math.min(lp.x, maxX));
        lp.y = Math.max(margin, Math.min(lp.y, maxY));
        wm.updateViewLayout(targetView, lp);
    }

    private void setResizingVisualState(boolean resizing) {
        targetView.setLayerType(resizing ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
        View handle = targetView.findViewById(R.id.resize_handle);
        if (handle != null) {
            handle.setAlpha(resizing ? 0.8f : 1f);
        }
        RecyclerView rv = targetView.findViewById(R.id.rv_content);
        if (rv != null) {
            rv.suppressLayout(resizing);
        }
    }
}
