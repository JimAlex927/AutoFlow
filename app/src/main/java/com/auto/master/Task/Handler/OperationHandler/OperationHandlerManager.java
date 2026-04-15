package com.auto.master.Task.Handler.OperationHandler;

import com.auto.master.Task.Handler.ResponseHandler.ColorMatchResponseHandler;
import com.auto.master.Task.Handler.ResponseHandler.ConditionBranchResponseHandler;
import com.auto.master.Task.Handler.ResponseHandler.DefaultResponseHandler;
import com.auto.master.Task.Handler.ResponseHandler.JumpTaskResponseHandler;
import com.auto.master.Task.Handler.ResponseHandler.JumpToNextOperationResponseHandler;
import com.auto.master.Task.Handler.ResponseHandler.MatchTemplateDynamicJumpResponseHandler;
import com.auto.master.Task.Operation.AppLaunchOperation;
import com.auto.master.Task.Operation.BackKeyOperation;
import com.auto.master.Task.Operation.ClickOperation;
import com.auto.master.Task.Operation.ColorMatchOperation;
import com.auto.master.Task.Operation.ColorSearchOperation;
import com.auto.master.Task.Operation.CropRegionOperation;
import com.auto.master.Task.Operation.DelayOperation;
import com.auto.master.Task.Operation.DynamicDelayOperation;
import com.auto.master.Task.Operation.GestureOperation;
import com.auto.master.Task.Operation.HttpRequestOperation;
import com.auto.master.Task.Operation.JumpTaskOperation;
import com.auto.master.Task.Operation.LoadImgToMatOperation;
import com.auto.master.Task.Operation.LoopOperation;
import com.auto.master.Task.Operation.MatchMapTemplateOperation;
import com.auto.master.Task.Operation.MatchTemplateOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OcrOperation;
import com.auto.master.Task.Operation.OperationType;
import com.auto.master.Task.Operation.SetCaptureScaleOperation;
import com.auto.master.Task.Operation.SwitchBranchOperation;
import com.auto.master.Task.Operation.VariableMathOperation;
import com.auto.master.Task.Operation.VariableScriptOperation;
import com.auto.master.Task.Operation.VariableSetOperation;
import com.auto.master.Task.Operation.VariableTemplateOperation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class OperationHandlerManager {
    private static final Map<Integer, OperationHandler> operationHandlerCacheMap = new ConcurrentHashMap<>();
    private static final Map<Integer, OperationDescriptor> descriptorsByCode = new LinkedHashMap<>();
    private static final Map<Class<? extends MetaOperation>, OperationDescriptor> descriptorsByClass = new HashMap<>();
    private static final Map<String, Integer> typeCodesByAlias = new HashMap<>();

    static {
        register(OperationType.CLICK, ClickOperation.class, ClickOperation::new, ClickOperationHandler::new, "click");
        register(OperationType.DELAY, DelayOperation.class, DelayOperation::new, DelayOperationHandler::new, "delay", "sleep");
        register(OperationType.CROP_REGION, CropRegionOperation.class, CropRegionOperation::new, CropRegionOperationHandler::new, "crop", "crop_region");
        register(OperationType.LOAD_IMG_TO_MAT, LoadImgToMatOperation.class, LoadImgToMatOperation::new, LoadImgToMatOperationHandler::new, "load", "load_img_to_mat");
        register(OperationType.GESTURE, GestureOperation.class, GestureOperation::new, GestureOperationHandler::new, "gesture");
        register(OperationType.MATCH_TEMPLATE, MatchTemplateOperation.class, MatchTemplateOperation::new, MatchtemplateOperationHandler::new, "match", "match_template");
        register(OperationType.MATCH_MAP_TEMPLATE, MatchMapTemplateOperation.class, MatchMapTemplateOperation::new, MatchMaptemplateOperationHandler::new, "matchmap", "match_map_template");
        register(OperationType.JUMP_TASK, JumpTaskOperation.class, JumpTaskOperation::new, JumpTaskOperationHandler::new, "jump", "jump_task");
        register(OperationType.OCR, OcrOperation.class, OcrOperation::new, OcrOperationHandler::new, "ocr");
        register(OperationType.VARIABLE_SCRIPT, VariableSetOperation.class, VariableSetOperation::new, VariableOperationHandler::new,
                "var_set", "variable_set", "var_script", "variable_script");
        linkOperationClass(OperationType.VARIABLE_SCRIPT.getCode(), VariableScriptOperation.class);
        register(OperationType.VARIABLE_MATH, VariableMathOperation.class, VariableMathOperation::new, VariableMathOperationHandler::new, "var_math", "variable_math");
        register(OperationType.VARIABLE_TEMPLATE, VariableTemplateOperation.class, VariableTemplateOperation::new, VariableTemplateOperationHandler::new, "var_template", "variable_template");
        register(OperationType.APP_LAUNCH, AppLaunchOperation.class, AppLaunchOperation::new, AppLaunchOperationHandler::new, "app", "launch_app", "app_launch");
        register(OperationType.SWITCH_BRANCH, SwitchBranchOperation.class, SwitchBranchOperation::new, SwitchBranchOperationHandler::new, "switch", "switch_branch", "branch", "if");
        register(OperationType.LOOP, LoopOperation.class, LoopOperation::new, LoopOperationHandler::new, "loop");
        register(OperationType.BACK_KEY, BackKeyOperation.class, BackKeyOperation::new, BackKeyOperationHandler::new, "back", "back_key");
        register(OperationType.COLOR_MATCH, ColorMatchOperation.class, ColorMatchOperation::new, ColorMatchOperationHandler::new, "color", "color_match");
        register(OperationType.COLOR_SEARCH, ColorSearchOperation.class, ColorSearchOperation::new, ColorSearchOperationHandler::new, "color_search", "find_color");
        register(OperationType.HTTP_REQUEST, HttpRequestOperation.class, HttpRequestOperation::new, HttpRequestOperationHandler::new, "http", "http_request");
        register(OperationType.DYNAMIC_DELAY, DynamicDelayOperation.class, DynamicDelayOperation::new, DynamicDelayOperationHandler::new, "dynamic_delay", "dynamicdelay");
        register(OperationType.SET_CAPTURE_SCALE, SetCaptureScaleOperation.class, SetCaptureScaleOperation::new, SetCaptureScaleOperationHandler::new, "set_scale", "set_capture_scale", "capture_scale");

        registerResponse(OperationType.CLICK.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.DELAY.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.DELAY.getCode(), 2, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.CROP_REGION.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.LOAD_IMG_TO_MAT.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.MATCH_TEMPLATE.getCode(), 1, MatchTemplateDynamicJumpResponseHandler::new);
        registerResponse(OperationType.MATCH_TEMPLATE.getCode(), 2, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.GESTURE.getCode(), 2, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.MATCH_MAP_TEMPLATE.getCode(), 1, MatchTemplateDynamicJumpResponseHandler::new);
        registerResponse(OperationType.JUMP_TASK.getCode(), 1, JumpTaskResponseHandler::new);
        registerResponse(OperationType.JUMP_TASK.getCode(), 2, JumpTaskResponseHandler::new);
        registerResponse(OperationType.OCR.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.VARIABLE_SCRIPT.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.VARIABLE_MATH.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.VARIABLE_TEMPLATE.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.APP_LAUNCH.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.SWITCH_BRANCH.getCode(), 1, ConditionBranchResponseHandler::new);
        registerResponse(OperationType.LOOP.getCode(), 1, ConditionBranchResponseHandler::new);
        registerResponse(OperationType.BACK_KEY.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.COLOR_MATCH.getCode(), 1, ColorMatchResponseHandler::new);
        registerResponse(OperationType.COLOR_SEARCH.getCode(), 1, ColorMatchResponseHandler::new);
        registerResponse(OperationType.HTTP_REQUEST.getCode(), 1, ColorMatchResponseHandler::new);
        registerResponse(OperationType.DYNAMIC_DELAY.getCode(), 1, JumpToNextOperationResponseHandler::new);
        registerResponse(OperationType.SET_CAPTURE_SCALE.getCode(), 1, JumpToNextOperationResponseHandler::new);
    }

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

    public static Integer resolveTypeCode(String rawType) {
        if (rawType == null) {
            return null;
        }
        String normalized = normalizeAlias(rawType);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
        }
        return typeCodesByAlias.get(normalized);
    }

    public static MetaOperation createOperation(Integer code) {
        OperationDescriptor descriptor = descriptorsByCode.get(code);
        return descriptor == null ? null : descriptor.createOperation();
    }

    public static DefaultResponseHandler createResponseHandler(Class<? extends MetaOperation> operationClass,
                                                               Integer responseType) {
        OperationDescriptor descriptor = operationClass == null ? null : descriptorsByClass.get(operationClass);
        return descriptor == null ? null : descriptor.createResponseHandler(responseType == null ? 1 : responseType);
    }

    private static OperationHandler createHandler(Integer code) {
        OperationDescriptor descriptor = descriptorsByCode.get(code);
        return descriptor == null ? null : descriptor.createHandler();
    }

    private static void register(OperationType type,
                                 Class<? extends MetaOperation> operationClass,
                                 Supplier<? extends MetaOperation> operationFactory,
                                 Supplier<OperationHandler> handlerFactory,
                                 String... aliases) {
        if (type == null || operationClass == null || operationFactory == null) {
            throw new IllegalArgumentException("Operation descriptor must have type, class and factory");
        }
        OperationDescriptor descriptor = new OperationDescriptor(type, operationClass, operationFactory, handlerFactory);
        descriptorsByCode.put(type.getCode(), descriptor);
        descriptorsByClass.put(operationClass, descriptor);
        registerAlias(type.name(), type.getCode());
        registerAlias(type.getIconKey(), type.getCode());
        if (aliases != null) {
            for (String alias : aliases) {
                registerAlias(alias, type.getCode());
            }
        }
    }

    private static void linkOperationClass(int code, Class<? extends MetaOperation> operationClass) {
        OperationDescriptor descriptor = descriptorsByCode.get(code);
        if (descriptor == null || operationClass == null) {
            return;
        }
        descriptorsByClass.put(operationClass, descriptor);
    }

    private static void registerResponse(int code,
                                         int responseType,
                                         Supplier<DefaultResponseHandler> responseFactory) {
        OperationDescriptor descriptor = descriptorsByCode.get(code);
        if (descriptor == null || responseFactory == null) {
            return;
        }
        descriptor.responseFactories.put(responseType, responseFactory);
    }

    private static void registerAlias(String alias, int code) {
        String normalized = normalizeAlias(alias);
        if (!normalized.isEmpty()) {
            typeCodesByAlias.put(normalized, code);
        }
    }

    private static String normalizeAlias(String alias) {
        return alias == null ? "" : alias.trim().toLowerCase(Locale.ROOT);
    }

    private static final class OperationDescriptor {
        private final OperationType type;
        private final Class<? extends MetaOperation> operationClass;
        private final Supplier<? extends MetaOperation> operationFactory;
        private final Supplier<OperationHandler> handlerFactory;
        private final Map<Integer, Supplier<DefaultResponseHandler>> responseFactories = new HashMap<>();

        private OperationDescriptor(OperationType type,
                                    Class<? extends MetaOperation> operationClass,
                                    Supplier<? extends MetaOperation> operationFactory,
                                    Supplier<OperationHandler> handlerFactory) {
            this.type = type;
            this.operationClass = operationClass;
            this.operationFactory = operationFactory;
            this.handlerFactory = handlerFactory;
        }

        public MetaOperation createOperation() {
            return operationFactory.get();
        }

        public OperationHandler createHandler() {
            return handlerFactory == null ? null : handlerFactory.get();
        }

        public DefaultResponseHandler createResponseHandler(int responseType) {
            Supplier<DefaultResponseHandler> factory = responseFactories.get(responseType);
            return factory == null ? null : factory.get();
        }
    }
}
