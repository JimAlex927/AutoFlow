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
        if (previousPosition >= 0 && nextPosition >= 0) {
            notifyItemChanged(previousPosition);
            if (previousPosition != nextPosition) {
                notifyItemChanged(nextPosition);
            }
        } else {
            notifyDataSetChanged();
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
        rebuildRows();
        notifyDataSetChanged();
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
        rebuildRows();
        notifyDataSetChanged();
    }

    private void rebuildRows() {
        rows.clear();
        for (MenuSection section : sections) {
            rows.add(new SectionRow(section));
            if (section.expanded) {
                for (MenuItem item : section.items) {
                    rows.add(new ItemRow(item));
                }
            }
        }
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
