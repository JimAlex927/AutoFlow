package com.auto.master.floatwin;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.auto.master.R;
import com.auto.master.auto.ScriptRunner;
import com.auto.master.auto.ScriptSession;
import com.auto.master.auto.ScriptSessionSnapshot;

final class FloatBallOverlayController {
    interface Host {
        FloatWindowService getService();
        Context getContext();
        WindowManager getWindowManager();
        int dp(int value);
        int[] getScreenSizePx();
        void hideProjectPanelDock();
        void showRuntimeAwareProjectPanel();
        void toggleActivePauseState();
        void stopActiveScriptFromUi();
        void removeProjectPanel();
        void showProjectPanel();
        void toggleRuntimeLogPanel();
        void showScriptSessionDialog();
        void captureToolScreenshot();
        void performBackAction();
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
    private View ballDockHandleLeft;
    private View ballDockHandleRight;
    private View ballDockHandleBottom;
    private TextView ballStatusText;

    private View fanMenuView;
    private WindowManager.LayoutParams fanMenuLp;
    private boolean fanMenuAttached = false;
    private boolean fanMenuShowing = false;
    private boolean fanMenuDragged = false;
    private boolean fanMenuWasRuntimeMode = false;
    private boolean toolsDockVisible = false;

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
                if (isFanMenuShowing()) {
                    hideFanMenu();
                } else {
                    showFanMenu();
                }
            } else if (ballCollapsedToEdge) {
                showFanMenu();
            } else if (isFanMenuShowing()) {
                hideFanMenu();
            } else {
                showFanMenu();
            }
        };
        View.OnLongClickListener ballLongClickListener = v -> {
            if (ScriptRunner.isCurrentScriptRunning()) {
                host.toggleActivePauseState();
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
        detachFanMenu();
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
        syncFanMenuWithRuntimeState();
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
        if (isFanMenuShowing() && !ScriptRunner.isCurrentScriptRunning()) {
            hideFanMenu();
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
        if (isFanMenuShowing()) {
            syncFanMenuWithRuntimeState();
            ballView.setVisibility(View.INVISIBLE);
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
        if (ballDockHandleLeft != null) {
            ballDockHandleLeft.setVisibility(collapsed && ballDockEdge == BALL_DOCK_EDGE_LEFT ? View.VISIBLE : View.GONE);
        }
        if (ballDockHandleRight != null) {
            ballDockHandleRight.setVisibility(collapsed && ballDockEdge == BALL_DOCK_EDGE_RIGHT ? View.VISIBLE : View.GONE);
        }
        if (ballDockHandleBottom != null) {
            ballDockHandleBottom.setVisibility(collapsed && ballDockEdge == BALL_DOCK_EDGE_BOTTOM ? View.VISIBLE : View.GONE);
        }
    }

    private void showFanMenu() {
        if (ballView == null || ballLp == null || isFanMenuShowing()) {
            return;
        }
        ensureFanMenu();
        if (fanMenuView == null || fanMenuLp == null) {
            return;
        }
        fanMenuDragged = false;
        refreshFanMenuRuntimeState();
        fanMenuWasRuntimeMode = ScriptRunner.isCurrentScriptRunning();
        positionFanMenuNearBall();
        updateFanMenuLayout();
        fanMenuShowing = true;
        ballView.setVisibility(View.INVISIBLE);
        fanMenuView.animate().cancel();
        fanMenuView.setScaleX(0.96f);
        fanMenuView.setScaleY(0.96f);
        fanMenuView.setAlpha(0f);
        fanMenuView.setVisibility(View.VISIBLE);
        fanMenuView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(110)
                .start();
    }

    private void ensureFanMenu() {
        if (fanMenuView != null && fanMenuAttached) {
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

        View btnPanel = fanMenuView.findViewById(R.id.fan_btn_panel);
        View btnLog = fanMenuView.findViewById(R.id.fan_btn_log);
        View btnSession = fanMenuView.findViewById(R.id.fan_btn_session);
        View btnTools = fanMenuView.findViewById(R.id.fan_btn_tools);
        View btnPause = fanMenuView.findViewById(R.id.fan_btn_pause);
        View btnStop = fanMenuView.findViewById(R.id.fan_btn_stop);
        View btnClose = fanMenuView.findViewById(R.id.fan_btn_close);
        View toolsDock = fanMenuView.findViewById(R.id.fan_tools_dock);
        View toolCapture = fanMenuView.findViewById(R.id.fan_tool_capture);
        View toolBack = fanMenuView.findViewById(R.id.fan_tool_back);
        View toolClose = fanMenuView.findViewById(R.id.fan_tool_close);
        bindFanMenuDrag(fanMenuView, btnPanel, btnLog, btnSession, btnTools, btnPause, btnStop,
                btnClose, toolsDock, toolCapture, toolBack, toolClose);
        btnPanel.setOnClickListener(v -> {
            if (ScriptRunner.isCurrentScriptRunning()) {
                host.hideProjectPanelDock();
                host.showRuntimeAwareProjectPanel();
            } else {
                host.showProjectPanel();
            }
        });
        btnLog.setOnClickListener(v -> host.toggleRuntimeLogPanel());
        btnSession.setOnClickListener(v -> host.showScriptSessionDialog());
        btnTools.setOnClickListener(v -> toggleToolsDock());
        toolCapture.setOnClickListener(v -> host.captureToolScreenshot());
        toolBack.setOnClickListener(v -> host.performBackAction());
        toolClose.setOnClickListener(v -> setToolsDockVisible(false));
        btnPause.setOnClickListener(v -> {
            host.toggleActivePauseState();
            refreshFanMenuRuntimeState();
        });
        btnStop.setOnClickListener(v -> {
            hideFanMenu();
            host.stopActiveScriptFromUi();
        });
        btnClose.setOnClickListener(v -> hideFanMenu());
        fanMenuView.setAlpha(0f);
        fanMenuView.setVisibility(View.INVISIBLE);
        positionFanMenuNearBall();
        host.getWindowManager().addView(fanMenuView, fanMenuLp);
        fanMenuAttached = true;
    }

    private void refreshFanMenuRuntimeState() {
        if (fanMenuView == null) {
            return;
        }
        boolean running = hasInteractiveRuntimeSession();
        fanMenuWasRuntimeMode = fanMenuWasRuntimeMode || running;
        View pause = fanMenuView.findViewById(R.id.fan_btn_pause);
        ImageView pauseIcon = fanMenuView.findViewById(R.id.fan_icon_pause);
        View stop = fanMenuView.findViewById(R.id.fan_btn_stop);
        ScriptSessionSnapshot snapshot = findInteractiveQuickActionSession();
        if (pause != null) {
            pause.setVisibility(running ? View.VISIBLE : View.GONE);
        }
        if (pauseIcon != null) {
            boolean paused = snapshot != null && snapshot.state == ScriptSession.State.PAUSED;
            pauseIcon.setImageResource(paused ? R.drawable.ic_float_menu_play : R.drawable.ic_float_menu_pause);
        }
        if (stop != null) {
            stop.setVisibility(running ? View.VISIBLE : View.GONE);
        }
        applyMenuModeVisibility(false);
    }

    private void toggleToolsDock() {
        setToolsDockVisible(!toolsDockVisible);
    }

    private void setToolsDockVisible(boolean visible) {
        if (fanMenuView == null) {
            toolsDockVisible = visible;
            return;
        }
        toolsDockVisible = visible;
        applyMenuModeVisibility(true);
        fanMenuView.post(() -> {
            clampFanMenuInsideScreen();
            updateFanMenuLayout();
        });
    }

    private void applyMenuModeVisibility(boolean animate) {
        if (fanMenuView == null) {
            return;
        }
        View menuCard = fanMenuView.findViewById(R.id.fan_menu_card);
        View toolsDock = fanMenuView.findViewById(R.id.fan_tools_dock);
        if (menuCard != null) {
            menuCard.animate().cancel();
            if (toolsDockVisible) {
                menuCard.setVisibility(View.GONE);
            } else {
                menuCard.setAlpha(1f);
                menuCard.setScaleX(1f);
                menuCard.setVisibility(View.VISIBLE);
            }
        }
        if (toolsDock != null) {
            toolsDock.animate().cancel();
            if (toolsDockVisible) {
                toolsDock.setAlpha(0f);
                toolsDock.setScaleX(0.96f);
                toolsDock.setVisibility(View.VISIBLE);
                if (animate) {
                    toolsDock.animate().alpha(1f).scaleX(1f).setDuration(110).start();
                } else {
                    toolsDock.setAlpha(1f);
                    toolsDock.setScaleX(1f);
                }
            } else if (animate && toolsDock.getVisibility() == View.VISIBLE) {
                toolsDock.animate()
                        .alpha(0f)
                        .scaleX(0.96f)
                        .setDuration(90)
                        .withEndAction(() -> toolsDock.setVisibility(View.GONE))
                        .start();
            } else {
                toolsDock.setVisibility(View.GONE);
            }
        }
    }

    private void syncFanMenuWithRuntimeState() {
        if (fanMenuView == null) {
            return;
        }
        boolean running = hasInteractiveRuntimeSession();
        refreshFanMenuRuntimeState();
        if (isFanMenuShowing() && fanMenuWasRuntimeMode && !running) {
            ballCollapsedForRunning = false;
            ballCollapsedToEdge = false;
            hideFanMenu();
        }
    }

    private ScriptSessionSnapshot findInteractiveQuickActionSession() {
        String activeSessionId = ScriptRunner.getActiveSessionId();
        ScriptSessionSnapshot active = TextUtils.isEmpty(activeSessionId)
                ? null
                : ScriptRunner.getSessionSnapshot(activeSessionId);
        if (isInteractiveRuntimeSession(active)) {
            return active;
        }
        for (ScriptSessionSnapshot snapshot : ScriptRunner.listSessionSnapshots()) {
            if (isInteractiveRuntimeSession(snapshot)) {
                return snapshot;
            }
        }
        return null;
    }

    private boolean hasInteractiveRuntimeSession() {
        for (ScriptSessionSnapshot snapshot : ScriptRunner.listSessionSnapshots()) {
            if (isInteractiveRuntimeSession(snapshot)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInteractiveRuntimeSession(ScriptSessionSnapshot snapshot) {
        if (snapshot == null || snapshot.state == null) {
            return false;
        }
        return snapshot.state == ScriptSession.State.QUEUED
                || snapshot.state == ScriptSession.State.RUNNING
                || snapshot.state == ScriptSession.State.PAUSED;
    }

    private void positionFanMenuNearBall() {
        if (fanMenuView == null || fanMenuLp == null || ballLp == null) {
            return;
        }
        int[] screen = host.getScreenSizePx();
        int[] ballSize = measureFloatingBallSize();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        fanMenuView.measure(widthSpec, heightSpec);
        int menuW = Math.max(fanMenuView.getMeasuredWidth(), host.dp(64));
        int menuH = Math.max(fanMenuView.getMeasuredHeight(), host.dp(144));
        int ballCenterX = ballLp.x + ballSize[0] / 2;
        int ballCenterY = ballLp.y + ballSize[1] / 2;
        int targetX;
        int edge = resolveDockEdge(ballLp.x, ballLp.y, false);
        if (edge == BALL_DOCK_EDGE_LEFT) {
            targetX = 0;
        } else if (edge == BALL_DOCK_EDGE_RIGHT) {
            targetX = screen[0] - menuW;
        } else {
            targetX = ballCenterX - menuW / 2;
        }
        int targetY = ballCenterY - menuH / 2;
        fanMenuLp.x = clampBallAxis(targetX, 0, Math.max(0, screen[0] - menuW));
        fanMenuLp.y = clampBallAxis(targetY, 0, Math.max(0, screen[1] - menuH));
    }

    private void bindFanMenuDrag(View... targets) {
        View.OnTouchListener dragListener = createFanMenuDragListener();
        for (View target : targets) {
            if (target != null) {
                target.setOnTouchListener(dragListener);
            }
        }
    }

    private View.OnTouchListener createFanMenuDragListener() {
        return new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downRawX;
            private float downRawY;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (fanMenuLp == null || fanMenuView == null) {
                    return false;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = fanMenuLp.x;
                        startY = fanMenuLp.y;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        dragging = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(event.getRawX() - downRawX);
                        int dy = Math.round(event.getRawY() - downRawY);
                        if (!dragging && Math.abs(dx) < host.dp(3) && Math.abs(dy) < host.dp(3)) {
                            return false;
                        }
                        dragging = true;
                        fanMenuDragged = true;
                        moveFanMenuTo(startX + dx, startY + dy);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging) {
                            collapseFanMenuToEdgeIfClose();
                        }
                        return dragging;
                    default:
                        return false;
                }
            }
        };
    }

    private void moveFanMenuTo(int x, int y) {
        if (fanMenuView == null || fanMenuLp == null) {
            return;
        }
        int[] screen = host.getScreenSizePx();
        int menuW = Math.max(fanMenuView.getWidth(), fanMenuView.getMeasuredWidth());
        int menuH = Math.max(fanMenuView.getHeight(), fanMenuView.getMeasuredHeight());
        if (menuW <= 0) {
            menuW = host.dp(64);
        }
        if (menuH <= 0) {
            menuH = host.dp(144);
        }
        fanMenuLp.x = clampBallAxis(x, 0, Math.max(0, screen[0] - menuW));
        fanMenuLp.y = clampBallAxis(y, 0, Math.max(0, screen[1] - menuH));
        try {
            host.getWindowManager().updateViewLayout(fanMenuView, fanMenuLp);
        } catch (Exception e) {
            Log.w(TAG, "move fan menu failed", e);
        }
    }

    private boolean collapseFanMenuToEdgeIfClose() {
        if (fanMenuView == null || fanMenuLp == null) {
            return false;
        }
        int[] screen = host.getScreenSizePx();
        int menuW = Math.max(fanMenuView.getWidth(), host.dp(64));
        int menuH = Math.max(fanMenuView.getHeight(), host.dp(144));
        int threshold = host.dp(BALL_AUTO_DOCK_THRESHOLD_DP);
        int left = fanMenuLp.x;
        int right = screen[0] - menuW - fanMenuLp.x;
        int bottom = screen[1] - menuH - fanMenuLp.y;
        int min = Math.min(left, Math.min(right, bottom));
        if (min > threshold) {
            return false;
        }
        if (min == left) {
            collapseFanMenuToDockHandle(BALL_DOCK_EDGE_LEFT, fanMenuLp.y + menuH / 2);
        } else if (min == right) {
            collapseFanMenuToDockHandle(BALL_DOCK_EDGE_RIGHT, fanMenuLp.y + menuH / 2);
        } else {
            collapseFanMenuToDockHandle(BALL_DOCK_EDGE_BOTTOM, fanMenuLp.x + menuW / 2);
        }
        return true;
    }

    private void collapseFanMenuToDockHandle(int edge, int anchor) {
        if (ballView == null || ballLp == null || fanMenuView == null) {
            return;
        }
        fanMenuView.animate().cancel();
        fanMenuView.setAlpha(0f);
        fanMenuView.setScaleX(0.96f);
        fanMenuView.setScaleY(0.96f);
        fanMenuView.setVisibility(View.INVISIBLE);
        fanMenuShowing = false;
        fanMenuDragged = false;
        ballView.setVisibility(View.VISIBLE);
        ballView.setAlpha(1f);
        ballDockEdge = edge;
        if (ScriptRunner.isCurrentScriptRunning() || ballCollapsedForRunning) {
            ballCollapsedForRunning = true;
            ballCollapsedToEdge = false;
        } else {
            ballCollapsedForRunning = false;
            ballCollapsedToEdge = true;
        }
        int[] screen = host.getScreenSizePx();
        int[] ballSize = measureFloatingBallSize();
        int anchorX = edge == BALL_DOCK_EDGE_RIGHT
                ? Math.max(0, screen[0] - ballSize[0])
                : edge == BALL_DOCK_EDGE_BOTTOM
                        ? clampBallAxis(anchor - ballSize[0] / 2, 0, Math.max(0, screen[0] - ballSize[0]))
                        : 0;
        int anchorY = edge == BALL_DOCK_EDGE_BOTTOM
                ? Math.max(0, screen[1] - ballSize[1])
                : clampBallAxis(anchor - ballSize[1] / 2, 0, Math.max(0, screen[1] - ballSize[1]));
        applyBallPresentation();
        applyDockedBallPosition(anchorX, anchorY);
    }

    private void updateFanMenuLayout() {
        if (fanMenuView == null || fanMenuLp == null || !fanMenuAttached) {
            return;
        }
        try {
            host.getWindowManager().updateViewLayout(fanMenuView, fanMenuLp);
        } catch (Exception e) {
            Log.w(TAG, "update fan menu layout failed", e);
        }
    }

    private void clampFanMenuInsideScreen() {
        if (fanMenuView == null || fanMenuLp == null) {
            return;
        }
        int[] screen = host.getScreenSizePx();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        fanMenuView.measure(widthSpec, heightSpec);
        int menuW = Math.max(fanMenuView.getMeasuredWidth(), host.dp(64));
        int menuH = Math.max(fanMenuView.getMeasuredHeight(), host.dp(144));
        fanMenuLp.x = clampBallAxis(fanMenuLp.x, 0, Math.max(0, screen[0] - menuW));
        fanMenuLp.y = clampBallAxis(fanMenuLp.y, 0, Math.max(0, screen[1] - menuH));
    }

    private void hideFanMenu() {
        if (!isFanMenuShowing()) {
            return;
        }
        WindowManager.LayoutParams closingLp = fanMenuLp;
        boolean moved = fanMenuDragged;
        fanMenuShowing = false;
        fanMenuDragged = false;
        View hidingView = fanMenuView;
        hidingView.animate().cancel();
        hidingView.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(90)
                .withEndAction(() -> {
                    hidingView.setVisibility(View.INVISIBLE);
                    restoreBallAfterFanMenu(hidingView, closingLp, moved);
                })
                .start();
    }

    private boolean isFanMenuShowing() {
        return fanMenuView != null && fanMenuShowing && fanMenuView.getVisibility() == View.VISIBLE;
    }

    private void detachFanMenu() {
        if (fanMenuView == null || !fanMenuAttached) {
            return;
        }
        fanMenuView.animate().cancel();
        try {
            host.getWindowManager().removeView(fanMenuView);
        } catch (Exception e) {
            Log.w(TAG, "detach fan menu failed", e);
        }
        fanMenuView = null;
        fanMenuLp = null;
        fanMenuAttached = false;
        fanMenuShowing = false;
        fanMenuDragged = false;
        fanMenuWasRuntimeMode = false;
    }

    private void restoreBallAfterFanMenu(View removedMenu, WindowManager.LayoutParams menuLp, boolean moved) {
        if (ballView == null || ballLp == null) {
            return;
        }
        if (moved && menuLp != null) {
            moveBallNearMenu(removedMenu, menuLp);
        }
        ballView.setVisibility(View.VISIBLE);
        ballView.setAlpha(1f);
        applyBallPresentation();
        try {
            host.getWindowManager().updateViewLayout(ballView, ballLp);
        } catch (Exception e) {
            Log.w(TAG, "restore ball after fan menu failed", e);
        }
    }

    private void moveBallNearMenu(View menu, WindowManager.LayoutParams menuLp) {
        int[] screen = host.getScreenSizePx();
        int[] ballSize = measureFloatingBallSize();
        int menuW = Math.max(menu.getWidth(), menu.getMeasuredWidth());
        int menuH = Math.max(menu.getHeight(), menu.getMeasuredHeight());
        if (menuW <= 0) {
            menuW = host.dp(64);
        }
        if (menuH <= 0) {
            menuH = host.dp(144);
        }
        int menuCenterX = menuLp.x + menuW / 2;
        int menuCenterY = menuLp.y + menuH / 2;
        if (ScriptRunner.isCurrentScriptRunning() || ballCollapsedForRunning) {
            int anchorX = menuCenterX < screen[0] / 2 ? 0 : Math.max(0, screen[0] - ballSize[0]);
            dockForRunning(anchorX, menuCenterY - ballSize[1] / 2);
            return;
        }
        int margin = host.dp(BALL_EDGE_MARGIN_DP);
        int targetX = clampBallAxis(menuCenterX - ballSize[0] / 2,
                margin,
                Math.max(margin, screen[0] - ballSize[0] - margin));
        int targetY = clampBallAxis(menuCenterY - ballSize[1] / 2,
                margin,
                Math.max(margin, screen[1] - ballSize[1] - margin));
        ballLp.x = targetX;
        ballLp.y = targetY;
        rememberIdleBallPosition(targetX, targetY);
    }
}
