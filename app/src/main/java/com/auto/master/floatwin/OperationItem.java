package com.auto.master.floatwin;

public class OperationItem {
    public String name;
    public String type;
    public String id;
    public int index;
    public long delayDurationMs;
    public boolean delayShowCountdown;

    public OperationItem(String name, String id, String type, int index) {
        this.name = name;
        this.id = id;
        this.type = type;
        this.index = index;
    }

    public OperationItem(String name, String id, String type, int index, long delayDurationMs, boolean delayShowCountdown) {
        this.name = name;
        this.id = id;
        this.type = type;
        this.index = index;
        this.delayDurationMs = delayDurationMs;
        this.delayShowCountdown = delayShowCountdown;
    }
}
