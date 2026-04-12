package com.auto.master.floatwin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.List;

/**
 * 节点剪贴板对话框，从 FloatWindowService 拆分而来。
 * 负责 showOperationClipboardDialog。
 */
public class ClipboardDialogHelper {

    public interface ClipboardCallbacks {
        boolean hasOperationClipboard();
        List<OperationClipboardEntry> getClipboardLibrary();
        void removeClipboardEntry(OperationClipboardEntry entry);
        void pasteOperationEntryRelative(OperationClipboardEntry entry,
                                         String targetOperationId,
                                         boolean insertBefore);
    }

    private final FloatWindowHost host;
    private final DialogHelpers dialogHelpers;
    private final ClipboardCallbacks callbacks;
    private final WindowManager wm;

    public ClipboardDialogHelper(FloatWindowHost host,
                                 DialogHelpers dialogHelpers,
                                 ClipboardCallbacks callbacks) {
        this.host = host;
        this.dialogHelpers = dialogHelpers;
        this.callbacks = callbacks;
        this.wm = host.getWindowManager();
    }

    public void showOperationClipboardDialog(String targetOperationId, boolean insertBefore) {
        if (!callbacks.hasOperationClipboard()) {
            host.showToast("节点库为空，先复制一个节点吧");
            return;
        }
        View dialogView = LayoutInflater.from(host.getContext()).inflate(R.layout.dialog_node_clipboard, null);
        WindowManager.LayoutParams dialogLp = dialogHelpers.buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.78f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 420, null);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        TextView btnClear = dialogView.findViewById(R.id.btn_clear);
        TextView btnCancel = dialogView.findViewById(R.id.btnStart);
        TextView btnConfirm = dialogView.findViewById(R.id.btnAddNode);
        TextView tvEmpty = dialogView.findViewById(R.id.tv_empty);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerView);

        if (tvTitle != null) {
            tvTitle.setText(insertBefore ? "从节点库插入到前面" : "从节点库粘贴到后面");
        }
        if (btnConfirm != null) {
            btnConfirm.setText(insertBefore ? "插入节点" : "粘贴节点");
        }

        List<OperationClipboardEntry> library = callbacks.getClipboardLibrary();
        OperationClipboardLibraryAdapter adapter = new OperationClipboardLibraryAdapter(
                library,
                callbacks::removeClipboardEntry);
        recyclerView.setLayoutManager(new LinearLayoutManager(host.getContext()));
        recyclerView.setAdapter(adapter);

        Runnable refreshUi = () -> {
            boolean empty = library.isEmpty();
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (tvEmpty != null) {
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
            if (btnConfirm != null) {
                btnConfirm.setEnabled(!empty && adapter.getSelectedEntry() != null);
                btnConfirm.setAlpha(btnConfirm.isEnabled() ? 1f : 0.45f);
            }
            if (btnClear != null) {
                btnClear.setVisibility(empty ? View.GONE : View.VISIBLE);
            }
        };
        adapter.setOnSelectionChanged(refreshUi);
        refreshUi.run();

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                library.clear();
                adapter.notifyDataSetChanged();
                refreshUi.run();
            });
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialogHelpers.safeRemoveView(dialogView));
        }
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                OperationClipboardEntry selectedEntry = adapter.getSelectedEntry();
                if (selectedEntry == null) {
                    host.showToast("先从节点库选一个节点");
                    return;
                }
                dialogHelpers.safeRemoveView(dialogView);
                callbacks.pasteOperationEntryRelative(selectedEntry, targetOperationId, insertBefore);
            });
        }
        dialogView.setOnClickListener(v -> {
            if (v == dialogView) {
                dialogHelpers.safeRemoveView(dialogView);
            }
        });
    }
}
