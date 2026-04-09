package com.auto.master.floatwin;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

class OperationPanelAdapter extends RecyclerView.Adapter<OperationPanelAdapter.ViewHolder> {
    interface OnItemClickListener {
        void onItemClick(OperationItem item);
    }

    interface OnActionListener {
        void onEdit(OperationItem item);
        void onDuplicate(OperationItem item);
        void onDelete(OperationItem item);
        void onMoveUp(OperationItem item);
        void onMoveDown(OperationItem item);
        void onFloatButton(OperationItem item);
    }

    interface OnBatchSelectionListener {
        void onBatchSelectionChanged(Set<String> selectedIds);
    }

    private final List<OperationItem> operations;
    private final OnItemClickListener listener;
    private final OnActionListener actionListener;
    private final OnBatchSelectionListener batchSelectionListener;
    private boolean batchMode = false;
    private final Set<String> batchSelectedIds = new HashSet<>();

    private final AtomicInteger selectedPosition = new AtomicInteger(-1);
    private String runningOperationId;
    private int prevPos = -1;

    OperationPanelAdapter(
            List<OperationItem> operations,
            OnItemClickListener listener,
            OnActionListener actionListener,
            OnBatchSelectionListener batchSelectionListener
    ) {
        this.operations = operations;
        this.listener = listener;
        this.actionListener = actionListener;
        this.batchSelectionListener = batchSelectionListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_operation_compact, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, @SuppressLint("RecyclerView") int position) {
        OperationItem item = operations.get(position);
        holder.opIndex.setText(String.format(Locale.getDefault(), "%02d", position + 1));
        holder.name.setText(item.name);
        holder.typeText.setText(getOperationTypeDisplayName(item.type));
        holder.opId.setText(item.id);

        boolean isRunning = runningOperationId != null && runningOperationId.equals(item.id);
        boolean isSelected = position == selectedPosition.get();
        boolean isBatchChecked = batchSelectedIds.contains(item.id);

        holder.itemView.setSelected(isSelected);

        // 动态着色：直接更新 ViewHolder 里缓存的 GradientDrawable，避免每次 new
        int typeColor = getOperationTypeColor(holder.itemView.getResources(), item.type);
        int typeBgColor = getOperationTypeBgColor(holder.itemView.getResources(), item.type);
        holder.badgeBg.setColor(typeColor);
        holder.chipBg.setColor(typeBgColor);
        holder.typeText.setTextColor(typeColor);

        // 左侧颜色条：正常态显示类型色，选中/运行态变色
        if (isRunning) {
            holder.itemView.setBackgroundColor(0x1AF44336);
            if (holder.selectionIndicator != null) {
                holder.selectionIndicator.setBackgroundColor(0xFFF44336);
            }
        } else if (isSelected) {
            holder.itemView.setBackgroundColor(0xFFE8F0FE);
            if (holder.selectionIndicator != null) {
                holder.selectionIndicator.setBackgroundColor(0xFF3C6DE4);
            }
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            if (holder.selectionIndicator != null) {
                holder.selectionIndicator.setBackgroundColor(typeColor);
            }
        }

        holder.batchCheckBox.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        holder.batchCheckBox.setChecked(isBatchChecked);
        holder.moreOptions.setVisibility(batchMode ? View.GONE : View.VISIBLE);
        // 非批量模式：颜色条常驻（类型色/选中色/运行色）；批量模式：按选中状态显示
        if (batchMode) {
            holder.selectionIndicator.setVisibility(isBatchChecked ? View.VISIBLE : View.INVISIBLE);
        } else {
            holder.selectionIndicator.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (batchMode) {
                toggleBatchSelection(item.id, position);
            } else {
                int previous = selectedPosition.get();
                selectedPosition.set(position);
                notifyItemChanged(previous);
                notifyItemChanged(position);
                if (listener != null) {
                    listener.onItemClick(item);
                }
            }
        });

        holder.moreOptions.setOnClickListener(v -> showMenu(v, item, position));
        holder.batchCheckBox.setOnClickListener(v -> toggleBatchSelection(item.id, position));
    }

    private void showMenu(View anchor, OperationItem item, int position) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(0, 1, 0, "编辑");
        menu.getMenu().add(0, 2, 1, "复制");
        menu.getMenu().add(0, 3, 2, "上移");
        menu.getMenu().add(0, 4, 3, "下移");
        menu.getMenu().add(0, 5, 4, "删除");
        menu.getMenu().add(0, 6, 5, "悬浮按钮");
        menu.getMenu().findItem(3).setEnabled(position > 0);
        menu.getMenu().findItem(4).setEnabled(position < operations.size() - 1);
        menu.setOnMenuItemClickListener(menuItem -> {
            if (actionListener == null) {
                return false;
            }
            switch (menuItem.getItemId()) {
                case 1:
                    actionListener.onEdit(item);
                    return true;
                case 2:
                    actionListener.onDuplicate(item);
                    return true;
                case 3:
                    actionListener.onMoveUp(item);
                    return true;
                case 4:
                    actionListener.onMoveDown(item);
                    return true;
                case 5:
                    actionListener.onDelete(item);
                    return true;
                case 6:
                    actionListener.onFloatButton(item);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    public void setRunningPosition(String operationId) {
        this.runningOperationId = operationId;
        int newPos = findPositionByKey(operationId);

        if (prevPos >= 0) {
            notifyItemChanged(prevPos);
        }
        if (newPos >= 0) {
            notifyItemChanged(newPos);
        }
        prevPos = newPos;
    }

    private int findPositionByKey(String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < operations.size(); i++) {
            if (key.equals(operations.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    public void clearRunningPosition() {
        int old = prevPos;
        runningOperationId = null;
        prevPos = -1;
        if (old >= 0) {
            notifyItemChanged(old);
        }
    }

    public OperationItem getSelectedItem() {
        if (selectedPosition.get() >= 0 && selectedPosition.get() < operations.size()) {
            return operations.get(selectedPosition.get());
        }
        return null;
    }

    public void clearSelection() {
        int prev = selectedPosition.get();
        selectedPosition.set(-1);
        if (prev >= 0) {
            notifyItemChanged(prev);
        }
    }

    public void selectById(String operationId) {
        if (TextUtils.isEmpty(operationId)) {
            return;
        }
        int target = -1;
        for (int i = 0; i < operations.size(); i++) {
            OperationItem item = operations.get(i);
            if (TextUtils.equals(operationId, item.id)) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            return;
        }
        int old = selectedPosition.get();
        selectedPosition.set(target);
        if (old >= 0) {
            notifyItemChanged(old);
        }
        notifyItemChanged(target);
    }

    public void setBatchMode(boolean enabled) {
        this.batchMode = enabled;
        if (enabled) {
            clearSelection();
        }
        if (!enabled) {
            batchSelectedIds.clear();
            notifyBatchChanged();
        }
        notifyDataSetChanged();
    }

    public void setBatchSelectedIds(Set<String> ids) {
        batchSelectedIds.clear();
        if (ids != null) {
            batchSelectedIds.addAll(ids);
        }
        notifyBatchChanged();
        notifyDataSetChanged();
    }

    private void toggleBatchSelection(String operationId, int position) {
        if (!batchMode) {
            return;
        }
        if (batchSelectedIds.contains(operationId)) {
            batchSelectedIds.remove(operationId);
        } else {
            batchSelectedIds.add(operationId);
        }
        notifyItemChanged(position);
        notifyBatchChanged();
    }

    private void notifyBatchChanged() {
        if (batchSelectionListener != null) {
            batchSelectionListener.onBatchSelectionChanged(new HashSet<>(batchSelectedIds));
        }
    }

    private String getOperationTypeDisplayName(String type) {
        if (type == null) return "未知";
        switch (type.toLowerCase(Locale.ROOT)) {
            case "click":           return "点击";
            case "delay":           return "延时";
            case "crop_region":     return "截图";
            case "match_template":  return "模板匹配";
            case "match_map_template": return "地图匹配";
            case "color_match":     return "颜色匹配";
            case "jump_task":       return "跳转Task";
            case "ocr":             return "OCR";
            case "condition_branch": return "条件分支";
            case "variable_script": return "变量脚本";
            case "variable_math":   return "变量计算";
            case "variable_template": return "变量模板";
            case "app_launch":      return "启动应用";
            default:                return type;
        }
    }

    private int getOperationTypeColor(Resources res, String type) {
        if (type == null) return res.getColor(com.auto.master.R.color.op_unknown);
        switch (type.toLowerCase(Locale.ROOT)) {
            case "click":           return res.getColor(com.auto.master.R.color.op_click);
            case "delay":           return res.getColor(com.auto.master.R.color.op_delay);
            case "crop_region":     return res.getColor(com.auto.master.R.color.op_crop);
            case "match_template":  return res.getColor(com.auto.master.R.color.op_match_template);
            case "match_map_template": return res.getColor(com.auto.master.R.color.op_match_map);
            case "color_match":     return res.getColor(com.auto.master.R.color.op_color_match);
            case "jump_task":       return res.getColor(com.auto.master.R.color.op_jump_task);
            case "ocr":             return res.getColor(com.auto.master.R.color.op_ocr);
            case "condition_branch": return res.getColor(com.auto.master.R.color.op_condition);
            case "variable_script": return res.getColor(com.auto.master.R.color.op_var_script);
            case "variable_math":   return res.getColor(com.auto.master.R.color.op_var_math);
            case "variable_template": return res.getColor(com.auto.master.R.color.op_var_template);
            case "app_launch":      return res.getColor(com.auto.master.R.color.op_app_launch);
            default:                return res.getColor(com.auto.master.R.color.op_unknown);
        }
    }

    private int getOperationTypeBgColor(Resources res, String type) {
        if (type == null) return res.getColor(com.auto.master.R.color.op_unknown_bg);
        switch (type.toLowerCase(Locale.ROOT)) {
            case "click":           return res.getColor(com.auto.master.R.color.op_click_bg);
            case "delay":           return res.getColor(com.auto.master.R.color.op_delay_bg);
            case "crop_region":     return res.getColor(com.auto.master.R.color.op_crop_bg);
            case "match_template":  return res.getColor(com.auto.master.R.color.op_match_template_bg);
            case "match_map_template": return res.getColor(com.auto.master.R.color.op_match_map_bg);
            case "color_match":     return res.getColor(com.auto.master.R.color.op_color_match_bg);
            case "jump_task":       return res.getColor(com.auto.master.R.color.op_jump_task_bg);
            case "ocr":             return res.getColor(com.auto.master.R.color.op_ocr_bg);
            case "condition_branch": return res.getColor(com.auto.master.R.color.op_condition_bg);
            case "variable_script": return res.getColor(com.auto.master.R.color.op_var_script_bg);
            case "variable_math":   return res.getColor(com.auto.master.R.color.op_var_math_bg);
            case "variable_template": return res.getColor(com.auto.master.R.color.op_var_template_bg);
            case "app_launch":      return res.getColor(com.auto.master.R.color.op_app_launch_bg);
            default:                return res.getColor(com.auto.master.R.color.op_unknown_bg);
        }
    }

    @Override
    public int getItemCount() {
        return operations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView opIndex;
        TextView name;
        TextView typeText;
        TextView opId;
        View selectionIndicator;
        ImageView moreOptions;
        CheckBox batchCheckBox;
        // 缓存 Drawable，避免 onBindViewHolder 每次 new
        GradientDrawable badgeBg;
        GradientDrawable chipBg;

        ViewHolder(View itemView) {
            super(itemView);
            opIndex = itemView.findViewById(R.id.operation_index);
            name = itemView.findViewById(R.id.list_item_text);
            typeText = itemView.findViewById(R.id.operation_type);
            opId = itemView.findViewById(R.id.operation_id);
            selectionIndicator = itemView.findViewById(R.id.selection_indicator);
            moreOptions = itemView.findViewById(R.id.more_options);
            batchCheckBox = itemView.findViewById(R.id.chk_batch);

            float density = itemView.getResources().getDisplayMetrics().density;

            badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.OVAL);
            opIndex.setBackground(badgeBg);
            opIndex.setTextColor(Color.WHITE);

            chipBg = new GradientDrawable();
            chipBg.setShape(GradientDrawable.RECTANGLE);
            chipBg.setCornerRadius(4 * density);
            typeText.setBackground(chipBg);
        }
    }
}
