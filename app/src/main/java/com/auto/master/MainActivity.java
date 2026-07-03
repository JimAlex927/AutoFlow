package com.auto.master;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
import androidx.core.view.WindowCompat;
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
import com.auto.master.importer.ProjectImportPickerActivity;
import com.auto.master.ui.EntityActionSheetAdapter;
import com.auto.master.utils.BatteryHardeningHelper;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final String SUPPORT_ASSET_NAME = "support.jpg";
    private static final String SUPPORT_PREFS = "AutoMasterSupport";
    private static final String PREF_SUPPORT_PROMPT_SEEN = "support_prompt_seen";

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
            if (payload instanceof HomeProjectRepository.ProjectSummary) {
                return 1;
            }
            if (payload instanceof HomeProjectRepository.TaskSummary) {
                return 2;
            }
            return 0;
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
                    || item.payload instanceof HomeProjectRepository.TaskSummary
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
    private TextView navHomeProjects;
    private TextView navHomePermissions;
    private TextView navHomeLab;
    private TextView navHomeSupport;
    private View layoutHomeQuickActions;
    private View layoutPanelHeader;
    private TextView btnHomeNewScript;
    private TextView btnHomeImportScript;
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
    private TextView actionSheetNameView;
    private TextView actionSheetHintView;
    private RecyclerView actionSheetListView;
    private TextView actionSheetPrimaryButton;
    private EntityActionSheetAdapter actionSheetAdapter;

    private boolean floatEnabled;
    private boolean shouldAutoStartFloat = true;
    private Level currentLevel = Level.PROJECT;
    private File currentProjectDir;
    private File currentTaskDir;
    private EntryAdapter entryAdapter;
    private boolean pendingSupportImageSave;

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

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> refreshPermissionStatus());

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingSupportImageSave) {
                    pendingSupportImageSave = false;
                    saveSupportImageToGallery();
                } else {
                    pendingSupportImageSave = false;
                    Toast.makeText(this, "未获得存储权限，无法保存收款码", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScreenCaptureManager.getInstance().init(this);
        ActivityHolder.register(getApplication());
        OpenCVLoader.initLocal();
        // 限制 OpenCV 内部线程数，避免 matchTemplate 等操作占满全部核心引起过热
        Core.setNumThreads(2);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_main);
        bindViews();
        setupPanelList();
        setupReusableActionSheet();
        bindActions();
        requestNotificationPermissionIfNeeded();
        syncFloatPanelState();
        maybeAutoStartFloatService();
        refreshPermissionStatus();
        showProjects();
        maybeShowSupportPrompt();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncFloatPanelState();
        maybeAutoStartFloatService();
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
        navHomeProjects = findViewById(R.id.nav_home_projects);
        navHomePermissions = findViewById(R.id.nav_home_permissions);
        navHomeLab = findViewById(R.id.nav_home_lab);
        navHomeSupport = findViewById(R.id.nav_home_support);
        layoutHomeQuickActions = findViewById(R.id.layout_home_quick_actions);
        layoutPanelHeader = findViewById(R.id.layout_panel_header);
        btnHomeNewScript = findViewById(R.id.btn_home_new_script);
        btnHomeImportScript = findViewById(R.id.btn_home_import_script);
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
        View popupView = LayoutInflater.from(this).inflate(R.layout.dialog_entity_action_sheet, null, false);
        View scrim = popupView.findViewById(R.id.entity_action_scrim);
        View panel = popupView.findViewById(R.id.entity_action_panel);
        actionSheetTitleView = popupView.findViewById(R.id.tv_entity_action_title);
        actionSheetNameView = popupView.findViewById(R.id.tv_entity_action_name);
        actionSheetHintView = popupView.findViewById(R.id.tv_entity_action_hint);
        actionSheetListView = popupView.findViewById(R.id.rv_entity_action_grid);
        actionSheetPrimaryButton = popupView.findViewById(R.id.btn_entity_action_primary);
        TextView closeTop = popupView.findViewById(R.id.btn_entity_action_close_top);
        TextView closeBottom = popupView.findViewById(R.id.btn_entity_action_close);
        actionSheetListView.setLayoutManager(new GridLayoutManager(this, 3));
        actionSheetAdapter = new EntityActionSheetAdapter();
        actionSheetListView.setAdapter(actionSheetAdapter);
        actionSheetDialog = new Dialog(this);
        actionSheetDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        actionSheetDialog.setContentView(popupView);
        actionSheetDialog.setCanceledOnTouchOutside(true);
        View.OnClickListener closeListener = v -> actionSheetDialog.dismiss();
        scrim.setOnClickListener(closeListener);
        closeTop.setOnClickListener(closeListener);
        closeBottom.setOnClickListener(closeListener);
        panel.setOnClickListener(v -> {
        });
        Window window = actionSheetDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    private void bindActions() {
        findViewById(R.id.btn_permission_sheet).setOnClickListener(v -> showPermissionSheet());
        btnPermissionQuick.setOnClickListener(v -> requestNextMissingPermission());
        btnToggleFloat.setOnClickListener(v -> toggleFloatPanel());
        navHomeProjects.setOnClickListener(v -> {
            selectHomeProjects();
            if (currentLevel != Level.PROJECT) {
                showProjects();
            }
        });
        navHomePermissions.setOnClickListener(v -> {
            selectHomeProjects();
            showPermissionSheet();
        });
        navHomeLab.setOnClickListener(v -> {
            selectHomeProjects();
            Toast.makeText(this, "扩展入口已预留，后续功能会放在这里", Toast.LENGTH_SHORT).show();
        });
        navHomeSupport.setOnClickListener(v -> {
            selectHomeSupport();
            showSupportDialog();
        });
        btnHomeNewScript.setOnClickListener(v -> showCreateProjectDialog());
        btnHomeImportScript.setOnClickListener(v -> importProjectInteractive());
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
        if (canWriteSystemSettings()) readyCount++;
        tvPermissionStatus.setText(readyCount == 5
                ? "权限已就绪"
                : "权限 " + readyCount + "/5 已就绪");
        btnPermissionQuick.setText(readyCount == 5 ? "已就绪" : "一键获取");
        btnPermissionQuick.setEnabled(readyCount < 5);
        btnPermissionQuick.setAlpha(readyCount == 5 ? 0.55f : 1f);
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
            shouldAutoStartFloat = false;
            Toast.makeText(this, R.string.toast_float_panel_stopped, Toast.LENGTH_SHORT).show();
        } else {
            startFloatService();
            floatEnabled = true;
            shouldAutoStartFloat = true;
            Toast.makeText(this, R.string.toast_float_panel_started, Toast.LENGTH_SHORT).show();
        }
        refreshPermissionStatus();
    }

    private void maybeAutoStartFloatService() {
        if (!shouldAutoStartFloat || floatEnabled || !canDrawOverlays()) {
            return;
        }
        startFloatService();
        floatEnabled = true;
        shouldAutoStartFloat = false;
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
        TextView btnWriteSettings = sheetView.findViewById(R.id.btn_permission_write_settings);

        btnAccessibility.setText(formatPermissionRow("无障碍", AutoAccessibilityService.isConnected()));
        btnProjection.setText(formatPermissionRow("录屏授权", ScreenCapture.hasProjectionPermission()));
        btnOverlay.setText(formatPermissionRow("悬浮窗", canDrawOverlays()));
        btnBattery.setText(formatPermissionRow("权限加固", BatteryHardeningHelper.isIgnoringBatteryOptimizations(this)));
        btnWriteSettings.setText(formatPermissionRow("修改系统设置(亮度)", canWriteSystemSettings()));
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
        btnWriteSettings.setOnClickListener(v -> {
            dialog.dismiss();
            requestWriteSettingsPermission();
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
        if (!canWriteSystemSettings()) {
            requestWriteSettingsPermission();
            return;
        }
        Toast.makeText(this, "所需权限都已经就绪", Toast.LENGTH_SHORT).show();
    }

    private boolean canWriteSystemSettings() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this);
    }

    private void requestWriteSettingsPermission() {
        if (canWriteSystemSettings()) {
            Toast.makeText(this, "已拥有修改系统设置权限", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
        Toast.makeText(this, "请在系统设置中允许“修改系统设置”，用于亮度修改 operation", Toast.LENGTH_LONG).show();
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
        selectHomeProjects();
        currentLevel = Level.PROJECT;
        currentProjectDir = null;
        currentTaskDir = null;
        applyPanelLayoutManager(Level.PROJECT);
        updatePanelChrome("脚本", "首页 / 脚本", "仅用于模拟真人自动化办公，严禁制作/传播作弊器、刷评、刷单、外挂等损害其他平台利益的违法违规行为。", false, true);
        loadPanelEntries(Level.PROJECT, null);
    }

    private void showTasks(File projectDir) {
        selectHomeProjects();
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
            String subtitle = "脚本文件夹";
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
        RecyclerView.LayoutManager target = level == Level.PROJECT || level == Level.TASK
                ? projectGridLayoutManager
                : panelLinearLayoutManager;
        if (rvProjects.getLayoutManager() != target) {
            rvProjects.setLayoutManager(target);
        }
    }

    private int resolveProjectSpanCount() {
        return 2;
    }

    private void selectHomeProjects() {
        setSidebarSelected(navHomeProjects, true);
        setSidebarSelected(navHomePermissions, false);
        setSidebarSelected(navHomeLab, false);
        setSidebarSelected(navHomeSupport, false);
    }

    private void selectHomeSupport() {
        setSidebarSelected(navHomeProjects, false);
        setSidebarSelected(navHomePermissions, false);
        setSidebarSelected(navHomeLab, false);
        setSidebarSelected(navHomeSupport, true);
    }

    private void setSidebarSelected(TextView view, boolean selected) {
        if (view != null) {
            view.setSelected(selected);
        }
    }

    private void updatePanelChrome(String title, String breadcrumb, String hint, boolean showBack, boolean showAdd) {
        if (layoutHomeQuickActions != null) {
            layoutHomeQuickActions.setVisibility(showBack ? View.GONE : View.VISIBLE);
        }
        if (layoutPanelHeader != null) {
            layoutPanelHeader.setVisibility(showBack ? View.VISIBLE : View.GONE);
        }
        tvPanelTitle.setText(title);
        tvPanelBreadcrumb.setVisibility(View.VISIBLE);
        tvPanelBreadcrumb.setText(breadcrumb);
        tvPanelHint.setText(hint);
        tvPanelHint.setVisibility(showBack ? View.GONE : View.VISIBLE);
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
        items.add(new ActionSheetItem(1, "打开", "查看 Task", true));
        items.add(new ActionSheetItem(2, "新建 Task", "添加任务", true));
        items.add(new ActionSheetItem(3, "重命名", "修改名称", true));
        items.add(new ActionSheetItem(4, "删除", "移除项目", true));
        showActionSheet("项目操作", projectDir.getName(), "管理当前项目和 Task", items, item -> {
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
        items.add(new ActionSheetItem(1, "悬浮窗", "打开节点页", true));
        items.add(new ActionSheetItem(2, "重命名", "修改名称", true));
        items.add(new ActionSheetItem(3, "删除", "移除 Task", true));
        showActionSheet("任务操作", taskDir.getName(), "管理当前 Task", items, item -> {
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

    private void showActionSheet(
            String title,
            String name,
            String hint,
            List<ActionSheetItem> items,
            ActionSheetHandler handler
    ) {
        if (actionSheetDialog == null
                || actionSheetTitleView == null
                || actionSheetNameView == null
                || actionSheetHintView == null
                || actionSheetAdapter == null) {
            setupReusableActionSheet();
        }
        actionSheetTitleView.setText(title);
        actionSheetNameView.setText(name);
        actionSheetHintView.setText(hint);
        if (actionSheetPrimaryButton != null) {
            actionSheetPrimaryButton.setVisibility(View.GONE);
        }
        List<EntityActionSheetAdapter.Item> actionItems = new ArrayList<>();
        for (ActionSheetItem item : items) {
            actionItems.add(new EntityActionSheetAdapter.Item(
                    item.id,
                    resolveActionIcon(item),
                    item.title,
                    item.desc,
                    item.enabled));
        }
        actionSheetAdapter.submitItems(actionItems, item -> {
            actionSheetDialog.dismiss();
            ActionSheetItem actionItem = findActionItem(items, item.id);
            if (actionItem != null) {
                handler.onAction(actionItem);
            }
        });
        actionSheetDialog.show();
        Window window = actionSheetDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    private ActionSheetItem findActionItem(List<ActionSheetItem> items, int id) {
        for (ActionSheetItem item : items) {
            if (item.id == id) {
                return item;
            }
        }
        return null;
    }

    private int resolveActionIcon(ActionSheetItem item) {
        String title = item == null ? "" : String.valueOf(item.title);
        if (title.contains("打开")) {
            return android.R.drawable.ic_media_play;
        }
        if (title.contains("悬浮")) {
            return android.R.drawable.ic_dialog_dialer;
        }
        if (title.contains("新建")) {
            return android.R.drawable.ic_input_add;
        }
        if (title.contains("重命名")) {
            return android.R.drawable.ic_menu_edit;
        }
        if (title.contains("删除")) {
            return android.R.drawable.ic_menu_delete;
        }
        return android.R.drawable.ic_menu_manage;
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

    private void importProjectInteractive() {
        Intent intent = new Intent(this, ProjectImportPickerActivity.class);
        startActivity(intent);
    }

    private void maybeShowSupportPrompt() {
        SharedPreferences prefs = getSharedPreferences(SUPPORT_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(PREF_SUPPORT_PROMPT_SEEN, false)) {
            return;
        }
        prefs.edit().putBoolean(PREF_SUPPORT_PROMPT_SEEN, true).apply();
        rvProjects.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                showSupportDialog();
            }
        }, 700L);
    }

    private void showSupportDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_support, null, false);
        ImageView qrView = view.findViewById(R.id.iv_support_qr);
        TextView btnClose = view.findViewById(R.id.btn_support_close);
        TextView btnSave = view.findViewById(R.id.btn_support_save);

        try (InputStream in = getAssets().open(SUPPORT_ASSET_NAME)) {
            qrView.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (Exception e) {
            qrView.setImageResource(R.drawable.ic_file_image);
            Toast.makeText(this, "收款码加载失败", Toast.LENGTH_SHORT).show();
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> saveSupportImageWithPermission());
        dialog.setOnDismissListener(d -> selectHomeProjects());
        dialog.show();
    }

    private void saveSupportImageWithPermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingSupportImageSave = true;
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        saveSupportImageToGallery();
    }

    private void saveSupportImageToGallery() {
        String fileName = "AutoMaster_support_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + ".jpg";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveSupportImageWithMediaStore(fileName);
            } else {
                saveSupportImageLegacy(fileName);
            }
            Toast.makeText(this, "收款码已保存到相册", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveSupportImageWithMediaStore(String fileName) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AutoMaster");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IllegalStateException("无法创建相册文件");
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) {
                throw new IllegalStateException("无法写入相册文件");
            }
            copySupportAsset(out);
        }
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        getContentResolver().update(uri, values, null, null);
    }

    @SuppressWarnings("deprecation")
    private void saveSupportImageLegacy(String fileName) throws Exception {
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "AutoMaster");
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IllegalStateException("无法创建目录: " + dir.getAbsolutePath());
        }
        File outFile = new File(dir, fileName);
        try (OutputStream out = new FileOutputStream(outFile)) {
            copySupportAsset(out);
        }
        MediaScannerConnection.scanFile(
                this,
                new String[]{outFile.getAbsolutePath()},
                new String[]{"image/jpeg"},
                null);
    }

    private void copySupportAsset(OutputStream out) throws Exception {
        try (InputStream in = getAssets().open(SUPPORT_ASSET_NAME)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private void requestMediaProjection() {
        if (ScreenCaptureManager.getInstance().isRunning()) {
            Toast.makeText(this, R.string.toast_capture_running, Toast.LENGTH_SHORT).show();
            return;
        }
        projectionLauncher.launch(ScreenCapture.createProjectionIntent(this));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
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
