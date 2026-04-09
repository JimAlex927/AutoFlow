package com.auto.master.Task;

import com.auto.master.Task.Operation.MetaOperation;
import com.google.gson.internal.LinkedTreeMap;

import java.util.HashMap;
import java.util.Map;

public class Task {

//    taskId
    private String id;

    private String name;

//    //执行当前task结束后 执行的 下一个taskId  task没有先后关系 operation自行实现
//    private String nextTaskId;

    private Map<String, MetaOperation> operationMap = new LinkedTreeMap<>();

    private String startOperationId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, MetaOperation> getOperationMap() {
        return operationMap;
    }

    public void setOperationMap(Map<String, MetaOperation> operationMap) {
        this.operationMap = operationMap;
    }

    public String getStartOperationId() {
        return startOperationId;
    }

    public void setStartOperationId(String startOperationId) {
        this.startOperationId = startOperationId;
    }

    /**
     *
     * @param operation
     */
    //给Task添加operation
    public void putOperation(MetaOperation operation){
        String opId = operation.getId();
        this.operationMap.put(opId,operation);
        operation.taskId = this.id;
    }
}
