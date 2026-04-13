package com.auto.master;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.auto.master.auto.ActivityHolder;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.capture.ScreenCapture;
import com.auto.master.capture.ScreenCaptureManager;
import com.auto.master.floatwin.FloatWindowService;
import com.auto.master.floatwin.RecyclerViewOptimizer;
import com.auto.master.homepanel.HomeProjectRepository;
import com.auto.master.utils.BatteryHardeningHelper;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private interface ActionSheetHandler {
        void onAction(ActionSheetItem item);
    }

    private enum Level {
        PROJECT,
        TASK,
        OPERATION
    }

    private static final class EntryItem {
        final int iconRes;
        final String title;
        final String subtitle;
        final String badge;
        final boolean showMore;
        final Object payload;

        EntryItem(int iconRes, String title, String subtitle, String badge, boolean showMore, Object payload) {
            this.iconRes = iconRes;
            this.title = title;
            this.subtitle = subtitle;
            this.badge = badge;
            this.showMore = showMore;
            this.payload = payload;
        }

        long stableId() {
            if (payload instanceof HomeProjectRepository.ProjectSummary) {
                return ((HomeProjectRepository.ProjectSummary) payload).dir.getAbsolutePath().hashCode();
            }
            if (payload instanceof HomeProjectRepository.TaskSummary) {
                return ((HomeProjectRepository.TaskSummary) payload).dir.getAbsolutePath().hashCode();
            }
            return (String.valueOf(title) + '\n' + subtitle + '\n' + badge).hashCode();
        }

        boolean isSameItem(@NonNull EntryItem other) {
            return stableId() == other.stableId()
                    && getPayloadType() == other.getPayloadType();
        }

        boolean hasSameContent(@NonNull EntryItem other) {
            return iconRes == other.iconRes
                    && showMore == other.showMore
                    && TextUtils.equals(title, other.title)
                    && TextUtils.equals(subtitle, other.subtitle)
                    && TextUtils.equals(badge, other.badge);
        }

        private int getPayloadType() {
            return payload instanceof HomeProjectRepository.ProjectSummary ? 1 : 0;
        }
    }

    private static final class ActionSheetItem {
        final int id;
        final String title;
        final String desc;
        final boolean enabled;

        ActionSheetItem(int id, String title, String desc, boolean enabled) {
            this.id = id;
            this.title = title;
            this.desc = desc;
            this.enabled = enabled;
        }

        boolean isSameItem(@NonNull ActionSheetItem other) {
            return id == other.id;
        }

        boolean hasSameContent(@NonNull ActionSheetItem other) {
            return enabled == other.enabled
                    && TextUtils.equals(title, other.title)
                    && TextUtils.equals(desc, other.desc);
        }
    }

    private static final class ActionSheetAdapter extends RecyclerView.Adapter<ActionSheetAdapter.ViewHolder> {
        private final List<ActionSheetItem> items = new ArrayList<>();
        private ActionSheetHandler handler;

        ActionSheetAdapter() {
            setHasStableIds(true);
        }

        void submitItems(List<ActionSheetItem> nextItems, ActionSheetHandler nextHandler) {
            List<ActionSheetItem> targetItems = nextItems == null ? Collections.emptyList() : new ArrayList<>(nextItems);
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
            handler = nextHandler;
            diffResult.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_node_action, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ActionSheetItem item = items.get(position);
            holder.tvName.setText(item.title);
            holder.tvDesc.setText(item.desc);
            holder.itemView.setAlpha(item.enabled ? 1f : 0.4f);
            holder.itemView.setOnClickListener(v -> {
                if (item.enabled && handler != null) {
                    handler.onAction(item);
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

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvDesc;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_action_name);
                tvDesc = itemView.findViewById(R.id.tv_action_desc);
            }
        }
    }

    private static final class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.ViewHolder> {
        private static final int TYPE_LIST_ROW = 0;
        private static final int TYPE_PROJECT_CARD = 1;

        interface OnItemClickListener {
            void onClick(EntryItem item);
        }

        interface OnMoreClickListener {
            void onMore(EntryItem item, View anchor);
        }

        private final List<EntryItem> items = new ArrayList<>();
        private final OnItemClickListener clickListener;
        private final OnMoreClickListener moreClickListener;

        EntryAdapter(OnItemClickListener clickListener, OnMoreClickListener moreClickListener) {
            this.clickListener = clickListener;
            this.moreClickListener = moreClickListener;
            setHasStableIds(true);
        }

        void submitItems(List<EntryItem> nextItems) {
            List<EntryItem> targetItems = nextItems == null ? Collections.emptyList() : new ArrayList<>(nextItems);
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
            diffResult.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutRes = viewType == TYPE_PROJECT_CARD
                    ? R.layout.item_home_project_card
                    : R.layout.item_home_panel_row;
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EntryItem item = items.get(position);
            holder.icon.setImageResource(item.iconRes);
            holder.title.setText(item.title);
            holder.subtitle.setText(item.subtitle);
            if (TextUtils.isEmpty(item.badge)) {
                holder.badge.setVisibility(View.GONE);
            } else {
                holder.badge.setVisibility(View.VISIBLE);
                holder.badge.setText(item.badge);
            }
            holder.more.setVisibility(item.showMore ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> clickListener.onClick(item));
            holder.more.setOnClickListener(v -> moreClickListener.onMore(item, v));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).stableId();
        }

        @Override
        public int getItemViewType(int position) {
            EntryItem item = items.get(position);
            return item.payload instanceof HomeProjectRepository.ProjectSummary
                    ? TYPE_PROJECT_CARD
                    : TYPE_LIST_ROW;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView subtitle;
            final TextView badge;
            final ImageView more;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.iv_icon);
                title = itemView.findViewById(R.id.tv_title);
                subtitle = itemView.findViewById(R.id.tv_subtitle);
                badge = itemView.findViewById(R.id.tv_badge);
                more = itemView.findViewById(R.id.iv_more);
            }
        }
    }

    private final HomeProjectRepository repository = new HomeProjectRepository();
    private final ExecutorService panelLoadExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger panelLoadToken = new AtomicInteger();

    private TextView tvPermissionStatus;
    private TextView btnPermissionQuick;
    private TextView btnToggleFloat;
    private ImageView btnPanelBack;
    private ImageView btnPanelAdd;
    private ImageView btnPanelRefresh;
    private TextView tvPanelTitle;
    private TextView tvPanelBreadcrumb;
    private TextView tvPanelHint;
    private View emptyProjectsLayout;
    private TextView tvEmptyTitle;
    private TextView tvEmptyHint;
    private TextView btnEmptyAction;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvProjects;
    private GridLayoutManager projectGridLayoutManager;
    private LinearLayoutManager panelLinearLayoutManager;
    private Dialog actionSheetDialog;
    private TextView actionSheetTitleView;
    private RecyclerView actionSheetListView;
    private ActionSheetAdapter actionSheetAdapter;

    private boolean floatEnabled;
    private Level currentLevel = Level.PROJECT;
    private File currentProjectDir;
    private File currentTaskDir;
    private EntryAdapter entryAdapter;

    private final ActivityResultLauncher<Intent> projectionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ScreenCapture.saveProjectionPermission(result.getResultCode(), result.getData());
                    ScreenCaptureManager.getInstance().init(this);
                    Toast.makeText(this, R.string.toast_projection_granted, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.toast_projection_denied, Toast.LENGTH_SHORT).show();
                }
                refreshPermissionStatus();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScreenCaptureManager.getInstance().init(this);
        ActivityHolder.register(getApplication());
        OpenCVLoader.initLocal();
        // 限制 OpenCV 内部线程数，避免 matchTemplate 等操作占满全部核心引起过热
        Core.setNumThreads(2);

        setContentView(R.layout.activity_main);
        bindViews();
        setupPanelList();
        setupReusableActionSheet();
        bindActions();
        syncFloatPanelState();
        refreshPermissionStatus();
        showProjects();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncFloatPanelState();
        refreshPermissionStatus();
        reloadCurrentLevel();
    }

    @Override
    protected void onDestroy() {
        panelLoadToken.incrementAndGet();
        panelLoadExecutor.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        tvPermissionStatus = findViewById(R.id.tv_permission_status);
        btnPermissionQuick = findViewById(R.id.btn_permission_quick);
        btnToggleFloat = findViewById(R.id.btn_toggle_float);
        btnPanelBack = findViewById(R.id.btn_panel_back);
        btnPanelAdd = findViewById(R.id.btn_panel_add);
        btnPanelRefresh = findViewById(R.id.btn_panel_refresh);
        tvPanelTitle = findViewById(R.id.tv_panel_title);
        tvPanelBreadcrumb = findViewById(R.id.tv_panel_breadcrumb);
        tvPanelHint = findViewById(R.id.tv_panel_hint);
        emptyProjectsLayout = findViewById(R.id.layout_empty_projects);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptyHint = findViewById(R.id.tv_empty_hint);
        btnEmptyAction = findViewById(R.id.btn_empty_action);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_projects);
        rvProjects = findViewById(R.id.rv_projects);
    }

    private void setupPanelList() {
        projectGridLayoutManager = new GridLayoutManager(this, resolveProjectSpanCount());
        panelLinearLayoutManager = new LinearLayoutManager(this);
        entryAdapter = new EntryAdapter(this::handleEntryClick, this::handleEntryMore);
        rvProjects.setLayoutManager(projectGridLayoutManager);
        rvProjects.setAdapter(entryAdapter);
        RecyclerViewOptimizer.optimize(rvProjects, false);
        swipeRefreshLayout.setOnRefreshListener(this::reloadCurrentLevel);
    }

    private void setupReusableActionSheet() {
        View popupView = LayoutInflater.from(this).inflate(R.layout.dialog_node_action_sheet, null, false);
        actionSheetTitleView = popupView.findViewById(R.id.tv_action_title);
        actionSheetListView = popupView.findViewById(R.id.rv_action_list);
        actionSheetListView.setLayoutManager(new LinearLayoutManager(this));
        actionSheetAdapter = new ActionSheetAdapter();
        actionSheetListView.setAdapter(actionSheetAdapter);
        actionSheetDialog = new AlertDialog.Builder(this)
                .setView(popupView)
                .create();
    }

    private void bindActions() {
        findViewById(R.id.btn_permission_sheet).setOnClickListener(v -> showPermissionSheet());
        btnPermissionQuick.setOnClickListener(v -> requestNextMissingPermission());
        btnToggleFloat.setOnClickListener(v -> toggleFloatPanel());
        btnPanelBack.setOnClickListener(v -> navigatePanelBack());
        btnPanelAdd.setOnClickListener(v -> handlePrimaryCreateAction());
        btnPanelRefresh.setOnClickListener(v -> reloadCurrentLevel());
        btnEmptyAction.setOnClickListener(v -> handlePrimaryCreateAction());
    }

    private void refreshPermissionStatus() {
        int readyCount = 0;
        if (AutoAccessibilityService.isConnected()) readyCount++;
        if (ScreenCapture.hasProjectionPermission()) readyCount++;
        if (canDrawOverlays()) readyCount++;
        if (BatteryHardeningHelper.isIgnoringBatteryOptimizations(this)) readyCount++;
        tvPermissionStatus.setText(readyCount == 4
                ? "权限已就绪"
                : "权限 " + readyCount + "/4 已就绪");
        btnPermissionQuick.setText(readyCount == 4 ? "已就绪" : "一键获取");
        btnPermissionQuick.setEnabled(readyCount < 4);
        btnPermissionQuick.setAlpha(readyCount == 4 ? 0.55f : 1f);
        updateFloatToggleButton();
    }

    private void toggleFloatPanel() {
        if (!canDrawOverlays()) {
            requestOverlayPermission();
            return;
        }
        if (floatEnabled) {
            stopFloatService();
            floatEnabled = false;
            Toast.makeText(this, R.string.toast_float_panel_stopped, Toast.LENGTH_SHORT).show();
        } else {
            startFloatService();
            floatEnabled = true;
            Toast.makeText(this, R.string.toast_float_panel_started, Toast.LENGTH_SHORT).show();
        }
        refreshPermissionStatus();
    }

    private void updateFloatToggleButton() {
        btnToggleFloat.setText(floatEnabled ? "关闭悬浮窗" : "启动悬浮窗");
        btnToggleFloat.setBackgroundResource(
                floatEnabled ? R.drawable.project_panel_btn_secondary : R.drawable.project_panel_btn_primary
        );
        btnToggleFloat.setTextColor(ContextCompat.getColor(
                this,
                android.R.color.white
        ));
    }

    private void showPermissionSheet() {
        View sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_home_permission_sheet, null, false);
        TextView tvDesc = sheetView.findViewById(R.id.tv_permission_sheet_desc);
        TextView btnAccessibility = sheetView.findViewById(R.id.btn_permission_accessibility);
        TextView btnProjection = sheetView.findViewById(R.id.btn_permission_projection);
        TextView btnOverlay = sheetView.findViewById(R.id.btn_permission_overlay);
        TextView btnBattery = sheetView.findViewById(R.id.btn_permission_battery);

        btnAccessibility.setText(formatPermissionRow("无障碍", AutoAccessibilityService.isConnected()));
        btnProjection.setText(formatPermissionRow("录屏授权", ScreenCapture.hasProjectionPermission()));
        btnOverlay.setText(formatPermissionRow("悬浮窗", canDrawOverlays()));
        btnBattery.setText(formatPermissionRow("权限加固", BatteryHardeningHelper.isIgnoringBatteryOptimizations(this)));
        tvDesc.setText("这里统一处理主界面所需权限，不占用项目面板空间。");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(sheetView)
                .create();

        btnAccessibility.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        btnProjection.setOnClickListener(v -> {
            dialog.dismiss();
            requestMediaProjection();
        });
        btnOverlay.setOnClickListener(v -> {
            dialog.dismiss();
            requestOverlayPermission();
        });
        btnBattery.setOnClickListener(v -> {
            dialog.dismiss();
            requestBatteryOptimizationExemption();
        });
        dialog.show();
    }

    private String formatPermissionRow(String label, boolean ready) {
        return ready ? label + "  已就绪" : label + "  去处理";
    }

    private void requestNextMissingPermission() {
        if (!AutoAccessibilityService.isConnected()) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        if (!ScreenCapture.hasProjectionPermission()) {
            requestMediaProjection();
            return;
        }
        if (!canDrawOverlays()) {
            requestOverlayPermission();
            return;
        }
        if (!BatteryHardeningHelper.isIgnoringBatteryOptimizations(this)) {
            requestBatteryOptimizationExemption();
            return;
        }
        Toast.makeText(this, "所需权限都已经就绪", Toast.LENGTH_SHORT).show();
    }

    private void reloadCurrentLevel() {
        if (currentLevel == Level.PROJECT) {
            showProjects();
            return;
        }
        if (currentLevel == Level.TASK && currentProjectDir != null) {
            showTasks(currentProjectDir);
            return;
        }
        showProjects();
    }

    private void showProjects() {
        currentLevel = Level.PROJECT;
        currentProjectDir = null;
        currentTaskDir = null;
        applyPanelLayoutManager(Level.PROJECT);
        updatePanelChrome("Projects", "主界面 / 项目", "这里直接管理全部项目，不再跳转旧页面。", false, true);
        loadPanelEntries(Level.PROJECT, null);
    }

    private void showTasks(File projectDir) {
        currentLevel = Level.TASK;
        currentProjectDir = projectDir;
        currentTaskDir = null;
        applyPanelLayoutManager(Level.TASK);
        updatePanelChrome(projectDir.getName(), "项目 / " + projectDir.getName(), "当前项目下的 Task 列表和菜单操作。", true, true);
        loadPanelEntries(Level.TASK, projectDir);
    }

    private void loadPanelEntries(Level level, File projectDir) {
        final int token = panelLoadToken.incrementAndGet();
        swipeRefreshLayout.setRefreshing(true);
        panelLoadExecutor.execute(() -> {
            List<EntryItem> entries = buildEntries(level, projectDir);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || token != panelLoadToken.get()) {
                    return;
                }
                if (level == Level.PROJECT) {
                    if (currentLevel != Level.PROJECT) {
                        return;
                    }
                    updateEmptyState(entries.isEmpty(), "暂无项目", "点击右上角添加项目", "创建项目");
                } else {
                    if (currentLevel != Level.TASK || currentProjectDir == null || projectDir == null
                            || !TextUtils.equals(currentProjectDir.getAbsolutePath(), projectDir.getAbsolutePath())) {
                        return;
                    }
                    updateEmptyState(entries.isEmpty(), "暂无 Task", "点击右上角添加 Task", "创建 Task");
                }
                entryAdapter.submitItems(entries);
                swipeRefreshLayout.setRefreshing(false);
            });
        });
    }

    private List<EntryItem> buildEntries(Level level, File projectDir) {
        List<EntryItem> entries = new ArrayList<>();
        if (level == Level.PROJECT) {
            List<HomeProjectRepository.ProjectSummary> projects = repository.loadProjects(this);
            for (HomeProjectRepository.ProjectSummary project : projects) {
                String subtitle = project.taskCount <= 0
                        ? "暂无 Task"
                        : project.taskCount + " 个 Task";
                entries.add(new EntryItem(
                        R.drawable.ic_folder_colored,
                        project.name,
                        subtitle,
                        null,
                        true,
                        project));
            }
            return entries;
        }
        List<HomeProjectRepository.TaskSummary> tasks = repository.loadTasks(projectDir);
        for (HomeProjectRepository.TaskSummary task : tasks) {
            String subtitle = task.assetCount > 0
                    ? task.operationCount + " 个节点 · " + task.assetCount + " 个资源"
                    : task.operationCount + " 个节点";
            entries.add(new EntryItem(
                    R.drawable.ic_folder,
                    task.name,
                    subtitle,
                    task.operationCount <= 0 ? null : task.operationCount + " 节点",
                    true,
                    task));
        }
        return entries;
    }

    private void applyPanelLayoutManager(Level level) {
        RecyclerView.LayoutManager target = level == Level.PROJECT
                ? projectGridLayoutManager
                : panelLinearLayoutManager;
        if (rvProjects.getLayoutManager() != target) {
            rvProjects.setLayoutManager(target);
        }
    }

    private int resolveProjectSpanCount() {
        float widthDp = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        if (widthDp >= 840f) {
            return 3;
        }
        return 2;
    }

    private void updatePanelChrome(String title, String breadcrumb, String hint, boolean showBack, boolean showAdd) {
        tvPanelTitle.setText(title);
        tvPanelBreadcrumb.setVisibility(View.VISIBLE);
        tvPanelBreadcrumb.setText(breadcrumb);
        tvPanelHint.setText(hint);
        btnPanelBack.setVisibility(showBack ? View.VISIBLE : View.GONE);
        btnPanelAdd.setVisibility(showAdd ? View.VISIBLE : View.GONE);
    }

    private void updateEmptyState(boolean isEmpty, String title, String hint, String actionText) {
        emptyProjectsLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvProjects.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        tvEmptyTitle.setText(title);
        tvEmptyHint.setText(hint);
        if (TextUtils.isEmpty(actionText)) {
            btnEmptyAction.setVisibility(View.GONE);
        } else {
            btnEmptyAction.setVisibility(View.VISIBLE);
            btnEmptyAction.setText(actionText);
        }
    }

    private void navigatePanelBack() {
        if (currentLevel == Level.TASK) {
            showProjects();
        }
    }

    private void handlePrimaryCreateAction() {
        if (currentLevel == Level.PROJECT) {
            showCreateProjectDialog();
            return;
        }
        if (currentLevel == Level.TASK) {
            showCreateTaskDialog();
            return;
        }
    }

    private void handleEntryClick(EntryItem item) {
        if (item.payload instanceof HomeProjectRepository.ProjectSummary) {
            showTasks(((HomeProjectRepository.ProjectSummary) item.payload).dir);
            return;
        }
        if (item.payload instanceof HomeProjectRepository.TaskSummary) {
            openTaskInFloatPanel(((HomeProjectRepository.TaskSummary) item.payload).dir, true);
        }
    }

    private void handleEntryMore(EntryItem item, View anchor) {
        if (item.payload instanceof HomeProjectRepository.ProjectSummary) {
            showProjectMenu(((HomeProjectRepository.ProjectSummary) item.payload).dir);
            return;
        }
        if (item.payload instanceof HomeProjectRepository.TaskSummary) {
            showTaskMenu(((HomeProjectRepository.TaskSummary) item.payload).dir);
            return;
        }
    }

    private void showProjectMenu(File projectDir) {
        if (projectDir == null) {
            return;
        }
        List<ActionSheetItem> items = new ArrayList<>();
        items.add(new ActionSheetItem(1, "打开项目", "进入这个项目下的 Task 列表", true));
        items.add(new ActionSheetItem(2, "新建 Task", "在当前项目里新增一个 Task", true));
        items.add(new ActionSheetItem(3, "重命名项目", "修改当前项目名称", true));
        items.add(new ActionSheetItem(4, "删除项目", "从本地删除这个项目", true));
        showActionSheet(projectDir.getName(), items, item -> {
            if (item.id == 1) {
                showTasks(projectDir);
                return;
            }
            if (item.id == 2) {
                currentProjectDir = projectDir;
                showCreateTaskDialog();
                return;
            }
            if (item.id == 3) {
                showRenameDialog(projectDir, true);
                return;
            }
            if (item.id == 4) {
                showDeleteDialog(projectDir, true);
            }
        });
    }

    private void showTaskMenu(File taskDir) {
        if (taskDir == null) {
            return;
        }
        List<ActionSheetItem> items = new ArrayList<>();
        items.add(new ActionSheetItem(1, "在悬浮窗打开", "切到这个 Task 的节点页面", true));
        items.add(new ActionSheetItem(2, "重命名 Task", "修改当前 Task 名称", true));
        items.add(new ActionSheetItem(3, "删除 Task", "从本地删除这个 Task", true));
        showActionSheet(taskDir.getName(), items, item -> {
            if (item.id == 1) {
                openTaskInFloatPanel(taskDir, true);
                return;
            }
            if (item.id == 2) {
                showRenameTaskDialog(taskDir);
                return;
            }
            if (item.id == 3) {
                showDeleteTaskDialog(taskDir);
            }
        });
    }

    private void showCreateProjectDialog() {
        EditText input = new EditText(this);
        input.setHint("输入项目名称");
        new AlertDialog.Builder(this)
                .setTitle("新建项目")
                .setView(input)
                .setPositiveButton("创建", (dialog, which) -> {
                    File dir = repository.createProject(this, input.getText().toString());
                    if (dir == null) {
                        Toast.makeText(this, "项目创建失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, "项目已创建", Toast.LENGTH_SHORT).show();
                    showTasks(dir);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCreateTaskDialog() {
        if (currentProjectDir == null) {
            return;
        }
        EditText input = new EditText(this);
        input.setHint("输入 Task 名称");
        new AlertDialog.Builder(this)
                .setTitle("新建 Task")
                .setView(input)
                .setPositiveButton("创建", (dialog, which) -> {
                    File dir = repository.createTask(currentProjectDir, input.getText().toString());
                    if (dir == null) {
                        Toast.makeText(this, "Task 创建失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, "Task 已创建", Toast.LENGTH_SHORT).show();
                    showTasks(currentProjectDir);
                    openTaskInFloatPanel(dir, false);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRenameDialog(File dir, boolean refreshProjects) {
        EditText input = new EditText(this);
        input.setText(dir.getName());
        new AlertDialog.Builder(this)
                .setTitle(refreshProjects ? "重命名项目" : "重命名 Task")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    boolean renamed = repository.rename(dir, input.getText().toString());
                    if (!renamed) {
                        Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show();
                    if (refreshProjects) {
                        showProjects();
                    } else {
                        File refreshedProjectDir = currentProjectDir;
                        if (refreshedProjectDir != null) {
                            showTasks(refreshedProjectDir);
                        } else {
                            showProjects();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteDialog(File dir, boolean refreshProjects) {
        new AlertDialog.Builder(this)
                .setTitle(refreshProjects ? "删除项目" : "删除 Task")
                .setMessage("确定删除 \"" + dir.getName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    boolean deleted = repository.deleteRecursively(dir);
                    if (!deleted) {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                    if (refreshProjects) {
                        showProjects();
                    } else if (currentProjectDir != null) {
                        showTasks(currentProjectDir);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRenameTaskDialog(File taskDir) {
        EditText input = new EditText(this);
        input.setText(taskDir.getName());
        new AlertDialog.Builder(this)
                .setTitle("重命名 Task")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String rawName = input.getText().toString();
                    String safeName = rawName == null ? "" : rawName.trim();
                    if (TextUtils.isEmpty(safeName)) {
                        Toast.makeText(this, "Task 名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File target = new File(taskDir.getParentFile(), safeName.replace("/", "_").replace("\\", "_"));
                    boolean renamed = repository.rename(taskDir, rawName);
                    if (!renamed) {
                        Toast.makeText(this, "Task 重命名失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (currentTaskDir != null && currentTaskDir.equals(taskDir)) {
                        currentTaskDir = target;
                    } else if (currentProjectDir != null) {
                        showTasks(currentProjectDir);
                    } else {
                        showProjects();
                    }
                    Toast.makeText(this, "Task 已重命名", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteTaskDialog(File taskDir) {
        new AlertDialog.Builder(this)
                .setTitle("删除 Task")
                .setMessage("确定删除 \"" + taskDir.getName() + "\" 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    boolean deleted = repository.deleteRecursively(taskDir);
                    if (!deleted) {
                        Toast.makeText(this, "删除 Task 失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, "Task 已删除", Toast.LENGTH_SHORT).show();
                    if (currentProjectDir != null) {
                        showTasks(currentProjectDir);
                    } else {
                        showProjects();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showActionSheet(String title, List<ActionSheetItem> items, ActionSheetHandler handler) {
        if (actionSheetDialog == null || actionSheetTitleView == null || actionSheetAdapter == null) {
            setupReusableActionSheet();
        }
        actionSheetTitleView.setText(title);
        actionSheetAdapter.submitItems(items, item -> {
            actionSheetDialog.dismiss();
            handler.onAction(item);
        });
        actionSheetDialog.show();
    }

    private void openTaskInFloatPanel(File taskDir, boolean showToast) {
        if (taskDir == null || !taskDir.isDirectory()) {
            Toast.makeText(this, "Task 不存在，无法打开悬浮窗", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!canDrawOverlays()) {
            requestOverlayPermission();
            return;
        }
        Intent intent = new Intent(this, FloatWindowService.class);
        intent.setAction(FloatWindowService.ACTION_OPEN_TASK_PANEL);
        if (currentProjectDir != null) {
            intent.putExtra(FloatWindowService.EXTRA_PROJECT_PATH, currentProjectDir.getAbsolutePath());
        }
        intent.putExtra(FloatWindowService.EXTRA_TASK_PATH, taskDir.getAbsolutePath());
        startService(intent);
        floatEnabled = true;
        refreshPermissionStatus();
    }

    private void requestMediaProjection() {
        if (ScreenCaptureManager.getInstance().isRunning()) {
            Toast.makeText(this, R.string.toast_capture_running, Toast.LENGTH_SHORT).show();
            return;
        }
        projectionLauncher.launch(ScreenCapture.createProjectionIntent(this));
    }

    private void requestOverlayPermission() {
        if (canDrawOverlays()) {
            Toast.makeText(this, R.string.toast_overlay_already_granted, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
        Toast.makeText(this, R.string.toast_overlay_prompt, Toast.LENGTH_LONG).show();
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void requestBatteryOptimizationExemption() {
        if (BatteryHardeningHelper.isIgnoringBatteryOptimizations(this)) {
            Toast.makeText(this, R.string.toast_battery_optimization_already_ignored, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.toast_hardening_recommendation, Toast.LENGTH_LONG).show();
        }
        BatteryHardeningHelper.showHardeningDialog(this);
    }

    private void startFloatService() {
        Intent intent = new Intent(this, FloatWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopFloatService() {
        stopService(new Intent(this, FloatWindowService.class));
    }

    @SuppressWarnings("deprecation")
    private void syncFloatPanelState() {
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (activityManager == null) {
            floatEnabled = false;
            return;
        }
        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (FloatWindowService.class.getName().equals(service.service.getClassName())) {
                floatEnabled = true;
                return;
            }
        }
        floatEnabled = false;
    }
}
