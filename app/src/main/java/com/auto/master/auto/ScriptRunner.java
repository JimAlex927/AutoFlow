package com.auto.master.auto;

import static android.os.Build.VERSION_CODES.O;
import static android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.Mat;
//import org.opencv.core.Rect as cvRect;
import android.graphics.Rect;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.auto.master.Task.Handler.OperationHandler.LoadImgToMatOperationHandler;
import com.auto.master.Task.Handler.OperationHandler.OperationHandler;
import com.auto.master.Task.Handler.OperationHandler.OperationHandlerManager;
import com.auto.master.Task.Handler.OperationHandler.RuntimeOperationLogFormatter;
import com.auto.master.Task.Handler.ResponseHandler.DefaultResponseHandler;
import com.auto.master.Task.Handler.ResponseHandler.ResponseHandlerManager;
import com.auto.master.Task.Operation.LoadImgToMatOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Operation.OperationType;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.Template.Template;

import com.auto.master.capture.ScreenCapture;
import com.auto.master.capture.ScreenCaptureManager;
import com.auto.master.utils.AppStorage;
import com.auto.master.utils.MatchResult;
import com.auto.master.utils.OpenCVHelper;

public final class ScriptRunner {

    private static final String TAG = "ScriptRunner";
    private static final int NATIVE_BUFFER_TRIM_INTERVAL_OPS = 512;
    private static final int MAX_CONCURRENT_SESSIONS = 3;
    private static final AtomicInteger THREAD_INDEX = new AtomicInteger(1);
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            MAX_CONCURRENT_SESSIONS,
            runnable -> {
                Thread thread = new Thread(runnable, "ScriptSession-" + THREAD_INDEX.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            });
    private static final ScriptSessionManager SESSION_MANAGER = new ScriptSessionManager();
    private static final ThreadLocal<ScriptSession> CURRENT_SESSION = new ThreadLocal<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // onOperationStart 必须逐条通知，否则变量节点等快速逻辑节点会把下一条真实执行节点的 UI 刷新吞掉。
    // 这里只保留完成事件的轻量节流，避免极短逻辑循环时频繁刷 UI。
    private static final long COMPLETE_LISTENER_THROTTLE_MS = 50;
    // 主线程繁忙时（如上一节点刚做完截图/OCR/列表刷新），倒计时悬浮条的 postAtFrontOfQueue
    // 任务可能延迟超过原先的 120ms，导致后台线程提前超时进入真正的 sleep。若 sleep 时长很短，
    // 等主线程恢复时"显示倒计时"和"隐藏倒计时"会背靠背执行、中间来不及渲染一帧，
    // 表现为"倒计时有时不显示"。调大超时降低这种竞态出现的概率。
    private static final long COUNTDOWN_READY_WAIT_MS = 400;

    /**
     * 脚本执行监听器接口
     * 用于实时通知 UI 当前执行状态
     */
    public interface ScriptExecutionListener {
        /**
         * 开始执行某个 operation
         * @param operationId operation 的 ID
         * @param operationName operation 的名称
         */
        void onOperationStart(String operationId, String operationName);

        default void onOperationStart(String sessionId, String operationId, String operationName) {
            onOperationStart(operationId, operationName);
        }

        default void onNodePreDelayStart(String operationId, long durationMs) {
        }

        default void onNodePreDelayStart(String sessionId, String operationId, long durationMs) {
            onNodePreDelayStart(operationId, durationMs);
        }

        /** 普通延时节点开始等待，优先通知倒计时 UI。 */
        default void onDelayOperationCountdownStart(String operationId, long durationMs) {
        }

        default void onDelayOperationCountdownStart(String sessionId, String operationId, long durationMs) {
            onDelayOperationCountdownStart(operationId, durationMs);
        }

        /** 普通延时节点即将 sleep；readySignal 在倒计时 UI 已启动后释放。 */
        default void onDelayOperationCountdownStart(String operationId,
                                                    long durationMs,
                                                    CountDownLatch readySignal) {
            onDelayOperationCountdownStart(operationId, durationMs);
            if (readySignal != null) {
                readySignal.countDown();
            }
        }

        default void onDelayOperationCountdownStart(String sessionId,
                                                    String operationId,
                                                    long durationMs,
                                                    CountDownLatch readySignal) {
            onDelayOperationCountdownStart(operationId, durationMs, readySignal);
        }

        /** MTry 开始第 current 次尝试（current=0 表示清除徽章）。 */
        default void onMtryAttempt(String operationId, int current, int total) {
        }

        default void onMtryAttempt(String sessionId, String operationId, int current, int total) {
            onMtryAttempt(operationId, current, total);
        }

        /** MTry 重试前延时开始，delayMs > 0 才调用。 */
        default void onMtryRetryDelay(String operationId, long delayMs) {
        }

        default void onMtryRetryDelay(String sessionId, String operationId, long delayMs) {
            onMtryRetryDelay(operationId, delayMs);
        }
        
        /**
         * operation 执行完成
         * @param operationId operation 的 ID
         * @param success 是否成功
         */
        void onOperationComplete(String operationId, boolean success);

        default void onOperationComplete(String sessionId, String operationId, boolean success) {
            onOperationComplete(operationId, success);
        }
        
        /**
         * 所有 operation 执行完成
         */
        void onScriptComplete();

        default void onScriptComplete(String sessionId) {
            onScriptComplete();
        }

        /**
         * Task 切换（用于悬浮窗显示当前 Task 的 operations）
         * @param taskId Task 的 ID（文件夹名）
         * @param taskName Task 的显示名称
         * @param operations Task 中的 operation 列表
         */
        void onTaskSwitch(String taskId, String taskName, List<com.auto.master.floatwin.OperationItem> operations);

        default void onTaskSwitch(String sessionId,
                                  String taskId,
                                  String taskName,
                                  List<com.auto.master.floatwin.OperationItem> operations) {
            onTaskSwitch(taskId, taskName, operations);
        }
    }
    
    // 当前监听器
    private static ScriptExecutionListener currentListener;
    
    /**
     * 设置脚本执行监听器
     */
    public static void setExecutionListener(ScriptExecutionListener listener) {
        currentListener = listener;
    }
    
    /**
     * 清除监听器
     */
    public static void clearExecutionListener() {
        currentListener = null;
    }

    /**
     * 获取当前监听器
     */
    public static ScriptExecutionListener getCurrentListener() {
        ScriptSession session = CURRENT_SESSION.get();
        return session == null ? currentListener : session.getListener();
    }

    public static String getCurrentSessionId() {
        ScriptSession session = CURRENT_SESSION.get();
        return session == null ? SESSION_MANAGER.getActiveSessionId() : session.getSessionId();
    }

    /**
     * 防止递归导致stack overflow
     * 1、储存下一个operation
     * 2、储存current operation 和 ctx
     */
//    下一个即将被处理的 operation

//    这个很关键
//    public static ScriptExecuteContext scriptExecuteContext = new ScriptExecuteContext();
//    =================================================
    private ScriptRunner() {
    }

//    public static void runJsonInBackground(final String projectName, final String jsonStr) {
//        // 在新的线程池中执行，避免阻塞
//        ExecutorService executor = Executors.newSingleThreadExecutor();
//        executor.execute(() -> {
//            try {
//                Log.d(TAG, "🚀 Background executing: " + projectName);
//
//                // 关键：使用 Application Context 而不是 Activity Context
//                Context appContext = getApplicationContext();
//
//                // 修改 ScriptRunner 使其接受 Context 参数
//                ScriptRunner.runJson(appContext, projectName, jsonStr);
//
//                Log.i(TAG, "✅ Background script execution completed: " + projectName);
//            } catch (Exception e) {
//                Log.e(TAG, "❌ Background script execution failed", e);
//            }
//        });
//    }

    //    private static Context getApplicationContext() {
//        try {
//            return MyApplication.getInstance().getApplicationContext(); // 你的Application类
//        } catch (Exception e) {
//            return null;
//        }
//    }
    public static void runJson(String projectName, String json) {
        EXECUTOR.execute(() -> {
            try {
                if (!AutoAccessibilityService.isConnected()) {
                    Log.e(TAG, "AccessibilityService not connected");
                    return;
                }
                JSONObject root = new JSONObject(json);
                JSONArray steps = root.optJSONArray("steps");
                if (steps == null) {
                    Log.e(TAG, "No steps");
                    return;
                }

                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    // --- 新增：记录单步开始时间 ---
                    long stepStartTime = System.currentTimeMillis();
                    Log.d(TAG, "Starting step#" + i + " type='" + i + "'");
                    boolean ok = runOneStepBlocking(projectName, step, i);

                    long stepEndTime = System.currentTimeMillis();
                    long durationMs = stepEndTime - stepStartTime;
                    Log.i(TAG, "Finished step#" + i + " type='" + i + "' in " + durationMs + "ms. Result: " + (ok ? "SUCCESS" : "FAILED"));

                    if (!ok) {
                        Log.e(TAG, "Step failed at index=" + i + " step=" + step);
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "runJson error", e);
            }
        });
    }


    public static void runProject(Project project) {
        EXECUTOR.execute(() -> {
            try {
                if (!AutoAccessibilityService.isConnected()) {
                    Log.e(TAG, "AccessibilityService not connected");
                    return;
                }
                String projectName = project.getProjectName();

                Map<String, Task> taskMap = project.getTaskMap();
                //这里控制入口
                String startTaskId = project.getStartTaskId();

                Task startTask = taskMap.get(startTaskId);

                runTask(startTask);


//                for (int i = 0; i < steps.length(); i++) {
//                    JSONObject step = steps.getJSONObject(i);
//                    // --- 新增：记录单步开始时间 ---
//                    long stepStartTime = System.currentTimeMillis();
//                    Log.d(TAG, "Starting step#" + i + " type='" + i  + "'");
//                    boolean ok = runOneStepBlocking(projectName,step, i);
//
//                    long stepEndTime = System.currentTimeMillis();
//                    long durationMs = stepEndTime - stepStartTime;
//                    Log.i(TAG, "Finished step#" + i + " type='" + i + "' in " + durationMs + "ms. Result: " + (ok ? "SUCCESS" : "FAILED"));
//
//                    if (!ok) {
//                        Log.e(TAG, "Step failed at index=" + i + " step=" + step);
//                        break;
//                    }
//                }
            } catch (Exception e) {
                Log.e(TAG, "runJson error", e);
            }
        });
    }

    @Deprecated
    public static void runTask(Task task) {
        String id = task.getId();
        String name = task.getName();
        String startOperationId = task.getStartOperationId();
        Map<String, MetaOperation> operationMap = task.getOperationMap();

        MetaOperation metaOperation = operationMap.get(startOperationId);

//        runOperation(metaOperation, null);
    }

    /**
     * 暂停当前运行的脚本
     */
    public static void pauseCurrentScript() {
        pauseScript(SESSION_MANAGER.getActiveSessionId());
    }

    public static void pauseScript(String sessionId) {
        ScriptSession session = SESSION_MANAGER.getSession(sessionId);
        ScriptExecuteContext ctx = session == null ? null : session.getExecuteContext();
        if (ctx == null) {
            return;
        }
        ctx.pause();
        session.setState(ScriptSession.State.PAUSED);
        Log.d(TAG, "脚本已暂停 session=" + sessionId);
    }

    /**
     * 恢复当前暂停的脚本
     */
    public static void resumeCurrentScript() {
        resumeScript(SESSION_MANAGER.getActiveSessionId());
    }

    public static void resumeScript(String sessionId) {
        ScriptSession session = SESSION_MANAGER.getSession(sessionId);
        ScriptExecuteContext ctx = session == null ? null : session.getExecuteContext();
        if (ctx == null) {
            return;
        }
        ScreenCaptureManager.getInstance().setKeepAliveDuringScript(true);
        ctx.resume();
        session.setState(ScriptSession.State.RUNNING);
        Log.d(TAG, "脚本已恢复 session=" + sessionId);
    }

    /**
     * 停止当前运行的脚本
     */
    public static void stopCurrentScript() {
        stopScript(SESSION_MANAGER.getActiveSessionId());
    }

    public static void stopScript(String sessionId) {
        ScriptSession session = SESSION_MANAGER.getSession(sessionId);
        ScriptExecuteContext ctx = session == null ? null : session.getExecuteContext();
        if (ctx == null) {
            return;
        }
        ctx.stop();
        session.setState(ScriptSession.State.STOPPING);
        Thread executeThread = session.getExecuteThread();
        if (executeThread != null) {
            executeThread.interrupt();
        }
        Log.d(TAG, "脚本已停止 session=" + sessionId);
    }

    /**
     * 获取当前脚本是否暂停
     */
    public static boolean isCurrentScriptPaused() {
        ScriptSession session = SESSION_MANAGER.getActiveSession();
        ScriptExecuteContext ctx = session == null ? null : session.getExecuteContext();
        return ctx != null && ctx.paused;
    }

    public static boolean waitIfCurrentSessionPaused() {
        ScriptSession session = CURRENT_SESSION.get();
        ScriptExecuteContext ctx = session == null ? null : session.getExecuteContext();
        if (ctx == null) {
            return true;
        }
        if (ctx.stopped || !Boolean.TRUE.equals(ctx.running)) {
            return false;
        }
        synchronized (ctx.pauseLock) {
            while (ctx.paused && Boolean.TRUE.equals(ctx.running) && !ctx.stopped) {
                try {
                    ctx.pauseLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return !ctx.stopped && Boolean.TRUE.equals(ctx.running);
    }

    public static boolean sleepCurrentSessionAware(long durationMs) {
        long remainingMs = Math.max(0L, durationMs);
        long lastTickMs = SystemClock.uptimeMillis();
        while (remainingMs > 0L) {
            if (!waitIfCurrentSessionPaused()) {
                return false;
            }
            long chunkMs = Math.min(remainingMs, 50L);
            try {
                Thread.sleep(chunkMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            long nowMs = SystemClock.uptimeMillis();
            remainingMs -= Math.max(0L, nowMs - lastTickMs);
            lastTickMs = nowMs;
        }
        return waitIfCurrentSessionPaused();
    }

    /**
     * 获取当前脚本是否正在运行
     */
    public static boolean isCurrentScriptRunning() {
        return SESSION_MANAGER.isAnyRunning();
    }

    public static boolean isAnyScriptRunning() {
        return SESSION_MANAGER.isAnyRunning();
    }

    public static boolean shouldCloneCaptureFrameForCurrentSession() {
        return SESSION_MANAGER.getRunningSessionCount() > 1;
    }

    public static boolean isScriptRunning(String sessionId) {
        return SESSION_MANAGER.isRunning(sessionId);
    }

    public static String createSession() {
        return createSession(null);
    }

    public static String createSession(String sessionName) {
        return SESSION_MANAGER.createSessionSlot(sessionName).getSessionId();
    }

    public static void closeSession(String sessionId) {
        ScriptSession session = SESSION_MANAGER.getSession(sessionId);
        if (session == null) {
            return;
        }
        ScriptSession.State state = session.getState();
        if (state == ScriptSession.State.IDLE
                || state == ScriptSession.State.COMPLETED
                || state == ScriptSession.State.FAILED
                || state == ScriptSession.State.STOPPED) {
            SESSION_MANAGER.removeSession(sessionId);
            return;
        }
        stopScript(sessionId);
    }

    public static String getActiveSessionId() {
        return SESSION_MANAGER.getActiveSessionId();
    }

    public static void setActiveSessionId(String sessionId) {
        SESSION_MANAGER.setActiveSessionId(sessionId);
    }

    public static List<ScriptSessionSnapshot> listSessionSnapshots() {
        return SESSION_MANAGER.listSnapshots();
    }

    public static ScriptSessionSnapshot getSessionSnapshot(String sessionId) {
        return SESSION_MANAGER.getSnapshot(sessionId);
    }

    /**
     * 获取 operation 类型名称
     */
    private static String getOperationTypeNameLegacy(Integer type) {
        /*
        if (type != null && type == 14) return "启动应用";
        if (type == null) return "未知";
        switch (type) {
            case 1: return "点击";
            case 2: return "延时";
            case 3: return "截图";
            case 4: return "加载图片";
            case 5: return "手势";
            case 6: return "模板匹配";
            case 7: return "多模板匹配";
            case 8: return "跳转Task";
            case 10: return "条件分支";
            case 11: return "变量脚本";
            case 12: return "变量运算";
            case 13: return "变量模板";
            default: return "未知";
        }
        */
        return "未知";
    }

    private static String getOperationTypeName(Integer type) {
        OperationType operationType = OperationType.fromCode(type);
        return operationType == null ? "未知" : operationType.getDisplayName();
    }

    private static boolean sleepNodePreDelay(ScriptExecuteContext ctx, long delayMs) throws InterruptedException {
        long endAt = SystemClock.uptimeMillis() + Math.max(0L, delayMs);
        while (SystemClock.uptimeMillis() < endAt) {
            if (ctx == null || ctx.stopped || !Boolean.TRUE.equals(ctx.running)) {
                return false;
            }
            synchronized (ctx.pauseLock) {
                while (ctx.paused && Boolean.TRUE.equals(ctx.running) && !ctx.stopped) {
                    ctx.pauseLock.wait();
                }
            }
            long remaining = endAt - SystemClock.uptimeMillis();
            if (remaining <= 0L) {
                break;
            }
            SystemClock.sleep(Math.min(remaining, 50L));
        }
        return ctx != null && !ctx.stopped && Boolean.TRUE.equals(ctx.running);
    }

    public static String runOperation(ScriptExecuteContext scriptExecuteContext) {
        return runOperation(scriptExecuteContext, scriptExecuteContext == null ? null : scriptExecuteContext.sessionId);
    }

    public static String runOperation(ScriptExecuteContext scriptExecuteContext, String requestedSessionId) {
        final ScriptSession session = SESSION_MANAGER.bindSession(requestedSessionId, scriptExecuteContext, currentListener);
        if (session == null) {
            throw new IllegalStateException("会话正在运行或不可用");
        }
        final String sessionId = session.getSessionId();
        scriptExecuteContext.sessionId = sessionId;

//        1、无限循环
        session.setFuture(EXECUTOR.submit(

//                运行之前先重新加载资源文件
//                重新加载下项目的资源文件到内存

                () -> {
//                    @Amalrich 我直接把wakelock注释掉 似乎不会导致脚本休眠
//                    PowerManager.WakeLock wakeLock = null;
//                    try {
//                        PowerManager pm = (PowerManager) com.auto.master.AtommApplication.instance
//                                .getSystemService(Context.POWER_SERVICE);
//                        if (pm != null) {
//                            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoMaster:ScriptRunner");
//                            wakeLock.acquire(); // 持有至脚本结束，finally 块中主动释放
//                        }
//                    } catch (Exception ignored) {}
                    int consecutiveErrors = 0;
                    int operationsSinceNativeTrim = 0;
                    boolean failed = false;
                    final ScriptExecutionListener sessionListener = session.getListener();
                    try {
                        CURRENT_SESSION.set(session);
                        session.setState(ScriptSession.State.RUNNING);
                        ScreenCaptureManager.getInstance().setKeepAliveDuringScript(true);
//                    *************************************************
                        String projectName = "";
                        String entryTaskName = "";
                        if (scriptExecuteContext.sharedContext != null
                                && scriptExecuteContext.sharedContext.anchorProject != null
                                && scriptExecuteContext.sharedContext.anchorProject.getProjectName() != null) {
                            projectName = scriptExecuteContext.sharedContext.anchorProject.getProjectName();
                        }
                        MetaOperation entryOperation = scriptExecuteContext.tobeHandledOperation;
                        if (entryOperation != null && entryOperation.taskId != null) {
                            entryTaskName = entryOperation.taskId;
                        }
                        session.setCurrentProjectName(projectName);
                        session.setCurrentTaskName(entryTaskName);
                        if (!entryTaskName.isEmpty() && !Template.isTaskCacheWarm(projectName, entryTaskName)) {
                            LoadImgToMatOperation loadImgToMatOperation = new LoadImgToMatOperation();
                            loadImgToMatOperation.setId("loadResource");
                            loadImgToMatOperation.setResponseType(1);
                            HashMap<String, Object> tmpInputMap = new HashMap<>();
                            tmpInputMap.put(MetaOperation.PROJECT, projectName);
                            tmpInputMap.put(MetaOperation.TASK, entryTaskName);
                            loadImgToMatOperation.setInputMap(tmpInputMap);
                            new LoadImgToMatOperationHandler().handle(loadImgToMatOperation, new OperationContext());
                        }
//                    *************************************************

                        session.setExecuteThread(Thread.currentThread());
                        while (Boolean.TRUE.equals(scriptExecuteContext.running)) {
                            try {
                                synchronized (scriptExecuteContext.pauseLock) {
                                    while (scriptExecuteContext.paused && Boolean.TRUE.equals(scriptExecuteContext.running)) {
                                        scriptExecuteContext.pauseLock.wait();
                                    }
                                }

                                if (scriptExecuteContext.stopped) {
                                    scriptExecuteContext.running = false;
                                    break;
                                }

                                MetaOperation operation = scriptExecuteContext.tobeHandledOperation;
                                if (operation == null) {
                                    if (!scriptExecuteContext.returnStack.isEmpty()) {
                                        MetaOperation jumpOp = scriptExecuteContext.returnStack.pop();
                                        if (jumpOp != null && jumpOp.getType() == 8) {
                                            Project anchorProject = scriptExecuteContext.sharedContext.anchorProject;
                                            if (anchorProject != null) {
                                                Task originTask = anchorProject.getTaskMap().get(jumpOp.taskId);
                                                if (originTask != null) {
                                                    Log.d(TAG, "子 Task 执行完，恢复到 JumpTaskOperation[" + jumpOp.getId() + "]");
                                                    scriptExecuteContext.justReturnedFromSubTask = true;
                                                    Map<String, Object> returnRes = new java.util.HashMap<>();
                                                    returnRes.put("__RETURN_FROM_SUBTASK__", true);
                                                    scriptExecuteContext.sharedContext.currentResponse = returnRes;
                                                    scriptExecuteContext.tobeHandledOperation = jumpOp;
                                                    scriptExecuteContext.sharedContext.lastOperation = jumpOp;

                                                    if (sessionListener != null) {
                                                        final ScriptExecutionListener listener = sessionListener;
                                                        final String taskName = originTask.getName();
                                                        final String taskId = jumpOp.taskId;
                                                        final java.util.List<com.auto.master.floatwin.OperationItem> operationItems =
                                                                new java.util.ArrayList<>();
                                                        int index = 0;
                                                        for (com.auto.master.Task.Operation.MetaOperation op : originTask.getOperationMap().values()) {
                                                            String typeName = getOperationTypeName(op.getType());
                                                            operationItems.add(new com.auto.master.floatwin.OperationItem(
                                                                    op.getName(),
                                                                    op.getId(),
                                                                    typeName,
                                                                    index++,
                                                                    com.auto.master.floatwin.FloatWindowService.extractDelayDurationMs(op),
                                                                    com.auto.master.floatwin.FloatWindowService.extractDelayShowCountdown(op),
                                                                    com.auto.master.floatwin.FloatWindowService.extractNodePreDelayMs(op),
                                                                    com.auto.master.floatwin.FloatWindowService.extractNodePreDelayMinMs(op),
                                                                    com.auto.master.floatwin.FloatWindowService.extractNodePreDelayMaxMs(op),
                                                                    com.auto.master.floatwin.FloatWindowService.extractNodePreDelayRandom(op)
                                                            ));
                                                        }
                                                        MAIN.post(() -> listener.onTaskSwitch(sessionId, taskId, taskName, operationItems));
                                                    }

                                                    SystemClock.sleep(10);
                                                    continue;
                                                }
                                            }
                                        }
                                    }

                                    MetaOperation repeatStart = scriptExecuteContext.advanceRepeatExecutionAtTaskEnd();
                                    if (repeatStart != null) {
                                        int completedRounds = scriptExecuteContext.getRepeatCompletedRounds();
                                        String totalRounds = scriptExecuteContext.isRepeatExecutionInfinite()
                                                ? "infinite"
                                                : String.valueOf(scriptExecuteContext.getRepeatTotalRounds());
                                        Log.i(TAG, "循环执行完成第 " + completedRounds + " 轮，重新开始: "
                                                + repeatStart.getId() + " (total=" + totalRounds + ")");
                                        scriptExecuteContext.tobeHandledOperation = repeatStart;
                                        continue;
                                    }

                                    scriptExecuteContext.running = false;
                                    break;
                                }

                                final String opId = operation.getId();
                                final String opName = operation.getName();
                                final long delayDurationMs =
                                        com.auto.master.floatwin.FloatWindowService.extractDelayDurationMs(operation);
                                final boolean delayCountdownOperation = delayDurationMs > 0L
                                        && com.auto.master.floatwin.FloatWindowService.extractDelayShowCountdown(operation);
                                final long operationLogStartMs = System.currentTimeMillis();
                                RuntimeOperationLogFormatter.logOperationStart(operation, scriptExecuteContext.sharedContext);
                                // 非延时倒计时节点：正常通知 operationStart
                                session.setCurrentOperation(opId, opName);
                                if (!delayCountdownOperation && sessionListener != null) {
                                    final ScriptExecutionListener listener = sessionListener;
                                    MAIN.post(() -> listener.onOperationStart(sessionId, opId, opName));
                                }

                                Integer type = operation.getType();
                                OperationHandler operationHandler = OperationHandlerManager.getOperationHandler(type);
                                if (operationHandler == null) {
                                    Log.e(TAG, "未找到 OperationHandler, type=" + type + ", opId=" + opId);
                                    if (sessionListener != null) {
                                        final ScriptExecutionListener listener = sessionListener;
                                        MAIN.post(() -> listener.onOperationComplete(sessionId, opId, false));
                                    }
                                    failed = true;
                                    scriptExecuteContext.running = false;
                                    break;
                                }

                                // nodePreDelay 先于主延时倒计时执行，避免 stopDelay() 把主倒计时 UI 杀掉
                                long nodePreDelayMs = OperationHandler.resolveNodePreDelayMs(operation);
                                if (nodePreDelayMs > 0L) {
                                    if (sessionListener != null) {
                                        final ScriptExecutionListener listener = sessionListener;
                                        final long delayForUi = nodePreDelayMs;
                                        // 延时倒计时节点：nodePreDelay 阶段用 onOperationStart 通知 UI
                                        if (delayCountdownOperation) {
                                            MAIN.post(() -> listener.onOperationStart(sessionId, opId, opName));
                                        }
                                        MAIN.post(() -> listener.onNodePreDelayStart(sessionId, opId, delayForUi));
                                    }
                                    if (!sleepNodePreDelay(scriptExecuteContext, nodePreDelayMs)) {
                                        scriptExecuteContext.running = false;
                                        break;
                                    }
                                }

                                // 延时倒计时：nodePreDelay 结束后才启动主倒计时 UI（此时不会被 nodePreDelay 覆盖）
                                if (delayCountdownOperation && sessionListener != null) {
                                    final ScriptExecutionListener listener = sessionListener;
                                    CountDownLatch countdownReady = new CountDownLatch(1);
                                    listener.onDelayOperationCountdownStart(sessionId, opId, delayDurationMs, countdownReady);
                                    try {
                                        countdownReady.await(COUNTDOWN_READY_WAIT_MS, TimeUnit.MILLISECONDS);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw e;
                                    }
                                }

                                boolean ok = operationHandler.handle(operation, scriptExecuteContext.sharedContext);
                                RuntimeOperationLogFormatter.logOperationDetail(operation, scriptExecuteContext.sharedContext, ok);
                                RuntimeOperationLogFormatter.logOperationComplete(
                                        operation,
                                        scriptExecuteContext.sharedContext,
                                        ok,
                                        System.currentTimeMillis() - operationLogStartMs);

                                long nowMs = System.currentTimeMillis();
                                boolean shouldNotifyComplete = sessionListener != null
                                        && (delayCountdownOperation
                                        || nowMs - session.getLastCompleteListenerNotifyMs() >= COMPLETE_LISTENER_THROTTLE_MS);
                                if (shouldNotifyComplete) {
                                    session.setLastCompleteListenerNotifyMs(nowMs);
                                    final ScriptExecutionListener listener = sessionListener;
                                    MAIN.post(() -> listener.onOperationComplete(sessionId, opId, ok));
                                }

                                // 纯逻辑操作（变量/分支/循环判断）完成极快，
                                // 主动让出 CPU 时间片，防止脚本逻辑循环把单核跑满
                                Thread.yield();

                                if (!ok) {
                                    failed = true;
                                    scriptExecuteContext.running = false;
                                    break;
                                }

                                Map<String, Object> currentResponse = scriptExecuteContext.sharedContext.currentResponse;
                                Integer responseType = operation.getResponseType();
                                DefaultResponseHandler responseHandler = ResponseHandlerManager.getResponseHandler(operation.getClass(), responseType);
                                if (responseHandler == null) {
                                    Log.e(TAG, "未找到 ResponseHandler, class=" + operation.getClass().getSimpleName() + ", responseType=" + responseType);
                                    failed = true;
                                    scriptExecuteContext.running = false;
                                    break;
                                }
                                responseHandler.process(currentResponse, scriptExecuteContext);
                                operationsSinceNativeTrim++;
                                if (operationsSinceNativeTrim >= NATIVE_BUFFER_TRIM_INTERVAL_OPS) {
                                    OpenCVHelper.releaseCurrentThreadBuffers();
                                    operationsSinceNativeTrim = 0;
                                }
                                consecutiveErrors = 0;
                            } catch (InterruptedException e) {
                                Log.d(TAG, "脚本执行被中断");
                                Thread.currentThread().interrupt();
                                if (!scriptExecuteContext.stopped) {
                                    failed = true;
                                }
                                scriptExecuteContext.running = false;
                                break;
                            } catch (Exception e) {
                                consecutiveErrors++;
                                Log.e(TAG, "运行线程抛出异常(" + consecutiveErrors + "): " + e.getMessage(), e);
                                if (consecutiveErrors >= 3) {
                                    failed = true;
                                    scriptExecuteContext.running = false;
                                    break;
                                }
                                SystemClock.sleep(50L * consecutiveErrors);
                            }
                        }
                    } finally {
                        if (scriptExecuteContext.stopped) {
                            session.setState(ScriptSession.State.STOPPED);
                        } else if (failed) {
                            session.setState(ScriptSession.State.FAILED);
                        } else {
                            session.setState(ScriptSession.State.COMPLETED);
                        }
                        session.setExecuteThread(null);
                        if (!SESSION_MANAGER.isAnyRunning()) {
                            ScreenCaptureManager.getInstance().setKeepAliveDuringScript(false);
                        }
                        OpenCVHelper.releaseCurrentThreadBuffers();
//                        if (wakeLock != null && wakeLock.isHeld()) {
//                            wakeLock.release();
//                        }
                        if (sessionListener != null) {
                            final ScriptExecutionListener listener = sessionListener;
                            MAIN.post(() -> listener.onScriptComplete(sessionId));
                        }
                        CURRENT_SESSION.remove();
                        SESSION_MANAGER.resetSessionToIdle(sessionId);
                    }
                }
        ));

        return sessionId;
    }

    /**
     * 返回 false 表示该步失败（可加 retry/timeout）
     */
    private static boolean runOneStepBlocking(String projectName, JSONObject step, int index) throws FileNotFoundException {
        String type = step.optString("type", "");
        Log.d(TAG, "Run step#" + index + " type=" + type);
        Integer code = -1;
        if ("makeTemplate".equals(type)) {
            code = 1;
        }


        switch (type) {
            case "sleep": {
                long ms = step.optLong("ms", 0);
                sleep(ms);
                return true;
            }

            case "click": {
                int x = step.optInt("x", -1);
                int y = step.optInt("y", -1);
                if (x < 0 || y < 0) return false;
                return blockingClick(x, y);
            }

            case "back": {
                return performGlobal(AutoAccessibilityService.get(), android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            }

            case "home": {
                return performGlobal(AutoAccessibilityService.get(), android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
            }

            case "screenshot": {
                String method = step.optString("method", "MEDIA_PROJECTION");
                String name = step.optString("name", "cap_" + System.currentTimeMillis());
                // name 不带后缀也行，内部会补
                Log.d("ScriptRunner", "当前线程: " + Thread.currentThread().getName());
                Log.d("ScriptRunner", "MAIN Looper: " + MAIN.getLooper());
                Log.d("ScriptRunner", "主 Looper: " + Looper.getMainLooper());
                Log.d("ScriptRunner", "Looper 是否相同: " + (MAIN.getLooper() == Looper.getMainLooper()));
//                MAIN.post(() -> {
                //todo 这里用当前SINGLE 线程池的那个独狼线程执行每一步
                android.app.Activity a = ActivityHolder.getTopActivity(); // 见下方 ActivityHolder
                if (a == null) {
                    Log.e(TAG, "No top activity for screenshot");
                    return false;
                }

                com.auto.master.capture.ScreenCapture.Method m;
                try {
                    m = com.auto.master.capture.ScreenCapture.Method.valueOf(method);
                } catch (Exception e) {
                    m = com.auto.master.capture.ScreenCapture.Method.MEDIA_PROJECTION;
                }
                String out = name + (m == com.auto.master.capture.ScreenCapture.Method.A11Y_DUMP ? ".json" : ".png");
                com.auto.master.capture.ScreenCapture.captureNow(a, m, out);
//                });
                sleep(500); // 给保存一点时间（你也可以做回调/等待文件出现）
                return true;
            }
            case "matchTemplate": {

//                todo 使用当前activity
                long stepStartTime = System.currentTimeMillis();
                Log.d(TAG, "匹配前#" + stepStartTime);


                android.app.Activity a = ActivityHolder.getTopActivity();
                String method = step.optString("method", "MEDIA_PROJECTION");
                String name = step.optString("name", "cap_" + System.currentTimeMillis());
                //todo 测试下保存模板
                com.auto.master.capture.ScreenCapture.Method m;
                try {
                    m = com.auto.master.capture.ScreenCapture.Method.valueOf(method);
                } catch (Exception e) {
                    m = com.auto.master.capture.ScreenCapture.Method.MEDIA_PROJECTION;
                }
                String out = name + (m == com.auto.master.capture.ScreenCapture.Method.A11Y_DUMP ? ".json" : ".png");
//                获取当前图片
                Mat latestMap = com.auto.master.capture.ScreenCapture.captureNow(a, m, out);
                long stepEndTime = System.currentTimeMillis();
                long durationMs = stepEndTime - stepStartTime;
                Log.d(TAG, "匹配后1#" + durationMs);
                //todo 测试下匹配 模板
                OpenCVHelper cv = OpenCVHelper.getInstance();

                Mat screenMat = latestMap;
                //todo 这里如果bitmap时 harware类型那么就是引用了 对它的修改都无效 所以 需要手动copy一下
                stepEndTime = System.currentTimeMillis();
                durationMs = stepEndTime - stepStartTime;
                Log.d(TAG, "匹配后2#" + durationMs);

                Mat tempMat = null;
                //匹配
                List<MatchResult> matchResults = cv.matchTemplate(screenMat, tempMat, new org.opencv.core.Rect(553, 139, 727 - 553, 176 - 139), 0, 0.6, 1, false);
                Log.d(TAG, "opencv 模板匹配结果: ");
                stepEndTime = System.currentTimeMillis();
                durationMs = stepEndTime - stepStartTime;

                Log.d(TAG, "匹配后3#" + durationMs);

                return true;


            }
            case "makeTemplate": {
                //todo 使用当前activity
                android.app.Activity a = ActivityHolder.getTopActivity();
                String method = step.optString("method", "MEDIA_PROJECTION");
                String name = step.optString("name", "cap_" + System.currentTimeMillis());
                //todo 测试下保存模板
                com.auto.master.capture.ScreenCapture.Method m;
                try {
                    m = com.auto.master.capture.ScreenCapture.Method.valueOf(method);
                } catch (Exception e) {
                    m = com.auto.master.capture.ScreenCapture.Method.MEDIA_PROJECTION;
                }
                String out = name + (m == com.auto.master.capture.ScreenCapture.Method.A11Y_DUMP ? ".json" : ".png");
                if (a == null) {
                    return false;
                }
//                获取当前图片
                Mat latestMat = com.auto.master.capture.ScreenCapture.captureNow(a, m, out);
                // region crop\  [553,139,727,176]
//                bitmap = Bitmap.createBitmap(bitmap, 553, 139, 727 - 553, 176 - 139);
                File file = new File(AppStorage.getAppFilesRoot(a), "temp.png");
                try (FileOutputStream ignored = new FileOutputStream(file)) {
                    // Keep placeholder creation behavior without leaking the stream.
                } catch (Exception e) {
                    Log.w(TAG, "create temp placeholder failed: " + file.getAbsolutePath(), e);
                }
//                boolean success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
                boolean success = true;
                if (success) {
                    Log.d(TAG, "保存模板成功: " + file.getAbsolutePath());
                }
                //todo 记得回收
                //这里把mat 存在缓存里
                OpenCVHelper cv = OpenCVHelper.getInstance();
//                Mat screenMat = cv.bitmapToMat(bitmap);
//                Template.putCache(projectName,"temp",",",screenMat);
//                bitmap.recycle();
                return true;
            }
            case "captureRegion": {
                String name = step.optString("name", "region_" + System.currentTimeMillis());

                Activity a = ActivityHolder.getTopActivity();
//                Activity a = null;
                if (a == null || a.isFinishing() || a.isDestroyed()) return false;


                MAIN.post(() -> {

//                    20260124 注释下面几行
//                    Intent it = new Intent(a, CropCaptureActivity.class);
//                    it.putExtra("saveName", name);
//                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                    a.startActivity(it);
                    startInteractiveRegionCaptureV3(name);
//                    startInteractiveRegionCaptureNoActivity(name);

//                    startInteractiveRegionCapture(name);
                });

                return true;
            }


            default:
                Log.e(TAG, "Unknown step type: " + type);
                return false;
        }
    }

    // 小工具：dp 转 px（放在 ScriptRunner 里即可）
    private static int dp(Context ctx, int dp) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return (int) (dp * d + 0.5f);
    }


    /*
    用于模板制作 截图 因为考虑到 有遮罩 所以需要强制刷新下帧 让遮罩可以删除
     */
    public static boolean startInteractiveRegionCaptureV3(String saveName) {
        AutoAccessibilityService service = AutoAccessibilityService.get();
        if (service == null) return false;

        final Context ctx = service;
        final WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return false;

        final int layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        final SelectionOverlayView overlay = new SelectionOverlayView(ctx);

        overlay.setListener(new SelectionOverlayView.Listener() {
            @Override
            public void onConfirm(android.graphics.Rect rectInOverlay, Bitmap ignored) {
                // 先隐藏/移除 overlay，避免截到自己
                overlay.setVisibility(View.INVISIBLE);
                safeRemove(wm, overlay);
                // 2. 强制刷新 UI
                overlay.post(new Runnable() {
                    @Override
                    public void run() {
                        // 强制重绘
                        overlay.invalidate();

                        // 再延迟移除
                        safeRemove(wm, overlay);
                    }
                });
//                safeRemove(wm, overlay);
                // ✅ 不要在主线程截图/裁剪/写文件
                new Thread(() -> {
                    try {
                        final Activity a = ActivityHolder.getTopActivity();
                        if (a == null) {
                            MAIN.post(() -> toastOnMain("无法获取前台 Activity"));
                            return;
                        }
//                        try { Thread.sleep(500); } catch (InterruptedException ignoredEx) {}

                        // 4. 触发系统界面刷新
                        overlay.post(() -> {
                            try {
                                // 执行一个空操作来触发刷新
                                View decorView = a.getWindow().getDecorView();
                                decorView.invalidate();
                            } catch (Exception e) {
                                Log.w(TAG, "Force refresh failed", e);
                            }
                        });
//                        MediaProjectionCaptureService.cleanCopy();

                        // 给系统一点时间让 overlay 完全移除（非常关键！）
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignoredEx) {
                        }

                        // ✅ 单次截图：不要 continuous
//                        Bitmap full = com.auto.master.capture.ScreenCapture.captureNow(
//                                a,
//                                ScreenCapture.Method.A11Y_DUMP,
//                                "tmp.png"
//                        );
//                      使用教程： 、
//                      1、调用captureNow 会触发 init函数 会去校验程序
//                      2、所以高频情况下不要使用captureNow函数 而是先调用一次captureNow 然后 余下的高频用 getLastestScrrenshoot函数
                        Mat fullMat = ScreenCapture.captureNow(a, ScreenCapture.Method.MEDIA_PROJECTION_SINGLE_SHOOT, "tmp_full");
                        if (fullMat == null) {
                            //重试一次
                            fullMat = ScreenCapture.getSingleBitMapWhileInContinous(false);
                        }

                        if (fullMat == null) {
                            MAIN.post(() -> toastOnMain("截图失败/为空"));
                            return;
                        }

                        // rect 是 overlay 坐标，通常就等于屏幕坐标
                        Rect crop = new Rect(rectInOverlay);
                        normalizeRect(crop);

                        // ⚠️ 关键：做边界裁剪，防止越界
                        crop.left = clamp(crop.left, 0, fullMat.width());
                        crop.right = clamp(crop.right, 0, fullMat.width());
                        crop.top = clamp(crop.top, 0, fullMat.height());
                        crop.bottom = clamp(crop.bottom, 0, fullMat.height());
                        normalizeRect(crop);

                        if (crop.width() <= 2 || crop.height() <= 2) {
                            MAIN.post(() -> toastOnMain("选区太小"));
                            return;
                        }
                        Bitmap full = OpenCVHelper.getInstance().matToBitmap(fullMat);
                        Bitmap cropped = Bitmap.createBitmap(full, crop.left, crop.top, crop.width(), crop.height());

                        // full 不再需要
                        full.recycle();

                        File dir = AppStorage.getAppDirectory(ctx, "templates");

                        File file = new File(dir, saveName + ".png");
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            boolean ok = cropped.compress(Bitmap.CompressFormat.PNG, 100, fos);
                            MAIN.post(() -> toastOnMain(ok ? ("模板已保存: " + saveName) : "保存失败"));
                        } finally {
                            cropped.recycle();
                        }
                    } catch (Throwable e) {
                        Log.e(TAG, "confirm capture failed", e);
                        MAIN.post(() -> toastOnMain("保存失败: " + e.getMessage()));
                    } finally {
                        MAIN.post(() -> safeRemove(wm, overlay));
                    }
                }, "capture-confirm").start();
            }

            @Override
            public void onCancel() {
                toastOnMain("已取消");
                safeRemove(wm, overlay);
            }
        });

        MAIN.post(() -> {
            try {
                wm.addView(overlay, params);
                toastOnMain("拖动框选区域，调整后点“确定”保存");
            } catch (Throwable t) {
                Log.e(TAG, "addView failed", t);
                toastOnMain("创建选区失败：" + t.getMessage());
            }
        });

        return true;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static void normalizeRect(Rect r) {
        int l = Math.min(r.left, r.right);
        int rr = Math.max(r.left, r.right);
        int t = Math.min(r.top, r.bottom);
        int bb = Math.max(r.top, r.bottom);
        r.set(l, t, rr, bb);
    }


    /**
     * 把任意 bitmap 按 CenterCrop 缩放+裁剪为目标尺寸。
     * 横屏/竖屏/分辨率不一致都能稳定铺满。
     */
    private static Bitmap centerCropToSize(Bitmap src, int dstW, int dstH) {
        if (src == null) return null;
        if (dstW <= 0 || dstH <= 0) return src;

        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) return src;

        // 如果已经同尺寸，直接返回
        if (sw == dstW && sh == dstH) return src;

        float scale = Math.max(dstW / (float) sw, dstH / (float) sh);
        float scaledW = sw * scale;
        float scaledH = sh * scale;

        float dx = (dstW - scaledW) / 2f;
        float dy = (dstH - scaledH) / 2f;

        Bitmap out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);

        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postTranslate(dx, dy);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(src, m, p);
        return out;
    }

    public static void safeRemove(WindowManager wm, SelectionOverlayView overlay) {
        MAIN.post(() -> {
            try {
                wm.removeViewImmediate(overlay);
            } catch (Throwable ignored) {
            }
        });
    }

    public static void toastOnMain(String msg) {
        Activity a = ActivityHolder.getTopActivity();
        if (a != null) {
            a.runOnUiThread(() -> Toast.makeText(a, msg, Toast.LENGTH_SHORT).show());
        } else {
            // fallback 到全局
            MAIN.post(() ->
                    Toast.makeText(AutoAccessibilityService.get(), msg, Toast.LENGTH_SHORT).show()
            );
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static boolean performGlobal(AutoAccessibilityService svc, int action) {
        if (svc == null) return false;
        final boolean[] res = {false};
        MAIN.post(() -> res[0] = svc.performGlobalAction(action));
        sleep(150);
        return res[0];
    }

    private static boolean blockingClick(int x, int y) {
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) return false;

        final Object lock = new Object();
        final boolean[] done = {false};
        final boolean[] ok = {false};

        MAIN.post(() -> svc.click(x, y,
                () -> {
                    synchronized (lock) {
                        ok[0] = true;
                        done[0] = true;
                        lock.notifyAll();
                    }
                },
                () -> {
                    synchronized (lock) {
                        ok[0] = false;
                        done[0] = true;
                        lock.notifyAll();
                    }
                }
        ));

        long start = System.currentTimeMillis();
        long timeout = 3000; // 3s 超时，可从 JSON 里带上 timeoutMs
        synchronized (lock) {
            while (!done[0]) {
                long left = timeout - (System.currentTimeMillis() - start);
                if (left <= 0) break;
                try {
                    lock.wait(left);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return done[0] && ok[0];
    }

}
