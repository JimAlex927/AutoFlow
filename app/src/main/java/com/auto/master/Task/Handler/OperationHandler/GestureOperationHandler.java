package com.auto.master.Task.Handler.OperationHandler;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.auto.master.Task.Operation.GestureOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Template.Template;
import com.auto.master.auto.ActivityHolder;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.auto.GestureOverlayView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * 手势操作处理器 - 简化版（无截图验证）
 * 
 * 完全依赖无障碍回调，不截图验证
 * 与混淆代码保持一致
 */
public class GestureOperationHandler extends OperationHandler {

    private static final String TAG = "GestureOperationHandler";
    // Gson 实例是线程安全的，静态复用避免每次反射初始化
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    GestureOperationHandler() {
        this.setType(5);
    }

    public void saveGestureData(GestureOverlayView.GestureNode node, Context a, String projectName, String taskName, String saveFileName) {
        File projectDir_ = new File(a.getExternalFilesDir(null), "projects");
        File projectDir = new File(projectDir_, projectName);
        File taskDir = new File(projectDir, taskName);
        File gestureDir = new File(taskDir, "gesture");
        if (!gestureDir.exists()) {
            gestureDir.mkdirs();
        }
        File gestureDataFile = new File(gestureDir, saveFileName);
        try (FileWriter writer = new FileWriter(gestureDataFile)) {
            GSON.toJson(node, writer);
        } catch (Exception e) {
            throw new RuntimeException("保存手势数据 JSON 失败", e);
        }
    }

    public GestureOverlayView.GestureNode loadGestureData(
            Context context,
            String projectName,
            String taskName,
            String saveFileName) {

        if (context == null || TextUtils.isEmpty(projectName) || TextUtils.isEmpty(saveFileName)) {
            Log.w("GestureLoader", "載入手勢參數無效");
            return null;
        }

        try {
            File projectDir_ = new File(context.getExternalFilesDir(null), "projects");
            File projectDir = new File(projectDir_, projectName);
            File taskDir = new File(projectDir, taskName);
            File gestureDir = new File(taskDir, "gesture");
            File gestureDataFile = new File(gestureDir, saveFileName);

            if (!gestureDataFile.exists() || !gestureDataFile.isFile()) {
                Log.w("GestureLoader", "手勢檔案不存在: " + gestureDataFile.getAbsolutePath());
                return null;
            }

            try (FileReader reader = new FileReader(gestureDataFile)) {
                GestureOverlayView.GestureNode node = GSON.fromJson(reader, GestureOverlayView.GestureNode.class);
                if (node == null) {
                    Log.e("GestureLoader", "反序列化結果為 null");
                } else {
                    Log.d("GestureLoader", "成功載入手勢節點");
                }
                return node;
            }
        } catch (Exception e) {
            Log.e("GestureLoader", "載入手勢資料失敗", e);
        }
        return null;
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        GestureOperation gestureOperation = (GestureOperation) obj;
        AutoAccessibilityService svc = AutoAccessibilityService.get();

        if (svc == null) {
            Log.e(TAG, "AutoAccessibilityService is null");
            return false;
        }

        Map<String, Object> inputMap = gestureOperation.getInputMap();
        String projectName = getStringSafe(inputMap, MetaOperation.PROJECT, "fallback");
        String taskName = getStringSafe(inputMap, MetaOperation.TASK, "");
        String saveFileName = getStringSafe(inputMap, MetaOperation.SAVEFILENAME, "gesture_" + System.currentTimeMillis() + ".json");
        String gestureTemplateIdRaw = getStringSafe(inputMap, MetaOperation.GESTURE_TEMPLATE_ID, saveFileName);
        final String gestureTemplateId = TextUtils.isEmpty(gestureTemplateIdRaw) ? saveFileName : gestureTemplateIdRaw;

        Log.d(TAG, "手势操作 - 项目: " + projectName + ", 任务: " + taskName + ", 模板: " + gestureTemplateId + ", 响应类型: " + obj.getResponseType());

        if (obj.getResponseType() == 1) {
            // 录制模式
            getMainHandler().post(() -> {
                Activity topActivity = ActivityHolder.getTopActivity();
                if (topActivity == null || topActivity.isFinishing()) {
                    Log.w(TAG, "沒有可用的 Activity，無法開始手勢錄製");
                    return;
                }

                svc.startGestureRecording(externalNode -> {
                    getMainHandler().postDelayed(() -> {
                        svc.replayGesture(externalNode, null);
                        saveGestureData(externalNode, topActivity, projectName, taskName, gestureTemplateId);
                        if (!TextUtils.equals(gestureTemplateId, saveFileName)) {
                            saveGestureData(externalNode, topActivity, projectName, taskName, saveFileName);
                        }
                        Template.putTaskSingleGestureCache(projectName, taskName, gestureTemplateId, externalNode);
                    }, 500);
                });
            });
            ctx.currentOperation = obj;
            ctx.lastOperation = obj;
            return true;
        } else if (obj.getResponseType() == 2) {
            // 执行模式 - 同步等待手势完成
            Activity topActivity = ActivityHolder.getTopActivity();
            GestureOverlayView.GestureNode gestureNode;
            gestureNode = Template.getTaskSingleGestureCache(projectName, taskName, gestureTemplateId);
            if (gestureNode == null) {
                gestureNode = loadGestureData(topActivity, projectName, taskName, gestureTemplateId);
                if (gestureNode != null) {
                    Template.putTaskSingleGestureCache(projectName, taskName, gestureTemplateId, gestureNode);
                }
            }

            if (gestureNode == null || gestureNode.strokes == null || gestureNode.strokes.isEmpty()) {
                Log.e(TAG, "手势数据无效 - gestureNode: " + (gestureNode == null ? "null" : "存在") + 
                    ", strokes: " + (gestureNode == null ? "N/A" : (gestureNode.strokes == null ? "null" : gestureNode.strokes.size() + "个")));
                return false;
            }

            Log.d(TAG, "开始执行手势 - 笔画数: " + gestureNode.strokes.size() + ", 持续时间: " + gestureNode.duration + "ms");

            // 同步执行手势，阻塞直到完成（包括所有重试）
            final boolean[] successResult = new boolean[]{false};
            final Object lock = new Object();
            final GestureOverlayView.GestureNode finalGestureNode = gestureNode;
            final boolean[] callbackInvoked = new boolean[]{false};

            // 先显示手势轨迹
            getMainHandler().post(() -> svc.showGestureTrail(finalGestureNode));
            
            // 在同步块内调用 replayGesture，确保 wait 在 notify 之前
            synchronized (lock) {
                svc.replayGesture(finalGestureNode, success -> {
                    synchronized (lock) {
                        Log.d(TAG, "手势执行回调 - 成功: " + success);
                        successResult[0] = success;
                        callbackInvoked[0] = true;
                        lock.notify();
                    }
                });

                // 等待手势执行完成，最多等待 30 秒
                try {
                    if (!callbackInvoked[0]) {
                        Log.d(TAG, "等待手势执行完成...");
                        lock.wait(30000);
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "等待手势执行被中断", e);
                    Thread.currentThread().interrupt();
                    return false;
                }
                
                if (!callbackInvoked[0]) {
                    Log.e(TAG, "手势执行超时");
                    return false;
                }
            }

            Log.d(TAG, "手势执行完成 - 结果: " + successResult[0]);

            ctx.currentOperation = obj;
            ctx.lastOperation = obj;
            Map<String, Object> res = new HashMap<>();
            res.put("GESTURE_PLAYED", successResult[0]);
            ctx.currentResponse = res;
            return successResult[0];
        }

        ctx.currentOperation = obj;
        ctx.lastOperation = obj;
        return false;
    }

    private String getStringSafe(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v instanceof String) ? (String) v : def;
    }
}
