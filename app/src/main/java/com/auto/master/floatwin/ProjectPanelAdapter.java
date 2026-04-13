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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ProjectPanelAdapter extends RecyclerView.Adapter<ProjectPanelAdapter.ViewHolder> {
    private static final int VIEW_TYPE_PROJECT = 1001;

    interface OnItemClickListener {
        void onItemClick(ProjectListItem item);
    }

    interface OnItemActionListener {
        void onMenuClick(ProjectListItem item, View anchor);
    }

    private final List<ProjectListItem> projects;
    private final OnItemClickListener listener;
    private final OnItemActionListener actionListener;

    ProjectPanelAdapter(
            List<ProjectListItem> projects,
            OnItemClickListener listener,
            OnItemActionListener actionListener
    ) {
        this.projects = new ArrayList<>(projects);
        this.listener = listener;
        this.actionListener = actionListener;
        setHasStableIds(true);
    }

    void submitProjects(List<ProjectListItem> items) {
        List<ProjectListItem> newItems = items == null ? Collections.emptyList() : new ArrayList<>(items);
        if (hasSameItems(newItems)) {
            return;
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return projects.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return TextUtils.equals(
                        projects.get(oldItemPosition).dir.getAbsolutePath(),
                        newItems.get(newItemPosition).dir.getAbsolutePath());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ProjectListItem oldItem = projects.get(oldItemPosition);
                ProjectListItem newItem = newItems.get(newItemPosition);
                return oldItem.taskCount == newItem.taskCount
                        && oldItem.lastModified == newItem.lastModified
                        && TextUtils.equals(oldItem.dir.getName(), newItem.dir.getName());
            }
        });
        projects.clear();
        projects.addAll(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    private boolean hasSameItems(List<ProjectListItem> newItems) {
        if (projects.size() != newItems.size()) {
            return false;
        }
        for (int i = 0; i < projects.size(); i++) {
            ProjectListItem oldItem = projects.get(i);
            ProjectListItem newItem = newItems.get(i);
            if (!TextUtils.equals(oldItem.dir.getAbsolutePath(), newItem.dir.getAbsolutePath())
                    || oldItem.taskCount != newItem.taskCount
                    || oldItem.lastModified != newItem.lastModified
                    || !TextUtils.equals(oldItem.dir.getName(), newItem.dir.getName())) {
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
        holder.bind(projects.get(position));
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    @Override
    public long getItemId(int position) {
        return projects.get(position).dir.getAbsolutePath().hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return VIEW_TYPE_PROJECT;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView info;
        private final ImageView moreOptions;
        private ProjectListItem currentProject;

        ViewHolder(View itemView, OnItemClickListener listener, OnItemActionListener actionListener) {
            super(itemView);
            name = itemView.findViewById(R.id.folder_name);
            info = itemView.findViewById(R.id.folder_info);
            moreOptions = itemView.findViewById(R.id.more_options);

            itemView.setOnClickListener(v -> {
                if (currentProject != null && listener != null) {
                    listener.onItemClick(currentProject);
                }
            });
            moreOptions.setOnClickListener(v -> {
                if (currentProject != null && actionListener != null) {
                    actionListener.onMenuClick(currentProject, v);
                }
            });
        }

        void bind(ProjectListItem project) {
            currentProject = project;
            name.setText(project.dir.getName());
            info.setVisibility(View.VISIBLE);
            info.setText(project.taskCount + " tasks");
            moreOptions.setVisibility(View.VISIBLE);
        }
    }
}
