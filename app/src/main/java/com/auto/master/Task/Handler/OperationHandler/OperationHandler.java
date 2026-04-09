package com.auto.master.Task.Handler.OperationHandler;

import android.os.Handler;
import android.os.Looper;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

/**
 * 任务：执行的最小单元的处理函数，策略模式。每一步可以看成是一个operationHandler
 */
public abstract class OperationHandler {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Integer type = -1;
    public  Handler getMainHandler(){
        return MAIN;
    }

    /**
     * 任何一个继承的类都必须返回tupe表明这是什么类型的 operation
     * @return
     */
    public int getType(){

        return this.type;
    }

    public void setType(Integer type){
        this.type = type;
    }

    public abstract boolean  handle(MetaOperation obj, OperationContext ctx);


}
