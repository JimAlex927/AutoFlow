package com.auto.master.Task.Handler.OperationHandler;

import static com.auto.master.auto.ScriptRunner.normalizeRect;
import static com.auto.master.auto.ScriptRunner.safeRemove;
import static com.auto.master.auto.ScriptRunner.toastOnMain;
import static java.lang.Math.clamp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.auto.master.Task.Operation.CropRegionOperation;
import com.auto.master.Task.Operation.LoadImgToMatOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Template.Template;
import com.auto.master.auto.ActivityHolder;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.auto.GestureOverlayView;
import com.auto.master.auto.SelectionOverlayView;
import com.auto.master.capture.CaptureScaleHelper;
import com.auto.master.capture.ScreenCapture;
import com.auto.master.capture.ScreenCaptureManager;
import com.auto.master.utils.OpenCVHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 这是一个 click 的处理器
 * 它的作用就只是调用无障碍然后点击那个点
 */
public class LoadImgToMatOperationHandler extends OperationHandler {

    private static final String TAG = "LoadImgToMatOpHandler";
    // Gson 实例线程安全，静态复用避免反射初始化开销
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    //    和operation 对应
    public LoadImgToMatOperationHandler() {
        this.setType(4);
    }


//    private void requestMediaProjection() {
//        // 先检查是否已授权（避免重复弹窗）
//        if (ScreenCaptureManager.getInstance().isRunning()) {

    /// /            Toast.makeText(this, "录屏已在运行中", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        Activity topActivity = ActivityHolder.getTopActivity();
//        Intent intent = ScreenCapture.createProjectionIntent(topActivity);
//        topActivity.projectionLauncher.launch(intent);
//    }
    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        LoadImgToMatOperation loadImgToMatOperation = (LoadImgToMatOperation) obj;

        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) return false;

        Map<String, Object> inputMap = loadImgToMatOperation.getInputMap();

        Object project = inputMap.get(MetaOperation.PROJECT);
        String projectName;
        if (project instanceof String) {
            projectName = (String) project;
        } else {
            projectName = "fallback";
        }

        // 优先用 Service 自身 Context，避免依赖 Activity（Activity 可能为 null）
        final Context appCtx = svc.getApplicationContext();
        File projectDir_ = new File(appCtx.getExternalFilesDir(null), "projects");
        File projectDir = new File(projectDir_, projectName);
        File[] taskDirs = projectDir.listFiles();
        if (taskDirs == null || taskDirs.length == 0) {
            Log.w(TAG, "项目没有可加载的 task: " + projectDir.getAbsolutePath());
            return true;
        }

        //
        for (File task : taskDirs) {
            if (task == null || !task.isDirectory()) {
                continue;
            }
            String taskName = task.getName();
            long snapshotToken = computeTaskSnapshot(task);
            if (!Template.shouldReloadTaskCache(projectName, taskName, snapshotToken)) {
                continue;
            }
            //这里写一个
            File imgDir = new File(task, "img");
            File gestureDir = new File(task, "gesture");
            File[] gestureFiles = gestureDir.listFiles();
            Map<String, GestureOverlayView.GestureNode> taskGestureNodes = new HashMap<>();
            if (gestureFiles != null) {
                for (File gestureDataFile : gestureFiles) {
                    try (FileReader reader = new FileReader(gestureDataFile)) {
                        // 直接反序列化為 GestureNode
                        GestureOverlayView.GestureNode node = GSON.fromJson(reader, GestureOverlayView.GestureNode.class);

                        if (node == null) {
                            Log.e("GestureLoader", "反序列化結果為 null");
                        } else {
                            Log.d("GestureLoader", "成功載入手勢節點，動作數: ");

                        }
                        taskGestureNodes.put(gestureDataFile.getName(), node);


                    } catch (Exception e) {

                    }
                }
//            这里存放task级别的node数据
                Template.putTaskGestureCache(projectName, taskName, taskGestureNodes);
            }


            if (!imgDir.exists()) {
                imgDir.mkdirs();
            }
            if (!gestureDir.exists()) {
                gestureDir.mkdirs();
            }

            // 加载当前 CAPTURE_SCALE 对应子目录的 manifest 和模板图片
            // 模板文件已按 capture scale 保存，无需再缩放
            String scaleDirName = com.auto.master.capture.CaptureScaleHelper.getScaleDirName(ScreenCaptureManager.CAPTURE_SCALE);
            File scaleDir = new File(imgDir, scaleDirName);
            File manifestFile = new File(scaleDir, "manifest.json");

            // 降级：如果 scale 目录不存在，尝试 legacy 平铺目录（scale=1.0 旧文件）
            boolean isLegacy = false;
            if (!manifestFile.exists()) {
                File legacyManifest = new File(imgDir, "manifest.json");
                if (legacyManifest.exists()) {
                    manifestFile = legacyManifest;
                    isLegacy = true;
                }
            }

            if (manifestFile.exists()) {
                String jsonContent = CropRegionOperationHandler.readFileToString(manifestFile);
                Type type = new TypeToken<Map<String, List<Integer>>>() {}.getType();
                Map<String, List<Integer>> existingManifest = GSON.fromJson(jsonContent, type);
                if (existingManifest == null) {
                    existingManifest = new HashMap<>();
                }
                Template.putTaskManifestCache(projectName, taskName, existingManifest);

                // 图片文件所在目录：scale 子目录（新）或 imgDir（legacy）
                File templateDir = isLegacy ? imgDir : scaleDir;

                Map<String, Mat> projectTaskMatMap = new HashMap<>();
                for (Map.Entry<String, List<Integer>> entry : existingManifest.entrySet()) {
                    String imgName = entry.getKey();
                    File imgFile = new File(templateDir, imgName);
                    if (!imgFile.exists()) {
                        Log.e(TAG, "文件不存在: " + imgFile.getPath());
                        continue;
                    }
                    Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getPath());
                    if (bitmap == null) {
                        Log.e(TAG, "无法解码图片文件: " + imgFile.getPath());
                        continue;
                    }
                    Mat mat = new Mat();
                    boolean matOk = false;
                    try {
                        Utils.bitmapToMat(bitmap, mat);
                        // legacy 文件需要按当前 scale 缩放；新 scale 子目录文件已是正确分辨率
                        // 使用实际轴向缩放系数，修正 16-byte 对齐导致 X/Y 轴缩放不一致的问题
                        if (isLegacy && ScreenCaptureManager.CAPTURE_SCALE != 1.0f) {
                            ScreenCaptureManager mgr = ScreenCaptureManager.getInstance();
                            float sx = mgr.getActualScaleX();
                            float sy = mgr.getActualScaleY();
                            Mat scaled = new Mat();
                            Imgproc.resize(mat, scaled,
                                    new org.opencv.core.Size(),
                                    sx, sy,
                                    Imgproc.INTER_LINEAR);
                            mat.release();
                            mat = scaled;
                        }
                        projectTaskMatMap.put(imgName, mat);
                        matOk = true;
                    } finally {
                        bitmap.recycle();
                        if (!matOk) mat.release();
                    }
                }
                Template.putTaskMatCache(projectName, taskName, projectTaskMatMap);
            }
            Template.markTaskCacheSnapshot(projectName, taskName, snapshotToken);
        }

        //调用结果  产生一个 response  放在 obj 或者 ctx里面
        Integer responseType = obj.getResponseType();
        // response =1 代表产生 默认的 response
        if (responseType == null || responseType == 1) {
            Map<String, Object> res = new HashMap<>();
            ctx.currentResponse = res;

            ctx.currentOperation = obj;
            ctx.lastOperation = obj;

            return true;
        }
        return false;
    }

    private long computeTaskSnapshot(File taskDir) {
        if (taskDir == null || !taskDir.exists()) {
            return -1L;
        }
        long max = taskDir.lastModified();
        File[] direct = taskDir.listFiles();
        if (direct == null) {
            return max;
        }
        for (File child : direct) {
            if (child == null) {
                continue;
            }
            max = Math.max(max, child.lastModified());
            if (child.isDirectory()) {
                File[] nested = child.listFiles();
                if (nested == null) {
                    continue;
                }
                for (File nestedFile : nested) {
                    if (nestedFile != null) {
                        max = Math.max(max, nestedFile.lastModified());
                    }
                }
            }
        }
        return max;
    }


}
