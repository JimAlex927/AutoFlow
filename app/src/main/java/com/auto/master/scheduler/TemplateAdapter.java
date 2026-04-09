package com.auto.master.scheduler;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.List;

public class TemplateAdapter extends RecyclerView.Adapter<TemplateAdapter.ViewHolder> {

    public interface OnTemplateClick {
        void onClick(ProjectTemplate template);
    }

    private final List<ProjectTemplate> templates;
    private final OnTemplateClick listener;

    public TemplateAdapter(List<ProjectTemplate> templates, OnTemplateClick listener) {
        this.templates = templates;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_template_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ProjectTemplate t = templates.get(position);
        holder.emoji.setText(t.emoji);
        holder.name.setText(t.name);
        holder.desc.setText(t.description);
        holder.category.setText(t.category);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(t);
        });
    }

    @Override
    public int getItemCount() {
        return templates.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView emoji, name, desc, category;

        ViewHolder(View v) {
            super(v);
            emoji = v.findViewById(R.id.tv_template_emoji);
            name = v.findViewById(R.id.tv_template_name);
            desc = v.findViewById(R.id.tv_template_desc);
            category = v.findViewById(R.id.tv_template_category);
        }
    }
}
