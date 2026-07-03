package com.auto.master.auto;

import com.auto.master.Task.Operation.OperationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ScriptSessionManager {
    private final ConcurrentMap<String, ScriptSession> sessions = new ConcurrentHashMap<>();
    private volatile String activeSessionId;

    public ScriptSession createSessionSlot() {
        return createSessionSlot(null);
    }

    public ScriptSession createSessionSlot(String sessionName) {
        String sessionId = UUID.randomUUID().toString();
        ScriptSession session = new ScriptSession(sessionId, sessionName);
        sessions.put(sessionId, session);
        activeSessionId = sessionId;
        return session;
    }

    public ScriptSession bindSession(String sessionId,
                                     ScriptExecuteContext executeContext,
                                     ScriptRunner.ScriptExecutionListener listener) {
        ScriptSession session = getSession(sessionId);
        if (session == null) {
            session = createSessionSlot(null);
        }
        if (!session.bind(executeContext, listener)) {
            return null;
        }
        if (executeContext != null) {
            executeContext.sessionId = session.getSessionId();
        }
        activeSessionId = session.getSessionId();
        wrapRuntimeLogSink(session);
        return session;
    }

    private void wrapRuntimeLogSink(ScriptSession session) {
        OperationContext ctx = session == null ? null : session.getOperationContext();
        if (ctx == null) {
            return;
        }
        OperationContext.RuntimeLogSink original = ctx.runtimeLogSink;
        ctx.runtimeLogSink = line -> {
            session.appendLog(line);
            if (original != null) {
                original.log(line);
            }
        };
    }

    public ScriptSession getSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        return sessions.get(sessionId);
    }

    public ScriptSession getActiveSession() {
        return getSession(activeSessionId);
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    public void setActiveSessionId(String sessionId) {
        if (sessionId != null && sessions.containsKey(sessionId)) {
            activeSessionId = sessionId;
        }
    }

    public List<ScriptSessionSnapshot> listSnapshots() {
        List<ScriptSessionSnapshot> result = new ArrayList<>();
        for (ScriptSession session : sessions.values()) {
            if (session != null) {
                result.add(session.snapshot());
            }
        }
        return result;
    }

    public ScriptSessionSnapshot getSnapshot(String sessionId) {
        ScriptSession session = getSession(sessionId);
        return session == null ? null : session.snapshot();
    }

    public boolean isAnyRunning() {
        for (ScriptSession session : sessions.values()) {
            if (isRunningState(session)) {
                return true;
            }
        }
        return false;
    }

    public int getRunningSessionCount() {
        int count = 0;
        for (ScriptSession session : sessions.values()) {
            if (isRunningState(session)) {
                count++;
            }
        }
        return count;
    }

    public boolean isActiveRunning() {
        return isRunningState(getActiveSession());
    }

    public boolean isRunning(String sessionId) {
        return isRunningState(getSession(sessionId));
    }

    private boolean isRunningState(ScriptSession session) {
        if (session == null) {
            return false;
        }
        ScriptSession.State state = session.getState();
        return state == ScriptSession.State.QUEUED
                || state == ScriptSession.State.RUNNING
                || state == ScriptSession.State.PAUSED
                || state == ScriptSession.State.STOPPING;
    }

    public void removeSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        sessions.remove(sessionId);
        if (sessionId.equals(activeSessionId)) {
            activeSessionId = null;
            for (String id : sessions.keySet()) {
                activeSessionId = id;
                break;
            }
        }
    }

    public void resetSessionToIdle(String sessionId) {
        ScriptSession session = getSession(sessionId);
        if (session != null) {
            session.resetToIdle();
        }
    }

    public void removeFinishedSessions() {
        for (ScriptSession session : sessions.values()) {
            if (session == null) {
                continue;
            }
            ScriptSession.State state = session.getState();
            if (state == ScriptSession.State.COMPLETED
                    || state == ScriptSession.State.FAILED
                    || state == ScriptSession.State.STOPPED) {
                sessions.remove(session.getSessionId());
            }
        }
    }
}
