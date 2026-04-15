package com.auto.master.floatwin.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;
import com.auto.master.utils.BitmapManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TemplateLibraryAdapter extends RecyclerView.Adapter<TemplateLibraryAdapter.ViewHolder> {

    private interface SelectionToggleTarget {
        void updateChecked(boolean checked);
    }

    public interface OnPickListener {
        void onPick(TemplateLibraryItem item);
    }

    public interface OnDeleteListener {
        void onDelete(TemplateLibraryItem item);
    }

    public interface OnSelectionChangedListener {
        void onChanged(int count);
    }

    public static class TemplateLibraryItem {
        public String fileName;
        /** Scale subdir this template lives in, e.g. "scale_100". Null for legacy flat-dir files. */
        public String scaleDirName;
        public File file;
        /** For folder items: number of templates in the folder. For template items: node reference count. */
        public int usageCount;
        /** True if this item represents a scale directory (folder), not a template file. */
        public boolean isFolder;

        public TemplateLibraryItem(String fileName, File file, int usageCount) {
            this(fileName, null, file, usageCount);
        }

        public TemplateLibraryItem(String fileName, String scaleDirName, File file, int usageCount) {
            this.fileName = fileName;
            this.scaleDirName = scaleDirName;
            this.file = file;
            this.usageCount = usageCount;
            this.isFolder = false;
        }

        /** Unique key combining scale dir + filename, used for identity. */
        public String getKey() {
            return android.text.TextUtils.isEmpty(scaleDirName)
                    ? fileName
                    : scaleDirName + "/" + fileName;
        }

        /** Display label: bare filename for template items, dir name for folder items. */
        public String getDisplayName() {
            return fileName;
        }
    }

    private final List<TemplateLibraryItem> allItems = new ArrayList<>();
    private final List<TemplateLibraryItem> shownItems = new ArrayList<>();
    private final OnPickListener listener;
    private final OnDeleteListener deleteListener;
    private final BitmapManager bitmapManager = BitmapManager.getInstance();
    private final Set<String> selectedNames = new HashSet<>();
    private boolean batchMode = false;
    private boolean deleteActionEnabled = false;
    private OnSelectionChangedListener selectionChangedListener;

    public TemplateLibraryAdapter(List<TemplateLibraryItem> items, OnPickListener listener) {
        this(items, listener, null);
    }

    public TemplateLibraryAdapter(List<TemplateLibraryItem> items,
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
        List<TemplateLibraryItem> filtered = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(q)) {
            filtered.addAll(allItems);
        } else {
            for (TemplateLibraryItem item : allItems) {
                if (item.fileName != null && item.fileName.toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(item);
                }
            }
        }
        applyShownItems(filtered);
    }

    public void setBatchMode(boolean enabled) {
        batchMode = enabled;
        if (!enabled) {
            selectedNames.clear();
            notifySelectionChanged();
        }
        notifyItemRangeChanged(0, shownItems.size());
    }

    public int getSelectedCount() {
        return selectedNames.size();
    }

    /** Returns bare filenames (e.g. "foo.png") of selected items, deduplicated. */
    public Set<String> getSelectedFileNames() {
        Set<String> names = new HashSet<>();
        for (TemplateLibraryItem item : allItems) {
            if (selectedNames.contains(item.getKey())) {
                names.add(item.fileName);
            }
        }
        return names;
    }

    public void selectItem(String fileName) {
        if (fileName == null || fileName.isEmpty()) return;
        // Look up the item's canonical key (e.g. "scale_100/foo.png") so that
        // onBindViewHolder's selectedNames.contains(item.getKey()) check works correctly.
        String keyToAdd = fileName;
        for (TemplateLibraryItem item : allItems) {
            if (TextUtils.equals(fileName, item.fileName)) {
                keyToAdd = item.getKey();
                break;
            }
        }
        selectedNames.add(keyToAdd);
        notifySelectionChanged();
        int position = findShownPosition(fileName);
        if (position >= 0) {
            notifyItemChanged(position);
        }
    }

    /** Returns the selected items (each has fileName + scaleDirName). */
    public List<TemplateLibraryItem> getSelectedItems() {
        List<TemplateLibraryItem> result = new ArrayList<>();
        for (TemplateLibraryItem item : allItems) {
            if (selectedNames.contains(item.getKey())) {
                result.add(item);
            }
        }
        return result;
    }

    public void setSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public void setDeleteActionEnabled(boolean enabled) {
        deleteActionEnabled = enabled;
        notifyItemRangeChanged(0, shownItems.size());
    }

    public void replaceData(List<TemplateLibraryItem> items) {
        allItems.clear();
        selectedNames.clear();
        if (items != null) {
            allItems.addAll(items);
        }
        notifySelectionChanged();
        applyShownItems(new ArrayList<>(allItems));
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onChanged(selectedNames.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_template_library, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TemplateLibraryItem item = shownItems.get(position);

        if (item.isFolder) {
            // ---- 文件夹条目 ----
            holder.ivImage.setImageBitmap(null);
            holder.ivImage.setBackgroundColor(0xFF2A5298); // 蓝色背景代表文件夹
            holder.tvName.setText(item.fileName);
            holder.tvMeta.setText(item.usageCount + " 个模板");
            holder.tvMeta.setTextColor(0xFF4e5f74);
            holder.checkBox.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPick(item);
            });
            holder.checkBox.setOnClickListener(null);
            return;
        }

        // ---- 普通模板条目 ----
        holder.ivImage.setBackgroundColor(0);
        holder.tvName.setText(item.getDisplayName());
        if (item.usageCount > 0) {
            holder.tvMeta.setText("引用节点: " + item.usageCount + " (受保护)");
            holder.tvMeta.setTextColor(0xFFB23B3B);
        } else {
            holder.tvMeta.setText("未被引用，可删除");
            holder.tvMeta.setTextColor(0xFF7A8794);
        }
        holder.ivImage.setImageBitmap(item.file == null
                ? null
                : bitmapManager.loadThumbnail(item.file.getAbsolutePath(), 160, 120));
        holder.checkBox.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        holder.checkBox.setChecked(selectedNames.contains(item.getKey()));
        holder.checkBox.setClickable(false);
        holder.checkBox.setFocusable(false);
        holder.btnDelete.setVisibility(deleteActionEnabled && !batchMode ? View.VISIBLE : View.GONE);
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(item);
            }
        });
        View.OnClickListener clickListener = v -> {
            if (batchMode) {
                toggleSelection(item, holder.checkBox::setChecked);
            } else if (listener != null) {
                listener.onPick(item);
            }
        };
        holder.itemView.setOnClickListener(clickListener);
        holder.checkBox.setOnClickListener(clickListener);
    }

    private void toggleSelection(TemplateLibraryItem item, SelectionToggleTarget target) {
        if (item == null || TextUtils.isEmpty(item.fileName)) {
            return;
        }
        String key = item.getKey();
        if (selectedNames.contains(key)) {
            selectedNames.remove(key);
        } else {
            selectedNames.add(key);
        }
        boolean checked = selectedNames.contains(key);
        if (target != null) {
            target.updateChecked(checked);
        }
        notifySelectionChanged();
    }

    @Override
    public int getItemCount() {
        return shownItems.size();
    }

    @Override
    public long getItemId(int position) {
        TemplateLibraryItem item = shownItems.get(position);
        if (item == null || item.file == null) {
            return position;
        }
        return item.file.getAbsolutePath().hashCode();
    }

    private int findShownPosition(String fileName) {
        for (int i = 0; i < shownItems.size(); i++) {
            if (TextUtils.equals(fileName, shownItems.get(i).fileName)) {
                return i;
            }
        }
        return -1;
    }

    private void applyShownItems(List<TemplateLibraryItem> nextItems) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new SimpleDiffCallback(shownItems, nextItems));
        shownItems.clear();
        shownItems.addAll(nextItems);
        diff.dispatchUpdatesTo(this);
    }

    private static final class SimpleDiffCallback extends DiffUtil.Callback {
        private final List<TemplateLibraryItem> oldItems;
        private final List<TemplateLibraryItem> newItems;

        SimpleDiffCallback(List<TemplateLibraryItem> oldItems, List<TemplateLibraryItem> newItems) {
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
            TemplateLibraryItem oldItem = oldItems.get(oldItemPosition);
            TemplateLibraryItem newItem = newItems.get(newItemPosition);
            return TextUtils.equals(oldItem.getKey(), newItem.getKey());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            TemplateLibraryItem oldItem = oldItems.get(oldItemPosition);
            TemplateLibraryItem newItem = newItems.get(newItemPosition);
            return TextUtils.equals(oldItem.getKey(), newItem.getKey())
                    && oldItem.usageCount == newItem.usageCount;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvName;
        TextView tvMeta;
        TextView btnDelete;
        CheckBox checkBox;

        ViewHolder(View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.iv_template_item);
            tvName = itemView.findViewById(R.id.tv_template_item_name);
            tvMeta = itemView.findViewById(R.id.tv_template_item_meta);
            btnDelete = itemView.findViewById(R.id.btn_template_delete);
            checkBox = itemView.findViewById(R.id.chk_template_pick);
        }
    }
}
