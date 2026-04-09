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

public class OperationIdPickerAdapter extends RecyclerView.Adapter<OperationIdPickerAdapter.ViewHolder> {

    public interface OnPickListener {
        void onPick(String id);
    }

    public static class OperationPickItem {
        public int order;
        public String id;
        public String name;
        public String type;

        public OperationPickItem(int order, String id, String name, String type) {
            this.order = order;
            this.id = id;
            this.name = name;
            this.type = type;
        }
    }

    private final List<OperationPickItem> allItems = new ArrayList<>();
    private final List<OperationPickItem> shownItems = new ArrayList<>();
    private final OnPickListener listener;
    private String selectedOperationId;

    public OperationIdPickerAdapter(List<OperationPickItem> items,
                                    String selectedOperationId,
                                    OnPickListener listener) {
        if (items != null) {
            allItems.addAll(items);
            shownItems.addAll(items);
        }
        this.selectedOperationId = selectedOperationId;
        this.listener = listener;
    }

    public void updateSelectedOperation(String selectedOperationId) {
        this.selectedOperationId = selectedOperationId;
        notifyDataSetChanged();
    }

    public void updateFilter(String query) {
        shownItems.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(q)) {
            shownItems.addAll(allItems);
        } else {
            for (OperationPickItem item : allItems) {
                String order = String.valueOf(item.order);
                String id = item.id == null ? "" : item.id.toLowerCase(Locale.ROOT);
                String name = item.name == null ? "" : item.name.toLowerCase(Locale.ROOT);
                String type = item.type == null ? "" : item.type.toLowerCase(Locale.ROOT);
                if (order.contains(q) || id.contains(q) || name.contains(q) || type.contains(q)) {
                    shownItems.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_operation_picker, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OperationPickItem item = shownItems.get(position);
        boolean selected = !TextUtils.isEmpty(selectedOperationId)
                && TextUtils.equals(selectedOperationId, item.id);
        holder.itemView.setSelected(selected);
        holder.tvId.setText(String.format(Locale.getDefault(), "#%02d  %s", item.order, item.name));
        holder.tvMeta.setText(item.type + "  |  " + item.id);
        holder.tvId.setTextColor(selected ? 0xFF1F4AA8 : 0xFF253342);
        holder.tvMeta.setTextColor(selected ? 0xFF315DBF : 0xFF6A7682);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPick(item.id);
            }
        });
    }

    @Override
    public int getItemCount() {
        return shownItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvId;
        TextView tvMeta;

        ViewHolder(View itemView) {
            super(itemView);
            tvId = itemView.findViewById(R.id.tv_operation_id);
            tvMeta = itemView.findViewById(R.id.tv_operation_meta);
        }
    }
}
