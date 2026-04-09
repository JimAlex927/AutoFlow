package com.auto.master.floatwin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.io.File;
import java.util.List;

class ProjectPanelAdapter extends RecyclerView.Adapter<ProjectPanelAdapter.ViewHolder> {
    interface OnItemClickListener {
        void onItemClick(File file);
    }

    private final List<File> projects;
    private final OnItemClickListener listener;

    ProjectPanelAdapter(List<File> projects, OnItemClickListener listener) {
        this.projects = projects;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project_folder_panel, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File project = projects.get(position);
        holder.name.setText(project.getName());
        holder.itemView.setOnClickListener(v -> listener.onItemClick(project));
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.folder_name);
        }
    }
}
