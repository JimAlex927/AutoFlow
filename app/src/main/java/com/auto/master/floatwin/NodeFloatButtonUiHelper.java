package com.auto.master.floatwin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
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
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.auto.master.R;

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
        final WindowManager.LayoutParams lp;

        NodeFloatBtnEntry(View rootView, WindowManager.LayoutParams lp) {
            this.rootView = rootView;
            this.lp = lp;
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

        final int[] selColor = {existing != null ? existing.color : NODE_BTN_COLORS[0]};
        final int[] selTextColor = {existing != null ? existing.textColor : 0xFFFFFFFF};
        final int[] selSize = {existing != null ? existing.sizeDp : 48};
        final int[] selAlpha = {existing != null ? (int) (existing.alpha * 100 + 0.5f) : 100};

        File currentProjectDir = host.getCurrentProjectDir();
        File currentTaskDir = host.getCurrentTaskDir();
        String projectName = currentProjectDir != null ? currentProjectDir.getName() : "";
        String taskName = currentTaskDir != null ? currentTaskDir.getName() : "";

        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_node_float_btn_config, null);

        int winType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams dialogLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                winType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        dialogLp.gravity = Gravity.CENTER;
        dialogLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        host.getWindowManager().addView(dialogView, dialogLp);

        FrameLayout preview = dialogView.findViewById(R.id.cfg_preview);
        TextView previewLabel = dialogView.findViewById(R.id.cfg_preview_label);
        EditText etLabel = dialogView.findViewById(R.id.cfg_et_label);
        if (existing != null && existing.labelText != null) {
            etLabel.setText(existing.labelText);
        }

        Runnable refreshPreview = () -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(selColor[0]);
            preview.setBackground(bg);
            preview.setAlpha(selAlpha[0] / 100f);
            String t = etLabel.getText().toString().trim();
            previewLabel.setText(t.isEmpty() ? host.abbreviate(item.name, 6) : host.abbreviate(t, 6));
            previewLabel.setTextColor(selTextColor[0]);
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

        EditText etX = dialogView.findViewById(R.id.cfg_et_x);
        EditText etY = dialogView.findViewById(R.id.cfg_et_y);
        WindowManager.LayoutParams ballLp = host.getBallLayoutParams();
        int defaultX = ballLp != null ? ballLp.x + host.dp(60) : 160;
        int defaultY = ballLp != null ? ballLp.y : 400;
        etX.setText(String.valueOf(existing != null ? existing.posX : defaultX));
        etY.setText(String.valueOf(existing != null ? existing.posY : defaultY));
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

        dialogView.findViewById(R.id.cfg_btn_cancel).setOnClickListener(v -> host.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.cfg_btn_confirm).setOnClickListener(v -> {
            host.safeRemoveView(dialogView);
            int posX = parseIntDefault(etX.getText().toString(), defaultX);
            int posY = parseIntDefault(etY.getText().toString(), defaultY);
            String labelTxt = etLabel.getText().toString().trim();
            NodeFloatButtonConfig cfg = existing != null
                    ? existing
                    : new NodeFloatButtonConfig(item.id, item.name, projectName, taskName, selColor[0], posX, posY);
            cfg.ensureDefaults();
            cfg.operationId = item.id;
            cfg.operationName = item.name;
            cfg.projectName = projectName;
            cfg.taskName = taskName;
            cfg.color = selColor[0];
            cfg.posX = posX;
            cfg.posY = posY;
            cfg.labelText = labelTxt.isEmpty() ? null : labelTxt;
            cfg.textColor = selTextColor[0];
            cfg.sizeDp = selSize[0];
            cfg.alpha = selAlpha[0] / 100f;
            cfg.hideWhileRunning = chkHide.isChecked();
            cfg.buttonEnabled = true;

            manager.saveConfig(cfg);
            removeNodeFloatBtn(item.id);
            addNodeFloatBtn(cfg);
            notifyFloatButtonsChanged();
            android.widget.Toast.makeText(
                    host.getContext(),
                    existing != null ? "悬浮按钮已更新" : "悬浮按钮已创建",
                    android.widget.Toast.LENGTH_SHORT).show();
        });

        dialogView.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) {
                host.safeRemoveView(dialogView);
                return true;
            }
            return false;
        });
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
        int sizePx = host.dp(cfg.sizeDp);
        return cfg.posX >= 0
                && cfg.posY >= 0
                && cfg.posX + sizePx <= screen[0]
                && cfg.posY + sizePx <= screen[1];
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
        TextView label = root.findViewById(R.id.node_btn_label);

        String displayLabel = (cfg.labelText != null && !cfg.labelText.isEmpty())
                ? cfg.labelText : host.abbreviate(cfg.operationName, 8);
        label.setText(displayLabel);
        label.setTextColor(cfg.textColor);

        int sizePx = host.dp(cfg.sizeDp);
        ViewGroup.LayoutParams cLp = container.getLayoutParams();
        cLp.width = sizePx;
        cLp.height = sizePx;
        container.setLayoutParams(cLp);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(cfg.color);
        container.setBackground(bg);
        root.setAlpha(cfg.alpha);

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

        container.setOnClickListener(v -> host.runNodeFloatButton(cfg));
        host.getWindowManager().addView(root, lp);
        nodeFloatBtnViews.put(cfg.operationId, new NodeFloatBtnEntry(root, lp));
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
        nodeFloatActionSheetItems.add(new ActionItem(2, "配置修改", "弹出运行时配置，可切换成可视化表单", true));
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
