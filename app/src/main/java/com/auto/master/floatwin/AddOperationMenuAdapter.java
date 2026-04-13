package com.auto.master.floatwin;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.ArrayList;
import java.util.List;

final class AddOperationMenuAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface OnItemSelectedListener {
        void onItemSelected(@NonNull MenuItem item);
    }

    static final class MenuSection {
        final String title;
        final String description;
        final List<MenuItem> items;
        boolean expanded;

        MenuSection(@NonNull String title, String description, @NonNull List<MenuItem> items) {
            this.title = title;
            this.description = description;
            this.items = items;
        }
    }

    static final class MenuItem {
        final String id;
        final String label;
        final String description;
        final String badgeText;
        final int colorRes;
        final boolean enabled;

        MenuItem(@NonNull String id,
                 @NonNull String label,
                 String description,
                 @NonNull String badgeText,
                 @ColorRes int colorRes,
                 boolean enabled) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.badgeText = badgeText;
            this.colorRes = colorRes;
            this.enabled = enabled;
        }
    }

    private static final int VIEW_TYPE_SECTION = 0;
    private static final int VIEW_TYPE_ITEM = 1;

    private final LayoutInflater inflater;
    private final List<MenuSection> sections = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final OnItemSelectedListener itemSelectedListener;
    private String selectedItemId;

    AddOperationMenuAdapter(@NonNull Context context,
                            @NonNull List<MenuSection> sections,
                            OnItemSelectedListener itemSelectedListener) {
        this.inflater = LayoutInflater.from(context);
        this.itemSelectedListener = itemSelectedListener;
        setHasStableIds(true);
        this.sections.addAll(sections);
        rebuildRows();
    }

    void setSelectedItem(@NonNull MenuItem item) {
        String nextId = item.id;
        if (TextUtils.equals(selectedItemId, nextId)) {
            return;
        }
        ensureSectionExpanded(item.id);
        int previousPosition = findItemPosition(selectedItemId);
        int nextPosition = findItemPosition(nextId);
        selectedItemId = nextId;
        if (previousPosition >= 0) {
            notifyItemChanged(previousPosition);
        }
        if (nextPosition >= 0 && previousPosition != nextPosition) {
            notifyItemChanged(nextPosition);
        }
    }

    MenuItem getSelectedItem() {
        return findItemById(selectedItemId);
    }

    MenuItem findFirstEnabledItem() {
        for (MenuSection section : sections) {
            for (MenuItem item : section.items) {
                if (item.enabled) {
                    return item;
                }
            }
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return rows.get(position).stableId;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).viewType;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SECTION) {
            View view = inflater.inflate(R.layout.item_add_operation_section, parent, false);
            return new SectionViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_add_operation_option, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof SectionViewHolder) {
            ((SectionViewHolder) holder).bind((SectionRow) row);
            return;
        }
        ((ItemViewHolder) holder).bind((ItemRow) row, position);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    void collapseAllExcept(@NonNull String itemId) {
        for (MenuSection section : sections) {
            section.expanded = containsItem(section, itemId);
        }
        applyRebuiltRows();
    }

    void toggleSection(@NonNull MenuSection targetSection) {
        boolean shouldExpand = !targetSection.expanded;
        boolean changed = false;
        for (MenuSection section : sections) {
            boolean nextExpanded = section == targetSection && shouldExpand;
            if (section.expanded != nextExpanded) {
                section.expanded = nextExpanded;
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        applyRebuiltRows();
    }

    private void applyRebuiltRows() {
        List<Row> newRows = buildRowsSnapshot();
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new RowDiffCallback(rows, newRows));
        rows.clear();
        rows.addAll(newRows);
        diffResult.dispatchUpdatesTo(this);
    }

    private void rebuildRows() {
        rows.clear();
        rows.addAll(buildRowsSnapshot());
    }

    private List<Row> buildRowsSnapshot() {
        List<Row> snapshot = new ArrayList<>();
        for (MenuSection section : sections) {
            snapshot.add(new SectionRow(section));
            if (section.expanded) {
                for (MenuItem item : section.items) {
                    snapshot.add(new ItemRow(item));
                }
            }
        }
        return snapshot;
    }

    private int findItemPosition(String itemId) {
        if (TextUtils.isEmpty(itemId)) {
            return -1;
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row instanceof ItemRow) {
                MenuItem item = ((ItemRow) row).item;
                if (TextUtils.equals(item.id, itemId)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void ensureSectionExpanded(@NonNull String itemId) {
        boolean changed = false;
        for (MenuSection section : sections) {
            if (containsItem(section, itemId) && !section.expanded) {
                section.expanded = true;
                changed = true;
            }
        }
        if (changed) {
            rebuildRows();
        }
    }

    private boolean containsItem(@NonNull MenuSection section, @NonNull String itemId) {
        for (MenuItem item : section.items) {
            if (TextUtils.equals(item.id, itemId)) {
                return true;
            }
        }
        return false;
    }

    private MenuItem findItemById(String itemId) {
        if (TextUtils.isEmpty(itemId)) {
            return null;
        }
        for (MenuSection section : sections) {
            for (MenuItem item : section.items) {
                if (TextUtils.equals(item.id, itemId)) {
                    return item;
                }
            }
        }
        return null;
    }

    private abstract static class Row {
        final int viewType;
        final long stableId;

        Row(int viewType, long stableId) {
            this.viewType = viewType;
            this.stableId = stableId;
        }
    }

    private static final class SectionRow extends Row {
        final MenuSection section;

        SectionRow(@NonNull MenuSection section) {
            super(VIEW_TYPE_SECTION, ("section:" + section.title).hashCode());
            this.section = section;
        }
    }

    private static final class ItemRow extends Row {
        final MenuItem item;

        ItemRow(@NonNull MenuItem item) {
            super(VIEW_TYPE_ITEM, ("item:" + item.id).hashCode());
            this.item = item;
        }
    }

    private static final class RowDiffCallback extends DiffUtil.Callback {
        private final List<Row> oldRows;
        private final List<Row> newRows;

        RowDiffCallback(@NonNull List<Row> oldRows, @NonNull List<Row> newRows) {
            this.oldRows = oldRows;
            this.newRows = newRows;
        }

        @Override
        public int getOldListSize() {
            return oldRows.size();
        }

        @Override
        public int getNewListSize() {
            return newRows.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldRows.get(oldItemPosition).stableId == newRows.get(newItemPosition).stableId;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Row oldRow = oldRows.get(oldItemPosition);
            Row newRow = newRows.get(newItemPosition);
            if (oldRow.viewType != newRow.viewType) {
                return false;
            }
            if (oldRow instanceof SectionRow && newRow instanceof SectionRow) {
                MenuSection oldSection = ((SectionRow) oldRow).section;
                MenuSection newSection = ((SectionRow) newRow).section;
                return TextUtils.equals(oldSection.title, newSection.title)
                        && TextUtils.equals(oldSection.description, newSection.description)
                        && oldSection.expanded == newSection.expanded;
            }
            if (oldRow instanceof ItemRow && newRow instanceof ItemRow) {
                MenuItem oldItem = ((ItemRow) oldRow).item;
                MenuItem newItem = ((ItemRow) newRow).item;
                return TextUtils.equals(oldItem.id, newItem.id)
                        && TextUtils.equals(oldItem.label, newItem.label)
                        && TextUtils.equals(oldItem.description, newItem.description)
                        && TextUtils.equals(oldItem.badgeText, newItem.badgeText)
                        && oldItem.colorRes == newItem.colorRes
                        && oldItem.enabled == newItem.enabled;
            }
            return false;
        }
    }

    private final class SectionViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView descView;
        private final TextView toggleView;

        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.tv_section_title);
            descView = itemView.findViewById(R.id.tv_section_desc);
            toggleView = itemView.findViewById(R.id.tv_section_toggle);
        }

        void bind(@NonNull SectionRow row) {
            MenuSection section = row.section;
            titleView.setText(section.title);
            if (TextUtils.isEmpty(section.description)) {
                descView.setVisibility(View.GONE);
            } else {
                descView.setVisibility(View.VISIBLE);
                descView.setText(section.description);
            }
            toggleView.setText(section.expanded ? "收起" : "展开");
            itemView.setOnClickListener(v -> toggleSection(section));
        }
    }

    private final class ItemViewHolder extends RecyclerView.ViewHolder {
        private final View rootView;
        private final TextView badgeView;
        private final TextView labelView;
        private final TextView descView;
        private final TextView selectionIndexView;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            rootView = itemView.findViewById(R.id.item_root);
            badgeView = itemView.findViewById(R.id.tv_badge);
            labelView = itemView.findViewById(R.id.tv_label);
            descView = itemView.findViewById(R.id.tv_desc);
            selectionIndexView = itemView.findViewById(R.id.tv_selection_index);
        }

        void bind(@NonNull ItemRow row, int position) {
            MenuItem item = row.item;
            boolean selected = TextUtils.equals(selectedItemId, item.id);
            rootView.setSelected(selected);
            rootView.setEnabled(item.enabled);
            rootView.setAlpha(item.enabled ? 1f : 0.45f);

            badgeView.setText(item.badgeText);
            labelView.setText(item.label);
            if (TextUtils.isEmpty(item.description)) {
                descView.setVisibility(View.GONE);
            } else {
                descView.setVisibility(View.VISIBLE);
                descView.setText(item.description);
            }

            Drawable badgeBackground = badgeView.getBackground();
            if (badgeBackground instanceof GradientDrawable) {
                badgeBackground = badgeBackground.mutate();
                ((GradientDrawable) badgeBackground)
                        .setColor(ContextCompat.getColor(itemView.getContext(), item.colorRes));
                badgeView.setBackground(badgeBackground);
            }

            int badgeTextColor = ContextCompat.getColor(itemView.getContext(), R.color.colorOnPrimary);
            badgeView.setTextColor(badgeTextColor);

            selectionIndexView.setVisibility(selected ? View.VISIBLE : View.GONE);
            if (selected) {
                selectionIndexView.setText("1");
            }

            rootView.setOnClickListener(v -> {
                if (!item.enabled) {
                    return;
                }
                setSelectedItem(item);
                if (itemSelectedListener != null) {
                    itemSelectedListener.onItemSelected(item);
                }
            });
        }
    }
}
