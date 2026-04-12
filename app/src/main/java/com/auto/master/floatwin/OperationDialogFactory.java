package com.auto.master.floatwin;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.auto.master.R;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.floatwin.adapter.LaunchAppPickerAdapter;

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
        void showGestureLibraryDialog(android.widget.AutoCompleteTextView gestureInput, android.widget.TextView statusView);
        void playGestureFromInput(android.widget.AutoCompleteTextView gestureInput, android.widget.TextView statusView);
        void beginGestureRecordFromDialog(View dialogView, android.widget.AutoCompleteTextView edtGestureFile, android.widget.TextView tvGestureStatus);
        void updateGestureStatus(android.widget.TextView statusView, String gestureFileName);
        String normalizeGestureFileName(String rawName);
    }

    public interface TaskOperationHelper {
        java.util.List<String> getCurrentProjectTaskIds();
        java.util.List<String> getTaskOperationIds(String taskId);
        String getOperationDisplayLabel(String operationId);
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

    public interface OcrHelper {
        void updateOcrBboxStatus(android.widget.TextView statusView, java.util.List<Integer> bbox);
        void beginOcrRegionPickFromDialog(View dialogView, android.widget.EditText edtBbox, android.widget.TextView statusView);
        void testOcrFromDialog(android.widget.EditText edtBbox, android.widget.EditText edtTimeout, android.widget.AutoCompleteTextView edtEngine, android.widget.TextView resultView);
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
    private OcrHelper ocrHelper;
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

    public void setOcrHelper(OcrHelper helper) {
        this.ocrHelper = helper;
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

        dialogHelpers.bindAutoComplete(edtClickMode, java.util.Arrays.asList(
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
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        prepareJumpTaskAutoComplete(edtTargetTask);
        prepareJumpTaskAutoComplete(edtTargetOperation);
        prepareJumpTaskAutoComplete(edtNextOperation);

        if (taskOperationHelper != null) {
            dialogHelpers.bindAutoComplete(edtTargetTask, taskOperationHelper.getCurrentProjectTaskIds());
        }
        bindJumpTaskTargetOperationSuggestions(edtTargetOperation, "");
        updateJumpTaskReturnSection(nextOperationSection, cbReturnAfterComplete);
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
                }
                lastTaskId = taskId;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
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

        btnPickTargetTask.setOnClickListener(v -> edtTargetTask.showDropDown());

        btnPickTargetOperation.setOnClickListener(v -> {
            String taskId = edtTargetTask.getText() == null ? "" : edtTargetTask.getText().toString().trim();
            if (TextUtils.isEmpty(taskId)) {
                edtTargetTask.setError("请先选择目标 Task");
                edtTargetTask.requestFocus();
                return;
            }
            edtTargetOperation.requestFocus();
            edtTargetOperation.showDropDown();
        });

        btnClearTargetOperation.setOnClickListener(v -> edtTargetOperation.setText("", false));

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

        dialogHelpers.bindAutoComplete(edtClickMode, java.util.Arrays.asList(
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
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
        }

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
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
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
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
        AutoCompleteTextView edtGestureFile = dialogView.findViewById(R.id.edt_gesture_file);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        android.widget.TextView tvGestureStatus = dialogView.findViewById(R.id.tv_gesture_status);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        if (gestureHelper != null) {
            gestureHelper.refreshGestureOptions(edtGestureFile);
        }
        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
        }

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            String gestureFile = "";
            if (inputMap != null) {
                gestureFile = inputMap.optString(MetaOperation.SAVEFILENAME, "");
                if (TextUtils.isEmpty(gestureFile)) {
                    gestureFile = inputMap.optString(MetaOperation.GESTURE_TEMPLATE_ID, "");
                }
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
            }
            edtGestureFile.setText(gestureFile);

            if (gestureHelper != null) {
                java.io.File gestureFileObj = gestureHelper.resolveTaskGestureFile(gestureFile);
                if (tvGestureStatus != null) {
                    tvGestureStatus.setText(gestureFileObj != null ?
                        ("状态：已存在 " + gestureFileObj.getName()) : "状态：未录制，建议点击下方按钮录制");
                }
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_gesture_ts).setOnClickListener(v -> {
            if (gestureHelper != null) {
                edtGestureFile.setText(gestureHelper.generateGestureTimestampName());
            }
        });

        dialogView.findViewById(R.id.btn_gesture_library).setOnClickListener(v -> {
            if (gestureHelper != null) {
                gestureHelper.showGestureLibraryDialog(edtGestureFile, tvGestureStatus);
            }
        });

        dialogView.findViewById(R.id.btn_play_gesture).setOnClickListener(v -> {
            if (gestureHelper != null) {
                gestureHelper.playGestureFromInput(edtGestureFile, tvGestureStatus);
            }
        });

        dialogView.findViewById(R.id.btn_record_gesture).setOnClickListener(v -> {
            if (gestureHelper != null) {
                gestureHelper.beginGestureRecordFromDialog(dialogView, edtGestureFile, tvGestureStatus);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        edtGestureFile.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (gestureHelper != null && tvGestureStatus != null) {
                    gestureHelper.updateGestureStatus(tvGestureStatus, s == null ? "" : s.toString());
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String normalizedGestureFile = "";
            if (gestureHelper != null) {
                normalizedGestureFile = gestureHelper.normalizeGestureFileName(
                    edtGestureFile.getText() == null ? "" : edtGestureFile.getText().toString().trim());
            }
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(normalizedGestureFile)) {
                edtGestureFile.setError("请填写手势文件名");
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
                inputMap.put(MetaOperation.SAVEFILENAME, normalizedGestureFile);
                inputMap.put(MetaOperation.GESTURE_TEMPLATE_ID, normalizedGestureFile);
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
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
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
                }
                lastTaskId = taskId;
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
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

        btnPickTargetTask.setOnClickListener(v -> edtTargetTask.showDropDown());

        btnPickTargetOperation.setOnClickListener(v -> {
            String taskId = edtTargetTask.getText() == null ? "" : edtTargetTask.getText().toString().trim();
            if (TextUtils.isEmpty(taskId)) {
                edtTargetTask.setError("请先选择目标 Task");
                edtTargetTask.requestFocus();
                return;
            }
            edtTargetOperation.requestFocus();
            edtTargetOperation.showDropDown();
        });

        btnClearTargetOperation.setOnClickListener(v -> edtTargetOperation.setText("", false));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择返回后的下一节点", operationId, edtNextOperation);
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
        AutoCompleteTextView edtGestureFile = dialogView.findViewById(R.id.edt_gesture_file);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        android.widget.TextView tvGestureStatus = dialogView.findViewById(R.id.tv_gesture_status);

        if (gestureHelper != null) {
            gestureHelper.refreshGestureOptions(edtGestureFile);
            String timestampName = gestureHelper.generateGestureTimestampName();
            edtGestureFile.setText(timestampName);
            gestureHelper.updateGestureStatus(tvGestureStatus, timestampName);
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_gesture_ts).setOnClickListener(v -> {
            if (gestureHelper != null) {
                edtGestureFile.setText(gestureHelper.generateGestureTimestampName());
            }
        });

        dialogView.findViewById(R.id.btn_gesture_library).setOnClickListener(v -> {
            if (gestureHelper != null) {
                gestureHelper.showGestureLibraryDialog(edtGestureFile, tvGestureStatus);
            }
        });

        dialogView.findViewById(R.id.btn_play_gesture).setOnClickListener(v -> {
            if (gestureHelper != null) {
                gestureHelper.playGestureFromInput(edtGestureFile, tvGestureStatus);
            }
        });

        dialogView.findViewById(R.id.btn_record_gesture).setOnClickListener(v -> {
            if (gestureHelper != null) {
                gestureHelper.beginGestureRecordFromDialog(dialogView, edtGestureFile, tvGestureStatus);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", null, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        edtGestureFile.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (gestureHelper != null && tvGestureStatus != null) {
                    gestureHelper.updateGestureStatus(tvGestureStatus, s == null ? "" : s.toString());
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String gestureFile = "";
            if (gestureHelper != null) {
                gestureFile = gestureHelper.normalizeGestureFileName(
                    edtGestureFile.getText() == null ? "" : edtGestureFile.getText().toString().trim());
            }
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(gestureFile)) {
                edtGestureFile.setError("请填写手势文件名");
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
                inputMap.put(MetaOperation.SAVEFILENAME, gestureFile);
                inputMap.put(MetaOperation.GESTURE_TEMPLATE_ID, gestureFile);
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
        edtTimeout.setText("5000");

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
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
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
                    edtTimeout.setText("5000");
                }

                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
            } else {
                edtSimilarity.setText("0.85");
                edtTimeout.setText("5000");
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_capture).setOnClickListener(v -> {
            if (templateHelper != null) {
                templateHelper.beginTemplateCaptureFromDialog(dialogView, edtTemplateFile);
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

        edtTimeout.setText("5000");
        if (chkSuccessClick != null) {
            chkSuccessClick.setChecked(true);
        }

        // 高级参数折叠
        setupAdvancedToggle(dialogView);

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

                String preDelay = edtPreDelay.getText().toString().trim();
                if (!TextUtils.isEmpty(preDelay)) {
                    inputMap.put("MATCH_PRE_DELAY_MS", Long.parseLong(preDelay));
                }

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
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        setupAdvancedToggle(dialogView);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
        }

        // 回填现有数据
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                Object timeout = inputMap.opt("MATCHTIMEOUT");
                edtTimeout.setText(timeout != null ? String.valueOf(timeout).replace(".0", "") : "5000");
                setOperationReferenceText(edtNextOperation, inputMap.optString("nextOperationId", ""));
                setOperationReferenceText(edtFallback, inputMap.optString("FALLBACKOPERATIONID", ""));
                edtPreDelay.setText(inputMap.optString("MATCH_PRE_DELAY_MS", ""));
                if (chkSuccessClick != null) {
                    chkSuccessClick.setChecked(inputMap.optBoolean(MetaOperation.SUCCEESCLICK, true));
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
                edtTimeout.setText("5000");
                if (chkSuccessClick != null) {
                    chkSuccessClick.setChecked(true);
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
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", operationId, edtFallback);
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

                String preDelay = edtPreDelay.getText().toString().trim();
                if (!TextUtils.isEmpty(preDelay)) {
                    inputMap.put("MATCH_PRE_DELAY_MS", Long.parseLong(preDelay));
                }

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

        edtTimeout.setText("5000");
        dialogHelpers.bindAutoComplete(edtMode, java.util.Arrays.asList("全部点都命中", "任意一点命中"));
        edtMode.setText("全部点都命中", false);
        setupAdvancedToggle(dialogView);

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
                if (!TextUtils.isEmpty(safeText(edtPreDelay))) {
                    inputMap.put(MetaOperation.MATCH_PRE_DELAY_MS, Long.parseLong(safeText(edtPreDelay)));
                }
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

        dialogHelpers.bindAutoComplete(edtMode, java.util.Arrays.asList("全部点都命中", "任意一点命中"));
        edtMode.setText("全部点都命中", false);
        edtTimeout.setText("5000");
        setupAdvancedToggle(dialogView);
        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
        }

        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                Object timeoutObj = inputMap.opt(MetaOperation.MATCHTIMEOUT);
                edtTimeout.setText(timeoutObj == null ? "5000" : String.valueOf(timeoutObj).replace(".0", ""));
                edtMode.setText(displayColorMatchMode(inputMap.optString(MetaOperation.COLOR_MATCH_MODE, MetaOperation.COLOR_MATCH_MODE_ALL)), false);
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                setOperationReferenceText(edtFallback, inputMap.optString(MetaOperation.FALLBACKOPERATIONID, ""));
                edtPreDelay.setText(inputMap.optString(MetaOperation.MATCH_PRE_DELAY_MS, ""));
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
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
            }
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", operationId, edtFallback);
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
                if (!TextUtils.isEmpty(safeText(edtPreDelay))) {
                    inputMap.put(MetaOperation.MATCH_PRE_DELAY_MS, Long.parseLong(safeText(edtPreDelay)));
                }
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

    public void showAddOcrDialog() {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_ocr, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtBbox = dialogView.findViewById(R.id.edt_bbox);
        EditText edtOutputVar = dialogView.findViewById(R.id.edt_output_var);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        AutoCompleteTextView edtEngine = dialogView.findViewById(R.id.edt_ocr_engine);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        android.widget.TextView tvBboxStatus = dialogView.findViewById(R.id.tv_bbox_status);
        android.widget.TextView tvOcrResult = dialogView.findViewById(R.id.tv_ocr_test_result);

        edtTimeout.setText("5000");
        edtOutputVar.setText("ocr_text");
        dialogHelpers.bindAutoComplete(edtEngine, java.util.Arrays.asList("paddle", "accurate", "fast"));
        edtEngine.setText("paddle", false);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, null);
        }
        if (ocrHelper != null) {
            ocrHelper.updateOcrBboxStatus(tvBboxStatus, null);
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_bbox).setOnClickListener(v -> {
            if (ocrHelper != null) {
                ocrHelper.beginOcrRegionPickFromDialog(dialogView, edtBbox, tvBboxStatus);
            }
        });

        dialogView.findViewById(R.id.btn_test_ocr).setOnClickListener(v -> {
            if (ocrHelper != null) {
                ocrHelper.testOcrFromDialog(edtBbox, edtTimeout, edtEngine, tvOcrResult);
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
            String bboxText = edtBbox.getText().toString().trim();
            String outputVar = edtOutputVar.getText().toString().trim();
            String timeoutText = edtTimeout.getText().toString().trim();
            String engine = edtEngine.getText() == null ? "paddle" : edtEngine.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }

            java.util.List<Integer> bbox = null;
            if (ocrHelper != null) {
                bbox = ocrHelper.parseBboxInput(bboxText);
            }
            if (bbox == null) {
                edtBbox.setError("请先框选或输入 x,y,w,h");
                return;
            }

            long timeout;
            try {
                timeout = Long.parseLong(timeoutText);
            } catch (Exception e) {
                edtTimeout.setError("请输入有效超时(毫秒)");
                return;
            }
            if (timeout <= 0) {
                edtTimeout.setError("超时必须大于0");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                if (idGenerator != null) {
                    operationObject.put("id", idGenerator.generateId());
                }
                operationObject.put("name", name);
                operationObject.put("type", 9);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.BBOX, new org.json.JSONArray(bbox));
                inputMap.put(MetaOperation.OCR_TIMEOUT, timeout);
                inputMap.put(MetaOperation.OCR_OUTPUT_VAR, outputVar);
                inputMap.put(MetaOperation.OCR_ENGINE, TextUtils.isEmpty(engine) ? "paddle" : engine);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                try {
                    JSONArray operations = crudHelper.readOperationsArray();
                    operations.put(operationObject);
                    String message = TextUtils.isEmpty(nextOp) ? "已保存OCR节点（无下一节点）" : ("已保存OCR节点，下一节点: " + nextOp);
                    if (crudHelper.writeOperationsArray(operations, message, () -> {
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
                host.showToast("构建OCR操作失败: " + e.getMessage());
            }
        });
    }

    public void showEditOcrDialog(String operationId, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_add_ocr, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(360, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.84f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 430, null);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtBbox = dialogView.findViewById(R.id.edt_bbox);
        EditText edtOutputVar = dialogView.findViewById(R.id.edt_output_var);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        AutoCompleteTextView edtEngine = dialogView.findViewById(R.id.edt_ocr_engine);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        android.widget.TextView tvBboxStatus = dialogView.findViewById(R.id.tv_bbox_status);
        android.widget.TextView tvOcrResult = dialogView.findViewById(R.id.tv_ocr_test_result);
        android.widget.TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
        }
        dialogHelpers.bindAutoComplete(edtEngine, java.util.Arrays.asList("paddle", "accurate", "fast"));

        // Pre-fill with existing data
        try {
            edtName.setText(operationObject.optString("name", ""));
            JSONObject inputMapObj = operationObject.optJSONObject("inputMap");
            String bboxText = "";
            if (inputMapObj != null) {
                org.json.JSONArray arr = inputMapObj.optJSONArray(MetaOperation.BBOX);
                if (arr != null && arr.length() >= 4) {
                    bboxText = arr.optInt(0) + "," + arr.optInt(1) + "," + arr.optInt(2) + "," + arr.optInt(3);
                }
                edtOutputVar.setText(inputMapObj.optString(MetaOperation.OCR_OUTPUT_VAR, ""));
                edtTimeout.setText(String.valueOf(inputMapObj.optLong(MetaOperation.OCR_TIMEOUT, 5000L)));
                String savedEngine = inputMapObj.optString(MetaOperation.OCR_ENGINE, "");
                edtEngine.setText(TextUtils.isEmpty(savedEngine) ? "paddle" : savedEngine, false);
                setOperationReferenceText(edtNextOperation, inputMapObj.optString(MetaOperation.NEXT_OPERATION_ID, ""));
            } else {
                edtTimeout.setText("5000");
                edtEngine.setText("paddle", false);
            }
            edtBbox.setText(bboxText);

            if (ocrHelper != null) {
                java.util.List<Integer> bbox = ocrHelper.parseBboxInput(bboxText);
                ocrHelper.updateOcrBboxStatus(tvBboxStatus, bbox);
            }
        } catch (Exception e) {
            host.showToast("加载操作数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_bbox).setOnClickListener(v -> {
            if (ocrHelper != null) {
                ocrHelper.beginOcrRegionPickFromDialog(dialogView, edtBbox, tvBboxStatus);
            }
        });

        dialogView.findViewById(R.id.btn_test_ocr).setOnClickListener(v -> {
            if (ocrHelper != null) {
                ocrHelper.testOcrFromDialog(edtBbox, edtTimeout, edtEngine, tvOcrResult);
            }
        });

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v ->
            dialogHelpers.safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String bboxStr = edtBbox.getText().toString().trim();
            String outputVar = edtOutputVar.getText().toString().trim();
            String timeoutText = edtTimeout.getText().toString().trim();
            String engine = edtEngine.getText() == null ? "paddle" : edtEngine.getText().toString().trim();
            String nextOp = safeText(edtNextOperation);

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }

            java.util.List<Integer> bbox = null;
            if (ocrHelper != null) {
                bbox = ocrHelper.parseBboxInput(bboxStr);
            }
            if (bbox == null) {
                edtBbox.setError("请先框选或输入 x,y,w,h");
                return;
            }

            long timeout;
            try {
                timeout = Long.parseLong(timeoutText);
            } catch (Exception e) {
                edtTimeout.setError("请输入有效超时(毫秒)");
                return;
            }
            if (timeout <= 0) {
                edtTimeout.setError("超时必须大于0");
                return;
            }

            try {
                JSONObject updatedOperation = new JSONObject();
                updatedOperation.put("id", operationId);
                updatedOperation.put("name", name);
                updatedOperation.put("type", 9);
                updatedOperation.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.BBOX, new org.json.JSONArray(bbox));
                inputMap.put(MetaOperation.OCR_TIMEOUT, timeout);
                inputMap.put(MetaOperation.OCR_OUTPUT_VAR, outputVar);
                inputMap.put(MetaOperation.OCR_ENGINE, TextUtils.isEmpty(engine) ? "paddle" : engine);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                updatedOperation.put("inputMap", inputMap);

                if (operationUpdater != null) {
                    String message = TextUtils.isEmpty(nextOp) ? "已保存OCR节点（无下一节点）" : ("已保存OCR节点，下一节点: " + nextOp);
                    if (operationUpdater.saveOperationJson(operationId, updatedOperation.toString(2))) {
                        host.showToast(message);
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
            dialogHelpers.bindAutoComplete(edtSourceMode, variableHelper.getVariableSourceModes());
            dialogHelpers.bindAutoComplete(edtVarType, variableHelper.getVariableValueTypes());
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
            dialogHelpers.bindAutoComplete(edtSourceMode, variableHelper.getVariableSourceModes());
            dialogHelpers.bindAutoComplete(edtVarType, variableHelper.getVariableValueTypes());
            variableHelper.bindVariableSourceModeWatcher(edtSourceMode, tvSourceLabel, edtSourceValue);
        }

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
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
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
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
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
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
                showOperationPickerForField("选择默认下一节点", operationId, edtNextOperation);
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
            dialogHelpers.bindAutoComplete(edtAction, variableMathHelper.getVariableMathActions());
        }
        dialogHelpers.bindAutoComplete(edtOperandMode, java.util.Arrays.asList("literal", "variable"));
        dialogHelpers.bindAutoComplete(edtOperandType, java.util.Collections.singletonList("number"));

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
            dialogHelpers.bindAutoComplete(edtAction, variableMathHelper.getVariableMathActions());
        }
        dialogHelpers.bindAutoComplete(edtOperandMode, java.util.Arrays.asList("literal", "variable"));
        dialogHelpers.bindAutoComplete(edtOperandType, java.util.Collections.singletonList("number"));

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
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
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
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
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
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
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
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
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
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
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
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

        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);

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
                showOperationPickerForField("选择 default 节点", operationId, edtDefaultNext);
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
        dialogHelpers.bindAutoComplete(edtOperator, operators);
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
        dialogHelpers.bindAutoComplete(edtOperator, operators);

        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);

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
                showOperationPickerForField("选择主路径节点", operationId, edtBodyNext);
            }
        });

        dialogView.findViewById(R.id.btn_pick_exit).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择另一条路径节点", operationId, edtExitNext);
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

        edtTimeout.setText("5000");
        edtTolerance.setText("18");
        edtMinPixels.setText("60");
        setupAdvancedToggle(dialogView);

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
            if (ocrHelper != null) {
                ocrHelper.beginOcrRegionPickFromDialog(dialogView, edtBbox, null);
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

            java.util.List<Integer> bbox = ocrHelper != null ? ocrHelper.parseBboxInput(bboxText) : null;
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
                if (!TextUtils.isEmpty(safeText(edtPreDelay))) {
                    try { inputMap.put(MetaOperation.MATCH_PRE_DELAY_MS, Long.parseLong(safeText(edtPreDelay))); } catch (Exception ignored) {}
                }
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

        edtTimeout.setText("5000");
        edtTolerance.setText("18");
        edtMinPixels.setText("60");
        setupAdvancedToggle(dialogView);

        if (nextOpBinder != null) {
            nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);
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
                edtTimeout.setText(timeoutObj == null ? "5000" : String.valueOf(timeoutObj).replace(".0", ""));
                // pre delay
                edtPreDelay.setText(inputMap.optString(MetaOperation.MATCH_PRE_DELAY_MS, ""));
                // next / fallback
                setOperationReferenceText(edtNextOperation, inputMap.optString(MetaOperation.NEXT_OPERATION_ID, ""));
                setOperationReferenceText(edtFallback, inputMap.optString(MetaOperation.FALLBACKOPERATIONID, ""));
            }
        } catch (Exception e) {
            host.showToast("加载区域找色数据失败: " + e.getMessage());
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_bbox).setOnClickListener(v -> {
            if (ocrHelper != null) {
                ocrHelper.beginOcrRegionPickFromDialog(dialogView, edtBbox, null);
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
                showOperationPickerForField("选择下一节点", operationId, edtNextOperation);
            }
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null) {
                showOperationPickerForField("选择失败跳转节点", operationId, edtFallback);
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

            java.util.List<Integer> bbox = ocrHelper != null ? ocrHelper.parseBboxInput(bboxText) : null;
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
                if (!TextUtils.isEmpty(safeText(edtPreDelay))) {
                    try { inputMap.put(MetaOperation.MATCH_PRE_DELAY_MS, Long.parseLong(safeText(edtPreDelay))); } catch (Exception ignored) {}
                }
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
        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);

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
        if (nextOpBinder != null) nextOpBinder.bindNextOperationSuggestions(dialogView, operationId);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v -> {
            if (operationPickerLauncher != null)
                showOperationPickerForField("选择成功后节点", operationId, edtNextOperation);
        });
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v -> {
            if (operationPickerLauncher != null)
                showOperationPickerForField("选择失败后节点", operationId, edtFallbackOperation);
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
