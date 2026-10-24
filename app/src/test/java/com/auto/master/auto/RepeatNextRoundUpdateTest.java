package com.auto.master.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Operation.RepeatExecutionOperation;

import org.junit.Test;

public class RepeatNextRoundUpdateTest {

    @Test
    public void updateRunsOnlyWhenTransitioningIntoAnotherRound() {
        ScriptExecuteContext executeContext = new ScriptExecuteContext();
        executeContext.sharedContext = new OperationContext();
        executeContext.sharedContext.variables.put("times", 0L);
        RepeatExecutionOperation startOperation = new RepeatExecutionOperation();
        executeContext.beginRepeatExecution(startOperation, "controller", null,
                MetaOperation.REPEAT_MODE_COUNT, 3, "", "times = times + 1");

        ScriptExecuteContext.RepeatAdvanceResult first =
                executeContext.advanceRepeatExecutionAtTaskEnd();
        assertTrue(first.restarting);
        assertEquals(1L, executeContext.sharedContext.variables.get("times"));

        ScriptExecuteContext.RepeatAdvanceResult second =
                executeContext.advanceRepeatExecutionAtTaskEnd();
        assertTrue(second.restarting);
        assertEquals(2L, executeContext.sharedContext.variables.get("times"));

        ScriptExecuteContext.RepeatAdvanceResult finalResult =
                executeContext.advanceRepeatExecutionAtTaskEnd();
        assertFalse(finalResult.restarting);
        assertEquals(2L, executeContext.sharedContext.variables.get("times"));
    }

    @Test
    public void expressionModeUpdatesVariableBeforeCheckingNextRoundCondition() {
        ScriptExecuteContext executeContext = new ScriptExecuteContext();
        executeContext.sharedContext = new OperationContext();
        executeContext.sharedContext.variables.put("times", 0L);
        RepeatExecutionOperation startOperation = new RepeatExecutionOperation();
        executeContext.beginRepeatExecution(startOperation, "controller", null,
                MetaOperation.REPEAT_MODE_EXPRESSION, 1,
                "${times} < 3", "times = times + 1");

        assertTrue(executeContext.advanceRepeatExecutionAtTaskEnd().restarting);
        assertEquals(1L, executeContext.sharedContext.variables.get("times"));
        assertTrue(executeContext.advanceRepeatExecutionAtTaskEnd().restarting);
        assertEquals(2L, executeContext.sharedContext.variables.get("times"));
        assertFalse(executeContext.advanceRepeatExecutionAtTaskEnd().restarting);
        assertEquals(3L, executeContext.sharedContext.variables.get("times"));
    }
}
