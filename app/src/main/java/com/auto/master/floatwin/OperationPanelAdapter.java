package com.auto.master.floatwin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;
import com.auto.master.capture.CaptureScaleHelper;
import com.auto.master.capture.ScreenCaptureManager;
import com.auto.master.ui.EntityActionSheetAdapter;

import java.io.File;
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

        void onRuntimeConfig(OperationItem item);

        void onFloatButton(OperationItem item);

        void onNodePreDelay(OperationItem item);

        void onBindSession(OperationItem item);
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
    private File taskDir;
    private final Map<String, Bitmap> previewBitmapCache = new HashMap<>();
    private PopupWindow nodeActionPopupWindow;
    private TextView nodeActionTitleView;
    private TextView nodeActionNameView;
    private TextView nodeActionHintView;
    private TextView nodeActionPrimaryButton;
    private EntityActionSheetAdapter nodeActionSheetAdapter;

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

    void setTaskDir(File taskDir) {
        String oldPath = this.taskDir == null ? "" : this.taskDir.getAbsolutePath();
        String newPath = taskDir == null ? "" : taskDir.getAbsolutePath();
        if (TextUtils.equals(oldPath, newPath)) {
            return;
        }
        clearPreviewBitmapCache();
        this.taskDir = taskDir;
        notifyAllItemsChanged();
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
                        && TextUtils.equals(oldItem.type, newItem.type)
                        && oldItem.delayDurationMs == newItem.delayDurationMs
                        && oldItem.nodePreDelayMs == newItem.nodePreDelayMs
                        && oldItem.nodePreDelayMinMs == newItem.nodePreDelayMinMs
                        && oldItem.nodePreDelayMaxMs == newItem.nodePreDelayMaxMs
                        && oldItem.nodePreDelayRandom == newItem.nodePreDelayRandom
                        && oldItem.templatePreviewNames.equals(newItem.templatePreviewNames);
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
                    || oldItem.delayShowCountdown != newItem.delayShowCountdown
                    || oldItem.nodePreDelayMs != newItem.nodePreDelayMs
                    || oldItem.nodePreDelayMinMs != newItem.nodePreDelayMinMs
                    || oldItem.nodePreDelayMaxMs != newItem.nodePreDelayMaxMs
                    || oldItem.nodePreDelayRandom != newItem.nodePreDelayRandom
                    || !oldItem.templatePreviewNames.equals(newItem.templatePreviewNames)) {
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
        if (holder.nodePreDelayText != null) {
            boolean hasDelay = item.nodePreDelayRandom || item.nodePreDelayMs > 0L;
            String delayText = item.nodePreDelayRandom
                    ? "等 " + item.nodePreDelayMinMs + "~" + item.nodePreDelayMaxMs + "ms"
                    : "等 " + item.nodePreDelayMs + "ms";
            holder.nodePreDelayText.setText(delayText);
            if (hasDelay) {
                holder.nodePreDelayText.setTextColor(0xFFB85C00);
                holder.nodePreDelayText.setBackgroundResource(R.drawable.node_delay_chip_active_bg);
            } else {
                holder.nodePreDelayText.setTextColor(0xFF8A97A6);
                holder.nodePreDelayText.setBackgroundResource(R.drawable.project_panel_type_chip_bg);
            }
        }

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
            holder.itemView.setBackgroundColor(0x9907A6BB);
            if (holder.selectionIndicator != null) {
                holder.selectionIndicator.setBackgroundColor(0xFF07A6BB);
            }
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            if (holder.selectionIndicator != null) {
                holder.selectionIndicator.setBackgroundColor(0xFF07A6BB);
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
        TemplatePreview primaryPreview = findFirstTemplatePreview(item);
        bindOperationVisual(holder, item, primaryPreview);
        bindTemplatePreviewStrip(holder, item, primaryPreview == null ? null : primaryPreview.name);

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
        if (holder.nodePreDelayButton != null) {
            holder.nodePreDelayButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onNodePreDelay(item);
                }
            });
        }
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

    private void bindOperationVisual(ViewHolder holder, OperationItem item, TemplatePreview primaryPreview) {
        if (holder.operationVisual == null || holder.operationVisualImage == null || holder.operationVisualText == null) {
            return;
        }
        Context context = holder.itemView.getContext();
        if (primaryPreview != null && primaryPreview.bitmap != null) {
            if (holder.operationVisualBg != null) {
                holder.operationVisualBg.setColor(0xFFFFFFFF);
                holder.operationVisualBg.setStroke(dp(context, 1), 0x663C6DE4);
            }
            holder.operationVisualText.setVisibility(View.GONE);
            holder.operationVisualImage.setVisibility(View.VISIBLE);
            holder.operationVisualImage.clearColorFilter();
            holder.operationVisualImage.setPadding(dp(context, 1), dp(context, 1), dp(context, 1), dp(context, 1));
            holder.operationVisualImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.operationVisualImage.setImageBitmap(primaryPreview.bitmap);
            return;
        }

        OperationVisualSpec spec = getOperationVisualSpec(item == null ? null : item.type);
        if (holder.operationVisualBg != null) {
            holder.operationVisualBg.setColor(spec.bgColor);
            holder.operationVisualBg.setStroke(dp(context, 1), spec.strokeColor);
        }
        holder.operationVisualImage.setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6));
        holder.operationVisualImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (!TextUtils.isEmpty(spec.text)) {
            holder.operationVisualImage.setVisibility(View.GONE);
            holder.operationVisualText.setVisibility(View.VISIBLE);
            holder.operationVisualText.setText(spec.text);
            holder.operationVisualText.setTextColor(spec.fgColor);
        } else {
            holder.operationVisualText.setVisibility(View.GONE);
            holder.operationVisualImage.setVisibility(View.VISIBLE);
            holder.operationVisualImage.setImageResource(spec.iconRes);
            holder.operationVisualImage.setColorFilter(spec.fgColor);
        }
    }

    private TemplatePreview findFirstTemplatePreview(OperationItem item) {
        List<String> templates = item == null ? Collections.emptyList() : item.templatePreviewNames;
        if (templates == null || templates.isEmpty() || taskDir == null) {
            return null;
        }
        for (String templateName : templates) {
            Bitmap bitmap = loadTemplatePreviewBitmap(templateName);
            if (bitmap != null) {
                return new TemplatePreview(templateName, bitmap);
            }
        }
        return null;
    }

    private void bindTemplatePreviewStrip(ViewHolder holder, OperationItem item, String skipTemplateName) {
        if (holder.templatePreviewStrip == null) {
            return;
        }
        holder.templatePreviewStrip.removeAllViews();
        List<String> templates = item == null ? Collections.emptyList() : item.templatePreviewNames;
        if (templates == null || templates.isEmpty() || taskDir == null) {
            holder.templatePreviewStrip.setVisibility(View.GONE);
            return;
        }

        int added = 0;
        for (String templateName : templates) {
            if (!TextUtils.isEmpty(skipTemplateName) && TextUtils.equals(skipTemplateName, templateName)) {
                continue;
            }
            if (added >= 3) {
                break;
            }
            Bitmap bitmap = loadTemplatePreviewBitmap(templateName);
            if (bitmap == null) {
                continue;
            }
            ImageView imageView = new ImageView(holder.itemView.getContext());
            int width = templates.size() == 1 ? dp(holder.itemView.getContext(), 42) : dp(holder.itemView.getContext(), 26);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dp(holder.itemView.getContext(), 24));
            lp.setMarginStart(dp(holder.itemView.getContext(), 4));
            imageView.setLayoutParams(lp);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageBitmap(bitmap);
            imageView.setBackground(makePreviewBackground());
            holder.templatePreviewStrip.addView(imageView);
            added++;
        }
        holder.templatePreviewStrip.setVisibility(added > 0 ? View.VISIBLE : View.GONE);
    }

    private Bitmap loadTemplatePreviewBitmap(String templateName) {
        if (taskDir == null || TextUtils.isEmpty(templateName)) {
            return null;
        }
        File imgDir = new File(taskDir, "img");
        File file = CaptureScaleHelper.resolveTemplateFile(
                imgDir,
                templateName,
                ScreenCaptureManager.CAPTURE_SCALE);
        if (file == null || !file.exists()) {
            return null;
        }
        String cacheKey = file.getAbsolutePath() + "#" + file.lastModified();
        Bitmap cached = previewBitmapCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculatePreviewSampleSize(bounds, 96, 64);
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap != null) {
            if (previewBitmapCache.size() > 64) {
                clearPreviewBitmapCache();
            }
            previewBitmapCache.put(cacheKey, bitmap);
        }
        return bitmap;
    }

    private int calculatePreviewSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options == null ? 0 : options.outHeight;
        int width = options == null ? 0 : options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = Math.max(1, height / 2);
            int halfWidth = Math.max(1, width / 2);
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private GradientDrawable makePreviewBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xFFFFFFFF);
        drawable.setStroke(1, 0x663C6DE4);
        drawable.setCornerRadius(4f);
        return drawable;
    }

    private OperationVisualSpec getOperationVisualSpec(String type) {
        String normalized = type == null ? "" : type.trim();
        switch (normalized) {
            case "点击":
            case "点击操作":
            case "click":
            case "手势":
            case "swipe":
                return new OperationVisualSpec(R.drawable.ic_op_touch, null,
                        0xFFE8F7F1, 0x3360B58B, 0xFF0E8F67);
            case "延时":
            case "等待操作":
            case "动态延时":
            case "sleep":
                return new OperationVisualSpec(R.drawable.ic_op_clock, null,
                        0xFFFFF4DF, 0x33D9891F, 0xFFB85C00);
            case "JS脚本":
            case "数学运算":
            case "模板变量":
                return new OperationVisualSpec(0, "var",
                        0xFFEAF1FF, 0x333C6DE4, 0xFF3167D8);
            case "截图区域":
            case "加载资源":
            case "模板匹配":
            case "图集匹配":
            case "颜色匹配":
            case "区域找色":
                return new OperationVisualSpec(R.drawable.ic_file_image, null,
                        0xFFEEF8FF, 0x334D94BE, 0xFF2C7DA0);
            case "返回按键":
                return new OperationVisualSpec(R.drawable.ic_back, null,
                        0xFFF3F5F8, 0x338A97A6, 0xFF637083);
            case "播放音频":
                return new OperationVisualSpec(0, "音",
                        0xFFFFF3E0, 0x33E65100, 0xFFE65100);
            case "修改系统参数":
                return new OperationVisualSpec(0, "参",
                        0xFFE0F7FA, 0x3300838F, 0xFF00838F);
            case "HTTP请求":
                return new OperationVisualSpec(0, "API",
                        0xFFFFEEF2, 0x33D45A72, 0xFFC23B57);
            case "多路分支":
                return new OperationVisualSpec(0, "IF",
                        0xFFFFF7E8, 0x33D99B28, 0xFF9B6A08);
            case "二分路":
                return new OperationVisualSpec(0, "LOOP",
                        0xFFF0EDFF, 0x33745ED9, 0xFF5B48BF);
            default:
                return new OperationVisualSpec(R.drawable.ic_operation, null,
                        0xFFF1F5F9, 0x338A97A6, 0xFF6B7788);
        }
    }

    private void clearPreviewBitmapCache() {
        for (Bitmap bitmap : previewBitmapCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        previewBitmapCache.clear();
    }

    private void showMenu(View anchor, OperationItem item, int position) {
        if (actionListener == null) {
            return;
        }
        List<ActionItem> actionItems = new ArrayList<>();
        actionItems.add(new ActionItem(1, "编辑", "节点配置", true));
        actionItems.add(new ActionItem(2, "复制", "存入节点库", true));
        actionItems.add(new ActionItem(3, "粘贴到后", "节点库 -> 后面", actionListener.canPaste()));
        actionItems.add(new ActionItem(4, "插入到前", "节点库 -> 前面", actionListener.canPaste()));
        actionItems.add(new ActionItem(5, "上移", "前移一位", position > 0));
        actionItems.add(new ActionItem(6, "下移", "后移一位", position < operations.size() - 1));
        actionItems.add(new ActionItem(7, "删除", "移除节点", true));
        actionItems.add(new ActionItem(8, "配置 UI", "可视化表单", true));
        actionItems.add(new ActionItem(12, "修改配置", "填写节点运行参数", true));
        actionItems.add(new ActionItem(9, "悬浮按钮", "创建/编辑", true));
        actionItems.add(new ActionItem(10, "前置延迟", "运行前等待", true));
        actionItems.add(new ActionItem(11, "绑定会话", "入口会话", true));

        ensureNodeActionPopup(anchor.getContext());
        if (nodeActionPopupWindow == null || nodeActionSheetAdapter == null) {
            return;
        }
        if (nodeActionTitleView != null) {
            nodeActionTitleView.setText("节点操作");
        }
        if (nodeActionNameView != null) {
            nodeActionNameView.setText(TextUtils.isEmpty(item.name) ? item.type : item.name);
        }
        if (nodeActionHintView != null) {
            String type = TextUtils.isEmpty(item.type) ? "Operation" : item.type;
            String id = TextUtils.isEmpty(item.id) ? "" : " · " + item.id;
            nodeActionHintView.setText(type + id);
        }
        if (nodeActionPrimaryButton != null) {
            nodeActionPrimaryButton.setVisibility(View.GONE);
        }
        nodeActionSheetAdapter.submitItems(toEntityActionItems(actionItems), action -> {
            if (!action.enabled) {
                return;
            }
            nodeActionPopupWindow.dismiss();
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
                case 12:
                    actionListener.onRuntimeConfig(item);
                    break;
                case 9:
                    actionListener.onFloatButton(item);
                    break;
                case 10:
                    actionListener.onNodePreDelay(item);
                    break;
                case 11:
                    actionListener.onBindSession(item);
                    break;
                default:
                    break;
            }
        });
        if (nodeActionPopupWindow.isShowing()) {
            nodeActionPopupWindow.dismiss();
        }
        nodeActionPopupWindow.showAtLocation(anchor.getRootView(), Gravity.CENTER, 0, 0);
    }

    private void ensureNodeActionPopup(Context context) {
        if (nodeActionPopupWindow != null) {
            return;
        }
        View popupView = LayoutInflater.from(context).inflate(R.layout.dialog_entity_action_sheet, null);
        View scrim = popupView.findViewById(R.id.entity_action_scrim);
        View panel = popupView.findViewById(R.id.entity_action_panel);
        nodeActionTitleView = popupView.findViewById(R.id.tv_entity_action_title);
        nodeActionNameView = popupView.findViewById(R.id.tv_entity_action_name);
        nodeActionHintView = popupView.findViewById(R.id.tv_entity_action_hint);
        nodeActionPrimaryButton = popupView.findViewById(R.id.btn_entity_action_primary);
        TextView closeTop = popupView.findViewById(R.id.btn_entity_action_close_top);
        TextView closeBottom = popupView.findViewById(R.id.btn_entity_action_close);
        RecyclerView recyclerView = popupView.findViewById(R.id.rv_entity_action_grid);
        nodeActionSheetAdapter = new EntityActionSheetAdapter();
        recyclerView.setLayoutManager(new GridLayoutManager(context, 3));
        recyclerView.setAdapter(nodeActionSheetAdapter);
        nodeActionPopupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true);
        nodeActionPopupWindow.setOutsideTouchable(true);
        nodeActionPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        nodeActionPopupWindow.setElevation(10f);
        View.OnClickListener closeListener = v -> nodeActionPopupWindow.dismiss();
        scrim.setOnClickListener(closeListener);
        closeTop.setOnClickListener(closeListener);
        closeBottom.setOnClickListener(closeListener);
        panel.setOnClickListener(v -> {
        });
    }

    private List<EntityActionSheetAdapter.Item> toEntityActionItems(List<ActionItem> actionItems) {
        List<EntityActionSheetAdapter.Item> items = new ArrayList<>();
        for (ActionItem action : actionItems) {
            items.add(new EntityActionSheetAdapter.Item(
                    action.id,
                    resolveActionIcon(action),
                    action.title,
                    action.desc,
                    action.enabled));
        }
        return items;
    }

    private int resolveActionIcon(ActionItem action) {
        if (action == null || TextUtils.isEmpty(action.title)) {
            return R.drawable.ic_action_config;
        }
        String title = action.title;
        if (title.contains("编辑") || title.contains("配置") || title.contains("设计") || title.contains("延迟") || title.contains("绑定")) {
            if (title.contains("延迟")) {
                return R.drawable.ic_action_delay;
            }
            if (title.contains("绑定")) {
                return R.drawable.ic_action_session;
            }
            if (title.contains("配置")) {
                return R.drawable.ic_action_config;
            }
            return R.drawable.ic_action_edit;
        }
        if (title.contains("复制")) {
            return R.drawable.ic_action_copy;
        }
        if (title.contains("粘贴")) {
            return R.drawable.ic_action_paste_after;
        }
        if (title.contains("插入")) {
            return R.drawable.ic_action_paste_before;
        }
        if (title.contains("上移")) {
            return R.drawable.ic_action_move_up;
        }
        if (title.contains("下移")) {
            return R.drawable.ic_action_move_down;
        }
        if (title.contains("删除")) {
            return R.drawable.ic_action_delete;
        }
        if (title.contains("悬浮")) {
            return R.drawable.ic_action_float;
        }
        return R.drawable.ic_action_list;
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
        final FrameLayout operationVisual;
        final ImageView operationVisualImage;
        final TextView operationVisualText;
        final GradientDrawable operationVisualBg;
        final LinearLayout templatePreviewStrip;
        final View selectionIndicator;
        final View floatBtnDot;
        final GradientDrawable floatBtnDotBg;
        final ImageView moreOptions;
        final TextView nodePreDelayText;
        final ImageView nodePreDelayButton;
        final CheckBox batchCheckBox;

        ViewHolder(View itemView) {
            super(itemView);
            opIndex = itemView.findViewById(R.id.operation_index);
            name = itemView.findViewById(R.id.list_item_text);
            typeText = itemView.findViewById(R.id.operation_type);
            opId = itemView.findViewById(R.id.operation_id);
            operationVisual = itemView.findViewById(R.id.operation_visual);
            operationVisualImage = itemView.findViewById(R.id.operation_visual_image);
            operationVisualText = itemView.findViewById(R.id.operation_visual_text);
            if (operationVisual != null) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(dp(itemView.getContext(), 7));
                operationVisual.setBackground(bg);
                operationVisualBg = bg;
            } else {
                operationVisualBg = null;
            }
            templatePreviewStrip = itemView.findViewById(R.id.template_preview_strip);
            nodePreDelayText = itemView.findViewById(R.id.node_pre_delay_text);
            nodePreDelayButton = itemView.findViewById(R.id.btn_node_pre_delay);
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

    private static final class TemplatePreview {
        final String name;
        final Bitmap bitmap;

        TemplatePreview(String name, Bitmap bitmap) {
            this.name = name;
            this.bitmap = bitmap;
        }
    }

    private static final class OperationVisualSpec {
        final int iconRes;
        final String text;
        final int bgColor;
        final int strokeColor;
        final int fgColor;

        OperationVisualSpec(int iconRes, String text, int bgColor, int strokeColor, int fgColor) {
            this.iconRes = iconRes;
            this.text = text;
            this.bgColor = bgColor;
            this.strokeColor = strokeColor;
            this.fgColor = fgColor;
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
