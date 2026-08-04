package com.auto.master.Task.Handler.OperationHandler;

import android.text.TextUtils;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Operation.OperationType;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RuntimeOperationLogFormatter {

    private RuntimeOperationLogFormatter() {
    }

    public static void logOperationDetail(MetaOperation operation, OperationContext ctx, boolean handlerOk) {
        if (operation == null || ctx == null || ctx.runtimeLogSink == null) {
            return;
        }
        String line = format(operation, ctx, handlerOk);
        if (!TextUtils.isEmpty(line)) {
            ctx.runtimeLogSink.log(line);
        }
    }

    public static void logOperationStart(MetaOperation operation, OperationContext ctx) {
        if (operation == null || ctx == null || ctx.runtimeLogSink == null) {
            return;
        }
        ctx.runtimeLogSink.log("[start] " + safe(operation.getId()) + " | " + safe(operation.getName()));
    }

    public static void logOperationComplete(MetaOperation operation,
                                            OperationContext ctx,
                                            boolean success,
                                            long costMs) {
        if (operation == null || ctx == null || ctx.runtimeLogSink == null) {
            return;
        }
        ctx.runtimeLogSink.log("[done] " + safe(operation.getId()) + " | success=" + success
                + (costMs >= 0 ? (" | " + costMs + "ms") : ""));
    }

    private static String format(MetaOperation operation, OperationContext ctx, boolean handlerOk) {
        OperationType type = OperationType.fromCode(operation.getType());
        if (type == OperationType.LOG_OUTPUT) {
            return null;
        }
        Map<String, Object> input = operation.getInputMap();
        Map<String, Object> response = ctx.currentResponse;
        boolean matched = bool(response, MetaOperation.MATCHED, handlerOk);
        String status = handlerOk ? (matched ? "成功" : "未命中") : "失败";

        switch (type == null ? -1 : type.getCode()) {
            case 1:
                return "点击: " + status + " target=" + inputText(input, MetaOperation.CLICK_TARGET)
                        + appendIfPresent(" mode=", inputText(input, MetaOperation.CLICK_EXECUTION_MODE))
                        + appendIfPresent(" result=", valueText(response, MetaOperation.RESULT));
            case 2:
                return "延时: 等待 " + formatMs(number(input, MetaOperation.SLEEP_DURATION,
                        number(response, MetaOperation.SLEEP_DURATION, 0L))) + " 结束";
            case 3:
                return "截图区域: " + status
                        + appendIfPresent(" bbox=", firstValue(response, MetaOperation.BBOX, MetaOperation.RESULT))
                        + appendIfPresent(" var=", inputText(input, MetaOperation.CROP_RESULT_VAR))
                        + appendIfPresent(" format=", inputText(input, MetaOperation.CROP_FORMAT));
            case 4:
                return "加载资源: " + status + appendIfPresent(" result=", valueText(response, MetaOperation.RESULT));
            case 5:
                return "手势: " + status
                        + appendIfPresent(" template=", inputText(input, MetaOperation.GESTURE_TEMPLATE_ID))
                        + appendIfPresent(" steps=", pipelineSize(input));
            case 6:
                return "模板匹配: " + (matched ? "命中" : "未命中")
                        + appendIfPresent(" template=", firstValue(response, MetaOperation.RESULT))
                        + appendIfPresent(" bbox=", valueText(response, MetaOperation.BBOX))
                        + appendIfPresent(" threshold=", inputText(input, MetaOperation.MATCHSIMILARITY))
                        + appendIfPresent(" reason=", valueText(response, "reason"));
            case 7:
                return "图集匹配: " + (matched ? "命中" : "未命中")
                        + appendIfPresent(" template=", firstValue(response, MetaOperation.RESULT))
                        + appendIfPresent(" bbox=", valueText(response, MetaOperation.BBOX))
                        + appendIfPresent(" reason=", valueText(response, "reason"));
            case 8:
                if (bool(response, "__RETURN_FROM_SUBTASK__", false)) {
                    return "跳转任务: 子任务返回，继续原任务";
                }
                return "跳转任务: targetTask=" + inputText(input, MetaOperation.TARGET_TASK_ID)
                        + appendIfPresent(" targetOp=", inputText(input, MetaOperation.TARGET_OPERATION_ID));
            case 11:
                return "JS变量脚本: " + status + appendIfPresent(" result=", valueText(response, MetaOperation.RESULT));
            case 12:
                return "变量运算: " + status
                        + appendIfPresent(" var=", inputText(input, MetaOperation.VAR_NAME))
                        + appendIfPresent(" result=", valueText(response, MetaOperation.RESULT));
            case 13:
                return "模板变量: " + status
                        + appendIfPresent(" var=", inputText(input, MetaOperation.VAR_NAME))
                        + appendIfPresent(" value=", valueText(response, MetaOperation.RESULT));
            case 14:
                return "启动应用: " + status
                        + appendIfPresent(" app=", inputText(input, MetaOperation.APP_LABEL))
                        + appendIfPresent(" package=", inputText(input, MetaOperation.APP_PACKAGE));
            case 15:
                return "分支判断: " + status
                        + appendIfPresent(" var=", inputText(input, MetaOperation.SWITCH_VAR_NAME))
                        + appendIfPresent(" value=", valueText(response, MetaOperation.RESULT));
            case 16:
                return "循环判断: " + status
                        + appendIfPresent(" var=", inputText(input, MetaOperation.LOOP_CONDITION_VAR))
                        + appendIfPresent(" value=", valueText(response, MetaOperation.RESULT));
            case 32:
                String repeatMode = inputText(input, MetaOperation.REPEAT_MODE);
                if (TextUtils.isEmpty(repeatMode)) {
                    repeatMode = bool(input, MetaOperation.REPEAT_INFINITE, false)
                            ? MetaOperation.REPEAT_MODE_INFINITE : MetaOperation.REPEAT_MODE_COUNT;
                }
                String repeatDetail = MetaOperation.REPEAT_MODE_INFINITE.equals(repeatMode)
                        ? "无限循环"
                        : MetaOperation.REPEAT_MODE_EXPRESSION.equals(repeatMode)
                        ? "表达式=" + inputText(input, MetaOperation.REPEAT_EXPRESSION)
                        : "共 " + inputText(input, MetaOperation.REPEAT_COUNT) + " 轮";
                return "循环执行: 从 " + inputText(input, MetaOperation.REPEAT_START_OPERATION_ID)
                        + " 开始，" + repeatDetail
                        + appendIfPresent(" 每轮处理=", inputText(input,
                        MetaOperation.REPEAT_NEXT_ROUND_EXPRESSION))
                        + appendIfPresent(" next=", inputText(input, MetaOperation.NEXT_OPERATION_ID));
            case 17:
                return "返回键: 已触发";
            case 18:
                return "颜色匹配: " + (matched ? "命中" : "未命中")
                        + appendIfPresent(" point=", firstValue(response, MetaOperation.CLICK_TARGET, MetaOperation.RESULT))
                        + appendIfPresent(" reason=", valueText(response, "reason"));
            case 19:
                return "找色: " + (matched ? "命中" : "未命中")
                        + appendIfPresent(" bbox=", valueText(response, MetaOperation.BBOX))
                        + appendIfPresent(" pixels=", valueText(response, "matchedPixels"));
            case 20:
                return "HTTP请求: " + status
                        + appendIfPresent(" method=", inputText(input, MetaOperation.HTTP_METHOD))
                        + appendIfPresent(" url=", trimLong(inputText(input, MetaOperation.HTTP_URL), 80))
                        + appendIfPresent(" status=", valueText(response, "http_status_code"))
                        + appendIfPresent(" body=", trimLong(valueText(response, "http_body"), 80));
            case 21:
                return "动态延时: 等待 " + formatMs(number(response, MetaOperation.SLEEP_DURATION,
                        number(input, MetaOperation.SLEEP_DURATION, 0L))) + " 结束"
                        + appendIfPresent(" var=", inputText(input, MetaOperation.DYNAMIC_DELAY_VAR_NAME));
            case 22:
                return "采集倍率: " + status
                        + appendScaleChange(response)
                        + appendIfPresent(" dir=", valueText(response, "scaleDirName"));
            case 23:
                return "关闭应用: " + status
                        + appendIfPresent(" app=", inputText(input, MetaOperation.APP_LABEL))
                        + appendIfPresent(" package=", inputText(input, MetaOperation.APP_PACKAGE));
            case 24:
                return "无障碍节点: " + status
                        + appendIfPresent(" find=", inputText(input, MetaOperation.A11Y_FIND_MODE) + ":" + inputText(input, MetaOperation.A11Y_FIND_VALUE))
                        + appendIfPresent(" action=", inputText(input, MetaOperation.A11Y_ACTION))
                        + appendIfPresent(" result=", trimLong(valueText(response, "a11y_result_text"), 80))
                        + appendIfPresent(" reason=", valueText(response, "a11y_fail_reason"));
            case 25:
                return "多次尝试: " + status
                        + appendIfPresent(" attempts=", valueText(response, "MTRY_ATTEMPTS_USED"))
                        + appendIfPresent(" wrapped=", inputText(input, MetaOperation.MTRY_WRAPPED_OPERATION_ID))
                        + appendIfPresent(" error=", valueText(response, "MTRY_LAST_ERROR"));
            case 26:
                return "OCR识别: " + (matched ? "命中" : "未命中")
                        + appendIfPresent(" text=", quote(trimLong(firstValue(response, MetaOperation.OCR_TEXT, MetaOperation.RESULT), 80)))
                        + appendIfPresent(" confidence=", valueText(response, MetaOperation.OCR_CONFIDENCE))
                        + appendIfPresent(" bbox=", valueText(response, MetaOperation.BBOX))
                        + appendIfPresent(" reason=", valueText(response, "reason"));
            case 27:
                return "AI目标检测: " + (matched ? "命中" : "未命中")
                        + appendIfPresent(" label=", firstValue(response, MetaOperation.AI_LABEL, MetaOperation.RESULT))
                        + appendIfPresent(" confidence=", percentText(response, MetaOperation.AI_CONFIDENCE))
                        + appendIfPresent(" bbox=", valueText(response, MetaOperation.BBOX))
                        + appendIfPresent(" count=", collectionSize(response, MetaOperation.AI_DETECTIONS))
                        + appendIfPresent(" reason=", valueText(response, "reason"));
            case 28:
                return "播放音频: " + status
                        + appendIfPresent(" file=", trimLong(inputText(input, MetaOperation.AUDIO_FILE_PATH), 80));
            case 29:
                return "修改系统参数: " + status
                        + appendIfPresent(" key=", inputText(input, MetaOperation.SYS_PARAM_KEY))
                        + appendIfPresent(" value=", inputText(input, MetaOperation.SYS_PARAM_VALUE));
            default:
                String display = type == null ? ("未知节点 type=" + operation.getType()) : type.getDisplayName();
                return display + ": " + status
                        + appendIfPresent(" result=", valueText(response, MetaOperation.RESULT))
                        + appendIfPresent(" reason=", firstValue(response, "reason", "error", "message"));
        }
    }

    private static String appendIfPresent(String label, String value) {
        return TextUtils.isEmpty(value) ? "" : label + value;
    }

    private static String appendScaleChange(Map<String, Object> response) {
        String prev = valueText(response, "prevScale");
        String next = valueText(response, "newScale");
        if (TextUtils.isEmpty(prev) && TextUtils.isEmpty(next)) {
            return "";
        }
        return " " + (TextUtils.isEmpty(prev) ? "?" : prev) + " -> " + (TextUtils.isEmpty(next) ? "?" : next);
    }

    private static String inputText(Map<String, Object> map, String key) {
        return valueText(map, key);
    }

    private static String valueText(Map<String, Object> map, String key) {
        if (map == null || key == null || !map.containsKey(key)) {
            return "";
        }
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstValue(Map<String, Object> map, String... keys) {
        if (keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = valueText(map, key);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return fallback;
    }

    private static long number(Map<String, Object> map, String key, long fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static String formatMs(long ms) {
        if (ms >= 1000L) {
            return String.format(Locale.US, "%.2fs", ms / 1000f);
        }
        return ms + "ms";
    }

    private static String pipelineSize(Map<String, Object> input) {
        Object pipeline = input == null ? null : input.get(MetaOperation.GESTURE_PIPELINE);
        if (pipeline instanceof Collection) {
            return String.valueOf(((Collection<?>) pipeline).size());
        }
        if (pipeline instanceof List) {
            return String.valueOf(((List<?>) pipeline).size());
        }
        return "";
    }

    private static String collectionSize(Map<String, Object> response, String key) {
        Object value = response == null ? null : response.get(key);
        if (value instanceof Collection) {
            return String.valueOf(((Collection<?>) value).size());
        }
        return "";
    }

    private static String percentText(Map<String, Object> response, String key) {
        Object value = response == null ? null : response.get(key);
        if (value instanceof Number) {
            double v = ((Number) value).doubleValue();
            if (v <= 1.0d) {
                return String.format(Locale.US, "%.1f%%", v * 100d);
            }
            return String.format(Locale.US, "%.1f", v);
        }
        return value == null ? "" : String.valueOf(value);
    }

    private static String trimLong(String text, int max) {
        if (TextUtils.isEmpty(text) || text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "...";
    }

    private static String quote(String text) {
        return TextUtils.isEmpty(text) ? "" : "\"" + text + "\"";
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
