package com.auto.master.scheduler;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.auto.ScriptExecuteContext;
import com.auto.master.auto.ScriptRunner;
import com.auto.master.utils.OperationGsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaskScheduleExecutor {
    private static final String TAG = "TaskScheduleExecutor";
    private static final ExecutorService SINGLE = Executors.newSingleThreadExecutor();

    private TaskScheduleExecutor() {
    }

    public static void execute(Context context, ScheduledTask spec) {
        if (context == null || spec == null) {
            return;
        }
        SINGLE.execute(() -> runInternal(context, spec));
    }

    private static void runInternal(Context context, ScheduledTask spec) {
        if (!AutoAccessibilityService.isConnected()) {
            Log.e(TAG, "accessibility not connected, skip schedule=" + spec.id);
            return;
        }
        if (ScriptRunner.isCurrentScriptRunning()) {
            Log.w(TAG, "script already running, skip schedule=" + spec.id);
            return;
        }
        try {
            Project project = loadProjectFromStorage(context, spec.projectName);
            if (project == null || project.getTaskMap() == null) {
                Log.e(TAG, "project not found: " + spec.projectName);
                return;
            }
            Task task = project.getTaskMap().get(spec.taskId);
            if (task == null || task.getOperationMap() == null || task.getOperationMap().isEmpty()) {
                Log.e(TAG, "task not found or empty: " + spec.taskId);
                return;
            }

            MetaOperation start = null;
            if (!TextUtils.isEmpty(spec.operationId)) {
                start = task.getOperationMap().get(spec.operationId);
            }
            if (start == null) {
                for (MetaOperation op : task.getOperationMap().values()) {
                    if (op != null) {
                        start = op;
                        break;
                    }
                }
            }
            if (start == null) {
                Log.e(TAG, "start operation not found for schedule=" + spec.id);
                return;
            }

            OperationContext opCtx = new OperationContext();
            opCtx.anchorProject = project;

            ScriptExecuteContext execCtx = new ScriptExecuteContext();
            execCtx.tobeHandledOperation = start;
            execCtx.sharedContext = opCtx;
            execCtx.running = true;
            Log.d(TAG, "trigger run project=" + spec.projectName + " task=" + spec.taskId + " op=" + start.getId());
            ScriptRunner.runOperation(execCtx);
        } catch (Exception e) {
            Log.e(TAG, "execute schedule failed id=" + spec.id, e);
        }
    }

    private static Project loadProjectFromStorage(Context context, String projectName) {
        if (context == null || TextUtils.isEmpty(projectName)) {
            return null;
        }
        File baseDir = new File(context.getExternalFilesDir(null), "projects");
        File projectDir = new File(baseDir, projectName);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return null;
        }

        Project project = new Project();
        project.setProjectName(projectName);
        Map<String, Task> taskMap = new HashMap<>();
        File[] taskDirs = projectDir.listFiles(File::isDirectory);
        if (taskDirs == null) {
            project.setTaskMap(taskMap);
            return project;
        }

        for (File taskDir : taskDirs) {
            File operationFile = new File(taskDir, "operations.json");
            if (!operationFile.exists()) {
                continue;
            }
            try {
                String content = readFileUtf8(operationFile);
                List<MetaOperation> operations = OperationGsonUtils.fromJson(content);
                Task task = new Task();
                task.setId(taskDir.getName());
                task.setName(taskDir.getName());
                for (MetaOperation operation : operations) {
                    if (operation == null || TextUtils.isEmpty(operation.getId())) {
                        continue;
                    }
                    task.putOperation(operation);
                }
                taskMap.put(task.getId(), task);
            } catch (Exception e) {
                Log.w(TAG, "load task failed: " + taskDir.getName(), e);
            }
        }
        project.setTaskMap(taskMap);
        return project;
    }

    private static String readFileUtf8(File file) throws Exception {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
