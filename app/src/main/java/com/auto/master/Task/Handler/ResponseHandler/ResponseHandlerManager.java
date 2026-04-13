package com.auto.master.Task.Handler.ResponseHandler;

import com.auto.master.Task.Handler.OperationHandler.OperationHandlerManager;
import com.auto.master.Task.Operation.MetaOperation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ResponseHandlerManager {

    private static final Map<String, DefaultResponseHandler> responseHandlerCacheMap = new ConcurrentHashMap<>();

    private static String makeKey(Class<? extends MetaOperation> operationClass, int responseType) {
        return operationClass.getName() + ":" + responseType;
    }

    public static DefaultResponseHandler getResponseHandler(Class<? extends MetaOperation> clz, Integer responseType) {
        int responseTypeCode = responseType == null ? 1 : responseType;
        String key = makeKey(clz, responseTypeCode);
        return responseHandlerCacheMap.computeIfAbsent(key, ignored -> {
            DefaultResponseHandler responseHandler =
                    OperationHandlerManager.createResponseHandler(clz, responseTypeCode);
            return responseHandler != null ? responseHandler : new JumpToNextOperationResponseHandler();
        });
    }
}
