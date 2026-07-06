package com.auto.master.floatwin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Base64;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;
import com.auto.master.Task.Handler.OperationHandler.OcrTextOperationHandler;
import com.auto.master.Task.Handler.OperationHandler.OperationHandler;
import com.auto.master.Task.Handler.OperationHandler.OperationHandlerManager;
import com.auto.master.Task.Operation.MatchMapTemplateOperation;
import com.auto.master.Task.Operation.MatchTemplateOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.capture.ScreenCapture;
import com.auto.master.floatwin.adapter.LaunchAppPickerAdapter;
import com.auto.master.floatwin.adapter.OperationIdPickerAdapter;
import com.auto.master.utils.AdaptivePollingController;
import com.auto.master.utils.RuntimeDisplayConfig;
import com.auto.master.utils.SystemRuntimeConfig;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Factory class for creating and managing operation dialogs.
 * Handles all Add/Edit dialog operations for different operation types.
 */
public class OperationDialogFactory {

    private final FloatWindowHost host;
    private final DialogHelpers dialogHelpers;
    private final OperationCrudHelper crudHelper;
    private final WindowManager wm;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Callbacks for dialog actions
    public interface OnOperationAddedListener {
        void onOperationAdded();
    }

    public interface OnOperationUpdatedListener {
        void onOperationUpdated();
    }

    public interface OperationIdGenerator {
        String generateId();
    }

    public interface NextOperationBinder {
        void bindNextOperationSuggestions(View dialogView, String excludeId);
    }

    public interface PointPickerLauncher {
        void showScreenPointPicker(PointPickerCallback callback, View... viewsToHide);
    }

    public interface PointPickerCallback {
        void onPointPicked(int x, int y);
    }

    public interface ColorPointPickerLauncher {
        void showColorPointPicker(ColorPointPickCallback callback, View... viewsToHide);
    }

    public interface ColorPointPickCallback {
        void onColorPointPicked(int x, int y, int color);
    }

    public interface OperationPickerLauncher {
        default void showOperationPickerDialog(String title, String excludeId, OperationSelectedCallback callback) {
            showOperationPickerDialog(title, excludeId, "", callback);
        }
        void showOperationPickerDialog(String title, String excludeId, String currentSelectedId, OperationSelectedCallback callback);
    }

    public interface OperationSelectedCallback {
        void onOperationSelected(String operationId);
    }

    public interface OperationUpdater {
        boolean saveOperationJson(String operationId, String newJson);
    }

    public interface GestureHelper {
        void refreshGestureOptions(android.widget.AutoCompleteTextView gestureInput);
        java.io.File resolveTaskGestureFile(String gestureFileName);
        String generateGestureTimestampName();
        void showGestureLibraryDialog(String currentFile, java.util.function.Consumer<String> onFileSelected);
        void beginPipelineStepRecord(View dialogView, boolean singleStroke, java.util.function.Consumer<String> onFileSaved);
        void beginPipelineContinuousRecord(View dialogView, java.util.function.Consumer<String> onFileSaved);
        void rerecordPipelineStep(View dialogView, boolean singleStroke, String oldFile, java.util.function.Consumer<String> onFileSaved);
        void rerecordPipelineContinuousRecord(View dialogView, String oldFile, java.util.function.Consumer<String> onFileSaved);
        void playGestureFile(View dialogView, String fileName, android.widget.TextView statusView);
        void playFullPipeline(View dialogView, org.json.JSONArray pipeline, android.widget.TextView statusView);
        com.auto.master.auto.GestureOverlayView.GestureNode readGestureFile(String fileName);
        String normalizeGestureFileName(String rawName);
    }

    public interface TaskOperationHelper {
        java.util.List<String> getCurrentProjectTaskIds();
        java.util.List<String> getTaskOperationIds(String taskId);
        String getOperationDisplayLabel(String operationId);

        default java.util.List<OperationIdPickerAdapter.OperationPickItem> getCurrentProjectTaskPickItems() {
            java.util.List<OperationIdPickerAdapter.OperationPickItem> items = new java.util.ArrayList<>();
            java.util.List<String> ids = getCurrentProjectTaskIds();
            if (ids != null) {
                for (String id : ids) {
                    if (!TextUtils.isEmpty(id)) {
                        items.add(new OperationIdPickerAdapter.OperationPickItem(items.size() + 1, id, id, "Task"));
                    }
                }
            }
            return items;
        }

        default java.util.List<OperationIdPickerAdapter.OperationPickItem> getTaskOperationPickItems(String taskId) {
            java.util.List<OperationIdPickerAdapter.OperationPickItem> items = new java.util.ArrayList<>();
            java.util.List<String> ids = getTaskOperationIds(taskId);
            if (ids != null) {
                for (String id : ids) {
                    if (!TextUtils.isEmpty(id)) {
                        items.add(new OperationIdPickerAdapter.OperationPickItem(items.size() + 1, id, id, "Operation"));
                    }
                }
            }
            return items;
        }

        default String getTaskDisplayLabel(String taskId) {
            return taskId == null ? "" : taskId;
        }

        default String getTaskOperationDisplayLabel(String taskId, String operationId) {
            return operationId == null ? "" : operationId;
        }
    }

    public interface TemplateHelper {
        void refreshTemplateOptions(android.widget.AutoCompleteTextView templateInput);
        void bindTemplatePreview(View dialogView, android.widget.AutoCompleteTextView templateInput);
        void renderRecentTemplateStrip(View dialogView, android.widget.AutoCompleteTextView templateInput);
        void setupAdvancedMatchSection(View dialogView, JSONObject operationObject, String excludeId);
        void fillAdvancedMatchInputMap(View dialogView, JSONObject inputMap);
        String generateTemplateTimestampName();
        void showTemplateLibraryDialog(android.widget.AutoCompleteTextView templateInput, View ownerDialog);
        void beginTemplateCaptureFromDialog(View dialogView, android.widget.AutoCompleteTextView edtTemplateFile);
    }

    public interface RegionPickHelper {
        void beginRegionPickFromDialog(View dialogView, android.widget.EditText edtBbox, android.widget.TextView statusView);
        java.util.List<Integer> parseBboxInput(String raw);
    }

    public interface VariableHelper {
        java.util.List<String> getVariableSourceModes();
        java.util.List<String> getVariableValueTypes();
        void bindVariableSourceModeWatcher(android.widget.AutoCompleteTextView modeView, android.widget.TextView labelView, android.widget.EditText valueView);
        String sourceModeValueToDisplay(String value);
        String sourceModeDisplayToValue(String display);
        void updateVariableSourceInputUi(android.widget.TextView labelView, android.widget.EditText valueView, String modeDisplay);
    }

    public interface VariableMathHelper {
        java.util.List<String> getVariableMathActions();
        boolean isUnaryMathAction(String action);
        void updateVariableMathOperandUi(android.widget.TextView labelView, android.widget.EditText valueView, String mode, String action);
        void bindVariableMathWatcher(android.widget.AutoCompleteTextView modeView, android.widget.AutoCompleteTextView actionView, android.widget.TextView labelView, android.widget.EditText valueView);
    }

    public interface MatchMapHelper {
        /** 启动全屏覆盖层让用户框选矩形区域，结果写回 edtBbox */
        void beginRegionPickForRow(View mainDialogView, EditText edtBbox);
        /** 打开多选模板库，currentSelected 为已选文件名列表，confirm 后回调 */
        void showTemplateMultiSelectDialog(java.util.List<String> currentSelected, OnMultiSelectConfirmed callback);
        /** 打开模板库单选，选中后将对应 manifest 中的 bbox 写入 edtBbox（无 bbox 则 toast） */
        void importBboxFromTemplate(View mainDialogView, EditText edtBbox);
        interface OnMultiSelectConfirmed {
            void onConfirmed(java.util.List<String> selectedFileNames);
        }
    }

    public interface LaunchAppHelper {
        void refreshAppOptions(AutoCompleteTextView view);
        LaunchAppPickerAdapter.LaunchAppItem findApp(String packageName);
        void showAppPicker(String title, String currentPackage, AutoCompleteTextView packageView,
                           EditText nameView, TextView summaryView);
        void updateAppSummary(TextView summaryView, String packageName);
        String normalizePackageName(String raw);
    }

    private MatchMapHelper matchMapHelper;

    private OnOperationAddedListener addListener;
    private OnOperationUpdatedListener updateListener;
    private OperationIdGenerator idGenerator;
    private NextOperationBinder nextOpBinder;
    private PointPickerLauncher pointPickerLauncher;
    private OperationPickerLauncher operationPickerLauncher;
    private ColorPointPickerLauncher colorPointPickerLauncher;
    private OperationUpdater operationUpdater;
    private GestureHelper gestureHelper;
    private TaskOperationHelper taskOperationHelper;
    private TemplateHelper templateHelper;
    private RegionPickHelper regionPickHelper;
    private VariableHelper variableHelper;
    private VariableMathHelper variableMathHelper;
    private LaunchAppHelper launchAppHelper;

    public OperationDialogFactory(FloatWindowHost host,
                                   DialogHelpers dialogHelpers,
                                   OperationCrudHelper crudHelper) {
        this.host = host;
        this.dialogHelpers = dialogHelpers;
        this.crudHelper = crudHelper;
        this.wm = host.getWindowManager();
    }

    // Setters for callbacks
    public void setOnOperationAddedListener(OnOperationAddedListener listener) {
        this.addListener = listener;
    }

    public void setOnOperationUpdatedListener(OnOperationUpdatedListener listener) {
        this.updateListener = listener;
    }

    public void setOperationIdGenerator(OperationIdGenerator generator) {
        this.idGenerator = generator;
    }

    public void setNextOperationBinder(NextOperationBinder binder) {
        this.nextOpBinder = binder;
    }

    public void setPointPickerLauncher(PointPickerLauncher launcher) {
        this.pointPickerLauncher = launcher;
    }

    public void setOperationPickerLauncher(OperationPickerLauncher launcher) {
        this.operationPickerLauncher = launcher;
    }

    public void setColorPointPickerLauncher(ColorPointPickerLauncher launcher) {
        this.colorPointPickerLauncher = launcher;
    }

    public void setOperationUpdater(OperationUpdater updater) {
        this.operationUpdater = updater;
    }

    public void setGestureHelper(GestureHelper helper) {
        this.gestureHelper = helper;
    }

    public void setTaskOperationHelper(TaskOperationHelper helper) {
        this.taskOperationHelper = helper;
    }

    public void setTemplateHelper(TemplateHelper helper) {
        this.templateHelper = helper;
    }

    public void setRegionPickHelper(RegionPickHelper helper) {
        this.regionPickHelper = helper;
    }

    public void setVariableHelper(VariableHelper helper) {
        this.variableHelper = helper;
    }

    public void setVariableMathHelper(VariableMathHelper helper) {
        this.variableMathHelper = helper;
    }

    public void setLaunchAppHelper(LaunchAppHelper helper) {
        this.launchAppHelper = helper;
    }

    public void setMatchMapHelper(MatchMapHelper helper) {
        this.matchMapHelper = helper;
    }

    // ==================== Click Operation ====================

    public void showAddClickDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_click, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 560, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtClickTarget = dialogView.findViewById(R.id.edt_click_target);
        AutoCompleteTextView edtClickMode = dialogView.findViewById(R.id.edt_click_mode);
        EditText edtClickSettleMs = dialogView.findViewById(R.id.edt_click_settle_ms);
        EditText edtClickWaitTimeoutMs = dialogView.findViewById(R.id.edt_click_wait_timeout_ms);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        dialogHelpers.bindDropdownSelect(edtClickMode, java.util.Arrays.asList(
                MetaOperation.CLICK_MODE_FAST,
                MetaOperation.CLICK_MODE_STRICT
        ));
        prepareJumpTaskAutoComplete(edtClickMode);
        edtClickMode.setText(MetaOperation.CLICK_MODE_FAST, false);
        edtClickSettleMs.setText("32");
        edtClickWaitTimeoutMs.setText("3000");

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_pick_point).setOnClickListener(v -> {
            if (pointPickerLauncher != null) {
                pointPickerLauncher.showScreenPointPicker(
                    (x, y) -> edtClickTarget.setText(x + "," + y),
                    dialogView);
            }
        });

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String clickTarget = normalizeClickTarget(edtClickTarget.getText().toString().trim());
            String clickMode = normalizeClickMode(edtClickMode.getText().toString().trim());
            Long settleMs = parsePositiveLong(edtClickSettleMs.getText().toString().trim());
            Long waitTimeoutMs = parsePositiveLong(edtClickWaitTimeoutMs.getText().toString().trim());
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (clickTarget == null) {
                edtClickTarget.setError("坐标格式示例: 500,800");
                return;
            }
            if (clickMode == null) {
                edtClickMode.setError("仅支持 fast 或 strict");
                return;
            }
            if (settleMs == null) {
                edtClickSettleMs.setError("请输入大于 0 的毫秒数");
                return;
            }
            if (waitTimeoutMs == null) {
                edtClickWaitTimeoutMs.setError("请输入大于 0 的毫秒数");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis());
                operationObject.put("name", name);
                operationObject.put("type", 1);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.CLICK_TARGET, clickTarget);
                inputMap.put(MetaOperation.CLICK_EXECUTION_MODE, clickMode);
                inputMap.put(MetaOperation.CLICK_SETTLE_MS, settleMs);
                inputMap.put(MetaOperation.CLICK_WAIT_TIMEOUT_MS, waitTimeoutMs);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) {
                        addListener.onOperationAdded();
                    }
                }
            } catch (Exception e) {
                host.showToast("构建点击操作失败: " + e.getMessage());
            }
        });
    }

    // ==================== Delay Operation ====================

    public void showAddDelayDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_delay, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 340, 0.82f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtDuration = dialogView.findViewById(R.id.edt_duration);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        CheckBox chkShowDelayCountdown = dialogView.findViewById(R.id.chk_show_delay_countdown);

        if (chkShowDelayCountdown != null) {
            chkShowDelayCountdown.setChecked(true);
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String durationStr = edtDuration.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(durationStr)) {
                edtDuration.setError("请填写延时时间");
                return;
            }

            long duration;
            try {
                duration = Long.parseLong(durationStr);
            } catch (NumberFormatException e) {
                edtDuration.setError("请输入有效的毫秒数");
                return;
            }
            if (duration < 0) {
                edtDuration.setError("延时时间不能小于 0");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis());
                operationObject.put("name", name);
                operationObject.put("type", 2);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.SLEEP_DURATION, duration);
                inputMap.put(MetaOperation.DELAY_SHOW_COUNTDOWN,
                        chkShowDelayCountdown == null || chkShowDelayCountdown.isChecked());
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) {
                        addListener.onOperationAdded();
                    }
                }
            } catch (Exception e) {
                host.showToast("构建延时操作失败: " + e.getMessage());
            }
        });
    }

    // ==================== Dynamic Delay Operation ====================

    public void showAddDynamicDelayDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_dynamic_delay, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 340, 0.82f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 400, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        CheckBox chkShowCountdown = dialogView.findViewById(R.id.chk_show_delay_countdown);

        if (chkShowCountdown != null) {
            chkShowCountdown.setChecked(true);
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis());
                operationObject.put("name", name);
                operationObject.put("type", 21);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.DYNAMIC_DELAY_VAR_NAME, varName);
                inputMap.put(MetaOperation.DELAY_SHOW_COUNTDOWN,
                        chkShowCountdown == null || chkShowCountdown.isChecked());
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) {
                        addListener.onOperationAdded();
                    }
                }
            } catch (Exception e) {
                host.showToast("构建动态延时操作失败: " + e.getMessage());
            }
        });
    }

    // ==================== Edit Dynamic Delay Operation ====================

    public void showEditDynamicDelayDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_dynamic_delay, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 340, 0.82f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 400, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        CheckBox chkShowCountdown = dialogView.findViewById(R.id.chk_show_delay_countdown);

        // 回填已有数据
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                edtVarName.setText(inputMap.optString(MetaOperation.DYNAMIC_DELAY_VAR_NAME, ""));
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                if (chkShowCountdown != null) {
                    chkShowCountdown.setChecked(inputMap.optBoolean(MetaOperation.DELAY_SHOW_COUNTDOWN, true));
                }
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 21);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.DYNAMIC_DELAY_VAR_NAME, varName);
                inputMap.put(MetaOperation.DELAY_SHOW_COUNTDOWN,
                        chkShowCountdown == null || chkShowCountdown.isChecked());
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("更新动态延时操作失败: " + e.getMessage());
            }
        });
    }

    // ==================== Helper Methods ====================

    private boolean appendOperation(JSONObject operationObject) {
        return crudHelper.appendOperation(operationObject, null);
    }

    private String normalizeClickTarget(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            return x + "," + y;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeClickMode(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return MetaOperation.CLICK_MODE_FAST;
        }
        String value = raw.trim().toLowerCase();
        if (MetaOperation.CLICK_MODE_FAST.equals(value) || MetaOperation.CLICK_MODE_STRICT.equals(value)) {
            return value;
        }
        return null;
    }

    private Long parsePositiveLong(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String defaultMatchTimeoutText() {
        return String.valueOf(MetaOperation.DEFAULT_MATCH_TIMEOUT_MS);
    }

    private void putOptionalMatchPreDelay(JSONObject inputMap, TextView input) throws org.json.JSONException {
        String text = safeText(input);
        if (TextUtils.isEmpty(text)) {
            inputMap.remove(MetaOperation.NODE_PRE_DELAY_MS);
            inputMap.remove(MetaOperation.NODE_PRE_DELAY_MIN_MS);
            inputMap.remove(MetaOperation.NODE_PRE_DELAY_MAX_MS);
            inputMap.remove(MetaOperation.NODE_PRE_DELAY_RANDOM);
            inputMap.remove(MetaOperation.MATCH_PRE_DELAY_MS);
            return;
        }
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("节点前延迟必须是 0~" + MetaOperation.MAX_NODE_PRE_DELAY_MS + " 的毫秒数");
        }
        if (value < 0) {
            throw new IllegalArgumentException("节点前延迟必须是 0~" + MetaOperation.MAX_NODE_PRE_DELAY_MS + " 的毫秒数");
        }
        if (value == 0) {
            inputMap.remove(MetaOperation.NODE_PRE_DELAY_MS);
            inputMap.remove(MetaOperation.NODE_PRE_DELAY_MIN_MS);
            inputMap.remove(MetaOperation.NODE_PRE_DELAY_MAX_MS);
            inputMap.remove(MetaOperation.NODE_PRE_DELAY_RANDOM);
            inputMap.remove(MetaOperation.MATCH_PRE_DELAY_MS);
            return;
        }
        inputMap.put(MetaOperation.NODE_PRE_DELAY_MS, Math.min(value, MetaOperation.MAX_NODE_PRE_DELAY_MS));
        inputMap.remove(MetaOperation.MATCH_PRE_DELAY_MS);
    }

    private void setupMatchDelayHint(EditText input) {
        if (input != null) {
            input.setHint("0~" + MetaOperation.MAX_NODE_PRE_DELAY_MS);
        }
    }

    private void setNodePreDelayText(EditText input, JSONObject inputMap) {
        if (input == null || inputMap == null) {
            return;
        }
        String value = inputMap.has(MetaOperation.NODE_PRE_DELAY_MS)
                ? inputMap.optString(MetaOperation.NODE_PRE_DELAY_MS, "")
                : inputMap.optString(MetaOperation.MATCH_PRE_DELAY_MS, "");
        input.setText(value);
    }

    private void setupPollingIntervalInputs(View dialogView,
                                            AdaptivePollingController.Profile profile,
                                            JSONObject inputMap) {
        EditText fastInput = dialogView.findViewById(R.id.edt_poll_fast_ms);
        EditText mediumInput = dialogView.findViewById(R.id.edt_poll_medium_ms);
        EditText slowInput = dialogView.findViewById(R.id.edt_poll_slow_ms);
        setPollingIntervalHints(fastInput, mediumInput, slowInput, profile);
        if (inputMap == null) {
            return;
        }
        setOptionalLongText(fastInput, inputMap.opt(MetaOperation.POLL_FAST_INTERVAL_MS));
        setOptionalLongText(mediumInput, inputMap.opt(MetaOperation.POLL_MEDIUM_INTERVAL_MS));
        setOptionalLongText(slowInput, inputMap.opt(MetaOperation.POLL_SLOW_INTERVAL_MS));
    }

    private void setPollingIntervalHints(EditText fastInput,
                                          EditText mediumInput,
                                          EditText slowInput,
                                          AdaptivePollingController.Profile profile) {
        if (profile == null) {
            profile = AdaptivePollingController.Profile.TEMPLATE_MATCH;
        }
        if (fastInput != null) {
            fastInput.setHint("默认 " + AdaptivePollingController.defaultFastIntervalMs(profile));
        }
        if (mediumInput != null) {
            mediumInput.setHint("默认 " + AdaptivePollingController.defaultMediumIntervalMs(profile));
        }
        if (slowInput != null) {
            slowInput.setHint("默认 " + AdaptivePollingController.defaultSlowIntervalMs(profile));
        }
    }

    private void fillPollingIntervalInputMap(View dialogView, JSONObject inputMap) throws org.json.JSONException {
        putOptionalInterval(dialogView, inputMap, R.id.edt_poll_fast_ms, MetaOperation.POLL_FAST_INTERVAL_MS);
        putOptionalInterval(dialogView, inputMap, R.id.edt_poll_medium_ms, MetaOperation.POLL_MEDIUM_INTERVAL_MS);
        putOptionalInterval(dialogView, inputMap, R.id.edt_poll_slow_ms, MetaOperation.POLL_SLOW_INTERVAL_MS);
    }

    private void putOptionalInterval(View dialogView,
                                     JSONObject inputMap,
                                     int viewId,
                                     String key) throws org.json.JSONException {
        EditText input = dialogView.findViewById(viewId);
        String text = safeText(input);
        if (TextUtils.isEmpty(text)) {
            inputMap.remove(key);
            return;
        }
        Long value = parsePositiveLong(text);
        if (value == null) {
            throw new IllegalArgumentException("轮询间隔必须是大于 0 的毫秒数");
        }
        inputMap.put(key, Math.max(10L, Math.min(value, 5000L)));
    }

    private void setOptionalLongText(EditText input, Object raw) {
        if (input == null || raw == null) {
            return;
        }
        input.setText(String.valueOf(raw).replace(".0", ""));
    }

    private void bindJumpTaskTargetOperationSuggestions(AutoCompleteTextView edtTargetOperation, String taskId) {
        if (edtTargetOperation == null) {
            return;
        }
        java.util.List<String> operationIds = java.util.Collections.emptyList();
        if (taskOperationHelper != null && !TextUtils.isEmpty(taskId)) {
            java.util.List<String> values = taskOperationHelper.getTaskOperationIds(taskId);
            if (values != null) {
                operationIds = values;
            }
        }
        dialogHelpers.bindAutoComplete(edtTargetOperation, operationIds);
    }

    private void updateJumpTaskReturnSection(View nextOperationSection, CheckBox cbReturnAfterComplete) {
        if (nextOperationSection == null || cbReturnAfterComplete == null) {
            return;
        }
        nextOperationSection.setVisibility(cbReturnAfterComplete.isChecked() ? View.VISIBLE : View.GONE);
    }

    private void prepareJumpTaskAutoComplete(AutoCompleteTextView view) {
        if (view == null) {
            return;
        }
        view.setThreshold(0);
        view.setOnClickListener(v -> {
            view.requestFocus();
            view.showDropDown();
        });
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                view.post(view::showDropDown);
            }
        });
    }

    private void showJumpTaskPickList(String title,
                                      java.util.List<OperationIdPickerAdapter.OperationPickItem> items,
                                      String currentSelectedId,
                                      boolean allowClear,
                                      OperationSelectedCallback callback) {
        if (items == null) {
            items = java.util.Collections.emptyList();
        }
        if (items.isEmpty()) {
            host.showToast("没有可选择的项目");
            return;
        }

        View pickerView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_operation_picker, null);
        WindowManager.LayoutParams pickerLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(pickerLp, 360, 0.78f, 0.92f);
        wm.addView(pickerView, pickerLp);
        dialogHelpers.setupDialogMoveAndScale(pickerView, pickerLp, 360, 440, null);

        TextView tvTitle = pickerView.findViewById(R.id.tv_picker_title);
        EditText edtSearch = pickerView.findViewById(R.id.edt_picker_search);
        RecyclerView rv = pickerView.findViewById(R.id.rv_picker);
        TextView btnClear = pickerView.findViewById(R.id.btn_picker_clear);

        tvTitle.setText(title);
        rv.setLayoutManager(new LinearLayoutManager(host.getContext()));
        OperationIdPickerAdapter adapter = new OperationIdPickerAdapter(items, currentSelectedId, id -> {
            dialogHelpers.safeRemoveView(pickerView);
            if (callback != null) {
                callback.onOperationSelected(id);
            }
        });
        rv.setAdapter(adapter);
        if (!TextUtils.isEmpty(currentSelectedId)) {
            for (int i = 0; i < items.size(); i++) {
                if (TextUtils.equals(currentSelectedId, items.get(i).id)) {
                    rv.scrollToPosition(i);
                    break;
                }
            }
        }

        pickerView.findViewById(R.id.btn_picker_close).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(pickerView));
        btnClear.setVisibility(allowClear ? View.VISIBLE : View.GONE);
        btnClear.setOnClickListener(v -> {
            dialogHelpers.safeRemoveView(pickerView);
            if (callback != null) {
                callback.onOperationSelected("");
            }
        });

        edtSearch.setHint("搜索序号、名称、类型或ID");
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.updateFilter(s == null ? "" : s.toString());
                adapter.updateSelectedOperation(currentSelectedId);
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void showJumpTaskPicker(String currentTaskId, OperationSelectedCallback callback) {
        java.util.List<OperationIdPickerAdapter.OperationPickItem> items =
                taskOperationHelper == null
                        ? java.util.Collections.emptyList()
                        : taskOperationHelper.getCurrentProjectTaskPickItems();
        showJumpTaskPickList("选择目标 Task", items, currentTaskId, false, callback);
    }

    private void showJumpTaskOperationPicker(String taskId,
                                             String currentOperationId,
                                             OperationSelectedCallback callback) {
        if (TextUtils.isEmpty(taskId)) {
            host.showToast("请先选择目标 Task");
            return;
        }
        java.util.List<OperationIdPickerAdapter.OperationPickItem> items =
                taskOperationHelper == null
                        ? java.util.Collections.emptyList()
                        : taskOperationHelper.getTaskOperationPickItems(taskId);
        showJumpTaskPickList("选择目标节点", items, currentOperationId, true, callback);
    }

    private void updateJumpTaskSelectionSummary(TextView taskSummary,
                                                TextView operationSummary,
                                                String taskId,
                                                String operationId) {
        if (taskSummary != null) {
            String label = taskOperationHelper == null
                    ? taskId
                    : taskOperationHelper.getTaskDisplayLabel(taskId);
            taskSummary.setText(TextUtils.isEmpty(label) ? "未选择目标 Task" : label);
        }
        if (operationSummary != null) {
            String label = taskOperationHelper == null
                    ? operationId
                    : taskOperationHelper.getTaskOperationDisplayLabel(taskId, operationId);
            operationSummary.setText(TextUtils.isEmpty(label) ? "未选择目标节点" : label);
        }
    }

    // ==================== Jump Task Operation ====================

    public void showAddJumpTaskDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_jump_task, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 500, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtTargetTask = dialogView.findViewById(R.id.edt_target_task);
        AutoCompleteTextView edtTargetOperation = dialogView.findViewById(R.id.edt_target_operation);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        CheckBox cbReturnAfterComplete = dialogView.findViewById(R.id.cb_return_after_complete);
        View nextOperationSection = dialogView.findViewById(R.id.layout_next_operation_section);
        TextView btnUseCurrentTask = dialogView.findViewById(R.id.btn_use_current_task);
        TextView btnPickTargetTask = dialogView.findViewById(R.id.btn_pick_target_task);
        TextView btnPickTargetOperation = dialogView.findViewById(R.id.btn_pick_target_operation);
        TextView btnClearTargetOperation = dialogView.findViewById(R.id.btn_clear_target_operation);
        TextView tvTargetTaskSummary = dialogView.findViewById(R.id.tv_target_task_summary);
        TextView tvTargetOperationSummary = dialogView.findViewById(R.id.tv_target_operation_summary);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        prepareJumpTaskAutoComplete(edtTargetTask);
        prepareJumpTaskAutoComplete(edtTargetOperation);
        prepareJumpTaskAutoComplete(edtNextOperation);

        if (taskOperationHelper != null) {
            dialogHelpers.bindAutoComplete(edtTargetTask, taskOperationHelper.getCurrentProjectTaskIds());
        }
        bindJumpTaskTargetOperationSuggestions(edtTargetOperation, "");
        updateJumpTaskReturnSection(nextOperationSection, cbReturnAfterComplete);
        updateJumpTaskSelectionSummary(tvTargetTaskSummary, tvTargetOperationSummary, "", "");
        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        String currentTaskId = host.getCurrentTaskDir() != null ? host.getCurrentTaskDir().getName() : "";

        edtTargetTask.addTextChangedListener(new TextWatcher() {
            private String lastTaskId = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String taskId = s == null ? "" : s.toString().trim();
                if (TextUtils.equals(lastTaskId, taskId)) {
                    return;
                }
                java.util.List<String> operationIds = java.util.Collections.emptyList();
                if (taskOperationHelper != null && !TextUtils.isEmpty(taskId)) {
                    java.util.List<String> values = taskOperationHelper.getTaskOperationIds(taskId);
                    if (values != null) {
                        operationIds = values;
                    }
                }
                dialogHelpers.bindAutoComplete(edtTargetOperation, operationIds);
                String currentOperationId = edtTargetOperation.getText() == null
                    ? ""
                    : edtTargetOperation.getText().toString().trim();
                if (!TextUtils.isEmpty(currentOperationId) && !operationIds.contains(currentOperationId)) {
                    edtTargetOperation.setText("", false);
                    currentOperationId = "";
                }
                updateJumpTaskSelectionSummary(tvTargetTaskSummary, tvTargetOperationSummary, taskId, currentOperationId);
                lastTaskId = taskId;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        edtTargetOperation.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String taskId = edtTargetTask.getText() == null ? "" : edtTargetTask.getText().toString().trim();
                String operationId = s == null ? "" : s.toString().trim();
                updateJumpTaskSelectionSummary(tvTargetTaskSummary, tvTargetOperationSummary, taskId, operationId);
            }

            @Override public void afterTextChanged(Editable s) {}
        });

        cbReturnAfterComplete.setOnCheckedChangeListener((buttonView, isChecked) ->
            updateJumpTaskReturnSection(nextOperationSection, cbReturnAfterComplete));

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnUseCurrentTask.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(currentTaskId)) {
                edtTargetTask.setText(currentTaskId, false);
                edtTargetTask.setSelection(currentTaskId.length());
            }
        });

        btnPickTargetTask.setOnClickListener(v -> {
            String current = edtTargetTask.getText() == null ? "" : edtTargetTask.getText().toString().trim();
            showJumpTaskPicker(current, taskId -> {
                edtTargetTask.setText(taskId, false);
                edtTargetTask.setSelection(edtTargetTask.length());
            });
        });

        btnPickTargetOperation.setOnClickListener(v -> {
            String taskId = edtTargetTask.getText() == null ? "" : edtTargetTask.getText().toString().trim();
            if (TextUtils.isEmpty(taskId)) {
                edtTargetTask.setError("请先选择目标 Task");
                edtTargetTask.requestFocus();
                return;
            }
            String current = edtTargetOperation.getText() == null ? "" : edtTargetOperation.getText().toString().trim();
            showJumpTaskOperationPicker(taskId, current, pickedOperationId -> {
                edtTargetOperation.setText(pickedOperationId, false);
                edtTargetOperation.setSelection(edtTargetOperation.length());
            });
        });

        btnClearTargetOperation.setOnClickListener(v -> {
            edtTargetOperation.setText("", false);
            String taskId = edtTargetTask.getText() == null ? "" : edtTargetTask.getText().toString().trim();
            updateJumpTaskSelectionSummary(tvTargetTaskSummary, tvTargetOperationSummary, taskId, "");
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择返回后的下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String targetTask = edtTargetTask.getText().toString().trim();
            String targetOperation = edtTargetOperation.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);
            boolean returnAfterComplete = cbReturnAfterComplete.isChecked();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写节点名称");
                return;
            }
            if (TextUtils.isEmpty(targetTask)) {
                edtTargetTask.setError("请选择目标 Task");
                return;
            }
            if (TextUtils.isEmpty(targetOperation)) {
                edtTargetOperation.setError("请选择目标 Operation");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis());
                operationObject.put("name", name);
                operationObject.put("type", 8);
                operationObject.put("responseType", returnAfterComplete ? 2 : 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.TARGET_TASK_ID, targetTask);
                inputMap.put(MetaOperation.TARGET_OPERATION_ID, targetOperation);
                inputMap.put(MetaOperation.RETURN_AFTER_COMPLETE, returnAfterComplete);
                if (returnAfterComplete && !TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) {
                        addListener.onOperationAdded();
                    }
                }
            } catch (Exception e) {
                host.showToast("保存跳转 Task 节点失败: " + e.getMessage());
            }
        });
    }

    // ==================== Edit Operations ====================

    public void showEditClickDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_click, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 560, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtClickTarget = dialogView.findViewById(R.id.edt_click_target);
        AutoCompleteTextView edtClickMode = dialogView.findViewById(R.id.edt_click_mode);
        EditText edtClickSettleMs = dialogView.findViewById(R.id.edt_click_settle_ms);
        EditText edtClickWaitTimeoutMs = dialogView.findViewById(R.id.edt_click_wait_timeout_ms);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        dialogHelpers.bindDropdownSelect(edtClickMode, java.util.Arrays.asList(
                MetaOperation.CLICK_MODE_FAST,
                MetaOperation.CLICK_MODE_STRICT
        ));
        prepareJumpTaskAutoComplete(edtClickMode);

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                edtClickTarget.setText(inputMap.optString(MetaOperation.CLICK_TARGET, ""));
                edtClickMode.setText(inputMap.optString(MetaOperation.CLICK_EXECUTION_MODE, MetaOperation.CLICK_MODE_FAST), false);
                edtClickSettleMs.setText(String.valueOf(inputMap.optLong(MetaOperation.CLICK_SETTLE_MS, 32L)));
                edtClickWaitTimeoutMs.setText(String.valueOf(inputMap.optLong(MetaOperation.CLICK_WAIT_TIMEOUT_MS, 3000L)));
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
            } else {
                edtClickMode.setText(MetaOperation.CLICK_MODE_FAST, false);
                edtClickSettleMs.setText("32");
                edtClickWaitTimeoutMs.setText("3000");
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_pick_point).setOnClickListener(v -> {
            if (pointPickerLauncher != null) {
                pointPickerLauncher.showScreenPointPicker(
                    (x, y) -> edtClickTarget.setText(x + "," + y),
                    dialogView);
            }
        });

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String clickTarget = normalizeClickTarget(edtClickTarget.getText().toString().trim());
            String clickMode = normalizeClickMode(edtClickMode.getText().toString().trim());
            Long settleMs = parsePositiveLong(edtClickSettleMs.getText().toString().trim());
            Long waitTimeoutMs = parsePositiveLong(edtClickWaitTimeoutMs.getText().toString().trim());
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (clickTarget == null) {
                edtClickTarget.setError("坐标格式示例: 500,800");
                return;
            }
            if (clickMode == null) {
                edtClickMode.setError("仅支持 fast 或 strict");
                return;
            }
            if (settleMs == null) {
                edtClickSettleMs.setError("请输入大于 0 的毫秒数");
                return;
            }
            if (waitTimeoutMs == null) {
                edtClickWaitTimeoutMs.setError("请输入大于 0 的毫秒数");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 1);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.CLICK_TARGET, clickTarget);
                inputMap.put(MetaOperation.CLICK_EXECUTION_MODE, clickMode);
                inputMap.put(MetaOperation.CLICK_SETTLE_MS, settleMs);
                inputMap.put(MetaOperation.CLICK_WAIT_TIMEOUT_MS, waitTimeoutMs);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("更新点击操作失败: " + e.getMessage());
            }
        });
    }

    public void showEditDelayDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_delay, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 340, 0.82f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtDuration = dialogView.findViewById(R.id.edt_duration);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        CheckBox chkShowDelayCountdown = dialogView.findViewById(R.id.chk_show_delay_countdown);

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                long duration = inputMap.optLong(MetaOperation.SLEEP_DURATION, 0L);
                edtDuration.setText(String.valueOf(duration));
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                if (chkShowDelayCountdown != null) {
                    chkShowDelayCountdown.setChecked(inputMap.optBoolean(MetaOperation.DELAY_SHOW_COUNTDOWN, true));
                }
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String durationStr = edtDuration.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(durationStr)) {
                edtDuration.setError("请填写延时时间");
                return;
            }

            long duration;
            try {
                duration = Long.parseLong(durationStr);
            } catch (NumberFormatException e) {
                edtDuration.setError("请输入有效的毫秒数");
                return;
            }
            if (duration < 0) {
                edtDuration.setError("延时时间不能小于 0");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 2);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.SLEEP_DURATION, duration);
                inputMap.put(MetaOperation.DELAY_SHOW_COUNTDOWN,
                        chkShowDelayCountdown == null || chkShowDelayCountdown.isChecked());
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("更新延时操作失败: " + e.getMessage());
            }
        });
    }

    public void showEditGestureDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_gesture, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(350, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 350, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 350, 440, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        android.widget.TextView tvPipelineStatus = dialogView.findViewById(R.id.tv_gesture_pipeline_status);
        LinearLayout lyPipelineSteps = dialogView.findViewById(R.id.ly_gesture_pipeline_steps);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        final JSONArray[] pipelineHolder = {new JSONArray()};

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                JSONArray existingPipeline = inputMap.optJSONArray(MetaOperation.GESTURE_PIPELINE);
                if (existingPipeline != null && existingPipeline.length() > 0) {
                    pipelineHolder[0] = existingPipeline;
                } else {
                    // 兼容旧的 SAVEFILENAME 格式：把单文件转为 1-step pipeline
                    String oldFile = inputMap.optString(MetaOperation.SAVEFILENAME, "");
                    if (TextUtils.isEmpty(oldFile)) {
                        oldFile = inputMap.optString(MetaOperation.GESTURE_TEMPLATE_ID, "");
                    }
                    if (!TextUtils.isEmpty(oldFile)) {
                        JSONObject step = new JSONObject();
                        step.put(MetaOperation.GESTURE_STEP_FILE, oldFile);
                        step.put(MetaOperation.GESTURE_STEP_DELAY_MS, 0);
                        pipelineHolder[0] = new JSONArray();
                        pipelineHolder[0].put(step);
                    }
                }
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        renderGesturePipelineSteps(lyPipelineSteps, pipelineHolder, tvPipelineStatus, dialogView);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pipeline_record_step).setOnClickListener(v -> {
            appendEmptyGesturePipelineStep(pipelineHolder);
            renderGesturePipelineSteps(lyPipelineSteps, pipelineHolder, tvPipelineStatus, dialogView);
        });

        dialogView.findViewById(R.id.btn_pipeline_record_continuous).setOnClickListener(v -> {
            if (gestureHelper == null) return;
            gestureHelper.beginPipelineContinuousRecord(dialogView, newFile -> {
                appendGesturePipelineStep(pipelineHolder, newFile, 0L);
                renderGesturePipelineSteps(lyPipelineSteps, pipelineHolder, tvPipelineStatus, dialogView);
            });
        });

        dialogView.findViewById(R.id.btn_pipeline_play_all).setOnClickListener(v -> {
            if (gestureHelper != null) {
                gestureHelper.playFullPipeline(dialogView, pipelineHolder[0], tvPipelineStatus);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (pipelineHolder[0].length() == 0) {
                host.showToast("请至少录制一个手势步骤");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 5);
                updatedOperation.put("responseType", 2);

                JSONObject inputMap = new JSONObject();
                java.io.File currentProjectDir = host.getCurrentProjectDir();
                java.io.File currentTaskDir = host.getCurrentTaskDir();
                inputMap.put(MetaOperation.PROJECT, currentProjectDir != null ? currentProjectDir.getName() : "");
                inputMap.put(MetaOperation.TASK, currentTaskDir != null ? currentTaskDir.getName() : "");
                inputMap.put(MetaOperation.GESTURE_PIPELINE, pipelineHolder[0]);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    public void showEditJumpTaskDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_jump_task, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 500, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtTargetTask = dialogView.findViewById(R.id.edt_target_task);
        AutoCompleteTextView edtTargetOperation = dialogView.findViewById(R.id.edt_target_operation);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        CheckBox cbReturnAfterComplete = dialogView.findViewById(R.id.cb_return_after_complete);
        View nextOperationSection = dialogView.findViewById(R.id.layout_next_operation_section);
        TextView btnUseCurrentTask = dialogView.findViewById(R.id.btn_use_current_task);
        TextView btnPickTargetTask = dialogView.findViewById(R.id.btn_pick_target_task);
        TextView btnPickTargetOperation = dialogView.findViewById(R.id.btn_pick_target_operation);
        TextView btnClearTargetOperation = dialogView.findViewById(R.id.btn_clear_target_operation);
        TextView tvTargetTaskSummary = dialogView.findViewById(R.id.tv_target_task_summary);
        TextView tvTargetOperationSummary = dialogView.findViewById(R.id.tv_target_operation_summary);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存修改");

        prepareJumpTaskAutoComplete(edtTargetTask);
        prepareJumpTaskAutoComplete(edtTargetOperation);
        prepareJumpTaskAutoComplete(edtNextOperation);

        if (taskOperationHelper != null) {
            dialogHelpers.bindAutoComplete(edtTargetTask, taskOperationHelper.getCurrentProjectTaskIds());
        }
        bindJumpTaskTargetOperationSuggestions(edtTargetOperation, "");
        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        edtTargetTask.addTextChangedListener(new android.text.TextWatcher() {
            private String lastTaskId = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String taskId = s == null ? "" : s.toString().trim();
                if (TextUtils.equals(lastTaskId, taskId)) {
                    return;
                }
                java.util.List<String> operationIds = java.util.Collections.emptyList();
                if (taskOperationHelper != null && !TextUtils.isEmpty(taskId)) {
                    java.util.List<String> values = taskOperationHelper.getTaskOperationIds(taskId);
                    if (values != null) {
                        operationIds = values;
                    }
                }
                dialogHelpers.bindAutoComplete(edtTargetOperation, operationIds);
                String currentOperationId = edtTargetOperation.getText() == null
                    ? ""
                    : edtTargetOperation.getText().toString().trim();
                if (!TextUtils.isEmpty(currentOperationId) && !operationIds.contains(currentOperationId)) {
                    edtTargetOperation.setText("", false);
                    currentOperationId = "";
                }
                updateJumpTaskSelectionSummary(tvTargetTaskSummary, tvTargetOperationSummary, taskId, currentOperationId);
                lastTaskId = taskId;
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        edtTargetOperation.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String taskId = edtTargetTask.getText() == null ? "" : edtTargetTask.getText().toString().trim();
                String operationId = s == null ? "" : s.toString().trim();
                updateJumpTaskSelectionSummary(tvTargetTaskSummary, tvTargetOperationSummary, taskId, operationId);
            }

            @Override public void afterTextChanged(Editable s) {}
        });

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                String targetTaskId = inputMap.optString(MetaOperation.TARGET_TASK_ID, "");
                String targetOperationId = inputMap.optString(MetaOperation.TARGET_OPERATION_ID, "");
                String nextOperationId = inputMap.optString(MetaOperation.NEXT_OPERATION_ID, "");
                boolean returnAfterComplete = inputMap.optBoolean(MetaOperation.RETURN_AFTER_COMPLETE,
                    !TextUtils.isEmpty(nextOperationId));

                edtTargetTask.setText(targetTaskId, false);
                bindJumpTaskTargetOperationSuggestions(edtTargetOperation, targetTaskId);
                edtTargetOperation.setText(targetOperationId, false);
                setOperationReferenceText(edtNextOperation, nextOperationId);
                cbReturnAfterComplete.setChecked(returnAfterComplete);
                updateJumpTaskSelectionSummary(tvTargetTaskSummary, tvTargetOperationSummary, targetTaskId, targetOperationId);
            }
        } catch (Exception e) {
            host.showToast("加载跳转 Task 节点失败: " + e.getMessage());
        }

        updateJumpTaskReturnSection(nextOperationSection, cbReturnAfterComplete);

        String currentTaskId = host.getCurrentTaskDir() != null ? host.getCurrentTaskDir().getName() : "";

        cbReturnAfterComplete.setOnCheckedChangeListener((buttonView, isChecked) ->
            updateJumpTaskReturnSection(nextOperationSection, cbReturnAfterComplete));

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnUseCurrentTask.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(currentTaskId)) {
                edtTargetTask.setText(currentTaskId, false);
                edtTargetTask.setSelection(currentTaskId.length());
            }
        });

        btnPickTargetTask.setOnClickListener(v -> {
            String current = edtTargetTask.getText() == null ? "" : edtTargetTask.getText().toString().trim();
            showJumpTaskPicker(current, taskId -> {
                edtTargetTask.setText(taskId, false);
                edtTargetTask.setSelection(edtTargetTask.length());
            });
        });

        btnPickTargetOperation.setOnClickListener(v -> {
            String taskId = edtTargetTask.getText() == null ? "" : edtTargetTask.getText().toString().trim();
            if (TextUtils.isEmpty(taskId)) {
                edtTargetTask.setError("请先选择目标 Task");
                edtTargetTask.requestFocus();
                return;
            }
            String current = edtTargetOperation.getText() == null ? "" : edtTargetOperation.getText().toString().trim();
            showJumpTaskOperationPicker(taskId, current, pickedOperationId -> {
                edtTargetOperation.setText(pickedOperationId, false);
                edtTargetOperation.setSelection(edtTargetOperation.length());
            });
        });

        btnClearTargetOperation.setOnClickListener(v -> {
            edtTargetOperation.setText("", false);
            String taskId = edtTargetTask.getText() == null ? "" : edtTargetTask.getText().toString().trim();
            updateJumpTaskSelectionSummary(tvTargetTaskSummary, tvTargetOperationSummary, taskId, "");
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择返回后的下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String targetTaskId = edtTargetTask.getText().toString().trim();
            String targetOperationId = edtTargetOperation.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);
            boolean returnAfterComplete = cbReturnAfterComplete.isChecked();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写节点名称");
                return;
            }
            if (TextUtils.isEmpty(targetTaskId)) {
                edtTargetTask.setError("请选择目标 Task");
                return;
            }
            if (TextUtils.isEmpty(targetOperationId)) {
                edtTargetOperation.setError("请选择目标 Operation");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 8);
                updatedOperation.put("responseType", returnAfterComplete ? 2 : 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.TARGET_TASK_ID, targetTaskId);
                inputMap.put(MetaOperation.TARGET_OPERATION_ID, targetOperationId);
                inputMap.put(MetaOperation.RETURN_AFTER_COMPLETE, returnAfterComplete);
                if (returnAfterComplete && !TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("保存跳转 Task 节点失败: " + e.getMessage());
            }
        });
    }

    public void showAddGestureDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_gesture, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(350, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 350, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 350, 440, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        android.widget.TextView tvPipelineStatus = dialogView.findViewById(R.id.tv_gesture_pipeline_status);
        LinearLayout lyPipelineSteps = dialogView.findViewById(R.id.ly_gesture_pipeline_steps);

        final JSONArray[] pipelineHolder = {new JSONArray()};

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pipeline_record_step).setOnClickListener(v -> {
            appendEmptyGesturePipelineStep(pipelineHolder);
            renderGesturePipelineSteps(lyPipelineSteps, pipelineHolder, tvPipelineStatus, dialogView);
        });

        dialogView.findViewById(R.id.btn_pipeline_record_continuous).setOnClickListener(v -> {
            if (gestureHelper == null) return;
            gestureHelper.beginPipelineContinuousRecord(dialogView, newFile -> {
                appendGesturePipelineStep(pipelineHolder, newFile, 0L);
                renderGesturePipelineSteps(lyPipelineSteps, pipelineHolder, tvPipelineStatus, dialogView);
            });
        });

        dialogView.findViewById(R.id.btn_pipeline_play_all).setOnClickListener(v -> {
            if (gestureHelper != null) {
                gestureHelper.playFullPipeline(dialogView, pipelineHolder[0], tvPipelineStatus);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (pipelineHolder[0].length() == 0) {
                host.showToast("请至少录制一个手势步骤");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) {
                    operationObject.put("id", idGenerator.generateId());
                }
                operationObject.put("name", name);
                operationObject.put("type", 5);
                operationObject.put("responseType", 2);

                JSONObject inputMap = new JSONObject();
                java.io.File currentProjectDir = host.getCurrentProjectDir();
                java.io.File currentTaskDir = host.getCurrentTaskDir();
                inputMap.put(MetaOperation.PROJECT, currentProjectDir != null ? currentProjectDir.getName() : "");
                inputMap.put(MetaOperation.TASK, currentTaskDir != null ? currentTaskDir.getName() : "");
                inputMap.put(MetaOperation.GESTURE_PIPELINE, pipelineHolder[0]);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                try {
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(operationObject);
                    if (crudHelper.writeOperationsArray(operations, "已添加手势操作", () -> {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (addListener != null) {
                            addListener.onOperationAdded();
                        }
                    })) {
                        // Success handled in callback
                    }
                } catch (Exception e) {
                    host.showToast("添加操作失败: " + e.getMessage());
                }
            } catch (Exception e) {
                host.showToast("构建手势操作失败: " + e.getMessage());
            }
        });
    }

    private void appendEmptyGesturePipelineStep(JSONArray[] pipelineHolder) {
        appendGesturePipelineStep(pipelineHolder, "", 500L);
    }

    private void appendGesturePipelineStep(JSONArray[] pipelineHolder, String fileName, long delayAfterMs) {
        if (pipelineHolder == null || pipelineHolder.length == 0) return;
        if (pipelineHolder[0] == null) pipelineHolder[0] = new JSONArray();
        try {
            JSONObject step = new JSONObject();
            step.put(MetaOperation.GESTURE_STEP_FILE, fileName == null ? "" : fileName);
            step.put(MetaOperation.GESTURE_STEP_DELAY_MS, Math.max(0L, delayAfterMs));
            pipelineHolder[0].put(step);
        } catch (Exception ignored) {}
    }

    private void renderGesturePipelineSteps(LinearLayout container, JSONArray[] pipelineHolder,
                                             android.widget.TextView statusView, View dialogView) {
        if (container == null) return;
        container.removeAllViews();
        JSONArray pipeline = pipelineHolder[0];
        if (pipeline == null || pipeline.length() == 0) {
            if (statusView != null) statusView.setText("尚未添加手势步骤");
            return;
        }
        if (statusView != null) {
            statusView.setText("共 " + pipeline.length() + " 个手势步骤");
        }
        android.content.Context ctx = container.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        int dp4 = (int) (4 * density);
        int dp6 = (int) (6 * density);
        int dp8 = (int) (8 * density);
        int dp56 = (int) (56 * density);

        for (int i = 0; i < pipeline.length(); i++) {
            final int idx = i;
            JSONObject step;
            try { step = pipeline.getJSONObject(i); } catch (Exception e) { continue; }
            final String[] fileHolder = {step.optString(MetaOperation.GESTURE_STEP_FILE, "")};
            long delayMs = step.optLong(MetaOperation.GESTURE_STEP_DELAY_MS, 300);

            // Card row
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundResource(com.auto.master.R.drawable.item_operation_compact_bg);
            row.setPadding(dp8, dp6, dp8, dp6);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp6);
            row.setLayoutParams(rowLp);

            // Top: thumbnail + (label + buttons) side by side
            LinearLayout topRow = new LinearLayout(ctx);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

            boolean isEmpty = TextUtils.isEmpty(fileHolder[0]);

            // Gesture thumbnail (or placeholder)
            GesturePreviewView preview = new GesturePreviewView(ctx);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dp56, dp56);
            previewLp.setMargins(0, 0, dp8, 0);
            preview.setLayoutParams(previewLp);
            preview.setBackgroundColor(isEmpty ? 0x22FF6600 : 0x11000000);
            if (!isEmpty && gestureHelper != null) {
                com.auto.master.auto.GestureOverlayView.GestureNode node =
                        gestureHelper.readGestureFile(fileHolder[0]);
                if (node != null) preview.setGestureNode(node);
            }
            topRow.addView(preview);

            // Right: label + buttons stacked
            LinearLayout rightCol = new LinearLayout(ctx);
            rightCol.setOrientation(LinearLayout.VERTICAL);
            rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            android.widget.TextView tvLabel = new android.widget.TextView(ctx);
            tvLabel.setText(isEmpty ? "步骤 " + (idx + 1) + "  ·  待录制" : (idx + 1) + ". " + fileHolder[0]);
            tvLabel.setTextSize(11f);
            tvLabel.setTextColor(isEmpty ? 0xFFBF5000 : 0xFF1F2937);
            tvLabel.setSingleLine(true);
            tvLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            tvLabel.setPadding(0, 0, 0, dp4);
            rightCol.addView(tvLabel);

            // Action buttons row
            LinearLayout btnRow = new LinearLayout(ctx);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            btnRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            if (!isEmpty) {
                // 有文件：回放 + 单次重录 + 步进重录 + 连续重录 + 选库 + 删除
                android.widget.TextView btnPlay = makePipelineBtn(ctx, "▶", 0xFF1a5276);
                btnPlay.setOnClickListener(v2 -> {
                    if (gestureHelper != null) gestureHelper.playGestureFile(dialogView, fileHolder[0], statusView);
                });
                btnRow.addView(btnPlay);
                btnRow.addView(makePipelineBtnSpace(ctx, dp4));
            }

            // 单次录制/重录
            android.widget.TextView btnSingle = makePipelineBtn(ctx, "单次", 0xFF5C3600);
            btnSingle.setOnClickListener(v2 -> {
                if (gestureHelper == null) return;
                if (isEmpty) {
                    gestureHelper.beginPipelineStepRecord(dialogView, true, newFile -> {
                        try { step.put(MetaOperation.GESTURE_STEP_FILE, newFile); fileHolder[0] = newFile; }
                        catch (Exception ignored) {}
                        renderGesturePipelineSteps(container, pipelineHolder, statusView, dialogView);
                    });
                } else {
                    gestureHelper.rerecordPipelineStep(dialogView, true, fileHolder[0], newFile -> {
                        try { step.put(MetaOperation.GESTURE_STEP_FILE, newFile); fileHolder[0] = newFile; }
                        catch (Exception ignored) {}
                        renderGesturePipelineSteps(container, pipelineHolder, statusView, dialogView);
                    });
                }
            });
            btnRow.addView(btnSingle);
            btnRow.addView(makePipelineBtnSpace(ctx, dp4));

            // 步进录制/重录
            android.widget.TextView btnMulti = makePipelineBtn(ctx, "步进", 0xFF1a3a6e);
            btnMulti.setOnClickListener(v2 -> {
                if (gestureHelper == null) return;
                if (isEmpty) {
                    gestureHelper.beginPipelineStepRecord(dialogView, false, newFile -> {
                        try { step.put(MetaOperation.GESTURE_STEP_FILE, newFile); fileHolder[0] = newFile; }
                        catch (Exception ignored) {}
                        renderGesturePipelineSteps(container, pipelineHolder, statusView, dialogView);
                    });
                } else {
                    gestureHelper.rerecordPipelineStep(dialogView, false, fileHolder[0], newFile -> {
                        try { step.put(MetaOperation.GESTURE_STEP_FILE, newFile); fileHolder[0] = newFile; }
                        catch (Exception ignored) {}
                        renderGesturePipelineSteps(container, pipelineHolder, statusView, dialogView);
                    });
                }
            });
            btnRow.addView(btnMulti);
            btnRow.addView(makePipelineBtnSpace(ctx, dp4));

            android.widget.TextView btnContinuous = makePipelineBtn(ctx, "连续", 0xFF0F766E);
            btnContinuous.setOnClickListener(v2 -> {
                if (gestureHelper == null) return;
                if (isEmpty) {
                    gestureHelper.beginPipelineContinuousRecord(dialogView, newFile -> {
                        try {
                            step.put(MetaOperation.GESTURE_STEP_FILE, newFile);
                            step.put(MetaOperation.GESTURE_STEP_DELAY_MS, 0L);
                            fileHolder[0] = newFile;
                        } catch (Exception ignored) {}
                        renderGesturePipelineSteps(container, pipelineHolder, statusView, dialogView);
                    });
                } else {
                    gestureHelper.rerecordPipelineContinuousRecord(dialogView, fileHolder[0], newFile -> {
                        try {
                            step.put(MetaOperation.GESTURE_STEP_FILE, newFile);
                            step.put(MetaOperation.GESTURE_STEP_DELAY_MS, 0L);
                            fileHolder[0] = newFile;
                        } catch (Exception ignored) {}
                        renderGesturePipelineSteps(container, pipelineHolder, statusView, dialogView);
                    });
                }
            });
            btnRow.addView(btnContinuous);
            btnRow.addView(makePipelineBtnSpace(ctx, dp4));

            if (!isEmpty) {
                android.widget.TextView btnLib = makePipelineBtn(ctx, "库", 0xFF1a5e36);
                btnLib.setOnClickListener(v2 -> {
                    if (gestureHelper != null) {
                        gestureHelper.showGestureLibraryDialog(fileHolder[0], newFile -> {
                            try { step.put(MetaOperation.GESTURE_STEP_FILE, newFile); fileHolder[0] = newFile; }
                            catch (Exception ignored) {}
                            renderGesturePipelineSteps(container, pipelineHolder, statusView, dialogView);
                        });
                    }
                });
                btnRow.addView(btnLib);
                btnRow.addView(makePipelineBtnSpace(ctx, dp4));
            }

            android.widget.TextView btnDel = makePipelineBtn(ctx, "×", 0xFFB23B3B);
            btnDel.setOnClickListener(v2 -> {
                JSONArray newArr = new JSONArray();
                for (int j = 0; j < pipeline.length(); j++) {
                    if (j != idx) { try { newArr.put(pipeline.get(j)); } catch (Exception ignored) {} }
                }
                pipelineHolder[0] = newArr;
                renderGesturePipelineSteps(container, pipelineHolder, statusView, dialogView);
            });
            btnRow.addView(btnDel);

            rightCol.addView(btnRow);
            topRow.addView(rightCol);
            row.addView(topRow);

            // Delay row
            LinearLayout delayRow = new LinearLayout(ctx);
            delayRow.setOrientation(LinearLayout.HORIZONTAL);
            delayRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams delayLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            delayLp.setMargins(0, dp4, 0, 0);
            delayRow.setLayoutParams(delayLp);

            android.widget.TextView tvDelay = new android.widget.TextView(ctx);
            tvDelay.setText("步后延时:");
            tvDelay.setTextSize(11f);
            tvDelay.setTextColor(0xFF4e5f74);
            tvDelay.setPadding(0, 0, dp6, 0);
            delayRow.addView(tvDelay);

            EditText etDelay = new EditText(ctx);
            LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                    (int)(100 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
            etDelay.setLayoutParams(etLp);
            etDelay.setText(String.valueOf(delayMs));
            etDelay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            etDelay.setSingleLine(true);
            etDelay.setTextSize(11f);
            etDelay.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(android.text.Editable s) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    try {
                        long val = TextUtils.isEmpty(s) ? 0 : Long.parseLong(s.toString());
                        step.put(MetaOperation.GESTURE_STEP_DELAY_MS, val);
                    } catch (Exception ignored) {}
                }
            });
            delayRow.addView(etDelay);

            android.widget.TextView tvMs = new android.widget.TextView(ctx);
            tvMs.setText(" ms");
            tvMs.setTextSize(11f);
            tvMs.setTextColor(0xFF4e5f74);
            delayRow.addView(tvMs);

            row.addView(delayRow);
            container.addView(row);
        }
    }

    private android.widget.TextView makePipelineBtn(android.content.Context ctx, String text, int bgColor) {
        android.widget.TextView btn = new android.widget.TextView(ctx);
        btn.setText(text);
        btn.setTextSize(11f);
        btn.setTextColor(0xFFFFFFFF);
        btn.setGravity(android.view.Gravity.CENTER);
        float d = ctx.getResources().getDisplayMetrics().density;
        int h = (int)(26 * d);
        int px = (int)(8 * d);
        btn.setPadding(px, 0, px, 0);
        btn.setMinHeight(h);
        btn.setBackgroundColor(bgColor);
        return btn;
    }

    private View makePipelineBtnSpace(android.content.Context ctx, int width) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
        return v;
    }

    public void showAddMatchTemplateDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_match_template, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(350, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 350, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 350, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtTemplateFile = dialogView.findViewById(R.id.edt_template_file);
        EditText edtSimilarity = dialogView.findViewById(R.id.edt_similarity);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallbackOperation = dialogView.findViewById(R.id.edt_fallback_operation);

        String defaultTemplate = "tpl_" + System.currentTimeMillis() + ".png";
        edtTemplateFile.setText(defaultTemplate);
        edtSimilarity.setText("0.85");
        edtTimeout.setText(defaultMatchTimeoutText());

        if (templateHelper != null) {
            templateHelper.refreshTemplateOptions(edtTemplateFile);
            templateHelper.bindTemplatePreview(dialogView, edtTemplateFile);
            templateHelper.renderRecentTemplateStrip(dialogView, edtTemplateFile);
            templateHelper.setupAdvancedMatchSection(dialogView, null, null);
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_template_ts).setOnClickListener(v -> {
            if (templateHelper != null) {
                edtTemplateFile.setText(templateHelper.generateTemplateTimestampName());
            }
        });

        dialogView.findViewById(R.id.btn_template_library).setOnClickListener(v -> {
            if (templateHelper != null) {
                templateHelper.showTemplateLibraryDialog(edtTemplateFile, dialogView);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", null, edtFallbackOperation);
            }
        });

        edtTemplateFile.setOnClickListener(v -> {
            if (templateHelper != null) {
                templateHelper.refreshTemplateOptions(edtTemplateFile);
            }
            edtTemplateFile.showDropDown();
        });

        dialogView.findViewById(R.id.btn_capture).setOnClickListener(v -> {
            if (templateHelper != null) {
                templateHelper.beginTemplateCaptureFromDialog(dialogView, edtTemplateFile);
            }
        });

        dialogView.findViewById(R.id.btn_edit_mask).setOnClickListener(v ->
            host.showTemplateMaskEditorByName(edtTemplateFile.getText().toString().trim(), null));

        dialogView.findViewById(R.id.btn_preview_bbox).setOnClickListener(v ->
            host.showTemplateBboxPreview(edtTemplateFile.getText().toString().trim()));

        TextView btnTestMatch = dialogView.findViewById(R.id.btn_test_match);
        btnTestMatch.setOnClickListener(v -> {
            MetaOperation testOperation = buildMatchTemplateTestOperation(
                    edtName, edtTemplateFile, edtSimilarity, edtTimeout, dialogView);
            if (testOperation != null) {
                runSingleMatchTest(dialogView, btnTestMatch, testOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String templateFile = edtTemplateFile.getText().toString().trim();
            String similarityText = edtSimilarity.getText().toString().trim();
            String timeoutText = edtTimeout.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(templateFile)) {
                edtTemplateFile.setError("请填写模板文件名");
                return;
            }

            double similarity;
            long timeout;
            try {
                similarity = Double.parseDouble(similarityText);
            } catch (Exception e) {
                edtSimilarity.setError("请输入 0~1 之间的数值");
                return;
            }
            try {
                timeout = Long.parseLong(timeoutText);
            } catch (Exception e) {
                edtTimeout.setError("请输入超时时间(毫秒)");
                return;
            }
            if (similarity <= 0 || similarity > 1.0) {
                edtSimilarity.setError("建议范围 0.6 ~ 0.99");
                return;
            }
            if (timeout <= 0) {
                edtTimeout.setError("超时必须大于 0");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) {
                    operationObject.put("id", idGenerator.generateId());
                }
                operationObject.put("name", name);
                operationObject.put("type", 6);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                java.io.File currentProjectDir = host.getCurrentProjectDir();
                java.io.File currentTaskDir = host.getCurrentTaskDir();
                inputMap.put(MetaOperation.PROJECT, currentProjectDir != null ? currentProjectDir.getName() : "");
                inputMap.put(MetaOperation.TASK, currentTaskDir != null ? currentTaskDir.getName() : "");
                inputMap.put(MetaOperation.SAVEFILENAME, templateFile.endsWith(".png") ? templateFile : templateFile + ".png");
                inputMap.put(MetaOperation.MATCHSIMILARITY, similarity);
                inputMap.put(MetaOperation.MATCHTIMEOUT, (double) timeout);

                if (templateHelper != null) {
                    templateHelper.fillAdvancedMatchInputMap(dialogView, inputMap);
                }

                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                try {
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(operationObject);
                    if (crudHelper.writeOperationsArray(operations, "已添加模板匹配操作", () -> {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (addListener != null) {
                            addListener.onOperationAdded();
                        }
                    })) {
                        // Success handled in callback
                    }
                } catch (Exception e) {
                    host.showToast("添加操作失败: " + e.getMessage());
                }
            } catch (Exception e) {
                host.showToast("构建模板匹配操作失败: " + e.getMessage());
            }
        });
    }

    public void showEditMatchTemplateDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_match_template, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(350, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 350, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 350, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtTemplateFile = dialogView.findViewById(R.id.edt_template_file);
        EditText edtSimilarity = dialogView.findViewById(R.id.edt_similarity);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallbackOperation = dialogView.findViewById(R.id.edt_fallback_operation);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        if (templateHelper != null) {
            templateHelper.refreshTemplateOptions(edtTemplateFile);
            templateHelper.bindTemplatePreview(dialogView, edtTemplateFile);
            templateHelper.renderRecentTemplateStrip(dialogView, edtTemplateFile);
            templateHelper.setupAdvancedMatchSection(dialogView, operationObject, null);
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_template_ts).setOnClickListener(v -> {
            if (templateHelper != null) {
                edtTemplateFile.setText(templateHelper.generateTemplateTimestampName());
            }
        });

        dialogView.findViewById(R.id.btn_template_library).setOnClickListener(v -> {
            if (templateHelper != null) {
                templateHelper.showTemplateLibraryDialog(edtTemplateFile, dialogView);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", null, edtFallbackOperation);
            }
        });

        edtTemplateFile.setOnClickListener(v -> {
            if (templateHelper != null) {
                templateHelper.refreshTemplateOptions(edtTemplateFile);
            }
            edtTemplateFile.showDropDown();
        });

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                edtTemplateFile.setText(inputMap.optString(MetaOperation.SAVEFILENAME, ""));

                Object similarity = inputMap.opt(MetaOperation.MATCHSIMILARITY);
                if (similarity != null) {
                    edtSimilarity.setText(String.valueOf(similarity));
                } else {
                    edtSimilarity.setText("0.85");
                }

                Object timeout = inputMap.opt(MetaOperation.MATCHTIMEOUT);
                if (timeout != null) {
                    edtTimeout.setText(String.valueOf(timeout).replace(".0", ""));
                } else {
                    edtTimeout.setText(defaultMatchTimeoutText());
                }

                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
            } else {
                edtSimilarity.setText("0.85");
                edtTimeout.setText(defaultMatchTimeoutText());
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_capture).setOnClickListener(v -> {
            if (templateHelper != null) {
                templateHelper.beginTemplateCaptureFromDialog(dialogView, edtTemplateFile);
            }
        });

        dialogView.findViewById(R.id.btn_edit_mask).setOnClickListener(v ->
            host.showTemplateMaskEditorByName(edtTemplateFile.getText().toString().trim(), null));

        dialogView.findViewById(R.id.btn_preview_bbox).setOnClickListener(v ->
            host.showTemplateBboxPreview(edtTemplateFile.getText().toString().trim()));

        TextView btnTestMatch = dialogView.findViewById(R.id.btn_test_match);
        btnTestMatch.setOnClickListener(v -> {
            MetaOperation testOperation = buildMatchTemplateTestOperation(
                    edtName, edtTemplateFile, edtSimilarity, edtTimeout, dialogView);
            if (testOperation != null) {
                runSingleMatchTest(dialogView, btnTestMatch, testOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String templateFile = edtTemplateFile.getText().toString().trim();
            String similarityText = edtSimilarity.getText().toString().trim();
            String timeoutText = edtTimeout.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(templateFile)) {
                edtTemplateFile.setError("请填写模板文件名");
                return;
            }

            double similarityVal;
            long timeoutVal;
            try {
                similarityVal = Double.parseDouble(similarityText);
            } catch (Exception e) {
                edtSimilarity.setError("请输入 0~1 之间的数值");
                return;
            }
            try {
                timeoutVal = Long.parseLong(timeoutText);
            } catch (Exception e) {
                edtTimeout.setError("请输入超时时间(毫秒)");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 6);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                java.io.File currentProjectDir = host.getCurrentProjectDir();
                java.io.File currentTaskDir = host.getCurrentTaskDir();
                inputMap.put(MetaOperation.PROJECT, currentProjectDir != null ? currentProjectDir.getName() : "");
                inputMap.put(MetaOperation.TASK, currentTaskDir != null ? currentTaskDir.getName() : "");
                inputMap.put(MetaOperation.SAVEFILENAME, templateFile.endsWith(".png") ? templateFile : templateFile + ".png");
                inputMap.put(MetaOperation.MATCHSIMILARITY, similarityVal);
                inputMap.put(MetaOperation.MATCHTIMEOUT, (double) timeoutVal);

                if (templateHelper != null) {
                    templateHelper.fillAdvancedMatchInputMap(dialogView, inputMap);
                }

                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    private MetaOperation buildMatchTemplateTestOperation(EditText edtName,
                                                          AutoCompleteTextView edtTemplateFile,
                                                          EditText edtSimilarity,
                                                          EditText edtTimeout,
                                                          View dialogView) {
        String templateFile = edtTemplateFile.getText().toString().trim();
        String similarityText = edtSimilarity.getText().toString().trim();
        String timeoutText = edtTimeout.getText().toString().trim();

        if (TextUtils.isEmpty(templateFile)) {
            edtTemplateFile.setError("请填写模板文件名");
            return null;
        }

        double similarity;
        long timeout;
        try {
            similarity = TextUtils.isEmpty(similarityText) ? 0.85d : Double.parseDouble(similarityText);
        } catch (Exception e) {
            edtSimilarity.setError("请输入 0~1 之间的数值");
            return null;
        }
        try {
            timeout = TextUtils.isEmpty(timeoutText)
                    ? Long.parseLong(defaultMatchTimeoutText())
                    : Long.parseLong(timeoutText);
        } catch (Exception e) {
            edtTimeout.setError("请输入超时时间(毫秒)");
            return null;
        }
        if (similarity <= 0 || similarity > 1.0d) {
            edtSimilarity.setError("建议范围 0.6 ~ 0.99");
            return null;
        }
        if (timeout <= 0) {
            edtTimeout.setError("超时必须大于 0");
            return null;
        }

        MatchTemplateOperation operation = new MatchTemplateOperation();
        operation.setId("single_test_match_" + System.currentTimeMillis());
        operation.setName(!TextUtils.isEmpty(safeText(edtName)) ? safeText(edtName) : "模板匹配单步测试");
        operation.setResponseType(1);

        java.util.Map<String, Object> inputMap = new java.util.HashMap<>();
        java.io.File currentProjectDir = host.getCurrentProjectDir();
        java.io.File currentTaskDir = host.getCurrentTaskDir();
        inputMap.put(MetaOperation.PROJECT, currentProjectDir != null ? currentProjectDir.getName() : "");
        inputMap.put(MetaOperation.TASK, currentTaskDir != null ? currentTaskDir.getName() : "");
        inputMap.put(MetaOperation.SAVEFILENAME, templateFile.endsWith(".png") ? templateFile : templateFile + ".png");
        inputMap.put(MetaOperation.MATCHSIMILARITY, similarity);
        inputMap.put(MetaOperation.MATCHTIMEOUT, (double) timeout);

        if (templateHelper != null) {
            JSONObject advanced = new JSONObject();
            templateHelper.fillAdvancedMatchInputMap(dialogView, advanced);
            inputMap.putAll(jsonObjectToJavaMap(advanced));
        }
        inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
        inputMap.remove(MetaOperation.FALLBACKOPERATIONID);

        operation.setInputMap(inputMap);
        return operation;
    }

    private MetaOperation buildMatchMapTestOperation(EditText edtName,
                                                     EditText edtTimeout,
                                                     LinearLayout lyEntries,
                                                     CheckBox chkSuccessClick,
                                                     CheckBox chkUseGray,
                                                     CheckBox chkUseMask,
                                                     EditText edtPreDelay,
                                                     View dialogView) {
        String timeoutText = edtTimeout.getText().toString().trim();
        long timeout;
        try {
            timeout = TextUtils.isEmpty(timeoutText)
                    ? Long.parseLong(defaultMatchTimeoutText())
                    : Long.parseLong(timeoutText);
        } catch (Exception e) {
            edtTimeout.setError("请输入超时时间(毫秒)");
            return null;
        }
        if (timeout <= 0) {
            edtTimeout.setError("超时必须大于 0");
            return null;
        }

        try {
            JSONObject matchMapJson = collectMatchMapEntries(lyEntries);
            if (matchMapJson.length() == 0) {
                host.showToast("请至少添加一条匹配规则");
                return null;
            }

            MatchMapTemplateOperation operation = new MatchMapTemplateOperation();
            operation.setId("single_test_match_map_" + System.currentTimeMillis());
            operation.setName(!TextUtils.isEmpty(safeText(edtName)) ? safeText(edtName) : "图集匹配单步测试");
            operation.setResponseType(1);

            java.util.Map<String, Object> inputMap = new java.util.HashMap<>();
            java.io.File currentProjectDir = host.getCurrentProjectDir();
            java.io.File currentTaskDir = host.getCurrentTaskDir();
            inputMap.put(MetaOperation.PROJECT, currentProjectDir != null ? currentProjectDir.getName() : "");
            inputMap.put(MetaOperation.TASK, currentTaskDir != null ? currentTaskDir.getName() : "");
            inputMap.put(MetaOperation.MATCHTIMEOUT, (double) timeout);
            inputMap.put(MetaOperation.MATCHMAP, matchMapJson.toString());
            inputMap.put(MetaOperation.SUCCEESCLICK, chkSuccessClick == null || chkSuccessClick.isChecked());
            inputMap.put(MetaOperation.MATCHUSEGRAY, chkUseGray != null && chkUseGray.isChecked());
            inputMap.put(MetaOperation.MATCHUSEMASK, chkUseMask == null || chkUseMask.isChecked());

            JSONObject optional = new JSONObject();
            putOptionalMatchPreDelay(optional, edtPreDelay);
            fillPollingIntervalInputMap(dialogView, optional);
            inputMap.putAll(jsonObjectToJavaMap(optional));

            operation.setInputMap(inputMap);
            return operation;
        } catch (Exception e) {
            host.showToast("构建单步测试失败: " + e.getMessage());
            return null;
        }
    }

    private void runSingleMatchTest(View dialogView, TextView button, MetaOperation operation) {
        OperationHandler handler = OperationHandlerManager.getOperationHandler(operation.getType());
        if (handler == null) {
            host.showToast("单步测试不可用: 未找到匹配处理器");
            return;
        }

        String originalText = button.getText().toString();
        View projectPanel = host.getProjectPanelView();
        int dialogVisibility = dialogView.getVisibility();
        int panelVisibility = projectPanel != null ? projectPanel.getVisibility() : View.VISIBLE;

        button.setEnabled(false);
        button.setText("测试中...");
        dialogView.setVisibility(View.INVISIBLE);
        if (projectPanel != null) {
            projectPanel.setVisibility(View.INVISIBLE);
        }

        new Thread(() -> {
            boolean handled = false;
            boolean matched = false;
            Object bbox = null;
            String error = null;
            try {
                Thread.sleep(120L);
                OperationContext ctx = new OperationContext();
                ctx.suppressVisualFeedback = false;
                handled = handler.handle(operation, ctx);
                if (ctx.currentResponse != null) {
                    matched = Boolean.TRUE.equals(ctx.currentResponse.get(MetaOperation.MATCHED));
                    bbox = ctx.currentResponse.get(MetaOperation.BBOX);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                error = "测试被中断";
            } catch (Throwable t) {
                error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            }

            final boolean finalHandled = handled;
            final boolean finalMatched = matched;
            final Object finalBbox = bbox;
            final String finalError = error;
            long restoreDelayMs = finalMatched ? 700L : 0L;
            mainHandler.postDelayed(() -> {
                dialogView.setVisibility(dialogVisibility);
                if (projectPanel != null) {
                    projectPanel.setVisibility(panelVisibility);
                }
                button.setEnabled(true);
                button.setText(originalText);

                if (!TextUtils.isEmpty(finalError)) {
                    host.showToast("单步测试失败: " + finalError);
                } else if (!finalHandled) {
                    host.showToast("单步测试未执行，请确认无障碍和录屏服务可用");
                } else if (finalMatched) {
                    host.showToast(finalBbox != null ? "单步测试命中: " + finalBbox : "单步测试命中");
                } else {
                    host.showToast("单步测试未命中");
                }
            }, restoreDelayMs);
        }, "operation-single-match-test").start();
    }

    private java.util.Map<String, Object> jsonObjectToJavaMap(JSONObject object) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        if (object == null) {
            return map;
        }
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) {
                continue;
            }
            if (value instanceof JSONObject) {
                map.put(key, jsonObjectToJavaMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.put(key, jsonArrayToJavaList((JSONArray) value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private java.util.List<Object> jsonArrayToJavaList(JSONArray array) {
        java.util.List<Object> list = new java.util.ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (value == null || value == JSONObject.NULL) {
                list.add(null);
            } else if (value instanceof JSONObject) {
                list.add(jsonObjectToJavaMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(jsonArrayToJavaList((JSONArray) value));
            } else {
                list.add(value);
            }
        }
        return list;
    }

    // ==================== MatchMapTemplate Operation ====================

    public void showAddMatchMapTemplateDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_match_map_template, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 480, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        LinearLayout lyEntries = dialogView.findViewById(R.id.ly_match_entries);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallback = dialogView.findViewById(R.id.edt_fallback_operation);
        EditText edtPreDelay = dialogView.findViewById(R.id.edt_match_pre_delay);
        CheckBox chkSuccessClick = dialogView.findViewById(R.id.chk_success_click);
        CheckBox chkUseGray = dialogView.findViewById(R.id.chk_use_gray);
        CheckBox chkUseMask = dialogView.findViewById(R.id.chk_use_mask);

        edtTimeout.setText(defaultMatchTimeoutText());
        setupMatchDelayHint(edtPreDelay);
        if (chkSuccessClick != null) {
            chkSuccessClick.setChecked(true);
        }
        if (chkUseGray != null) {
            chkUseGray.setChecked(false);
        }
        if (chkUseMask != null) {
            chkUseMask.setChecked(true);
        }

        // 高级参数折叠
        setupAdvancedToggle(dialogView);
        setupPollingIntervalInputs(dialogView, AdaptivePollingController.Profile.MATCH_MAP, null);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        // 默认添加一个空行
        addMatchMapEntryRow(lyEntries, dialogView, "", new java.util.ArrayList<>(), "0.88");

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_add_entry).setOnClickListener(v ->
            addMatchMapEntryRow(lyEntries, dialogView, "", new java.util.ArrayList<>(), "0.88"));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", null, edtFallback);
            }
        });

        TextView btnTestMatch = dialogView.findViewById(R.id.btn_test_match);
        btnTestMatch.setOnClickListener(v -> {
            MetaOperation testOperation = buildMatchMapTestOperation(
                    edtName, edtTimeout, lyEntries, chkSuccessClick, chkUseGray, chkUseMask, edtPreDelay, dialogView);
            if (testOperation != null) {
                runSingleMatchTest(dialogView, btnTestMatch, testOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String timeoutText = edtTimeout.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }

            long timeout;
            try {
                timeout = Long.parseLong(timeoutText);
            } catch (Exception e) {
                edtTimeout.setError("请输入超时时间(毫秒)");
                return;
            }

            try {
                JSONObject matchMapJson = collectMatchMapEntries(lyEntries);
                if (matchMapJson.length() == 0) {
                    host.showToast("请至少添加一条匹配规则");
                    return;
                }

                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) {
                    operationObject.put("id", idGenerator.generateId());
                }
                operationObject.put("name", name);
                operationObject.put("type", 7);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                java.io.File currentProjectDir = host.getCurrentProjectDir();
                java.io.File currentTaskDir = host.getCurrentTaskDir();
                inputMap.put("PROJECT", currentProjectDir != null ? currentProjectDir.getName() : "");
                inputMap.put("TASK", currentTaskDir != null ? currentTaskDir.getName() : "");
                inputMap.put("MATCHTIMEOUT", (double) timeout);
                inputMap.put("MatchMap", matchMapJson);
                inputMap.put(MetaOperation.SUCCEESCLICK, chkSuccessClick == null || chkSuccessClick.isChecked());
                inputMap.put(MetaOperation.MATCHUSEGRAY, chkUseGray != null && chkUseGray.isChecked());
                inputMap.put(MetaOperation.MATCHUSEMASK, chkUseMask == null || chkUseMask.isChecked());

                putOptionalMatchPreDelay(inputMap, edtPreDelay);
                fillPollingIntervalInputMap(dialogView, inputMap);

                String fallback = safeText(edtFallback);
                if (!TextUtils.isEmpty(fallback)) {
                    inputMap.put("FALLBACKOPERATIONID", fallback);
                }

                String nextOp = safeText(edtNextOperation);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put("nextOperationId", nextOp);
                }

                operationObject.put("inputMap", inputMap);

                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(operationObject);
                crudHelper.writeOperationsArray(operations, "已添加图集匹配操作", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) addListener.onOperationAdded();
                });

            } catch (Exception e) {
                host.showToast("添加图集匹配失败: " + e.getMessage());
            }
        });
    }

    public void showEditMatchMapTemplateDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_match_map_template, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 480, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        LinearLayout lyEntries = dialogView.findViewById(R.id.ly_match_entries);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallback = dialogView.findViewById(R.id.edt_fallback_operation);
        EditText edtPreDelay = dialogView.findViewById(R.id.edt_match_pre_delay);
        CheckBox chkSuccessClick = dialogView.findViewById(R.id.chk_success_click);
        CheckBox chkUseGray = dialogView.findViewById(R.id.chk_use_gray);
        CheckBox chkUseMask = dialogView.findViewById(R.id.chk_use_mask);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");
        setupMatchDelayHint(edtPreDelay);

        setupAdvancedToggle(dialogView);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        // 回填现有数据
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                Object timeout = inputMap.opt("MATCHTIMEOUT");
                edtTimeout.setText(timeout != null ? String.valueOf(timeout).replace(".0", "") : defaultMatchTimeoutText());
                setOperationReferenceText(edtNextOperation, inputMap.optString("nextOperationId", ""));
                setOperationReferenceText(edtFallback, inputMap.optString("FALLBACKOPERATIONID", ""));
                setNodePreDelayText(edtPreDelay, inputMap);
                setupPollingIntervalInputs(dialogView, AdaptivePollingController.Profile.MATCH_MAP, inputMap);
                if (chkSuccessClick != null) {
                    chkSuccessClick.setChecked(inputMap.optBoolean(MetaOperation.SUCCEESCLICK, true));
                }
                if (chkUseGray != null) {
                    chkUseGray.setChecked(inputMap.optBoolean(MetaOperation.MATCHUSEGRAY, false));
                }
                if (chkUseMask != null) {
                    chkUseMask.setChecked(inputMap.optBoolean(MetaOperation.MATCHUSEMASK, true));
                }

                // 恢复匹配条目（每个 bbox 对应一行，模板合并展示）
                JSONObject matchMap = inputMap.optJSONObject("MatchMap");
                if (matchMap != null) {
                    java.util.Iterator<String> bboxKeys = matchMap.keys();
                    while (bboxKeys.hasNext()) {
                        String bbox = bboxKeys.next();
                        JSONObject templates = matchMap.optJSONObject(bbox);
                        if (templates != null) {
                            java.util.List<String> tplNames = new java.util.ArrayList<>();
                            double sim = 0.88;
                            java.util.Iterator<String> tplKeys = templates.keys();
                            while (tplKeys.hasNext()) {
                                String tpl = tplKeys.next();
                                sim = templates.optDouble(tpl, 0.88);
                                tplNames.add(tpl);
                            }
                            addMatchMapEntryRow(lyEntries, dialogView, bbox, tplNames, String.valueOf(sim));
                        }
                    }
                }
            } else {
                edtTimeout.setText(defaultMatchTimeoutText());
                if (chkSuccessClick != null) {
                    chkSuccessClick.setChecked(true);
                }
                if (chkUseGray != null) {
                    chkUseGray.setChecked(false);
                }
                if (chkUseMask != null) {
                    chkUseMask.setChecked(true);
                }
            }
        } catch (Exception e) {
            host.showToast("加载图集匹配数据失败: " + e.getMessage());
        }

        if (lyEntries.getChildCount() == 0) {
            addMatchMapEntryRow(lyEntries, dialogView, "", new java.util.ArrayList<>(), "0.88");
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_add_entry).setOnClickListener(v ->
            addMatchMapEntryRow(lyEntries, dialogView, "", new java.util.ArrayList<>(), "0.88"));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", null, edtFallback);
            }
        });

        TextView btnTestMatch = dialogView.findViewById(R.id.btn_test_match);
        btnTestMatch.setOnClickListener(v -> {
            MetaOperation testOperation = buildMatchMapTestOperation(
                    edtName, edtTimeout, lyEntries, chkSuccessClick, chkUseGray, chkUseMask, edtPreDelay, dialogView);
            if (testOperation != null) {
                runSingleMatchTest(dialogView, btnTestMatch, testOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String timeoutText = edtTimeout.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }

            long timeout;
            try {
                timeout = Long.parseLong(timeoutText);
            } catch (Exception e) {
                edtTimeout.setError("请输入超时时间(毫秒)");
                return;
            }

            try {
                JSONObject matchMapJson = collectMatchMapEntries(lyEntries);
                if (matchMapJson.length() == 0) {
                    host.showToast("请至少添加一条匹配规则");
                    return;
                }

                JSONObject updatedOp = new JSONObject();
                updatedOp.put("id", operationId);
                updatedOp.put("name", name);
                updatedOp.put("type", 7);
                updatedOp.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                java.io.File currentProjectDir = host.getCurrentProjectDir();
                java.io.File currentTaskDir = host.getCurrentTaskDir();
                inputMap.put("PROJECT", currentProjectDir != null ? currentProjectDir.getName() : "");
                inputMap.put("TASK", currentTaskDir != null ? currentTaskDir.getName() : "");
                inputMap.put("MATCHTIMEOUT", (double) timeout);
                inputMap.put("MatchMap", matchMapJson);
                inputMap.put(MetaOperation.SUCCEESCLICK, chkSuccessClick == null || chkSuccessClick.isChecked());
                inputMap.put(MetaOperation.MATCHUSEGRAY, chkUseGray != null && chkUseGray.isChecked());
                inputMap.put(MetaOperation.MATCHUSEMASK, chkUseMask == null || chkUseMask.isChecked());

                putOptionalMatchPreDelay(inputMap, edtPreDelay);
                fillPollingIntervalInputMap(dialogView, inputMap);

                String fallback = safeText(edtFallback);
                if (!TextUtils.isEmpty(fallback)) {
                    inputMap.put("FALLBACKOPERATIONID", fallback);
                }

                String nextOp = safeText(edtNextOperation);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put("nextOperationId", nextOp);
                }

                updatedOp.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    operationUpdater.saveOperationJson(operationId, updatedOp.toString());
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) updateListener.onOperationUpdated();
                }

            } catch (Exception e) {
                host.showToast("保存图集匹配失败: " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void addMatchMapEntryRow(LinearLayout container, View mainDialogView,
                                     String bbox, java.util.List<String> templates, String similarity) {
        View row = LayoutInflater.from(host.getContext()).inflate(R.layout.item_match_map_entry_row, container, false);

        EditText edtBbox = row.findViewById(R.id.edt_match_bbox);
        android.widget.TextView tvTemplates = row.findViewById(R.id.tv_selected_templates);
        EditText edtSim = row.findViewById(R.id.edt_match_similarity);

        // Initialise values
        if (!TextUtils.isEmpty(bbox)) edtBbox.setText(bbox);
        if (!TextUtils.isEmpty(similarity)) edtSim.setText(similarity);
        java.util.List<String> tplList = templates != null ? new java.util.ArrayList<>(templates) : new java.util.ArrayList<>();
        tvTemplates.setTag(tplList);
        tvTemplates.setText(tplList.isEmpty() ? "" : android.text.TextUtils.join(", ", tplList));

        // ── 框选区域 ──────────────────────────────────────────────────
        row.findViewById(R.id.btn_pick_region).setOnClickListener(v -> {
            if (matchMapHelper != null) matchMapHelper.beginRegionPickForRow(mainDialogView, edtBbox);
        });

        // ── 从模板 manifest 导入 bbox ──────────────────────────────────
        row.findViewById(R.id.btn_import_bbox).setOnClickListener(v -> {
            if (matchMapHelper != null) matchMapHelper.importBboxFromTemplate(mainDialogView, edtBbox);
        });

        // ── 预览搜索区域 ───────────────────────────────────────────────
        row.findViewById(R.id.btn_preview_region).setOnClickListener(v -> {
            String bboxText = edtBbox.getText().toString().trim();
            if (TextUtils.isEmpty(bboxText)) {
                host.showToast("请先填写区域坐标");
                return;
            }
            try {
                String[] parts = bboxText.split("[,，\\s]+");
                if (parts.length < 4) {
                    host.showToast("区域格式: x,y,w,h");
                    return;
                }
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int w = Integer.parseInt(parts[2].trim());
                int h = Integer.parseInt(parts[3].trim());
                host.showRawBboxPreview(x, y, w, h);
            } catch (Exception e) {
                host.showToast("区域格式无效");
            }
        });

        // ── 多选模板 ──────────────────────────────────────────────────
        row.findViewById(R.id.btn_select_templates).setOnClickListener(v -> {
            if (matchMapHelper != null) {
                java.util.List<String> current = (java.util.List<String>) tvTemplates.getTag();
                matchMapHelper.showTemplateMultiSelectDialog(
                    current != null ? current : new java.util.ArrayList<>(),
                    selected -> {
                        tvTemplates.setTag(new java.util.ArrayList<>(selected));
                        tvTemplates.setText(selected.isEmpty() ? "" : android.text.TextUtils.join(", ", selected));
                    });
            }
        });

        // ── 上移 ──────────────────────────────────────────────────────
        row.findViewById(R.id.btn_move_up).setOnClickListener(v -> moveRowUp(container, row));

        // ── 下移 ──────────────────────────────────────────────────────
        row.findViewById(R.id.btn_move_down).setOnClickListener(v -> moveRowDown(container, row));

        // ── 删除 ──────────────────────────────────────────────────────
        row.findViewById(R.id.btn_delete_entry).setOnClickListener(v -> container.removeView(row));

        container.addView(row);
    }

    private void moveRowUp(LinearLayout container, View row) {
        int idx = container.indexOfChild(row);
        if (idx > 0) {
            container.removeViewAt(idx);
            container.addView(row, idx - 1);
        }
    }

    private void moveRowDown(LinearLayout container, View row) {
        int idx = container.indexOfChild(row);
        if (idx >= 0 && idx < container.getChildCount() - 1) {
            container.removeViewAt(idx);
            container.addView(row, idx + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private JSONObject collectMatchMapEntries(LinearLayout container) throws org.json.JSONException {
        JSONObject matchMap = new JSONObject();
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            EditText edtBbox = row.findViewById(R.id.edt_match_bbox);
            android.widget.TextView tvTpls = row.findViewById(R.id.tv_selected_templates);
            EditText edtSim = row.findViewById(R.id.edt_match_similarity);
            if (edtBbox == null || tvTpls == null || edtSim == null) continue;

            String bbox = edtBbox.getText().toString().trim();
            java.util.List<String> tpls = (java.util.List<String>) tvTpls.getTag();
            boolean hasBbox = !TextUtils.isEmpty(bbox);
            boolean hasTemplates = tpls != null && !tpls.isEmpty();
            if (!hasBbox && !hasTemplates) continue;
            if (!hasBbox) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 条规则未填写区域");
            }
            if (!hasTemplates) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 条规则未选择模板");
            }

            double sim = 0.88;
            try { sim = Double.parseDouble(edtSim.getText().toString().trim()); } catch (Exception ignored) {}

            if (!matchMap.has(bbox)) matchMap.put(bbox, new JSONObject());
            JSONObject group = matchMap.getJSONObject(bbox);
            for (String tpl : tpls) {
                if (!TextUtils.isEmpty(tpl)) group.put(tpl, sim);
            }
        }
        return matchMap;
    }

    private void setupAdvancedToggle(View dialogView) {
        LinearLayout lyAdvancedToggle = dialogView.findViewById(R.id.ly_advanced_toggle);
        LinearLayout lyAdvancedPanel = dialogView.findViewById(R.id.ly_advanced_panel);
        android.widget.TextView tvArrow = dialogView.findViewById(R.id.tv_advanced_arrow);
        if (lyAdvancedToggle == null || lyAdvancedPanel == null) return;
        lyAdvancedToggle.setOnClickListener(v -> {
            boolean visible = lyAdvancedPanel.getVisibility() == View.VISIBLE;
            lyAdvancedPanel.setVisibility(visible ? View.GONE : View.VISIBLE);
            if (tvArrow != null) tvArrow.setText(visible ? "▼" : "▲");
        });
    }

    // ==================== ColorMatch Operation ====================

    public void showAddColorMatchDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_color_match, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 500, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        AutoCompleteTextView edtMode = dialogView.findViewById(R.id.edt_color_match_mode);
        LinearLayout lyPoints = dialogView.findViewById(R.id.ly_color_points);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallback = dialogView.findViewById(R.id.edt_fallback_operation);
        EditText edtPreDelay = dialogView.findViewById(R.id.edt_match_pre_delay);

        edtTimeout.setText(defaultMatchTimeoutText());
        setupMatchDelayHint(edtPreDelay);
        dialogHelpers.bindDropdownSelect(edtMode, java.util.Arrays.asList("全部点都命中", "任意一点命中"));
        edtMode.setText("全部点都命中", false);
        setupAdvancedToggle(dialogView);
        setupPollingIntervalInputs(dialogView, AdaptivePollingController.Profile.COLOR_CHECK, null);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        addColorMatchPointRow(lyPoints, dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_add_color_point).setOnClickListener(v -> addColorMatchPointRow(lyPoints, dialogView, null));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", null, edtFallback);
            }
        });
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = safeText(edtName);
            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }

            Long timeout = parsePositiveLong(safeText(edtTimeout));
            if (timeout == null) {
                edtTimeout.setError("请输入有效超时(毫秒)");
                return;
            }

            try {
                JSONArray points = collectColorMatchPoints(lyPoints);
                if (points.length() == 0) {
                    host.showToast("请至少添加一个采样点");
                    return;
                }

                JSONObject operationObject = new JSONObject();
                operationObject.put("id", idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis());
                operationObject.put("name", name);
                operationObject.put("type", 18);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.COLOR_POINTS, points);
                inputMap.put(MetaOperation.COLOR_MATCH_MODE, parseColorMatchMode(safeText(edtMode)));
                inputMap.put(MetaOperation.MATCHTIMEOUT, timeout);
                putOptionalMatchPreDelay(inputMap, edtPreDelay);
                fillPollingIntervalInputMap(dialogView, inputMap);
                if (!TextUtils.isEmpty(safeText(edtFallback))) {
                    inputMap.put(MetaOperation.FALLBACKOPERATIONID, safeText(edtFallback));
                }
                if (!TextUtils.isEmpty(safeText(edtNextOperation))) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, safeText(edtNextOperation));
                }
                operationObject.put("inputMap", inputMap);

                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(operationObject);
                crudHelper.writeOperationsArray(operations, "已添加颜色匹配操作", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) {
                        addListener.onOperationAdded();
                    }
                });
            } catch (Exception e) {
                host.showToast("添加颜色匹配失败: " + e.getMessage());
            }
        });
    }

    public void showEditColorMatchDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_color_match, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 500, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        AutoCompleteTextView edtMode = dialogView.findViewById(R.id.edt_color_match_mode);
        LinearLayout lyPoints = dialogView.findViewById(R.id.ly_color_points);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallback = dialogView.findViewById(R.id.edt_fallback_operation);
        EditText edtPreDelay = dialogView.findViewById(R.id.edt_match_pre_delay);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        dialogHelpers.bindDropdownSelect(edtMode, java.util.Arrays.asList("全部点都命中", "任意一点命中"));
        edtMode.setText("全部点都命中", false);
        edtTimeout.setText(defaultMatchTimeoutText());
        setupMatchDelayHint(edtPreDelay);
        setupAdvancedToggle(dialogView);
        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                Object timeoutObj = inputMap.opt(MetaOperation.MATCHTIMEOUT);
                edtTimeout.setText(timeoutObj == null ? defaultMatchTimeoutText() : String.valueOf(timeoutObj).replace(".0", ""));
                edtMode.setText(displayColorMatchMode(inputMap.optString(MetaOperation.COLOR_MATCH_MODE, MetaOperation.COLOR_MATCH_MODE_ALL)), false);
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                setOperationReferenceText(edtFallback, inputMap.optString(MetaOperation.FALLBACKOPERATIONID, ""));
                setNodePreDelayText(edtPreDelay, inputMap);
                setupPollingIntervalInputs(dialogView, AdaptivePollingController.Profile.COLOR_CHECK, inputMap);
                JSONArray points = inputMap.optJSONArray(MetaOperation.COLOR_POINTS);
                if (points != null) {
                    for (int i = 0; i < points.length(); i++) {
                        JSONObject point = points.optJSONObject(i);
                        if (point != null) {
                            addColorMatchPointRow(lyPoints, dialogView, point);
                        }
                    }
                }
            }
        } catch (Exception e) {
            host.showToast("加载颜色匹配数据失败: " + e.getMessage());
        }

        if (lyPoints.getChildCount() == 0) {
            addColorMatchPointRow(lyPoints, dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_add_color_point).setOnClickListener(v -> addColorMatchPointRow(lyPoints, dialogView, null));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", null, edtFallback);
            }
        });
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = safeText(edtName);
            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            Long timeout = parsePositiveLong(safeText(edtTimeout));
            if (timeout == null) {
                edtTimeout.setError("请输入有效超时(毫秒)");
                return;
            }

            try {
                JSONArray points = collectColorMatchPoints(lyPoints);
                if (points.length() == 0) {
                    host.showToast("请至少添加一个采样点");
                    return;
                }

                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 18);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.COLOR_POINTS, points);
                inputMap.put(MetaOperation.COLOR_MATCH_MODE, parseColorMatchMode(safeText(edtMode)));
                inputMap.put(MetaOperation.MATCHTIMEOUT, timeout);
                putOptionalMatchPreDelay(inputMap, edtPreDelay);
                fillPollingIntervalInputMap(dialogView, inputMap);
                if (!TextUtils.isEmpty(safeText(edtFallback))) {
                    inputMap.put(MetaOperation.FALLBACKOPERATIONID, safeText(edtFallback));
                }
                if (!TextUtils.isEmpty(safeText(edtNextOperation))) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, safeText(edtNextOperation));
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) {
                        updateListener.onOperationUpdated();
                    }
                }
            } catch (Exception e) {
                host.showToast("保存颜色匹配失败: " + e.getMessage());
            }
        });
    }

    private void addColorMatchPointRow(LinearLayout container, View ownerDialog, JSONObject pointObject) {
        View row = LayoutInflater.from(host.getContext()).inflate(R.layout.item_color_match_point, container, false);
        TextView tvTitle = row.findViewById(R.id.tv_point_title);
        EditText edtPoint = row.findViewById(R.id.edt_point_xy);
        EditText edtColor = row.findViewById(R.id.edt_point_color);
        EditText edtTolerance = row.findViewById(R.id.edt_point_tolerance);
        View colorPreview = row.findViewById(R.id.view_point_color_preview);

        int rowIndex = container.getChildCount() + 1;
        tvTitle.setText("采样点 " + rowIndex);
        edtTolerance.setText("12");

        if (pointObject != null) {
            int x = pointObject.optInt("x", -1);
            int y = pointObject.optInt("y", -1);
            if (x >= 0 && y >= 0) {
                edtPoint.setText(x + "," + y);
            }
            String colorText = pointObject.optString(MetaOperation.COLOR_VALUE, "");
            if (!TextUtils.isEmpty(colorText)) {
                edtColor.setText(colorText);
            }
            Object tolerance = pointObject.opt(MetaOperation.COLOR_TOLERANCE);
            if (tolerance != null) {
                edtTolerance.setText(String.valueOf(tolerance).replace(".0", ""));
            }
        }

        applyColorPreview(colorPreview, safeText(edtColor));
        edtColor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applyColorPreview(colorPreview, s == null ? "" : s.toString());
            }
        });

        row.findViewById(R.id.btn_pick_color_point).setOnClickListener(v -> {
            if (colorPointPickerLauncher != null) {
                colorPointPickerLauncher.showColorPointPicker((x, y, color) -> {
                    edtPoint.setText(x + "," + y);
                    edtColor.setText(formatColor(color));
                    applyColorPreview(colorPreview, formatColor(color));
                }, ownerDialog);
            }
        });
        row.findViewById(R.id.btn_remove_point).setOnClickListener(v -> {
            container.removeView(row);
            refreshColorPointTitles(container);
        });

        container.addView(row);
        refreshColorPointTitles(container);
    }

    private void refreshColorPointTitles(LinearLayout container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            TextView title = child.findViewById(R.id.tv_point_title);
            if (title != null) {
                title.setText("采样点 " + (i + 1));
            }
        }
    }

    private JSONArray collectColorMatchPoints(LinearLayout container) throws org.json.JSONException {
        JSONArray array = new JSONArray();
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            EditText edtPoint = row.findViewById(R.id.edt_point_xy);
            EditText edtColor = row.findViewById(R.id.edt_point_color);
            EditText edtTolerance = row.findViewById(R.id.edt_point_tolerance);

            String pointText = safeText(edtPoint);
            String colorText = normalizeColorText(safeText(edtColor));
            String toleranceText = safeText(edtTolerance);
            boolean hasAny = !TextUtils.isEmpty(pointText) || !TextUtils.isEmpty(colorText);
            if (!hasAny) {
                continue;
            }

            int[] xy = parsePointText(pointText);
            if (xy == null) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个采样点坐标格式应为 x,y");
            }
            if (TextUtils.isEmpty(colorText)) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个采样点未设置颜色");
            }
            try {
                android.graphics.Color.parseColor(colorText);
            } catch (Exception e) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个采样点颜色格式不正确");
            }

            int tolerance = 12;
            if (!TextUtils.isEmpty(toleranceText)) {
                try {
                    tolerance = Integer.parseInt(toleranceText);
                } catch (Exception e) {
                    throw new IllegalArgumentException("第 " + (i + 1) + " 个采样点容差不是数字");
                }
            }
            tolerance = Math.max(0, Math.min(255, tolerance));

            JSONObject item = new JSONObject();
            item.put("x", xy[0]);
            item.put("y", xy[1]);
            item.put(MetaOperation.COLOR_VALUE, colorText);
            item.put(MetaOperation.COLOR_TOLERANCE, tolerance);
            array.put(item);
        }
        return array;
    }

    private void applyColorPreview(View preview, String colorText) {
        if (preview == null) {
            return;
        }
        int color = 0xFFFFFFFF;
        try {
            color = android.graphics.Color.parseColor(normalizeColorText(colorText));
        } catch (Exception ignored) {
        }
        preview.setBackgroundColor(color);
    }

    private String normalizeColorText(String raw) {
        String text = raw == null ? "" : raw.trim().toUpperCase();
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        return text.startsWith("#") ? text : ("#" + text);
    }

    private String formatColor(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    private int[] parsePointText(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            if (x < 0 || y < 0) {
                return null;
            }
            return new int[]{x, y};
        } catch (Exception e) {
            return null;
        }
    }

    private String parseColorMatchMode(String displayText) {
        return "任意一点命中".equals(displayText) ? MetaOperation.COLOR_MATCH_MODE_ANY : MetaOperation.COLOR_MATCH_MODE_ALL;
    }

    private String displayColorMatchMode(String mode) {
        return MetaOperation.COLOR_MATCH_MODE_ANY.equalsIgnoreCase(mode) ? "任意一点命中" : "全部点都命中";
    }

    private void showOperationPickerForField(String title, String excludeId, TextView targetView) {
        if (operationPickerLauncher == null || targetView == null) {
            return;
        }
        operationPickerLauncher.showOperationPickerDialog(
                title,
                excludeId,
                safeText(targetView),
                operationId -> setOperationReferenceText(targetView, operationId));
    }

    private void setOperationReferenceText(TextView view, String rawValue) {
        if (view == null) {
            return;
        }
        String operationId = extractOperationReferenceId(rawValue);
        view.setText(TextUtils.isEmpty(operationId) ? "" : operationId);
    }

    private String extractOperationReferenceId(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        int start = raw.lastIndexOf('<');
        int end = raw.lastIndexOf('>');
        if (start >= 0 && end > start + 1) {
            return raw.substring(start + 1, end).trim();
        }
        return raw.trim();
    }

    private boolean isOperationReferenceField(TextView view) {
        if (view == null) {
            return false;
        }
        int id = view.getId();
        return id == R.id.edt_next_operation
                || id == R.id.edt_fallback_operation
                || id == R.id.edt_wrapped_operation
                || id == R.id.edt_default_next
                || id == R.id.edt_body_next
                || id == R.id.edt_exit_next
                || id == R.id.edt_case_next;
    }

    private String safeText(TextView view) {
        if (view == null || view.getText() == null) {
            return "";
        }
        String raw = view.getText().toString().trim();
        return isOperationReferenceField(view) ? extractOperationReferenceId(raw) : raw;
    }

    public void showAddVariableSetDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_variable_set, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        AutoCompleteTextView edtSourceMode = dialogView.findViewById(R.id.edt_source_mode);
        EditText edtSourceValue = dialogView.findViewById(R.id.edt_source_value);
        android.widget.TextView tvSourceLabel = dialogView.findViewById(R.id.tv_source_value_label);
        AutoCompleteTextView edtVarType = dialogView.findViewById(R.id.edt_var_type);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        if (variableHelper != null) {
            dialogHelpers.bindDropdownSelect(edtSourceMode, variableHelper.getVariableSourceModes());
            dialogHelpers.bindDropdownSelect(edtVarType, variableHelper.getVariableValueTypes());
            variableHelper.bindVariableSourceModeWatcher(edtSourceMode, tvSourceLabel, edtSourceValue);
            edtSourceMode.setText(variableHelper.sourceModeValueToDisplay("literal"), false);
            variableHelper.updateVariableSourceInputUi(tvSourceLabel, edtSourceValue,
                variableHelper.sourceModeValueToDisplay("literal"));
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        edtVarType.setText("auto", false);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String sourceModeDisplay = edtSourceMode.getText() == null ?
                (variableHelper != null ? variableHelper.sourceModeValueToDisplay("literal") : "literal") :
                edtSourceMode.getText().toString().trim();
            String sourceMode = variableHelper != null ?
                variableHelper.sourceModeDisplayToValue(sourceModeDisplay) : "literal";
            String sourceValue = edtSourceValue.getText().toString();
            String varType = edtVarType.getText() == null ? "auto" : edtVarType.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }
            if (("variable".equalsIgnoreCase(sourceMode) || "response".equalsIgnoreCase(sourceMode))
                    && TextUtils.isEmpty(sourceValue.trim())) {
                edtSourceValue.setError("当前来源模式需要填写值");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) {
                    operationObject.put("id", idGenerator.generateId());
                }
                operationObject.put("name", name);
                operationObject.put("type", 11);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_SOURCE_MODE, TextUtils.isEmpty(sourceMode) ? "literal" : sourceMode);
                inputMap.put(MetaOperation.VAR_SOURCE_VALUE, sourceValue);
                inputMap.put(MetaOperation.VAR_TYPE, TextUtils.isEmpty(varType) ? "auto" : varType);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                try {
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(operationObject);
                    if (crudHelper.writeOperationsArray(operations, "已添加变量赋值操作", () -> {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (addListener != null) {
                            addListener.onOperationAdded();
                        }
                    })) {
                        // Success handled in callback
                    }
                } catch (Exception e) {
                    host.showToast("添加操作失败: " + e.getMessage());
                }
            } catch (Exception e) {
                host.showToast("构建变量赋值节点失败: " + e.getMessage());
            }
        });
    }

    public void showEditVariableSetDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_variable_set, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        AutoCompleteTextView edtSourceMode = dialogView.findViewById(R.id.edt_source_mode);
        EditText edtSourceValue = dialogView.findViewById(R.id.edt_source_value);
        android.widget.TextView tvSourceLabel = dialogView.findViewById(R.id.tv_source_value_label);
        AutoCompleteTextView edtVarType = dialogView.findViewById(R.id.edt_var_type);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        if (variableHelper != null) {
            dialogHelpers.bindDropdownSelect(edtSourceMode, variableHelper.getVariableSourceModes());
            dialogHelpers.bindDropdownSelect(edtVarType, variableHelper.getVariableValueTypes());
            variableHelper.bindVariableSourceModeWatcher(edtSourceMode, tvSourceLabel, edtSourceValue);
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                edtVarName.setText(inputMap.optString(MetaOperation.VAR_NAME, ""));
                String sourceMode = inputMap.optString(MetaOperation.VAR_SOURCE_MODE, "");
                if (variableHelper != null) {
                    edtSourceMode.setText(variableHelper.sourceModeValueToDisplay(sourceMode), false);
                }
                edtSourceValue.setText(inputMap.optString(MetaOperation.VAR_SOURCE_VALUE, ""));
                String varType = inputMap.optString(MetaOperation.VAR_TYPE, "");
                edtVarType.setText(TextUtils.isEmpty(varType) ? "auto" : varType, false);
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));

                if (variableHelper != null) {
                    String modeDisplay = edtSourceMode.getText() == null ?
                        variableHelper.sourceModeValueToDisplay("literal") :
                        edtSourceMode.getText().toString();
                    variableHelper.updateVariableSourceInputUi(tvSourceLabel, edtSourceValue, modeDisplay);
                }
            } else {
                edtVarType.setText("auto", false);
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String valueModeDisplay = edtSourceMode.getText() == null ?
                (variableHelper != null ? variableHelper.sourceModeValueToDisplay("literal") : "literal") :
                edtSourceMode.getText().toString().trim();
            String valueMode = variableHelper != null ?
                variableHelper.sourceModeDisplayToValue(valueModeDisplay) : "literal";
            String value = edtSourceValue.getText().toString();
            String valueType = edtVarType.getText() == null ? "auto" : edtVarType.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }
            if (("variable".equalsIgnoreCase(valueMode) || "response".equalsIgnoreCase(valueMode))
                    && TextUtils.isEmpty(value.trim())) {
                edtSourceValue.setError("当前来源模式需要填写值");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 11);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_SOURCE_MODE, TextUtils.isEmpty(valueMode) ? "literal" : valueMode);
                inputMap.put(MetaOperation.VAR_SOURCE_VALUE, value);
                inputMap.put(MetaOperation.VAR_TYPE, TextUtils.isEmpty(valueType) ? "auto" : valueType);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    public void showAddVariableScriptDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_variable_script, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtScript = dialogView.findViewById(R.id.edt_script);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择默认下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String script = edtScript.getText().toString();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(script.trim())) {
                edtScript.setError("请填写脚本代码");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) {
                    operationObject.put("id", idGenerator.generateId());
                }
                operationObject.put("name", name);
                operationObject.put("type", 11);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_SCRIPT_CODE, script);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                try {
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(operationObject);
                    if (crudHelper.writeOperationsArray(operations, "已添加变量脚本操作", () -> {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (addListener != null) {
                            addListener.onOperationAdded();
                        }
                    })) {
                        // Success handled in callback
                    }
                } catch (Exception e) {
                    host.showToast("添加操作失败: " + e.getMessage());
                }
            } catch (Exception e) {
                host.showToast("构建变量脚本节点失败: " + e.getMessage());
            }
        });
    }

    public void showEditVariableScriptDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_variable_script, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtScript = dialogView.findViewById(R.id.edt_script);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                edtScript.setText(inputMap.optString(MetaOperation.VAR_SCRIPT_CODE, ""));
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择默认下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String script = edtScript.getText().toString();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(script.trim())) {
                edtScript.setError("请填写脚本代码");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 11);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_SCRIPT_CODE, script);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    public void showAddVariableMathDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_variable_math, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        AutoCompleteTextView edtAction = dialogView.findViewById(R.id.edt_action);
        AutoCompleteTextView edtOperandMode = dialogView.findViewById(R.id.edt_operand_mode);
        EditText edtOperandValue = dialogView.findViewById(R.id.edt_operand_value);
        android.widget.TextView tvOperandLabel = dialogView.findViewById(R.id.tv_operand_label);
        AutoCompleteTextView edtOperandType = dialogView.findViewById(R.id.edt_operand_type);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        if (variableMathHelper != null) {
            dialogHelpers.bindDropdownSelect(edtAction, variableMathHelper.getVariableMathActions());
        }
        dialogHelpers.bindDropdownSelect(edtOperandMode, java.util.Arrays.asList("literal", "variable"));
        dialogHelpers.bindDropdownSelect(edtOperandType, java.util.Collections.singletonList("number"));

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        if (variableMathHelper != null) {
            variableMathHelper.bindVariableMathWatcher(edtOperandMode, edtAction, tvOperandLabel, edtOperandValue);
            variableMathHelper.updateVariableMathOperandUi(tvOperandLabel, edtOperandValue, "literal", "add");
        }

        edtAction.setText("add", false);
        edtOperandMode.setText("literal", false);
        edtOperandType.setText("number", false);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String action = edtAction.getText() == null ? "add" : edtAction.getText().toString().trim();
            String operandMode = edtOperandMode.getText() == null ? "literal" : edtOperandMode.getText().toString().trim();
            String operandValue = edtOperandValue.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }
            if (variableMathHelper != null && !variableMathHelper.isUnaryMathAction(action) && TextUtils.isEmpty(operandValue)) {
                edtOperandValue.setError("请填写操作数");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) {
                    operationObject.put("id", idGenerator.generateId());
                }
                operationObject.put("name", name);
                operationObject.put("type", 12);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_ACTION, TextUtils.isEmpty(action) ? "add" : action);
                inputMap.put(MetaOperation.VAR_OPERAND_MODE, TextUtils.isEmpty(operandMode) ? "literal" : operandMode);
                inputMap.put(MetaOperation.VAR_OPERAND_VALUE, operandValue);
                inputMap.put(MetaOperation.VAR_OPERAND_TYPE, "number");
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                try {
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(operationObject);
                    if (crudHelper.writeOperationsArray(operations, "已添加变量运算操作", () -> {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (addListener != null) {
                            addListener.onOperationAdded();
                        }
                    })) {
                        // Success handled in callback
                    }
                } catch (Exception e) {
                    host.showToast("添加操作失败: " + e.getMessage());
                }
            } catch (Exception e) {
                host.showToast("构建变量运算节点失败: " + e.getMessage());
            }
        });
    }

    public void showEditVariableMathDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_variable_math, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        AutoCompleteTextView edtAction = dialogView.findViewById(R.id.edt_action);
        AutoCompleteTextView edtOperandMode = dialogView.findViewById(R.id.edt_operand_mode);
        EditText edtOperandValue = dialogView.findViewById(R.id.edt_operand_value);
        android.widget.TextView tvOperandLabel = dialogView.findViewById(R.id.tv_operand_label);
        AutoCompleteTextView edtOperandType = dialogView.findViewById(R.id.edt_operand_type);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        if (variableMathHelper != null) {
            dialogHelpers.bindDropdownSelect(edtAction, variableMathHelper.getVariableMathActions());
        }
        dialogHelpers.bindDropdownSelect(edtOperandMode, java.util.Arrays.asList("literal", "variable"));
        dialogHelpers.bindDropdownSelect(edtOperandType, java.util.Collections.singletonList("number"));

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        if (variableMathHelper != null) {
            variableMathHelper.bindVariableMathWatcher(edtOperandMode, edtAction, tvOperandLabel, edtOperandValue);
        }

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                edtVarName.setText(inputMap.optString(MetaOperation.VAR_NAME, ""));
                String action = inputMap.optString(MetaOperation.VAR_ACTION, "");
                edtAction.setText(TextUtils.isEmpty(action) ? "add" : action, false);
                String operandMode = inputMap.optString(MetaOperation.VAR_OPERAND_MODE, "");
                edtOperandMode.setText(TextUtils.isEmpty(operandMode) ? "literal" : operandMode, false);
                edtOperandValue.setText(inputMap.optString(MetaOperation.VAR_OPERAND_VALUE, ""));
                edtOperandType.setText("number", false);
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));

                if (variableMathHelper != null) {
                    String mode = edtOperandMode.getText() == null ? "literal" : edtOperandMode.getText().toString();
                    String act = edtAction.getText() == null ? "add" : edtAction.getText().toString();
                    variableMathHelper.updateVariableMathOperandUi(tvOperandLabel, edtOperandValue, mode, act);
                }
            } else {
                edtAction.setText("add", false);
                edtOperandMode.setText("literal", false);
                edtOperandType.setText("number", false);
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String valueAction = edtAction.getText() == null ? "add" : edtAction.getText().toString().trim();
            String valueMode = edtOperandMode.getText() == null ? "literal" : edtOperandMode.getText().toString().trim();
            String value = edtOperandValue.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }
            if (variableMathHelper != null && !variableMathHelper.isUnaryMathAction(valueAction) && TextUtils.isEmpty(value)) {
                edtOperandValue.setError("请填写操作数");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 12);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_ACTION, TextUtils.isEmpty(valueAction) ? "add" : valueAction);
                inputMap.put(MetaOperation.VAR_OPERAND_MODE, TextUtils.isEmpty(valueMode) ? "literal" : valueMode);
                inputMap.put(MetaOperation.VAR_OPERAND_VALUE, value);
                inputMap.put(MetaOperation.VAR_OPERAND_TYPE, "number");
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    public void showAddVariableTemplateDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_variable_template, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        EditText edtTemplate = dialogView.findViewById(R.id.edt_template);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String template = edtTemplate.getText().toString();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) {
                    operationObject.put("id", idGenerator.generateId());
                }
                operationObject.put("name", name);
                operationObject.put("type", 13);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_TEMPLATE, template);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                try {
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(operationObject);
                    if (crudHelper.writeOperationsArray(operations, "已添加变量模板操作", () -> {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (addListener != null) {
                            addListener.onOperationAdded();
                        }
                    })) {
                        // Success handled in callback
                    }
                } catch (Exception e) {
                    host.showToast("添加操作失败: " + e.getMessage());
                }
            } catch (Exception e) {
                host.showToast("构建变量模板节点失败: " + e.getMessage());
            }
        });
    }

    public void showEditVariableTemplateDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_variable_template, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        EditText edtTemplate = dialogView.findViewById(R.id.edt_template);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                edtVarName.setText(inputMap.optString(MetaOperation.VAR_NAME, ""));
                edtTemplate.setText(inputMap.optString(MetaOperation.VAR_TEMPLATE, ""));
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String template = edtTemplate.getText().toString();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 13);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_TEMPLATE, template);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    // ==================== Launch App Operation ====================

    public void showAddLaunchAppDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_launch_app, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 460, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtAppPackage = dialogView.findViewById(R.id.edt_app_package);
        TextView tvAppSummary = dialogView.findViewById(R.id.tv_app_summary);
        EditText edtLaunchDelay = dialogView.findViewById(R.id.edt_launch_delay);
        CheckBox cbSkipIfForeground = dialogView.findViewById(R.id.cb_skip_if_foreground);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        if (launchAppHelper != null) {
            launchAppHelper.refreshAppOptions(edtAppPackage);
            launchAppHelper.updateAppSummary(tvAppSummary, "");
        }
        edtLaunchDelay.setText("1500");
        cbSkipIfForeground.setChecked(true);
        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_app).setOnClickListener(v -> {
            if (launchAppHelper != null) {
                String cur = edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString();
                launchAppHelper.showAppPicker("选择要启动的应用", cur, edtAppPackage, edtName, tvAppSummary);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        edtAppPackage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (launchAppHelper != null) {
                    launchAppHelper.updateAppSummary(tvAppSummary, s == null ? "" : s.toString());
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String raw = edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString();
            String packageName = launchAppHelper != null ? launchAppHelper.normalizePackageName(raw) : raw.trim();
            String delayStr = edtLaunchDelay.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(packageName)) {
                edtAppPackage.setError("请选择或输入应用包名");
                return;
            }

            long launchDelayMs;
            try {
                launchDelayMs = Long.parseLong(delayStr);
            } catch (Exception e) {
                edtLaunchDelay.setError("请输入有效的毫秒数");
                return;
            }
            if (launchDelayMs < 0L) {
                edtLaunchDelay.setError("等待时间不能小于 0");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis());
                operationObject.put("name", name);
                operationObject.put("type", 14);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.APP_PACKAGE, packageName);
                if (launchAppHelper != null) {
                    LaunchAppPickerAdapter.LaunchAppItem appItem = launchAppHelper.findApp(packageName);
                    if (appItem != null) {
                        inputMap.put(MetaOperation.APP_LABEL, appItem.label);
                    }
                }
                inputMap.put(MetaOperation.APP_SKIP_IF_FOREGROUND, cbSkipIfForeground.isChecked());
                inputMap.put(MetaOperation.APP_LAUNCH_DELAY_MS, launchDelayMs);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) {
                        addListener.onOperationAdded();
                    }
                }
            } catch (Exception e) {
                host.showToast("构建启动应用节点失败: " + e.getMessage());
            }
        });
    }

    public void showEditLaunchAppDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_launch_app, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 460, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtAppPackage = dialogView.findViewById(R.id.edt_app_package);
        TextView tvAppSummary = dialogView.findViewById(R.id.tv_app_summary);
        EditText edtLaunchDelay = dialogView.findViewById(R.id.edt_launch_delay);
        CheckBox cbSkipIfForeground = dialogView.findViewById(R.id.cb_skip_if_foreground);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        if (launchAppHelper != null) {
            launchAppHelper.refreshAppOptions(edtAppPackage);
        }
        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        // Pre-fill existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                String packageName = inputMap.optString(MetaOperation.APP_PACKAGE, "");
                edtAppPackage.setText(packageName);
                edtLaunchDelay.setText(String.valueOf(inputMap.optLong(MetaOperation.APP_LAUNCH_DELAY_MS, 1500L)));
                cbSkipIfForeground.setChecked(inputMap.optBoolean(MetaOperation.APP_SKIP_IF_FOREGROUND, true));
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                if (launchAppHelper != null) {
                    launchAppHelper.updateAppSummary(tvAppSummary, packageName);
                }
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_app).setOnClickListener(v -> {
            if (launchAppHelper != null) {
                String cur = edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString();
                launchAppHelper.showAppPicker("选择要启动的应用", cur, edtAppPackage, edtName, tvAppSummary);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        edtAppPackage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (launchAppHelper != null) {
                    launchAppHelper.updateAppSummary(tvAppSummary, s == null ? "" : s.toString());
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String raw = edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString();
            String packageName = launchAppHelper != null ? launchAppHelper.normalizePackageName(raw) : raw.trim();
            String delayStr = edtLaunchDelay.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(packageName)) {
                edtAppPackage.setError("请选择或输入应用包名");
                return;
            }

            long launchDelayMs;
            try {
                launchDelayMs = Long.parseLong(delayStr);
            } catch (Exception e) {
                edtLaunchDelay.setError("请输入有效的毫秒数");
                return;
            }
            if (launchDelayMs < 0L) {
                edtLaunchDelay.setError("等待时间不能小于 0");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 14);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.APP_PACKAGE, packageName);
                if (launchAppHelper != null) {
                    LaunchAppPickerAdapter.LaunchAppItem appItem = launchAppHelper.findApp(packageName);
                    if (appItem != null) {
                        inputMap.put(MetaOperation.APP_LABEL, appItem.label);
                    }
                }
                inputMap.put(MetaOperation.APP_SKIP_IF_FOREGROUND, cbSkipIfForeground.isChecked());
                inputMap.put(MetaOperation.APP_LAUNCH_DELAY_MS, launchDelayMs);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    // ==================== Close App Operation ====================

    public void showAddCloseAppDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_close_app, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 500, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtAppPackage = dialogView.findViewById(R.id.edt_app_package);
        TextView tvAppSummary = dialogView.findViewById(R.id.tv_app_summary);
        EditText edtCloseDelay = dialogView.findViewById(R.id.edt_close_delay);
        CheckBox cbReturnHome = dialogView.findViewById(R.id.cb_return_home);
        CheckBox cbKillBackground = dialogView.findViewById(R.id.cb_kill_background);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        if (launchAppHelper != null) {
            launchAppHelper.refreshAppOptions(edtAppPackage);
        }
        updateCloseAppSummary(tvAppSummary, "");
        edtCloseDelay.setText("800");
        cbReturnHome.setChecked(true);
        cbKillBackground.setChecked(true);
        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_app).setOnClickListener(v -> {
            if (launchAppHelper != null) {
                String cur = edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString();
                launchAppHelper.showAppPicker("选择要关闭的应用", cur, edtAppPackage, null, null);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        edtAppPackage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (launchAppHelper != null) {
                    updateCloseAppSummary(tvAppSummary, s == null ? "" : s.toString());
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String raw = edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString();
            String packageName = launchAppHelper != null ? launchAppHelper.normalizePackageName(raw) : raw.trim();
            String name = edtName.getText().toString().trim();
            String delayStr = edtCloseDelay.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(packageName)) {
                edtAppPackage.setError("请选择或输入应用包名");
                return;
            }

            long closeDelayMs;
            try {
                closeDelayMs = Long.parseLong(delayStr);
            } catch (Exception e) {
                edtCloseDelay.setError("请输入有效的毫秒数");
                return;
            }
            if (closeDelayMs < 0L) {
                edtCloseDelay.setError("等待时间不能小于 0");
                return;
            }

            LaunchAppPickerAdapter.LaunchAppItem appItem =
                    launchAppHelper != null ? launchAppHelper.findApp(packageName) : null;
            if (TextUtils.isEmpty(name)) {
                name = "关闭" + (appItem == null ? packageName : appItem.label);
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis());
                operationObject.put("name", name);
                operationObject.put("type", 23);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.APP_PACKAGE, packageName);
                if (appItem != null) {
                    inputMap.put(MetaOperation.APP_LABEL, appItem.label);
                }
                inputMap.put(MetaOperation.APP_CLOSE_DELAY_MS, closeDelayMs);
                inputMap.put(MetaOperation.APP_CLOSE_RETURN_HOME, cbReturnHome.isChecked());
                inputMap.put(MetaOperation.APP_CLOSE_KILL_BACKGROUND, cbKillBackground.isChecked());
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) {
                        addListener.onOperationAdded();
                    }
                }
            } catch (Exception e) {
                host.showToast("构建关闭应用节点失败: " + e.getMessage());
            }
        });
    }

    public void showEditCloseAppDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_close_app, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 500, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtAppPackage = dialogView.findViewById(R.id.edt_app_package);
        TextView tvAppSummary = dialogView.findViewById(R.id.tv_app_summary);
        EditText edtCloseDelay = dialogView.findViewById(R.id.edt_close_delay);
        CheckBox cbReturnHome = dialogView.findViewById(R.id.cb_return_home);
        CheckBox cbKillBackground = dialogView.findViewById(R.id.cb_kill_background);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        if (launchAppHelper != null) {
            launchAppHelper.refreshAppOptions(edtAppPackage);
        }
        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                String packageName = inputMap.optString(MetaOperation.APP_PACKAGE, "");
                edtAppPackage.setText(packageName);
                edtCloseDelay.setText(String.valueOf(inputMap.optLong(MetaOperation.APP_CLOSE_DELAY_MS, 800L)));
                cbReturnHome.setChecked(inputMap.optBoolean(MetaOperation.APP_CLOSE_RETURN_HOME, true));
                cbKillBackground.setChecked(inputMap.optBoolean(MetaOperation.APP_CLOSE_KILL_BACKGROUND, true));
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                if (launchAppHelper != null) {
                    updateCloseAppSummary(tvAppSummary, packageName);
                }
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_app).setOnClickListener(v -> {
            if (launchAppHelper != null) {
                String cur = edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString();
                launchAppHelper.showAppPicker("选择要关闭的应用", cur, edtAppPackage, null, null);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        edtAppPackage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (launchAppHelper != null) {
                    updateCloseAppSummary(tvAppSummary, s == null ? "" : s.toString());
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnConfirm.setOnClickListener(v -> {
            String raw = edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString();
            String packageName = launchAppHelper != null ? launchAppHelper.normalizePackageName(raw) : raw.trim();
            String name = edtName.getText().toString().trim();
            String delayStr = edtCloseDelay.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(packageName)) {
                edtAppPackage.setError("请选择或输入应用包名");
                return;
            }

            long closeDelayMs;
            try {
                closeDelayMs = Long.parseLong(delayStr);
            } catch (Exception e) {
                edtCloseDelay.setError("请输入有效的毫秒数");
                return;
            }
            if (closeDelayMs < 0L) {
                edtCloseDelay.setError("等待时间不能小于 0");
                return;
            }

            LaunchAppPickerAdapter.LaunchAppItem appItem =
                    launchAppHelper != null ? launchAppHelper.findApp(packageName) : null;
            if (TextUtils.isEmpty(name)) {
                name = "关闭" + (appItem == null ? packageName : appItem.label);
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 23);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.APP_PACKAGE, packageName);
                if (appItem != null) {
                    inputMap.put(MetaOperation.APP_LABEL, appItem.label);
                }
                inputMap.put(MetaOperation.APP_CLOSE_DELAY_MS, closeDelayMs);
                inputMap.put(MetaOperation.APP_CLOSE_RETURN_HOME, cbReturnHome.isChecked());
                inputMap.put(MetaOperation.APP_CLOSE_KILL_BACKGROUND, cbKillBackground.isChecked());
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) {
                            updateListener.onOperationUpdated();
                        }
                    }
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    private void updateCloseAppSummary(TextView summaryView, String packageName) {
        if (summaryView == null) {
            return;
        }
        String normalized = launchAppHelper != null ? launchAppHelper.normalizePackageName(packageName) :
                (packageName == null ? "" : packageName.trim());
        if (TextUtils.isEmpty(normalized)) {
            summaryView.setText("状态: 未选择应用");
            return;
        }
        LaunchAppPickerAdapter.LaunchAppItem item =
                launchAppHelper != null ? launchAppHelper.findApp(normalized) : null;
        if (item == null) {
            summaryView.setText("状态: 将尝试关闭 " + normalized);
            return;
        }
        summaryView.setText("状态: " + item.label + " (" + item.packageName + ")");
    }

    // ==================== Switch Branch Operation ====================

    public void showAddSwitchBranchDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_switch_branch, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 520, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        LinearLayout lyCaseContainer = dialogView.findViewById(R.id.ly_case_container);
        AutoCompleteTextView edtDefaultNext = dialogView.findViewById(R.id.edt_default_next);

        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_add_case).setOnClickListener(v ->
            addSwitchCaseRow(lyCaseContainer, null, null));

        dialogView.findViewById(R.id.btn_pick_default).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择 default 节点", null, edtDefaultNext);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String defaultNext = safeText(edtDefaultNext);

            if (TextUtils.isEmpty(name)) { edtName.setError("请填写操作名称"); return; }
            if (TextUtils.isEmpty(varName)) { edtVarName.setError("请填写变量名"); return; }

            try {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) operationObject.put("id", idGenerator.generateId());
                operationObject.put("name", name);
                operationObject.put("type", 15);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.SWITCH_VAR_NAME, varName);
                inputMap.put(MetaOperation.BRANCH_DEFAULT_NEXT, defaultNext);
                inputMap.put(MetaOperation.BRANCH_RULES, collectSwitchCases(lyCaseContainer));
                operationObject.put("inputMap", inputMap);

                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(operationObject);
                crudHelper.writeOperationsArray(operations, "已添加多路分支节点", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) addListener.onOperationAdded();
                });
            } catch (Exception e) {
                host.showToast("构建多路分支节点失败: " + e.getMessage());
            }
        });
    }

    public void showEditSwitchBranchDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_switch_branch, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 520, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        LinearLayout lyCaseContainer = dialogView.findViewById(R.id.ly_case_container);
        AutoCompleteTextView edtDefaultNext = dialogView.findViewById(R.id.edt_default_next);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                edtVarName.setText(inputMap.optString(MetaOperation.SWITCH_VAR_NAME, ""));
                setOperationReferenceText(edtDefaultNext, inputMap.optString(MetaOperation.BRANCH_DEFAULT_NEXT, ""));
                org.json.JSONArray rulesArr = inputMap.optJSONArray(MetaOperation.BRANCH_RULES);
                if (rulesArr != null) {
                    for (int i = 0; i < rulesArr.length(); i++) {
                        JSONObject rule = rulesArr.optJSONObject(i);
                        if (rule != null) {
                            addSwitchCaseRow(lyCaseContainer,
                                rule.optString("value", ""),
                                rule.optString("nextOperationId", ""));
                        }
                    }
                }
            }
        } catch (Exception e) {
            host.showToast("加载数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_add_case).setOnClickListener(v ->
            addSwitchCaseRow(lyCaseContainer, null, null));

        dialogView.findViewById(R.id.btn_pick_default).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择 default 节点", null, edtDefaultNext);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String defaultNext = safeText(edtDefaultNext);

            if (TextUtils.isEmpty(name)) { edtName.setError("请填写操作名称"); return; }
            if (TextUtils.isEmpty(varName)) { edtVarName.setError("请填写变量名"); return; }

            try {
                JSONObject updated = new JSONObject();
                updated.put("id", operationId);
                updated.put("name", name);
                updated.put("type", 15);
                updated.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.SWITCH_VAR_NAME, varName);
                inputMap.put(MetaOperation.BRANCH_DEFAULT_NEXT, defaultNext);
                inputMap.put(MetaOperation.BRANCH_RULES, collectSwitchCases(lyCaseContainer));
                updated.put("inputMap", inputMap);

                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updated.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) updateListener.onOperationUpdated();
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    // ==================== Loop Operation ====================

    public void showAddLoopDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_loop, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 520, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtConditionVar = dialogView.findViewById(R.id.edt_condition_var);
        AutoCompleteTextView edtOperator = dialogView.findViewById(R.id.edt_operator);
        EditText edtOperand = dialogView.findViewById(R.id.edt_operand);
        AutoCompleteTextView edtBodyNext = dialogView.findViewById(R.id.edt_body_next);
        AutoCompleteTextView edtExitNext = dialogView.findViewById(R.id.edt_exit_next);

        java.util.List<String> operators = java.util.Arrays.asList(
            "is_true", "is_false", "lt", "lte", "gt", "gte", "eq", "neq", "not_empty", "empty");
        dialogHelpers.bindDropdownSelect(edtOperator, operators);
        edtOperator.setText("is_true", false);

        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_body).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择主路径节点", null, edtBodyNext);
            }
        });

        dialogView.findViewById(R.id.btn_pick_exit).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择另一条路径节点", null, edtExitNext);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String condVar = edtConditionVar.getText().toString().trim();
            String operator = edtOperator.getText().toString().trim();
            String operand = edtOperand.getText().toString().trim();
            String bodyNext = safeText(edtBodyNext);
            String exitNext = safeText(edtExitNext);

            if (TextUtils.isEmpty(name)) { edtName.setError("请填写操作名称"); return; }
            if (TextUtils.isEmpty(condVar)) { edtConditionVar.setError("请填写条件变量名"); return; }
            if (TextUtils.isEmpty(bodyNext)) { edtBodyNext.setError("请选择主路径节点"); return; }

            try {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) operationObject.put("id", idGenerator.generateId());
                operationObject.put("name", name);
                operationObject.put("type", 16);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.LOOP_CONDITION_VAR, condVar);
                inputMap.put(MetaOperation.LOOP_OPERATOR, operator);
                inputMap.put(MetaOperation.LOOP_OPERAND, operand);
                inputMap.put(MetaOperation.LOOP_BODY_NEXT, bodyNext);
                inputMap.put(MetaOperation.LOOP_EXIT_NEXT, exitNext);
                operationObject.put("inputMap", inputMap);

                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(operationObject);
                crudHelper.writeOperationsArray(operations, "已添加二分路节点", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) addListener.onOperationAdded();
                });
            } catch (Exception e) {
                host.showToast("构建二分路节点失败: " + e.getMessage());
            }
        });
    }

    public void showEditLoopDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_loop, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 520, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtConditionVar = dialogView.findViewById(R.id.edt_condition_var);
        AutoCompleteTextView edtOperator = dialogView.findViewById(R.id.edt_operator);
        EditText edtOperand = dialogView.findViewById(R.id.edt_operand);
        AutoCompleteTextView edtBodyNext = dialogView.findViewById(R.id.edt_body_next);
        AutoCompleteTextView edtExitNext = dialogView.findViewById(R.id.edt_exit_next);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        java.util.List<String> operators = java.util.Arrays.asList(
            "is_true", "is_false", "lt", "lte", "gt", "gte", "eq", "neq", "not_empty", "empty");
        dialogHelpers.bindDropdownSelect(edtOperator, operators);

        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                edtConditionVar.setText(inputMap.optString(MetaOperation.LOOP_CONDITION_VAR, ""));
                edtOperator.setText(inputMap.optString(MetaOperation.LOOP_OPERATOR, "is_true"), false);
                edtOperand.setText(inputMap.optString(MetaOperation.LOOP_OPERAND, ""));
                setOperationReferenceText(edtBodyNext, inputMap.optString(MetaOperation.LOOP_BODY_NEXT, ""));
                setOperationReferenceText(edtExitNext, inputMap.optString(MetaOperation.LOOP_EXIT_NEXT, ""));
            }
        } catch (Exception e) {
            host.showToast("加载数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_body).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择主路径节点", null, edtBodyNext);
            }
        });

        dialogView.findViewById(R.id.btn_pick_exit).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择另一条路径节点", null, edtExitNext);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String condVar = edtConditionVar.getText().toString().trim();
            String operator = edtOperator.getText().toString().trim();
            String operand = edtOperand.getText().toString().trim();
            String bodyNext = safeText(edtBodyNext);
            String exitNext = safeText(edtExitNext);

            if (TextUtils.isEmpty(name)) { edtName.setError("请填写操作名称"); return; }
            if (TextUtils.isEmpty(condVar)) { edtConditionVar.setError("请填写条件变量名"); return; }
            if (TextUtils.isEmpty(bodyNext)) { edtBodyNext.setError("请选择主路径节点"); return; }

            try {
                JSONObject updated = new JSONObject();
                updated.put("id", operationId);
                updated.put("name", name);
                updated.put("type", 16);
                updated.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.LOOP_CONDITION_VAR, condVar);
                inputMap.put(MetaOperation.LOOP_OPERATOR, operator);
                inputMap.put(MetaOperation.LOOP_OPERAND, operand);
                inputMap.put(MetaOperation.LOOP_BODY_NEXT, bodyNext);
                inputMap.put(MetaOperation.LOOP_EXIT_NEXT, exitNext);
                updated.put("inputMap", inputMap);

                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updated.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) updateListener.onOperationUpdated();
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    // ==================== MTry Operation ====================

    public void showAddMtryDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_mtry, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 460, null);

        bindMtryDialog(dialogView, null, null);
    }

    public void showEditMtryDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_mtry, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 460, null);

        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) {
            btnConfirm.setText("保存");
        }
        bindMtryDialog(dialogView, operationId, operationObject);
    }

    private void bindMtryDialog(View dialogView, String operationId, JSONObject operationObject) {
        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtWrapped = dialogView.findViewById(R.id.edt_wrapped_operation);
        EditText edtAttempts = dialogView.findViewById(R.id.edt_mtry_attempts);
        CheckBox chkRunResponseHandler = dialogView.findViewById(R.id.chk_mtry_run_response_handler);
        EditText edtRetryDelay = dialogView.findViewById(R.id.edt_mtry_retry_delay);
        CheckBox chkRetryCountdown = dialogView.findViewById(R.id.chk_mtry_retry_countdown);
        AutoCompleteTextView edtNext = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallback = dialogView.findViewById(R.id.edt_fallback_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        edtAttempts.setText("10");
        if (chkRunResponseHandler != null) {
            chkRunResponseHandler.setChecked(true);
        }
        if (edtRetryDelay != null) {
            edtRetryDelay.setText("0");
        }
        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
        }

        if (operationObject != null) {
            try {
                edtName.setText(operationObject.optString("name", ""));
                JSONObject inputMap = operationObject.optJSONObject("inputMap");
                if (inputMap != null) {
                    setOperationReferenceText(edtWrapped, inputMap.optString(MetaOperation.MTRY_WRAPPED_OPERATION_ID, ""));
                    edtAttempts.setText(inputMap.optString(MetaOperation.MTRY_ATTEMPTS, "10"));
                    if (chkRunResponseHandler != null) {
                        chkRunResponseHandler.setChecked(inputMap.optBoolean(MetaOperation.MTRY_RUN_RESPONSE_HANDLER, true));
                    }
                    if (edtRetryDelay != null) {
                        edtRetryDelay.setText(String.valueOf(inputMap.optLong(MetaOperation.MTRY_RETRY_DELAY_MS, 0L)));
                    }
                    if (chkRetryCountdown != null) {
                        chkRetryCountdown.setChecked(inputMap.optBoolean(MetaOperation.MTRY_RETRY_SHOW_COUNTDOWN, false));
                    }
                    setOperationReferenceText(edtNext, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                    setOperationReferenceText(edtFallback, inputMap.optString(MetaOperation.FALLBACKOPERATIONID, ""));
                }
            } catch (Exception e) {
                host.showToast("加载多次尝试节点数据失败: " + e.getMessage());
            }
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_wrapped).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择被包裹节点", operationId, edtWrapped);
            }
        });
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择成功后节点", operationId, edtNext);
            }
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败后节点", operationId, edtFallback);
            }
        });
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> saveMtryOperation(
                dialogView, operationId, edtName, edtWrapped, edtAttempts,
                chkRunResponseHandler, edtRetryDelay, chkRetryCountdown, edtNext, edtFallback));
    }

    private void saveMtryOperation(View dialogView,
                                   String operationId,
                                   EditText edtName,
                                   AutoCompleteTextView edtWrapped,
                                   EditText edtAttempts,
                                   CheckBox chkRunResponseHandler,
                                   EditText edtRetryDelay,
                                   CheckBox chkRetryCountdown,
                                   AutoCompleteTextView edtNext,
                                   AutoCompleteTextView edtFallback) {
        String name = safeText(edtName);
        String wrapped = safeText(edtWrapped);
        String attemptsText = safeText(edtAttempts);
        String next = safeText(edtNext);
        String fallback = safeText(edtFallback);

        if (TextUtils.isEmpty(name)) {
            edtName.setError("请填写操作名称");
            return;
        }
        if (TextUtils.isEmpty(wrapped)) {
            edtWrapped.setError("请选择被包裹节点");
            return;
        }
        if (!TextUtils.isEmpty(operationId) && TextUtils.equals(operationId, wrapped)) {
            edtWrapped.setError("不能包裹自身");
            return;
        }

        long attempts;
        try {
            attempts = Long.parseLong(attemptsText);
        } catch (Exception e) {
            edtAttempts.setError("请输入 1~1000 的整数");
            return;
        }
        if (attempts <= 0 || attempts > 1000) {
            edtAttempts.setError("请输入 1~1000 的整数");
            return;
        }

        try {
            JSONObject op = new JSONObject();
            boolean isEdit = !TextUtils.isEmpty(operationId);
            op.put("id", isEdit ? operationId : (idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis()));
            op.put("name", name);
            op.put("type", 25);
            op.put("responseType", 1);

            JSONObject inputMap = new JSONObject();
            inputMap.put(MetaOperation.MTRY_WRAPPED_OPERATION_ID, wrapped);
            inputMap.put(MetaOperation.MTRY_ATTEMPTS, attempts);
            inputMap.put(MetaOperation.MTRY_RUN_RESPONSE_HANDLER,
                    chkRunResponseHandler == null || chkRunResponseHandler.isChecked());
            long retryDelayMs = 0L;
            if (edtRetryDelay != null) {
                try { retryDelayMs = Math.max(0L, Long.parseLong(safeText(edtRetryDelay))); }
                catch (Exception ignored) {}
            }
            inputMap.put(MetaOperation.MTRY_RETRY_DELAY_MS, retryDelayMs);
            inputMap.put(MetaOperation.MTRY_RETRY_SHOW_COUNTDOWN,
                    chkRetryCountdown != null && chkRetryCountdown.isChecked());
            if (!TextUtils.isEmpty(next)) {
                inputMap.put(MetaOperation.NEXT_OPERATION_ID, next);
            }
            if (!TextUtils.isEmpty(fallback)) {
                inputMap.put(MetaOperation.FALLBACKOPERATIONID, fallback);
            }
            op.put("inputMap", inputMap);

            if (isEdit) {
                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, op.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) updateListener.onOperationUpdated();
                }
            } else {
                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(op);
                crudHelper.writeOperationsArray(operations, "已添加多次尝试节点", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) addListener.onOperationAdded();
                });
            }
        } catch (Exception e) {
            host.showToast("保存多次尝试节点失败: " + e.getMessage());
        }
    }

    // ==================== Switch Branch Helper Methods ====================

    private void addSwitchCaseRow(LinearLayout container, String caseValue, String nextId) {
        View row = LayoutInflater.from(host.getContext()).inflate(R.layout.item_switch_case_row, container, false);
        EditText edtValue = row.findViewById(R.id.edt_case_value);
        AutoCompleteTextView edtNext = row.findViewById(R.id.edt_case_next);
        TextView btnDelete = row.findViewById(R.id.btn_delete_case);
        TextView btnPick = row.findViewById(R.id.btn_pick_case_next);

        if (caseValue != null) edtValue.setText(caseValue);
        if (nextId != null) setOperationReferenceText(edtNext, nextId);

        btnDelete.setOnClickListener(v -> container.removeView(row));

        btnPick.setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择跳转节点", null, edtNext);
            }
        });

        container.addView(row);
    }

    // ==================== ColorSearch Operation ====================

    public void showAddColorSearchDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_color_search, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 500, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtBbox = dialogView.findViewById(R.id.edt_bbox);
        EditText edtColorValue = dialogView.findViewById(R.id.edt_color_value);
        View viewColorPreview = dialogView.findViewById(R.id.view_color_preview);
        EditText edtTolerance = dialogView.findViewById(R.id.edt_color_tolerance);
        EditText edtMinPixels = dialogView.findViewById(R.id.edt_min_pixels);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        EditText edtPreDelay = dialogView.findViewById(R.id.edt_match_pre_delay);
        AutoCompleteTextView edtFallback = dialogView.findViewById(R.id.edt_fallback_operation);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        edtTimeout.setText(defaultMatchTimeoutText());
        setupMatchDelayHint(edtPreDelay);
        edtTolerance.setText("18");
        edtMinPixels.setText("60");
        setupAdvancedToggle(dialogView);
        setupPollingIntervalInputs(dialogView, AdaptivePollingController.Profile.COLOR_CHECK, null);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        applyColorPreview(viewColorPreview, "");
        edtColorValue.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applyColorPreview(viewColorPreview, s == null ? "" : s.toString());
            }
        });

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_bbox).setOnClickListener(v -> {
            if (regionPickHelper != null) {
                regionPickHelper.beginRegionPickFromDialog(dialogView, edtBbox, null);
            }
        });

        dialogView.findViewById(R.id.btn_pick_color).setOnClickListener(v -> {
            if (colorPointPickerLauncher != null) {
                colorPointPickerLauncher.showColorPointPicker((x, y, color) -> {
                    String colorStr = formatColor(color);
                    edtColorValue.setText(colorStr);
                    applyColorPreview(viewColorPreview, colorStr);
                }, dialogView);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", null, edtFallback);
            }
        });
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = safeText(edtName);
            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            String bboxText = safeText(edtBbox);
            if (TextUtils.isEmpty(bboxText)) {
                edtBbox.setError("请填写检测区域");
                return;
            }
            String colorText = normalizeColorText(safeText(edtColorValue));
            if (TextUtils.isEmpty(colorText)) {
                edtColorValue.setError("请填写目标颜色");
                return;
            }
            try {
                android.graphics.Color.parseColor(colorText);
            } catch (Exception e) {
                edtColorValue.setError("颜色格式不正确，请使用 #RRGGBB");
                return;
            }
            Long timeout = parsePositiveLong(safeText(edtTimeout));
            if (timeout == null) {
                edtTimeout.setError("请输入有效超时(毫秒)");
                return;
            }

            java.util.List<Integer> bbox = regionPickHelper != null ? regionPickHelper.parseBboxInput(bboxText) : null;
            if (bbox == null || bbox.size() < 4) {
                edtBbox.setError("区域格式应为 x,y,w,h");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis());
                operationObject.put("name", name);
                operationObject.put("type", 19);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.BBOX, new JSONArray(bbox));
                inputMap.put(MetaOperation.COLOR_VALUE, colorText);
                String tolStr = safeText(edtTolerance);
                if (!TextUtils.isEmpty(tolStr)) {
                    try { inputMap.put(MetaOperation.COLOR_TOLERANCE, Integer.parseInt(tolStr)); } catch (Exception ignored) {}
                }
                String minPixStr = safeText(edtMinPixels);
                if (!TextUtils.isEmpty(minPixStr)) {
                    try { inputMap.put(MetaOperation.COLOR_SEARCH_MIN_PIXELS, Integer.parseInt(minPixStr)); } catch (Exception ignored) {}
                }
                inputMap.put(MetaOperation.MATCHTIMEOUT, timeout);
                putOptionalMatchPreDelay(inputMap, edtPreDelay);
                fillPollingIntervalInputMap(dialogView, inputMap);
                if (!TextUtils.isEmpty(safeText(edtFallback))) {
                    inputMap.put(MetaOperation.FALLBACKOPERATIONID, safeText(edtFallback));
                }
                if (!TextUtils.isEmpty(safeText(edtNextOperation))) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, safeText(edtNextOperation));
                }
                operationObject.put("inputMap", inputMap);

                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(operationObject);
                crudHelper.writeOperationsArray(operations, "已添加区域找色操作", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) {
                        addListener.onOperationAdded();
                    }
                });
            } catch (Exception e) {
                host.showToast("添加区域找色失败: " + e.getMessage());
            }
        });
    }

    public void showEditColorSearchDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_color_search, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 500, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtBbox = dialogView.findViewById(R.id.edt_bbox);
        EditText edtColorValue = dialogView.findViewById(R.id.edt_color_value);
        View viewColorPreview = dialogView.findViewById(R.id.view_color_preview);
        EditText edtTolerance = dialogView.findViewById(R.id.edt_color_tolerance);
        EditText edtMinPixels = dialogView.findViewById(R.id.edt_min_pixels);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        EditText edtPreDelay = dialogView.findViewById(R.id.edt_match_pre_delay);
        AutoCompleteTextView edtFallback = dialogView.findViewById(R.id.edt_fallback_operation);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        edtTimeout.setText(defaultMatchTimeoutText());
        setupMatchDelayHint(edtPreDelay);
        edtTolerance.setText("18");
        edtMinPixels.setText("60");
        setupAdvancedToggle(dialogView);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        edtColorValue.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applyColorPreview(viewColorPreview, s == null ? "" : s.toString());
            }
        });

        // Pre-fill existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                // bbox
                JSONArray bboxArr = inputMap.optJSONArray(MetaOperation.BBOX);
                if (bboxArr != null && bboxArr.length() >= 4) {
                    edtBbox.setText(bboxArr.optInt(0) + "," + bboxArr.optInt(1) + "," + bboxArr.optInt(2) + "," + bboxArr.optInt(3));
                }
                // color
                String colorVal = inputMap.optString(MetaOperation.COLOR_VALUE, "");
                if (!TextUtils.isEmpty(colorVal)) {
                    edtColorValue.setText(colorVal);
                    applyColorPreview(viewColorPreview, colorVal);
                }
                // tolerance
                Object tolObj = inputMap.opt(MetaOperation.COLOR_TOLERANCE);
                edtTolerance.setText(tolObj == null ? "18" : String.valueOf(tolObj).replace(".0", ""));
                // min pixels
                Object minPixObj = inputMap.opt(MetaOperation.COLOR_SEARCH_MIN_PIXELS);
                edtMinPixels.setText(minPixObj == null ? "60" : String.valueOf(minPixObj).replace(".0", ""));
                // timeout
                Object timeoutObj = inputMap.opt(MetaOperation.MATCHTIMEOUT);
                edtTimeout.setText(timeoutObj == null ? defaultMatchTimeoutText() : String.valueOf(timeoutObj).replace(".0", ""));
                // pre delay
                setNodePreDelayText(edtPreDelay, inputMap);
                setupPollingIntervalInputs(dialogView, AdaptivePollingController.Profile.COLOR_CHECK, inputMap);
                // next / fallback
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                setOperationReferenceText(edtFallback, inputMap.optString(MetaOperation.FALLBACKOPERATIONID, ""));
            }
        } catch (Exception e) {
            host.showToast("加载区域找色数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_bbox).setOnClickListener(v -> {
            if (regionPickHelper != null) {
                regionPickHelper.beginRegionPickFromDialog(dialogView, edtBbox, null);
            }
        });

        dialogView.findViewById(R.id.btn_pick_color).setOnClickListener(v -> {
            if (colorPointPickerLauncher != null) {
                colorPointPickerLauncher.showColorPointPicker((x, y, color) -> {
                    String colorStr = formatColor(color);
                    edtColorValue.setText(colorStr);
                    applyColorPreview(viewColorPreview, colorStr);
                }, dialogView);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", null, edtFallback);
            }
        });
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = safeText(edtName);
            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            String bboxText = safeText(edtBbox);
            if (TextUtils.isEmpty(bboxText)) {
                edtBbox.setError("请填写检测区域");
                return;
            }
            String colorText = normalizeColorText(safeText(edtColorValue));
            if (TextUtils.isEmpty(colorText)) {
                edtColorValue.setError("请填写目标颜色");
                return;
            }
            try {
                android.graphics.Color.parseColor(colorText);
            } catch (Exception e) {
                edtColorValue.setError("颜色格式不正确，请使用 #RRGGBB");
                return;
            }
            Long timeout = parsePositiveLong(safeText(edtTimeout));
            if (timeout == null) {
                edtTimeout.setError("请输入有效超时(毫秒)");
                return;
            }

            java.util.List<Integer> bbox = regionPickHelper != null ? regionPickHelper.parseBboxInput(bboxText) : null;
            if (bbox == null || bbox.size() < 4) {
                edtBbox.setError("区域格式应为 x,y,w,h");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 19);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.BBOX, new JSONArray(bbox));
                inputMap.put(MetaOperation.COLOR_VALUE, colorText);
                String tolStr = safeText(edtTolerance);
                if (!TextUtils.isEmpty(tolStr)) {
                    try { inputMap.put(MetaOperation.COLOR_TOLERANCE, Integer.parseInt(tolStr)); } catch (Exception ignored) {}
                }
                String minPixStr = safeText(edtMinPixels);
                if (!TextUtils.isEmpty(minPixStr)) {
                    try { inputMap.put(MetaOperation.COLOR_SEARCH_MIN_PIXELS, Integer.parseInt(minPixStr)); } catch (Exception ignored) {}
                }
                inputMap.put(MetaOperation.MATCHTIMEOUT, timeout);
                putOptionalMatchPreDelay(inputMap, edtPreDelay);
                fillPollingIntervalInputMap(dialogView, inputMap);
                if (!TextUtils.isEmpty(safeText(edtFallback))) {
                    inputMap.put(MetaOperation.FALLBACKOPERATIONID, safeText(edtFallback));
                }
                if (!TextUtils.isEmpty(safeText(edtNextOperation))) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, safeText(edtNextOperation));
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) {
                        updateListener.onOperationUpdated();
                    }
                }
            } catch (Exception e) {
                host.showToast("保存区域找色失败: " + e.getMessage());
            }
        });
    }

    // ==================== OCR Text Operation ====================

    public void showAddOcrTextDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_ocr_text, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 560, null);

        bindOcrTextDialog(dialogView, null, null);
    }

    public void showEditOcrTextDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_ocr_text, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 560, null);

        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) {
            btnConfirm.setText("保存");
        }
        bindOcrTextDialog(dialogView, operationId, operationObject);
    }

    private void bindOcrTextDialog(View dialogView, String operationId, JSONObject operationObject) {
        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtBbox = dialogView.findViewById(R.id.edt_bbox);
        AutoCompleteTextView edtModel = dialogView.findViewById(R.id.edt_ocr_model);
        EditText edtLanguage = null;
        EditText edtTrainedData = null;
        EditText edtTextVar = dialogView.findViewById(R.id.edt_ocr_text_var);
        EditText edtMinConfidence = dialogView.findViewById(R.id.edt_ocr_min_confidence);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        TextView btnTestOcr = dialogView.findViewById(R.id.btn_test_ocr);
        TextView tvTestResult = dialogView.findViewById(R.id.tv_ocr_test_result);
        ImageView ivProcessedPreview = dialogView.findViewById(R.id.iv_ocr_processed_preview);
        EditText edtPreDelay = dialogView.findViewById(R.id.edt_match_pre_delay);
        EditText edtScaleFactor = dialogView.findViewById(R.id.edt_ocr_scale_factor);
        AutoCompleteTextView edtPsm = dialogView.findViewById(R.id.edt_ocr_psm);
        EditText edtConfidenceVar = dialogView.findViewById(R.id.edt_ocr_confidence_var);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        CheckBox chkGrayscale = dialogView.findViewById(R.id.chk_ocr_grayscale);
        CheckBox chkCompress = dialogView.findViewById(R.id.chk_ocr_compress);
        CheckBox chkGaussian = dialogView.findViewById(R.id.chk_ocr_gaussian);
        SeekBar skThreshold = dialogView.findViewById(R.id.sk_ocr_threshold);
        SeekBar skCompress = dialogView.findViewById(R.id.sk_ocr_compress);
        SeekBar skGaussian = dialogView.findViewById(R.id.sk_ocr_gaussian);
        TextView tvThresholdValue = dialogView.findViewById(R.id.tv_ocr_threshold_value);
        TextView tvCompressValue = dialogView.findViewById(R.id.tv_ocr_compress_value);
        TextView tvGaussianValue = dialogView.findViewById(R.id.tv_ocr_gaussian_value);
        CheckBox chkUseMask = dialogView.findViewById(R.id.chk_ocr_use_mask);
        TextView btnEditMask = dialogView.findViewById(R.id.btn_edit_ocr_mask);
        TextView tvMaskStatus = dialogView.findViewById(R.id.tv_ocr_mask_status);
        EditText edtPipeline = dialogView.findViewById(R.id.edt_ocr_pipeline);
        LinearLayout lyPipelineSteps = dialogView.findViewById(R.id.ly_ocr_pipeline_steps);
        TextView btnAddPipelineStep = dialogView.findViewById(R.id.btn_add_ocr_pipeline_step);
        CheckBox chkInvert = dialogView.findViewById(R.id.chk_ocr_invert);
        SeekBar skContrast = dialogView.findViewById(R.id.sk_ocr_contrast);
        TextView tvContrastValue = dialogView.findViewById(R.id.tv_ocr_contrast_value);
        CheckBox chkMedian = dialogView.findViewById(R.id.chk_ocr_median);
        CheckBox chkSharpen = dialogView.findViewById(R.id.chk_ocr_sharpen);
        AutoCompleteTextView edtMorphMode = dialogView.findViewById(R.id.edt_ocr_morph_mode);
        SeekBar skMorph = dialogView.findViewById(R.id.sk_ocr_morph);
        TextView tvMorphValue = dialogView.findViewById(R.id.tv_ocr_morph_value);

        edtName.setText("OCR 识别");
        edtModel.setText("chi_sim.traineddata + eng.traineddata", false);
        edtMinConfidence.setText("0");
        edtTimeout.setText(defaultMatchTimeoutText());
        setupMatchDelayHint(edtPreDelay);
        edtScaleFactor.setText("1.0");
        chkGrayscale.setChecked(false);
        chkCompress.setChecked(false);
        chkGaussian.setChecked(false);
        chkUseMask.setChecked(false);
        chkInvert.setChecked(false);
        chkMedian.setChecked(false);
        chkSharpen.setChecked(false);
        skThreshold.setProgress(0);
        skCompress.setProgress(100);
        skGaussian.setProgress(0);
        skContrast.setProgress(100);
        edtMorphMode.setText("none", false);
        skMorph.setProgress(0);
        edtPsm.setText("auto", false);
        setupAdvancedToggle(dialogView);
        dialogHelpers.bindDropdownSelect(edtModel, java.util.Arrays.asList(
                "chi_sim.traineddata + eng.traineddata",
                "chi_sim.traineddata",
                "eng.traineddata",
                "digits_comma.traineddata",
                "digitsall_layer.traineddata",
                "digits_layer.traineddata",
                "digits.traineddata"
        ));
        edtModel.setOnItemClickListener((parent, view, position, id) ->
                applyOcrModelPreset(safeText(edtModel), edtLanguage, edtTrainedData));
        dialogHelpers.bindDropdownSelect(edtPsm, java.util.Arrays.asList(
                "auto", "block", "line", "word", "sparse"
        ));
        dialogHelpers.bindDropdownSelect(edtMorphMode, java.util.Arrays.asList(
                "none", "dilate", "erode", "open", "close"
        ));

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        final String[] ocrMaskBase64 = {""};
        if (operationObject != null) {
            try {
                edtName.setText(operationObject.optString("name", ""));
                JSONObject inputMap = operationObject.optJSONObject("inputMap");
                if (inputMap != null) {
                    JSONArray bboxArr = inputMap.optJSONArray(MetaOperation.BBOX);
                    if (bboxArr != null && bboxArr.length() >= 4) {
                        edtBbox.setText(bboxArr.optInt(0) + "," + bboxArr.optInt(1) + ","
                                + bboxArr.optInt(2) + "," + bboxArr.optInt(3));
                    }
                    edtModel.setText(ocrTrainingFileLabel(inputMap), false);
                    edtTextVar.setText(inputMap.optString(MetaOperation.OCR_TEXT_VAR, ""));
                    setOptionalLongText(edtMinConfidence, inputMap.opt(MetaOperation.OCR_MIN_CONFIDENCE));
                    Object timeoutObj = inputMap.opt(MetaOperation.MATCHTIMEOUT);
                    edtTimeout.setText(timeoutObj == null ? defaultMatchTimeoutText() : String.valueOf(timeoutObj).replace(".0", ""));
                    setNodePreDelayText(edtPreDelay, inputMap);
                    Object scaleObj = inputMap.opt(MetaOperation.OCR_SCALE_FACTOR);
                    edtScaleFactor.setText(scaleObj == null ? "1.0" : String.valueOf(scaleObj).replace(".0", ""));
                    int threshold = inputMap.optInt(MetaOperation.OCR_THRESHOLD, 0);
                    skThreshold.setProgress(Math.max(0, Math.min(255, threshold)));
                    chkGrayscale.setChecked(inputMap.optBoolean(MetaOperation.OCR_GRAYSCALE, false));
                    int compress = inputMap.optInt(MetaOperation.OCR_COMPRESS_PERCENT, 100);
                    chkCompress.setChecked(compress < 100);
                    skCompress.setProgress(Math.max(10, Math.min(100, compress)));
                    boolean gaussian = inputMap.optBoolean(MetaOperation.OCR_GAUSSIAN_BLUR, false);
                    int gaussianRadius = inputMap.optInt(MetaOperation.OCR_GAUSSIAN_RADIUS, 0);
                    chkGaussian.setChecked(gaussian);
                    skGaussian.setProgress(Math.max(0, Math.min(10, gaussianRadius)));
                    ocrMaskBase64[0] = inputMap.optString(MetaOperation.OCR_MASK_PNG, "");
                    chkUseMask.setChecked(inputMap.optBoolean(MetaOperation.OCR_USE_MASK, !TextUtils.isEmpty(ocrMaskBase64[0])));
                    JSONArray pipeline = inputMap.optJSONArray(MetaOperation.OCR_PREPROCESS_PIPELINE);
                    if (pipeline == null) {
                        pipeline = buildLegacyOcrPipeline(inputMap, !TextUtils.isEmpty(ocrMaskBase64[0]));
                    }
                    edtPipeline.setText(pipeline == null || pipeline.length() == 0 ? "" : pipeline.toString(2));
                    chkInvert.setChecked(inputMap.optBoolean(MetaOperation.OCR_INVERT, false));
                    skContrast.setProgress(Math.max(50, Math.min(300, inputMap.optInt(MetaOperation.OCR_CONTRAST_PERCENT, 100))));
                    chkMedian.setChecked(inputMap.optBoolean(MetaOperation.OCR_MEDIAN_DENOISE, false));
                    chkSharpen.setChecked(inputMap.optBoolean(MetaOperation.OCR_SHARPEN, false));
                    edtMorphMode.setText(inputMap.optString(MetaOperation.OCR_MORPH_MODE, "none"), false);
                    skMorph.setProgress(Math.max(0, Math.min(3, inputMap.optInt(MetaOperation.OCR_MORPH_ITERATIONS, 0))));
                    edtPsm.setText(inputMap.optString(MetaOperation.OCR_PAGE_SEG_MODE, "auto"), false);
                    edtConfidenceVar.setText(inputMap.optString(MetaOperation.OCR_CONFIDENCE_VAR, ""));
                    setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                }
            } catch (Exception e) {
                host.showToast("加载 OCR 节点数据失败: " + e.getMessage());
            }
        }
        updateOcrMaskStatus(tvMaskStatus, ocrMaskBase64[0]);

        final int[] previewSeq = {0};
        final Runnable[] pendingPreviewRefresh = {null};
        Runnable schedulePreviewRefresh = () -> {
            if (pendingPreviewRefresh[0] != null) {
                mainHandler.removeCallbacks(pendingPreviewRefresh[0]);
            }
            pendingPreviewRefresh[0] = () -> {
                int seq = ++previewSeq[0];
                updateOcrProcessedPreview(
                        dialogView, edtBbox, edtModel, edtLanguage, edtTrainedData,
                        edtScaleFactor, chkGrayscale, chkCompress, chkGaussian,
                        skThreshold, skCompress, skGaussian, chkUseMask, ocrMaskBase64,
                        edtPipeline, chkInvert, skContrast, chkMedian, chkSharpen, edtMorphMode, skMorph,
                        ivProcessedPreview, seq, previewSeq);
            };
            mainHandler.postDelayed(pendingPreviewRefresh[0], 180L);
        };
        Runnable renderPipeline = () -> renderOcrPipelineSteps(
                lyPipelineSteps, edtPipeline, ocrMaskBase64, tvMaskStatus, chkUseMask,
                dialogView, edtBbox, schedulePreviewRefresh);

        bindOcrSeekBarLabels(skThreshold, tvThresholdValue, "二值化阈值: ", "", schedulePreviewRefresh);
        bindOcrSeekBarLabels(skCompress, tvCompressValue, "原图的", "%", schedulePreviewRefresh);
        bindOcrSeekBarLabels(skGaussian, tvGaussianValue, "模糊半径: ", "", schedulePreviewRefresh);
        bindOcrContrastSeekBar(skContrast, tvContrastValue, schedulePreviewRefresh);
        bindOcrSeekBarLabels(skMorph, tvMorphValue, "形态次数: ", "", schedulePreviewRefresh);
        addOcrPreviewTextWatcher(edtBbox, schedulePreviewRefresh);
        addOcrPreviewTextWatcher(edtLanguage, schedulePreviewRefresh);
        addOcrPreviewTextWatcher(edtTrainedData, schedulePreviewRefresh);
        addOcrPreviewTextWatcher(edtScaleFactor, schedulePreviewRefresh);
        addOcrPreviewTextWatcher(edtPipeline, schedulePreviewRefresh);
        addOcrPreviewTextWatcher(edtMorphMode, schedulePreviewRefresh);
        edtModel.setOnItemClickListener((parent, view, position, id) -> {
            applyOcrModelPreset(safeText(edtModel), edtLanguage, edtTrainedData);
            schedulePreviewRefresh.run();
        });
        chkGrayscale.setOnCheckedChangeListener((buttonView, isChecked) -> schedulePreviewRefresh.run());
        chkCompress.setOnCheckedChangeListener((buttonView, isChecked) -> schedulePreviewRefresh.run());
        chkGaussian.setOnCheckedChangeListener((buttonView, isChecked) -> schedulePreviewRefresh.run());
        chkUseMask.setOnCheckedChangeListener((buttonView, isChecked) -> schedulePreviewRefresh.run());
        chkInvert.setOnCheckedChangeListener((buttonView, isChecked) -> schedulePreviewRefresh.run());
        chkMedian.setOnCheckedChangeListener((buttonView, isChecked) -> schedulePreviewRefresh.run());
        chkSharpen.setOnCheckedChangeListener((buttonView, isChecked) -> schedulePreviewRefresh.run());
        btnAddPipelineStep.setOnClickListener(v -> showOcrPipelinePool(
                lyPipelineSteps, edtPipeline, ocrMaskBase64, tvMaskStatus, chkUseMask,
                dialogView, edtBbox, schedulePreviewRefresh));
        renderPipeline.run();
        schedulePreviewRefresh.run();

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_bbox).setOnClickListener(v -> {
            if (regionPickHelper != null) {
                regionPickHelper.beginRegionPickFromDialog(dialogView, edtBbox, null);
            }
        });
        btnEditMask.setOnClickListener(v -> showOcrMaskEditor(
                dialogView, edtBbox, ocrMaskBase64, tvMaskStatus, chkUseMask, schedulePreviewRefresh));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
            }
        });
        btnTestOcr.setOnClickListener(v -> runOcrDialogTest(
                dialogView, edtBbox, edtModel, edtLanguage, edtTrainedData,
                edtScaleFactor, chkGrayscale, chkCompress, chkGaussian,
                skThreshold, skCompress, skGaussian, chkUseMask, ocrMaskBase64,
                edtPipeline, chkInvert, skContrast, chkMedian, chkSharpen, edtMorphMode, skMorph,
                edtPsm, tvTestResult, ivProcessedPreview));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));

        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setOnClickListener(v -> saveOcrTextOperation(
                dialogView, operationId, edtName, edtBbox, edtModel, edtLanguage, edtTrainedData,
                edtTextVar, edtMinConfidence, edtTimeout, edtPreDelay,
                edtScaleFactor, chkGrayscale, chkCompress, chkGaussian,
                skThreshold, skCompress, skGaussian, chkUseMask, ocrMaskBase64,
                edtPipeline, chkInvert, skContrast, chkMedian, chkSharpen, edtMorphMode, skMorph,
                edtPsm, edtConfidenceVar, edtNextOperation));
    }

    private void saveOcrTextOperation(
            View dialogView,
            String operationId,
            EditText edtName,
            EditText edtBbox,
            AutoCompleteTextView edtModel,
            EditText edtLanguage,
            EditText edtTrainedData,
            EditText edtTextVar,
            EditText edtMinConfidence,
            EditText edtTimeout,
            EditText edtPreDelay,
            EditText edtScaleFactor,
            CheckBox chkGrayscale,
            CheckBox chkCompress,
            CheckBox chkGaussian,
            SeekBar skThreshold,
            SeekBar skCompress,
            SeekBar skGaussian,
            CheckBox chkUseMask,
            String[] ocrMaskBase64,
            EditText edtPipeline,
            CheckBox chkInvert,
            SeekBar skContrast,
            CheckBox chkMedian,
            CheckBox chkSharpen,
            AutoCompleteTextView edtMorphMode,
            SeekBar skMorph,
            AutoCompleteTextView edtPsm,
            EditText edtConfidenceVar,
            AutoCompleteTextView edtNextOperation) {

        String name = safeText(edtName);
        if (TextUtils.isEmpty(name)) {
            edtName.setError("请填写操作名称");
            return;
        }
        String bboxText = safeText(edtBbox);
        if (TextUtils.isEmpty(bboxText)) {
            edtBbox.setError("请填写识别区域");
            return;
        }
        java.util.List<Integer> bbox = regionPickHelper != null ? regionPickHelper.parseBboxInput(bboxText) : parseOcrBboxText(bboxText);
        if (bbox == null || bbox.size() < 4) {
            edtBbox.setError("区域格式应为 x,y,w,h");
            return;
        }

        Long timeout = parsePositiveLong(safeText(edtTimeout));
        if (timeout == null) {
            edtTimeout.setError("请输入有效超时(毫秒)");
            return;
        }
        Integer minConfidence = parseBoundedInt(safeText(edtMinConfidence), 0, 0, 100);
        if (minConfidence == null) {
            edtMinConfidence.setError("请输入 0~100 的整数");
            return;
        }
        Float scaleFactor = parseBoundedFloat(safeText(edtScaleFactor), 2.0f, 1.0f, 4.0f);
        if (scaleFactor == null) {
            edtScaleFactor.setError("请输入 1.0~4.0 的数值");
            return;
        }
        int threshold = Math.max(0, Math.min(255, skThreshold.getProgress()));
        String pageSegMode = normalizeOcrPageSegMode(safeText(edtPsm));
        if (pageSegMode == null) {
            edtPsm.setError("仅支持 auto、block、line、word、sparse 或数字模式");
            return;
        }

        try {
            JSONObject inputMap = new JSONObject();
            inputMap.put(MetaOperation.BBOX, new JSONArray(bbox));
            String modelCode = ocrModelCode(safeText(edtModel));
            inputMap.put(MetaOperation.OCR_MODEL, modelCode);
            inputMap.put(MetaOperation.OCR_LANGUAGE, ocrLanguageFromTrainingFiles(safeText(edtModel)));
            String trainedData = ocrSingleTrainingFileName(safeText(edtModel));
            if (!TextUtils.isEmpty(trainedData)) {
                inputMap.put(MetaOperation.OCR_TRAINED_DATA, trainedData);
            }
            if (!TextUtils.isEmpty(safeText(edtTextVar))) {
                inputMap.put(MetaOperation.OCR_TEXT_VAR, safeText(edtTextVar));
            }
            inputMap.put(MetaOperation.OCR_MIN_CONFIDENCE, minConfidence);
            inputMap.put(MetaOperation.MATCHTIMEOUT, timeout);
            putOptionalMatchPreDelay(inputMap, edtPreDelay);
            inputMap.put(MetaOperation.OCR_SCALE_FACTOR, scaleFactor);
            inputMap.put(MetaOperation.OCR_GRAYSCALE, chkGrayscale.isChecked());
            inputMap.put(MetaOperation.OCR_THRESHOLD, threshold);
            inputMap.put(MetaOperation.OCR_COMPRESS_PERCENT, chkCompress.isChecked() ? Math.max(10, skCompress.getProgress()) : 100);
            inputMap.put(MetaOperation.OCR_GAUSSIAN_BLUR, chkGaussian.isChecked());
            inputMap.put(MetaOperation.OCR_GAUSSIAN_RADIUS, chkGaussian.isChecked() ? skGaussian.getProgress() : 0);
            boolean useMask = chkUseMask != null && chkUseMask.isChecked()
                    && ocrMaskBase64 != null && !TextUtils.isEmpty(ocrMaskBase64[0]);
            inputMap.put(MetaOperation.OCR_USE_MASK, useMask);
            if (useMask) {
                inputMap.put(MetaOperation.OCR_MASK_PNG, ocrMaskBase64[0]);
                int[] maskSize = decodeOcrMaskSize(ocrMaskBase64[0]);
                if (maskSize != null) {
                    inputMap.put(MetaOperation.OCR_MASK_WIDTH, maskSize[0]);
                    inputMap.put(MetaOperation.OCR_MASK_HEIGHT, maskSize[1]);
                }
            }
            JSONArray preprocessPipeline = parseOcrPipelineInput(edtPipeline, true);
            if (preprocessPipeline == null) {
                return;
            }
            if (preprocessPipeline.length() > 0) {
                inputMap.put(MetaOperation.OCR_PREPROCESS_PIPELINE, preprocessPipeline);
            }
            inputMap.put(MetaOperation.OCR_INVERT, chkInvert != null && chkInvert.isChecked());
            inputMap.put(MetaOperation.OCR_CONTRAST_PERCENT, skContrast == null ? 100 : Math.max(50, Math.min(300, skContrast.getProgress())));
            inputMap.put(MetaOperation.OCR_MEDIAN_DENOISE, chkMedian != null && chkMedian.isChecked());
            inputMap.put(MetaOperation.OCR_SHARPEN, chkSharpen != null && chkSharpen.isChecked());
            inputMap.put(MetaOperation.OCR_MORPH_MODE, normalizeOcrMorphMode(safeText(edtMorphMode)));
            inputMap.put(MetaOperation.OCR_MORPH_ITERATIONS, skMorph == null ? 0 : Math.max(0, Math.min(3, skMorph.getProgress())));
            inputMap.put(MetaOperation.OCR_DIGITS_ONLY, "number".equals(modelCode));
            inputMap.put(MetaOperation.OCR_PAGE_SEG_MODE, pageSegMode);
            if (!TextUtils.isEmpty(safeText(edtConfidenceVar))) {
                inputMap.put(MetaOperation.OCR_CONFIDENCE_VAR, safeText(edtConfidenceVar));
            }
            if (!TextUtils.isEmpty(safeText(edtNextOperation))) {
                inputMap.put(MetaOperation.NEXT_OPERATION_ID, safeText(edtNextOperation));
            }

            if (operationId != null) {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 26);
                updatedOperation.put("responseType", 1);
                updatedOperation.put("inputMap", inputMap);
                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) {
                        updateListener.onOperationUpdated();
                    }
                }
            } else {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis());
                operationObject.put("name", name);
                operationObject.put("type", 26);
                operationObject.put("responseType", 1);
                operationObject.put("inputMap", inputMap);

                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(operationObject);
                crudHelper.writeOperationsArray(operations, "已添加 OCR 识别节点", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) {
                        addListener.onOperationAdded();
                    }
                });
            }
        } catch (Exception e) {
            host.showToast("保存 OCR 节点失败: " + e.getMessage());
        }
    }

    // ==================== AI Object Detection Operation ====================

    public void showAddAiDetectDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_ai_detect, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 560, null);

        bindAiDetectDialog(dialogView, null, null);
    }

    public void showEditAiDetectDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_ai_detect, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 560, null);

        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) {
            btnConfirm.setText("保存");
        }
        bindAiDetectDialog(dialogView, operationId, operationObject);
    }

    private void bindAiDetectDialog(View dialogView, String operationId, JSONObject operationObject) {
        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtBbox = dialogView.findViewById(R.id.edt_bbox);
        EditText edtModelPath = dialogView.findViewById(R.id.edt_ai_model_path);
        EditText edtLabelsPath = dialogView.findViewById(R.id.edt_ai_labels_path);
        EditText edtTargetLabel = dialogView.findViewById(R.id.edt_ai_target_label);
        EditText edtResultVar = dialogView.findViewById(R.id.edt_ai_result_var);
        EditText edtBboxVar = dialogView.findViewById(R.id.edt_ai_bbox_var);
        EditText edtInputSize = dialogView.findViewById(R.id.edt_ai_input_size);
        EditText edtMinConfidence = dialogView.findViewById(R.id.edt_ai_min_confidence);
        EditText edtIouThreshold = dialogView.findViewById(R.id.edt_ai_iou_threshold);
        EditText edtMaxResults = dialogView.findViewById(R.id.edt_ai_max_results);
        EditText edtPreDelay = dialogView.findViewById(R.id.edt_match_pre_delay);
        EditText edtConfidenceVar = dialogView.findViewById(R.id.edt_ai_confidence_var);
        EditText edtLabelVar = dialogView.findViewById(R.id.edt_ai_label_var);
        EditText edtCenterVar = dialogView.findViewById(R.id.edt_ai_center_var);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        edtName.setText("AI 目标检测");
        edtInputSize.setText("640");
        edtMinConfidence.setText("0.25");
        edtIouThreshold.setText("0.45");
        edtMaxResults.setText("10");
        setupMatchDelayHint(edtPreDelay);
        setupAdvancedToggle(dialogView);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
        }

        if (operationObject != null) {
            try {
                edtName.setText(operationObject.optString("name", ""));
                JSONObject inputMap = operationObject.optJSONObject("inputMap");
                if (inputMap != null) {
                    JSONArray bboxArr = inputMap.optJSONArray(MetaOperation.BBOX);
                    if (bboxArr != null && bboxArr.length() >= 4) {
                        edtBbox.setText(bboxArr.optInt(0) + "," + bboxArr.optInt(1) + ","
                                + bboxArr.optInt(2) + "," + bboxArr.optInt(3));
                    }
                    edtModelPath.setText(inputMap.optString(MetaOperation.AI_MODEL_PATH, ""));
                    edtLabelsPath.setText(inputMap.optString(MetaOperation.AI_LABELS_PATH, ""));
                    edtTargetLabel.setText(inputMap.optString(MetaOperation.AI_TARGET_LABEL, ""));
                    edtResultVar.setText(inputMap.optString(MetaOperation.AI_RESULT_VAR, ""));
                    edtBboxVar.setText(inputMap.optString(MetaOperation.AI_BBOX_VAR, ""));
                    setOptionalLongText(edtInputSize, inputMap.opt(MetaOperation.AI_INPUT_SIZE));
                    setOptionalLongText(edtMaxResults, inputMap.opt(MetaOperation.AI_MAX_RESULTS));
                    setOptionalFloatText(edtMinConfidence, inputMap.opt(MetaOperation.AI_MIN_CONFIDENCE));
                    setOptionalFloatText(edtIouThreshold, inputMap.opt(MetaOperation.AI_IOU_THRESHOLD));
                    setNodePreDelayText(edtPreDelay, inputMap);
                    edtConfidenceVar.setText(inputMap.optString(MetaOperation.AI_CONFIDENCE_VAR, ""));
                    edtLabelVar.setText(inputMap.optString(MetaOperation.AI_LABEL_VAR, ""));
                    edtCenterVar.setText(inputMap.optString(MetaOperation.AI_CENTER_VAR, ""));
                    setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                }
            } catch (Exception e) {
                host.showToast("加载 AI 检测节点数据失败: " + e.getMessage());
            }
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_bbox).setOnClickListener(v -> {
            if (regionPickHelper != null) {
                regionPickHelper.beginRegionPickFromDialog(dialogView, edtBbox, null);
            }
        });
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
            }
        });
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> saveAiDetectOperation(
                dialogView, operationId, edtName, edtBbox, edtModelPath, edtLabelsPath,
                edtTargetLabel, edtResultVar, edtBboxVar, edtInputSize, edtMinConfidence,
                edtIouThreshold, edtMaxResults, edtPreDelay, edtConfidenceVar, edtLabelVar,
                edtCenterVar, edtNextOperation));
    }

    private void saveAiDetectOperation(View dialogView,
                                       String operationId,
                                       EditText edtName,
                                       EditText edtBbox,
                                       EditText edtModelPath,
                                       EditText edtLabelsPath,
                                       EditText edtTargetLabel,
                                       EditText edtResultVar,
                                       EditText edtBboxVar,
                                       EditText edtInputSize,
                                       EditText edtMinConfidence,
                                       EditText edtIouThreshold,
                                       EditText edtMaxResults,
                                       EditText edtPreDelay,
                                       EditText edtConfidenceVar,
                                       EditText edtLabelVar,
                                       EditText edtCenterVar,
                                       AutoCompleteTextView edtNextOperation) {
        String name = safeText(edtName);
        if (TextUtils.isEmpty(name)) {
            edtName.setError("请填写操作名称");
            return;
        }
        String bboxText = safeText(edtBbox);
        if (TextUtils.isEmpty(bboxText)) {
            edtBbox.setError("请填写识别区域");
            return;
        }
        java.util.List<Integer> bbox = regionPickHelper != null
                ? regionPickHelper.parseBboxInput(bboxText)
                : parseOcrBboxText(bboxText);
        if (bbox == null || bbox.size() < 4) {
            edtBbox.setError("区域格式应为 x,y,w,h");
            return;
        }
        if (TextUtils.isEmpty(safeText(edtModelPath))) {
            edtModelPath.setError("请填写 .tflite 模型路径");
            return;
        }
        Integer inputSize = parseBoundedInt(safeText(edtInputSize), 640, 32, 2048);
        if (inputSize == null) {
            edtInputSize.setError("请输入 32~2048 的整数");
            return;
        }
        Float minConfidence = parseBoundedFloat(safeText(edtMinConfidence), 0.25f, 0.01f, 0.99f);
        if (minConfidence == null) {
            edtMinConfidence.setError("请输入 0.01~0.99 的数值");
            return;
        }
        Float iouThreshold = parseBoundedFloat(safeText(edtIouThreshold), 0.45f, 0.01f, 0.99f);
        if (iouThreshold == null) {
            edtIouThreshold.setError("请输入 0.01~0.99 的数值");
            return;
        }
        Integer maxResults = parseBoundedInt(safeText(edtMaxResults), 10, 1, 50);
        if (maxResults == null) {
            edtMaxResults.setError("请输入 1~50 的整数");
            return;
        }

        try {
            JSONObject inputMap = new JSONObject();
            inputMap.put(MetaOperation.BBOX, new JSONArray(bbox));
            inputMap.put(MetaOperation.AI_MODEL_PATH, safeText(edtModelPath));
            putOptionalString(inputMap, MetaOperation.AI_LABELS_PATH, edtLabelsPath);
            putOptionalString(inputMap, MetaOperation.AI_TARGET_LABEL, edtTargetLabel);
            putOptionalString(inputMap, MetaOperation.AI_RESULT_VAR, edtResultVar);
            putOptionalString(inputMap, MetaOperation.AI_BBOX_VAR, edtBboxVar);
            inputMap.put(MetaOperation.AI_INPUT_SIZE, inputSize);
            inputMap.put(MetaOperation.AI_MIN_CONFIDENCE, minConfidence);
            inputMap.put(MetaOperation.AI_IOU_THRESHOLD, iouThreshold);
            inputMap.put(MetaOperation.AI_MAX_RESULTS, maxResults);
            putOptionalMatchPreDelay(inputMap, edtPreDelay);
            putOptionalString(inputMap, MetaOperation.AI_CONFIDENCE_VAR, edtConfidenceVar);
            putOptionalString(inputMap, MetaOperation.AI_LABEL_VAR, edtLabelVar);
            putOptionalString(inputMap, MetaOperation.AI_CENTER_VAR, edtCenterVar);
            if (!TextUtils.isEmpty(safeText(edtNextOperation))) {
                inputMap.put(MetaOperation.NEXT_OPERATION_ID, safeText(edtNextOperation));
            }

            if (operationId != null) {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 27);
                updatedOperation.put("responseType", 1);
                updatedOperation.put("inputMap", inputMap);
                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) {
                        updateListener.onOperationUpdated();
                    }
                }
            } else {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", idGenerator != null ? idGenerator.generateId() : "op_" + System.currentTimeMillis());
                operationObject.put("name", name);
                operationObject.put("type", 27);
                operationObject.put("responseType", 1);
                operationObject.put("inputMap", inputMap);

                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(operationObject);
                crudHelper.writeOperationsArray(operations, "已添加 AI 目标检测节点", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) {
                        addListener.onOperationAdded();
                    }
                });
            }
        } catch (Exception e) {
            host.showToast("保存 AI 检测节点失败: " + e.getMessage());
        }
    }

    private void putOptionalString(JSONObject inputMap, String key, EditText editText) throws Exception {
        String value = safeText(editText);
        if (!TextUtils.isEmpty(value)) {
            inputMap.put(key, value);
        }
    }

    private void setOptionalFloatText(EditText editText, Object value) {
        if (editText == null || value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        editText.setText(text);
    }

    private java.util.List<Integer> parseOcrBboxText(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        String[] parts = raw.split("[,，\\s]+");
        if (parts.length < 4) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int w = Integer.parseInt(parts[2].trim());
            int h = Integer.parseInt(parts[3].trim());
            if (w <= 0 || h <= 0) {
                return null;
            }
            return java.util.Arrays.asList(x, y, w, h);
        } catch (Exception e) {
            return null;
        }
    }

    private JSONArray parseOcrPipelineInput(EditText edtPipeline, boolean showError) {
        if (edtPipeline == null) {
            return new JSONArray();
        }
        String raw = safeText(edtPipeline);
        if (TextUtils.isEmpty(raw)) {
            edtPipeline.setError(null);
            return new JSONArray();
        }
        try {
            JSONArray array = new JSONArray(raw);
            edtPipeline.setError(null);
            return array;
        } catch (Exception e) {
            if (showError) {
                edtPipeline.setError("流水线必须是 JSON 数组");
                host.showToast("OCR 预处理流水线 JSON 格式错误");
            }
            return null;
        }
    }

    private JSONArray buildLegacyOcrPipeline(JSONObject inputMap, boolean hasMask) {
        JSONArray pipeline = new JSONArray();
        if (inputMap == null) {
            return pipeline;
        }
        try {
            if (hasMask && inputMap.optBoolean(MetaOperation.OCR_USE_MASK, true)) {
                pipeline.put(new JSONObject().put("type", "mask"));
            }
            double scale = inputMap.optDouble(MetaOperation.OCR_SCALE_FACTOR, 1.0d);
            int compress = inputMap.optInt(MetaOperation.OCR_COMPRESS_PERCENT, 100);
            if (Math.abs(scale - 1.0d) > 0.01d || compress != 100) {
                pipeline.put(new JSONObject()
                        .put("type", "resize")
                        .put("factor", scale)
                        .put("percent", compress));
            }
            if (inputMap.optBoolean(MetaOperation.OCR_GRAYSCALE, false)) {
                pipeline.put(new JSONObject().put("type", "grayscale"));
            }
            int contrast = inputMap.optInt(MetaOperation.OCR_CONTRAST_PERCENT, 100);
            if (contrast != 100) {
                pipeline.put(new JSONObject().put("type", "contrast").put("percent", contrast));
            }
            if (inputMap.optBoolean(MetaOperation.OCR_MEDIAN_DENOISE, false)) {
                pipeline.put(new JSONObject().put("type", "median"));
            }
            if (inputMap.optBoolean(MetaOperation.OCR_GAUSSIAN_BLUR, false)) {
                pipeline.put(new JSONObject()
                        .put("type", "gaussian")
                        .put("radius", Math.max(1, inputMap.optInt(MetaOperation.OCR_GAUSSIAN_RADIUS, 1))));
            }
            if (inputMap.optBoolean(MetaOperation.OCR_SHARPEN, false)) {
                pipeline.put(new JSONObject().put("type", "sharpen"));
            }
            int threshold = inputMap.optInt(MetaOperation.OCR_THRESHOLD, 0);
            if (threshold > 0) {
                pipeline.put(new JSONObject().put("type", "threshold").put("value", threshold));
            }
            if (inputMap.optBoolean(MetaOperation.OCR_INVERT, false)) {
                pipeline.put(new JSONObject().put("type", "invert"));
            }
            String morph = inputMap.optString(MetaOperation.OCR_MORPH_MODE, "none");
            int morphIterations = inputMap.optInt(MetaOperation.OCR_MORPH_ITERATIONS, 0);
            if (!"none".equals(normalizeOcrMorphMode(morph)) && morphIterations > 0) {
                pipeline.put(new JSONObject()
                        .put("type", "morph")
                        .put("mode", normalizeOcrMorphMode(morph))
                        .put("iterations", morphIterations));
            }
        } catch (Exception ignored) {
        }
        return pipeline;
    }

    private void showOcrPipelinePool(LinearLayout container,
                                     EditText edtPipeline,
                                     String[] ocrMaskBase64,
                                     TextView tvMaskStatus,
                                     CheckBox chkUseMask,
                                     View dialogView,
                                     EditText edtBbox,
                                     Runnable onChanged) {
        final String[] labels = {
                "Mask（忽略画出的区域）",
                "缩放",
                "灰度化",
                "反色",
                "二值化",
                "对比度",
                "中值去噪",
                "高斯模糊",
                "锐化",
                "形态处理"
        };
        final String[] types = {
                "mask", "resize", "grayscale", "invert", "threshold",
                "contrast", "median", "gaussian", "sharpen", "morph"
        };
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(host.getContext())
                .setTitle("添加预处理")
                .setItems(labels, (d, which) -> {
                    appendOcrPipelineStep(edtPipeline, defaultOcrPipelineStep(types[which]));
                    renderOcrPipelineSteps(container, edtPipeline, ocrMaskBase64, tvMaskStatus, chkUseMask,
                            dialogView, edtBbox, onChanged);
                    if (onChanged != null) onChanged.run();
                })
                .create();
        showOverlayAlertDialog(dialog);
    }

    private JSONObject defaultOcrPipelineStep(String type) {
        JSONObject step = new JSONObject();
        try {
            step.put("type", type);
            if ("resize".equals(type)) {
                step.put("factor", 2.0);
                step.put("percent", 100);
            } else if ("threshold".equals(type)) {
                step.put("value", 127);
            } else if ("contrast".equals(type)) {
                step.put("percent", 140);
            } else if ("gaussian".equals(type)) {
                step.put("radius", 1);
            } else if ("morph".equals(type)) {
                step.put("mode", "dilate");
                step.put("iterations", 1);
            }
        } catch (Exception ignored) {
        }
        return step;
    }

    private void appendOcrPipelineStep(EditText edtPipeline, JSONObject step) {
        JSONArray pipeline = parseOcrPipelineInput(edtPipeline, false);
        if (pipeline == null) pipeline = new JSONArray();
        pipeline.put(step);
        setOcrPipelineText(edtPipeline, pipeline);
    }

    private void setOcrPipelineText(EditText edtPipeline, JSONArray pipeline) {
        if (edtPipeline == null) return;
        try {
            edtPipeline.setText(pipeline == null || pipeline.length() == 0 ? "" : pipeline.toString(2));
        } catch (Exception e) {
            edtPipeline.setText(pipeline == null ? "" : pipeline.toString());
        }
    }

    private void renderOcrPipelineSteps(LinearLayout container,
                                        EditText edtPipeline,
                                        String[] ocrMaskBase64,
                                        TextView tvMaskStatus,
                                        CheckBox chkUseMask,
                                        View dialogView,
                                        EditText edtBbox,
                                        Runnable onChanged) {
        if (container == null) return;
        container.removeAllViews();
        JSONArray pipeline = parseOcrPipelineInput(edtPipeline, false);
        if (pipeline == null || pipeline.length() == 0) {
            TextView empty = new TextView(host.getContext());
            empty.setText("未添加预处理步骤");
            empty.setTextColor(0xFF7A8A9A);
            empty.setTextSize(12);
            empty.setPadding(8, 6, 8, 8);
            container.addView(empty);
            return;
        }
        for (int i = 0; i < pipeline.length(); i++) {
            JSONObject step = pipeline.optJSONObject(i);
            if (step == null) continue;
            container.addView(createOcrPipelineStepRow(
                    container, edtPipeline, pipeline, i, step,
                    ocrMaskBase64, tvMaskStatus, chkUseMask, dialogView, edtBbox, onChanged));
        }
    }

    private View createOcrPipelineStepRow(LinearLayout container,
                                          EditText edtPipeline,
                                          JSONArray pipeline,
                                          int index,
                                          JSONObject step,
                                          String[] ocrMaskBase64,
                                          TextView tvMaskStatus,
                                          CheckBox chkUseMask,
                                          View dialogView,
                                          EditText edtBbox,
                                          Runnable onChanged) {
        LinearLayout row = new LinearLayout(host.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(8, 6, 8, 6);
        row.setBackgroundResource(R.drawable.item_operation_compact_bg);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, host.dp(6));
        row.setLayoutParams(rowLp);

        TextView title = new TextView(host.getContext());
        title.setText((index + 1) + ". " + ocrPipelineStepLabel(step) + "  " + ocrPipelineStepSummary(step));
        title.setTextColor(0xFF4E5F74);
        title.setTextSize(12);
        title.setSingleLine(false);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        row.addView(makeOcrPipelineButton("参", v -> {
            if ("mask".equals(step.optString("type"))) {
                showOcrMaskEditor(dialogView, edtBbox, ocrMaskBase64, tvMaskStatus, chkUseMask, onChanged);
            } else {
                editOcrPipelineStep(container, edtPipeline, pipeline, index,
                        ocrMaskBase64, tvMaskStatus, chkUseMask, dialogView, edtBbox, onChanged);
            }
        }));
        row.addView(makeOcrPipelineButton("↑", v -> moveOcrPipelineStep(container, edtPipeline, pipeline, index, -1,
                ocrMaskBase64, tvMaskStatus, chkUseMask, dialogView, edtBbox, onChanged)));
        row.addView(makeOcrPipelineButton("↓", v -> moveOcrPipelineStep(container, edtPipeline, pipeline, index, 1,
                ocrMaskBase64, tvMaskStatus, chkUseMask, dialogView, edtBbox, onChanged)));
        row.addView(makeOcrPipelineButton("×", v -> removeOcrPipelineStep(container, edtPipeline, pipeline, index,
                ocrMaskBase64, tvMaskStatus, chkUseMask, dialogView, edtBbox, onChanged)));
        return row;
    }

    private TextView makeOcrPipelineButton(String text, View.OnClickListener listener) {
        TextView button = new TextView(host.getContext());
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor("×".equals(text) ? 0xFFB23B3B : 0xFF00695C);
        button.setGravity(android.view.Gravity.CENTER);
        button.setBackgroundResource(R.drawable.item_operation_compact_bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(host.dp(28), host.dp(30));
        lp.setMargins(host.dp(4), 0, 0, 0);
        button.setLayoutParams(lp);
        button.setOnClickListener(listener);
        return button;
    }

    private void editOcrPipelineStep(LinearLayout container,
                                     EditText edtPipeline,
                                     JSONArray pipeline,
                                     int index,
                                     String[] ocrMaskBase64,
                                     TextView tvMaskStatus,
                                     CheckBox chkUseMask,
                                     View dialogView,
                                     EditText edtBbox,
                                     Runnable onChanged) {
        JSONObject step = pipeline.optJSONObject(index);
        if (step == null) {
            return;
        }
        String type = step.optString("type", "");
        if (!isKnownOcrPipelineStep(type)) {
            editOcrPipelineStepAsJson(container, edtPipeline, pipeline, index,
                    ocrMaskBase64, tvMaskStatus, chkUseMask, dialogView, edtBbox, onChanged);
            return;
        }

        LinearLayout form = new LinearLayout(host.getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = host.dp(12);
        form.setPadding(pad, host.dp(8), pad, 0);

        CheckBox enabled = new CheckBox(host.getContext());
        enabled.setText("启用此步骤");
        enabled.setTextColor(0xFF4E5F74);
        enabled.setChecked(step.optBoolean("enabled", true));
        form.addView(enabled);

        EditText factorInput = null;
        EditText percentInput = null;
        EditText thresholdInput = null;
        EditText contrastInput = null;
        EditText radiusInput = null;
        AutoCompleteTextView morphModeInput = null;
        EditText morphIterationsInput = null;

        if ("resize".equals(type) || "scale".equals(type)) {
            factorInput = addOcrDialogNumberInput(form, "倍率", String.valueOf(step.optDouble("factor", 2.0)), true);
            percentInput = addOcrDialogNumberInput(form, "百分比", String.valueOf(step.optInt("percent", 100)), false);
        } else if ("threshold".equals(type) || "binary".equals(type)) {
            thresholdInput = addOcrDialogNumberInput(form, "阈值(0-255)", String.valueOf(step.optInt("value", 127)), false);
        } else if ("contrast".equals(type)) {
            contrastInput = addOcrDialogNumberInput(form, "对比度百分比(50-300)", String.valueOf(step.optInt("percent", 140)), false);
        } else if ("gaussian".equals(type) || "blur".equals(type) || "gaussian_blur".equals(type)) {
            radiusInput = addOcrDialogNumberInput(form, "模糊半径(0-10)", String.valueOf(step.optInt("radius", 1)), false);
        } else if ("morph".equals(type) || "morphology".equals(type)) {
            morphModeInput = addOcrDialogChoiceInput(form, "模式", step.optString("mode", "dilate"),
                    java.util.Arrays.asList("dilate", "erode", "open", "close"));
            morphIterationsInput = addOcrDialogNumberInput(form, "次数(0-3)", String.valueOf(step.optInt("iterations", 1)), false);
        } else {
            TextView note = new TextView(host.getContext());
            note.setText("这个步骤没有额外参数。");
            note.setTextColor(0xFF7A8A9A);
            note.setTextSize(12);
            note.setPadding(0, host.dp(8), 0, 0);
            form.addView(note);
        }

        final EditText finalFactorInput = factorInput;
        final EditText finalPercentInput = percentInput;
        final EditText finalThresholdInput = thresholdInput;
        final EditText finalContrastInput = contrastInput;
        final EditText finalRadiusInput = radiusInput;
        final AutoCompleteTextView finalMorphModeInput = morphModeInput;
        final EditText finalMorphIterationsInput = morphIterationsInput;
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(host.getContext())
                .setTitle("参数: " + ocrPipelineStepLabel(step))
                .setView(form)
                .setPositiveButton("保存", (d, which) -> {
                    try {
                        JSONObject updated = new JSONObject();
                        updated.put("type", type);
                        if (!enabled.isChecked()) {
                            updated.put("enabled", false);
                        }
                        if (finalFactorInput != null) {
                            updated.put("factor", parseDialogFloat(finalFactorInput, 1.0f, 0.1f, 8.0f));
                            updated.put("percent", parseDialogInt(finalPercentInput, 100, 1, 400));
                        } else if (finalThresholdInput != null) {
                            updated.put("value", parseDialogInt(finalThresholdInput, 127, 0, 255));
                        } else if (finalContrastInput != null) {
                            updated.put("percent", parseDialogInt(finalContrastInput, 140, 50, 300));
                        } else if (finalRadiusInput != null) {
                            updated.put("radius", parseDialogInt(finalRadiusInput, 1, 0, 10));
                        } else if (finalMorphModeInput != null) {
                            updated.put("mode", normalizeOcrMorphMode(safeText(finalMorphModeInput)));
                            updated.put("iterations", parseDialogInt(finalMorphIterationsInput, 1, 0, 3));
                        }
                        pipeline.put(index, updated);
                        setOcrPipelineText(edtPipeline, pipeline);
                        renderOcrPipelineSteps(container, edtPipeline, ocrMaskBase64, tvMaskStatus, chkUseMask,
                                dialogView, edtBbox, onChanged);
                        if (onChanged != null) onChanged.run();
                    } catch (Exception e) {
                        host.showToast("参数保存失败: " + e.getMessage());
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        showOverlayAlertDialog(dialog);
    }

    private void editOcrPipelineStepAsJson(LinearLayout container,
                                           EditText edtPipeline,
                                           JSONArray pipeline,
                                           int index,
                                           String[] ocrMaskBase64,
                                           TextView tvMaskStatus,
                                           CheckBox chkUseMask,
                                           View dialogView,
                                           EditText edtBbox,
                                           Runnable onChanged) {
        EditText editor = new EditText(host.getContext());
        editor.setSingleLine(false);
        editor.setMinLines(4);
        editor.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        JSONObject step = pipeline.optJSONObject(index);
        editor.setText(step == null ? "{}" : step.toString());
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(host.getContext())
                .setTitle("编辑步骤 JSON")
                .setView(editor)
                .setPositiveButton("保存", (d, which) -> {
                    try {
                        pipeline.put(index, new JSONObject(editor.getText().toString()));
                        setOcrPipelineText(edtPipeline, pipeline);
                        renderOcrPipelineSteps(container, edtPipeline, ocrMaskBase64, tvMaskStatus, chkUseMask,
                                dialogView, edtBbox, onChanged);
                        if (onChanged != null) onChanged.run();
                    } catch (Exception e) {
                        host.showToast("步骤 JSON 格式错误");
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        showOverlayAlertDialog(dialog);
    }

    private boolean isKnownOcrPipelineStep(String type) {
        if (TextUtils.isEmpty(type)) return false;
        return "resize".equals(type) || "scale".equals(type)
                || "grayscale".equals(type) || "gray".equals(type)
                || "invert".equals(type)
                || "threshold".equals(type) || "binary".equals(type)
                || "contrast".equals(type)
                || "median".equals(type) || "denoise".equals(type) || "median_denoise".equals(type)
                || "gaussian".equals(type) || "blur".equals(type) || "gaussian_blur".equals(type)
                || "sharpen".equals(type)
                || "morph".equals(type) || "morphology".equals(type);
    }

    private EditText addOcrDialogNumberInput(LinearLayout form, String label, String value, boolean decimal) {
        TextView title = new TextView(host.getContext());
        title.setText(label);
        title.setTextColor(0xFF4E5F74);
        title.setTextSize(12);
        title.setPadding(0, host.dp(8), 0, host.dp(2));
        form.addView(title);

        EditText input = new EditText(host.getContext());
        input.setSingleLine(true);
        input.setText(value);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | (decimal ? android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        form.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private AutoCompleteTextView addOcrDialogChoiceInput(LinearLayout form,
                                                         String label,
                                                         String value,
                                                         java.util.List<String> options) {
        TextView title = new TextView(host.getContext());
        title.setText(label);
        title.setTextColor(0xFF4E5F74);
        title.setTextSize(12);
        title.setPadding(0, host.dp(8), 0, host.dp(2));
        form.addView(title);

        AutoCompleteTextView input = new AutoCompleteTextView(host.getContext());
        input.setSingleLine(true);
        input.setText(value, false);
        dialogHelpers.bindAutoComplete(input, options);
        form.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private int parseDialogInt(EditText input, int def, int min, int max) {
        Integer value = parseBoundedInt(safeText(input), def, min, max);
        return value == null ? def : value;
    }

    private float parseDialogFloat(EditText input, float def, float min, float max) {
        Float value = parseBoundedFloat(safeText(input), def, min, max);
        return value == null ? def : value;
    }

    private void showOverlayAlertDialog(android.app.AlertDialog dialog) {
        android.view.Window preWindow = dialog.getWindow();
        if (preWindow != null) {
            preWindow.setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
    }

    private void moveOcrPipelineStep(LinearLayout container,
                                     EditText edtPipeline,
                                     JSONArray pipeline,
                                     int index,
                                     int direction,
                                     String[] ocrMaskBase64,
                                     TextView tvMaskStatus,
                                     CheckBox chkUseMask,
                                     View dialogView,
                                     EditText edtBbox,
                                     Runnable onChanged) {
        int target = index + direction;
        if (target < 0 || target >= pipeline.length()) return;
        JSONArray next = new JSONArray();
        for (int i = 0; i < pipeline.length(); i++) {
            if (i == index) {
                next.put(pipeline.opt(target));
            } else if (i == target) {
                next.put(pipeline.opt(index));
            } else {
                next.put(pipeline.opt(i));
            }
        }
        setOcrPipelineText(edtPipeline, next);
        renderOcrPipelineSteps(container, edtPipeline, ocrMaskBase64, tvMaskStatus, chkUseMask, dialogView, edtBbox, onChanged);
        if (onChanged != null) onChanged.run();
    }

    private void removeOcrPipelineStep(LinearLayout container,
                                       EditText edtPipeline,
                                       JSONArray pipeline,
                                       int index,
                                       String[] ocrMaskBase64,
                                       TextView tvMaskStatus,
                                       CheckBox chkUseMask,
                                       View dialogView,
                                       EditText edtBbox,
                                       Runnable onChanged) {
        JSONArray next = new JSONArray();
        for (int i = 0; i < pipeline.length(); i++) {
            if (i != index) next.put(pipeline.opt(i));
        }
        setOcrPipelineText(edtPipeline, next);
        renderOcrPipelineSteps(container, edtPipeline, ocrMaskBase64, tvMaskStatus, chkUseMask, dialogView, edtBbox, onChanged);
        if (onChanged != null) onChanged.run();
    }

    private String ocrPipelineStepLabel(JSONObject step) {
        String type = step == null ? "" : step.optString("type", "");
        if ("mask".equals(type)) return "Mask";
        if ("resize".equals(type) || "scale".equals(type)) return "缩放";
        if ("grayscale".equals(type) || "gray".equals(type)) return "灰度";
        if ("invert".equals(type)) return "反色";
        if ("threshold".equals(type) || "binary".equals(type)) return "二值化";
        if ("contrast".equals(type)) return "对比度";
        if ("median".equals(type) || "denoise".equals(type)) return "中值去噪";
        if ("gaussian".equals(type) || "blur".equals(type)) return "高斯模糊";
        if ("sharpen".equals(type)) return "锐化";
        if ("morph".equals(type) || "morphology".equals(type)) return "形态";
        return TextUtils.isEmpty(type) ? "未知" : type;
    }

    private String ocrPipelineStepSummary(JSONObject step) {
        if (step == null) return "";
        String type = step.optString("type", "");
        if ("resize".equals(type) || "scale".equals(type)) {
            return "x" + step.optDouble("factor", 1.0) + " / " + step.optInt("percent", 100) + "%";
        }
        if ("threshold".equals(type) || "binary".equals(type)) {
            return "阈值 " + step.optInt("value", 127);
        }
        if ("contrast".equals(type)) {
            return step.optInt("percent", 140) + "%";
        }
        if ("gaussian".equals(type) || "blur".equals(type)) {
            return "半径 " + step.optInt("radius", 1);
        }
        if ("morph".equals(type) || "morphology".equals(type)) {
            return step.optString("mode", "dilate") + " x" + step.optInt("iterations", 1);
        }
        return "";
    }

    private void bindOcrSeekBarLabels(SeekBar seekBar, TextView label, String prefix) {
        bindOcrSeekBarLabels(seekBar, label, prefix, "");
    }

    private void bindOcrSeekBarLabels(SeekBar seekBar, TextView label, String prefix, String suffix) {
        bindOcrSeekBarLabels(seekBar, label, prefix, suffix, null);
    }

    private void bindOcrSeekBarLabels(SeekBar seekBar, TextView label, String prefix, String suffix, Runnable onChanged) {
        if (seekBar == null || label == null) {
            return;
        }
        label.setText(prefix + seekBar.getProgress() + suffix);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(prefix + progress + suffix);
                if (fromUser && onChanged != null) {
                    onChanged.run();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        });
    }

    private void bindOcrContrastSeekBar(SeekBar seekBar, TextView label, Runnable onChanged) {
        if (seekBar == null || label == null) {
            return;
        }
        if (seekBar.getProgress() < 50) {
            seekBar.setProgress(50);
        }
        label.setText("对比度: " + seekBar.getProgress() + "%");
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 50) {
                    seekBar.setProgress(50);
                    return;
                }
                label.setText("对比度: " + progress + "%");
                if (fromUser && onChanged != null) {
                    onChanged.run();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (seekBar.getProgress() < 50) {
                    seekBar.setProgress(50);
                }
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        });
    }

    private void addOcrPreviewTextWatcher(EditText input, Runnable onChanged) {
        if (input == null || onChanged == null) {
            return;
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                onChanged.run();
            }
        });
    }

    private void showOcrMaskEditor(View dialogView,
                                   EditText edtBbox,
                                   String[] ocrMaskBase64,
                                   TextView tvMaskStatus,
                                   CheckBox chkUseMask,
                                   Runnable onChanged) {
        java.util.List<Integer> bbox = regionPickHelper != null
                ? regionPickHelper.parseBboxInput(safeText(edtBbox))
                : parseOcrBboxText(safeText(edtBbox));
        if (bbox == null || bbox.size() < 4) {
            edtBbox.setError("请先填写有效识别区域");
            return;
        }
        Runnable restoreViews = host.hideViewsForCapture(dialogView, host.getProjectPanelView());
        mainHandler.postDelayed(() -> new Thread(() -> {
            Bitmap source = null;
            try {
                Rect roi = new Rect(bbox.get(0), bbox.get(1), bbox.get(0) + bbox.get(2), bbox.get(1) + bbox.get(3));
                source = ScreenCapture.captureLatestBitmap(roi, 800L, 40L);
                Bitmap finalSource = source;
                source = null;
                mainHandler.post(() -> {
                    restoreViews.run();
                    if (finalSource == null || finalSource.isRecycled()) {
                        host.showToast("截图失败，无法编辑 OCR Mask");
                        return;
                    }
                    openOcrMaskEditorOverlay(finalSource, ocrMaskBase64, tvMaskStatus, chkUseMask, onChanged);
                });
            } catch (Exception e) {
                Bitmap failedSource = source;
                mainHandler.post(() -> {
                    restoreViews.run();
                    host.showToast("打开 OCR Mask 失败: " + e.getMessage());
                    if (failedSource != null && !failedSource.isRecycled()) {
                        failedSource.recycle();
                    }
                });
            }
        }, "ocr-mask-capture").start(), 180L);
    }

    private void openOcrMaskEditorOverlay(Bitmap source,
                                          String[] ocrMaskBase64,
                                          TextView tvMaskStatus,
                                          CheckBox chkUseMask,
                                          Runnable onChanged) {
        Bitmap initialMask = decodeOcrMask(ocrMaskBase64 == null ? "" : ocrMaskBase64[0]);
        View overlay = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_template_mask_editor, null);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        TemplateMaskEditorView editor = overlay.findViewById(R.id.template_mask_editor_view);
        TextView tvTitle = overlay.findViewById(R.id.tv_mask_editor_title);
        TextView btnDraw = overlay.findViewById(R.id.btn_mask_draw);
        TextView btnErase = overlay.findViewById(R.id.btn_mask_erase);
        TextView btnClear = overlay.findViewById(R.id.btn_mask_clear);
        TextView btnSave = overlay.findViewById(R.id.btn_mask_save);
        SeekBar sbBrush = overlay.findViewById(R.id.sb_mask_brush);
        TextView tvBrush = overlay.findViewById(R.id.tv_mask_brush_size);

        if (tvTitle != null) {
            tvTitle.setText("OCR Mask：画出的区域会被忽略");
        }
        editor.setTemplateBitmap(source, initialMask);
        if (source != null && !source.isRecycled()) source.recycle();
        if (initialMask != null && !initialMask.isRecycled()) initialMask.recycle();

        Runnable updateModeUi = () -> {
            boolean erase = editor.isEraseMode();
            btnDraw.setBackgroundResource(erase ? R.drawable.item_operation_compact_bg : R.drawable.panel_btn_primary_selector);
            btnDraw.setTextColor(erase ? android.graphics.Color.rgb(49, 93, 191) : android.graphics.Color.WHITE);
            btnErase.setBackgroundResource(erase ? R.drawable.panel_btn_primary_selector : R.drawable.item_operation_compact_bg);
            btnErase.setTextColor(erase ? android.graphics.Color.WHITE : android.graphics.Color.rgb(49, 93, 191));
        };
        Runnable updateBrushUi = () -> tvBrush.setText(editor.getBrushSize() + " px");

        btnDraw.setOnClickListener(v -> {
            editor.setEraseMode(false);
            updateModeUi.run();
        });
        btnErase.setOnClickListener(v -> {
            editor.setEraseMode(true);
            updateModeUi.run();
        });
        btnClear.setOnClickListener(v -> editor.clearMask());
        sbBrush.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                editor.setBrushSize(progress + 1);
                updateBrushUi.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        overlay.findViewById(R.id.btn_mask_zoom_in).setOnClickListener(v -> editor.zoomIn());
        overlay.findViewById(R.id.btn_mask_zoom_out).setOnClickListener(v -> editor.zoomOut());
        overlay.findViewById(R.id.btn_mask_editor_close).setOnClickListener(v -> {
            editor.release();
            dialogHelpers.safeRemoveView(overlay);
        });
        btnSave.setOnClickListener(v -> {
            Bitmap out = editor.hasMaskPixels() ? editor.exportMaskBitmap() : null;
            try {
                if (ocrMaskBase64 != null) {
                    ocrMaskBase64[0] = out == null ? "" : encodeBitmapToBase64(out);
                }
                if (chkUseMask != null) {
                    chkUseMask.setChecked(out != null);
                }
                updateOcrMaskStatus(tvMaskStatus, ocrMaskBase64 == null ? "" : ocrMaskBase64[0]);
                if (onChanged != null) {
                    onChanged.run();
                }
                host.showToast(out == null ? "OCR Mask 已清空" : "OCR Mask 已保存到当前节点");
            } catch (Exception e) {
                host.showToast("保存 OCR Mask 失败: " + e.getMessage());
            } finally {
                if (out != null && !out.isRecycled()) out.recycle();
                editor.release();
                dialogHelpers.safeRemoveView(overlay);
            }
        });

        updateModeUi.run();
        updateBrushUi.run();
        wm.addView(overlay, lp);
    }

    private Bitmap decodeOcrMask(String rawBase64) {
        if (TextUtils.isEmpty(rawBase64)) {
            return null;
        }
        try {
            String data = stripDataUriPrefix(rawBase64);
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    private String encodeBitmapToBase64(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private int[] decodeOcrMaskSize(String rawBase64) {
        Bitmap mask = decodeOcrMask(rawBase64);
        if (mask == null || mask.isRecycled()) {
            return null;
        }
        int[] size = new int[]{mask.getWidth(), mask.getHeight()};
        mask.recycle();
        return size;
    }

    private void updateOcrMaskStatus(TextView statusView, String rawBase64) {
        if (statusView == null) return;
        int[] size = decodeOcrMaskSize(rawBase64);
        statusView.setText(size == null ? "Mask: 未绘制" : "Mask: 已绘制 " + size[0] + "×" + size[1]);
    }

    private String stripDataUriPrefix(String raw) {
        if (raw == null) return "";
        int comma = raw.indexOf(',');
        if (raw.startsWith("data:") && comma >= 0 && comma < raw.length() - 1) {
            return raw.substring(comma + 1);
        }
        return raw;
    }

    private void updateOcrProcessedPreview(View dialogView,
                                           EditText edtBbox,
                                           AutoCompleteTextView edtModel,
                                           EditText edtLanguage,
                                           EditText edtTrainedData,
                                           EditText edtScaleFactor,
                                           CheckBox chkGrayscale,
                                           CheckBox chkCompress,
                                           CheckBox chkGaussian,
                                           SeekBar skThreshold,
                                           SeekBar skCompress,
                                           SeekBar skGaussian,
                                           CheckBox chkUseMask,
                                           String[] ocrMaskBase64,
                                           EditText edtPipeline,
                                           CheckBox chkInvert,
                                           SeekBar skContrast,
                                           CheckBox chkMedian,
                                           CheckBox chkSharpen,
                                           AutoCompleteTextView edtMorphMode,
                                           SeekBar skMorph,
                                           ImageView ivProcessedPreview,
                                           int seq,
                                           int[] latestSeq) {
        if (dialogView == null || ivProcessedPreview == null) {
            return;
        }
        java.util.List<Integer> bbox = regionPickHelper != null
                ? regionPickHelper.parseBboxInput(safeText(edtBbox))
                : parseOcrBboxText(safeText(edtBbox));
        if (bbox == null || bbox.size() < 4) {
            ivProcessedPreview.setImageDrawable(null);
            return;
        }
        Float scaleFactor = parseBoundedFloat(safeText(edtScaleFactor), 2.0f, 1.0f, 4.0f);
        if (scaleFactor == null) {
            ivProcessedPreview.setImageDrawable(null);
            return;
        }

        java.util.Map<String, Object> inputMap = new java.util.HashMap<>();
        String modelCode = ocrModelCode(safeText(edtModel));
        inputMap.put(MetaOperation.OCR_MODEL, modelCode);
        inputMap.put(MetaOperation.OCR_LANGUAGE, ocrLanguageFromTrainingFiles(safeText(edtModel)));
        inputMap.put(MetaOperation.OCR_TRAINED_DATA, ocrSingleTrainingFileName(safeText(edtModel)));
        inputMap.put(MetaOperation.OCR_SCALE_FACTOR, scaleFactor);
        inputMap.put(MetaOperation.OCR_GRAYSCALE, chkGrayscale != null && chkGrayscale.isChecked());
        inputMap.put(MetaOperation.OCR_THRESHOLD, skThreshold == null ? 0 : Math.max(0, Math.min(255, skThreshold.getProgress())));
        inputMap.put(MetaOperation.OCR_COMPRESS_PERCENT,
                chkCompress != null && chkCompress.isChecked() && skCompress != null ? Math.max(10, skCompress.getProgress()) : 100);
        inputMap.put(MetaOperation.OCR_GAUSSIAN_BLUR, chkGaussian != null && chkGaussian.isChecked());
        inputMap.put(MetaOperation.OCR_GAUSSIAN_RADIUS,
                chkGaussian != null && chkGaussian.isChecked() && skGaussian != null ? skGaussian.getProgress() : 0);
        inputMap.put(MetaOperation.OCR_USE_MASK,
                chkUseMask != null && chkUseMask.isChecked() && ocrMaskBase64 != null && !TextUtils.isEmpty(ocrMaskBase64[0]));
        inputMap.put(MetaOperation.OCR_MASK_PNG, ocrMaskBase64 == null ? "" : ocrMaskBase64[0]);
        JSONArray preprocessPipeline = parseOcrPipelineInput(edtPipeline, false);
        if (preprocessPipeline != null && preprocessPipeline.length() > 0) {
            inputMap.put(MetaOperation.OCR_PREPROCESS_PIPELINE, jsonArrayToJavaList(preprocessPipeline));
        }
        inputMap.put(MetaOperation.OCR_INVERT, chkInvert != null && chkInvert.isChecked());
        inputMap.put(MetaOperation.OCR_CONTRAST_PERCENT, skContrast == null ? 100 : Math.max(50, Math.min(300, skContrast.getProgress())));
        inputMap.put(MetaOperation.OCR_MEDIAN_DENOISE, chkMedian != null && chkMedian.isChecked());
        inputMap.put(MetaOperation.OCR_SHARPEN, chkSharpen != null && chkSharpen.isChecked());
        inputMap.put(MetaOperation.OCR_MORPH_MODE, normalizeOcrMorphMode(safeText(edtMorphMode)));
        inputMap.put(MetaOperation.OCR_MORPH_ITERATIONS, skMorph == null ? 0 : Math.max(0, Math.min(3, skMorph.getProgress())));
        inputMap.put(MetaOperation.OCR_DIGITS_ONLY, "number".equals(modelCode));
        OcrTextOperationHandler.OcrOptions options = OcrTextOperationHandler.OcrOptions.fromInput(inputMap);

        new Thread(() -> {
            android.graphics.Bitmap preview = OcrTextOperationHandler.renderProcessedPreview(
                    host.getContext().getApplicationContext(),
                    bbox,
                    options);
            dialogView.post(() -> {
                if (latestSeq != null && seq != latestSeq[0]) {
                    if (preview != null && !preview.isRecycled()) {
                        preview.recycle();
                    }
                    return;
                }
                if (preview == null || preview.isRecycled()) {
                    ivProcessedPreview.setImageDrawable(null);
                } else {
                    ivProcessedPreview.setImageBitmap(preview);
                }
            });
        }, "ocr-preview-render").start();
    }

    private void applyOcrModelPreset(String label, EditText edtLanguage, EditText edtTrainedData) {
        if (edtLanguage != null) {
            edtLanguage.setText(ocrLanguageFromTrainingFiles(label));
        }
        if (edtTrainedData != null) {
            edtTrainedData.setText(ocrSingleTrainingFileName(label));
        }
    }

    private String ocrModelCode(String label) {
        java.util.List<String> files = parseOcrTrainingFiles(label);
        if (files.size() == 1 && "chi_sim.traineddata".equals(files.get(0))) {
            return "chinese";
        }
        if (files.size() == 1 && "eng.traineddata".equals(files.get(0))) {
            return "english";
        }
        if (files.size() == 1 && "number.traineddata".equals(files.get(0))) {
            return "number";
        }
        if (files.size() == 2
                && files.contains("chi_sim.traineddata")
                && files.contains("eng.traineddata")) {
            return "chi_eng";
        }
        if (!files.isEmpty()) {
            return "custom";
        }
        return "chi_eng";
    }

    private String ocrModelLabel(String code) {
        if ("chinese".equals(code)) {
            return "chi_sim.traineddata";
        }
        if ("english".equals(code)) {
            return "eng.traineddata";
        }
        if ("number".equals(code)) {
            return "number.traineddata";
        }
        if ("custom".equals(code)) {
            return "custom.traineddata";
        }
        return "chi_sim.traineddata + eng.traineddata";
    }

    private String ocrTrainingFileLabel(JSONObject inputMap) {
        if (inputMap == null) {
            return "chi_sim.traineddata + eng.traineddata";
        }
        String model = inputMap.optString(MetaOperation.OCR_MODEL, "chi_eng");
        if ("chinese".equals(model) || "english".equals(model) || "number".equals(model) || "chi_eng".equals(model)) {
            return ocrModelLabel(model);
        }
        String language = inputMap.optString(MetaOperation.OCR_LANGUAGE, "");
        if (!TextUtils.isEmpty(language)) {
            return ocrTrainingFileLabelFromLanguage(language);
        }
        String trainedData = inputMap.optString(MetaOperation.OCR_TRAINED_DATA, "");
        return TextUtils.isEmpty(trainedData) ? ocrModelLabel(model) : normalizeOcrTrainingFileToken(trainedData);
    }

    private String ocrTrainingFileLabelFromLanguage(String language) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        if (!TextUtils.isEmpty(language)) {
            for (String part : language.split("\\+")) {
                String token = normalizeOcrTrainingFileToken(part);
                if (!TextUtils.isEmpty(token)) {
                    labels.add(token);
                }
            }
        }
        return labels.isEmpty() ? "chi_sim.traineddata + eng.traineddata" : TextUtils.join(" + ", labels);
    }

    private String ocrLanguageFromTrainingFiles(String label) {
        java.util.List<String> files = parseOcrTrainingFiles(label);
        if (files.isEmpty()) {
            files = parseOcrTrainingFiles("chi_sim.traineddata + eng.traineddata");
        }
        java.util.List<String> languages = new java.util.ArrayList<>();
        for (String file : files) {
            String language = file.endsWith(".traineddata")
                    ? file.substring(0, file.length() - ".traineddata".length())
                    : file;
            if (!TextUtils.isEmpty(language)) {
                languages.add(language);
            }
        }
        return languages.isEmpty() ? "chi_sim+eng" : TextUtils.join("+", languages);
    }

    private String ocrSingleTrainingFileName(String label) {
        java.util.List<String> files = parseOcrTrainingFiles(label);
        return files.size() == 1 ? files.get(0) : "";
    }

    private java.util.List<String> parseOcrTrainingFiles(String label) {
        java.util.List<String> files = new java.util.ArrayList<>();
        if (TextUtils.isEmpty(label)) {
            return files;
        }
        String normalized = label.replace('＋', '+');
        for (String part : normalized.split("\\+")) {
            String token = normalizeOcrTrainingFileToken(part);
            if (!TextUtils.isEmpty(token) && !files.contains(token)) {
                files.add(token);
            }
        }
        return files;
    }

    private String normalizeOcrTrainingFileToken(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        String token = raw.trim();
        int slash = Math.max(token.lastIndexOf('/'), token.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < token.length()) {
            token = token.substring(slash + 1);
        }
        if ("中文".equals(token) || "识别中文".equals(token)) {
            token = "chi_sim";
        } else if ("英文".equals(token) || "识别英文".equals(token)) {
            token = "eng";
        }
        String lower = token.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".traineddata")) {
            token = token + ".traineddata";
        }
        return token;
    }

    private void runOcrDialogTest(View dialogView,
                                  EditText edtBbox,
                                  AutoCompleteTextView edtModel,
                                  EditText edtLanguage,
                                  EditText edtTrainedData,
                                  EditText edtScaleFactor,
                                  CheckBox chkGrayscale,
                                  CheckBox chkCompress,
                                  CheckBox chkGaussian,
                                  SeekBar skThreshold,
                                  SeekBar skCompress,
                                  SeekBar skGaussian,
                                  CheckBox chkUseMask,
                                  String[] ocrMaskBase64,
                                  EditText edtPipeline,
                                  CheckBox chkInvert,
                                  SeekBar skContrast,
                                  CheckBox chkMedian,
                                  CheckBox chkSharpen,
                                  AutoCompleteTextView edtMorphMode,
                                  SeekBar skMorph,
                                  AutoCompleteTextView edtPsm,
                                  TextView tvTestResult,
                                  ImageView ivProcessedPreview) {
        String bboxText = safeText(edtBbox);
        java.util.List<Integer> bbox = regionPickHelper != null ? regionPickHelper.parseBboxInput(bboxText) : parseOcrBboxText(bboxText);
        if (bbox == null || bbox.size() < 4) {
            edtBbox.setError("区域格式应为 x,y,w,h");
            return;
        }
        Float scaleFactor = parseBoundedFloat(safeText(edtScaleFactor), 2.0f, 1.0f, 4.0f);
        if (scaleFactor == null) {
            edtScaleFactor.setError("请输入 1.0~4.0 的数值");
            return;
        }
        int threshold = Math.max(0, Math.min(255, skThreshold.getProgress()));
        String pageSegMode = normalizeOcrPageSegMode(safeText(edtPsm));
        if (pageSegMode == null) {
            edtPsm.setError("仅支持 auto、block、line、word、sparse 或数字模式");
            return;
        }

        tvTestResult.setText("正在识别...");
        java.util.Map<String, Object> inputMap = new java.util.HashMap<>();
        String modelCode = ocrModelCode(safeText(edtModel));
        inputMap.put(MetaOperation.OCR_MODEL, modelCode);
        inputMap.put(MetaOperation.OCR_LANGUAGE, ocrLanguageFromTrainingFiles(safeText(edtModel)));
        inputMap.put(MetaOperation.OCR_TRAINED_DATA, ocrSingleTrainingFileName(safeText(edtModel)));
        inputMap.put(MetaOperation.OCR_SCALE_FACTOR, scaleFactor);
        inputMap.put(MetaOperation.OCR_GRAYSCALE, chkGrayscale.isChecked());
        inputMap.put(MetaOperation.OCR_THRESHOLD, threshold);
        inputMap.put(MetaOperation.OCR_COMPRESS_PERCENT, chkCompress.isChecked() ? Math.max(10, skCompress.getProgress()) : 100);
        inputMap.put(MetaOperation.OCR_GAUSSIAN_BLUR, chkGaussian.isChecked());
        inputMap.put(MetaOperation.OCR_GAUSSIAN_RADIUS, chkGaussian.isChecked() ? skGaussian.getProgress() : 0);
        inputMap.put(MetaOperation.OCR_USE_MASK,
                chkUseMask != null && chkUseMask.isChecked() && ocrMaskBase64 != null && !TextUtils.isEmpty(ocrMaskBase64[0]));
        inputMap.put(MetaOperation.OCR_MASK_PNG, ocrMaskBase64 == null ? "" : ocrMaskBase64[0]);
        JSONArray preprocessPipeline = parseOcrPipelineInput(edtPipeline, true);
        if (preprocessPipeline == null) {
            return;
        }
        if (preprocessPipeline.length() > 0) {
            inputMap.put(MetaOperation.OCR_PREPROCESS_PIPELINE, jsonArrayToJavaList(preprocessPipeline));
        }
        inputMap.put(MetaOperation.OCR_INVERT, chkInvert != null && chkInvert.isChecked());
        inputMap.put(MetaOperation.OCR_CONTRAST_PERCENT, skContrast == null ? 100 : Math.max(50, Math.min(300, skContrast.getProgress())));
        inputMap.put(MetaOperation.OCR_MEDIAN_DENOISE, chkMedian != null && chkMedian.isChecked());
        inputMap.put(MetaOperation.OCR_SHARPEN, chkSharpen != null && chkSharpen.isChecked());
        inputMap.put(MetaOperation.OCR_MORPH_MODE, normalizeOcrMorphMode(safeText(edtMorphMode)));
        inputMap.put(MetaOperation.OCR_MORPH_ITERATIONS, skMorph == null ? 0 : Math.max(0, Math.min(3, skMorph.getProgress())));
        inputMap.put(MetaOperation.OCR_DIGITS_ONLY, "number".equals(modelCode));
        OcrTextOperationHandler.OcrOptions options = OcrTextOperationHandler.OcrOptions.fromInput(inputMap);
        new Thread(() -> {
            OcrTextOperationHandler.OcrPreviewResult result =
                    OcrTextOperationHandler.recognizeOnce(
                            host.getContext().getApplicationContext(),
                            bbox,
                            options,
                            pageSegMode);
            dialogView.post(() -> {
                StringBuilder builder = new StringBuilder();
                builder.append(result.success ? "识别完成" : "识别失败");
                builder.append("  置信度: ").append(result.confidence);
                if (!TextUtils.isEmpty(result.reason)) {
                    builder.append("\n状态: ").append(result.reason);
                }
                if (!TextUtils.isEmpty(result.message)) {
                    builder.append("\n提示: ").append(result.message);
                }
                builder.append("\n\n").append(TextUtils.isEmpty(result.text) ? "(空结果)" : result.text);
                tvTestResult.setText(builder.toString());
                if (ivProcessedPreview != null && result.previewBitmap != null && !result.previewBitmap.isRecycled()) {
                    ivProcessedPreview.setImageBitmap(result.previewBitmap);
                }
            });
        }, "ocr-dialog-test").start();
    }

    private Integer parseBoundedInt(String raw, int def, int min, int max) {
        int value = def;
        if (!TextUtils.isEmpty(raw)) {
            try {
                value = Integer.parseInt(raw.trim());
            } catch (Exception e) {
                return null;
            }
        }
        return Math.max(min, Math.min(max, value));
    }

    private Float parseBoundedFloat(String raw, float def, float min, float max) {
        float value = def;
        if (!TextUtils.isEmpty(raw)) {
            try {
                value = Float.parseFloat(raw.trim());
            } catch (Exception e) {
                return null;
            }
        }
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeOcrPageSegMode(String raw) {
        String value = TextUtils.isEmpty(raw) ? "auto" : raw.trim().toLowerCase();
        if ("auto".equals(value) || "block".equals(value) || "line".equals(value)
                || "word".equals(value) || "sparse".equals(value)
                || "single_block".equals(value) || "single_line".equals(value)
                || "single_word".equals(value) || "sparse_text".equals(value)) {
            return value;
        }
        try {
            Integer.parseInt(value);
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeOcrMorphMode(String raw) {
        String value = TextUtils.isEmpty(raw) ? "none" : raw.trim().toLowerCase();
        if ("加粗".equals(raw) || "膨胀".equals(raw) || "dilate".equals(value)) return "dilate";
        if ("腐蚀".equals(raw) || "细化".equals(raw) || "erode".equals(value)) return "erode";
        if ("开运算".equals(raw) || "open".equals(value)) return "open";
        if ("闭运算".equals(raw) || "close".equals(value)) return "close";
        return "none";
    }

    private String defaultIfEmpty(String raw, String def) {
        return TextUtils.isEmpty(raw) ? def : raw;
    }

    // ==================== Back Key Operation ====================

    public void showAddBackKeyDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_back_key, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 350, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        edtName.setText("返回按键");
        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                operationPickerLauncher.showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText);
            }
        });
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) operationObject.put("id", idGenerator.generateId());
                operationObject.put("name", name);
                operationObject.put("type", 17);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(operationObject);
                crudHelper.writeOperationsArray(operations, "已添加返回按键节点", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) addListener.onOperationAdded();
                });
            } catch (Exception e) {
                host.showToast("构建返回按键操作失败: " + e.getMessage());
            }
        });
    }

    public void showEditBackKeyDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_back_key, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 350, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        edtName.setText(operationObject.optString("name", ""));
        JSONObject inputMap0 = operationObject.optJSONObject("inputMap");
        edtNextOperation.setText(inputMap0 != null ? inputMap0.optString(MetaOperation.NEXT_OPERATION_ID, "") : "");
        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                operationPickerLauncher.showOperationPickerDialog("选择下一节点", operationId, edtNextOperation::setText);
            }
        });
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }

            try {
                JSONObject updated = new JSONObject();
                updated.put("id", operationId);
                updated.put("name", name);
                updated.put("type", 17);
                updated.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updated.put("inputMap", inputMap);

                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updated.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) updateListener.onOperationUpdated();
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    // ==================== HTTP 请求操作 ====================

    public void showAddHttpRequestDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_http_request, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 500, null);

        bindHttpRequestDialog(dialogView, null, null);
    }

    public void showEditHttpRequestDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_http_request, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 500, null);

        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) btnConfirm.setText("保存");

        bindHttpRequestDialog(dialogView, operationId, operationObject);
    }

    private void bindHttpRequestDialog(View dialogView, String operationId, JSONObject operationObject) {
        EditText edtName          = dialogView.findViewById(R.id.edt_name);
        EditText edtUrl           = dialogView.findViewById(R.id.edt_url);
        android.widget.Spinner spinnerMethod = dialogView.findViewById(R.id.spinner_method);
        EditText edtHeaders       = dialogView.findViewById(R.id.edt_headers);
        View tvBodyLabel          = dialogView.findViewById(R.id.tv_body_label);
        EditText edtBody          = dialogView.findViewById(R.id.edt_body);
        EditText edtResponseVar   = dialogView.findViewById(R.id.edt_response_var);
        EditText edtStatusVar     = dialogView.findViewById(R.id.edt_status_var);
        EditText edtTimeoutMs     = dialogView.findViewById(R.id.edt_timeout_ms);
        AutoCompleteTextView edtNextOperation     = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallbackOperation = dialogView.findViewById(R.id.edt_fallback_operation);

        // 方法列表
        String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD"};
        android.widget.ArrayAdapter<String> methodAdapter = new android.widget.ArrayAdapter<>(
                host.getContext(), android.R.layout.simple_spinner_item, methods);
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMethod.setAdapter(methodAdapter);

        // 根据方法显示/隐藏请求体
        Runnable updateBodyVisibility = () -> {
            Object sel = spinnerMethod.getSelectedItem();
            String m = sel != null ? sel.toString() : "GET";
            boolean hasBody = !m.equals("GET") && !m.equals("HEAD");
            if (tvBodyLabel != null) tvBodyLabel.setVisibility(hasBody ? View.VISIBLE : View.GONE);
            if (edtBody != null)     edtBody.setVisibility(hasBody ? View.VISIBLE : View.GONE);
        };
        spinnerMethod.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateBodyVisibility.run();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // 预填充（编辑模式）
        if (operationObject != null) {
            try {
                edtName.setText(operationObject.optString("name", ""));
                JSONObject im = operationObject.optJSONObject("inputMap");
                if (im != null) {
                    edtUrl.setText(im.optString(MetaOperation.HTTP_URL, ""));
                    String savedMethod = im.optString(MetaOperation.HTTP_METHOD, "GET").toUpperCase();
                    for (int i = 0; i < methods.length; i++) {
                        if (methods[i].equals(savedMethod)) { spinnerMethod.setSelection(i); break; }
                    }
                    edtHeaders.setText(im.optString(MetaOperation.HTTP_HEADERS, ""));
                    edtBody.setText(im.optString(MetaOperation.HTTP_BODY, ""));
                    edtResponseVar.setText(im.optString(MetaOperation.HTTP_RESPONSE_VAR, ""));
                    edtStatusVar.setText(im.optString(MetaOperation.HTTP_STATUS_VAR, ""));
                    long timeout = im.optLong(MetaOperation.HTTP_TIMEOUT_MS, 0);
                    if (timeout > 0) edtTimeoutMs.setText(String.valueOf(timeout));
                    setOperationReferenceText(edtNextOperation,     im.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                    setOperationReferenceText(edtFallbackOperation, im.optString(MetaOperation.FALLBACKOPERATIONID, ""));
                }
            } catch (Exception e) {
                host.showToast("加载操作数据失败: " + e.getMessage());
            }
        }
        updateBodyVisibility.run();

        // 绑定下一节点自动补全
        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null)
                showOperationPickerForField("选择成功后节点", null, edtNextOperation);
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null)
                showOperationPickerForField("选择失败后节点", null, edtFallbackOperation);
        });
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));

        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> saveHttpRequestOperation(
                    dialogView, operationId, operationObject,
                    edtName, edtUrl, spinnerMethod, edtHeaders, edtBody,
                    edtResponseVar, edtStatusVar, edtTimeoutMs,
                    edtNextOperation, edtFallbackOperation));
        }
    }

    private void saveHttpRequestOperation(
            View dialogView,
            String operationId,
            JSONObject existingObject,
            EditText edtName,
            EditText edtUrl,
            android.widget.Spinner spinnerMethod,
            EditText edtHeaders,
            EditText edtBody,
            EditText edtResponseVar,
            EditText edtStatusVar,
            EditText edtTimeoutMs,
            AutoCompleteTextView edtNextOperation,
            AutoCompleteTextView edtFallbackOperation) {

        String name        = edtName.getText().toString().trim();
        String url         = edtUrl.getText().toString().trim();
        String method      = spinnerMethod.getSelectedItem() != null ? spinnerMethod.getSelectedItem().toString() : "GET";
        String headers     = edtHeaders.getText().toString();
        String body        = edtBody.isShown() ? edtBody.getText().toString() : "";
        String responseVar = edtResponseVar.getText().toString().trim();
        String statusVar   = edtStatusVar.getText().toString().trim();
        String timeoutStr  = edtTimeoutMs.getText().toString().trim();
        String nextOp      = safeText(edtNextOperation);
        String fallbackOp  = safeText(edtFallbackOperation);

        if (TextUtils.isEmpty(name))  { edtName.setError("请填写操作名称"); return; }
        if (TextUtils.isEmpty(url))   { edtUrl.setError("请填写请求 URL"); return; }

        try {
            JSONObject inputMap = new JSONObject();
            inputMap.put(MetaOperation.HTTP_URL,    url);
            inputMap.put(MetaOperation.HTTP_METHOD, method);
            if (!TextUtils.isEmpty(headers))     inputMap.put(MetaOperation.HTTP_HEADERS,      headers);
            if (!TextUtils.isEmpty(body))        inputMap.put(MetaOperation.HTTP_BODY,         body);
            if (!TextUtils.isEmpty(responseVar)) inputMap.put(MetaOperation.HTTP_RESPONSE_VAR, responseVar);
            if (!TextUtils.isEmpty(statusVar))   inputMap.put(MetaOperation.HTTP_STATUS_VAR,   statusVar);
            if (!TextUtils.isEmpty(timeoutStr)) {
                try { inputMap.put(MetaOperation.HTTP_TIMEOUT_MS, Long.parseLong(timeoutStr)); }
                catch (NumberFormatException ignored) {}
            }
            if (!TextUtils.isEmpty(nextOp))     inputMap.put(MetaOperation.NEXT_OPERATION_ID,    nextOp);
            if (!TextUtils.isEmpty(fallbackOp)) inputMap.put(MetaOperation.FALLBACKOPERATIONID, fallbackOp);

            boolean isEdit = operationId != null;
            if (isEdit) {
                // 编辑模式：更新现有操作
                JSONObject updated = new JSONObject();
                updated.put("id",           operationId);
                updated.put("name",         name);
                updated.put("type",         20);
                updated.put("responseType", 1);
                updated.put("inputMap",     inputMap);
                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updated.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) updateListener.onOperationUpdated();
                }
            } else {
                // 新增模式
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) operationObject.put("id", idGenerator.generateId());
                operationObject.put("name",         name);
                operationObject.put("type",         20);
                operationObject.put("responseType", 1);
                operationObject.put("inputMap",     inputMap);

                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(operationObject);
                crudHelper.writeOperationsArray(operations, "已添加 HTTP 请求节点", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) addListener.onOperationAdded();
                });
            }
        } catch (Exception e) {
            host.showToast("保存失败: " + e.getMessage());
        }
    }

    // ==================== 截图区域操作 ====================

    private static final String[] CROP_FORMAT_LABELS = {"JPEG（体积小，有损）", "PNG（无损，体积大）"};
    private static final String[] CROP_FORMAT_VALUES = {"jpeg", "png"};

    public void showAddCropRegionDialog() {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_crop_region, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 520, null);
        bindCropRegionDialog(dialogView, null, null);
    }

    public void showEditCropRegionDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_crop_region, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 520, null);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) btnConfirm.setText("保存");
        bindCropRegionDialog(dialogView, operationId, operationObject);
    }

    private void bindCropRegionDialog(View dialogView, String operationId, JSONObject operationObject) {
        EditText edtName                   = dialogView.findViewById(R.id.edt_name);
        EditText edtBbox                   = dialogView.findViewById(R.id.edt_bbox);
        TextView tvBboxStatus              = dialogView.findViewById(R.id.tv_bbox_status);
        android.widget.Spinner spinnerFmt  = dialogView.findViewById(R.id.spinner_format);
        View tvQualityLabel                = dialogView.findViewById(R.id.tv_quality_label);
        EditText edtQuality                = dialogView.findViewById(R.id.edt_quality);
        EditText edtResultVar              = dialogView.findViewById(R.id.edt_result_var);
        android.widget.CheckBox chkDataUri = dialogView.findViewById(R.id.chk_data_uri);
        EditText edtPreDelay               = dialogView.findViewById(R.id.edt_pre_delay);
        AutoCompleteTextView edtNext       = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallback   = dialogView.findViewById(R.id.edt_fallback_operation);
        View lyAdvancedToggle              = dialogView.findViewById(R.id.ly_advanced_toggle);
        View lyAdvancedPanel               = dialogView.findViewById(R.id.ly_advanced_panel);
        TextView tvAdvancedArrow           = dialogView.findViewById(R.id.tv_advanced_arrow);

        // 格式 Spinner
        android.widget.ArrayAdapter<String> fmtAdapter = new android.widget.ArrayAdapter<>(
                host.getContext(), android.R.layout.simple_spinner_item, CROP_FORMAT_LABELS);
        fmtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFmt.setAdapter(fmtAdapter);
        spinnerFmt.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                boolean jpeg = pos == 0;
                if (tvQualityLabel != null) tvQualityLabel.setVisibility(jpeg ? View.VISIBLE : View.GONE);
                if (edtQuality != null)     edtQuality.setVisibility(jpeg ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        // 高级折叠
        final boolean[] advExp = {false};
        if (lyAdvancedToggle != null) {
            lyAdvancedToggle.setOnClickListener(v -> {
                advExp[0] = !advExp[0];
                if (lyAdvancedPanel != null) lyAdvancedPanel.setVisibility(advExp[0] ? View.VISIBLE : View.GONE);
                if (tvAdvancedArrow != null) tvAdvancedArrow.setText(advExp[0] ? "▲" : "▼");
            });
        }

        // 预填充（编辑模式）
        if (operationObject != null) {
            try {
                edtName.setText(operationObject.optString("name", ""));
                JSONObject im = operationObject.optJSONObject("inputMap");
                if (im != null) {
                    org.json.JSONArray bboxArr = im.optJSONArray(MetaOperation.BBOX);
                    if (bboxArr != null && bboxArr.length() == 4) {
                        edtBbox.setText(bboxArr.getInt(0) + "," + bboxArr.getInt(1)
                                + "," + bboxArr.getInt(2) + "," + bboxArr.getInt(3));
                        if (tvBboxStatus != null) tvBboxStatus.setVisibility(View.VISIBLE);
                    }
                    String savedFmt = im.optString(MetaOperation.CROP_FORMAT, "jpeg");
                    for (int i = 0; i < CROP_FORMAT_VALUES.length; i++) {
                        if (CROP_FORMAT_VALUES[i].equals(savedFmt)) { spinnerFmt.setSelection(i); break; }
                    }
                    edtQuality.setText(String.valueOf(im.optInt(MetaOperation.CROP_QUALITY, 80)));
                    edtResultVar.setText(im.optString(MetaOperation.CROP_RESULT_VAR, ""));
                    chkDataUri.setChecked(im.optBoolean(MetaOperation.CROP_DATA_URI, false));
                    long pd = im.optLong(MetaOperation.NODE_PRE_DELAY_MS, 0);
                    if (pd > 0) edtPreDelay.setText(String.valueOf(pd));
                    setOperationReferenceText(edtNext,     im.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                    setOperationReferenceText(edtFallback, im.optString(MetaOperation.FALLBACKOPERATIONID, ""));
                }
            } catch (Exception e) {
                host.showToast("加载数据失败: " + e.getMessage());
            }
        }

        // 框选
        View btnPickBbox = dialogView.findViewById(R.id.btn_pick_bbox);
        if (btnPickBbox != null) {
            btnPickBbox.setOnClickListener(v -> {
                if (regionPickHelper != null) {
                    regionPickHelper.beginRegionPickFromDialog(dialogView, edtBbox, tvBboxStatus);
                    if (tvBboxStatus != null) tvBboxStatus.setVisibility(View.VISIBLE);
                }
            });
        }

        // 自动补全 + 选择器
        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) showOperationPickerForField("选择下一节点", null, edtNext);
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) showOperationPickerForField("选择失败节点", null, edtFallback);
        });
        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));

        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> saveCropRegionOperation(
                    dialogView, operationId, operationObject,
                    edtName, edtBbox, spinnerFmt, edtQuality,
                    edtResultVar, chkDataUri, edtPreDelay, edtNext, edtFallback));
        }
    }

    private void saveCropRegionOperation(
            View dialogView, String operationId, JSONObject existingObj,
            EditText edtName, EditText edtBbox,
            android.widget.Spinner spinnerFmt, EditText edtQuality,
            EditText edtResultVar, android.widget.CheckBox chkDataUri,
            EditText edtPreDelay,
            AutoCompleteTextView edtNext, AutoCompleteTextView edtFallback) {

        String name       = edtName.getText().toString().trim();
        String bboxStr    = edtBbox.getText().toString().trim();
        int    fmtPos     = spinnerFmt.getSelectedItemPosition();
        String format     = (fmtPos >= 0 && fmtPos < CROP_FORMAT_VALUES.length) ? CROP_FORMAT_VALUES[fmtPos] : "jpeg";
        String qualityStr = edtQuality.getText().toString().trim();
        String resultVar  = edtResultVar.getText().toString().trim();
        boolean dataUri   = chkDataUri.isChecked();
        String preDelayStr = edtPreDelay.getText().toString().trim();
        String nextOp     = safeText(edtNext);
        String fallbackOp = safeText(edtFallback);

        if (TextUtils.isEmpty(name))      { edtName.setError("请填写操作名称"); return; }
        if (TextUtils.isEmpty(bboxStr))   { edtBbox.setError("请填写截图区域"); return; }
        if (TextUtils.isEmpty(resultVar)) { edtResultVar.setError("请填写存入变量名"); return; }

        java.util.List<Integer> bbox = regionPickHelper != null
                ? regionPickHelper.parseBboxInput(bboxStr) : parseBboxFallback(bboxStr);
        if (bbox == null || bbox.size() != 4) { edtBbox.setError("格式错误，应为 x,y,宽,高"); return; }

        try {
            JSONObject inputMap = new JSONObject();
            org.json.JSONArray bboxArr = new org.json.JSONArray();
            for (int v : bbox) bboxArr.put(v);
            inputMap.put(MetaOperation.BBOX,            bboxArr);
            inputMap.put(MetaOperation.CROP_FORMAT,     format);
            inputMap.put(MetaOperation.CROP_RESULT_VAR, resultVar);
            if (dataUri) inputMap.put(MetaOperation.CROP_DATA_URI, true);
            if (!TextUtils.isEmpty(qualityStr)) {
                try { inputMap.put(MetaOperation.CROP_QUALITY, Integer.parseInt(qualityStr)); }
                catch (NumberFormatException ignored) {}
            }
            if (!TextUtils.isEmpty(preDelayStr)) {
                try { inputMap.put(MetaOperation.NODE_PRE_DELAY_MS, Long.parseLong(preDelayStr)); }
                catch (NumberFormatException ignored) {}
            }
            if (!TextUtils.isEmpty(nextOp))     inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
            if (!TextUtils.isEmpty(fallbackOp)) inputMap.put(MetaOperation.FALLBACKOPERATIONID, fallbackOp);

            boolean isEdit = operationId != null;
            if (isEdit) {
                JSONObject updated = new JSONObject();
                updated.put("id",           operationId);
                updated.put("name",         name);
                updated.put("type",         3);
                updated.put("responseType", 4);
                updated.put("inputMap",     inputMap);
                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updated.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) updateListener.onOperationUpdated();
                }
            } else {
                JSONObject opObj = new JSONObject();
                if (idGenerator != null) opObj.put("id", idGenerator.generateId());
                opObj.put("name",         name);
                opObj.put("type",         3);
                opObj.put("responseType", 4);
                opObj.put("inputMap",     inputMap);
                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(opObj);
                crudHelper.writeOperationsArray(operations, "已添加截图区域节点", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) addListener.onOperationAdded();
                });
            }
        } catch (Exception e) {
            host.showToast("保存失败: " + e.getMessage());
        }
    }

    private java.util.List<Integer> parseBboxFallback(String raw) {
        try {
            String[] parts = raw.split(",");
            if (parts.length != 4) return null;
            java.util.List<Integer> list = new java.util.ArrayList<>();
            for (String p : parts) list.add(Integer.parseInt(p.trim()));
            return list;
        } catch (Exception e) { return null; }
    }

    // ==================== 无障碍节点操作 ====================

    private static final String[] A11Y_FIND_MODE_LABELS = {
            "文字精确匹配", "文字包含", "文字开头匹配",
            "资源 ID (viewId)", "内容描述精确", "内容描述包含", "类名 (className)"
    };
    private static final String[] A11Y_FIND_MODE_VALUES = {
            "text", "textContains", "textStartsWith",
            "viewId", "contentDesc", "contentDescContains", "className"
    };

    private static final String[] A11Y_ACTION_LABELS = {
            "点击", "长按", "读取文字", "读取内容描述", "输入文字",
            "向上滚动", "向下滚动", "请求焦点", "仅检测是否存在"
    };
    private static final String[] A11Y_ACTION_VALUES = {
            "click", "longClick", "getText", "getDesc", "setText",
            "scrollUp", "scrollDown", "focus", "exists"
    };

    public void showAddAccessibilityNodeDialog() {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_accessibility_node, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 560, null);

        bindAccessibilityNodeDialog(dialogView, null, null);
    }

    public void showEditAccessibilityNodeDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_accessibility_node, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 560, null);

        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) btnConfirm.setText("保存");

        bindAccessibilityNodeDialog(dialogView, operationId, operationObject);
    }

    private void bindAccessibilityNodeDialog(View dialogView, String operationId, JSONObject operationObject) {
        EditText edtName              = dialogView.findViewById(R.id.edt_name);
        android.widget.Spinner spinnerFindMode = dialogView.findViewById(R.id.spinner_find_mode);
        EditText edtFindValue         = dialogView.findViewById(R.id.edt_find_value);
        EditText edtPackageFilter     = dialogView.findViewById(R.id.edt_package_filter);
        EditText edtMatchIndex        = dialogView.findViewById(R.id.edt_match_index);
        TextView tvScannedNodeSummary = dialogView.findViewById(R.id.tv_scanned_node_summary);
        EditText edtTimeoutMs         = dialogView.findViewById(R.id.edt_timeout_ms);
        View tvPollLabel              = dialogView.findViewById(R.id.tv_poll_label);
        EditText edtPollIntervalMs    = dialogView.findViewById(R.id.edt_poll_interval_ms);
        android.widget.CheckBox chkScrollIntoView = dialogView.findViewById(R.id.chk_scroll_into_view);
        android.widget.Spinner spinnerAction      = dialogView.findViewById(R.id.spinner_action);
        View tvActionTextLabel        = dialogView.findViewById(R.id.tv_action_text_label);
        EditText edtActionText        = dialogView.findViewById(R.id.edt_action_text);
        View tvResultVarLabel         = dialogView.findViewById(R.id.tv_result_var_label);
        EditText edtResultVar         = dialogView.findViewById(R.id.edt_result_var);
        AutoCompleteTextView edtNextOperation     = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallbackOperation = dialogView.findViewById(R.id.edt_fallback_operation);

        // 绑定查找方式 Spinner
        android.widget.ArrayAdapter<String> findModeAdapter = new android.widget.ArrayAdapter<>(
                host.getContext(), android.R.layout.simple_spinner_item, A11Y_FIND_MODE_LABELS);
        findModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFindMode.setAdapter(findModeAdapter);

        // 绑定动作 Spinner
        android.widget.ArrayAdapter<String> actionAdapter = new android.widget.ArrayAdapter<>(
                host.getContext(), android.R.layout.simple_spinner_item, A11Y_ACTION_LABELS);
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAction.setAdapter(actionAdapter);

        // 根据动作显示/隐藏条件字段
        Runnable updateActionFields = () -> {
            int pos = spinnerAction.getSelectedItemPosition();
            if (pos < 0 || pos >= A11Y_ACTION_VALUES.length) return;
            String act = A11Y_ACTION_VALUES[pos];
            boolean isSetText = "setText".equals(act);
            boolean isReadResult = "getText".equals(act) || "getDesc".equals(act) || "exists".equals(act);
            if (tvActionTextLabel != null) tvActionTextLabel.setVisibility(isSetText ? View.VISIBLE : View.GONE);
            if (edtActionText != null)     edtActionText.setVisibility(isSetText ? View.VISIBLE : View.GONE);
            if (tvResultVarLabel != null)  tvResultVarLabel.setVisibility(isReadResult ? View.VISIBLE : View.GONE);
            if (edtResultVar != null)      edtResultVar.setVisibility(isReadResult ? View.VISIBLE : View.GONE);
        };
        spinnerAction.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                updateActionFields.run();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        // 超时 > 0 时才显示轮询间隔
        if (edtTimeoutMs != null) {
            edtTimeoutMs.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    long v = 0;
                    try { v = Long.parseLong(s.toString().trim()); } catch (Exception ignored) {}
                    int vis = v > 0 ? View.VISIBLE : View.GONE;
                    if (tvPollLabel != null) tvPollLabel.setVisibility(vis);
                    if (edtPollIntervalMs != null) edtPollIntervalMs.setVisibility(vis);
                }
            });
        }

        // 扫描界面节点树按钮
        View btnScan = dialogView.findViewById(R.id.btn_scan_nodes);
        if (btnScan != null) {
            final android.widget.Spinner finalSpinnerFindMode = spinnerFindMode;
            final EditText finalEdtFindValue = edtFindValue;
            final EditText finalEdtPackageFilter = edtPackageFilter;
            final EditText finalEdtMatchIndex = edtMatchIndex;
            final TextView finalScannedNodeSummary = tvScannedNodeSummary;
            btnScan.setOnClickListener(v -> {
                AccessibilityNodeScannerOverlay scanner = new AccessibilityNodeScannerOverlay(
                        host.getContext(), wm,
                        (findMode, findValue, matchIndex, node) -> {
                            // 回填查找方式
                            for (int i = 0; i < A11Y_FIND_MODE_VALUES.length; i++) {
                                if (A11Y_FIND_MODE_VALUES[i].equals(findMode)) {
                                    finalSpinnerFindMode.setSelection(i);
                                    break;
                                }
                            }
                            // 回填查找值
                            finalEdtFindValue.setText(findValue);
                            // 同一个选择器命中多个节点时，自动填入当前节点序号
                            finalEdtMatchIndex.setText(String.valueOf(Math.max(0, matchIndex)));
                            // 回填包名（如有）
                            if (!TextUtils.isEmpty(node.packageName)
                                    && !node.packageName.equals(host.getContext().getPackageName())) {
                                finalEdtPackageFilter.setText(node.packageName);
                            }
                            if (finalScannedNodeSummary != null) {
                                finalScannedNodeSummary.setText(
                                        "已选择: " + node.shortClassName
                                                + " | " + node.displayName()
                                                + "\n" + "selector = { mode: \"" + findMode
                                                + "\", value: \"" + findValue
                                                + "\", index: " + Math.max(0, matchIndex) + " }"
                                                + "\n" + "bounds = " + node.boundsScreen.toShortString());
                            }
                        });
                scanner.show(dialogView, host.getProjectPanelView());
            });
        }

        // 预填充（编辑模式）
        if (operationObject != null) {
            try {
                edtName.setText(operationObject.optString("name", ""));
                JSONObject im = operationObject.optJSONObject("inputMap");
                if (im != null) {
                    String savedFindMode = im.optString(MetaOperation.A11Y_FIND_MODE, "text");
                    for (int i = 0; i < A11Y_FIND_MODE_VALUES.length; i++) {
                        if (A11Y_FIND_MODE_VALUES[i].equals(savedFindMode)) {
                            spinnerFindMode.setSelection(i); break;
                        }
                    }
                    edtFindValue.setText(im.optString(MetaOperation.A11Y_FIND_VALUE, ""));
                    edtPackageFilter.setText(im.optString(MetaOperation.A11Y_PACKAGE_FILTER, ""));
                    long matchIndex = im.optLong(MetaOperation.A11Y_MATCH_INDEX, 0);
                    edtMatchIndex.setText(String.valueOf(matchIndex));
                    long timeout = im.optLong(MetaOperation.A11Y_TIMEOUT_MS, 0);
                    edtTimeoutMs.setText(String.valueOf(timeout));
                    long poll = im.optLong(MetaOperation.A11Y_POLL_INTERVAL_MS, 200);
                    edtPollIntervalMs.setText(String.valueOf(poll));
                    chkScrollIntoView.setChecked(im.optBoolean(MetaOperation.A11Y_SCROLL_INTO_VIEW, false));

                    String savedAction = im.optString(MetaOperation.A11Y_ACTION, "click");
                    for (int i = 0; i < A11Y_ACTION_VALUES.length; i++) {
                        if (A11Y_ACTION_VALUES[i].equals(savedAction)) {
                            spinnerAction.setSelection(i); break;
                        }
                    }
                    edtActionText.setText(im.optString(MetaOperation.A11Y_ACTION_TEXT, ""));
                    edtResultVar.setText(im.optString(MetaOperation.A11Y_RESULT_VAR, ""));
                    setOperationReferenceText(edtNextOperation,     im.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                    setOperationReferenceText(edtFallbackOperation, im.optString(MetaOperation.FALLBACKOPERATIONID, ""));
                }
            } catch (Exception e) {
                host.showToast("加载操作数据失败: " + e.getMessage());
            }
        }
        updateActionFields.run();

        // 绑定下一节点自动补全
        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null)
                showOperationPickerForField("选择成功后节点", null, edtNextOperation);
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null)
                showOperationPickerForField("选择失败后节点", null, edtFallbackOperation);
        });

        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> saveAccessibilityNodeOperation(
                    dialogView, operationId, operationObject,
                    edtName, spinnerFindMode, edtFindValue, edtPackageFilter,
                    edtMatchIndex, edtTimeoutMs, edtPollIntervalMs,
                    chkScrollIntoView, spinnerAction, edtActionText, edtResultVar,
                    edtNextOperation, edtFallbackOperation));
        }
    }

    private void saveAccessibilityNodeOperation(
            View dialogView,
            String operationId,
            JSONObject existingObject,
            EditText edtName,
            android.widget.Spinner spinnerFindMode,
            EditText edtFindValue,
            EditText edtPackageFilter,
            EditText edtMatchIndex,
            EditText edtTimeoutMs,
            EditText edtPollIntervalMs,
            android.widget.CheckBox chkScrollIntoView,
            android.widget.Spinner spinnerAction,
            EditText edtActionText,
            EditText edtResultVar,
            AutoCompleteTextView edtNextOperation,
            AutoCompleteTextView edtFallbackOperation) {

        String name          = edtName.getText().toString().trim();
        int    findModePos   = spinnerFindMode.getSelectedItemPosition();
        String findMode      = (findModePos >= 0 && findModePos < A11Y_FIND_MODE_VALUES.length)
                               ? A11Y_FIND_MODE_VALUES[findModePos] : "text";
        String findValue     = edtFindValue.getText().toString().trim();
        String pkgFilter     = edtPackageFilter.getText().toString().trim();
        String matchIndexStr = edtMatchIndex.getText().toString().trim();
        String timeoutStr    = edtTimeoutMs.getText().toString().trim();
        String pollStr       = edtPollIntervalMs.getText().toString().trim();
        boolean scrollIntoView = chkScrollIntoView.isChecked();
        int    actionPos     = spinnerAction.getSelectedItemPosition();
        String action        = (actionPos >= 0 && actionPos < A11Y_ACTION_VALUES.length)
                               ? A11Y_ACTION_VALUES[actionPos] : "click";
        String actionText    = edtActionText.isShown() ? edtActionText.getText().toString() : "";
        String resultVar     = edtResultVar.isShown() ? edtResultVar.getText().toString().trim() : "";
        String nextOp        = safeText(edtNextOperation);
        String fallbackOp    = safeText(edtFallbackOperation);

        if (TextUtils.isEmpty(name))      { edtName.setError("请填写操作名称"); return; }
        if (TextUtils.isEmpty(findValue) && !"className".equals(findMode)) {
            edtFindValue.setError("请填写查找值"); return;
        }

        try {
            JSONObject inputMap = new JSONObject();
            inputMap.put(MetaOperation.A11Y_FIND_MODE,  findMode);
            inputMap.put(MetaOperation.A11Y_FIND_VALUE, findValue);
            inputMap.put(MetaOperation.A11Y_ACTION,     action);

            if (!TextUtils.isEmpty(pkgFilter))  inputMap.put(MetaOperation.A11Y_PACKAGE_FILTER, pkgFilter);
            if (scrollIntoView)                 inputMap.put(MetaOperation.A11Y_SCROLL_INTO_VIEW, true);
            if (!TextUtils.isEmpty(actionText)) inputMap.put(MetaOperation.A11Y_ACTION_TEXT, actionText);
            if (!TextUtils.isEmpty(resultVar))  inputMap.put(MetaOperation.A11Y_RESULT_VAR, resultVar);

            if (!TextUtils.isEmpty(matchIndexStr)) {
                try { inputMap.put(MetaOperation.A11Y_MATCH_INDEX, Long.parseLong(matchIndexStr)); }
                catch (NumberFormatException ignored) {}
            }
            if (!TextUtils.isEmpty(timeoutStr)) {
                try { inputMap.put(MetaOperation.A11Y_TIMEOUT_MS, Long.parseLong(timeoutStr)); }
                catch (NumberFormatException ignored) {}
            }
            if (!TextUtils.isEmpty(pollStr)) {
                try { inputMap.put(MetaOperation.A11Y_POLL_INTERVAL_MS, Long.parseLong(pollStr)); }
                catch (NumberFormatException ignored) {}
            }

            if (!TextUtils.isEmpty(nextOp))     inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
            if (!TextUtils.isEmpty(fallbackOp)) inputMap.put(MetaOperation.FALLBACKOPERATIONID, fallbackOp);

            boolean isEdit = operationId != null;
            if (isEdit) {
                JSONObject updated = new JSONObject();
                updated.put("id",           operationId);
                updated.put("name",         name);
                updated.put("type",         24);
                updated.put("responseType", 1);
                updated.put("inputMap",     inputMap);
                if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updated.toString(2))) {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (updateListener != null) updateListener.onOperationUpdated();
                }
            } else {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) operationObject.put("id", idGenerator.generateId());
                operationObject.put("name",         name);
                operationObject.put("type",         24);
                operationObject.put("responseType", 1);
                operationObject.put("inputMap",     inputMap);

                JSONArray operations = crudHelper.readOperationsArray();
                operations.put(operationObject);
                crudHelper.writeOperationsArray(operations, "已添加无障碍节点", () -> {
                    dialogHelpers.safeRemoveView(dialogView);
                    if (addListener != null) addListener.onOperationAdded();
                });
            }
        } catch (Exception e) {
            host.showToast("保存失败: " + e.getMessage());
        }
    }

    // ==================== 播放音频操作 ====================

    public void showAddPlayAudioDialog() {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_play_audio, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 340, 0.82f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 430, null);
        bindPlayAudioDialog(dialogView, null, null);
    }

    public void showEditPlayAudioDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_play_audio, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 340, 0.82f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 430, null);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) btnConfirm.setText("保存");
        bindPlayAudioDialog(dialogView, operationId, operationObject);
    }

    private void bindPlayAudioDialog(View dialogView, String operationId, JSONObject operationObject) {
        android.widget.EditText edtName      = dialogView.findViewById(R.id.edt_name);
        android.widget.EditText edtAudioPath = dialogView.findViewById(R.id.edt_audio_path);
        android.widget.CheckBox chkWait      = dialogView.findViewById(R.id.chk_wait_complete);
        android.widget.AutoCompleteTextView edtNext = dialogView.findViewById(R.id.edt_next_operation);

        if (operationObject != null) {
            try {
                edtName.setText(operationObject.optString("name", ""));
                JSONObject im = operationObject.optJSONObject("inputMap");
                if (im != null) {
                    edtAudioPath.setText(im.optString(MetaOperation.AUDIO_FILE_PATH, ""));
                    if (chkWait != null) chkWait.setChecked(im.optBoolean(MetaOperation.AUDIO_WAIT_COMPLETE, false));
                    setOperationReferenceText(edtNext, im.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                }
            } catch (Exception e) {
                host.showToast("加载数据失败: " + e.getMessage());
            }
        }

        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null)
                showOperationPickerForField("选择下一节点", operationId, edtNext);
        });

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name      = edtName.getText().toString().trim();
            String audioPath = edtAudioPath.getText().toString().trim();
            String nextOp    = safeText(edtNext);
            boolean waitComplete = chkWait != null && chkWait.isChecked();

            if (TextUtils.isEmpty(name))      { edtName.setError("请填写操作名称"); return; }
            if (TextUtils.isEmpty(audioPath)) { edtAudioPath.setError("请填写音频文件路径"); return; }

            try {
                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.AUDIO_FILE_PATH, audioPath);
                if (waitComplete) inputMap.put(MetaOperation.AUDIO_WAIT_COMPLETE, true);
                if (!TextUtils.isEmpty(nextOp)) inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);

                boolean isEdit = operationId != null;
                if (isEdit) {
                    JSONObject updated = new JSONObject();
                    updated.put("id", operationId);
                    updated.put("name", name);
                    updated.put("type", 28);
                    updated.put("responseType", 1);
                    updated.put("inputMap", inputMap);
                    if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updated.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) updateListener.onOperationUpdated();
                    }
                } else {
                    JSONObject opObj = new JSONObject();
                    if (idGenerator != null) opObj.put("id", idGenerator.generateId());
                    opObj.put("name", name);
                    opObj.put("type", 28);
                    opObj.put("responseType", 1);
                    opObj.put("inputMap", inputMap);
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(opObj);
                    crudHelper.writeOperationsArray(operations, "已添加播放音频节点", () -> {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (addListener != null) addListener.onOperationAdded();
                    });
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    // ==================== 日志输出操作 ====================

    private static final String[] LOG_LEVEL_LABELS = {
            "INFO", "WARN", "ERROR", "DEBUG"
    };

    public void showAddLogOutputDialog() {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_log_output, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(350, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 350, 0.78f, 0.9f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 350, 430, null);
        bindLogOutputDialog(dialogView, null, null);
    }

    public void showEditLogOutputDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_log_output, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(350, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 350, 0.78f, 0.9f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 350, 430, null);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) btnConfirm.setText("保存");
        bindLogOutputDialog(dialogView, operationId, operationObject);
    }

    private void bindLogOutputDialog(View dialogView, String operationId, JSONObject operationObject) {
        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtMessage = dialogView.findViewById(R.id.edt_log_message);
        android.widget.Spinner spinnerLevel = dialogView.findViewById(R.id.spinner_log_level);
        AutoCompleteTextView edtNext = dialogView.findViewById(R.id.edt_next_operation);

        android.widget.ArrayAdapter<String> levelAdapter = new android.widget.ArrayAdapter<>(
                host.getContext(), android.R.layout.simple_spinner_item, LOG_LEVEL_LABELS);
        levelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLevel.setAdapter(levelAdapter);

        if (operationObject != null) {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject im = operationObject.optJSONObject("inputMap");
            if (im != null) {
                edtMessage.setText(im.optString(MetaOperation.LOG_MESSAGE, ""));
                String level = im.optString(MetaOperation.LOG_LEVEL, "INFO");
                for (int i = 0; i < LOG_LEVEL_LABELS.length; i++) {
                    if (LOG_LEVEL_LABELS[i].equalsIgnoreCase(level)) {
                        spinnerLevel.setSelection(i, false);
                        break;
                    }
                }
                setOperationReferenceText(edtNext, im.optString(MetaOperation.NEXT_OPERATION_ID, ""));
            }
        } else {
            edtName.setText("日志输出");
            spinnerLevel.setSelection(0, false);
        }

        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", operationId, edtNext);
            }
        });

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText() == null ? "" : edtName.getText().toString().trim();
            String message = edtMessage.getText() == null ? "" : edtMessage.getText().toString();
            String nextOp = safeText(edtNext);
            int levelPos = spinnerLevel.getSelectedItemPosition();
            String level = levelPos >= 0 && levelPos < LOG_LEVEL_LABELS.length
                    ? LOG_LEVEL_LABELS[levelPos]
                    : "INFO";

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }

            try {
                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.LOG_MESSAGE, message);
                inputMap.put(MetaOperation.LOG_LEVEL, level);
                if (!TextUtils.isEmpty(nextOp)) inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);

                if (operationId != null) {
                    JSONObject updated = new JSONObject();
                    updated.put("id", operationId);
                    updated.put("name", name);
                    updated.put("type", 30);
                    updated.put("responseType", 1);
                    updated.put("inputMap", inputMap);
                    if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updated.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) updateListener.onOperationUpdated();
                    }
                } else {
                    JSONObject opObj = new JSONObject();
                    if (idGenerator != null) opObj.put("id", idGenerator.generateId());
                    opObj.put("name", name);
                    opObj.put("type", 30);
                    opObj.put("responseType", 1);
                    opObj.put("inputMap", inputMap);
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(opObj);
                    crudHelper.writeOperationsArray(operations, "已添加日志输出节点", () -> {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (addListener != null) addListener.onOperationAdded();
                    });
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    // ==================== 修改系统参数操作 ====================

    private static final String[] SYS_PARAM_KEY_LABELS = {
            "全局采集倍率", "倒计时颜色", "手势颜色", "运行日志输出"
    };
    private static final String[] SYS_PARAM_KEY_VALUES = {
            MetaOperation.SYS_PARAM_CAPTURE_SCALE,
            MetaOperation.SYS_PARAM_COUNTDOWN_COLOR,
            MetaOperation.SYS_PARAM_GESTURE_COLOR,
            MetaOperation.SYS_PARAM_RUNTIME_LOG_ENABLED
    };

    private static final String[] SCALE_LABELS = {
            "0.25 (25%)", "0.375 (37.5%)", "0.4 (40%)", "0.5 (50%)",
            "0.625 (62.5%)", "0.75 (75%)", "0.875 (87.5%)", "1.0 (100% 默认)"
    };
    private static final String[] SCALE_VALUES = {
            "0.25", "0.375", "0.4", "0.5", "0.625", "0.75", "0.875", "1.0"
    };

    private static final String[] COUNTDOWN_COLOR_LABELS = {
            "红粉 (默认)", "蓝色", "绿色", "黄色", "橙色", "白色", "青色"
    };
    private static final String[] COUNTDOWN_COLOR_VALUES = {
            "FFFFB3B3", "FF99BBFF", "FF99FFB3", "FFFFEEAA", "FFFFCC88", "FFFFFFFF", "FF80FFFF"
    };

    private static final String[] GESTURE_COLOR_LABELS = {
            "红色 (默认)", "蓝色", "绿色", "黄色", "橙色", "白色", "青色"
    };
    private static final String[] GESTURE_COLOR_VALUES = {
            "AAFF0000", "AA0044FF", "AA00CC44", "AAFFCC00", "AAFF6600", "AAFFFFFF", "AA00CCFF"
    };

    private static final String[] BOOLEAN_LABELS = {
            "开启", "关闭"
    };
    private static final String[] BOOLEAN_VALUES = {
            "true", "false"
    };

    public void showAddSetSystemParamDialog() {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_set_system_param, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 340, 0.82f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 430, null);
        bindSetSystemParamDialog(dialogView, null, null);
    }

    public void showEditSetSystemParamDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_set_system_param, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 340, 0.82f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 430, null);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) btnConfirm.setText("保存");
        bindSetSystemParamDialog(dialogView, operationId, operationObject);
    }

    private void bindSetSystemParamDialog(View dialogView, String operationId, JSONObject operationObject) {
        android.widget.EditText edtName            = dialogView.findViewById(R.id.edt_name);
        android.widget.Spinner  spinnerParamKey    = dialogView.findViewById(R.id.spinner_param_key);
        android.widget.Spinner  spinnerParamValue  = dialogView.findViewById(R.id.spinner_param_value);
        android.widget.AutoCompleteTextView edtNext = dialogView.findViewById(R.id.edt_next_operation);

        // 参数类型 Spinner
        android.widget.ArrayAdapter<String> keyAdapter = new android.widget.ArrayAdapter<>(
                host.getContext(), android.R.layout.simple_spinner_item, SYS_PARAM_KEY_LABELS);
        keyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerParamKey.setAdapter(keyAdapter);

        int initialKeyPos = 0;
        String initialValue = currentSystemParamValue(0);

        // 预填充（编辑模式）
        if (operationObject != null) {
            try {
                edtName.setText(operationObject.optString("name", ""));
                JSONObject im = operationObject.optJSONObject("inputMap");
                if (im != null) {
                    String savedKey = im.optString(MetaOperation.SYS_PARAM_KEY, "");
                    String savedValue = im.optString(MetaOperation.SYS_PARAM_VALUE, "");
                    for (int i = 0; i < SYS_PARAM_KEY_VALUES.length; i++) {
                        if (SYS_PARAM_KEY_VALUES[i].equals(savedKey)) {
                            initialKeyPos = i;
                            initialValue = savedValue;
                            break;
                        }
                    }
                    setOperationReferenceText(edtNext, im.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                }
            } catch (Exception e) {
                host.showToast("加载数据失败: " + e.getMessage());
            }
        }

        spinnerParamKey.setSelection(initialKeyPos, false);
        updateSystemParamValueSpinner(spinnerParamValue, initialKeyPos, initialValue);
        final int[] lastParamKeyPos = {initialKeyPos};
        final boolean[] paramKeySelectionReady = {false};
        spinnerParamKey.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (!paramKeySelectionReady[0]) {
                    lastParamKeyPos[0] = pos;
                    return;
                }
                if (pos == lastParamKeyPos[0]) {
                    return;
                }
                lastParamKeyPos[0] = pos;
                updateSystemParamValueSpinner(spinnerParamValue, pos, currentSystemParamValue(pos));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        spinnerParamKey.post(() -> paramKeySelectionReady[0] = true);

        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null)
                showOperationPickerForField("选择下一节点", operationId, edtNext);
        });

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name   = edtName.getText().toString().trim();
            String nextOp = safeText(edtNext);

            if (TextUtils.isEmpty(name)) { edtName.setError("请填写操作名称"); return; }

            int keyPos   = spinnerParamKey.getSelectedItemPosition();
            int valuePos = spinnerParamValue.getSelectedItemPosition();
            if (keyPos < 0 || keyPos >= SYS_PARAM_KEY_VALUES.length) return;

            String paramKey = SYS_PARAM_KEY_VALUES[keyPos];
            String[] vals = getSpinnerValueOptions(spinnerParamValue, keyPos);
            if (valuePos < 0 || valuePos >= vals.length) return;
            String paramValue = vals[valuePos];

            try {
                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.SYS_PARAM_KEY, paramKey);
                inputMap.put(MetaOperation.SYS_PARAM_VALUE, paramValue);
                if (!TextUtils.isEmpty(nextOp)) inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);

                boolean isEdit = operationId != null;
                if (isEdit) {
                    JSONObject updated = new JSONObject();
                    updated.put("id", operationId);
                    updated.put("name", name);
                    updated.put("type", 29);
                    updated.put("responseType", 1);
                    updated.put("inputMap", inputMap);
                    if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updated.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) updateListener.onOperationUpdated();
                    }
                } else {
                    JSONObject opObj = new JSONObject();
                    if (idGenerator != null) opObj.put("id", idGenerator.generateId());
                    opObj.put("name", name);
                    opObj.put("type", 29);
                    opObj.put("responseType", 1);
                    opObj.put("inputMap", inputMap);
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(opObj);
                    crudHelper.writeOperationsArray(operations, "已添加修改系统参数节点", () -> {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (addListener != null) addListener.onOperationAdded();
                    });
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    private void updateSystemParamValueSpinner(android.widget.Spinner spinnerParamValue,
                                               int keyPos,
                                               String preferredValue) {
        String[] baseLabels = systemParamValueLabels(keyPos);
        String[] baseValues = systemParamValueValues(keyPos);
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (int i = 0; i < baseValues.length; i++) {
            labels.add(baseLabels[i]);
            values.add(baseValues[i]);
        }

        int selected = findSystemParamValueIndex(values, keyPos, preferredValue);
        if (selected < 0 && !TextUtils.isEmpty(preferredValue)) {
            String normalized = normalizeSystemParamValue(keyPos, preferredValue);
            if (!TextUtils.isEmpty(normalized)) {
                labels.add(0, systemParamDynamicLabel(keyPos, normalized));
                values.add(0, normalized);
                selected = 0;
            }
        }
        if (selected < 0) {
            selected = Math.min(defaultSystemParamValueIndex(keyPos), Math.max(0, values.size() - 1));
        }

        android.widget.ArrayAdapter<String> valueAdapter = new android.widget.ArrayAdapter<>(
                host.getContext(), android.R.layout.simple_spinner_item, labels);
        valueAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerParamValue.setAdapter(valueAdapter);
        spinnerParamValue.setTag(values.toArray(new String[0]));
        spinnerParamValue.setSelection(selected, false);
    }

    private String[] getSpinnerValueOptions(android.widget.Spinner spinnerParamValue, int keyPos) {
        Object tag = spinnerParamValue == null ? null : spinnerParamValue.getTag();
        if (tag instanceof String[]) {
            return (String[]) tag;
        }
        return systemParamValueValues(keyPos);
    }

    private String[] systemParamValueLabels(int keyPos) {
        if (keyPos == 0) return SCALE_LABELS;
        if (keyPos == 1) return COUNTDOWN_COLOR_LABELS;
        if (keyPos == 2) return GESTURE_COLOR_LABELS;
        return BOOLEAN_LABELS;
    }

    private String[] systemParamValueValues(int keyPos) {
        if (keyPos == 0) return SCALE_VALUES;
        if (keyPos == 1) return COUNTDOWN_COLOR_VALUES;
        if (keyPos == 2) return GESTURE_COLOR_VALUES;
        return BOOLEAN_VALUES;
    }

    private int defaultSystemParamValueIndex(int keyPos) {
        return keyPos == 0 ? findSystemParamValueIndex(
                new java.util.ArrayList<>(java.util.Arrays.asList(SCALE_VALUES)),
                keyPos,
                trimSystemParamFloat(SystemRuntimeConfig.DEFAULT_CAPTURE_SCALE)) : 0;
    }

    private String currentSystemParamValue(int keyPos) {
        if (keyPos == 0) {
            return trimSystemParamFloat(SystemRuntimeConfig.load(host.getContext()).captureScale);
        }
        if (keyPos == 1) {
            return colorToSystemParamValue(RuntimeDisplayConfig.COUNTDOWN_FILL_COLOR);
        }
        if (keyPos == 2) {
            return colorToSystemParamValue(RuntimeDisplayConfig.GESTURE_STROKE_COLOR);
        }
        return SystemRuntimeConfig.load(host.getContext()).runtimeLogEnabled ? "true" : "false";
    }

    private int findSystemParamValueIndex(java.util.List<String> values, int keyPos, String preferredValue) {
        if (values == null || TextUtils.isEmpty(preferredValue)) {
            return -1;
        }
        String normalized = normalizeSystemParamValue(keyPos, preferredValue);
        for (int i = 0; i < values.size(); i++) {
            if (systemParamValuesEqual(keyPos, values.get(i), normalized)) {
                return i;
            }
        }
        return -1;
    }

    private boolean systemParamValuesEqual(int keyPos, String left, String right) {
        if (keyPos == 0) {
            try {
                return Math.abs(Float.parseFloat(left.trim()) - Float.parseFloat(right.trim())) < 0.0001f;
            } catch (Exception ignored) {
                return TextUtils.equals(left, right);
            }
        }
        if (keyPos == 3) {
            return TextUtils.equals(normalizeSystemBooleanValue(left), normalizeSystemBooleanValue(right));
        }
        return TextUtils.equals(normalizeSystemColorValue(left), normalizeSystemColorValue(right));
    }

    private String normalizeSystemParamValue(int keyPos, String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        if (keyPos == 0) {
            try {
                float value = Float.parseFloat(raw.trim());
                value = Math.max(0.25f, Math.min(1.0f, value));
                return trimSystemParamFloat(value);
            } catch (Exception ignored) {
                return "";
            }
        }
        if (keyPos == 3) {
            return normalizeSystemBooleanValue(raw);
        }
        return normalizeSystemColorValue(raw);
    }

    private String systemParamDynamicLabel(int keyPos, String value) {
        if (keyPos == 0) {
            try {
                float scale = Float.parseFloat(value);
                return value + " (" + Math.round(scale * 100f) + "%)";
            } catch (Exception ignored) {
                return value;
            }
        }
        if (keyPos == 3) {
            return "true".equals(normalizeSystemBooleanValue(value)) ? "开启" : "关闭";
        }
        return "#" + value;
    }

    private String trimSystemParamFloat(float value) {
        if (Math.abs(value - Math.round(value)) < 0.0001f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.3f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private String colorToSystemParamValue(int color) {
        return String.format(Locale.US, "%08X", color);
    }

    private String normalizeSystemColorValue(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().replace("#", "").toUpperCase(Locale.US);
        if (value.length() == 6) {
            value = "FF" + value;
        }
        return value;
    }

    private String normalizeSystemBooleanValue(String raw) {
        if (raw == null) {
            return "false";
        }
        String value = raw.trim();
        return ("true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "on".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value)
                || "enable".equalsIgnoreCase(value)) ? "true" : "false";
    }

    // ==================== 修改屏幕亮度操作 ====================

    public void showAddSetBrightnessDialog() {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_set_brightness, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 340, 0.82f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 430, null);
        bindSetBrightnessDialog(dialogView, null, null);
    }

    public void showEditSetBrightnessDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_add_set_brightness, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 340, 0.82f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 340, 430, null);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        if (btnConfirm != null) btnConfirm.setText("保存");
        bindSetBrightnessDialog(dialogView, operationId, operationObject);
    }

    private void bindSetBrightnessDialog(View dialogView, String operationId, JSONObject operationObject) {
        android.widget.EditText edtName        = dialogView.findViewById(R.id.edt_name);
        android.widget.SeekBar  seekBrightness = dialogView.findViewById(R.id.seek_brightness);
        android.widget.TextView tvValue        = dialogView.findViewById(R.id.tv_brightness_value);
        android.widget.AutoCompleteTextView edtNext = dialogView.findViewById(R.id.edt_next_operation);

        int initialPercent = MetaOperation.DEFAULT_RESTORE_BRIGHTNESS_PERCENT;

        if (operationObject != null) {
            try {
                edtName.setText(operationObject.optString("name", ""));
                JSONObject im = operationObject.optJSONObject("inputMap");
                if (im != null) {
                    initialPercent = im.optInt(MetaOperation.BRIGHTNESS_PERCENT, initialPercent);
                    setOperationReferenceText(edtNext, im.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                }
            } catch (Exception e) {
                host.showToast("加载数据失败: " + e.getMessage());
            }
        } else {
            edtName.setText("修改屏幕亮度");
        }

        initialPercent = Math.max(1, Math.min(100, initialPercent));
        seekBrightness.setProgress(initialPercent - 1);
        tvValue.setText(initialPercent + "%");
        seekBrightness.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int progress, boolean fromUser) {
                tvValue.setText((progress + 1) + "%");
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });

        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null)
                showOperationPickerForField("选择下一节点", operationId, edtNext);
        });

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name   = edtName.getText().toString().trim();
            String nextOp = safeText(edtNext);
            int percent   = seekBrightness.getProgress() + 1;

            if (TextUtils.isEmpty(name)) { edtName.setError("请填写操作名称"); return; }

            try {
                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.BRIGHTNESS_PERCENT, percent);
                if (!TextUtils.isEmpty(nextOp)) inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);

                boolean isEdit = operationId != null;
                if (isEdit) {
                    JSONObject updated = new JSONObject();
                    updated.put("id", operationId);
                    updated.put("name", name);
                    updated.put("type", 31);
                    updated.put("responseType", 1);
                    updated.put("inputMap", inputMap);
                    if (operationUpdater != null && operationUpdater.saveOperationJson(operationId, updated.toString(2))) {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (updateListener != null) updateListener.onOperationUpdated();
                    }
                } else {
                    JSONObject opObj = new JSONObject();
                    if (idGenerator != null) opObj.put("id", idGenerator.generateId());
                    opObj.put("name", name);
                    opObj.put("type", 31);
                    opObj.put("responseType", 1);
                    opObj.put("inputMap", inputMap);
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(opObj);
                    crudHelper.writeOperationsArray(operations, "已添加修改屏幕亮度节点", () -> {
                        dialogHelpers.safeRemoveView(dialogView);
                        if (addListener != null) addListener.onOperationAdded();
                    });
                }
            } catch (Exception e) {
                host.showToast("保存失败: " + e.getMessage());
            }
        });
    }

    private org.json.JSONArray collectSwitchCases(LinearLayout container) throws org.json.JSONException {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            EditText edtValue = row.findViewById(R.id.edt_case_value);
            AutoCompleteTextView edtNext = row.findViewById(R.id.edt_case_next);
            if (edtValue == null || edtNext == null) continue;
            String val = edtValue.getText().toString().trim();
            String nxt = safeText(edtNext);
            if (TextUtils.isEmpty(val) && TextUtils.isEmpty(nxt)) continue;
            JSONObject rule = new JSONObject();
            rule.put("value", val);
            rule.put("nextOperationId", nxt);
            arr.put(rule);
        }
        return arr;
    }
}
