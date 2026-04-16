package com.auto.master.floatwin;

import android.content.Context;
import android.view.View;

import androidx.annotation.Nullable;

import com.auto.master.auto.ScriptRunner;

final class RuntimeTransitionCoordinator {
    interface Host {
        Context getContext();
        boolean isPaused();
        void setPaused(boolean paused);
        void updateRunningPanelStatus(String status, int color);
        void applyBallPresentation();
        void syncProjectPanelRuntimeUi();
        void showToast(String message);
        void recordFailureReason(String reason);
        void appendRunLog(String log);
        void persistCurrentRunLog();
        void finishRunSession(String reason);
        void hideProjectPanelDock();
        void setBallVisible(boolean visible);
        void stopDelayProgress();
        void hideStepOverlay();
        void clearCurrentRunningOperationState();
        void clearCurrentOperationRunningPosition();
        void restoreBallAfterRun();
        void updateNotification(String text);
        void clearFlowGraphHighlight();
        void clearExecutionListener();
        void refreshAppLaunchPollingState();
        long getCurrentRunStartMs();
        void showRuntimeAwareProjectPanel();
        @Nullable View getProjectPanelView();
        void removeProjectPanel();
    }

    private final Host host;

    RuntimeTransitionCoordinator(Host host) {
        this.host = host;
    }

    void togglePauseState() {
        if (host.isPaused()) {
            ScriptRunner.resumeCurrentScript();
            host.setPaused(false);
            host.updateRunningPanelStatus("运行中", 0xFF4CAF50);
            host.applyBallPresentation();
            host.showToast("脚本已继续");
        } else {
            ScriptRunner.pauseCurrentScript();
            host.setPaused(true);
            host.updateRunningPanelStatus("已暂停", 0xFFFF9800);
            host.applyBallPresentation();
            host.showToast("脚本已暂停");
        }
        host.syncProjectPanelRuntimeUi();
    }

    void stopScriptFromUi() {
        ScriptRunner.stopCurrentScript();
        host.recordFailureReason("stopped_by_user");
        host.appendRunLog("=== Run Stopped By User ===");
        host.persistCurrentRunLog();
        host.finishRunSession("stopped_by_user");
        host.hideProjectPanelDock();
        host.setBallVisible(true);
        host.stopDelayProgress();
        host.hideStepOverlay();
        host.clearCurrentRunningOperationState();
        host.setPaused(false);
        host.updateRunningPanelStatus("已停止", 0xFFF44336);
        host.clearCurrentOperationRunningPosition();
        host.restoreBallAfterRun();
        host.syncProjectPanelRuntimeUi();
        host.showToast("脚本已停止");
    }

    void transitionAfterRunStart(boolean openProjectPanelNow) {
        host.setBallVisible(true);
        if (openProjectPanelNow) {
            host.showRuntimeAwareProjectPanel();
            return;
        }
        smoothHideProjectPanel(() -> host.showToast("后台运行中，可点悬浮球查看状态"));
    }

    void handleScriptComplete() {
        host.refreshAppLaunchPollingState();
        host.stopDelayProgress();
        host.appendRunLog("=== Run Complete === total=" + (System.currentTimeMillis() - host.getCurrentRunStartMs()) + "ms");
        host.persistCurrentRunLog();
        host.finishRunSession("completed");
        host.clearCurrentOperationRunningPosition();
        host.updateRunningPanelStatus("已完成", 0xFF4CAF50);
        host.clearCurrentRunningOperationState();
        host.setPaused(false);
        host.hideProjectPanelDock();
        host.setBallVisible(true);
        host.restoreBallAfterRun();
        host.updateNotification("运行完成");
        host.hideStepOverlay();
        host.clearFlowGraphHighlight();
        host.clearExecutionListener();
        host.syncProjectPanelRuntimeUi();
        host.showToast("所有操作执行完成");
    }

    private void smoothHideProjectPanel(Runnable endAction) {
        View panel = host.getProjectPanelView();
        if (panel == null) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        panel.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction(() -> {
                    host.removeProjectPanel();
                    if (endAction != null) {
                        endAction.run();
                    }
                })
                .start();
    }
}
