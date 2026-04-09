package com.auto.master.floatwin.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;
import com.auto.master.auto.GestureOverlayView;
import com.auto.master.floatwin.GesturePreviewView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GestureLibraryAdapter extends RecyclerView.Adapter<GestureLibraryAdapter.ViewHolder> {

    public interface OnPickListener {
        void onPick(GestureLibraryItem item);
    }

    public static class GestureLibraryItem {
        public String fileName;
        public File file;
        public GestureOverlayView.GestureNode node;
        public long duration;
        public int strokeCount;

        public GestureLibraryItem(String fileName, File file, GestureOverlayView.GestureNode node) {
            this.fileName = fileName;
            this.file = file;
            this.node = node;
            this.duration = node == null ? 0L : node.duration;
            this.strokeCount = (node == null || node.strokes == null) ? 0 : node.strokes.size();
        }
    }

    private final List<GestureLibraryItem> allItems = new ArrayList<>();
    private final List<GestureLibraryItem> shownItems = new ArrayList<>();
    private final OnPickListener listener;

    public GestureLibraryAdapter(List<GestureLibraryItem> items, OnPickListener listener) {
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
            for (GestureLibraryItem item : allItems) {
                String name = item.fileName == null ? "" : item.fileName.toLowerCase(Locale.ROOT);
                if (name.contains(q)) {
                    shownItems.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gesture_library, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GestureLibraryItem item = shownItems.get(position);
        holder.tvName.setText(item.fileName);
        if (item.node == null) {
            holder.tvMeta.setText("手势文件损坏或为空");
            holder.tvMeta.setTextColor(0xFFB23B3B);
        } else {
            holder.tvMeta.setText("轨迹: " + item.strokeCount + " | 时长: " + item.duration + "ms");
            holder.tvMeta.setTextColor(0xFF7A8794);
        }
        holder.previewView.setGestureNode(item.node);
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
        GesturePreviewView previewView;
        TextView tvName;
        TextView tvMeta;

        ViewHolder(View itemView) {
            super(itemView);
            previewView = itemView.findViewById(R.id.view_gesture_preview);
            tvName = itemView.findViewById(R.id.tv_gesture_item_name);
            tvMeta = itemView.findViewById(R.id.tv_gesture_item_meta);
        }
    }
}
