package com.auto.master.floatwin;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.content.ClipData;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.auto.master.R;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.configui.ConfigUiCanvasEditorView;
import com.auto.master.configui.ConfigUiComponent;
import com.auto.master.configui.ConfigUiFormRenderer;
import com.auto.master.configui.ConfigUiOption;
import com.auto.master.configui.ConfigUiPage;
import com.auto.master.configui.ConfigUiSchema;
import com.auto.master.configui.ConfigUiStore;
import com.auto.master.configui.ConfigUiValueCodec;
import com.auto.master.utils.UUIDGenerator;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class FloatWindowConfigUiHelper {

    interface Host {
        Context getContext();

        WindowManager getWindowManager();

        WindowManager.LayoutParams buildDialogLayoutParams(int widthDp, boolean focusable);

        void safeRemoveView(View view);

        void showToast(String message);

        int dp(int value);

        String abbreviate(String value, int maxChars);

        @Nullable NodeFloatButtonManager getNodeFloatButtonManager();

        @Nullable ConfigUiStore getConfigUiStore();

        @Nullable File getCurrentProjectDir();

        @Nullable File getCurrentTaskDir();

        @Nullable WindowManager.LayoutParams getBallLayoutParams();

        int getDefaultNodeButtonColor();
    }

    private static final String CONFIG_UI_DRAG_LABEL = "config_ui_component";

    private final Host host;

    FloatWindowConfigUiHelper(Host host) {
        this.host = host;
    }

    void showNodeRuntimeConfigDialog(NodeFloatButtonConfig cfg) {
        if (cfg == null) {
            return;
        }
        cfg.ensureDefaults();
        ConfigUiSchema schema = resolveNodeConfigUiSchema(cfg);
        if (schema != null) {
            showVisualNodeRuntimeConfigDialog(cfg, schema, null);
            return;
        }
        showLegacyNodeRuntimeConfigDialog(cfg);
    }

    void showConfigUiDesignerDialog(OperationItem item) {
        if (item == null) {
            return;
        }
        showConfigUiDesignerDialog(getOrCreateNodeConfig(item, false));
    }

    void showConfigUiDesignerDialog(NodeFloatButtonConfig cfg) {
        NodeFloatButtonManager nodeFloatBtnManager = host.getNodeFloatButtonManager();
        ConfigUiStore configUiStore = host.getConfigUiStore();
        if (cfg == null || nodeFloatBtnManager == null || configUiStore == null) {
            return;
        }
        cfg.ensureDefaults();
        ensureNodeConfigOwnership(cfg);
        String schemaId = ensureNodeConfigUiSchemaId(cfg);
        ConfigUiSchema baseSchema = resolveNodeConfigUiSchema(cfg);
        if (baseSchema == null) {
            baseSchema = ConfigUiSchema.createDefault(schemaId, cfg.operationName + " 配置");
        }
        ConfigUiSchema workingSchema = cloneConfigUiSchema(baseSchema);
        workingSchema.schemaId = schemaId;
        workingSchema.ensureDefaults();

        Context ctx = new android.view.ContextThemeWrapper(host.getContext(), R.style.Theme_AtomMaster);
        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_config_ui_designer, null);
        EditText nameInput = dialogView.findViewById(R.id.et_config_ui_name);
        HorizontalScrollView pageTabsScroll = dialogView.findViewById(R.id.scroll_config_ui_tabs);
        LinearLayout pageTabsContainer = dialogView.findViewById(R.id.layout_config_ui_tabs);
        TextView pageMetaView = dialogView.findViewById(R.id.tv_config_ui_page_meta);
        TextView addPageBtn = dialogView.findViewById(R.id.btn_config_ui_add_page);
        TextView renamePageBtn = dialogView.findViewById(R.id.btn_config_ui_rename_page);
        TextView deletePageBtn = dialogView.findViewById(R.id.btn_config_ui_delete_page);
        TextView pageScaleBtn = dialogView.findViewById(R.id.btn_config_ui_page_scale);
        TextView editComponentBtn = dialogView.findViewById(R.id.btn_config_ui_edit_component);
        TextView moveComponentBtn = dialogView.findViewById(R.id.btn_config_ui_move_component);
        TextView selectionHintView = dialogView.findViewById(R.id.tv_config_ui_selection_hint);
        ConfigUiCanvasEditorView canvasView = dialogView.findViewById(R.id.view_config_ui_canvas);
        final int[] currentPageIndex = {0};
        final Runnable[] refreshPageUiRef = new Runnable[1];

        nameInput.setText(TextUtils.isEmpty(workingSchema.name) ? cfg.operationName + " 配置" : workingSchema.name);
        Runnable refreshSelectionUi = () -> {
            ConfigUiComponent selectedComponent = canvasView == null ? null : canvasView.getSelectedComponent();
            boolean moveModeActive = canvasView != null && canvasView.isMoveModeEnabled();
            String selectedName = selectedComponent == null
                    ? ""
                    : host.abbreviate(TextUtils.isEmpty(selectedComponent.label)
                    ? selectedComponent.getDisplayTypeName()
                    : selectedComponent.label, 12);
            if (editComponentBtn != null) {
                editComponentBtn.setAlpha(selectedComponent == null ? 0.45f : 1f);
                editComponentBtn.setText(selectedComponent == null
                        ? "编辑已选组件"
                        : ("编辑 " + selectedName));
            }
            if (moveComponentBtn != null) {
                moveComponentBtn.setAlpha(selectedComponent == null ? 0.45f : 1f);
                moveComponentBtn.setText(moveModeActive
                        ? "结束移动"
                        : (selectedComponent == null ? "移动已选组件" : ("移动 " + selectedName)));
                moveComponentBtn.setTextColor(moveModeActive ? 0xFF0F766E : 0xFF2F4F7C);
            }
            if (selectionHintView != null) {
                if (selectedComponent == null) {
                    selectionHintView.setText("先点选一个组件，再编辑或移动；双指可直接缩放选中的组件。");
                } else if (moveModeActive) {
                    selectionHintView.setText("正在移动「" + selectedName + "」：单指拖动位置，双指可继续缩放，点“结束移动”退出。");
                } else {
                    selectionHintView.setText("已选中「" + selectedName + "」：点上方按钮进入移动，或直接双指缩放这个组件。");
                }
            }
        };
        if (canvasView != null) {
            canvasView.setListener(new ConfigUiCanvasEditorView.Listener() {
                @Override
                public void onEditComponent(ConfigUiComponent component) {
                    showConfigUiComponentEditDialog(component, () -> canvasView.setPage(
                            getSafeConfigUiPage(workingSchema, currentPageIndex[0])));
                }

                @Override
                public void onDeleteComponent(ConfigUiComponent component) {
                }

                @Override
                public void onCanvasChanged() {
                }

                @Override
                public void onSelectionChanged(@Nullable ConfigUiComponent component, boolean moveModeEnabled) {
                    refreshSelectionUi.run();
                }
            });
        }

        Runnable refreshPageUi = () -> {
            workingSchema.ensureDefaults();
            if (currentPageIndex[0] < 0) {
                currentPageIndex[0] = 0;
            }
            if (currentPageIndex[0] >= workingSchema.pages.size()) {
                currentPageIndex[0] = Math.max(0, workingSchema.pages.size() - 1);
            }
            populateConfigUiPageTabs(pageTabsScroll, pageTabsContainer, workingSchema, currentPageIndex[0], index -> {
                currentPageIndex[0] = index;
                if (refreshPageUiRef[0] != null) {
                    refreshPageUiRef[0].run();
                }
            });
            if (pageMetaView != null) {
                ConfigUiPage currentPage = getSafeConfigUiPage(workingSchema, currentPageIndex[0]);
                String title = currentPage == null || TextUtils.isEmpty(currentPage.title)
                        ? ("页面 " + (currentPageIndex[0] + 1))
                        : currentPage.title;
                pageMetaView.setText("第 " + (currentPageIndex[0] + 1) + " / " + workingSchema.pages.size()
                        + " 页 · 当前：" + title);
            }
            ConfigUiPage page = getSafeConfigUiPage(workingSchema, currentPageIndex[0]);
            if (canvasView != null) {
                canvasView.setPage(page);
            }
            if (pageScaleBtn != null) {
                int scalePercent = page == null ? 100 : page.scalePercent;
                pageScaleBtn.setText("缩放 " + scalePercent + "%");
            }
            deletePageBtn.setAlpha(workingSchema.pages.size() > 1 ? 1f : 0.45f);
            refreshSelectionUi.run();
        };
        refreshPageUiRef[0] = refreshPageUi;
        refreshPageUi.run();

        addPageBtn.setOnClickListener(v -> showSimpleOverlayTextInputDialog(
                "新增页面",
                "页面名称",
                "页面 " + (workingSchema.pages.size() + 1),
                value -> {
                    workingSchema.pages.add(ConfigUiPage.createDefault(
                            TextUtils.isEmpty(value) ? ("页面 " + (workingSchema.pages.size() + 1)) : value));
                    currentPageIndex[0] = workingSchema.pages.size() - 1;
                    refreshPageUi.run();
                }));
        renamePageBtn.setOnClickListener(v -> {
            ConfigUiPage page = getSafeConfigUiPage(workingSchema, currentPageIndex[0]);
            if (page == null) {
                return;
            }
            showSimpleOverlayTextInputDialog(
                    "页面改名",
                    "新的页面名称",
                    page.title,
                    value -> {
                        page.title = TextUtils.isEmpty(value) ? page.title : value.trim();
                        refreshPageUi.run();
                    });
        });
        deletePageBtn.setOnClickListener(v -> {
            if (workingSchema.pages.size() <= 1) {
                host.showToast("至少保留一个页面");
                return;
            }
            workingSchema.pages.remove(currentPageIndex[0]);
            currentPageIndex[0] = Math.max(0, currentPageIndex[0] - 1);
            refreshPageUi.run();
        });
        if (pageScaleBtn != null) {
            pageScaleBtn.setOnClickListener(v -> {
                ConfigUiPage page = getSafeConfigUiPage(workingSchema, currentPageIndex[0]);
                if (page == null) {
                    return;
                }
                showSimpleOverlayTextInputDialog(
                        "页面整体缩放",
                        "输入百分比，建议 50-150",
                        String.valueOf(page.scalePercent),
                        value -> {
                            int scale = parseIntDefault(value, page.scalePercent);
                            page.scalePercent = Math.max(40, Math.min(200, scale));
                            refreshPageUi.run();
                        });
            });
        }
        if (editComponentBtn != null) {
            editComponentBtn.setOnClickListener(v -> {
                ConfigUiComponent selectedComponent = canvasView == null ? null : canvasView.getSelectedComponent();
                if (selectedComponent == null) {
                    host.showToast("先选中一个组件");
                    return;
                }
                showConfigUiComponentEditDialog(selectedComponent, () -> {
                    if (canvasView != null) {
                        canvasView.setPage(getSafeConfigUiPage(workingSchema, currentPageIndex[0]));
                    }
                    refreshSelectionUi.run();
                });
            });
        }
        if (moveComponentBtn != null) {
            moveComponentBtn.setOnClickListener(v -> {
                if (canvasView == null) {
                    return;
                }
                if (canvasView.isMoveModeEnabled()) {
                    canvasView.endMoveMode();
                    return;
                }
                if (!canvasView.beginMoveSelectedComponent()) {
                    host.showToast("先选中一个组件");
                }
            });
        }

        bindConfigUiPaletteSource(dialogView.findViewById(R.id.btn_add_text_component),
                workingSchema, currentPageIndex, canvasView, ConfigUiComponent.TYPE_TEXT);
        bindConfigUiPaletteSource(dialogView.findViewById(R.id.btn_add_number_component),
                workingSchema, currentPageIndex, canvasView, ConfigUiComponent.TYPE_NUMBER);
        bindConfigUiPaletteSource(dialogView.findViewById(R.id.btn_add_switch_component),
                workingSchema, currentPageIndex, canvasView, ConfigUiComponent.TYPE_SWITCH);
        bindConfigUiPaletteSource(dialogView.findViewById(R.id.btn_add_select_component),
                workingSchema, currentPageIndex, canvasView, ConfigUiComponent.TYPE_SELECT);
        bindConfigUiPaletteSource(dialogView.findViewById(R.id.btn_add_textarea_component),
                workingSchema, currentPageIndex, canvasView, ConfigUiComponent.TYPE_TEXTAREA);
        bindConfigUiPaletteSource(dialogView.findViewById(R.id.btn_add_array_component),
                workingSchema, currentPageIndex, canvasView, ConfigUiComponent.TYPE_ARRAY);
        bindConfigUiPaletteSource(dialogView.findViewById(R.id.btn_add_title_component),
                workingSchema, currentPageIndex, canvasView, ConfigUiComponent.TYPE_TITLE);

        if (canvasView != null) {
            bindConfigUiDropTarget(canvasView, workingSchema, currentPageIndex, canvasView);
        }

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }

        dialogView.findViewById(R.id.btn_preview_config_ui).setOnClickListener(v -> {
            workingSchema.name = readTrimmedText(nameInput, cfg.operationName + " 配置");
            String error = validateConfigUiSchema(workingSchema);
            if (!TextUtils.isEmpty(error)) {
                host.showToast(error);
                return;
            }
            showVisualNodeRuntimeConfigDialog(cfg, cloneConfigUiSchema(workingSchema), "ConfigUI 预览");
        });
        dialogView.findViewById(R.id.btn_config_ui_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_config_ui_save).setOnClickListener(v -> {
            workingSchema.name = readTrimmedText(nameInput, cfg.operationName + " 配置");
            String error = validateConfigUiSchema(workingSchema);
            if (!TextUtils.isEmpty(error)) {
                host.showToast(error);
                return;
            }
            NodeFloatButtonConfig updated = nodeFloatBtnManager.getConfig(cfg.operationId);
            if (updated == null) {
                updated = cfg;
            }
            updated.ensureDefaults();
            ensureNodeConfigOwnership(updated);
            updated.configUiSchemaId = schemaId;
            nodeFloatBtnManager.saveConfig(updated);
            ConfigUiSchema schemaToSave = cloneConfigUiSchema(workingSchema);
            stampSchemaOwnership(schemaToSave, updated);
            configUiStore.saveSchema(schemaToSave);
            dialog.dismiss();
            host.showToast("ConfigUI 已绑定到节点");
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            int maxHeight = Math.min(
                    host.dp(860),
                    (int) (host.getContext().getResources().getDisplayMetrics().heightPixels * 0.94f));
            dialog.getWindow().setLayout(
                    Math.min(host.dp(760), (int) (host.getContext().getResources().getDisplayMetrics().widthPixels * 0.99f)),
                    maxHeight);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    boolean shouldRetainNodeConfigMetadata(@Nullable NodeFloatButtonConfig cfg) {
        if (cfg == null) {
            return false;
        }
        cfg.ensureDefaults();
        return !TextUtils.isEmpty(cfg.configUiSchemaId)
                || !TextUtils.isEmpty(cfg.runtimeVariablesText);
    }

    NodeFloatButtonConfig getOrCreateNodeConfig(OperationItem item, boolean enableButton) {
        NodeFloatButtonManager nodeFloatBtnManager = host.getNodeFloatButtonManager();
        NodeFloatButtonConfig existing = nodeFloatBtnManager == null || item == null
                ? null
                : nodeFloatBtnManager.getConfig(item.id);
        if (existing != null) {
            existing.ensureDefaults();
            if (enableButton) {
                existing.buttonEnabled = true;
            }
            File currentProjectDir = host.getCurrentProjectDir();
            File currentTaskDir = host.getCurrentTaskDir();
            if (TextUtils.isEmpty(existing.projectName) && currentProjectDir != null) {
                existing.projectName = currentProjectDir.getName();
            }
            if (TextUtils.isEmpty(existing.taskName) && currentTaskDir != null) {
                existing.taskName = currentTaskDir.getName();
            }
            if (TextUtils.isEmpty(existing.operationName)) {
                existing.operationName = item.name;
            }
            return existing;
        }

        File currentProjectDir = host.getCurrentProjectDir();
        File currentTaskDir = host.getCurrentTaskDir();
        WindowManager.LayoutParams ballLp = host.getBallLayoutParams();
        String projectName = currentProjectDir != null ? currentProjectDir.getName() : "";
        String taskName = currentTaskDir != null ? currentTaskDir.getName() : "";
        int defaultX = ballLp != null ? ballLp.x + host.dp(60) : 160;
        int defaultY = ballLp != null ? ballLp.y : 400;
        NodeFloatButtonConfig created = new NodeFloatButtonConfig(
                item.id,
                item.name,
                projectName,
                taskName,
                host.getDefaultNodeButtonColor(),
                defaultX,
                defaultY);
        created.ensureDefaults();
        created.buttonEnabled = enableButton;
        return created;
    }

    void applyNodeRuntimeVariables(OperationContext ctx, @Nullable NodeFloatButtonConfig cfg) {
        if (ctx == null || cfg == null) {
            return;
        }
        cfg.ensureDefaults();
        if (ctx.variables == null) {
            ctx.variables = new HashMap<>();
        }
        if (TextUtils.isEmpty(cfg.runtimeVariablesText)) {
            ctx.variables.put("configMap", new HashMap<String, Object>());
            return;
        }
        Map<String, Object> configMap = new HashMap<>();
        String[] lines = cfg.runtimeVariablesText.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                Object coerced = coerceNodeRuntimeValue(value);
                ctx.variables.put(key, coerced);
                configMap.put(key, coerced);
            }
        }
        ctx.variables.put("configMap", configMap);
    }

    private void showLegacyNodeRuntimeConfigDialog(NodeFloatButtonConfig cfg) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_node_runtime_config, null);
        WindowManager.LayoutParams dialogLp = host.buildDialogLayoutParams(320, true);
        host.getWindowManager().addView(dialogView, dialogLp);

        TextView titleView = dialogView.findViewById(R.id.tv_runtime_cfg_title);
        EditText variablesInput = dialogView.findViewById(R.id.et_runtime_variables);
        if (titleView != null) {
            titleView.setText("运行配置: " + host.abbreviate(cfg.operationName, 14));
        }
        if (variablesInput != null) {
            variablesInput.setText(cfg.runtimeVariablesText == null ? "" : cfg.runtimeVariablesText);
            variablesInput.setSelection(variablesInput.getText() == null ? 0 : variablesInput.getText().length());
            variablesInput.setHint("每行 key=value\n# 注释行\n\n脚本中可通过：\n  vars.myKey        （独立变量）\n  vars.configMap.myKey （字典访问）");
        }

        dialogView.findViewById(R.id.btn_runtime_cfg_clear).setOnClickListener(v -> {
            if (variablesInput != null) {
                variablesInput.setText("");
            }
        });
        dialogView.findViewById(R.id.btn_runtime_cfg_cancel).setOnClickListener(v -> host.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_runtime_cfg_save).setOnClickListener(v -> {
            String raw = variablesInput == null || variablesInput.getText() == null
                    ? ""
                    : variablesInput.getText().toString();
            List<String> invalidLines = collectInvalidRuntimeVariableLines(raw);
            if (!invalidLines.isEmpty()) {
                host.showToast("格式错误，请检查第 " + invalidLines.get(0) + " 行");
                return;
            }
            NodeFloatButtonManager nodeFloatBtnManager = host.getNodeFloatButtonManager();
            NodeFloatButtonConfig updated = nodeFloatBtnManager == null ? null : nodeFloatBtnManager.getConfig(cfg.operationId);
            if (updated == null) {
                updated = cfg;
            }
            updated.ensureDefaults();
            ensureNodeConfigOwnership(updated);
            updated.runtimeVariablesText = raw.trim();
            if (nodeFloatBtnManager != null) {
                nodeFloatBtnManager.saveConfig(updated);
            }
            host.safeRemoveView(dialogView);
            host.showToast("运行配置已保存");
        });
    }

    private void showVisualNodeRuntimeConfigDialog(NodeFloatButtonConfig cfg,
                                                   ConfigUiSchema schema,
                                                   @Nullable String titleOverride) {
        if (cfg == null || schema == null) {
            return;
        }
        cfg.ensureDefaults();
        schema.ensureDefaults();
        Map<String, String> baseValues = ConfigUiValueCodec.parse(cfg.runtimeVariablesText);
        ConfigUiFormRenderer.FormSession session =
                ConfigUiFormRenderer.create(host.getContext(), schema, baseValues);
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_config_ui_runtime, null);
        WindowManager.LayoutParams dialogLp = host.buildDialogLayoutParams(380, true);
        dialogLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        host.getWindowManager().addView(dialogView, dialogLp);

        TextView titleView = dialogView.findViewById(R.id.tv_config_ui_runtime_title);
        FrameLayout contentContainer = dialogView.findViewById(R.id.layout_config_ui_runtime_content);
        if (titleView != null) {
            titleView.setText(TextUtils.isEmpty(titleOverride)
                    ? ("运行配置: " + host.abbreviate(cfg.operationName, 14))
                    : titleOverride);
        }
        if (contentContainer != null) {
            contentContainer.addView(session.getRootView(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        dialogView.findViewById(R.id.btn_config_ui_runtime_cancel)
                .setOnClickListener(v -> host.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_config_ui_runtime_text_mode)
                .setOnClickListener(v -> {
                    host.safeRemoveView(dialogView);
                    showLegacyNodeRuntimeConfigDialog(cfg);
                });
        dialogView.findViewById(R.id.btn_config_ui_runtime_save)
                .setOnClickListener(v -> {
                    List<String> missingRequired = session.findMissingRequiredFields(schema);
                    if (!missingRequired.isEmpty()) {
                        host.showToast("请先填写必填项：" + missingRequired.get(0));
                        return;
                    }
                    Map<String, String> merged = ConfigUiValueCodec.merge(
                            baseValues,
                            session.collectValues(),
                            schema.collectFieldKeys());
                    NodeFloatButtonManager nodeFloatBtnManager = host.getNodeFloatButtonManager();
                    NodeFloatButtonConfig updated = nodeFloatBtnManager == null ? null : nodeFloatBtnManager.getConfig(cfg.operationId);
                    if (updated == null) {
                        updated = cfg;
                    }
                    updated.ensureDefaults();
                    ensureNodeConfigOwnership(updated);
                    updated.runtimeVariablesText = ConfigUiValueCodec.encode(merged);
                    if (nodeFloatBtnManager != null) {
                        nodeFloatBtnManager.saveConfig(updated);
                    }
                    host.safeRemoveView(dialogView);
                    host.showToast("可视化配置已保存");
                });
    }

    @Nullable
    private ConfigUiSchema resolveNodeConfigUiSchema(@Nullable NodeFloatButtonConfig cfg) {
        ConfigUiStore configUiStore = host.getConfigUiStore();
        if (cfg == null || configUiStore == null) {
            return null;
        }
        cfg.ensureDefaults();
        ensureNodeConfigOwnership(cfg);
        if (TextUtils.isEmpty(cfg.configUiSchemaId)) {
            return null;
        }
        ConfigUiSchema schema = configUiStore.getSchema(cfg.configUiSchemaId);
        if (schema != null) {
            stampSchemaOwnership(schema, cfg);
        }
        return schema;
    }

    private static final class ConfigUiPaletteDragPayload {
        final String componentType;

        ConfigUiPaletteDragPayload(String componentType) {
            this.componentType = componentType;
        }
    }

    private void bindConfigUiPaletteSource(View source,
                                           ConfigUiSchema schema,
                                           int[] currentPageIndex,
                                           @Nullable ConfigUiCanvasEditorView canvasView,
                                           String componentType) {
        if (source == null) {
            return;
        }
        source.setOnClickListener(v -> {
            ConfigUiPage page = getSafeConfigUiPage(schema, currentPageIndex[0]);
            if (page == null || canvasView == null) {
                return;
            }
            canvasView.setPage(page);
            canvasView.addComponent(componentType, host.dp(48), host.dp(48));
        });
        source.setOnLongClickListener(v -> {
            ClipData clipData = ClipData.newPlainText(CONFIG_UI_DRAG_LABEL, componentType);
            ConfigUiPaletteDragPayload payload = new ConfigUiPaletteDragPayload(componentType);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(clipData, new View.DragShadowBuilder(v), payload, 0);
            } else {
                v.startDrag(clipData, new View.DragShadowBuilder(v), payload, 0);
            }
            return true;
        });
    }

    private void bindConfigUiDropTarget(View dropZone,
                                        ConfigUiSchema schema,
                                        int[] currentPageIndex,
                                        @Nullable ConfigUiCanvasEditorView canvasView) {
        if (dropZone == null) {
            return;
        }
        dropZone.setOnDragListener((v, event) -> {
            String componentType = extractConfigUiDragComponentType(event);
            if (TextUtils.isEmpty(componentType)) {
                return false;
            }
            switch (event.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                case android.view.DragEvent.ACTION_DRAG_LOCATION:
                    if (canvasView != null) {
                        canvasView.setDropActive(true);
                    }
                    return true;
                case android.view.DragEvent.ACTION_DRAG_EXITED:
                    if (canvasView != null) {
                        canvasView.setDropActive(false);
                    }
                    return true;
                case android.view.DragEvent.ACTION_DROP:
                    ConfigUiPage page = getSafeConfigUiPage(schema, currentPageIndex[0]);
                    if (canvasView != null && page != null) {
                        canvasView.setPage(page);
                        canvasView.addComponent(componentType, event.getX(), event.getY());
                        canvasView.setDropActive(false);
                    }
                    return true;
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    if (canvasView != null) {
                        canvasView.setDropActive(false);
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    @Nullable
    private String extractConfigUiDragComponentType(android.view.DragEvent event) {
        if (event == null) {
            return null;
        }
        Object localState = event.getLocalState();
        if (localState instanceof ConfigUiPaletteDragPayload) {
            return ((ConfigUiPaletteDragPayload) localState).componentType;
        }
        ClipData clipData = event.getClipData();
        if (clipData != null && clipData.getItemCount() > 0 && clipData.getItemAt(0) != null) {
            CharSequence text = clipData.getItemAt(0).getText();
            return text == null ? null : text.toString();
        }
        return null;
    }

    @Nullable
    private ConfigUiPage getSafeConfigUiPage(ConfigUiSchema schema, int index) {
        if (schema == null) {
            return null;
        }
        schema.ensureDefaults();
        if (index < 0 || index >= schema.pages.size()) {
            return null;
        }
        ConfigUiPage page = schema.pages.get(index);
        if (page != null) {
            page.ensureDefaults();
        }
        return page;
    }

    private void populateConfigUiPageTabs(@Nullable HorizontalScrollView scrollView,
                                          @Nullable LinearLayout container,
                                          ConfigUiSchema schema,
                                          int selectedIndex,
                                          @Nullable java.util.function.IntConsumer onSelected) {
        if (container == null || schema == null) {
            return;
        }
        schema.ensureDefaults();
        container.removeAllViews();
        Context context = host.getContext();
        int safeIndex = Math.max(0, Math.min(selectedIndex, Math.max(0, schema.pages.size() - 1)));
        for (int i = 0; i < schema.pages.size(); i++) {
            ConfigUiPage page = schema.pages.get(i);
            String title = page == null || TextUtils.isEmpty(page.title)
                    ? ("页面 " + (i + 1))
                    : page.title;
            TextView tabView = new TextView(context);
            tabView.setText(title);
            tabView.setSingleLine(true);
            tabView.setEllipsize(TextUtils.TruncateAt.END);
            tabView.setTypeface(tabView.getTypeface(), i == safeIndex ? Typeface.BOLD : Typeface.NORMAL);
            tabView.setPadding(host.dp(14), host.dp(9), host.dp(14), host.dp(9));
            applyConfigUiPageTabStyle(tabView, i == safeIndex);
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            if (i > 0) {
                tabLp.leftMargin = host.dp(8);
            }
            final int pageIndex = i;
            tabView.setOnClickListener(v -> {
                if (onSelected != null) {
                    onSelected.accept(pageIndex);
                }
            });
            container.addView(tabView, tabLp);
            if (i == safeIndex && scrollView != null) {
                tabView.post(() -> {
                    int scrollX = Math.max(0, tabView.getLeft() - host.dp(12));
                    scrollView.smoothScrollTo(scrollX, 0);
                });
            }
        }
    }

    private void applyConfigUiPageTabStyle(TextView view, boolean selected) {
        if (view == null) {
            return;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(host.dp(14));
        bg.setColor(selected ? 0xFF3C6DE4 : 0xFFF3F6FA);
        bg.setStroke(host.dp(1), selected ? 0xFF2C58C7 : 0xFFD7E1EB);
        view.setBackground(bg);
        view.setTextColor(selected ? 0xFFFFFFFF : 0xFF526273);
    }

    private void showConfigUiComponentEditDialog(ConfigUiComponent component, Runnable onSaved) {
        if (component == null) {
            return;
        }
        component.ensureDefaults();
        Context ctx = new android.view.ContextThemeWrapper(host.getContext(), R.style.Theme_AtomMaster);
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_config_ui_component_edit, null);
        TextView typeView = view.findViewById(R.id.tv_component_type);
        EditText labelInput = view.findViewById(R.id.et_component_label);
        EditText keyInput = view.findViewById(R.id.et_component_key);
        EditText placeholderInput = view.findViewById(R.id.et_component_placeholder);
        EditText defaultInput = view.findViewById(R.id.et_component_default);
        EditText helperInput = view.findViewById(R.id.et_component_helper);
        EditText accentColorInput = view.findViewById(R.id.et_component_accent_color);
        AutoCompleteTextView displayStyleInput = view.findViewById(R.id.actv_component_display_style);
        EditText widthInput = view.findViewById(R.id.et_component_width);
        EditText heightInput = view.findViewById(R.id.et_component_height);
        EditText scaleInput = view.findViewById(R.id.et_component_scale);
        EditText maxLinesInput = view.findViewById(R.id.et_component_max_lines);
        EditText unitSuffixInput = view.findViewById(R.id.et_component_unit_suffix);
        View numberRulesLayout = view.findViewById(R.id.layout_component_number_rules);
        EditText numberMinInput = view.findViewById(R.id.et_component_number_min);
        EditText numberMaxInput = view.findViewById(R.id.et_component_number_max);
        EditText numberStepInput = view.findViewById(R.id.et_component_number_step);
        TextView switchThemeLabel = view.findViewById(R.id.tv_switch_theme_label);
        EditText switchOnColorInput = view.findViewById(R.id.et_switch_on_color);
        EditText switchOffColorInput = view.findViewById(R.id.et_switch_off_color);
        EditText switchThumbColorInput = view.findViewById(R.id.et_switch_thumb_color);
        View sizeLayout = view.findViewById(R.id.layout_component_size);
        TextView optionsLabel = view.findViewById(R.id.tv_component_options_label);
        EditText optionsInput = view.findViewById(R.id.et_component_options);

        typeView.setText("组件类型：" + component.getDisplayTypeName());
        labelInput.setText(component.label);
        keyInput.setText(component.fieldKey);
        placeholderInput.setText(component.placeholder);
        defaultInput.setText(component.defaultValue);
        helperInput.setText(component.helperText);
        accentColorInput.setText(component.accentColor);
        displayStyleInput.setAdapter(new ArrayAdapter<>(
                ctx,
                android.R.layout.simple_list_item_1,
                new String[] {
                        ConfigUiComponent.DISPLAY_STYLE_AUTO,
                        ConfigUiComponent.DISPLAY_STYLE_DROPDOWN,
                        ConfigUiComponent.DISPLAY_STYLE_CHIPS
                }));
        displayStyleInput.setText(component.displayStyle, false);
        displayStyleInput.setOnClickListener(v -> displayStyleInput.showDropDown());
        optionsInput.setText(joinConfigUiOptions(component.options));
        widthInput.setText(String.valueOf(component.widthDp));
        heightInput.setText(String.valueOf(component.heightDp));
        scaleInput.setText(String.valueOf(component.scalePercent));
        maxLinesInput.setText(String.valueOf(component.maxLines));
        unitSuffixInput.setText(component.unitSuffix);
        numberMinInput.setText(component.numberMin);
        numberMaxInput.setText(component.numberMax);
        numberStepInput.setText(component.numberStep);
        switchOnColorInput.setText(component.switchOnColor);
        switchOffColorInput.setText(component.switchOffColor);
        switchThumbColorInput.setText(component.switchThumbColor);

        boolean isTitle = ConfigUiComponent.TYPE_TITLE.equals(component.type);
        boolean isTextarea = ConfigUiComponent.TYPE_TEXTAREA.equals(component.type);
        boolean isSelect = ConfigUiComponent.TYPE_SELECT.equals(component.type);
        boolean isArray = ConfigUiComponent.TYPE_ARRAY.equals(component.type);
        boolean isSwitch = ConfigUiComponent.TYPE_SWITCH.equals(component.type);
        boolean isNumber = ConfigUiComponent.TYPE_NUMBER.equals(component.type);
        keyInput.setVisibility(isTitle ? View.GONE : View.VISIBLE);
        placeholderInput.setVisibility(isTitle ? View.GONE : View.VISIBLE);
        defaultInput.setVisibility(isTitle ? View.GONE : View.VISIBLE);
        sizeLayout.setVisibility(View.VISIBLE);
        accentColorInput.setVisibility(View.VISIBLE);
        displayStyleInput.setVisibility(isSelect ? View.VISIBLE : View.GONE);
        maxLinesInput.setVisibility((isTextarea || isArray || (!isTitle && !isSwitch && !isSelect && !isNumber)) ? View.VISIBLE : View.GONE);
        unitSuffixInput.setVisibility(isNumber ? View.VISIBLE : View.GONE);
        numberRulesLayout.setVisibility(isNumber ? View.VISIBLE : View.GONE);
        switchThemeLabel.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
        switchOnColorInput.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
        switchOffColorInput.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
        switchThumbColorInput.setVisibility(isSwitch ? View.VISIBLE : View.GONE);
        optionsLabel.setVisibility(isSelect ? View.VISIBLE : View.GONE);
        optionsInput.setVisibility(isSelect ? View.VISIBLE : View.GONE);
        if (isArray) {
            placeholderInput.setHint("每行一个元素，或粘贴 JSON 数组");
            defaultInput.setHint("默认值，例如 [\"A\",\"B\",1]");
            helperInput.setHint("辅助说明，例如：脚本里可直接用 vars.myArray[0]");
        } else if (isTextarea) {
            placeholderInput.setHint("多行输入提示");
            defaultInput.setHint("默认值，可直接写多行文本");
            helperInput.setHint("辅助说明，例如：支持粘贴说明、脚本片段等");
        } else if (isSwitch) {
            defaultInput.setHint("默认值，例如 true 或 false");
        } else if (isNumber) {
            defaultInput.setHint("默认值，例如 10 或 0.5");
        }

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle("编辑组件")
                .setView(view)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        Math.min(host.dp(500), (int) (host.getContext().getResources().getDisplayMetrics().widthPixels * 0.96f)),
                        Math.min(host.dp(760), (int) (host.getContext().getResources().getDisplayMetrics().heightPixels * 0.92f)));
            }
            TextView positiveBtn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            if (positiveBtn == null) {
                return;
            }
            final boolean[] saving = {false};
            positiveBtn.setOnClickListener(v -> {
                if (saving[0]) {
                    return;
                }
                saving[0] = true;
                v.setEnabled(false);
                positiveBtn.setText("保存中...");
                clearFocusAndHideKeyboard(view);
                view.post(() -> {
                    try {
                        String label = readTrimmedText(labelInput, component.getDisplayTypeName());
                        String key = readTrimmedText(keyInput, "");
                        if (!isTitle && TextUtils.isEmpty(key)) {
                            host.showToast("请输入字段 Key");
                            return;
                        }
                        List<ConfigUiOption> options = isSelect
                                ? parseConfigUiOptions(optionsInput.getText() == null ? "" : optionsInput.getText().toString())
                                : new ArrayList<>();
                        if (isSelect && options.isEmpty()) {
                            host.showToast("下拉组件至少需要一个选项");
                            return;
                        }
                        component.label = label;
                        component.fieldKey = isTitle ? "" : key;
                        component.placeholder = isTitle ? "" : readTrimmedText(placeholderInput, "");
                        component.defaultValue = isTitle ? "" : readTrimmedText(defaultInput, "");
                        component.helperText = readTrimmedText(helperInput, "");
                        component.accentColor = normalizeConfigUiColorInput(
                                readTrimmedText(accentColorInput, component.accentColor),
                                ConfigUiComponent.defaultAccentColorForType(component.type));
                        component.displayStyle = isSelect
                                ? readTrimmedText(displayStyleInput, ConfigUiComponent.DISPLAY_STYLE_AUTO)
                                : ConfigUiComponent.DISPLAY_STYLE_AUTO;
                        component.widthDp = Math.max(80, parseIntDefault(readTrimmedText(widthInput,
                                String.valueOf(ConfigUiComponent.defaultWidthForType(component.type))),
                                ConfigUiComponent.defaultWidthForType(component.type)));
                        component.heightDp = Math.max(48, parseIntDefault(readTrimmedText(heightInput,
                                String.valueOf(ConfigUiComponent.defaultHeightForType(component.type))),
                                ConfigUiComponent.defaultHeightForType(component.type)));
                        component.scalePercent = Math.max(40, Math.min(200, parseIntDefault(
                                readTrimmedText(scaleInput, String.valueOf(component.scalePercent)),
                                component.scalePercent)));
                        component.maxLines = Math.max(1, parseIntDefault(
                                readTrimmedText(maxLinesInput, String.valueOf(component.maxLines)),
                                component.maxLines));
                        component.unitSuffix = isNumber ? readTrimmedText(unitSuffixInput, "") : "";
                        component.numberMin = isNumber ? readTrimmedText(numberMinInput, "") : "";
                        component.numberMax = isNumber ? readTrimmedText(numberMaxInput, "") : "";
                        component.numberStep = isNumber ? readTrimmedText(numberStepInput, "1") : "";
                        component.switchOnColor = isSwitch
                                ? normalizeConfigUiColorInput(readTrimmedText(switchOnColorInput, component.switchOnColor), "#16A34A")
                                : "";
                        component.switchOffColor = isSwitch
                                ? normalizeConfigUiColorInput(readTrimmedText(switchOffColorInput, component.switchOffColor), "#64748B")
                                : "";
                        component.switchThumbColor = isSwitch
                                ? normalizeConfigUiColorInput(readTrimmedText(switchThumbColorInput, component.switchThumbColor), "#FDE68A")
                                : "";
                        component.options = options;
                        component.ensureDefaults();
                        dialog.dismiss();
                        if (onSaved != null) {
                            onSaved.run();
                        }
                        host.showToast("组件已保存");
                    } finally {
                        if (dialog.isShowing()) {
                            saving[0] = false;
                            v.setEnabled(true);
                            positiveBtn.setText("保存");
                        }
                    }
                });
            });
        });
        dialog.show();
    }

    private void showConfigUiPagePickerDialog(ConfigUiSchema schema,
                                              int selectedIndex,
                                              java.util.function.IntConsumer onSelected) {
        if (schema == null) {
            return;
        }
        schema.ensureDefaults();
        Context ctx = new android.view.ContextThemeWrapper(host.getContext(), R.style.Theme_AtomMaster);
        String[] pageTitles = new String[schema.pages.size()];
        for (int i = 0; i < schema.pages.size(); i++) {
            ConfigUiPage page = schema.pages.get(i);
            pageTitles[i] = (i + 1) + ". " + (page == null || TextUtils.isEmpty(page.title) ? "页面" : page.title);
        }
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle("切换页面")
                .setSingleChoiceItems(pageTitles, Math.max(0, Math.min(selectedIndex, pageTitles.length - 1)),
                        (d, which) -> {
                            if (onSelected != null) {
                                onSelected.accept(which);
                            }
                            d.dismiss();
                        })
                .setNegativeButton("取消", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

    private String normalizeConfigUiColorInput(String raw, String fallback) {
        String value = TextUtils.isEmpty(raw) ? fallback : raw.trim();
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        try {
            Color.parseColor(value);
            return value.toUpperCase(Locale.ROOT);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void showSimpleOverlayTextInputDialog(String title,
                                                  String hint,
                                                  String initialValue,
                                                  java.util.function.Consumer<String> onSubmit) {
        Context ctx = new android.view.ContextThemeWrapper(host.getContext(), R.style.Theme_AtomMaster);
        EditText input = new EditText(ctx);
        input.setHint(hint);
        input.setText(initialValue);
        if (input.getText() != null) {
            input.setSelection(input.getText().length());
        }
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (onSubmit != null) {
                        onSubmit.accept(value);
                    }
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void clearFocusAndHideKeyboard(View view) {
        if (view == null) {
            return;
        }
        view.clearFocus();
        View focused = view.findFocus();
        if (focused != null) {
            focused.clearFocus();
        }
        InputMethodManager imm = (InputMethodManager) host.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View target = focused != null ? focused : view;
            imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
        }
    }

    private String ensureNodeConfigUiSchemaId(NodeFloatButtonConfig cfg) {
        cfg.ensureDefaults();
        if (TextUtils.isEmpty(cfg.configUiSchemaId)) {
            cfg.configUiSchemaId = cfg.operationId + "_config_ui";
        }
        return cfg.configUiSchemaId;
    }

    private void ensureNodeConfigOwnership(@Nullable NodeFloatButtonConfig cfg) {
        if (cfg == null) {
            return;
        }
        File currentProjectDir = host.getCurrentProjectDir();
        File currentTaskDir = host.getCurrentTaskDir();
        if (TextUtils.isEmpty(cfg.projectName) && currentProjectDir != null) {
            cfg.projectName = currentProjectDir.getName();
        }
        if (TextUtils.isEmpty(cfg.taskName) && currentTaskDir != null) {
            cfg.taskName = currentTaskDir.getName();
        }
    }

    private void stampSchemaOwnership(@Nullable ConfigUiSchema schema,
                                      @Nullable NodeFloatButtonConfig cfg) {
        if (schema == null) {
            return;
        }
        if (cfg != null) {
            ensureNodeConfigOwnership(cfg);
        }
        File currentProjectDir = host.getCurrentProjectDir();
        File currentTaskDir = host.getCurrentTaskDir();
        if (TextUtils.isEmpty(schema.projectName)) {
            if (cfg != null && !TextUtils.isEmpty(cfg.projectName)) {
                schema.projectName = cfg.projectName;
            } else if (currentProjectDir != null) {
                schema.projectName = currentProjectDir.getName();
            }
        }
        if (TextUtils.isEmpty(schema.taskName)) {
            if (cfg != null && !TextUtils.isEmpty(cfg.taskName)) {
                schema.taskName = cfg.taskName;
            } else if (currentTaskDir != null) {
                schema.taskName = currentTaskDir.getName();
            }
        }
    }

    private String validateConfigUiSchema(ConfigUiSchema schema) {
        if (schema == null) {
            return "配置 UI 无效";
        }
        schema.ensureDefaults();
        Set<String> usedKeys = new HashSet<>();
        for (ConfigUiPage page : schema.pages) {
            if (page == null || page.components == null) {
                continue;
            }
            for (ConfigUiComponent component : page.components) {
                if (component == null) {
                    continue;
                }
                component.ensureDefaults();
                if (!component.bindsValue()) {
                    continue;
                }
                String key = component.fieldKey == null ? "" : component.fieldKey.trim();
                if (key.isEmpty()) {
                    return "存在未填写字段 Key 的组件";
                }
                if (!usedKeys.add(key)) {
                    return "字段 Key 重复：" + key;
                }
                if (ConfigUiComponent.TYPE_SELECT.equals(component.type)
                        && (component.options == null || component.options.isEmpty())) {
                    return "下拉组件至少需要一个选项";
                }
                if (ConfigUiComponent.TYPE_NUMBER.equals(component.type)
                        && !TextUtils.isEmpty(component.numberMin)
                        && !TextUtils.isEmpty(component.numberMax)) {
                    try {
                        if (Double.parseDouble(component.numberMin) > Double.parseDouble(component.numberMax)) {
                            return "数字组件的最小值不能大于最大值";
                        }
                    } catch (Exception ignored) {
                        return "数字组件的最小值/最大值格式不正确";
                    }
                }
            }
        }
        return null;
    }

    private ConfigUiSchema cloneConfigUiSchema(ConfigUiSchema schema) {
        Gson gson = new Gson();
        ConfigUiSchema cloned = gson.fromJson(gson.toJson(schema), ConfigUiSchema.class);
        if (cloned != null) {
            cloned.ensureDefaults();
        }
        return cloned == null
                ? ConfigUiSchema.createDefault(UUIDGenerator.prefixedUUID("cfgui"), "节点配置")
                : cloned;
    }

    private List<ConfigUiOption> parseConfigUiOptions(String raw) {
        List<ConfigUiOption> result = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                String label = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                if (!label.isEmpty() && !value.isEmpty()) {
                    result.add(new ConfigUiOption(label, value));
                }
            } else {
                result.add(new ConfigUiOption(trimmed, trimmed));
            }
        }
        return result;
    }

    private String joinConfigUiOptions(List<ConfigUiOption> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ConfigUiOption option : options) {
            if (option == null) {
                continue;
            }
            String label = option.label == null ? "" : option.label.trim();
            String value = option.value == null ? "" : option.value.trim();
            if (label.isEmpty() && value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            if (TextUtils.equals(label, value) || value.isEmpty()) {
                builder.append(label);
            } else {
                builder.append(label).append('=').append(value);
            }
        }
        return builder.toString();
    }

    private String readTrimmedText(@Nullable TextView view, String fallback) {
        if (view == null || view.getText() == null) {
            return fallback;
        }
        String value = view.getText().toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private List<String> collectInvalidRuntimeVariableLines(@Nullable String raw) {
        List<String> invalid = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return invalid;
        }
        String[] lines = raw.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                invalid.add(String.valueOf(i + 1));
                continue;
            }
            String key = line.substring(0, eq).trim();
            if (key.isEmpty()) {
                invalid.add(String.valueOf(i + 1));
            }
        }
        return invalid;
    }

    private static int parseIntDefault(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return def;
        }
    }

    private Object coerceNodeRuntimeValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                return jsonArrayToJavaList(new JSONArray(trimmed));
            } catch (Exception ignored) {
            }
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                return jsonObjectToJavaMap(new JSONObject(trimmed));
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

    private List<Object> jsonArrayToJavaList(JSONArray array) {
        List<Object> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            list.add(jsonValueToJava(array.opt(i)));
        }
        return list;
    }

    private Map<String, Object> jsonObjectToJavaMap(JSONObject object) {
        Map<String, Object> map = new HashMap<>();
        if (object == null) {
            return map;
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, jsonValueToJava(object.opt(key)));
        }
        return map;
    }

    private Object jsonValueToJava(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return null;
        }
        if (value instanceof JSONArray) {
            return jsonArrayToJavaList((JSONArray) value);
        }
        if (value instanceof JSONObject) {
            return jsonObjectToJavaMap((JSONObject) value);
        }
        return value;
    }
}
