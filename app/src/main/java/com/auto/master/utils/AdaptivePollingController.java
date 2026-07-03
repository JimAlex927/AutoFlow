package com.auto.master.utils;

import android.graphics.Rect;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.auto.master.auto.ScriptRunner;
import com.auto.master.capture.ScreenCapture;
import com.auto.master.Task.Operation.MetaOperation;

import org.opencv.core.Mat;

import java.util.Map;

/**
 * 自适应轮询控制器：
 * - 前期保持较快响应
 * - 连续未命中后自动降频，降低 CPU / 截图链压力
 * - 默认不复用旧帧，优先保证截图新鲜度
 */
public final class AdaptivePollingController {

    public enum Profile {
        TEMPLATE_MATCH,
        MATCH_MAP,
        COLOR_CHECK
    }

    public static volatile long DEFAULT_TEMPLATE_FAST_INTERVAL_MS = 220L;
    public static volatile long DEFAULT_TEMPLATE_MEDIUM_INTERVAL_MS = 380L;
    public static volatile long DEFAULT_TEMPLATE_SLOW_INTERVAL_MS = 560L;
    public static volatile long DEFAULT_MATCH_MAP_FAST_INTERVAL_MS = 220L;
    public static volatile long DEFAULT_MATCH_MAP_MEDIUM_INTERVAL_MS = 380L;
    public static volatile long DEFAULT_MATCH_MAP_SLOW_INTERVAL_MS = 560L;
    public static volatile long DEFAULT_COLOR_FAST_INTERVAL_MS = 220L;
    public static volatile long DEFAULT_COLOR_MEDIUM_INTERVAL_MS = 380L;
    public static volatile long DEFAULT_COLOR_SLOW_INTERVAL_MS = 560L;

    private final long fastIntervalMs;
    private final long mediumIntervalMs;
    private final long slowIntervalMs;
    private final long warmupWindowMs;
    private final long slowdownWindowMs;
    private final int mediumMissThreshold;
    private final int slowMissThreshold;

    private final long createdAtMs;
    private int consecutiveMisses = 0;
    private int lastObservedFrameSeq = -1;
    private boolean lastAcquireReturnedFreshFrame = false;
    private boolean lastAcquiredFrameOwnedByCaller = false;

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
                DEFAULT_TEMPLATE_FAST_INTERVAL_MS, DEFAULT_TEMPLATE_MEDIUM_INTERVAL_MS, DEFAULT_TEMPLATE_SLOW_INTERVAL_MS,
                500L, 1800L,
                3, 8
        );
    }

    public static AdaptivePollingController forTemplateMatch(@Nullable Map<String, Object> inputMap) {
        return fromInputMap(inputMap,
                DEFAULT_TEMPLATE_FAST_INTERVAL_MS,
                DEFAULT_TEMPLATE_MEDIUM_INTERVAL_MS,
                DEFAULT_TEMPLATE_SLOW_INTERVAL_MS,
                500L, 1800L, 3, 8);
    }

    public static AdaptivePollingController forMatchMap() {
        return new AdaptivePollingController(
                DEFAULT_MATCH_MAP_FAST_INTERVAL_MS, DEFAULT_MATCH_MAP_MEDIUM_INTERVAL_MS, DEFAULT_MATCH_MAP_SLOW_INTERVAL_MS,
                500L, 1800L,
                3, 8
        );
    }

    public static AdaptivePollingController forMatchMap(@Nullable Map<String, Object> inputMap) {
        return fromInputMap(inputMap,
                DEFAULT_MATCH_MAP_FAST_INTERVAL_MS,
                DEFAULT_MATCH_MAP_MEDIUM_INTERVAL_MS,
                DEFAULT_MATCH_MAP_SLOW_INTERVAL_MS,
                500L, 1800L, 3, 8);
    }

    public static AdaptivePollingController forColorCheck() {
        return new AdaptivePollingController(
                DEFAULT_COLOR_FAST_INTERVAL_MS, DEFAULT_COLOR_MEDIUM_INTERVAL_MS, DEFAULT_COLOR_SLOW_INTERVAL_MS,
                500L, 1800L,
                3, 7
        );
    }

    public static AdaptivePollingController forColorCheck(@Nullable Map<String, Object> inputMap) {
        return fromInputMap(inputMap,
                DEFAULT_COLOR_FAST_INTERVAL_MS,
                DEFAULT_COLOR_MEDIUM_INTERVAL_MS,
                DEFAULT_COLOR_SLOW_INTERVAL_MS,
                500L, 1800L, 3, 7);
    }

    public static long defaultFastIntervalMs(Profile profile) {
        switch (profile) {
            case MATCH_MAP:
                return DEFAULT_MATCH_MAP_FAST_INTERVAL_MS;
            case COLOR_CHECK:
                return DEFAULT_COLOR_FAST_INTERVAL_MS;
            case TEMPLATE_MATCH:
            default:
                return DEFAULT_TEMPLATE_FAST_INTERVAL_MS;
        }
    }

    public static long defaultMediumIntervalMs(Profile profile) {
        switch (profile) {
            case MATCH_MAP:
                return DEFAULT_MATCH_MAP_MEDIUM_INTERVAL_MS;
            case COLOR_CHECK:
                return DEFAULT_COLOR_MEDIUM_INTERVAL_MS;
            case TEMPLATE_MATCH:
            default:
                return DEFAULT_TEMPLATE_MEDIUM_INTERVAL_MS;
        }
    }

    public static long defaultSlowIntervalMs(Profile profile) {
        switch (profile) {
            case MATCH_MAP:
                return DEFAULT_MATCH_MAP_SLOW_INTERVAL_MS;
            case COLOR_CHECK:
                return DEFAULT_COLOR_SLOW_INTERVAL_MS;
            case TEMPLATE_MATCH:
            default:
                return DEFAULT_TEMPLATE_SLOW_INTERVAL_MS;
        }
    }

    private static AdaptivePollingController fromInputMap(@Nullable Map<String, Object> inputMap,
                                                          long defaultFastIntervalMs,
                                                          long defaultMediumIntervalMs,
                                                          long defaultSlowIntervalMs,
                                                          long warmupWindowMs,
                                                          long slowdownWindowMs,
                                                          int mediumMissThreshold,
                                                          int slowMissThreshold) {
        long fastIntervalMs = parseIntervalMs(inputMap, MetaOperation.POLL_FAST_INTERVAL_MS, defaultFastIntervalMs);
        long mediumIntervalMs = parseIntervalMs(inputMap, MetaOperation.POLL_MEDIUM_INTERVAL_MS, defaultMediumIntervalMs);
        long slowIntervalMs = parseIntervalMs(inputMap, MetaOperation.POLL_SLOW_INTERVAL_MS, defaultSlowIntervalMs);
        return new AdaptivePollingController(
                fastIntervalMs, mediumIntervalMs, slowIntervalMs,
                warmupWindowMs, slowdownWindowMs,
                mediumMissThreshold, slowMissThreshold
        );
    }

    private static long parseIntervalMs(@Nullable Map<String, Object> inputMap, String key, long fallback) {
        if (inputMap == null) {
            return fallback;
        }
        Object raw = inputMap.get(key);
        long value = fallback;
        if (raw instanceof Number) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof String) {
            try {
                value = Long.parseLong(((String) raw).trim());
            } catch (Exception ignored) {
                value = fallback;
            }
        }
        return Math.max(10L, Math.min(value, 5000L));
    }

    @Nullable
    public Mat acquireFrame() {
        return acquireFrame(shouldCloneFrameForCurrentExecution());
    }

    @Nullable
    public Mat acquireFrame(boolean clone) {
        Mat frame = ScreenCapture.getSingleBitMapWhileInContinous(clone);
        lastAcquiredFrameOwnedByCaller = clone && frame != null;
        lastAcquireReturnedFreshFrame = updateFreshState(frame);
        return frame;
    }

    @Nullable
    public Mat acquireFrame(@Nullable Rect roi) {
        return acquireFrame(roi, shouldCloneFrameForCurrentExecution());
    }

    @Nullable
    public Mat acquireFrame(@Nullable Rect roi, boolean clone) {
        if (roi == null || roi.isEmpty()) {
            return acquireFrame(clone);
        }
        Mat frame = ScreenCapture.getSingleBitMapRoiWhileInContinous(roi, clone);
        lastAcquiredFrameOwnedByCaller = clone && frame != null;
        lastAcquireReturnedFreshFrame = updateFreshState(frame);
        return frame;
    }

    public boolean hasFreshFrame() {
        return lastAcquireReturnedFreshFrame;
    }

    public void releaseFrameIfOwned(@Nullable Mat frame) {
        if (!lastAcquiredFrameOwnedByCaller || frame == null) {
            return;
        }
        try {
            frame.release();
        } catch (Throwable ignored) {
        } finally {
            lastAcquiredFrameOwnedByCaller = false;
        }
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
            ScriptRunner.sleepCurrentSessionAware(targetIntervalMs - elapsedMs);
        } else {
            ScriptRunner.waitIfCurrentSessionPaused();
        }
    }

    private boolean updateFreshState(@Nullable Mat frame) {
        if (frame == null || frame.empty()) {
            return false;
        }
        int frameSeq = ScreenCapture.getFrameSequence();
        if (frameSeq <= 0) {
            return false;
        }
        if (frameSeq == lastObservedFrameSeq) {
            return false;
        }
        lastObservedFrameSeq = frameSeq;
        return true;
    }

    private boolean shouldCloneFrameForCurrentExecution() {
        return ScriptRunner.shouldCloneCaptureFrameForCurrentSession();
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
