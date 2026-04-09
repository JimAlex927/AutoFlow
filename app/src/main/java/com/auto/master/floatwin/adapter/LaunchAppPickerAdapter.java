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
    }

    public void updateFilter(String query) {
        shownItems.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(q)) {
            shownItems.addAll(allItems);
        } else {
            for (LaunchAppItem item : allItems) {
                String label = item.label == null ? "" : item.label.toLowerCase(Locale.ROOT);
                String packageName = item.packageName == null ? "" : item.packageName.toLowerCase(Locale.ROOT);
                if (label.contains(q) || packageName.contains(q)) {
                    shownItems.add(item);
                }
            }
        }
        notifyDataSetChanged();
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
