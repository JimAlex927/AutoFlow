package com.auto.master.auto;

import java.util.Collections;
import java.util.List;

public final class ScriptSessionSnapshot {
    public final String sessionId;
    public final String sessionName;
    public final ScriptSession.State state;
    public final String projectName;
    public final String taskName;
    public final String operationId;
    public final String operationName;
    public final long startTimeMs;
    public final List<String> logs;

    ScriptSessionSnapshot(String sessionId,
                          String sessionName,
                          ScriptSession.State state,
                          String projectName,
                          String taskName,
                          String operationId,
                          String operationName,
                          long startTimeMs,
                          List<String> logs) {
        this.sessionId = sessionId;
        this.sessionName = sessionName;
        this.state = state;
        this.projectName = projectName;
        this.taskName = taskName;
        this.operationId = operationId;
        this.operationName = operationName;
        this.startTimeMs = startTimeMs;
        this.logs = logs == null ? Collections.emptyList() : Collections.unmodifiableList(logs);
    }
}
