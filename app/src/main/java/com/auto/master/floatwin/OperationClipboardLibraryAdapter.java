package com.auto.master.floatwin;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class OperationClipboardLibraryAdapter extends RecyclerView.Adapter<OperationClipboardLibraryAdapter.ViewHolder> {
    private static final Object PAYLOAD_SELECTION = new Object();
    interface OnRemoveListener {
        void onRemove(@Nullable OperationClipboardEntry entry);
    }

    private final List<OperationClipboardEntry> items;
    private final OnRemoveListener removeListener;
    private Runnable selectionChangedListener;
    private int selectedPosition;

    OperationClipboardLibraryAdapter(List<OperationClipboardEntry> items, OnRemoveListener removeListener) {
        this.items = items;
        this.removeListener = removeListener;
        this.selectedPosition = items.isEmpty() ? -1 : 0;
        setHasStableIds(true);
    }

    void setOnSelectionChanged(@Nullable Runnable selectionChangedListener) {
        this.selectionChangedListener = selectionChangedListener;
    }

    @Nullable
    OperationClipboardEntry getSelectedEntry() {
        if (selectedPosition < 0 || selectedPosition >= items.size()) {
            return null;
        }
        return items.get(selectedPosition);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_node_clipboard, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OperationClipboardEntry entry = items.get(position);
        holder.tvName.setText(entry.name);
        String sourceTask = TextUtils.isEmpty(entry.sourceTaskPath) ? "-" : new File(entry.sourceTaskPath).getName();
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(entry.createdAt));
        holder.tvMeta.setText("来源 Task: " + sourceTask + "  |  复制时间: " + time);
        bindSelection(holder, position == selectedPosition);

        holder.itemView.setOnClickListener(v -> {
            int bindingPosition = holder.getBindingAdapterPosition();
            if (bindingPosition < 0) {
                return;
            }
            int old = selectedPosition;
            selectedPosition = bindingPosition;
            if (old >= 0) {
                notifyItemChanged(old, PAYLOAD_SELECTION);
            }
            if (selectedPosition >= 0 && selectedPosition != old) {
                notifyItemChanged(selectedPosition, PAYLOAD_SELECTION);
            }
            if (selectionChangedListener != null) {
                selectionChangedListener.run();
            }
        });

        holder.btnRemove.setOnClickListener(v -> {
            int bindingPosition = holder.getBindingAdapterPosition();
            if (bindingPosition < 0 || bindingPosition >= items.size()) {
                return;
            }
            OperationClipboardEntry removed = items.get(bindingPosition);
            if (removeListener != null) {
                removeListener.onRemove(removed);
            }
            if (items.isEmpty()) {
                selectedPosition = -1;
            } else if (selectedPosition >= items.size()) {
                selectedPosition = items.size() - 1;
            } else if (bindingPosition == selectedPosition) {
                selectedPosition = Math.min(bindingPosition, items.size() - 1);
            }
            notifyItemRemoved(bindingPosition);
            if (selectedPosition >= 0) {
                notifyItemChanged(selectedPosition, PAYLOAD_SELECTION);
            }
            if (selectionChangedListener != null) {
                selectionChangedListener.run();
            }
        });
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            bindSelection(holder, position == selectedPosition);
            return;
        }
        onBindViewHolder(holder, position);
    }

    private void bindSelection(@NonNull ViewHolder holder, boolean selected) {
        holder.itemView.setBackgroundColor(selected ? 0xFFE8F0FE : Color.TRANSPARENT);
        holder.selectedIndicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        OperationClipboardEntry entry = items.get(position);
        if (entry == null) {
            return position;
        }
        return (String.valueOf(entry.createdAt) + "|" + entry.name + "|" + entry.sourceTaskPath).hashCode();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View selectedIndicator;
        final TextView tvName;
        final TextView tvMeta;
        final TextView btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            selectedIndicator = itemView.findViewById(R.id.view_selected);
            tvName = itemView.findViewById(R.id.tv_clipboard_name);
            tvMeta = itemView.findViewById(R.id.tv_clipboard_meta);
            btnRemove = itemView.findViewById(R.id.btn_remove);
        }
    }
}
