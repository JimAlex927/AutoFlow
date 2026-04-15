package com.auto.master.floatwin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.auto.master.R;
import com.auto.master.auto.ColorPointPickerView;
import com.auto.master.auto.AutoAccessibilityService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 管理截图后打开的取点/取色悬浮层，减少 FloatWindowService 的 UI 责任。
 */
public class CapturePickerHelper {

    public interface Host {
        Context getContext();
        WindowManager getWindowManager();
        Handler getUiHandler();
        int dp(int value);
        void showToast(String message);
        @Nullable Bitmap captureFreshScreenBitmap();
        @Nullable View getProjectPanelView();
        Runnable hideViewsForCapture(View... viewsToHide);
    }

    public interface OnPointPickedListener {
        void onPointPicked(int x, int y);
    }

    public interface OnColorPointPickedListener {
        void onColorPointPicked(int x, int y, int color);
    }

    private static final long PICKER_SETTLE_DELAY_MS = 220L;

    private final Host host;

    public CapturePickerHelper(Host host) {
        this.host = host;
    }

    public void showScreenPointPicker(OnPointPickedListener listener, View... viewsToHide) {
        List<View> hideTargets = buildHideTargets(viewsToHide);
        Runnable restoreViews = host.hideViewsForCapture(hideTargets.toArray(new View[0]));
        postToUiDelayed(() -> {
            try {
                Bitmap fullBitmap = host.captureFreshScreenBitmap();
                if (fullBitmap == null || fullBitmap.isRecycled()) {
                    restoreViews.run();
                    host.showToast("截图失败，无法取点");
                    return;
                }

                WindowManager overlayWm = getPickerOverlayWindowManager();
                Context overlayContext = getPickerOverlayContext();
                View overlay = LayoutInflater.from(overlayContext).inflate(R.layout.dialog_pick_point_overlay, null);
                WindowManager.LayoutParams lp = buildPickerOverlayParams();
                ColorPointPickerView pickerView = overlay.findViewById(R.id.point_picker_view);
                View floatingPanel = overlay.findViewById(R.id.pick_point_floating_panel);
                TextView tvCoord = overlay.findViewById(R.id.tv_pick_point_coord);
                pickerView.setOnSelectionChangedListener((x, y, color) -> {
                    if (tvCoord != null) {
                        tvCoord.setText("x=" + x + ", y=" + y);
                    }
                });
                pickerView.setOnMagnifierLayoutChangedListener(rect ->
                        updateFloatingPickerPanelPosition(floatingPanel, rect));
                pickerView.setScreenshot(fullBitmap, true);

                overlay.findViewById(R.id.btn_pick_point_cancel).setOnClickListener(v -> {
                    pickerView.release();
                    safeRemoveView(overlayWm, overlay);
                    restoreViews.run();
                });
                overlay.findViewById(R.id.btn_pick_point_confirm).setOnClickListener(v -> {
                    if (!pickerView.hasSelection()) {
                        host.showToast("请先移动到目标位置");
                        return;
                    }
                    if (listener != null) {
                        com.auto.master.capture.ScreenCaptureManager captureManager =
                                com.auto.master.capture.ScreenCaptureManager.getInstance();
                        int screenX = captureManager.captureToScreenX(pickerView.getSelectedX());
                        int screenY = captureManager.captureToScreenY(pickerView.getSelectedY());
                        listener.onPointPicked(screenX, screenY);
                    }
                    pickerView.release();
                    safeRemoveView(overlayWm, overlay);
                    restoreViews.run();
                });

                try {
                    overlayWm.addView(overlay, lp);
                } catch (Throwable t) {
                    pickerView.release();
                    throw t;
                }
            } catch (Exception e) {
                restoreViews.run();
                host.showToast("打开取点器失败: " + e.getMessage());
            }
        }, PICKER_SETTLE_DELAY_MS);
    }

    public void showColorPointPicker(OnColorPointPickedListener listener, View... viewsToHide) {
        List<View> hideTargets = buildHideTargets(viewsToHide);
        Runnable restoreViews = host.hideViewsForCapture(hideTargets.toArray(new View[0]));
        postToUiDelayed(() -> {
            try {
                Bitmap fullBitmap = host.captureFreshScreenBitmap();
                if (fullBitmap == null || fullBitmap.isRecycled()) {
                    restoreViews.run();
                    host.showToast("截图失败，无法取色");
                    return;
                }

                WindowManager overlayWm = getPickerOverlayWindowManager();
                Context overlayContext = getPickerOverlayContext();
                View overlay = LayoutInflater.from(overlayContext).inflate(R.layout.dialog_pick_color_overlay, null);
                WindowManager.LayoutParams lp = buildPickerOverlayParams();
                ColorPointPickerView pickerView = overlay.findViewById(R.id.color_picker_view);
                View floatingPanel = overlay.findViewById(R.id.pick_color_floating_panel);
                TextView tvColorValue = overlay.findViewById(R.id.tv_pick_color_value);
                TextView tvCoord = overlay.findViewById(R.id.tv_pick_color_coord);
                View preview = overlay.findViewById(R.id.view_pick_color_preview);
                pickerView.setOnSelectionChangedListener((x, y, color) -> {
                    if (tvColorValue != null) {
                        tvColorValue.setText(String.format(Locale.getDefault(), "#%06X", 0xFFFFFF & color));
                    }
                    if (tvCoord != null) {
                        tvCoord.setText("x=" + x + ", y=" + y);
                    }
                    if (preview != null) {
                        preview.setBackgroundColor(color);
                    }
                });
                pickerView.setOnMagnifierLayoutChangedListener(rect ->
                        updateFloatingPickerPanelPosition(floatingPanel, rect));
                pickerView.setScreenshot(fullBitmap, true);

                overlay.findViewById(R.id.btn_pick_color_cancel).setOnClickListener(v -> {
                    pickerView.release();
                    safeRemoveView(overlayWm, overlay);
                    restoreViews.run();
                });
                overlay.findViewById(R.id.btn_pick_color_confirm).setOnClickListener(v -> {
                    if (!pickerView.hasSelection()) {
                        host.showToast("请先移动到目标像素");
                        return;
                    }
                    if (listener != null) {
                        com.auto.master.capture.ScreenCaptureManager captureManager =
                                com.auto.master.capture.ScreenCaptureManager.getInstance();
                        int screenX = captureManager.captureToScreenX(pickerView.getSelectedX());
                        int screenY = captureManager.captureToScreenY(pickerView.getSelectedY());
                        listener.onColorPointPicked(screenX, screenY, pickerView.getSelectedColor());
                    }
                    pickerView.release();
                    safeRemoveView(overlayWm, overlay);
                    restoreViews.run();
                });

                try {
                    overlayWm.addView(overlay, lp);
                } catch (Throwable t) {
                    pickerView.release();
                    throw t;
                }
            } catch (Exception e) {
                restoreViews.run();
                host.showToast("打开取色器失败: " + e.getMessage());
            }
        }, PICKER_SETTLE_DELAY_MS);
    }

    private List<View> buildHideTargets(View... viewsToHide) {
        List<View> hideTargets = new ArrayList<>();
        hideTargets.add(host.getProjectPanelView());
        if (viewsToHide != null) {
            Collections.addAll(hideTargets, viewsToHide);
        }
        return hideTargets;
    }

    private void postToUiDelayed(Runnable action, long delayMs) {
        if (action == null) {
            return;
        }
        Handler handler = host.getUiHandler();
        if (handler != null) {
            handler.postDelayed(action, delayMs);
        } else {
            action.run();
        }
    }

    private Context getPickerOverlayContext() {
        AutoAccessibilityService service = AutoAccessibilityService.get();
        return service != null ? service : host.getContext();
    }

    private WindowManager getPickerOverlayWindowManager() {
        Context overlayContext = getPickerOverlayContext();
        WindowManager overlayWm = (WindowManager) overlayContext.getSystemService(Context.WINDOW_SERVICE);
        return overlayWm != null ? overlayWm : host.getWindowManager();
    }

    private WindowManager.LayoutParams buildPickerOverlayParams() {
        int type;
        if (AutoAccessibilityService.get() != null) {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
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
        return lp;
    }

    private void safeRemoveView(@Nullable WindowManager targetWm, View view) {
        if (view == null) {
            return;
        }
        try {
            WindowManager removeWm = targetWm != null ? targetWm : host.getWindowManager();
            if (removeWm != null) {
                removeWm.removeView(view);
            }
        } catch (Exception ignored) {
        }
    }

    private void updateFloatingPickerPanelPosition(View panel, RectF magnifierBounds) {
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
            int margin = host.dp(12);
            int gap = host.dp(12);

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
}
