package com.auto.master.floatwin;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.io.File;
import java.util.Locale;

final class ProjectPanelUiHelper {

    static final int LEVEL_PROJECT = 0;
    static final int LEVEL_TASK = 1;
    static final int LEVEL_OPERATION = 2;

    interface Host {
        FloatWindowService getService();

        Context getContext();

        WindowManager getWindowManager();

        Handler getUiHandler();

        int dp(int value);

        int[] getScreenSizePx();

        void initDialogFactory();

        void clampPanelToScreen(@Nullable WindowManager.LayoutParams lp);

        void adaptProjectPanelSizeToCurrentScreen(@Nullable WindowManager.LayoutParams lp);

        int getSharedPanelX();

        int getSharedPanelY();

        void rememberSharedPanelPosition(@Nullable WindowManager.LayoutParams lp);

        boolean shouldRefreshProjectPanelContent();

        void refreshCurrentLevelList();

        void hideRunningPanel();

        int getProjectPanelLevel();

        @Nullable
        File getCurrentProjectDir();

        @Nullable
        File getCurrentTaskDir();

        void loadOperations(File taskDir);

        @Nullable
        String getCurrentRunningOperationId();

        boolean isScriptActiveForUi();

        boolean isPaused();

        boolean isOperationBatchMode();

        int getBatchSelectedOperationCount();

        String getRuntimeStatusText();

        int getRuntimeStatusColor();

        int getCurrentOperationIndex();

        int getTotalOperationCount();

        String getCurrentRunningOperationName();

        String getCurrentRunningProject();

        String getCurrentRunningTask();

        boolean selectOperationInProjectPanel(String operationId);

        int findOperationPositionInProjectPanel(String operationId);

        void showToast(String message);

        void onProjectPanelBackClick();

        void onProjectPanelAddClick();

        boolean onProjectPanelAddLongClick();

        void onProjectPanelRefreshClick();

        boolean onProjectPanelRefreshLongClick();

        void onProjectPanelSearchQueryChanged(String query);

        void cancelPendingProjectPanelSearchRefresh();

        void setupProjectPanelBusinessActions(View panelView);

        @Nullable
        View getProjectPanelView();

        void setProjectPanelView(@Nullable View view);

        @Nullable
        WindowManager.LayoutParams getProjectPanelLayoutParams();

        void setProjectPanelLayoutParams(@Nullable WindowManager.LayoutParams lp);

        @Nullable
        View getProjectPanelDockView();

        void setProjectPanelDockView(@Nullable View view);

        @Nullable
        WindowManager.LayoutParams getProjectPanelDockLayoutParams();

        void setProjectPanelDockLayoutParams(@Nullable WindowManager.LayoutParams lp);

        boolean isProjectPanelDockOnLeft();

        void setProjectPanelDockOnLeft(boolean onLeft);

        @Nullable
        RecyclerView getProjectPanelRecyclerView();

        void setProjectPanelRecyclerView(@Nullable RecyclerView recyclerView);
    }

    private static final int PROJECT_PANEL_DEFAULT_W_DP = 320;
    private static final int PROJECT_PANEL_DEFAULT_H_DP = 440;
    private static final int PROJECT_PANEL_MIN_W_DP = 220;
    private static final int PROJECT_PANEL_MIN_H_DP = 240;
    private static final float PROJECT_PANEL_MAX_H_RATIO_LANDSCAPE = 0.78f;
    private static final int PROJECT_PANEL_DOCK_W_DP = 16;
    private static final int PROJECT_PANEL_DOCK_H_DP = 112;
    private static final int PROJECT_PANEL_DOCK_TRIGGER_DP = 18;
    private static final int PROJECT_PANEL_DOCK_MARGIN_DP = 6;
    private static final long SEARCH_REFRESH_DELAY_MS = 120L;

    private final Host host;

    ProjectPanelUiHelper(Host host) {
        this.host = host;
    }

    void onDestroy() {
        detachProjectPanel();
        detachProjectPanelDock();
        host.setProjectPanelView(null);
        host.setProjectPanelLayoutParams(null);
        host.setProjectPanelDockView(null);
        host.setProjectPanelDockLayoutParams(null);
        host.setProjectPanelRecyclerView(null);
    }

    void onConfigurationChanged() {
        WindowManager.LayoutParams panelLp = host.getProjectPanelLayoutParams();
        View panelView = host.getProjectPanelView();
        if (panelLp != null) {
            host.adaptProjectPanelSizeToCurrentScreen(panelLp);
            host.clampPanelToScreen(panelLp);
            if (panelView != null && isProjectPanelAttached()) {
                try {
                    host.getWindowManager().updateViewLayout(panelView, panelLp);
                } catch (Exception ignored) {
                }
            }
        }

        if (isProjectPanelDockAttached()) {
            updateProjectPanelDockLayout();
            View dockView = host.getProjectPanelDockView();
            WindowManager.LayoutParams dockLp = host.getProjectPanelDockLayoutParams();
            if (dockView != null && dockLp != null) {
                try {
                    host.getWindowManager().updateViewLayout(dockView, dockLp);
                } catch (Exception ignored) {
                }
            }
        }
    }

    void toggleProjectPanel() {
        if (!isProjectPanelVisible()) {
            showProjectPanel();
            return;
        }
        removeProjectPanel();
    }

    void showProjectPanel() {
        prepareProjectPanel();
        WindowManager.LayoutParams panelLp = host.getProjectPanelLayoutParams();
        View panelView = host.getProjectPanelView();
        if (panelLp == null || panelView == null) {
            return;
        }
        host.adaptProjectPanelSizeToCurrentScreen(panelLp);
        hideProjectPanelDock();
        panelView.animate().cancel();
        panelView.setAlpha(1f);
        panelView.setTranslationX(0f);
        panelView.setTranslationY(0f);
        attachProjectPanelIfNeeded();
        host.clampPanelToScreen(panelLp);
        if (isProjectPanelAttached()) {
            host.getWindowManager().updateViewLayout(panelView, panelLp);
        }
        panelView.setVisibility(View.VISIBLE);
        if (host.shouldRefreshProjectPanelContent()) {
            host.refreshCurrentLevelList();
        } else {
            updateUIForLevel();
        }
        syncProjectPanelRuntimeUi();
    }

    void showRuntimeAwareProjectPanel() {
        host.hideRunningPanel();
        if (host.getCurrentTaskDir() != null) {
            clearProjectPanelSearch();
        }
        showProjectPanel();
        File currentTaskDir = host.getCurrentTaskDir();
        if (currentTaskDir != null) {
            host.loadOperations(currentTaskDir);
        }
        updateUIForLevel();
        syncProjectPanelRuntimeUi();
        if (!TextUtils.isEmpty(host.getCurrentRunningOperationId())) {
            focusCurrentRunningOperation();
        }
    }

    void removeProjectPanel() {
        View panelView = host.getProjectPanelView();
        WindowManager.LayoutParams panelLp = host.getProjectPanelLayoutParams();
        if (panelView != null && panelLp != null && isProjectPanelAttached()) {
            panelView.animate().cancel();
            host.rememberSharedPanelPosition(panelLp);
            host.cancelPendingProjectPanelSearchRefresh();
            panelView.setVisibility(View.GONE);
            detachProjectPanel();
        }
        if (host.isScriptActiveForUi()) {
            showProjectPanelDock();
        } else {
            hideProjectPanelDock();
        }
    }

    void toggleSearch() {
        View panelView = host.getProjectPanelView();
        if (panelView == null) {
            return;
        }
        LinearLayout searchLayout = panelView.findViewById(R.id.ly_search);
        if (searchLayout == null) {
            return;
        }
        if (searchLayout.getVisibility() == View.VISIBLE) {
            searchLayout.setVisibility(View.GONE);
            return;
        }
        searchLayout.setVisibility(View.VISIBLE);
        EditText searchInput = panelView.findViewById(R.id.edt_search);
        if (searchInput != null) {
            searchInput.requestFocus();
        }
    }

    void clearProjectPanelSearch() {
        host.onProjectPanelSearchQueryChanged("");
        View panelView = host.getProjectPanelView();
        if (panelView == null) {
            return;
        }
        EditText searchInput = panelView.findViewById(R.id.edt_search);
        if (searchInput != null && searchInput.length() > 0) {
            host.cancelPendingProjectPanelSearchRefresh();
            searchInput.setText("");
        }
    }

    void prepareProjectPanel() {
        if (host.getProjectPanelView() != null && host.getProjectPanelLayoutParams() != null) {
            return;
        }

        View panelView = LayoutInflater.from(host.getContext()).inflate(R.layout.window_project_panel, null);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams panelLp = new WindowManager.LayoutParams(
                host.dp(PROJECT_PANEL_DEFAULT_W_DP),
                host.dp(PROJECT_PANEL_DEFAULT_H_DP),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.x = host.getSharedPanelX();
        panelLp.y = host.getSharedPanelY();
        host.adaptProjectPanelSizeToCurrentScreen(panelLp);

        host.setProjectPanelView(panelView);
        host.setProjectPanelLayoutParams(panelLp);

        host.initDialogFactory();
        setupProjectPanelShell(panelView, panelLp);
        host.setupProjectPanelBusinessActions(panelView);
    }

    private void setupProjectPanelShell(View panelView, WindowManager.LayoutParams panelLp) {
        panelView.findViewById(R.id.btn_close).setOnClickListener(v -> removeProjectPanel());
        panelView.findViewById(R.id.btn_back).setOnClickListener(v -> host.onProjectPanelBackClick());
        panelView.findViewById(R.id.btn_add).setOnClickListener(v -> host.onProjectPanelAddClick());
        panelView.findViewById(R.id.btn_add).setOnLongClickListener(v -> host.onProjectPanelAddLongClick());
        panelView.findViewById(R.id.btn_search).setOnClickListener(v -> toggleSearch());

        EditText searchInput = panelView.findViewById(R.id.edt_search);
        ImageView clearButton = panelView.findViewById(R.id.btn_clear_search);
        if (clearButton != null && searchInput != null) {
            clearButton.setOnClickListener(v -> searchInput.setText(""));
        }
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    host.onProjectPanelSearchQueryChanged(s == null ? "" : s.toString());
                    host.cancelPendingProjectPanelSearchRefresh();
                    host.getUiHandler().postDelayed(host::refreshCurrentLevelList, SEARCH_REFRESH_DELAY_MS);
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        RecyclerView recyclerView = panelView.findViewById(R.id.rv_content);
        host.setProjectPanelRecyclerView(recyclerView);
        if (recyclerView != null) {
            recyclerView.setItemAnimator(null);
            recyclerView.setItemViewCacheSize(12);
        }

        View refreshButton = panelView.findViewById(R.id.btn_refresh);
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> host.onProjectPanelRefreshClick());
            refreshButton.setOnLongClickListener(v -> host.onProjectPanelRefreshLongClick());
        }

        DragTouchListener dragTouchListener =
                new DragTouchListener(panelLp, host.getWindowManager(), panelView, host.getService(), true);
        bindDragTarget(panelView.findViewById(R.id.drag_header), dragTouchListener);
        bindDragTarget(panelView.findViewById(R.id.drag_handle), dragTouchListener);
        bindDragTarget(panelView.findViewById(R.id.breadcrumb_layout), dragTouchListener);
        bindDragTarget(panelView.findViewById(R.id.runtime_status_bar), dragTouchListener);
        bindDragTarget(panelView.findViewById(R.id.ly_search), dragTouchListener);
        bindDragTarget(panelView.findViewById(R.id.footer_drag_zone), dragTouchListener);
        bindDragTarget(panelView.findViewById(R.id.empty_view), dragTouchListener);
        int[] footerButtonIds = {
                R.id.btn_run,
                R.id.btn_edit,
                R.id.btn_move_up,
                R.id.btn_move_down,
                R.id.btn_batch
        };
        for (int footerButtonId : footerButtonIds) {
            bindDragTarget(panelView.findViewById(footerButtonId), dragTouchListener);
        }

        View resizeHandle = panelView.findViewById(R.id.resize_handle);
        if (resizeHandle != null) {
            resizeHandle.setOnTouchListener(new PanelResizeTouchListener(
                    panelLp,
                    host.getWindowManager(),
                    panelView,
                    host.getService(),
                    PROJECT_PANEL_MIN_W_DP,
                    PROJECT_PANEL_MIN_H_DP,
                    PROJECT_PANEL_MAX_H_RATIO_LANDSCAPE
            ));
        }
    }

    private void bindDragTarget(@Nullable View target, View.OnTouchListener listener) {
        if (target != null) {
            target.setOnTouchListener(listener);
        }
    }

    private boolean isProjectPanelDockAttached() {
        View dockView = host.getProjectPanelDockView();
        return dockView != null && dockView.getParent() != null;
    }

    private void prepareProjectPanelDock() {
        if (host.getProjectPanelDockView() != null && host.getProjectPanelDockLayoutParams() != null) {
            return;
        }

        View dockView = LayoutInflater.from(host.getContext()).inflate(R.layout.overlay_project_panel_dock, null);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams dockLp = new WindowManager.LayoutParams(
                host.dp(PROJECT_PANEL_DOCK_W_DP),
                host.dp(PROJECT_PANEL_DOCK_H_DP),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        dockLp.gravity = Gravity.TOP | Gravity.START;

        View dockTouch = dockView.findViewById(R.id.project_panel_dock_touch);
        if (dockTouch != null) {
            dockTouch.setOnTouchListener(new View.OnTouchListener() {
                private final int slopPx = ViewConfiguration.get(host.getContext()).getScaledTouchSlop();
                private float downRawX;
                private float downRawY;
                private boolean moved;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downRawX = event.getRawX();
                            downRawY = event.getRawY();
                            moved = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - downRawX;
                            float dy = event.getRawY() - downRawY;
                            if (!moved && (Math.abs(dx) > slopPx || Math.abs(dy) > slopPx)) {
                                moved = true;
                            }
                            if (moved && shouldRevealProjectPanelFromDock(dx, dy)) {
                                revealProjectPanelFromDock();
                                return true;
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            return true;
                        default:
                            return false;
                    }
                }
            });
        }

        host.setProjectPanelDockView(dockView);
        host.setProjectPanelDockLayoutParams(dockLp);
    }

    private boolean shouldRevealProjectPanelFromDock(float dx, float dy) {
        if (Math.abs(dx) < Math.abs(dy)) {
            return false;
        }
        int triggerPx = host.dp(PROJECT_PANEL_DOCK_TRIGGER_DP);
        return host.isProjectPanelDockOnLeft() ? dx >= triggerPx : dx <= -triggerPx;
    }

    private int getRememberedProjectPanelWidthPx() {
        WindowManager.LayoutParams panelLp = host.getProjectPanelLayoutParams();
        if (panelLp != null && panelLp.width > 0) {
            return panelLp.width;
        }
        return host.dp(PROJECT_PANEL_DEFAULT_W_DP);
    }

    private int getRememberedProjectPanelHeightPx() {
        WindowManager.LayoutParams panelLp = host.getProjectPanelLayoutParams();
        if (panelLp != null && panelLp.height > 0) {
            return panelLp.height;
        }
        return host.dp(PROJECT_PANEL_DEFAULT_H_DP);
    }

    private void updateProjectPanelDockLayout() {
        View dockView = host.getProjectPanelDockView();
        WindowManager.LayoutParams dockLp = host.getProjectPanelDockLayoutParams();
        if (dockView == null || dockLp == null) {
            return;
        }

        int[] screen = host.getScreenSizePx();
        int dockWidth = host.dp(PROJECT_PANEL_DOCK_W_DP);
        int dockHeight = host.dp(PROJECT_PANEL_DOCK_H_DP);
        int margin = host.dp(PROJECT_PANEL_DOCK_MARGIN_DP);
        int panelWidth = getRememberedProjectPanelWidthPx();
        int panelHeight = getRememberedProjectPanelHeightPx();
        int panelCenterX = host.getSharedPanelX() + (panelWidth / 2);

        boolean dockOnLeft = panelCenterX <= (screen[0] / 2);
        host.setProjectPanelDockOnLeft(dockOnLeft);
        dockLp.width = dockWidth;
        dockLp.height = dockHeight;
        dockLp.x = dockOnLeft ? 0 : Math.max(0, screen[0] - dockWidth);

        int desiredY = host.getSharedPanelY() + Math.max(0, (panelHeight - dockHeight) / 2);
        int maxY = Math.max(margin, screen[1] - dockHeight - margin);
        dockLp.y = Math.max(margin, Math.min(desiredY, maxY));

        View line = dockView.findViewById(R.id.project_panel_dock_line);
        if (line != null) {
            android.widget.FrameLayout.LayoutParams lineLp =
                    (android.widget.FrameLayout.LayoutParams) line.getLayoutParams();
            lineLp.gravity = (dockOnLeft ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL;
            line.setLayoutParams(lineLp);
        }
    }

    private void attachProjectPanelDockIfNeeded() {
        View dockView = host.getProjectPanelDockView();
        WindowManager.LayoutParams dockLp = host.getProjectPanelDockLayoutParams();
        if (dockView == null || dockLp == null || isProjectPanelDockAttached()) {
            return;
        }
        host.getWindowManager().addView(dockView, dockLp);
    }

    private void detachProjectPanelDock() {
        View dockView = host.getProjectPanelDockView();
        if (dockView == null || !isProjectPanelDockAttached()) {
            return;
        }
        try {
            host.getWindowManager().removeView(dockView);
        } catch (Exception ignored) {
        }
    }

    void showProjectPanelDock() {
        if (!host.isScriptActiveForUi()) {
            hideProjectPanelDock();
            return;
        }
        host.adaptProjectPanelSizeToCurrentScreen(host.getProjectPanelLayoutParams());
        prepareProjectPanelDock();
        updateProjectPanelDockLayout();
        attachProjectPanelDockIfNeeded();
        View dockView = host.getProjectPanelDockView();
        WindowManager.LayoutParams dockLp = host.getProjectPanelDockLayoutParams();
        if (dockView != null) {
            dockView.animate().cancel();
            dockView.setAlpha(1f);
            if (isProjectPanelDockAttached() && dockLp != null) {
                host.getWindowManager().updateViewLayout(dockView, dockLp);
            }
        }
    }

    void hideProjectPanelDock() {
        View dockView = host.getProjectPanelDockView();
        if (dockView != null) {
            dockView.animate().cancel();
        }
        detachProjectPanelDock();
    }

    private void revealProjectPanelFromDock() {
        if (!host.isScriptActiveForUi()) {
            hideProjectPanelDock();
            return;
        }
        prepareProjectPanel();
        WindowManager.LayoutParams panelLp = host.getProjectPanelLayoutParams();
        WindowManager.LayoutParams dockLp = host.getProjectPanelDockLayoutParams();
        if (panelLp != null) {
            host.adaptProjectPanelSizeToCurrentScreen(panelLp);
            int[] screen = host.getScreenSizePx();
            int margin = host.dp(8);
            panelLp.x = host.isProjectPanelDockOnLeft()
                    ? margin
                    : Math.max(margin, screen[0] - panelLp.width - margin);
            if (dockLp != null) {
                panelLp.y = dockLp.y - Math.max(0, (panelLp.height - dockLp.height) / 2);
            }
            host.clampPanelToScreen(panelLp);
            host.rememberSharedPanelPosition(panelLp);
        }
        hideProjectPanelDock();
        showRuntimeAwareProjectPanel();
        View panelView = host.getProjectPanelView();
        if (panelView != null) {
            float startOffset = host.isProjectPanelDockOnLeft() ? -host.dp(28) : host.dp(28);
            panelView.setAlpha(0f);
            panelView.setTranslationX(startOffset);
            panelView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(180)
                    .start();
        }
    }

    private boolean isProjectPanelAttached() {
        View panelView = host.getProjectPanelView();
        return panelView != null && panelView.getParent() != null;
    }

    private boolean isProjectPanelVisible() {
        View panelView = host.getProjectPanelView();
        return panelView != null
                && isProjectPanelAttached()
                && panelView.getVisibility() == View.VISIBLE;
    }

    private void attachProjectPanelIfNeeded() {
        View panelView = host.getProjectPanelView();
        WindowManager.LayoutParams panelLp = host.getProjectPanelLayoutParams();
        if (panelView == null || panelLp == null || isProjectPanelAttached()) {
            return;
        }
        host.getWindowManager().addView(panelView, panelLp);
    }

    private void detachProjectPanel() {
        View panelView = host.getProjectPanelView();
        if (panelView == null || !isProjectPanelAttached()) {
            return;
        }
        try {
            host.getWindowManager().removeView(panelView);
        } catch (Exception ignored) {
        }
    }

    void updateUIForLevel() {
        View panelView = host.getProjectPanelView();
        if (panelView == null) {
            return;
        }
        TextView titleView = panelView.findViewById(R.id.tv_title);
        ImageView backButton = panelView.findViewById(R.id.btn_back);
        LinearLayout breadcrumbLayout = panelView.findViewById(R.id.breadcrumb_layout);
        TextView breadcrumbView = panelView.findViewById(R.id.tv_breadcrumb);
        TextView breadcrumbLegacyView = panelView.findViewById(R.id.tv_breadcrumb_legacy);
        if (titleView == null || backButton == null || breadcrumbLayout == null) {
            return;
        }

        switch (host.getProjectPanelLevel()) {
            case LEVEL_PROJECT:
                titleView.setText("Projects");
                backButton.setVisibility(View.GONE);
                breadcrumbLayout.setVisibility(View.GONE);
                if (breadcrumbView != null) {
                    breadcrumbView.setVisibility(View.GONE);
                    breadcrumbView.setText("");
                }
                if (breadcrumbLegacyView != null) {
                    breadcrumbLegacyView.setText("");
                }
                break;
            case LEVEL_TASK:
                File currentProjectDir = host.getCurrentProjectDir();
                titleView.setText(currentProjectDir != null ? currentProjectDir.getName() : "Tasks");
                backButton.setVisibility(View.VISIBLE);
                breadcrumbLayout.setVisibility(View.VISIBLE);
                if (breadcrumbView != null) {
                    breadcrumbView.setVisibility(View.VISIBLE);
                    breadcrumbView.setText("Project");
                }
                if (breadcrumbLegacyView != null) {
                    breadcrumbLegacyView.setText(currentProjectDir != null ? currentProjectDir.getName() : "");
                }
                break;
            case LEVEL_OPERATION:
                File currentTaskDir = host.getCurrentTaskDir();
                File projectDir = host.getCurrentProjectDir();
                titleView.setText(currentTaskDir != null ? currentTaskDir.getName() : "Operations");
                backButton.setVisibility(View.VISIBLE);
                breadcrumbLayout.setVisibility(View.VISIBLE);
                String path = (projectDir != null ? projectDir.getName() : "") + " > "
                        + (currentTaskDir != null ? currentTaskDir.getName() : "");
                if (breadcrumbView != null) {
                    breadcrumbView.setVisibility(View.VISIBLE);
                    breadcrumbView.setText("Operation");
                }
                if (breadcrumbLegacyView != null) {
                    breadcrumbLegacyView.setText(path);
                }
                break;
            default:
                break;
        }
        refreshProjectPanelFooterState();
        syncProjectPanelRuntimeUi();
    }

    void refreshProjectPanelFooterState() {
        View panelView = host.getProjectPanelView();
        if (panelView == null) {
            return;
        }
        TextView runButton = panelView.findViewById(R.id.btn_run);
        TextView editButton = panelView.findViewById(R.id.btn_edit);
        TextView moveUpButton = panelView.findViewById(R.id.btn_move_up);
        TextView moveDownButton = panelView.findViewById(R.id.btn_move_down);
        TextView batchButton = panelView.findViewById(R.id.btn_batch);
        if (runButton == null || editButton == null || moveUpButton == null
                || moveDownButton == null || batchButton == null) {
            return;
        }

        boolean runtimeMode = host.getProjectPanelLevel() == LEVEL_OPERATION && host.isScriptActiveForUi();
        if (runtimeMode) {
            runButton.setText(host.isPaused() ? "继续" : "暂停");
            editButton.setText("停止");
            batchButton.setText("收起");
            moveUpButton.setVisibility(View.GONE);
            moveDownButton.setVisibility(View.GONE);
            batchButton.setVisibility(View.VISIBLE);
            return;
        }

        moveUpButton.setVisibility(View.VISIBLE);
        moveDownButton.setVisibility(View.VISIBLE);
        batchButton.setVisibility(View.VISIBLE);
        if (host.isOperationBatchMode()) {
            runButton.setText("删除选中(" + host.getBatchSelectedOperationCount() + ")");
            editButton.setText("退出批量");
            batchButton.setText("批量中");
        } else {
            runButton.setText("▶ 运行");
            editButton.setText("✎ 编辑");
            moveUpButton.setText("上移");
            moveDownButton.setText("下移");
            batchButton.setText("批量");
        }
    }

    void syncProjectPanelRuntimeUi() {
        View panelView = host.getProjectPanelView();
        if (panelView == null) {
            return;
        }
        View runtimeBar = panelView.findViewById(R.id.runtime_status_bar);
        TextView statusView = panelView.findViewById(R.id.tv_runtime_status);
        TextView progressView = panelView.findViewById(R.id.tv_runtime_progress);
        TextView detailView = panelView.findViewById(R.id.tv_runtime_detail);
        View dotView = panelView.findViewById(R.id.runtime_status_dot);
        if (runtimeBar == null || statusView == null || progressView == null
                || detailView == null || dotView == null) {
            return;
        }

        boolean visible = host.getProjectPanelLevel() == LEVEL_OPERATION && host.isScriptActiveForUi();
        runtimeBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            refreshProjectPanelFooterState();
            return;
        }

        statusView.setText(host.getRuntimeStatusText());
        statusView.setTextColor(host.getRuntimeStatusColor());
        dotView.setBackgroundColor(host.getRuntimeStatusColor());
        progressView.setText(String.format(
                Locale.getDefault(),
                "%d/%d",
                Math.max(0, host.getCurrentOperationIndex()),
                Math.max(0, host.getTotalOperationCount())
        ));

        String detail = !TextUtils.isEmpty(host.getCurrentRunningOperationName())
                ? "当前节点: " + host.getCurrentRunningOperationName()
                : "当前任务: " + (TextUtils.isEmpty(host.getCurrentRunningTask()) ? "-" : host.getCurrentRunningTask());
        if (!TextUtils.isEmpty(host.getCurrentRunningProject())) {
            detail = host.getCurrentRunningProject() + " / " + detail;
        }
        detailView.setText(detail);
        refreshProjectPanelFooterState();
    }

    void focusCurrentRunningOperation() {
        String operationId = host.getCurrentRunningOperationId();
        if (TextUtils.isEmpty(operationId) || !host.selectOperationInProjectPanel(operationId)) {
            host.showToast("当前没有可定位的运行节点");
            return;
        }
        RecyclerView recyclerView = host.getProjectPanelRecyclerView();
        int position = host.findOperationPositionInProjectPanel(operationId);
        if (recyclerView != null && position >= 0) {
            recyclerView.scrollToPosition(position);
        }
    }
}
