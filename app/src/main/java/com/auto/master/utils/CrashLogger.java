package com.auto.master.utils;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CrashLogger {

    private static final String TAG = "CrashLogger";
    private static final Object LOCK = new Object();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final long ANR_TIMEOUT_MS = 5000L;
    private static final long ANR_POLL_INTERVAL_MS = 1500L;
    private static final int EXIT_TRACE_MAX_CHARS = 12000;
    private static volatile boolean installed = false;
    private static volatile boolean anrWatchdogStarted = false;
    private static volatile boolean anrReported = false;
    private static volatile Context appContext;
    private static volatile Thread.UncaughtExceptionHandler previousHandler;

    private static volatile File currentRunLogFile;
    private static volatile File currentSessionMarkerFile;
    private static volatile String currentProject = "";
    private static volatile String currentTask = "";
    private static volatile String currentOperationId = "";
    private static volatile String currentOperationName = "";

    private CrashLogger() {
    }

    public static void install(Context context) {
        if (context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        if (installed) {
            return;
        }
        synchronized (LOCK) {
            if (installed) {
                return;
            }
            previousHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                try {
                    writeCrashReport(thread, throwable);
                } catch (Throwable t) {
                    Log.e(TAG, "write crash report failed", t);
                } finally {
                    if (previousHandler != null) {
                        previousHandler.uncaughtException(thread, throwable);
                    } else {
                        Process.killProcess(Process.myPid());
                        System.exit(10);
                    }
                }
            });
            recoverHistoricalProcessExitInfoLocked();
            recoverPreviousAbnormalSessionLocked();
            startAnrWatchdogIfNeeded();
            installed = true;
        }
    }

    public static void startRunSession(
            Context context,
            @Nullable String projectName,
            @Nullable String taskName,
            @Nullable String entryOperationId,
            @Nullable String entryOperationName
    ) {
        install(context);
        synchronized (LOCK) {
            currentProject = valueOrEmpty(projectName);
            currentTask = valueOrEmpty(taskName);
            currentOperationId = valueOrEmpty(entryOperationId);
            currentOperationName = valueOrEmpty(entryOperationName);
            currentRunLogFile = createRunLogFile();
            currentSessionMarkerFile = getRunSessionMarkerFile();
            anrReported = false;
            appendToCurrentRunLog("=== Live Run Start ===");
            appendToCurrentRunLog("project=" + currentProject + ", task=" + currentTask);
            if (!TextUtils.isEmpty(currentOperationId) || !TextUtils.isEmpty(currentOperationName)) {
                appendToCurrentRunLog("entry=" + currentOperationId + " (" + currentOperationName + ")");
            }
            appendToCurrentRunLog(memorySummary());
            writeCurrentSessionMarker();
        }
    }

    public static void updateRunContext(
            @Nullable String projectName,
            @Nullable String taskName,
            @Nullable String operationId,
            @Nullable String operationName
    ) {
        synchronized (LOCK) {
            if (projectName != null) currentProject = projectName;
            if (taskName != null) currentTask = taskName;
            if (operationId != null) currentOperationId = operationId;
            if (operationName != null) currentOperationName = operationName;
            writeCurrentSessionMarker();
        }
    }

    public static void appendRunLog(Context context, @Nullable String line) {
        install(context);
        synchronized (LOCK) {
            appendToCurrentRunLog(line);
        }
    }

    public static void finishRunSession(Context context, String reason) {
        install(context);
        synchronized (LOCK) {
            appendToCurrentRunLog("=== Live Run End: " + reason + " ===");
            appendToCurrentRunLog(memorySummary());
            clearCurrentSessionMarker();
            currentRunLogFile = null;
            currentSessionMarkerFile = null;
            currentProject = "";
            currentTask = "";
            currentOperationId = "";
            currentOperationName = "";
            anrReported = false;
        }
    }

    public static void logHandledException(Context context, String stage, Throwable throwable) {
        install(context);
        synchronized (LOCK) {
            appendToCurrentRunLog("[ERROR] handled_exception stage=" + valueOrEmpty(stage)
                    + " | " + summarizeThrowable(throwable));
            writeErrorReport(
                    createCrashFile("handled"),
                    "handled_exception",
                    valueOrEmpty(stage),
                    Thread.currentThread(),
                    throwable
            );
            writeErrorReport(
                    createErrorRunLogFile("handled"),
                    "handled_exception",
                    valueOrEmpty(stage),
                    Thread.currentThread(),
                    throwable
            );
        }
    }

    public static String getDiagnosticsRootPath(Context context) {
        File root = getDiagnosticsRoot(context == null ? appContext : context.getApplicationContext());
        return root == null ? "" : root.getAbsolutePath();
    }

    private static void writeCrashReport(Thread thread, Throwable throwable) {
        synchronized (LOCK) {
            appendToCurrentRunLog("[ERROR] fatal_exception | " + summarizeThrowable(throwable));
            writeErrorReport(createCrashFile("fatal"), "fatal_exception", "uncaught", thread, throwable);
            writeErrorReport(createErrorRunLogFile("fatal"), "fatal_exception", "uncaught", thread, throwable);
        }
    }

    private static void appendToCurrentRunLog(@Nullable String line) {
        if (currentRunLogFile == null || TextUtils.isEmpty(line)) {
            return;
        }
        appendLine(currentRunLogFile, line);
    }

    private static File createRunLogFile() {
        File dir = getDir("run_logs");
        if (dir == null) {
            return null;
        }
        return new File(dir, "live_run_" + nowForFile() + ".log");
    }

    private static File createErrorRunLogFile(String kind) {
        File dir = getDir("run_logs");
        if (dir == null) {
            return null;
        }
        return new File(dir, "ERROR_" + valueOrEmpty(kind) + "_" + nowForFile() + ".log");
    }

    private static File getRunSessionMarkerFile() {
        File dir = getDir("run_logs");
        if (dir == null) {
            return null;
        }
        return new File(dir, "_active_run_session.marker");
    }

    private static File getExitInfoMarkerFile() {
        File dir = getDir("run_logs");
        if (dir == null) {
            return null;
        }
        return new File(dir, "_last_exit_info.marker");
    }

    private static File createCrashFile(String prefix) {
        File dir = getDir("crashes");
        if (dir == null) {
            return null;
        }
        return new File(dir, prefix + "_crash_" + nowForFile() + ".log");
    }

    private static File getDir(String child) {
        File root = getDiagnosticsRoot(appContext);
        if (root == null) {
            return null;
        }
        File dir = new File(root, child);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        return dir;
    }

    private static File getDiagnosticsRoot(Context context) {
        if (context == null) {
            return null;
        }
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            base = context.getFilesDir();
        }
        if (base == null) {
            return null;
        }
        File dir = new File(base, "diagnostics");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        return dir;
    }

    private static void appendLine(File file, @Nullable String line) {
        if (file == null || TextUtils.isEmpty(line)) {
            return;
        }
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line);
            if (!line.endsWith("\n")) {
                writer.write("\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "append line failed", e);
        }
    }

    private static String tail(File file, int maxLines) {
        if (file == null || !file.exists()) {
            return "";
        }
        ArrayDeque<String> deque = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (deque.size() >= maxLines) {
                    deque.removeFirst();
                }
                deque.addLast(line);
            }
        } catch (Exception e) {
            return "read_tail_failed: " + e.getMessage();
        }
        StringBuilder sb = new StringBuilder();
        for (String line : deque) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String readWholeFile(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            return "read_failed: " + e.getMessage();
        }
        return sb.toString();
    }

    private static void overwriteFile(File file, @Nullable String text) {
        if (file == null) {
            return;
        }
        try (FileWriter writer = new FileWriter(file, false)) {
            if (!TextUtils.isEmpty(text)) {
                writer.write(text);
            }
        } catch (Exception e) {
            Log.e(TAG, "overwrite file failed", e);
        }
    }

    private static void writeErrorReport(File file,
                                         String kind,
                                         String stage,
                                         @Nullable Thread thread,
                                         @Nullable Throwable throwable) {
        appendLine(file, "timestamp=" + nowForFile());
        appendLine(file, "kind=" + valueOrEmpty(kind));
        appendLine(file, "stage=" + valueOrEmpty(stage));
        appendLine(file, "thread=" + (thread == null ? "-" : thread.getName()));
        appendLine(file, "project=" + currentProject + ", task=" + currentTask);
        appendLine(file, "operation=" + currentOperationId + " (" + currentOperationName + ")");
        appendLine(file, memorySummary());
        if (currentRunLogFile != null) {
            appendLine(file, "live_run_log=" + currentRunLogFile.getAbsolutePath());
            appendLine(file, "--- recent_run_log_tail ---");
            appendLine(file, tail(currentRunLogFile, 80));
        }
        appendLine(file, "--- stacktrace ---");
        appendLine(file, stackTraceOf(throwable));
    }

    private static void writeCurrentSessionMarker() {
        if (currentSessionMarkerFile == null) {
            currentSessionMarkerFile = getRunSessionMarkerFile();
        }
        if (currentSessionMarkerFile == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("timestamp=").append(nowForFile()).append('\n');
        sb.append("project=").append(currentProject).append('\n');
        sb.append("task=").append(currentTask).append('\n');
        sb.append("operation_id=").append(currentOperationId).append('\n');
        sb.append("operation_name=").append(currentOperationName).append('\n');
        if (currentRunLogFile != null) {
            sb.append("live_run_log=").append(currentRunLogFile.getAbsolutePath()).append('\n');
        }
        overwriteFile(currentSessionMarkerFile, sb.toString());
    }

    private static void clearCurrentSessionMarker() {
        File marker = currentSessionMarkerFile != null ? currentSessionMarkerFile : getRunSessionMarkerFile();
        if (marker != null && marker.exists() && !marker.delete()) {
            overwriteFile(marker, "");
        }
    }

    private static void recoverPreviousAbnormalSessionLocked() {
        File marker = getRunSessionMarkerFile();
        if (marker == null || !marker.exists()) {
            return;
        }
        String markerContent = readWholeFile(marker);
        String liveRunPath = extractMarkerValue(markerContent, "live_run_log");
        writeAbnormalSessionRecoveryReport(createErrorRunLogFile("abnormal_exit"), markerContent, liveRunPath);
        writeAbnormalSessionRecoveryReport(createCrashFile("abnormal_exit"), markerContent, liveRunPath);
        if (!marker.delete()) {
            overwriteFile(marker, "");
        }
    }

    private static void writeAbnormalSessionRecoveryReport(File file,
                                                           String markerContent,
                                                           String liveRunPath) {
        appendLine(file, "timestamp=" + nowForFile());
        appendLine(file, "kind=abnormal_previous_exit");
        appendLine(file, "stage=startup_recovery");
        appendLine(file, "reason=previous_session_did_not_finish_cleanly");
        appendLine(file, "--- previous_session_marker ---");
        appendLine(file, markerContent);
        if (!TextUtils.isEmpty(liveRunPath)) {
            appendLine(file, "--- previous_live_run_tail ---");
            appendLine(file, tail(new File(liveRunPath), 80));
        }
    }

    private static String extractMarkerValue(String markerContent, String key) {
        if (TextUtils.isEmpty(markerContent) || TextUtils.isEmpty(key)) {
            return "";
        }
        String prefix = key + "=";
        String[] lines = markerContent.split("\n");
        for (String line : lines) {
            if (line != null && line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static void recoverHistoricalProcessExitInfoLocked() {
        if (appContext == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        try {
            ActivityManager activityManager = appContext.getSystemService(ActivityManager.class);
            if (activityManager == null) {
                return;
            }
            List<ApplicationExitInfo> exitInfos =
                    activityManager.getHistoricalProcessExitReasons(appContext.getPackageName(), 0, 8);
            if (exitInfos == null || exitInfos.isEmpty()) {
                return;
            }
            ApplicationExitInfo latestExit = exitInfos.get(0);
            String fingerprint = buildExitInfoFingerprint(latestExit);
            File markerFile = getExitInfoMarkerFile();
            String lastFingerprint = readWholeFile(markerFile).trim();
            if (fingerprint.equals(lastFingerprint)) {
                return;
            }
            overwriteFile(markerFile, fingerprint);
            if (!shouldReportExitInfo(latestExit)) {
                return;
            }
            writeProcessExitInfoReport(createErrorRunLogFile("process_exit"), latestExit);
            writeProcessExitInfoReport(createCrashFile("process_exit"), latestExit);
        } catch (Throwable t) {
            Log.e(TAG, "recover historical process exit info failed", t);
        }
    }

    private static String buildExitInfoFingerprint(@Nullable ApplicationExitInfo exitInfo) {
        if (exitInfo == null) {
            return "";
        }
        return exitInfo.getTimestamp()
                + "|"
                + exitInfo.getReason()
                + "|"
                + exitInfo.getStatus()
                + "|"
                + exitInfo.getImportance()
                + "|"
                + valueOrEmpty(exitInfo.getProcessName());
    }

    private static boolean shouldReportExitInfo(@Nullable ApplicationExitInfo exitInfo) {
        if (exitInfo == null) {
            return false;
        }
        int reason = exitInfo.getReason();
        return reason != 1
                && reason != 10
                && reason != 11;
    }

    private static void writeProcessExitInfoReport(File file, @Nullable ApplicationExitInfo exitInfo) {
        if (file == null || exitInfo == null) {
            return;
        }
        appendLine(file, "timestamp=" + nowForFile());
        appendLine(file, "kind=historical_process_exit");
        appendLine(file, "process=" + valueOrEmpty(exitInfo.getProcessName()));
        appendLine(file, "pid=" + exitInfo.getPid());
        appendLine(file, "reason=" + reasonToString(exitInfo.getReason())
                + " (" + exitInfo.getReason() + ")");
        appendLine(file, "importance=" + exitInfo.getImportance());
        appendLine(file, "status=" + exitInfo.getStatus());
        appendLine(file, "description=" + safeCharSequence(exitInfo.getDescription()));
        appendLine(file, "timestamp_ms=" + exitInfo.getTimestamp());
        appendLine(file, String.format(
                Locale.US,
                "memory pss=%.2fMB rss=%.2fMB",
                exitInfo.getPss() / 1024f,
                exitInfo.getRss() / 1024f
        ));
        appendLine(file, "--- exit_trace ---");
        appendLine(file, readExitTrace(exitInfo));
    }

    private static String readExitTrace(@Nullable ApplicationExitInfo exitInfo) {
        if (exitInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "";
        }
        try (InputStream inputStream = exitInfo.getTraceInputStream()) {
            if (inputStream == null) {
                return "";
            }
            InputStreamReader reader = new InputStreamReader(inputStream);
            char[] buffer = new char[2048];
            StringBuilder sb = new StringBuilder();
            int read;
            while ((read = reader.read(buffer)) != -1 && sb.length() < EXIT_TRACE_MAX_CHARS) {
                int allowed = Math.min(read, EXIT_TRACE_MAX_CHARS - sb.length());
                if (allowed <= 0) {
                    break;
                }
                sb.append(buffer, 0, allowed);
            }
            return sb.toString();
        } catch (Throwable t) {
            return "read_exit_trace_failed: " + t.getMessage();
        }
    }

    private static String reasonToString(int reason) {
        switch (reason) {
            case 6:
                return "ANR";
            case 4:
                return "CRASH";
            case 5:
                return "CRASH_NATIVE";
            case 12:
                return "DEPENDENCY_DIED";
            case 9:
                return "EXCESSIVE_RESOURCE_USAGE";
            case 1:
                return "EXIT_SELF";
            case 7:
                return "INITIALIZATION_FAILURE";
            case 3:
                return "LOW_MEMORY";
            case 13:
                return "OTHER";
            case 8:
                return "PERMISSION_CHANGE";
            case 2:
                return "SIGNALED";
            case 10:
                return "USER_REQUESTED";
            case 11:
                return "USER_STOPPED";
            default:
                return "UNKNOWN";
        }
    }

    private static void startAnrWatchdogIfNeeded() {
        if (anrWatchdogStarted) {
            return;
        }
        anrWatchdogStarted = true;
        Thread watchdogThread = new Thread(() -> {
            while (true) {
                try {
                    final long token = SystemClock.uptimeMillis();
                    final long[] ackHolder = {-1L};
                    MAIN_HANDLER.post(() -> ackHolder[0] = token);
                    SystemClock.sleep(ANR_TIMEOUT_MS);
                    if (ackHolder[0] != token && !Debug.isDebuggerConnected()) {
                        synchronized (LOCK) {
                            if (!anrReported) {
                                anrReported = true;
                                Throwable synthetic = buildSyntheticThrowable(
                                        "ANR watchdog detected main thread stall > " + ANR_TIMEOUT_MS + "ms",
                                        Looper.getMainLooper().getThread().getStackTrace());
                                appendToCurrentRunLog("[ERROR] anr_detected | " + summarizeThrowable(synthetic));
                                writeErrorReport(createCrashFile("anr"),
                                        "anr_watchdog",
                                        "main_thread_blocked",
                                        Looper.getMainLooper().getThread(),
                                        synthetic);
                                writeErrorReport(createErrorRunLogFile("anr"),
                                        "anr_watchdog",
                                        "main_thread_blocked",
                                        Looper.getMainLooper().getThread(),
                                        synthetic);
                            }
                        }
                    } else {
                        anrReported = false;
                    }
                    SystemClock.sleep(ANR_POLL_INTERVAL_MS);
                } catch (Throwable t) {
                    Log.e(TAG, "ANR watchdog failed", t);
                    SystemClock.sleep(ANR_POLL_INTERVAL_MS);
                }
            }
        }, "CrashLogger-ANR");
        watchdogThread.setDaemon(true);
        watchdogThread.start();
    }

    private static Throwable buildSyntheticThrowable(String message, StackTraceElement[] stack) {
        RuntimeException exception = new RuntimeException(message);
        if (stack != null && stack.length > 0) {
            exception.setStackTrace(stack);
        }
        return exception;
    }

    private static String stackTraceOf(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String memorySummary() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        long nativeHeap = Debug.getNativeHeapAllocatedSize();
        return String.format(
                Locale.US,
                "memory java_used=%.2fMB java_max=%.2fMB native_used=%.2fMB",
                used / 1024f / 1024f,
                max / 1024f / 1024f,
                nativeHeap / 1024f / 1024f
        );
    }

    private static String nowForFile() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
    }

    private static String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static String safeCharSequence(@Nullable CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static String summarizeThrowable(@Nullable Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String name = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        if (TextUtils.isEmpty(message)) {
            return name;
        }
        return name + ": " + message;
    }
}
