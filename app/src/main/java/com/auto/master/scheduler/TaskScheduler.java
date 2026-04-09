package com.auto.master.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.UUID;

public final class TaskScheduler {
    public static final String ACTION_TRIGGER = "com.auto.master.scheduler.ACTION_TRIGGER";
    public static final String EXTRA_SCHEDULE_ID = "extra_schedule_id";
    private static final String TAG = "TaskScheduler";

    private TaskScheduler() {
    }

    public static String schedule(Context context, ScheduledTask task) {
        if (context == null || task == null) {
            return null;
        }
        if (TextUtils.isEmpty(task.projectName) || TextUtils.isEmpty(task.taskId)) {
            return null;
        }
        if (TextUtils.isEmpty(task.id)) {
            task.id = UUID.randomUUID().toString();
        }
        task.enabled = true;
        task.triggerAtMs = Math.max(task.triggerAtMs, System.currentTimeMillis() + 1000L);
        TaskSchedulerStore.upsert(context, task);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return null;
        }

        PendingIntent pi = buildPendingIntent(context, task.id, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(pi);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.triggerAtMs, pi);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, task.triggerAtMs, pi);
        }
        Log.d(TAG, "scheduled id=" + task.id + " at=" + task.triggerAtMs);
        return task.id;
    }

    public static void cancel(Context context, String scheduleId) {
        if (context == null || TextUtils.isEmpty(scheduleId)) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            PendingIntent pi = buildPendingIntent(context, scheduleId, PendingIntent.FLAG_NO_CREATE);
            if (pi != null) {
                alarmManager.cancel(pi);
                pi.cancel();
            }
        }
        TaskSchedulerStore.remove(context, scheduleId);
    }

    public static void cancelAll(Context context) {
        if (context == null) {
            return;
        }
        List<ScheduledTask> all = TaskSchedulerStore.getAll(context);
        for (ScheduledTask item : all) {
            if (item != null && !TextUtils.isEmpty(item.id)) {
                cancel(context, item.id);
            }
        }
        TaskSchedulerStore.clear(context);
    }

    public static void restoreAll(Context context) {
        if (context == null) {
            return;
        }
        List<ScheduledTask> all = TaskSchedulerStore.getAll(context);
        long now = System.currentTimeMillis();
        for (ScheduledTask item : all) {
            if (item == null || !item.enabled || TextUtils.isEmpty(item.id)) {
                continue;
            }
            if (item.triggerAtMs < now + 1000L) {
                item.triggerAtMs = now + 1500L;
            }
            schedule(context, item);
        }
    }

    static PendingIntent buildPendingIntent(Context context, String scheduleId, int flags) {
        Intent intent = new Intent(context, TaskScheduleReceiver.class);
        intent.setAction(ACTION_TRIGGER);
        intent.putExtra(EXTRA_SCHEDULE_ID, scheduleId);
        int requestCode = scheduleId.hashCode();
        int allFlags = flags | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode, intent, allFlags);
    }
}
