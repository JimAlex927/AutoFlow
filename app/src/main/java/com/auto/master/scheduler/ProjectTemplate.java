package com.auto.master.scheduler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Built-in project templates for common automation scenarios.
 * OCR-related presets are intentionally removed for now.
 */
public class ProjectTemplate {

    public final String id;
    public final String name;
    public final String description;
    public final String emoji;
    public final String category;

    private ProjectTemplate(String id, String name, String description, String emoji, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.emoji = emoji;
        this.category = category;
    }

    public static List<ProjectTemplate> getBuiltinTemplates() {
        List<ProjectTemplate> list = new ArrayList<>();
        list.add(new ProjectTemplate(
                "sign_in",
                "签到流程",
                "启动应用、等待、截图、模板匹配并点击签到按钮",
                "OK",
                "日常"));
        list.add(new ProjectTemplate(
                "flash_sale",
                "抢购点击",
                "延迟等待、截图、匹配目标按钮后连续点击",
                "FAST",
                "效率"));
        list.add(new ProjectTemplate(
                "screen_monitor",
                "屏幕监控",
                "截图后执行模板匹配，再根据条件做分支处理",
                "MON",
                "监控"));
        list.add(new ProjectTemplate(
                "auto_restart",
                "自动重启",
                "截图检测异常状态，根据分支逻辑重启应用并回跳任务",
                "RST",
                "恢复"));
        list.add(new ProjectTemplate(
                "batch_click",
                "批量点击",
                "按顺序执行延迟与多个点击节点",
                "SEQ",
                "日常"));
        return list;
    }

    /**
     * Returns a JSON array string of MetaOperation-compatible operation objects.
     * These can be written directly to operations.json in a new task folder.
     */
    public String buildOperationsJson() {
        List<Map<String, Object>> ops = new ArrayList<>();
        switch (id) {
            case "sign_in":
                ops.add(op("启动应用", 14));
                ops.add(op("等待界面加载", 2));
                ops.add(op("截图区域", 3));
                ops.add(op("匹配签到按钮", 6));
                ops.add(op("点击签到", 1));
                break;
            case "flash_sale":
                ops.add(op("等待开抢", 2));
                ops.add(op("截图区域", 3));
                ops.add(op("匹配购买按钮", 6));
                ops.add(op("点击购买", 1));
                ops.add(op("等待确认", 2));
                ops.add(op("点击确认", 1));
                break;
            case "screen_monitor":
                ops.add(op("截图监控区域", 3));
                ops.add(op("匹配监控模板", 6));
                ops.add(op("条件分支", 10));
                ops.add(op("点击告警处理", 1));
                break;
            case "auto_restart":
                ops.add(op("截图异常区域", 3));
                ops.add(op("匹配异常模板", 6));
                ops.add(op("条件分支", 10));
                ops.add(op("重新启动应用", 14));
                ops.add(op("等待恢复", 2));
                ops.add(op("跳转回主流程", 8));
                break;
            case "batch_click":
                ops.add(op("延迟", 2));
                ops.add(op("点击1", 1));
                ops.add(op("延迟", 2));
                ops.add(op("点击2", 1));
                ops.add(op("延迟", 2));
                ops.add(op("点击3", 1));
                break;
            default:
                break;
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ops.size(); i++) {
            sb.append(toJson(ops.get(i)));
            if (i < ops.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static Map<String, Object> op(String name, int typeCode) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", shortUuid());
        item.put("name", name);
        item.put("type", typeCode);
        item.put("inputMap", new LinkedHashMap<>());
        return item;
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"")
                        .append(((String) value).replace("\\", "\\\\").replace("\"", "\\\""))
                        .append("\"");
            } else if (value instanceof Integer) {
                sb.append(value);
            } else if (value instanceof Map) {
                sb.append("{}");
            } else {
                sb.append("null");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
