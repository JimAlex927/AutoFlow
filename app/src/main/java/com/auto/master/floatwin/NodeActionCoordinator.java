package com.auto.master.floatwin;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import java.io.File;
import java.util.List;

final class NodeActionCoordinator {
    interface Host {
        Context getContext();
        Handler getUiHandler();
        File getProjectsRootDir();
        void prepareProjectPanel();
        void prepareNodePanelState(File projectDir, File taskDir);
        void clearProjectPanelSearch();
        void invalidateOperationListCache(@Nullable File taskDir);
        void showProjectPanel();
        void loadOperations(File taskDir, boolean forceReload);
        void updateUIForLevel();
        void focusOperationInPanel(String operationId);
        @Nullable Project findCachedProjectByName(String projectName);
        @Nullable Project loadProjectFromDir(File projectDir);
        void upsertCachedProject(Project project);
        @Nullable NodeFloatButtonConfig getNodeFloatButtonConfig(String operationId);
        boolean shouldPromptConfigUiBeforeRun(@Nullable NodeFloatButtonConfig cfg);
        void showRuntimeConfigBeforeRun(NodeFloatButtonConfig cfg, Runnable continueAfterSave);
        void applyNodeRuntimeVariables(OperationContext ctx, @Nullable NodeFloatButtonConfig cfg);
        void onNodeRunStarting(String operationId);
        void hideNodeFloatButtonUntilScriptStops(String operationId);
        @Nullable String resolveBoundSessionForStartOperation(MetaOperation startOperation);
        void startOperationWithMode(MetaOperation startOperation,
                                    OperationContext ctx,
                                    String projectName,
                                    String selectedTaskName,
                                    List<OperationItem> selectedTaskOperations,
                                    boolean openProjectPanelNow,
                                    @Nullable String scriptSessionId);
        List<OperationItem> buildOperationItemsFromTask(Task task);
        void showToast(String message);
    }

    private final Host host;

    NodeActionCoordinator(Host host) {
        this.host = host;
    }

    void navigateToNodeInPanel(NodeFloatButtonConfig cfg) {
        File projectsRoot = host.getProjectsRootDir();
        File projectDir = new File(projectsRoot, cfg.projectName);
        File taskDir = new File(projectDir, cfg.taskName);
        if (!projectDir.isDirectory() || !taskDir.isDirectory()) {
            host.showToast("找不到对应项目/任务，可能已被删除");
            return;
        }
        host.getUiHandler().post(() -> {
            host.prepareProjectPanel();
            host.prepareNodePanelState(projectDir, taskDir);
            host.clearProjectPanelSearch();
            host.invalidateOperationListCache(taskDir);
            host.showProjectPanel();
            host.loadOperations(taskDir, true);
            host.updateUIForLevel();
            host.getUiHandler().postDelayed(() -> host.focusOperationInPanel(cfg.operationId), 120);
        });
    }

    void runFromNodeFloatButton(NodeFloatButtonConfig cfg) {
        RunLaunchData data = buildRunLaunchDataForNode(cfg.projectName, cfg.taskName, cfg.operationId);
        if (data == null) {
            host.showToast("无法找到节点，请确认项目/任务未被删除");
            return;
        }
        Runnable startRun = () -> {
            NodeFloatButtonConfig latestCfg = host.getNodeFloatButtonConfig(cfg.operationId);
            if (latestCfg == null) {
                latestCfg = cfg;
            }
            latestCfg.ensureDefaults();
            host.applyNodeRuntimeVariables(data.ctx, latestCfg);
            String scriptSessionId = host.resolveBoundSessionForStartOperation(data.startOperation);
            if (scriptSessionId == null || scriptSessionId.isEmpty()) {
                return;
            }
            host.onNodeRunStarting(cfg.operationId);
            if (latestCfg.hideWhileRunning) {
                host.hideNodeFloatButtonUntilScriptStops(cfg.operationId);
            }
            host.startOperationWithMode(
                    data.startOperation,
                    data.ctx,
                    data.projectName,
                    data.selectedTaskName,
                    data.selectedTaskOperations,
                    false,
                    scriptSessionId
            );
        };
        NodeFloatButtonConfig latestCfg = host.getNodeFloatButtonConfig(cfg.operationId);
        if (latestCfg == null) {
            latestCfg = cfg;
        }
        if (host.shouldPromptConfigUiBeforeRun(latestCfg)) {
            host.showRuntimeConfigBeforeRun(latestCfg, startRun);
        } else {
            startRun.run();
        }
    }

    @Nullable
    private RunLaunchData buildRunLaunchDataForNode(String projectName, String taskName, String operationId) {
        Project project = host.findCachedProjectByName(projectName);
        if (project == null) {
            File projectDir = new File(host.getProjectsRootDir(), projectName);
            if (projectDir.exists()) {
                project = host.loadProjectFromDir(projectDir);
                if (project != null) {
                    host.upsertCachedProject(project);
                }
            }
        }
        if (project == null || project.getTaskMap() == null) {
            return null;
        }

        Task task = project.getTaskMap().get(taskName);
        if (task == null || task.getOperationMap() == null) {
            return null;
        }

        MetaOperation startOp = task.getOperationMap().get(operationId);
        if (startOp == null) {
            return null;
        }

        List<OperationItem> ops = host.buildOperationItemsFromTask(task);
        OperationContext ctx = new OperationContext();
        ctx.anchorProject = project;

        RunLaunchData data = new RunLaunchData();
        data.startOperation = startOp;
        data.selectedTask = task;
        data.projectName = projectName;
        data.selectedTaskName = taskName;
        data.selectedTaskOperations = ops;
        data.ctx = ctx;
        return data;
    }
}
