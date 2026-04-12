package com.auto.master.floatwin;

import org.json.JSONObject;

final class OperationClipboardEntry {
    final JSONObject operationJson;
    final String name;
    final String sourceTaskPath;
    final long createdAt;

    OperationClipboardEntry(JSONObject operationJson, String name, String sourceTaskPath, long createdAt) {
        this.operationJson = operationJson;
        this.name = name;
        this.sourceTaskPath = sourceTaskPath;
        this.createdAt = createdAt;
    }
}
