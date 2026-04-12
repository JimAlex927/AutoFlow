package com.auto.master.floatwin;

public class OperationItem {
    public String name;
    public String type;
    public String id;
    public int index;
    public long delayDurationMs;
    public boolean delayShowCountdown;

    public OperationItem(String name, String id, String type, int index) {
        this(name, id, type, index, 0L, true);
    }

    public OperationItem(String name, String id, String type, int index, long delayDurationMs) {
        this(name, id, type, index, delayDurationMs, true);
    }

    public OperationItem(String name, String id, String type, int index, long delayDurationMs, boolean delayShowCountdown) {
        this.name = name;
        this.id = id;
        this.type = type;
        this.index = index;
        this.delayDurationMs = Math.max(0L, delayDurationMs);
        this.delayShowCountdown = delayShowCountdown;
    }
}
