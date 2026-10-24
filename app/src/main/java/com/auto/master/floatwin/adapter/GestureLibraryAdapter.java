package com.auto.master.floatwin.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;
import com.auto.master.auto.GestureOverlayView;
import com.auto.master.floatwin.GesturePreviewView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GestureLibraryAdapter extends RecyclerView.Adapter<GestureLibraryAdapter.ViewHolder> {

    public interface OnPickListener {
        void onPick(GestureLibraryItem item);
    }

    public interface OnDeleteListener {
        void onDelete(GestureLibraryItem item);
    }

    public interface OnSelectionChangedListener {
        void onChanged(int count, boolean allShownSelected);
    }

    public static class GestureLibraryItem {
        public String fileName;
        public File file;
        public GestureOverlayView.GestureNode node;
        public long duration;
        public int strokeCount;
        public int usageCount;

        public GestureLibraryItem(String fileName, File file, GestureOverlayView.GestureNode node) {
            this(fileName, file, node, 0);
        }

        public GestureLibraryItem(String fileName, File file,
                                  GestureOverlayView.GestureNode node, int usageCount) {
            this.fileName = fileName;
            this.file = file;
            this.node = node;
            this.duration = node == null ? 0L : node.duration;
            this.strokeCount = (node == null || node.strokes == null) ? 0 : node.strokes.size();
            this.usageCount = Math.max(0, usageCount);
        }
    }

    private final List<GestureLibraryItem> allItems = new ArrayList<>();
    private final List<GestureLibraryItem> shownItems = new ArrayList<>();
    private final OnPickListener listener;
    private final OnDeleteListener deleteListener;
    private final Set<String> selectedNames = new HashSet<>();
    private boolean batchMode;
    private boolean deleteActionEnabled;
    private OnSelectionChangedListener selectionChangedListener;
    private String currentQuery = "";

    public GestureLibraryAdapter(List<GestureLibraryItem> items, OnPickListener listener) {
        this(items, listener, null);
    }

    public GestureLibraryAdapter(List<GestureLibraryItem> items,
                                 OnPickListener listener,
                                 OnDeleteListener deleteListener) {
        if (items != null) {
            allItems.addAll(items);
            shownItems.addAll(items);
        }
        this.listener = listener;
        this.deleteListener = deleteListener;
        setHasStableIds(true);
    }

    public void updateFilter(String query) {
        List<GestureLibraryItem> filtered = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        currentQuery = q;
        if (TextUtils.isEmpty(q)) {
            filtered.addAll(allItems);
        } else {
            for (GestureLibraryItem item : allItems) {
                String name = item.fileName == null ? "" : item.fileName.toLowerCase(Locale.ROOT);
                if (name.contains(q)) {
                    filtered.add(item);
                }
            }
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new SimpleDiffCallback(shownItems, filtered));
        shownItems.clear();
        shownItems.addAll(filtered);
        diff.dispatchUpdatesTo(this);
        notifySelectionChanged();
    }

    public void replaceData(List<GestureLibraryItem> items) {
        allItems.clear();
        selectedNames.clear();
        if (items != null) {
            allItems.addAll(items);
        }
        updateFilter(currentQuery);
    }

    public void setBatchMode(boolean enabled) {
        batchMode = enabled;
        if (!enabled) {
            selectedNames.clear();
            notifySelectionChanged();
        }
        notifyItemRangeChanged(0, shownItems.size());
    }

    public void setDeleteActionEnabled(boolean enabled) {
        deleteActionEnabled = enabled;
        notifyItemRangeChanged(0, shownItems.size());
    }

    public void setSelectionChangedListener(OnSelectionChangedListener listener) {
        selectionChangedListener = listener;
        notifySelectionChanged();
    }

    public int getSelectedCount() {
        return selectedNames.size();
    }

    public List<GestureLibraryItem> getSelectedItems() {
        List<GestureLibraryItem> result = new ArrayList<>();
        for (GestureLibraryItem item : allItems) {
            if (selectedNames.contains(item.fileName)) {
                result.add(item);
            }
        }
        return result;
    }

    public void toggleSelectAllShown() {
        boolean allSelected = areAllShownSelected();
        for (GestureLibraryItem item : shownItems) {
            if (allSelected) {
                selectedNames.remove(item.fileName);
            } else {
                selectedNames.add(item.fileName);
            }
        }
        notifyItemRangeChanged(0, shownItems.size());
        notifySelectionChanged();
    }

    private boolean areAllShownSelected() {
        if (shownItems.isEmpty()) {
            return false;
        }
        for (GestureLibraryItem item : shownItems) {
            if (!selectedNames.contains(item.fileName)) {
                return false;
            }
        }
        return true;
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onChanged(selectedNames.size(), areAllShownSelected());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gesture_library, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GestureLibraryItem item = shownItems.get(position);
        holder.tvName.setText(item.fileName);
        if (item.node == null) {
            holder.tvMeta.setText(item.usageCount > 0
                    ? "手势文件损坏或为空 | 引用节点: " + item.usageCount + "（受保护）"
                    : "手势文件损坏或为空 | 未被引用，可删除");
            holder.tvMeta.setTextColor(0xFFB23B3B);
        } else {
            String usage = item.usageCount > 0
                    ? " | 引用节点: " + item.usageCount + "（受保护）"
                    : " | 未被引用，可删除";
            holder.tvMeta.setText("步骤: " + item.strokeCount
                    + " | 时长: " + item.duration + "ms" + usage);
            holder.tvMeta.setTextColor(item.usageCount > 0 ? 0xFFB23B3B : 0xFF7A8794);
        }
        holder.previewView.setGestureNode(item.node);
        holder.checkBox.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        holder.checkBox.setChecked(selectedNames.contains(item.fileName));
        holder.checkBox.setClickable(false);
        holder.checkBox.setFocusable(false);
        holder.btnDelete.setVisibility(deleteActionEnabled && !batchMode
                ? View.VISIBLE : View.GONE);
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(item);
            }
        });
        View.OnClickListener clickListener = v -> {
            if (batchMode) {
                toggleSelection(item);
            } else if (listener != null) {
                listener.onPick(item);
            }
        };
        holder.itemView.setOnClickListener(clickListener);
        holder.checkBox.setOnClickListener(clickListener);
    }

    private void toggleSelection(GestureLibraryItem item) {
        if (item == null || TextUtils.isEmpty(item.fileName)) {
            return;
        }
        if (!selectedNames.add(item.fileName)) {
            selectedNames.remove(item.fileName);
        }
        int position = shownItems.indexOf(item);
        if (position >= 0) {
            notifyItemChanged(position);
        }
        notifySelectionChanged();
    }

    @Override
    public int getItemCount() {
        return shownItems.size();
    }

    @Override
    public long getItemId(int position) {
        GestureLibraryItem item = shownItems.get(position);
        return item.file == null ? position : item.file.getAbsolutePath().hashCode();
    }

    private static final class SimpleDiffCallback extends DiffUtil.Callback {
        private final List<GestureLibraryItem> oldItems;
        private final List<GestureLibraryItem> newItems;

        SimpleDiffCallback(List<GestureLibraryItem> oldItems, List<GestureLibraryItem> newItems) {
            this.oldItems = oldItems;
            this.newItems = newItems;
        }

        @Override
        public int getOldListSize() {
            return oldItems.size();
        }

        @Override
        public int getNewListSize() {
            return newItems.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            File oldFile = oldItems.get(oldItemPosition).file;
            File newFile = newItems.get(newItemPosition).file;
            String oldPath = oldFile == null ? oldItems.get(oldItemPosition).fileName : oldFile.getAbsolutePath();
            String newPath = newFile == null ? newItems.get(newItemPosition).fileName : newFile.getAbsolutePath();
            return TextUtils.equals(oldPath, newPath);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            GestureLibraryItem oldItem = oldItems.get(oldItemPosition);
            GestureLibraryItem newItem = newItems.get(newItemPosition);
            return TextUtils.equals(oldItem.fileName, newItem.fileName)
                    && oldItem.duration == newItem.duration
                    && oldItem.strokeCount == newItem.strokeCount
                    && oldItem.usageCount == newItem.usageCount
                    && oldItem.node == newItem.node;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        GesturePreviewView previewView;
        TextView tvName;
        TextView tvMeta;
        TextView btnDelete;
        CheckBox checkBox;

        ViewHolder(View itemView) {
            super(itemView);
            previewView = itemView.findViewById(R.id.view_gesture_preview);
            tvName = itemView.findViewById(R.id.tv_gesture_item_name);
            tvMeta = itemView.findViewById(R.id.tv_gesture_item_meta);
            btnDelete = itemView.findViewById(R.id.btn_gesture_delete);
            checkBox = itemView.findViewById(R.id.chk_gesture_pick);
        }
    }
}
