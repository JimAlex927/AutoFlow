package com.auto.master.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.auto.master.capture.CaptureScaleHelper;
import com.auto.master.capture.ScreenCaptureManager;

public final class SystemRuntimeConfig {
    private static final String PREFS_NAME = "AutoFlowSystemRuntimeConfig";
    private static final String KEY_CAPTURE_SCALE = "capture_scale";
    private static final String KEY_IDLE_PAUSE_THRESHOLD_MS = "idle_pause_threshold_ms";
    private static final String KEY_GESTURE_RECORD_IDLE_FINISH_MS = "gesture_record_idle_finish_ms";
    private static final String KEY_TEMPLATE_FAST_MS = "template_fast_ms";
    private static final String KEY_TEMPLATE_MEDIUM_MS = "template_medium_ms";
    private static final String KEY_TEMPLATE_SLOW_MS = "template_slow_ms";
    private static final String KEY_MATCH_MAP_FAST_MS = "match_map_fast_ms";
    private static final String KEY_MATCH_MAP_MEDIUM_MS = "match_map_medium_ms";
    private static final String KEY_MATCH_MAP_SLOW_MS = "match_map_slow_ms";
    private static final String KEY_COLOR_FAST_MS = "color_fast_ms";
    private static final String KEY_COLOR_MEDIUM_MS = "color_medium_ms";
    private static final String KEY_COLOR_SLOW_MS = "color_slow_ms";
    private static final String KEY_GESTURE_STEP_REPLAY = "gesture_step_replay";
    private static final String KEY_GESTURE_STEP_INTERVAL_MS = "gesture_step_interval_ms";
    private static final String KEY_RUNTIME_LOG_ENABLED = "runtime_log_enabled";

    public static final float DEFAULT_CAPTURE_SCALE = 1.0f;
    public static final long DEFAULT_IDLE_PAUSE_THRESHOLD_MS = 0L;
    public static final long DEFAULT_GESTURE_RECORD_IDLE_FINISH_MS = 2200L;

    public float captureScale = DEFAULT_CAPTURE_SCALE;
    public long idlePauseThresholdMs = DEFAULT_IDLE_PAUSE_THRESHOLD_MS;
    public long gestureRecordIdleFinishMs = DEFAULT_GESTURE_RECORD_IDLE_FINISH_MS;
    public long templateFastMs = 220L;
    public long templateMediumMs = 380L;
    public long templateSlowMs = 560L;
    public long matchMapFastMs = 220L;
    public long matchMapMediumMs = 380L;
    public long matchMapSlowMs = 560L;
    public long colorFastMs = 220L;
    public long colorMediumMs = 380L;
    public long colorSlowMs = 560L;
    public boolean gestureStepReplay = true;
    public long gestureStepIntervalMs = 500L;
    public boolean runtimeLogEnabled = true;

    public static SystemRuntimeConfig load(Context context) {
        SystemRuntimeConfig cfg = new SystemRuntimeConfig();
        if (context == null) {
            return cfg;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        cfg.captureScale = clampFloat(prefs.getFloat(KEY_CAPTURE_SCALE,
                CaptureScaleHelper.loadScale(context)), 0.25f, 1.0f);
        cfg.idlePauseThresholdMs = clampIdlePauseThreshold(prefs.getLong(KEY_IDLE_PAUSE_THRESHOLD_MS,
                DEFAULT_IDLE_PAUSE_THRESHOLD_MS));
        cfg.gestureRecordIdleFinishMs = clampGestureRecordIdleFinish(prefs.getLong(
                KEY_GESTURE_RECORD_IDLE_FINISH_MS,
                DEFAULT_GESTURE_RECORD_IDLE_FINISH_MS));
        cfg.templateFastMs = clampPolling(prefs.getLong(KEY_TEMPLATE_FAST_MS, cfg.templateFastMs));
        cfg.templateMediumMs = clampPolling(prefs.getLong(KEY_TEMPLATE_MEDIUM_MS, cfg.templateMediumMs));
        cfg.templateSlowMs = clampPolling(prefs.getLong(KEY_TEMPLATE_SLOW_MS, cfg.templateSlowMs));
        cfg.matchMapFastMs = clampPolling(prefs.getLong(KEY_MATCH_MAP_FAST_MS, cfg.matchMapFastMs));
        cfg.matchMapMediumMs = clampPolling(prefs.getLong(KEY_MATCH_MAP_MEDIUM_MS, cfg.matchMapMediumMs));
        cfg.matchMapSlowMs = clampPolling(prefs.getLong(KEY_MATCH_MAP_SLOW_MS, cfg.matchMapSlowMs));
        cfg.colorFastMs = clampPolling(prefs.getLong(KEY_COLOR_FAST_MS, cfg.colorFastMs));
        cfg.colorMediumMs = clampPolling(prefs.getLong(KEY_COLOR_MEDIUM_MS, cfg.colorMediumMs));
        cfg.colorSlowMs = clampPolling(prefs.getLong(KEY_COLOR_SLOW_MS, cfg.colorSlowMs));
        cfg.gestureStepReplay = prefs.getBoolean(KEY_GESTURE_STEP_REPLAY, true);
        cfg.gestureStepIntervalMs = clampLong(prefs.getLong(KEY_GESTURE_STEP_INTERVAL_MS, 500L), 0L, 30000L);
        cfg.runtimeLogEnabled = prefs.getBoolean(KEY_RUNTIME_LOG_ENABLED, true);
        return cfg;
    }

    public void save(Context context) {
        if (context == null) {
            return;
        }
        normalize();
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_CAPTURE_SCALE, captureScale)
                .putLong(KEY_IDLE_PAUSE_THRESHOLD_MS, idlePauseThresholdMs)
                .putLong(KEY_GESTURE_RECORD_IDLE_FINISH_MS, gestureRecordIdleFinishMs)
                .putLong(KEY_TEMPLATE_FAST_MS, templateFastMs)
                .putLong(KEY_TEMPLATE_MEDIUM_MS, templateMediumMs)
                .putLong(KEY_TEMPLATE_SLOW_MS, templateSlowMs)
                .putLong(KEY_MATCH_MAP_FAST_MS, matchMapFastMs)
                .putLong(KEY_MATCH_MAP_MEDIUM_MS, matchMapMediumMs)
                .putLong(KEY_MATCH_MAP_SLOW_MS, matchMapSlowMs)
                .putLong(KEY_COLOR_FAST_MS, colorFastMs)
                .putLong(KEY_COLOR_MEDIUM_MS, colorMediumMs)
                .putLong(KEY_COLOR_SLOW_MS, colorSlowMs)
                .putBoolean(KEY_GESTURE_STEP_REPLAY, gestureStepReplay)
                .putLong(KEY_GESTURE_STEP_INTERVAL_MS, gestureStepIntervalMs)
                .putBoolean(KEY_RUNTIME_LOG_ENABLED, runtimeLogEnabled)
                .apply();
        CaptureScaleHelper.saveScale(context, captureScale);
    }

    public void applyToRuntime() {
        normalize();
        ScreenCaptureManager.CAPTURE_SCALE = captureScale;
        ScreenCaptureManager.IDLE_PAUSE_THRESHOLD_MS = idlePauseThresholdMs;
        AdaptivePollingController.DEFAULT_TEMPLATE_FAST_INTERVAL_MS = templateFastMs;
        AdaptivePollingController.DEFAULT_TEMPLATE_MEDIUM_INTERVAL_MS = templateMediumMs;
        AdaptivePollingController.DEFAULT_TEMPLATE_SLOW_INTERVAL_MS = templateSlowMs;
        AdaptivePollingController.DEFAULT_MATCH_MAP_FAST_INTERVAL_MS = matchMapFastMs;
        AdaptivePollingController.DEFAULT_MATCH_MAP_MEDIUM_INTERVAL_MS = matchMapMediumMs;
        AdaptivePollingController.DEFAULT_MATCH_MAP_SLOW_INTERVAL_MS = matchMapSlowMs;
        AdaptivePollingController.DEFAULT_COLOR_FAST_INTERVAL_MS = colorFastMs;
        AdaptivePollingController.DEFAULT_COLOR_MEDIUM_INTERVAL_MS = colorMediumMs;
        AdaptivePollingController.DEFAULT_COLOR_SLOW_INTERVAL_MS = colorSlowMs;
    }

    public void normalize() {
        captureScale = clampFloat(captureScale, 0.25f, 1.0f);
        idlePauseThresholdMs = clampIdlePauseThreshold(idlePauseThresholdMs);
        gestureRecordIdleFinishMs = clampGestureRecordIdleFinish(gestureRecordIdleFinishMs);
        templateFastMs = clampPolling(templateFastMs);
        templateMediumMs = clampPolling(templateMediumMs);
        templateSlowMs = clampPolling(templateSlowMs);
        matchMapFastMs = clampPolling(matchMapFastMs);
        matchMapMediumMs = clampPolling(matchMapMediumMs);
        matchMapSlowMs = clampPolling(matchMapSlowMs);
        colorFastMs = clampPolling(colorFastMs);
        colorMediumMs = clampPolling(colorMediumMs);
        colorSlowMs = clampPolling(colorSlowMs);
    }

    private static long clampPolling(long value) {
        return clampLong(value, 10L, 5000L);
    }

    private static long clampGestureRecordIdleFinish(long value) {
        return clampLong(value, 500L, 120000L);
    }

    private static long clampIdlePauseThreshold(long value) {
        if (value <= 0L) {
            return 0L;
        }
        return clampLong(value, 500L, 120000L);
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
