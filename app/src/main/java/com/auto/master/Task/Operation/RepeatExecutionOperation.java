package com.auto.master.Task.Operation;

/** Starts a complete Task-tail sequence repeatedly from a selected operation. */
public class RepeatExecutionOperation extends MetaOperation {
    public RepeatExecutionOperation() {
        this.setType(OperationType.REPEAT_EXECUTION.getCode());
    }
}
