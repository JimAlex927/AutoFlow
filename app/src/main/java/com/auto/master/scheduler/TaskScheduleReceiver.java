package com.auto.master.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

public class TaskScheduleReceiver extends BroadcastReceiver {
    private static final String TAG = "TaskScheduleReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            TaskScheduler.restoreAll(context.getApplicationContext());
            Log.d(TAG, "restore schedules on action=" + action);
            return;
        }
        if (TaskScheduler.ACTION_TRIGGER.equals(action)) {
            String scheduleId = intent.getStringExtra(TaskScheduler.EXTRA_SCHEDULE_ID);
            if (TextUtils.isEmpty(scheduleId)) {
                return;
            }
            ScheduledTask task = TaskSchedulerStore.findById(context, scheduleId);
            if (task == null || !task.enabled) {
                TaskSchedulerStore.remove(context, scheduleId);
                return;
            }
            TaskScheduleExecutor.execute(context.getApplicationContext(), task);
            TaskSchedulerStore.remove(context, scheduleId);
        }
    }
}
