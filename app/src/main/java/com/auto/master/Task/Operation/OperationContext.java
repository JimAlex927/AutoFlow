package com.auto.master.Task.Operation;

import com.auto.master.Task.Project.Project;

import java.util.HashMap;
import java.util.Map;

public class OperationContext {
    //运行一个operation的时候必须携带一个上下文
//    1、 project  包含当前这个Operation的 project数据
    public Project anchorProject;


    //最近一步handle的结果
    public Map<String,Object> currentResponse;

    //
    public MetaOperation lastOperation;


    public MetaOperation currentOperation;

    // 运行期变量池（跨节点共享）
    public Map<String, Object> variables = new HashMap<>();

}
