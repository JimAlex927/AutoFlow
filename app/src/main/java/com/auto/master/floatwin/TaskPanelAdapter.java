package com.auto.master.floatwin;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class TaskPanelAdapter extends RecyclerView.Adapter<TaskPanelAdapter.ViewHolder> {
    private static final int VIEW_TYPE_TASK = 1002;

    interface OnItemClickListener {
        void onItemClick(File file);
    }

    interface OnItemActionListener {
        void onMenuClick(File file, View anchor);
    }

    private final List<File> items;
    private final OnItemClickListener listener;
    private final OnItemActionListener actionListener;

    TaskPanelAdapter(
            List<File> items,
            OnItemClickListener listener,
            OnItemActionListener actionListener
    ) {
        this.items = new ArrayList<>(items);
        this.listener = listener;
        this.actionListener = actionListener;
        setHasStableIds(true);
    }

    void submitItems(List<File> newItems) {
        List<File> targetItems = newItems == null ? Collections.emptyList() : new ArrayList<>(newItems);
        if (hasSameItems(targetItems)) {
            return;
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return targetItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return TextUtils.equals(
                        items.get(oldItemPosition).getAbsolutePath(),
                        targetItems.get(newItemPosition).getAbsolutePath());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                File oldItem = items.get(oldItemPosition);
                File newItem = targetItems.get(newItemPosition);
                return oldItem.isDirectory() == newItem.isDirectory()
                        && oldItem.lastModified() == newItem.lastModified()
                        && oldItem.length() == newItem.length()
                        && TextUtils.equals(oldItem.getName(), newItem.getName());
            }
        });
        items.clear();
        items.addAll(targetItems);
        diffResult.dispatchUpdatesTo(this);
    }

    private boolean hasSameItems(List<File> newItems) {
        if (items.size() != newItems.size()) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            File oldItem = items.get(i);
            File newItem = newItems.get(i);
            if (!TextUtils.equals(oldItem.getAbsolutePath(), newItem.getAbsolutePath())
                    || oldItem.isDirectory() != newItem.isDirectory()
                    || oldItem.lastModified() != newItem.lastModified()
                    || oldItem.length() != newItem.length()
                    || !TextUtils.equals(oldItem.getName(), newItem.getName())) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project_folder_panel, parent, false);
        return new ViewHolder(view, listener, actionListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getAbsolutePath().hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return VIEW_TYPE_TASK;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView name;
        private final TextView info;
        private final ImageView editIcon;
        private final ImageView moreOptions;
        private File currentFile;

        ViewHolder(View itemView, OnItemClickListener listener, OnItemActionListener actionListener) {
            super(itemView);
            icon = itemView.findViewById(R.id.folder_icon);
            name = itemView.findViewById(R.id.folder_name);
            info = itemView.findViewById(R.id.folder_info);
            editIcon = itemView.findViewById(R.id.edit_icon);
            moreOptions = itemView.findViewById(R.id.more_options);

            itemView.setOnClickListener(v -> {
                if (currentFile != null && listener != null) {
                    listener.onItemClick(currentFile);
                }
            });
            moreOptions.setOnClickListener(v -> {
                if (currentFile != null && currentFile.isDirectory() && actionListener != null) {
                    actionListener.onMenuClick(currentFile, v);
                }
            });
        }

        void bind(File file) {
            currentFile = file;
            name.setText(file.getName());
            info.setVisibility(View.GONE);
            if (file.isDirectory()) {
                icon.setImageResource(R.drawable.ic_folder_colored);
                editIcon.setVisibility(View.GONE);
                moreOptions.setVisibility(View.VISIBLE);
            } else {
                icon.setImageResource(R.drawable.ic_file);
                editIcon.setVisibility(View.VISIBLE);
                moreOptions.setVisibility(View.GONE);
            }
        }
    }
}
