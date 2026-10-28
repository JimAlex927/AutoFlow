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
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.auto.master.R;

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

    private final FloatWindowHost host;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // ── Step indicator overlay ─────────────────────────────────────────────
    private View stepOverlayView;
    private WindowManager.LayoutParams stepOverlayLp;

    // ── Delay progress overlay ─────────────────────────────────────────────
    private DelayCountdownOverlayView delayOverlayView;
    private WindowManager.LayoutParams delayOverlayLp;

    // ── Delay state ────────────────────────────────────────────────────────
    private String activeDelayOperationId;
    private long activeDelayDurationMs = 0L;
    private long activeDelayStartMs = 0L;
    private long activeDelayElapsedBeforePauseMs = 0L;
    private long activeDelayShownAtMs = 0L;
    private boolean activeDelayPaused = false;
    private Runnable pendingStopRunnable;

    // 倒计时悬浮条至少可见这么久，避免主线程繁忙时"显示"和"隐藏"两个任务
    // 背靠背执行、中间来不及渲染出一帧，导致倒计时看起来"没显示"。
    private static final long MIN_VISIBLE_MS = 150L;

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

    /**
     * 更新步骤覆盖层上的 MTry 尝试次数徽章。
     * current=0 / total=0 时隐藏徽章。
     */
    public void showMtryAttempt(int current, int total) {
        uiHandler.post(() -> {
            if (stepOverlayView == null) return;
            TextView tvBadge = stepOverlayView.findViewById(R.id.tv_mtry_attempt);
            if (tvBadge == null) return;
            if (current <= 0 || total <= 0) {
                tvBadge.setVisibility(View.GONE);
            } else {
                tvBadge.setText("试 " + current + "/" + total);
                tvBadge.setVisibility(View.VISIBLE);
            }
        });
    }

    /** 启动 MTry 重试间延时倒计时覆盖层（复用已有延时 overlay）。 */
    public void startRetryDelay(String operationId, long durationMs) {
        uiHandler.post(() -> {
            stopDelay();
            if (durationMs <= 0) return;
            activeDelayOperationId = operationId + "_retry";
            activeDelayDurationMs = durationMs;
            startDelayClock();
            renderDelayProgress(true, 0L, activeDelayDurationMs);
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
            case "多次尝试节点": return 0xFF5E35B1;
            case "条件分支": return 0xFFFF6F00;
            case "启动应用": return 0xFF43A047;
            default:       return 0xFF4CAF50;
        }
    }

    // ── Delay progress overlay ─────────────────────────────────────────────

    /** 如果 opItem 是带倒计时的延迟操作，则启动延迟进度显示。 */
    public void maybeStartDelay(@Nullable OperationItem opItem) {
        maybeStartDelay(opItem, null);
    }

    /** 如果 opItem 是带倒计时的延迟操作，则启动延迟进度显示。 */
    public void maybeStartDelay(@Nullable OperationItem opItem, @Nullable Runnable onFirstFrameReady) {
        cancelPendingStop();
        stopDelay();
        if (opItem == null) return;
        long durationMs = opItem.nodePreDelayRandom
                ? opItem.nodePreDelayMaxMs
                : (opItem.nodePreDelayMs > 0L ? opItem.nodePreDelayMs : opItem.delayDurationMs);
        boolean showCountdown = durationMs > 0L || opItem.delayShowCountdown;
        if (durationMs <= 0L || !showCountdown) return;
        activeDelayOperationId = opItem.id;
        activeDelayDurationMs = durationMs;
        startDelayClock();
        renderDelayProgress(true, 0L, activeDelayDurationMs, onFirstFrameReady);
    }

    /** 暂停或恢复当前倒计时；暂停期间不消耗剩余延时时间，也不请求绘制帧。 */
    public void setPaused(boolean paused) {
        Runnable update = () -> {
            if (TextUtils.isEmpty(activeDelayOperationId)
                    || activeDelayDurationMs <= 0L
                    || activeDelayPaused == paused) {
                return;
            }
            if (paused) {
                activeDelayElapsedBeforePauseMs = getActiveDelayElapsedMs();
                activeDelayPaused = true;
                if (delayOverlayView != null) {
                    delayOverlayView.pause();
                }
                return;
            }
            activeDelayStartMs = SystemClock.uptimeMillis();
            activeDelayPaused = false;
            if (delayOverlayView != null) {
                delayOverlayView.resume();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            update.run();
        } else {
            uiHandler.post(update);
        }
    }

    /**
     * 如果 operationId 正是当前延迟操作，则停止延迟进度显示。
     * 若悬浮条刚显示不久（小于 {@link #MIN_VISIBLE_MS}），延后到至少可见这么久再隐藏，
     * 避免主线程繁忙时"显示"与"隐藏"背靠背执行导致用户完全看不到倒计时。
     */
    public void stopDelayIfMatch(String operationId) {
        if (!android.text.TextUtils.equals(activeDelayOperationId, operationId)) {
            return;
        }
        long visibleElapsed = SystemClock.uptimeMillis() - activeDelayShownAtMs;
        if (activeDelayDurationMs > 0L && visibleElapsed < MIN_VISIBLE_MS) {
            cancelPendingStop();
            pendingStopRunnable = this::stopDelay;
            uiHandler.postDelayed(pendingStopRunnable, MIN_VISIBLE_MS - visibleElapsed);
        } else {
            stopDelay();
        }
    }

    /** 停止延迟进度显示并隐藏悬浮层。 */
    public void stopDelay() {
        cancelPendingStop();
        activeDelayOperationId = null;
        activeDelayDurationMs = 0L;
        activeDelayStartMs = 0L;
        activeDelayElapsedBeforePauseMs = 0L;
        activeDelayShownAtMs = 0L;
        activeDelayPaused = false;
        renderDelayProgress(false, 0L, 0L);
    }

    private void cancelPendingStop() {
        if (pendingStopRunnable != null) {
            uiHandler.removeCallbacks(pendingStopRunnable);
            pendingStopRunnable = null;
        }
    }

    /**
     * 重新同步延迟进度到当前真实状态（面板重建后调用）。
     * 对应原 FloatWindowService.renderDelayProgressState()。
     */
    public void syncDelayState() {
        if (!TextUtils.isEmpty(activeDelayOperationId) && activeDelayDurationMs > 0L) {
            long elapsed = getActiveDelayElapsedMs();
            renderDelayProgress(true, elapsed, activeDelayDurationMs);
            if (activeDelayPaused && delayOverlayView != null) {
                delayOverlayView.pause();
            }
        } else {
            renderDelayProgress(false, 0L, 0L);
        }
    }

    /** 销毁所有悬浮层（在 Service.onDestroy() 中调用）。 */
    public void destroy() {
        cancelPendingStop();
        // hideStep() 通过 post() 异步执行，onDestroy 不等待；直接同步移除。
        if (stepOverlayView != null) {
            safeRemove(stepOverlayView);
            stepOverlayView = null;
            stepOverlayLp = null;
        }
        destroyDelayOverlay();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private void renderDelayProgress(boolean visible, long elapsedMs, long durationMs) {
        renderDelayProgress(visible, elapsedMs, durationMs, null);
    }

    private void startDelayClock() {
        long now = SystemClock.uptimeMillis();
        activeDelayStartMs = now;
        activeDelayShownAtMs = now;
        activeDelayElapsedBeforePauseMs = 0L;
        activeDelayPaused = false;
    }

    private long getActiveDelayElapsedMs() {
        long elapsed = activeDelayElapsedBeforePauseMs;
        if (!activeDelayPaused && activeDelayStartMs > 0L) {
            elapsed += Math.max(0L, SystemClock.uptimeMillis() - activeDelayStartMs);
        }
        return Math.min(activeDelayDurationMs, Math.max(0L, elapsed));
    }

    private void renderDelayProgress(boolean visible,
                                     long elapsedMs,
                                     long durationMs,
                                     @Nullable Runnable onFirstFrameReady) {
        if (!visible || durationMs <= 0L) {
            if (delayOverlayView != null) delayOverlayView.stop();
            if (onFirstFrameReady != null) onFirstFrameReady.run();
            return;
        }
        ensureDelayOverlay();
        if (delayOverlayView == null) {
            if (onFirstFrameReady != null) onFirstFrameReady.run();
            return;
        }
        long safeElapsed = Math.max(0L, Math.min(elapsedMs, durationMs));
        delayOverlayView.start(durationMs, safeElapsed, onFirstFrameReady);
    }

    private void ensureDelayOverlay() {
        if (delayOverlayView != null && delayOverlayView.getParent() != null) return;
        try {
            if (delayOverlayView == null) {
                delayOverlayView = new DelayCountdownOverlayView(host.getContext());
            }
            delayOverlayLp = buildOverlayLp(
                    host.dp(200),
                    host.dp(25),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            delayOverlayLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            delayOverlayLp.x = 0;
            delayOverlayLp.y = host.dp(100);
            host.getWindowManager().addView(delayOverlayView, delayOverlayLp);
        } catch (Exception e) {
            Log.e(TAG, "addView delayOverlay failed", e);
            delayOverlayLp = null;
            if (delayOverlayView != null && delayOverlayView.getParent() == null) {
                delayOverlayView = null;
            }
        }
    }

    private void destroyDelayOverlay() {
        if (delayOverlayView != null) safeRemove(delayOverlayView);
        delayOverlayView = null;
        delayOverlayLp = null;
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

}
