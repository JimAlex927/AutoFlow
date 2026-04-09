package com.auto.master.scheduler;

/**
 * Represents a persistent trigger that fires when an app is launched
 * or when a notification matching a keyword arrives.
 *
 * This is separate from ScheduledTask (time-based) to keep concerns clean.
 */
public class AppNotificationTrigger {

    public static final int TYPE_APP_LAUNCH = 1;
    public static final int TYPE_NOTIFICATION = 2;

    /** Unique trigger ID */
    public String id;

    /** TYPE_APP_LAUNCH or TYPE_NOTIFICATION */
    public int triggerType;

    /** Which project to run */
    public String projectName;

    /** Which task to run */
    public String taskId;

    /** Optional: which operation to start from (null = startOperationId from task) */
    public String operationId;

    /** Whether this trigger is active */
    public boolean enabled = true;

    // --- APP_LAUNCH fields ---

    /** App package name to watch (e.g. "com.tencent.mm") */
    public String watchPackage;

    /** Human-readable app label for display */
    public String watchAppLabel;

    // --- NOTIFICATION fields ---

    /** Package whose notifications to watch (null = any package) */
    public String notificationPackage;

    /** Keyword that must appear in the notification title or text */
    public String notificationKeyword;

    public AppNotificationTrigger() {
    }
}
