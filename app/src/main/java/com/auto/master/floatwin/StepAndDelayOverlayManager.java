package com.auto.master.floatwin;

import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.auto.master.R;

import java.util.Locale;

/**
 * 执行步骤指示器 & 延迟进度悬浮层的生命周期管理。
 * 从 FloatWindowService（14k 行 God 类）中拆分出来，职责单一。
 *
 * <p>使用方式：在 FloatWindowService 中持有实例，
 * 将 showStep / hideStep / maybeStartDelay / stopDelay 等调用委托至此类。
 * 在 Service.onDestroy() 中调用 {@link #destroy()} 进行清理。</p>
 */
public class StepAndDelayOverlayManager {

    private static final String TAG = "StepDelayOverlay";
    private static final long DELAY_TICK_MS = 220L;

    private final FloatWindowHost host;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // ── Step indicator overlay ─────────────────────────────────────────────
    private View stepOverlayView;
    private WindowManager.LayoutParams stepOverlayLp;

    // ── Delay progress overlay ─────────────────────────────────────────────
    private View delayOverlayView;
    private WindowManager.LayoutParams delayOverlayLp;
    private ProgressBar delayOverlayProgressBar;
    private TextView delayOverlayValueText;

    // ── Delay state ────────────────────────────────────────────────────────
    private String activeDelayOperationId;
    private long activeDelayDurationMs = 0L;
    private long activeDelayStartMs = 0L;
    private int lastDelayOverlayProgress = -1;
    private String lastDelayOverlayText = "";

    private final Runnable delayProgressRunnable = this::tickDelayProgress;

    public StepAndDelayOverlayManager(FloatWindowHost host) {
        this.host = host;
    }

    // ── Step overlay ───────────────────────────────────────────────────────

    /** 显示/更新步骤指示器。可从任意线程调用（内部切换到主线程）。 */
    public void showStep(String operationName, String typeLabel, int stepIndex, int total) {
        uiHandler.post(() -> {
            WindowManager wm = host.getWindowManager();
            if (stepOverlayView == null) {
                stepOverlayView = LayoutInflater.from(host.getContext())
                        .inflate(R.layout.overlay_step_indicator, null);
                stepOverlayLp = buildOverlayLp(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
                stepOverlayLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                stepOverlayLp.x = 0;
                stepOverlayLp.y = getStatusBarHeightPx() + host.dp(6);
                try {
                    wm.addView(stepOverlayView, stepOverlayLp);
                } catch (Exception e) {
                    Log.e(TAG, "addView stepOverlay failed", e);
                    stepOverlayView = null;
                    return;
                }
            }
            TextView tvName = stepOverlayView.findViewById(R.id.tv_step_op_name);
            TextView tvInfo = stepOverlayView.findViewById(R.id.tv_step_info);
            View typeBar = stepOverlayView.findViewById(R.id.step_type_bar);
            if (tvName != null) tvName.setText(operationName);
            if (tvInfo != null) tvInfo.setText("步骤 " + stepIndex + " / " + total
                    + (typeLabel != null ? "  [" + typeLabel + "]" : ""));
            if (typeBar != null) typeBar.setBackgroundColor(getTypeColor(typeLabel));
        });
    }

    /** 隐藏步骤指示器。可从任意线程调用。 */
    public void hideStep() {
        uiHandler.post(() -> {
            if (stepOverlayView != null) {
                safeRemove(stepOverlayView);
                stepOverlayView = null;
                stepOverlayLp = null;
            }
        });
    }

    /** 根据操作类型标签返回对应的颜色值。 */
    public int getTypeColor(String typeLabel) {
        if (typeLabel == null) return 0xFF4CAF50;
        switch (typeLabel) {
            case "点击":    return 0xFF1E88E5;
            case "延时":    return 0xFFFB8C00;
            case "手势":    return 0xFF8E24AA;
            case "截图区域": return 0xFF039BE5;
            case "模板匹配": return 0xFF00897B;
            case "颜色匹配": return 0xFFD84315;
            case "跳转Task": return 0xFFE53935;
            case "OCR":    return 0xFF3949AB;
            case "条件分支": return 0xFFFF6F00;
            case "启动应用": return 0xFF43A047;
            default:       return 0xFF4CAF50;
        }
    }

    // ── Delay progress overlay ─────────────────────────────────────────────

    /** 如果 opItem 是带倒计时的延迟操作，则启动延迟进度显示。 */
    public void maybeStartDelay(@Nullable OperationItem opItem) {
        stopDelay();
        if (opItem == null || opItem.delayDurationMs <= 0L || !opItem.delayShowCountdown) return;
        activeDelayOperationId = opItem.id;
        activeDelayDurationMs = opItem.delayDurationMs;
        activeDelayStartMs = SystemClock.uptimeMillis();
        renderDelayProgress(true, 0L, activeDelayDurationMs);
        uiHandler.postDelayed(delayProgressRunnable, DELAY_TICK_MS);
    }

    /** 如果 operationId 正是当前延迟操作，则停止延迟进度显示。 */
    public void stopDelayIfMatch(String operationId) {
        if (android.text.TextUtils.equals(activeDelayOperationId, operationId)) {
            stopDelay();
        }
    }

    /** 停止延迟进度显示并隐藏悬浮层。 */
    public void stopDelay() {
        uiHandler.removeCallbacks(delayProgressRunnable);
        activeDelayOperationId = null;
        activeDelayDurationMs = 0L;
        activeDelayStartMs = 0L;
        renderDelayProgress(false, 0L, 0L);
    }

    /**
     * 重新同步延迟进度到当前真实状态（面板重建后调用）。
     * 对应原 FloatWindowService.renderDelayProgressState()。
     */
    public void syncDelayState() {
        if (!TextUtils.isEmpty(activeDelayOperationId) && activeDelayDurationMs > 0L) {
            long elapsed = Math.min(activeDelayDurationMs,
                    Math.max(0L, SystemClock.uptimeMillis() - activeDelayStartMs));
            renderDelayProgress(true, elapsed, activeDelayDurationMs);
        } else {
            renderDelayProgress(false, 0L, 0L);
        }
    }

    /** 销毁所有悬浮层（在 Service.onDestroy() 中调用）。 */
    public void destroy() {
        uiHandler.removeCallbacks(delayProgressRunnable);
        // hideStep() 通过 post() 异步执行，onDestroy 不等待；直接同步移除。
        if (stepOverlayView != null) {
            safeRemove(stepOverlayView);
            stepOverlayView = null;
            stepOverlayLp = null;
        }
        destroyDelayOverlay();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private void tickDelayProgress() {
        if (TextUtils.isEmpty(activeDelayOperationId) || activeDelayDurationMs <= 0L) {
            renderDelayProgress(false, 0L, 0L);
            return;
        }
        long elapsed = Math.min(activeDelayDurationMs,
                Math.max(0L, SystemClock.uptimeMillis() - activeDelayStartMs));
        renderDelayProgress(true, elapsed, activeDelayDurationMs);
        if (elapsed < activeDelayDurationMs) {
            uiHandler.postDelayed(delayProgressRunnable, DELAY_TICK_MS);
        }
    }

    private void renderDelayProgress(boolean visible, long elapsedMs, long durationMs) {
        if (!visible || durationMs <= 0L) {
            lastDelayOverlayProgress = -1;
            lastDelayOverlayText = "";
            if (delayOverlayView != null) delayOverlayView.setVisibility(View.GONE);
            return;
        }
        ensureDelayOverlay();
        if (delayOverlayProgressBar == null || delayOverlayValueText == null) return;
        if (delayOverlayView.getVisibility() != View.VISIBLE) {
            delayOverlayView.setVisibility(View.VISIBLE);
        }
        long safeElapsed = Math.max(0L, Math.min(elapsedMs, durationMs));
        int progress = (int) Math.min(1000L, (safeElapsed * 1000L) / durationMs);
        String text = "延迟 " + fmtDuration(safeElapsed) + " / " + fmtDuration(durationMs);
        if (progress != lastDelayOverlayProgress) {
            delayOverlayProgressBar.setProgress(progress);
            lastDelayOverlayProgress = progress;
        }
        if (!text.equals(lastDelayOverlayText)) {
            delayOverlayValueText.setText(text);
            lastDelayOverlayText = text;
        }
    }

    private void ensureDelayOverlay() {
        if (delayOverlayView != null && delayOverlayView.getParent() != null) return;
        try {
            if (delayOverlayView == null) {
                delayOverlayView = LayoutInflater.from(host.getContext())
                        .inflate(R.layout.overlay_delay_progress, null);
                delayOverlayProgressBar =
                        delayOverlayView.findViewById(R.id.progress_delay_overlay);
                delayOverlayValueText =
                        delayOverlayView.findViewById(R.id.tv_delay_overlay_value);
            }
            delayOverlayLp = buildOverlayLp(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            delayOverlayLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            delayOverlayLp.x = 0;
            delayOverlayLp.y = getStatusBarHeightPx() + host.dp(38);
            host.getWindowManager().addView(delayOverlayView, delayOverlayLp);
        } catch (Exception e) {
            Log.e(TAG, "addView delayOverlay failed", e);
            delayOverlayLp = null;
            delayOverlayProgressBar = null;
            delayOverlayValueText = null;
            if (delayOverlayView != null && delayOverlayView.getParent() == null) {
                delayOverlayView = null;
            }
        }
    }

    private void destroyDelayOverlay() {
        if (delayOverlayView != null) safeRemove(delayOverlayView);
        delayOverlayView = null;
        delayOverlayLp = null;
        delayOverlayProgressBar = null;
        delayOverlayValueText = null;
        lastDelayOverlayProgress = -1;
        lastDelayOverlayText = "";
    }

    private WindowManager.LayoutParams buildOverlayLp(int w, int h, int flags) {
        return new WindowManager.LayoutParams(
                w, h,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                flags,
                PixelFormat.TRANSLUCENT);
    }

    private void safeRemove(View v) {
        if (v == null) return;
        try { host.getWindowManager().removeView(v); } catch (Exception ignored) {}
    }

    private int getStatusBarHeightPx() {
        int resId = host.getContext().getResources()
                .getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            try {
                return host.getContext().getResources().getDimensionPixelSize(resId);
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private String fmtDuration(long ms) {
        if (ms <= 0L)           return "0s";
        if (ms < 1000L)         return ms + "ms";
        if (ms % 1000L == 0L)  return (ms / 1000L) + "s";
        return String.format(Locale.getDefault(), "%.1fs", ms / 1000f);
    }
}
