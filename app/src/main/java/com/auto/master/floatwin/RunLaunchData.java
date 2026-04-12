package com.auto.master.floatwin;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Task;

import java.util.ArrayList;
import java.util.List;

final class RunLaunchData {
    MetaOperation startOperation;
    Task selectedTask;
    String projectName;
    String selectedTaskName;
    List<OperationItem> selectedTaskOperations = new ArrayList<>();
    OperationContext ctx;
}
