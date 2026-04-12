package com.auto.master.floatwin;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.List;

class RunningPanelAdapter extends RecyclerView.Adapter<RunningPanelAdapter.ViewHolder> {
    private List<OperationItem> operations;
    private int runningPosition = -1;

    RunningPanelAdapter(List<OperationItem> operations) {
        this.operations = operations;
    }

    public void setOperations(List<OperationItem> operations) {
        this.operations = operations;
        notifyDataSetChanged();
    }

    public void setRunningPosition(int position) {
        int prev = runningPosition;
        runningPosition = position;
        if (prev >= 0) {
            notifyItemChanged(prev);
        }
        if (position >= 0) {
            notifyItemChanged(position);
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
        if (position == runningPosition) {
            holder.itemView.setBackgroundColor(0x66EF9A9A);
            holder.indicator.setVisibility(View.VISIBLE);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            holder.indicator.setVisibility(View.GONE);
        }
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
