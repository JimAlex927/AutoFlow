package com.auto.master.Task.Project;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Task;
import com.google.gson.internal.LinkedTreeMap;

import java.util.HashMap;
import java.util.Map;

public class Project {
//    项目名
    private String projectName;

    private Map<String, Task> taskMap = new LinkedTreeMap<>();

//    private Map<String, MetaOperation> operationMap = new HashMap<>();

    //这个项目的入口
    private String startTaskId;






    public Map<String, Task> getTaskMap() {
        return taskMap;
    }

    public void setTaskMap(Map<String, Task> taskMap) {
        this.taskMap = taskMap;
    }

    public String getStartTaskId() {
        return startTaskId;
    }

    public void setStartTaskId(String startTaskId) {
        this.startTaskId = startTaskId;
    }




    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

//    public Map<String, MetaOperation> getOperationMap() {
//        return operationMap;
//    }


}
