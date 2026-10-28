package com.auto.master.floatwin;

import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;

import com.auto.master.utils.RuntimeDisplayConfig;

import java.util.Locale;

/**
 * 倒计时悬浮条：基于 SystemClock.uptimeMillis() 计时，与 Thread.sleep 同源，
 * 不受 ANIMATOR_DURATION_SCALE 影响，始终精准匹配实际延迟时长。
 *
 * 显示：条形从满→空，文字显示剩余时间（3.0s → 0.0s）。
 */
public class DelayCountdownOverlayView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect  = new RectF();
    private final RectF fillRect   = new RectF();

    private final float cornerRadius;
    private long durationMs = 0L;
    private long elapsedBeforeResumeMs = 0L;
    private long resumedAtUptimeMs = 0L;
    private boolean running = false;
    private boolean paused = false;
    private float textBaseline;
    private long lastDisplayedTenth = Long.MIN_VALUE;
    private String displayedTime = "0.0s";

    private final TimeAnimator frameAnimator = new TimeAnimator();

    public DelayCountdownOverlayView(Context context) {
        super(context);

        trackPaint.setColor(0x33FFFFFF);
        trackPaint.setStyle(Paint.Style.FILL);

        fillPaint.setColor(RuntimeDisplayConfig.COUNTDOWN_FILL_COLOR);
        fillPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(sp(11.5f));
        cornerRadius = dp(5f);
        frameAnimator.setTimeListener((animation, totalTime, deltaTime) -> {
            if (!running || paused) {
                return;
            }
            if (currentElapsedMs() >= durationMs) {
                running = false;
                animation.cancel();
                setVisibility(GONE);
                return;
            }
            invalidate();
        });

        setAlpha(0.9f);
        setVisibility(GONE);
    }

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * 启动倒计时。
     *
     * @param durationMs     总时长（ms），与 Thread.sleep 传入的值相同
     * @param startElapsedMs 已经过的时间（通常为 0，syncDelayState 时传实际值）
     * @param onReady        启动后同步回调（用于释放 CountDownLatch）
     */
    public void start(long durationMs, long startElapsedMs, Runnable onReady) {
        stopInternal();
        this.durationMs = Math.max(1L, durationMs);
        this.elapsedBeforeResumeMs = Math.max(0L, Math.min(startElapsedMs, this.durationMs));
        this.resumedAtUptimeMs = SystemClock.uptimeMillis();
        this.running = true;
        this.paused = false;
        this.lastDisplayedTenth = Long.MIN_VALUE;
        setVisibility(VISIBLE);
        invalidate();
        frameAnimator.start();

        // 立即回调，不等下一帧 —— 保证 CountDownLatch 在 sleep 开始前释放
        if (onReady != null) onReady.run();
    }

    public void start(long durationMs, long startElapsedMs) {
        start(durationMs, startElapsedMs, null);
    }

    public void stop() {
        stopInternal();
        setVisibility(GONE);
    }

    public void pause() {
        if (!running || paused) {
            return;
        }
        elapsedBeforeResumeMs = currentElapsedMs();
        paused = true;
        frameAnimator.pause();
        invalidate();
    }

    public void resume() {
        if (!running || !paused) {
            return;
        }
        resumedAtUptimeMs = SystemClock.uptimeMillis();
        paused = false;
        frameAnimator.resume();
    }

    // ── draw ──────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        if (!running || durationMs <= 0L || getWidth() <= 0) return;

        long elapsed = currentElapsedMs();
        long remaining = Math.max(0L, durationMs - elapsed);
        float remainingFraction = remaining / (float) durationMs;

        fillPaint.setColor(RuntimeDisplayConfig.COUNTDOWN_FILL_COLOR);

        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);

        // 倒计时条：满→空
        fillRect.set(0f, 0f, getWidth() * remainingFraction, getHeight());
        canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, fillPaint);

        // 剩余时间文字
        long displayedTenth = (remaining + 99L) / 100L;
        if (displayedTenth != lastDisplayedTenth) {
            lastDisplayedTenth = displayedTenth;
            displayedTime = fmtRemaining(remaining);
        }
        canvas.drawText(displayedTime, getWidth() / 2f, textBaseline, textPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        trackRect.set(0f, 0f, w, h);
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        textBaseline = trackRect.centerY()
                - (fontMetrics.bottom - fontMetrics.top) / 2f
                - fontMetrics.top;
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private void stopInternal() {
        running = false;
        paused = false;
        durationMs = 0L;
        elapsedBeforeResumeMs = 0L;
        resumedAtUptimeMs = 0L;
        if (frameAnimator.isStarted()) {
            frameAnimator.cancel();
        }
    }

    private long currentElapsedMs() {
        long elapsed = elapsedBeforeResumeMs;
        if (!paused && resumedAtUptimeMs > 0L) {
            elapsed += Math.max(0L, SystemClock.uptimeMillis() - resumedAtUptimeMs);
        }
        return Math.min(durationMs, Math.max(0L, elapsed));
    }

    @Override
    protected void onDetachedFromWindow() {
        stopInternal();
        super.onDetachedFromWindow();
    }

    // ── formatting ────────────────────────────────────────────────────────────

    private String fmtRemaining(long ms) {
        if (ms <= 0L) return "0.0s";
        int minutes = (int) ((ms % 3_600_000L) / 60_000L);
        int seconds = (int) ((ms % 60_000L) / 1_000L);
        int tenths  = (int) ((ms % 1_000L) / 100L);
        if (minutes > 0) {
            return String.format(Locale.getDefault(), "%d:%02d.%ds", minutes, seconds, tenths);
        }
        return String.format(Locale.getDefault(), "%d.%ds", seconds, tenths);
    }

    // ── util ──────────────────────────────────────────────────────────────────

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }
}
