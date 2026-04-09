package com.auto.master.floatwin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.io.File;
import java.util.List;

class TaskPanelAdapter extends RecyclerView.Adapter<TaskPanelAdapter.ViewHolder> {
    interface OnItemClickListener {
        void onItemClick(File file);
    }

    private final List<File> items;
    private final OnItemClickListener listener;

    TaskPanelAdapter(List<File> items, OnItemClickListener listener) {
        this.items = items;
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
        File file = items.get(position);
        holder.name.setText(file.getName());

        if (file.isDirectory()) {
            holder.icon.setImageResource(R.drawable.ic_folder_colored);
            holder.editIcon.setVisibility(View.GONE);
        } else {
            holder.icon.setImageResource(R.drawable.ic_file);
            holder.editIcon.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(file));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;
        ImageView editIcon;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.folder_icon);
            name = itemView.findViewById(R.id.folder_name);
            editIcon = itemView.findViewById(R.id.edit_icon);
        }
    }
}
