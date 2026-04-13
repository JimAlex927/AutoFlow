package com.auto.master.floatwin.adapter;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LaunchAppPickerAdapter extends RecyclerView.Adapter<LaunchAppPickerAdapter.ViewHolder> {

    public interface OnPickListener {
        void onPick(LaunchAppItem item);
    }

    public static class LaunchAppItem {
        public final String label;
        public final String packageName;
        public final Drawable icon;

        public LaunchAppItem(String label, String packageName, @Nullable Drawable icon) {
            this.label = label == null ? "" : label;
            this.packageName = packageName == null ? "" : packageName;
            this.icon = icon;
        }
    }

    private final List<LaunchAppItem> allItems = new ArrayList<>();
    private final List<LaunchAppItem> shownItems = new ArrayList<>();
    private final OnPickListener listener;

    public LaunchAppPickerAdapter(List<LaunchAppItem> items, OnPickListener listener) {
        if (items != null) {
            allItems.addAll(items);
            shownItems.addAll(items);
        }
        this.listener = listener;
        setHasStableIds(true);
    }

    public void updateFilter(String query) {
        List<LaunchAppItem> filtered = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(q)) {
            filtered.addAll(allItems);
        } else {
            for (LaunchAppItem item : allItems) {
                String label = item.label == null ? "" : item.label.toLowerCase(Locale.ROOT);
                String packageName = item.packageName == null ? "" : item.packageName.toLowerCase(Locale.ROOT);
                if (label.contains(q) || packageName.contains(q)) {
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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_picker, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LaunchAppItem item = shownItems.get(position);
        holder.tvAppName.setText(item.label);
        holder.tvAppPackage.setText(item.packageName);
        holder.ivAppIcon.setImageDrawable(item.icon);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return shownItems.size();
    }

    @Override
    public long getItemId(int position) {
        return shownItems.get(position).packageName.hashCode();
    }

    private static final class SimpleDiffCallback extends DiffUtil.Callback {
        private final List<LaunchAppItem> oldItems;
        private final List<LaunchAppItem> newItems;

        SimpleDiffCallback(List<LaunchAppItem> oldItems, List<LaunchAppItem> newItems) {
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
            return TextUtils.equals(oldItems.get(oldItemPosition).packageName,
                    newItems.get(newItemPosition).packageName);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            LaunchAppItem oldItem = oldItems.get(oldItemPosition);
            LaunchAppItem newItem = newItems.get(newItemPosition);
            return TextUtils.equals(oldItem.label, newItem.label)
                    && TextUtils.equals(oldItem.packageName, newItem.packageName)
                    && oldItem.icon == newItem.icon;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivAppIcon;
        final TextView tvAppName;
        final TextView tvAppPackage;

        ViewHolder(View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.iv_app_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvAppPackage = itemView.findViewById(R.id.tv_app_package);
        }
    }
}
