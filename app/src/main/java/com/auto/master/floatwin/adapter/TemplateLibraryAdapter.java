package com.auto.master.floatwin.adapter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

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
        public File file;
        public int usageCount;

        public TemplateLibraryItem(String fileName, File file, int usageCount) {
            this.fileName = fileName;
            this.file = file;
            this.usageCount = usageCount;
        }
    }

    private final List<TemplateLibraryItem> allItems = new ArrayList<>();
    private final List<TemplateLibraryItem> shownItems = new ArrayList<>();
    private final OnPickListener listener;
    private final OnDeleteListener deleteListener;
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
    }

    public void updateFilter(String query) {
        shownItems.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(q)) {
            shownItems.addAll(allItems);
        } else {
            for (TemplateLibraryItem item : allItems) {
                if (item.fileName != null && item.fileName.toLowerCase(Locale.ROOT).contains(q)) {
                    shownItems.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setBatchMode(boolean enabled) {
        batchMode = enabled;
        if (!enabled) {
            selectedNames.clear();
            notifySelectionChanged();
        }
        notifyDataSetChanged();
    }

    public int getSelectedCount() {
        return selectedNames.size();
    }

    public Set<String> getSelectedFileNames() {
        return new HashSet<>(selectedNames);
    }

    public void selectItem(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            selectedNames.add(fileName);
            notifySelectionChanged();
            notifyDataSetChanged();
        }
    }

    public void setSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public void setDeleteActionEnabled(boolean enabled) {
        deleteActionEnabled = enabled;
        notifyDataSetChanged();
    }

    public void replaceData(List<TemplateLibraryItem> items) {
        allItems.clear();
        shownItems.clear();
        selectedNames.clear();
        if (items != null) {
            allItems.addAll(items);
            shownItems.addAll(items);
        }
        notifySelectionChanged();
        notifyDataSetChanged();
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
        holder.tvName.setText(item.fileName);
        if (item.usageCount > 0) {
            holder.tvMeta.setText("引用节点: " + item.usageCount + " (受保护)");
            holder.tvMeta.setTextColor(0xFFB23B3B);
        } else {
            holder.tvMeta.setText("未被引用，可删除");
            holder.tvMeta.setTextColor(0xFF7A8794);
        }
        Bitmap bitmap = item.file == null ? null : BitmapFactory.decodeFile(item.file.getAbsolutePath());
        holder.ivImage.setImageBitmap(bitmap);
        holder.checkBox.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        holder.checkBox.setChecked(selectedNames.contains(item.fileName));
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
        if (selectedNames.contains(item.fileName)) {
            selectedNames.remove(item.fileName);
        } else {
            selectedNames.add(item.fileName);
        }
        boolean checked = selectedNames.contains(item.fileName);
        if (target != null) {
            target.updateChecked(checked);
        }
        notifySelectionChanged();
    }

    @Override
    public int getItemCount() {
        return shownItems.size();
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
