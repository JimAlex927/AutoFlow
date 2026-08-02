package com.auto.master.auto;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ScriptSessionTest {

    @Test
    public void resetToIdleKeepsMostRecentRunLogs() {
        ScriptSession session = new ScriptSession("session-id", "test");
        session.appendLog("first line");
        session.appendLog("last line");

        session.resetToIdle();

        assertEquals(ScriptSession.State.IDLE, session.getState());
        assertEquals(2, session.getLogsSnapshot().size());
        assertEquals("last line", session.getLogsSnapshot().get(1));
    }
}
