package com.auto.master.Task.Operation;

/**
 * All supported operation types in AtommMaster.
 * Replaces legacy magic integer constants with a typed enum.
 */
public enum OperationType {

    CLICK(1, "点击", "click"),
    DELAY(2, "延时", "delay"),
    CROP_REGION(3, "截图区域", "crop"),
    LOAD_IMG_TO_MAT(4, "加载资源", "load"),
    GESTURE(5, "手势", "gesture"),
    MATCH_TEMPLATE(6, "模板匹配", "match"),
    MATCH_MAP_TEMPLATE(7, "图集匹配", "matchmap"),
    JUMP_TASK(8, "跳转任务", "jump"),
    OCR(9, "文字识别", "ocr"),
    VARIABLE_SCRIPT(11, "脚本变量", "script"),
    VARIABLE_MATH(12, "数学运算", "math"),
    VARIABLE_TEMPLATE(13, "模板变量", "template"),
    APP_LAUNCH(14, "启动应用", "app"),
    SWITCH_BRANCH(15, "多路分支", "switch"),
    LOOP(16, "二分路", "loop"),
    BACK_KEY(17, "返回按键", "back"),
    COLOR_MATCH(18, "颜色匹配", "color"),
    COLOR_SEARCH(19, "区域找色", "color_search"),
    HTTP_REQUEST(20, "HTTP请求", "http_request"),
    DYNAMIC_DELAY(21, "动态延时", "dynamic_delay"),
    SET_CAPTURE_SCALE(22, "采集倍率", "set_scale");

    private final int code;
    private final String displayName;
    private final String iconKey;
    private static final java.util.Map<Integer, OperationType> LOOKUP = new java.util.HashMap<>();

    static {
        for (OperationType type : values()) {
            LOOKUP.put(type.code, type);
        }
    }

    OperationType(int code, String displayName, String iconKey) {
        this.code = code;
        this.displayName = displayName;
        this.iconKey = iconKey;
    }

    public int getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconKey() {
        return iconKey;
    }

    public static OperationType fromCode(int code) {
        return LOOKUP.get(code);
    }

    public static OperationType fromCode(Integer code) {
        return code == null ? null : fromCode(code.intValue());
    }
}
