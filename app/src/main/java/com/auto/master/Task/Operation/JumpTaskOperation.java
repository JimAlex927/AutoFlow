package com.auto.master.Task.Operation;

/**
 * 跳转 Task 操作
 * 用于跳转到指定 Task 的指定 Operation 执行
 * 支持执行完子 Task 后返回原 Task 继续执行
 */
public class JumpTaskOperation extends MetaOperation {

    public JumpTaskOperation() {
        // JumpTask Operation 类型为 8
        this.setType(8);
    }
}
