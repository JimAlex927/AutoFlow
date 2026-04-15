package com.auto.master.auto;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import java.util.ArrayDeque;
import java.util.Deque;

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
    // 使用 ArrayDeque 代替 Stack（Stack 继承 Vector，每次操作都加锁；
    // returnStack 只在单一脚本执行线程访问，不需要同步）
    public Deque<MetaOperation> returnStack = new ArrayDeque<>();

    // 标记是否刚从子 Task 返回（用于 JumpTaskOperation 判断）
    public volatile boolean justReturnedFromSubTask = false;

    // 用于 pause/resume 的锁对象，避免执行线程轮询 Thread.sleep(100)
    public final Object pauseLock = new Object();

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
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    /**
     * 停止脚本执行
     */
    public void stop() {
        stopped = true;
        running = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }
}
