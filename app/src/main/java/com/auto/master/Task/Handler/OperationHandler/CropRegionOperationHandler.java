package com.auto.master.Task.Handler.OperationHandler;

import static com.auto.master.auto.ScriptRunner.normalizeRect;
import static com.auto.master.auto.ScriptRunner.safeRemove;
import static com.auto.master.auto.ScriptRunner.toastOnMain;
import static org.opencv.android.NativeCameraView.TAG;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.auto.master.R;
import com.auto.master.Task.Operation.CropRegionOperation;
import com.auto.master.Task.Operation.DelayOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Template.Template;
import com.auto.master.auto.ActivityHolder;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.auto.ColorPointPickerView;
import com.auto.master.auto.SelectionOverlayView;
import com.auto.master.capture.CaptureScaleHelper;
import com.auto.master.capture.ScreenCapture;
import com.auto.master.capture.ScreenCaptureManager;
import com.auto.master.utils.OpenCVHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.opencv.core.Mat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 这是一个 click 的处理器
 * 它的作用就只是调用无障碍然后点击那个点
 */
public class CropRegionOperationHandler extends OperationHandler {

    public static Integer inited = 0;
    @Nullable
    private static volatile TemplateCaptureEventListener templateCaptureEventListener;

    public interface TemplateCaptureEventListener {
        void onTemplateSaved(String projectName, String taskName, String saveFileName, Rect rect);

        void onTemplateCaptureCancelled(String projectName, String taskName, String saveFileName);
    }

    public static void setTemplateCaptureEventListener(@Nullable TemplateCaptureEventListener listener) {
        templateCaptureEventListener = listener;
    }

    public static void clearTemplateCaptureEventListener(@Nullable TemplateCaptureEventListener expected) {
        if (expected == null || templateCaptureEventListener == expected) {
            templateCaptureEventListener = null;
        }
    }


//    和operation 对应
     CropRegionOperationHandler() {
         this.setType(3);
    }

    /**
     * 读取文件内容为字符串
     */
    public static String readFileToString(File file) {
        try {
            // 方法1：使用 Files (Java 7+)
//            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            // 方法2：使用 BufferedReader (兼容老版本)
        
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();

        } catch (IOException e) {
            Log.e(TAG, "读取文件失败: " + file.getAbsolutePath(), e);
            return "";
        }
    }

    /**
     * 保存 manifest 到 JSON 文件
     */
    private void saveManifestToFile(Map<String, List<Integer>> manifest, File outputFile) {
        BufferedWriter bufferedWriter = null;
        try {
            // 确保目录存在
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 使用 Gson 格式化输出（美化 JSON）
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonContent = gson.toJson(manifest);

            // 使用 BufferedWriter 写入文件
            bufferedWriter = new BufferedWriter(new FileWriter(outputFile));
            bufferedWriter.write(jsonContent);
            bufferedWriter.flush(); // 确保数据写入磁盘

            Log.d(TAG, "成功保存 manifest.json，共 " + manifest.size() + " 个条目");

        } catch (IOException e) {
            Log.e(TAG, "保存 manifest.json 失败", e);
            throw new RuntimeException("保存 manifest.json 失败", e);
        } finally {
            // 确保资源被释放
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.close();
                } catch (IOException e) {
                    Log.e(TAG, "关闭 BufferedWriter 失败", e);
                }
            }
        }
    }
    private void saveImg(Bitmap cropped, Context a, String projectName, String taskName,
                         String saveFileName, List<Integer> stdBbox) {
        saveImg(cropped, a, projectName, taskName, saveFileName, stdBbox, null);
    }

    private void saveImg(Bitmap cropped, Context a, String projectName, String taskName,
                         String saveFileName, List<Integer> stdBbox,
                         @Nullable String targetScaleDir) {
        File projectDir_ = new File(a.getExternalFilesDir(null),"projects");
        File projectDir = new File(projectDir_,projectName);
        File taskDir = new File(projectDir,taskName);
        File imgDir = new File(taskDir,"img");
        if (!imgDir.exists()){
            imgDir.mkdirs();
        }

        // scale-aware：优先使用调用方传入的 targetScaleDir（替换截图时保持与旧文件同目录），
        // 否则由当前 CAPTURE_SCALE 决定
        File scaleDir;
        if (!android.text.TextUtils.isEmpty(targetScaleDir)) {
            scaleDir = new File(imgDir, targetScaleDir);
            if (!scaleDir.exists()) scaleDir.mkdirs();
        } else {
            scaleDir = CaptureScaleHelper.getOrCreateScaleImgDir(imgDir, ScreenCaptureManager.CAPTURE_SCALE);
        }

        File croppedImgFile = new File(scaleDir, saveFileName);

        // 替换旧文件前清除 BitmapManager 的缩略图缓存，确保 UI 刷新后显示新截图
        com.auto.master.utils.BitmapManager.getInstance().removeBitmap(croppedImgFile.getAbsolutePath());
        // 同时清除 Template mat 缓存，确保下次匹配使用新模板
        Template.clearTaskSingleMatCache(projectName, taskName, saveFileName);

        try (FileOutputStream fileOutputStream = new FileOutputStream(croppedImgFile, false)) {
            boolean success = cropped.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            fileOutputStream.flush();
            if (success){
                Log.d(TAG, "保存模板成功: " + croppedImgFile.getAbsolutePath());
                toastOnMain("保存模板成功: " + croppedImgFile.getName());
            }
            Map<String,List<Integer>> manifest  = new HashMap<>();
            manifest.put(saveFileName,stdBbox);
            // manifest.json is stored per-scale in the same directory as the template image
            File manifestFile = new File(scaleDir, "manifest.json");
            if (manifestFile.exists()){
                String jsonContent = readFileToString(manifestFile);
                Type type = new TypeToken<Map<String, List<Integer>>>() {}.getType();
                Map<String, List<Integer>> existingManifest = new Gson().fromJson(jsonContent, type);
                if (existingManifest != null) {
                    manifest.putAll(existingManifest);
                    manifest.put(saveFileName,stdBbox);
                    Log.d(TAG, "成功加载现有 manifest.json，包含 " + manifest.size() + " 个条目");
                }
            } else {
                Log.d(TAG, "manifest.json 不存在，将创建新文件");
            }
            saveManifestToFile(manifest, manifestFile);

        } catch (Exception e) {
            return;
        }
    }

    private interface TemplateBboxReadyCallback {
        void onReady(Rect finalRect);
    }

    private void notifyTemplateSaved(String projectName,
                                     String taskName,
                                     String saveFileName,
                                     Rect rect) {
        TemplateCaptureEventListener listener = templateCaptureEventListener;
        if (listener == null || rect == null) {
            return;
        }
        Rect savedRect = new Rect(rect);
        new Handler(Looper.getMainLooper()).post(() ->
                listener.onTemplateSaved(projectName, taskName, saveFileName, savedRect));
    }

    private void notifyTemplateCaptureCancelled(String projectName,
                                                String taskName,
                                                String saveFileName) {
        TemplateCaptureEventListener listener = templateCaptureEventListener;
        if (listener == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() ->
                listener.onTemplateCaptureCancelled(projectName, taskName, saveFileName));
    }

    private void promptTemplateSaveMode(Context context,
                                        Rect coarseRect,
                                        Runnable onDirectSave,
                                        Runnable onRefine,
                                        Runnable onCancel) {
        new Handler(Looper.getMainLooper()).post(() -> {
            android.view.ContextThemeWrapper themed =
                    new android.view.ContextThemeWrapper(context, R.style.Theme_AtomMaster);
            String message = "粗选区域: x=" + coarseRect.left
                    + ", y=" + coarseRect.top
                    + ", w=" + coarseRect.width()
                    + ", h=" + coarseRect.height();
            AlertDialog dialog = new AlertDialog.Builder(themed)
                    .setTitle("模板截图")
                    .setMessage(message)
                    .setNegativeButton("取消", (d, which) -> {
                        if (onCancel != null) {
                            onCancel.run();
                        }
                    })
                    .setNeutralButton("进入精选", (d, which) -> {
                        if (onRefine != null) {
                            onRefine.run();
                        }
                    })
                    .setPositiveButton("直接保存", (d, which) -> {
                        if (onDirectSave != null) {
                            onDirectSave.run();
                        }
                    })
                    .create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            }
            dialog.show();
        });
    }

    private void showTemplateRefinePointPicker(Context context,
                                               WindowManager wm,
                                               Bitmap sourceBitmap,
                                               Rect coarseRect,
                                               TemplateBboxReadyCallback callback,
                                               @Nullable Runnable onCancel) {
        if (sourceBitmap == null || sourceBitmap.isRecycled() || coarseRect == null) {
            toastOnMain("精选失败：截图为空");
            return;
        }
        // coarseRect is in screen coordinates; sourceBitmap is at capture scale.
        // Convert screen coords to capture-scale pixel offsets for the bitmap crop.
        float captureScale = ScreenCaptureManager.CAPTURE_SCALE;
        int scaledLeft   = (int)(coarseRect.left   * captureScale);
        int scaledTop    = (int)(coarseRect.top     * captureScale);
        int scaledRight  = (int)(coarseRect.right   * captureScale);
        int scaledBottom = (int)(coarseRect.bottom  * captureScale);
        scaledLeft   = Math.max(0, Math.min(scaledLeft,   sourceBitmap.getWidth() - 1));
        scaledTop    = Math.max(0, Math.min(scaledTop,    sourceBitmap.getHeight() - 1));
        scaledRight  = Math.max(scaledLeft + 1, Math.min(scaledRight,  sourceBitmap.getWidth()));
        scaledBottom = Math.max(scaledTop + 1,  Math.min(scaledBottom, sourceBitmap.getHeight()));
        if (scaledRight - scaledLeft <= 1 || scaledBottom - scaledTop <= 1) {
            toastOnMain("粗选区域过小，无法精选");
            return;
        }
        final int refineLeft   = scaledLeft;
        final int refineTop    = scaledTop;

        Bitmap refineBitmap = Bitmap.createBitmap(sourceBitmap,
                scaledLeft, scaledTop, scaledRight - scaledLeft, scaledBottom - scaledTop);

        new Handler(Looper.getMainLooper()).post(() -> {
            View overlay = LayoutInflater.from(context).inflate(R.layout.dialog_refine_template_bbox_overlay, null);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                    PixelFormat.TRANSLUCENT
            );
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

            ColorPointPickerView pickerView = overlay.findViewById(R.id.template_refine_picker_view);
            View floatingPanel = overlay.findViewById(R.id.template_refine_floating_panel);
            TextView tvStep = overlay.findViewById(R.id.tv_template_refine_step);
            TextView tvCoord = overlay.findViewById(R.id.tv_template_refine_coord);
            ImageView ivPreview = overlay.findViewById(R.id.iv_template_refine_preview);
            TextView btnConfirm = overlay.findViewById(R.id.btn_template_refine_confirm);

            final int[] firstPoint = new int[2];
            final boolean[] firstSelected = {false};
            final Bitmap[] previewBitmapHolder = new Bitmap[1];
            final long[] lastPreviewTs = {0L};

            pickerView.setOnSelectionChangedListener((x, y, color) -> {
                if (tvCoord != null) {
                    // x, y are capture-scale pixel offsets within refineBitmap
                    // Convert to screen coords for display
                    int absScreenX = captureScale > 0 ? Math.round((refineLeft + x) / captureScale) : (refineLeft + x);
                    int absScreenY = captureScale > 0 ? Math.round((refineTop  + y) / captureScale) : (refineTop  + y);
                    if (firstSelected[0]) {
                        int left = Math.min(firstPoint[0], x);
                        int top = Math.min(firstPoint[1], y);
                        int width = Math.abs(firstPoint[0] - x) + 1;
                        int height = Math.abs(firstPoint[1] - y) + 1;
                        int dispX = captureScale > 0 ? Math.round((refineLeft + left) / captureScale) : (refineLeft + left);
                        int dispY = captureScale > 0 ? Math.round((refineTop  + top ) / captureScale) : (refineTop  + top );
                        tvCoord.setText("x=" + dispX
                                + ", y=" + dispY
                                + "  w=" + width
                                + ", h=" + height);
                    } else {
                        tvCoord.setText("x=" + absScreenX + ", y=" + absScreenY);
                    }
                }
                if (firstSelected[0] && ivPreview != null) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastPreviewTs[0] >= 40L) {
                        lastPreviewTs[0] = now;
                        updateTemplateRefinePreview(ivPreview, refineBitmap,
                                firstPoint[0], firstPoint[1], x, y, previewBitmapHolder);
                    }
                }
            });
            pickerView.setOnMagnifierLayoutChangedListener(rect ->
                    updateFloatingPanelPosition(floatingPanel, rect));
            pickerView.setScreenshot(refineBitmap, true);
            pickerView.setSelection(0, 0);
            if (tvStep != null) {
                tvStep.setText("精选第1步：选择左上角");
            }
            if (btnConfirm != null) {
                btnConfirm.setText("下一步");
            }

            View btnCancel = overlay.findViewById(R.id.btn_template_refine_cancel);
            if (btnCancel != null) {
                btnCancel.setOnClickListener(v -> {
                    recyclePreviewBitmap(previewBitmapHolder);
                    pickerView.release();
                    try {
                        wm.removeView(overlay);
                    } catch (Exception ignored) {
                    }
                    toastOnMain("已取消精选");
                    if (onCancel != null) {
                        onCancel.run();
                    }
                });
            }

            if (btnConfirm != null) {
                btnConfirm.setOnClickListener(v -> {
                    if (!pickerView.hasSelection()) {
                        toastOnMain("请先选择一个点");
                        return;
                    }
                    if (!firstSelected[0]) {
                        firstPoint[0] = pickerView.getSelectedX();
                        firstPoint[1] = pickerView.getSelectedY();
                        firstSelected[0] = true;
                        pickerView.setSelection(refineBitmap.getWidth() - 1, refineBitmap.getHeight() - 1);
                        if (tvStep != null) {
                            tvStep.setText("精选第2步：选择右下角");
                        }
                        if (ivPreview != null) {
                            ivPreview.setVisibility(View.VISIBLE);
                            updateTemplateRefinePreview(ivPreview, refineBitmap,
                                    firstPoint[0], firstPoint[1],
                                    pickerView.getSelectedX(), pickerView.getSelectedY(),
                                    previewBitmapHolder);
                        }
                        btnConfirm.setText("完成精选");
                        return;
                    }

                    int secondX = pickerView.getSelectedX();
                    int secondY = pickerView.getSelectedY();
                    int left = Math.min(firstPoint[0], secondX);
                    int top = Math.min(firstPoint[1], secondY);
                    int right = Math.max(firstPoint[0], secondX) + 1;
                    int bottom = Math.max(firstPoint[1], secondY) + 1;
                    // Convert capture-scale offsets back to screen coords for saveTemplateFromRect
                    Rect finalRect = new Rect(
                            captureScale > 0 ? Math.round((refineLeft + left)   / captureScale) : (refineLeft + left),
                            captureScale > 0 ? Math.round((refineTop  + top)    / captureScale) : (refineTop  + top),
                            captureScale > 0 ? Math.round((refineLeft + right)  / captureScale) : (refineLeft + right),
                            captureScale > 0 ? Math.round((refineTop  + bottom) / captureScale) : (refineTop  + bottom)
                    );
                    recyclePreviewBitmap(previewBitmapHolder);
                    pickerView.release();
                    try {
                        wm.removeView(overlay);
                    } catch (Exception ignored) {
                    }
                    if (callback != null) {
                        callback.onReady(finalRect);
                    }
                });
            }

            try {
                wm.addView(overlay, lp);
                toastOnMain("精选模式：请先选左上角，再选右下角");
            } catch (Throwable t) {
                recyclePreviewBitmap(previewBitmapHolder);
                pickerView.release();
                Log.e(TAG, "add refine overlay failed", t);
                if (onCancel != null) {
                    onCancel.run();
                } else {
                    recycleBitmap(sourceBitmap);
                }
                toastOnMain("创建精选界面失败：" + t.getMessage());
            }
        });
    }

    private void updateFloatingPanelPosition(View panel, RectF magnifierBounds) {
        if (panel == null || magnifierBounds == null) {
            return;
        }
        panel.post(() -> {
            if (!(panel.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                return;
            }
            View parent = (View) panel.getParent();
            if (parent == null) {
                return;
            }
            int parentWidth = parent.getWidth();
            int parentHeight = parent.getHeight();
            if (parentWidth <= 0 || parentHeight <= 0) {
                return;
            }
            if (panel.getWidth() <= 0 || panel.getHeight() <= 0) {
                panel.measure(
                        View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(parentHeight, View.MeasureSpec.AT_MOST)
                );
            }
            int panelWidth = Math.max(panel.getWidth(), panel.getMeasuredWidth());
            int panelHeight = Math.max(panel.getHeight(), panel.getMeasuredHeight());
            int margin = dp(12);
            int gap = dp(12);

            int left;
            int top;
            if (magnifierBounds.right + gap + panelWidth <= parentWidth - margin) {
                left = Math.round(magnifierBounds.right + gap);
                top = Math.round(magnifierBounds.centerY() - panelHeight / 2f);
            } else if (magnifierBounds.left - gap - panelWidth >= margin) {
                left = Math.round(magnifierBounds.left - gap - panelWidth);
                top = Math.round(magnifierBounds.centerY() - panelHeight / 2f);
            } else if (magnifierBounds.bottom + gap + panelHeight <= parentHeight - margin) {
                left = Math.round(magnifierBounds.centerX() - panelWidth / 2f);
                top = Math.round(magnifierBounds.bottom + gap);
            } else {
                left = Math.round(magnifierBounds.centerX() - panelWidth / 2f);
                top = Math.round(magnifierBounds.top - gap - panelHeight);
            }

            left = Math.max(margin, Math.min(left, parentWidth - panelWidth - margin));
            top = Math.max(margin, Math.min(top, parentHeight - panelHeight - margin));

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) panel.getLayoutParams();
            layoutParams.gravity = Gravity.TOP | Gravity.START;
            layoutParams.leftMargin = left;
            layoutParams.topMargin = top;
            panel.setLayoutParams(layoutParams);
        });
    }

    private void recyclePreviewBitmap(Bitmap[] previewHolder) {
        if (previewHolder == null || previewHolder.length == 0) {
            return;
        }
        Bitmap preview = previewHolder[0];
        if (preview != null && !preview.isRecycled()) {
            preview.recycle();
        }
        previewHolder[0] = null;
    }

    private void recycleBitmap(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private void updateTemplateRefinePreview(ImageView previewView,
                                             Bitmap sourceBitmap,
                                             int firstX,
                                             int firstY,
                                             int secondX,
                                             int secondY,
                                             Bitmap[] previewHolder) {
        if (previewView == null || sourceBitmap == null || sourceBitmap.isRecycled()) {
            return;
        }
        int left = Math.max(0, Math.min(firstX, secondX));
        int top = Math.max(0, Math.min(firstY, secondY));
        int right = Math.min(sourceBitmap.getWidth(), Math.max(firstX, secondX) + 1);
        int bottom = Math.min(sourceBitmap.getHeight(), Math.max(firstY, secondY) + 1);
        if (right <= left || bottom <= top) {
            previewView.setVisibility(View.GONE);
            recyclePreviewBitmap(previewHolder);
            return;
        }

        int previewSize = dp(88);
        Bitmap previewBitmap = Bitmap.createBitmap(previewSize, previewSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(previewBitmap);
        canvas.drawColor(Color.WHITE);
        Rect src = new Rect(left, top, right, bottom);
        Rect dst = new Rect(0, 0, previewSize, previewSize);
        float srcRatio = src.width() / (float) src.height();
        float dstRatio = 1f;
        if (srcRatio > dstRatio) {
            int targetHeight = Math.max(1, Math.round(previewSize / srcRatio));
            int offset = (previewSize - targetHeight) / 2;
            dst.set(0, offset, previewSize, offset + targetHeight);
        } else {
            int targetWidth = Math.max(1, Math.round(previewSize * srcRatio));
            int offset = (previewSize - targetWidth) / 2;
            dst.set(offset, 0, offset + targetWidth, previewSize);
        }
        canvas.drawBitmap(sourceBitmap, src, dst, null);
        recyclePreviewBitmap(previewHolder);
        previewHolder[0] = previewBitmap;
        previewView.setImageBitmap(previewBitmap);
        previewView.setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        Context ctx = AutoAccessibilityService.get();
        if (ctx == null) {
            return value;
        }
        return (int) (value * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void saveTemplateFromRect(Bitmap sourceBitmap,
                                      Rect rect,
                                      Context context,
                                      String projectName,
                                      String taskName,
                                      String saveFileName,
                                      @Nullable String targetScaleDir) {
        if (sourceBitmap == null || sourceBitmap.isRecycled() || rect == null) {
            toastOnMain("保存失败：模板区域无效");
            return;
        }

        // rect 是 SelectionOverlayView 返回的屏幕坐标（view 坐标系）。
        // sourceBitmap 是 capture 分辨率下的截图（可能是 540×1168 等缩小版）。
        // 需要将屏幕坐标转换为 bitmap 坐标后才能正确裁剪。
        float captureScale = ScreenCaptureManager.CAPTURE_SCALE;

        // 将屏幕坐标转换为 bitmap（capture-scale）坐标用于裁剪。
        // 必须与 ScreenCaptureManager.sanitizeRoi() 使用相同的截断（floor）方式，
        // 否则模板尺寸与 screenMat ROI 尺寸会差 1px，导致模板无法匹配。
        int bitmapLeft   = (int)(rect.left   * captureScale);
        int bitmapTop    = (int)(rect.top     * captureScale);
        int bitmapRight  = (int)(rect.right   * captureScale);
        int bitmapBottom = (int)(rect.bottom  * captureScale);

        // 夹紧到 bitmap 边界
        bitmapLeft   = Math.max(0, Math.min(bitmapLeft,   sourceBitmap.getWidth() - 1));
        bitmapTop    = Math.max(0, Math.min(bitmapTop,    sourceBitmap.getHeight() - 1));
        bitmapRight  = Math.max(bitmapLeft + 1, Math.min(bitmapRight,  sourceBitmap.getWidth()));
        bitmapBottom = Math.max(bitmapTop + 1,  Math.min(bitmapBottom, sourceBitmap.getHeight()));

        if (bitmapRight - bitmapLeft <= 1 || bitmapBottom - bitmapTop <= 1) {
            toastOnMain("选区太小");
            return;
        }

        // manifest.json 中始终存储屏幕坐标（scale-neutral），
        // 运行时由 ScreenCaptureManager.sanitizeRoi 统一转换到 capture 坐标
        Rect safeScreenRect = new Rect(rect);

        Bitmap cropped = null;
        try {
            cropped = Bitmap.createBitmap(sourceBitmap,
                    bitmapLeft, bitmapTop,
                    bitmapRight - bitmapLeft, bitmapBottom - bitmapTop);
            List<Integer> bbox = Arrays.asList(
                    safeScreenRect.left,
                    safeScreenRect.top,
                    safeScreenRect.width(),
                    safeScreenRect.height()
            );
            saveImg(cropped, context, projectName, taskName, saveFileName, bbox, targetScaleDir);
            notifyTemplateSaved(projectName, taskName, saveFileName, safeScreenRect);
        } finally {
            recycleBitmap(cropped);
        }
    }


    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        CropRegionOperation cropRegionOperation = (CropRegionOperation) obj;

        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) return false;


        //这是这个operation定义的一些东西
        Map<String, Object> inputMap = cropRegionOperation.getInputMap();
        List<Double> bbox = new ArrayList<>();
        Object o = inputMap.get(MetaOperation.BBOX);
        if (o instanceof ArrayList){
            bbox = (ArrayList) o;
        }else {
            bbox = null;
        }
        Object project = inputMap.get(MetaOperation.PROJECT);

        String projectName;
        if (project instanceof String){
            projectName = (String) project;
        } else {
            projectName = "fallback";
        }

        Object task = inputMap.get(MetaOperation.TASK);

        String taskName;
        if (task instanceof String){
            taskName = (String) task;
        } else {
            taskName = "";
        }

        Object saveFile = inputMap.get(MetaOperation.SAVEFILENAME);

        String saveFileName;
        if (saveFile instanceof String){
            saveFileName = (String) saveFile;
        } else {
            saveFileName = "fallback.png";
        }

        // 替换截图时传入的目标 scale 子目录（如 "scale_100"），null 表示由当前 CAPTURE_SCALE 决定
        Object targetScaleDirObj = inputMap.get(MetaOperation.TARGET_SCALE_DIR);
        String targetScaleDir = targetScaleDirObj instanceof String
                ? (String) targetScaleDirObj : null;

        final Activity a = ActivityHolder.getTopActivity();

        AutoAccessibilityService autoAccessibilityService = AutoAccessibilityService.get();
        if (autoAccessibilityService==null){
            return false;
        }
//        int currentPhysicalRotation = AutoAccessibilityService.getCurrentPhysicalRotation(autoAccessibilityService);

        /**
         * 然后这里需要注意的是 如果有 bbox 直接给它裁剪一下
         */
        Bitmap cropped = null;
        if (a == null) return false;

        Bitmap full = ScreenCapture.captureLatestBitmap(null, 480L, 80L);
        if (full == null || full.isRecycled()) {
            return false;
        }
        List<Integer> stdBbox = new ArrayList<>();
        if (bbox!=null&&bbox.size()==4){
            Double x = bbox.get(0);
            Double y = bbox.get(1);
            Double w = bbox.get(2);
            Double h = bbox.get(3);
//        裁剪的bitmap
            cropped  = Bitmap.createBitmap(full, x.intValue(), y.intValue(), w.intValue(), h.intValue());
            stdBbox.add(x.intValue());
            stdBbox.add(y.intValue());
            stdBbox.add(w.intValue());
            stdBbox.add(h.intValue());
        }
//        /标准的bbox格式




        //调用结果  产生一个 response  放在 obj 或者 ctx里面
        Integer responseType = obj.getResponseType();
        // response =1 代表产生 默认的 response
        if (responseType==null||responseType==1){
            Map<String, Object> res = new HashMap<>();
            res.put(MetaOperation.FULLBITMAPRES,full);
            res.put(MetaOperation.CROPPEDBITMAPRES,cropped);
            ctx.currentResponse = res;
            ctx.lastOperation = obj;

            return true;
        } else if (responseType==null||responseType==2) {
            //这里比上一种多了一步操作 -->> 保存图片
//            保存图片
            saveImg(cropped,a,projectName,taskName,saveFileName,stdBbox);

            Map<String, Object> res = new HashMap<>();
            res.put(MetaOperation.BBOX, stdBbox.isEmpty() ? null : stdBbox);
            res.put(MetaOperation.RESULT, saveFileName);
            ctx.currentResponse = res;
            ctx.lastOperation = obj;
            recycleBitmap(cropped);
            recycleBitmap(full);
            return true;
            //todo 记得回收
            //这里把mat 存在缓存里
//            OpenCVHelper cv = OpenCVHelper.getInstance();
//            Mat screenMat = cv.bitmapToMat(bitmap);
//            Template.putCache(projectName,"temp",screenMat);
//            bitmap.recycle();
        }else if (responseType==null||responseType==3){
            //手动选图！！！
            AutoAccessibilityService service = AutoAccessibilityService.get();
            if (service == null) return false;
            final Context ctx1 = service;
            final WindowManager wm = (WindowManager) ctx1.getSystemService(Context.WINDOW_SERVICE);
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

            final SelectionOverlayView overlay = new SelectionOverlayView(ctx1);
            Bitmap finalFull = full;
            overlay.setFrozenBackground(finalFull, false);
            overlay.setRefineEnabled(true);
            overlay.setListener(new SelectionOverlayView.Listener() {
                @Override
                public void onConfirm(android.graphics.Rect rectInOverlay, Bitmap ignored) {
                    Rect coarseRect = new Rect(rectInOverlay);
                    normalizeRect(coarseRect);
                    overlay.setVisibility(View.INVISIBLE);
                    safeRemove(wm, overlay);
                    promptTemplateSaveMode(ctx1, coarseRect,
                            () -> {
                                saveTemplateFromRect(finalFull, coarseRect, ctx1, projectName, taskName, saveFileName, targetScaleDir);
                                recycleBitmap(finalFull);
                            },
                            () -> showTemplateRefinePointPicker(ctx1, wm, finalFull, coarseRect,
                                    finalRect -> {
                                        saveTemplateFromRect(finalFull, finalRect, ctx1, projectName, taskName, saveFileName, targetScaleDir);
                                        recycleBitmap(finalFull);
                                    },
                                    () -> {
                                        recycleBitmap(finalFull);
                                        notifyTemplateCaptureCancelled(projectName, taskName, saveFileName);
                                    }),
                            () -> {
                                toastOnMain("已取消");
                                recycleBitmap(finalFull);
                                notifyTemplateCaptureCancelled(projectName, taskName, saveFileName);
                            });
                }

                @Override
                public void onCancel() {
                    toastOnMain("已取消");
                    safeRemove(wm, overlay);
                    recycleBitmap(finalFull);
                    notifyTemplateCaptureCancelled(projectName, taskName, saveFileName);
                }
            });
            overlay.setOnRefineRequestedListener(rectInOverlay -> {
                Rect coarseRect = new Rect(rectInOverlay);
                normalizeRect(coarseRect);
                overlay.setVisibility(View.INVISIBLE);
                safeRemove(wm, overlay);
                showTemplateRefinePointPicker(ctx1, wm, finalFull, coarseRect,
                        finalRect -> {
                            saveTemplateFromRect(finalFull, finalRect, ctx1, projectName, taskName, saveFileName, targetScaleDir);
                            recycleBitmap(finalFull);
                        },
                        () -> {
                            recycleBitmap(finalFull);
                            notifyTemplateCaptureCancelled(projectName, taskName, saveFileName);
                        });
            });

            getMainHandler().post(() -> {
                try {
                    wm.addView(overlay, params);
                    toastOnMain("拖动框选区域，调整后点“确定”保存");
                } catch (Throwable t) {
                    recycleBitmap(finalFull);
                    Log.e(TAG, "addView failed", t);
                    toastOnMain("创建选区失败：" + t.getMessage());
                }
            });
        }


        return false;
    }


}
