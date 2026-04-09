package com.auto.master.Task.Handler.ResponseHandler;

import com.auto.master.Task.Operation.ClickOperation;
import com.auto.master.Task.Operation.ColorMatchOperation;
import com.auto.master.Task.Operation.ColorSearchOperation;
import com.auto.master.Task.Operation.CropRegionOperation;
import com.auto.master.Task.Operation.DelayOperation;
import com.auto.master.Task.Operation.GestureOperation;
import com.auto.master.Task.Operation.JumpTaskOperation;
import com.auto.master.Task.Operation.LoadImgToMatOperation;
import com.auto.master.Task.Operation.MatchMapTemplateOperation;
import com.auto.master.Task.Operation.MatchTemplateOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OcrOperation;
import com.auto.master.Task.Operation.VariableMathOperation;
import com.auto.master.Task.Operation.LoopOperation;
import com.auto.master.Task.Operation.SwitchBranchOperation;
import com.auto.master.Task.Operation.VariableScriptOperation;
import com.auto.master.Task.Operation.VariableSetOperation;
import com.auto.master.Task.Operation.VariableTemplateOperation;
import com.auto.master.Task.Operation.BackKeyOperation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ResponseHandlerManager {

    private static final Map<Class<? extends MetaOperation>, Map<Integer, DefaultResponseHandler>> responseHandlerCacheMap = new HashMap<>();

    // Registry: maps (OperationClass, ResponseType) -> Handler Factory
    private static final Map<String, Supplier<DefaultResponseHandler>> HANDLER_REGISTRY = new HashMap<>();

    static {
        // Register all operation type + response type combinations
        register(ClickOperation.class, 1, JumpToNextOperationResponseHandler::new);

        register(DelayOperation.class, 1, JumpToNextOperationResponseHandler::new);
        register(DelayOperation.class, 2, JumpToNextOperationResponseHandler::new);

        register(CropRegionOperation.class, 1, JumpToNextOperationResponseHandler::new);
        register(LoadImgToMatOperation.class, 1, JumpToNextOperationResponseHandler::new);

        register(MatchTemplateOperation.class, 1, MatchTemplateDynamicJumpResponseHandler::new);
        register(MatchTemplateOperation.class, 2, JumpToNextOperationResponseHandler::new);

        register(GestureOperation.class, 2, JumpToNextOperationResponseHandler::new);

        register(MatchMapTemplateOperation.class, 1, MatchTemplateDynamicJumpResponseHandler::new);

        register(JumpTaskOperation.class, 1, JumpTaskResponseHandler::new);
        register(JumpTaskOperation.class, 2, JumpTaskResponseHandler::new);

        register(OcrOperation.class, 1, JumpToNextOperationResponseHandler::new);

        register(VariableScriptOperation.class, 1, JumpToNextOperationResponseHandler::new);
        register(SwitchBranchOperation.class, 1, ConditionBranchResponseHandler::new);
        register(LoopOperation.class, 1, ConditionBranchResponseHandler::new);

        register(VariableSetOperation.class, 1, JumpToNextOperationResponseHandler::new);
        register(VariableMathOperation.class, 1, JumpToNextOperationResponseHandler::new);
        register(VariableTemplateOperation.class, 1, JumpToNextOperationResponseHandler::new);
        
        register(BackKeyOperation.class, 1, JumpToNextOperationResponseHandler::new);
        register(ColorMatchOperation.class, 1, ColorMatchResponseHandler::new);
        register(ColorSearchOperation.class, 1, ColorMatchResponseHandler::new);
    }

    private static void register(Class<? extends MetaOperation> operationClass, int responseType, Supplier<DefaultResponseHandler> handlerFactory) {
        String key = makeKey(operationClass, responseType);
        HANDLER_REGISTRY.put(key, handlerFactory);
    }

    private static String makeKey(Class<? extends MetaOperation> operationClass, int responseType) {
        return operationClass.getName() + ":" + responseType;
    }

    public static DefaultResponseHandler getResponseHandler(Class<? extends MetaOperation> clz, Integer responseType) {
        int responseTypeCode = responseType == null ? 1 : responseType;

        // Check cache first
        Map<Integer, DefaultResponseHandler> integerDefaultResponseHandlerMap = responseHandlerCacheMap.get(clz);
        if (integerDefaultResponseHandlerMap == null) {
            integerDefaultResponseHandlerMap = new HashMap<>();
            responseHandlerCacheMap.put(clz, integerDefaultResponseHandlerMap);
        }

        DefaultResponseHandler responseHandler = integerDefaultResponseHandlerMap.get(responseTypeCode);
        if (responseHandler != null) {
            return responseHandler;
        }

        // Look up in registry
        String key = makeKey(clz, responseTypeCode);
        Supplier<DefaultResponseHandler> factory = HANDLER_REGISTRY.get(key);

        if (factory != null) {
            responseHandler = factory.get();
            integerDefaultResponseHandlerMap.put(responseTypeCode, responseHandler);
        } else {
            // Default fallback
            responseHandler = new JumpToNextOperationResponseHandler();
        }

        return responseHandler;
    }
}
