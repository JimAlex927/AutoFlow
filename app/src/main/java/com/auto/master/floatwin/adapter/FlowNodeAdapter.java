package com.auto.master.floatwin.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FlowNodeAdapter extends RecyclerView.Adapter<FlowNodeAdapter.ViewHolder> {

    public interface OnNodeClickListener {
        void onClick(FlowNodeItem item);
    }

    public interface OnSelectionChangedListener {
        void onChanged(int position);
    }

    public static class FlowNodeItem {
        public int order;
        public String id;
        public String name;
        public String type;
        public String nextId;
        public String fallbackId;

        public FlowNodeItem(int order, String id, String name, String type, String nextId, String fallbackId) {
            this.order = order;
            this.id = id;
            this.name = name;
            this.type = type;
            this.nextId = nextId;
            this.fallbackId = fallbackId;
        }
    }

    private final List<FlowNodeItem> nodes;
    private final OnNodeClickListener listener;
    private OnSelectionChangedListener selectionChangedListener;
    private int selectedPosition = -1;

    public FlowNodeAdapter(List<FlowNodeItem> nodes, OnNodeClickListener listener) {
        this.nodes = nodes == null ? new ArrayList<>() : nodes;
        this.listener = listener;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public boolean moveSelectedBy(int delta) {
        if (selectedPosition < 0) {
            return false;
        }
        int target = selectedPosition + delta;
        if (target < 0 || target >= nodes.size()) {
            return false;
        }
        swapItems(selectedPosition, target);
        selectedPosition = target;
        notifyItemChanged(selectedPosition);
        notifySelectionChanged();
        return true;
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onChanged(selectedPosition);
        }
    }

    public void swapItems(int from, int to) {
        if (from < 0 || to < 0 || from >= nodes.size() || to >= nodes.size()) {
            return;
        }
        java.util.Collections.swap(nodes, from, to);
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).order = i + 1;
        }
        notifyItemMoved(from, to);
        notifyItemChanged(from);
        notifyItemChanged(to);

        if (selectedPosition == from) {
            selectedPosition = to;
        } else if (selectedPosition == to) {
            selectedPosition = from;
        }
        notifySelectionChanged();
    }

    public List<String> getOrderedIds() {
        List<String> ids = new ArrayList<>();
        for (FlowNodeItem node : nodes) {
            if (!TextUtils.isEmpty(node.id)) {
                ids.add(node.id);
            }
        }
        return ids;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_flow_node, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FlowNodeItem item = nodes.get(position);
        holder.tvTitle.setText(String.format(Locale.getDefault(), "%02d  %s", item.order, item.name));
        holder.tvMeta.setText(item.id + " | " + item.type);
        holder.tvNext.setText("主线  →  " + (TextUtils.isEmpty(item.nextId) ? "(空)" : item.nextId));
        holder.tvFallback.setText("分支  ↘  " + (TextUtils.isEmpty(item.fallbackId) ? "(空)" : item.fallbackId));

        boolean hasMain = !TextUtils.isEmpty(item.nextId);
        boolean hasBranch = !TextUtils.isEmpty(item.fallbackId);
        if (hasBranch) {
            holder.indicator.setBackgroundColor(0xFFB23B3B);
            holder.tvFallback.setTextColor(0xFFB23B3B);
        } else if (hasMain) {
            holder.indicator.setBackgroundColor(0xFF4CAF50);
            holder.tvFallback.setTextColor(0xFF95A2AF);
        } else {
            holder.indicator.setBackgroundColor(0xFF8D9AA9);
            holder.tvFallback.setTextColor(0xFF95A2AF);
        }
        boolean selected = position == selectedPosition;
        holder.itemView.setAlpha(selected ? 1f : 0.88f);
        holder.itemView.setScaleX(selected ? 1.02f : 1f);
        holder.itemView.setScaleY(selected ? 1.02f : 1f);
        holder.itemView.setOnClickListener(v -> {
            int old = selectedPosition;
            selectedPosition = holder.getBindingAdapterPosition();
            if (old >= 0) {
                notifyItemChanged(old);
            }
            notifyItemChanged(selectedPosition);
            notifySelectionChanged();
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onClick(item);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return nodes.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvMeta;
        TextView tvNext;
        TextView tvFallback;
        View indicator;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_flow_title);
            tvMeta = itemView.findViewById(R.id.tv_flow_meta);
            tvNext = itemView.findViewById(R.id.tv_flow_next);
            tvFallback = itemView.findViewById(R.id.tv_flow_fallback);
            indicator = itemView.findViewById(R.id.flow_indicator);
        }
    }
}
