package com.auto.master.configui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class ConfigUiCanvasEditorView extends FrameLayout {
    public interface Listener {
        void onEditComponent(ConfigUiComponent component);
        void onDeleteComponent(ConfigUiComponent component);
        void onCanvasChanged();
    }

    private static final int CANVAS_MIN_HEIGHT_DP = 560;
    private ConfigUiPage page;
    private Listener listener;
    private final TextView emptyHintView;
    @Nullable
    private String selectedComponentId;
    @Nullable
    private View selectedBlockView;

    public ConfigUiCanvasEditorView(Context context) {
        this(context, null);
    }

    public ConfigUiCanvasEditorView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ConfigUiCanvasEditorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(dp(10), dp(10), dp(10), dp(10));
        setBackground(buildCanvasBackground(false));

        emptyHintView = new TextView(context);
        emptyHintView.setText("拖组件到画布里，放到哪里，预览就在哪里");
        emptyHintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        emptyHintView.setTextColor(0xFF7B8794);
        emptyHintView.setGravity(Gravity.CENTER);
        addView(emptyHintView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setPage(@Nullable ConfigUiPage page) {
        this.page = page;
        if (this.page != null) {
            this.page.ensureDefaults();
        }
        post(this::render);
    }

    public void setDropActive(boolean active) {
        setBackground(buildCanvasBackground(active));
    }

    public void addComponent(String componentType, float xPx, float yPx) {
        if (page == null) {
            return;
        }
        page.ensureDefaults();
        int index = page.components == null ? 1 : page.components.size() + 1;
        ConfigUiComponent component = ConfigUiComponent.createPreset(componentType, index);
        component.ensureDefaults();
        int widthDp = component.widthDp;
        int heightDp = component.heightDp;
        float scale = resolveScale();
        int maxWidthDp = pxToDp(Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight()), scale);
        if (maxWidthDp > 0) {
            widthDp = Math.min(widthDp, Math.max(120, maxWidthDp));
        }
        component.widthDp = widthDp;
        component.heightDp = heightDp;
        component.xDp = Math.max(0, pxToDp(xPx, scale) - widthDp / 2);
        component.yDp = Math.max(0, pxToDp(yPx, scale) - heightDp / 2);
        clampComponentIntoCanvas(component);
        page.components.add(component);
        selectedComponentId = component.id;
        ensureCanvasHeight(component);
        render();
        if (listener != null) {
            listener.onCanvasChanged();
            listener.onEditComponent(component);
        }
    }

    private void render() {
        removeAllViews();
        addView(emptyHintView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        if (page == null) {
            emptyHintView.setVisibility(View.VISIBLE);
            setMinimumHeight(dp(CANVAS_MIN_HEIGHT_DP));
            return;
        }
        page.ensureDefaults();
        selectedBlockView = null;
        setMinimumHeight(scaleDp(Math.max(CANVAS_MIN_HEIGHT_DP, computeCanvasHeightDp()), resolveScale()));
        if (page.components == null || page.components.isEmpty()) {
            emptyHintView.setVisibility(View.VISIBLE);
            return;
        }
        emptyHintView.setVisibility(View.GONE);
        for (ConfigUiComponent component : page.components) {
            if (component == null) {
                continue;
            }
            component.ensureDefaults();
            addView(createComponentBlock(component));
        }
    }

    private int computeCanvasHeightDp() {
        int result = page == null ? CANVAS_MIN_HEIGHT_DP : page.canvasHeightDp;
        if (page != null && page.components != null) {
            for (ConfigUiComponent component : page.components) {
                if (component == null) {
                    continue;
                }
                component.ensureDefaults();
                result = Math.max(result,
                        component.yDp + Math.round(component.heightDp * resolveComponentScale(component)) + 40);
            }
        }
        return Math.max(CANVAS_MIN_HEIGHT_DP, result);
    }

    @SuppressLint("ClickableViewAccessibility")
    private View createComponentBlock(ConfigUiComponent component) {
        Context context = getContext();
        float pageScale = resolveScale();
        float contentScale = pageScale * resolveComponentScale(component);
        FrameLayout shell = new FrameLayout(context);
        shell.setTag(component.id);
        applyShellStyle(shell, TextUtils.equals(selectedComponentId, component.id));
        shell.setElevation(scaleDp(2, contentScale));
        int shellPad = scaleDp(10, contentScale);
        shell.setPadding(shellPad, shellPad, shellPad, shellPad);

        LayoutParams shellLp = new LayoutParams(scaleDp(component.widthDp, contentScale), scaleDp(component.heightDp, contentScale));
        shellLp.leftMargin = scaleDp(component.xDp, pageScale);
        shellLp.topMargin = scaleDp(component.yDp, pageScale);
        shell.setLayoutParams(shellLp);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        shell.addView(content, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        if (ConfigUiComponent.TYPE_SWITCH.equals(component.type)) {
            bindSwitchPreviewBlock(context, content, component, contentScale);
        } else {
            TextView title = new TextView(context);
            title.setText(TextUtils.isEmpty(component.label) ? component.getDisplayTypeName() : component.label);
            title.setTextColor(0xFF243244);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * contentScale);
            title.setTypeface(title.getTypeface(), Typeface.BOLD);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            content.addView(title);

            TextView meta = new TextView(context);
            String key = TextUtils.isEmpty(component.fieldKey) ? component.getDisplayTypeName() : component.fieldKey;
            meta.setText(key);
            meta.setTextColor(0xFF6B7B8C);
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f * contentScale);
            meta.setMaxLines(2);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            metaLp.topMargin = scaleDp(4, contentScale);
            content.addView(meta, metaLp);

            TextView footer = new TextView(context);
            footer.setText(component.getDisplayTypeName()
                    + "  " + component.widthDp + "x" + component.heightDp
                    + "  组件:" + component.scalePercent + "%");
            footer.setTextColor(0xFF9AA8B5);
            footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * contentScale);
            LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            footerLp.topMargin = scaleDp(8, contentScale);
            content.addView(footer, footerLp);
        }

        TextView deleteBtn = new TextView(context);
        deleteBtn.setText("×");
        deleteBtn.setGravity(Gravity.CENTER);
        deleteBtn.setTextColor(0xFFF44336);
        deleteBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * contentScale);
        GradientDrawable deleteBg = new GradientDrawable();
        deleteBg.setShape(GradientDrawable.OVAL);
        deleteBg.setColor(0xFFFDEBEC);
        deleteBtn.setBackground(deleteBg);
        LayoutParams deleteLp = new LayoutParams(scaleDp(24, contentScale), scaleDp(24, contentScale), Gravity.END | Gravity.TOP);
        shell.addView(deleteBtn, deleteLp);

        deleteBtn.setOnClickListener(v -> {
            if (page == null || page.components == null) {
                return;
            }
            page.components.remove(component);
            if (listener != null) {
                listener.onDeleteComponent(component);
                listener.onCanvasChanged();
            }
            render();
        });

        shell.setOnTouchListener(new ComponentDragTouchListener(shell, component));
        if (TextUtils.equals(selectedComponentId, component.id)) {
            selectedBlockView = shell;
        }
        return shell;
    }

    private void bindSwitchPreviewBlock(Context context,
                                        LinearLayout content,
                                        ConfigUiComponent component,
                                        float contentScale) {
        TextView title = new TextView(context);
        title.setText(TextUtils.isEmpty(component.label) ? "开关" : component.label);
        title.setTextColor(0xFF243244);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * contentScale);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(title);

        boolean checked = "true".equalsIgnoreCase(component.defaultValue);
        int onTrackColor = parseColorOrDefault(component.switchOnColor, 0xFF16A34A);
        int offTrackColor = parseColorOrDefault(component.switchOffColor, 0xFF64748B);
        int thumbColor = parseColorOrDefault(component.switchThumbColor, 0xFFFDE68A);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(scaleDp(16, contentScale));
        rowBg.setColor(checked ? mixColorWithWhite(onTrackColor, 0.86f) : mixColorWithWhite(offTrackColor, 0.90f));
        rowBg.setStroke(scaleDp(1, contentScale), checked ? mixColorWithWhite(onTrackColor, 0.55f) : mixColorWithWhite(offTrackColor, 0.60f));
        row.setBackground(rowBg);
        int rowPadH = scaleDp(10, contentScale);
        int rowPadV = scaleDp(8, contentScale);
        row.setPadding(rowPadH, rowPadV, rowPadH, rowPadV);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = scaleDp(10, contentScale);
        content.addView(row, rowLp);

        TextView state = new TextView(context);
        state.setText(checked ? "已开启" : "已关闭");
        state.setTextColor(checked ? darkenColor(onTrackColor, 0.38f) : darkenColor(offTrackColor, 0.15f));
        state.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * contentScale);
        LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        row.addView(state, stateLp);

        FrameLayout track = new FrameLayout(context);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(scaleDp(999, contentScale));
        trackBg.setColor(checked ? onTrackColor : offTrackColor);
        trackBg.setStroke(scaleDp(1, contentScale), checked ? darkenColor(onTrackColor, 0.30f) : darkenColor(offTrackColor, 0.32f));
        track.setBackground(trackBg);
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                scaleDp(40, contentScale), scaleDp(22, contentScale));
        row.addView(track, trackLp);

        View thumb = new View(context);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setShape(GradientDrawable.OVAL);
        thumbBg.setColor(checked ? thumbColor : 0xFFFFFFFF);
        thumbBg.setStroke(scaleDp(1, contentScale), checked ? darkenColor(thumbColor, 0.32f) : 0xFF1E293B);
        thumb.setBackground(thumbBg);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(
                scaleDp(14, contentScale), scaleDp(14, contentScale));
        thumbLp.gravity = checked ? (Gravity.END | Gravity.CENTER_VERTICAL) : (Gravity.START | Gravity.CENTER_VERTICAL);
        thumbLp.leftMargin = scaleDp(4, contentScale);
        thumbLp.rightMargin = scaleDp(4, contentScale);
        track.addView(thumb, thumbLp);

        TextView footer = new TextView(context);
        footer.setText("Toggle Switch  " + component.widthDp + "x" + component.heightDp
                + "  组件:" + component.scalePercent + "%");
        footer.setTextColor(0xFF9AA8B5);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f * contentScale);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = scaleDp(8, contentScale);
        content.addView(footer, footerLp);
    }

    private int parseColorOrDefault(String raw, int fallback) {
        if (TextUtils.isEmpty(raw)) {
            return fallback;
        }
        try {
            return Color.parseColor(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int mixColorWithWhite(int color, float whiteRatio) {
        float safeRatio = Math.max(0f, Math.min(1f, whiteRatio));
        int r = Math.round(Color.red(color) * (1f - safeRatio) + 255f * safeRatio);
        int g = Math.round(Color.green(color) * (1f - safeRatio) + 255f * safeRatio);
        int b = Math.round(Color.blue(color) * (1f - safeRatio) + 255f * safeRatio);
        return Color.argb(255, r, g, b);
    }

    private int darkenColor(int color, float amount) {
        float safeAmount = Math.max(0f, Math.min(1f, amount));
        int r = Math.round(Color.red(color) * (1f - safeAmount));
        int g = Math.round(Color.green(color) * (1f - safeAmount));
        int b = Math.round(Color.blue(color) * (1f - safeAmount));
        return Color.argb(255, r, g, b);
    }

    private void clampComponentIntoCanvas(ConfigUiComponent component) {
        if (component == null) {
            return;
        }
        int availableWidthDp = pxToDp(Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight()), resolveScale());
        if (availableWidthDp > 0) {
            int maxLeft = Math.max(0, availableWidthDp - component.widthDp);
            component.xDp = Math.max(0, Math.min(component.xDp, maxLeft));
        } else {
            component.xDp = Math.max(0, component.xDp);
        }
        component.yDp = Math.max(0, component.yDp);
    }

    private void ensureCanvasHeight(ConfigUiComponent component) {
        if (page == null || component == null) {
            return;
        }
        page.canvasHeightDp = Math.max(page.canvasHeightDp, component.yDp + component.heightDp + 40);
    }

    private GradientDrawable buildCanvasBackground(boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(active ? 0xFFEFF5FF : 0xFFF7FAFD);
        bg.setStroke(dp(1), active ? 0xFF3C6DE4 : 0xFFD5DFEA);
        return bg;
    }

    private void applyShellStyle(View shell, boolean selected) {
        if (shell == null) {
            return;
        }
        GradientDrawable shellBg = new GradientDrawable();
        shellBg.setCornerRadius(dp(16));
        shellBg.setColor(0xFFFFFFFF);
        shellBg.setStroke(dp(1), selected ? 0xFF3C6DE4 : 0xFFD9E2EC);
        shell.setBackground(shellBg);
    }

    private void refreshSelectionVisuals() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child == emptyHintView) {
                continue;
            }
            Object tag = child.getTag();
            boolean selected = tag instanceof String && TextUtils.equals((String) tag, selectedComponentId);
            applyShellStyle(child, selected);
            if (selected) {
                selectedBlockView = child;
            }
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics());
    }

    private int scaleDp(int value, float scale) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                Math.max(1f, value * scale),
                getResources().getDisplayMetrics());
    }

    private int pxToDp(float px, float scale) {
        return (int) (px / (getResources().getDisplayMetrics().density * Math.max(0.4f, scale)) + 0.5f);
    }

    private float resolveScale() {
        if (page == null || page.scalePercent <= 0) {
            return 1.0f;
        }
        return Math.max(0.4f, Math.min(2.0f, page.scalePercent / 100f));
    }

    private float resolveComponentScale(ConfigUiComponent component) {
        if (component == null || component.scalePercent <= 0) {
            return 1.0f;
        }
        return Math.max(0.4f, Math.min(2.0f, component.scalePercent / 100f));
    }

    private final class ComponentDragTouchListener implements OnTouchListener {
        private final View blockView;
        private final ConfigUiComponent component;
        private float downRawX;
        private float downRawY;
        private int startXDp;
        private int startYDp;
        private boolean dragging;

        ComponentDragTouchListener(View blockView, ConfigUiComponent component) {
            this.blockView = blockView;
            this.component = component;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (component == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startXDp = component.xDp;
                    startYDp = component.yDp;
                    dragging = false;
                    selectedComponentId = component.id;
                    selectedBlockView = blockView;
                    refreshSelectionVisuals();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - downRawX;
                    float deltaY = event.getRawY() - downRawY;
                    if (!dragging && (Math.abs(deltaX) > dp(8) || Math.abs(deltaY) > dp(8))) {
                        dragging = true;
                    }
                    float scale = resolveScale();
                    component.xDp = startXDp + pxToDp(deltaX, scale);
                    component.yDp = startYDp + pxToDp(deltaY, scale);
                    clampComponentIntoCanvas(component);
                    ensureCanvasHeight(component);
                    updateBlockLayout(blockView, component);
                    if (listener != null) {
                        listener.onCanvasChanged();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (!dragging && listener != null) {
                        listener.onEditComponent(component);
                    } else if (listener != null) {
                        listener.onCanvasChanged();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return false;
            }
        }

        private void updateBlockLayout(View block, ConfigUiComponent component) {
            if (block == null || component == null) {
                return;
            }
            float pageScale = resolveScale();
            float contentScale = pageScale * resolveComponentScale(component);
            LayoutParams lp = (LayoutParams) block.getLayoutParams();
            lp.leftMargin = scaleDp(component.xDp, pageScale);
            lp.topMargin = scaleDp(component.yDp, pageScale);
            lp.width = scaleDp(component.widthDp, contentScale);
            lp.height = scaleDp(component.heightDp, contentScale);
            block.setLayoutParams(lp);
            setMinimumHeight(scaleDp(computeCanvasHeightDp(), pageScale));
        }
    }
}
