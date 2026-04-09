package com.auto.master;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

/** Grid adapter for grid page. */
public class GridPageAdapter extends RecyclerView.Adapter<GridPageAdapter.ViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(GridAction action);
    }

    public enum GridAction {
        OPEN_ACCESSIBILITY,
        REQUEST_SNAPSHOT,
        TAKE_SNAPSHOT,
        RUN_SCRIPT,
        GO_BACK
    }

    public static class GridItem {
        public final String label;
        public final GridAction action;
        public GridItem(String label, GridAction action) {
            this.label = label;
            this.action = action;
        }
    }

    private final List<GridItem> items;
    private final OnItemClickListener listener;

    public GridPageAdapter(List<GridItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final Button btn;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            btn = itemView.findViewById(R.id.grid_item_btn);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grid_button, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GridItem item = items.get(position);
        holder.btn.setText(item.label);
        holder.btn.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item.action);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
