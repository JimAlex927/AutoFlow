package com.auto.master.floatwin;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.auto.master.auto.ScriptRunner;
import com.auto.master.scheduler.AppNotificationTrigger;
import com.auto.master.scheduler.ScheduledTask;
import com.auto.master.scheduler.TaskScheduleExecutor;
import com.auto.master.scheduler.TriggerStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * App 启动 / 通知触发器的轮询逻辑管理器。
 * 从 FloatWindowService（14k 行 God 类）中拆分出来，职责单一。
 *
 * <p>注意：{@link #TRIGGER_FEATURE_ENABLED} = false，触发器功能目前已停用，
 * 但代码已整理到这里以备日后开启。</p>
 *
 * <p>UI 相关的对话框（showTriggerManager、showAddTriggerDialog 等）仍留在
 * FloatWindowService，因为它们重度依赖 WindowManager/LayoutInflater 工具方法。
 * 这里只负责：线程生命周期、缓存刷新、触发检测。</p>
 */
public class AppLaunchTriggerManager {

    private static final String TAG = "AppLaunchTriggerMgr";

    /** 将此置为 true 可开启 App 启动触发器功能。 */
    static final boolean TRIGGER_FEATURE_ENABLED = false;

    private static final long POLL_INTERVAL_MS = 2000L;

    private final Context context;

    private HandlerThread pollThread;
    private Handler pollHandler;

    private final Object cacheLock = new Object();
    private List<AppNotificationTrigger> cachedTriggers = Collections.emptyList();
    private String lastForegroundPackage = "";

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (ScriptRunner.isCurrentScriptRunning()) {
                stopPolling();
                return;
            }
            checkTriggers();
            // 仍有激活触发器时继续轮询
            if (!getSnapshot().isEmpty()) {
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    public AppLaunchTriggerManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // ── 生命周期 ───────────────────────────────────────────────────────────

    /**
     * Service.onCreate() 中调用：初始化轮询线程并根据已保存的触发器决定是否启动轮询。
     */
    public void initIfNeeded() {
        if (!TRIGGER_FEATURE_ENABLED) return;
        ensureThread();
        refreshCache();
        if (!getSnapshot().isEmpty()) startPolling();
    }

    /**
     * Service.onDestroy() 中调用：停止轮询，退出后台线程。
     */
    public void destroy() {
        stopPolling();
        if (pollThread != null) {
            pollThread.quitSafely();
            pollThread = null;
            pollHandler = null;
        }
    }

    // ── 公开操作 ───────────────────────────────────────────────────────────

    /** 从持久化存储刷新激活触发器缓存。 */
    public void refreshCache() {
        if (!TRIGGER_FEATURE_ENABLED) {
            synchronized (cacheLock) { cachedTriggers = Collections.emptyList(); }
            return;
        }
        List<AppNotificationTrigger> active =
                TriggerStore.getByType(context, AppNotificationTrigger.TYPE_APP_LAUNCH);
        synchronized (cacheLock) {
            cachedTriggers = (active == null) ? Collections.emptyList() : new ArrayList<>(active);
        }
    }

    /** 返回当前缓存的触发器快照（线程安全）。 */
    public List<AppNotificationTrigger> getSnapshot() {
        synchronized (cacheLock) {
            return cachedTriggers.isEmpty()
                    ? Collections.emptyList()
                    : new ArrayList<>(cachedTriggers);
        }
    }

    /** 刷新缓存后，根据是否有激活触发器决定启动或停止轮询。 */
    public void refreshPollingState() {
        refreshCache();
        if (getSnapshot().isEmpty()) stopPolling(); else startPolling();
    }

    /** 启动后台轮询（若脚本正在运行则立即停止）。 */
    public void startPolling() {
        if (!TRIGGER_FEATURE_ENABLED || ScriptRunner.isCurrentScriptRunning()) {
            stopPolling();
            return;
        }
        ensureThread();
        refreshCache();
        if (getSnapshot().isEmpty()) { stopPolling(); return; }
        if (pollHandler == null) return;
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    /** 停止后台轮询（不退出线程）。 */
    public void stopPolling() {
        if (pollHandler != null) pollHandler.removeCallbacks(pollRunnable);
    }

    // ── 内部实现 ───────────────────────────────────────────────────────────

    private void ensureThread() {
        if (pollThread != null) return;
        pollThread = new HandlerThread("AutoFlow-AppLaunchPoll");
        pollThread.start();
        pollHandler = new Handler(pollThread.getLooper());
    }

    private void checkTriggers() {
        if (!TRIGGER_FEATURE_ENABLED || ScriptRunner.isCurrentScriptRunning()) return;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) return;
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager)
                            context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return;

            long now = System.currentTimeMillis();
            List<android.app.usage.UsageStats> stats =
                    usm.queryUsageStats(
                            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                            now - 5000, now);
            if (stats == null || stats.isEmpty()) return;

            String foreground = null;
            long maxTime = 0;
            for (android.app.usage.UsageStats us : stats) {
                if (us.getLastTimeUsed() > maxTime) {
                    maxTime = us.getLastTimeUsed();
                    foreground = us.getPackageName();
                }
            }
            if (foreground == null || foreground.equals(lastForegroundPackage)) return;
            lastForegroundPackage = foreground;

            for (AppNotificationTrigger t : getSnapshot()) {
                if (t.enabled && foreground.equals(t.watchPackage)) {
                    Log.d(TAG, "trigger fired: pkg=" + foreground + " -> " + t.projectName);
                    ScheduledTask spec = new ScheduledTask();
                    spec.id = t.id;
                    spec.projectName = t.projectName;
                    spec.taskId = t.taskId;
                    spec.operationId = t.operationId;
                    spec.enabled = true;
                    TaskScheduleExecutor.execute(context, spec);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "checkTriggers failed", e);
        }
    }
}
