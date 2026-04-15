package com.auto.master.Task.Handler.ResponseHandler;

import android.os.Handler;
import android.os.Looper;

import com.auto.master.Task.Task;
import com.auto.master.auto.ScriptExecuteContext;

import java.util.Map;

public abstract class DefaultResponseHandler {

    public Integer type = -1;

    /** Shared main-thread Handler — avoids allocating a new Handler on every click path. */
    protected static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public void process(Object response, ScriptExecuteContext ctx) {
        return;
    }

    /**
     * Fallback task lookup: scans all tasks in the project to find the one that owns
     * the given operation ID. Used when {@code lastOperation.taskId} is absent or stale.
     */
    protected static Task resolveTaskByOperationId(Map<String, Task> taskMap, String operationId) {
        if (taskMap == null || operationId == null) {
            return null;
        }
        for (Task t : taskMap.values()) {
            if (t != null && t.getOperationMap() != null
                    && t.getOperationMap().containsKey(operationId)) {
                return t;
            }
        }
        return null;
    }
}
