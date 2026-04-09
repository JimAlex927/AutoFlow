package com.auto.master.floatwin;

import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

class PanelResizeTouchListener implements View.OnTouchListener {
    private final WindowManager.LayoutParams lp;
    private final WindowManager wm;
    private final View targetView;
    private final FloatWindowService service;
    private final int minWidthDp;
    private final int minHeightDp;

    private float downRawX;
    private float downRawY;
    private int startW;
    private int startH;

    PanelResizeTouchListener(
            WindowManager.LayoutParams lp,
            WindowManager wm,
            View targetView,
            FloatWindowService service,
            int minWidthDp,
            int minHeightDp
    ) {
        this.lp = lp;
        this.wm = wm;
        this.targetView = targetView;
        this.service = service;
        this.minWidthDp = minWidthDp;
        this.minHeightDp = minHeightDp;
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                startW = lp.width;
                startH = lp.height;
                v.animate().alpha(0.7f).setDuration(90).start();
                return true;

            case MotionEvent.ACTION_MOVE:
                int[] screen = service.getScreenSizePx();
                int margin = service.dp(12);
                int maxW = Math.max(service.dp(minWidthDp), screen[0] - margin * 2);
                int maxH = Math.max(service.dp(minHeightDp), screen[1] - margin * 2);
                int minW = service.dp(minWidthDp);
                int minH = service.dp(minHeightDp);

                int newW = startW + (int) (e.getRawX() - downRawX);
                int newH = startH + (int) (e.getRawY() - downRawY);
                newW = Math.max(minW, Math.min(newW, maxW));
                newH = Math.max(minH, Math.min(newH, maxH));

                lp.width = newW;
                lp.height = newH;
                lp.x = Math.max(margin, Math.min(lp.x, Math.max(margin, screen[0] - newW - margin)));
                lp.y = Math.max(margin, Math.min(lp.y, Math.max(margin, screen[1] - newH - margin)));
                wm.updateViewLayout(targetView, lp);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                v.animate().alpha(1f).setDuration(90).start();
                return true;

            default:
                return false;
        }
    }
}
