package com.auto.master.auto;

import com.auto.master.Task.Operation.OperationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public final class ScriptSession {
    public enum State {
        IDLE,
        QUEUED,
        RUNNING,
        PAUSED,
        STOPPING,
        STOPPED,
        COMPLETED,
        FAILED
    }

    private static final int MAX_LOG_LINES = 1000;

    private final String sessionId;
    private final String sessionName;
    private volatile ScriptExecuteContext executeContext;
    private volatile OperationContext operationContext;
    private volatile ScriptRunner.ScriptExecutionListener listener;
    private final List<String> logs = new ArrayList<>();
    private final Object logLock = new Object();
    private volatile long startTimeMs;

    private volatile Future<?> future;
    private volatile Thread executeThread;
    private volatile State state = State.IDLE;
    private volatile long lastCompleteListenerNotifyMs = 0L;
    private volatile String currentOperationId = "";
    private volatile String currentOperationName = "";
    private volatile String currentProjectName = "";
    private volatile String currentTaskName = "";

    ScriptSession(String sessionId, String sessionName) {
        this.sessionId = sessionId;
        this.sessionName = sessionName == null || sessionName.trim().isEmpty()
                ? sessionId.substring(0, Math.min(8, sessionId.length()))
                : sessionName.trim();
        this.startTimeMs = System.currentTimeMillis();
    }

    boolean bind(ScriptExecuteContext executeContext,
                 ScriptRunner.ScriptExecutionListener listener) {
        if (state != State.IDLE) {
            return false;
        }
        this.executeContext = executeContext;
        this.operationContext = executeContext == null ? null : executeContext.sharedContext;
        this.listener = listener;
        this.startTimeMs = System.currentTimeMillis();
        this.lastCompleteListenerNotifyMs = 0L;
        this.currentOperationId = "";
        this.currentOperationName = "";
        clearLogs();
        state = State.QUEUED;
        return true;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSessionName() {
        return sessionName;
    }

    public ScriptExecuteContext getExecuteContext() {
        return executeContext;
    }

    public OperationContext getOperationContext() {
        return operationContext;
    }

    public ScriptRunner.ScriptExecutionListener getListener() {
        return listener;
    }

    public Future<?> getFuture() {
        return future;
    }

    void setFuture(Future<?> future) {
        this.future = future;
    }

    public Thread getExecuteThread() {
        return executeThread;
    }

    void setExecuteThread(Thread executeThread) {
        this.executeThread = executeThread;
    }

    public State getState() {
        return state;
    }

    void setState(State state) {
        this.state = state;
    }

    long getLastCompleteListenerNotifyMs() {
        return lastCompleteListenerNotifyMs;
    }

    void setLastCompleteListenerNotifyMs(long lastCompleteListenerNotifyMs) {
        this.lastCompleteListenerNotifyMs = lastCompleteListenerNotifyMs;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public String getCurrentOperationId() {
        return currentOperationId;
    }

    public String getCurrentOperationName() {
        return currentOperationName;
    }

    void setCurrentOperation(String operationId, String operationName) {
        this.currentOperationId = operationId == null ? "" : operationId;
        this.currentOperationName = operationName == null ? "" : operationName;
    }

    public String getCurrentProjectName() {
        return currentProjectName;
    }

    void setCurrentProjectName(String currentProjectName) {
        this.currentProjectName = currentProjectName == null ? "" : currentProjectName;
    }

    public String getCurrentTaskName() {
        return currentTaskName;
    }

    void setCurrentTaskName(String currentTaskName) {
        this.currentTaskName = currentTaskName == null ? "" : currentTaskName;
    }

    void appendLog(String line) {
        if (line == null) {
            return;
        }
        synchronized (logLock) {
            if (logs.size() >= MAX_LOG_LINES) {
                logs.remove(0);
            }
            logs.add(line);
        }
    }

    void clearLogs() {
        synchronized (logLock) {
            logs.clear();
        }
    }

    void resetToIdle() {
        executeContext = null;
        operationContext = null;
        listener = null;
        future = null;
        executeThread = null;
        lastCompleteListenerNotifyMs = 0L;
        currentOperationId = "";
        currentOperationName = "";
        currentProjectName = "";
        currentTaskName = "";
        startTimeMs = System.currentTimeMillis();
        // Keep the just-finished run available to the runtime log panel.  The
        // next bind() already clears the previous run before execution starts,
        // so clearing here only creates a window in which completion observers
        // see an empty log snapshot.
        state = State.IDLE;
    }

    public List<String> getLogsSnapshot() {
        synchronized (logLock) {
            return new ArrayList<>(logs);
        }
    }

    public ScriptSessionSnapshot snapshot() {
        return new ScriptSessionSnapshot(
                sessionId,
                sessionName,
                state,
                currentProjectName,
                currentTaskName,
                currentOperationId,
                currentOperationName,
                startTimeMs,
                getLogsSnapshot());
    }
}
