package com.auto.master.floatwin;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class RunningPanelAdapter extends RecyclerView.Adapter<RunningPanelAdapter.ViewHolder> {
    private static final Object PAYLOAD_RUNNING_STATE = new Object();
    private List<OperationItem> operations;
    private int runningPosition = -1;

    RunningPanelAdapter(List<OperationItem> operations) {
        this.operations = operations == null ? Collections.emptyList() : new ArrayList<>(operations);
        setHasStableIds(true);
    }

    public void setOperations(List<OperationItem> operations) {
        List<OperationItem> nextItems = operations == null ? Collections.emptyList() : new ArrayList<>(operations);
        List<OperationItem> oldItems = this.operations == null ? Collections.emptyList() : new ArrayList<>(this.operations);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return nextItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                OperationItem oldItem = oldItems.get(oldItemPosition);
                OperationItem newItem = nextItems.get(newItemPosition);
                return oldItem != null
                        && newItem != null
                        && android.text.TextUtils.equals(oldItem.id, newItem.id);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                OperationItem oldItem = oldItems.get(oldItemPosition);
                OperationItem newItem = nextItems.get(newItemPosition);
                return oldItem != null
                        && newItem != null
                        && android.text.TextUtils.equals(oldItem.id, newItem.id)
                        && android.text.TextUtils.equals(oldItem.name, newItem.name)
                        && android.text.TextUtils.equals(oldItem.type, newItem.type)
                        && oldItem.index == newItem.index;
            }
        });
        this.operations = nextItems;
        if (runningPosition >= this.operations.size()) {
            runningPosition = -1;
        }
        diffResult.dispatchUpdatesTo(this);
    }

    public void setRunningPosition(int position) {
        int prev = runningPosition;
        runningPosition = position;
        if (prev >= 0 && prev < getItemCount()) {
            notifyItemChanged(prev, PAYLOAD_RUNNING_STATE);
        }
        if (position >= 0 && position < getItemCount()) {
            notifyItemChanged(position, PAYLOAD_RUNNING_STATE);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_operation_simple, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OperationItem item = operations.get(position);
        holder.tvName.setText(item.name);
        holder.tvType.setText(item.type);
        bindRunningState(holder, position == runningPosition);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            bindRunningState(holder, position == runningPosition);
            return;
        }
        onBindViewHolder(holder, position);
    }

    private void bindRunningState(@NonNull ViewHolder holder, boolean isRunning) {
        if (isRunning) {
            holder.itemView.setBackgroundColor(0x66EF9A9A);
            holder.indicator.setVisibility(View.VISIBLE);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            holder.indicator.setVisibility(View.GONE);
        }
    }

    @Override
    public long getItemId(int position) {
        OperationItem item = operations.get(position);
        return item != null && item.id != null ? item.id.hashCode() : position;
    }

    @Override
    public int getItemCount() {
        return operations != null ? operations.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvType;
        final View indicator;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_op_name);
            tvType = itemView.findViewById(R.id.tv_op_type);
            indicator = itemView.findViewById(R.id.running_indicator);
        }
    }
}
