package com.auto.master.floatwin;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.auto.master.R;
import com.auto.master.auto.ScriptRunner;

final class FloatBallOverlayController {
    interface Host {
        FloatWindowService getService();
        Context getContext();
        WindowManager getWindowManager();
        int dp(int value);
        int[] getScreenSizePx();
        void hideProjectPanelDock();
        void showRuntimeAwareProjectPanel();
        void togglePauseState();
        void removeProjectPanel();
        void showProjectPanel();
        void showToast(String message);
        boolean isPaused();
    }

    private static final String TAG = "FloatBallOverlay";
    private static final int BALL_EDGE_MARGIN_DP = 6;
    private static final int BALL_AUTO_DOCK_THRESHOLD_DP = 24;
    private static final int BALL_EDGE_OVERLAP_DP = 2;
    private static final int BALL_DOCK_EDGE_NONE = 0;
    private static final int BALL_DOCK_EDGE_LEFT = 1;
    private static final int BALL_DOCK_EDGE_RIGHT = 2;
    private static final int BALL_DOCK_EDGE_BOTTOM = 3;

    private final Host host;

    private View ballView;
    private WindowManager.LayoutParams ballLp;
    private View ballCoreView;
    private TextView ballDockHandleLeft;
    private TextView ballDockHandleRight;
    private TextView ballDockHandleBottom;
    private TextView ballStatusText;

    private View fanMenuView;
    private WindowManager.LayoutParams fanMenuLp;

    private int lastIdleBallX = 50;
    private int lastIdleBallY = 300;
    private int ballDockEdge = BALL_DOCK_EDGE_LEFT;
    private boolean ballCollapsedForRunning = false;
    private boolean ballCollapsedToEdge = false;

    FloatBallOverlayController(Host host) {
        this.host = host;
    }

    WindowManager.LayoutParams getLayoutParams() {
        return ballLp;
    }

    void show() {
        if (ballView != null) {
            return;
        }

        Context context = host.getContext();
        WindowManager wm = host.getWindowManager();
        ballView = LayoutInflater.from(context).inflate(R.layout.floating_ball_layout, null);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        ballLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        ballLp.gravity = Gravity.TOP | Gravity.START;
        ballLp.x = 50;
        ballLp.y = 300;
        lastIdleBallX = ballLp.x;
        lastIdleBallY = ballLp.y;

        View ball = ballView.findViewById(R.id.floating_ball_container);
        ballCoreView = ballView.findViewById(R.id.floating_ball_core);
        ballDockHandleLeft = ballView.findViewById(R.id.ball_dock_handle_left);
        ballDockHandleRight = ballView.findViewById(R.id.ball_dock_handle_right);
        ballDockHandleBottom = ballView.findViewById(R.id.ball_dock_handle_bottom);
        ballStatusText = ballView.findViewById(R.id.ball_status_text);

        View.OnClickListener ballClickListener = v -> {
            if (ScriptRunner.isCurrentScriptRunning()) {
                host.hideProjectPanelDock();
                host.showRuntimeAwareProjectPanel();
            } else if (ballCollapsedToEdge) {
                expandIdleBallFromEdge();
            } else if (fanMenuView != null) {
                hideFanMenu();
            } else {
                showFanMenu();
            }
        };
        View.OnLongClickListener ballLongClickListener = v -> {
            if (ScriptRunner.isCurrentScriptRunning()) {
                host.togglePauseState();
            } else {
                hideFanMenu();
                host.removeProjectPanel();
                host.showToast("已收起面板");
            }
            return true;
        };
        View.OnTouchListener dragListener = new DragTouchListener(ballLp, wm, ballView, host.getService(), true) {
            @Override
            protected void onDragEnd(int finalX, int finalY) {
                if (ScriptRunner.isCurrentScriptRunning() || ballCollapsedForRunning) {
                    dockForRunning(finalX, finalY);
                } else {
                    handleIdleBallDragEnd(finalX, finalY);
                }
            }
        };

        bindBallTarget(ball, ballClickListener, ballLongClickListener, dragListener);
        bindBallTarget(ballStatusText, ballClickListener, ballLongClickListener, dragListener);
        bindBallTarget(ballDockHandleLeft, ballClickListener, ballLongClickListener, dragListener);
        bindBallTarget(ballDockHandleRight, ballClickListener, ballLongClickListener, dragListener);
        bindBallTarget(ballDockHandleBottom, ballClickListener, ballLongClickListener, dragListener);

        wm.addView(ballView, ballLp);
        ballView.post(this::applyBallPresentation);
    }

    void remove() {
        hideFanMenu();
        host.hideProjectPanelDock();
        if (ballView == null) {
            return;
        }
        try {
            host.getWindowManager().removeView(ballView);
        } catch (Exception e) {
            Log.w(TAG, "remove ball view failed", e);
        }
        ballView = null;
        ballLp = null;
        ballCoreView = null;
        ballStatusText = null;
        ballDockHandleLeft = null;
        ballDockHandleRight = null;
        ballDockHandleBottom = null;
    }

    void setVisible(boolean visible) {
        if (ballView == null) {
            return;
        }
        ballView.animate().cancel();
        if (!visible) {
            hideFanMenu();
            if (ballStatusText != null) {
                ballStatusText.setVisibility(View.GONE);
            }
            ballView.setAlpha(0f);
            ballView.setVisibility(View.INVISIBLE);
            return;
        }
        ballView.setVisibility(View.VISIBLE);
        ballView.setAlpha(1f);
        applyBallPresentation();
        if (ballLp != null && ballView.getParent() != null) {
            try {
                host.getWindowManager().updateViewLayout(ballView, ballLp);
            } catch (Exception e) {
                Log.w(TAG, "restore ball layout failed", e);
            }
        }
    }

    void refreshPresentation() {
        applyBallPresentation();
    }

    void dockForRunning(int anchorX, int anchorY) {
        if (ballView == null || ballLp == null) {
            return;
        }
        ballDockEdge = resolveDockEdge(anchorX, anchorY, false);
        ballCollapsedForRunning = true;
        ballCollapsedToEdge = false;
        applyBallPresentation();
        applyDockedBallPosition(anchorX, anchorY);
    }

    void dockForRunningFromCurrentPosition() {
        int anchorX = ballLp != null ? ballLp.x : lastIdleBallX;
        int anchorY = ballLp != null ? ballLp.y : lastIdleBallY;
        dockForRunning(anchorX, anchorY);
    }

    void restoreAfterRun() {
        if (ballView == null || ballLp == null) {
            return;
        }
        ballCollapsedForRunning = false;
        ballCollapsedToEdge = false;
        applyBallPresentation();
        int[] screen = host.getScreenSizePx();
        int[] size = measureFloatingBallSize();
        int margin = host.dp(BALL_EDGE_MARGIN_DP);
        ballLp.x = Math.max(margin, Math.min(lastIdleBallX, Math.max(margin, screen[0] - size[0] - margin)));
        ballLp.y = Math.max(margin, Math.min(lastIdleBallY, Math.max(margin, screen[1] - size[1] - margin)));
        try {
            host.getWindowManager().updateViewLayout(ballView, ballLp);
        } catch (Exception e) {
            Log.w(TAG, "restore ball after run failed", e);
        }
    }

    private void bindBallTarget(
            View view,
            View.OnClickListener clickListener,
            View.OnLongClickListener longClickListener,
            View.OnTouchListener touchListener
    ) {
        if (view == null) {
            return;
        }
        view.setOnClickListener(clickListener);
        view.setOnLongClickListener(longClickListener);
        view.setOnTouchListener(touchListener);
    }

    private void handleIdleBallDragEnd(int finalX, int finalY) {
        if (resolveDockEdge(finalX, finalY, true) != BALL_DOCK_EDGE_NONE) {
            collapseIdleBallToEdge(finalX, finalY);
            return;
        }
        rememberIdleBallPosition(finalX, finalY);
    }

    private int[] measureFloatingBallSize() {
        if (ballView == null) {
            return new int[]{host.dp(56), host.dp(56)};
        }
        int width = ballView.getWidth();
        int height = ballView.getHeight();
        if (width > 0 && height > 0) {
            return new int[]{width, height};
        }
        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        ballView.measure(widthSpec, heightSpec);
        width = ballView.getMeasuredWidth();
        height = ballView.getMeasuredHeight();
        if (width <= 0) {
            width = host.dp(56);
        }
        if (height <= 0) {
            height = host.dp(56);
        }
        return new int[]{width, height};
    }

    private void rememberIdleBallPosition(int finalX, int finalY) {
        lastIdleBallX = finalX;
        lastIdleBallY = finalY;
        ballDockEdge = resolveDockEdge(finalX, finalY, false);
        ballCollapsedForRunning = false;
        ballCollapsedToEdge = false;
        applyBallPresentation();
    }

    private int resolveDockEdge(int anchorX, int anchorY, boolean requireThreshold) {
        int[] screen = host.getScreenSizePx();
        int[] size = measureFloatingBallSize();
        int leftDistance = Math.max(0, anchorX);
        int rightDistance = Math.max(0, screen[0] - size[0] - anchorX);
        int bottomDistance = Math.max(0, screen[1] - size[1] - anchorY);
        int threshold = host.dp(BALL_AUTO_DOCK_THRESHOLD_DP);
        int minDistance = leftDistance;
        int edge = BALL_DOCK_EDGE_LEFT;
        if (rightDistance < minDistance) {
            minDistance = rightDistance;
            edge = BALL_DOCK_EDGE_RIGHT;
        }
        if (bottomDistance < minDistance) {
            minDistance = bottomDistance;
            edge = BALL_DOCK_EDGE_BOTTOM;
        }
        if (requireThreshold && minDistance > threshold) {
            return BALL_DOCK_EDGE_NONE;
        }
        return edge;
    }

    private int clampBallAxis(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    private void applyDockedBallPosition(int anchorX, int anchorY) {
        if (ballView == null || ballLp == null) {
            return;
        }
        int[] screen = host.getScreenSizePx();
        int[] dockSize = measureFloatingBallSize();
        int overlap = host.dp(BALL_EDGE_OVERLAP_DP);
        switch (ballDockEdge) {
            case BALL_DOCK_EDGE_LEFT:
                ballLp.x = -overlap;
                ballLp.y = clampBallAxis(anchorY, 0, Math.max(0, screen[1] - dockSize[1]));
                break;
            case BALL_DOCK_EDGE_RIGHT:
                ballLp.x = Math.max(0, screen[0] - dockSize[0]) + overlap;
                ballLp.y = clampBallAxis(anchorY, 0, Math.max(0, screen[1] - dockSize[1]));
                break;
            case BALL_DOCK_EDGE_BOTTOM:
                ballLp.x = clampBallAxis(anchorX, 0, Math.max(0, screen[0] - dockSize[0]));
                ballLp.y = Math.max(0, screen[1] - dockSize[1]);
                break;
            default:
                break;
        }
        try {
            host.getWindowManager().updateViewLayout(ballView, ballLp);
        } catch (Exception e) {
            Log.w(TAG, "apply docked ball position failed", e);
        }
    }

    private void collapseIdleBallToEdge(int anchorX, int anchorY) {
        if (ballView == null || ballLp == null) {
            return;
        }
        int edge = resolveDockEdge(anchorX, anchorY, true);
        if (edge == BALL_DOCK_EDGE_NONE) {
            rememberIdleBallPosition(anchorX, anchorY);
            return;
        }
        ballDockEdge = edge;
        ballCollapsedToEdge = true;
        ballCollapsedForRunning = false;
        applyBallPresentation();
        applyDockedBallPosition(anchorX, anchorY);
    }

    private void expandIdleBallFromEdge() {
        if (ballView == null || ballLp == null) {
            return;
        }
        ballCollapsedToEdge = false;
        ballCollapsedForRunning = false;
        applyBallPresentation();
        int[] screen = host.getScreenSizePx();
        int[] size = measureFloatingBallSize();
        int margin = host.dp(BALL_EDGE_MARGIN_DP);
        int targetX = clampBallAxis(ballLp.x, margin, Math.max(margin, screen[0] - size[0] - margin));
        int targetY = clampBallAxis(ballLp.y, margin, Math.max(margin, screen[1] - size[1] - margin));
        if (ballDockEdge == BALL_DOCK_EDGE_LEFT) {
            targetX = margin;
        } else if (ballDockEdge == BALL_DOCK_EDGE_RIGHT) {
            targetX = Math.max(margin, screen[0] - size[0] - margin);
        } else if (ballDockEdge == BALL_DOCK_EDGE_BOTTOM) {
            targetY = Math.max(margin, screen[1] - size[1] - margin);
        }
        ballLp.x = targetX;
        ballLp.y = targetY;
        lastIdleBallX = targetX;
        lastIdleBallY = targetY;
        try {
            host.getWindowManager().updateViewLayout(ballView, ballLp);
        } catch (Exception e) {
            Log.w(TAG, "expand idle ball from edge failed", e);
        }
    }

    private void applyBallPresentation() {
        if (ballView == null) {
            return;
        }
        boolean running = ballCollapsedForRunning || ScriptRunner.isCurrentScriptRunning();
        boolean collapsed = running || ballCollapsedToEdge;
        if (ballCoreView != null) {
            ballCoreView.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
        if (ballStatusText != null) {
            ballStatusText.setVisibility(View.GONE);
        }
        String handleLabel = running ? (host.isPaused() ? "停" : "运") : "A";
        if (ballDockHandleLeft != null) {
            ballDockHandleLeft.setText(handleLabel);
            ballDockHandleLeft.setVisibility(collapsed && ballDockEdge == BALL_DOCK_EDGE_LEFT ? View.VISIBLE : View.GONE);
        }
        if (ballDockHandleRight != null) {
            ballDockHandleRight.setText(handleLabel);
            ballDockHandleRight.setVisibility(collapsed && ballDockEdge == BALL_DOCK_EDGE_RIGHT ? View.VISIBLE : View.GONE);
        }
        if (ballDockHandleBottom != null) {
            ballDockHandleBottom.setText(handleLabel);
            ballDockHandleBottom.setVisibility(collapsed && ballDockEdge == BALL_DOCK_EDGE_BOTTOM ? View.VISIBLE : View.GONE);
        }
    }

    private void showFanMenu() {
        if (fanMenuView != null || ballView == null || ballLp == null) {
            return;
        }

        fanMenuView = LayoutInflater.from(host.getContext()).inflate(R.layout.floating_action_menu, null);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        fanMenuLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        fanMenuLp.gravity = Gravity.TOP | Gravity.START;
        fanMenuLp.x = ballLp.x;
        fanMenuLp.y = Math.max(0, ballLp.y - host.dp(112));

        View btnPanel = fanMenuView.findViewById(R.id.fan_btn_panel);
        View btnClose = fanMenuView.findViewById(R.id.fan_btn_close);

        btnPanel.setOnClickListener(v -> {
            hideFanMenu();
            host.showProjectPanel();
        });
        btnClose.setOnClickListener(v -> hideFanMenu());

        host.getWindowManager().addView(fanMenuView, fanMenuLp);
        animateFanButton(btnPanel, 0);
        animateFanButton(btnClose, 90);
    }

    private void animateFanButton(View btn, long delayMs) {
        btn.setAlpha(0f);
        btn.setTranslationY(btn.getTranslationY());
        btn.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(200)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();
    }

    private void hideFanMenu() {
        if (fanMenuView == null) {
            return;
        }
        View btnPanel = fanMenuView.findViewById(R.id.fan_btn_panel);
        View btnClose = fanMenuView.findViewById(R.id.fan_btn_close);
        View toRemove = fanMenuView;
        btnClose.animate().alpha(0f).translationY(-20).setDuration(100).setStartDelay(0).start();
        btnPanel.animate().alpha(0f).translationY(-20).setDuration(100).setStartDelay(40)
                .withEndAction(() -> {
                    try {
                        host.getWindowManager().removeView(toRemove);
                    } catch (Exception ignored) {
                    }
                }).start();
        fanMenuView = null;
        fanMenuLp = null;
    }
}
