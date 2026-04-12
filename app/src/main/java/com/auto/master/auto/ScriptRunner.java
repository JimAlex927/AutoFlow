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
import android.provider.Settings;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.auto.master.Task.Handler.OperationHandler.LoadImgToMatOperationHandler;
import com.auto.master.Task.Handler.OperationHandler.OperationHandler;
import com.auto.master.Task.Handler.OperationHandler.OperationHandlerManager;
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
import com.auto.master.utils.MatchResult;
import com.auto.master.utils.OpenCVHelper;

public final class ScriptRunner {

    private static final String TAG = "ScriptRunner";
    private static final ExecutorService SINGLE = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // 当前执行线程，用于中断
    private static volatile Thread currentExecuteThread;

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
        
        /**
         * operation 执行完成
         * @param operationId operation 的 ID
         * @param success 是否成功
         */
        void onOperationComplete(String operationId, boolean success);
        
        /**
         * 所有 operation 执行完成
         */
        void onScriptComplete();

        /**
         * Task 切换（用于悬浮窗显示当前 Task 的 operations）
         * @param taskId Task 的 ID（文件夹名）
         * @param taskName Task 的显示名称
         * @param operations Task 中的 operation 列表
         */
        void onTaskSwitch(String taskId, String taskName, List<com.auto.master.floatwin.OperationItem> operations);
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
        return currentListener;
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
        SINGLE.execute(() -> {
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
        SINGLE.execute(() -> {
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

    // 当前的执行上下文，用于控制暂停/停止
    private static ScriptExecuteContext currentExecuteContext;

    /**
     * 暂停当前运行的脚本
     */
    public static void pauseCurrentScript() {
        if (currentExecuteContext != null) {
            currentExecuteContext.pause();
            Log.d(TAG, "脚本已暂停");
        }
    }

    /**
     * 恢复当前暂停的脚本
     */
    public static void resumeCurrentScript() {
        if (currentExecuteContext != null) {
            currentExecuteContext.resume();
            Log.d(TAG, "脚本已恢复");
        }
    }

    /**
     * 停止当前运行的脚本
     */
    public static void stopCurrentScript() {
        if (currentExecuteContext != null) {
            currentExecuteContext.stop();
            // 中断执行线程，让阻塞的 operation 立即退出
            if (currentExecuteThread != null) {
                currentExecuteThread.interrupt();
            }
            Log.d(TAG, "脚本已停止");
        }
    }

    /**
     * 获取当前脚本是否暂停
     */
    public static boolean isCurrentScriptPaused() {
        return currentExecuteContext != null && currentExecuteContext.paused;
    }

    /**
     * 获取当前脚本是否正在运行
     */
    public static boolean isCurrentScriptRunning() {
        return currentExecuteContext != null && currentExecuteContext.running;
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
            case 9: return "OCR识别(已弃用)";
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

    public static void runOperation(ScriptExecuteContext scriptExecuteContext) {
        currentExecuteContext = scriptExecuteContext;

//        1、无限循环
        SINGLE.execute(

//                运行之前先重新加载资源文件
//                重新加载下项目的资源文件到内存

                () -> {
//                    @Amalrich 我直接把wakelock注释掉 似乎不会导致脚本休眠
//                    PowerManager.WakeLock wakeLock = null;
//                    try {
//                        PowerManager pm = (PowerManager) com.auto.master.AtommApplication.instance
//                                .getSystemService(Context.POWER_SERVICE);
//                        if (pm != null) {
//                            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AtommMaster:ScriptRunner");
//                            wakeLock.acquire(); // 持有至脚本结束，finally 块中主动释放
//                        }
//                    } catch (Exception ignored) {}
                    int consecutiveErrors = 0;
                    try {
//                    *************************************************
                        LoadImgToMatOperation loadImgToMatOperation = new LoadImgToMatOperation();
                        loadImgToMatOperation.setId("loadResource");
                        loadImgToMatOperation.setResponseType(1);
                        HashMap<String, Object> tmpInputMap = new HashMap<>();
                        String projectName = "";
                        if (scriptExecuteContext.sharedContext != null
                                && scriptExecuteContext.sharedContext.anchorProject != null
                                && scriptExecuteContext.sharedContext.anchorProject.getProjectName() != null) {
                            projectName = scriptExecuteContext.sharedContext.anchorProject.getProjectName();
                        }
                        tmpInputMap.put(MetaOperation.PROJECT, projectName);
                        loadImgToMatOperation.setInputMap(tmpInputMap);
                        new LoadImgToMatOperationHandler().handle(loadImgToMatOperation, new OperationContext());
//                    *************************************************

                        currentExecuteThread = Thread.currentThread();
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

                                                    if (currentListener != null) {
                                                        final ScriptExecutionListener listener = currentListener;
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
                                                                    com.auto.master.floatwin.FloatWindowService.extractDelayShowCountdown(op)
                                                            ));
                                                        }
                                                        MAIN.post(() -> listener.onTaskSwitch(taskId, taskName, operationItems));
                                                    }

                                                    SystemClock.sleep(10);
                                                    continue;
                                                }
                                            }
                                        }
                                    }

                                    scriptExecuteContext.running = false;
                                    break;
                                }

                                final String opId = operation.getId();
                                final String opName = operation.getName();
                                if (currentListener != null) {
                                    final ScriptExecutionListener listener = currentListener;
                                    MAIN.post(() -> listener.onOperationStart(opId, opName));
                                }

                                Integer type = operation.getType();
                                OperationHandler operationHandler = OperationHandlerManager.getOperationHandler(type);
                                if (operationHandler == null) {
                                    Log.e(TAG, "未找到 OperationHandler, type=" + type + ", opId=" + opId);
                                    if (currentListener != null) {
                                        final ScriptExecutionListener listener = currentListener;
                                        MAIN.post(() -> listener.onOperationComplete(opId, false));
                                    }
                                    scriptExecuteContext.running = false;
                                    break;
                                }

                                boolean ok = operationHandler.handle(operation, scriptExecuteContext.sharedContext);

                                if (currentListener != null) {
                                    final ScriptExecutionListener listener = currentListener;
                                    MAIN.post(() -> listener.onOperationComplete(opId, ok));
                                }

                                if (!ok) {
                                    scriptExecuteContext.running = false;
                                    break;
                                }

                                Map<String, Object> currentResponse = scriptExecuteContext.sharedContext.currentResponse;
                                Integer responseType = operation.getResponseType();
                                DefaultResponseHandler responseHandler = ResponseHandlerManager.getResponseHandler(operation.getClass(), responseType);
                                if (responseHandler == null) {
                                    Log.e(TAG, "未找到 ResponseHandler, class=" + operation.getClass().getSimpleName() + ", responseType=" + responseType);
                                    scriptExecuteContext.running = false;
                                    break;
                                }
                                responseHandler.process(currentResponse, scriptExecuteContext);
                                consecutiveErrors = 0;
                            } catch (InterruptedException e) {
                                Log.d(TAG, "脚本执行被中断");
                                Thread.currentThread().interrupt();
                                scriptExecuteContext.running = false;
                                break;
                            } catch (Exception e) {
                                consecutiveErrors++;
                                Log.e(TAG, "运行线程抛出异常(" + consecutiveErrors + "): " + e.getMessage(), e);
                                if (consecutiveErrors >= 3) {
                                    scriptExecuteContext.running = false;
                                    break;
                                }
                                SystemClock.sleep(50L * consecutiveErrors);
                            }
                        }
                    } finally {
                        currentExecuteContext = null;
                        currentExecuteThread = null;
//                        if (wakeLock != null && wakeLock.isHeld()) {
//                            wakeLock.release();
//                        }
                        if (currentListener != null) {
                            final ScriptExecutionListener listener = currentListener;
                            MAIN.post(listener::onScriptComplete);
                        }
                    }
                }
        );

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
//                获取当前图片
                Mat latestMat = com.auto.master.capture.ScreenCapture.captureNow(a, m, out);
                // region crop\  [553,139,727,176]
//                bitmap = Bitmap.createBitmap(bitmap, 553, 139, 727 - 553, 176 - 139);
                File file = new File(a.getExternalFilesDir(null), "temp.png");
                file.getParentFile().mkdirs();
                FileOutputStream fileOutputStream = new FileOutputStream(file);
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

                        File dir = new File(ctx.getExternalFilesDir(null), "templates");
                        //noinspection ResultOfMethodCallIgnored
                        dir.mkdirs();

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
            new Handler(Looper.getMainLooper()).post(() ->
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
