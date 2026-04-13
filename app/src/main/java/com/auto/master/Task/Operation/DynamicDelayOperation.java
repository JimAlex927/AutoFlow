package com.auto.master.Task.Operation;

/**
 * 动态延时操作：延时时长由运行时变量决定（单位毫秒）。
 * type = 21
 */
public class DynamicDelayOperation extends MetaOperation {

    public DynamicDelayOperation() {
        this.setType(21);
    }
}
