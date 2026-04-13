package com.auto.master.floatwin.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OperationIdPickerAdapter extends RecyclerView.Adapter<OperationIdPickerAdapter.ViewHolder> {
    private static final Object PAYLOAD_SELECTION = new Object();

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
        setHasStableIds(true);
    }

    public void updateSelectedOperation(String selectedOperationId) {
        String previousId = this.selectedOperationId;
        this.selectedOperationId = selectedOperationId;
        int previous = findPositionById(previousId);
        int current = findPositionById(selectedOperationId);
        if (previous >= 0) {
            notifyItemChanged(previous, PAYLOAD_SELECTION);
        }
        if (current >= 0 && current != previous) {
            notifyItemChanged(current, PAYLOAD_SELECTION);
        }
    }

    public void updateFilter(String query) {
        List<OperationPickItem> filtered = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(q)) {
            filtered.addAll(allItems);
        } else {
            for (OperationPickItem item : allItems) {
                String order = String.valueOf(item.order);
                String id = item.id == null ? "" : item.id.toLowerCase(Locale.ROOT);
                String name = item.name == null ? "" : item.name.toLowerCase(Locale.ROOT);
                String type = item.type == null ? "" : item.type.toLowerCase(Locale.ROOT);
                if (order.contains(q) || id.contains(q) || name.contains(q) || type.contains(q)) {
                    filtered.add(item);
                }
            }
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new SimpleDiffCallback(shownItems, filtered));
        shownItems.clear();
        shownItems.addAll(filtered);
        diff.dispatchUpdatesTo(this);
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
        bindSelection(holder, item);
        holder.tvId.setText(String.format(Locale.getDefault(), "#%02d  %s", item.order, item.name));
        holder.tvMeta.setText(item.type + "  |  " + item.id);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPick(item.id);
            }
        });
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            bindSelection(holder, shownItems.get(position));
            return;
        }
        onBindViewHolder(holder, position);
    }

    private void bindSelection(@NonNull ViewHolder holder, @NonNull OperationPickItem item) {
        boolean selected = !TextUtils.isEmpty(selectedOperationId)
                && TextUtils.equals(selectedOperationId, item.id);
        holder.itemView.setSelected(selected);
        holder.tvId.setTextColor(selected ? 0xFF1F4AA8 : 0xFF253342);
        holder.tvMeta.setTextColor(selected ? 0xFF315DBF : 0xFF6A7682);
    }

    @Override
    public int getItemCount() {
        return shownItems.size();
    }

    @Override
    public long getItemId(int position) {
        OperationPickItem item = shownItems.get(position);
        return item.id == null ? position : item.id.hashCode();
    }

    private int findPositionById(String id) {
        if (TextUtils.isEmpty(id)) {
            return -1;
        }
        for (int i = 0; i < shownItems.size(); i++) {
            if (TextUtils.equals(id, shownItems.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private static final class SimpleDiffCallback extends DiffUtil.Callback {
        private final List<OperationPickItem> oldItems;
        private final List<OperationPickItem> newItems;

        SimpleDiffCallback(List<OperationPickItem> oldItems, List<OperationPickItem> newItems) {
            this.oldItems = oldItems;
            this.newItems = newItems;
        }

        @Override
        public int getOldListSize() {
            return oldItems.size();
        }

        @Override
        public int getNewListSize() {
            return newItems.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return TextUtils.equals(oldItems.get(oldItemPosition).id, newItems.get(newItemPosition).id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            OperationPickItem oldItem = oldItems.get(oldItemPosition);
            OperationPickItem newItem = newItems.get(newItemPosition);
            return oldItem.order == newItem.order
                    && TextUtils.equals(oldItem.id, newItem.id)
                    && TextUtils.equals(oldItem.name, newItem.name)
                    && TextUtils.equals(oldItem.type, newItem.type);
        }
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
