package com.auto.master.floatwin;

import static com.auto.master.auto.ScriptRunner.toastOnMain;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.res.Configuration;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;
import com.auto.master.Task.Handler.OperationHandler.CropRegionOperationHandler;
import com.auto.master.floatwin.adapter.FlowNodeAdapter;
import com.auto.master.floatwin.adapter.GestureLibraryAdapter;
import com.auto.master.floatwin.adapter.LaunchAppPickerAdapter;
import com.auto.master.floatwin.adapter.OperationIdPickerAdapter;
import com.auto.master.floatwin.adapter.TemplateLibraryAdapter;
import com.auto.master.Task.Handler.OperationHandler.OperationHandler;
import com.auto.master.Task.Handler.OperationHandler.OperationHandlerManager;
import com.auto.master.Task.Operation.CropRegionOperation;
import com.auto.master.Task.Operation.LoadImgToMatOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Operation.OperationType;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.Template.Template;
import com.auto.master.auto.ActivityHolder;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.auto.GestureOverlayView;
import com.auto.master.auto.SelectionOverlayView;
import com.auto.master.auto.ScriptExecuteContext;
import com.auto.master.auto.ScriptRunner;
import com.auto.master.ocr.OcrEngine;
import com.auto.master.importer.ProjectImportPickerActivity;
import com.auto.master.importer.ScriptPackageManager;
import com.auto.master.utils.AdaptivePollingController;
import com.auto.master.utils.CrashLogger;
import com.auto.master.utils.OpenCVHelper;
import com.auto.master.utils.OperationGsonUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.opencv.core.Mat;

import org.json.JSONArray;
import org.json.JSONObject;

public class FloatWindowService extends Service implements ScriptRunner.ScriptExecutionListener, FloatWindowHost {

    private static final String TAG = "FloatWindowService";
    private static final int PROJECT_PANEL_VIEW_TYPE_PROJECT = 1001;
    private static final int PROJECT_PANEL_VIEW_TYPE_TASK = 1002;
    private static final int PROJECT_PANEL_VIEW_TYPE_OPERATION = 1003;
    private static final String ACTION_STOP_SELF = "com.auto.master.floatwin.action.STOP_SELF";
    private static final String ACTION_REFRESH_PROJECTS = "com.auto.master.floatwin.action.REFRESH_PROJECTS";
    public static final String ACTION_OPEN_TASK_PANEL = "com.auto.master.floatwin.action.OPEN_TASK_PANEL";
    public static final String EXTRA_PROJECT_PATH = "project_path";
    public static final String EXTRA_TASK_PATH = "task_path";
    private static final long TASK_REMOVED_RESTART_DELAY_MS = 600L;
    private WindowManager wm;

    private View ballView;
    private WindowManager.LayoutParams ballLp;

    private View projectPanelView;
    private WindowManager.LayoutParams projectPanelLp;
    private View projectPanelDockView;
    private WindowManager.LayoutParams projectPanelDockLp;
    private boolean projectPanelDockOnLeft = true;
    private int sharedPanelX = Integer.MIN_VALUE;
    private int sharedPanelY = Integer.MIN_VALUE;

    private enum NavigationLevel {PROJECT, TASK, OPERATION}

    private NavigationLevel currentLevel = NavigationLevel.PROJECT;
    private File currentProjectDir;
    private File currentTaskDir;

    /**
     * 用于优化 悬浮窗project面板的缓存
     */
    private List<Project> cachedProjects = new ArrayList<>();

    /*
    用户当前的 operations的 adapter 也就是 展示 operations的 那个页面

     */
    private OperationPanelAdapter currentOperationAdapter;


    // 类成员变量：当前选中的 OperationItem（null 表示未选中）
    private OperationItem selectedOperation = null;
    
    // 悬浮球上的状态文字
    private TextView ballStatusText;
    // 当前正在运行的 operation 名称
    private String currentRunningOperationName = "";
    private String currentRunningOperationId = "";
    private String runtimeStatusText = "待机";
    private int runtimeStatusColor = 0xFF4CAF50;
    private static final int PROJECT_PANEL_DEFAULT_W_DP = 320;
    private static final int PROJECT_PANEL_DEFAULT_H_DP = 440;
    private static final int PROJECT_PANEL_MIN_W_DP = 220;
    private static final int PROJECT_PANEL_MIN_H_DP = 240;
    private static final float PROJECT_PANEL_MAX_H_RATIO_LANDSCAPE = 0.78f;
    private static final float PROJECT_PANEL_MAX_H_RATIO_PORTRAIT = 0.88f;
    private static final int PROJECT_PANEL_DOCK_W_DP = 16;
    private static final int PROJECT_PANEL_DOCK_H_DP = 112;
    private static final int PROJECT_PANEL_DOCK_TRIGGER_DP = 18;
    private static final int PROJECT_PANEL_DOCK_MARGIN_DP = 6;
    private static final long CAPTURE_UI_SETTLE_DELAY_MS = 320L;
    private static final int TEMPLATE_CAPTURE_PREVIEW_MAX_RETRIES = 240;
    private static final int RUNNING_PANEL_MIN_W_DP = 260;
    private static final int RUNNING_PANEL_MIN_H_DP = 320;
    
    // ========== 运行状态面板相关 ==========
    private View runningPanelView;
    private WindowManager.LayoutParams runningPanelLp;
    private boolean isRunningPanelShowing = false;
    
    // 运行状态数据
    private List<OperationItem> runningOperations = new ArrayList<>();
    private RunningPanelAdapter runningPanelAdapter;
    private int currentOperationIndex = 0;
    private int totalOperationCount = 0;
    private boolean isPaused = false;
    
    // 当前运行的 project 和 task 信息
    private String currentRunningProject = "";
    private String currentRunningTask = "";
    private final List<String> currentRunLogs = new ArrayList<>();
    private final Map<String, Long> opStartTimeMs = new HashMap<>();
    private final List<Long> opDurationsMs = new ArrayList<>();
    private final Map<String, Integer> opFailureReasons = new HashMap<>();
    private int opSuccessCount = 0;
    private int opFailureCount = 0;
    private String latestFailureReason = "-";
    private long currentRunStartMs = 0L;
    private static final long DELAY_PROGRESS_UPDATE_INTERVAL_MS = 220L;
    private String activeDelayOperationId;
    private long activeDelayDurationMs = 0L;
    private long activeDelayStartMs = 0L;
    private int lastDelayOverlayProgress = -1;
    private String lastDelayOverlayText = "";

    private String currentSearchQuery = "";
    private final List<OperationClipboardEntry> operationClipboardLibrary = new ArrayList<>();
    private static final int OPERATION_CLIPBOARD_LIMIT = 24;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRefreshRunnable = this::refreshCurrentLevelList;
    private final Runnable delayProgressRunnable = this::tickDelayProgress;
    private RecyclerView projectPanelRecyclerView;
    private ProjectPanelAdapter projectPanelAdapter;
    private TaskPanelAdapter taskPanelAdapter;
    private OperationPanelAdapter operationPanelAdapter;
    private PopupWindow taskActionPopupWindow;
    private TextView taskActionPopupTitleView;
    private RecyclerView taskActionPopupListView;
    private final List<OperationPanelAdapter.ActionItem> taskActionSheetItems = new ArrayList<>();
    private OperationPanelAdapter.ActionSheetAdapter taskActionSheetAdapter;
    @Nullable
    private java.util.function.Consumer<OperationPanelAdapter.ActionItem> taskActionSheetHandler;
    private PopupWindow nodeFloatActionPopupWindow;
    private TextView nodeFloatActionPopupTitleView;
    private RecyclerView nodeFloatActionPopupListView;
    private final List<OperationPanelAdapter.ActionItem> nodeFloatActionSheetItems = new ArrayList<>();
    private OperationPanelAdapter.ActionSheetAdapter nodeFloatActionSheetAdapter;
    @Nullable
    private java.util.function.Consumer<OperationPanelAdapter.ActionItem> nodeFloatActionSheetHandler;
    private ItemTouchHelper operationDragHelper;
    private boolean projectPanelContentDirty = true;
    private long projectListCacheVersion = Long.MIN_VALUE;
    private final List<ProjectListItem> projectListCache = new ArrayList<>();
    private File taskListCacheProjectDir;
    private long taskListCacheVersion = Long.MIN_VALUE;
    private final List<File> taskListCache = new ArrayList<>();
    private final Map<String, List<File>> taskItemsMemoryCache = new HashMap<>();
    private final Map<String, Long> taskItemsMemoryVersions = new HashMap<>();
    private File operationListCacheTaskDir;
    private long operationListCacheVersion = Long.MIN_VALUE;
    private final List<OperationItem> operationListCache = new ArrayList<>();
    private final Map<String, List<OperationItem>> operationItemsMemoryCache = new HashMap<>();
    private final Map<String, Long> operationItemsMemoryVersions = new HashMap<>();
    private RecyclerView.ItemDecoration operationDivider;
    private String pendingSelectedOperationId;
    private boolean operationBatchMode = false;
    private final Set<String> batchSelectedOperationIds = new HashSet<>();
    private String lastRenderedOperationTaskKey = "";

    // ========== 节点悬浮按钮 ==========
    private NodeFloatButtonManager nodeFloatBtnManager;
    private final Map<String, NodeFloatBtnEntry> nodeFloatBtnViews = new HashMap<>();

    private static class NodeFloatBtnEntry {
        View rootView;
        WindowManager.LayoutParams lp;
        NodeFloatBtnEntry(View rootView, WindowManager.LayoutParams lp) {
            this.rootView = rootView;
            this.lp = lp;
        }
    }

    private static final String METHOD_TM_CCOEFF = "TM_CCOEFF (4)";
    private static final String METHOD_TM_CCOEFF_NORMED = "TM_CCOEFF_NORMED (5)";
    private static final String METHOD_TM_CCORR = "TM_CCORR (2)";
    private static final String METHOD_TM_CCORR_NORMED = "TM_CCORR_NORMED (3)";
    private static final String METHOD_TM_SQDIFF = "TM_SQDIFF (0)";
    private static final String METHOD_TM_SQDIFF_NORMED = "TM_SQDIFF_NORMED (1)";

    // ---- Extracted helper objects (Phase 2 refactoring) ----
    private OperationCrudHelper crudHelper;
    private DialogHelpers dialogHelpers;
    private OperationDialogFactory dialogFactory;
    private FileIOManager fileIOManager;
    
    // ==================== Phase 4: Step Overlay ====================
    private View stepOverlayView;
    private WindowManager.LayoutParams stepOverlayLp;
    private View delayOverlayView;
    private WindowManager.LayoutParams delayOverlayLp;
    private ProgressBar delayOverlayProgressBar;
    private TextView delayOverlayValueText;

    // ==================== Phase 4: App-launch trigger polling ====================
    private static final boolean TRIGGER_FEATURE_ENABLED = false;
    private HandlerThread appLaunchPollThread;
    private Handler appLaunchPollHandler;
    private final Object appLaunchTriggerCacheLock = new Object();
    private List<com.auto.master.scheduler.AppNotificationTrigger> cachedAppLaunchTriggers =
            Collections.emptyList();
    private String lastForegroundPackage = "";
    private static final long APP_LAUNCH_POLL_INTERVAL_MS = 2000L;



    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化WindowManager
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 初始化IO管理器
        fileIOManager = new FileIOManager();

        // 初始化节点悬浮按钮管理器并恢复已保存的按钮
        nodeFloatBtnManager = new NodeFloatButtonManager(this);
        restoreNodeFloatButtons();
        
        startMyForeground();
        showBall();
        prepareProjectPanel();
        prewarmProjectStructure();
        renderProjectItems(filterProjectItems(projectListCache, currentSearchQuery));
        preloadProjectDataAsync();

        if (TRIGGER_FEATURE_ENABLED) {
            initAppLaunchPollThreadIfNeeded();
            refreshAppLaunchTriggerCache();
            if (!getCachedAppLaunchTriggersSnapshot().isEmpty()) {
                startAppLaunchPolling();
            }
        }
    }

    private void startMyForeground() {
        String channelId = "float_window_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Float Window",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Notification notification =
                new NotificationCompat.Builder(this, channelId)
                        .setContentTitle("悬浮窗运行中")
                        .setContentText("点击返回应用")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setOngoing(true)
                        .build();

        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_SELF.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_REFRESH_PROJECTS.equals(intent.getAction())) {
            if (isProjectPanelAttached() && currentLevel == NavigationLevel.PROJECT) {
                invalidateProjectListCache();
                loadProjects(true);
            }
            return START_STICKY;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授权悬浮窗权限", Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_OPEN_TASK_PANEL.equals(intent.getAction())) {
            handleOpenTaskPanelIntent(intent);
        }
        return START_STICKY;
    }

    private void handleOpenTaskPanelIntent(@NonNull Intent intent) {
        String taskPath = intent.getStringExtra(EXTRA_TASK_PATH);
        if (TextUtils.isEmpty(taskPath)) {
            return;
        }
        File taskDir = new File(taskPath);
        File projectDir = taskDir.getParentFile();
        String explicitProjectPath = intent.getStringExtra(EXTRA_PROJECT_PATH);
        if (!TextUtils.isEmpty(explicitProjectPath)) {
            projectDir = new File(explicitProjectPath);
        }
        if (projectDir == null || !projectDir.isDirectory() || !taskDir.isDirectory()) {
            Toast.makeText(this, "Task 路径无效，无法打开悬浮窗节点页", Toast.LENGTH_SHORT).show();
            return;
        }
        final File targetProjectDir = projectDir;
        final File targetTaskDir = taskDir;
        uiHandler.post(() -> {
            prepareProjectPanel();
            currentProjectDir = targetProjectDir;
            currentTaskDir = targetTaskDir;
            currentLevel = NavigationLevel.OPERATION;
            clearProjectPanelSearch();
            invalidateOperationListCache(targetTaskDir);
            projectPanelContentDirty = true;
            showProjectPanel();
            loadOperations(targetTaskDir, true);
            updateUIForLevel();
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        scheduleServiceRestart(TASK_REMOVED_RESTART_DELAY_MS);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        uiHandler.post(this::refreshNodeFloatButtonsForCurrentScreen);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // 清理Handler的pending消息，防止内存泄漏
        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }
        if (appLaunchPollHandler != null) {
            appLaunchPollHandler.removeCallbacksAndMessages(null);
        }
        if (appLaunchPollThread != null) {
            appLaunchPollThread.quitSafely();
            appLaunchPollThread = null;
            appLaunchPollHandler = null;
        }
        if (taskActionPopupWindow != null) {
            taskActionPopupWindow.dismiss();
            taskActionPopupWindow = null;
        }
        if (nodeFloatActionPopupWindow != null) {
            nodeFloatActionPopupWindow.dismiss();
            nodeFloatActionPopupWindow = null;
        }
        
        removeAllNodeFloatButtons();
        removeBall();
        detachProjectPanel();
        detachProjectPanelDock();
        projectPanelView = null;
        projectPanelLp = null;
        projectPanelDockView = null;
        projectPanelDockLp = null;
        projectPanelRecyclerView = null;
        projectPanelAdapter = null;
        taskPanelAdapter = null;
        operationPanelAdapter = null;
        currentOperationAdapter = null;
        hideStepOverlay();
        destroyDelayOverlay();
        stopAppLaunchPolling();
        
        // 关闭IO管理器
        if (fileIOManager != null) {
            fileIOManager.shutdown();
            fileIOManager = null;
        }
    }

    private void scheduleServiceRestart(long delayMs) {
        try {
            Intent restartIntent = new Intent(getApplicationContext(), FloatWindowService.class);
            restartIntent.setPackage(getPackageName());

            PendingIntent pendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pendingIntent = PendingIntent.getForegroundService(
                        getApplicationContext(),
                        1001,
                        restartIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
            } else {
                pendingIntent = PendingIntent.getService(
                        getApplicationContext(),
                        1001,
                        restartIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
            }

            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + Math.max(100L, delayMs),
                        pendingIntent
                );
            }
        } catch (Exception e) {
            Log.w(TAG, "scheduleServiceRestart failed", e);
        }
    }

    public static boolean canDrawOverlays(Service s) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(s);
    }

    private void showBall() {
        if (ballView != null) return;

        ballView = LayoutInflater.from(this).inflate(R.layout.floating_ball_layout, null);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        ballLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        ballLp.gravity = Gravity.TOP | Gravity.START;
        ballLp.x = 50;
        ballLp.y = 300;

        View ball = ballView.findViewById(R.id.floating_ball_container);
        ball.setOnClickListener(v -> {
            if (ScriptRunner.isCurrentScriptRunning()) {
                if (isRunningPanelShowing) {
                    hideRunningPanel();
                } else {
                    showRunningQuickMenu(v);
                }
            } else {
                // 非运行状态：展开/收起扇形快捷菜单
                if (fanMenuView != null) {
                    hideFanMenu();
                } else {
                    showFanMenu();
                }
            }
        });
        ball.setOnLongClickListener(v -> {
            if (ScriptRunner.isCurrentScriptRunning()) {
                togglePauseState();
            } else {
                hideFanMenu();
                removeProjectPanel();
                hideRunningPanel();
                Toast.makeText(this, "已收起面板", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        ball.setOnTouchListener(new DragTouchListener(ballLp, wm, ballView, this));

        // 初始化状态文字
        ballStatusText = ballView.findViewById(R.id.ball_status_text);

        wm.addView(ballView, ballLp);
    }

    // ==================== 浮球扇形快捷菜单 ====================

    private View fanMenuView;
    private WindowManager.LayoutParams fanMenuLp;

    private void showFanMenu() {
        if (fanMenuView != null || ballView == null) return;

        fanMenuView = LayoutInflater.from(this).inflate(R.layout.floating_action_menu, null);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        fanMenuLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        fanMenuLp.gravity = Gravity.TOP | Gravity.START;
        // 定位在浮球上方
        fanMenuLp.x = ballLp.x;
        fanMenuLp.y = Math.max(0, ballLp.y - dp(112));

        View btnPanel    = fanMenuView.findViewById(R.id.fan_btn_panel);
        View btnClose    = fanMenuView.findViewById(R.id.fan_btn_close);

        btnPanel.setOnClickListener(v -> { hideFanMenu(); showProjectPanel(); });
        btnClose.setOnClickListener(v -> hideFanMenu());

        wm.addView(fanMenuView, fanMenuLp);

        // 错开动画展开
        animateFanButton(btnPanel,    0);
        animateFanButton(btnClose,    90);
    }

    private void animateFanButton(View btn, long delayMs) {
        btn.setAlpha(0f);
        btn.setTranslationY(btn.getTranslationY()); // 保留 XML 里设置的初始偏移
        btn.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(200)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();
    }

    private void hideFanMenu() {
        if (fanMenuView == null) return;
        View btnPanel    = fanMenuView.findViewById(R.id.fan_btn_panel);
        View btnClose    = fanMenuView.findViewById(R.id.fan_btn_close);
        View toRemove = fanMenuView;
        // 收起：反向，关闭最先，面板最后
        btnClose.animate().alpha(0f).translationY(-20).setDuration(100).setStartDelay(0).start();
        btnPanel.animate().alpha(0f).translationY(-20).setDuration(100).setStartDelay(40)
                .withEndAction(() -> {
                    try { wm.removeView(toRemove); } catch (Exception ignored) {}
                }).start();
        fanMenuView = null;
        fanMenuLp = null;
    }

    // ==================== 节点悬浮按钮 ====================

    /** 预定义颜色板 */
    private static final int[] NODE_BTN_COLORS = {
            0xFF3C6DE4, // 蓝
            0xFF4CAF50, // 绿
            0xFFF44336, // 红
            0xFFFF9800, // 橙
            0xFF9C27B0, // 紫
            0xFF00BCD4, // 青
            0xFF795548, // 棕
            0xFF607D8B, // 蓝灰
    };

    /**
     * 显示节点悬浮按钮配置弹窗。
     * 支持：按钮文字、按钮颜色、文字颜色、大小、透明度、执行隐藏、位置选点、删除。
     */
    @SuppressLint("ClickableViewAccessibility")
    private void showNodeFloatBtnConfig(OperationItem item) {
        NodeFloatButtonConfig existing = nodeFloatBtnManager.getConfig(item.id);

        // 可变配置状态
        final int[]   selColor     = {existing != null ? existing.color                    : NODE_BTN_COLORS[0]};
        final int[]   selTextColor = {existing != null ? existing.textColor                : 0xFFFFFFFF};
        final int[]   selSize      = {existing != null ? existing.sizeDp                   : 48};
        final int[]   selAlpha     = {existing != null ? (int)(existing.alpha * 100 + 0.5f) : 100};

        String projectName = currentProjectDir != null ? currentProjectDir.getName() : "";
        String taskName    = currentTaskDir    != null ? currentTaskDir.getName()    : "";

        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_node_float_btn_config, null);

        int winType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams dlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                winType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        dlp.gravity = Gravity.CENTER;
        dlp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        wm.addView(dv, dlp);

        // ── 预览 ─────────────────────────────────────────────────────────
        FrameLayout preview      = dv.findViewById(R.id.cfg_preview);
        TextView    previewLabel = dv.findViewById(R.id.cfg_preview_label);

        // ── 按钮文字 ─────────────────────────────────────────────────────
        EditText etLabel = dv.findViewById(R.id.cfg_et_label);
        if (existing != null && existing.labelText != null) etLabel.setText(existing.labelText);

        // ── 预览更新闭包 ─────────────────────────────────────────────────
        Runnable refreshPreview = () -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(selColor[0]);
            preview.setBackground(bg);
            preview.setAlpha(selAlpha[0] / 100f);
            String t = etLabel.getText().toString().trim();
            previewLabel.setText(t.isEmpty() ? abbreviate(item.name, 6) : abbreviate(t, 6));
            previewLabel.setTextColor(selTextColor[0]);
        };
        refreshPreview.run();

        etLabel.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { refreshPreview.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // ── 按钮颜色色板 ─────────────────────────────────────────────────
        LinearLayout swatchRow = dv.findViewById(R.id.cfg_color_swatches);
        float density = getResources().getDisplayMetrics().density;
        int swSz  = (int)(28 * density);
        int swMgn = (int)(5 * density);
        for (int color : NODE_BTN_COLORS) {
            FrameLayout sw = new FrameLayout(this);
            GradientDrawable swBg = new GradientDrawable();
            swBg.setShape(GradientDrawable.OVAL);
            swBg.setColor(color);
            sw.setBackground(swBg);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(swSz, swSz);
            swLp.setMargins(swMgn, swMgn, swMgn, swMgn);
            sw.setLayoutParams(swLp);
            sw.setOnClickListener(v -> { selColor[0] = color; refreshPreview.run(); });
            swatchRow.addView(sw);
        }

        // ── 文字颜色色板 ─────────────────────────────────────────────────
        int[] textColors = {0xFFFFFFFF, 0xFF222222, 0xFFFFEB3B, 0xFFCCCCCC};
        LinearLayout tcRow = dv.findViewById(R.id.cfg_text_color_row);
        for (int tc : textColors) {
            FrameLayout sw = new FrameLayout(this);
            GradientDrawable swBg = new GradientDrawable();
            swBg.setShape(GradientDrawable.OVAL);
            swBg.setColor(tc);
            // 浅色加描边，方便辨认
            if ((tc & 0x00FFFFFF) >= 0x00BBBBBB) swBg.setStroke((int)(1.5f * density), 0x44000000);
            sw.setBackground(swBg);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(swSz, swSz);
            swLp.setMargins(swMgn, swMgn, swMgn, swMgn);
            sw.setLayoutParams(swLp);
            sw.setOnClickListener(v -> { selTextColor[0] = tc; refreshPreview.run(); });
            tcRow.addView(sw);
        }

        // ── 大小步进 ─────────────────────────────────────────────────────
        TextView tvSizeVal = dv.findViewById(R.id.cfg_size_val);
        Runnable refreshSize = () -> tvSizeVal.setText(selSize[0] + "dp");
        refreshSize.run();
        dv.findViewById(R.id.cfg_size_minus).setOnClickListener(v -> {
            if (selSize[0] > 32) { selSize[0] -= 4; refreshSize.run(); }
        });
        dv.findViewById(R.id.cfg_size_plus).setOnClickListener(v -> {
            if (selSize[0] < 88) { selSize[0] += 4; refreshSize.run(); }
        });

        // ── 透明度步进 ───────────────────────────────────────────────────
        TextView tvAlphaVal = dv.findViewById(R.id.cfg_alpha_val);
        Runnable refreshAlpha = () -> tvAlphaVal.setText(selAlpha[0] + "%");
        refreshAlpha.run();
        dv.findViewById(R.id.cfg_alpha_minus).setOnClickListener(v -> {
            if (selAlpha[0] > 20) { selAlpha[0] -= 10; refreshAlpha.run(); refreshPreview.run(); }
        });
        dv.findViewById(R.id.cfg_alpha_plus).setOnClickListener(v -> {
            if (selAlpha[0] < 100) { selAlpha[0] += 10; refreshAlpha.run(); refreshPreview.run(); }
        });

        // ── 执行时隐藏 ───────────────────────────────────────────────────
        CheckBox chkHide = dv.findViewById(R.id.cfg_chk_hide);
        if (existing != null) chkHide.setChecked(existing.hideWhileRunning);

        // ── 位置 ─────────────────────────────────────────────────────────
        EditText etX = dv.findViewById(R.id.cfg_et_x);
        EditText etY = dv.findViewById(R.id.cfg_et_y);
        int defaultX = ballLp != null ? ballLp.x + dp(60) : 160;
        int defaultY = ballLp != null ? ballLp.y           : 400;
        etX.setText(String.valueOf(existing != null ? existing.posX : defaultX));
        etY.setText(String.valueOf(existing != null ? existing.posY : defaultY));
        dv.findViewById(R.id.cfg_btn_pick).setOnClickListener(v ->
                showPositionPickOverlay(dv, etX, etY, selSize));

        // ── 删除按钮（仅已有配置时显示） ─────────────────────────────────
        View btnDelete = dv.findViewById(R.id.cfg_btn_delete);
        if (existing != null) {
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> {
                safeRemoveView(dv);
                nodeFloatBtnManager.removeConfig(item.id);
                removeNodeFloatBtn(item.id);
                refreshFloatBtnBadges();
                Toast.makeText(this, "已删除悬浮按钮", Toast.LENGTH_SHORT).show();
            });
        }

        // ── 取消 ─────────────────────────────────────────────────────────
        dv.findViewById(R.id.cfg_btn_cancel).setOnClickListener(v -> safeRemoveView(dv));

        // ── 确定 ─────────────────────────────────────────────────────────
        dv.findViewById(R.id.cfg_btn_confirm).setOnClickListener(v -> {
            safeRemoveView(dv);
            int posX = parseIntDefault(etX.getText().toString(), defaultX);
            int posY = parseIntDefault(etY.getText().toString(), defaultY);
            String labelTxt = etLabel.getText().toString().trim();

            NodeFloatButtonConfig cfg = new NodeFloatButtonConfig(
                    item.id, item.name, projectName, taskName, selColor[0], posX, posY);
            cfg.labelText        = labelTxt.isEmpty() ? null : labelTxt;
            cfg.textColor        = selTextColor[0];
            cfg.sizeDp           = selSize[0];
            cfg.alpha            = selAlpha[0] / 100f;
            cfg.hideWhileRunning = chkHide.isChecked();

            nodeFloatBtnManager.saveConfig(cfg);
            removeNodeFloatBtn(item.id);
            addNodeFloatBtn(cfg);
            refreshFloatBtnBadges();
            Toast.makeText(this,
                    existing != null ? "悬浮按钮已更新" : "悬浮按钮已创建",
                    Toast.LENGTH_SHORT).show();
        });

        // 点击弹窗外关闭
        dv.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) { safeRemoveView(dv); return true; }
            return false;
        });
    }

    /**
     * 全屏点选位置覆盖层：用户点击屏幕任意位置即设为悬浮按钮坐标。
     * 点击取消区域则仅关闭覆盖层，不改坐标。
     */
    @SuppressLint("ClickableViewAccessibility")
    private void showPositionPickOverlay(View dialogView, EditText etX, EditText etY, int[] sizeRef) {
        dialogView.setVisibility(View.INVISIBLE);

        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x66000000);

        // 提示文字
        TextView hint = new TextView(this);
        hint.setText("点击屏幕设置悬浮按钮的位置");
        hint.setTextColor(0xFFFFFFFF);
        hint.setTextSize(15f);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(0xCC1A2332);
        hint.setPadding(dp(24), dp(12), dp(24), dp(12));
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hintLp.topMargin = dp(80);
        overlay.addView(hint, hintLp);

        // 取消按钮
        TextView cancelTv = new TextView(this);
        cancelTv.setText("取  消");
        cancelTv.setTextColor(0xFFFFFFFF);
        cancelTv.setTextSize(14f);
        cancelTv.setGravity(Gravity.CENTER);
        cancelTv.setBackgroundColor(0xCC1A2332);
        cancelTv.setPadding(dp(32), dp(12), dp(32), dp(12));
        FrameLayout.LayoutParams cancelLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cancelLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cancelLp.bottomMargin = dp(80);
        overlay.addView(cancelTv, cancelLp);

        int winType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams olp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                winType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        olp.gravity = Gravity.TOP | Gravity.START;
        wm.addView(overlay, olp);

        overlay.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float tx = e.getRawX(), ty = e.getRawY();
                // 判断是否点在"取消"区域
                boolean hitCancel = false;
                if (cancelTv.getWidth() > 0) {
                    int[] loc = new int[2];
                    cancelTv.getLocationOnScreen(loc);
                    hitCancel = tx >= loc[0] && tx <= loc[0] + cancelTv.getWidth()
                            && ty >= loc[1] && ty <= loc[1] + cancelTv.getHeight();
                }
                safeRemoveView(overlay);
                dialogView.setVisibility(View.VISIBLE);
                if (!hitCancel) {
                    float d = getResources().getDisplayMetrics().density;
                    int half = (int)(sizeRef[0] * d / 2);
                    etX.setText(String.valueOf(Math.max(0, (int) tx - half)));
                    etY.setText(String.valueOf(Math.max(0, (int) ty - half)));
                }
            }
            return true;
        });
    }

    /** 安全解析整数，失败返回默认值。 */
    private static int parseIntDefault(String s, int def) {
        if (s == null || s.trim().isEmpty()) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private boolean isNodeFloatBtnVisibleForCurrentScreen(NodeFloatButtonConfig cfg) {
        if (cfg == null) {
            return false;
        }
        cfg.ensureDefaults();
        int[] screen = getScreenSizePx();
        int sizePx = dp(cfg.sizeDp);
        return cfg.posX >= 0
                && cfg.posY >= 0
                && cfg.posX + sizePx <= screen[0]
                && cfg.posY + sizePx <= screen[1];
    }

    private void refreshNodeFloatButtonsForCurrentScreen() {
        Map<String, NodeFloatButtonConfig> configs = nodeFloatBtnManager == null
                ? Collections.emptyMap()
                : new HashMap<>(nodeFloatBtnManager.getAllConfigs());
        removeAllNodeFloatButtons();
        for (NodeFloatButtonConfig cfg : configs.values()) {
            if (cfg != null) {
                addNodeFloatBtn(cfg);
            }
        }
        refreshFloatBtnBadges();
    }

    /**
     * 把一个节点悬浮按钮添加到 WindowManager，应用所有配置字段。
     */
    @SuppressLint("ClickableViewAccessibility")
    private void addNodeFloatBtn(NodeFloatButtonConfig cfg) {
        if (nodeFloatBtnViews.containsKey(cfg.operationId)) return;
        cfg.ensureDefaults(); // 防止旧存档缺字段
        if (!isNodeFloatBtnVisibleForCurrentScreen(cfg)) {
            return;
        }

        View root = LayoutInflater.from(this).inflate(R.layout.window_node_float_btn, null);
        FrameLayout container = root.findViewById(R.id.node_btn_container);
        TextView label = root.findViewById(R.id.node_btn_label);

        // 文字
        String displayLabel = (cfg.labelText != null && !cfg.labelText.isEmpty())
                ? cfg.labelText : abbreviate(cfg.operationName, 8);
        label.setText(displayLabel);
        label.setTextColor(cfg.textColor);

        // 大小：覆盖 XML 中的 48dp 固定尺寸
        int sizePx = dp(cfg.sizeDp);
        ViewGroup.LayoutParams cLp = container.getLayoutParams();
        cLp.width  = sizePx;
        cLp.height = sizePx;
        container.setLayoutParams(cLp);

        // 圆形背景色
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(cfg.color);
        container.setBackground(bg);

        // 透明度作用于整个悬浮件根视图
        root.setAlpha(cfg.alpha);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = cfg.posX;
        lp.y = cfg.posY;

        // 拖拽（含长按检测）
        // 注意：setOnTouchListener 返回 true 会拦截所有事件，系统 setOnLongClickListener 失效，
        // 因此长按逻辑统一由 DragTouchListener 内部计时完成。
        container.setOnTouchListener(new DragTouchListener(lp, wm, root, this, true) {
            @Override
            protected void onDragEnd(int finalX, int finalY) {
                NodeFloatButtonConfig updated = nodeFloatBtnManager.getConfig(cfg.operationId);
                if (updated != null) {
                    updated.posX = finalX;
                    updated.posY = finalY;
                    nodeFloatBtnManager.saveConfig(updated);
                }
            }

            @Override
            protected void onLongPress() {
                showNodeFloatBtnMenu(container, cfg);
            }
        });

        // 单击 → 运行节点
        container.setOnClickListener(v -> runFromNodeFloatBtn(cfg));

        wm.addView(root, lp);
        nodeFloatBtnViews.put(cfg.operationId, new NodeFloatBtnEntry(root, lp));
    }

    /** 移除指定 operationId 的节点悬浮按钮（不删配置）。 */
    private void removeNodeFloatBtn(String operationId) {
        NodeFloatBtnEntry entry = nodeFloatBtnViews.remove(operationId);
        if (entry != null) {
            safeRemoveView(entry.rootView);
        }
    }

    /** 移除所有节点悬浮按钮（服务销毁时调用）。 */
    private void removeAllNodeFloatButtons() {
        for (NodeFloatBtnEntry entry : nodeFloatBtnViews.values()) {
            safeRemoveView(entry.rootView);
        }
        nodeFloatBtnViews.clear();
    }

    /** 服务启动时恢复所有已保存的节点悬浮按钮。 */
    private void restoreNodeFloatButtons() {
        if (nodeFloatBtnManager == null) return;
        for (NodeFloatButtonConfig cfg : nodeFloatBtnManager.getAllConfigs().values()) {
            addNodeFloatBtn(cfg);
        }
    }

    private void ensureNodeFloatActionPopup() {
        if (nodeFloatActionPopupWindow != null) {
            return;
        }
        View popupView = LayoutInflater.from(this).inflate(R.layout.dialog_node_action_sheet, null);
        nodeFloatActionPopupTitleView = popupView.findViewById(R.id.tv_action_title);
        nodeFloatActionPopupListView = popupView.findViewById(R.id.rv_action_list);
        if (nodeFloatActionPopupListView != null) {
            nodeFloatActionPopupListView.setLayoutManager(new LinearLayoutManager(this));
            nodeFloatActionSheetAdapter = new OperationPanelAdapter.ActionSheetAdapter(
                    nodeFloatActionSheetItems,
                    action -> {
                        if (action == null || !action.enabled) {
                            return;
                        }
                        if (nodeFloatActionPopupWindow != null) {
                            nodeFloatActionPopupWindow.dismiss();
                        }
                        if (nodeFloatActionSheetHandler != null) {
                            nodeFloatActionSheetHandler.accept(action);
                        }
                    });
            nodeFloatActionPopupListView.setAdapter(nodeFloatActionSheetAdapter);
        }
        nodeFloatActionPopupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        nodeFloatActionPopupWindow.setOutsideTouchable(true);
        nodeFloatActionPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        nodeFloatActionPopupWindow.setElevation(10f);
    }

    /**
     * 长按悬浮按钮弹出可扩展菜单。
     * 菜单项可随需求继续追加 ActionItem(id, title, desc, enabled)。
     */
    private void showNodeFloatBtnMenu(View anchor, NodeFloatButtonConfig cfg) {
        ensureNodeFloatActionPopup();
        if (nodeFloatActionPopupWindow == null
                || nodeFloatActionPopupTitleView == null
                || nodeFloatActionSheetAdapter == null) {
            return;
        }
        nodeFloatActionPopupTitleView.setText(cfg.operationName);
        nodeFloatActionSheetItems.clear();
        nodeFloatActionSheetItems.add(new OperationPanelAdapter.ActionItem(1, "运行节点", "立即运行这个节点", true));
        nodeFloatActionSheetItems.add(new OperationPanelAdapter.ActionItem(2, "配置修改", "修改运行前注入的变量配置", true));
        nodeFloatActionSheetItems.add(new OperationPanelAdapter.ActionItem(3, "按钮设置", "修改文字、颜色、大小和位置", true));
        nodeFloatActionSheetItems.add(new OperationPanelAdapter.ActionItem(4, "定位节点", "打开面板并高亮这个节点", true));
        nodeFloatActionSheetItems.add(new OperationPanelAdapter.ActionItem(5, "移除悬浮按钮", "删除这个悬浮按钮（不影响节点）", true));
        nodeFloatActionSheetHandler = action -> {
            switch (action.id) {
                case 1:
                    runFromNodeFloatBtn(cfg);
                    break;
                case 2:
                    showNodeRuntimeConfigDialog(cfg);
                    break;
                case 3:
                    OperationItem fakeItem = new OperationItem(cfg.operationName, cfg.operationId, "", 0);
                    showNodeFloatBtnConfig(fakeItem);
                    break;
                case 4:
                    navigateToNodeInPanel(cfg);
                    break;
                case 5:
                    nodeFloatBtnManager.removeConfig(cfg.operationId);
                    removeNodeFloatBtn(cfg.operationId);
                    refreshFloatBtnBadges();
                    Toast.makeText(this, "已移除悬浮按钮", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        };
        nodeFloatActionSheetAdapter.notifyDataSetChanged();
        if (nodeFloatActionPopupWindow.isShowing()) {
            nodeFloatActionPopupWindow.dismiss();
        }
        nodeFloatActionPopupWindow.showAsDropDown(anchor, -dp(180), dp(4), Gravity.END);
    }

    /**
     * 打开/显示项目面板，导航到 cfg 对应的节点列表，并高亮选中该节点。
     */
    private void navigateToNodeInPanel(NodeFloatButtonConfig cfg) {
        File projectsRoot = getProjectsRootDir();
        File projectDir   = new File(projectsRoot, cfg.projectName);
        File taskDir      = new File(projectDir,   cfg.taskName);
        if (!projectDir.isDirectory() || !taskDir.isDirectory()) {
            Toast.makeText(this, "找不到对应项目/任务，可能已被删除", Toast.LENGTH_SHORT).show();
            return;
        }
        uiHandler.post(() -> {
            prepareProjectPanel();
            currentProjectDir    = projectDir;
            currentTaskDir       = taskDir;
            currentLevel         = NavigationLevel.OPERATION;
            clearProjectPanelSearch();
            invalidateOperationListCache(taskDir);
            projectPanelContentDirty = true;
            showProjectPanel();
            loadOperations(taskDir, true);
            updateUIForLevel();
            // 加载完成后选中目标节点并滚动到可见区域
            uiHandler.postDelayed(() -> {
                if (currentOperationAdapter == null) return;
                currentOperationAdapter.selectById(cfg.operationId);
                int pos = currentOperationAdapter.findPositionById(cfg.operationId);
                if (pos >= 0) {
                    RecyclerView rv = getProjectPanelRecyclerView();
                    if (rv != null) rv.scrollToPosition(pos);
                }
            }, 120);
        });
    }

    private void showNodeRuntimeConfigDialog(NodeFloatButtonConfig cfg) {
        if (cfg == null) {
            return;
        }
        cfg.ensureDefaults();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_node_runtime_config, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(320, true);
        wm.addView(dialogView, dialogLp);

        TextView titleView = dialogView.findViewById(R.id.tv_runtime_cfg_title);
        EditText variablesInput = dialogView.findViewById(R.id.et_runtime_variables);
        if (titleView != null) {
            titleView.setText("运行配置: " + abbreviate(cfg.operationName, 14));
        }
        if (variablesInput != null) {
            variablesInput.setText(cfg.runtimeVariablesText == null ? "" : cfg.runtimeVariablesText);
            variablesInput.setSelection(variablesInput.getText() == null ? 0 : variablesInput.getText().length());
            // 提示用户两种脚本访问方式
            variablesInput.setHint("每行 key=value\n# 注释行\n\n脚本中可通过：\n  vars.myKey        （独立变量）\n  vars.configMap.myKey （字典访问）");
        }

        dialogView.findViewById(R.id.btn_runtime_cfg_clear).setOnClickListener(v -> {
            if (variablesInput != null) {
                variablesInput.setText("");
            }
        });
        dialogView.findViewById(R.id.btn_runtime_cfg_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_runtime_cfg_save).setOnClickListener(v -> {
            String raw = variablesInput == null || variablesInput.getText() == null
                    ? ""
                    : variablesInput.getText().toString();
            List<String> invalidLines = collectInvalidRuntimeVariableLines(raw);
            if (!invalidLines.isEmpty()) {
                Toast.makeText(this, "格式错误，请检查第 " + invalidLines.get(0) + " 行", Toast.LENGTH_SHORT).show();
                return;
            }
            NodeFloatButtonConfig updated = nodeFloatBtnManager.getConfig(cfg.operationId);
            if (updated == null) {
                updated = cfg;
            }
            updated.ensureDefaults();
            updated.runtimeVariablesText = raw.trim();
            nodeFloatBtnManager.saveConfig(updated);
            safeRemoveView(dialogView);
            Toast.makeText(this, "运行配置已保存", Toast.LENGTH_SHORT).show();
        });
    }

    private List<String> collectInvalidRuntimeVariableLines(@Nullable String raw) {
        List<String> invalid = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) {
            return invalid;
        }
        String[] lines = raw.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                invalid.add(String.valueOf(i + 1));
                continue;
            }
            String key = line.substring(0, eq).trim();
            if (key.isEmpty()) {
                invalid.add(String.valueOf(i + 1));
            }
        }
        return invalid;
    }

    private void applyNodeRuntimeVariables(OperationContext ctx, @Nullable NodeFloatButtonConfig cfg) {
        if (ctx == null || cfg == null) {
            return;
        }
        cfg.ensureDefaults();
        if (ctx.variables == null) {
            ctx.variables = new HashMap<>();
        }
        if (TextUtils.isEmpty(cfg.runtimeVariablesText)) {
            // 没有配置时也保证 configMap 存在（空 Map），脚本无需判空
            ctx.variables.put("configMap", new HashMap<String, Object>());
            return;
        }
        // 解析 key=value 行，同时注入为独立变量和 configMap 字典两种形式
        Map<String, Object> configMap = new HashMap<>();
        String[] lines = cfg.runtimeVariablesText.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                Object coerced = coerceNodeRuntimeValue(value);
                // 1. 注入为独立变量（兼容旧用法：直接 vars.myKey）
                ctx.variables.put(key, coerced);
                // 2. 同时收入 configMap（新用法：vars.configMap.myKey）
                configMap.put(key, coerced);
            }
        }
        // configMap 作为一个整体字典注入，脚本中通过 vars.configMap 访问
        ctx.variables.put("configMap", configMap);
    }

    private Object coerceNodeRuntimeValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        try {
            if (trimmed.contains(".")) {
                return Double.parseDouble(trimmed);
            }
            long longValue = Long.parseLong(trimmed);
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return (int) longValue;
            }
            return longValue;
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    /**
     * 把当前所有悬浮按钮的 id→颜色 映射同步给节点列表适配器，刷新标识点。
     * 在增删/修改悬浮按钮配置后调用。
     */
    private void refreshFloatBtnBadges() {
        if (currentOperationAdapter == null) return;
        currentOperationAdapter.setFloatBtnColors(buildFloatBtnColorMap());
    }

    /** 从 NodeFloatButtonManager 构建 operationId → 按钮颜色 的映射。 */
    private Map<String, Integer> buildFloatBtnColorMap() {
        if (nodeFloatBtnManager == null) return Collections.emptyMap();
        Map<String, NodeFloatButtonConfig> configs = nodeFloatBtnManager.getAllConfigs();
        Map<String, Integer> result = new HashMap<>(configs.size());
        for (Map.Entry<String, NodeFloatButtonConfig> e : configs.entrySet()) {
            if (e.getValue() != null) result.put(e.getKey(), e.getValue().color);
        }
        return result;
    }

    /**
     * 点击节点悬浮按钮时运行对应节点。
     * 直接后台运行，不弹运行模式选择。
     * 若 cfg.hideWhileRunning == true，则运行期间隐藏按钮，运行结束后恢复。
     */
    private void runFromNodeFloatBtn(NodeFloatButtonConfig cfg) {
        if (ScriptRunner.isCurrentScriptRunning()) {
            Toast.makeText(this, "脚本运行中，请先停止", Toast.LENGTH_SHORT).show();
            return;
        }
        RunLaunchData data = buildRunLaunchDataForNode(cfg.projectName, cfg.taskName, cfg.operationId);
        if (data == null) {
            Toast.makeText(this, "无法找到节点，请确认项目/任务未被删除", Toast.LENGTH_SHORT).show();
            return;
        }
        if (cfg.hideWhileRunning) {
            NodeFloatBtnEntry entry = nodeFloatBtnViews.get(cfg.operationId);
            if (entry != null) {
                entry.rootView.setVisibility(View.INVISIBLE);
                scheduleRestoreNodeBtnVisibility(cfg.operationId, entry.rootView);
            }
        }
        startOperationWithMode(
                data.startOperation, data.ctx,
                data.projectName, data.selectedTaskName,
                data.selectedTaskOperations, false);
    }

    /** 每 500 ms 轮询一次，脚本结束后恢复悬浮按钮可见性。 */
    private void scheduleRestoreNodeBtnVisibility(String operationId, View btnView) {
        uiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (ScriptRunner.isCurrentScriptRunning()) {
                    uiHandler.postDelayed(this, 500);
                } else {
                    if (nodeFloatBtnViews.containsKey(operationId)) {
                        btnView.setVisibility(View.VISIBLE);
                    }
                }
            }
        }, 500);
    }

    /**
     * 根据 projectName + taskName + operationId 构造运行数据。
     * 与 prepareRunLaunchData() 逻辑类似，但不依赖 UI 选中状态。
     */
    @Nullable
    private RunLaunchData buildRunLaunchDataForNode(String projectName, String taskName, String operationId) {
        Project project = findCachedProjectByName(projectName);
        if (project == null) {
            File projectDir = new File(getProjectsRootDir(), projectName);
            if (projectDir.exists()) {
                project = loadProjectFromDir(projectDir);
                if (project != null) upsertCachedProject(project);
            }
        }
        if (project == null || project.getTaskMap() == null) return null;

        Task task = project.getTaskMap().get(taskName);
        if (task == null || task.getOperationMap() == null) return null;

        MetaOperation startOp = task.getOperationMap().get(operationId);
        if (startOp == null) return null;

        List<OperationItem> ops = buildOperationItemsFromTask(task);
        OperationContext ctx = new OperationContext();
        ctx.anchorProject = project;
        NodeFloatButtonConfig cfg = nodeFloatBtnManager == null ? null : nodeFloatBtnManager.getConfig(operationId);
        applyNodeRuntimeVariables(ctx, cfg);

        RunLaunchData data = new RunLaunchData();
        data.startOperation = startOp;
        data.selectedTask = task;
        data.projectName = projectName;
        data.selectedTaskName = taskName;
        data.selectedTaskOperations = ops;
        data.ctx = ctx;
        return data;
    }

    /** 截断字符串，超出 maxChars 时加 "…"。 */
    private static String abbreviate(String s, int maxChars) {
        if (s == null) return "";
        return s.length() <= maxChars ? s : s.substring(0, maxChars - 1) + "…";
    }

    private void showRunningQuickMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, isPaused ? "继续脚本" : "暂停脚本");
        popup.getMenu().add(0, 2, 1, "打开节点面板");
        popup.getMenu().add(0, 3, 2, "停止脚本");
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                togglePauseState();
                return true;
            }
            if (id == 2) {
                showRuntimeAwareProjectPanel();
                return true;
            }
            if (id == 3) {
                stopScriptFromUi();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void togglePauseState() {
        if (isPaused) {
            ScriptRunner.resumeCurrentScript();
            isPaused = false;
            updateRunningPanelStatus("运行中", 0xFF4CAF50);
            if (ballStatusText != null && !TextUtils.isEmpty(currentRunningOperationName)) {
                ballStatusText.setText("运行中: " + currentRunningOperationName);
                ballStatusText.setVisibility(View.VISIBLE);
            }
            Toast.makeText(this, "脚本已继续", Toast.LENGTH_SHORT).show();
        } else {
            ScriptRunner.pauseCurrentScript();
            isPaused = true;
            updateRunningPanelStatus("已暂停", 0xFFFF9800);
            if (ballStatusText != null) {
                ballStatusText.setText("已暂停");
                ballStatusText.setVisibility(View.VISIBLE);
            }
            Toast.makeText(this, "脚本已暂停", Toast.LENGTH_SHORT).show();
        }
        syncPauseButtonIfPanelVisible();
        syncProjectPanelRuntimeUi();
    }

    private void syncPauseButtonIfPanelVisible() {
        if (runningPanelView == null) {
            return;
        }
        TextView btnPause = runningPanelView.findViewById(R.id.btn_pause);
        if (btnPause == null) {
            return;
        }
        if (isPaused) {
            btnPause.setText("▶ 继续");
            btnPause.setBackgroundResource(R.drawable.btn_run_selector);
        } else {
            btnPause.setText("⏸ 暂停");
            btnPause.setBackgroundResource(R.drawable.btn_pause_selector);
        }
    }

    private void stopScriptFromUi() {
        ScriptRunner.stopCurrentScript();
        recordFailureReason("stopped_by_user");
        updateRuntimeMetricsPanel();
        appendRunLog("=== Run Stopped By User ===");
        persistCurrentRunLog();
        CrashLogger.finishRunSession(this, "stopped_by_user");
        hideRunningPanel();
        hideProjectPanelDock();
        setBallVisible(true);
        stopDelayProgress();
        hideStepOverlay();
        currentRunningOperationId = "";
        currentRunningOperationName = "";
        isPaused = false;
        updateRunningPanelStatus("已停止", 0xFFF44336);
        if (currentOperationAdapter != null) {
            currentOperationAdapter.clearRunningPosition();
        }
        if (ballStatusText != null) {
            ballStatusText.setVisibility(View.GONE);
        }
        syncProjectPanelRuntimeUi();
        Toast.makeText(this, "脚本已停止", Toast.LENGTH_SHORT).show();
    }

    private void removeBall() {
        hideFanMenu();
        hideProjectPanelDock();
        if (ballView != null) {
            wm.removeView(ballView);
            ballView = null;
        }
    }

    private void setBallVisible(boolean visible) {
        if (ballView == null) {
            return;
        }
        ballView.animate().cancel();
        if (!visible) {
            hideFanMenu();
            if (ballStatusText != null) {
                ballStatusText.setVisibility(View.GONE);
            }
            ballView.setAlpha(0f);
            ballView.setVisibility(View.INVISIBLE);
            return;
        }
        ballView.setVisibility(View.VISIBLE);
        ballView.setAlpha(1f);
        if (ballLp != null && ballView.getParent() != null) {
            try {
                wm.updateViewLayout(ballView, ballLp);
            } catch (Exception e) {
                Log.w(TAG, "restore ball layout failed", e);
            }
        }
    }

    private void toggleProjectPanel() {
        if (!isProjectPanelVisible()) showProjectPanel();
        else removeProjectPanel();
    }

    private void showProjectPanel() {
        prepareProjectPanel();
        adaptProjectPanelSizeToCurrentScreen(projectPanelLp);
        hideProjectPanelDock();
        projectPanelView.animate().cancel();
        projectPanelView.setAlpha(1f);
        projectPanelView.setTranslationX(0f);
        projectPanelView.setTranslationY(0f);
        attachProjectPanelIfNeeded();
        clampPanelToScreen(projectPanelLp);
        if (isProjectPanelAttached()) {
            wm.updateViewLayout(projectPanelView, projectPanelLp);
        }
        projectPanelView.setVisibility(View.VISIBLE);
        if (projectPanelContentDirty || getProjectPanelRecyclerView() == null || getProjectPanelRecyclerView().getAdapter() == null) {
            refreshCurrentLevelList();
        } else {
            updateUIForLevel();
        }
        syncProjectPanelRuntimeUi();
    }

    private void showRuntimeAwareProjectPanel() {
        hideRunningPanel();
        if (currentTaskDir != null) {
            currentLevel = NavigationLevel.OPERATION;
            clearProjectPanelSearch();
        }
        showProjectPanel();
        if (currentLevel == NavigationLevel.OPERATION && currentTaskDir != null) {
            loadOperations(currentTaskDir);
        }
        updateUIForLevel();
        syncProjectPanelRuntimeUi();
        if (!TextUtils.isEmpty(currentRunningOperationId)) {
            focusCurrentRunningOperation();
        }
    }

    private void removeProjectPanel() {
        if (projectPanelView != null && isProjectPanelAttached()) {
            projectPanelView.animate().cancel();
            rememberSharedPanelPosition(projectPanelLp);
            uiHandler.removeCallbacks(searchRefreshRunnable);
            projectPanelView.setVisibility(View.GONE);
            detachProjectPanel();
        }
        if (isScriptActiveForUi()) {
            showProjectPanelDock();
        } else {
            hideProjectPanelDock();
        }
    }

    private boolean isScriptActiveForUi() {
        return ScriptRunner.isCurrentScriptRunning() || isPaused;
    }

    private void prepareProjectPanel() {
        if (projectPanelView != null && projectPanelLp != null) {
            return;
        }

        projectPanelView = LayoutInflater.from(this).inflate(R.layout.window_project_panel, null);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        projectPanelLp = new WindowManager.LayoutParams(
                dp(PROJECT_PANEL_DEFAULT_W_DP), dp(PROJECT_PANEL_DEFAULT_H_DP),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );
        projectPanelLp.gravity = Gravity.TOP | Gravity.START;
        projectPanelLp.x = getSharedPanelX();
        projectPanelLp.y = getSharedPanelY();
        adaptProjectPanelSizeToCurrentScreen(projectPanelLp);

        initDialogFactory();
        setupProjectPanel();
    }

    private boolean isProjectPanelDockAttached() {
        return projectPanelDockView != null && projectPanelDockView.getParent() != null;
    }

    private void prepareProjectPanelDock() {
        if (projectPanelDockView != null && projectPanelDockLp != null) {
            return;
        }

        projectPanelDockView = LayoutInflater.from(this).inflate(R.layout.overlay_project_panel_dock, null);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        projectPanelDockLp = new WindowManager.LayoutParams(
                dp(PROJECT_PANEL_DOCK_W_DP), dp(PROJECT_PANEL_DOCK_H_DP),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        projectPanelDockLp.gravity = Gravity.TOP | Gravity.START;

        View dockTouch = projectPanelDockView.findViewById(R.id.project_panel_dock_touch);
        dockTouch.setOnTouchListener(new View.OnTouchListener() {
            private final int slopPx = ViewConfiguration.get(FloatWindowService.this).getScaledTouchSlop();
            private float downRawX;
            private float downRawY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!moved && (Math.abs(dx) > slopPx || Math.abs(dy) > slopPx)) {
                            moved = true;
                        }
                        if (moved && shouldRevealProjectPanelFromDock(dx, dy)) {
                            revealProjectPanelFromDock();
                            return true;
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private boolean shouldRevealProjectPanelFromDock(float dx, float dy) {
        if (Math.abs(dx) < Math.abs(dy)) {
            return false;
        }
        int triggerPx = dp(PROJECT_PANEL_DOCK_TRIGGER_DP);
        return projectPanelDockOnLeft ? dx >= triggerPx : dx <= -triggerPx;
    }

    private int getRememberedProjectPanelWidthPx() {
        if (projectPanelLp != null && projectPanelLp.width > 0) {
            return projectPanelLp.width;
        }
        return dp(PROJECT_PANEL_DEFAULT_W_DP);
    }

    private int getRememberedProjectPanelHeightPx() {
        if (projectPanelLp != null && projectPanelLp.height > 0) {
            return projectPanelLp.height;
        }
        return dp(PROJECT_PANEL_DEFAULT_H_DP);
    }

    private void updateProjectPanelDockLayout() {
        if (projectPanelDockView == null || projectPanelDockLp == null) {
            return;
        }
        int[] screen = getScreenSizePx();
        int dockWidth = dp(PROJECT_PANEL_DOCK_W_DP);
        int dockHeight = dp(PROJECT_PANEL_DOCK_H_DP);
        int margin = dp(PROJECT_PANEL_DOCK_MARGIN_DP);
        int panelWidth = getRememberedProjectPanelWidthPx();
        int panelHeight = getRememberedProjectPanelHeightPx();
        int panelCenterX = getSharedPanelX() + (panelWidth / 2);

        projectPanelDockOnLeft = panelCenterX <= (screen[0] / 2);
        projectPanelDockLp.width = dockWidth;
        projectPanelDockLp.height = dockHeight;
        projectPanelDockLp.x = projectPanelDockOnLeft ? 0 : Math.max(0, screen[0] - dockWidth);

        int desiredY = getSharedPanelY() + Math.max(0, (panelHeight - dockHeight) / 2);
        int maxY = Math.max(margin, screen[1] - dockHeight - margin);
        projectPanelDockLp.y = Math.max(margin, Math.min(desiredY, maxY));

        View line = projectPanelDockView.findViewById(R.id.project_panel_dock_line);
        if (line != null) {
            FrameLayout.LayoutParams lineLp = (FrameLayout.LayoutParams) line.getLayoutParams();
            lineLp.gravity = (projectPanelDockOnLeft ? Gravity.START : Gravity.END) | Gravity.CENTER_VERTICAL;
            line.setLayoutParams(lineLp);
        }
    }

    private void attachProjectPanelDockIfNeeded() {
        if (projectPanelDockView == null || isProjectPanelDockAttached()) {
            return;
        }
        wm.addView(projectPanelDockView, projectPanelDockLp);
    }

    private void detachProjectPanelDock() {
        if (projectPanelDockView == null || !isProjectPanelDockAttached()) {
            return;
        }
        try {
            wm.removeView(projectPanelDockView);
        } catch (Exception e) {
            Log.w(TAG, "detach project dock failed", e);
        }
    }

    private void showProjectPanelDock() {
        if (!isScriptActiveForUi()) {
            hideProjectPanelDock();
            return;
        }
        adaptProjectPanelSizeToCurrentScreen(projectPanelLp);
        prepareProjectPanelDock();
        updateProjectPanelDockLayout();
        attachProjectPanelDockIfNeeded();
        if (projectPanelDockView != null) {
            projectPanelDockView.animate().cancel();
            projectPanelDockView.setAlpha(1f);
            if (isProjectPanelDockAttached()) {
                wm.updateViewLayout(projectPanelDockView, projectPanelDockLp);
            }
        }
    }

    private void hideProjectPanelDock() {
        if (projectPanelDockView != null) {
            projectPanelDockView.animate().cancel();
        }
        detachProjectPanelDock();
    }

    private void revealProjectPanelFromDock() {
        if (!isScriptActiveForUi()) {
            hideProjectPanelDock();
            return;
        }
        prepareProjectPanel();
        if (projectPanelLp != null) {
            adaptProjectPanelSizeToCurrentScreen(projectPanelLp);
            int[] screen = getScreenSizePx();
            int margin = dp(8);
            projectPanelLp.x = projectPanelDockOnLeft
                    ? margin
                    : Math.max(margin, screen[0] - projectPanelLp.width - margin);
            if (projectPanelDockLp != null) {
                projectPanelLp.y = projectPanelDockLp.y - Math.max(0, (projectPanelLp.height - projectPanelDockLp.height) / 2);
            }
            clampPanelToScreen(projectPanelLp);
            rememberSharedPanelPosition(projectPanelLp);
        }
        hideProjectPanelDock();
        showRuntimeAwareProjectPanel();
        if (projectPanelView != null) {
            float startOffset = projectPanelDockOnLeft ? -dp(28) : dp(28);
            projectPanelView.setAlpha(0f);
            projectPanelView.setTranslationX(startOffset);
            projectPanelView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(180)
                    .start();
        }
    }

    private boolean isProjectPanelAttached() {
        return projectPanelView != null && projectPanelView.getParent() != null;
    }

    private boolean isProjectPanelVisible() {
        return projectPanelView != null
                && isProjectPanelAttached()
                && projectPanelView.getVisibility() == View.VISIBLE;
    }

    private void attachProjectPanelIfNeeded() {
        if (projectPanelView == null || isProjectPanelAttached()) {
            return;
        }
        wm.addView(projectPanelView, projectPanelLp);
    }

    private void detachProjectPanel() {
        if (projectPanelView == null || !isProjectPanelAttached()) {
            return;
        }
        try {
            wm.removeView(projectPanelView);
        } catch (Exception e) {
            Log.w(TAG, "detach project panel failed", e);
        }
    }

    private void setupProjectPanel() {
        projectPanelView.findViewById(R.id.btn_close).setOnClickListener(v -> removeProjectPanel());
        projectPanelView.findViewById(R.id.btn_back).setOnClickListener(v -> navigateBack());
        projectPanelView.findViewById(R.id.btn_add).setOnClickListener(v -> showAddDialog());
        projectPanelView.findViewById(R.id.btn_add).setOnLongClickListener(v -> {
            if (currentLevel == NavigationLevel.PROJECT) {
                importProjectInteractive();
                return true;
            }
            return false;
        });
        projectPanelView.findViewById(R.id.btn_search).setOnClickListener(v -> toggleSearch());

        EditText edtSearch = projectPanelView.findViewById(R.id.edt_search);
        ImageView btnClearSearch = projectPanelView.findViewById(R.id.btn_clear_search);
        btnClearSearch.setOnClickListener(v -> edtSearch.setText(""));
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s == null ? "" : s.toString();
                uiHandler.removeCallbacks(searchRefreshRunnable);
                uiHandler.postDelayed(searchRefreshRunnable, 120L);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        projectPanelRecyclerView = projectPanelView.findViewById(R.id.rv_content);
        if (projectPanelRecyclerView != null) {
            projectPanelRecyclerView.setItemAnimator(null);
            projectPanelRecyclerView.setItemViewCacheSize(12);
        }
        ensureProjectPanelAdapters();
        projectPanelContentDirty = true;
        
        // 刷新按钮 - 重新加载当前 task 的 operations
        View btnRefresh = projectPanelView.findViewById(R.id.btn_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                if (currentLevel == NavigationLevel.OPERATION && currentTaskDir != null) {
                    showFlowGraphDialog();
                } else {
                    Toast.makeText(this, "请在 Operation 列表中打开流程图", Toast.LENGTH_SHORT).show();
                }
            });
            btnRefresh.setOnLongClickListener(v -> {
                if (currentLevel == NavigationLevel.PROJECT) {
                    invalidateProjectListCache();
                    loadProjects(true);
                    Toast.makeText(this, "已刷新项目列表", Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (currentLevel == NavigationLevel.TASK && currentProjectDir != null) {
                    invalidateTaskListCache(currentProjectDir);
                    loadTasks(currentProjectDir, true);
                    Toast.makeText(this, "已刷新 Task 列表", Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (currentLevel == NavigationLevel.OPERATION && currentTaskDir != null) {
                    invalidateOperationListCache(currentTaskDir);
                    reloadCurrentProject();
                    loadOperations(currentTaskDir, true);
                    Toast.makeText(this, "已刷新列表", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        DragTouchListener panelDragTouchListener =
                new DragTouchListener(projectPanelLp, wm, projectPanelView, this, true);
        View dragHeader = projectPanelView.findViewById(R.id.drag_header);
        dragHeader.setOnTouchListener(panelDragTouchListener);
        View dragHandle = projectPanelView.findViewById(R.id.drag_handle);
        if (dragHandle != null) {
            dragHandle.setOnTouchListener(panelDragTouchListener);
        }
        View breadcrumbLayout = projectPanelView.findViewById(R.id.breadcrumb_layout);
        if (breadcrumbLayout != null) {
            breadcrumbLayout.setOnTouchListener(panelDragTouchListener);
        }
        View runtimeStatusBar = projectPanelView.findViewById(R.id.runtime_status_bar);
        if (runtimeStatusBar != null) {
            runtimeStatusBar.setOnTouchListener(panelDragTouchListener);
        }
        View searchLayout = projectPanelView.findViewById(R.id.ly_search);
        if (searchLayout != null) {
            searchLayout.setOnTouchListener(panelDragTouchListener);
        }
        View footerDragZone = projectPanelView.findViewById(R.id.footer_drag_zone);
        if (footerDragZone != null) {
            footerDragZone.setOnTouchListener(panelDragTouchListener);
        }
        int[] footerButtonIds = new int[]{
                R.id.btn_run,
                R.id.btn_edit,
                R.id.btn_move_up,
                R.id.btn_move_down,
                R.id.btn_batch
        };
        for (int footerButtonId : footerButtonIds) {
            View footerButton = projectPanelView.findViewById(footerButtonId);
            if (footerButton != null) {
                footerButton.setOnTouchListener(panelDragTouchListener);
            }
        }
        View emptyView = projectPanelView.findViewById(R.id.empty_view);
        if (emptyView != null) {
            emptyView.setOnTouchListener(panelDragTouchListener);
        }
        View resizeHandle = projectPanelView.findViewById(R.id.resize_handle);
        if (resizeHandle != null) {
            resizeHandle.setOnTouchListener(new PanelResizeTouchListener(
                    projectPanelLp,
                    wm,
                    projectPanelView,
                    this,
                    PROJECT_PANEL_MIN_W_DP,
                    PROJECT_PANEL_MIN_H_DP,
                    PROJECT_PANEL_MAX_H_RATIO_LANDSCAPE
            ));
        }

//       todo  这里加载projects
        loadProjects();

//        todo 绑定运行按钮
        TextView btnRun = projectPanelView.findViewById(R.id.btn_run);
        TextView btnEdit = projectPanelView.findViewById(R.id.btn_edit);
        TextView btnMoveUp = projectPanelView.findViewById(R.id.btn_move_up);
        TextView btnMoveDown = projectPanelView.findViewById(R.id.btn_move_down);
        TextView btnBatch = projectPanelView.findViewById(R.id.btn_batch);
        btnBatch.setOnClickListener(v -> {
            if (currentLevel == NavigationLevel.OPERATION && isScriptActiveForUi()) {
                removeProjectPanel();
                Toast.makeText(this, "已切到后台运行", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentLevel != NavigationLevel.OPERATION) {
                Toast.makeText(this, "请先进入 Operation 列表", Toast.LENGTH_SHORT).show();
                return;
            }
            setOperationBatchMode(!operationBatchMode);
        });
        refreshProjectPanelFooterState();

        btnMoveUp.setOnClickListener(v -> {
            if (currentLevel == NavigationLevel.OPERATION && isScriptActiveForUi()) {
                focusCurrentRunningOperation();
                return;
            }
            if (currentLevel != NavigationLevel.OPERATION) {
                Toast.makeText(this, "请先进入 Operation 列表", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentOperationAdapter == null) {
                return;
            }
            OperationItem selected = currentOperationAdapter.getSelectedItem();
            if (selected == null) {
                Toast.makeText(this, "请先选中节点", Toast.LENGTH_SHORT).show();
                return;
            }
            moveOperation(selected.id, -1);
        });

        btnMoveDown.setOnClickListener(v -> {
            if (currentLevel == NavigationLevel.OPERATION && isScriptActiveForUi()) {
                removeProjectPanel();
                Toast.makeText(this, "已收起节点面板", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentLevel != NavigationLevel.OPERATION) {
                Toast.makeText(this, "请先进入 Operation 列表", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentOperationAdapter == null) {
                return;
            }
            OperationItem selected = currentOperationAdapter.getSelectedItem();
            if (selected == null) {
                Toast.makeText(this, "请先选中节点", Toast.LENGTH_SHORT).show();
                return;
            }
            moveOperation(selected.id, 1);
        });

        // 绑定运行按钮（简化版）
        btnRun.setOnClickListener(v -> {
            if (currentLevel == NavigationLevel.OPERATION && isScriptActiveForUi()) {
                togglePauseState();
                return;
            }
            if (operationBatchMode) {
                deleteBatchSelectedOperations();
                return;
            }

            // 简单的缩放动画
            v.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction(() -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start();
                })
                .start();
            
            // 震动反馈（兼容所有版本）
            try {
                android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(android.content.Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(30);
                    }
                }
            } catch (Exception ignored) {}
            
            RunLaunchData launchData = prepareRunLaunchData();
            if (launchData == null) {
                return;
            }

            Toast.makeText(this, "开始运行: " + launchData.startOperation.getName(), Toast.LENGTH_SHORT).show();
            showRunModeMenu(v, showPanel -> startOperationWithMode(
                    launchData.startOperation,
                    launchData.ctx,
                    launchData.projectName,
                    launchData.selectedTaskName,
                    launchData.selectedTaskOperations,
                    showPanel
            ));
        });

        btnRun.setOnLongClickListener(v -> {
            if (currentLevel == NavigationLevel.OPERATION && isScriptActiveForUi()) {
                return true;
            }
            if (operationBatchMode) {
                return true;
            }
            RunLaunchData launchData = prepareRunLaunchData();
            if (launchData == null) {
                return true;
            }

            PrecheckResult precheckResult = runPrecheck(launchData.selectedTask, launchData.selectedTaskName);
            Runnable continueRun = () -> showRunModeMenu(v, showPanel -> startOperationWithMode(
                    launchData.startOperation,
                    launchData.ctx,
                    launchData.projectName,
                    launchData.selectedTaskName,
                    launchData.selectedTaskOperations,
                    showPanel
            ));
            showPrecheckDialog(precheckResult, continueRun);
            return true;
        });


//        todo 绑定编辑按钮
        // 绑定编辑按钮
        btnEdit.setOnClickListener(v -> {
            if (currentLevel == NavigationLevel.OPERATION && isScriptActiveForUi()) {
                stopScriptFromUi();
                return;
            }
            if (operationBatchMode) {
                setOperationBatchMode(false);
                return;
            }

            if (currentLevel != NavigationLevel.OPERATION) {
                Toast.makeText(this, "请先进入 Operation 列表", Toast.LENGTH_SHORT).show();
                return;
            }

            RecyclerView rv = projectPanelView.findViewById(R.id.rv_content);
            OperationPanelAdapter adapter = (OperationPanelAdapter) rv.getAdapter();
            if (adapter == null) return;

            OperationItem selected = adapter.getSelectedItem();
            if (selected == null) {
                Toast.makeText(this, "请先选中一个操作", Toast.LENGTH_SHORT).show();
                return;
            }

            // 显示编辑对话框
            showEditOperationDialog(selected, adapter);
        });
    }

    /**
     * 显示编辑 Operation JSON 对话框
     */
    private void showEditOperationDialog(OperationItem selected, OperationPanelAdapter adapter) {
        // 获取当前 operation 的 JSON
        String operationJson = getOperationJson(selected.id);
        if (operationJson == null) {
            Toast.makeText(this, "无法读取 operation 数据", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject operationObject = new JSONObject(operationJson);
            int type = operationObject.optInt("type", -1);
            if (type == 1) {
                dialogFactory.showEditClickDialog(selected.id, operationObject);
                return;
            }
            if (type == 2) {
                dialogFactory.showEditDelayDialog(selected.id, operationObject);
                return;
            }
            if (type == 8) {
                dialogFactory.showEditJumpTaskDialog(selected.id, operationObject);
                return;
            }
            if (type == 6) {
                dialogFactory.showEditMatchTemplateDialog(selected.id, operationObject);
                return;
            }
            if (type == 7) {
                dialogFactory.showEditMatchMapTemplateDialog(selected.id, operationObject);
                return;
            }
            if (type == 5) {
                dialogFactory.showEditGestureDialog(selected.id, operationObject);
                return;
            }
            if (type == 11) {
                dialogFactory.showEditVariableScriptDialog(selected.id, operationObject);
                return;
            }
            if (type == 12) {
                dialogFactory.showEditVariableMathDialog(selected.id, operationObject);
                return;
            }
            if (type == 13) {
                dialogFactory.showEditVariableTemplateDialog(selected.id, operationObject);
                return;
            }
            if (type == 14) {
                dialogFactory.showEditLaunchAppDialog(selected.id, operationObject);
                return;
            }
            if (type == 15) {
                dialogFactory.showEditSwitchBranchDialog(selected.id, operationObject);
                return;
            }
            if (type == 16) {
                dialogFactory.showEditLoopDialog(selected.id, operationObject);
                return;
            }
            if (type == 17) {
                showEditBackKeyDialog(selected, adapter, operationObject);
                return;
            }
            if (type == 18) {
                dialogFactory.showEditColorMatchDialog(selected.id, operationObject);
                return;
            }
            if (type == 19) {
                dialogFactory.showEditColorSearchDialog(selected.id, operationObject);
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "解析 operation 失败，回退到 JSON 编辑", e);
        }
        
        // 创建悬浮窗类型的对话框
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_json, null);
        
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        
        WindowManager.LayoutParams dialogLp = new WindowManager.LayoutParams(
                dp(340), WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        dialogLp.gravity = Gravity.CENTER;
        
        // 设置初始值
        TextView tvOpId = dialogView.findViewById(R.id.tv_op_id);
        EditText edtJson = dialogView.findViewById(R.id.edt_json);
        
        tvOpId.setText(selected.id);
        edtJson.setText(operationJson);
        
        // 添加对话框到窗口
        wm.addView(dialogView, dialogLp);
        
        // 让 EditText 获取焦点并弹出键盘
        edtJson.requestFocus();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(edtJson, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
        
        // 取消按钮
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            wm.removeView(dialogView);
        });
        
        // 保存按钮
        dialogView.findViewById(R.id.btn_save).setOnClickListener(v -> {
            String newJson = edtJson.getText().toString().trim();
            
            if (newJson.isEmpty()) {
                Toast.makeText(this, "JSON 不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 验证 JSON 格式
            try {
                new org.json.JSONObject(newJson);
            } catch (Exception e) {
                Toast.makeText(this, "JSON 格式错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 保存修改
            if (saveOperationJson(selected.id, newJson, adapter)) {
                wm.removeView(dialogView);
            }
        });
    }
    
    /**
     * 获取指定 operation 的 JSON 字符串（异步版本）
     */
    private void getOperationJsonAsync(String operationId, OperationJsonCallback callback) {
        if (currentTaskDir == null) {
            callback.onResult(null);
            return;
        }
        
        File jsonFile = new File(currentTaskDir, "operations.json");
        fileIOManager.readFileAsync(jsonFile, (content, error) -> {
            if (error != null || content == null) {
                callback.onResult(null);
                return;
            }
            
            try {
                // 解析 JSON 数组
                JSONArray jsonArray = new JSONArray(content);
                
                // 查找对应的 operation
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if (operationId.equals(jsonObject.optString("id"))) {
                        // 返回格式化的 JSON
                        callback.onResult(jsonObject.toString(2));
                        return;
                    }
                }
                callback.onResult(null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse operation JSON", e);
                callback.onResult(null);
            }
        });
    }
    
    /**
     * 获取指定 operation 的 JSON 字符串（保留同步版本用于兼容）
     * @deprecated 使用 getOperationJsonAsync 替代
     */
    @Deprecated
    private String getOperationJson(String operationId) {
        try {
            File jsonFile = new File(currentTaskDir, "operations.json");
            String content = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                content = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
            }
            
            // 解析 JSON 数组
            JSONArray jsonArray = new JSONArray(content);
            
            // 查找对应的 operation
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if (operationId.equals(jsonObject.optString("id"))) {
                    // 返回格式化的 JSON
                    return jsonObject.toString(2);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "读取 operation JSON 失败", e);
        }
        return null;
    }
    
    /**
     * 保存 operation JSON
     */
    private boolean saveOperationJson(String operationId, String newJson, OperationPanelAdapter adapter) {
        try {
            File jsonFile = new File(currentTaskDir, "operations.json");
            String content = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                content = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
            }
            
            // 解析 JSON 数组
            JSONArray jsonArray = new JSONArray(content);
            
            // 查找并替换对应的 operation
            boolean found = false;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if (operationId.equals(jsonObject.optString("id"))) {
                    // 替换为新的 JSON
                    JSONObject newJsonObj = new JSONObject(newJson);
                    newJsonObj.put("id", operationId);
                    if (!newJsonObj.has("type") && jsonObject.has("type")) {
                        newJsonObj.put("type", jsonObject.optInt("type", 1));
                    }
                    if (!newJsonObj.has("responseType") && jsonObject.has("responseType")) {
                        newJsonObj.put("responseType", jsonObject.optInt("responseType", 1));
                    }
                    if (!newJsonObj.has("name") && jsonObject.has("name")) {
                        newJsonObj.put("name", jsonObject.optString("name", ""));
                    }
                    if (!newJsonObj.has("inputMap") && jsonObject.has("inputMap")) {
                        newJsonObj.put("inputMap", jsonObject.optJSONObject("inputMap"));
                    }
                    jsonArray.put(i, newJsonObj);
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                Toast.makeText(this, "未找到要更新的 operation", Toast.LENGTH_SHORT).show();
                return false;
            }
            
            // 保存回文件
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(jsonArray.toString(2));
            }

            int cleaned = 0;
            
            // 重新加载当前 project 到缓存（从文件重新解析）
            reloadCurrentProject();
            
            // 刷新列表
            loadOperations(currentTaskDir);
            refreshOpenFlowGraphPanel(operationId);
            
            Toast.makeText(this, cleaned > 0 ? "保存成功，已清理 " + cleaned + " 张无用图片" : "保存成功", Toast.LENGTH_SHORT).show();
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "保存 operation JSON 失败", e);
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 重新加载当前 project（用于保存 operation 后刷新缓存）
     */
    private void reloadCurrentProject() {
        if (currentProjectDir == null) return;
        
        try {
            Template.clearProjectCache(currentProjectDir.getName());
            // 从文件重新加载当前 project
            Project refreshedProject = loadProjectFromDir(currentProjectDir);
            if (refreshedProject != null) {
                upsertCachedProject(refreshedProject);
            }
        } catch (Exception e) {
            Log.e(TAG, "重新加载 project 失败", e);
        }
    }
    
    /**
     * 从目录加载 Project 对象
     */
    private Project loadProjectFromDir(File projectDir) {
        try {
            Project project = new Project();
            project.setProjectName(projectDir.getName());

            Map<String, Task> taskMap = new HashMap<>();
            File[] taskDirs = projectDir.listFiles(File::isDirectory);
            if (taskDirs != null) {
                for (File taskDir : taskDirs) {
                    File opFile = new File(taskDir, "operations.json");
                    if (opFile.exists()) {
                        Task task = new Task();
                        task.setId(taskDir.getName());
                        task.setName(taskDir.getName());

                        // 优先用 Gson 完整解析（脚本执行需要），失败时回退到 JSONArray 轻量解析
                        boolean loaded = false;
                        try {
                            String content = readFileContent(opFile);
                            List<MetaOperation> ops = OperationGsonUtils.fromJson(content);
                            for (MetaOperation op : ops) {
                                if (op == null || TextUtils.isEmpty(op.getId())) continue;
                                task.putOperation(op);
                            }
                            loaded = !task.getOperationMap().isEmpty();
                        } catch (Exception ignored) {
                        }
                        if (!loaded) {
                            // Gson 失败时（release 混淆可能导致），用 JSONArray 解析
                            // 仅保存 id/name/type，足够面板展示；执行路径会在运行时再次解析
                            try {
                                String content = readFileContent(opFile);
                                JSONArray arr = new JSONArray(content);
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject obj = arr.optJSONObject(i);
                                    if (obj == null) continue;
                                    String id = obj.optString("id", "");
                                    if (TextUtils.isEmpty(id)) continue;
                                    int typeInt = obj.optInt("type", 1);
                                    MetaOperation stub = new com.auto.master.Task.Operation.ClickOperation();
                                    stub.setId(id);
                                    stub.setName(obj.optString("name", "未命名"));
                                    stub.setType(typeInt);
                                    task.putOperation(stub);
                                }
                            } catch (Exception ignored2) {
                            }
                        }
                        taskMap.put(taskDir.getName(), task);
                    }
                }
            }
            project.setTaskMap(taskMap);
            return project;
        } catch (Exception e) {
            Log.e(TAG, "加载 project 失败: " + projectDir.getName(), e);
            return null;
        }
    }

    private void navigateBack() {
        switch (currentLevel) {
            case TASK:
                currentLevel = NavigationLevel.PROJECT;
                currentProjectDir = null;
                clearProjectPanelSearch();
                loadProjects();
                break;
            case OPERATION:
                setOperationBatchMode(false);
                currentLevel = NavigationLevel.TASK;
                clearProjectPanelSearch();
                loadTasks(currentProjectDir);
                break;
        }
        updateUIForLevel();
    }

    private void updateUIForLevel() {
        TextView tvTitle = projectPanelView.findViewById(R.id.tv_title);
        ImageView btnBack = projectPanelView.findViewById(R.id.btn_back);
        LinearLayout breadcrumbLayout = projectPanelView.findViewById(R.id.breadcrumb_layout);
        TextView tvBreadcrumb = projectPanelView.findViewById(R.id.tv_breadcrumb);
        TextView tvBreadcrumbLegacy = projectPanelView.findViewById(R.id.tv_breadcrumb_legacy);

        switch (currentLevel) {
            case PROJECT:
                tvTitle.setText("Projects");
                btnBack.setVisibility(View.GONE);
                breadcrumbLayout.setVisibility(View.GONE);
                if (tvBreadcrumb != null) {
                    tvBreadcrumb.setVisibility(View.GONE);
                    tvBreadcrumb.setText("");
                }
                if (tvBreadcrumbLegacy != null) {
                    tvBreadcrumbLegacy.setText("");
                }
                break;
            case TASK:
                tvTitle.setText(currentProjectDir != null ? currentProjectDir.getName() : "Tasks");
                btnBack.setVisibility(View.VISIBLE);
                breadcrumbLayout.setVisibility(View.VISIBLE);
                if (tvBreadcrumb != null) {
                    tvBreadcrumb.setVisibility(View.VISIBLE);
                    tvBreadcrumb.setText("Project");
                }
                if (tvBreadcrumbLegacy != null) {
                    tvBreadcrumbLegacy.setText(currentProjectDir != null ? currentProjectDir.getName() : "");
                }
                break;
            case OPERATION:
                tvTitle.setText(currentTaskDir != null ? currentTaskDir.getName() : "Operations");
                btnBack.setVisibility(View.VISIBLE);
                breadcrumbLayout.setVisibility(View.VISIBLE);
                String path = (currentProjectDir != null ? currentProjectDir.getName() : "") + " > " +
                        (currentTaskDir != null ? currentTaskDir.getName() : "");
                if (tvBreadcrumb != null) {
                    tvBreadcrumb.setVisibility(View.VISIBLE);
                    tvBreadcrumb.setText("Operation");
                }
                if (tvBreadcrumbLegacy != null) {
                    tvBreadcrumbLegacy.setText(path);
                }
                break;
        }
        refreshProjectPanelFooterState();
        syncProjectPanelRuntimeUi();
    }

    private void refreshProjectPanelFooterState() {
        if (projectPanelView == null) {
            return;
        }
        TextView btnRun = projectPanelView.findViewById(R.id.btn_run);
        TextView btnEdit = projectPanelView.findViewById(R.id.btn_edit);
        TextView btnMoveUp = projectPanelView.findViewById(R.id.btn_move_up);
        TextView btnMoveDown = projectPanelView.findViewById(R.id.btn_move_down);
        TextView btnBatch = projectPanelView.findViewById(R.id.btn_batch);
        if (btnRun == null || btnEdit == null || btnMoveUp == null || btnMoveDown == null || btnBatch == null) {
            return;
        }

        boolean runtimeMode = currentLevel == NavigationLevel.OPERATION && isScriptActiveForUi();
        if (runtimeMode) {
            btnRun.setText(isPaused ? "继续" : "暂停");
            btnEdit.setText("停止");
            btnBatch.setText("收起");
            btnMoveUp.setVisibility(View.GONE);
            btnMoveDown.setVisibility(View.GONE);
            btnBatch.setVisibility(View.VISIBLE);
            return;
        }

        btnMoveUp.setVisibility(View.VISIBLE);
        btnMoveDown.setVisibility(View.VISIBLE);
        btnBatch.setVisibility(View.VISIBLE);
        if (operationBatchMode) {
            btnRun.setText("删除选中(" + batchSelectedOperationIds.size() + ")");
            btnEdit.setText("退出批量");
            btnBatch.setText("批量中");
        } else {
            btnRun.setText("▶ 运行");
            btnEdit.setText("✎ 编辑");
            btnMoveUp.setText("上移");
            btnMoveDown.setText("下移");
            btnBatch.setText("批量");
        }
    }

    private void syncProjectPanelRuntimeUi() {
        if (projectPanelView == null) {
            return;
        }
        View runtimeBar = projectPanelView.findViewById(R.id.runtime_status_bar);
        TextView tvStatus = projectPanelView.findViewById(R.id.tv_runtime_status);
        TextView tvProgress = projectPanelView.findViewById(R.id.tv_runtime_progress);
        TextView tvDetail = projectPanelView.findViewById(R.id.tv_runtime_detail);
        View dot = projectPanelView.findViewById(R.id.runtime_status_dot);
        if (runtimeBar == null || tvStatus == null || tvProgress == null || tvDetail == null || dot == null) {
            return;
        }

        boolean visible = currentLevel == NavigationLevel.OPERATION && isScriptActiveForUi();
        runtimeBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            refreshProjectPanelFooterState();
            return;
        }

        tvStatus.setText(runtimeStatusText);
        tvStatus.setTextColor(runtimeStatusColor);
        dot.setBackgroundColor(runtimeStatusColor);
        tvProgress.setText(String.format(Locale.getDefault(), "%d/%d",
                Math.max(0, currentOperationIndex),
                Math.max(0, totalOperationCount)));

        String detail = !TextUtils.isEmpty(currentRunningOperationName)
                ? "当前节点: " + currentRunningOperationName
                : "当前任务: " + (TextUtils.isEmpty(currentRunningTask) ? "-" : currentRunningTask);
        if (!TextUtils.isEmpty(currentRunningProject)) {
            detail = currentRunningProject + " / " + detail;
        }
        tvDetail.setText(detail);
        refreshProjectPanelFooterState();
    }

    private void focusCurrentRunningOperation() {
        if (currentOperationAdapter == null || TextUtils.isEmpty(currentRunningOperationId)) {
            Toast.makeText(this, "当前没有可定位的运行节点", Toast.LENGTH_SHORT).show();
            return;
        }
        currentOperationAdapter.selectById(currentRunningOperationId);
        RecyclerView rv = getProjectPanelRecyclerView();
        int position = currentOperationAdapter.findPositionById(currentRunningOperationId);
        if (rv != null && position >= 0) {
            rv.scrollToPosition(position);
        }
    }

    private void toggleSearch() {
        LinearLayout lySearch = projectPanelView.findViewById(R.id.ly_search);
        if (lySearch.getVisibility() == View.VISIBLE) {
            lySearch.setVisibility(View.GONE);
        } else {
            lySearch.setVisibility(View.VISIBLE);
            EditText edtSearch = projectPanelView.findViewById(R.id.edt_search);
            edtSearch.requestFocus();
        }
    }

    // ==================== Dialog Factory 初始化 ====================

    private void initDialogFactory() {
        if (dialogFactory != null) return; // 已初始化

        crudHelper = new OperationCrudHelper(this);
        dialogHelpers = new DialogHelpers(this);
        dialogFactory = new OperationDialogFactory(this, dialogHelpers, crudHelper);

        dialogFactory.setOperationIdGenerator(this::generateOperationId);

        dialogFactory.setOnOperationAddedListener(() -> {
            reloadCurrentProject();
            loadOperations(currentTaskDir);
        });

        dialogFactory.setOnOperationUpdatedListener(() -> {
            reloadCurrentProject();
            loadOperations(currentTaskDir);
        });

        dialogFactory.setNextOperationBinder((view, excludeId) ->
            bindNextOperationSuggestions(view, excludeId));

        dialogFactory.setPointPickerLauncher((callback, viewsToHide) ->
            showScreenPointPicker((x, y) -> callback.onPointPicked(x, y), viewsToHide));

        dialogFactory.setColorPointPickerLauncher((callback, viewsToHide) ->
            showColorPointPicker((x, y, color) -> callback.onColorPointPicked(x, y, color), viewsToHide));

        dialogFactory.setOperationPickerLauncher(new OperationDialogFactory.OperationPickerLauncher() {
            @Override
            public void showOperationPickerDialog(String title,
                                                  String excludeId,
                                                  String currentSelectedId,
                                                  OperationDialogFactory.OperationSelectedCallback callback) {
                FloatWindowService.this.showOperationPickerDialog(
                        title,
                        excludeId,
                        currentSelectedId,
                        callback == null ? null : callback::onOperationSelected);
            }
        });

        dialogFactory.setOperationUpdater((operationId, newJson) ->
            saveOperationJson(operationId, newJson, currentOperationAdapter));

        dialogFactory.setGestureHelper(new OperationDialogFactory.GestureHelper() {
            @Override public void refreshGestureOptions(android.widget.AutoCompleteTextView v) { FloatWindowService.this.refreshGestureOptions(v); }
            @Override public java.io.File resolveTaskGestureFile(String n) { return FloatWindowService.this.resolveTaskGestureFile(n); }
            @Override public String generateGestureTimestampName() { return FloatWindowService.this.generateGestureTimestampName(); }
            @Override public void showGestureLibraryDialog(android.widget.AutoCompleteTextView g, android.widget.TextView s) { FloatWindowService.this.showGestureLibraryDialog(g, s); }
            @Override public void playGestureFromInput(android.widget.AutoCompleteTextView g, android.widget.TextView s) { FloatWindowService.this.playGestureFromInput(g, s); }
            @Override public void beginGestureRecordFromDialog(View d, android.widget.AutoCompleteTextView e, android.widget.TextView t) { FloatWindowService.this.beginGestureRecordFromDialog(d, e, t); }
            @Override public void updateGestureStatus(android.widget.TextView s, String n) { FloatWindowService.this.updateGestureStatus(s, n); }
            @Override public String normalizeGestureFileName(String r) { return FloatWindowService.this.normalizeGestureFileName(r); }
        });

        dialogFactory.setTaskOperationHelper(new OperationDialogFactory.TaskOperationHelper() {
            @Override public java.util.List<String> getCurrentProjectTaskIds() { return FloatWindowService.this.getCurrentProjectTaskIds(); }
            @Override public java.util.List<String> getTaskOperationIds(String taskId) { return FloatWindowService.this.getTaskOperationIds(taskId); }
            @Override public String getOperationDisplayLabel(String operationId) { return FloatWindowService.this.formatCurrentTaskOperationReference(operationId); }
        });

        dialogFactory.setTemplateHelper(new OperationDialogFactory.TemplateHelper() {
            @Override public void refreshTemplateOptions(android.widget.AutoCompleteTextView v) { FloatWindowService.this.refreshTemplateOptions(v); }
            @Override public void bindTemplatePreview(View d, android.widget.AutoCompleteTextView v) { FloatWindowService.this.bindTemplatePreview(d, v); }
            @Override public void renderRecentTemplateStrip(View d, android.widget.AutoCompleteTextView v) { FloatWindowService.this.renderRecentTemplateStrip(d, v); }
            @Override public void setupAdvancedMatchSection(View d, org.json.JSONObject o, String e) { FloatWindowService.this.setupAdvancedMatchSection(d, o, e); }
            @Override public void fillAdvancedMatchInputMap(View d, org.json.JSONObject m) { FloatWindowService.this.fillAdvancedMatchInputMap(d, m); }
            @Override public String generateTemplateTimestampName() { return FloatWindowService.this.generateTemplateTimestampName(); }
            @Override public void showTemplateLibraryDialog(android.widget.AutoCompleteTextView v, View o) { FloatWindowService.this.showTemplateLibraryDialog(v, o); }
            @Override public void beginTemplateCaptureFromDialog(View d, android.widget.AutoCompleteTextView e) { FloatWindowService.this.beginTemplateCaptureFromDialog(d, e); }
        });

        dialogFactory.setMatchMapHelper(new OperationDialogFactory.MatchMapHelper() {
            @Override
            public void beginRegionPickForRow(View mainDialogView, android.widget.EditText edtBbox) {
                FloatWindowService.this.beginRegionPickFromDialog(mainDialogView, edtBbox, null);
            }
            @Override
            public void showTemplateMultiSelectDialog(java.util.List<String> currentSelected,
                    OperationDialogFactory.MatchMapHelper.OnMultiSelectConfirmed callback) {
                FloatWindowService.this.showTemplateMultiSelectDialog(currentSelected, callback);
            }
            @Override
            public void importBboxFromTemplate(View mainDialogView, android.widget.EditText edtBbox) {
                FloatWindowService.this.importBboxFromTemplate(mainDialogView, edtBbox);
            }
        });

        dialogFactory.setOcrHelper(new OperationDialogFactory.OcrHelper() {
            @Override public void updateOcrBboxStatus(android.widget.TextView s, java.util.List<Integer> b) { FloatWindowService.this.updateOcrBboxStatus(s, b); }
            @Override public void beginOcrRegionPickFromDialog(View d, android.widget.EditText e, android.widget.TextView s) { FloatWindowService.this.beginRegionPickFromDialog(d, e, s); }
            @Override public void testOcrFromDialog(android.widget.EditText b, android.widget.EditText t, android.widget.AutoCompleteTextView en, android.widget.TextView r) { FloatWindowService.this.testOcrFromDialog(b, t, en, r); }
            @Override public java.util.List<Integer> parseBboxInput(String raw) { return FloatWindowService.this.parseBboxInput(raw); }
        });

        dialogFactory.setVariableHelper(new OperationDialogFactory.VariableHelper() {
            @Override public java.util.List<String> getVariableSourceModes() { return FloatWindowService.this.getVariableSourceModes(); }
            @Override public java.util.List<String> getVariableValueTypes() { return FloatWindowService.this.getVariableValueTypes(); }
            @Override public void bindVariableSourceModeWatcher(android.widget.AutoCompleteTextView m, android.widget.TextView l, android.widget.EditText v) { FloatWindowService.this.bindVariableSourceModeWatcher(m, l, v); }
            @Override public String sourceModeValueToDisplay(String v) { return FloatWindowService.this.sourceModeValueToDisplay(v); }
            @Override public String sourceModeDisplayToValue(String d) { return FloatWindowService.this.sourceModeDisplayToValue(d); }
            @Override public void updateVariableSourceInputUi(android.widget.TextView l, android.widget.EditText v, String m) { FloatWindowService.this.updateVariableSourceInputUi(l, v, m); }
        });

        dialogFactory.setVariableMathHelper(new OperationDialogFactory.VariableMathHelper() {
            @Override public java.util.List<String> getVariableMathActions() { return FloatWindowService.this.getVariableMathActions(); }
            @Override public boolean isUnaryMathAction(String a) { return FloatWindowService.this.isUnaryMathAction(a); }
            @Override public void updateVariableMathOperandUi(android.widget.TextView l, android.widget.EditText v, String m, String a) { FloatWindowService.this.updateVariableMathOperandUi(l, v, m, a); }
            @Override public void bindVariableMathWatcher(android.widget.AutoCompleteTextView mo, android.widget.AutoCompleteTextView ac, android.widget.TextView l, android.widget.EditText v) { FloatWindowService.this.bindVariableMathWatcher(mo, ac, l, v); }
        });

        dialogFactory.setLaunchAppHelper(new OperationDialogFactory.LaunchAppHelper() {
            @Override public void refreshAppOptions(android.widget.AutoCompleteTextView v) { FloatWindowService.this.refreshLaunchableAppOptions(v); }
            @Override public com.auto.master.floatwin.adapter.LaunchAppPickerAdapter.LaunchAppItem findApp(String pkg) { return FloatWindowService.this.findLaunchableApp(pkg); }
            @Override public void showAppPicker(String title, String cur, android.widget.AutoCompleteTextView pv, android.widget.EditText nv, android.widget.TextView sv) { FloatWindowService.this.showAppPickerDialog(title, cur, pv, nv, sv); }
            @Override public void updateAppSummary(android.widget.TextView s, String pkg) { FloatWindowService.this.updateLaunchAppSummary(s, pkg); }
            @Override public String normalizePackageName(String r) { return FloatWindowService.this.normalizePackageName(r); }
        });
    }

    private void showAddDialog() {
        if (currentLevel == NavigationLevel.PROJECT) {
            promptCreateProject();
            return;
        }
        if (currentLevel == NavigationLevel.TASK) {
            promptCreateTask();
            return;
        }
        if (currentTaskDir == null) {
            Toast.makeText(this, "请先进入 Operation 列表", Toast.LENGTH_SHORT).show();
            return;
        }

        android.view.ContextThemeWrapper themedCtx =
                new android.view.ContextThemeWrapper(this, R.style.Theme_AtomMaster);
        View dialogView = LayoutInflater.from(themedCtx).inflate(R.layout.dialog_add_operation, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.78f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 460, null);

        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_add_operation_menu);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(null);

        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnCancel.setOnClickListener(v -> safeRemoveView(dialogView));

        List<AddOperationMenuAdapter.MenuSection> sections = buildAddOperationMenuSections();
        AddOperationMenuAdapter adapter = new AddOperationMenuAdapter(
                this,
                sections,
                item -> btnConfirm.setEnabled(item.enabled)
        );
        recyclerView.setAdapter(adapter);
        btnConfirm.setEnabled(false);

        AddOperationMenuAdapter.MenuItem initialItem = adapter.findFirstEnabledItem();
        if (initialItem != null) {
            adapter.collapseAllExcept(initialItem.id);
            adapter.setSelectedItem(initialItem);
            btnConfirm.setEnabled(true);
        }

        btnConfirm.setOnClickListener(v -> {
            AddOperationMenuAdapter.MenuItem selectedItem = adapter.getSelectedItem();
            if (selectedItem == null) {
                Toast.makeText(this, "请选择一个节点类型", Toast.LENGTH_SHORT).show();
                return;
            }
            safeRemoveView(dialogView);
            dispatchAddOperationMenuAction(selectedItem.id);
        });
    }

    private List<AddOperationMenuAdapter.MenuSection> buildAddOperationMenuSections() {
        List<AddOperationMenuAdapter.MenuSection> sections = new ArrayList<>();

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "交互节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("click", "点击", "执行点击或坐标点按", "点", R.color.op_click, true),
                        new AddOperationMenuAdapter.MenuItem("delay", "延时", "等待一段时间后继续", "延", R.color.op_delay, true),
                        new AddOperationMenuAdapter.MenuItem("gesture", "手势", "执行录制好的滑动或连点", "手", R.color.op_gesture, true),
                        new AddOperationMenuAdapter.MenuItem("back_key", "返回键", "触发系统返回动作", "返", R.color.op_back_key, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "识别节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("match_template", "模板匹配", "识别单张模板图片", "图", R.color.op_match_template, true),
                        new AddOperationMenuAdapter.MenuItem("match_map_template", "图集匹配", "在图集中查找可命中的模板", "集", R.color.op_match_map, true),
                        new AddOperationMenuAdapter.MenuItem("color_match", "颜色匹配", "按坐标组校验颜色", "色", R.color.op_color_match, true),
                        new AddOperationMenuAdapter.MenuItem("color_search", "找色", "在区域中搜索目标颜色", "找", R.color.op_color_search, true),
                        new AddOperationMenuAdapter.MenuItem("ocr", "OCR 识别", "识别屏幕文字并输出结果", "文", R.color.op_ocr, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "流程节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("jump_task", "跳转任务", "切换到目标任务或节点", "跳", R.color.op_jump_task, true),
                        new AddOperationMenuAdapter.MenuItem("switch_branch", "分支判断", "根据条件选择不同路径", "支", R.color.op_condition, true),
                        new AddOperationMenuAdapter.MenuItem("loop", "循环", "重复执行一段流程", "循", R.color.op_condition, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "变量节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("variable_set", "变量设置", "写入固定值或运行时值", "设", R.color.op_var_template, true),
                        new AddOperationMenuAdapter.MenuItem("variable_script", "变量脚本", "用脚本生成变量结果", "本", R.color.op_var_script, true),
                        new AddOperationMenuAdapter.MenuItem("variable_math", "变量运算", "做数值计算与表达式处理", "算", R.color.op_var_math, true),
                        new AddOperationMenuAdapter.MenuItem("variable_template", "模板变量", "从模板结果中提取变量", "模", R.color.op_var_template, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "系统节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("launch_app", "启动应用", "拉起指定应用并等待前台", "启", R.color.op_app_launch, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "即将支持",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("crop_region", "区域裁剪", "保留竞品风格入口，当前尚未开放", "裁", R.color.op_crop, false)
                )));

        return sections;
    }

    private void dispatchAddOperationMenuAction(String actionId) {
        if (TextUtils.isEmpty(actionId)) {
            return;
        }
        switch (actionId) {
            case "click":
                dialogFactory.showAddClickDialog();
                return;
            case "delay":
                dialogFactory.showAddDelayDialog();
                return;
            case "gesture":
                showAddGestureDialog();
                return;
            case "back_key":
                showAddBackKeyDialog();
                return;
            case "match_template":
                dialogFactory.showAddMatchTemplateDialog();
                return;
            case "match_map_template":
                dialogFactory.showAddMatchMapTemplateDialog();
                return;
            case "color_match":
                dialogFactory.showAddColorMatchDialog();
                return;
            case "color_search":
                dialogFactory.showAddColorSearchDialog();
                return;
            case "ocr":
                dialogFactory.showAddOcrDialog();
                return;
            case "jump_task":
                dialogFactory.showAddJumpTaskDialog();
                return;
            case "switch_branch":
                dialogFactory.showAddSwitchBranchDialog();
                return;
            case "loop":
                dialogFactory.showAddLoopDialog();
                return;
            case "variable_set":
                dialogFactory.showAddVariableSetDialog();
                return;
            case "variable_script":
                dialogFactory.showAddVariableScriptDialog();
                return;
            case "variable_math":
                dialogFactory.showAddVariableMathDialog();
                return;
            case "variable_template":
                dialogFactory.showAddVariableTemplateDialog();
                return;
            case "launch_app":
                dialogFactory.showAddLaunchAppDialog();
                return;
            case "crop_region":
                Toast.makeText(this, "该类型将很快支持", Toast.LENGTH_SHORT).show();
                return;
            default:
                Toast.makeText(this, "暂不支持该节点类型", Toast.LENGTH_SHORT).show();
        }
    }

    private void promptCreateProject() {
        showNameInputDialog(
                "新建项目",
                "输入项目名称",
                suggestNextDirectoryName(getProjectsRootDir(), "Project"),
                this::createProject);
    }

    private void promptCreateTask() {
        if (currentProjectDir == null) {
            Toast.makeText(this, "请先选择 Project", Toast.LENGTH_SHORT).show();
            return;
        }
        showNameInputDialog(
                "新建 Task",
                "输入 Task 名称",
                suggestNextDirectoryName(currentProjectDir, "Task"),
                name -> createTask(currentProjectDir, name));
    }

    private void promptRenameProject(File projectDir) {
        if (projectDir == null || !projectDir.isDirectory()) {
            Toast.makeText(this, "当前 Project 无效", Toast.LENGTH_SHORT).show();
            return;
        }
        showNameInputDialog(
                "重命名项目",
                "输入新的项目名称",
                projectDir.getName(),
                name -> renameProject(projectDir, name));
    }

    private void promptRenameTask(File taskDir) {
        if (taskDir == null || !taskDir.isDirectory()) {
            Toast.makeText(this, "当前 Task 无效", Toast.LENGTH_SHORT).show();
            return;
        }
        showNameInputDialog(
                "重命名 Task",
                "输入新的 Task 名称",
                taskDir.getName(),
                name -> renameTask(taskDir, name));
    }

    private void showNameInputDialog(
            String title,
            String hint,
            String initialValue,
            java.util.function.Consumer<String> onSubmit) {
        android.view.ContextThemeWrapper ctx =
                new android.view.ContextThemeWrapper(this, R.style.Theme_AtomMaster);
        android.widget.EditText input = new android.widget.EditText(ctx);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setText(initialValue);
        input.setSelection(input.getText().length());

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = input.getText() == null
                            ? ""
                            : input.getText().toString().trim();
                    String error = validateEntryName(name);
                    if (error != null) {
                        input.setError(error);
                        return;
                    }
                    input.clearFocus();
                    dialog.dismiss();
                    if (onSubmit != null) {
                        onSubmit.accept(name);
                    }
                }));
        dialog.show();
    }

    @Nullable
    private String validateEntryName(String name) {
        if (TextUtils.isEmpty(name)) {
            return "名称不能为空";
        }
        if (".".equals(name) || "..".equals(name)) {
            return "名称不合法";
        }
        if (name.endsWith(".") || name.endsWith(" ")) {
            return "名称不能以空格或 . 结尾";
        }
        if (Pattern.compile("[\\\\/:*?\"<>|]").matcher(name).find()) {
            return "名称不能包含 \\ / : * ? \" < > |";
        }
        return null;
    }

    private String suggestNextDirectoryName(File parentDir, String prefix) {
        int index = 1;
        while (index < 1000) {
            String candidate = String.format(Locale.ROOT, "%s_%02d", prefix, index);
            if (parentDir == null || !new File(parentDir, candidate).exists()) {
                return candidate;
            }
            index++;
        }
        return prefix + "_" + System.currentTimeMillis();
    }

    private int migrateProjectReferencesAfterRename(File projectDir, String oldProjectName, String newProjectName) {
        if (projectDir == null || TextUtils.isEmpty(oldProjectName) || TextUtils.isEmpty(newProjectName)) {
            return 0;
        }
        File[] taskDirs = projectDir.listFiles(File::isDirectory);
        if (taskDirs == null || taskDirs.length == 0) {
            return 0;
        }
        int changedFiles = 0;
        for (File dir : taskDirs) {
            if (rewriteTaskOperationReferences(dir, oldProjectName, newProjectName, null, null, false)) {
                changedFiles++;
            }
        }
        return changedFiles;
    }

    private int migrateTaskReferencesAfterRename(File projectDir, File renamedTaskDir, String oldTaskName, String newTaskName) {
        if (projectDir == null || TextUtils.isEmpty(oldTaskName) || TextUtils.isEmpty(newTaskName)) {
            return 0;
        }
        File[] taskDirs = projectDir.listFiles(File::isDirectory);
        if (taskDirs == null || taskDirs.length == 0) {
            return 0;
        }
        int changedFiles = 0;
        for (File dir : taskDirs) {
            boolean rewriteTaskContext = renamedTaskDir != null && renamedTaskDir.equals(dir);
            if (rewriteTaskOperationReferences(dir, null, null, oldTaskName, newTaskName, rewriteTaskContext)) {
                changedFiles++;
            }
        }
        return changedFiles;
    }

    private boolean rewriteTaskOperationReferences(File taskDir,
                                                   @Nullable String oldProjectName,
                                                   @Nullable String newProjectName,
                                                   @Nullable String oldTaskName,
                                                   @Nullable String newTaskName,
                                                   boolean rewriteTaskContext) {
        if (taskDir == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        File operationsFile = new File(taskDir, "operations.json");
        if (!operationsFile.exists()) {
            return false;
        }
        try {
            String content = new String(Files.readAllBytes(operationsFile.toPath()), StandardCharsets.UTF_8);
            if (TextUtils.isEmpty(content.trim())) {
                return false;
            }
            JSONArray array = new JSONArray(content);
            boolean changed = false;
            for (int i = 0; i < array.length(); i++) {
                JSONObject operation = array.optJSONObject(i);
                if (operation == null) {
                    continue;
                }
                if (rewriteTaskContext && replaceJsonString(operation, "taskId", oldTaskName, newTaskName)) {
                    changed = true;
                }
                JSONObject inputMap = operation.optJSONObject("inputMap");
                if (inputMap == null) {
                    continue;
                }
                if (replaceJsonString(inputMap, MetaOperation.PROJECT, oldProjectName, newProjectName)) {
                    changed = true;
                }
                if (rewriteTaskContext && replaceJsonString(inputMap, MetaOperation.TASK, oldTaskName, newTaskName)) {
                    changed = true;
                }
                if (replaceJsonString(inputMap, MetaOperation.TARGET_TASK_ID, oldTaskName, newTaskName)) {
                    changed = true;
                }
                if (replaceJsonString(inputMap, MetaOperation.ORIGIN_TASK_ID, oldTaskName, newTaskName)) {
                    changed = true;
                }
            }
            if (!changed) {
                return false;
            }
            Files.write(operationsFile.toPath(), array.toString(2).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "迁移节点引用失败: " + taskDir.getAbsolutePath(), e);
            return false;
        }
    }

    private boolean replaceJsonString(@Nullable JSONObject jsonObject,
                                      @Nullable String key,
                                      @Nullable String oldValue,
                                      @Nullable String newValue) {
        if (jsonObject == null
                || TextUtils.isEmpty(key)
                || TextUtils.isEmpty(oldValue)
                || TextUtils.isEmpty(newValue)) {
            return false;
        }
        String current = jsonObject.optString(key, null);
        if (!TextUtils.equals(current, oldValue)) {
            return false;
        }
        try {
            jsonObject.put(key, newValue);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "更新 JSON 字段失败: " + key, e);
            return false;
        }
    }

    private void renameProject(File projectDir, String newName) {
        if (projectDir == null || TextUtils.isEmpty(newName)) {
            return;
        }
        if (TextUtils.equals(projectDir.getName(), newName)) {
            Toast.makeText(this, "项目名称未变化", Toast.LENGTH_SHORT).show();
            return;
        }
        File parentDir = projectDir.getParentFile();
        if (parentDir == null) {
            Toast.makeText(this, "项目目录异常", Toast.LENGTH_SHORT).show();
            return;
        }
        File newProjectDir = new File(parentDir, newName);
        if (newProjectDir.exists()) {
            Toast.makeText(this, "已存在同名项目", Toast.LENGTH_SHORT).show();
            return;
        }
        String oldProjectName = projectDir.getName();
        Toast.makeText(this, "正在重命名项目...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean success = projectDir.renameTo(newProjectDir);
            if (success && newProjectDir.exists()) {
                migrateProjectReferencesAfterRename(newProjectDir, oldProjectName, newName);
                Template.clearProjectCache(oldProjectName);
                Template.clearProjectCache(newName);
            }
            uiHandler.post(() -> {
                if (!success || !newProjectDir.exists()) {
                    Toast.makeText(this, "项目重命名失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                File updatedTaskDir = null;
                if (currentProjectDir != null && currentProjectDir.equals(projectDir)) {
                    currentProjectDir = newProjectDir;
                    if (currentTaskDir != null) {
                        updatedTaskDir = new File(newProjectDir, currentTaskDir.getName());
                        if (updatedTaskDir.exists()) {
                            currentTaskDir = updatedTaskDir;
                        }
                    }
                }
                cachedProjects.removeIf(project -> project != null
                        && TextUtils.equals(project.getProjectName(), oldProjectName));
                invalidateProjectListCache();
                invalidateTaskListCache(null);
                invalidateOperationListCache(null);
                Project refreshedProject = loadProjectFromDir(newProjectDir);
                if (refreshedProject != null) {
                    upsertCachedProject(refreshedProject);
                }
                if (TextUtils.equals(currentRunningProject, oldProjectName)) {
                    currentRunningProject = newName;
                    CrashLogger.updateRunContext(currentRunningProject, currentRunningTask,
                            currentRunningOperationId, currentRunningOperationName);
                }
                if (currentLevel == NavigationLevel.PROJECT) {
                    loadProjects(true);
                } else if (currentLevel == NavigationLevel.TASK && currentProjectDir != null
                        && currentProjectDir.equals(newProjectDir)) {
                    loadTasks(newProjectDir, true);
                } else if (currentLevel == NavigationLevel.OPERATION && currentTaskDir != null) {
                    loadOperations(currentTaskDir, true);
                    refreshOpenFlowGraphPanel(null);
                }
                updateUIForLevel();
                Toast.makeText(this, "项目已重命名", Toast.LENGTH_SHORT).show();
            });
        }, "rename-project").start();
    }

    private void renameTask(File taskDir, String newName) {
        if (taskDir == null || TextUtils.isEmpty(newName)) {
            return;
        }
        if (TextUtils.equals(taskDir.getName(), newName)) {
            Toast.makeText(this, "Task 名称未变化", Toast.LENGTH_SHORT).show();
            return;
        }
        File projectDir = taskDir.getParentFile();
        if (projectDir == null || !projectDir.isDirectory()) {
            Toast.makeText(this, "Task 目录异常", Toast.LENGTH_SHORT).show();
            return;
        }
        File newTaskDir = new File(projectDir, newName);
        if (newTaskDir.exists()) {
            Toast.makeText(this, "已存在同名 Task", Toast.LENGTH_SHORT).show();
            return;
        }
        String oldTaskName = taskDir.getName();
        String projectName = projectDir.getName();
        Toast.makeText(this, "正在重命名 Task...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean success = taskDir.renameTo(newTaskDir);
            if (success && newTaskDir.exists()) {
                migrateTaskReferencesAfterRename(projectDir, newTaskDir, oldTaskName, newName);
                Template.invalidateTaskCache(projectName, oldTaskName);
                Template.invalidateTaskCache(projectName, newName);
            }
            uiHandler.post(() -> {
                if (!success || !newTaskDir.exists()) {
                    Toast.makeText(this, "Task 重命名失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (currentTaskDir != null && currentTaskDir.equals(taskDir)) {
                    currentTaskDir = newTaskDir;
                }
                invalidateProjectListCache();
                invalidateTaskListCache(null);
                invalidateOperationListCache(null);
                Project refreshedProject = loadProjectFromDir(projectDir);
                if (refreshedProject != null) {
                    upsertCachedProject(refreshedProject);
                }
                if (TextUtils.equals(currentRunningTask, oldTaskName)
                        && currentProjectDir != null
                        && currentProjectDir.equals(projectDir)) {
                    currentRunningTask = newName;
                    CrashLogger.updateRunContext(currentRunningProject, currentRunningTask,
                            currentRunningOperationId, currentRunningOperationName);
                }
                if (currentLevel == NavigationLevel.TASK && currentProjectDir != null
                        && currentProjectDir.equals(projectDir)) {
                    loadTasks(projectDir, true);
                } else if (currentLevel == NavigationLevel.OPERATION && currentTaskDir != null
                        && currentTaskDir.equals(newTaskDir)) {
                    loadOperations(newTaskDir, true);
                    refreshOpenFlowGraphPanel(null);
                }
                updateUIForLevel();
                Toast.makeText(this, "Task 已重命名", Toast.LENGTH_SHORT).show();
            });
        }, "rename-task").start();
    }

    private void createProject(String projectName) {
        File rootDir = getProjectsRootDir();
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            Toast.makeText(this, "无法创建项目根目录", Toast.LENGTH_SHORT).show();
            return;
        }
        File projectDir = new File(rootDir, projectName);
        if (projectDir.exists()) {
            Toast.makeText(this, "项目已存在", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!projectDir.mkdirs()) {
            Toast.makeText(this, "项目创建失败", Toast.LENGTH_SHORT).show();
            return;
        }
        File defaultTaskDir = null;
        try {
            defaultTaskDir = ensureTaskStructure(projectDir, suggestNextDirectoryName(projectDir, "Task"));
        } catch (IOException e) {
            fileIOManager.deleteRecursivelyAsync(projectDir, (success, error) -> {
                // best effort rollback
            });
            Toast.makeText(this, "初始化默认 Task 失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        clearProjectPanelSearch();
        invalidateProjectListCache();
        invalidateTaskListCache(projectDir);
        invalidateOperationListCache(defaultTaskDir);
        currentProjectDir = projectDir;
        currentTaskDir = defaultTaskDir;
        currentLevel = NavigationLevel.OPERATION;
        loadOperations(defaultTaskDir, true);
        updateUIForLevel();
        Toast.makeText(this, "项目已创建", Toast.LENGTH_SHORT).show();
    }

    private void createTask(File projectDir, String taskName) {
        if (projectDir == null || !projectDir.exists()) {
            Toast.makeText(this, "当前 Project 无效", Toast.LENGTH_SHORT).show();
            return;
        }
        File taskDir = new File(projectDir, taskName);
        if (taskDir.exists()) {
            Toast.makeText(this, "Task 已存在", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!taskDir.mkdirs()) {
            Toast.makeText(this, "Task 创建失败", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ensureTaskStructure(projectDir, taskName);
        } catch (IOException e) {
            // 初始化失败，异步删除taskDir
            fileIOManager.deleteRecursivelyAsync(taskDir, (success, error) -> {
                // 删除完成，无需额外操作
            });
            Toast.makeText(this, "初始化 Task 失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        clearProjectPanelSearch();
        invalidateTaskListCache(projectDir);
        invalidateOperationListCache(taskDir);
        if (projectDir.equals(currentProjectDir)) {
            currentTaskDir = taskDir;
            currentLevel = NavigationLevel.OPERATION;
            loadOperations(taskDir, true);
        } else {
            currentProjectDir = projectDir;
            currentTaskDir = taskDir;
            currentLevel = NavigationLevel.OPERATION;
            loadOperations(taskDir, true);
        }
        updateUIForLevel();
        Toast.makeText(this, "Task 已创建", Toast.LENGTH_SHORT).show();
    }

    private File ensureTaskStructure(File projectDir, String taskName) throws IOException {
        File taskDir = new File(projectDir, taskName);
        if (!taskDir.exists() && !taskDir.mkdirs()) {
            throw new IOException("无法创建 Task 目录");
        }
        File operationsFile = new File(taskDir, "operations.json");
        if (!operationsFile.exists()) {
            try (FileWriter writer = new FileWriter(operationsFile)) {
                writer.write("[]");
            }
        }
        File imgDir = new File(taskDir, "img");
        if (!imgDir.exists() && !imgDir.mkdirs()) {
            throw new IOException("无法创建模板目录");
        }
        return taskDir;
    }

    /**
     * 这里是加载project 可以优化不用每次都加载 读取io
     */
    private void loadProjects() {
        loadProjects(false);
    }

    private void loadProjects(boolean forceReload) {
        if (projectPanelView == null) return;
        RecyclerView rv = getProjectPanelRecyclerView();
        if (rv == null) return;
        
        // 优化RecyclerView性能
        if (!(rv.getLayoutManager() instanceof LinearLayoutManager)
                || rv.getLayoutManager() instanceof GridLayoutManager) {
            RecyclerViewOptimizer.optimizeForProjectList(rv, this);
        }

        File root = getProjectsRootDir();
        long version = root.lastModified();
        if (!forceReload && projectListCacheVersion == version) {
            renderProjectItems(filterProjectItems(projectListCache, currentSearchQuery));
            return;
        }

        new Thread(() -> {
            List<ProjectListItem> items = new ArrayList<>();
            File[] dirs = root.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    File[] taskDirs = dir.listFiles(File::isDirectory);
                    int taskCount = taskDirs == null ? 0 : taskDirs.length;
                    items.add(new ProjectListItem(dir, taskCount, dir.lastModified()));
                }
            }
            Collections.sort(items, (left, right) -> {
                int byTime = Long.compare(right.lastModified, left.lastModified);
                if (byTime != 0) {
                    return byTime;
                }
                return left.dir.getName().compareToIgnoreCase(right.dir.getName());
            });

            long resolvedVersion = root.lastModified();
            uiHandler.post(() -> {
                if (projectPanelView == null) return;
                projectListCache.clear();
                projectListCache.addAll(items);
                projectListCacheVersion = resolvedVersion;
                renderProjectItems(filterProjectItems(projectListCache, currentSearchQuery));
            });
        }, "project-panel-load").start();
    }

    private void loadProjectsLegacy() {
        if (projectPanelView == null) return;
        RecyclerView rv = projectPanelView.findViewById(R.id.rv_content);
        rv.setLayoutManager(new GridLayoutManager(this, 2));

        // 快照搜索词，后台线程只读不写
        final String querySnap = currentSearchQuery;
        android.os.Handler mainH = new android.os.Handler(android.os.Looper.getMainLooper());

        new Thread(() -> {
            File root = new File(getExternalFilesDir(null), "projects");
            if (!root.exists()) root.mkdirs();

            List<File>    allDirs = new ArrayList<>();
            List<Project> cache   = new ArrayList<>();

            File[] dirs = root.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File d : dirs) {
                    allDirs.add(d);
                    Project project = new Project();
                    project.setProjectName(d.getName());
                    File[] taskFiles = d.listFiles();
                    if (taskFiles != null) {
                        for (File taskFile : taskFiles) {
                            Task task = new Task();
                            task.setName(taskFile.getName());
                            task.setId(taskFile.getName());
                            project.getTaskMap().put(task.getName(), task);
                            File json = new File(taskFile, "operations.json");
                            String content = "";
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                try {
                                    content = new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8);
                                } catch (Exception ignored) {}
                            }
                            for (MetaOperation mop : OperationGsonUtils.fromJson(content)) {
                                if (mop.getId() != null) task.putOperation(mop);
                            }
                        }
                    }
                    cache.add(project);
                }
            }

            // 过滤搜索
            List<File> result = allDirs;
            if (!TextUtils.isEmpty(querySnap.trim())) {
                String q = querySnap.trim().toLowerCase(Locale.ROOT);
                List<File> filtered = new ArrayList<>();
                for (File f : allDirs) {
                    if (f.getName().toLowerCase(Locale.ROOT).contains(q)) filtered.add(f);
                }
                result = filtered;
            }

            final List<File>    finalResult = result;
            final List<Project> finalCache  = cache;

            mainH.post(() -> {
                if (projectPanelView == null) return; // 面板已关闭，跳过
                cachedProjects = finalCache;
                ProjectPanelAdapter adapter = new ProjectPanelAdapter(
                        new ArrayList<>(),
                        item -> {
                        },
                        null);
                rv.setAdapter(adapter);
                updateEmptyView(finalResult.isEmpty(), "点击右上角 + 创建项目");
            });
        }).start();
    }

    /**
     * 这里是加载 task 可以缓存优化 不用每次都io
     *
     * @param projectDir
     */
    private void loadTasks(File projectDir) {
        loadTasks(projectDir, false);
    }

    private void loadTasks(File projectDir, boolean forceReload) {
        if (projectDir == null || projectPanelView == null) return;
        RecyclerView rv = getProjectPanelRecyclerView();
        if (rv == null) return;
        
        // 优化RecyclerView性能
        if (!(rv.getLayoutManager() instanceof LinearLayoutManager)
                || rv.getLayoutManager() instanceof GridLayoutManager) {
            RecyclerViewOptimizer.optimizeForProjectList(rv, this);
        }

        long version = projectDir.lastModified();
        String projectKey = buildFileCacheKey(projectDir);
        if (!forceReload
                && projectDir.equals(taskListCacheProjectDir)
                && taskListCacheVersion == version) {
            renderTaskItems(filterTaskItems(taskListCache, currentSearchQuery));
            return;
        }

        List<File> cachedItems = forceReload ? null : taskItemsMemoryCache.get(projectKey);
        Long cachedVersion = forceReload ? null : taskItemsMemoryVersions.get(projectKey);
        if (cachedItems != null && cachedVersion != null && cachedVersion == version) {
            taskListCacheProjectDir = projectDir;
            taskListCacheVersion = version;
            taskListCache.clear();
            taskListCache.addAll(cachedItems);
            renderTaskItems(filterTaskItems(taskListCache, currentSearchQuery));
            return;
        }

        List<File> items = readTaskItems(projectDir);

        taskListCacheProjectDir = projectDir;
        taskListCacheVersion = version;
        taskListCache.clear();
        taskListCache.addAll(items);
        taskItemsMemoryCache.put(projectKey, new ArrayList<>(items));
        taskItemsMemoryVersions.put(projectKey, version);
        renderTaskItems(filterTaskItems(taskListCache, currentSearchQuery));
    }

    private void loadTasksLegacy(File projectDir) {
        RecyclerView rv = projectPanelView.findViewById(R.id.rv_content);
        rv.setLayoutManager(new LinearLayoutManager(this));
        List<File> items = new ArrayList<>();
        File[] dirs = projectDir.listFiles(File::isDirectory);
        if (dirs != null) for (File d : dirs) items.add(d);

        File[] files = projectDir.listFiles(f -> f.isFile() && !f.getName().startsWith("."));
        if (files != null) for (File f : files) items.add(f);


        if (!TextUtils.isEmpty(currentSearchQuery.trim())) {
            String q = currentSearchQuery.trim().toLowerCase(Locale.ROOT);
            List<File> filtered = new ArrayList<>();
            for (File item : items) {
                if (item.getName().toLowerCase(Locale.ROOT).contains(q)) {
                    filtered.add(item);
                }
            }
            items = filtered;
        }

        TaskPanelAdapter adapter = new TaskPanelAdapter(
                items,
                file -> {
                    if (file.isDirectory()) {
                        currentTaskDir = file;
                        currentLevel = NavigationLevel.OPERATION;
                        currentSearchQuery = "";
                        loadOperations(file);
                        updateUIForLevel();
                    } else {
                        Toast.makeText(this, "文件: " + file.getName(), Toast.LENGTH_SHORT).show();
                    }
                },
                this::showTaskActionMenu);
        rv.setAdapter(adapter);
        updateEmptyView(items.isEmpty(), "点击右上角 + 创建 Task");
    }

    private void updateEmptyView(boolean isEmpty, String hint) {
        if (projectPanelView == null) return;
        View emptyView = projectPanelView.findViewById(R.id.empty_view);
        View rvContent = projectPanelView.findViewById(R.id.rv_content);
        android.widget.TextView tvEmptyHint = projectPanelView.findViewById(R.id.tv_empty_hint);
        if (emptyView == null || rvContent == null) return;
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvContent.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (tvEmptyHint != null && hint != null) {
            tvEmptyHint.setText(hint);
        }
    }

    private void loadOperations(File taskDir) {
        loadOperations(taskDir, false);
    }

    private void loadOperations(File taskDir, boolean forceReload) {
        if (taskDir == null || projectPanelView == null) return;
        RecyclerView rv = getProjectPanelRecyclerView();
        if (rv == null) return;
        
        // 优化RecyclerView性能（操作列表使用独立配置）
        if (!(rv.getLayoutManager() instanceof LinearLayoutManager)
                || rv.getLayoutManager() instanceof GridLayoutManager) {
            RecyclerViewOptimizer.optimizeForOperationList(rv, this);
        }
        if (operationDivider != null) {
            rv.removeItemDecoration(operationDivider);
            operationDivider = null;
        }

        File json = new File(taskDir, "operations.json");
        long version = json.exists() ? json.lastModified() : Long.MIN_VALUE;
        String taskKey = buildFileCacheKey(taskDir);
        boolean shouldAnimate = !TextUtils.equals(lastRenderedOperationTaskKey, taskKey)
                && TextUtils.isEmpty(normalizeQuery(currentSearchQuery));
        rv.setLayoutAnimation(shouldAnimate
                ? android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.op_layout_enter)
                : null);
        List<OperationItem> allOperations;
        if (!forceReload
                && taskDir.equals(operationListCacheTaskDir)
                && operationListCacheVersion == version) {
            allOperations = new ArrayList<>(operationListCache);
        } else if (!forceReload
                && operationItemsMemoryCache.containsKey(taskKey)
                && operationItemsMemoryVersions.containsKey(taskKey)
                && operationItemsMemoryVersions.get(taskKey) == version) {
            allOperations = new ArrayList<>(operationItemsMemoryCache.get(taskKey));
            operationListCacheTaskDir = taskDir;
            operationListCacheVersion = version;
            operationListCache.clear();
            operationListCache.addAll(allOperations);
        } else {
            allOperations = readOperationItems(taskDir);
            operationListCacheTaskDir = taskDir;
            operationListCacheVersion = version;
            operationListCache.clear();
            operationListCache.addAll(allOperations);
            operationItemsMemoryCache.put(taskKey, new ArrayList<>(allOperations));
            operationItemsMemoryVersions.put(taskKey, version);
        }

        if (allOperations.isEmpty()) {
            List<OperationItem> fallbackOperations = readOperationItemsDirect(taskDir);
            if (!fallbackOperations.isEmpty()) {
                allOperations = fallbackOperations;
                operationListCacheTaskDir = taskDir;
                operationListCacheVersion = version;
                operationListCache.clear();
                operationListCache.addAll(allOperations);
                operationItemsMemoryCache.put(taskKey, new ArrayList<>(allOperations));
                operationItemsMemoryVersions.put(taskKey, version);
            }
        }

        List<OperationItem> operations = filterOperationItems(allOperations, currentSearchQuery);
        ensureProjectPanelAdapters();
        switchProjectPanelAdapter(operationPanelAdapter);
        attachOperationDragHelperIfNeeded(rv);
        // 在 submitOperations 之前静默写入颜色映射，保证 DiffUtil 绑定时数据已就绪
        operationPanelAdapter.initFloatBtnColors(buildFloatBtnColorMap());
        operationPanelAdapter.submitOperations(operations);
        operationPanelAdapter.setBatchMode(operationBatchMode);
        operationPanelAdapter.setBatchSelectedIds(batchSelectedOperationIds);
        updateEmptyView(operations.isEmpty(), "点击右上角 + 添加操作");
        if (!TextUtils.isEmpty(pendingSelectedOperationId)) {
            operationPanelAdapter.selectById(pendingSelectedOperationId);
            pendingSelectedOperationId = null;
        }
        this.currentOperationAdapter = operationPanelAdapter;
        if (!TextUtils.isEmpty(currentRunningOperationId)) {
            operationPanelAdapter.setRunningPosition(currentRunningOperationId);
        }
        lastRenderedOperationTaskKey = taskKey;
        projectPanelContentDirty = false;
        syncProjectPanelRuntimeUi();
    }

    private void loadOperationsLegacy(File taskDir) {
        RecyclerView rv = projectPanelView.findViewById(R.id.rv_content);
        rv.setLayoutManager(new LinearLayoutManager(this));
        // 移除旧 divider（卡片式 item 自带间距，不需要分割线）
        if (operationDivider != null) {
            rv.removeItemDecoration(operationDivider);
            operationDivider = null;
        }
        // 列表进入动画
        rv.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(
                this, R.anim.op_layout_enter));

        List<OperationItem> operations = new ArrayList<>();
        File json = new File(taskDir, "operations.json");
        if (json.exists()) {
            try {
                // 优先从缓存获取，如果没有缓存则直接从文件加载
                Project project = null;
                for(Project p: this.cachedProjects){
                    if(p.getProjectName().equals(currentProjectDir.getName())){
                        project = p;
                        break;
                    }
                }
                
                // 如果缓存中没有，直接从文件加载
                if (project == null) {
                    String content = new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8);
                    List<MetaOperation> metaOperations = OperationGsonUtils.fromJson(content);
                    int i = 0;
                    for (MetaOperation op : metaOperations) {
                        operations.add(new OperationItem(op.getName(), op.getId(), getOperationTypeName(op.getType()), i,
                                extractDelayDurationMs(op), extractDelayShowCountdown(op)));
                        i++;
                    }
                } else {
                    // 从缓存获取
                    Map<String, Task> taskMap = project.getTaskMap();
                    Task task = taskMap.get(currentTaskDir.getName());
                    if (task != null) {
                        Map<String, MetaOperation> operationMap = task.getOperationMap();
                        int i = 0;
                        for (MetaOperation value : operationMap.values()) {
                            operations.add(new OperationItem(value.getName(), value.getId(), getOperationTypeName(value.getType()), i,
                                    extractDelayDurationMs(value), extractDelayShowCountdown(value)));
                            i++;
                        }
                    }

                    // 缓存里没有该 task（常见于新建 task 后），回退到文件读取
                    if (operations.isEmpty()) {
                        String content = new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8);
                        List<MetaOperation> metaOperations = OperationGsonUtils.fromJson(content);
                        int i = 0;
                        for (MetaOperation op : metaOperations) {
                            operations.add(new OperationItem(op.getName(), op.getId(), getOperationTypeName(op.getType()), i,
                                    extractDelayDurationMs(op), extractDelayShowCountdown(op)));
                            i++;
                        }
                    }
                }
            } catch (Exception e) {
            Log.e(TAG, "加载 operations 失败", e);
        }
    }

//        OperationPanelAdapter adapter = new OperationPanelAdapter(operations, operation -> {
//            Toast.makeText(this, "Operation: " + operation.name, Toast.LENGTH_SHORT).show();
//        });
        /*
        这里需要保存 adapter的引用 以便后面能拿到
         */
        if (!TextUtils.isEmpty(currentSearchQuery.trim())) {
            String q = currentSearchQuery.trim().toLowerCase(Locale.ROOT);
            List<OperationItem> filtered = new ArrayList<>();
            for (OperationItem operationItem : operations) {
                if ((operationItem.name != null && operationItem.name.toLowerCase(Locale.ROOT).contains(q))
                        || (operationItem.id != null && operationItem.id.toLowerCase(Locale.ROOT).contains(q))
                        || (operationItem.type != null && operationItem.type.toLowerCase(Locale.ROOT).contains(q))) {
                    filtered.add(operationItem);
                }
            }
            operations = filtered;
        }

        OperationPanelAdapter adapter = new OperationPanelAdapter(operations,
                operation -> {
                },
                new OperationPanelAdapter.OnActionListener() {
                    @Override
                    public void onEdit(OperationItem item) {
                        showEditOperationDialog(item, currentOperationAdapter);
                    }

                    @Override
                    public void onCopy(OperationItem item) {
                        copyOperationToClipboard(item.id);
                    }

                    @Override
                    public void onPasteAfter(OperationItem item) {
                        pasteOperationRelative(item.id, false);
                    }

                    @Override
                    public void onInsertBefore(OperationItem item) {
                        pasteOperationRelative(item.id, true);
                    }

                    @Override
                    public void onDelete(OperationItem item) {
                        deleteOperation(item.id);
                    }

                    @Override
                    public void onMoveUp(OperationItem item) {
                        moveOperation(item.id, -1);
                    }

                    @Override
                    public void onMoveDown(OperationItem item) {
                        moveOperation(item.id, 1);
                    }

                    @Override
                    public boolean canPaste() {
                        return hasOperationClipboard();
                    }

                    @Override
                    public void onFloatButton(OperationItem item) {
                        showNodeFloatBtnConfig(item);
                    }
                },
                selectedIds -> {
                    batchSelectedOperationIds.clear();
                    batchSelectedOperationIds.addAll(selectedIds);
                    updateBatchActionCount();
                });
        adapter.setBatchMode(operationBatchMode);
        adapter.setBatchSelectedIds(batchSelectedOperationIds);
        rv.setAdapter(adapter);
        updateEmptyView(operations.isEmpty(), "点击右上角 + 添加操作");
        if (!TextUtils.isEmpty(pendingSelectedOperationId)) {
            adapter.selectById(pendingSelectedOperationId);
            pendingSelectedOperationId = null;
        }
//        todo 关键 后台动态修改正在运行的 operation的 关键
        this.currentOperationAdapter = adapter;

        // 假设你正在执行第 index 个 operation
//        private void onOperationStart(int index) {
//            if (operationAdapter != null) {
//                operationAdapter.setRunningPosition(index);
//                 operationAdapter.setRunningPosition(index);
//                  todo  这里改成 operation的 id更好
//            }
//        }
//
//        private void onOperationFinish(int index) {
//            if (operationAdapter != null) {
//                operationAdapter.clearRunningPosition();  // 或只在全部完成时清空
//            }
//        }
    }

    private void preloadProjectDataAsync() {
        new Thread(() -> {
            File root = getProjectsRootDir();
            File[] dirs = root.listFiles(File::isDirectory);
            List<ProjectListItem> items = new ArrayList<>();
            List<Project> warmedProjects = new ArrayList<>();
            Map<String, List<File>> warmedTaskItems = new HashMap<>();
            Map<String, Long> warmedTaskVersions = new HashMap<>();
            Map<String, List<OperationItem>> warmedOperationItems = new HashMap<>();
            Map<String, Long> warmedOperationVersions = new HashMap<>();
            if (dirs != null) {
                for (File dir : dirs) {
                    List<File> taskItems = readTaskItems(dir);
                    int taskCount = 0;
                    for (File item : taskItems) {
                        if (item.isDirectory()) {
                            taskCount++;
                        }
                    }
                    items.add(new ProjectListItem(dir, taskCount, dir.lastModified()));
                    warmedTaskItems.put(buildFileCacheKey(dir), new ArrayList<>(taskItems));
                    warmedTaskVersions.put(buildFileCacheKey(dir), dir.lastModified());
                    Project project = loadProjectFromDir(dir);
                    if (project != null) {
                        warmedProjects.add(project);
                    }
                    Map<String, Task> taskMap = project == null ? null : project.getTaskMap();
                    for (File item : taskItems) {
                        if (!item.isDirectory()) {
                            continue;
                        }
                        Task task = taskMap == null ? null : taskMap.get(item.getName());
                        File opFile = new File(item, "operations.json");
                        long opVersion = opFile.exists() ? opFile.lastModified() : Long.MIN_VALUE;
                        List<OperationItem> opItems = task == null
                                ? new ArrayList<>()
                                : buildOperationItemsFromTask(task);
                        if (opItems.isEmpty()) {
                            opItems = readOperationItemsDirect(item);
                        }
                        warmedOperationItems.put(buildFileCacheKey(item), new ArrayList<>(opItems));
                        warmedOperationVersions.put(buildFileCacheKey(item), opVersion);
                    }
                }
            }
            Collections.sort(items, (left, right) -> {
                int byTime = Long.compare(right.lastModified, left.lastModified);
                if (byTime != 0) {
                    return byTime;
                }
                return left.dir.getName().compareToIgnoreCase(right.dir.getName());
            });

            long version = root.lastModified();
            uiHandler.post(() -> {
                projectListCache.clear();
                projectListCache.addAll(items);
                projectListCacheVersion = version;
                cachedProjects.clear();
                cachedProjects.addAll(warmedProjects);
                taskItemsMemoryCache.clear();
                taskItemsMemoryCache.putAll(warmedTaskItems);
                taskItemsMemoryVersions.clear();
                taskItemsMemoryVersions.putAll(warmedTaskVersions);
                operationItemsMemoryCache.clear();
                operationItemsMemoryCache.putAll(warmedOperationItems);
                operationItemsMemoryVersions.clear();
                operationItemsMemoryVersions.putAll(warmedOperationVersions);
                if (isProjectPanelAttached() && currentLevel == NavigationLevel.PROJECT) {
                    renderProjectItems(filterProjectItems(projectListCache, currentSearchQuery));
                }
            });
        }, "project-preload").start();
    }

    private void renderProjectItems(List<ProjectListItem> items) {
        RecyclerView rv = getProjectPanelRecyclerView();
        if (rv == null) return;
        ensureProjectPanelAdapters();
        rv.setLayoutAnimation(null);
        switchProjectPanelAdapter(projectPanelAdapter);
        projectPanelAdapter.submitProjects(items);
        updateEmptyView(items.isEmpty(), "点击右上角 + 创建项目");
        projectPanelContentDirty = false;
    }

    private void renderTaskItems(List<File> items) {
        RecyclerView rv = getProjectPanelRecyclerView();
        if (rv == null) return;
        ensureProjectPanelAdapters();
        rv.setLayoutAnimation(null);
        switchProjectPanelAdapter(taskPanelAdapter);
        taskPanelAdapter.submitItems(items);
        updateEmptyView(items.isEmpty(), "点击右上角 + 创建 Task");
        projectPanelContentDirty = false;
    }

    private RecyclerView getProjectPanelRecyclerView() {
        if (projectPanelRecyclerView == null && projectPanelView != null) {
            projectPanelRecyclerView = projectPanelView.findViewById(R.id.rv_content);
        }
        return projectPanelRecyclerView;
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private List<ProjectListItem> filterProjectItems(List<ProjectListItem> source, String query) {
        String normalized = normalizeQuery(query);
        if (normalized.isEmpty()) {
            return new ArrayList<>(source);
        }
        List<ProjectListItem> fallback = new ArrayList<>();
        for (ProjectListItem item : source) {
            if (item != null && item.dir.getName().toLowerCase(Locale.ROOT).contains(normalized)) {
                fallback.add(item);
            }
        }
        return fallback;
    }

    private List<File> filterTaskItems(List<File> source, String query) {
        String normalized = normalizeQuery(query);
        if (normalized.isEmpty()) {
            return new ArrayList<>(source);
        }
        List<File> fallback = new ArrayList<>();
        for (File item : source) {
            if (item.getName().toLowerCase(Locale.ROOT).contains(normalized)) {
                fallback.add(item);
            }
        }
        return fallback;
    }

    private List<OperationItem> filterOperationItems(List<OperationItem> source, String query) {
        String normalized = normalizeQuery(query);
        if (normalized.isEmpty()) {
            return new ArrayList<>(source);
        }
        List<OperationItem> filtered = new ArrayList<>();
        for (OperationItem item : source) {
            if ((item.name != null && item.name.toLowerCase(Locale.ROOT).contains(normalized))
                    || (item.id != null && item.id.toLowerCase(Locale.ROOT).contains(normalized))
                    || (item.type != null && item.type.toLowerCase(Locale.ROOT).contains(normalized))) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private List<OperationItem> readOperationItems(File taskDir) {
        List<OperationItem> operations = new ArrayList<>();
        if (currentProjectDir != null) {
            Project cachedProject = findCachedProjectByName(currentProjectDir.getName());
            if (cachedProject != null && cachedProject.getTaskMap() != null) {
                Task task = cachedProject.getTaskMap().get(taskDir.getName());
                if (task != null && task.getOperationMap() != null && !task.getOperationMap().isEmpty()) {
                    int index = 0;
                    for (MetaOperation op : task.getOperationMap().values()) {
                        if (op == null) {
                            continue;
                        }
                        operations.add(new OperationItem(
                                op.getName(),
                                op.getId(),
                                getOperationTypeName(op.getType()),
                                index++,
                                extractDelayDurationMs(op),
                                extractDelayShowCountdown(op)));
                    }
                    if (!operations.isEmpty()) {
                        return operations;
                    }
                }
            }
        }

        return readOperationItemsDirect(taskDir);
    }

    private List<OperationItem> readOperationItemsDirect(File taskDir) {
        List<OperationItem> operations = new ArrayList<>();
        File jsonFile = new File(taskDir, "operations.json");
        if (!jsonFile.exists()) {
            return operations;
        }
        try {
            // 使用 JSONArray 直接解析，与流程图面板保持一致，避免 release 下
            // R8 对 Gson TypeToken 泛型擦除导致反序列化静默失败返回空列表的问题。
            String content = readFileContent(jsonFile);
            if (TextUtils.isEmpty(content)) {
                return operations;
            }
            JSONArray array = new JSONArray(content);
            for (int i = 0; i < array.length(); i++) {
                JSONObject op = array.optJSONObject(i);
                if (op == null) {
                    continue;
                }
                String id = op.optString("id", "");
                if (TextUtils.isEmpty(id)) {
                    continue;
                }
                String name = op.optString("name", "未命名");
                int typeInt = op.optInt("type", -1);
                String typeName = getOperationTypeName(typeInt);
                long delayMs = 0L;
                boolean showCountdown = false;
                if (typeInt == OperationType.DELAY.getCode()) {
                    JSONObject inputMap = op.optJSONObject("inputMap");
                    if (inputMap != null) {
                        delayMs = inputMap.optLong(MetaOperation.SLEEP_DURATION, 0L);
                        showCountdown = inputMap.optBoolean(MetaOperation.DELAY_SHOW_COUNTDOWN, false);
                    }
                }
                operations.add(new OperationItem(name, id, typeName, i, delayMs, showCountdown));
            }
        } catch (Exception e) {
            Log.e(TAG, "load operations failed", e);
        }
        return operations;
    }

    private String readFileContent(File file) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            }
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "readFileContent failed: " + file.getAbsolutePath(), e);
            return "";
        }
    }

    private void clearProjectPanelSearch() {
        currentSearchQuery = "";
        if (projectPanelView == null) {
            return;
        }
        EditText edtSearch = projectPanelView.findViewById(R.id.edt_search);
        if (edtSearch != null && edtSearch.length() > 0) {
            uiHandler.removeCallbacks(searchRefreshRunnable);
            edtSearch.setText("");
        }
    }

    private void invalidateProjectListCache() {
        projectListCacheVersion = Long.MIN_VALUE;
        projectListCache.clear();
        projectPanelContentDirty = true;
    }

    private void invalidateTaskListCache(@Nullable File projectDir) {
        projectPanelContentDirty = true;
        if (projectDir == null) {
            taskItemsMemoryCache.clear();
            taskItemsMemoryVersions.clear();
            taskListCacheProjectDir = null;
            taskListCacheVersion = Long.MIN_VALUE;
            taskListCache.clear();
            return;
        }
        taskItemsMemoryCache.remove(buildFileCacheKey(projectDir));
        taskItemsMemoryVersions.remove(buildFileCacheKey(projectDir));
        if (projectDir.equals(taskListCacheProjectDir)) {
            taskListCacheProjectDir = null;
            taskListCacheVersion = Long.MIN_VALUE;
            taskListCache.clear();
        }
    }

    private void invalidateOperationListCache(@Nullable File taskDir) {
        projectPanelContentDirty = true;
        if (taskDir == null) {
            operationItemsMemoryCache.clear();
            operationItemsMemoryVersions.clear();
            operationListCacheTaskDir = null;
            operationListCacheVersion = Long.MIN_VALUE;
            operationListCache.clear();
            return;
        }
        operationItemsMemoryCache.remove(buildFileCacheKey(taskDir));
        operationItemsMemoryVersions.remove(buildFileCacheKey(taskDir));
        if (taskDir.equals(operationListCacheTaskDir)) {
            operationListCacheTaskDir = null;
            operationListCacheVersion = Long.MIN_VALUE;
            operationListCache.clear();
        }
    }

    private void confirmDeleteProject(ProjectListItem item) {
        if (item == null || item.dir == null) {
            return;
        }
        showOverlayConfirmDialog(
                "删除项目",
                "确定删除项目 \"" + item.dir.getName() + "\" 吗？",
                () -> deleteProject(item.dir));
    }

    private void showProjectActionMenu(ProjectListItem item, View anchor) {
        if (item == null || item.dir == null || anchor == null) {
            return;
        }
        View popupView = LayoutInflater.from(this).inflate(R.layout.dialog_node_action_sheet, null);
        TextView tvTitle = popupView.findViewById(R.id.tv_action_title);
        RecyclerView rvActions = popupView.findViewById(R.id.rv_action_list);
        if (tvTitle != null) {
            tvTitle.setText(item.dir.getName());
        }
        rvActions.setLayoutManager(new LinearLayoutManager(this));

        List<OperationPanelAdapter.ActionItem> actionItems = new ArrayList<>();
        actionItems.add(new OperationPanelAdapter.ActionItem(1, "导出项目", "打包成 zip 文件并分享", true));
        actionItems.add(new OperationPanelAdapter.ActionItem(2, "重命名项目", "修改当前项目名称", true));
        actionItems.add(new OperationPanelAdapter.ActionItem(3, "删除项目", "从本地删除该项目", true));

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(10f);

        rvActions.setAdapter(new OperationPanelAdapter.ActionSheetAdapter(actionItems, action -> {
            popupWindow.dismiss();
            if (action.id == 1) {
                exportProjectAsync(item.dir);
            } else if (action.id == 2) {
                promptRenameProject(item.dir);
            } else if (action.id == 3) {
                confirmDeleteProject(item);
            }
        }));
        popupWindow.showAsDropDown(anchor, -dp(160), dp(4), Gravity.END);
    }

    private void exportProjectAsync(File projectDir) {
        if (projectDir == null) {
            return;
        }
        Toast.makeText(this, "正在导出项目...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File zipFile = ScriptPackageManager.exportProject(this, projectDir.getName());
                Uri uri = FileProvider.getUriForFile(
                        this, getPackageName() + ".fileprovider", zipFile);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/zip");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, projectDir.getName() + ".zip");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(shareIntent, "分享项目: " + projectDir.getName());
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                uiHandler.post(() -> startActivity(chooser));
            } catch (Exception e) {
                uiHandler.post(() -> Toast.makeText(this,
                        "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void importProjectInteractive() {
        Intent intent = new Intent(this, ProjectImportPickerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void confirmDeleteTask(File taskDir) {
        if (taskDir == null || !taskDir.isDirectory()) {
            return;
        }
        showOverlayConfirmDialog(
                "删除 Task",
                "确定删除 Task \"" + taskDir.getName() + "\" 吗？",
                () -> deleteTask(taskDir));
    }

    private void showTaskActionMenu(File taskDir, View anchor) {
        if (taskDir == null || !taskDir.isDirectory() || anchor == null) {
            return;
        }
        ensureTaskActionPopup();
        if (taskActionPopupWindow == null || taskActionPopupTitleView == null || taskActionSheetAdapter == null) {
            return;
        }
        taskActionPopupTitleView.setText(taskDir.getName());
        taskActionSheetItems.clear();
        taskActionSheetItems.add(new OperationPanelAdapter.ActionItem(1, "打开 Task", "进入这个 Task 的节点列表", true));
        taskActionSheetItems.add(new OperationPanelAdapter.ActionItem(2, "模板库", "管理当前 Task 的模板截图", true));
        taskActionSheetItems.add(new OperationPanelAdapter.ActionItem(3, "重命名 Task", "修改当前 Task 名称", true));
        taskActionSheetItems.add(new OperationPanelAdapter.ActionItem(4, "删除 Task", "从本地删除这个 Task", true));
        taskActionSheetHandler = action -> {
            if (action == null || !action.enabled) {
                return;
            }
            switch (action.id) {
                case 1:
                    currentTaskDir = taskDir;
                    currentLevel = NavigationLevel.OPERATION;
                    clearProjectPanelSearch();
                    loadOperations(taskDir);
                    updateUIForLevel();
                    break;
                case 2:
                    showTaskTemplateLibraryManagerDialog(taskDir);
                    break;
                case 3:
                    promptRenameTask(taskDir);
                    break;
                case 4:
                    confirmDeleteTask(taskDir);
                    break;
                default:
                    break;
            }
        };
        taskActionSheetAdapter.notifyDataSetChanged();
        if (taskActionPopupWindow.isShowing()) {
            taskActionPopupWindow.dismiss();
        }
        taskActionPopupWindow.showAsDropDown(anchor, -dp(180), dp(4), Gravity.END);
    }

    private void ensureTaskActionPopup() {
        if (taskActionPopupWindow != null) {
            return;
        }
        View popupView = LayoutInflater.from(this).inflate(R.layout.dialog_node_action_sheet, null);
        taskActionPopupTitleView = popupView.findViewById(R.id.tv_action_title);
        taskActionPopupListView = popupView.findViewById(R.id.rv_action_list);
        if (taskActionPopupListView != null) {
            taskActionPopupListView.setLayoutManager(new LinearLayoutManager(this));
            taskActionSheetAdapter = new OperationPanelAdapter.ActionSheetAdapter(taskActionSheetItems, action -> {
                if (action == null || !action.enabled) {
                    return;
                }
                if (taskActionPopupWindow != null) {
                    taskActionPopupWindow.dismiss();
                }
                if (taskActionSheetHandler != null) {
                    taskActionSheetHandler.accept(action);
                }
            });
            taskActionPopupListView.setAdapter(taskActionSheetAdapter);
        }
        taskActionPopupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        taskActionPopupWindow.setOutsideTouchable(true);
        taskActionPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        taskActionPopupWindow.setElevation(10f);
    }

    private void showTaskTemplateLibraryManagerDialog(File taskDir) {
        if (taskDir == null || !taskDir.isDirectory()) {
            Toast.makeText(this, "当前 Task 无效", Toast.LENGTH_SHORT).show();
            return;
        }
        currentTaskDir = taskDir;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_template_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        applyTemplateLibraryDialogViewport(dialogLp);
        wm.addView(dialogView, dialogLp);

        TextView tvTitle = dialogView.findViewById(R.id.tv_library_title);
        TextView btnAdd = dialogView.findViewById(R.id.btn_library_add);
        RecyclerView rv = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch = dialogView.findViewById(R.id.edt_library_search);
        TextView btnBatch = dialogView.findViewById(R.id.btn_library_batch);
        TextView btnDelete = dialogView.findViewById(R.id.btn_library_delete);
        View selectActions = dialogView.findViewById(R.id.ly_library_select_actions);

        if (tvTitle != null) {
            tvTitle.setText("模板库");
        }
        if (btnAdd != null) {
            btnAdd.setVisibility(View.VISIBLE);
        }
        if (selectActions != null) {
            selectActions.setVisibility(View.GONE);
        }

        rv.setLayoutManager(new GridLayoutManager(this, 3));
        final boolean[] batchMode = {false};
        final TemplateLibraryAdapter[] adapterRef = new TemplateLibraryAdapter[1];
        final Runnable[] refreshLibrary = new Runnable[1];

        TemplateLibraryAdapter adapter = new TemplateLibraryAdapter(
                getCurrentTaskTemplateLibraryItems(),
                item -> showTaskTemplateItemActionDialog(taskDir, item, dialogView, refreshLibrary[0]),
                item -> {
                    if (item == null || TextUtils.isEmpty(item.fileName)) {
                        return;
                    }
                    currentTaskDir = taskDir;
                    int deleted = deleteTemplateFiles(Collections.singleton(item.fileName));
                    if (deleted <= 0) {
                        Toast.makeText(this, "模板仍被引用或删除失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, "已删除模板 " + item.fileName, Toast.LENGTH_SHORT).show();
                    refreshTemplateCachesForCurrentProject();
                    if (refreshLibrary[0] != null) {
                        refreshLibrary[0].run();
                    }
                });
        adapterRef[0] = adapter;
        adapter.setDeleteActionEnabled(true);
        rv.setAdapter(adapter);

        refreshLibrary[0] = () -> {
            currentTaskDir = taskDir;
            if (adapterRef[0] != null) {
                adapterRef[0].replaceData(getCurrentTaskTemplateLibraryItems());
                if (adapterRef[0].getItemCount() == 0) {
                    batchMode[0] = false;
                    adapterRef[0].setBatchMode(false);
                }
                btnDelete.setText("删除(" + adapterRef[0].getSelectedCount() + ")");
                btnBatch.setText(batchMode[0] ? "完成" : "批量");
            }
        };
        adapter.setSelectionChangedListener(count -> btnDelete.setText("删除(" + count + ")"));
        refreshLibrary[0].run();

        dialogView.findViewById(R.id.btn_library_close).setOnClickListener(v -> safeRemoveView(dialogView));
        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> {
                safeRemoveView(dialogView);
                showNameInputDialog(
                        "新增模板",
                        "输入模板文件名",
                        generateTemplateTimestampName(),
                        name -> {
                            currentTaskDir = taskDir;
                            launchTemplateCaptureAfterUiSettled(name);
                        });
            });
        }
        btnBatch.setOnClickListener(v -> {
            batchMode[0] = !batchMode[0];
            adapter.setBatchMode(batchMode[0]);
            btnBatch.setText(batchMode[0] ? "完成" : "批量");
            btnDelete.setText("删除(" + adapter.getSelectedCount() + ")");
        });
        btnDelete.setOnClickListener(v -> {
            currentTaskDir = taskDir;
            Set<String> selected = adapter.getSelectedFileNames();
            if (selected.isEmpty()) {
                Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show();
                return;
            }
            int deleted = deleteTemplateFiles(selected);
            int skipped = selected.size() - deleted;
            if (skipped > 0) {
                Toast.makeText(this, "已删 " + deleted + " 张，" + skipped + " 张仍被节点引用", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已删除 " + deleted + " 张图片", Toast.LENGTH_SHORT).show();
            }
            refreshTemplateCachesForCurrentProject();
            refreshLibrary[0].run();
        });
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.updateFilter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void showTaskTemplateItemActionDialog(File taskDir,
                                                  TemplateLibraryAdapter.TemplateLibraryItem item,
                                                  @Nullable View managerDialog,
                                                  @Nullable Runnable onChanged) {
        if (taskDir == null || item == null || TextUtils.isEmpty(item.fileName)) {
            return;
        }
        String[] actions = new String[]{"替换截图", "重命名模板", "删除模板"};
        android.view.ContextThemeWrapper ctx =
                new android.view.ContextThemeWrapper(this, R.style.Theme_AtomMaster);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle(item.fileName)
                .setItems(actions, (d, which) -> {
                    currentTaskDir = taskDir;
                    if (which == 0) {
                        safeRemoveView(managerDialog);
                        launchTemplateCaptureAfterUiSettled(item.fileName);
                        return;
                    }
                    if (which == 1) {
                        if (item.usageCount > 0) {
                            Toast.makeText(this, "模板仍被节点引用，暂不支持重命名", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showNameInputDialog(
                                "重命名模板",
                                "输入新的模板文件名",
                                item.fileName,
                                newName -> {
                                    currentTaskDir = taskDir;
                                    if (renameTemplateFile(taskDir, item.fileName, normalizeTemplateFileName(newName))) {
                                        Toast.makeText(this, "模板已重命名", Toast.LENGTH_SHORT).show();
                                        refreshTemplateCachesForCurrentProject();
                                        if (onChanged != null) {
                                            onChanged.run();
                                        }
                                    } else {
                                        Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                                    }
                                });
                        return;
                    }
                    int deleted = deleteTemplateFiles(Collections.singleton(item.fileName));
                    if (deleted > 0) {
                        Toast.makeText(this, "已删除模板 " + item.fileName, Toast.LENGTH_SHORT).show();
                        refreshTemplateCachesForCurrentProject();
                        if (onChanged != null) {
                            onChanged.run();
                        }
                    } else {
                        Toast.makeText(this, "模板仍被引用或删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

    private void launchTemplateCaptureAfterUiSettled(String templateFileName) {
        String normalizedName = normalizeTemplateFileName(templateFileName);
        if (TextUtils.isEmpty(normalizedName)) {
            Toast.makeText(this, "请先输入模板文件名", Toast.LENGTH_SHORT).show();
            return;
        }
        uiHandler.postDelayed(() -> launchTemplateCapture(normalizedName), CAPTURE_UI_SETTLE_DELAY_MS);
    }

    private void refreshTemplateSelectionAfterCapture(View dialogView,
                                                      AutoCompleteTextView templateInput,
                                                      @Nullable String templateFileName) {
        if (dialogView == null || templateInput == null || TextUtils.isEmpty(templateFileName)) {
            return;
        }
        String normalizedName = normalizeTemplateFileName(templateFileName);
        templateInput.setText(normalizedName, false);
        refreshTemplateCachesForCurrentProject();
        refreshTemplateOptions(templateInput);
        renderRecentTemplateStrip(dialogView, templateInput);
        updateTemplatePreview(dialogView.findViewById(R.id.iv_template_preview),
                dialogView.findViewById(R.id.tv_template_preview_tip),
                normalizedName);
    }

    private void registerTemplateCaptureDialogRefresh(View dialogView,
                                                      AutoCompleteTextView templateInput,
                                                      Runnable restoreViews) {
        CropRegionOperationHandler.TemplateCaptureEventListener listener =
                new CropRegionOperationHandler.TemplateCaptureEventListener() {
                    @Override
                    public void onTemplateSaved(String projectName, String taskName, String saveFileName, Rect rect) {
                        uiHandler.post(() -> {
                            boolean projectMismatch = currentProjectDir != null && !TextUtils.isEmpty(projectName)
                                    && !TextUtils.equals(currentProjectDir.getName(), projectName);
                            boolean taskMismatch = currentTaskDir != null && !TextUtils.isEmpty(taskName)
                                    && !TextUtils.equals(currentTaskDir.getName(), taskName);
                            if (projectMismatch || taskMismatch) {
                                CropRegionOperationHandler.clearTemplateCaptureEventListener(this);
                                return;
                            }
                            refreshTemplateSelectionAfterCapture(dialogView, templateInput, saveFileName);
                            if (restoreViews != null) {
                                restoreViews.run();
                            }
                            CropRegionOperationHandler.clearTemplateCaptureEventListener(this);
                        });
                    }

                    @Override
                    public void onTemplateCaptureCancelled(String projectName, String taskName, String saveFileName) {
                        uiHandler.post(() -> {
                            if (restoreViews != null) {
                                restoreViews.run();
                            }
                            CropRegionOperationHandler.clearTemplateCaptureEventListener(this);
                        });
                    }
                };
        CropRegionOperationHandler.setTemplateCaptureEventListener(listener);
    }

    private boolean renameTemplateFile(File taskDir, String oldName, String newName) {
        if (taskDir == null || TextUtils.isEmpty(oldName) || TextUtils.isEmpty(newName)) {
            return false;
        }
        if (TextUtils.equals(oldName, newName)) {
            return true;
        }
        try {
            File imgDir = new File(taskDir, "img");
            File oldFile = new File(imgDir, oldName);
            File newFile = new File(imgDir, newName);
            if (!oldFile.exists() || newFile.exists()) {
                return false;
            }
            if (!oldFile.renameTo(newFile)) {
                return false;
            }

            File manifestFile = new File(imgDir, "manifest.json");
            if (manifestFile.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
                if (!TextUtils.isEmpty(content.trim())) {
                    JSONObject manifest = new JSONObject(content);
                    if (manifest.has(oldName)) {
                        Object bbox = manifest.get(oldName);
                        manifest.remove(oldName);
                        manifest.put(newName, bbox);
                        try (FileWriter writer = new FileWriter(manifestFile)) {
                            writer.write(manifest.toString(2));
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "重命名模板失败", e);
            return false;
        }
    }

    private void deleteProject(File projectDir) {
        if (projectDir == null) {
            return;
        }
        
        // 显示删除中提示
        Toast.makeText(this, "正在删除项目...", Toast.LENGTH_SHORT).show();
        
        // 异步删除
        fileIOManager.deleteRecursivelyAsync(projectDir, (success, error) -> {
            if (!success) {
                Toast.makeText(this, "删除失败，请重试", Toast.LENGTH_SHORT).show();
                return;
            }
            
            cachedProjects.removeIf(project ->
                    project != null && TextUtils.equals(project.getProjectName(), projectDir.getName()));
            clearProjectPanelSearch();
            invalidateProjectListCache();
            invalidateTaskListCache(projectDir);
            invalidateOperationListCache(null);
            if (projectDir.equals(currentProjectDir)) {
                currentProjectDir = null;
                currentTaskDir = null;
                currentLevel = NavigationLevel.PROJECT;
            }
            loadProjects(true);
            updateUIForLevel();
            Toast.makeText(this, "项目已删除", Toast.LENGTH_SHORT).show();
        });
    }

    private void deleteTask(File taskDir) {
        if (taskDir == null) {
            return;
        }
        
        // 显示删除中提示
        Toast.makeText(this, "正在删除Task...", Toast.LENGTH_SHORT).show();
        
        // 异步删除
        fileIOManager.deleteRecursivelyAsync(taskDir, (success, error) -> {
            if (!success) {
                Toast.makeText(this, "删除失败，请重试", Toast.LENGTH_SHORT).show();
                return;
            }
            
            File parentProject = taskDir.getParentFile();
            clearProjectPanelSearch();
            invalidateTaskListCache(parentProject);
            invalidateOperationListCache(taskDir);
            if (taskDir.equals(currentTaskDir)) {
                currentTaskDir = null;
                currentLevel = NavigationLevel.TASK;
            }
            if (parentProject != null && parentProject.equals(currentProjectDir)) {
                loadTasks(parentProject, true);
            } else if (currentLevel == NavigationLevel.PROJECT) {
                loadProjects(true);
            }
            updateUIForLevel();
            Toast.makeText(this, "Task 已删除", Toast.LENGTH_SHORT).show();
        });
    }

    private void showOverlayConfirmDialog(String title, String message, Runnable onConfirm) {
        android.view.ContextThemeWrapper ctx =
                new android.view.ContextThemeWrapper(this, R.style.Theme_AtomMaster);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, which) -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                })
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();
    }

    @Override
    public int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ==================== Phase 4A: Flow Graph double-tap wiring ====================

    /** Called from showFlowGraphDialog to wire double-tap → edit directly */
    private void wireFlowGraphDoubleTap(FlowGraphView graphView) {
        graphView.setOnNodeDoubleTapListener(node -> {
            if (node == null) return;
            showEditOperationDialog(
                    new OperationItem(node.name, node.id, node.type, node.order - 1),
                    currentOperationAdapter);
        });
    }

    // ==================== Phase 4B: Execution Step Overlay ====================

    private void showStepOverlay(String operationName, String typeLabel, int stepIndex, int total) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (stepOverlayView == null) {
                stepOverlayView = LayoutInflater.from(this).inflate(
                        R.layout.overlay_step_indicator, null);
                stepOverlayLp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                : WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                stepOverlayLp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                stepOverlayLp.x = 0;
                stepOverlayLp.y = getStatusBarHeightPx() + dp(6);
                try {
                    wm.addView(stepOverlayView, stepOverlayLp);
                } catch (Exception e) {
                    Log.e(TAG, "add step overlay failed", e);
                    stepOverlayView = null;
                    return;
                }
            }
            TextView tvName = stepOverlayView.findViewById(R.id.tv_step_op_name);
            TextView tvInfo = stepOverlayView.findViewById(R.id.tv_step_info);
            View typeBar = stepOverlayView.findViewById(R.id.step_type_bar);
            if (tvName != null) tvName.setText(operationName);
            if (tvInfo != null) tvInfo.setText("步骤 " + stepIndex + " / " + total
                    + (typeLabel != null ? "  [" + typeLabel + "]" : ""));
            if (typeBar != null) {
                int color = getTypeColorForOverlay(typeLabel);
                typeBar.setBackgroundColor(color);
            }
        });
    }

    private int getStatusBarHeightPx() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            try {
                return getResources().getDimensionPixelSize(resId);
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private void hideStepOverlay() {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (stepOverlayView != null) {
                try {
                    wm.removeView(stepOverlayView);
                } catch (Exception ignored) {
                }
                stepOverlayView = null;
                stepOverlayLp = null;
            }
        });
    }

    private int getTypeColorForOverlay(String typeLabel) {
        if (typeLabel == null) return 0xFF4CAF50;
        switch (typeLabel) {
            case "点击": return 0xFF1E88E5;
            case "延时": return 0xFFFB8C00;
            case "手势": return 0xFF8E24AA;
            case "截图区域": return 0xFF039BE5;
            case "模板匹配": return 0xFF00897B;
            case "颜色匹配": return 0xFFD84315;
            case "跳转Task": return 0xFFE53935;
            case "OCR": return 0xFF3949AB;
            case "条件分支": return 0xFFFF6F00;
            case "启动应用": return 0xFF43A047;
            default: return 0xFF4CAF50;
        }
    }

    // ==================== Phase 4C: Project Templates ====================

    private void showTemplatePicker(java.util.function.Consumer<String> onProjectNameChosen) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_template_picker, null);
        WindowManager.LayoutParams lp = buildDialogLayoutParams(320, true);
        wm.addView(dialogView, lp);

        androidx.recyclerview.widget.RecyclerView rv =
                dialogView.findViewById(R.id.rv_templates);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        java.util.List<com.auto.master.scheduler.ProjectTemplate> templates =
                com.auto.master.scheduler.ProjectTemplate.getBuiltinTemplates();
        com.auto.master.scheduler.TemplateAdapter adapter =
                new com.auto.master.scheduler.TemplateAdapter(templates, template -> {
                    safeRemoveView(dialogView);
                    showTemplateProjectNameDialog(template, onProjectNameChosen);
                });
        rv.setAdapter(adapter);

        dialogView.findViewById(R.id.btn_template_blank).setOnClickListener(v -> {
            safeRemoveView(dialogView);
            if (onProjectNameChosen != null) onProjectNameChosen.accept(null); // null = blank
        });
        dialogView.findViewById(R.id.btn_template_cancel).setOnClickListener(v ->
                safeRemoveView(dialogView));
    }

    private void showTemplateProjectNameDialog(
            com.auto.master.scheduler.ProjectTemplate template,
            java.util.function.Consumer<String> callback) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(
                new android.view.ContextThemeWrapper(this, R.style.Theme_AtomMaster));
        android.widget.EditText input = new android.widget.EditText(
                new android.view.ContextThemeWrapper(this, R.style.Theme_AtomMaster));
        input.setHint("输入项目名称");
        input.setText(template.name);
        builder.setTitle("从模板创建：" + template.emoji + " " + template.name)
                .setView(input)
                .setPositiveButton("创建", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "项目名不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    installTemplate(template, name);
                    if (callback != null) callback.accept(name);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void installTemplate(com.auto.master.scheduler.ProjectTemplate template, String projectName) {
        try {
            File root = new File(getExternalFilesDir(null), "projects");
            File projectDir = new File(root, projectName);
            File taskDir = new File(projectDir, "Task_01");
            taskDir.mkdirs();
            File opsFile = new File(taskDir, "operations.json");
            String json = template.buildOperationsJson();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(opsFile);
            fos.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();
            Toast.makeText(this, "已从模板创建项目：" + projectName, Toast.LENGTH_SHORT).show();
            invalidateProjectListCache();
            if (projectPanelView != null) loadProjects(true);
        } catch (Exception e) {
            Log.e(TAG, "installTemplate failed", e);
            Toast.makeText(this, "模板安装失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== Phase 4D: Trigger Manager ====================

    private void showTriggerManager() {
        if (!TRIGGER_FEATURE_ENABLED) {
            Toast.makeText(this, "触发器功能已停用", Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_trigger_manager, null);
        WindowManager.LayoutParams lp = buildDialogLayoutParams(340, true);
        wm.addView(dialogView, lp);

        final java.util.List<com.auto.master.scheduler.AppNotificationTrigger> triggers =
                new java.util.ArrayList<>(com.auto.master.scheduler.TriggerStore.getAll(this));

        androidx.recyclerview.widget.RecyclerView rv =
                dialogView.findViewById(R.id.rv_triggers);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));

        android.view.View emptyTv = dialogView.findViewById(R.id.tv_trigger_empty);
        Runnable updateEmpty = () -> {
            if (emptyTv != null)
                emptyTv.setVisibility(triggers.isEmpty() ? View.VISIBLE : View.GONE);
        };

        com.auto.master.scheduler.TriggerAdapter adapter =
                new com.auto.master.scheduler.TriggerAdapter(triggers, new com.auto.master.scheduler.TriggerAdapter.OnTriggerAction() {
                    @Override
                    public void onToggle(com.auto.master.scheduler.AppNotificationTrigger trigger, boolean enabled) {
                        com.auto.master.scheduler.TriggerStore.upsert(
                                FloatWindowService.this, trigger);
                        refreshAppLaunchPollingState();
                    }

                    @Override
                    public void onDelete(com.auto.master.scheduler.AppNotificationTrigger trigger) {
                        com.auto.master.scheduler.TriggerStore.remove(
                                FloatWindowService.this, trigger.id);
                        refreshAppLaunchPollingState();
                        triggers.remove(trigger);
                        rv.getAdapter().notifyDataSetChanged();
                        updateEmpty.run();
                    }
                });
        rv.setAdapter(adapter);
        updateEmpty.run();

        dialogView.findViewById(R.id.btn_add_app_trigger).setOnClickListener(v ->
                showAddAppLaunchTriggerDialog(triggers, adapter, updateEmpty));
        dialogView.findViewById(R.id.btn_add_notif_trigger).setOnClickListener(v ->
                showAddNotifTriggerDialog(triggers, adapter, updateEmpty));
        dialogView.findViewById(R.id.btn_trigger_open_settings).setOnClickListener(v -> {
            try {
                android.content.Intent intent = new android.content.Intent(
                        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开通知设置", Toast.LENGTH_SHORT).show();
            }
        });
        dialogView.findViewById(R.id.btn_trigger_close).setOnClickListener(v ->
                safeRemoveView(dialogView));
    }

    private void showAddAppLaunchTriggerDialog(
            java.util.List<com.auto.master.scheduler.AppNotificationTrigger> list,
            com.auto.master.scheduler.TriggerAdapter adapter,
            Runnable updateEmpty) {
        android.view.ContextThemeWrapper ctx =
                new android.view.ContextThemeWrapper(this, R.style.Theme_AtomMaster);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(ctx);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));

        android.widget.EditText edtPackage = new android.widget.EditText(ctx);
        edtPackage.setHint("应用包名 (如 com.tencent.mm)");
        android.widget.EditText edtLabel = new android.widget.EditText(ctx);
        edtLabel.setHint("应用名称 (显示用，如 微信)");
        android.widget.EditText edtProject = new android.widget.EditText(ctx);
        edtProject.setHint("项目名称");
        android.widget.EditText edtTask = new android.widget.EditText(ctx);
        edtTask.setHint("Task 名称");

        layout.addView(edtPackage);
        layout.addView(edtLabel);
        layout.addView(edtProject);
        layout.addView(edtTask);

        new android.app.AlertDialog.Builder(ctx)
                .setTitle("添加 App 启动触发")
                .setView(layout)
                .setPositiveButton("添加", (d, w) -> {
                    String pkg = edtPackage.getText().toString().trim();
                    String label = edtLabel.getText().toString().trim();
                    String project = edtProject.getText().toString().trim();
                    String task = edtTask.getText().toString().trim();
                    if (pkg.isEmpty() || project.isEmpty() || task.isEmpty()) {
                        Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    com.auto.master.scheduler.AppNotificationTrigger t =
                            new com.auto.master.scheduler.AppNotificationTrigger();
                    t.id = java.util.UUID.randomUUID().toString();
                    t.triggerType = com.auto.master.scheduler.AppNotificationTrigger.TYPE_APP_LAUNCH;
                    t.watchPackage = pkg;
                    t.watchAppLabel = label.isEmpty() ? pkg : label;
                    t.projectName = project;
                    t.taskId = task;
                    t.enabled = true;
                    com.auto.master.scheduler.TriggerStore.upsert(this, t);
                    refreshAppLaunchTriggerCache();
                    list.add(t);
                    adapter.notifyDataSetChanged();
                    updateEmpty.run();
                    startAppLaunchPolling();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddNotifTriggerDialog(
            java.util.List<com.auto.master.scheduler.AppNotificationTrigger> list,
            com.auto.master.scheduler.TriggerAdapter adapter,
            Runnable updateEmpty) {
        android.view.ContextThemeWrapper ctx =
                new android.view.ContextThemeWrapper(this, R.style.Theme_AtomMaster);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(ctx);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));

        android.widget.EditText edtPkg = new android.widget.EditText(ctx);
        edtPkg.setHint("来源应用包名 (留空=任意应用)");
        android.widget.EditText edtKw = new android.widget.EditText(ctx);
        edtKw.setHint("通知关键词 (留空=任意通知)");
        android.widget.EditText edtProject = new android.widget.EditText(ctx);
        edtProject.setHint("项目名称");
        android.widget.EditText edtTask = new android.widget.EditText(ctx);
        edtTask.setHint("Task 名称");

        layout.addView(edtPkg);
        layout.addView(edtKw);
        layout.addView(edtProject);
        layout.addView(edtTask);

        new android.app.AlertDialog.Builder(ctx)
                .setTitle("添加通知触发")
                .setView(layout)
                .setPositiveButton("添加", (d, w) -> {
                    String project = edtProject.getText().toString().trim();
                    String task = edtTask.getText().toString().trim();
                    if (project.isEmpty() || task.isEmpty()) {
                        Toast.makeText(this, "请填写项目和 Task 名称", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    com.auto.master.scheduler.AppNotificationTrigger t =
                            new com.auto.master.scheduler.AppNotificationTrigger();
                    t.id = java.util.UUID.randomUUID().toString();
                    t.triggerType = com.auto.master.scheduler.AppNotificationTrigger.TYPE_NOTIFICATION;
                    t.notificationPackage = edtPkg.getText().toString().trim();
                    t.notificationKeyword = edtKw.getText().toString().trim();
                    t.projectName = project;
                    t.taskId = task;
                    t.enabled = true;
                    com.auto.master.scheduler.TriggerStore.upsert(this, t);
                    list.add(t);
                    adapter.notifyDataSetChanged();
                    updateEmpty.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== Phase 4D: App-launch polling ====================

    private void initAppLaunchPollThreadIfNeeded() {
        if (!TRIGGER_FEATURE_ENABLED) {
            return;
        }
        if (appLaunchPollThread != null) {
            return;
        }
        appLaunchPollThread = new HandlerThread("Atomm-AppLaunchPoll");
        appLaunchPollThread.start();
        appLaunchPollHandler = new Handler(appLaunchPollThread.getLooper());
    }

    private void refreshAppLaunchTriggerCache() {
        if (!TRIGGER_FEATURE_ENABLED) {
            synchronized (appLaunchTriggerCacheLock) {
                cachedAppLaunchTriggers = Collections.emptyList();
            }
            return;
        }
        List<com.auto.master.scheduler.AppNotificationTrigger> active =
                com.auto.master.scheduler.TriggerStore.getByType(
                        this, com.auto.master.scheduler.AppNotificationTrigger.TYPE_APP_LAUNCH);
        synchronized (appLaunchTriggerCacheLock) {
            cachedAppLaunchTriggers = active == null
                    ? Collections.emptyList()
                    : new ArrayList<>(active);
        }
    }

    private List<com.auto.master.scheduler.AppNotificationTrigger> getCachedAppLaunchTriggersSnapshot() {
        synchronized (appLaunchTriggerCacheLock) {
            if (cachedAppLaunchTriggers.isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(cachedAppLaunchTriggers);
        }
    }

    private void refreshAppLaunchPollingState() {
        refreshAppLaunchTriggerCache();
        if (getCachedAppLaunchTriggersSnapshot().isEmpty()) {
            stopAppLaunchPolling();
        } else {
            startAppLaunchPolling();
        }
    }

    private void startAppLaunchPolling() {
        if (!TRIGGER_FEATURE_ENABLED || ScriptRunner.isCurrentScriptRunning()) {
            stopAppLaunchPolling();
            return;
        }
        initAppLaunchPollThreadIfNeeded();
        refreshAppLaunchTriggerCache();
        if (getCachedAppLaunchTriggersSnapshot().isEmpty()) {
            stopAppLaunchPolling();
            return;
        }
        if (appLaunchPollHandler == null) {
            return;
        }
        appLaunchPollHandler.removeCallbacks(appLaunchPollRunnable);
        appLaunchPollHandler.postDelayed(appLaunchPollRunnable, APP_LAUNCH_POLL_INTERVAL_MS);
    }

    private void stopAppLaunchPolling() {
        if (appLaunchPollHandler != null) {
            appLaunchPollHandler.removeCallbacks(appLaunchPollRunnable);
        }
    }

    private final Runnable appLaunchPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (ScriptRunner.isCurrentScriptRunning()) {
                stopAppLaunchPolling();
                return;
            }
            checkAppLaunchTriggers();
            // Continue polling only if there are active app-launch triggers
            java.util.List<com.auto.master.scheduler.AppNotificationTrigger> active =
                    getCachedAppLaunchTriggersSnapshot();
            if (!active.isEmpty()) {
                appLaunchPollHandler.postDelayed(this, APP_LAUNCH_POLL_INTERVAL_MS);
            }
        }
    };

    private void checkAppLaunchTriggers() {
        if (!TRIGGER_FEATURE_ENABLED || ScriptRunner.isCurrentScriptRunning()) return;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) return;
        try {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager)
                    getSystemService(android.content.Context.USAGE_STATS_SERVICE);
            if (usm == null) return;
            long now = System.currentTimeMillis();
            java.util.List<android.app.usage.UsageStats> stats =
                    usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                            now - 5000, now);
            if (stats == null || stats.isEmpty()) return;

            String foreground = null;
            long maxTime = 0;
            for (android.app.usage.UsageStats us : stats) {
                if (us.getLastTimeUsed() > maxTime) {
                    maxTime = us.getLastTimeUsed();
                    foreground = us.getPackageName();
                }
            }
            if (foreground == null || foreground.equals(lastForegroundPackage)) return;

            String newPkg = foreground;
            lastForegroundPackage = newPkg;

            java.util.List<com.auto.master.scheduler.AppNotificationTrigger> triggers =
                    getCachedAppLaunchTriggersSnapshot();
            for (com.auto.master.scheduler.AppNotificationTrigger t : triggers) {
                if (t.enabled && newPkg.equals(t.watchPackage)) {
                    Log.d(TAG, "app-launch trigger fired: pkg=" + newPkg + " -> " + t.projectName);
                    com.auto.master.scheduler.ScheduledTask spec =
                            new com.auto.master.scheduler.ScheduledTask();
                    spec.id = t.id;
                    spec.projectName = t.projectName;
                    spec.taskId = t.taskId;
                    spec.operationId = t.operationId;
                    spec.enabled = true;
                    com.auto.master.scheduler.TaskScheduleExecutor.execute(this, spec);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "checkAppLaunchTriggers failed", e);
        }
    }

    // ==================== FloatWindowHost Implementation ====================

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public WindowManager getWindowManager() {
        return wm;
    }

    @Override
    public File getProjectsRootDir() {
        File root = new File(getExternalFilesDir(null), "projects");
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public File getCurrentProjectDir() {
        return currentProjectDir;
    }

    @Override
    public File getCurrentTaskDir() {
        return currentTaskDir;
    }

    // ==================== End FloatWindowHost Implementation ====================

    int[] getScreenSizePx() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowMetrics metrics = wm.getCurrentWindowMetrics();
            return new int[]{metrics.getBounds().width(), metrics.getBounds().height()};
        }
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    private void clampPanelToScreen(WindowManager.LayoutParams lp) {
        if (lp == null) {
            return;
        }
        int[] screen = getScreenSizePx();
        int margin = dp(8);
        int panelWidth = Math.max(lp.width, dp(PROJECT_PANEL_MIN_W_DP));
        int panelHeight = Math.max(lp.height, dp(PROJECT_PANEL_MIN_H_DP));
        int maxX = Math.max(margin, screen[0] - panelWidth - margin);
        int maxY = Math.max(margin, screen[1] - panelHeight - margin);
        lp.x = Math.max(margin, Math.min(lp.x, maxX));
        lp.y = Math.max(margin, Math.min(lp.y, maxY));
    }

    private void adaptPanelSizeToScreen(WindowManager.LayoutParams lp, int desiredWidthDp, int desiredHeightDp) {
        int[] screen = getScreenSizePx();
        int margin = dp(12);
        int maxW = Math.max(dp(220), screen[0] - margin * 2);
        int maxH = Math.max(dp(260), screen[1] - margin * 2);
        int desiredW = dp(desiredWidthDp);
        int desiredH = dp(desiredHeightDp);

        lp.width = Math.min(desiredW, maxW);
        lp.height = Math.min(desiredH, maxH);

        lp.x = Math.max(margin, Math.min(lp.x, Math.max(margin, screen[0] - lp.width - margin)));
        lp.y = Math.max(margin, Math.min(lp.y, Math.max(margin, screen[1] - lp.height - margin)));
    }

    private void adaptProjectPanelSizeToCurrentScreen(WindowManager.LayoutParams lp) {
        if (lp == null) {
            return;
        }
        int[] screen = getScreenSizePx();
        int margin = dp(12);
        boolean landscape = screen[0] > screen[1];
        int maxW = Math.max(dp(PROJECT_PANEL_MIN_W_DP), screen[0] - margin * 2);
        int maxH = Math.max(
                dp(PROJECT_PANEL_MIN_H_DP),
                (int) (screen[1] * (landscape ? PROJECT_PANEL_MAX_H_RATIO_LANDSCAPE : PROJECT_PANEL_MAX_H_RATIO_PORTRAIT))
        );

        int desiredW = lp.width > 0 ? lp.width : dp(PROJECT_PANEL_DEFAULT_W_DP);
        int desiredH = lp.height > 0 ? lp.height : dp(PROJECT_PANEL_DEFAULT_H_DP);
        lp.width = Math.min(desiredW, maxW);
        lp.height = Math.min(desiredH, maxH);

        lp.x = Math.max(margin, Math.min(lp.x, Math.max(margin, screen[0] - lp.width - margin)));
        lp.y = Math.max(margin, Math.min(lp.y, Math.max(margin, screen[1] - lp.height - margin)));
    }

    private void prewarmProjectStructure() {
        File root = getProjectsRootDir();
        File[] dirs = root.listFiles(File::isDirectory);
        List<ProjectListItem> items = new ArrayList<>();
        taskItemsMemoryCache.clear();
        taskItemsMemoryVersions.clear();
        if (dirs != null) {
            for (File dir : dirs) {
                List<File> taskItems = readTaskItems(dir);
                int taskCount = 0;
                for (File item : taskItems) {
                    if (item.isDirectory()) {
                        taskCount++;
                    }
                }
                items.add(new ProjectListItem(dir, taskCount, dir.lastModified()));
                taskItemsMemoryCache.put(buildFileCacheKey(dir), new ArrayList<>(taskItems));
                taskItemsMemoryVersions.put(buildFileCacheKey(dir), dir.lastModified());
            }
        }
        Collections.sort(items, (left, right) -> {
            int byTime = Long.compare(right.lastModified, left.lastModified);
            if (byTime != 0) {
                return byTime;
            }
            return left.dir.getName().compareToIgnoreCase(right.dir.getName());
        });
        projectListCache.clear();
        projectListCache.addAll(items);
        projectListCacheVersion = root.lastModified();
        projectPanelContentDirty = true;
    }

    private static class DragTouchListener implements View.OnTouchListener {
        private static final long FRAME_INTERVAL_MS = 16L;
        private final WindowManager.LayoutParams lp;
        private final WindowManager wm;
        private final View targetView;
        private final FloatWindowService service;
        private final boolean keepInScreen;

        private float downRawX, downRawY;
        private int startX, startY;

        private boolean moved = false;
        private boolean longPressFired = false;
        private final int slopPx;
        private long lastUpdateMs;
        private int pendingX;
        private int pendingY;

        private final android.os.Handler longPressHandler =
                new android.os.Handler(android.os.Looper.getMainLooper());
        private Runnable longPressRunnable;
        private final long longPressTimeoutMs =
                android.view.ViewConfiguration.getLongPressTimeout();

        DragTouchListener(WindowManager.LayoutParams lp, WindowManager wm, View targetView, FloatWindowService service) {
            this(lp, wm, targetView, service, false);
        }

        DragTouchListener(WindowManager.LayoutParams lp, WindowManager wm, View targetView, FloatWindowService service,
                          boolean keepInScreen) {
            this.lp = lp;
            this.wm = wm;
            this.targetView = targetView;
            this.service = service;
            this.keepInScreen = keepInScreen;
            this.slopPx = android.view.ViewConfiguration.get(service).getScaledTouchSlop();
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    moved = false;
                    longPressFired = false;
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    pendingX = lp.x;
                    pendingY = lp.y;
                    lastUpdateMs = 0L;
                    setDraggingVisualState(true);
                    longPressRunnable = () -> {
                        if (!moved) {
                            longPressFired = true;
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                            onLongPress();
                        }
                    };
                    longPressHandler.postDelayed(longPressRunnable, longPressTimeoutMs);
                    return true;

                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downRawX;
                    float dy = e.getRawY() - downRawY;

                    if (!moved && (Math.abs(dx) > slopPx || Math.abs(dy) > slopPx)) {
                        moved = true;
                        cancelLongPress();
                    }

                    if (moved) {
                        int nextX = startX + (int) dx;
                        int nextY = startY + (int) dy;
                        if (keepInScreen) {
                            int[] screen = service.getScreenSizePx();
                            int margin = service.dp(6);
                            int viewW = Math.max(targetView.getWidth(), 1);
                            int viewH = Math.max(targetView.getHeight(), 1);
                            int maxX = Math.max(margin, screen[0] - viewW - margin);
                            int maxY = Math.max(margin, screen[1] - viewH - margin);
                            nextX = Math.max(margin, Math.min(nextX, maxX));
                            nextY = Math.max(margin, Math.min(nextY, maxY));
                        }
                        pendingX = nextX;
                        pendingY = nextY;
                        long now = android.os.SystemClock.uptimeMillis();
                        if (lastUpdateMs == 0L || now - lastUpdateMs >= FRAME_INTERVAL_MS) {
                            applyPendingPosition();
                            lastUpdateMs = now;
                        }
                    }
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelLongPress();
                    if (moved) {
                        applyPendingPosition();
                        onDragEnd(lp.x, lp.y);
                    }
                    setDraggingVisualState(false);
                    if (!moved && !longPressFired) {
                        v.performClick();
                    }
                    return true;
            }
            return false;
        }

        private void cancelLongPress() {
            if (longPressRunnable != null) {
                longPressHandler.removeCallbacks(longPressRunnable);
                longPressRunnable = null;
            }
        }

        /** 拖拽结束回调，子类可覆盖以持久化位置。 */
        protected void onDragEnd(int finalX, int finalY) {}

        /** 长按回调，子类可覆盖以响应长按手势。 */
        protected void onLongPress() {}

        private void applyPendingPosition() {
            if (lp.x == pendingX && lp.y == pendingY) {
                return;
            }
            lp.x = pendingX;
            lp.y = pendingY;
            wm.updateViewLayout(targetView, lp);
        }

        private void setDraggingVisualState(boolean dragging) {
            targetView.setLayerType(dragging ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
            View header = targetView.findViewById(R.id.drag_header);
            if (header != null) {
                header.setAlpha(dragging ? 0.94f : 1f);
            }
            RecyclerView rv = targetView.findViewById(R.id.rv_content);
            if (rv != null) {
                rv.suppressLayout(dragging);
            }
        }
    }

    private static class PanelResizeTouchListener implements View.OnTouchListener {
        private static final long FRAME_INTERVAL_MS = 16L;
        private final WindowManager.LayoutParams lp;
        private final WindowManager wm;
        private final View targetView;
        private final FloatWindowService service;
        private final int minWidthDp;
        private final int minHeightDp;
        private final float landscapeMaxHeightRatio;

        private float downRawX;
        private float downRawY;
        private int startW;
        private int startH;
        private int pendingW;
        private int pendingH;
        private long lastUpdateMs;

        PanelResizeTouchListener(WindowManager.LayoutParams lp, WindowManager wm, View targetView,
                                 FloatWindowService service, int minWidthDp, int minHeightDp) {
            this(lp, wm, targetView, service, minWidthDp, minHeightDp, 1f);
        }

        PanelResizeTouchListener(WindowManager.LayoutParams lp, WindowManager wm, View targetView,
                                 FloatWindowService service, int minWidthDp, int minHeightDp,
                                 float landscapeMaxHeightRatio) {
            this.lp = lp;
            this.wm = wm;
            this.targetView = targetView;
            this.service = service;
            this.minWidthDp = minWidthDp;
            this.minHeightDp = minHeightDp;
            this.landscapeMaxHeightRatio = landscapeMaxHeightRatio;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    startW = lp.width;
                    startH = lp.height;
                    pendingW = lp.width;
                    pendingH = lp.height;
                    lastUpdateMs = 0L;
                    setResizingVisualState(true);
                    return true;

                case MotionEvent.ACTION_MOVE: {
                    int[] screen = service.getScreenSizePx();
                    int minW = service.dp(minWidthDp);
                    int minH = service.dp(minHeightDp);
                    int newW = startW + (int) (e.getRawX() - downRawX);
                    int newH = startH + (int) (e.getRawY() - downRawY);
                    int maxH = screen[1] - service.dp(24);
                    if (landscapeMaxHeightRatio > 0f && screen[0] > screen[1]) {
                        maxH = Math.min(maxH, Math.max(minH, (int) (screen[1] * landscapeMaxHeightRatio)));
                    }
                    pendingW = Math.max(minW, Math.min(newW, screen[0]));
                    pendingH = Math.max(minH, Math.min(newH, maxH));
                    long now = android.os.SystemClock.uptimeMillis();
                    if (lastUpdateMs == 0L || now - lastUpdateMs >= FRAME_INTERVAL_MS) {
                        applyPendingSize();
                        lastUpdateMs = now;
                    }
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    applyPendingSize();
                    setResizingVisualState(false);
                    return true;
            }
            return false;
        }

        private void applyPendingSize() {
            if (lp.width == pendingW && lp.height == pendingH) {
                return;
            }
            lp.width = pendingW;
            lp.height = pendingH;
            service.clampPanelToScreen(lp);
            wm.updateViewLayout(targetView, lp);
        }

        private void setResizingVisualState(boolean resizing) {
            targetView.setLayerType(resizing ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, null);
            View handle = targetView.findViewById(R.id.resize_handle);
            if (handle != null) {
                handle.setAlpha(resizing ? 0.8f : 1f);
            }
            RecyclerView rv = targetView.findViewById(R.id.rv_content);
            if (rv != null) {
                rv.suppressLayout(resizing);
            }
        }
    }

    private static class ResizeTouchListener implements View.OnTouchListener {
        private final boolean isTopLeft;
        private final FloatWindowService service;

        private float downRawX, downRawY;
        private int startW, startH;
        private int startX, startY;

        ResizeTouchListener(boolean isTopLeft) {
            this.isTopLeft = isTopLeft;
            this.service = null;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            return false;
        }
    }

    public static class OperationItem {
        public String name;
        public String type;
        public String id;
        public int index;
        public long delayDurationMs;
        public boolean delayShowCountdown;

        public OperationItem(String name, String id, String type, int index) {
            this(name, id, type, index, 0L, true);
        }

        public OperationItem(String name, String id, String type, int index, long delayDurationMs) {
            this(name, id, type, index, delayDurationMs, true);
        }

        public OperationItem(String name, String id, String type, int index, long delayDurationMs, boolean delayShowCountdown) {
            this.name = name;
            this.id = id;
            this.type = type;
            this.index = index;
            this.delayDurationMs = Math.max(0L, delayDurationMs);
            this.delayShowCountdown = delayShowCountdown;
        }
    }

    private static class OperationClipboardEntry {
        final JSONObject operationJson;
        final String name;
        final String sourceTaskPath;
        final long createdAt;

        OperationClipboardEntry(JSONObject operationJson, String name, String sourceTaskPath, long createdAt) {
            this.operationJson = operationJson;
            this.name = name;
            this.sourceTaskPath = sourceTaskPath;
            this.createdAt = createdAt;
        }
    }

    private static class ProjectListItem {
        final File dir;
        final int taskCount;
        final long lastModified;

        ProjectListItem(File dir, int taskCount, long lastModified) {
            this.dir = dir;
            this.taskCount = taskCount;
            this.lastModified = lastModified;
        }
    }

    static class ProjectPanelAdapter extends RecyclerView.Adapter<ProjectPanelAdapter.ViewHolder> {
        interface OnItemClickListener {
            void onItemClick(ProjectListItem item);
        }

        interface OnItemActionListener {
            void onMenuClick(ProjectListItem item, View anchor);
        }

        private final List<ProjectListItem> projects;
        private final OnItemClickListener listener;
        private final OnItemActionListener actionListener;

        ProjectPanelAdapter(
                List<ProjectListItem> projects,
                OnItemClickListener listener,
                OnItemActionListener actionListener) {
            this.projects = new ArrayList<>(projects);
            this.listener = listener;
            this.actionListener = actionListener;
            setHasStableIds(true);
        }

        void submitProjects(List<ProjectListItem> items) {
            List<ProjectListItem> newItems = items == null ? Collections.emptyList() : new ArrayList<>(items);
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return projects.size();
                }

                @Override
                public int getNewListSize() {
                    return newItems.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return TextUtils.equals(
                            projects.get(oldItemPosition).dir.getAbsolutePath(),
                            newItems.get(newItemPosition).dir.getAbsolutePath());
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    ProjectListItem oldItem = projects.get(oldItemPosition);
                    ProjectListItem newItem = newItems.get(newItemPosition);
                    return oldItem.taskCount == newItem.taskCount
                            && oldItem.lastModified == newItem.lastModified
                            && TextUtils.equals(oldItem.dir.getName(), newItem.dir.getName());
                }
            });
            projects.clear();
            projects.addAll(newItems);
            diffResult.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project_folder_panel, parent, false);
            return new ViewHolder(v, listener, actionListener);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectListItem project = projects.get(position);
            holder.bind(project);
        }

        @Override
        public int getItemCount() {
            return projects.size();
        }

        @Override
        public long getItemId(int position) {
            return projects.get(position).dir.getAbsolutePath().hashCode();
        }

        @Override
        public int getItemViewType(int position) {
            return PROJECT_PANEL_VIEW_TYPE_PROJECT;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            TextView info;
            ImageView moreOptions;
            private ProjectListItem currentProject;

            ViewHolder(View itemView, OnItemClickListener listener, OnItemActionListener actionListener) {
                super(itemView);
                name = itemView.findViewById(R.id.folder_name);
                info = itemView.findViewById(R.id.folder_info);
                moreOptions = itemView.findViewById(R.id.more_options);

                itemView.setOnClickListener(v -> {
                    if (currentProject != null && listener != null) {
                        listener.onItemClick(currentProject);
                    }
                });

                moreOptions.setOnClickListener(v -> {
                    if (currentProject != null && actionListener != null) {
                        actionListener.onMenuClick(currentProject, v);
                    }
                });
            }

            void bind(ProjectListItem project) {
                currentProject = project;
                name.setText(project.dir.getName());
                info.setVisibility(View.VISIBLE);
                info.setText(project.taskCount + " tasks");
                moreOptions.setVisibility(View.VISIBLE);
            }
        }
    }

    static class TaskPanelAdapter extends RecyclerView.Adapter<TaskPanelAdapter.ViewHolder> {
        interface OnItemClickListener {
            void onItemClick(File file);
        }

        interface OnItemActionListener {
            void onMenuClick(File file, View anchor);
        }

        private final List<File> items;
        private final OnItemClickListener listener;
        private final OnItemActionListener actionListener;

        TaskPanelAdapter(
                List<File> items,
                OnItemClickListener listener,
                OnItemActionListener actionListener) {
            this.items = new ArrayList<>(items);
            this.listener = listener;
            this.actionListener = actionListener;
            setHasStableIds(true);
        }

        void submitItems(List<File> newItems) {
            List<File> targetItems = newItems == null ? Collections.emptyList() : new ArrayList<>(newItems);
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
                    return TextUtils.equals(
                            items.get(oldItemPosition).getAbsolutePath(),
                            targetItems.get(newItemPosition).getAbsolutePath());
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    File oldItem = items.get(oldItemPosition);
                    File newItem = targetItems.get(newItemPosition);
                    return oldItem.isDirectory() == newItem.isDirectory()
                            && oldItem.lastModified() == newItem.lastModified()
                            && oldItem.length() == newItem.length()
                            && TextUtils.equals(oldItem.getName(), newItem.getName());
                }
            });
            items.clear();
            items.addAll(targetItems);
            diffResult.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project_folder_panel, parent, false);
            return new ViewHolder(v, listener, actionListener);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = items.get(position);
            holder.bind(file);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).getAbsolutePath().hashCode();
        }

        @Override
        public int getItemViewType(int position) {
            return PROJECT_PANEL_VIEW_TYPE_TASK;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            TextView info;
            ImageView editIcon;
            ImageView moreOptions;
            private File currentFile;

            ViewHolder(View itemView, OnItemClickListener listener, OnItemActionListener actionListener) {
                super(itemView);
                icon = itemView.findViewById(R.id.folder_icon);
                name = itemView.findViewById(R.id.folder_name);
                info = itemView.findViewById(R.id.folder_info);
                editIcon = itemView.findViewById(R.id.edit_icon);
                moreOptions = itemView.findViewById(R.id.more_options);

                itemView.setOnClickListener(v -> {
                    if (currentFile != null && listener != null) {
                        listener.onItemClick(currentFile);
                    }
                });

                moreOptions.setOnClickListener(v -> {
                    if (currentFile != null && currentFile.isDirectory() && actionListener != null) {
                        actionListener.onMenuClick(currentFile, v);
                    }
                });
            }

            void bind(File file) {
                currentFile = file;
                name.setText(file.getName());
                info.setVisibility(View.GONE);

                if (file.isDirectory()) {
                    icon.setImageResource(R.drawable.ic_folder_colored);
                    editIcon.setVisibility(View.GONE);
                    moreOptions.setVisibility(View.VISIBLE);
                } else {
                    icon.setImageResource(R.drawable.ic_file);
                    editIcon.setVisibility(View.VISIBLE);
                    moreOptions.setVisibility(View.GONE);
                }
            }
        }
    }

    static class OperationPanelAdapter extends RecyclerView.Adapter<OperationPanelAdapter.ViewHolder> {
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
            void onFloatButton(OperationItem item);
        }

        private static class ActionItem {
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

        interface OnBatchSelectionListener {
            void onBatchSelectionChanged(Set<String> selectedIds);
        }

        private final List<OperationItem> operations;
        private final OnItemClickListener listener;
        private final OnActionListener actionListener;
        private final OnBatchSelectionListener batchSelectionListener;
        private boolean batchMode = false;
        private final Set<String> batchSelectedIds = new HashSet<>();

        private AtomicInteger selectedPosition = new AtomicInteger(-1);  // -1 表示未选中
        private String runningOperationId = null;           // 当前正在后台执行的（红色高亮）
        private int prevPos = -1;
        /** operationId → 悬浮按钮颜色，用于显示跟随按钮颜色的小标识点。 */
        private Map<String, Integer> floatBtnColorMap = Collections.emptyMap();

        /**
         * 这里后台运行的时候 需要拿到 adapter 然后 改变 runningPosition 为对应的 index 改成 id
         *
         */

        OperationPanelAdapter(List<OperationItem> operations,
                             OnItemClickListener listener,
                             OnActionListener actionListener,
                             OnBatchSelectionListener batchSelectionListener) {
            this.operations = new ArrayList<>(operations);
            this.listener = listener;
            this.actionListener = actionListener;
            this.batchSelectionListener = batchSelectionListener;
            setHasStableIds(true);
        }

        /**
         * 静默写入 id→颜色 映射，不触发任何 notify。
         * 必须在 submitOperations() 之前调用，让 DiffUtil 绑定时数据就绪。
         */
        void initFloatBtnColors(Map<String, Integer> colorMap) {
            floatBtnColorMap = (colorMap == null)
                    ? Collections.<String, Integer>emptyMap()
                    : new HashMap<>(colorMap);
        }

        /**
         * 增量更新 id→颜色 映射，只 notify 发生变化的条目。
         * 在增删/修改悬浮按钮配置后调用，供外部实时刷新标识点颜色。
         */
        void setFloatBtnColors(Map<String, Integer> colorMap) {
            Map<String, Integer> next = (colorMap == null)
                    ? Collections.<String, Integer>emptyMap()
                    : new HashMap<>(colorMap);
            if (next.equals(floatBtnColorMap)) return; // 完全一致则跳过
            // 计算变化范围：旧 + 新的 key 并集
            Set<String> changed = new HashSet<>(floatBtnColorMap.keySet());
            changed.addAll(next.keySet());
            floatBtnColorMap = next;
            for (int i = 0; i < operations.size(); i++) {
                OperationItem op = operations.get(i);
                if (op != null && changed.contains(op.id)) {
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

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_operation_compact, parent, false);
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
            boolean isSelected = (position == selectedPosition.get());
            boolean isBatchChecked = batchSelectedIds.contains(item.id);

            // 设置选中状态背景
            holder.itemView.setSelected(isSelected);
            
            // 选中指示条
            if (holder.selectionIndicator != null) {
                holder.selectionIndicator.setVisibility(isSelected ? View.VISIBLE : View.INVISIBLE);
            }
            
            // 运行状态高亮
            if (isRunning) {
                holder.itemView.setBackgroundColor(0x66EF9A9A);
                if (holder.selectionIndicator != null) {
                    holder.selectionIndicator.setBackgroundColor(0xFFF44336); // 红色指示条
                }
            } else if (isSelected) {
                holder.itemView.setBackgroundColor(0xFFE8F0FE);  // 浅蓝色背景
                if (holder.selectionIndicator != null) {
                    holder.selectionIndicator.setBackgroundColor(0xFF3c6de4); // 蓝色指示条
                }
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                if (holder.selectionIndicator != null) {
                    holder.selectionIndicator.setBackgroundColor(0xFF3c6de4);
                }
            }

            holder.batchCheckBox.setVisibility(batchMode ? View.VISIBLE : View.GONE);
            holder.batchCheckBox.setChecked(isBatchChecked);
            holder.moreOptions.setVisibility(batchMode ? View.GONE : View.VISIBLE);
            holder.selectionIndicator.setVisibility(batchMode ? (isBatchChecked ? View.VISIBLE : View.INVISIBLE)
                    : (isSelected ? View.VISIBLE : View.INVISIBLE));

            // 悬浮按钮标识点：颜色跟随对应悬浮按钮颜色
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

            View.OnClickListener menuClick = v -> showMenu(v, item, position);
            holder.moreOptions.setOnClickListener(menuClick);
            holder.batchCheckBox.setOnClickListener(v -> toggleBatchSelection(item.id, position));
        }

        @Override
        public long getItemId(int position) {
            OperationItem item = operations.get(position);
            String stableKey = !TextUtils.isEmpty(item.id)
                    ? item.id
                    : String.valueOf(item.name) + "#" + position;
            return stableKey.hashCode();
        }

        @Override
        public int getItemViewType(int position) {
            return PROJECT_PANEL_VIEW_TYPE_OPERATION;
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
            actionItems.add(new ActionItem(8, "悬浮按钮", "为这个节点创建/编辑专属悬浮按钮", true));

            View popupView = LayoutInflater.from(anchor.getContext()).inflate(R.layout.dialog_node_action_sheet, null);
            TextView tvTitle = popupView.findViewById(R.id.tv_action_title);
            RecyclerView rvActions = popupView.findViewById(R.id.rv_action_list);
            if (tvTitle != null) {
                tvTitle.setText(TextUtils.isEmpty(item.name) ? "节点操作" : item.name);
            }
            rvActions.setLayoutManager(new LinearLayoutManager(anchor.getContext()));
            PopupWindow popupWindow = new PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popupWindow.setElevation(10f);
            rvActions.setAdapter(new ActionSheetAdapter(actionItems, action -> {
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
                        actionListener.onFloatButton(item);
                        break;
                    default:
                        break;
                }
            }));
            popupWindow.showAsDropDown(anchor, -dp(anchor.getContext(), 180), dp(anchor.getContext(), 4), Gravity.END);
        }

        // 对外提供方法，让外部（比如服务、线程）动态设置"正在运行的项"
        public void setRunningPosition(String operationId) {
            if (TextUtils.equals(this.runningOperationId, operationId)) {
                return;
            }
            String prev = this.runningOperationId;
            this.runningOperationId = operationId;

//            int prevPos = findPositionByKey(prev);
            int newPos = findPositionByKey(operationId);

            if (prevPos >= 0) notifyItemChanged(prevPos);
            if (newPos >= 0)  notifyItemChanged(newPos);
            prevPos = newPos;
        }
        private int findPositionByKey(String key) {
            if (key == null) return -1;
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

        // 新增：获取当前选中项
        public OperationItem getSelectedItem() {
            if (selectedPosition.get() >= 0 && selectedPosition.get() < operations.size()) {
                return operations.get(selectedPosition.get());
            }
            return null;
        }

        // 可选：清空选中
        public void clearSelection() {
            int prev = selectedPosition.get();
            if (prev < 0) {
                return;
            }
            selectedPosition.set(-1);
            if (prev >= 0) notifyItemChanged(prev);
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
            if (old >= 0) notifyItemChanged(old);
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
            if (operations.isEmpty()) {
                return;
            }
            notifyItemRangeChanged(0, operations.size());
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
            TextView opIndex;
            TextView name;
            TextView typeText;
            TextView opId;
            View selectionIndicator;
            /** 悬浮按钮标识点 View */
            View floatBtnDot;
            /** 持有 Drawable 引用，bind 时只改颜色，零额外对象分配 */
            GradientDrawable floatBtnDotBg;
            ImageView moreOptions;
            CheckBox batchCheckBox;

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
                    // GradientDrawable 整个 ViewHolder 生命周期只 new 一次
                    floatBtnDotBg = new GradientDrawable();
                    floatBtnDotBg.setShape(GradientDrawable.OVAL);
                    floatBtnDot.setBackground(floatBtnDotBg);
                    // 颜色在 onBindViewHolder 里按 floatBtnColorMap 动态设置
                }
            }
        }

        private static int dp(Context context, int value) {
            return (int) (value * context.getResources().getDisplayMetrics().density);
        }

        private static class ActionSheetAdapter extends RecyclerView.Adapter<ActionSheetAdapter.ViewHolder> {
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

    // ==================== ScriptExecutionListener 实现 ====================

    @Override
    public void onOperationStart(String operationId, String operationName) {
        stopAppLaunchPolling();
        opStartTimeMs.put(operationId, System.currentTimeMillis());
        appendRunLog("[start] " + operationId + " | " + operationName);
        // 1. 更新运行状态面板
        currentRunningOperationId = operationId;
        currentRunningOperationName = operationName;
        CrashLogger.updateRunContext(currentRunningProject, currentRunningTask, operationId, operationName);
        updateRunningPanelStatus("运行中", 0xFF4CAF50);
        OperationItem opItem = findRunningItem(operationId);

        // 找到当前 operation 在列表中的位置
        int runningPos = -1;
        for (int i = 0; i < runningOperations.size(); i++) {
            if (runningOperations.get(i).id.equals(operationId)) {
                runningPos = i;
                break;
            }
        }

        // 更新当前索引为实际位置（避免无限增加）
        if (runningPos >= 0) {
            currentOperationIndex = runningPos + 1;
        }

        // 更新运行面板
        if (runningPanelAdapter != null) {
            runningPanelAdapter.setRunningPosition(runningPos);
        }
        if (currentOperationAdapter != null) {
            new Handler(Looper.getMainLooper()).post(() -> currentOperationAdapter.setRunningPosition(operationId));
        }
        updateRunningPanelProgress();
        maybeStartDelayProgress(opItem);

        // 2. 更新悬浮球状态
        if (ballStatusText != null) {
            ballStatusText.setText("运行中: " + operationName);
            ballStatusText.setVisibility(View.VISIBLE);
        }

        // 3. 更新通知栏
        updateNotification("正在运行: " + operationName);

        // 4. Phase 4B: 实时执行覆盖层
        final String typeLabel;
        typeLabel = opItem != null ? opItem.type : null;
        showStepOverlay(operationName, typeLabel, currentOperationIndex, totalOperationCount);

        // 5. Phase 4A: 更新 FlowGraph 高亮
        updateFlowGraphHighlight(operationId);
    }

    private OperationItem findRunningItem(String operationId) {
        for (OperationItem item : runningOperations) {
            if (item != null && item.id != null && item.id.equals(operationId)) {
                return item;
            }
        }
        return null;
    }

    public static long extractDelayDurationMs(@Nullable MetaOperation operation) {
        if (operation == null || operation.getType() == null || operation.getType() != OperationType.DELAY.getCode()) {
            return 0L;
        }
        Map<String, Object> inputMap = operation.getInputMap();
        if (inputMap == null) {
            return 0L;
        }
        Object value = inputMap.get(MetaOperation.SLEEP_DURATION);
        if (value instanceof Number) {
            return Math.max(0L, ((Number) value).longValue());
        }
        if (value instanceof String) {
            try {
                return Math.max(0L, Long.parseLong(((String) value).trim()));
            } catch (Exception ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    public static boolean extractDelayShowCountdown(@Nullable MetaOperation operation) {
        if (operation == null || operation.getType() == null || operation.getType() != OperationType.DELAY.getCode()) {
            return false;
        }
        Map<String, Object> inputMap = operation.getInputMap();
        if (inputMap == null) {
            return true;
        }
        Object value = inputMap.get(MetaOperation.DELAY_SHOW_COUNTDOWN);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.isEmpty()) {
                return true;
            }
            return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
        }
        return true;
    }

    private void maybeStartDelayProgress(@Nullable OperationItem opItem) {
        stopDelayProgress();
        if (opItem == null || opItem.delayDurationMs <= 0L || !opItem.delayShowCountdown) {
            return;
        }
        activeDelayOperationId = opItem.id;
        activeDelayDurationMs = opItem.delayDurationMs;
        activeDelayStartMs = SystemClock.uptimeMillis();
        renderDelayProgress(true, 0L, activeDelayDurationMs);
        uiHandler.postDelayed(delayProgressRunnable, DELAY_PROGRESS_UPDATE_INTERVAL_MS);
    }

    private void tickDelayProgress() {
        if (TextUtils.isEmpty(activeDelayOperationId) || activeDelayDurationMs <= 0L) {
            renderDelayProgress(false, 0L, 0L);
            return;
        }
        long elapsed = Math.min(activeDelayDurationMs, Math.max(0L, SystemClock.uptimeMillis() - activeDelayStartMs));
        renderDelayProgress(true, elapsed, activeDelayDurationMs);
        if (elapsed < activeDelayDurationMs) {
            uiHandler.postDelayed(delayProgressRunnable, DELAY_PROGRESS_UPDATE_INTERVAL_MS);
        }
    }

    private void stopDelayProgress() {
        uiHandler.removeCallbacks(delayProgressRunnable);
        activeDelayOperationId = null;
        activeDelayDurationMs = 0L;
        activeDelayStartMs = 0L;
        renderDelayProgress(false, 0L, 0L);
    }

    private void renderDelayProgressState() {
        if (!TextUtils.isEmpty(activeDelayOperationId) && activeDelayDurationMs > 0L) {
            long elapsed = Math.min(activeDelayDurationMs, Math.max(0L, SystemClock.uptimeMillis() - activeDelayStartMs));
            renderDelayProgress(true, elapsed, activeDelayDurationMs);
            return;
        }
        renderDelayProgress(false, 0L, 0L);
    }

    private void renderDelayProgress(boolean visible, long elapsedMs, long durationMs) {
        if (!visible || durationMs <= 0L) {
            lastDelayOverlayProgress = -1;
            lastDelayOverlayText = "";
            hideDelayOverlay();
            return;
        }
        ensureDelayOverlay();
        if (delayOverlayProgressBar == null || delayOverlayValueText == null) {
            return;
        }
        if (delayOverlayView.getVisibility() != View.VISIBLE) {
            delayOverlayView.setVisibility(View.VISIBLE);
        }
        long safeElapsed = Math.max(0L, Math.min(elapsedMs, durationMs));
        int progress = (int) Math.min(1000L, (safeElapsed * 1000L) / durationMs);
        String progressText = "延迟 " + formatDelayDuration(safeElapsed) + " / " + formatDelayDuration(durationMs);
        if (progress != lastDelayOverlayProgress) {
            delayOverlayProgressBar.setProgress(progress);
            lastDelayOverlayProgress = progress;
        }
        if (!progressText.equals(lastDelayOverlayText)) {
            delayOverlayValueText.setText(progressText);
            lastDelayOverlayText = progressText;
        }
    }

    private void ensureDelayOverlay() {
        if (delayOverlayView != null && delayOverlayView.getParent() != null) {
            return;
        }
        try {
            if (delayOverlayView == null) {
                delayOverlayView = LayoutInflater.from(this).inflate(R.layout.overlay_delay_progress, null);
                delayOverlayProgressBar = delayOverlayView.findViewById(R.id.progress_delay_overlay);
                delayOverlayValueText = delayOverlayView.findViewById(R.id.tv_delay_overlay_value);
            }
            delayOverlayLp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            delayOverlayLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            delayOverlayLp.x = 0;
            delayOverlayLp.y = getStatusBarHeightPx() + dp(38);
            wm.addView(delayOverlayView, delayOverlayLp);
        } catch (Exception e) {
            Log.e(TAG, "add delay overlay failed", e);
            delayOverlayLp = null;
            delayOverlayProgressBar = null;
            delayOverlayValueText = null;
            if (delayOverlayView != null && delayOverlayView.getParent() == null) {
                delayOverlayView = null;
            }
        }
    }

    private void hideDelayOverlay() {
        if (delayOverlayView != null) {
            delayOverlayView.setVisibility(View.GONE);
        }
    }

    private void destroyDelayOverlay() {
        if (delayOverlayView != null) {
            safeRemoveView(delayOverlayView);
        }
        delayOverlayView = null;
        delayOverlayLp = null;
        delayOverlayProgressBar = null;
        delayOverlayValueText = null;
        lastDelayOverlayProgress = -1;
        lastDelayOverlayText = "";
    }

    private String formatDelayDuration(long durationMs) {
        if (durationMs <= 0L) {
            return "0s";
        }
        if (durationMs < 1000L) {
            return durationMs + "ms";
        }
        if (durationMs % 1000L == 0L) {
            return (durationMs / 1000L) + "s";
        }
        return String.format(Locale.getDefault(), "%.1fs", durationMs / 1000f);
    }

    private FlowGraphView currentFlowGraphView;
    private Runnable flowGraphRefreshAction;
    private String flowGraphSelectedNodeId;

    private void updateFlowGraphHighlight(String operationId) {
        if (currentFlowGraphView != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (currentFlowGraphView != null)
                    currentFlowGraphView.setHighlightedNodeId(operationId);
            });
        }
    }

    @Override
    public void onOperationComplete(String operationId, boolean success) {
        if (TextUtils.equals(activeDelayOperationId, operationId)) {
            stopDelayProgress();
        }
        Long startMs = opStartTimeMs.remove(operationId);
        long cost = startMs == null ? -1 : (System.currentTimeMillis() - startMs);
        appendRunLog("[done] " + operationId + " | success=" + success + (cost >= 0 ? (" | " + cost + "ms") : ""));
        if (cost >= 0) {
            opDurationsMs.add(cost);
        }
        if (success) {
            opSuccessCount++;
        } else {
            opFailureCount++;
            recordFailureReason("operation_failed");
        }
        updateRuntimeMetricsPanel();
        // operation 完成，更新状态
        if (!success) {
            // 如果失败，更新状态为错误
            updateRunningPanelStatus("运行出错", 0xFFF44336);
        }
        syncProjectPanelRuntimeUi();
    }

    @Override
    public void onScriptComplete() {
        refreshAppLaunchPollingState();
        stopDelayProgress();
        appendRunLog("=== Run Complete === total=" + (System.currentTimeMillis() - currentRunStartMs) + "ms");
        persistCurrentRunLog();
        CrashLogger.finishRunSession(this, "completed");
        updateRuntimeMetricsPanel();
        // 所有 operation 执行完成
        if (runningPanelAdapter != null) {
            runningPanelAdapter.setRunningPosition(-1);
        }
        if (currentOperationAdapter != null) {
            currentOperationAdapter.clearRunningPosition();
        }

        // 更新状态为完成
        updateRunningPanelStatus("已完成", 0xFF4CAF50);

        // 隐藏悬浮球状态
        if (ballStatusText != null) {
            ballStatusText.setVisibility(View.GONE);
        }

        currentRunningOperationId = "";
        currentRunningOperationName = "";
        isPaused = false;
        hideProjectPanelDock();
        setBallVisible(true);
        updateNotification("运行完成");

        // Phase 4B: 隐藏步骤覆盖层
        hideStepOverlay();

        // Phase 4A: 清除 FlowGraph 高亮
        if (currentFlowGraphView != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (currentFlowGraphView != null) currentFlowGraphView.setHighlightedNodeId(null);
            });
        }

        // 清除监听器
        ScriptRunner.clearExecutionListener();

        hideRunningPanel();
        syncProjectPanelRuntimeUi();
        Toast.makeText(this, "所有操作执行完成", Toast.LENGTH_SHORT).show();
    }

    private void refreshCurrentLevelList() {
        if (projectPanelView == null) {
            return;
        }
        if (currentLevel == NavigationLevel.PROJECT) {
            loadProjects();
        } else if (currentLevel == NavigationLevel.TASK && currentProjectDir != null) {
            loadTasks(currentProjectDir);
        } else if (currentLevel == NavigationLevel.OPERATION && currentTaskDir != null) {
            loadOperations(currentTaskDir);
        }
    }

    private WindowManager.LayoutParams buildDialogLayoutParams(int widthDp, boolean focusable) {
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!focusable) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }

        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean landscape = metrics.widthPixels > metrics.heightPixels;
        int width = Math.min(dp(widthDp), (int) (metrics.widthPixels * 0.96f));
        int height = landscape
                ? Math.max(dp(240), (int) (metrics.heightPixels * 0.93f))
                : WindowManager.LayoutParams.WRAP_CONTENT;

        WindowManager.LayoutParams dialogLp = new WindowManager.LayoutParams(
                width,
                height,
                type,
                flags,
                PixelFormat.TRANSLUCENT
        );
        dialogLp.gravity = Gravity.CENTER;
        return dialogLp;
    }

    private void applyTemplateLibraryDialogViewport(WindowManager.LayoutParams dialogLp) {
        if (dialogLp == null) {
            return;
        }
        if (dialogHelpers != null) {
            dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 350, 0.84f, 0.92f);
            return;
        }
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean landscape = metrics.widthPixels > metrics.heightPixels;
        int maxWidth = Math.max(dp(240), (int) (metrics.widthPixels * 0.96f));
        dialogLp.width = Math.min(dp(350), maxWidth);
        dialogLp.height = Math.max(dp(240), (int) (metrics.heightPixels * (landscape ? 0.92f : 0.84f)));
    }

    private void setupDialogMoveAndScale(View dialogView,
                                         WindowManager.LayoutParams dialogLp,
                                         int normalWidthDp,
                                         int expandedWidthDp) {
        if (dialogView == null || dialogLp == null) {
            return;
        }
        View dragHeader = dialogView.findViewById(R.id.dialog_drag_header);
        if (dragHeader != null) {
            dragHeader.setOnTouchListener(new DragTouchListener(dialogLp, wm, dialogView, this));
        }

        View scaleView = dialogView.findViewById(R.id.btn_scale_dialog);
        if (scaleView instanceof TextView) {
            TextView btnScale = (TextView) scaleView;
            final boolean[] expanded = {false};
            btnScale.setOnClickListener(v -> {
                expanded[0] = !expanded[0];
                dialogLp.width = dp(expanded[0] ? expandedWidthDp : normalWidthDp);
                try {
                    wm.updateViewLayout(dialogView, dialogLp);
                } catch (Exception ignored) {
                }
                btnScale.setText(expanded[0] ? "缩小" : "放大");
            });
        }
    }

    private void safeRemoveView(View view) {
        safeRemoveView(wm, view);
    }

    private void safeRemoveView(@Nullable WindowManager targetWm, View view) {
        if (view == null) {
            return;
        }
        try {
            WindowManager removeWm = targetWm != null ? targetWm : wm;
            if (removeWm != null) {
                removeWm.removeView(view);
            }
        } catch (Exception ignored) {
        }
    }

    private String generateOperationId() {
        return "op_" + System.currentTimeMillis();
    }

    private boolean appendOperation(JSONObject operationObject) {
        if (currentTaskDir == null) {
            Toast.makeText(this, "当前 Task 无效", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            File jsonFile = new File(currentTaskDir, "operations.json");
            JSONArray jsonArray = new JSONArray();
            if (jsonFile.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String content = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
                if (!TextUtils.isEmpty(content.trim())) {
                    jsonArray = new JSONArray(content);
                }
            }

            jsonArray.put(operationObject);

            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(jsonArray.toString(2));
            }

            reloadCurrentProject();
            loadOperations(currentTaskDir);
            Toast.makeText(this, "已添加操作", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "添加 operation 失败", e);
            Toast.makeText(this, "添加失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private JSONArray readOperationsArray() throws Exception {
        File jsonFile = new File(currentTaskDir, "operations.json");
        if (!jsonFile.exists()) {
            return new JSONArray();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return new JSONArray();
        }
        String content = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
        if (TextUtils.isEmpty(content.trim())) {
            return new JSONArray();
        }
        return new JSONArray(content);
    }

    private Set<String> collectReferencedImageFiles(JSONArray operationsArray) {
        Set<String> refs = new HashSet<>();
        if (operationsArray == null) {
            return refs;
        }
        for (int i = 0; i < operationsArray.length(); i++) {
            JSONObject op = operationsArray.optJSONObject(i);
            if (op == null) {
                continue;
            }
            JSONObject inputMap = op.optJSONObject("inputMap");
            if (inputMap == null) {
                continue;
            }
            String name = inputMap.optString(MetaOperation.SAVEFILENAME, "").trim();
            if (TextUtils.isEmpty(name)) {
                continue;
            }
            refs.add(name);
            if (!name.contains(".")) {
                refs.add(name + ".png");
            }
        }
        return refs;
    }

    private int cleanupUnusedTaskImages(JSONArray operationsArray) {
        if (currentTaskDir == null) {
            return 0;
        }
        try {
            Set<String> refs = collectReferencedImageFiles(operationsArray);
            File imgDir = new File(currentTaskDir, "img");
            if (!imgDir.exists()) {
                return 0;
            }

            int deleted = 0;
            File[] files = imgDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String name = file.getName();
                    if ("manifest.json".equalsIgnoreCase(name)) {
                        continue;
                    }
                    String lower = name.toLowerCase(Locale.ROOT);
                    boolean isImage = lower.endsWith(".png") || lower.endsWith(".jpg")
                            || lower.endsWith(".jpeg") || lower.endsWith(".webp");
                    if (!isImage) {
                        continue;
                    }
                    if (!refs.contains(name) && file.delete()) {
                        deleted++;
                    }
                }
            }

            File manifestFile = new File(imgDir, "manifest.json");
            if (manifestFile.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
                if (!TextUtils.isEmpty(content.trim())) {
                    JSONObject manifest = new JSONObject(content);
                    List<String> keys = new ArrayList<>();
                    java.util.Iterator<String> it = manifest.keys();
                    while (it.hasNext()) {
                        keys.add(it.next());
                    }
                    boolean changed = false;
                    for (String key : keys) {
                        if (!refs.contains(key)) {
                            manifest.remove(key);
                            changed = true;
                        }
                    }
                    if (changed) {
                        try (FileWriter writer = new FileWriter(manifestFile)) {
                            writer.write(manifest.toString(2));
                        }
                    }
                }
            }

            if (deleted > 0) {
                refreshTemplateCachesForCurrentProject();
            }
            return deleted;
        } catch (Exception e) {
            Log.w(TAG, "清理未使用模板失败", e);
            return 0;
        }
    }

    private boolean writeOperationsArray(JSONArray jsonArray, String successText) {
        if (currentTaskDir == null) {
            return false;
        }
        try {
            File jsonFile = new File(currentTaskDir, "operations.json");
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(jsonArray.toString(2));
            }
            int cleaned = 0;
            reloadCurrentProject();
            loadOperations(currentTaskDir);
            if (!TextUtils.isEmpty(successText)) {
                if (cleaned > 0) {
                    Toast.makeText(this, successText + "，清理图片 " + cleaned + " 张", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, successText, Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "写入 operations.json 失败", e);
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private int findOperationIndex(JSONArray jsonArray, String operationId) {
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.optJSONObject(i);
            if (jsonObject != null && operationId.equals(jsonObject.optString("id"))) {
                return i;
            }
        }
        return -1;
    }

    private void deleteOperation(String operationId) {
        if (currentTaskDir == null || TextUtils.isEmpty(operationId)) {
            return;
        }
        try {
            JSONArray original = readOperationsArray();
            JSONArray result = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < original.length(); i++) {
                JSONObject item = original.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                if (!removed && operationId.equals(item.optString("id"))) {
                    removed = true;
                    continue;
                }
                result.put(item);
            }
            if (!removed) {
                Toast.makeText(this, "未找到要删除的操作", Toast.LENGTH_SHORT).show();
                return;
            }
            writeOperationsArray(result, "已删除操作");
        } catch (Exception e) {
            Log.e(TAG, "删除操作失败", e);
            Toast.makeText(this, "删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean hasOperationClipboard() {
        return !operationClipboardLibrary.isEmpty();
    }

    private void copyOperationToClipboard(String operationId) {
        if (currentTaskDir == null || TextUtils.isEmpty(operationId)) {
            return;
        }
        try {
            JSONArray original = readOperationsArray();
            int index = findOperationIndex(original, operationId);
            if (index < 0) {
                Toast.makeText(this, "未找到要复制的节点", Toast.LENGTH_SHORT).show();
                return;
            }
            JSONObject source = original.getJSONObject(index);
            OperationClipboardEntry entry = new OperationClipboardEntry(
                    new JSONObject(source.toString()),
                    source.optString("name", "节点"),
                    currentTaskDir.getAbsolutePath(),
                    System.currentTimeMillis());
            operationClipboardLibrary.add(0, entry);
            while (operationClipboardLibrary.size() > OPERATION_CLIPBOARD_LIMIT) {
                operationClipboardLibrary.remove(operationClipboardLibrary.size() - 1);
            }
            Toast.makeText(this, "已加入节点库: " + entry.name + " (" + operationClipboardLibrary.size() + ")", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "复制到节点剪贴板失败", e);
            Toast.makeText(this, "复制失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void pasteOperationRelative(String targetOperationId, boolean insertBefore) {
        showOperationClipboardDialog(targetOperationId, insertBefore);
    }

    private void pasteOperationEntryRelative(@NonNull OperationClipboardEntry entry, String targetOperationId, boolean insertBefore) {
        if (currentTaskDir == null || TextUtils.isEmpty(targetOperationId)) {
            return;
        }
        try {
            JSONArray original = readOperationsArray();
            int index = findOperationIndex(original, targetOperationId);
            if (index < 0) {
                Toast.makeText(this, "未找到插入位置", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject pasted = new JSONObject(entry.operationJson.toString());
            pasted.put("id", generateOperationId());
            String oldName = pasted.optString("name", TextUtils.isEmpty(entry.name) ? "节点" : entry.name);
            pasted.put("name", oldName + " - 粘贴");

            JSONArray result = new JSONArray();
            String newOperationId = pasted.optString("id");
            for (int i = 0; i < original.length(); i++) {
                JSONObject item = original.optJSONObject(i);
                if (i == index && insertBefore) {
                    result.put(pasted);
                }
                if (item != null) {
                    result.put(item);
                }
                if (i == index && !insertBefore) {
                    result.put(pasted);
                }
            }
            pendingSelectedOperationId = newOperationId;

            String successText = insertBefore ? "已插入节点" : "已粘贴节点";
            JSONObject inputMap = pasted.optJSONObject("inputMap");
            boolean crossTaskPaste = !TextUtils.isEmpty(entry.sourceTaskPath)
                    && !TextUtils.equals(entry.sourceTaskPath, currentTaskDir.getAbsolutePath());
            if (crossTaskPaste && inputMap != null && !TextUtils.isEmpty(inputMap.optString(MetaOperation.SAVEFILENAME))) {
                successText = successText + "，请确认模板图片已同步";
            }
            writeOperationsArray(result, successText);
        } catch (Exception e) {
            Log.e(TAG, "粘贴节点失败", e);
            Toast.makeText(this, "粘贴失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void duplicateOperation(String operationId) {
        if (currentTaskDir == null || TextUtils.isEmpty(operationId)) {
            return;
        }
        try {
            JSONArray original = readOperationsArray();
            int index = findOperationIndex(original, operationId);
            if (index < 0) {
                Toast.makeText(this, "未找到要复制的操作", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject source = original.getJSONObject(index);
            JSONObject copy = new JSONObject(source.toString());
            copy.put("id", generateOperationId());
            String oldName = source.optString("name", "操作");
            copy.put("name", oldName + " - 副本");

            JSONArray result = new JSONArray();
            for (int i = 0; i < original.length(); i++) {
                JSONObject item = original.optJSONObject(i);
                if (item != null) {
                    result.put(item);
                }
                if (i == index) {
                    result.put(copy);
                }
            }
            writeOperationsArray(result, "已复制操作");
        } catch (Exception e) {
            Log.e(TAG, "复制操作失败", e);
            Toast.makeText(this, "复制失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showOperationClipboardDialog(String targetOperationId, boolean insertBefore) {
        if (!hasOperationClipboard()) {
            Toast.makeText(this, "节点库为空，先复制一个节点吧", Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_node_clipboard, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.78f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 420, null);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        TextView btnClear = dialogView.findViewById(R.id.btn_clear);
        TextView btnCancel = dialogView.findViewById(R.id.btnStart);
        TextView btnConfirm = dialogView.findViewById(R.id.btnAddNode);
        TextView tvEmpty = dialogView.findViewById(R.id.tv_empty);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recyclerView);

        if (tvTitle != null) {
            tvTitle.setText(insertBefore ? "从节点库插入到前面" : "从节点库粘贴到后面");
        }
        if (btnConfirm != null) {
            btnConfirm.setText(insertBefore ? "插入节点" : "粘贴节点");
        }

        OperationClipboardLibraryAdapter adapter = new OperationClipboardLibraryAdapter(
                operationClipboardLibrary,
                this::removeOperationClipboardEntry);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        Runnable refreshUi = () -> {
            boolean empty = operationClipboardLibrary.isEmpty();
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (tvEmpty != null) {
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
            if (btnConfirm != null) {
                btnConfirm.setEnabled(!empty && adapter.getSelectedEntry() != null);
                btnConfirm.setAlpha(btnConfirm.isEnabled() ? 1f : 0.45f);
            }
            if (btnClear != null) {
                btnClear.setVisibility(empty ? View.GONE : View.VISIBLE);
            }
        };
        adapter.setOnSelectionChanged(refreshUi);
        refreshUi.run();

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                operationClipboardLibrary.clear();
                adapter.notifyDataSetChanged();
                refreshUi.run();
            });
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> safeRemoveView(dialogView));
        }
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                OperationClipboardEntry selectedEntry = adapter.getSelectedEntry();
                if (selectedEntry == null) {
                    Toast.makeText(this, "先从节点库选一个节点", Toast.LENGTH_SHORT).show();
                    return;
                }
                safeRemoveView(dialogView);
                pasteOperationEntryRelative(selectedEntry, targetOperationId, insertBefore);
            });
        }
        dialogView.setOnClickListener(v -> {
            if (v == dialogView) {
                safeRemoveView(dialogView);
            }
        });
    }

    private void removeOperationClipboardEntry(@Nullable OperationClipboardEntry entry) {
        if (entry == null) {
            return;
        }
        operationClipboardLibrary.remove(entry);
    }

    private void attachOperationDragHelperIfNeeded(@Nullable RecyclerView recyclerView) {
        if (recyclerView == null) {
            return;
        }
        if (operationDragHelper != null) {
            return;
        }
        operationDragHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            private boolean moved;

            @Override
            public boolean isLongPressDragEnabled() {
                return currentLevel == NavigationLevel.OPERATION
                        && operationBatchMode
                        && TextUtils.isEmpty(normalizeQuery(currentSearchQuery));
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                if (!(recyclerView.getAdapter() instanceof OperationPanelAdapter) || currentOperationAdapter == null) {
                    return false;
                }
                moved = true;
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                return currentOperationAdapter.moveItem(from, to);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    moved = false;
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (moved) {
                    persistOperationOrderFromAdapter();
                    moved = false;
                }
            }
        });
        operationDragHelper.attachToRecyclerView(recyclerView);
    }

    private void persistOperationOrderFromAdapter() {
        if (currentTaskDir == null || currentOperationAdapter == null) {
            return;
        }
        if (!TextUtils.isEmpty(normalizeQuery(currentSearchQuery))) {
            Toast.makeText(this, "搜索结果中不支持拖拽排序", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONArray original = readOperationsArray();
            List<String> orderedIds = currentOperationAdapter.getOperationIdsSnapshot();
            if (orderedIds.isEmpty() || orderedIds.size() != original.length()) {
                return;
            }
            Map<String, JSONObject> byId = new HashMap<>();
            for (int i = 0; i < original.length(); i++) {
                JSONObject item = original.optJSONObject(i);
                if (item != null) {
                    byId.put(item.optString("id"), item);
                }
            }
            JSONArray reordered = new JSONArray();
            for (String id : orderedIds) {
                JSONObject item = byId.get(id);
                if (item != null) {
                    reordered.put(item);
                }
            }
            if (reordered.length() != original.length()) {
                return;
            }
            OperationItem selectedItem = currentOperationAdapter.getSelectedItem();
            pendingSelectedOperationId = selectedItem != null ? selectedItem.id : null;
            writeOperationsArray(reordered, "已更新节点顺序");
        } catch (Exception e) {
            Log.e(TAG, "拖拽排序保存失败", e);
            Toast.makeText(this, "排序保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void moveOperation(String operationId, int direction) {
        if (currentTaskDir == null || TextUtils.isEmpty(operationId) || direction == 0) {
            return;
        }
        try {
            JSONArray original = readOperationsArray();
            int currentIndex = findOperationIndex(original, operationId);
            if (currentIndex < 0) {
                Toast.makeText(this, "未找到要移动的操作", Toast.LENGTH_SHORT).show();
                return;
            }

            int targetIndex = currentIndex + direction;
            if (targetIndex < 0 || targetIndex >= original.length()) {
                Toast.makeText(this, "已经到边界了", Toast.LENGTH_SHORT).show();
                return;
            }

            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < original.length(); i++) {
                JSONObject item = original.optJSONObject(i);
                if (item != null) {
                    list.add(item);
                }
            }
            if (currentIndex >= list.size() || targetIndex >= list.size()) {
                return;
            }

            JSONObject current = list.get(currentIndex);
            list.set(currentIndex, list.get(targetIndex));
            list.set(targetIndex, current);

            JSONArray result = new JSONArray();
            for (JSONObject item : list) {
                result.put(item);
            }
            pendingSelectedOperationId = operationId;
            writeOperationsArray(result, direction < 0 ? "已上移" : "已下移");
        } catch (Exception e) {
            Log.e(TAG, "移动操作失败", e);
            Toast.makeText(this, "移动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setOperationBatchMode(boolean enabled) {
        operationBatchMode = enabled;
        if (!enabled) {
            batchSelectedOperationIds.clear();
        } else if (!TextUtils.isEmpty(normalizeQuery(currentSearchQuery))) {
            Toast.makeText(this, "搜索状态下仅支持批量选择，拖拽排序请先清空搜索", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "批量模式已开启，可长按节点拖拽排序", Toast.LENGTH_SHORT).show();
        }
        refreshProjectPanelFooterState();

        if (currentOperationAdapter != null) {
            currentOperationAdapter.setBatchMode(enabled);
            currentOperationAdapter.setBatchSelectedIds(batchSelectedOperationIds);
        }
    }

    private void updateOperationActionButtons(TextView btnRun, TextView btnEdit, TextView btnBatch) {
        refreshProjectPanelFooterState();
    }

    private void updateBatchActionCount() {
        refreshProjectPanelFooterState();
    }

    private void deleteBatchSelectedOperations() {
        if (!operationBatchMode) {
            return;
        }
        if (batchSelectedOperationIds.isEmpty()) {
            Toast.makeText(this, "请先选择要删除的操作", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONArray original = readOperationsArray();
            JSONArray result = new JSONArray();
            int removedCount = 0;
            for (int i = 0; i < original.length(); i++) {
                JSONObject item = original.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String opId = item.optString("id");
                if (batchSelectedOperationIds.contains(opId)) {
                    removedCount++;
                    continue;
                }
                result.put(item);
            }

            if (removedCount <= 0) {
                Toast.makeText(this, "未删除任何操作", Toast.LENGTH_SHORT).show();
                return;
            }
            writeOperationsArray(result, "已删除 " + removedCount + " 个操作");
            setOperationBatchMode(false);
        } catch (Exception e) {
            Log.e(TAG, "批量删除失败", e);
            Toast.makeText(this, "批量删除失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private List<String> getCurrentTaskOperationIds(String excludeId) {
        List<String> ids = new ArrayList<>();
        if (currentTaskDir == null) {
            return ids;
        }
        try {
            JSONArray operations = readOperationsArray();
            for (int i = 0; i < operations.length(); i++) {
                JSONObject item = operations.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id", "");
                if (TextUtils.isEmpty(id)) {
                    continue;
                }
                if (!TextUtils.isEmpty(excludeId) && excludeId.equals(id)) {
                    continue;
                }
                ids.add(id);
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    private List<OperationIdPickerAdapter.OperationPickItem> getCurrentTaskOperationPickItems(String excludeId) {
        List<OperationIdPickerAdapter.OperationPickItem> items = new ArrayList<>();
        if (currentTaskDir == null) {
            return items;
        }
        try {
            JSONArray operations = readOperationsArray();
            for (int i = 0; i < operations.length(); i++) {
                JSONObject item = operations.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id", "");
                if (TextUtils.isEmpty(id)) {
                    continue;
                }
                if (!TextUtils.isEmpty(excludeId) && excludeId.equals(id)) {
                    continue;
                }
                String name = item.optString("name", "未命名");
                int typeInt = item.optInt("type", -1);
                String type = getOperationTypeName(typeInt);
                items.add(new OperationIdPickerAdapter.OperationPickItem(items.size() + 1, id, name, type));
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private List<FlowNodeAdapter.FlowNodeItem> getCurrentTaskFlowNodes() {
        List<FlowNodeAdapter.FlowNodeItem> nodes = new ArrayList<>();
        if (currentTaskDir == null) {
            return nodes;
        }
        try {
            JSONArray operations = readOperationsArray();
            for (int i = 0; i < operations.length(); i++) {
                JSONObject op = operations.optJSONObject(i);
                if (op == null) {
                    continue;
                }
                String id = op.optString("id", "");
                String name = op.optString("name", "未命名");
                int typeInt = op.optInt("type", -1);
                String type = getOperationTypeName(typeInt);
                JSONObject inputMap = op.optJSONObject("inputMap");
                String nextId;
                String fallbackId;
                if (typeInt == 16) {
                    // LOOP: body next is main path, exit next is fallback
                    nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.LOOP_BODY_NEXT, "");
                    fallbackId = inputMap == null ? "" : inputMap.optString(MetaOperation.LOOP_EXIT_NEXT, "");
                } else if (typeInt == 10 || typeInt == 15) {
                    // Condition / Switch branch: default path + first explicit branch for list hint
                    nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.BRANCH_DEFAULT_NEXT, "");
                    fallbackId = firstBranchTarget(inputMap);
                } else {
                    nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.NEXT_OPERATION_ID, "");
                    fallbackId = inputMap == null ? "" : inputMap.optString(MetaOperation.FALLBACKOPERATIONID, "");
                }
                nodes.add(new FlowNodeAdapter.FlowNodeItem(i + 1, id, name, type, nextId, fallbackId));
            }
        } catch (Exception e) {
            Log.w(TAG, "读取流程图节点失败", e);
        }
        return nodes;
    }

    private List<FlowGraphView.Node> getCurrentTaskGraphNodes() {
        List<FlowGraphView.Node> nodes = new ArrayList<>();
        if (currentTaskDir == null) {
            return nodes;
        }
        try {
            JSONArray operations = readOperationsArray();
            for (int i = 0; i < operations.length(); i++) {
                JSONObject op = operations.optJSONObject(i);
                if (op == null) {
                    continue;
                }
                FlowGraphView.Node node = new FlowGraphView.Node();
                node.order = i + 1;
                node.id = op.optString("id", "");
                node.name = op.optString("name", "未命名");
                node.typeCode = op.optInt("type", -1);
                node.type = getOperationTypeName(node.typeCode);

                JSONObject inputMap = op.optJSONObject("inputMap");
                int typeInt = node.typeCode;
                if (typeInt == 16) {
                    node.nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.LOOP_BODY_NEXT, "");
                    node.fallbackId = inputMap == null ? "" : inputMap.optString(MetaOperation.LOOP_EXIT_NEXT, "");
                } else if (typeInt == 10 || typeInt == 15) {
                    node.nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.BRANCH_DEFAULT_NEXT, "");
                    appendBranchEdges(node, inputMap);
                } else {
                    node.nextId = inputMap == null ? "" : inputMap.optString(MetaOperation.NEXT_OPERATION_ID, "");
                    node.fallbackId = inputMap == null ? "" : inputMap.optString(MetaOperation.FALLBACKOPERATIONID, "");
                }
                nodes.add(node);
            }
        } catch (Exception e) {
            Log.w(TAG, "读取流程图节点失败", e);
        }
        return nodes;
    }

    private String firstBranchTarget(JSONObject inputMap) {
        if (inputMap == null) {
            return "";
        }
        JSONArray rules = inputMap.optJSONArray(MetaOperation.BRANCH_RULES);
        if (rules == null) {
            return "";
        }
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) {
                continue;
            }
            String target = rule.optString("nextOperationId", "");
            if (TextUtils.isEmpty(target)) {
                target = rule.optString("next", "");
            }
            if (TextUtils.isEmpty(target)) {
                target = rule.optString("target", "");
            }
            if (!TextUtils.isEmpty(target)) {
                return target;
            }
        }
        return "";
    }

    private void appendBranchEdges(FlowGraphView.Node node, JSONObject inputMap) {
        if (node == null || inputMap == null) {
            return;
        }
        JSONArray rules = inputMap.optJSONArray(MetaOperation.BRANCH_RULES);
        if (rules == null) {
            return;
        }
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) {
                continue;
            }
            String target = rule.optString("nextOperationId", "");
            if (TextUtils.isEmpty(target)) {
                target = rule.optString("next", "");
            }
            if (TextUtils.isEmpty(target)) {
                target = rule.optString("target", "");
            }
            if (TextUtils.isEmpty(target)) {
                continue;
            }
            targets.add(target);
        }
        for (int i = 0; i < targets.size(); i++) {
            FlowGraphView.Node.Edge edge = new FlowGraphView.Node.Edge();
            edge.toId = targets.get(i);
            edge.kind = "branch";
            edge.fromFallbackPort = false;
            edge.sourceSlotIndex = i;
            edge.sourceSlotCount = targets.size();
            node.extraEdges.add(edge);
        }
    }

    // ==================== Flow Graph Panel (full floating window) ====================

    private View flowGraphPanelView;
    private WindowManager.LayoutParams flowGraphPanelLp;

    private void showFlowGraphDialog() {
        // Entry point kept for compatibility — delegates to full panel
        showFlowGraphPanel();
    }

    private void showFlowGraphPanel() {
        if (flowGraphPanelView != null) {
            // Already open — bring to front
            try { wm.removeView(flowGraphPanelView); } catch (Exception ignored) {}
            flowGraphPanelView = null;
        }

        final List<FlowNodeAdapter.FlowNodeItem> nodes = getCurrentTaskFlowNodes();

        flowGraphPanelView = LayoutInflater.from(this).inflate(R.layout.window_flow_graph_panel, null);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        flowGraphPanelLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        flowGraphPanelLp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        adaptPanelSizeToScreen(flowGraphPanelLp, 340, 520);
        flowGraphPanelLp.x = getSharedPanelX();
        flowGraphPanelLp.y = getSharedPanelY();
        wm.addView(flowGraphPanelView, flowGraphPanelLp);

        // Drag + resize
        View dragHeader  = flowGraphPanelView.findViewById(R.id.drag_header);
        View resizeHandle = flowGraphPanelView.findViewById(R.id.resize_handle);
        dragHeader.setOnTouchListener(new DragTouchListener(flowGraphPanelLp, wm, flowGraphPanelView, this, true));
        resizeHandle.setOnTouchListener(new PanelResizeTouchListener(
                flowGraphPanelLp, wm, flowGraphPanelView, this, (int) dp(300), (int) dp(400)));

        FlowGraphView graphView = flowGraphPanelView.findViewById(R.id.flow_graph_view);
        graphView.setInteractionReadOnly(true);
        TextView tvSelected   = flowGraphPanelView.findViewById(R.id.tv_flow_selected);
        TextView btnEdit      = flowGraphPanelView.findViewById(R.id.btn_flow_edit_node);
        TextView btnSetNext   = flowGraphPanelView.findViewById(R.id.btn_flow_set_next);
        TextView btnSetFall   = flowGraphPanelView.findViewById(R.id.btn_flow_set_fallback);
        TextView btnClearConn = flowGraphPanelView.findViewById(R.id.btn_flow_clear_conn);

        final FlowNodeAdapter.FlowNodeItem[] sel = {null};

        // Render helper
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            List<FlowNodeAdapter.FlowNodeItem> latest = getCurrentTaskFlowNodes();
            nodes.clear();
            nodes.addAll(latest);
            graphView.setNodes(getCurrentTaskGraphNodes());
            if (!TextUtils.isEmpty(flowGraphSelectedNodeId)) {
                FlowNodeAdapter.FlowNodeItem refreshed = findFlowNodeById(nodes, flowGraphSelectedNodeId);
                sel[0] = refreshed;
                graphView.setSelectedNodeId(refreshed == null ? null : refreshed.id);
                flowGraphSelectedNodeId = refreshed == null ? null : refreshed.id;
            }
            updateFlowSelectionUi(sel[0], tvSelected, btnEdit, btnSetNext, btnSetFall);
        };
        flowGraphRefreshAction = render[0];

        // Select listener
        graphView.setOnNodeSelectListener(node -> {
            sel[0] = node == null ? null : findFlowNodeById(nodes, node.id);
            flowGraphSelectedNodeId = sel[0] == null ? null : sel[0].id;
            updateFlowSelectionUi(sel[0], tvSelected, btnEdit, btnSetNext, btnSetFall);
        });

        graphView.setOnNodeDoubleTapListener(null);
        graphView.setOnConnectListener(null);

        // Running highlight tracking
        currentFlowGraphView = graphView;
        flowGraphPanelView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) {
                if (currentFlowGraphView == graphView) currentFlowGraphView = null;
            }
        });

        // Header buttons
        flowGraphPanelView.findViewById(R.id.btn_flow_close).setOnClickListener(v -> closeFlowGraphPanel());
        flowGraphPanelView.findViewById(R.id.btn_flow_back).setOnClickListener(v -> closeFlowGraphPanel());
        flowGraphPanelView.findViewById(R.id.btn_flow_center).setOnClickListener(v -> graphView.resetViewTransform());
        flowGraphPanelView.findViewById(R.id.btn_flow_auto_layout).setOnClickListener(v -> {
            graphView.autoArrange();
            Toast.makeText(this, "已自动排列", Toast.LENGTH_SHORT).show();
        });

        // Bottom bar buttons
        btnEdit.setOnClickListener(v -> {
            if (sel[0] == null) {
                Toast.makeText(this, "请先点击选中一个节点", Toast.LENGTH_SHORT).show();
                return;
            }
            flowGraphSelectedNodeId = sel[0].id;
            showEditOperationDialog(
                    new OperationItem(sel[0].name, sel[0].id, sel[0].type, sel[0].order - 1),
                    currentOperationAdapter);
        });

        btnSetNext.setOnClickListener(v -> {
            if (sel[0] == null) { Toast.makeText(this, "请先选中节点", Toast.LENGTH_SHORT).show(); return; }
            showOperationPickerDialog("选择主线下一节点", null, pickedId -> {
                if (updateFlowConnection(sel[0].id, MetaOperation.NEXT_OPERATION_ID, pickedId)) {
                    render[0].run();
                    Toast.makeText(this, "主线已设置", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnSetFall.setOnClickListener(v -> {
            if (sel[0] == null) { Toast.makeText(this, "请先选中节点", Toast.LENGTH_SHORT).show(); return; }
            showOperationPickerDialog("选择分支节点", null, pickedId -> {
                if (updateFlowConnection(sel[0].id, MetaOperation.FALLBACKOPERATIONID, pickedId)) {
                    render[0].run();
                    Toast.makeText(this, "分支已设置", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnClearConn.setOnClickListener(v -> {
            if (sel[0] == null) { Toast.makeText(this, "请先选中节点", Toast.LENGTH_SHORT).show(); return; }
            boolean clearedNext = updateFlowConnection(sel[0].id, MetaOperation.NEXT_OPERATION_ID, "");
            boolean clearedFall = updateFlowConnection(sel[0].id, MetaOperation.FALLBACKOPERATIONID, "");
            if (clearedNext || clearedFall) {
                render[0].run();
                Toast.makeText(this, "已清除连线", Toast.LENGTH_SHORT).show();
            }
        });

        render[0].run();
    }

    private void closeFlowGraphPanel() {
        if (flowGraphPanelView != null) {
            rememberSharedPanelPosition(flowGraphPanelLp);
            safeRemoveView(flowGraphPanelView);
            flowGraphPanelView = null;
            flowGraphPanelLp   = null;
            flowGraphRefreshAction = null;
            flowGraphSelectedNodeId = null;
        }
    }

    private void refreshOpenFlowGraphPanel(@Nullable String preferredNodeId) {
        if (!TextUtils.isEmpty(preferredNodeId)) {
            flowGraphSelectedNodeId = preferredNodeId;
        }
        if (flowGraphPanelView == null || flowGraphRefreshAction == null) {
            return;
        }
        uiHandler.post(flowGraphRefreshAction);
    }

    private FlowNodeAdapter.FlowNodeItem findFlowNodeById(List<FlowNodeAdapter.FlowNodeItem> nodes, String nodeId) {
        if (nodes == null || TextUtils.isEmpty(nodeId)) {
            return null;
        }
        for (FlowNodeAdapter.FlowNodeItem item : nodes) {
            if (TextUtils.equals(nodeId, item.id)) {
                return item;
            }
        }
        return null;
    }

    private void updateFlowSelectionUi(FlowNodeAdapter.FlowNodeItem selected,
                                       TextView tvSelected,
                                       TextView btnEditNode,
                                       TextView btnSetNext,
                                       TextView btnSetFallback) {
        boolean has = selected != null;
        if (!has) {
            tvSelected.setText("未选中节点");
        } else {
            tvSelected.setText("已选中: " + selected.order + " | " + selected.name + " | " + selected.id);
        }
        btnEditNode.setEnabled(has);
        btnSetNext.setEnabled(has);
        btnSetFallback.setEnabled(has);
        btnEditNode.setAlpha(has ? 1f : 0.45f);
        btnSetNext.setAlpha(has ? 1f : 0.45f);
        btnSetFallback.setAlpha(has ? 1f : 0.45f);
    }

    private boolean updateFlowConnection(String operationId, String key, String targetId) {
        if (currentTaskDir == null || TextUtils.isEmpty(operationId) || TextUtils.isEmpty(key)) {
            return false;
        }
        try {
            JSONArray array = readOperationsArray();
            boolean found = false;
            for (int i = 0; i < array.length(); i++) {
                JSONObject op = array.optJSONObject(i);
                if (op == null) {
                    continue;
                }
                if (!TextUtils.equals(operationId, op.optString("id", ""))) {
                    continue;
                }
                JSONObject inputMap = op.optJSONObject("inputMap");
                if (inputMap == null) {
                    inputMap = new JSONObject();
                }
                if (TextUtils.isEmpty(targetId)) {
                    inputMap.remove(key);
                } else {
                    inputMap.put(key, targetId);
                }
                op.put("inputMap", inputMap);
                found = true;
                break;
            }
            if (!found) {
                return false;
            }
            return writeOperationsArray(array, "");
        } catch (Exception e) {
            Log.w(TAG, "更新流程连线失败", e);
            return false;
        }
    }

    private boolean saveFlowNodeOrder(List<String> orderedIds) {
        if (currentTaskDir == null || orderedIds == null || orderedIds.isEmpty()) {
            return false;
        }
        try {
            JSONArray original = readOperationsArray();
            Map<String, JSONObject> map = new HashMap<>();
            List<JSONObject> others = new ArrayList<>();
            for (int i = 0; i < original.length(); i++) {
                JSONObject obj = original.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String id = obj.optString("id", "");
                if (TextUtils.isEmpty(id)) {
                    others.add(obj);
                } else {
                    map.put(id, obj);
                }
            }

            JSONArray sorted = new JSONArray();
            for (String id : orderedIds) {
                JSONObject obj = map.remove(id);
                if (obj != null) {
                    sorted.put(obj);
                }
            }
            for (JSONObject obj : map.values()) {
                sorted.put(obj);
            }
            for (JSONObject obj : others) {
                sorted.put(obj);
            }

            return writeOperationsArray(sorted, "");
        } catch (Exception e) {
            Log.w(TAG, "保存流程顺序失败", e);
            return false;
        }
    }

    private List<String> getCurrentProjectTaskIds() {
        List<String> taskIds = new ArrayList<>();
        if (currentProjectDir == null) {
            return taskIds;
        }
        File[] taskDirs = currentProjectDir.listFiles(File::isDirectory);
        if (taskDirs == null) {
            return taskIds;
        }
        for (File taskDir : taskDirs) {
            taskIds.add(taskDir.getName());
        }
        return taskIds;
    }

    private List<String> getTaskOperationIds(String taskId) {
        List<String> ids = new ArrayList<>();
        if (currentProjectDir == null || TextUtils.isEmpty(taskId)) {
            return ids;
        }
        File taskDir = new File(currentProjectDir, taskId);
        File operationsFile = new File(taskDir, "operations.json");
        if (!operationsFile.exists() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return ids;
        }
        try {
            String content = new String(Files.readAllBytes(operationsFile.toPath()), StandardCharsets.UTF_8);
            if (TextUtils.isEmpty(content.trim())) {
                return ids;
            }
            JSONArray arr = new JSONArray(content);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item != null) {
                    String id = item.optString("id", "");
                    if (!TextUtils.isEmpty(id)) {
                        ids.add(id);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    private void bindAutoComplete(AutoCompleteTextView view, List<String> options) {
        if (view == null) {
            return;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options);
        view.setAdapter(adapter);
        view.setThreshold(0);
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                view.post(view::showDropDown);
            }
        });
        view.setOnClickListener(v -> view.showDropDown());
    }

    private void bindNextOperationSuggestions(View dialogView, String excludeId) {
        AutoCompleteTextView next = dialogView.findViewById(R.id.edt_next_operation);
        if (next == null) {
            return;
        }
        List<OperationIdPickerAdapter.OperationPickItem> items = getCurrentTaskOperationPickItems(excludeId);
        List<String> options = new ArrayList<>();
        for (OperationIdPickerAdapter.OperationPickItem item : items) {
            if (item != null && !TextUtils.isEmpty(item.id)) {
                options.add(item.id);
            }
        }
        bindAutoComplete(next, options);
    }

    private String formatCurrentTaskOperationReference(String operationId) {
        if (TextUtils.isEmpty(operationId)) {
            return "";
        }
        List<OperationIdPickerAdapter.OperationPickItem> items = getCurrentTaskOperationPickItems(null);
        for (OperationIdPickerAdapter.OperationPickItem item : items) {
            if (TextUtils.equals(operationId, item.id)) {
                return formatOperationReferenceLabel(item);
            }
        }
        return operationId;
    }

    private String formatOperationReferenceLabel(OperationIdPickerAdapter.OperationPickItem item) {
        if (item == null) {
            return "";
        }
        String safeName = TextUtils.isEmpty(item.name) ? "未命名" : item.name;
        return String.format(Locale.getDefault(), "#%02d %s <%s>", item.order, safeName, item.id);
    }

    private String extractOperationReferenceId(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        int start = raw.lastIndexOf('<');
        int end = raw.lastIndexOf('>');
        if (start >= 0 && end > start + 1) {
            return raw.substring(start + 1, end).trim();
        }
        return raw.trim();
    }

    private List<String> getCurrentTaskTemplateFiles() {
        List<String> files = new ArrayList<>();
        if (currentTaskDir == null) {
            return files;
        }
        File imgDir = new File(currentTaskDir, "img");
        File[] imgFiles = imgDir.listFiles(f -> {
            if (!f.isFile()) {
                return false;
            }
            String name = f.getName().toLowerCase(Locale.ROOT);
            return !"manifest.json".equals(name)
                    && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp"));
        });
        if (imgFiles == null) {
            return files;
        }
        java.util.Arrays.sort(imgFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : imgFiles) {
            files.add(file.getName());
        }
        return files;
    }

    private void incrementTemplateUsage(Map<String, Integer> usage, String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return;
        }
        String normalized = fileName.trim();
        if (TextUtils.isEmpty(normalized)) {
            return;
        }
        int old = usage.containsKey(normalized) ? usage.get(normalized) : 0;
        usage.put(normalized, old + 1);
        if (!normalized.contains(".")) {
            String png = normalized + ".png";
            int oldPng = usage.containsKey(png) ? usage.get(png) : 0;
            usage.put(png, oldPng + 1);
        }
    }

    private Map<String, Integer> getTemplateUsageCountMap() {
        Map<String, Integer> usage = new HashMap<>();
        if (currentTaskDir == null) {
            return usage;
        }
        try {
            JSONArray operations = readOperationsArray();
            for (int i = 0; i < operations.length(); i++) {
                JSONObject op = operations.optJSONObject(i);
                if (op == null) {
                    continue;
                }
                JSONObject inputMap = op.optJSONObject("inputMap");
                if (inputMap == null) {
                    continue;
                }
                incrementTemplateUsage(usage, inputMap.optString(MetaOperation.SAVEFILENAME, ""));

                JSONObject matchMap = inputMap.optJSONObject(MetaOperation.MATCHMAP);
                if (matchMap != null) {
                    Iterator<String> bboxKeys = matchMap.keys();
                    while (bboxKeys.hasNext()) {
                        String bbox = bboxKeys.next();
                        JSONObject templates = matchMap.optJSONObject(bbox);
                        if (templates == null) {
                            continue;
                        }
                        Iterator<String> templateKeys = templates.keys();
                        while (templateKeys.hasNext()) {
                            incrementTemplateUsage(usage, templateKeys.next());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return usage;
    }

    private List<TemplateLibraryAdapter.TemplateLibraryItem> getCurrentTaskTemplateLibraryItems() {
        List<TemplateLibraryAdapter.TemplateLibraryItem> items = new ArrayList<>();
        if (currentTaskDir == null) {
            return items;
        }
        Map<String, Integer> usageMap = getTemplateUsageCountMap();
        File imgDir = new File(currentTaskDir, "img");
        File[] imgFiles = imgDir.listFiles(f -> {
            if (!f.isFile()) {
                return false;
            }
            String name = f.getName().toLowerCase(Locale.ROOT);
            return !"manifest.json".equals(name)
                    && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp"));
        });
        if (imgFiles == null) {
            return items;
        }
        java.util.Arrays.sort(imgFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : imgFiles) {
            int usageCount = usageMap.containsKey(file.getName()) ? usageMap.get(file.getName()) : 0;
            items.add(new TemplateLibraryAdapter.TemplateLibraryItem(file.getName(), file, usageCount));
        }
        return items;
    }

    private List<String> getMatchMethodOptions() {
        List<String> options = new ArrayList<>();
        options.add(METHOD_TM_CCOEFF_NORMED);
        options.add(METHOD_TM_CCOEFF);
        options.add(METHOD_TM_CCORR_NORMED);
        options.add(METHOD_TM_CCORR);
        options.add(METHOD_TM_SQDIFF_NORMED);
        options.add(METHOD_TM_SQDIFF);
        return options;
    }

    private int parseMethodCode(String methodText) {
        if (TextUtils.isEmpty(methodText)) {
            return 5;
        }
        Matcher matcher = Pattern.compile("\\((\\d+)\\)").matcher(methodText);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {
            }
        }
        String t = methodText.toUpperCase(Locale.ROOT);
        if (t.contains("SQDIFF_NORMED")) return 1;
        if (t.contains("SQDIFF")) return 0;
        if (t.contains("CCORR_NORMED")) return 3;
        if (t.contains("CCORR")) return 2;
        if (t.contains("CCOEFF") && !t.contains("NORMED")) return 4;
        return 5;
    }

    private String methodLabelFromCode(double code) {
        int method = (int) code;
        switch (method) {
            case 0:
                return METHOD_TM_SQDIFF;
            case 1:
                return METHOD_TM_SQDIFF_NORMED;
            case 2:
                return METHOD_TM_CCORR;
            case 3:
                return METHOD_TM_CCORR_NORMED;
            case 4:
                return METHOD_TM_CCOEFF;
            case 5:
            default:
                return METHOD_TM_CCOEFF_NORMED;
        }
    }

    private File resolveTaskTemplateFile(String templateName) {
        if (currentTaskDir == null || TextUtils.isEmpty(templateName)) {
            return null;
        }
        String finalName = templateName.endsWith(".png") ? templateName : templateName + ".png";
        File imgDir = new File(currentTaskDir, "img");
        File file = new File(imgDir, finalName);
        if (file.exists()) {
            return file;
        }
        return null;
    }

    private void updateTemplatePreview(ImageView ivPreview, TextView tvTip, String templateName) {
        if (ivPreview == null || tvTip == null) {
            return;
        }
        File file = resolveTaskTemplateFile(templateName);
        if (file == null) {
            ivPreview.setImageDrawable(null);
            ivPreview.setImageBitmap(null);
            tvTip.setVisibility(View.VISIBLE);
            tvTip.setText("暂无模板预览，点击上方按钮可立即截图");
            return;
        }
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            ivPreview.setImageDrawable(null);
            ivPreview.setImageBitmap(null);
            tvTip.setVisibility(View.VISIBLE);
            tvTip.setText("模板读取失败: " + file.getName());
            return;
        }
        ivPreview.setImageDrawable(null);
        ivPreview.setImageBitmap(bitmap);
        tvTip.setVisibility(View.GONE);
    }

    private void refreshTemplateCachesForCurrentProject() {
        if (currentProjectDir == null) {
            return;
        }
        try {
            LoadImgToMatOperation loadOp = new LoadImgToMatOperation();
            loadOp.setId("reload_resource_" + System.currentTimeMillis());
            loadOp.setResponseType(1);
            HashMap<String, Object> input = new HashMap<>();
            input.put(MetaOperation.PROJECT, currentProjectDir.getName());
            loadOp.setInputMap(input);
            new com.auto.master.Task.Handler.OperationHandler.LoadImgToMatOperationHandler().handle(loadOp, new OperationContext());
        } catch (Exception e) {
            Log.w(TAG, "刷新模板缓存失败", e);
        }
    }

    private void bindTemplatePreview(View dialogView, AutoCompleteTextView templateInput) {
        ImageView ivPreview = dialogView.findViewById(R.id.iv_template_preview);
        TextView tvTip = dialogView.findViewById(R.id.tv_template_preview_tip);
        if (templateInput == null) {
            return;
        }
        templateInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateTemplatePreview(ivPreview, tvTip, s == null ? "" : s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        templateInput.setOnDismissListener(() -> updateTemplatePreview(ivPreview, tvTip,
                templateInput.getText() == null ? "" : templateInput.getText().toString().trim()));
        updateTemplatePreview(ivPreview, tvTip, templateInput.getText() == null ? "" : templateInput.getText().toString().trim());
    }

    private void refreshTemplateOptions(AutoCompleteTextView templateInput) {
        if (templateInput == null) {
            return;
        }
        bindAutoComplete(templateInput, getCurrentTaskTemplateFiles());
    }

    private List<String> getCurrentTaskGestureFiles() {
        List<String> files = new ArrayList<>();
        if (currentTaskDir == null) {
            return files;
        }
        File gestureDir = new File(currentTaskDir, "gesture");
        File[] gestureFiles = gestureDir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".json"));
        if (gestureFiles == null) {
            return files;
        }
        java.util.Arrays.sort(gestureFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : gestureFiles) {
            files.add(file.getName());
        }
        return files;
    }

    private GestureOverlayView.GestureNode readGestureNodeFromFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        try (FileReader reader = new FileReader(file)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.fromJson(reader, GestureOverlayView.GestureNode.class);
        } catch (Exception e) {
            Log.w(TAG, "读取手势文件失败: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    private List<GestureLibraryAdapter.GestureLibraryItem> getCurrentTaskGestureLibraryItems() {
        List<GestureLibraryAdapter.GestureLibraryItem> items = new ArrayList<>();
        if (currentTaskDir == null) {
            return items;
        }
        File gestureDir = new File(currentTaskDir, "gesture");
        File[] files = gestureDir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null) {
            return items;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : files) {
            GestureOverlayView.GestureNode node = readGestureNodeFromFile(file);
            items.add(new GestureLibraryAdapter.GestureLibraryItem(file.getName(), file, node));
        }
        return items;
    }

    private void updateGestureStatus(TextView statusView, String gestureFileName) {
        if (statusView == null) {
            return;
        }
        File file = resolveTaskGestureFile(gestureFileName);
        if (file == null) {
            statusView.setText("状态：未找到手势文件");
            return;
        }
        GestureOverlayView.GestureNode node = readGestureNodeFromFile(file);
        if (node == null || node.strokes == null || node.strokes.isEmpty()) {
            statusView.setText("状态：手势文件无效");
            return;
        }
        statusView.setText("状态：已选择 " + file.getName() + " | 轨迹" + node.strokes.size() + "条 | " + node.duration + "ms");
    }

    private void playGestureFromInput(AutoCompleteTextView gestureInput, TextView statusView) {
        if (gestureInput == null) {
            return;
        }
        String typed = gestureInput.getText() == null ? "" : gestureInput.getText().toString().trim();
        String fileName = normalizeGestureFileName(typed);
        if (TextUtils.isEmpty(fileName)) {
            Toast.makeText(this, "请先选择手势文件", Toast.LENGTH_SHORT).show();
            return;
        }
        File file = resolveTaskGestureFile(fileName);
        if (file == null) {
            Toast.makeText(this, "手势文件不存在", Toast.LENGTH_SHORT).show();
            updateGestureStatus(statusView, fileName);
            return;
        }
        GestureOverlayView.GestureNode node = readGestureNodeFromFile(file);
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            Toast.makeText(this, "无障碍服务未连接", Toast.LENGTH_SHORT).show();
            return;
        }
        if (node == null || node.strokes == null || node.strokes.isEmpty()) {
            Toast.makeText(this, "手势数据无效", Toast.LENGTH_SHORT).show();
            updateGestureStatus(statusView, fileName);
            return;
        }
        svc.showGestureTrail(node);
        svc.replayGesture(node, success -> {
            Handler main = new Handler(Looper.getMainLooper());
            main.post(() -> {
                Toast.makeText(this, success ? "回放完成" : "回放取消", Toast.LENGTH_SHORT).show();
                updateGestureStatus(statusView, fileName);
            });
        });
    }

    private void showGestureLibraryDialog(AutoCompleteTextView gestureInput, TextView statusView) {
        List<GestureLibraryAdapter.GestureLibraryItem> items = getCurrentTaskGestureLibraryItems();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gesture_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);

        RecyclerView rv = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch = dialogView.findViewById(R.id.edt_library_search);
        rv.setLayoutManager(new LinearLayoutManager(this));
        GestureLibraryAdapter adapter = new GestureLibraryAdapter(items, item -> {
            gestureInput.setText(item.fileName, false);
            refreshGestureOptions(gestureInput);
            updateGestureStatus(statusView, item.fileName);
            safeRemoveView(dialogView);
        });
        rv.setAdapter(adapter);

        dialogView.findViewById(R.id.btn_library_close).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_library_clear).setOnClickListener(v -> {
            gestureInput.setText("", false);
            updateGestureStatus(statusView, "");
            safeRemoveView(dialogView);
        });
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.updateFilter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void refreshGestureOptions(AutoCompleteTextView gestureInput) {
        if (gestureInput == null) {
            return;
        }
        bindAutoComplete(gestureInput, getCurrentTaskGestureFiles());
    }

    private String normalizeGestureFileName(String rawName) {
        if (TextUtils.isEmpty(rawName)) {
            return "";
        }
        String name = rawName.trim();
        if (!name.endsWith(".json")) {
            name = name + ".json";
        }
        return name;
    }

    private String generateGestureTimestampName() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return "gesture_" + ts + ".json";
    }

    private File resolveTaskGestureFile(String gestureFileName) {
        if (currentTaskDir == null || TextUtils.isEmpty(gestureFileName)) {
            return null;
        }
        File gestureDir = new File(currentTaskDir, "gesture");
        String finalName = normalizeGestureFileName(gestureFileName);
        File file = new File(gestureDir, finalName);
        return file.exists() ? file : null;
    }

    private boolean saveGestureNodeForCurrentTask(String gestureFileName, GestureOverlayView.GestureNode node) {
        if (currentTaskDir == null || currentProjectDir == null || node == null) {
            return false;
        }
        String finalName = normalizeGestureFileName(gestureFileName);
        if (TextUtils.isEmpty(finalName)) {
            return false;
        }
        File gestureDir = new File(currentTaskDir, "gesture");
        if (!gestureDir.exists() && !gestureDir.mkdirs()) {
            return false;
        }
        File file = new File(gestureDir, finalName);
        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(node, writer);
            Template.putTaskSingleGestureCache(currentProjectDir.getName(), currentTaskDir.getName(), finalName, node);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "保存手势文件失败: " + file.getAbsolutePath(), e);
            return false;
        }
    }

    private void beginGestureRecordFromDialog(View dialogView,
                                              AutoCompleteTextView edtGestureFile,
                                              TextView tvGestureStatus) {
        if (currentProjectDir == null || currentTaskDir == null) {
            Toast.makeText(this, "当前 Task 无效", Toast.LENGTH_SHORT).show();
            return;
        }
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            Toast.makeText(this, "无障碍服务未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        String typedName = edtGestureFile.getText() == null ? "" : edtGestureFile.getText().toString().trim();
        String normalized = normalizeGestureFileName(typedName);
        if (TextUtils.isEmpty(normalized)) {
            normalized = generateGestureTimestampName();
            edtGestureFile.setText(normalized, false);
        }
        final String finalGestureName = normalized;
        Runnable restoreViews = hideViewsForCapture(dialogView, projectPanelView);

        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> {
            try {
                if (tvGestureStatus != null) {
                    tvGestureStatus.setText("状态：录制中，请在屏幕上完成手势");
                }
                Toast.makeText(this, "开始录制手势，请在屏幕上操作", Toast.LENGTH_SHORT).show();
                svc.startGestureRecording(node -> {
                    boolean ok = saveGestureNodeForCurrentTask(finalGestureName, node);
                    refreshGestureOptions(edtGestureFile);
                    edtGestureFile.setText(finalGestureName, false);

                    if (!ok) {
                        if (tvGestureStatus != null) {
                            tvGestureStatus.setText("状态：保存失败");
                        }
                        Toast.makeText(this, "手势保存失败", Toast.LENGTH_SHORT).show();
                        restoreViews.run();
                        return;
                    }

                    // 录制成功后立即在底层UI执行手势，让录制产生真实效果
                    // 等待遮罩完全消失后再注入，避免遮罩仍在渲染时的视觉冲突
                    if (tvGestureStatus != null) {
                        tvGestureStatus.setText("状态：录制完成，正在底层UI执行...");
                    }

                    AutoAccessibilityService replaySvc = AutoAccessibilityService.get();
                    if (replaySvc == null) {
                        if (tvGestureStatus != null) {
                            tvGestureStatus.setText("状态：已录制 " + finalGestureName);
                        }
                        Toast.makeText(this, "手势已录制（无障碍服务断开，跳过执行）", Toast.LENGTH_SHORT).show();
                        restoreViews.run();
                        return;
                    }

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            replaySvc.replayGesture(node, success -> {
                                try {
                                    if (tvGestureStatus != null) {
                                        tvGestureStatus.setText(success
                                                ? "状态：已录制并执行 " + finalGestureName
                                                : "状态：已录制 " + finalGestureName + "（执行未成功）");
                                    }
                                    Toast.makeText(this,
                                            success ? "手势录制成功，已在底层UI执行" : "手势已录制，执行未成功（可手动回放验证）",
                                            Toast.LENGTH_SHORT).show();
                                } finally {
                                    restoreViews.run();
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "重播手势失败", e);
                            if (tvGestureStatus != null) {
                                tvGestureStatus.setText("状态：已录制 " + finalGestureName);
                            }
                            restoreViews.run();
                        }
                    }, 180); // 等待录制遮罩完全移除后再注入手势
                });
            } catch (Exception e) {
                restoreViews.run();
                Log.e(TAG, "启动手势录制失败", e);
                Toast.makeText(this, "启动录制失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, 220);
    }

    private List<String> getRecentTemplateFiles(int maxCount) {
        List<String> all = getCurrentTaskTemplateFiles();
        if (all.size() <= maxCount) {
            return all;
        }
        return new ArrayList<>(all.subList(0, maxCount));
    }

    private void renderRecentTemplateStrip(View dialogView, AutoCompleteTextView templateInput) {
        LinearLayout container = dialogView.findViewById(R.id.ly_recent_templates);
        if (container == null) {
            return;
        }
        container.removeAllViews();

        List<String> files = getRecentTemplateFiles(8);
        if (files.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无模板");
            empty.setTextColor(0xFF6A7682);
            empty.setTextSize(12f);
            empty.setPadding(dp(8), dp(8), dp(8), dp(8));
            container.addView(empty);
            return;
        }

        for (String fileName : files) {
            File file = resolveTaskTemplateFile(fileName);
            if (file == null) {
                continue;
            }
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(dp(4), dp(4), dp(4), dp(4));
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.MATCH_PARENT);
            itemLp.rightMargin = dp(6);
            item.setLayoutParams(itemLp);
            item.setBackgroundResource(R.drawable.item_operation_compact_bg);

            ImageView img = new ImageView(this);
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(60), dp(42));
            img.setLayoutParams(imgLp);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            img.setImageBitmap(bitmap);

            TextView name = new TextView(this);
            name.setText(fileName);
            name.setTextColor(0xFF4E5F74);
            name.setTextSize(10f);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(dp(2), dp(2), dp(2), 0);

            item.addView(img);
            item.addView(name);

            item.setOnClickListener(v -> {
                templateInput.setText(fileName, false);
                ImageView ivPreview = dialogView.findViewById(R.id.iv_template_preview);
                TextView tvTip = dialogView.findViewById(R.id.tv_template_preview_tip);
                updateTemplatePreview(ivPreview, tvTip, fileName);
            });
            container.addView(item);
        }
    }

    private Runnable hideViewsForCapture(View... viewsToHide) {
        List<View> restoreViews = new ArrayList<>();
        List<Integer> restoreStates = new ArrayList<>();
        if (viewsToHide != null) {
            for (View view : viewsToHide) {
                if (view == null) {
                    continue;
                }
                restoreViews.add(view);
                restoreStates.add(view.getVisibility());
                view.setVisibility(View.GONE);
            }
        }
        return () -> {
            for (int i = 0; i < restoreViews.size(); i++) {
                View v = restoreViews.get(i);
                Integer state = restoreStates.get(i);
                if (v != null) {
                    v.setVisibility(state == null ? View.VISIBLE : state);
                }
            }
        };
    }

    /** 多选模板库：进入时已选 currentSelected，确认后回调。 */
    private void showTemplateMultiSelectDialog(
            java.util.List<String> currentSelected,
            OperationDialogFactory.MatchMapHelper.OnMultiSelectConfirmed callback) {

        List<com.auto.master.floatwin.adapter.TemplateLibraryAdapter.TemplateLibraryItem> items =
                getCurrentTaskTemplateLibraryItems();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_template_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        applyTemplateLibraryDialogViewport(dialogLp);
        wm.addView(dialogView, dialogLp);

        ((TextView) dialogView.findViewById(R.id.tv_library_title)).setText("选择模板（可多选）");

        androidx.recyclerview.widget.RecyclerView rv = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch = dialogView.findViewById(R.id.edt_library_search);
        View manageActions = dialogView.findViewById(R.id.ly_library_manage_actions);
        View selectActions = dialogView.findViewById(R.id.ly_library_select_actions);
        TextView btnCancel = dialogView.findViewById(R.id.btn_library_cancel);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_library_confirm);
        if (manageActions != null) manageActions.setVisibility(View.GONE);
        if (selectActions != null) selectActions.setVisibility(View.VISIBLE);

        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        // 传 null listener —— 点击单项不触发回调，由批量选择控制
        com.auto.master.floatwin.adapter.TemplateLibraryAdapter adapter =
                new com.auto.master.floatwin.adapter.TemplateLibraryAdapter(items, null);
        rv.setAdapter(adapter);

        // 直接进入批量选择模式，并预选当前已选项
        adapter.setBatchMode(true);
        if (currentSelected != null) {
            for (String name : currentSelected) adapter.selectItem(name);
        }

        Runnable updateConfirmBtn = () -> {
            int cnt = adapter.getSelectedCount();
            btnConfirm.setText(cnt == 0 ? "确定" : "确定(" + cnt + ")");
        };
        adapter.setSelectionChangedListener(count -> updateConfirmBtn.run());
        updateConfirmBtn.run();

        dialogView.findViewById(R.id.btn_library_close).setOnClickListener(v -> safeRemoveView(dialogView));
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> safeRemoveView(dialogView));
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                java.util.List<String> selected = new java.util.ArrayList<>(adapter.getSelectedFileNames());
                safeRemoveView(dialogView);
                if (callback != null) callback.onConfirmed(selected);
            });
        }

        if (edtSearch != null) {
            edtSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.updateFilter(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    /** 从模板 manifest 导入 bbox：弹出单选模板库，选中后将 manifest 中的 bbox 写入 edtBbox。 */
    private void importBboxFromTemplate(View mainDialogView, EditText edtBbox) {
        List<com.auto.master.floatwin.adapter.TemplateLibraryAdapter.TemplateLibraryItem> items =
                getCurrentTaskTemplateLibraryItems();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_template_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        applyTemplateLibraryDialogViewport(dialogLp);
        wm.addView(dialogView, dialogLp);

        ((TextView) dialogView.findViewById(R.id.tv_library_title)).setText("选择模板以导入其区域");

        androidx.recyclerview.widget.RecyclerView rv = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch = dialogView.findViewById(R.id.edt_library_search);
        View manageActions = dialogView.findViewById(R.id.ly_library_manage_actions);
        View selectActions = dialogView.findViewById(R.id.ly_library_select_actions);
        if (manageActions != null) manageActions.setVisibility(View.GONE);
        if (selectActions != null) selectActions.setVisibility(View.GONE);

        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        com.auto.master.floatwin.adapter.TemplateLibraryAdapter adapter =
                new com.auto.master.floatwin.adapter.TemplateLibraryAdapter(items, item -> {
                    List<Integer> bbox = getBboxFromTemplate(item.fileName);
                    if (bbox != null && bbox.size() >= 4) {
                        edtBbox.setText(bbox.get(0) + "," + bbox.get(1) + "," + bbox.get(2) + "," + bbox.get(3));
                    } else {
                        Toast.makeText(this, "模板 " + item.fileName + " 无区域记录", Toast.LENGTH_SHORT).show();
                    }
                    safeRemoveView(dialogView);
                });
        rv.setAdapter(adapter);

        dialogView.findViewById(R.id.btn_library_close).setOnClickListener(v -> safeRemoveView(dialogView));

        if (edtSearch != null) {
            edtSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.updateFilter(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    /** 读取 manifest 中某模板的 bbox；先查内存 cache，再读文件。 */
    private List<Integer> getBboxFromTemplate(String fileName) {
        if (currentProjectDir == null || currentTaskDir == null) return null;
        // 1. 内存缓存
        java.util.Map<String, List<Integer>> manifest =
                com.auto.master.Template.Template.getTaskManifestCache(
                        currentProjectDir.getName(), currentTaskDir.getName());
        if (manifest != null && manifest.containsKey(fileName)) return manifest.get(fileName);
        // 2. 读文件
        try {
            java.io.File mf = new java.io.File(new java.io.File(currentTaskDir, "img"), "manifest.json");
            if (!mf.exists()) return null;
            String content = new String(java.nio.file.Files.readAllBytes(mf.toPath()));
            org.json.JSONObject json = new org.json.JSONObject(content);
            org.json.JSONArray arr = json.optJSONArray(fileName);
            if (arr != null && arr.length() >= 4) {
                List<Integer> bbox = new java.util.ArrayList<>();
                for (int i = 0; i < arr.length(); i++) bbox.add(arr.getInt(i));
                return bbox;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void beginTemplateCaptureFromDialog(View dialogView, AutoCompleteTextView edtTemplateFile) {
        String fileName = edtTemplateFile.getText() == null ? "" : edtTemplateFile.getText().toString().trim();
        String normalizedName = normalizeTemplateFileName(fileName);
        edtTemplateFile.setText(normalizedName, false);
        Runnable restoreViews = hideViewsForCapture(dialogView, projectPanelView);
        registerTemplateCaptureDialogRefresh(dialogView, edtTemplateFile, restoreViews);

        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> {
            long startedAt = System.currentTimeMillis();
            boolean started = launchTemplateCapture(normalizedName);
            if (!started) {
                CropRegionOperationHandler.clearTemplateCaptureEventListener(null);
                restoreViews.run();
                return;
            }
            waitAndRefreshTemplatePreview(dialogView, edtTemplateFile, startedAt, restoreViews);
        }, CAPTURE_UI_SETTLE_DELAY_MS);
    }

    private void waitAndRefreshTemplatePreview(View dialogView,
                                               AutoCompleteTextView templateInput,
                                               long startedAt,
                                               Runnable onCaptureFinished) {
        Handler main = new Handler(Looper.getMainLooper());
        final int[] retry = {0};
        final boolean[] cacheReloaded = {false};
        final boolean[] finished = {false};
        Runnable poll = new Runnable() {
            @Override
            public void run() {
                retry[0]++;
                String currentName = templateInput.getText() == null ? "" : templateInput.getText().toString().trim();
                File file = resolveTaskTemplateFile(currentName);
                boolean ready = false;
                if (file != null && file.exists() && file.length() > 0) {
                    long lastModified = file.lastModified();
                    ready = lastModified >= (startedAt - 2000);
                }

                refreshTemplateOptions(templateInput);
                renderRecentTemplateStrip(dialogView, templateInput);
                ImageView ivPreview = dialogView.findViewById(R.id.iv_template_preview);
                TextView tvTip = dialogView.findViewById(R.id.tv_template_preview_tip);
                updateTemplatePreview(ivPreview, tvTip, currentName);

                if (ready && !cacheReloaded[0]) {
                    cacheReloaded[0] = true;
                    refreshTemplateSelectionAfterCapture(dialogView, templateInput, currentName);
                }

                if (!ready && retry[0] < TEMPLATE_CAPTURE_PREVIEW_MAX_RETRIES) {
                    main.postDelayed(this, 300);
                } else if (!cacheReloaded[0]) {
                    refreshTemplateCachesForCurrentProject();
                }

                if ((ready || retry[0] >= TEMPLATE_CAPTURE_PREVIEW_MAX_RETRIES) && !finished[0]) {
                    finished[0] = true;
                    if (onCaptureFinished != null) {
                        onCaptureFinished.run();
                    }
                }
            }
        };
        main.postDelayed(poll, 350);
    }

    private String generateTemplateTimestampName() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return "tpl_" + ts + ".png";
    }

    private String normalizeTemplateFileName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png") || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
            return name;
        }
        return name + ".png";
    }

    private interface OnOperationIdPickedListener {
        void onPicked(String operationId);
    }

    private void showOperationPickerDialog(String title,
                                           String excludeId,
                                           OnOperationIdPickedListener listener) {
        showOperationPickerDialog(title, excludeId, "", listener);
    }

    private void showOperationPickerDialog(String title,
                                           String excludeId,
                                           String currentSelectedId,
                                           OnOperationIdPickedListener listener) {
        List<OperationIdPickerAdapter.OperationPickItem> items = getCurrentTaskOperationPickItems(excludeId);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_operation_picker, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 360, 0.78f, 0.92f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 360, 420, null);

        TextView tvTitle = dialogView.findViewById(R.id.tv_picker_title);
        EditText edtSearch = dialogView.findViewById(R.id.edt_picker_search);
        RecyclerView rv = dialogView.findViewById(R.id.rv_picker);

        tvTitle.setText(title);
        rv.setLayoutManager(new LinearLayoutManager(this));
        OperationIdPickerAdapter adapter = new OperationIdPickerAdapter(items, currentSelectedId, id -> {
            safeRemoveView(dialogView);
            if (listener != null) {
                listener.onPicked(id);
            }
        });
        rv.setAdapter(adapter);
        if (!TextUtils.isEmpty(currentSelectedId)) {
            for (int i = 0; i < items.size(); i++) {
                if (TextUtils.equals(currentSelectedId, items.get(i).id)) {
                    rv.scrollToPosition(i);
                    break;
                }
            }
        }

        dialogView.findViewById(R.id.btn_picker_close).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_picker_clear).setOnClickListener(v -> {
            safeRemoveView(dialogView);
            if (listener != null) {
                listener.onPicked("");
            }
        });

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.updateFilter(s == null ? "" : s.toString());
                adapter.updateSelectedOperation(currentSelectedId);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private static class PrecheckResult {
        List<String> blocking = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int fixAction = 0;

        boolean hasBlocking() {
            return !blocking.isEmpty();
        }
    }

    private static class RunLaunchData {
        MetaOperation startOperation;
        Task selectedTask;
        String projectName;
        String selectedTaskName;
        List<OperationItem> selectedTaskOperations = new ArrayList<>();
        OperationContext ctx;
    }

    @Nullable
    private RunLaunchData prepareRunLaunchData() {
        if (projectPanelView == null) {
            return null;
        }
        if (currentLevel != NavigationLevel.OPERATION) {
            Toast.makeText(this, "请先进入 Operation 列表", Toast.LENGTH_SHORT).show();
            return null;
        }

        if (currentProjectDir == null) {
            Toast.makeText(this, "当前项目无效", Toast.LENGTH_SHORT).show();
            return null;
        }

        RecyclerView rv = projectPanelView.findViewById(R.id.rv_content);
        OperationPanelAdapter adapter = (OperationPanelAdapter) rv.getAdapter();
        if (adapter == null) {
            return null;
        }

        OperationItem selected = adapter.getSelectedItem();
        if (selected == null) {
            Toast.makeText(this, "请先选中一个操作", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (TextUtils.isEmpty(selected.id)) {
            Toast.makeText(this, "选中操作缺少 ID，无法运行", Toast.LENGTH_SHORT).show();
            return null;
        }

        String projectName = currentProjectDir.getName();
        Project project = findCachedProjectByName(projectName);
        if (project == null) {
            project = loadProjectFromDir(currentProjectDir);
            if (project != null) {
                upsertCachedProject(project);
            }
        }
        if (project == null || project.getTaskMap() == null || project.getTaskMap().isEmpty()) {
            Toast.makeText(this, "当前项目没有可运行的 Task", Toast.LENGTH_SHORT).show();
            return null;
        }

        MetaOperation startOperation = null;
        String selectedTaskName = null;
        Task selectedTask = null;
        for (Map.Entry<String, Task> entry : project.getTaskMap().entrySet()) {
            Task task = entry.getValue();
            if (task == null || task.getOperationMap() == null) {
                continue;
            }
            MetaOperation candidate = task.getOperationMap().get(selected.id);
            if (candidate != null) {
                startOperation = candidate;
                selectedTaskName = entry.getKey();
                selectedTask = task;
                break;
            }
        }

        if (startOperation == null || TextUtils.isEmpty(selectedTaskName) || selectedTask == null) {
            reloadCurrentProject();
            project = findCachedProjectByName(projectName);
            if (project != null && project.getTaskMap() != null) {
                for (Map.Entry<String, Task> entry : project.getTaskMap().entrySet()) {
                    Task task = entry.getValue();
                    if (task == null || task.getOperationMap() == null) {
                        continue;
                    }
                    MetaOperation candidate = task.getOperationMap().get(selected.id);
                    if (candidate != null) {
                        startOperation = candidate;
                        selectedTaskName = entry.getKey();
                        selectedTask = task;
                        break;
                    }
                }
            }
        }

        if (startOperation == null || TextUtils.isEmpty(selectedTaskName) || selectedTask == null) {
            Log.e(TAG, "未找到选中的 operation: " + selected.id);
            Toast.makeText(this, "未找到选中的 operation", Toast.LENGTH_SHORT).show();
            return null;
        }

        List<OperationItem> selectedTaskOperations = buildOperationItemsFromTask(selectedTask);

        OperationContext ctx = new OperationContext();
        ctx.anchorProject = project;

        RunLaunchData launchData = new RunLaunchData();
        launchData.startOperation = startOperation;
        launchData.selectedTask = selectedTask;
        launchData.projectName = projectName;
        launchData.selectedTaskName = selectedTaskName;
        launchData.selectedTaskOperations = selectedTaskOperations;
        launchData.ctx = ctx;
        return launchData;
    }

    @Nullable
    private Project findCachedProjectByName(String projectName) {
        if (TextUtils.isEmpty(projectName) || cachedProjects == null) {
            return null;
        }
        for (Project p : cachedProjects) {
            if (p != null && TextUtils.equals(projectName, p.getProjectName())) {
                return p;
            }
        }
        return null;
    }

    private void upsertCachedProject(Project project) {
        if (project == null || TextUtils.isEmpty(project.getProjectName())) {
            return;
        }
        cachedProjects.removeIf(p -> TextUtils.equals(project.getProjectName(), p.getProjectName()));
        cachedProjects.add(project);
        syncProjectMemoryCaches(project);
    }

    private List<OperationItem> buildOperationItemsFromTask(Task task) {
        List<OperationItem> items = new ArrayList<>();
        if (task == null || task.getOperationMap() == null) {
            return items;
        }
        int opIndex = 0;
        for (MetaOperation operation : task.getOperationMap().values()) {
            if (operation == null || TextUtils.isEmpty(operation.getId())) {
                continue;
            }
            items.add(new OperationItem(operation.getName(), operation.getId(), getOperationTypeName(operation.getType()),
                    opIndex++, extractDelayDurationMs(operation), extractDelayShowCountdown(operation)));
        }
        return items;
    }

    private String buildFileCacheKey(@Nullable File file) {
        return file == null ? "" : file.getAbsolutePath();
    }

    private List<File> readTaskItems(File projectDir) {
        List<File> items = new ArrayList<>();
        File[] dirs = projectDir.listFiles(File::isDirectory);
        if (dirs != null) {
            Collections.addAll(items, dirs);
        }

        File[] files = projectDir.listFiles(file -> file.isFile() && !file.getName().startsWith("."));
        if (files != null) {
            Collections.addAll(items, files);
        }

        Collections.sort(items, (left, right) -> {
            if (left.isDirectory() != right.isDirectory()) {
                return left.isDirectory() ? -1 : 1;
            }
            return left.getName().compareToIgnoreCase(right.getName());
        });
        return items;
    }

    private void syncProjectMemoryCaches(Project project) {
        if (project == null || TextUtils.isEmpty(project.getProjectName())) {
            return;
        }
        File projectDir = new File(getProjectsRootDir(), project.getProjectName());
        if (!projectDir.exists()) {
            return;
        }
        List<File> taskItems = readTaskItems(projectDir);
        String projectKey = buildFileCacheKey(projectDir);
        taskItemsMemoryCache.put(projectKey, new ArrayList<>(taskItems));
        taskItemsMemoryVersions.put(projectKey, projectDir.lastModified());

        Map<String, Task> taskMap = project.getTaskMap();
        for (File item : taskItems) {
            if (!item.isDirectory()) {
                continue;
            }
            Task task = taskMap == null ? null : taskMap.get(item.getName());
            List<OperationItem> operationItems = task == null
                    ? new ArrayList<>()
                    : buildOperationItemsFromTask(task);
            if (operationItems.isEmpty()) {
                operationItems = readOperationItemsDirect(item);
            }
            File opFile = new File(item, "operations.json");
            String taskKey = buildFileCacheKey(item);
            operationItemsMemoryCache.put(taskKey, new ArrayList<>(operationItems));
            operationItemsMemoryVersions.put(taskKey, opFile.exists() ? opFile.lastModified() : Long.MIN_VALUE);
        }
    }

    private void ensureProjectPanelAdapters() {
        if (projectPanelAdapter == null) {
            projectPanelAdapter = new ProjectPanelAdapter(
                    new ArrayList<>(),
                    item -> {
                        currentProjectDir = item.dir;
                        currentTaskDir = null;
                        currentLevel = NavigationLevel.TASK;
                        clearProjectPanelSearch();
                        loadTasks(item.dir);
                        updateUIForLevel();
                    },
                    (item, anchor) -> showProjectActionMenu(item, anchor));
        }
        if (taskPanelAdapter == null) {
            taskPanelAdapter = new TaskPanelAdapter(
                    new ArrayList<>(),
                    file -> {
                        if (file.isDirectory()) {
                            currentTaskDir = file;
                            currentLevel = NavigationLevel.OPERATION;
                            clearProjectPanelSearch();
                            loadOperations(file);
                            updateUIForLevel();
                        } else {
                            Toast.makeText(this, "文件: " + file.getName(), Toast.LENGTH_SHORT).show();
                        }
                    },
                    this::showTaskActionMenu);
        }
        if (operationPanelAdapter == null) {
            operationPanelAdapter = new OperationPanelAdapter(
                    new ArrayList<>(),
                    operation -> {
                    },
                    new OperationPanelAdapter.OnActionListener() {
                        @Override
                        public void onEdit(OperationItem item) {
                            showEditOperationDialog(item, currentOperationAdapter);
                        }

                        @Override
                        public void onCopy(OperationItem item) {
                            copyOperationToClipboard(item.id);
                        }

                        @Override
                        public void onPasteAfter(OperationItem item) {
                            pasteOperationRelative(item.id, false);
                        }

                        @Override
                        public void onInsertBefore(OperationItem item) {
                            pasteOperationRelative(item.id, true);
                        }

                        @Override
                        public void onDelete(OperationItem item) {
                            deleteOperation(item.id);
                        }

                        @Override
                        public void onMoveUp(OperationItem item) {
                            moveOperation(item.id, -1);
                        }

                        @Override
                        public void onMoveDown(OperationItem item) {
                            moveOperation(item.id, 1);
                        }

                        @Override
                        public boolean canPaste() {
                            return hasOperationClipboard();
                        }

                        @Override
                        public void onFloatButton(OperationItem item) {
                            showNodeFloatBtnConfig(item);
                        }
                    },
                    selectedIds -> {
                        batchSelectedOperationIds.clear();
                        batchSelectedOperationIds.addAll(selectedIds);
                        updateBatchActionCount();
                    });
        }
        currentOperationAdapter = operationPanelAdapter;
    }

    private void switchProjectPanelAdapter(@Nullable RecyclerView.Adapter<?> adapter) {
        RecyclerView rv = getProjectPanelRecyclerView();
        if (rv == null || adapter == null) {
            return;
        }
        if (rv.getAdapter() != adapter) {
            rv.stopScroll();
            rv.setAdapter(adapter);
        }
    }

    private PrecheckResult runPrecheck(Task selectedTask, String selectedTaskName) {
        PrecheckResult result = new PrecheckResult();
        boolean a11yEnabled = com.auto.master.auto.AutoAccessibilityService.isConnected();
        boolean overlayEnabled = canDrawOverlays(this);

        if (!a11yEnabled) {
            result.blocking.add("- 辅助功能未连接");
            result.fixAction = 1;
        }
        if (!overlayEnabled) {
            result.blocking.add("- 悬浮窗权限未授权");
            if (result.fixAction == 0) {
                result.fixAction = 2;
            }
        }

        boolean needProjection = false;
        int emptyIdCount = 0;
        int missingTemplateCount = 0;

        if (selectedTask != null && selectedTask.getOperationMap() != null) {
            for (MetaOperation op : selectedTask.getOperationMap().values()) {
                if (op == null) {
                    continue;
                }
                if (TextUtils.isEmpty(op.getId())) {
                    emptyIdCount++;
                }
                int type = op.getType();
                if (type == 3 || type == 6 || type == 7 || type == 18) {
                    needProjection = true;
                }

                if (type == 6) {
                    Object nameObj = op.getInputMap() == null ? null : op.getInputMap().get(MetaOperation.SAVEFILENAME);
                    String fileName = nameObj == null ? "" : String.valueOf(nameObj).trim();
                    if (!TextUtils.isEmpty(fileName)) {
                        File imgFile = new File(new File(currentTaskDir, "img"), fileName);
                        if (!imgFile.exists()) {
                            missingTemplateCount++;
                        }
                    }
                }
            }
        }

        if (needProjection && !com.auto.master.capture.ScreenCapture.hasProjectionPermission()) {
            result.blocking.add("- 当前脚本涉及模板/截图操作，但录屏授权未开启");
            if (result.fixAction == 0) {
                result.fixAction = 3;
            }
        }

        if (emptyIdCount > 0) {
            result.blocking.add("- 检测到 " + emptyIdCount + " 个节点缺少 ID");
        }
        if (missingTemplateCount > 0) {
            result.warnings.add("- 检测到 " + missingTemplateCount + " 个模板文件不存在");
        }
        if (selectedTask == null || selectedTask.getOperationMap() == null || selectedTask.getOperationMap().isEmpty()) {
            result.blocking.add("- Task 无有效 operation");
        }

        return result;
    }

    private void showPrecheckDialog(PrecheckResult result, Runnable continueAction) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_run_precheck, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(320, true);
        wm.addView(dialogView, dialogLp);

        TextView tvContent = dialogView.findViewById(R.id.tv_precheck_content);
        StringBuilder sb = new StringBuilder();
        if (!result.blocking.isEmpty()) {
            sb.append("阻断项:\n");
            for (String s : result.blocking) {
                sb.append(s).append("\n");
            }
        }
        if (!result.warnings.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("提醒项:\n");
            for (String s : result.warnings) {
                sb.append(s).append("\n");
            }
        }
        if (sb.length() == 0) {
            sb.append("阻断项: 无\n提醒项: 无\n\n可直接运行。");
        }
        tvContent.setText(sb.toString().trim());

        dialogView.findViewById(R.id.btn_precheck_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_precheck_continue).setOnClickListener(v -> {
            safeRemoveView(dialogView);
            if (continueAction != null) {
                continueAction.run();
            }
        });
        dialogView.findViewById(R.id.btn_precheck_fix).setOnClickListener(v -> {
            safeRemoveView(dialogView);
            handlePrecheckFix(result.fixAction);
        });
    }

    private void handlePrecheckFix(int fixAction) {
        try {
            if (fixAction == 1) {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            }
            if (fixAction == 2) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            }
            if (fixAction == 3) {
                Intent i = new Intent(this, com.auto.master.MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                Toast.makeText(this, "请在首页点击“录屏授权”", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, "打开修复页面失败", Toast.LENGTH_SHORT).show();
        }
    }

    private interface OnRunModeSelectedListener {
        void onSelected(boolean showRunningPanel);
    }

    private void showRunModeMenu(View anchor, OnRunModeSelectedListener listener) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_run_mode, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(280, true);
        wm.addView(dialogView, dialogLp);

        dialogView.findViewById(R.id.btn_run_with_panel).setOnClickListener(v -> {
            safeRemoveView(dialogView);
            if (listener != null) {
                listener.onSelected(true);
            }
        });

        dialogView.findViewById(R.id.btn_run_background).setOnClickListener(v -> {
            safeRemoveView(dialogView);
            if (listener != null) {
                listener.onSelected(false);
            }
        });

        dialogView.findViewById(R.id.btn_run_mode_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
    }

    private void startOperationWithMode(MetaOperation startOperation,
                                        OperationContext ctx,
                                        String projectName,
                                        String selectedTaskName,
                                        List<OperationItem> selectedTaskOperations,
                                        boolean showRunningPanelNow) {
        runningOperations.clear();
        runningOperations.addAll(selectedTaskOperations);
        totalOperationCount = selectedTaskOperations.size();
        currentOperationIndex = 0;
        currentRunningProject = projectName;
        currentRunningTask = selectedTaskName;
        isPaused = false;
        beginRunLog(projectName, selectedTaskName, startOperation);

        try {
            Log.d(TAG, "开始运行 operation: " + startOperation.getName() + " (" + startOperation.getId() + ")");

            ScriptRunner.setExecutionListener(this);

            ScriptExecuteContext scriptExecuteContext = new ScriptExecuteContext();
            scriptExecuteContext.tobeHandledOperation = startOperation;
            scriptExecuteContext.sharedContext = ctx;
            scriptExecuteContext.running = true;
            ScriptRunner.runOperation(scriptExecuteContext);

            transitionAfterRunStart(showRunningPanelNow);

            Log.d(TAG, "ScriptRunner.runOperation 已调用");
        } catch (Exception e) {
            Log.d("检验检验检验检验检验检验", "setupProjectPanel: " + e.toString());
            Toast.makeText(this, "运行失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            recordFailureReason("startup_exception");
            appendRunLog("[error] 启动失败: " + e.getMessage());
            persistCurrentRunLog();
            CrashLogger.logHandledException(this, "startOperationWithMode", e);
            CrashLogger.finishRunSession(this, "startup_exception");
            ScriptRunner.clearExecutionListener();
        }
    }

    private void beginRunLog(String projectName, String taskName, MetaOperation startOperation) {
        currentRunStartMs = System.currentTimeMillis();
        currentRunLogs.clear();
        opStartTimeMs.clear();
        opDurationsMs.clear();
        opFailureReasons.clear();
        opSuccessCount = 0;
        opFailureCount = 0;
        latestFailureReason = "-";
        CrashLogger.startRunSession(
                this,
                projectName,
                taskName,
                startOperation == null ? "" : startOperation.getId(),
                startOperation == null ? "" : startOperation.getName()
        );
        
        appendRunLog("=== Run Start ===");
        appendRunLog("project=" + projectName + ", task=" + taskName);
        if (startOperation != null) {
            appendRunLog("entry=" + startOperation.getId() + " (" + startOperation.getName() + ")");
        }
        updateRuntimeMetricsPanel();
    }

    private void recordFailureReason(String reason) {
        if (TextUtils.isEmpty(reason)) {
            reason = "unknown";
        }
        latestFailureReason = reason;
        Integer count = opFailureReasons.get(reason);
        opFailureReasons.put(reason, count == null ? 1 : count + 1);
    }

    private String getTopFailureReason() {
        if (opFailureReasons.isEmpty()) {
            return latestFailureReason;
        }
        String top = latestFailureReason;
        int max = -1;
        for (Map.Entry<String, Integer> entry : opFailureReasons.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > max) {
                max = entry.getValue();
                top = entry.getKey();
            }
        }
        if (max <= 0) {
            return top;
        }
        return top + " (" + max + ")";
    }

    private long percentileMs(List<Long> data, double percentile) {
        if (data == null || data.isEmpty()) {
            return -1;
        }
        List<Long> sorted = new ArrayList<>(data);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.size()) {
            index = sorted.size() - 1;
        }
        return sorted.get(index);
    }

    private long averageMs(List<Long> data) {
        if (data == null || data.isEmpty()) {
            return -1;
        }
        long sum = 0;
        for (Long v : data) {
            if (v != null) {
                sum += v;
            }
        }
        return sum / Math.max(1, data.size());
    }

    private void updateRuntimeMetricsPanel() {
        if (runningPanelView == null) {
            return;
        }
        TextView tvSuccessRate = runningPanelView.findViewById(R.id.tv_success_rate);
        TextView tvDurationStats = runningPanelView.findViewById(R.id.tv_duration_stats);
        TextView tvFailureReason = runningPanelView.findViewById(R.id.tv_failure_reason);
        if (tvSuccessRate == null || tvDurationStats == null || tvFailureReason == null) {
            return;
        }

        int total = opSuccessCount + opFailureCount;
        if (total <= 0) {
            tvSuccessRate.setText("成功率: -");
        } else {
            double successRate = (opSuccessCount * 100.0d) / total;
            tvSuccessRate.setText(String.format(Locale.getDefault(), "成功率: %.1f%% (%d/%d)", successRate, opSuccessCount, total));
        }

        long avg = averageMs(opDurationsMs);
        long p50 = percentileMs(opDurationsMs, 0.50d);
        long p95 = percentileMs(opDurationsMs, 0.95d);
        if (avg < 0 || p50 < 0 || p95 < 0) {
            tvDurationStats.setText("耗时: -");
        } else {
            tvDurationStats.setText(String.format(Locale.getDefault(), "耗时: avg %dms | p50 %dms | p95 %dms", avg, p50, p95));
        }

        tvFailureReason.setText("失败: " + getTopFailureReason());
    }

    private void appendRunLog(String line) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String logLine = ts + "  " + line;
        currentRunLogs.add(logLine);
        CrashLogger.appendRunLog(this, logLine);
    }

    private void persistCurrentRunLog() {
        if (currentProjectDir == null || TextUtils.isEmpty(currentRunningTask) || currentRunLogs.isEmpty()) {
            return;
        }
        try {
            File taskDir = new File(currentProjectDir, currentRunningTask);
            File logDir = new File(taskDir, "run_logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            String fileName = "run_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date(currentRunStartMs)) + ".log";
            File logFile = new File(logDir, fileName);
            try (FileWriter writer = new FileWriter(logFile)) {
                for (String line : currentRunLogs) {
                    writer.write(line);
                    writer.write("\n");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "写入运行日志失败", e);
        }
    }

    private void transitionAfterRunStart(boolean showRunningPanelNow) {
        setBallVisible(false);
        if (showRunningPanelNow) {
            showRuntimeAwareProjectPanel();
            return;
        }
        smoothHideProjectPanel(() -> {
            hideRunningPanel();
            showProjectPanelDock();
            Toast.makeText(this, "后台运行中，可点悬浮球查看状态", Toast.LENGTH_SHORT).show();
        });
    }

    private void smoothHideProjectPanel(Runnable endAction) {
        if (projectPanelView == null) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        View panel = projectPanelView;
        panel.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction(() -> {
                    removeProjectPanel();
                    if (endAction != null) {
                        endAction.run();
                    }
                })
                .start();
    }

    private void showRunningPanelSmooth() {
        if (runningPanelView == null) {
            showRunningPanel();
        }
        if (runningPanelView != null) {
            runningPanelView.setAlpha(0f);
            runningPanelView.animate().alpha(1f).setDuration(180).start();
        }
    }

    private interface OnPointPickedListener {
        void onPointPicked(int x, int y);
    }

    private interface OnColorPointPickedListener {
        void onColorPointPicked(int x, int y, int color);
    }

    private void showTemplateLibraryDialog(AutoCompleteTextView templateInput, View ownerDialog) {
        List<TemplateLibraryAdapter.TemplateLibraryItem> items = getCurrentTaskTemplateLibraryItems();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_template_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        applyTemplateLibraryDialogViewport(dialogLp);
        wm.addView(dialogView, dialogLp);

        TextView tvTitle = dialogView.findViewById(R.id.tv_library_title);
        TextView btnAdd = dialogView.findViewById(R.id.btn_library_add);
        RecyclerView rv = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch = dialogView.findViewById(R.id.edt_library_search);
        TextView btnBatch = dialogView.findViewById(R.id.btn_library_batch);
        TextView btnDelete = dialogView.findViewById(R.id.btn_library_delete);
        View selectActions = dialogView.findViewById(R.id.ly_library_select_actions);
        if (tvTitle != null) {
            tvTitle.setText("选择模板图片");
        }
        if (btnAdd != null) {
            btnAdd.setVisibility(View.GONE);
        }
        rv.setLayoutManager(new GridLayoutManager(this, 3));
        final boolean[] batchMode = {false};
        final Runnable[] updateBatchUi = new Runnable[1];
        final TemplateLibraryAdapter[] adapterRef = new TemplateLibraryAdapter[1];
        TemplateLibraryAdapter adapter = new TemplateLibraryAdapter(items, item -> {
            templateInput.setText(item.fileName, false);
            refreshTemplateOptions(templateInput);
            updateTemplatePreview(ownerDialog.findViewById(R.id.iv_template_preview),
                    ownerDialog.findViewById(R.id.tv_template_preview_tip),
                    item.fileName);
            renderRecentTemplateStrip(ownerDialog, templateInput);
            safeRemoveView(dialogView);
        }, item -> {
            if (item == null || TextUtils.isEmpty(item.fileName)) {
                return;
            }
            if (item.usageCount > 0) {
                Toast.makeText(this, "模板仍被节点引用，暂时不能删除", Toast.LENGTH_SHORT).show();
                return;
            }

            Set<String> target = new HashSet<>();
            target.add(item.fileName);
            int deleted = deleteTemplateFiles(target);
            if (deleted <= 0) {
                Toast.makeText(this, "删除失败，请稍后重试", Toast.LENGTH_SHORT).show();
                return;
            }

            String currentTemplate = templateInput.getText() == null ? "" : templateInput.getText().toString().trim();
            if (item.fileName.equals(currentTemplate)) {
                templateInput.setText("", false);
            }

            Toast.makeText(this, "已删除模板 " + item.fileName, Toast.LENGTH_SHORT).show();
            if (adapterRef[0] == null) {
                return;
            }
            adapterRef[0].replaceData(getCurrentTaskTemplateLibraryItems());
            refreshTemplateOptions(templateInput);
            renderRecentTemplateStrip(ownerDialog, templateInput);
            updateTemplatePreview(ownerDialog.findViewById(R.id.iv_template_preview),
                    ownerDialog.findViewById(R.id.tv_template_preview_tip),
                    templateInput.getText() == null ? "" : templateInput.getText().toString().trim());
            refreshTemplateCachesForCurrentProject();
            if (adapterRef[0].getItemCount() == 0) {
                batchMode[0] = false;
                adapterRef[0].setBatchMode(false);
            }
            if (updateBatchUi[0] != null) {
                updateBatchUi[0].run();
            }
        });
        adapterRef[0] = adapter;
        adapter.setDeleteActionEnabled(true);
        rv.setAdapter(adapter);

        updateBatchUi[0] = () -> {
            int count = adapter.getSelectedCount();
            btnDelete.setText("删除(" + count + ")");
            btnBatch.setText(batchMode[0] ? "完成" : "批量");
        };
        adapter.setSelectionChangedListener(count -> updateBatchUi[0].run());
        updateBatchUi[0].run();

        if (selectActions != null) {
            selectActions.setVisibility(View.GONE);
        }
        dialogView.findViewById(R.id.btn_library_close).setOnClickListener(v -> safeRemoveView(dialogView));
        btnBatch.setOnClickListener(v -> {
            batchMode[0] = !batchMode[0];
            adapter.setBatchMode(batchMode[0]);
            updateBatchUi[0].run();
        });
        btnDelete.setOnClickListener(v -> {
            Set<String> selected = adapter.getSelectedFileNames();
            if (selected.isEmpty()) {
                Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show();
                return;
            }
            int deleted = deleteTemplateFiles(selected);
            int skipped = selected.size() - deleted;
            if (skipped > 0) {
                Toast.makeText(this, "已删 " + deleted + " 张，" + skipped + " 张仍被节点引用", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已删除 " + deleted + " 张图片", Toast.LENGTH_SHORT).show();
            }
            List<TemplateLibraryAdapter.TemplateLibraryItem> refreshed = getCurrentTaskTemplateLibraryItems();
            adapter.replaceData(refreshed);
            refreshTemplateOptions(templateInput);
            renderRecentTemplateStrip(ownerDialog, templateInput);
            updateTemplatePreview(ownerDialog.findViewById(R.id.iv_template_preview),
                    ownerDialog.findViewById(R.id.tv_template_preview_tip),
                    templateInput.getText() == null ? "" : templateInput.getText().toString().trim());
            refreshTemplateCachesForCurrentProject();
            if (adapter.getItemCount() == 0) {
                batchMode[0] = false;
                adapter.setBatchMode(false);
            }
            updateBatchUi[0].run();
        });
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.updateFilter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private int deleteTemplateFiles(Set<String> fileNames) {
        if (currentTaskDir == null || fileNames == null || fileNames.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        try {
            Map<String, Integer> usageMap = getTemplateUsageCountMap();
            File imgDir = new File(currentTaskDir, "img");
            for (String name : fileNames) {
                if (TextUtils.isEmpty(name)) {
                    continue;
                }
                int usageCount = usageMap.containsKey(name) ? usageMap.get(name) : 0;
                if (usageCount > 0) {
                    continue;
                }
                File file = new File(imgDir, name);
                if (file.exists() && file.isFile() && file.delete()) {
                    deleted++;
                }
            }

            File manifestFile = new File(imgDir, "manifest.json");
            if (manifestFile.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
                if (!TextUtils.isEmpty(content.trim())) {
                    JSONObject manifest = new JSONObject(content);
                    boolean changed = false;
                    for (String name : fileNames) {
                        if (manifest.has(name)) {
                            manifest.remove(name);
                            changed = true;
                        }
                    }
                    if (changed) {
                        try (FileWriter writer = new FileWriter(manifestFile)) {
                            writer.write(manifest.toString(2));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "删除模板文件失败", e);
        }
        return deleted;
    }

    private WindowManager.LayoutParams buildFullscreenOverlayParams() {
        return buildSelectionOverlayLayoutParams();
    }

    private WindowManager.LayoutParams buildSelectionOverlayLayoutParams() {
        return buildSelectionOverlayLayoutParams(false);
    }

    private WindowManager.LayoutParams buildSelectionOverlayLayoutParams(boolean preferAccessibilityOverlay) {
        int type;
        if (preferAccessibilityOverlay && AutoAccessibilityService.get() != null) {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    private Context getPickerOverlayContext() {
        AutoAccessibilityService service = AutoAccessibilityService.get();
        return service != null ? service : this;
    }

    private WindowManager getPickerOverlayWindowManager() {
        Context overlayContext = getPickerOverlayContext();
        WindowManager overlayWm = (WindowManager) overlayContext.getSystemService(Context.WINDOW_SERVICE);
        return overlayWm != null ? overlayWm : wm;
    }

    private WindowManager.LayoutParams buildPickerOverlayParams() {
        return buildSelectionOverlayLayoutParams(true);
    }

    @Nullable
    private Bitmap captureFreshScreenBitmap() {
        final Activity a = ActivityHolder.getTopActivity();

        if (a == null) return null;

        AutoAccessibilityService autoAccessibilityService = AutoAccessibilityService.get();
        if (autoAccessibilityService==null){
            return null;
        }

        AdaptivePollingController pollingController = AdaptivePollingController.forTemplateMatch();
        Mat mat = null;
        for (int i = 0; i < 6; i++) {
            mat = pollingController.acquireFrame();
            if (mat != null && !mat.empty()) {
                break;
            }
            SystemClock.sleep(80);
        }

        if (mat == null || mat.empty()) {
            return null;
        }

        Bitmap full = OpenCVHelper.getInstance().matToBitmap(mat);
        if (full == null || full.isRecycled()) {
            return null;
        }
        return full;
    }

    private void showScreenPointPicker(OnPointPickedListener listener) {
        showScreenPointPicker(listener, new View[0]);
    }

    private void showScreenPointPicker(OnPointPickedListener listener, View... viewsToHide) {
        List<View> hideTargets = new ArrayList<>();
        hideTargets.add(projectPanelView);
        if (viewsToHide != null) {
            Collections.addAll(hideTargets, viewsToHide);
        }
        Runnable restoreViews = hideViewsForCapture(hideTargets.toArray(new View[0]));
        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> {
            try {
                Bitmap fullBitmap = captureFreshScreenBitmap();
                if (fullBitmap == null || fullBitmap.isRecycled()) {
                    restoreViews.run();
                    Toast.makeText(this, "截图失败，无法取点", Toast.LENGTH_SHORT).show();
                    return;
                }

                WindowManager overlayWm = getPickerOverlayWindowManager();
                Context overlayContext = getPickerOverlayContext();
                View overlay = LayoutInflater.from(overlayContext).inflate(R.layout.dialog_pick_point_overlay, null);
                WindowManager.LayoutParams lp = buildPickerOverlayParams();
                com.auto.master.auto.ColorPointPickerView pickerView = overlay.findViewById(R.id.point_picker_view);
                View floatingPanel = overlay.findViewById(R.id.pick_point_floating_panel);
                TextView tvCoord = overlay.findViewById(R.id.tv_pick_point_coord);
                pickerView.setOnSelectionChangedListener((x, y, color) -> {
                    if (tvCoord != null) {
                        tvCoord.setText("x=" + x + ", y=" + y);
                    }
                });
                pickerView.setOnMagnifierLayoutChangedListener(rect ->
                        updateFloatingPickerPanelPosition(floatingPanel, rect));
                pickerView.setScreenshot(fullBitmap, true);

                overlay.findViewById(R.id.btn_pick_point_cancel).setOnClickListener(v -> {
                    pickerView.release();
                    safeRemoveView(overlayWm, overlay);
                    restoreViews.run();
                });
                overlay.findViewById(R.id.btn_pick_point_confirm).setOnClickListener(v -> {
                    if (!pickerView.hasSelection()) {
                        Toast.makeText(this, "请先移动到目标位置", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (listener != null) {
                        listener.onPointPicked(pickerView.getSelectedX(), pickerView.getSelectedY());
                    }
                    pickerView.release();
                    safeRemoveView(overlayWm, overlay);
                    restoreViews.run();
                });

                try {
                    overlayWm.addView(overlay, lp);
                } catch (Throwable t) {
                    pickerView.release();
                    throw t;
                }
            } catch (Exception e) {
                restoreViews.run();
                Toast.makeText(this, "打开取点器失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, 220);
    }

    private void showColorPointPicker(OnColorPointPickedListener listener, View... viewsToHide) {
        List<View> hideTargets = new ArrayList<>();
        hideTargets.add(projectPanelView);
        if (viewsToHide != null) {
            Collections.addAll(hideTargets, viewsToHide);
        }
        Runnable restoreViews = hideViewsForCapture(hideTargets.toArray(new View[0]));
        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> {
            try {
                Bitmap fullBitmap = captureFreshScreenBitmap();
                if (fullBitmap == null || fullBitmap.isRecycled()) {
                    restoreViews.run();
                    Toast.makeText(this, "截图失败，无法取色", Toast.LENGTH_SHORT).show();
                    return;
                }

                WindowManager overlayWm = getPickerOverlayWindowManager();
                Context overlayContext = getPickerOverlayContext();
                View overlay = LayoutInflater.from(overlayContext).inflate(R.layout.dialog_pick_color_overlay, null);
                WindowManager.LayoutParams lp = buildPickerOverlayParams();
                com.auto.master.auto.ColorPointPickerView pickerView = overlay.findViewById(R.id.color_picker_view);
                View floatingPanel = overlay.findViewById(R.id.pick_color_floating_panel);
                TextView tvColorValue = overlay.findViewById(R.id.tv_pick_color_value);
                TextView tvCoord = overlay.findViewById(R.id.tv_pick_color_coord);
                View preview = overlay.findViewById(R.id.view_pick_color_preview);
                pickerView.setOnSelectionChangedListener((x, y, color) -> {
                    if (tvColorValue != null) {
                        tvColorValue.setText(String.format(Locale.getDefault(), "#%06X", 0xFFFFFF & color));
                    }
                    if (tvCoord != null) {
                        tvCoord.setText("x=" + x + ", y=" + y);
                    }
                    if (preview != null) {
                        preview.setBackgroundColor(color);
                    }
                });
                pickerView.setOnMagnifierLayoutChangedListener(rect ->
                        updateFloatingPickerPanelPosition(floatingPanel, rect));
                pickerView.setScreenshot(fullBitmap, true);

                overlay.findViewById(R.id.btn_pick_color_cancel).setOnClickListener(v -> {
                    pickerView.release();
                    safeRemoveView(overlayWm, overlay);
                    restoreViews.run();
                });
                overlay.findViewById(R.id.btn_pick_color_confirm).setOnClickListener(v -> {
                    if (!pickerView.hasSelection()) {
                        Toast.makeText(this, "请先移动到目标像素", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int x = pickerView.getSelectedX();
                    int y = pickerView.getSelectedY();
                    int color = pickerView.getSelectedColor();
                    if (listener != null) {
                        listener.onColorPointPicked(x, y, color);
                    }
                    pickerView.release();
                    safeRemoveView(overlayWm, overlay);
                    restoreViews.run();
                });

                try {
                    overlayWm.addView(overlay, lp);
                } catch (Throwable t) {
                    pickerView.release();
                    throw t;
                }
            } catch (Exception e) {
                restoreViews.run();
                Toast.makeText(this, "打开取色器失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, 220);
    }

    private void updateFloatingPickerPanelPosition(View panel, RectF magnifierBounds) {
        if (panel == null || magnifierBounds == null) {
            return;
        }
        panel.post(() -> {
            if (!(panel.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                return;
            }
            View parent = (View) panel.getParent();
            if (parent == null) {
                return;
            }
            int parentWidth = parent.getWidth();
            int parentHeight = parent.getHeight();
            if (parentWidth <= 0 || parentHeight <= 0) {
                return;
            }
            if (panel.getWidth() <= 0 || panel.getHeight() <= 0) {
                panel.measure(
                        View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(parentHeight, View.MeasureSpec.AT_MOST)
                );
            }
            int panelWidth = Math.max(panel.getWidth(), panel.getMeasuredWidth());
            int panelHeight = Math.max(panel.getHeight(), panel.getMeasuredHeight());
            int margin = dp(12);
            int gap = dp(12);

            int left;
            int top;
            if (magnifierBounds.right + gap + panelWidth <= parentWidth - margin) {
                left = Math.round(magnifierBounds.right + gap);
                top = Math.round(magnifierBounds.centerY() - panelHeight / 2f);
            } else if (magnifierBounds.left - gap - panelWidth >= margin) {
                left = Math.round(magnifierBounds.left - gap - panelWidth);
                top = Math.round(magnifierBounds.centerY() - panelHeight / 2f);
            } else if (magnifierBounds.bottom + gap + panelHeight <= parentHeight - margin) {
                left = Math.round(magnifierBounds.centerX() - panelWidth / 2f);
                top = Math.round(magnifierBounds.bottom + gap);
            } else {
                left = Math.round(magnifierBounds.centerX() - panelWidth / 2f);
                top = Math.round(magnifierBounds.top - gap - panelHeight);
            }

            left = Math.max(margin, Math.min(left, parentWidth - panelWidth - margin));
            top = Math.max(margin, Math.min(top, parentHeight - panelHeight - margin));

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) panel.getLayoutParams();
            layoutParams.gravity = Gravity.TOP | Gravity.START;
            layoutParams.leftMargin = left;
            layoutParams.topMargin = top;
            panel.setLayoutParams(layoutParams);
        });
    }

    private void setupAdvancedMatchSection(View dialogView, JSONObject operationObject, String excludeId) {
        View toggle = dialogView.findViewById(R.id.ly_advanced_toggle);
        LinearLayout panel = dialogView.findViewById(R.id.ly_advanced_panel);
        TextView toggleText = dialogView.findViewById(R.id.tv_advanced_toggle);
        TextView arrow = dialogView.findViewById(R.id.tv_advanced_arrow);

        CheckBox chkGray = dialogView.findViewById(R.id.chk_use_gray);
        CheckBox chkSuccessClick = dialogView.findViewById(R.id.chk_success_click);
        CheckBox chkUseCanny = dialogView.findViewById(R.id.chk_use_canny);
        AutoCompleteTextView methodInput = dialogView.findViewById(R.id.edt_match_method);
        EditText scaleFactorInput = dialogView.findViewById(R.id.edt_scale_factor);
        AutoCompleteTextView fallbackInput = dialogView.findViewById(R.id.edt_fallback_operation);
        EditText preDelayInput = dialogView.findViewById(R.id.edt_match_pre_delay);
        EditText postDelayInput = dialogView.findViewById(R.id.edt_match_post_delay);

        bindAutoComplete(methodInput, getMatchMethodOptions());
        bindAutoComplete(fallbackInput, getCurrentTaskOperationIds(excludeId));
        methodInput.setText(METHOD_TM_CCOEFF_NORMED, false);
        scaleFactorInput.setText("1.0");
        chkSuccessClick.setChecked(true);
        chkUseCanny.setChecked(false);
        if (preDelayInput != null) preDelayInput.setText("0");
        if (postDelayInput != null) postDelayInput.setText("0");

        if (operationObject != null) {
            JSONObject inputMap = operationObject.optJSONObject("inputMap");
            if (inputMap != null) {
                chkGray.setChecked(inputMap.optBoolean(MetaOperation.MATCHUSEGRAY, false));
                chkSuccessClick.setChecked(inputMap.optBoolean(MetaOperation.SUCCEESCLICK, true));
                chkUseCanny.setChecked(inputMap.optBoolean(MetaOperation.MATCHUSECANNARY, false));
                double method = inputMap.optDouble(MetaOperation.MATCHMETHOD, 5);
                methodInput.setText(methodLabelFromCode(method), false);
                Object sfObj = inputMap.opt(MetaOperation.MATCHSCALEFACTOR);
                if (sfObj != null) {
                    scaleFactorInput.setText(String.valueOf(sfObj));
                }
                fallbackInput.setText(inputMap.optString(MetaOperation.FALLBACKOPERATIONID, ""), false);
                Object preDelayObj = inputMap.opt(MetaOperation.MATCH_PRE_DELAY_MS);
                Object postDelayObj = inputMap.opt(MetaOperation.MATCH_POST_DELAY_MS);
                if (preDelayObj != null && preDelayInput != null) {
                    preDelayInput.setText(String.valueOf(preDelayObj).replace(".0", ""));
                }
                if (postDelayObj != null && postDelayInput != null) {
                    postDelayInput.setText(String.valueOf(postDelayObj).replace(".0", ""));
                }
            }
        }

        if (toggle != null && panel != null && toggleText != null && arrow != null) {
            toggle.setOnClickListener(v -> {
                boolean expanding = panel.getVisibility() != View.VISIBLE;
                panel.setVisibility(expanding ? View.VISIBLE : View.GONE);
                toggleText.setText(expanding ? "收起高级参数" : "展开高级参数");
                arrow.setText(expanding ? "▲" : "▼");
            });
        }
    }

    private void fillAdvancedMatchInputMap(View dialogView, JSONObject inputMap) {
        CheckBox chkGray = dialogView.findViewById(R.id.chk_use_gray);
        CheckBox chkSuccessClick = dialogView.findViewById(R.id.chk_success_click);
        CheckBox chkUseCanny = dialogView.findViewById(R.id.chk_use_canny);
        AutoCompleteTextView methodInput = dialogView.findViewById(R.id.edt_match_method);
        AutoCompleteTextView fallbackInput = dialogView.findViewById(R.id.edt_fallback_operation);
        EditText scaleFactorInput = dialogView.findViewById(R.id.edt_scale_factor);
        EditText preDelayInput = dialogView.findViewById(R.id.edt_match_pre_delay);
        EditText postDelayInput = dialogView.findViewById(R.id.edt_match_post_delay);

        String methodText = methodInput == null || methodInput.getText() == null ? "" : methodInput.getText().toString().trim();
        String sfText = scaleFactorInput == null || scaleFactorInput.getText() == null ? "" : scaleFactorInput.getText().toString().trim();

        int methodCode = parseMethodCode(methodText);
        double scaleFactor = 1.0;
        if (!TextUtils.isEmpty(sfText)) {
            try {
                scaleFactor = Double.parseDouble(sfText);
            } catch (Exception ignored) {
                scaleFactor = 1.0;
            }
        }
        if (scaleFactor <= 0) {
            scaleFactor = 1.0;
        }
        String fallbackId = fallbackInput == null || fallbackInput.getText() == null
                ? ""
                : fallbackInput.getText().toString().trim();
        String preDelayText = preDelayInput == null || preDelayInput.getText() == null
                ? ""
                : preDelayInput.getText().toString().trim();
        String postDelayText = postDelayInput == null || postDelayInput.getText() == null
                ? ""
                : postDelayInput.getText().toString().trim();
        long preDelay = 0L;
        long postDelay = 0L;
        try {
            if (!TextUtils.isEmpty(preDelayText)) preDelay = Long.parseLong(preDelayText);
        } catch (Exception ignored) {}
        try {
            if (!TextUtils.isEmpty(postDelayText)) postDelay = Long.parseLong(postDelayText);
        } catch (Exception ignored) {}
        if (preDelay < 0) preDelay = 0;
        if (postDelay < 0) postDelay = 0;
        if (preDelay > 5000) preDelay = 5000;
        if (postDelay > 5000) postDelay = 5000;

        try {
            inputMap.put(MetaOperation.MATCHUSEGRAY, chkGray != null && chkGray.isChecked());
            inputMap.put(MetaOperation.MATCHMETHOD, (double) methodCode);
            inputMap.put(MetaOperation.MATCHSCALEFACTOR, scaleFactor);
            inputMap.put(MetaOperation.SUCCEESCLICK, chkSuccessClick != null && chkSuccessClick.isChecked());
            inputMap.put(MetaOperation.MATCHUSECANNARY, chkUseCanny != null && chkUseCanny.isChecked());
            if (preDelay > 0) {
                inputMap.put(MetaOperation.MATCH_PRE_DELAY_MS, (double) preDelay);
            } else {
                inputMap.remove(MetaOperation.MATCH_PRE_DELAY_MS);
            }
            if (postDelay > 0) {
                inputMap.put(MetaOperation.MATCH_POST_DELAY_MS, (double) postDelay);
            } else {
                inputMap.remove(MetaOperation.MATCH_POST_DELAY_MS);
            }
            if (TextUtils.isEmpty(fallbackId)) {
                inputMap.remove(MetaOperation.FALLBACKOPERATIONID);
            } else {
                inputMap.put(MetaOperation.FALLBACKOPERATIONID, fallbackId);
            }
        } catch (Exception e) {
            Log.w(TAG, "写入高级匹配参数失败", e);
        }
    }

    private boolean launchTemplateCapture(String templateFileName) {
        if (currentProjectDir == null || currentTaskDir == null) {
            Toast.makeText(this, "当前 Project/Task 无效", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (TextUtils.isEmpty(templateFileName)) {
            Toast.makeText(this, "请先输入模板文件名", Toast.LENGTH_SHORT).show();
            return false;
        }

        String finalName = templateFileName.endsWith(".png") ? templateFileName : templateFileName + ".png";
        CropRegionOperation cropOperation = new CropRegionOperation();
        cropOperation.setId("capture_template_" + System.currentTimeMillis());
        cropOperation.setResponseType(3);
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put(MetaOperation.PROJECT, currentProjectDir.getName());
        inputMap.put(MetaOperation.TASK, currentTaskDir.getName());
        inputMap.put(MetaOperation.SAVEFILENAME, finalName);
        cropOperation.setInputMap(inputMap);

        OperationHandler handler = OperationHandlerManager.getOperationHandler(3);
        if (handler == null) {
            Toast.makeText(this, "截图功能不可用", Toast.LENGTH_SHORT).show();
            return false;
        }
        handler.handle(cropOperation, new OperationContext());
        Toast.makeText(this, "已进入框选截图，请在屏幕上选择模板区域", Toast.LENGTH_SHORT).show();
        return true;
    }

    private String normalizeClickTarget(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(-?\\d+)\\D+(-?\\d+)").matcher(raw.trim());
        if (!matcher.find()) {
            return null;
        }
        return "(" + matcher.group(1) + "," + matcher.group(2) + ")";
    }

    private JSONObject getInputMapOrCreate(JSONObject operationObject) {
        JSONObject inputMap = operationObject.optJSONObject("inputMap");
        if (inputMap == null) {
            inputMap = new JSONObject();
        }
        return inputMap;
    }

    private String inputMapString(JSONObject operationObject, String key) {
        JSONObject inputMap = operationObject.optJSONObject("inputMap");
        return inputMap == null ? "" : inputMap.optString(key, "");
    }

    private long inputMapLong(JSONObject operationObject, String key, long defVal) {
        JSONObject inputMap = operationObject.optJSONObject("inputMap");
        if (inputMap == null) {
            return defVal;
        }
        return inputMap.optLong(key, defVal);
    }

    private boolean inputMapBoolean(JSONObject operationObject, String key, boolean defVal) {
        JSONObject inputMap = operationObject.optJSONObject("inputMap");
        if (inputMap == null) {
            return defVal;
        }
        return inputMap.optBoolean(key, defVal);
    }

    private String normalizePackageName(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        Matcher matcher = Pattern.compile("([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)").matcher(raw.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return raw.trim();
    }

    private List<LaunchAppPickerAdapter.LaunchAppItem> getLaunchableApps() {
        List<LaunchAppPickerAdapter.LaunchAppItem> items = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        Set<String> seenPackages = new HashSet<>();
        if (infos == null) {
            return items;
        }
        for (ResolveInfo info : infos) {
            if (info == null || info.activityInfo == null || info.activityInfo.applicationInfo == null) {
                continue;
            }
            String packageName = info.activityInfo.applicationInfo.packageName;
            if (TextUtils.isEmpty(packageName) || !seenPackages.add(packageName)) {
                continue;
            }
            CharSequence label = info.loadLabel(pm);
            Drawable icon = info.loadIcon(pm);
            items.add(new LaunchAppPickerAdapter.LaunchAppItem(label == null ? packageName : label.toString(), packageName, icon));
        }
        Collections.sort(items, (a, b) -> {
            int labelCompare = a.label.compareToIgnoreCase(b.label);
            if (labelCompare != 0) {
                return labelCompare;
            }
            return a.packageName.compareToIgnoreCase(b.packageName);
        });
        return items;
    }

    @Nullable
    private LaunchAppPickerAdapter.LaunchAppItem findLaunchableApp(String packageName) {
        String normalized = normalizePackageName(packageName);
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }
        for (LaunchAppPickerAdapter.LaunchAppItem item : getLaunchableApps()) {
            if (normalized.equals(item.packageName)) {
                return item;
            }
        }
        return null;
    }

    private void refreshLaunchableAppOptions(AutoCompleteTextView view) {
        if (view == null) {
            return;
        }
        List<String> options = new ArrayList<>();
        for (LaunchAppPickerAdapter.LaunchAppItem item : getLaunchableApps()) {
            options.add(item.packageName);
        }
        bindAutoComplete(view, options);
    }

    private void updateLaunchAppSummary(TextView summaryView, String packageName) {
        if (summaryView == null) {
            return;
        }
        String normalized = normalizePackageName(packageName);
        if (TextUtils.isEmpty(normalized)) {
            summaryView.setText("状态: 未选择应用");
            return;
        }
        LaunchAppPickerAdapter.LaunchAppItem item = findLaunchableApp(normalized);
        if (item == null) {
            summaryView.setText("状态: 将尝试启动 " + normalized);
            return;
        }
        summaryView.setText("状态: " + item.label + " (" + item.packageName + ")");
    }

    private void showAppPickerDialog(String title,
                                     @Nullable String currentPackage,
                                     AutoCompleteTextView packageView,
                                     @Nullable EditText nameView,
                                     @Nullable TextView summaryView) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_operation_picker, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        wm.addView(dialogView, dialogLp);

        TextView tvTitle = dialogView.findViewById(R.id.tv_picker_title);
        EditText edtSearch = dialogView.findViewById(R.id.edt_picker_search);
        RecyclerView rvPicker = dialogView.findViewById(R.id.rv_picker);
        if (tvTitle != null) {
            tvTitle.setText(title);
        }
        if (edtSearch != null) {
            edtSearch.setHint("搜索应用名或包名");
        }
        rvPicker.setLayoutManager(new LinearLayoutManager(this));
        LaunchAppPickerAdapter adapter = new LaunchAppPickerAdapter(getLaunchableApps(), item -> {
            packageView.setText(item.packageName);
            packageView.setSelection(item.packageName.length());
            if (summaryView != null) {
                updateLaunchAppSummary(summaryView, item.packageName);
            }
            if (nameView != null && TextUtils.isEmpty(nameView.getText() == null ? "" : nameView.getText().toString().trim())) {
                nameView.setText("打开" + item.label);
            }
            safeRemoveView(dialogView);
        });
        rvPicker.setAdapter(adapter);

        if (!TextUtils.isEmpty(currentPackage)) {
            if (edtSearch != null) {
                edtSearch.setText(normalizePackageName(currentPackage));
            }
            adapter.updateFilter(currentPackage);
        }

        if (edtSearch != null) {
            edtSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.updateFilter(s == null ? "" : s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        View btnClose = dialogView.findViewById(R.id.btn_picker_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> safeRemoveView(dialogView));
        }
        View btnClear = dialogView.findViewById(R.id.btn_picker_clear);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                packageView.setText("");
                if (summaryView != null) {
                    updateLaunchAppSummary(summaryView, "");
                }
                safeRemoveView(dialogView);
            });
        }
    }

    @Nullable
    private List<Integer> parseBboxInput(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(-?\\d+)\\D+(-?\\d+)\\D+(-?\\d+)\\D+(-?\\d+)").matcher(raw.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int w = Integer.parseInt(matcher.group(3));
            int h = Integer.parseInt(matcher.group(4));
            if (w <= 1 || h <= 1) {
                return null;
            }
            List<Integer> bbox = new ArrayList<>();
            bbox.add(x);
            bbox.add(y);
            bbox.add(w);
            bbox.add(h);
            return bbox;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void updateOcrBboxStatus(TextView statusView, @Nullable List<Integer> bbox) {
        if (statusView == null) {
            return;
        }
        if (bbox == null || bbox.size() < 4) {
            statusView.setText("状态：未框选区域");
            return;
        }
        statusView.setText("状态：已选区域 x=" + bbox.get(0) + ", y=" + bbox.get(1)
                + ", w=" + bbox.get(2) + ", h=" + bbox.get(3));
    }

    private void beginRegionPickFromDialog(View dialogView, EditText edtBbox, TextView statusView) {

        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            toastOnMain("服务未启动");
            return;
        }

        Context ctx = svc;
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        Runnable restoreViews = hideViewsForCapture(dialogView, projectPanelView);
        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> {
            try {
                Bitmap fullBitmap = captureFreshScreenBitmap();
                if (fullBitmap == null || fullBitmap.isRecycled()) {
                    restoreViews.run();
//                    Toast.makeText(this, "截图失败，无法框选", Toast.LENGTH_SHORT).show();
                    toastOnMain("截图失败，无法框选");
                    return;
                }

                final int layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
                final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        layoutType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                        PixelFormat.TRANSLUCENT
                );
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lp.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                }


                SelectionOverlayView overlay = new SelectionOverlayView(ctx);
                overlay.setFrozenBackground(fullBitmap, true);
                overlay.setListener(new SelectionOverlayView.Listener() {
                    @Override
                    public void onConfirm(Rect rectInOverlay, Bitmap croppedBitmap) {
                        try {
                            int x = rectInOverlay.left;
                            int y = rectInOverlay.top;
                            int w = rectInOverlay.width();
                            int h = rectInOverlay.height();
                            if (w <= 1 || h <= 1) {
                                toastOnMain("选区太小");
                            } else {
                                String text = x + "," + y + "," + w + "," + h;
                                edtBbox.setText(text);
                                updateOcrBboxStatus(statusView, parseBboxInput(text));
                            }
                        } finally {
                            safeRemoveView(overlay);
                            restoreViews.run();
                        }
                    }

                    @Override
                    public void onCancel() {
                        safeRemoveView(overlay);
                        restoreViews.run();
                    }
                });
                try {
                    wm.addView(overlay, lp);
                } catch (Throwable t) {
                    if (!fullBitmap.isRecycled()) {
                        fullBitmap.recycle();
                    }
                    throw t;
                }
                toastOnMain("请拖动框选OCR区域，然后点确定");
            } catch (Exception e) {
                restoreViews.run();
                toastOnMain("框选失败: " + e.getMessage());
            }
        }, 220);
    }

    private void testOcrFromDialog(EditText edtBbox, EditText edtTimeout, AutoCompleteTextView edtEngine, TextView resultView) {
        List<Integer> bbox = parseBboxInput(edtBbox.getText() == null ? "" : edtBbox.getText().toString());
        if (bbox == null) {
            edtBbox.setError("请先框选或输入 x,y,w,h");
            return;
        }
        long timeout;
        try {
            timeout = Long.parseLong(edtTimeout.getText() == null ? "5000" : edtTimeout.getText().toString().trim());
        } catch (Exception e) {
            timeout = 5000L;
        }
        if (timeout <= 0) {
            timeout = 5000L;
        }
        String engine = edtEngine == null || edtEngine.getText() == null ? "paddle" : edtEngine.getText().toString().trim();
        boolean accurateMode = !"fast".equalsIgnoreCase(engine);
        if (resultView != null) {
            resultView.setText("测试结果：识别中...");
        }

        final long finalTimeout = timeout;
        final boolean finalAccurateMode = accurateMode;
        new Thread(() -> {
            String text = recognizeOcrTextForRegion(bbox, finalTimeout, finalAccurateMode, engine);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (TextUtils.isEmpty(text)) {
                    if (resultView != null) {
                        resultView.setText("测试结果：未识别到文本");
                    }
                    Toast.makeText(this, "测试识别为空", Toast.LENGTH_SHORT).show();
                    showOcrTestResultDialog("测试结果", "未识别到文本");
                } else {
                    if (resultView != null) {
                        String preview = text.length() > 120 ? text.substring(0, 120) + "..." : text;
                        resultView.setText("测试结果预览：\n" + preview + "\n\n(点击“测试识别结果”可再次查看完整内容)");
                    }
                    Toast.makeText(this, "测试识别完成", Toast.LENGTH_SHORT).show();
                    showOcrTestResultDialog("测试结果", text);
                }
            });
        }, "ocr-test").start();
    }

    private void showOcrTestResultDialog(String title, String content) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_json, null);
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams dialogLp = new WindowManager.LayoutParams(
                dp(360), WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        dialogLp.gravity = Gravity.CENTER;

        TextView tvOpId = dialogView.findViewById(R.id.tv_op_id);
        EditText edtJson = dialogView.findViewById(R.id.edt_json);
        tvOpId.setText(title);
        edtJson.setText(content == null ? "" : content);
        edtJson.setSelection(0);

        TextView btnSave = dialogView.findViewById(R.id.btn_save);
        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        btnSave.setText("复制");
        btnCancel.setText("关闭");

        btnSave.setOnClickListener(v -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("ocr_result", edtJson.getText().toString()));
                    Toast.makeText(this, "已复制测试结果", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "复制失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        btnCancel.setOnClickListener(v -> safeRemoveView(dialogView));

        wm.addView(dialogView, dialogLp);
    }

    @Nullable
    private String recognizeOcrTextForRegion(List<Integer> bbox, long timeoutMs, boolean accurateMode, String engine) {
        try {
            Bitmap fullBitmap = captureFreshScreenBitmap();
            if (fullBitmap == null || fullBitmap.isRecycled()) {
                Toast.makeText(this, "截图失败，无法取色", Toast.LENGTH_SHORT).show();
                return null;
            }

            int x = Math.max(0, bbox.get(0));
            int y = Math.max(0, bbox.get(1));
            int w = Math.max(1, bbox.get(2));
            int h = Math.max(1, bbox.get(3));
            x = Math.min(x, fullBitmap.getWidth() - 1);
            y = Math.min(y, fullBitmap.getHeight() - 1);
            w = Math.min(w, fullBitmap.getWidth() - x);
            h = Math.min(h, fullBitmap.getHeight() - y);
            if (w <= 1 || h <= 1) {
                return null;
            }
            Bitmap crop = Bitmap.createBitmap(fullBitmap, x, y, w, h);
            return OcrEngine.recognize(this, crop, engine, accurateMode, timeoutMs);
        } catch (Exception e) {
            Log.e(TAG, "OCR测试异常", e);
            return null;
        }
    }

    private void showAddLaunchAppDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_launch_app, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 460);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtAppPackage = dialogView.findViewById(R.id.edt_app_package);
        TextView tvAppSummary = dialogView.findViewById(R.id.tv_app_summary);
        EditText edtLaunchDelay = dialogView.findViewById(R.id.edt_launch_delay);
        CheckBox cbSkipIfForeground = dialogView.findViewById(R.id.cb_skip_if_foreground);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        refreshLaunchableAppOptions(edtAppPackage);
        bindNextOperationSuggestions(dialogView, null);
        edtLaunchDelay.setText("1500");
        cbSkipIfForeground.setChecked(true);
        updateLaunchAppSummary(tvAppSummary, "");

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_app).setOnClickListener(v ->
                showAppPickerDialog("选择要启动的应用",
                        edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString(),
                        edtAppPackage,
                        edtName,
                        tvAppSummary));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));

        edtAppPackage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLaunchAppSummary(tvAppSummary, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String packageName = normalizePackageName(edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString());
            String delayValue = edtLaunchDelay.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(packageName)) {
                edtAppPackage.setError("请选择或输入应用包名");
                return;
            }

            long launchDelayMs;
            try {
                launchDelayMs = Long.parseLong(delayValue);
            } catch (Exception e) {
                edtLaunchDelay.setError("请输入有效的毫秒数");
                return;
            }
            if (launchDelayMs < 0L) {
                edtLaunchDelay.setError("等待时间不能小于 0");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 14);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.APP_PACKAGE, packageName);
                LaunchAppPickerAdapter.LaunchAppItem appItem = findLaunchableApp(packageName);
                if (appItem != null) {
                    inputMap.put(MetaOperation.APP_LABEL, appItem.label);
                }
                inputMap.put(MetaOperation.APP_SKIP_IF_FOREGROUND, cbSkipIfForeground.isChecked());
                inputMap.put(MetaOperation.APP_LAUNCH_DELAY_MS, launchDelayMs);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建启动应用节点失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditLaunchAppDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_launch_app, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 460);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtAppPackage = dialogView.findViewById(R.id.edt_app_package);
        TextView tvAppSummary = dialogView.findViewById(R.id.tv_app_summary);
        EditText edtLaunchDelay = dialogView.findViewById(R.id.edt_launch_delay);
        CheckBox cbSkipIfForeground = dialogView.findViewById(R.id.cb_skip_if_foreground);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        refreshLaunchableAppOptions(edtAppPackage);
        bindNextOperationSuggestions(dialogView, selected.id);

        String packageName = inputMapString(operationObject, MetaOperation.APP_PACKAGE);
        edtName.setText(operationObject.optString("name", selected.name));
        edtAppPackage.setText(packageName);
        edtLaunchDelay.setText(String.valueOf(inputMapLong(operationObject, MetaOperation.APP_LAUNCH_DELAY_MS, 1500L)));
        cbSkipIfForeground.setChecked(inputMapBoolean(operationObject, MetaOperation.APP_SKIP_IF_FOREGROUND, true));
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));
        updateLaunchAppSummary(tvAppSummary, packageName);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_app).setOnClickListener(v ->
                showAppPickerDialog("选择要启动的应用",
                        edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString(),
                        edtAppPackage,
                        edtName,
                        tvAppSummary));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", selected.id, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));

        edtAppPackage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLaunchAppSummary(tvAppSummary, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String normalizedPackage = normalizePackageName(edtAppPackage.getText() == null ? "" : edtAppPackage.getText().toString());
            String delayValue = edtLaunchDelay.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(normalizedPackage)) {
                edtAppPackage.setError("请选择或输入应用包名");
                return;
            }

            long launchDelayMs;
            try {
                launchDelayMs = Long.parseLong(delayValue);
            } catch (Exception e) {
                edtLaunchDelay.setError("请输入有效的毫秒数");
                return;
            }
            if (launchDelayMs < 0L) {
                edtLaunchDelay.setError("等待时间不能小于 0");
                return;
            }

            try {
                operationObject.put("name", name);
                operationObject.put("type", 14);
                operationObject.put("responseType", 1);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.APP_PACKAGE, normalizedPackage);
                LaunchAppPickerAdapter.LaunchAppItem appItem = findLaunchableApp(normalizedPackage);
                if (appItem != null) {
                    inputMap.put(MetaOperation.APP_LABEL, appItem.label);
                } else {
                    inputMap.remove(MetaOperation.APP_LABEL);
                }
                inputMap.put(MetaOperation.APP_SKIP_IF_FOREGROUND, cbSkipIfForeground.isChecked());
                inputMap.put(MetaOperation.APP_LAUNCH_DELAY_MS, launchDelayMs);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddClickDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_click, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 340, 420);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtClickTarget = dialogView.findViewById(R.id.edt_click_target);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        bindNextOperationSuggestions(dialogView, null);
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_pick_point).setOnClickListener(v ->
                showScreenPointPicker((x, y) -> edtClickTarget.setText(x + "," + y), dialogView, projectPanelView));

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String clickTarget = normalizeClickTarget(edtClickTarget.getText().toString().trim());
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (clickTarget == null) {
                edtClickTarget.setError("坐标格式示例: 500,800");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 1);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.CLICK_TARGET, clickTarget);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建点击操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddDelayDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_delay, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 340, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtDuration = dialogView.findViewById(R.id.edt_duration);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        bindNextOperationSuggestions(dialogView, null);
        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String durationStr = edtDuration.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(durationStr)) {
                edtDuration.setError("请填写延时时间");
                return;
            }

            long duration;
            try {
                duration = Long.parseLong(durationStr);
            } catch (NumberFormatException e) {
                edtDuration.setError("请输入有效的毫秒数");
                return;
            }
            if (duration < 0) {
                edtDuration.setError("延时时间不能小于 0");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 2);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.SLEEP_DURATION, duration);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建延时操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddGestureDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_gesture, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 350, 440);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtGestureFile = dialogView.findViewById(R.id.edt_gesture_file);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView tvGestureStatus = dialogView.findViewById(R.id.tv_gesture_status);

        refreshGestureOptions(edtGestureFile);
        bindNextOperationSuggestions(dialogView, null);
        edtGestureFile.setText(generateGestureTimestampName());
        updateGestureStatus(tvGestureStatus, edtGestureFile.getText() == null ? "" : edtGestureFile.getText().toString());

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_gesture_ts).setOnClickListener(v -> edtGestureFile.setText(generateGestureTimestampName()));
        dialogView.findViewById(R.id.btn_gesture_library).setOnClickListener(v ->
                showGestureLibraryDialog(edtGestureFile, tvGestureStatus));
        dialogView.findViewById(R.id.btn_play_gesture).setOnClickListener(v ->
                playGestureFromInput(edtGestureFile, tvGestureStatus));
        dialogView.findViewById(R.id.btn_record_gesture).setOnClickListener(v ->
                beginGestureRecordFromDialog(dialogView, edtGestureFile, tvGestureStatus));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));

        edtGestureFile.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateGestureStatus(tvGestureStatus, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String gestureFile = normalizeGestureFileName(edtGestureFile.getText() == null ? "" : edtGestureFile.getText().toString().trim());
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(gestureFile)) {
                edtGestureFile.setError("请填写手势文件名");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 5);
                operationObject.put("responseType", 2);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.PROJECT, currentProjectDir != null ? currentProjectDir.getName() : "");
                inputMap.put(MetaOperation.TASK, currentTaskDir != null ? currentTaskDir.getName() : "");
                inputMap.put(MetaOperation.SAVEFILENAME, gestureFile);
                inputMap.put(MetaOperation.GESTURE_TEMPLATE_ID, gestureFile);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建手势操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddJumpTaskDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_jump_task, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtTargetTask = dialogView.findViewById(R.id.edt_target_task);
        AutoCompleteTextView edtTargetOperation = dialogView.findViewById(R.id.edt_target_operation);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        bindAutoComplete(edtTargetTask, getCurrentProjectTaskIds());
        bindNextOperationSuggestions(dialogView, null);
        edtTargetTask.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                bindAutoComplete(edtTargetOperation, getTaskOperationIds(s == null ? "" : s.toString().trim()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String targetTaskId = edtTargetTask.getText().toString().trim();
            String targetOperationId = edtTargetOperation.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(targetTaskId)) {
                edtTargetTask.setError("请填写目标 Task ID");
                return;
            }
            if (TextUtils.isEmpty(targetOperationId)) {
                edtTargetOperation.setError("请填写目标 Operation ID");
                return;
            }

            boolean returnAfterComplete = !TextUtils.isEmpty(nextOp);

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 8);
                operationObject.put("responseType", returnAfterComplete ? 2 : 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.TARGET_TASK_ID, targetTaskId);
                inputMap.put(MetaOperation.TARGET_OPERATION_ID, targetOperationId);
                inputMap.put(MetaOperation.RETURN_AFTER_COMPLETE, returnAfterComplete);
                if (returnAfterComplete) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建跳转操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddBackKeyDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_back_key, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 340, 350);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        edtName.setText("返回按键");
        bindNextOperationSuggestions(dialogView, null);
        
        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 17);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建返回按键操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddMatchTemplateDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_match_template, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 350, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtTemplateFile = dialogView.findViewById(R.id.edt_template_file);
        EditText edtSimilarity = dialogView.findViewById(R.id.edt_similarity);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallbackOperation = dialogView.findViewById(R.id.edt_fallback_operation);

        String defaultTemplate = "tpl_" + System.currentTimeMillis() + ".png";
        edtTemplateFile.setText(defaultTemplate);
        edtSimilarity.setText("0.85");
        edtTimeout.setText("5000");
        refreshTemplateOptions(edtTemplateFile);
        bindNextOperationSuggestions(dialogView, null);
        bindTemplatePreview(dialogView, edtTemplateFile);
        renderRecentTemplateStrip(dialogView, edtTemplateFile);
        setupAdvancedMatchSection(dialogView, null, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_template_ts).setOnClickListener(v -> edtTemplateFile.setText(generateTemplateTimestampName()));
        dialogView.findViewById(R.id.btn_template_library).setOnClickListener(v ->
                showTemplateLibraryDialog(edtTemplateFile, dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v ->
                showOperationPickerDialog("选择失败跳转节点", null, edtFallbackOperation::setText));
        edtTemplateFile.setOnClickListener(v -> {
            refreshTemplateOptions(edtTemplateFile);
            edtTemplateFile.showDropDown();
        });

        dialogView.findViewById(R.id.btn_capture).setOnClickListener(v -> {
            beginTemplateCaptureFromDialog(dialogView, edtTemplateFile);
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String templateFile = edtTemplateFile.getText().toString().trim();
            String similarityText = edtSimilarity.getText().toString().trim();
            String timeoutText = edtTimeout.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(templateFile)) {
                edtTemplateFile.setError("请填写模板文件名");
                return;
            }

            double similarity;
            long timeout;
            try {
                similarity = Double.parseDouble(similarityText);
            } catch (Exception e) {
                edtSimilarity.setError("请输入 0~1 之间的数值");
                return;
            }
            try {
                timeout = Long.parseLong(timeoutText);
            } catch (Exception e) {
                edtTimeout.setError("请输入超时时间(毫秒)");
                return;
            }
            if (similarity <= 0 || similarity > 1.0) {
                edtSimilarity.setError("建议范围 0.6 ~ 0.99");
                return;
            }
            if (timeout <= 0) {
                edtTimeout.setError("超时必须大于 0");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 6);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.PROJECT, currentProjectDir != null ? currentProjectDir.getName() : "");
                inputMap.put(MetaOperation.TASK, currentTaskDir != null ? currentTaskDir.getName() : "");
                inputMap.put(MetaOperation.SAVEFILENAME, templateFile.endsWith(".png") ? templateFile : templateFile + ".png");
                inputMap.put(MetaOperation.MATCHSIMILARITY, similarity);
                inputMap.put(MetaOperation.MATCHTIMEOUT, (double) timeout);
                fillAdvancedMatchInputMap(dialogView, inputMap);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建模板匹配操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditMatchTemplateDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_match_template, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 350, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtTemplateFile = dialogView.findViewById(R.id.edt_template_file);
        EditText edtSimilarity = dialogView.findViewById(R.id.edt_similarity);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        AutoCompleteTextView edtFallbackOperation = dialogView.findViewById(R.id.edt_fallback_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        refreshTemplateOptions(edtTemplateFile);
        bindNextOperationSuggestions(dialogView, null);
        bindTemplatePreview(dialogView, edtTemplateFile);
        renderRecentTemplateStrip(dialogView, edtTemplateFile);
        setupAdvancedMatchSection(dialogView, operationObject, null);
        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_template_ts).setOnClickListener(v -> edtTemplateFile.setText(generateTemplateTimestampName()));
        dialogView.findViewById(R.id.btn_template_library).setOnClickListener(v ->
                showTemplateLibraryDialog(edtTemplateFile, dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_pick_fallback).setOnClickListener(v ->
                showOperationPickerDialog("选择失败跳转节点", null, edtFallbackOperation::setText));
        edtTemplateFile.setOnClickListener(v -> {
            refreshTemplateOptions(edtTemplateFile);
            edtTemplateFile.showDropDown();
        });

        edtName.setText(operationObject.optString("name", selected.name));
        edtTemplateFile.setText(inputMapString(operationObject, MetaOperation.SAVEFILENAME));
        Object similarity = null;
        JSONObject inputMapTmp = operationObject.optJSONObject("inputMap");
        if (inputMapTmp != null) {
            similarity = inputMapTmp.opt(MetaOperation.MATCHSIMILARITY);
        }
        if (similarity != null) {
            edtSimilarity.setText(String.valueOf(similarity));
        } else {
            edtSimilarity.setText("0.85");
        }
        Object timeout = inputMapTmp == null ? null : inputMapTmp.opt(MetaOperation.MATCHTIMEOUT);
        if (timeout != null) {
            edtTimeout.setText(String.valueOf(timeout).replace(".0", ""));
        } else {
            edtTimeout.setText("5000");
        }
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));

        dialogView.findViewById(R.id.btn_capture).setOnClickListener(v -> {
            beginTemplateCaptureFromDialog(dialogView, edtTemplateFile);
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String templateFile = edtTemplateFile.getText().toString().trim();
            String similarityText = edtSimilarity.getText().toString().trim();
            String timeoutText = edtTimeout.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(templateFile)) {
                edtTemplateFile.setError("请填写模板文件名");
                return;
            }

            double similarityVal;
            long timeoutVal;
            try {
                similarityVal = Double.parseDouble(similarityText);
            } catch (Exception e) {
                edtSimilarity.setError("请输入 0~1 之间的数值");
                return;
            }
            try {
                timeoutVal = Long.parseLong(timeoutText);
            } catch (Exception e) {
                edtTimeout.setError("请输入超时时间(毫秒)");
                return;
            }

            try {
                operationObject.put("name", name);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.PROJECT, currentProjectDir != null ? currentProjectDir.getName() : "");
                inputMap.put(MetaOperation.TASK, currentTaskDir != null ? currentTaskDir.getName() : "");
                inputMap.put(MetaOperation.SAVEFILENAME, templateFile.endsWith(".png") ? templateFile : templateFile + ".png");
                inputMap.put(MetaOperation.MATCHSIMILARITY, similarityVal);
                inputMap.put(MetaOperation.MATCHTIMEOUT, (double) timeoutVal);
                fillAdvancedMatchInputMap(dialogView, inputMap);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditClickDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_click, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 340, 420);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtClickTarget = dialogView.findViewById(R.id.edt_click_target);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");
        bindNextOperationSuggestions(dialogView, null);
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_pick_point).setOnClickListener(v ->
                showScreenPointPicker((x, y) -> edtClickTarget.setText(x + "," + y), dialogView, projectPanelView));
        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));

        edtName.setText(operationObject.optString("name", selected.name));
        String target = inputMapString(operationObject, MetaOperation.CLICK_TARGET);
        edtClickTarget.setText(target);
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String clickTarget = normalizeClickTarget(edtClickTarget.getText().toString().trim());
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (clickTarget == null) {
                edtClickTarget.setError("坐标格式示例: 500,800");
                return;
            }

            try {
                operationObject.put("name", name);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.CLICK_TARGET, clickTarget);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditDelayDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_delay, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 340, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtDuration = dialogView.findViewById(R.id.edt_duration);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");
        bindNextOperationSuggestions(dialogView, null);
        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", selected.id, edtNextOperation::setText));

        edtName.setText(operationObject.optString("name", selected.name));
        edtDuration.setText(String.valueOf(inputMapLong(operationObject, MetaOperation.SLEEP_DURATION, 0L)));
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String durationStr = edtDuration.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            long duration;
            try {
                duration = Long.parseLong(durationStr);
            } catch (Exception e) {
                edtDuration.setError("请输入有效毫秒数");
                return;
            }
            if (duration < 0) {
                edtDuration.setError("延时不能小于 0");
                return;
            }

            try {
                operationObject.put("name", name);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.SLEEP_DURATION, duration);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditBackKeyDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_back_key, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 340, 350);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        edtName.setText(operationObject.optString("name", selected.name));
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));
        bindNextOperationSuggestions(dialogView, selected.id);
        
        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", selected.id, edtNextOperation::setText));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }

            try {
                operationObject.put("name", name);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditGestureDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_gesture, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 350, 440);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtGestureFile = dialogView.findViewById(R.id.edt_gesture_file);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView tvGestureStatus = dialogView.findViewById(R.id.tv_gesture_status);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        refreshGestureOptions(edtGestureFile);
        bindNextOperationSuggestions(dialogView, null);

        edtName.setText(operationObject.optString("name", selected.name));
        String gestureFile = inputMapString(operationObject, MetaOperation.SAVEFILENAME);
        if (TextUtils.isEmpty(gestureFile)) {
            gestureFile = inputMapString(operationObject, MetaOperation.GESTURE_TEMPLATE_ID);
        }
        edtGestureFile.setText(gestureFile);
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));

        File gestureFileObj = resolveTaskGestureFile(gestureFile);
        if (tvGestureStatus != null) {
            tvGestureStatus.setText(gestureFileObj != null ? ("状态：已存在 " + gestureFileObj.getName()) : "状态：未录制，建议点击下方按钮录制");
        }

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_gesture_ts).setOnClickListener(v -> edtGestureFile.setText(generateGestureTimestampName()));
        dialogView.findViewById(R.id.btn_gesture_library).setOnClickListener(v ->
                showGestureLibraryDialog(edtGestureFile, tvGestureStatus));
        dialogView.findViewById(R.id.btn_play_gesture).setOnClickListener(v ->
                playGestureFromInput(edtGestureFile, tvGestureStatus));
        dialogView.findViewById(R.id.btn_record_gesture).setOnClickListener(v ->
                beginGestureRecordFromDialog(dialogView, edtGestureFile, tvGestureStatus));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", selected.id, edtNextOperation::setText));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));

        edtGestureFile.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateGestureStatus(tvGestureStatus, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String normalizedGestureFile = normalizeGestureFileName(edtGestureFile.getText() == null ? "" : edtGestureFile.getText().toString().trim());
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(normalizedGestureFile)) {
                edtGestureFile.setError("请填写手势文件名");
                return;
            }

            try {
                operationObject.put("name", name);
                operationObject.put("type", 5);
                operationObject.put("responseType", 2);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.PROJECT, currentProjectDir != null ? currentProjectDir.getName() : "");
                inputMap.put(MetaOperation.TASK, currentTaskDir != null ? currentTaskDir.getName() : "");
                inputMap.put(MetaOperation.SAVEFILENAME, normalizedGestureFile);
                inputMap.put(MetaOperation.GESTURE_TEMPLATE_ID, normalizedGestureFile);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditJumpTaskDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_jump_task, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(340, true);
        wm.addView(dialogView, dialogLp);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        AutoCompleteTextView edtTargetTask = dialogView.findViewById(R.id.edt_target_task);
        AutoCompleteTextView edtTargetOperation = dialogView.findViewById(R.id.edt_target_operation);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        bindAutoComplete(edtTargetTask, getCurrentProjectTaskIds());
        bindNextOperationSuggestions(dialogView, null);
        edtTargetTask.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                bindAutoComplete(edtTargetOperation, getTaskOperationIds(s == null ? "" : s.toString().trim()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        edtName.setText(operationObject.optString("name", selected.name));
        edtTargetTask.setText(inputMapString(operationObject, MetaOperation.TARGET_TASK_ID));
        edtTargetOperation.setText(inputMapString(operationObject, MetaOperation.TARGET_OPERATION_ID));
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String targetTaskId = edtTargetTask.getText().toString().trim();
            String targetOperationId = edtTargetOperation.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(targetTaskId)) {
                edtTargetTask.setError("请填写目标 Task ID");
                return;
            }
            if (TextUtils.isEmpty(targetOperationId)) {
                edtTargetOperation.setError("请填写目标 Operation ID");
                return;
            }

            boolean returnAfterComplete = !TextUtils.isEmpty(nextOp);

            try {
                operationObject.put("name", name);
                operationObject.put("responseType", returnAfterComplete ? 2 : 1);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.TARGET_TASK_ID, targetTaskId);
                inputMap.put(MetaOperation.TARGET_OPERATION_ID, targetOperationId);
                inputMap.put(MetaOperation.RETURN_AFTER_COMPLETE, returnAfterComplete);
                if (returnAfterComplete) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                } else {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                }
                operationObject.put("inputMap", inputMap);

                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddOcrDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_ocr, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtBbox = dialogView.findViewById(R.id.edt_bbox);
        EditText edtOutputVar = dialogView.findViewById(R.id.edt_output_var);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        AutoCompleteTextView edtEngine = dialogView.findViewById(R.id.edt_ocr_engine);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView tvBboxStatus = dialogView.findViewById(R.id.tv_bbox_status);
        TextView tvOcrResult = dialogView.findViewById(R.id.tv_ocr_test_result);

        edtTimeout.setText("5000");
        edtOutputVar.setText("ocr_text");
        bindAutoComplete(edtEngine, java.util.Arrays.asList("paddle", "accurate", "fast"));
        edtEngine.setText("paddle", false);
        bindNextOperationSuggestions(dialogView, null);
        updateOcrBboxStatus(tvBboxStatus, null);

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_bbox).setOnClickListener(v ->
                beginRegionPickFromDialog(dialogView, edtBbox, tvBboxStatus));
        dialogView.findViewById(R.id.btn_test_ocr).setOnClickListener(v ->
                testOcrFromDialog(edtBbox, edtTimeout, edtEngine, tvOcrResult));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String bboxText = edtBbox.getText().toString().trim();
            String outputVar = edtOutputVar.getText().toString().trim();
            String timeoutText = edtTimeout.getText().toString().trim();
            String engine = edtEngine.getText() == null ? "paddle" : edtEngine.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            List<Integer> bbox = parseBboxInput(bboxText);
            if (bbox == null) {
                edtBbox.setError("请先框选或输入 x,y,w,h");
                return;
            }
            long timeout;
            try {
                timeout = Long.parseLong(timeoutText);
            } catch (Exception e) {
                edtTimeout.setError("请输入有效超时(毫秒)");
                return;
            }
            if (timeout <= 0) {
                edtTimeout.setError("超时必须大于0");
                return;
            }

            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 9);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.BBOX, new JSONArray(bbox));
                inputMap.put(MetaOperation.OCR_TIMEOUT, timeout);
                inputMap.put(MetaOperation.OCR_OUTPUT_VAR, outputVar);
                inputMap.put(MetaOperation.OCR_ENGINE, TextUtils.isEmpty(engine) ? "paddle" : engine);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    Toast.makeText(this, TextUtils.isEmpty(nextOp) ? "已保存OCR节点（无下一节点）" : ("已保存OCR节点，下一节点: " + nextOp), Toast.LENGTH_SHORT).show();
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建OCR操作失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditOcrDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_ocr, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtBbox = dialogView.findViewById(R.id.edt_bbox);
        EditText edtOutputVar = dialogView.findViewById(R.id.edt_output_var);
        EditText edtTimeout = dialogView.findViewById(R.id.edt_timeout);
        AutoCompleteTextView edtEngine = dialogView.findViewById(R.id.edt_ocr_engine);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView tvBboxStatus = dialogView.findViewById(R.id.tv_bbox_status);
        TextView tvOcrResult = dialogView.findViewById(R.id.tv_ocr_test_result);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        bindNextOperationSuggestions(dialogView, null);
        bindAutoComplete(edtEngine, java.util.Arrays.asList("paddle", "accurate", "fast"));

        edtName.setText(operationObject.optString("name", selected.name));
        JSONObject inputMapObj = operationObject.optJSONObject("inputMap");
        String bboxText = "";
        if (inputMapObj != null) {
            JSONArray arr = inputMapObj.optJSONArray(MetaOperation.BBOX);
            if (arr != null && arr.length() >= 4) {
                bboxText = arr.optInt(0) + "," + arr.optInt(1) + "," + arr.optInt(2) + "," + arr.optInt(3);
            }
        }
        edtBbox.setText(bboxText);
        edtOutputVar.setText(inputMapString(operationObject, MetaOperation.OCR_OUTPUT_VAR));
        edtTimeout.setText(String.valueOf(inputMapLong(operationObject, MetaOperation.OCR_TIMEOUT, 5000L)));
        String savedEngine = inputMapString(operationObject, MetaOperation.OCR_ENGINE);
        edtEngine.setText(TextUtils.isEmpty(savedEngine) ? "paddle" : savedEngine, false);
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));
        updateOcrBboxStatus(tvBboxStatus, parseBboxInput(bboxText));

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_bbox).setOnClickListener(v ->
                beginRegionPickFromDialog(dialogView, edtBbox, tvBboxStatus));
        dialogView.findViewById(R.id.btn_test_ocr).setOnClickListener(v ->
                testOcrFromDialog(edtBbox, edtTimeout, edtEngine, tvOcrResult));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", selected.id, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String bboxStr = edtBbox.getText().toString().trim();
            String outputVar = edtOutputVar.getText().toString().trim();
            String timeoutText = edtTimeout.getText().toString().trim();
            String engine = edtEngine.getText() == null ? "paddle" : edtEngine.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            List<Integer> bbox = parseBboxInput(bboxStr);
            if (bbox == null) {
                edtBbox.setError("请先框选或输入 x,y,w,h");
                return;
            }
            long timeout;
            try {
                timeout = Long.parseLong(timeoutText);
            } catch (Exception e) {
                edtTimeout.setError("请输入有效超时(毫秒)");
                return;
            }
            if (timeout <= 0) {
                edtTimeout.setError("超时必须大于0");
                return;
            }

            try {
                operationObject.put("name", name);
                operationObject.put("type", 9);
                operationObject.put("responseType", 1);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.BBOX, new JSONArray(bbox));
                inputMap.put(MetaOperation.OCR_TIMEOUT, timeout);
                inputMap.put(MetaOperation.OCR_OUTPUT_VAR, outputVar);
                inputMap.put(MetaOperation.OCR_ENGINE, TextUtils.isEmpty(engine) ? "paddle" : engine);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    Toast.makeText(this, TextUtils.isEmpty(nextOp) ? "已保存OCR节点（无下一节点）" : ("已保存OCR节点，下一节点: " + nextOp), Toast.LENGTH_SHORT).show();
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddVariableScriptDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_variable_script, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtScript = dialogView.findViewById(R.id.edt_script);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        bindNextOperationSuggestions(dialogView, null);
        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择默认下一节点", null, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));

        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String script = edtScript.getText().toString();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(script.trim())) {
                edtScript.setError("请填写脚本代码");
                return;
            }
            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 11);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_SCRIPT_CODE, script);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建变量脚本节点失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditVariableScriptDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_variable_script, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtScript = dialogView.findViewById(R.id.edt_script);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        bindNextOperationSuggestions(dialogView, selected.id);
        edtName.setText(operationObject.optString("name", selected.name));
        edtScript.setText(inputMapString(operationObject, MetaOperation.VAR_SCRIPT_CODE));
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择默认下一节点", selected.id, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));

        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String script = edtScript.getText().toString();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(script.trim())) {
                edtScript.setError("请填写脚本代码");
                return;
            }
            try {
                operationObject.put("name", name);
                operationObject.put("type", 11);
                operationObject.put("responseType", 1);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.VAR_SCRIPT_CODE, script);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);
                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<String> getVariableSourceModes() {
        return java.util.Arrays.asList("固定值(推荐)", "复制变量", "读取上一步结果");
    }

    private String sourceModeDisplayToValue(String display) {
        if (TextUtils.isEmpty(display)) {
            return "literal";
        }
        String value = display.trim();
        if ("复制变量".equals(value)) {
            return "variable";
        }
        if ("读取上一步结果".equals(value)) {
            return "response";
        }
        return "literal";
    }

    private String sourceModeValueToDisplay(String value) {
        if ("variable".equalsIgnoreCase(value)) {
            return "复制变量";
        }
        if ("response".equalsIgnoreCase(value)) {
            return "读取上一步结果";
        }
        return "固定值(推荐)";
    }

    private List<String> getVariableValueTypes() {
        return java.util.Arrays.asList("auto", "string", "number", "boolean");
    }

    private List<String> getVariableMathActions() {
        return java.util.Arrays.asList("set", "add", "sub", "mul", "div", "mod", "inc", "dec", "negate", "abs");
    }

    private boolean isUnaryMathAction(String action) {
        if (action == null) {
            return false;
        }
        String value = action.trim().toLowerCase(Locale.ROOT);
        return "inc".equals(value) || "dec".equals(value) || "negate".equals(value) || "abs".equals(value);
    }

    private void updateVariableSourceInputUi(TextView labelView, EditText valueView, String modeDisplay) {
        String safeMode = sourceModeDisplayToValue(modeDisplay);
        if ("variable".equals(safeMode)) {
            labelView.setText("要复制的变量名");
            valueView.setHint("例如: page_title");
        } else if ("response".equals(safeMode)) {
            labelView.setText("上一步结果字段名");
            valueView.setHint("通常填: result");
        } else {
            labelView.setText("输入值");
            valueView.setHint("例如: 3 或 hello");
        }
    }

    private void updateVariableMathOperandUi(TextView labelView, EditText valueView, String mode, String action) {
        boolean unary = isUnaryMathAction(action);
        if (unary) {
            labelView.setText("操作数 (当前运算无需填写)");
            valueView.setHint("该运算会忽略操作数");
            valueView.setEnabled(false);
            return;
        }
        valueView.setEnabled(true);
        if ("variable".equalsIgnoreCase(mode)) {
            labelView.setText("操作数变量名");
            valueView.setHint("例如: retry_step");
        } else {
            labelView.setText("操作数");
            valueView.setHint("例如: 1");
        }
    }

    private void bindVariableSourceModeWatcher(AutoCompleteTextView modeView, TextView labelView, EditText valueView) {
        modeView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateVariableSourceInputUi(labelView, valueView, s == null ? "literal" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void bindVariableMathWatcher(AutoCompleteTextView modeView, AutoCompleteTextView actionView,
                                         TextView labelView, EditText valueView) {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String mode = modeView.getText() == null ? "literal" : modeView.getText().toString();
                String action = actionView.getText() == null ? "add" : actionView.getText().toString();
                updateVariableMathOperandUi(labelView, valueView, mode, action);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        modeView.addTextChangedListener(watcher);
        actionView.addTextChangedListener(watcher);
    }

    private void showAddVariableSetDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_variable_set, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        AutoCompleteTextView edtSourceMode = dialogView.findViewById(R.id.edt_source_mode);
        EditText edtSourceValue = dialogView.findViewById(R.id.edt_source_value);
        TextView tvSourceLabel = dialogView.findViewById(R.id.tv_source_value_label);
        AutoCompleteTextView edtVarType = dialogView.findViewById(R.id.edt_var_type);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        bindAutoComplete(edtSourceMode, getVariableSourceModes());
        bindAutoComplete(edtVarType, getVariableValueTypes());
        bindNextOperationSuggestions(dialogView, null);
        bindVariableSourceModeWatcher(edtSourceMode, tvSourceLabel, edtSourceValue);
        edtSourceMode.setText(sourceModeValueToDisplay("literal"), false);
        edtVarType.setText("auto", false);
        updateVariableSourceInputUi(tvSourceLabel, edtSourceValue, sourceModeValueToDisplay("literal"));

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String sourceModeDisplay = edtSourceMode.getText() == null ? sourceModeValueToDisplay("literal") : edtSourceMode.getText().toString().trim();
            String sourceMode = sourceModeDisplayToValue(sourceModeDisplay);
            String sourceValue = edtSourceValue.getText().toString();
            String varType = edtVarType.getText() == null ? "auto" : edtVarType.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }
            if (("variable".equalsIgnoreCase(sourceMode) || "response".equalsIgnoreCase(sourceMode))
                    && TextUtils.isEmpty(sourceValue.trim())) {
                edtSourceValue.setError("当前来源模式需要填写值");
                return;
            }
            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 11);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_SOURCE_MODE, TextUtils.isEmpty(sourceMode) ? "literal" : sourceMode);
                inputMap.put(MetaOperation.VAR_SOURCE_VALUE, sourceValue);
                inputMap.put(MetaOperation.VAR_TYPE, TextUtils.isEmpty(varType) ? "auto" : varType);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建变量赋值节点失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditVariableSetDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_variable_set, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        AutoCompleteTextView edtSourceMode = dialogView.findViewById(R.id.edt_source_mode);
        EditText edtSourceValue = dialogView.findViewById(R.id.edt_source_value);
        TextView tvSourceLabel = dialogView.findViewById(R.id.tv_source_value_label);
        AutoCompleteTextView edtVarType = dialogView.findViewById(R.id.edt_var_type);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        bindAutoComplete(edtSourceMode, getVariableSourceModes());
        bindAutoComplete(edtVarType, getVariableValueTypes());
        bindNextOperationSuggestions(dialogView, selected.id);
        bindVariableSourceModeWatcher(edtSourceMode, tvSourceLabel, edtSourceValue);

        edtName.setText(operationObject.optString("name", selected.name));
        edtVarName.setText(inputMapString(operationObject, MetaOperation.VAR_NAME));
        String sourceMode = inputMapString(operationObject, MetaOperation.VAR_SOURCE_MODE);
        edtSourceMode.setText(sourceModeValueToDisplay(sourceMode), false);
        edtSourceValue.setText(inputMapString(operationObject, MetaOperation.VAR_SOURCE_VALUE));
        String varType = inputMapString(operationObject, MetaOperation.VAR_TYPE);
        edtVarType.setText(TextUtils.isEmpty(varType) ? "auto" : varType, false);
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));
        updateVariableSourceInputUi(tvSourceLabel, edtSourceValue,
                edtSourceMode.getText() == null ? sourceModeValueToDisplay("literal") : edtSourceMode.getText().toString());

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", selected.id, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String valueModeDisplay = edtSourceMode.getText() == null ? sourceModeValueToDisplay("literal") : edtSourceMode.getText().toString().trim();
            String valueMode = sourceModeDisplayToValue(valueModeDisplay);
            String value = edtSourceValue.getText().toString();
            String valueType = edtVarType.getText() == null ? "auto" : edtVarType.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }
            if (("variable".equalsIgnoreCase(valueMode) || "response".equalsIgnoreCase(valueMode))
                    && TextUtils.isEmpty(value.trim())) {
                edtSourceValue.setError("当前来源模式需要填写值");
                return;
            }
            try {
                operationObject.put("name", name);
                operationObject.put("type", 11);
                operationObject.put("responseType", 1);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_SOURCE_MODE, TextUtils.isEmpty(valueMode) ? "literal" : valueMode);
                inputMap.put(MetaOperation.VAR_SOURCE_VALUE, value);
                inputMap.put(MetaOperation.VAR_TYPE, TextUtils.isEmpty(valueType) ? "auto" : valueType);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);
                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddVariableMathDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_variable_math, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        AutoCompleteTextView edtAction = dialogView.findViewById(R.id.edt_action);
        AutoCompleteTextView edtOperandMode = dialogView.findViewById(R.id.edt_operand_mode);
        EditText edtOperandValue = dialogView.findViewById(R.id.edt_operand_value);
        TextView tvOperandLabel = dialogView.findViewById(R.id.tv_operand_label);
        AutoCompleteTextView edtOperandType = dialogView.findViewById(R.id.edt_operand_type);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        bindAutoComplete(edtAction, getVariableMathActions());
        bindAutoComplete(edtOperandMode, java.util.Arrays.asList("literal", "variable"));
        bindAutoComplete(edtOperandType, java.util.Collections.singletonList("number"));
        bindNextOperationSuggestions(dialogView, null);
        bindVariableMathWatcher(edtOperandMode, edtAction, tvOperandLabel, edtOperandValue);
        edtAction.setText("add", false);
        edtOperandMode.setText("literal", false);
        edtOperandType.setText("number", false);
        updateVariableMathOperandUi(tvOperandLabel, edtOperandValue, "literal", "add");

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String action = edtAction.getText() == null ? "add" : edtAction.getText().toString().trim();
            String operandMode = edtOperandMode.getText() == null ? "literal" : edtOperandMode.getText().toString().trim();
            String operandValue = edtOperandValue.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }
            if (!isUnaryMathAction(action) && TextUtils.isEmpty(operandValue)) {
                edtOperandValue.setError("请填写操作数");
                return;
            }
            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 12);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_ACTION, TextUtils.isEmpty(action) ? "add" : action);
                inputMap.put(MetaOperation.VAR_OPERAND_MODE, TextUtils.isEmpty(operandMode) ? "literal" : operandMode);
                inputMap.put(MetaOperation.VAR_OPERAND_VALUE, operandValue);
                inputMap.put(MetaOperation.VAR_OPERAND_TYPE, "number");
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);

                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建变量运算节点失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditVariableMathDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_variable_math, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        AutoCompleteTextView edtAction = dialogView.findViewById(R.id.edt_action);
        AutoCompleteTextView edtOperandMode = dialogView.findViewById(R.id.edt_operand_mode);
        EditText edtOperandValue = dialogView.findViewById(R.id.edt_operand_value);
        TextView tvOperandLabel = dialogView.findViewById(R.id.tv_operand_label);
        AutoCompleteTextView edtOperandType = dialogView.findViewById(R.id.edt_operand_type);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        bindAutoComplete(edtAction, getVariableMathActions());
        bindAutoComplete(edtOperandMode, java.util.Arrays.asList("literal", "variable"));
        bindAutoComplete(edtOperandType, java.util.Collections.singletonList("number"));
        bindNextOperationSuggestions(dialogView, selected.id);
        bindVariableMathWatcher(edtOperandMode, edtAction, tvOperandLabel, edtOperandValue);

        edtName.setText(operationObject.optString("name", selected.name));
        edtVarName.setText(inputMapString(operationObject, MetaOperation.VAR_NAME));
        String action = inputMapString(operationObject, MetaOperation.VAR_ACTION);
        edtAction.setText(TextUtils.isEmpty(action) ? "add" : action, false);
        String operandMode = inputMapString(operationObject, MetaOperation.VAR_OPERAND_MODE);
        edtOperandMode.setText(TextUtils.isEmpty(operandMode) ? "literal" : operandMode, false);
        edtOperandValue.setText(inputMapString(operationObject, MetaOperation.VAR_OPERAND_VALUE));
        edtOperandType.setText("number", false);
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));
        updateVariableMathOperandUi(tvOperandLabel, edtOperandValue,
                edtOperandMode.getText() == null ? "literal" : edtOperandMode.getText().toString(),
                edtAction.getText() == null ? "add" : edtAction.getText().toString());

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", selected.id, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String valueAction = edtAction.getText() == null ? "add" : edtAction.getText().toString().trim();
            String valueMode = edtOperandMode.getText() == null ? "literal" : edtOperandMode.getText().toString().trim();
            String value = edtOperandValue.getText().toString().trim();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }
            if (!isUnaryMathAction(valueAction) && TextUtils.isEmpty(value)) {
                edtOperandValue.setError("请填写操作数");
                return;
            }
            try {
                operationObject.put("name", name);
                operationObject.put("type", 12);
                operationObject.put("responseType", 1);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_ACTION, TextUtils.isEmpty(valueAction) ? "add" : valueAction);
                inputMap.put(MetaOperation.VAR_OPERAND_MODE, TextUtils.isEmpty(valueMode) ? "literal" : valueMode);
                inputMap.put(MetaOperation.VAR_OPERAND_VALUE, value);
                inputMap.put(MetaOperation.VAR_OPERAND_TYPE, "number");
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);
                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddVariableTemplateDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_variable_template, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        EditText edtTemplate = dialogView.findViewById(R.id.edt_template);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);

        bindNextOperationSuggestions(dialogView, null);
        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", null, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String template = edtTemplate.getText().toString();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }
            try {
                JSONObject operationObject = new JSONObject();
                operationObject.put("id", generateOperationId());
                operationObject.put("name", name);
                operationObject.put("type", 13);
                operationObject.put("responseType", 1);

                JSONObject inputMap = new JSONObject();
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_TEMPLATE, template);
                if (!TextUtils.isEmpty(nextOp)) {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);
                if (appendOperation(operationObject)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "构建变量模板节点失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditVariableTemplateDialog(OperationItem selected, OperationPanelAdapter adapter, JSONObject operationObject) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_variable_template, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);
        setupDialogMoveAndScale(dialogView, dialogLp, 360, 430);

        EditText edtName = dialogView.findViewById(R.id.edt_name);
        EditText edtVarName = dialogView.findViewById(R.id.edt_var_name);
        EditText edtTemplate = dialogView.findViewById(R.id.edt_template);
        AutoCompleteTextView edtNextOperation = dialogView.findViewById(R.id.edt_next_operation);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_confirm);
        btnConfirm.setText("保存");

        bindNextOperationSuggestions(dialogView, selected.id);
        edtName.setText(operationObject.optString("name", selected.name));
        edtVarName.setText(inputMapString(operationObject, MetaOperation.VAR_NAME));
        edtTemplate.setText(inputMapString(operationObject, MetaOperation.VAR_TEMPLATE));
        edtNextOperation.setText(inputMapString(operationObject, MetaOperation.NEXT_OPERATION_ID));

        dialogView.findViewById(R.id.btn_close_top).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_pick_next).setOnClickListener(v ->
                showOperationPickerDialog("选择下一节点", selected.id, edtNextOperation::setText));
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        btnConfirm.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String varName = edtVarName.getText().toString().trim();
            String template = edtTemplate.getText().toString();
            String nextOp = edtNextOperation.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                edtName.setError("请填写操作名称");
                return;
            }
            if (TextUtils.isEmpty(varName)) {
                edtVarName.setError("请填写变量名");
                return;
            }
            try {
                operationObject.put("name", name);
                operationObject.put("type", 13);
                operationObject.put("responseType", 1);
                JSONObject inputMap = getInputMapOrCreate(operationObject);
                inputMap.put(MetaOperation.VAR_NAME, varName);
                inputMap.put(MetaOperation.VAR_TEMPLATE, template);
                if (TextUtils.isEmpty(nextOp)) {
                    inputMap.remove(MetaOperation.NEXT_OPERATION_ID);
                } else {
                    inputMap.put(MetaOperation.NEXT_OPERATION_ID, nextOp);
                }
                operationObject.put("inputMap", inputMap);
                if (saveOperationJson(selected.id, operationObject.toString(2), adapter)) {
                    safeRemoveView(dialogView);
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onTaskSwitch(String taskId, String taskName, List<OperationItem> operations) {
        stopDelayProgress();
        // Task 切换时更新悬浮窗显示的 operations 列表
        Log.d(TAG, "切换到 Task: " + taskName + " (" + taskId + "), operations: " + (operations != null ? operations.size() : 0));
        
        // 直接使用传入的 operations 列表
        runningOperations.clear();
        if (operations != null) {
            runningOperations.addAll(operations);
        }
        totalOperationCount = runningOperations.size();
        
        // 重置索引
        currentOperationIndex = 0;
        currentRunningTask = taskName;
        CrashLogger.updateRunContext(currentRunningProject, taskName, currentRunningOperationId, currentRunningOperationName);
        
        // 在主线程刷新面板
        new Handler(Looper.getMainLooper()).post(() -> {
            if (runningPanelAdapter != null) {
                runningPanelAdapter.notifyDataSetChanged();
                Log.d(TAG, "已刷新 runningPanelAdapter，数据条数: " + runningOperations.size());
            } else {
                Log.w(TAG, "runningPanelAdapter 为 null，无法刷新");
            }
            
            // 更新进度显示
            updateRunningPanelProgress();
        });
        
        // 更新通知栏
        updateNotification("已切换到: " + taskName);
    }

    /**
     * 从当前项目中加载指定 Task 的 operations
     * @param taskId Task ID（即文件夹名）
     */
    private void loadOperationsFromProject(String taskId) {
        if (currentProjectDir == null) {
            Log.w(TAG, "currentProjectDir 为空，无法加载 Task 的 operations");
            return;
        }
        
        File taskDir = new File(currentProjectDir, taskId);
        if (!taskDir.exists()) {
            Log.w(TAG, "Task 目录不存在: " + taskDir.getAbsolutePath());
            return;
        }
        
        File operationsFile = new File(taskDir, "operations.json");
        if (!operationsFile.exists()) {
            Log.w(TAG, "operations.json 不存在: " + operationsFile.getAbsolutePath());
            return;
        }
        
        try {
            String json = new String(java.nio.file.Files.readAllBytes(operationsFile.toPath()));
            List<MetaOperation> operations = OperationGsonUtils.fromJson(json);
            
            runningOperations.clear();
            int index = 0;
            for (MetaOperation op : operations) {
                String typeName = getOperationTypeName(op.getType());
                runningOperations.add(new OperationItem(
                    op.getName(),
                    op.getId(),
                    typeName,
                    index++,
                    extractDelayDurationMs(op),
                    extractDelayShowCountdown(op)
                ));
            }
            
            totalOperationCount = runningOperations.size();
            Log.d(TAG, "已加载 " + runningOperations.size() + " 个 operations 从 " + operationsFile.getAbsolutePath());
            
        } catch (Exception e) {
            Log.e(TAG, "加载 operations 失败: " + operationsFile.getAbsolutePath(), e);
        }
    }

    /**
     * 直接设置 operations 列表（用于 Task 切换时）
     */
    public void setRunningOperations(List<OperationItem> operations, String taskName) {
        stopDelayProgress();
        runningOperations.clear();
        if (operations != null) {
            runningOperations.addAll(operations);
        }
        totalOperationCount = runningOperations.size();
        currentOperationIndex = 0;
        currentRunningTask = taskName;
        CrashLogger.updateRunContext(currentRunningProject, taskName, currentRunningOperationId, currentRunningOperationName);
        
        if (runningPanelAdapter != null) {
            runningPanelAdapter.notifyDataSetChanged();
        }
        updateRunningPanelProgress();
    }

    private String getOperationTypeNameLegacy(Integer type) {
        /*
        if (type != null && type == 14) return "启动应用";
        if (type == null) return "未知";
        switch (type) {
            case 1: return "点击";
            case 2: return "延时";
            case 3: return "截图";
            case 4: return "加载图片";
            case 5: return "手势";
            case 6: return "模板匹配";
            case 7: return "多模板匹配";
            case 8: return "跳转Task";
            case 9: return "OCR识别(已弃用)";
            case 10: return "条件分支";
            case 11: return "变量脚本";
            case 12: return "变量运算";
            case 13: return "变量模板";
            default: return "未知";
        }
        */
        return "未知";
    }

    private String getOperationTypeName(Integer type) {
        OperationType operationType = OperationType.fromCode(type);
        return operationType == null ? "未知" : operationType.getDisplayName();
    }

    /**
     * 更新通知栏状态
     */
    private void updateNotification(String text) {
        String channelId = "float_window_channel";
        Notification notification =
                new NotificationCompat.Builder(this, channelId)
                        .setContentTitle("悬浮窗运行中")
                        .setContentText(text)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setOngoing(true)
                        .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(1, notification);
        }
    }

    // ==================== 运行状态面板 ====================

    /**
     * 显示运行状态面板
     */
    private void showRunningPanel() {
        if (runningPanelView != null) return;

        runningPanelView = LayoutInflater.from(this).inflate(R.layout.window_running_panel, null);

        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        runningPanelLp = new WindowManager.LayoutParams(
                dp(300), dp(400),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        runningPanelLp.gravity = Gravity.TOP | Gravity.START;
        runningPanelLp.x = getSharedPanelX();
        runningPanelLp.y = getSharedPanelY();
        adaptPanelSizeToScreen(runningPanelLp, 300, 400);

        setupRunningPanel();
        wm.addView(runningPanelView, runningPanelLp);
        isRunningPanelShowing = true;
    }

    /**
     * 关闭运行状态面板
     */
    private void hideRunningPanel() {
        if (runningPanelView != null) {
            rememberSharedPanelPosition(runningPanelLp);
            wm.removeView(runningPanelView);
            runningPanelView = null;
            runningPanelLp = null;
            isRunningPanelShowing = false;
        }
    }

    private int getSharedPanelX() {
        if (sharedPanelX != Integer.MIN_VALUE) {
            return sharedPanelX;
        }
        return dp(50);
    }

    private int getSharedPanelY() {
        if (sharedPanelY != Integer.MIN_VALUE) {
            return sharedPanelY;
        }
        return dp(100);
    }

    private void rememberSharedPanelPosition(WindowManager.LayoutParams lp) {
        if (lp == null) {
            return;
        }
        sharedPanelX = lp.x;
        sharedPanelY = lp.y;
    }

    /**
     * 切换运行状态面板显示/隐藏
     */
    private void toggleRunningPanel() {
        if (isRunningPanelShowing) {
            hideRunningPanel();
        } else {
            showRunningPanel();
        }
    }

    /**
     * 设置运行状态面板
     */
    private void setupRunningPanel() {
        // 关闭按钮
        runningPanelView.findViewById(R.id.btn_close_running).setOnClickListener(v -> hideRunningPanel());

        // 拖动头
        View dragHeader = runningPanelView.findViewById(R.id.panel_header);
        dragHeader.setOnTouchListener(new DragTouchListener(runningPanelLp, wm, runningPanelView, this, true));
        View resizeHandle = runningPanelView.findViewById(R.id.resize_handle);
        if (resizeHandle != null) {
            resizeHandle.setOnTouchListener(new PanelResizeTouchListener(
                    runningPanelLp,
                    wm,
                    runningPanelView,
                    this,
                    RUNNING_PANEL_MIN_W_DP,
                    RUNNING_PANEL_MIN_H_DP
            ));
        }

        // 暂停/继续按钮
        TextView btnPause = runningPanelView.findViewById(R.id.btn_pause);
        btnPause.setOnClickListener(v -> togglePauseState());

        // 停止按钮
        runningPanelView.findViewById(R.id.btn_stop).setOnClickListener(v -> stopScriptFromUi());

        // 设置初始状态
        updateRunningPanelStatus("运行中", 0xFF4CAF50);
        syncPauseButtonIfPanelVisible();

        // 初始化列表
        RecyclerView rv = runningPanelView.findViewById(R.id.rv_running_operations);
        rv.setLayoutManager(new LinearLayoutManager(this));
        runningPanelAdapter = new RunningPanelAdapter(runningOperations);
        rv.setAdapter(runningPanelAdapter);

        // 更新进度
        updateRunningPanelProgress();
        renderDelayProgressState();
        updateRuntimeMetricsPanel();
    }

    /**
     * 更新运行状态面板的状态显示
     */
    private void updateRunningPanelStatus(String status, int color) {
        runtimeStatusText = status;
        runtimeStatusColor = color;
        if (runningPanelView == null) {
            syncProjectPanelRuntimeUi();
            return;
        }

        TextView tvStatus = runningPanelView.findViewById(R.id.tv_status);
        View indicator = runningPanelView.findViewById(R.id.status_indicator);

        tvStatus.setText(status);
        tvStatus.setTextColor(color);

        // 更新指示器颜色
        indicator.setBackgroundColor(color);
        syncProjectPanelRuntimeUi();
    }

    /**
     * 更新运行状态面板的进度
     */
    private void updateRunningPanelProgress() {
        if (runningPanelView == null) {
            syncProjectPanelRuntimeUi();
            return;
        }

        TextView tvProgress = runningPanelView.findViewById(R.id.tv_progress);
        TextView tvCurrentOp = runningPanelView.findViewById(R.id.tv_current_op);
        renderDelayProgressState();

        tvProgress.setText("进度: " + currentOperationIndex + "/" + totalOperationCount);

        if (!currentRunningOperationName.isEmpty()) {
            tvCurrentOp.setText("当前: " + currentRunningOperationName);
        } else {
            tvCurrentOp.setText("当前: -");
        }
        syncProjectPanelRuntimeUi();
    }

    // ==================== RunningPanelAdapter ====================

    static class RunningPanelAdapter extends RecyclerView.Adapter<RunningPanelAdapter.ViewHolder> {
        private List<OperationItem> operations;
        private int runningPosition = -1;

        RunningPanelAdapter(List<OperationItem> operations) {
            this.operations = operations;
        }

        public void setOperations(List<OperationItem> operations) {
            this.operations = operations;
            notifyDataSetChanged();
        }

        public void setRunningPosition(int position) {
            int prev = runningPosition;
            runningPosition = position;
            if (prev >= 0) notifyItemChanged(prev);
            if (position >= 0) notifyItemChanged(position);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_operation_simple, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OperationItem item = operations.get(position);
            holder.tvName.setText(item.name);
            holder.tvType.setText(item.type);

            // 高亮当前运行的 operation
            if (position == runningPosition) {
                holder.itemView.setBackgroundColor(0x66EF9A9A);
                holder.indicator.setVisibility(View.VISIBLE);
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                holder.indicator.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return operations != null ? operations.size() : 0;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName;
            TextView tvType;
            View indicator;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_op_name);
                tvType = itemView.findViewById(R.id.tv_op_type);
                indicator = itemView.findViewById(R.id.running_indicator);
            }
        }
    }

    private static class OperationClipboardLibraryAdapter extends RecyclerView.Adapter<OperationClipboardLibraryAdapter.ViewHolder> {
        interface OnRemoveListener {
            void onRemove(@Nullable OperationClipboardEntry entry);
        }

        private final List<OperationClipboardEntry> items;
        private final OnRemoveListener removeListener;
        private Runnable selectionChangedListener;
        private int selectedPosition;

        OperationClipboardLibraryAdapter(List<OperationClipboardEntry> items, OnRemoveListener removeListener) {
            this.items = items;
            this.removeListener = removeListener;
            this.selectedPosition = items.isEmpty() ? -1 : 0;
        }

        void setOnSelectionChanged(@Nullable Runnable selectionChangedListener) {
            this.selectionChangedListener = selectionChangedListener;
        }

        @Nullable
        OperationClipboardEntry getSelectedEntry() {
            if (selectedPosition < 0 || selectedPosition >= items.size()) {
                return null;
            }
            return items.get(selectedPosition);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_node_clipboard, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OperationClipboardEntry entry = items.get(position);
            holder.tvName.setText(entry.name);
            String sourceTask = TextUtils.isEmpty(entry.sourceTaskPath) ? "-" : new File(entry.sourceTaskPath).getName();
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(entry.createdAt));
            holder.tvMeta.setText("来源 Task: " + sourceTask + "  |  复制时间: " + time);

            boolean selected = position == selectedPosition;
            holder.itemView.setBackgroundColor(selected ? 0xFFE8F0FE : Color.TRANSPARENT);
            holder.selectedIndicator.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);

            holder.itemView.setOnClickListener(v -> {
                int old = selectedPosition;
                selectedPosition = holder.getBindingAdapterPosition();
                if (old >= 0) {
                    notifyItemChanged(old);
                }
                if (selectedPosition >= 0) {
                    notifyItemChanged(selectedPosition);
                }
                if (selectionChangedListener != null) {
                    selectionChangedListener.run();
                }
            });

            holder.btnRemove.setOnClickListener(v -> {
                int bindingPosition = holder.getBindingAdapterPosition();
                if (bindingPosition < 0 || bindingPosition >= items.size()) {
                    return;
                }
                OperationClipboardEntry removed = items.get(bindingPosition);
                if (removeListener != null) {
                    removeListener.onRemove(removed);
                }
                if (items.isEmpty()) {
                    selectedPosition = -1;
                } else if (selectedPosition >= items.size()) {
                    selectedPosition = items.size() - 1;
                } else if (bindingPosition == selectedPosition) {
                    selectedPosition = Math.min(bindingPosition, items.size() - 1);
                }
                notifyDataSetChanged();
                if (selectionChangedListener != null) {
                    selectionChangedListener.run();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final View selectedIndicator;
            final TextView tvName;
            final TextView tvMeta;
            final TextView btnRemove;

            ViewHolder(View itemView) {
                super(itemView);
                selectedIndicator = itemView.findViewById(R.id.view_selected);
                tvName = itemView.findViewById(R.id.tv_clipboard_name);
                tvMeta = itemView.findViewById(R.id.tv_clipboard_meta);
                btnRemove = itemView.findViewById(R.id.btn_remove);
            }
        }
    }

    // ==================== 回调接口 ====================
    
    /**
     * Operation JSON 读取回调
     */
    private interface OperationJsonCallback {
        void onResult(String json);
    }
    
    /**
     * Operation JSON 保存回调
     */
    private interface SaveOperationCallback {
        void onResult(boolean success);
    }
}
