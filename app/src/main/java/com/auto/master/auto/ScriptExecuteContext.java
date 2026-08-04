package com.auto.master.auto;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import java.util.ArrayDeque;
import java.util.Deque;

public class ScriptExecuteContext {

    public volatile String sessionId;

    public  volatile MetaOperation tobeHandledOperation;

    public volatile int repeatedTimes = 0;

    private volatile RepeatExecutionState repeatExecutionState;

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

    public void beginRepeatExecution(MetaOperation startOperation,
                                     String controllerOperationId,
                                     MetaOperation nextOperation,
                                     String mode,
                                     int totalRounds,
                                     String expression) {
        beginRepeatExecution(startOperation, controllerOperationId, nextOperation, mode,
                totalRounds, expression, "");
    }

    public void beginRepeatExecution(MetaOperation startOperation,
                                     String controllerOperationId,
                                     MetaOperation nextOperation,
                                     String mode,
                                     int totalRounds,
                                     String expression,
                                     String nextRoundExpression) {
        repeatExecutionState = new RepeatExecutionState(
                startOperation,
                controllerOperationId,
                nextOperation,
                mode,
                Math.max(1, totalRounds),
                expression,
                nextRoundExpression);
    }

    /**
     * Called when a repeat round reaches its boundary. The result records whether
     * the selected start node should run again or the configured next node should
     * receive control after the loop has completed.
     */
    public RepeatAdvanceResult advanceRepeatExecutionAtTaskEnd() {
        RepeatExecutionState state = repeatExecutionState;
        if (state == null || state.startOperation == null) {
            return null;
        }
        state.completedRounds++;
        boolean continueRepeat;
        if (MetaOperation.REPEAT_MODE_EXPRESSION.equals(state.mode)) {
            if (!executeRepeatNextRoundUpdate(state)) {
                continueRepeat = false;
            } else {
                RepeatExpressionEvaluator.EvaluationResult evaluation =
                        RepeatExpressionEvaluator.evaluate(
                                state.expression,
                                sharedContext == null ? null : sharedContext.variables);
                continueRepeat = evaluation.shouldContinue;
                logRepeatExpressionResult(state, evaluation);
            }
        } else {
            continueRepeat = MetaOperation.REPEAT_MODE_INFINITE.equals(state.mode);
            if (MetaOperation.REPEAT_MODE_COUNT.equals(state.mode)) {
                continueRepeat = state.completedRounds < state.totalRounds;
            }
            if (continueRepeat && !executeRepeatNextRoundUpdate(state)) {
                continueRepeat = false;
            }
        }
        if (continueRepeat) {
            return new RepeatAdvanceResult(state.startOperation, true, state.completedRounds,
                    state.totalRounds, state.mode);
        }
        repeatExecutionState = null;
        return new RepeatAdvanceResult(state.nextOperation, false, state.completedRounds,
                state.totalRounds, state.mode);
    }

    private boolean executeRepeatNextRoundUpdate(RepeatExecutionState state) {
        if (state == null || state.nextRoundExpression == null
                || state.nextRoundExpression.trim().isEmpty()) {
            return true;
        }
        RepeatExpressionEvaluator.UpdateResult update =
                RepeatExpressionEvaluator.executeNextRoundUpdate(
                        state.nextRoundExpression,
                        sharedContext == null ? null : sharedContext.variables);
        logRepeatNextRoundUpdate(state, update);
        return update.success;
    }

    private void logRepeatNextRoundUpdate(
            RepeatExecutionState state,
            RepeatExpressionEvaluator.UpdateResult update) {
        if (sharedContext == null || sharedContext.runtimeLogSink == null || update == null) {
            return;
        }
        String expression = compactRepeatLogExpression(
                state == null ? null : state.nextRoundExpression);
        if (!update.success) {
            sharedContext.runtimeLogSink.log("[error] 循环第 "
                    + state.completedRounds + " 轮进入下一轮前处理失败: "
                    + update.errorMessage + "，已结束循环，表达式=" + expression);
            return;
        }
        sharedContext.runtimeLogSink.log("[info] 循环第 "
                + state.completedRounds + " 轮进入下一轮前处理完成，表达式=" + expression);
    }

    private void logRepeatExpressionResult(
            RepeatExecutionState state,
            RepeatExpressionEvaluator.EvaluationResult evaluation) {
        if (sharedContext == null || sharedContext.runtimeLogSink == null || evaluation == null) {
            return;
        }
        String expression = compactRepeatLogExpression(
                state == null ? null : state.expression);
        if (evaluation.hasError()) {
            sharedContext.runtimeLogSink.log("[error] 循环表达式第 "
                    + state.completedRounds + " 轮求值失败: "
                    + evaluation.errorMessage + "，表达式=" + expression);
            return;
        }
        sharedContext.runtimeLogSink.log("[info] 循环表达式第 "
                + state.completedRounds + " 轮 => "
                + evaluation.shouldContinue + "，表达式=" + expression);
    }

    private static String compactRepeatLogExpression(String rawExpression) {
        String expression = rawExpression == null ? "" : rawExpression;
        expression = expression.replace('\n', ' ').replace('\r', ' ').trim();
        return expression.length() > 120
                ? expression.substring(0, 117) + "..." : expression;
    }

    public boolean isRepeatExecutionActiveFor(String controllerOperationId) {
        RepeatExecutionState state = repeatExecutionState;
        return state != null && controllerOperationId != null
                && controllerOperationId.equals(state.controllerOperationId);
    }

    public static final class RepeatAdvanceResult {
        public final MetaOperation nextOperation;
        public final boolean restarting;
        public final int completedRounds;
        public final int totalRounds;
        public final String mode;

        RepeatAdvanceResult(MetaOperation nextOperation,
                            boolean restarting,
                            int completedRounds,
                            int totalRounds,
                            String mode) {
            this.nextOperation = nextOperation;
            this.restarting = restarting;
            this.completedRounds = completedRounds;
            this.totalRounds = totalRounds;
            this.mode = mode;
        }
    }

    private static final class RepeatExecutionState {
        final MetaOperation startOperation;
        final String controllerOperationId;
        final MetaOperation nextOperation;
        final String mode;
        final int totalRounds;
        final String expression;
        final String nextRoundExpression;
        int completedRounds;

        RepeatExecutionState(MetaOperation startOperation,
                             String controllerOperationId,
                             MetaOperation nextOperation,
                             String mode,
                             int totalRounds,
                             String expression,
                             String nextRoundExpression) {
            this.startOperation = startOperation;
            this.controllerOperationId = controllerOperationId;
            this.nextOperation = nextOperation;
            this.mode = mode;
            this.totalRounds = totalRounds;
            this.expression = expression == null ? "" : expression.trim();
            this.nextRoundExpression = nextRoundExpression == null
                    ? "" : nextRoundExpression.trim();
        }
    }
}
