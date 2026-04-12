package com.auto.master.configui;

import android.text.TextUtils;

import com.auto.master.utils.UUIDGenerator;

import java.util.ArrayList;
import java.util.List;

public class ConfigUiComponent {
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_SWITCH = "switch";
    public static final String TYPE_SELECT = "select";
    public static final String TYPE_ARRAY = "array";
    public static final String TYPE_TITLE = "title";
    public static final int SPAN_HALF = 1;
    public static final int SPAN_FULL = 2;

    public String id;
    public String type;
    public String label;
    public String fieldKey;
    public String placeholder;
    public String defaultValue;
    public String helperText;
    public boolean required;
    public int spanSize;
    public int xDp;
    public int yDp;
    public int widthDp;
    public int heightDp;
    public int scalePercent;
    public String switchOnColor;
    public String switchOffColor;
    public String switchThumbColor;
    public List<ConfigUiOption> options = new ArrayList<>();

    public ConfigUiComponent() {}

    public void ensureDefaults() {
        if (TextUtils.isEmpty(id)) {
            id = UUIDGenerator.prefixedUUID("cfgui_cmp");
        }
        if (TextUtils.isEmpty(type)) {
            type = TYPE_TEXT;
        }
        if (options == null) {
            options = new ArrayList<>();
        }
        if (label == null) {
            label = "";
        }
        if (fieldKey == null) {
            fieldKey = "";
        }
        if (placeholder == null) {
            placeholder = "";
        }
        if (defaultValue == null) {
            defaultValue = "";
        }
        if (helperText == null) {
            helperText = "";
        }
        if (switchOnColor == null) {
            switchOnColor = "";
        }
        if (switchOffColor == null) {
            switchOffColor = "";
        }
        if (switchThumbColor == null) {
            switchThumbColor = "";
        }
        if (TYPE_TITLE.equals(type)) {
            spanSize = SPAN_FULL;
        } else if (spanSize != SPAN_HALF && spanSize != SPAN_FULL) {
            spanSize = SPAN_HALF;
        }
        if (widthDp <= 0) {
            widthDp = defaultWidthForType(type);
        }
        if (heightDp <= 0) {
            heightDp = defaultHeightForType(type);
        }
        if (scalePercent <= 0) {
            scalePercent = 100;
        }
        if (TYPE_SWITCH.equals(type)) {
            if (TextUtils.isEmpty(switchOnColor)) {
                switchOnColor = "#16A34A";
            }
            if (TextUtils.isEmpty(switchOffColor)) {
                switchOffColor = "#64748B";
            }
            if (TextUtils.isEmpty(switchThumbColor)) {
                switchThumbColor = "#FDE68A";
            }
        }
        if (xDp < 0) {
            xDp = 0;
        }
        if (yDp < 0) {
            yDp = 0;
        }
    }

    public boolean bindsValue() {
        ensureDefaults();
        return !TYPE_TITLE.equals(type) && !TextUtils.isEmpty(fieldKey);
    }

    public String getDisplayTypeName() {
        ensureDefaults();
        switch (type) {
            case TYPE_NUMBER:
                return "数字输入";
            case TYPE_SWITCH:
                return "开关";
            case TYPE_SELECT:
                return "下拉选择";
            case TYPE_ARRAY:
                return "数组";
            case TYPE_TITLE:
                return "标题";
            case TYPE_TEXT:
            default:
                return "文本输入";
        }
    }

    public String getDisplaySpanName() {
        ensureDefaults();
        return spanSize == SPAN_FULL ? "整行" : "半宽";
    }

    public String getDisplayScaleName() {
        ensureDefaults();
        return scalePercent + "%";
    }

    public static int defaultWidthForType(String type) {
        if (TYPE_TITLE.equals(type)) {
            return 260;
        }
        if (TYPE_SWITCH.equals(type)) {
            return 150;
        }
        if (TYPE_ARRAY.equals(type)) {
            return 220;
        }
        return 170;
    }

    public static int defaultHeightForType(String type) {
        if (TYPE_TITLE.equals(type)) {
            return 56;
        }
        if (TYPE_SWITCH.equals(type)) {
            return 72;
        }
        if (TYPE_ARRAY.equals(type)) {
            return 132;
        }
        return 88;
    }

    public static ConfigUiComponent createPreset(String type, int index) {
        ConfigUiComponent component = new ConfigUiComponent();
        component.type = type;
        component.id = UUIDGenerator.prefixedUUID("cfgui_cmp");
        if (TYPE_NUMBER.equals(type)) {
            component.label = "数字字段 " + index;
            component.fieldKey = "number_" + index;
            component.placeholder = "请输入数字";
            component.spanSize = SPAN_HALF;
        } else if (TYPE_SWITCH.equals(type)) {
            component.label = "开关字段 " + index;
            component.fieldKey = "switch_" + index;
            component.defaultValue = "false";
            component.switchOnColor = "#16A34A";
            component.switchOffColor = "#64748B";
            component.switchThumbColor = "#FDE68A";
            component.spanSize = SPAN_HALF;
        } else if (TYPE_SELECT.equals(type)) {
            component.label = "选择字段 " + index;
            component.fieldKey = "select_" + index;
            component.placeholder = "请选择";
            component.options.add(new ConfigUiOption("选项 A", "A"));
            component.options.add(new ConfigUiOption("选项 B", "B"));
            component.spanSize = SPAN_HALF;
        } else if (TYPE_ARRAY.equals(type)) {
            component.label = "数组字段 " + index;
            component.fieldKey = "array_" + index;
            component.placeholder = "每行一个元素，或直接粘贴 JSON 数组";
            component.helperText = "支持每行一个值，也支持 [\"a\",1,true] 这种 JSON 数组格式";
            component.defaultValue = "[\"item1\",\"item2\"]";
            component.spanSize = SPAN_FULL;
        } else if (TYPE_TITLE.equals(type)) {
            component.label = "分组标题 " + index;
            component.spanSize = SPAN_FULL;
        } else {
            component.label = "文本字段 " + index;
            component.fieldKey = "text_" + index;
            component.placeholder = "请输入内容";
            component.spanSize = SPAN_HALF;
        }
        component.ensureDefaults();
        return component;
    }
}
