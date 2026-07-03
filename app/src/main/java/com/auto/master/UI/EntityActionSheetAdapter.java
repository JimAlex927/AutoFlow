package com.auto.master.ui;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EntityActionSheetAdapter extends RecyclerView.Adapter<EntityActionSheetAdapter.ViewHolder> {
    public interface OnActionClickListener {
        void onActionClick(@NonNull Item item);
    }

    public static final class Item {
        public final int id;
        @DrawableRes
        public final int iconRes;
        public final String title;
        public final String desc;
        public final boolean enabled;

        public Item(int id, @DrawableRes int iconRes, String title, String desc, boolean enabled) {
            this.id = id;
            this.iconRes = iconRes;
            this.title = title;
            this.desc = desc;
            this.enabled = enabled;
        }

        boolean isSameItem(@NonNull Item other) {
            return id == other.id;
        }

        boolean hasSameContent(@NonNull Item other) {
            return iconRes == other.iconRes
                    && enabled == other.enabled
                    && TextUtils.equals(title, other.title)
                    && TextUtils.equals(desc, other.desc);
        }
    }

    private final List<Item> items = new ArrayList<>();
    private OnActionClickListener listener;

    public EntityActionSheetAdapter() {
        setHasStableIds(true);
    }

    public void submitItems(List<Item> nextItems, OnActionClickListener nextListener) {
        List<Item> targetItems = nextItems == null ? Collections.emptyList() : new ArrayList<>(nextItems);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return targetItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return items.get(oldItemPosition).isSameItem(targetItems.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return items.get(oldItemPosition).hasSameContent(targetItems.get(newItemPosition));
            }
        });
        items.clear();
        items.addAll(targetItems);
        listener = nextListener;
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_entity_action_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = items.get(position);
        holder.icon.setImageResource(item.iconRes);
        holder.title.setText(item.title);
        if (TextUtils.isEmpty(item.desc)) {
            holder.desc.setVisibility(View.GONE);
        } else {
            holder.desc.setVisibility(View.VISIBLE);
            holder.desc.setText(item.desc);
        }
        holder.itemView.setAlpha(item.enabled ? 1f : 0.38f);
        holder.itemView.setOnClickListener(v -> {
            if (item.enabled && listener != null) {
                listener.onActionClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView desc;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_entity_action_icon);
            title = itemView.findViewById(R.id.tv_entity_action_title);
            desc = itemView.findViewById(R.id.tv_entity_action_desc);
        }
    }
}
