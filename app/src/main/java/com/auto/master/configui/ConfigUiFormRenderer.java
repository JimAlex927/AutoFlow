package com.auto.master.configui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.auto.master.R;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ConfigUiFormRenderer {
    private ConfigUiFormRenderer() {}

    private static final int DEFAULT_SWITCH_ON_COLOR = 0xFF16A34A;
    private static final int DEFAULT_SWITCH_OFF_COLOR = 0xFF64748B;
    private static final int DEFAULT_SWITCH_THUMB_COLOR = 0xFFFDE68A;
    private static final ArgbEvaluator ARGB_EVALUATOR = new ArgbEvaluator();

    public interface FieldBinding {
        String getKey();
        String getValue();
    }

    public static final class FormSession {
        private final View rootView;
        private final LinkedHashMap<String, FieldBinding> bindings;

        FormSession(View rootView, List<FieldBinding> bindings) {
            this.rootView = rootView;
            this.bindings = new LinkedHashMap<>();
            if (bindings != null) {
                for (FieldBinding binding : bindings) {
                    if (binding == null || TextUtils.isEmpty(binding.getKey())) {
                        continue;
                    }
                    this.bindings.put(binding.getKey(), binding);
                }
            }
        }

        public View getRootView() {
            return rootView;
        }

        public LinkedHashMap<String, String> collectValues() {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            for (FieldBinding binding : bindings.values()) {
                if (binding == null) {
                    continue;
                }
                String key = binding.getKey();
                if (TextUtils.isEmpty(key)) {
                    continue;
                }
                String value = binding.getValue();
                if (!TextUtils.isEmpty(value)) {
                    values.put(key, value);
                }
            }
            return values;
        }

        public List<String> findMissingRequiredFields(@Nullable ConfigUiSchema schema) {
            List<String> missing = new ArrayList<>();
            if (schema == null) {
                return missing;
            }
            schema.ensureDefaults();
            for (ConfigUiPage page : schema.pages) {
                if (page == null || page.components == null) {
                    continue;
                }
                for (ConfigUiComponent component : page.components) {
                    if (component == null) {
                        continue;
                    }
                    component.ensureDefaults();
                    if (!component.required || !component.bindsValue()) {
                        continue;
                    }
                    FieldBinding binding = bindings.get(component.fieldKey);
                    String value = binding == null ? "" : binding.getValue();
                    if (TextUtils.isEmpty(value == null ? "" : value.trim())) {
                        missing.add(TextUtils.isEmpty(component.label) ? component.fieldKey : component.label);
                    }
                }
            }
            return missing;
        }
    }

    public static FormSession create(Context context,
                                     ConfigUiSchema schema,
                                     Map<String, String> initialValues) {
        schema.ensureDefaults();
        Context themedContext = new ContextThemeWrapper(context, R.style.Theme_AtomMaster);
        List<FieldBinding> bindings = new ArrayList<>();
        LinearLayout root = new LinearLayout(themedContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView tabScroll = new HorizontalScrollView(themedContext);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(themedContext);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(0, 0, 0, dp(themedContext, 8));
        tabScroll.addView(tabRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(tabScroll, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout pageContainer = new FrameLayout(themedContext);
        pageContainer.setBackground(createPageHostBackground());
        LinearLayout.LayoutParams pageContainerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(themedContext, 340));
        root.addView(pageContainer, pageContainerLp);

        List<View> pageViews = new ArrayList<>();
        List<TextView> tabs = new ArrayList<>();
        for (int i = 0; i < schema.pages.size(); i++) {
            ConfigUiPage page = schema.pages.get(i);
            pageViews.add(buildPageView(themedContext, page, initialValues, bindings));
            TextView tab = buildTabView(themedContext, page.title, i == 0);
            final int index = i;
            tab.setOnClickListener(v -> showPage(pageContainer, pageViews, tabs, index));
            tabs.add(tab);
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                tabLp.leftMargin = dp(themedContext, 8);
            }
            tabRow.addView(tab, tabLp);
        }

        showPage(pageContainer, pageViews, tabs, 0);
        return new FormSession(root, bindings);
    }

    private static View buildPageView(Context context,
                                      ConfigUiPage page,
                                      Map<String, String> initialValues,
                                      List<FieldBinding> bindings) {
        page.ensureDefaults();
        float pageScale = resolvePageScale(page);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        FrameLayout container = new FrameLayout(context);
        container.setPadding(0, 0, 0, dp(context, 8));
        container.setClipChildren(false);
        container.setClipToPadding(false);
        scrollView.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (page.components == null || page.components.isEmpty()) {
            TextView emptyView = new TextView(context);
            emptyView.setText("这个页面还没有组件，先去设计器里添加。");
            emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * pageScale);
            emptyView.setTextColor(0xFF7B8794);
            emptyView.setGravity(android.view.Gravity.CENTER);
            container.addView(emptyView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    scaleDp(context, Math.max(240, page.canvasHeightDp), pageScale)));
            return scrollView;
        }

        int canvasHeightDp = Math.max(240, page.canvasHeightDp);
        for (ConfigUiComponent component : page.components) {
            if (component == null) {
                continue;
            }
            component.ensureDefaults();
            canvasHeightDp = Math.max(canvasHeightDp, component.yDp + component.heightDp + 24);
        }
        container.setMinimumHeight(scaleDp(context, canvasHeightDp, pageScale));

        for (ConfigUiComponent component : page.components) {
            if (component == null) {
                continue;
            }
            component.ensureDefaults();
            container.addView(buildComponentView(context, component, initialValues, bindings, pageScale));
        }
        return scrollView;
    }

    private static View buildComponentView(Context context,
                                           ConfigUiComponent component,
                                           Map<String, String> initialValues,
                                           List<FieldBinding> bindings,
                                           float pageScale) {
        float componentScale = resolveComponentScale(component);
        float contentScale = pageScale * componentScale;
        int accentColor = resolveAccentColor(component);
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackground(ConfigUiComponent.TYPE_TITLE.equals(component.type)
                ? createTitleCardBackground(accentColor)
                : createCardBackground(accentColor));
        int innerPad = scaleDp(context, 12, contentScale);
        wrapper.setPadding(innerPad, innerPad, innerPad, innerPad);
        FrameLayout.LayoutParams wrapperLp = new FrameLayout.LayoutParams(
                scaleDp(context, component.widthDp, contentScale),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapperLp.leftMargin = scaleDp(context, component.xDp, pageScale);
        wrapperLp.topMargin = scaleDp(context, component.yDp, pageScale);
        wrapper.setLayoutParams(wrapperLp);
        wrapper.setMinimumHeight(scaleDp(context, component.heightDp, contentScale));
        wrapper.setClickable(false);
        wrapper.setFocusable(false);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        wrapper.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(context);
        label.setText(TextUtils.isEmpty(component.label) ? component.getDisplayTypeName() : component.label);
        label.setTextColor(0xFF243244);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * contentScale);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(label, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView typeChip = buildChipView(context, component.getDisplayTypeName(), accentColor, true, contentScale);
        titleRow.addView(typeChip);
        if (component.required) {
            LinearLayout.LayoutParams requiredLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            requiredLp.leftMargin = scaleDp(context, 6, contentScale);
            titleRow.addView(buildChipView(context, "必填", 0xFFE05D4E, false, contentScale), requiredLp);
        }

        if (!TextUtils.isEmpty(component.fieldKey)) {
            TextView keyMeta = new TextView(context);
            keyMeta.setText("变量键: " + component.fieldKey);
            keyMeta.setTextColor(0xFF6B7B8C);
            keyMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * contentScale);
            LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            keyLp.topMargin = scaleDp(context, 4, contentScale);
            wrapper.addView(keyMeta, keyLp);
        }

        String initialValue = initialValues == null ? null : initialValues.get(component.fieldKey);
        if (TextUtils.isEmpty(initialValue)) {
            initialValue = component.defaultValue;
        }

        if (ConfigUiComponent.TYPE_TITLE.equals(component.type)) {
            TextView badge = buildChipView(context, "SECTION", accentColor, true, contentScale);
            wrapper.removeAllViews();
            wrapper.addView(badge);
            TextView title = new TextView(context);
            title.setText(component.label);
            title.setTextColor(0xFF172433);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f * contentScale);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.topMargin = scaleDp(context, 10, contentScale);
            wrapper.addView(title, titleLp);
            if (!TextUtils.isEmpty(component.helperText)) {
                TextView helper = buildHelperView(context, component.helperText);
                helper.setPadding(0, scaleDp(context, 6, contentScale), 0, 0);
                helper.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * contentScale);
                wrapper.addView(helper);
            }
            return wrapper;
        }

        if (ConfigUiComponent.TYPE_SWITCH.equals(component.type)) {
            LinearLayout switchRow = new LinearLayout(context);
            switchRow.setOrientation(LinearLayout.HORIZONTAL);
            switchRow.setGravity(Gravity.CENTER_VERTICAL);
            int rowPadH = scaleDp(context, 12, contentScale);
            int rowPadV = scaleDp(context, 8, contentScale);
            switchRow.setPadding(rowPadH, rowPadV, rowPadH, rowPadV);
            wrapper.addView(switchRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView stateView = new TextView(context);
            stateView.setTextColor(0xFF526273);
            stateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * contentScale);
            LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            switchRow.addView(stateView, stateLp);

            boolean[] checkedHolder = new boolean[] { "true".equalsIgnoreCase(initialValue) };
            FrameLayout toggleView = buildToggleView(context, contentScale, component);
            switchRow.addView(toggleView);

            Runnable syncStateImmediate = () -> applySwitchState(
                    switchRow, stateView, toggleView, component, checkedHolder[0], contentScale, false);
            Runnable toggleAction = () -> {
                checkedHolder[0] = !checkedHolder[0];
                applySwitchState(switchRow, stateView, toggleView, component, checkedHolder[0], contentScale, true);
            };
            syncStateImmediate.run();
            toggleView.setClickable(false);
            toggleView.setFocusable(false);
            installFastToggleTouchHandler(switchRow, toggleAction);
            final String key = component.fieldKey;
            bindings.add(new FieldBinding() {
                @Override
                public String getKey() {
                    return key;
                }

                @Override
                public String getValue() {
                    return String.valueOf(checkedHolder[0]);
                }
            });
        } else if (ConfigUiComponent.TYPE_TEXTAREA.equals(component.type)) {
            EditText input = new EditText(context);
            input.setBackground(createInputBackground(accentColor));
            input.setPadding(scaleDp(context, 12, contentScale), scaleDp(context, 10, contentScale),
                    scaleDp(context, 12, contentScale), scaleDp(context, 10, contentScale));
            input.setTextColor(0xFF243244);
            input.setHint(TextUtils.isEmpty(component.placeholder) ? "请输入多行内容" : component.placeholder);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * contentScale);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setFocusable(true);
            input.setFocusableInTouchMode(true);
            input.setClickable(true);
            input.setCursorVisible(true);
            input.setLongClickable(true);
            input.setHorizontallyScrolling(false);
            input.setMinLines(Math.max(3, component.maxLines));
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            input.setText(initialValue == null ? "" : initialValue);
            wrapper.addView(input);
            installScrollableChildTouchBridge(input);
            final String key = component.fieldKey;
            bindings.add(new FieldBinding() {
                @Override
                public String getKey() {
                    return key;
                }

                @Override
                public String getValue() {
                    return input.getText() == null ? "" : input.getText().toString().trim();
                }
            });
        } else if (ConfigUiComponent.TYPE_ARRAY.equals(component.type)) {
            wrapper.addView(buildSubtleCaption(context, "支持每行一个值，也支持直接粘贴 JSON 数组。", contentScale));
            EditText arrayInput = new EditText(context);
            arrayInput.setBackground(createInputBackground(accentColor));
            arrayInput.setPadding(scaleDp(context, 12, contentScale), scaleDp(context, 10, contentScale),
                    scaleDp(context, 12, contentScale), scaleDp(context, 10, contentScale));
            arrayInput.setTextColor(0xFF243244);
            arrayInput.setHint(TextUtils.isEmpty(component.placeholder)
                    ? "每行一个元素，或直接粘贴 JSON 数组"
                    : component.placeholder);
            arrayInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * contentScale);
            arrayInput.setGravity(Gravity.TOP | Gravity.START);
            arrayInput.setFocusable(true);
            arrayInput.setFocusableInTouchMode(true);
            arrayInput.setClickable(true);
            arrayInput.setCursorVisible(true);
            arrayInput.setLongClickable(true);
            arrayInput.setHorizontallyScrolling(false);
            arrayInput.setMinLines(Math.max(4, component.maxLines));
            arrayInput.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            arrayInput.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            arrayInput.setText(formatArrayEditorText(initialValue));
            wrapper.addView(arrayInput);
            installScrollableChildTouchBridge(arrayInput);
            final String key = component.fieldKey;
            bindings.add(new FieldBinding() {
                @Override
                public String getKey() {
                    return key;
                }

                @Override
                public String getValue() {
                    return encodeArrayEditorValue(arrayInput.getText());
                }
            });
        } else if (ConfigUiComponent.TYPE_SELECT.equals(component.type)) {
            if (shouldRenderSelectAsChips(component)) {
                final String[] selectedValue = { resolveInitialSelectValue(component, initialValue) };
                HorizontalScrollView chipScroll = new HorizontalScrollView(context);
                chipScroll.setHorizontalScrollBarEnabled(false);
                LinearLayout chipRow = new LinearLayout(context);
                chipRow.setOrientation(LinearLayout.HORIZONTAL);
                chipRow.setPadding(0, scaleDp(context, 4, contentScale), 0, 0);
                chipScroll.addView(chipRow, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                List<TextView> chipViews = new ArrayList<>();
                List<String> chipValues = new ArrayList<>();
                if (component.options != null) {
                    for (int i = 0; i < component.options.size(); i++) {
                        ConfigUiOption option = component.options.get(i);
                        if (option == null) {
                            continue;
                        }
                        String optionLabel = TextUtils.isEmpty(option.label) ? option.value : option.label;
                        String optionValue = TextUtils.isEmpty(option.value) ? optionLabel : option.value;
                        TextView chip = buildSelectChip(context, optionLabel, accentColor, contentScale);
                        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        if (i > 0) {
                            chipLp.leftMargin = scaleDp(context, 8, contentScale);
                        }
                        chipRow.addView(chip, chipLp);
                        chipViews.add(chip);
                        chipValues.add(optionValue);
                        chip.setOnClickListener(v -> {
                            selectedValue[0] = optionValue;
                            updateSelectChipStyles(chipViews, chipValues, selectedValue[0], accentColor);
                        });
                    }
                }
                updateSelectChipStyles(chipViews, chipValues, selectedValue[0], accentColor);
                wrapper.addView(chipScroll);
                final String key = component.fieldKey;
                bindings.add(new FieldBinding() {
                    @Override
                    public String getKey() {
                        return key;
                    }

                    @Override
                    public String getValue() {
                        return selectedValue[0] == null ? "" : selectedValue[0];
                    }
                });
                if (!TextUtils.isEmpty(component.helperText)) {
                    TextView helper = buildHelperView(context, component.helperText);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = scaleDp(context, 6, contentScale);
                    helper.setLayoutParams(lp);
                    helper.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * contentScale);
                    wrapper.addView(helper);
                }
                return wrapper;
            }
            android.widget.AutoCompleteTextView selectView =
                    new android.widget.AutoCompleteTextView(context);
            selectView.setBackground(createInputBackground(accentColor));
            selectView.setPadding(scaleDp(context, 12, contentScale), scaleDp(context, 10, contentScale),
                    scaleDp(context, 12, contentScale), scaleDp(context, 10, contentScale));
            selectView.setTextColor(0xFF243244);
            selectView.setHint(TextUtils.isEmpty(component.placeholder) ? "请选择" : component.placeholder);
            selectView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * contentScale);
            selectView.setFocusable(true);
            selectView.setFocusableInTouchMode(true);
            selectView.setClickable(true);
            selectView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            List<String> labels = new ArrayList<>();
            Map<String, String> labelToValue = new LinkedHashMap<>();
            if (component.options != null) {
                for (ConfigUiOption option : component.options) {
                    if (option == null) {
                        continue;
                    }
                    String label_ = TextUtils.isEmpty(option.label) ? option.value : option.label;
                    labels.add(label_);
                    labelToValue.put(label_, TextUtils.isEmpty(option.value) ? label_ : option.value);
                }
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    context, android.R.layout.simple_list_item_1, labels);
            selectView.setAdapter(adapter);
            selectView.setOnClickListener(v -> selectView.showDropDown());
            if (!TextUtils.isEmpty(initialValue)) {
                String matchedLabel = initialValue;
                for (Map.Entry<String, String> entry : labelToValue.entrySet()) {
                    if (TextUtils.equals(entry.getValue(), initialValue)) {
                        matchedLabel = entry.getKey();
                        break;
                    }
                }
                selectView.setText(matchedLabel, false);
            }
            wrapper.addView(selectView);
            installScrollableChildTouchBridge(selectView);
            final String key = component.fieldKey;
            bindings.add(new FieldBinding() {
                @Override
                public String getKey() {
                    return key;
                }

                @Override
                public String getValue() {
                    String raw = selectView.getText() == null ? "" : selectView.getText().toString().trim();
                    if (TextUtils.isEmpty(raw)) {
                        return "";
                    }
                    String mapped = labelToValue.get(raw);
                    return mapped == null ? raw : mapped;
                }
            });
        } else {
            EditText input = new EditText(context);
            input.setBackground(createInputBackground(accentColor));
            input.setPadding(scaleDp(context, 12, contentScale), scaleDp(context, 10, contentScale),
                    scaleDp(context, 12, contentScale), scaleDp(context, 10, contentScale));
            input.setTextColor(0xFF243244);
            input.setHint(TextUtils.isEmpty(component.placeholder) ? "请输入" : component.placeholder);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * contentScale);
            input.setFocusable(true);
            input.setFocusableInTouchMode(true);
            input.setClickable(true);
            input.setCursorVisible(true);
            input.setLongClickable(true);
            input.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            if (ConfigUiComponent.TYPE_NUMBER.equals(component.type)) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
            } else {
                if (component.maxLines > 1) {
                    input.setGravity(Gravity.TOP | Gravity.START);
                    input.setHorizontallyScrolling(false);
                    input.setMinLines(Math.max(2, component.maxLines));
                    input.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                } else {
                    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                }
            }
            input.setText(initialValue == null ? "" : initialValue);
            wrapper.addView(input);
            installScrollableChildTouchBridge(input);
            final String key = component.fieldKey;
            bindings.add(new FieldBinding() {
                @Override
                public String getKey() {
                    return key;
                }

                @Override
                public String getValue() {
                    if (ConfigUiComponent.TYPE_NUMBER.equals(component.type)) {
                        return normalizeNumericValue(
                                input.getText() == null ? "" : input.getText().toString(),
                                parseOptionalDouble(component.numberMin),
                                parseOptionalDouble(component.numberMax));
                    }
                    return input.getText() == null ? "" : input.getText().toString().trim();
                }
            });
        }

        if (!TextUtils.isEmpty(component.helperText)) {
            TextView helper = buildHelperView(context, component.helperText);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = scaleDp(context, 6, contentScale);
            helper.setLayoutParams(lp);
            helper.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * contentScale);
            wrapper.addView(helper);
        }
        return wrapper;
    }

    private static TextView buildHelperView(Context context, String text) {
        TextView helper = new TextView(context);
        helper.setText(text);
        helper.setTextColor(0xFF7B8794);
        helper.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        helper.setLineSpacing(0f, 1.1f);
        return helper;
    }

    private static TextView buildChipView(Context context,
                                          String text,
                                          int accentColor,
                                          boolean filled,
                                          float contentScale) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * contentScale);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setTextColor(filled ? 0xFFFFFFFF : darkenColor(accentColor, 0.12f));
        chip.setPadding(scaleDp(context, 10, contentScale), scaleDp(context, 5, contentScale),
                scaleDp(context, 10, contentScale), scaleDp(context, 5, contentScale));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(scaleDp(context, 999, contentScale));
        bg.setColor(filled ? accentColor : mixColorWithWhite(accentColor, 0.88f));
        bg.setStroke(scaleDp(context, 1, contentScale), mixColorWithWhite(accentColor, filled ? 0.12f : 0.55f));
        chip.setBackground(bg);
        return chip;
    }

    private static TextView buildSubtleCaption(Context context, String text, float contentScale) {
        TextView caption = new TextView(context);
        caption.setText(text);
        caption.setTextColor(0xFF6B7B8C);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f * contentScale);
        caption.setPadding(0, scaleDp(context, 6, contentScale), 0, scaleDp(context, 6, contentScale));
        return caption;
    }

    private static TextView buildSelectChip(Context context,
                                            String text,
                                            int accentColor,
                                            float contentScale) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * contentScale);
        chip.setPadding(scaleDp(context, 14, contentScale), scaleDp(context, 9, contentScale),
                scaleDp(context, 14, contentScale), scaleDp(context, 9, contentScale));
        chip.setMinWidth(scaleDp(context, 64, contentScale));
        chip.setGravity(Gravity.CENTER);
        applySelectChipStyle(chip, false, accentColor);
        return chip;
    }

    private static void updateSelectChipStyles(List<TextView> chips,
                                               List<String> chipValues,
                                               @Nullable String selectedValue,
                                               int accentColor) {
        if (chips == null || chipValues == null) {
            return;
        }
        for (int i = 0; i < chips.size(); i++) {
            TextView chip = chips.get(i);
            String value = i < chipValues.size() ? chipValues.get(i) : "";
            applySelectChipStyle(chip, TextUtils.equals(value, selectedValue), accentColor);
        }
    }

    private static void applySelectChipStyle(TextView chip, boolean selected, int accentColor) {
        if (chip == null) {
            return;
        }
        chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF334155);
        chip.setTypeface(chip.getTypeface(), selected
                ? android.graphics.Typeface.BOLD
                : android.graphics.Typeface.NORMAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(chip.getContext(), 14));
        bg.setColor(selected ? accentColor : 0xFFFFFFFF);
        bg.setStroke(dp(chip.getContext(), 1),
                selected ? darkenColor(accentColor, 0.14f) : mixColorWithWhite(accentColor, 0.64f));
        chip.setBackground(bg);
    }

    private static void showPage(FrameLayout pageContainer,
                                 List<View> pageViews,
                                 List<TextView> tabs,
                                 int index) {
        if (pageContainer == null || pageViews == null || pageViews.isEmpty()) {
            return;
        }
        int safeIndex = Math.max(0, Math.min(index, pageViews.size() - 1));
        pageContainer.removeAllViews();
        pageContainer.addView(pageViews.get(safeIndex), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < tabs.size(); i++) {
            updateTabStyle(tabs.get(i), i == safeIndex);
        }
    }

    private static TextView buildTabView(Context context, String title, boolean selected) {
        TextView tab = new TextView(context);
        tab.setText(TextUtils.isEmpty(title) ? "页面" : title);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tab.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        updateTabStyle(tab, selected);
        return tab;
    }

    private static void updateTabStyle(TextView tab, boolean selected) {
        if (tab == null) {
            return;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(tab.getContext(), 16));
        bg.setColor(selected ? 0xFF3C6DE4 : 0xFFF3F6FA);
        tab.setBackground(bg);
        tab.setTextColor(selected ? 0xFFFFFFFF : 0xFF526273);
    }

    private static boolean shouldRenderSelectAsChips(ConfigUiComponent component) {
        if (component == null || !ConfigUiComponent.TYPE_SELECT.equals(component.type)) {
            return false;
        }
        if (ConfigUiComponent.DISPLAY_STYLE_CHIPS.equals(component.displayStyle)) {
            return true;
        }
        if (ConfigUiComponent.DISPLAY_STYLE_DROPDOWN.equals(component.displayStyle)) {
            return false;
        }
        return component.options != null && component.options.size() > 0 && component.options.size() <= 5;
    }

    private static String resolveInitialSelectValue(ConfigUiComponent component, @Nullable String initialValue) {
        if (component == null || component.options == null || component.options.isEmpty()) {
            return initialValue == null ? "" : initialValue;
        }
        if (!TextUtils.isEmpty(initialValue)) {
            for (ConfigUiOption option : component.options) {
                if (option == null) {
                    continue;
                }
                String optionLabel = TextUtils.isEmpty(option.label) ? option.value : option.label;
                String optionValue = TextUtils.isEmpty(option.value) ? optionLabel : option.value;
                if (TextUtils.equals(optionValue, initialValue) || TextUtils.equals(optionLabel, initialValue)) {
                    return optionValue;
                }
            }
        }
        return "";
    }

    private static int resolveAccentColor(@Nullable ConfigUiComponent component) {
        if (component == null) {
            return 0xFF2563EB;
        }
        return parseColorOrDefault(
                component.accentColor,
                parseColorOrDefault(ConfigUiComponent.defaultAccentColorForType(component.type), 0xFF2563EB));
    }

    @Nullable
    private static Double parseOptionalDouble(@Nullable String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeNumericValue(@Nullable String raw,
                                                @Nullable Double minValue,
                                                @Nullable Double maxValue) {
        Double parsed = parseOptionalDouble(raw);
        if (parsed == null) {
            return "";
        }
        double clamped = parsed;
        if (minValue != null) {
            clamped = Math.max(clamped, minValue);
        }
        if (maxValue != null) {
            clamped = Math.min(clamped, maxValue);
        }
        if (Math.abs(clamped - Math.rint(clamped)) < 0.0000001d) {
            return String.valueOf((long) Math.rint(clamped));
        }
        return BigDecimal.valueOf(clamped).stripTrailingZeros().toPlainString();
    }

    private static GradientDrawable createCardBackground(int accentColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        bg.setColors(new int[] {
                mixColorWithWhite(accentColor, 0.95f),
                0xFFFFFFFF
        });
        bg.setCornerRadius(18f);
        bg.setStroke(1, mixColorWithWhite(accentColor, 0.68f));
        return bg;
    }

    private static GradientDrawable createTitleCardBackground(int accentColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        bg.setColors(new int[] {
                mixColorWithWhite(accentColor, 0.88f),
                mixColorWithWhite(accentColor, 0.96f)
        });
        bg.setCornerRadius(20f);
        bg.setStroke(1, mixColorWithWhite(accentColor, 0.58f));
        return bg;
    }

    private static GradientDrawable createPageHostBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        bg.setColors(new int[] {0xFFF8FBFF, 0xFFF2F6FB});
        bg.setCornerRadius(20f);
        bg.setStroke(1, 0xFFD9E4EF);
        return bg;
    }

    private static GradientDrawable createInputBackground(int accentColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16f);
        bg.setColor(0xFFFFFFFF);
        bg.setStroke(1, mixColorWithWhite(accentColor, 0.70f));
        return bg;
    }

    private static GradientDrawable createSwitchRowBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18f);
        return bg;
    }

    private static GradientDrawable createToggleThumbBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        return bg;
    }

    private static GradientDrawable createToggleTrackBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(999f);
        return bg;
    }

    private static FrameLayout buildToggleView(Context context,
                                               float contentScale,
                                               ConfigUiComponent component) {
        FrameLayout track = new FrameLayout(context);
        int trackWidth = scaleDp(context, 40, contentScale);
        int trackHeight = scaleDp(context, 22, contentScale);
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(trackWidth, trackHeight);
        track.setLayoutParams(trackLp);
        track.setBackground(createToggleTrackBackground());

        View thumb = new View(context);
        int thumbSize = scaleDp(context, 14, contentScale);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(thumbSize, thumbSize);
        thumbLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        thumb.setBackground(createToggleThumbBackground());
        track.addView(thumb, thumbLp);
        int thumbTravelPx = Math.max(0, trackWidth - thumbSize - scaleDp(context, 8, contentScale));
        track.setTag(new ToggleVisualHolder(
                thumb,
                (GradientDrawable) track.getBackground(),
                (GradientDrawable) thumb.getBackground(),
                thumbTravelPx));
        applySwitchState(null, null, track, component,
                "true".equalsIgnoreCase(component == null ? null : component.defaultValue),
                contentScale, false);
        return track;
    }

    private static void applySwitchState(LinearLayout switchRow,
                                         TextView stateView,
                                         FrameLayout toggleView,
                                         ConfigUiComponent component,
                                         boolean checked,
                                         float contentScale,
                                         boolean animate) {
        if (toggleView == null || component == null) {
            return;
        }
        SwitchPalette palette = resolveSwitchPalette(component);
        GradientDrawable rowBg = null;
        if (switchRow != null) {
            if (!(switchRow.getBackground() instanceof GradientDrawable)) {
                switchRow.setBackground(createSwitchRowBackground());
            }
            if (switchRow.getBackground() instanceof GradientDrawable) {
                rowBg = (GradientDrawable) switchRow.getBackground();
            }
        }
        if (stateView != null) {
            stateView.setText(checked ? "已开启" : "已关闭");
        }
        ToggleVisualHolder holder = toggleView.getTag() instanceof ToggleVisualHolder
                ? (ToggleVisualHolder) toggleView.getTag()
                : null;
        if (holder == null) {
            return;
        }
        float targetProgress = checked ? 1f : 0f;
        if (holder.animator != null) {
            holder.animator.cancel();
            holder.animator = null;
        }
        if (!animate) {
            applySwitchProgress(rowBg, stateView, holder, targetProgress, palette);
            holder.progress = targetProgress;
            return;
        }
        final GradientDrawable finalRowBg = rowBg;
        final TextView finalStateView = stateView;
        ValueAnimator animator = ValueAnimator.ofFloat(holder.progress, targetProgress);
        animator.setDuration(checked ? 180L : 150L);
        animator.addUpdateListener(animation -> {
            Object animated = animation.getAnimatedValue();
            float progress = animated instanceof Number ? ((Number) animated).floatValue() : targetProgress;
            applySwitchProgress(finalRowBg, finalStateView, holder, progress, palette);
            holder.progress = progress;
        });
        animator.start();
        holder.animator = animator;
    }

    private static void applySwitchProgress(@Nullable GradientDrawable rowBackground,
                                            @Nullable TextView stateView,
                                            ToggleVisualHolder holder,
                                            float progress,
                                            SwitchPalette palette) {
        float safeProgress = Math.max(0f, Math.min(1f, progress));
        if (rowBackground != null) {
            rowBackground.setColor(interpolateColor(palette.rowOffFill, palette.rowOnFill, safeProgress));
            rowBackground.setStroke(1, interpolateColor(palette.rowOffStroke, palette.rowOnStroke, safeProgress));
        }
        if (stateView != null) {
            stateView.setTextColor(interpolateColor(palette.textOff, palette.textOn, safeProgress));
        }
        holder.trackDrawable.setColor(interpolateColor(palette.trackOffFill, palette.trackOnFill, safeProgress));
        holder.trackDrawable.setStroke(1, interpolateColor(palette.trackOffStroke, palette.trackOnStroke, safeProgress));
        holder.thumbDrawable.setColor(interpolateColor(palette.thumbOffFill, palette.thumbOnFill, safeProgress));
        holder.thumbDrawable.setStroke(1, interpolateColor(palette.thumbOffStroke, palette.thumbOnStroke, safeProgress));
        holder.thumbView.setTranslationX(holder.thumbTravelPx * safeProgress);
    }

    private static void installFastToggleTouchHandler(View target, Runnable onToggle) {
        if (target == null || onToggle == null) {
            return;
        }
        target.setClickable(true);
        target.setFocusable(true);
        target.setFocusableInTouchMode(true);
        target.setOnTouchListener(new View.OnTouchListener() {
            final int slop = ViewConfiguration.get(target.getContext()).getScaledTouchSlop();
            float downX;
            float downY;
            boolean tracking;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event == null) {
                    return false;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        tracking = true;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        v.setPressed(true);
                        requestDisallowFromAllParents(v, true);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (tracking && (Math.abs(event.getRawX() - downX) > slop
                                || Math.abs(event.getRawY() - downY) > slop)) {
                            tracking = false;
                            v.setPressed(false);
                        }
                        requestDisallowFromAllParents(v, tracking);
                        return true;
                    case MotionEvent.ACTION_UP:
                        boolean shouldToggle = tracking;
                        tracking = false;
                        v.setPressed(false);
                        requestDisallowFromAllParents(v, false);
                        if (shouldToggle) {
                            onToggle.run();
                            v.performClick();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        tracking = false;
                        v.setPressed(false);
                        requestDisallowFromAllParents(v, false);
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private static void requestDisallowFromAllParents(View view, boolean disallow) {
        ViewParent parent = view == null ? null : view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    private static SwitchPalette resolveSwitchPalette(ConfigUiComponent component) {
        int onTrack = parseColorOrDefault(component.switchOnColor, DEFAULT_SWITCH_ON_COLOR);
        int offTrack = parseColorOrDefault(component.switchOffColor, DEFAULT_SWITCH_OFF_COLOR);
        int thumbBase = parseColorOrDefault(component.switchThumbColor, DEFAULT_SWITCH_THUMB_COLOR);
        return new SwitchPalette(
                mixColorWithWhite(onTrack, 0.86f),
                mixColorWithWhite(offTrack, 0.90f),
                mixColorWithWhite(onTrack, 0.55f),
                mixColorWithWhite(offTrack, 0.60f),
                onTrack,
                offTrack,
                darkenColor(onTrack, 0.30f),
                darkenColor(offTrack, 0.32f),
                darkenColor(onTrack, 0.38f),
                darkenColor(offTrack, 0.15f),
                thumbBase,
                0xFFFFFFFF,
                darkenColor(thumbBase, 0.32f),
                0xFF1E293B);
    }

    private static int parseColorOrDefault(String raw, int fallback) {
        if (TextUtils.isEmpty(raw)) {
            return fallback;
        }
        try {
            return Color.parseColor(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int mixColorWithWhite(int color, float whiteRatio) {
        float safeRatio = Math.max(0f, Math.min(1f, whiteRatio));
        return interpolateColor(color, 0xFFFFFFFF, safeRatio);
    }

    private static int darkenColor(int color, float amount) {
        float safeAmount = Math.max(0f, Math.min(1f, amount));
        return interpolateColor(color, 0xFF000000, safeAmount);
    }

    private static int interpolateColor(int fromColor, int toColor, float fraction) {
        return (int) ARGB_EVALUATOR.evaluate(Math.max(0f, Math.min(1f, fraction)), fromColor, toColor);
    }

    private static final class ToggleVisualHolder {
        final View thumbView;
        final GradientDrawable trackDrawable;
        final GradientDrawable thumbDrawable;
        final int thumbTravelPx;
        float progress;
        @Nullable ValueAnimator animator;

        ToggleVisualHolder(View thumbView,
                           GradientDrawable trackDrawable,
                           GradientDrawable thumbDrawable,
                           int thumbTravelPx) {
            this.thumbView = thumbView;
            this.trackDrawable = trackDrawable;
            this.thumbDrawable = thumbDrawable;
            this.thumbTravelPx = thumbTravelPx;
            this.progress = 0f;
        }
    }

    private static final class SwitchPalette {
        final int rowOnFill;
        final int rowOffFill;
        final int rowOnStroke;
        final int rowOffStroke;
        final int trackOnFill;
        final int trackOffFill;
        final int trackOnStroke;
        final int trackOffStroke;
        final int textOn;
        final int textOff;
        final int thumbOnFill;
        final int thumbOffFill;
        final int thumbOnStroke;
        final int thumbOffStroke;

        SwitchPalette(int rowOnFill,
                      int rowOffFill,
                      int rowOnStroke,
                      int rowOffStroke,
                      int trackOnFill,
                      int trackOffFill,
                      int trackOnStroke,
                      int trackOffStroke,
                      int textOn,
                      int textOff,
                      int thumbOnFill,
                      int thumbOffFill,
                      int thumbOnStroke,
                      int thumbOffStroke) {
            this.rowOnFill = rowOnFill;
            this.rowOffFill = rowOffFill;
            this.rowOnStroke = rowOnStroke;
            this.rowOffStroke = rowOffStroke;
            this.trackOnFill = trackOnFill;
            this.trackOffFill = trackOffFill;
            this.trackOnStroke = trackOnStroke;
            this.trackOffStroke = trackOffStroke;
            this.textOn = textOn;
            this.textOff = textOff;
            this.thumbOnFill = thumbOnFill;
            this.thumbOffFill = thumbOffFill;
            this.thumbOnStroke = thumbOnStroke;
            this.thumbOffStroke = thumbOffStroke;
        }
    }

    private static String formatArrayEditorText(String initialValue) {
        if (TextUtils.isEmpty(initialValue)) {
            return "";
        }
        String trimmed = initialValue.trim();
        if (!(trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return initialValue;
        }
        try {
            JSONArray array = new JSONArray(trimmed);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) {
                    builder.append('\n');
                }
                builder.append(stringifyArrayItem(array.opt(i)));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return initialValue;
        }
    }

    private static String encodeArrayEditorValue(CharSequence rawText) {
        String raw = rawText == null ? "" : rawText.toString().trim();
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        if (raw.startsWith("[") && raw.endsWith("]")) {
            try {
                return new JSONArray(raw).toString();
            } catch (Exception ignored) {
            }
        }
        JSONArray array = new JSONArray();
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            array.put(parseArrayItem(trimmed));
        }
        return array.length() == 0 ? "" : array.toString();
    }

    private static Object parseArrayItem(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        String trimmed = raw.trim();
        if ("null".equalsIgnoreCase(trimmed)) {
            return JSONObject.NULL;
        }
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                return new org.json.JSONTokener(trimmed).nextValue();
            } catch (Exception ignored) {
            }
        }
        try {
            if (trimmed.contains(".")) {
                return Double.parseDouble(trimmed);
            }
            long longValue = Long.parseLong(trimmed);
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return (int) longValue;
            }
            return longValue;
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    private static String stringifyArrayItem(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "null";
        }
        if (value instanceof JSONArray || value instanceof JSONObject) {
            return String.valueOf(value);
        }
        return String.valueOf(value);
    }

    private static void installScrollableChildTouchBridge(View view) {
        if (view == null) {
            return;
        }
        view.setOnTouchListener((v, event) -> {
            if (event == null) {
                return false;
            }
            int action = event.getActionMasked();
            boolean disallow = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                disallow = false;
            }
            ViewParent parent = v.getParent();
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(disallow);
                parent = parent.getParent();
            }
            return false;
        });
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics());
    }

    private static int scaleDp(Context context, int value, float scale) {
        return dp(context, Math.max(1, Math.round(value * scale)));
    }

    private static float resolvePageScale(ConfigUiPage page) {
        if (page == null || page.scalePercent <= 0) {
            return 1.0f;
        }
        return Math.max(0.4f, Math.min(2.0f, page.scalePercent / 100f));
    }

    private static float resolveComponentScale(ConfigUiComponent component) {
        if (component == null || component.scalePercent <= 0) {
            return 1.0f;
        }
        return Math.max(0.4f, Math.min(2.0f, component.scalePercent / 100f));
    }

}
