package com.auto.master.Task.Operation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class OperationGsonHelper {

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(MetaOperation.class, new MetaOperationDeserializer())
            .registerTypeAdapter(MetaOperation.class, new MetaOperationSerializer())
            .create();

    /**
     * 解析 JSON 字符串为 MetaOperation 列表
     */
    public static List<MetaOperation> parseOperations(String jsonContent) {
        Type operationListType = new TypeToken<List<MetaOperation>>() {}.getType();
        return gson.fromJson(jsonContent, operationListType);
    }

    /**
     * 解析单个操作 JSON
     */
    public static MetaOperation parseOperation(String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            return null;
        }
        return gson.fromJson(jsonContent, MetaOperation.class);
    }

    /**
     * 将 MetaOperation 列表序列化为 JSON 字符串
     */
    public static String toJson(List<MetaOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return "[]";
        }
        return gson.toJson(operations);
    }

    private static class MetaOperationSerializer implements JsonSerializer<MetaOperation> {
        @Override
        public JsonElement serialize(MetaOperation src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            if (src == null) {
                return jsonObject;
            }

            Integer type = src.getType();
            if (type != null) {
                jsonObject.addProperty("type", type);
            }

            Integer responseType = src.getResponseType();
            if (responseType != null) {
                jsonObject.addProperty("responseType", responseType);
            }

            if (src.taskId != null) {
                jsonObject.addProperty("taskId", src.taskId);
            }

            if (src.getId() != null) {
                jsonObject.addProperty("id", src.getId());
            }

            if (src.getName() != null) {
                jsonObject.addProperty("name", src.getName());
            }

            Map<String, Object> inputMap = src.getInputMap();
            if (inputMap != null) {
                jsonObject.add("inputMap", context.serialize(inputMap));
            } else {
                jsonObject.add("inputMap", new JsonObject());
            }

            return jsonObject;
        }
    }

    /**
     * 自定义反序列化器 - 处理多态类型
     */
    private static class MetaOperationDeserializer implements JsonDeserializer<MetaOperation> {
        @Override
        public MetaOperation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {

            JsonObject jsonObject = json.getAsJsonObject();

            // 获取 type 字段来确定具体类型
            JsonElement typeElement = jsonObject.get("type");
            if (typeElement == null) {
                throw new JsonParseException("Missing required field: type");
            }

            Integer type = extractType(typeElement);
            MetaOperation operation = createOperationByType(type);

            // 设置通用字段
            if (jsonObject.has("id")) {
                operation.setId(jsonObject.get("id").getAsString());
            } else {
                operation.setId("op_" + System.currentTimeMillis() + "_" + Math.random());
            }

            if (jsonObject.has("name")) {
                operation.setName(jsonObject.get("name").getAsString());
            }

            if (jsonObject.has("responseType")) {
                operation.setResponseType(jsonObject.get("responseType").getAsInt());
            }

//            if (jsonObject.has("taskId")) {
//                operation.setTaskId(jsonObject.get("taskId").getAsString());
//            }

            // 处理 inputMap
            Map<String, Object> inputMap = extractInputMap(jsonObject, type);
            operation.setInputMap(inputMap);

            return operation;
        }

        private Integer extractType(JsonElement typeElement) {
            if (typeElement.isJsonPrimitive()) {
                JsonPrimitive primitive = typeElement.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    return primitive.getAsInt();
                } else if (primitive.isString()) {
                    // 支持字符串类型的 type："1", "2" 等
                    try {
                        return Integer.parseInt(primitive.getAsString());
                    } catch (NumberFormatException e) {
                        // 如果是 "click", "delay" 等字符串，转换为对应的数字
                        String typeStr = primitive.getAsString().toLowerCase();
                        switch (typeStr) {
                            case "click": return 1;
                            case "delay":
                            case "sleep": return 2;
                            case "launch_app":
                            case "app_launch": return 14;
                            case "ocr": return 9;
                            case "branch":
                            case "if": return 10;
                            case "var_set":
                            case "variable_set": return 11;
                            case "var_script":
                            case "variable_script": return 11;
                            case "var_math":
                            case "variable_math": return 12;
                            case "var_template":
                            case "variable_template": return 13;
                            case "switch":
                            case "switch_branch": return 15;
                            case "loop": return 16;
                            case "color_match":
                            case "color": return 18;
                            case "color_search":
                            case "find_color": return 19;
                            case "http_request":
                            case "http": return 20;
                            default: return 1;
                        }
                    }
                }
            }
            return 1; // 默认类型
        }

        private MetaOperation createOperationByType(Integer type) {
            switch (type) {
                case 1:
                    return new ClickOperation();
                case 2:
                    return new DelayOperation();
                case 3:
                    return new CropRegionOperation();
                case 4:
                    return new LoadImgToMatOperation();
                case 5:
                    return new GestureOperation();
                case 6:
                    return new MatchTemplateOperation();
                case 7:
                    return new MatchMapTemplateOperation();
                case 8:
                    return new JumpTaskOperation();
                case 9:
                    return new OcrOperation();
                case 11:
                    return new VariableScriptOperation();
                case 12:
                    return new VariableMathOperation();
                case 13:
                    return new VariableTemplateOperation();
                case 14:
                    return new AppLaunchOperation();
                case 15:
                    return new SwitchBranchOperation();
                case 16:
                    return new LoopOperation();
                case 17:
                    return new BackKeyOperation();
                case 18:
                    return new ColorMatchOperation();
                case 19:
                    return new ColorSearchOperation();
                case 20:
                    return new HttpRequestOperation();
                default:
                    return new ClickOperation();
            }
        }

        private Map<String, Object> extractInputMap(JsonObject jsonObject, Integer type) {
            Map<String, Object> inputMap = new HashMap<>();

            // 如果 JSON 中有 inputMap 字段，直接使用
            if (jsonObject.has("inputMap") && jsonObject.get("inputMap").isJsonObject()) {
                JsonObject inputMapObj = jsonObject.getAsJsonObject("inputMap");
                for (Map.Entry<String, JsonElement> entry : inputMapObj.entrySet()) {
                    inputMap.put(entry.getKey(), parseJsonElement(entry.getValue()));
                }
                return inputMap;
            }

            // 否则从顶级字段构建 inputMap
            switch (type) {
                case 1: // ClickOperation
                    buildClickInputMap(jsonObject, inputMap);
                    break;
                case 2: // DelayOperation
                    buildDelayInputMap(jsonObject, inputMap);
                    break;
                case 14:
                    buildAppLaunchInputMap(jsonObject, inputMap);
                    break;
            }

            // 通用的 nextOperationId
            if (jsonObject.has("nextOperationId")) {
                inputMap.put(MetaOperation.NEXT_OPERATION_ID, jsonObject.get("nextOperationId").getAsString());
            }

            return inputMap;
        }

        private void buildClickInputMap(JsonObject jsonObject, Map<String, Object> inputMap) {
            // 支持多种坐标格式
            if (jsonObject.has("x") && jsonObject.has("y")) {
                int x = jsonObject.get("x").getAsInt();
                int y = jsonObject.get("y").getAsInt();
                inputMap.put(MetaOperation.CLICK_TARGET, "x:" + x + ",y:" + y);
            } else if (jsonObject.has("clickTarget")) {
                inputMap.put(MetaOperation.CLICK_TARGET, jsonObject.get("clickTarget").getAsString());
            } else if (jsonObject.has("point")) {
                // 支持 {"point": {"x": 100, "y": 200}} 格式
                JsonObject point = jsonObject.getAsJsonObject("point");
                int x = point.get("x").getAsInt();
                int y = point.get("y").getAsInt();
                inputMap.put(MetaOperation.CLICK_TARGET, "x:" + x + ",y:" + y);
            }
        }

        private void buildDelayInputMap(JsonObject jsonObject, Map<String, Object> inputMap) {
            if (jsonObject.has("duration")) {
                inputMap.put(MetaOperation.SLEEP_DURATION, jsonObject.get("duration").getAsInt());
            } else if (jsonObject.has("sleepDuration")) {
                inputMap.put(MetaOperation.SLEEP_DURATION, jsonObject.get("sleepDuration").getAsInt());
            } else if (jsonObject.has("delay")) {
                inputMap.put(MetaOperation.SLEEP_DURATION, jsonObject.get("delay").getAsInt());
            }
            if (jsonObject.has("showCountdown")) {
                inputMap.put(MetaOperation.DELAY_SHOW_COUNTDOWN, jsonObject.get("showCountdown").getAsBoolean());
            } else if (jsonObject.has("showDelayCountdown")) {
                inputMap.put(MetaOperation.DELAY_SHOW_COUNTDOWN, jsonObject.get("showDelayCountdown").getAsBoolean());
            } else if (jsonObject.has(MetaOperation.DELAY_SHOW_COUNTDOWN)) {
                inputMap.put(MetaOperation.DELAY_SHOW_COUNTDOWN, jsonObject.get(MetaOperation.DELAY_SHOW_COUNTDOWN).getAsBoolean());
            }
        }

        private void buildAppLaunchInputMap(JsonObject jsonObject, Map<String, Object> inputMap) {
            if (jsonObject.has("packageName")) {
                inputMap.put(MetaOperation.APP_PACKAGE, jsonObject.get("packageName").getAsString());
            } else if (jsonObject.has("appPackage")) {
                inputMap.put(MetaOperation.APP_PACKAGE, jsonObject.get("appPackage").getAsString());
            }
            if (jsonObject.has("appLabel")) {
                inputMap.put(MetaOperation.APP_LABEL, jsonObject.get("appLabel").getAsString());
            }
            if (jsonObject.has("skipIfForeground")) {
                inputMap.put(MetaOperation.APP_SKIP_IF_FOREGROUND, jsonObject.get("skipIfForeground").getAsBoolean());
            }
            if (jsonObject.has("launchDelayMs")) {
                inputMap.put(MetaOperation.APP_LAUNCH_DELAY_MS, jsonObject.get("launchDelayMs").getAsLong());
            }
        }

        private Object parseJsonElement(JsonElement element) {
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isString()) {
                    return primitive.getAsString();
                } else if (primitive.isNumber()) {
                    // 保持数字类型
                    if (primitive.getAsString().contains(".")) {
                        return primitive.getAsDouble();
                    } else {
                        return primitive.getAsLong();
                    }
                } else if (primitive.isBoolean()) {
                    return primitive.getAsBoolean();
                }
            } else if (element.isJsonNull()) {
                return null;
            } else if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                List<Object> items = new ArrayList<>();
                for (JsonElement item : array) {
                    items.add(parseJsonElement(item));
                }
                return items;
            }
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    map.put(entry.getKey(), parseJsonElement(entry.getValue()));
                }
                return map;
            }
            return element.toString();
        }
    }
}
