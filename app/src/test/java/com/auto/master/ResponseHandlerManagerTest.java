package com.auto.master;

import static org.junit.Assert.assertTrue;

import com.auto.master.Task.Handler.ResponseHandler.JumpToNextOperationResponseHandler;
import com.auto.master.Task.Handler.ResponseHandler.ResponseHandlerManager;
import com.auto.master.Task.Operation.VariableScriptOperation;

import org.junit.Test;

public class ResponseHandlerManagerTest {

    @Test
    public void variableScriptUsesJumpToNextResponseHandler() {
        assertTrue(
                ResponseHandlerManager.getResponseHandler(VariableScriptOperation.class, 1)
                        instanceof JumpToNextOperationResponseHandler
        );
    }
}
