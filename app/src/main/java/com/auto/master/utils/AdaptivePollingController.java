package com.auto.master.utils;

import android.graphics.Rect;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.auto.master.capture.ScreenCapture;

import org.opencv.core.Mat;

/**
 * 自适应轮询控制器：
 * - 前期保持较快响应
 * - 连续未命中后自动降频，降低 CPU / 截图链压力
 * - 默认不复用旧帧，优先保证截图新鲜度
 */
public final class AdaptivePollingController {

    private final long fastIntervalMs;
    private final long mediumIntervalMs;
    private final long slowIntervalMs;
    private final long warmupWindowMs;
    private final long slowdownWindowMs;
    private final int mediumMissThreshold;
    private final int slowMissThreshold;

    private final long createdAtMs;
    private int consecutiveMisses = 0;

    private AdaptivePollingController(long fastIntervalMs,
                                      long mediumIntervalMs,
                                      long slowIntervalMs,
                                      long warmupWindowMs,
                                      long slowdownWindowMs,
                                      int mediumMissThreshold,
                                      int slowMissThreshold) {
        this.fastIntervalMs = fastIntervalMs;
        this.mediumIntervalMs = mediumIntervalMs;
        this.slowIntervalMs = slowIntervalMs;
        this.warmupWindowMs = warmupWindowMs;
        this.slowdownWindowMs = slowdownWindowMs;
        this.mediumMissThreshold = mediumMissThreshold;
        this.slowMissThreshold = slowMissThreshold;
        this.createdAtMs = SystemClock.uptimeMillis();
    }

    public static AdaptivePollingController forTemplateMatch() {
        return new AdaptivePollingController(
                200L, 280L, 380L,
                1200L, 4000L,
                3, 8
        );
    }

    public static AdaptivePollingController forMatchMap() {
        return new AdaptivePollingController(
                200L, 260L, 340L,
                1600L, 3600L,
                3, 7
        );
    }

    public static AdaptivePollingController forColorCheck() {
        return new AdaptivePollingController(
                200L, 300L, 420L,
                1200L, 4200L,
                2, 5
        );
    }

    @Nullable
    public Mat acquireFrame() {
        return ScreenCapture.getSingleBitMapWhileInContinous(false);
    }

    @Nullable
    public Mat acquireFrame(@Nullable Rect roi) {
        if (roi == null || roi.isEmpty()) {
            return acquireFrame();
        }
        return ScreenCapture.getSingleBitMapRoiWhileInContinous(roi, false);
    }

    public void onMiss() {
        consecutiveMisses++;
    }

    public void onHit() {
        consecutiveMisses = 0;
    }

    public void sleepUntilNextIteration(long loopStartMs) {
        long targetIntervalMs = currentIntervalMs(SystemClock.uptimeMillis());
        long elapsedMs = SystemClock.uptimeMillis() - loopStartMs;
        if (elapsedMs < targetIntervalMs) {
            SystemClock.sleep(targetIntervalMs - elapsedMs);
        }
    }

    private long currentIntervalMs(long nowMs) {
        long elapsedMs = nowMs - createdAtMs;
        if (consecutiveMisses >= slowMissThreshold || elapsedMs >= slowdownWindowMs) {
            return slowIntervalMs;
        }
        if (consecutiveMisses >= mediumMissThreshold || elapsedMs >= warmupWindowMs) {
            return mediumIntervalMs;
        }
        return fastIntervalMs;
    }
}
