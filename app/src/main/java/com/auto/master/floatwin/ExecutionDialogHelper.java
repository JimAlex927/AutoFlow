package com.auto.master.floatwin;

import android.content.Intent;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.auto.master.R;

/**
 * 执行流程相关 dialog 的 Helper，从 FloatWindowService 拆分而来。
 * 负责 showPrecheckDialog / handlePrecheckFix / showRunModeMenu。
 */
public class ExecutionDialogHelper {

    public interface OnRunModeSelectedListener {
        void onSelected(boolean openProjectPanelNow);
    }

    private final FloatWindowHost host;
    private final DialogHelpers dialogHelpers;
    private final WindowManager wm;

    public ExecutionDialogHelper(FloatWindowHost host, DialogHelpers dialogHelpers) {
        this.host = host;
        this.dialogHelpers = dialogHelpers;
        this.wm = host.getWindowManager();
    }

    public void showPrecheckDialog(PrecheckResult result, Runnable continueAction) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_run_precheck, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(320, true);
        wm.addView(dialogView, dialogLp);

        TextView tvContent = dialogView.findViewById(R.id.tv_precheck_content);
        StringBuilder sb = new StringBuilder();
        if (!result.blocking.isEmpty()) {
            sb.append("阻断项:\n");
            for (String s : result.blocking) {
                sb.append(s).append("\n");
            }
        }
        if (!result.warnings.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("提醒项:\n");
            for (String s : result.warnings) {
                sb.append(s).append("\n");
            }
        }
        if (sb.length() == 0) {
            sb.append("阻断项: 无\n提醒项: 无\n\n可直接运行。");
        }
        tvContent.setText(sb.toString().trim());

        dialogView.findViewById(R.id.btn_precheck_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_precheck_continue).setOnClickListener(v -> {
            dialogHelpers.safeRemoveView(dialogView);
            if (continueAction != null) {
                continueAction.run();
            }
        });
        dialogView.findViewById(R.id.btn_precheck_fix).setOnClickListener(v -> {
            dialogHelpers.safeRemoveView(dialogView);
            handlePrecheckFix(result.fixAction);
        });
    }

    private void handlePrecheckFix(int fixAction) {
        try {
            if (fixAction == 1) {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                host.getContext().startActivity(i);
                return;
            }
            if (fixAction == 2) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + host.getContext().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                host.getContext().startActivity(i);
                return;
            }
            if (fixAction == 3) {
                Intent i = new Intent(host.getContext(), com.auto.master.MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                host.getContext().startActivity(i);
                host.showToast("请在首页点击\u201c录屏授权\u201d");
                return;
            }
        } catch (Exception e) {
            host.showToast("打开修复页面失败");
        }
    }

    public void showRunModeMenu(View anchor, OnRunModeSelectedListener listener) {
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_run_mode, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(280, true);
        wm.addView(dialogView, dialogLp);

        dialogView.findViewById(R.id.btn_run_with_panel).setOnClickListener(v -> {
            dialogHelpers.safeRemoveView(dialogView);
            if (listener != null) {
                listener.onSelected(true);
            }
        });

        dialogView.findViewById(R.id.btn_run_background).setOnClickListener(v -> {
            dialogHelpers.safeRemoveView(dialogView);
            if (listener != null) {
                listener.onSelected(false);
            }
        });

        dialogView.findViewById(R.id.btn_run_mode_cancel).setOnClickListener(v ->
                dialogHelpers.safeRemoveView(dialogView));
    }
}
