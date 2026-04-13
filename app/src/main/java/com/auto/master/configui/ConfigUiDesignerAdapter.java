package com.auto.master.configui;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigUiDesignerAdapter extends RecyclerView.Adapter<ConfigUiDesignerAdapter.ViewHolder> {
    public interface Listener {
        void onEdit(ConfigUiComponent component);
        void onDelete(ConfigUiComponent component);
        void onStartDrag(RecyclerView.ViewHolder holder);
    }

    private final List<ConfigUiComponent> components = new ArrayList<>();
    private final Listener listener;

    public ConfigUiDesignerAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitComponents(List<ConfigUiComponent> items) {
        List<ConfigUiComponent> nextItems = new ArrayList<>();
        if (items != null) {
            for (ConfigUiComponent item : items) {
                if (item != null) {
                    item.ensureDefaults();
                    nextItems.add(item);
                }
            }
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new SimpleDiffCallback(components, nextItems));
        components.clear();
        components.addAll(nextItems);
        diff.dispatchUpdatesTo(this);
    }

    public boolean moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0
                || fromPosition >= components.size()
                || toPosition >= components.size()) {
            return false;
        }
        if (fromPosition == toPosition) {
            return false;
        }
        Collections.swap(components, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_config_ui_component, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConfigUiComponent component = components.get(position);
        component.ensureDefaults();
        holder.title.setText(component.label == null || component.label.trim().isEmpty()
                ? component.getDisplayTypeName()
                : component.label);
        String key = component.fieldKey == null ? "" : component.fieldKey.trim();
        if (key.isEmpty()) {
            holder.meta.setText(component.getDisplayTypeName() + "  ·  " + component.getDisplayBehaviorName());
        } else {
            holder.meta.setText(component.getDisplayTypeName() + "  ·  " + key + "  ·  " + component.getDisplayBehaviorName());
        }
        holder.editBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEdit(component);
            }
        });
        holder.deleteBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(component);
            }
        });
        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && listener != null) {
                listener.onStartDrag(holder);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return components.size();
    }

    @Override
    public long getItemId(int position) {
        ConfigUiComponent component = components.get(position);
        component.ensureDefaults();
        return component.id.hashCode();
    }

    private static final class SimpleDiffCallback extends DiffUtil.Callback {
        private final List<ConfigUiComponent> oldItems;
        private final List<ConfigUiComponent> newItems;

        SimpleDiffCallback(List<ConfigUiComponent> oldItems, List<ConfigUiComponent> newItems) {
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
            ConfigUiComponent oldItem = oldItems.get(oldItemPosition);
            ConfigUiComponent newItem = newItems.get(newItemPosition);
            oldItem.ensureDefaults();
            newItem.ensureDefaults();
            return oldItem.id.equals(newItem.id);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            ConfigUiComponent oldItem = oldItems.get(oldItemPosition);
            ConfigUiComponent newItem = newItems.get(newItemPosition);
            oldItem.ensureDefaults();
            newItem.ensureDefaults();
            return oldItem.type.equals(newItem.type)
                    && oldItem.label.equals(newItem.label)
                    && oldItem.fieldKey.equals(newItem.fieldKey)
                    && oldItem.getDisplayBehaviorName().equals(newItem.getDisplayBehaviorName());
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final TextView editBtn;
        final TextView deleteBtn;
        final ImageView dragHandle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_component_title);
            meta = itemView.findViewById(R.id.tv_component_meta);
            editBtn = itemView.findViewById(R.id.btn_component_edit);
            deleteBtn = itemView.findViewById(R.id.btn_component_delete);
            dragHandle = itemView.findViewById(R.id.iv_component_drag);
        }
    }
}
