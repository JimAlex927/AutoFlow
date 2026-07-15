package com.auto.master.floatwin;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.ArrayList;
import java.util.List;

/** A scrollable catalogue of operation categories with independently collapsible sections. */
final class AddOperationMenuAdapter extends RecyclerView.Adapter<AddOperationMenuAdapter.SectionViewHolder> {

    interface OnItemSelectedListener {
        void onItemSelected(@NonNull MenuItem item);
    }

    static final class MenuSection {
        final String title;
        final String description;
        final List<MenuItem> items;
        boolean expanded = true;

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

    private final LayoutInflater inflater;
    private final List<MenuSection> sections = new ArrayList<>();
    private final OnItemSelectedListener itemSelectedListener;
    private String selectedItemId;

    AddOperationMenuAdapter(@NonNull Context context,
                            @NonNull List<MenuSection> sections,
                            OnItemSelectedListener itemSelectedListener) {
        inflater = LayoutInflater.from(context);
        this.sections.addAll(sections);
        this.itemSelectedListener = itemSelectedListener;
        setHasStableIds(true);
    }

    void setSelectedItem(@NonNull MenuItem item) {
        if (TextUtils.equals(selectedItemId, item.id)) {
            return;
        }
        selectedItemId = item.id;
        notifyDataSetChanged();
    }

    MenuItem getSelectedItem() {
        for (MenuSection section : sections) {
            for (MenuItem item : section.items) {
                if (TextUtils.equals(item.id, selectedItemId)) {
                    return item;
                }
            }
        }
        return null;
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
        return ("section:" + sections.get(position).title).hashCode();
    }

    @NonNull
    @Override
    public SectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SectionViewHolder(inflater.inflate(R.layout.item_add_operation_section, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull SectionViewHolder holder, int position) {
        holder.bind(sections.get(position));
    }

    @Override
    public int getItemCount() {
        return sections.size();
    }

    final class SectionViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView descView;
        private final TextView toggleView;
        private final View headerView;
        private final GridLayout gridView;

        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.tv_section_title);
            descView = itemView.findViewById(R.id.tv_section_desc);
            toggleView = itemView.findViewById(R.id.tv_section_toggle);
            headerView = itemView.findViewById(R.id.section_header);
            gridView = itemView.findViewById(R.id.grid_section_items);
        }

        void bind(@NonNull MenuSection section) {
            titleView.setText(section.title);
            if (TextUtils.isEmpty(section.description)) {
                descView.setVisibility(View.GONE);
            } else {
                descView.setText(section.description);
                descView.setVisibility(View.VISIBLE);
            }
            toggleView.setText(section.expanded ? "⌃" : "⌄");
            gridView.setVisibility(section.expanded ? View.VISIBLE : View.GONE);
            headerView.setOnClickListener(v -> {
                section.expanded = !section.expanded;
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    notifyItemChanged(position);
                }
            });

            if (!section.expanded) {
                gridView.removeAllViews();
                return;
            }
            gridView.removeAllViews();
            for (MenuItem item : section.items) {
                View optionView = inflater.inflate(R.layout.item_add_operation_option, gridView, false);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = 0;
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                int margin = Math.round(2f * optionView.getResources().getDisplayMetrics().density);
                params.setMargins(margin, margin, margin, margin);
                optionView.setLayoutParams(params);
                bindOption(optionView, item);
                gridView.addView(optionView);
            }
        }

        private void bindOption(@NonNull View optionView, @NonNull MenuItem item) {
            View rootView = optionView.findViewById(R.id.item_root);
            ImageView iconView = optionView.findViewById(R.id.iv_operation_icon);
            TextView labelView = optionView.findViewById(R.id.tv_label);
            TextView descView = optionView.findViewById(R.id.tv_desc);
            TextView selectedView = optionView.findViewById(R.id.tv_selection_index);
            boolean selected = TextUtils.equals(selectedItemId, item.id);

            rootView.setSelected(selected);
            rootView.setEnabled(item.enabled);
            rootView.setAlpha(item.enabled ? 1f : 0.42f);
            iconView.setImageResource(resolveIcon(item.id));
            iconView.setColorFilter(ContextCompat.getColor(optionView.getContext(), R.color.addOperationMenuText));
            labelView.setText(item.label);
            descView.setVisibility(View.GONE);
            selectedView.setVisibility(selected ? View.VISIBLE : View.GONE);
            selectedView.setText("✓");

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

        private int resolveIcon(@NonNull String operationId) {
            switch (operationId) {
                case "click":
                    return R.drawable.ic_op_touch;
                case "gesture":
                    return R.drawable.ic_action_gesture;
                case "delay":
                case "dynamic_delay":
                    return R.drawable.ic_op_clock;
                case "back_key":
                    return R.drawable.ic_back;
                case "match_template":
                case "match_map_template":
                case "ai_detect":
                case "crop_region":
                    return R.drawable.ic_file_image;
                case "ocr_text":
                    return R.drawable.ic_add_operation_ocr;
                case "color_match":
                case "color_search":
                    return R.drawable.ic_add_operation_color;
                case "jump_task":
                    return R.drawable.ic_add_operation_jump;
                case "mtry":
                case "repeat_execution":
                case "loop":
                    return R.drawable.ic_add_operation_repeat;
                case "switch_branch":
                    return R.drawable.ic_add_operation_branch;
                case "variable_script":
                case "variable_math":
                case "variable_template":
                    return R.drawable.ic_file_script;
                case "launch_app":
                case "close_app":
                    return R.drawable.ic_add_operation_app;
                case "a11y_node":
                    return R.drawable.ic_add_operation_accessibility;
                case "log_output":
                    return R.drawable.ic_float_menu_log;
                case "http_request":
                    return R.drawable.ic_add_operation_network;
                case "play_audio":
                    return R.drawable.ic_add_operation_audio;
                case "set_sys_param":
                    return R.drawable.ic_add_operation_settings;
                case "set_brightness":
                    return R.drawable.ic_add_operation_brightness;
                default:
                    return R.drawable.ic_operation;
            }
        }
    }
}
