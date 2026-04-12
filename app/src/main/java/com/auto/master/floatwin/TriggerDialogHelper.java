package com.auto.master.floatwin;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;
import com.auto.master.scheduler.AppNotificationTrigger;
import com.auto.master.scheduler.TriggerAdapter;
import com.auto.master.scheduler.TriggerStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 触发器相关 dialog 的 Helper，从 FloatWindowService 拆分而来。
 * 负责 showTriggerManager / showAddAppLaunchTriggerDialog / showAddNotifTriggerDialog。
 */
public class TriggerDialogHelper {

    private final FloatWindowHost host;
    private final DialogHelpers dialogHelpers;
    private final AppLaunchTriggerManager triggerManager;
    private final WindowManager wm;

    public TriggerDialogHelper(FloatWindowHost host,
                               DialogHelpers dialogHelpers,
                               AppLaunchTriggerManager triggerManager) {
        this.host = host;
        this.dialogHelpers = dialogHelpers;
        this.triggerManager = triggerManager;
        this.wm = host.getWindowManager();
    }

    public void showTriggerManager() {
        if (!AppLaunchTriggerManager.TRIGGER_FEATURE_ENABLED) {
            host.showToast("触发器功能已停用");
            return;
        }
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_trigger_manager, null);
        WindowManager.LayoutParams lp = dialogHelpers.buildDialogLayoutParams(340, true);
        wm.addView(dialogView, lp);

        final List<AppNotificationTrigger> triggers =
                new ArrayList<>(TriggerStore.getAll(host.getContext()));

        RecyclerView rv = dialogView.findViewById(R.id.rv_triggers);
        rv.setLayoutManager(new LinearLayoutManager(host.getContext()));

        View emptyTv = dialogView.findViewById(R.id.tv_trigger_empty);
        Runnable updateEmpty = () -> {
            if (emptyTv != null)
                emptyTv.setVisibility(triggers.isEmpty() ? View.VISIBLE : View.GONE);
        };

        TriggerAdapter adapter = new TriggerAdapter(triggers, new TriggerAdapter.OnTriggerAction() {
            @Override
            public void onToggle(AppNotificationTrigger trigger, boolean enabled) {
                TriggerStore.upsert(host.getContext(), trigger);
                triggerManager.refreshPollingState();
            }

            @Override
            public void onDelete(AppNotificationTrigger trigger) {
                TriggerStore.remove(host.getContext(), trigger.id);
                triggerManager.refreshPollingState();
                triggers.remove(trigger);
                rv.getAdapter().notifyDataSetChanged();
                updateEmpty.run();
            }
        });
        rv.setAdapter(adapter);
        updateEmpty.run();

        dialogView.findViewById(R.id.btn_add_app_trigger).setOnClickListener(v ->
                showAddAppLaunchTriggerDialog(triggers, adapter, updateEmpty));
        dialogView.findViewById(R.id.btn_add_notif_trigger).setOnClickListener(v ->
                showAddNotifTriggerDialog(triggers, adapter, updateEmpty));
        dialogView.findViewById(R.id.btn_trigger_open_settings).setOnClickListener(v -> {
            try {
                Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                host.getContext().startActivity(intent);
            } catch (Exception e) {
                host.showToast("无法打开通知设置");
            }
        });
        dialogView.findViewById(R.id.btn_trigger_close).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
    }

    private void showAddAppLaunchTriggerDialog(
            List<AppNotificationTrigger> list,
            TriggerAdapter adapter,
            Runnable updateEmpty) {
        android.view.ContextThemeWrapper ctx =
                new android.view.ContextThemeWrapper(host.getContext(), R.style.Theme_AtomMaster);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(ctx);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(host.dp(16), host.dp(8), host.dp(16), host.dp(8));

        EditText edtPackage = new EditText(ctx);
        edtPackage.setHint("应用包名 (如 com.tencent.mm)");
        EditText edtLabel = new EditText(ctx);
        edtLabel.setHint("应用名称 (显示用，如 微信)");
        EditText edtProject = new EditText(ctx);
        edtProject.setHint("项目名称");
        EditText edtTask = new EditText(ctx);
        edtTask.setHint("Task 名称");

        layout.addView(edtPackage);
        layout.addView(edtLabel);
        layout.addView(edtProject);
        layout.addView(edtTask);

        new android.app.AlertDialog.Builder(ctx)
                .setTitle("添加 App 启动触发")
                .setView(layout)
                .setPositiveButton("添加", (d, w) -> {
                    String pkg = edtPackage.getText().toString().trim();
                    String label = edtLabel.getText().toString().trim();
                    String project = edtProject.getText().toString().trim();
                    String task = edtTask.getText().toString().trim();
                    if (pkg.isEmpty() || project.isEmpty() || task.isEmpty()) {
                        host.showToast("请填写完整信息");
                        return;
                    }
                    AppNotificationTrigger t = new AppNotificationTrigger();
                    t.id = UUID.randomUUID().toString();
                    t.triggerType = AppNotificationTrigger.TYPE_APP_LAUNCH;
                    t.watchPackage = pkg;
                    t.watchAppLabel = label.isEmpty() ? pkg : label;
                    t.projectName = project;
                    t.taskId = task;
                    t.enabled = true;
                    TriggerStore.upsert(host.getContext(), t);
                    triggerManager.refreshCache();
                    list.add(t);
                    adapter.notifyDataSetChanged();
                    updateEmpty.run();
                    triggerManager.startPolling();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddNotifTriggerDialog(
            List<AppNotificationTrigger> list,
            TriggerAdapter adapter,
            Runnable updateEmpty) {
        android.view.ContextThemeWrapper ctx =
                new android.view.ContextThemeWrapper(host.getContext(), R.style.Theme_AtomMaster);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(ctx);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(host.dp(16), host.dp(8), host.dp(16), host.dp(8));

        EditText edtPkg = new EditText(ctx);
        edtPkg.setHint("来源应用包名 (留空=任意应用)");
        EditText edtKw = new EditText(ctx);
        edtKw.setHint("通知关键词 (留空=任意通知)");
        EditText edtProject = new EditText(ctx);
        edtProject.setHint("项目名称");
        EditText edtTask = new EditText(ctx);
        edtTask.setHint("Task 名称");

        layout.addView(edtPkg);
        layout.addView(edtKw);
        layout.addView(edtProject);
        layout.addView(edtTask);

        new android.app.AlertDialog.Builder(ctx)
                .setTitle("添加通知触发")
                .setView(layout)
                .setPositiveButton("添加", (d, w) -> {
                    String project = edtProject.getText().toString().trim();
                    String task = edtTask.getText().toString().trim();
                    if (project.isEmpty() || task.isEmpty()) {
                        host.showToast("请填写项目和 Task 名称");
                        return;
                    }
                    AppNotificationTrigger t = new AppNotificationTrigger();
                    t.id = UUID.randomUUID().toString();
                    t.triggerType = AppNotificationTrigger.TYPE_NOTIFICATION;
                    t.notificationPackage = edtPkg.getText().toString().trim();
                    t.notificationKeyword = edtKw.getText().toString().trim();
                    t.projectName = project;
                    t.taskId = task;
                    t.enabled = true;
                    TriggerStore.upsert(host.getContext(), t);
                    list.add(t);
                    adapter.notifyDataSetChanged();
                    updateEmpty.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
