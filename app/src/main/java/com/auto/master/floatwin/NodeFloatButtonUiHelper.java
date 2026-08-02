package com.auto.master.floatwin;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.text.Editable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.auto.master.R;
import com.auto.master.utils.AppStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class NodeFloatButtonUiHelper {

    interface Host {
        FloatWindowService getService();

        Context getContext();

        WindowManager getWindowManager();

        Handler getUiHandler();

        int dp(int value);

        String abbreviate(String value, int maxChars);

        void safeRemoveView(View view);

        int[] getScreenSizePx();

        @Nullable WindowManager.LayoutParams getBallLayoutParams();

        @Nullable File getCurrentProjectDir();

        @Nullable File getCurrentTaskDir();

        @Nullable NodeFloatButtonManager getNodeFloatButtonManager();

        boolean shouldRetainNodeConfigMetadata(@Nullable NodeFloatButtonConfig cfg);

        void showRuntimeConfig(NodeFloatButtonConfig cfg);

        void showConfigUiDesigner(NodeFloatButtonConfig cfg);

        void runNodeFloatButton(NodeFloatButtonConfig cfg);

        void navigateToNodeInPanel(NodeFloatButtonConfig cfg);

        boolean isScriptRunning();

        void onFloatButtonsChanged(Map<String, Integer> colorMap);
    }

    private static final int[] NODE_BTN_COLORS = {
            0xFF4CAF50,
            0xFF2196F3,
            0xFFF44336,
            0xFFFF9800,
            0xFF9C27B0,
            0xFF00BCD4,
            0xFF795548,
            0xFF607D8B,
    };

    private static final int[] NODE_BTN_BORDER_COLORS = {
            0x00000000,
            0xFFFFFFFF,
            0xFF111827,
            0xFFFFEB3B,
            0xFF00E5FF,
            0xFFFF4081,
            0xFF7C4DFF,
            0xFF00C853,
            0xFFFF9800,
    };

    private static final Choice[] SHAPE_CHOICES = {
            new Choice(NodeFloatButtonConfig.SHAPE_CIRCLE, "圆形"),
            new Choice(NodeFloatButtonConfig.SHAPE_CAPSULE, "胶囊"),
            new Choice(NodeFloatButtonConfig.SHAPE_ROUNDED_RECT, "小方")
    };

    private static final Choice[] EFFECT_CHOICES = {
            new Choice(NodeFloatButtonConfig.EFFECT_NONE, "无"),
            new Choice(NodeFloatButtonConfig.EFFECT_PULSE, "脉冲"),
            new Choice(NodeFloatButtonConfig.EFFECT_SCALE, "缩放"),
            new Choice(NodeFloatButtonConfig.EFFECT_ROTATE, "旋转"),
            new Choice(NodeFloatButtonConfig.EFFECT_BREATH, "呼吸")
    };

    private static final Choice[] STATUS_CHOICES = {
            new Choice(NodeFloatButtonConfig.STATUS_STYLE_NONE, "普通"),
            new Choice(NodeFloatButtonConfig.STATUS_STYLE_PROGRESS, "进度"),
            new Choice(NodeFloatButtonConfig.STATUS_STYLE_FLASH, "闪色")
    };

    private static final IconChoice[] ICON_CHOICES = {
            new IconChoice("", "无", ""),
            new IconChoice("play", "运行", "▶"),
            new IconChoice("check", "确认", "✓"),
            new IconChoice("tap", "点击", "●"),
            new IconChoice("image", "图片", "▧"),
            new IconChoice("clock", "等待", "◷"),
            new IconChoice("star", "星标", "★"),
            new IconChoice("bolt", "闪电", "⚡")
    };

    @Nullable
    private static java.util.function.Consumer<String> pendingSystemImagePickCallback;

    static void deliverSystemPickedImage(String relativePath) {
        java.util.function.Consumer<String> callback = pendingSystemImagePickCallback;
        pendingSystemImagePickCallback = null;
        if (callback != null) {
            callback.accept(relativePath == null ? "" : relativePath);
        }
    }

    private static class Choice {
        final String value;
        final String label;

        Choice(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    private static final class IconChoice extends Choice {
        final String glyph;

        IconChoice(String value, String label, String glyph) {
            super(value, label);
            this.glyph = glyph;
        }
    }

    private final Host host;
    private final Map<String, NodeFloatBtnEntry> nodeFloatBtnViews = new HashMap<>();
    private PopupWindow nodeFloatActionPopupWindow;
    private TextView nodeFloatActionPopupTitleView;
    private androidx.recyclerview.widget.RecyclerView nodeFloatActionPopupListView;
    private final List<ActionItem> nodeFloatActionSheetItems = new ArrayList<>();
    private ActionSheetAdapter nodeFloatActionSheetAdapter;
    @Nullable
    private java.util.function.Consumer<ActionItem> nodeFloatActionSheetHandler;

    private static final class NodeFloatBtnEntry {
        final View rootView;
        final FrameLayout buttonView;
        final ProgressRingView progressRingView;
        final WindowManager.LayoutParams lp;

        NodeFloatBtnEntry(View rootView, FrameLayout buttonView, ProgressRingView progressRingView,
                          WindowManager.LayoutParams lp) {
            this.rootView = rootView;
            this.buttonView = buttonView;
            this.progressRingView = progressRingView;
            this.lp = lp;
        }
    }

    private static final class ProgressRingView extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();
        private float startAngle;

        ProgressRingView(Context context) {
            super(context);
            setWillNotDraw(false);
            setVisibility(View.GONE);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }

        void configure(int color, float strokeWidth) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            invalidate();
        }

        void setStartAngle(float startAngle) {
            this.startAngle = startAngle;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float inset = paint.getStrokeWidth() / 2f + 1f;
            rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawArc(rect, startAngle, 92f, false, paint);
        }
    }

    private static final class ActionItem {
        final int id;
        final String title;
        final String desc;
        final boolean enabled;

        ActionItem(int id, String title, String desc, boolean enabled) {
            this.id = id;
            this.title = title;
            this.desc = desc;
            this.enabled = enabled;
        }
    }

    private static final class ActionSheetAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ActionSheetAdapter.ViewHolder> {
        private final List<ActionItem> items;
        private final java.util.function.Consumer<ActionItem> clickHandler;

        ActionSheetAdapter(List<ActionItem> items, java.util.function.Consumer<ActionItem> clickHandler) {
            this.items = items;
            this.clickHandler = clickHandler;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_node_action, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ActionItem item = items.get(position);
            holder.tvName.setText(item.title);
            holder.tvDesc.setText(item.desc);
            holder.itemView.setAlpha(item.enabled ? 1f : 0.4f);
            holder.itemView.setOnClickListener(v -> {
                if (item.enabled && clickHandler != null) {
                    clickHandler.accept(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvDesc;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_action_name);
                tvDesc = itemView.findViewById(R.id.tv_action_desc);
            }
        }
    }

    NodeFloatButtonUiHelper(Host host) {
        this.host = host;
    }

    void onDestroy() {
        if (nodeFloatActionPopupWindow != null) {
            nodeFloatActionPopupWindow.dismiss();
            nodeFloatActionPopupWindow = null;
        }
        removeAllNodeFloatButtons();
    }

    void restoreNodeFloatButtons() {
        NodeFloatButtonManager manager = host.getNodeFloatButtonManager();
        if (manager == null) {
            return;
        }
        for (NodeFloatButtonConfig cfg : manager.getAllConfigs().values()) {
            if (cfg != null && Boolean.TRUE.equals(cfg.buttonEnabled)) {
                addNodeFloatBtn(cfg);
            }
        }
        notifyFloatButtonsChanged();
    }

    void refreshNodeFloatButtonsForCurrentScreen() {
        NodeFloatButtonManager manager = host.getNodeFloatButtonManager();
        Map<String, NodeFloatButtonConfig> configs = manager == null
                ? Collections.emptyMap()
                : new HashMap<>(manager.getAllConfigs());
        removeAllNodeFloatButtons();
        for (NodeFloatButtonConfig cfg : configs.values()) {
            if (cfg != null && Boolean.TRUE.equals(cfg.buttonEnabled)) {
                addNodeFloatBtn(cfg);
            }
        }
        notifyFloatButtonsChanged();
    }

    void showNodeFloatBtnConfig(OperationItem item) {
        NodeFloatButtonManager manager = host.getNodeFloatButtonManager();
        if (item == null || manager == null) {
            return;
        }
        NodeFloatButtonConfig existing = manager.getConfig(item.id);
        if (existing != null) {
            existing.ensureDefaults();
        }
        final NodeFloatButtonConfig originalSnapshot = existing == null ? null : copyConfig(existing);

        final int[] selColor = {existing != null ? existing.color : NODE_BTN_COLORS[0]};
        final int[] selTextColor = {existing != null ? existing.textColor : 0xFFFFFFFF};
        final int[] selBorderColor = {existing != null ? existing.borderColor : 0x00000000};
        final int[] selBorderWidth = {existing != null ? existing.borderWidthDp : 0};
        final String[] selShape = {existing != null ? existing.shape : NodeFloatButtonConfig.SHAPE_CIRCLE};
        final String[] selEffect = {existing != null ? existing.clickEffect : NodeFloatButtonConfig.EFFECT_NONE};
        final String[] selStatusStyle = {existing != null ? existing.statusStyle : NodeFloatButtonConfig.STATUS_STYLE_NONE};
        final String[] selIconKey = {existing != null ? existing.iconKey : ""};
        final int[] selSize = {existing != null ? existing.sizeDp : 48};
        final int[] selAlpha = {existing != null ? (int) (existing.alpha * 100 + 0.5f) : 100};

        File currentProjectDir = host.getCurrentProjectDir();
        File currentTaskDir = host.getCurrentTaskDir();
        String projectName = existing != null && !isBlank(existing.projectName)
                ? existing.projectName
                : (currentProjectDir != null ? currentProjectDir.getName() : "");
        String taskName = existing != null && !isBlank(existing.taskName)
                ? existing.taskName
                : (currentTaskDir != null ? currentTaskDir.getName() : "");

        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_node_float_btn_config, null);

        int winType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int[] screen = host.getScreenSizePx();
        int dialogWidth = screen[0] > 0 ? Math.round(screen[0] * 0.5f) : host.dp(420);
        int dialogHeight = screen[1] > 0 ? Math.round(screen[1] * 0.6f) : host.dp(460);
        WindowManager.LayoutParams dialogLp = new WindowManager.LayoutParams(
                dialogWidth,
                dialogHeight,
                winType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        dialogLp.gravity = Gravity.CENTER;
        dialogLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        host.getWindowManager().addView(dialogView, dialogLp);

        FrameLayout preview = dialogView.findViewById(R.id.cfg_preview);
        ImageView previewImage = dialogView.findViewById(R.id.cfg_preview_image);
        TextView previewIcon = dialogView.findViewById(R.id.cfg_preview_icon);
        TextView previewLabel = dialogView.findViewById(R.id.cfg_preview_label);
        EditText etLabel = dialogView.findViewById(R.id.cfg_et_label);
        EditText etImage = dialogView.findViewById(R.id.cfg_et_image);
        EditText etX = dialogView.findViewById(R.id.cfg_et_x);
        EditText etY = dialogView.findViewById(R.id.cfg_et_y);
        WindowManager.LayoutParams ballLp = host.getBallLayoutParams();
        int defaultX = ballLp != null ? ballLp.x + host.dp(60) : 160;
        int defaultY = ballLp != null ? ballLp.y : 400;
        etX.setText(String.valueOf(existing != null ? existing.posX : defaultX));
        etY.setText(String.valueOf(existing != null ? existing.posY : defaultY));
        if (existing != null && existing.labelText != null) {
            etLabel.setText(existing.labelText);
        }
        if (existing != null && existing.imagePath != null) {
            etImage.setText(existing.imagePath);
        }

        Runnable refreshPreview = () -> {
            updatePreviewSize(preview, selShape[0]);
            clipButtonShape(preview, selShape[0]);
            setButtonBackground(preview, selShape[0], selColor[0], selBorderColor[0], host.dp(selBorderWidth[0]));
            preview.setAlpha(selAlpha[0] / 100f);
            String t = etLabel.getText().toString().trim();
            boolean hasImage = applyImage(previewImage,
                    resolveNodeButtonImage(etImage.getText().toString(), projectName, taskName),
                    host.dp(6));
            String glyph = iconGlyph(selIconKey[0]);
            boolean hasIcon = !hasImage && glyph.length() > 0;
            previewIcon.setText(glyph);
            previewIcon.setTextColor(selTextColor[0]);
            previewIcon.setVisibility(hasIcon ? View.VISIBLE : View.GONE);
            previewLabel.setText(t.isEmpty() ? host.abbreviate(item.name, 6) : host.abbreviate(t, 6));
            previewLabel.setTextColor(selTextColor[0]);
            previewLabel.setVisibility((hasImage || hasIcon) && t.isEmpty() ? View.GONE : View.VISIBLE);
            if (existing != null) {
                NodeFloatButtonConfig draft = buildNodeButtonDraft(
                        item, projectName, taskName,
                        selColor[0], selTextColor[0], selBorderColor[0], selBorderWidth[0],
                        selShape[0], selEffect[0], selStatusStyle[0], selIconKey[0],
                        selSize[0], selAlpha[0], etLabel, etImage, etX, etY,
                        defaultX, defaultY, existing.hideWhileRunning);
                updateOrAddNodeFloatBtn(draft);
            }
        };
        refreshPreview.run();

        etLabel.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                refreshPreview.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etImage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                refreshPreview.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        android.text.TextWatcher positionWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                refreshPreview.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        etX.addTextChangedListener(positionWatcher);
        etY.addTextChangedListener(positionWatcher);
        dialogView.findViewById(R.id.cfg_btn_clear_image).setOnClickListener(v -> etImage.setText(""));
        dialogView.findViewById(R.id.cfg_btn_pick_image).setOnClickListener(v ->
                requestSystemImagePick(projectName, taskName, etImage, dialogView));

        LinearLayout swatchRow = dialogView.findViewById(R.id.cfg_color_swatches);
        float density = host.getContext().getResources().getDisplayMetrics().density;
        int swSz = (int) (28 * density);
        int swMgn = (int) (5 * density);
        for (int color : NODE_BTN_COLORS) {
            FrameLayout sw = new FrameLayout(host.getContext());
            GradientDrawable swBg = new GradientDrawable();
            swBg.setShape(GradientDrawable.OVAL);
            swBg.setColor(color);
            sw.setBackground(swBg);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(swSz, swSz);
            swLp.setMargins(swMgn, swMgn, swMgn, swMgn);
            sw.setLayoutParams(swLp);
            sw.setOnClickListener(v -> {
                selColor[0] = color;
                refreshPreview.run();
            });
            swatchRow.addView(sw);
        }

        LinearLayout borderColorRow = dialogView.findViewById(R.id.cfg_border_color_row);
        for (int color : NODE_BTN_BORDER_COLORS) {
            TextView sw = new TextView(host.getContext());
            sw.setGravity(Gravity.CENTER);
            sw.setText(color == 0 ? "无" : "");
            sw.setTextColor(0xFF6A7682);
            sw.setTextSize(10f);
            GradientDrawable swBg = new GradientDrawable();
            swBg.setShape(GradientDrawable.OVAL);
            swBg.setColor(color == 0 ? 0xFFFFFFFF : color);
            swBg.setStroke((int) (1.5f * density), color == 0 ? 0xFFB8C2CC : 0x33000000);
            sw.setBackground(swBg);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(swSz, swSz);
            swLp.setMargins(swMgn, swMgn, swMgn, swMgn);
            sw.setLayoutParams(swLp);
            sw.setOnClickListener(v -> {
                selBorderColor[0] = color;
                if (color != 0 && selBorderWidth[0] == 0) {
                    selBorderWidth[0] = 3;
                    TextView tv = dialogView.findViewById(R.id.cfg_border_val);
                    if (tv != null) tv.setText(selBorderWidth[0] + "dp");
                }
                refreshPreview.run();
            });
            borderColorRow.addView(sw);
        }

        int[] textColors = {0xFFFFFFFF, 0xFF222222, 0xFFFFEB3B, 0xFFCCCCCC};
        LinearLayout tcRow = dialogView.findViewById(R.id.cfg_text_color_row);
        for (int tc : textColors) {
            FrameLayout sw = new FrameLayout(host.getContext());
            GradientDrawable swBg = new GradientDrawable();
            swBg.setShape(GradientDrawable.OVAL);
            swBg.setColor(tc);
            if ((tc & 0x00FFFFFF) >= 0x00BBBBBB) {
                swBg.setStroke((int) (1.5f * density), 0x44000000);
            }
            sw.setBackground(swBg);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(swSz, swSz);
            swLp.setMargins(swMgn, swMgn, swMgn, swMgn);
            sw.setLayoutParams(swLp);
            sw.setOnClickListener(v -> {
                selTextColor[0] = tc;
                refreshPreview.run();
            });
            tcRow.addView(sw);
        }

        bindIconChoiceRow(dialogView.findViewById(R.id.cfg_icon_row), selIconKey, refreshPreview);
        bindChoiceRow(dialogView.findViewById(R.id.cfg_shape_row), SHAPE_CHOICES, selShape, refreshPreview);
        bindChoiceRow(dialogView.findViewById(R.id.cfg_effect_row), EFFECT_CHOICES, selEffect, refreshPreview);
        bindChoiceRow(dialogView.findViewById(R.id.cfg_status_style_row), STATUS_CHOICES, selStatusStyle, refreshPreview);

        TextView tvBorderVal = dialogView.findViewById(R.id.cfg_border_val);
        Runnable refreshBorder = () -> tvBorderVal.setText(selBorderWidth[0] + "dp");
        refreshBorder.run();
        dialogView.findViewById(R.id.cfg_border_minus).setOnClickListener(v -> {
            if (selBorderWidth[0] > 0) {
                selBorderWidth[0] -= 1;
                refreshBorder.run();
                refreshPreview.run();
            }
        });
        dialogView.findViewById(R.id.cfg_border_plus).setOnClickListener(v -> {
            if (selBorderWidth[0] < 10) {
                selBorderWidth[0] += 1;
                if (selBorderColor[0] == 0) {
                    selBorderColor[0] = 0xFFFFFFFF;
                }
                refreshBorder.run();
                refreshPreview.run();
            }
        });

        TextView tvSizeVal = dialogView.findViewById(R.id.cfg_size_val);
        Runnable refreshSize = () -> tvSizeVal.setText(selSize[0] + "dp");
        refreshSize.run();
        dialogView.findViewById(R.id.cfg_size_minus).setOnClickListener(v -> {
            if (selSize[0] > 32) {
                selSize[0] -= 4;
                refreshSize.run();
            }
        });
        dialogView.findViewById(R.id.cfg_size_plus).setOnClickListener(v -> {
            if (selSize[0] < 88) {
                selSize[0] += 4;
                refreshSize.run();
            }
        });

        TextView tvAlphaVal = dialogView.findViewById(R.id.cfg_alpha_val);
        Runnable refreshAlpha = () -> tvAlphaVal.setText(selAlpha[0] + "%");
        refreshAlpha.run();
        dialogView.findViewById(R.id.cfg_alpha_minus).setOnClickListener(v -> {
            if (selAlpha[0] > 20) {
                selAlpha[0] -= 10;
                refreshAlpha.run();
                refreshPreview.run();
            }
        });
        dialogView.findViewById(R.id.cfg_alpha_plus).setOnClickListener(v -> {
            if (selAlpha[0] < 100) {
                selAlpha[0] += 10;
                refreshAlpha.run();
                refreshPreview.run();
            }
        });

        CheckBox chkHide = dialogView.findViewById(R.id.cfg_chk_hide);
        if (existing != null) {
            chkHide.setChecked(existing.hideWhileRunning);
        }

        dialogView.findViewById(R.id.cfg_btn_pick).setOnClickListener(v ->
                showPositionPickOverlay(dialogView, etX, etY, selSize));

        View btnDelete = dialogView.findViewById(R.id.cfg_btn_delete);
        if (existing != null) {
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                host.safeRemoveView(dialogView);
                if (host.shouldRetainNodeConfigMetadata(existing)) {
                    existing.buttonEnabled = false;
                    manager.saveConfig(existing);
                } else {
                    manager.removeConfig(item.id);
                }
                removeNodeFloatBtn(item.id);
                notifyFloatButtonsChanged();
                android.widget.Toast.makeText(host.getContext(), "已删除悬浮按钮", android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        dialogView.findViewById(R.id.cfg_btn_cancel).setOnClickListener(v -> {
            if (originalSnapshot != null) {
                updateOrAddNodeFloatBtn(originalSnapshot);
            }
            host.safeRemoveView(dialogView);
        });
        dialogView.findViewById(R.id.cfg_btn_confirm).setOnClickListener(v -> {
            host.safeRemoveView(dialogView);
            NodeFloatButtonConfig cfg = buildNodeButtonDraft(
                    item, projectName, taskName,
                    selColor[0], selTextColor[0], selBorderColor[0], selBorderWidth[0],
                    selShape[0], selEffect[0], selStatusStyle[0], selIconKey[0],
                    selSize[0], selAlpha[0], etLabel, etImage, etX, etY,
                    defaultX, defaultY, chkHide.isChecked());
            // Button appearance and node runtime metadata share the same
            // persisted object.  Editing the button must not detach its
            // Config UI or erase the values entered through that UI.
            if (existing != null) {
                cfg.runtimeVariablesText = existing.runtimeVariablesText;
                cfg.configUiSchemaId = existing.configUiSchemaId;
            }
            cfg.hideWhileRunning = chkHide.isChecked();
            cfg.buttonEnabled = true;

            manager.saveConfig(cfg);
            updateOrAddNodeFloatBtn(cfg);
            notifyFloatButtonsChanged();
            android.widget.Toast.makeText(
                    host.getContext(),
                    existing != null ? "悬浮按钮已更新" : "悬浮按钮已创建",
                    android.widget.Toast.LENGTH_SHORT).show();
        });

    }

    private NodeFloatButtonConfig buildNodeButtonDraft(OperationItem item,
                                                        String projectName,
                                                        String taskName,
                                                        int color,
                                                        int textColor,
                                                        int borderColor,
                                                        int borderWidthDp,
                                                        String shape,
                                                        String clickEffect,
                                                        String statusStyle,
                                                        String iconKey,
                                                        int sizeDp,
                                                        int alphaPercent,
                                                        EditText etLabel,
                                                        EditText etImage,
                                                        EditText etX,
                                                        EditText etY,
                                                        int defaultX,
                                                        int defaultY,
                                                        boolean hideWhileRunning) {
        NodeFloatButtonConfig cfg = new NodeFloatButtonConfig();
        cfg.operationId = item.id;
        cfg.operationName = item.name;
        cfg.projectName = projectName;
        cfg.taskName = taskName;
        cfg.color = color;
        cfg.posX = parseIntDefault(etX.getText().toString(), defaultX);
        cfg.posY = parseIntDefault(etY.getText().toString(), defaultY);
        String labelTxt = etLabel.getText().toString().trim();
        cfg.labelText = labelTxt.isEmpty() ? null : labelTxt;
        cfg.textColor = textColor;
        cfg.borderColor = borderWidthDp > 0 ? borderColor : 0x00000000;
        cfg.borderWidthDp = borderWidthDp;
        cfg.imagePath = etImage.getText().toString().trim();
        cfg.imagePaddingDp = 4;
        cfg.iconKey = iconKey == null ? "" : iconKey;
        cfg.shape = shape;
        cfg.clickEffect = clickEffect;
        cfg.statusStyle = statusStyle;
        cfg.sizeDp = sizeDp;
        cfg.alpha = Math.max(0.2f, Math.min(1f, alphaPercent / 100f));
        cfg.hideWhileRunning = hideWhileRunning;
        cfg.buttonEnabled = true;
        cfg.ensureDefaults();
        return cfg;
    }

    private NodeFloatButtonConfig copyConfig(NodeFloatButtonConfig src) {
        if (src == null) return null;
        NodeFloatButtonConfig copy = new NodeFloatButtonConfig();
        copy.operationId = src.operationId;
        copy.operationName = src.operationName;
        copy.projectName = src.projectName;
        copy.taskName = src.taskName;
        copy.color = src.color;
        copy.posX = src.posX;
        copy.posY = src.posY;
        copy.labelText = src.labelText;
        copy.textColor = src.textColor;
        copy.borderColor = src.borderColor;
        copy.borderWidthDp = src.borderWidthDp;
        copy.imagePath = src.imagePath;
        copy.iconKey = src.iconKey;
        copy.shape = src.shape;
        copy.clickEffect = src.clickEffect;
        copy.statusStyle = src.statusStyle;
        copy.imagePaddingDp = src.imagePaddingDp;
        copy.sizeDp = src.sizeDp;
        copy.alpha = src.alpha;
        copy.hideWhileRunning = src.hideWhileRunning;
        copy.runtimeVariablesText = src.runtimeVariablesText;
        copy.configUiSchemaId = src.configUiSchemaId;
        copy.buttonEnabled = src.buttonEnabled;
        copy.ensureDefaults();
        return copy;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    Map<String, Integer> getFloatBtnColorMap() {
        NodeFloatButtonManager manager = host.getNodeFloatButtonManager();
        if (manager == null) {
            return Collections.emptyMap();
        }
        Map<String, NodeFloatButtonConfig> configs = manager.getAllConfigs();
        Map<String, Integer> result = new HashMap<>(configs.size());
        for (Map.Entry<String, NodeFloatButtonConfig> entry : configs.entrySet()) {
            if (entry.getValue() != null && Boolean.TRUE.equals(entry.getValue().buttonEnabled)) {
                result.put(entry.getKey(), entry.getValue().color);
            }
        }
        return result;
    }

    void hideButtonUntilScriptStops(String operationId) {
        NodeFloatBtnEntry entry = nodeFloatBtnViews.get(operationId);
        if (entry == null) {
            return;
        }
        entry.rootView.setVisibility(View.INVISIBLE);
        scheduleRestoreNodeBtnVisibility(operationId, entry.rootView);
    }

    private void notifyFloatButtonsChanged() {
        host.onFloatButtonsChanged(getFloatBtnColorMap());
    }

    private boolean isNodeFloatBtnVisibleForCurrentScreen(NodeFloatButtonConfig cfg) {
        if (cfg == null) {
            return false;
        }
        cfg.ensureDefaults();
        if (!Boolean.TRUE.equals(cfg.buttonEnabled)) {
            return false;
        }
        int[] screen = host.getScreenSizePx();
        int widthPx = buttonWidthPx(cfg);
        int heightPx = host.dp(cfg.sizeDp);
        return cfg.posX >= 0
                && cfg.posY >= 0
                && cfg.posX + widthPx <= screen[0]
                && cfg.posY + heightPx <= screen[1];
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addNodeFloatBtn(NodeFloatButtonConfig cfg) {
        NodeFloatButtonManager manager = host.getNodeFloatButtonManager();
        if (manager == null || nodeFloatBtnViews.containsKey(cfg.operationId)) {
            return;
        }
        cfg.ensureDefaults();
        if (!isNodeFloatBtnVisibleForCurrentScreen(cfg)) {
            return;
        }

        View root = LayoutInflater.from(host.getContext()).inflate(R.layout.window_node_float_btn, null);
        FrameLayout container = root.findViewById(R.id.node_btn_container);
        ProgressRingView progressRing = new ProgressRingView(host.getContext());
        container.addView(progressRing, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = cfg.posX;
        lp.y = cfg.posY;

        NodeFloatBtnEntry entry = new NodeFloatBtnEntry(root, container, progressRing, lp);
        renderNodeFloatButtonEntry(entry, cfg);

        container.setOnTouchListener(new DragTouchListener(lp, host.getWindowManager(), root, host.getService(), true) {
            @Override
            protected void onDragEnd(int finalX, int finalY) {
                NodeFloatButtonConfig updated = manager.getConfig(cfg.operationId);
                if (updated != null) {
                    updated.posX = finalX;
                    updated.posY = finalY;
                    manager.saveConfig(updated);
                }
            }

            @Override
            protected void onLongPress() {
                showNodeFloatBtnMenu(container, cfg);
            }
        });

        container.setOnClickListener(v ->
                playClickEffect(container, cfg, () -> {
                    markButtonRunningUntilScriptStops(cfg.operationId);
                    host.runNodeFloatButton(cfg);
                }));
        host.getWindowManager().addView(root, lp);
        nodeFloatBtnViews.put(cfg.operationId, entry);
    }

    private void updateOrAddNodeFloatBtn(NodeFloatButtonConfig cfg) {
        if (cfg == null) return;
        cfg.ensureDefaults();
        NodeFloatBtnEntry entry = nodeFloatBtnViews.get(cfg.operationId);
        if (entry == null) {
            addNodeFloatBtn(cfg);
            return;
        }
        entry.lp.x = cfg.posX;
        entry.lp.y = cfg.posY;
        renderNodeFloatButtonEntry(entry, cfg);
        try {
            host.getWindowManager().updateViewLayout(entry.rootView, entry.lp);
        } catch (Exception ignored) {
        }
    }

    private void renderNodeFloatButtonEntry(NodeFloatBtnEntry entry, NodeFloatButtonConfig cfg) {
        if (entry == null || cfg == null) return;
        cfg.ensureDefaults();
        ImageView image = entry.rootView.findViewById(R.id.node_btn_image);
        TextView icon = entry.rootView.findViewById(R.id.node_btn_icon);
        TextView label = entry.rootView.findViewById(R.id.node_btn_label);

        boolean hasCustomLabel = cfg.labelText != null && !cfg.labelText.isEmpty();
        String displayLabel = hasCustomLabel ? cfg.labelText : host.abbreviate(cfg.operationName, 8);
        label.setText(displayLabel);
        label.setTextColor(cfg.textColor);

        int sizePx = host.dp(cfg.sizeDp);
        ViewGroup.LayoutParams cLp = entry.buttonView.getLayoutParams();
        cLp.width = buttonWidthPx(cfg);
        cLp.height = sizePx;
        entry.buttonView.setLayoutParams(cLp);

        setButtonBackground(entry.buttonView, cfg.shape, cfg.color, cfg.borderColor, host.dp(cfg.borderWidthDp));
        clipButtonShape(entry.buttonView, cfg.shape);
        entry.progressRingView.setVisibility(View.GONE);
        entry.progressRingView.configure(0xFFFFFFFF, Math.max(host.dp(2), host.dp(cfg.borderWidthDp + 1)));

        boolean hasImage = applyImage(image,
                resolveNodeButtonImage(cfg.imagePath, cfg.projectName, cfg.taskName),
                host.dp(cfg.imagePaddingDp));
        String glyph = iconGlyph(cfg.iconKey);
        boolean hasIcon = !hasImage && glyph.length() > 0;
        icon.setText(glyph);
        icon.setTextColor(cfg.textColor);
        icon.setVisibility(hasIcon ? View.VISIBLE : View.GONE);
        label.setVisibility((hasImage || hasIcon) && !hasCustomLabel ? View.GONE : View.VISIBLE);
        entry.rootView.setAlpha(cfg.alpha);
        entry.buttonView.setScaleX(1f);
        entry.buttonView.setScaleY(1f);
        entry.buttonView.setRotation(0f);
        applyIdleEffect(entry.buttonView, cfg);
    }

    private void removeNodeFloatBtn(String operationId) {
        NodeFloatBtnEntry entry = nodeFloatBtnViews.remove(operationId);
        if (entry != null) {
            host.safeRemoveView(entry.rootView);
        }
    }

    private void removeAllNodeFloatButtons() {
        for (NodeFloatBtnEntry entry : nodeFloatBtnViews.values()) {
            host.safeRemoveView(entry.rootView);
        }
        nodeFloatBtnViews.clear();
    }

    private void ensureNodeFloatActionPopup() {
        if (nodeFloatActionPopupWindow != null) {
            return;
        }
        View popupView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_node_action_sheet, null);
        nodeFloatActionPopupTitleView = popupView.findViewById(R.id.tv_action_title);
        nodeFloatActionPopupListView = popupView.findViewById(R.id.rv_action_list);
        if (nodeFloatActionPopupListView != null) {
            nodeFloatActionPopupListView.setLayoutManager(new LinearLayoutManager(host.getContext()));
            nodeFloatActionSheetAdapter = new ActionSheetAdapter(
                    nodeFloatActionSheetItems,
                    action -> {
                        if (action == null || !action.enabled) {
                            return;
                        }
                        if (nodeFloatActionPopupWindow != null) {
                            nodeFloatActionPopupWindow.dismiss();
                        }
                        if (nodeFloatActionSheetHandler != null) {
                            nodeFloatActionSheetHandler.accept(action);
                        }
                    });
            nodeFloatActionPopupListView.setAdapter(nodeFloatActionSheetAdapter);
        }
        nodeFloatActionPopupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        nodeFloatActionPopupWindow.setOutsideTouchable(true);
        nodeFloatActionPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        nodeFloatActionPopupWindow.setElevation(10f);
    }

    private void showNodeFloatBtnMenu(View anchor, NodeFloatButtonConfig cfg) {
        NodeFloatButtonManager manager = host.getNodeFloatButtonManager();
        if (manager == null) {
            return;
        }
        ensureNodeFloatActionPopup();
        if (nodeFloatActionPopupWindow == null
                || nodeFloatActionPopupTitleView == null
                || nodeFloatActionSheetAdapter == null) {
            return;
        }
        nodeFloatActionPopupTitleView.setText(cfg.operationName);
        nodeFloatActionSheetItems.clear();
        nodeFloatActionSheetItems.add(new ActionItem(1, "运行节点", "立即运行这个节点", true));
        nodeFloatActionSheetItems.add(new ActionItem(2, "修改配置", "默认打开自定义 UI，可切换通用列表", true));
        nodeFloatActionSheetItems.add(new ActionItem(3, "ConfigUI 设计", "拖动排序组件，设计这个节点的可视化配置界面", true));
        nodeFloatActionSheetItems.add(new ActionItem(4, "按钮设置", "修改文字、颜色、大小和位置", true));
        nodeFloatActionSheetItems.add(new ActionItem(5, "定位节点", "打开面板并高亮这个节点", true));
        nodeFloatActionSheetItems.add(new ActionItem(6, "移除悬浮按钮", "删除这个悬浮按钮（不影响节点）", true));
        nodeFloatActionSheetHandler = action -> {
            switch (action.id) {
                case 1:
                    host.runNodeFloatButton(cfg);
                    break;
                case 2:
                    host.showRuntimeConfig(cfg);
                    break;
                case 3:
                    host.showConfigUiDesigner(cfg);
                    break;
                case 4:
                    showNodeFloatBtnConfig(new OperationItem(cfg.operationName, cfg.operationId, "", 0));
                    break;
                case 5:
                    host.navigateToNodeInPanel(cfg);
                    break;
                case 6:
                    if (host.shouldRetainNodeConfigMetadata(cfg)) {
                        cfg.buttonEnabled = false;
                        manager.saveConfig(cfg);
                    } else {
                        manager.removeConfig(cfg.operationId);
                    }
                    removeNodeFloatBtn(cfg.operationId);
                    notifyFloatButtonsChanged();
                    android.widget.Toast.makeText(host.getContext(), "已移除悬浮按钮", android.widget.Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        };
        nodeFloatActionSheetAdapter.notifyDataSetChanged();
        if (nodeFloatActionPopupWindow.isShowing()) {
            nodeFloatActionPopupWindow.dismiss();
        }
        nodeFloatActionPopupWindow.showAsDropDown(anchor, -host.dp(180), host.dp(4), Gravity.END);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showPositionPickOverlay(View dialogView, EditText etX, EditText etY, int[] sizeRef) {
        dialogView.setVisibility(View.INVISIBLE);

        FrameLayout overlay = new FrameLayout(host.getContext());
        overlay.setBackgroundColor(0x66000000);

        TextView hint = new TextView(host.getContext());
        hint.setText("点击屏幕设置悬浮按钮的位置");
        hint.setTextColor(0xFFFFFFFF);
        hint.setTextSize(15f);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(0xCC1A2332);
        hint.setPadding(host.dp(24), host.dp(12), host.dp(24), host.dp(12));
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hintLp.topMargin = host.dp(80);
        overlay.addView(hint, hintLp);

        TextView cancelTv = new TextView(host.getContext());
        cancelTv.setText("取  消");
        cancelTv.setTextColor(0xFFFFFFFF);
        cancelTv.setTextSize(14f);
        cancelTv.setGravity(Gravity.CENTER);
        cancelTv.setBackgroundColor(0xCC1A2332);
        cancelTv.setPadding(host.dp(32), host.dp(12), host.dp(32), host.dp(12));
        FrameLayout.LayoutParams cancelLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cancelLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cancelLp.bottomMargin = host.dp(80);
        overlay.addView(cancelTv, cancelLp);

        int winType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams olp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                winType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        olp.gravity = Gravity.TOP | Gravity.START;
        host.getWindowManager().addView(overlay, olp);

        overlay.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float tx = e.getRawX();
                float ty = e.getRawY();
                boolean hitCancel = false;
                if (cancelTv.getWidth() > 0) {
                    int[] loc = new int[2];
                    cancelTv.getLocationOnScreen(loc);
                    hitCancel = tx >= loc[0] && tx <= loc[0] + cancelTv.getWidth()
                            && ty >= loc[1] && ty <= loc[1] + cancelTv.getHeight();
                }
                host.safeRemoveView(overlay);
                dialogView.setVisibility(View.VISIBLE);
                if (!hitCancel) {
                    float d = host.getContext().getResources().getDisplayMetrics().density;
                    int half = (int) (sizeRef[0] * d / 2);
                    etX.setText(String.valueOf(Math.max(0, (int) tx - half)));
                    etY.setText(String.valueOf(Math.max(0, (int) ty - half)));
                }
            }
            return true;
        });
    }

    private void showNodeButtonImagePicker(EditText target) {
        File taskDir = host.getCurrentTaskDir();
        if (taskDir == null || !taskDir.isDirectory()) {
            android.widget.Toast.makeText(host.getContext(), "当前 Task 无效", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        List<File> files = new ArrayList<>();
        collectImageFiles(taskDir, files, 3);
        if (files.isEmpty()) {
            android.widget.Toast.makeText(host.getContext(), "当前 Task 下没有可选图片", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Collections.sort(files, (a, b) -> a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()));

        LinearLayout shell = new LinearLayout(host.getContext());
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(host.dp(14), host.dp(14), host.dp(14), host.dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(host.dp(10));
        bg.setStroke(host.dp(1), 0xFFDCE4EF);
        shell.setBackground(bg);

        TextView title = new TextView(host.getContext());
        title.setText("选择按钮图片");
        title.setTextColor(0xFF243244);
        title.setTextSize(15f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        shell.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, host.dp(34)));

        android.widget.ScrollView scroll = new android.widget.ScrollView(host.getContext());
        LinearLayout list = new LinearLayout(host.getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new android.widget.ScrollView.LayoutParams(
                android.widget.ScrollView.LayoutParams.MATCH_PARENT,
                android.widget.ScrollView.LayoutParams.WRAP_CONTENT));
        shell.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, host.dp(260)));

        TextView cancel = new TextView(host.getContext());
        cancel.setText("取消");
        cancel.setGravity(Gravity.CENTER);
        cancel.setTextColor(0xFF6A7682);
        cancel.setTextSize(13f);
        cancel.setBackgroundResource(R.drawable.item_operation_compact_bg);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, host.dp(38));
        cancelLp.setMargins(0, host.dp(10), 0, 0);
        shell.addView(cancel, cancelLp);

        int winType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                host.dp(320),
                WindowManager.LayoutParams.WRAP_CONTENT,
                winType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        host.getWindowManager().addView(shell, lp);

        for (File file : files) {
            String rel = relativePath(taskDir, file);
            TextView row = new TextView(host.getContext());
            row.setText(rel);
            row.setTextColor(0xFF243244);
            row.setTextSize(12f);
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(host.dp(10), 0, host.dp(10), 0);
            row.setBackgroundResource(R.drawable.item_operation_compact_bg);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, host.dp(40));
            rowLp.setMargins(0, 0, 0, host.dp(6));
            list.addView(row, rowLp);
            row.setOnClickListener(v -> {
                target.setText(rel);
                target.setSelection(target.getText().length());
                host.safeRemoveView(shell);
            });
        }

        cancel.setOnClickListener(v -> host.safeRemoveView(shell));
        shell.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) {
                host.safeRemoveView(shell);
                return true;
            }
            return false;
        });
    }

    private void collectImageFiles(File dir, List<File> out, int depthLeft) {
        if (dir == null || out == null || depthLeft < 0 || out.size() >= 80) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file == null) continue;
            if (file.isDirectory()) {
                collectImageFiles(file, out, depthLeft - 1);
            } else if (isSupportedButtonImage(file)) {
                out.add(file);
                if (out.size() >= 80) return;
            }
        }
    }

    private boolean isSupportedButtonImage(File file) {
        String name = file == null ? "" : file.getName().toLowerCase();
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".webp");
    }

    private String relativePath(File base, File file) {
        try {
            String root = base.getCanonicalPath();
            String path = file.getCanonicalPath();
            if (path.startsWith(root)) {
                String rel = path.substring(root.length());
                while (rel.startsWith(File.separator)) rel = rel.substring(1);
                return rel.replace(File.separatorChar, '/');
            }
        } catch (Exception ignored) {}
        return file.getAbsolutePath();
    }

    private void scheduleRestoreNodeBtnVisibility(String operationId, View btnView) {
        host.getUiHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (host.isScriptRunning()) {
                    host.getUiHandler().postDelayed(this, 500);
                } else if (nodeFloatBtnViews.containsKey(operationId)) {
                    btnView.setVisibility(View.VISIBLE);
                }
            }
        }, 500);
    }

    void onOperationRunComplete(String operationId, boolean success) {
        NodeFloatBtnEntry entry = nodeFloatBtnViews.get(operationId);
        if (entry == null) {
            return;
        }
        restoreButtonVisual(operationId, success);
    }

    private void requestSystemImagePick(String projectName, String taskName, EditText target, View dialogView) {
        pendingSystemImagePickCallback = path -> {
            host.getUiHandler().post(() -> {
                if (path != null && !path.isEmpty()) {
                    target.setText(path);
                    target.setSelection(target.getText().length());
                }
                dialogView.setVisibility(View.VISIBLE);
            });
        };
        Intent intent = new Intent(host.getContext(), NodeButtonImagePickerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(NodeButtonImagePickerActivity.EXTRA_PROJECT, projectName);
        intent.putExtra(NodeButtonImagePickerActivity.EXTRA_TASK, taskName);
        try {
            dialogView.setVisibility(View.INVISIBLE);
            host.getContext().startActivity(intent);
        } catch (Exception e) {
            pendingSystemImagePickCallback = null;
            dialogView.setVisibility(View.VISIBLE);
            android.widget.Toast.makeText(host.getContext(), "打开图片选择器失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void bindChoiceRow(LinearLayout row, Choice[] choices, String[] selected, Runnable onChanged) {
        if (row == null) return;
        row.removeAllViews();
        for (Choice choice : choices) {
            TextView btn = makeChoiceButton(choice.label, choice.value.equals(selected[0]));
            btn.setOnClickListener(v -> {
                selected[0] = choice.value;
                bindChoiceRow(row, choices, selected, onChanged);
                onChanged.run();
            });
            row.addView(btn);
        }
    }

    private void bindIconChoiceRow(LinearLayout row, String[] selected, Runnable onChanged) {
        if (row == null) return;
        row.removeAllViews();
        for (IconChoice choice : ICON_CHOICES) {
            String label = choice.value.isEmpty() ? "无" : choice.glyph + " " + choice.label;
            TextView btn = makeChoiceButton(label, choice.value.equals(selected[0]));
            btn.setOnClickListener(v -> {
                selected[0] = choice.value;
                bindIconChoiceRow(row, selected, onChanged);
                onChanged.run();
            });
            row.addView(btn);
        }
    }

    private TextView makeChoiceButton(String label, boolean selected) {
        TextView tv = new TextView(host.getContext());
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setTextSize(12f);
        tv.setTextColor(selected ? 0xFFFFFFFF : 0xFF315DBF);
        tv.setPadding(host.dp(10), 0, host.dp(10), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(host.dp(8));
        bg.setColor(selected ? 0xFF3C6DE4 : 0xFFF4F7FB);
        bg.setStroke(host.dp(1), selected ? 0xFF3C6DE4 : 0xFFD7E0EC);
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        lp.setMargins(0, 0, host.dp(6), 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void setButtonBackground(View view, String shape, int fillColor, int borderColor, int borderWidthPx) {
        GradientDrawable bg = new GradientDrawable();
        boolean circle = NodeFloatButtonConfig.SHAPE_CIRCLE.equals(shape);
        bg.setShape(circle ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        if (!circle) {
            bg.setCornerRadius(NodeFloatButtonConfig.SHAPE_CAPSULE.equals(shape)
                    ? 1000f
                    : host.dp(12));
        }
        bg.setColor(fillColor);
        if (borderWidthPx > 0 && borderColor != 0) {
            bg.setStroke(borderWidthPx, borderColor);
        }
        view.setBackground(bg);
    }

    private void clipButtonShape(View view, String shape) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                if (NodeFloatButtonConfig.SHAPE_CIRCLE.equals(shape)) {
                    outline.setOval(0, 0, v.getWidth(), v.getHeight());
                } else {
                    float radius = NodeFloatButtonConfig.SHAPE_CAPSULE.equals(shape)
                            ? v.getHeight() / 2f
                            : host.dp(12);
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
                }
            }
        });
    }

    private int buttonWidthPx(NodeFloatButtonConfig cfg) {
        int sizePx = host.dp(cfg.sizeDp);
        return NodeFloatButtonConfig.SHAPE_CAPSULE.equals(cfg.shape)
                ? Math.round(sizePx * 1.62f)
                : sizePx;
    }

    private void updatePreviewSize(View preview, String shape) {
        ViewGroup.LayoutParams lp = preview.getLayoutParams();
        int height = host.dp(72);
        lp.height = height;
        lp.width = NodeFloatButtonConfig.SHAPE_CAPSULE.equals(shape)
                ? Math.round(height * 1.62f)
                : height;
        preview.setLayoutParams(lp);
    }

    private String iconGlyph(String key) {
        if (key == null || key.isEmpty()) return "";
        for (IconChoice choice : ICON_CHOICES) {
            if (choice.value.equals(key)) return choice.glyph;
        }
        return "";
    }

    private void applyIdleEffect(View view, NodeFloatButtonConfig cfg) {
        cancelStoredAnimator(view);
        if (!NodeFloatButtonConfig.EFFECT_BREATH.equals(cfg.clickEffect)) {
            return;
        }
        ObjectAnimator sx = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.08f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.08f);
        sx.setDuration(900);
        sy.setDuration(900);
        sx.setRepeatMode(ValueAnimator.REVERSE);
        sy.setRepeatMode(ValueAnimator.REVERSE);
        sx.setRepeatCount(ValueAnimator.INFINITE);
        sy.setRepeatCount(ValueAnimator.INFINITE);
        sx.start();
        sy.start();
        view.setTag(R.id.node_btn_container, sx);
        view.setTag(R.id.node_btn_image, sy);
    }

    private void playClickEffect(View view, NodeFloatButtonConfig cfg, Runnable after) {
        cancelStoredAnimator(view);
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setRotation(0f);
        String effect = cfg.clickEffect;
        if (NodeFloatButtonConfig.EFFECT_ROTATE.equals(effect)) {
            view.animate().rotationBy(360f).setDuration(260).withEndAction(after).start();
        } else if (NodeFloatButtonConfig.EFFECT_SCALE.equals(effect)) {
            view.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90)
                    .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(110)
                            .withEndAction(after).start()).start();
        } else if (NodeFloatButtonConfig.EFFECT_PULSE.equals(effect)) {
            view.animate().scaleX(1.16f).scaleY(1.16f).setDuration(100)
                    .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(130)
                            .withEndAction(after).start()).start();
        } else {
            after.run();
            applyIdleEffect(view, cfg);
        }
    }

    private void markButtonRunningUntilScriptStops(String operationId) {
        NodeFloatBtnEntry entry = nodeFloatBtnViews.get(operationId);
        NodeFloatButtonManager manager = host.getNodeFloatButtonManager();
        NodeFloatButtonConfig cfg = manager == null ? null : manager.getConfig(operationId);
        if (entry == null || cfg == null) {
            return;
        }
        cfg.ensureDefaults();
        if (NodeFloatButtonConfig.STATUS_STYLE_NONE.equals(cfg.statusStyle)) {
            return;
        }
        cancelStoredAnimator(entry.buttonView);
        entry.buttonView.animate().cancel();
        entry.buttonView.setScaleX(1f);
        entry.buttonView.setScaleY(1f);
        entry.buttonView.setRotation(0f);
        setButtonBackground(entry.buttonView, cfg.shape, 0xFF3C6DE4,
                cfg.borderColor == 0 ? 0xFFFFFFFF : cfg.borderColor,
                Math.max(host.dp(2), host.dp(cfg.borderWidthDp)));
        if (NodeFloatButtonConfig.STATUS_STYLE_PROGRESS.equals(cfg.statusStyle)) {
            entry.progressRingView.setVisibility(View.VISIBLE);
            entry.progressRingView.configure(0xFFFFFFFF, Math.max(host.dp(2), host.dp(cfg.borderWidthDp + 1)));
            ValueAnimator progress = ValueAnimator.ofFloat(0f, 360f);
            progress.setDuration(900);
            progress.setRepeatCount(ValueAnimator.INFINITE);
            progress.addUpdateListener(a ->
                    entry.progressRingView.setStartAngle((Float) a.getAnimatedValue()));
            progress.start();
            entry.buttonView.setTag(R.id.node_btn_container, progress);
        }
        scheduleRestoreNodeBtnStyle(operationId);
    }

    private void scheduleRestoreNodeBtnStyle(String operationId) {
        host.getUiHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (host.isScriptRunning()) {
                    host.getUiHandler().postDelayed(this, 500);
                } else if (nodeFloatBtnViews.containsKey(operationId)) {
                    restoreButtonVisual(operationId, true);
                }
            }
        }, 700);
    }

    private void restoreButtonVisual(String operationId, boolean success) {
        NodeFloatBtnEntry entry = nodeFloatBtnViews.get(operationId);
        NodeFloatButtonManager manager = host.getNodeFloatButtonManager();
        NodeFloatButtonConfig cfg = manager == null ? null : manager.getConfig(operationId);
        if (entry == null || cfg == null) {
            return;
        }
        cfg.ensureDefaults();
        cancelStoredAnimator(entry.buttonView);
        entry.buttonView.animate().cancel();
        entry.progressRingView.setVisibility(View.GONE);
        setButtonBackground(entry.buttonView, cfg.shape, cfg.color, cfg.borderColor, host.dp(cfg.borderWidthDp));
        entry.buttonView.setScaleX(1f);
        entry.buttonView.setScaleY(1f);
        entry.buttonView.setRotation(0f);
        if (NodeFloatButtonConfig.STATUS_STYLE_FLASH.equals(cfg.statusStyle)) {
            int flashColor = success ? 0xFF00C853 : 0xFFFF3B30;
            setButtonBackground(entry.buttonView, cfg.shape, flashColor, cfg.borderColor, host.dp(cfg.borderWidthDp));
            host.getUiHandler().postDelayed(() -> {
                NodeFloatBtnEntry latest = nodeFloatBtnViews.get(operationId);
                if (latest != null) {
                    setButtonBackground(latest.buttonView, cfg.shape, cfg.color, cfg.borderColor, host.dp(cfg.borderWidthDp));
                    applyIdleEffect(latest.buttonView, cfg);
                }
            }, 260);
        } else {
            applyIdleEffect(entry.buttonView, cfg);
        }
    }

    private void cancelStoredAnimator(View view) {
        Object a = view.getTag(R.id.node_btn_container);
        if (a instanceof Animator) {
            ((Animator) a).cancel();
            view.setTag(R.id.node_btn_container, null);
        }
        Object b = view.getTag(R.id.node_btn_image);
        if (b instanceof Animator) {
            ((Animator) b).cancel();
            view.setTag(R.id.node_btn_image, null);
        }
    }

    private boolean applyImage(ImageView imageView, @Nullable File imageFile, int paddingPx) {
        if (imageView == null) {
            return false;
        }
        if (imageFile == null || !imageFile.isFile()) {
            imageView.setImageDrawable(null);
            imageView.setVisibility(View.GONE);
            return false;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        if (bitmap == null) {
            imageView.setImageDrawable(null);
            imageView.setVisibility(View.GONE);
            return false;
        }
        imageView.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        imageView.setImageBitmap(bitmap);
        imageView.setVisibility(View.VISIBLE);
        return true;
    }

    @Nullable
    private File resolveNodeButtonImage(String rawPath, String projectName, String taskName) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return null;
        }
        String raw = rawPath.trim();
        File direct = new File(raw);
        if (direct.isAbsolute() && direct.isFile()) {
            return direct;
        }

        File currentTaskDir = host.getCurrentTaskDir();
        if (currentTaskDir != null) {
            File inCurrentTask = new File(currentTaskDir, raw);
            if (inCurrentTask.isFile()) {
                return inCurrentTask;
            }
        }

        File base = AppStorage.getAppFilesRoot(host.getContext());
        if (base != null && projectName != null && !projectName.isEmpty()
                && taskName != null && !taskName.isEmpty()) {
            File inTask = new File(base,
                    "projects" + File.separator
                            + projectName + File.separator
                            + taskName + File.separator
                            + raw);
            if (inTask.isFile()) {
                return inTask;
            }
            File inProjects = new File(base, raw);
            if (inProjects.isFile()) {
                return inProjects;
            }
        }
        return direct.isFile() ? direct : null;
    }

    private static int parseIntDefault(String s, int def) {
        if (s == null || s.trim().isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
