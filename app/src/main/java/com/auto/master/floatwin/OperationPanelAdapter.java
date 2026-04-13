package com.auto.master.floatwin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

class OperationPanelAdapter extends RecyclerView.Adapter<OperationPanelAdapter.ViewHolder> {
    private static final int VIEW_TYPE_OPERATION = 1003;

    interface OnItemClickListener {
        void onItemClick(OperationItem item);
    }

    interface OnActionListener {
        void onEdit(OperationItem item);

        void onCopy(OperationItem item);

        void onPasteAfter(OperationItem item);

        void onInsertBefore(OperationItem item);

        void onDelete(OperationItem item);

        void onMoveUp(OperationItem item);

        void onMoveDown(OperationItem item);

        boolean canPaste();

        void onConfigUi(OperationItem item);

        void onFloatButton(OperationItem item);
    }

    interface OnBatchSelectionListener {
        void onBatchSelectionChanged(Set<String> selectedIds);
    }

    static final class ActionItem {
        final int id;
        final String title;
        final String desc;
        final boolean enabled;

        ActionItem(int id, String title, String desc, boolean enabled) {
            this.id = id;
            this.title = title;
            this.desc = desc;
            this.enabled = enabled;
        }
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
    private Map<String, Integer> floatBtnColorMap = Collections.emptyMap();

    OperationPanelAdapter(
            List<OperationItem> operations,
            OnItemClickListener listener,
            OnActionListener actionListener,
            OnBatchSelectionListener batchSelectionListener
    ) {
        this.operations = new ArrayList<>(operations);
        this.listener = listener;
        this.actionListener = actionListener;
        this.batchSelectionListener = batchSelectionListener;
        setHasStableIds(true);
    }

    void initFloatBtnColors(Map<String, Integer> colorMap) {
        floatBtnColorMap = colorMap == null ? Collections.emptyMap() : new HashMap<>(colorMap);
    }

    void setFloatBtnColors(Map<String, Integer> colorMap) {
        Map<String, Integer> next = colorMap == null ? Collections.emptyMap() : new HashMap<>(colorMap);
        if (next.equals(floatBtnColorMap)) {
            return;
        }
        Set<String> changed = new HashSet<>(floatBtnColorMap.keySet());
        changed.addAll(next.keySet());
        floatBtnColorMap = next;
        for (int i = 0; i < operations.size(); i++) {
            OperationItem operation = operations.get(i);
            if (operation != null && changed.contains(operation.id)) {
                notifyItemChanged(i);
            }
        }
    }

    void submitOperations(List<OperationItem> newItems) {
        String selectedId = null;
        OperationItem selected = getSelectedItem();
        if (selected != null) {
            selectedId = selected.id;
        }
        List<OperationItem> targetItems = newItems == null ? Collections.emptyList() : new ArrayList<>(newItems);
        if (hasSameItems(targetItems)) {
            if (!TextUtils.isEmpty(selectedId)) {
                selectedPosition.set(findPositionByKey(selectedId));
            } else if (selectedPosition.get() >= operations.size()) {
                selectedPosition.set(-1);
            }
            prevPos = findPositionByKey(runningOperationId);
            return;
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return operations.size();
            }

            @Override
            public int getNewListSize() {
                return targetItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                OperationItem oldItem = operations.get(oldItemPosition);
                OperationItem newItem = targetItems.get(newItemPosition);
                return TextUtils.equals(oldItem.id, newItem.id)
                        && oldItem.index == newItem.index;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                OperationItem oldItem = operations.get(oldItemPosition);
                OperationItem newItem = targetItems.get(newItemPosition);
                return oldItem.index == newItem.index
                        && TextUtils.equals(oldItem.id, newItem.id)
                        && TextUtils.equals(oldItem.name, newItem.name)
                        && TextUtils.equals(oldItem.type, newItem.type);
            }
        });
        operations.clear();
        operations.addAll(targetItems);
        if (!TextUtils.isEmpty(selectedId)) {
            selectedPosition.set(findPositionByKey(selectedId));
        } else if (selectedPosition.get() >= operations.size()) {
            selectedPosition.set(-1);
        }
        Set<String> validIds = new HashSet<>();
        for (OperationItem item : operations) {
            if (item != null && !TextUtils.isEmpty(item.id)) {
                validIds.add(item.id);
            }
        }
        batchSelectedIds.retainAll(validIds);
        prevPos = findPositionByKey(runningOperationId);
        notifyBatchChanged();
        diffResult.dispatchUpdatesTo(this);
    }

    private boolean hasSameItems(List<OperationItem> newItems) {
        if (operations.size() != newItems.size()) {
            return false;
        }
        for (int i = 0; i < operations.size(); i++) {
            OperationItem oldItem = operations.get(i);
            OperationItem newItem = newItems.get(i);
            if (!TextUtils.equals(oldItem.id, newItem.id)
                    || oldItem.index != newItem.index
                    || !TextUtils.equals(oldItem.name, newItem.name)
                    || !TextUtils.equals(oldItem.type, newItem.type)
                    || oldItem.delayDurationMs != newItem.delayDurationMs
                    || oldItem.delayShowCountdown != newItem.delayShowCountdown) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_operation_compact, parent, false);
        return new ViewHolder(view);
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
        if (holder.selectionIndicator != null) {
            holder.selectionIndicator.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
        }

        if (isRunning) {
            holder.itemView.setBackgroundColor(0x66EF9A9A);
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
                holder.selectionIndicator.setBackgroundColor(0xFF3C6DE4);
            }
        }

        holder.batchCheckBox.setVisibility(batchMode ? View.VISIBLE : View.GONE);
        holder.batchCheckBox.setChecked(isBatchChecked);
        holder.moreOptions.setVisibility(batchMode ? View.GONE : View.VISIBLE);
        holder.selectionIndicator.setVisibility(batchMode
                ? (isBatchChecked ? View.VISIBLE : View.INVISIBLE)
                : (isSelected ? View.VISIBLE : View.INVISIBLE));

        if (holder.floatBtnDot != null) {
            Integer btnColor = floatBtnColorMap.get(item.id);
            if (btnColor != null) {
                holder.floatBtnDot.setVisibility(View.VISIBLE);
                if (holder.floatBtnDotBg != null) {
                    holder.floatBtnDotBg.setColor(btnColor);
                }
            } else {
                holder.floatBtnDot.setVisibility(View.GONE);
            }
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

    @Override
    public long getItemId(int position) {
        OperationItem item = operations.get(position);
        String stableKey = !TextUtils.isEmpty(item.id) ? item.id : String.valueOf(item.name) + "#" + position;
        return stableKey.hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        return VIEW_TYPE_OPERATION;
    }

    private void showMenu(View anchor, OperationItem item, int position) {
        if (actionListener == null) {
            return;
        }
        List<ActionItem> actionItems = new ArrayList<>();
        actionItems.add(new ActionItem(1, "编辑节点", "打开这个节点的编辑页", true));
        actionItems.add(new ActionItem(2, "复制到节点库", "先收进节点库，后面可反复粘贴", true));
        actionItems.add(new ActionItem(3, "从节点库粘贴到后面", "从节点库挑一个节点插到当前节点后面", actionListener.canPaste()));
        actionItems.add(new ActionItem(4, "从节点库插入到前面", "从节点库挑一个节点插到当前节点前面", actionListener.canPaste()));
        actionItems.add(new ActionItem(5, "上移", "把当前节点往前挪一位", position > 0));
        actionItems.add(new ActionItem(6, "下移", "把当前节点往后挪一位", position < operations.size() - 1));
        actionItems.add(new ActionItem(7, "删除", "删除当前节点", true));
        actionItems.add(new ActionItem(8, "ConfigUI 设计", "为这个节点设计可视化配置界面", true));
        actionItems.add(new ActionItem(9, "悬浮按钮", "为这个节点创建/编辑专属悬浮按钮", true));

        View popupView = LayoutInflater.from(anchor.getContext()).inflate(R.layout.dialog_node_action_sheet, null);
        TextView titleView = popupView.findViewById(R.id.tv_action_title);
        RecyclerView recyclerView = popupView.findViewById(R.id.rv_action_list);
        if (titleView != null) {
            titleView.setText(TextUtils.isEmpty(item.name) ? "节点操作" : item.name);
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(anchor.getContext()));
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(10f);
        recyclerView.setAdapter(new ActionSheetAdapter(actionItems, action -> {
            if (!action.enabled) {
                return;
            }
            popupWindow.dismiss();
            switch (action.id) {
                case 1:
                    actionListener.onEdit(item);
                    break;
                case 2:
                    actionListener.onCopy(item);
                    break;
                case 3:
                    actionListener.onPasteAfter(item);
                    break;
                case 4:
                    actionListener.onInsertBefore(item);
                    break;
                case 5:
                    actionListener.onMoveUp(item);
                    break;
                case 6:
                    actionListener.onMoveDown(item);
                    break;
                case 7:
                    actionListener.onDelete(item);
                    break;
                case 8:
                    actionListener.onConfigUi(item);
                    break;
                case 9:
                    actionListener.onFloatButton(item);
                    break;
                default:
                    break;
            }
        }));
        popupWindow.showAsDropDown(anchor, -dp(anchor.getContext(), 180), dp(anchor.getContext(), 4), Gravity.END);
    }

    public void setRunningPosition(String operationId) {
        if (TextUtils.equals(this.runningOperationId, operationId)) {
            return;
        }
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

    public int findPositionById(String operationId) {
        return findPositionByKey(operationId);
    }

    public List<String> getOperationIdsSnapshot() {
        List<String> ids = new ArrayList<>();
        for (OperationItem operation : operations) {
            if (operation != null && !TextUtils.isEmpty(operation.id)) {
                ids.add(operation.id);
            }
        }
        return ids;
    }

    public boolean moveItem(int from, int to) {
        if (from < 0 || to < 0 || from >= operations.size() || to >= operations.size()) {
            return false;
        }
        if (from == to) {
            return true;
        }
        String selectedId = null;
        OperationItem selected = getSelectedItem();
        if (selected != null) {
            selectedId = selected.id;
        }
        Collections.swap(operations, from, to);
        notifyItemMoved(from, to);
        if (!TextUtils.isEmpty(selectedId)) {
            selectedPosition.set(findPositionByKey(selectedId));
        }
        return true;
    }

    public OperationItem getSelectedItem() {
        if (selectedPosition.get() >= 0 && selectedPosition.get() < operations.size()) {
            return operations.get(selectedPosition.get());
        }
        return null;
    }

    public void clearSelection() {
        int prev = selectedPosition.get();
        if (prev < 0) {
            return;
        }
        selectedPosition.set(-1);
        notifyItemChanged(prev);
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
        if (old == target) {
            return;
        }
        selectedPosition.set(target);
        if (old >= 0) {
            notifyItemChanged(old);
        }
        notifyItemChanged(target);
    }

    public void setBatchMode(boolean enabled) {
        if (this.batchMode == enabled) {
            return;
        }
        this.batchMode = enabled;
        if (enabled) {
            clearSelection();
        }
        if (!enabled) {
            batchSelectedIds.clear();
            notifyBatchChanged();
        }
        notifyAllItemsChanged();
    }

    public void setBatchSelectedIds(Set<String> ids) {
        Set<String> nextIds = ids == null ? Collections.emptySet() : new HashSet<>(ids);
        if (batchSelectedIds.equals(nextIds)) {
            return;
        }
        Set<String> changedIds = new HashSet<>(batchSelectedIds);
        changedIds.addAll(nextIds);
        batchSelectedIds.clear();
        batchSelectedIds.addAll(nextIds);
        notifyBatchChanged();
        if (!batchMode) {
            return;
        }
        for (int i = 0; i < operations.size(); i++) {
            OperationItem item = operations.get(i);
            if (item != null && changedIds.contains(item.id)) {
                notifyItemChanged(i);
            }
        }
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

    private void notifyAllItemsChanged() {
        if (!operations.isEmpty()) {
            notifyItemRangeChanged(0, operations.size());
        }
    }

    private String getOperationTypeDisplayName(String type) {
        if (type == null) {
            return "未知操作";
        }
        switch (type.toLowerCase()) {
            case "click":
                return "点击操作";
            case "sleep":
                return "等待操作";
            case "input":
                return "输入操作";
            case "swipe":
                return "滑动操作";
            default:
                return type;
        }
    }

    @Override
    public int getItemCount() {
        return operations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView opIndex;
        final TextView name;
        final TextView typeText;
        final TextView opId;
        final View selectionIndicator;
        final View floatBtnDot;
        final GradientDrawable floatBtnDotBg;
        final ImageView moreOptions;
        final CheckBox batchCheckBox;

        ViewHolder(View itemView) {
            super(itemView);
            opIndex = itemView.findViewById(R.id.operation_index);
            name = itemView.findViewById(R.id.list_item_text);
            typeText = itemView.findViewById(R.id.operation_type);
            opId = itemView.findViewById(R.id.operation_id);
            selectionIndicator = itemView.findViewById(R.id.selection_indicator);
            moreOptions = itemView.findViewById(R.id.more_options);
            batchCheckBox = itemView.findViewById(R.id.chk_batch);
            floatBtnDot = itemView.findViewById(R.id.float_btn_dot);
            if (floatBtnDot != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                floatBtnDot.setBackground(bg);
                floatBtnDotBg = bg;
            } else {
                floatBtnDotBg = null;
            }
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    static class ActionSheetAdapter extends RecyclerView.Adapter<ActionSheetAdapter.ViewHolder> {
        interface OnActionClickListener {
            void onActionClick(ActionItem action);
        }

        private final List<ActionItem> items;
        private final OnActionClickListener listener;

        ActionSheetAdapter(List<ActionItem> items, OnActionClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_node_action, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ActionItem item = items.get(position);
            holder.tvName.setText(item.title);
            holder.tvDesc.setText(item.desc);
            holder.itemView.setAlpha(item.enabled ? 1f : 0.42f);
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onActionClick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvDesc;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_action_name);
                tvDesc = itemView.findViewById(R.id.tv_action_desc);
            }
        }
    }
}
