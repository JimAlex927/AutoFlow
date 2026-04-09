package com.auto.master.Task.Handler.OperationHandler;

import com.auto.master.Task.Operation.OperationType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OperationHandlerManager {
    private static final Map<Integer, OperationHandler> operationHandlerCacheMap = new ConcurrentHashMap<>();

    /**
     * 一种operation 只对应一种 operationType 同时也只对应一种 operationHandler
     * 但是一个operationHandler会根据responseType输出不同的 response
     */
    public static OperationHandler getOperationHandler(Integer operationType) {
        if (operationType == null) {
            return null;
        }
        return operationHandlerCacheMap.computeIfAbsent(operationType, OperationHandlerManager::createHandler);
    }

    private static OperationHandler createHandler(int operationType) {
        OperationType type = OperationType.fromCode(operationType);
        if (type == null) {
            return null;
        }
        switch (type) {
            case CLICK:              return new ClickOperationHandler();
            case DELAY:              return new DelayOperationHandler();
            case CROP_REGION:        return new CropRegionOperationHandler();
            case LOAD_IMG_TO_MAT:    return new LoadImgToMatOperationHandler();
            case GESTURE:            return new GestureOperationHandler();
            case MATCH_TEMPLATE:     return new MatchtemplateOperationHandler();
            case MATCH_MAP_TEMPLATE: return new MatchMaptemplateOperationHandler();
            case JUMP_TASK:          return new JumpTaskOperationHandler();
            case OCR:                return new OcrOperationHandler();
            case VARIABLE_SCRIPT:    return new VariableScriptOperationHandler();
            case VARIABLE_MATH:      return new VariableMathOperationHandler();
            case VARIABLE_TEMPLATE:  return new VariableTemplateOperationHandler();
            case APP_LAUNCH:         return new AppLaunchOperationHandler();
            case SWITCH_BRANCH:      return new SwitchBranchOperationHandler();
            case LOOP:               return new LoopOperationHandler();
            case BACK_KEY:           return new BackKeyOperationHandler();
            case COLOR_MATCH:        return new ColorMatchOperationHandler();
            case COLOR_SEARCH:       return new ColorSearchOperationHandler();
            default:                 return null;
        }
    }
}
