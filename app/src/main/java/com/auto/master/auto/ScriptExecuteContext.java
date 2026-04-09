package com.auto.master.auto;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import java.util.Stack;

public class ScriptExecuteContext {

    public  volatile MetaOperation tobeHandledOperation;

    /**
     * 这个currentoperation放在了 operationContext里面 所以这里不需要了 注释掉即可
     */
//    public  volatile MetaOperation currentOperation;

    public  volatile OperationContext sharedContext;

    public volatile Boolean running;
    
    // 暂停标志
    public volatile boolean paused = false;
    
    // 停止标志（强制停止）
    public volatile boolean stopped = false;

    // 返回栈：用于 Task 跳转后返回原 Task
    // 栈中保存的是跳转操作本身，子 Task 执行完后恢复它，让它决定下一步
    public Stack<MetaOperation> returnStack = new Stack<>();

    // 标记是否刚从子 Task 返回（用于 JumpTaskOperation 判断）
    public volatile boolean justReturnedFromSubTask = false;
    
    /**
     * 暂停脚本执行
     */
    public void pause() {
        paused = true;
    }
    
    /**
     * 恢复脚本执行
     */
    public void resume() {
        paused = false;
    }
    
    /**
     * 停止脚本执行
     */
    public void stop() {
        stopped = true;
        running = false;
    }
}
