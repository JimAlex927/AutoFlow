package com.auto.master.configui;

import android.text.TextUtils;

import com.auto.master.utils.UUIDGenerator;

import java.util.ArrayList;
import java.util.List;

public class ConfigUiComponent {
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_TEXTAREA = "textarea";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_SWITCH = "switch";
    public static final String TYPE_SELECT = "select";
    public static final String TYPE_ARRAY = "array";
    public static final String TYPE_TITLE = "title";
    public static final String DISPLAY_STYLE_AUTO = "auto";
    public static final String DISPLAY_STYLE_DROPDOWN = "dropdown";
    public static final String DISPLAY_STYLE_CHIPS = "chips";
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
    public int maxLines;
    public String accentColor;
    public String displayStyle;
    public String unitSuffix;
    public String numberMin;
    public String numberMax;
    public String numberStep;
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
        if (accentColor == null) {
            accentColor = "";
        }
        if (displayStyle == null) {
            displayStyle = "";
        }
        if (unitSuffix == null) {
            unitSuffix = "";
        }
        if (numberMin == null) {
            numberMin = "";
        }
        if (numberMax == null) {
            numberMax = "";
        }
        if (numberStep == null) {
            numberStep = "";
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
        if (maxLines <= 0) {
            maxLines = defaultMaxLinesForType(type);
        }
        if (TextUtils.isEmpty(accentColor)) {
            accentColor = defaultAccentColorForType(type);
        }
        if (TextUtils.isEmpty(displayStyle)) {
            displayStyle = DISPLAY_STYLE_AUTO;
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
        if (TYPE_NUMBER.equals(type) && TextUtils.isEmpty(numberStep)) {
            numberStep = "1";
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
            case TYPE_TEXTAREA:
                return "长文本";
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

    public String getDisplayBehaviorName() {
        ensureDefaults();
        if (TYPE_SELECT.equals(type)) {
            if (DISPLAY_STYLE_CHIPS.equals(displayStyle)) {
                return "芯片选择";
            }
            if (DISPLAY_STYLE_DROPDOWN.equals(displayStyle)) {
                return "下拉选择";
            }
            return "自动布局";
        }
        if (TYPE_NUMBER.equals(type)) {
            return TextUtils.isEmpty(unitSuffix) ? "步进输入" : ("步进输入 · " + unitSuffix);
        }
        if (TYPE_TEXTAREA.equals(type)) {
            return "多行输入 · " + maxLines + " 行";
        }
        if (TYPE_TEXT.equals(type) && maxLines > 1) {
            return "扩展文本 · " + maxLines + " 行";
        }
        return getDisplaySpanName();
    }

    public static int defaultWidthForType(String type) {
        if (TYPE_TITLE.equals(type)) {
            return 320;
        }
        if (TYPE_SWITCH.equals(type)) {
            return 220;
        }
        if (TYPE_ARRAY.equals(type)) {
            return 300;
        }
        if (TYPE_TEXTAREA.equals(type)) {
            return 300;
        }
        if (TYPE_SELECT.equals(type)) {
            return 240;
        }
        if (TYPE_NUMBER.equals(type)) {
            return 220;
        }
        return 240;
    }

    public static int defaultHeightForType(String type) {
        if (TYPE_TITLE.equals(type)) {
            return 68;
        }
        if (TYPE_SWITCH.equals(type)) {
            return 78;
        }
        if (TYPE_ARRAY.equals(type)) {
            return 150;
        }
        if (TYPE_TEXTAREA.equals(type)) {
            return 138;
        }
        return 92;
    }

    public static int defaultMaxLinesForType(String type) {
        if (TYPE_TEXTAREA.equals(type)) {
            return 5;
        }
        if (TYPE_ARRAY.equals(type)) {
            return 6;
        }
        if (TYPE_TITLE.equals(type)) {
            return 2;
        }
        return 1;
    }

    public static String defaultAccentColorForType(String type) {
        if (TYPE_TEXTAREA.equals(type)) {
            return "#0F766E";
        }
        if (TYPE_NUMBER.equals(type)) {
            return "#C2410C";
        }
        if (TYPE_SWITCH.equals(type)) {
            return "#15803D";
        }
        if (TYPE_SELECT.equals(type)) {
            return "#1D4ED8";
        }
        if (TYPE_ARRAY.equals(type)) {
            return "#0F766E";
        }
        if (TYPE_TITLE.equals(type)) {
            return "#0F172A";
        }
        return "#2563EB";
    }

    public static ConfigUiComponent createPreset(String type, int index) {
        ConfigUiComponent component = new ConfigUiComponent();
        component.type = type;
        component.id = UUIDGenerator.prefixedUUID("cfgui_cmp");
        component.accentColor = defaultAccentColorForType(type);
        component.displayStyle = DISPLAY_STYLE_AUTO;
        if (TYPE_NUMBER.equals(type)) {
            component.label = "数字字段 " + index;
            component.fieldKey = "number_" + index;
            component.placeholder = "请输入数字";
            component.helperText = "支持小数、负数，也可以直接用步进按钮微调。";
            component.numberStep = "1";
            component.spanSize = SPAN_HALF;
        } else if (TYPE_TEXTAREA.equals(type)) {
            component.label = "长文本字段 " + index;
            component.fieldKey = "textarea_" + index;
            component.placeholder = "请输入多行说明、备注或模板片段";
            component.helperText = "适合备注、描述、代码片段、命令模板等较长内容。";
            component.maxLines = 5;
            component.spanSize = SPAN_FULL;
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
            component.helperText = "选项较少时可切成芯片式选择，更直观。";
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
            component.helperText = "用于把配置拆成更清晰的区块。";
            component.spanSize = SPAN_FULL;
        } else {
            component.label = "文本字段 " + index;
            component.fieldKey = "text_" + index;
            component.placeholder = "请输入内容";
            component.helperText = "适合账号、路径、标识符、短文本等输入。";
            component.spanSize = SPAN_HALF;
        }
        component.ensureDefaults();
        return component;
    }
}
