package com.auto.master.scheduler;

public class ScheduledTask {
    public String id;
    public String projectName;
    public String taskId;
    public String operationId;
    public long triggerAtMs;
    public boolean enabled = true;

    public ScheduledTask() {
    }
}
