package com.auto.master.floatwin;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import com.auto.master.R;

import java.util.List;

/**
 * Helper class for common dialog operations.
 * Provides utilities for building, managing, and displaying dialogs.
 */
public class DialogHelpers {

    private final FloatWindowHost host;
    private final WindowManager wm;

    public DialogHelpers(FloatWindowHost host) {
        this.host = host;
        this.wm = host.getWindowManager();
    }

    /**
     * Build layout parameters for a dialog window.
     * Width is capped to 96% of screen. In landscape mode height is capped
     * to 93% of screen height to prevent dialogs from overflowing the display.
     */
    public WindowManager.LayoutParams buildDialogLayoutParams(int widthDp, boolean focusable) {
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!focusable) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        android.util.DisplayMetrics metrics = host.getContext().getResources().getDisplayMetrics();
        boolean landscape = metrics.widthPixels > metrics.heightPixels;

        int width = Math.min(host.dp(widthDp), (int) (metrics.widthPixels * 0.96f));
        // In landscape the screen is shorter — cap height so dialogs never overflow.
        // applyAdaptiveDialogViewport will override this for complex tall dialogs.
        // In portrait WRAP_CONTENT is fine; dialogs are rarely taller than portrait screens.
        int height = landscape
                ? Math.max(host.dp(240), (int) (metrics.heightPixels * 0.93f))
                : WindowManager.LayoutParams.WRAP_CONTENT;

        WindowManager.LayoutParams dialogLp = new WindowManager.LayoutParams(
                width,
                height,
                type,
                flags,
                PixelFormat.TRANSLUCENT
        );
        dialogLp.gravity = Gravity.CENTER;
        return dialogLp;
    }

    public void applyAdaptiveDialogViewport(WindowManager.LayoutParams dialogLp, int widthDp) {
        applyAdaptiveDialogViewport(dialogLp, widthDp, 0.84f, 0.94f);
    }

    public void applyAdaptiveDialogViewport(WindowManager.LayoutParams dialogLp,
                                            int widthDp,
                                            float portraitHeightRatio,
                                            float landscapeHeightRatio) {
        if (dialogLp == null) {
            return;
        }
        android.util.DisplayMetrics metrics = host.getContext().getResources().getDisplayMetrics();
        boolean landscape = metrics.widthPixels > metrics.heightPixels;
        int maxWidth = Math.max(host.dp(240), (int) (metrics.widthPixels * 0.96f));
        float heightRatio = landscape ? landscapeHeightRatio : portraitHeightRatio;
        dialogLp.width = Math.min(host.dp(widthDp), maxWidth);
        dialogLp.height = Math.max(host.dp(240), (int) (metrics.heightPixels * heightRatio));
    }

    /**
     * Setup drag and scale functionality for a dialog.
     * Attaches a touch listener to dialog_drag_header that moves the window,
     * and wires up btn_scale_dialog to toggle between normal/expanded width.
     */
    public void setupDialogMoveAndScale(View dialogView,
                                        WindowManager.LayoutParams dialogLp,
                                        int normalWidthDp,
                                        int expandedWidthDp,
                                        Object unused) {
        if (dialogView == null || dialogLp == null) {
            return;
        }

        // ── Drag via dialog_drag_header ──────────────────────────────────────
        View dragHeader = dialogView.findViewById(R.id.dialog_drag_header);
        if (dragHeader != null) {
            dragHeader.setOnTouchListener(new View.OnTouchListener() {
                private float lastRawX;
                private float lastRawY;
                private boolean dragging;
                private long lastUpdateMs;
                private static final float SLOP_PX = 8f;
                private static final long THROTTLE_MS = 16L;

                @Override
                public boolean onTouch(View v, MotionEvent ev) {
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            lastRawX = ev.getRawX();
                            lastRawY = ev.getRawY();
                            dragging = false;
                            lastUpdateMs = 0;
                            return true;

                        case MotionEvent.ACTION_MOVE: {
                            float dx = ev.getRawX() - lastRawX;
                            float dy = ev.getRawY() - lastRawY;
                            if (!dragging && Math.abs(dx) < SLOP_PX && Math.abs(dy) < SLOP_PX) {
                                return true;
                            }
                            dragging = true;
                            long now = System.currentTimeMillis();
                            if (now - lastUpdateMs < THROTTLE_MS) {
                                return true;
                            }
                            lastUpdateMs = now;

                            dialogLp.x += (int) dx;
                            dialogLp.y += (int) dy;

                            // Keep dialog within screen bounds
                            android.util.DisplayMetrics dm =
                                    host.getContext().getResources().getDisplayMetrics();
                            int halfW = dialogLp.width > 0
                                    ? dialogLp.width / 2
                                    : host.dp(normalWidthDp) / 2;
                            int edgeY = host.dp(40); // minimum margin from top/bottom
                            int halfH = dialogLp.height > 0 ? dialogLp.height / 2 : host.dp(120);
                            dialogLp.x = Math.max(-dm.widthPixels / 2 + halfW,
                                    Math.min(dm.widthPixels / 2 - halfW, dialogLp.x));
                            dialogLp.y = Math.max(-dm.heightPixels / 2 + edgeY,
                                    Math.min(dm.heightPixels / 2 - edgeY, dialogLp.y));

                            try {
                                wm.updateViewLayout(dialogView, dialogLp);
                            } catch (Exception ignored) {
                            }

                            lastRawX = ev.getRawX();
                            lastRawY = ev.getRawY();
                            return true;
                        }

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            dragging = false;
                            return true;
                    }
                    return false;
                }
            });
        }

        // ── Scale toggle via btn_scale_dialog ────────────────────────────────
        View scaleView = dialogView.findViewById(R.id.btn_scale_dialog);
        if (scaleView instanceof TextView) {
            TextView btnScale = (TextView) scaleView;
            final boolean[] expanded = {false};
            btnScale.setOnClickListener(v -> {
                expanded[0] = !expanded[0];
                android.util.DisplayMetrics metrics =
                        host.getContext().getResources().getDisplayMetrics();
                int maxWidth = Math.max(host.dp(240), (int) (metrics.widthPixels * 0.96f));
                dialogLp.width = Math.min(host.dp(expanded[0] ? expandedWidthDp : normalWidthDp), maxWidth);
                try {
                    wm.updateViewLayout(dialogView, dialogLp);
                } catch (Exception ignored) {
                }
                btnScale.setText(expanded[0] ? "缩小" : "放大");
            });
        }
    }

    /**
     * Safely remove a view from WindowManager
     */
    public void safeRemoveView(View view) {
        if (view == null) {
            return;
        }
        try {
            wm.removeView(view);
        } catch (Exception ignored) {
        }
    }

    /**
     * Bind autocomplete suggestions to an AutoCompleteTextView
     */
    public void bindAutoComplete(AutoCompleteTextView view, List<String> options) {
        if (view == null || options == null) {
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                host.getContext(),
                android.R.layout.simple_dropdown_item_1line,
                options
        );
        view.setAdapter(adapter);
        view.setThreshold(1);
    }
}
