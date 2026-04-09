package com.auto.master.scheduler;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Listens for incoming notifications and fires matching automation tasks.
 *
 * Users must grant notification access via:
 *   Settings → Notifications → Notification access → AtommMaster
 *
 * Matching logic:
 *   - If trigger.notificationPackage is set, only match notifications from that package
 *   - If trigger.notificationKeyword is set, notification title or text must contain it
 *   - Both conditions must be satisfied when both are set
 */
public class NotificationTriggerService extends NotificationListenerService {

    private static final String TAG = "NotifTriggerService";
    private static volatile NotificationTriggerService sInstance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Log.d(TAG, "NotificationTriggerService created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        executor.shutdown();
    }

    public static boolean isConnected() {
        return sInstance != null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        final String pkg = sbn.getPackageName();
        final String title = getNotificationText(sbn, Notification.EXTRA_TITLE);
        final String text = getNotificationText(sbn, Notification.EXTRA_TEXT);

        executor.execute(() -> checkTriggers(getApplicationContext(), pkg, title, text));
    }

    private void checkTriggers(Context context, String pkg, String title, String text) {
        List<AppNotificationTrigger> triggers =
                TriggerStore.getByType(context, AppNotificationTrigger.TYPE_NOTIFICATION);

        for (AppNotificationTrigger trigger : triggers) {
            if (!trigger.enabled) continue;

            // Package filter
            if (!TextUtils.isEmpty(trigger.notificationPackage)
                    && !trigger.notificationPackage.equals(pkg)) {
                continue;
            }

            // Keyword filter
            if (!TextUtils.isEmpty(trigger.notificationKeyword)) {
                String kw = trigger.notificationKeyword.toLowerCase();
                String t = (title == null ? "" : title).toLowerCase();
                String b = (text == null ? "" : text).toLowerCase();
                if (!t.contains(kw) && !b.contains(kw)) {
                    continue;
                }
            }

            Log.d(TAG, "notification trigger matched: id=" + trigger.id
                    + " pkg=" + pkg + " keyword=" + trigger.notificationKeyword);
            fireTrigger(context, trigger);
        }
    }

    private void fireTrigger(Context context, AppNotificationTrigger trigger) {
        // Reuse ScheduledTask execution logic via a temporary ScheduledTask wrapper
        ScheduledTask spec = new ScheduledTask();
        spec.id = trigger.id;
        spec.projectName = trigger.projectName;
        spec.taskId = trigger.taskId;
        spec.operationId = trigger.operationId;
        spec.enabled = true;
        TaskScheduleExecutor.execute(context, spec);
    }

    private String getNotificationText(StatusBarNotification sbn, String key) {
        try {
            Bundle extras = sbn.getNotification().extras;
            CharSequence cs = extras == null ? null : extras.getCharSequence(key);
            return cs == null ? null : cs.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
