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
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
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
import android.provider.MediaStore;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import com.auto.master.configui.ConfigUiComponent;
import com.auto.master.configui.ConfigUiCanvasEditorView;
import com.auto.master.configui.ConfigUiDesignerAdapter;
import com.auto.master.configui.ConfigUiFormRenderer;
import com.auto.master.configui.ConfigUiOption;
import com.auto.master.configui.ConfigUiPage;
import com.auto.master.configui.ConfigUiSchema;
import com.auto.master.configui.ConfigUiStore;
import com.auto.master.configui.ConfigUiValueCodec;
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
import com.auto.master.auto.ScriptSession;
import com.auto.master.auto.ScriptSessionSnapshot;
import com.auto.master.capture.CaptureScaleHelper;
import com.auto.master.capture.ScreenCapture;
import com.auto.master.capture.ScreenCaptureManager;
import com.auto.master.capture.ScreenCapturePermissionActivity;
import com.auto.master.importer.ProjectImportPickerActivity;
import com.auto.master.importer.ScriptPackageManager;
import com.auto.master.ui.EntityActionSheetAdapter;
import com.auto.master.utils.BitmapManager;
import com.auto.master.utils.CrashLogger;
import com.auto.master.utils.AdaptivePollingController;
import com.auto.master.utils.AppStorage;
import com.auto.master.utils.OpenCVHelper;
import com.auto.master.utils.OperationGsonUtils;
import com.auto.master.utils.SystemRuntimeConfig;
import com.auto.master.utils.UUIDGenerator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.opencv.core.Mat;

import org.json.JSONArray;
import org.json.JSONObject;

public class FloatWindowService extends Service implements ScriptRunner.ScriptExecutionListener, FloatWindowHost {

    private static final String TAG = "FloatWindowService";
    // 脚本运行期间持有的 CPU WakeLock，防止系统在脚本执行中途休眠进程
    private android.os.PowerManager.WakeLock scriptWakeLock;

    private static final int PROJECT_PANEL_VIEW_TYPE_PROJECT = 1001;
    private static final int PROJECT_PANEL_VIEW_TYPE_TASK = 1002;
    private static final int PROJECT_PANEL_VIEW_TYPE_OPERATION = 1003;
    private static final String ACTION_STOP_SELF = "com.auto.master.floatwin.action.STOP_SELF";
    private static final String ACTION_REFRESH_PROJECTS = "com.auto.master.floatwin.action.REFRESH_PROJECTS";
    public static final String ACTION_OPEN_TASK_PANEL = "com.auto.master.floatwin.action.OPEN_TASK_PANEL";
    public static final String ACTION_OPEN_TASK_LIBRARY = "com.auto.master.floatwin.action.OPEN_TASK_LIBRARY";
    public static final String EXTRA_PROJECT_PATH = "project_path";
    public static final String EXTRA_TASK_PATH = "task_path";
    public static final String EXTRA_TASK_LIBRARY_KIND = "task_library_kind";
    public static final String TASK_LIBRARY_TEMPLATE = "template";
    public static final String TASK_LIBRARY_GESTURE = "gesture";
    private static final long TASK_REMOVED_RESTART_DELAY_MS = 600L;
    private static final String CONFIG_UI_DRAG_LABEL = "config_ui_component";
    private WindowManager wm;

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
    private static final long CAPTURE_OVERLAY_LAUNCH_DELAY_MS = 80L;
    private static final long CAPTURE_PERMISSION_SETTLE_DELAY_MS = 900L;
    private static final long CAPTURE_READY_TIMEOUT_MS = 3200L;
    private static final int TEMPLATE_CAPTURE_PREVIEW_MAX_RETRIES = 240;
    private final Object pendingCleanCaptureLock = new Object();
    @Nullable
    private Bitmap pendingCleanCaptureBitmap;

    // ========== 运行状态数据 ==========
    private List<OperationItem> runningOperations = new ArrayList<>();
    private int currentOperationIndex = 0;
    private int totalOperationCount = 0;
    private boolean isPaused = false;
    
    // 当前运行的 project 和 task 信息
    private String currentRunningProject = "";
    private String currentRunningTask = "";
    private String selectedScriptSessionId = "";
    private final List<String> currentRunLogs = new ArrayList<>();
    private RuntimeLogPanelView runtimeLogPanelView;
    private WindowManager.LayoutParams runtimeLogPanelLp;
    private LinearLayout scriptSessionPanelView;
    private LinearLayout scriptSessionListContentView;
    private EditText scriptSessionCreateNameInput;
    private WindowManager.LayoutParams scriptSessionPanelLp;
    private LinearLayout runSessionPickerView;
    private WindowManager.LayoutParams runSessionPickerLp;
    private final Map<String, Long> opStartTimeMs = new HashMap<>();
    private final List<Long> opDurationsMs = new ArrayList<>();
    private final Map<String, Integer> opFailureReasons = new HashMap<>();
    private int opSuccessCount = 0;
    private int opFailureCount = 0;
    private String latestFailureReason = "-";
    private long currentRunStartMs = 0L;
    private boolean focusRunMode = false;
    // delay overlay state moved to StepAndDelayOverlayManager

    private String currentSearchQuery = "";
    private final List<OperationClipboardEntry> operationClipboardLibrary = new ArrayList<>();
    private static final int OPERATION_CLIPBOARD_LIMIT = 24;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService panelDataExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger panelDataLoadToken = new AtomicInteger();
    /** Service 存活标志：onDestroy 后置 false，后台线程回调据此短路，避免访问已销毁状态 */
    private volatile boolean serviceAlive = true;
    private final Runnable searchRefreshRunnable = this::refreshCurrentLevelList;
    private RecyclerView projectPanelRecyclerView;
    private ProjectPanelAdapter projectPanelAdapter;
    private TaskPanelAdapter taskPanelAdapter;
    private OperationPanelAdapter operationPanelAdapter;
    private PopupWindow entityActionPopupWindow;
    private TextView entityActionTitleView;
    private TextView entityActionNameView;
    private TextView entityActionHintView;
    private TextView entityActionPrimaryButton;
    private RecyclerView entityActionRecyclerView;
    private EntityActionSheetAdapter entityActionSheetAdapter;
    private PopupWindow taskActionPopupWindow;
    private TextView taskActionPopupTitleView;
    private RecyclerView taskActionPopupListView;
    private final List<OperationPanelAdapter.ActionItem> taskActionSheetItems = new ArrayList<>();
    private OperationPanelAdapter.ActionSheetAdapter taskActionSheetAdapter;
    @Nullable
    private java.util.function.Consumer<OperationPanelAdapter.ActionItem> taskActionSheetHandler;
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
    private ConfigUiStore configUiStore;
    private final FloatWindowConfigUiHelper configUiHelper =
            new FloatWindowConfigUiHelper(new FloatWindowConfigUiHelper.Host() {
                @Override
                public Context getContext() {
                    return FloatWindowService.this;
                }

                @Override
                public WindowManager getWindowManager() {
                    return wm;
                }

                @Override
                public WindowManager.LayoutParams buildDialogLayoutParams(int widthDp, boolean focusable) {
                    return FloatWindowService.this.buildDialogLayoutParams(widthDp, focusable);
                }

                @Override
                public void safeRemoveView(View view) {
                    FloatWindowService.this.safeRemoveView(view);
                }

                @Override
                public void showToast(String message) {
                    Toast.makeText(FloatWindowService.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public int dp(int value) {
                    return FloatWindowService.this.dp(value);
                }

                @Override
                public String abbreviate(String value, int maxChars) {
                    return FloatWindowService.this.abbreviate(value, maxChars);
                }

                @Override
                public NodeFloatButtonManager getNodeFloatButtonManager() {
                    return nodeFloatBtnManager;
                }

                @Override
                public ConfigUiStore getConfigUiStore() {
                    return configUiStore;
                }

                @Override
                public File getCurrentProjectDir() {
                    return currentProjectDir;
                }

                @Override
                public File getCurrentTaskDir() {
                    return currentTaskDir;
                }

                @Override
                public WindowManager.LayoutParams getBallLayoutParams() {
                    return FloatWindowService.this.getBallLayoutParams();
                }

                @Override
                public int getDefaultNodeButtonColor() {
                    return NODE_BTN_COLORS[0];
                }
            });
    private final NodeActionCoordinator nodeActionCoordinator =
            new NodeActionCoordinator(new NodeActionCoordinator.Host() {
                @Override
                public Context getContext() {
                    return FloatWindowService.this;
                }

                @Override
                public Handler getUiHandler() {
                    return uiHandler;
                }

                @Override
                public File getProjectsRootDir() {
                    return FloatWindowService.this.getProjectsRootDir();
                }

                @Override
                public void prepareProjectPanel() {
                    FloatWindowService.this.prepareProjectPanel();
                }

                @Override
                public void prepareNodePanelState(File projectDir, File taskDir) {
                    currentProjectDir = projectDir;
                    currentTaskDir = taskDir;
                    currentLevel = NavigationLevel.OPERATION;
                    projectPanelContentDirty = true;
                }

                @Override
                public void clearProjectPanelSearch() {
                    FloatWindowService.this.clearProjectPanelSearch();
                }

                @Override
                public void invalidateOperationListCache(@Nullable File taskDir) {
                    FloatWindowService.this.invalidateOperationListCache(taskDir);
                }

                @Override
                public void showProjectPanel() {
                    FloatWindowService.this.showProjectPanel();
                }

                @Override
                public void loadOperations(File taskDir, boolean forceReload) {
                    FloatWindowService.this.loadOperations(taskDir, forceReload);
                }

                @Override
                public void updateUIForLevel() {
                    FloatWindowService.this.updateUIForLevel();
                }

                @Override
                public void focusOperationInPanel(String operationId) {
                    if (currentOperationAdapter == null) {
                        return;
                    }
                    currentOperationAdapter.selectById(operationId);
                    int pos = currentOperationAdapter.findPositionById(operationId);
                    if (pos >= 0) {
                        RecyclerView rv = getProjectPanelRecyclerView();
                        if (rv != null) {
                            rv.scrollToPosition(pos);
                        }
                    }
                }

                @Override
                public @Nullable Project findCachedProjectByName(String projectName) {
                    return FloatWindowService.this.findCachedProjectByName(projectName);
                }

                @Override
                public @Nullable Project loadProjectFromDir(File projectDir) {
                    return FloatWindowService.this.loadProjectFromDir(projectDir);
                }

                @Override
                public void upsertCachedProject(Project project) {
                    FloatWindowService.this.upsertCachedProject(project);
                }

                @Override
                public @Nullable NodeFloatButtonConfig getNodeFloatButtonConfig(String operationId) {
                    return nodeFloatBtnManager == null ? null : nodeFloatBtnManager.getConfig(operationId);
                }

                @Override
                public void applyNodeRuntimeVariables(OperationContext ctx, @Nullable NodeFloatButtonConfig cfg) {
                    configUiHelper.applyNodeRuntimeVariables(ctx, cfg);
                }

                @Override
                public void hideNodeFloatButtonUntilScriptStops(String operationId) {
                    nodeFloatButtonUiHelper.hideButtonUntilScriptStops(operationId);
                }

                @Override
                public @Nullable String resolveBoundSessionForStartOperation(MetaOperation startOperation) {
                    return FloatWindowService.this.resolveBoundSessionForStartOperation(startOperation, true);
                }

                @Override
                public void startOperationWithMode(MetaOperation startOperation,
                                                   OperationContext ctx,
                                                   String projectName,
                                                   String selectedTaskName,
                                                   List<OperationItem> selectedTaskOperations,
                                                   boolean openProjectPanelNow,
                                                   @Nullable String scriptSessionId) {
                    FloatWindowService.this.startOperationWithMode(
                            startOperation, ctx, projectName, selectedTaskName, selectedTaskOperations, openProjectPanelNow, scriptSessionId);
                }

                @Override
                public List<OperationItem> buildOperationItemsFromTask(Task task) {
                    return FloatWindowService.this.buildOperationItemsFromTask(task);
                }

                @Override
                public void showToast(String message) {
                    Toast.makeText(FloatWindowService.this, message, Toast.LENGTH_SHORT).show();
                }
            });
    private final RuntimeTransitionCoordinator runtimeTransitionCoordinator =
            new RuntimeTransitionCoordinator(new RuntimeTransitionCoordinator.Host() {
                @Override
                public Context getContext() {
                    return FloatWindowService.this;
                }

                @Override
                public @Nullable String getSelectedScriptSessionId() {
                    return selectedScriptSessionId;
                }

                @Override
                public boolean isPaused() {
                    ScriptSessionSnapshot snapshot = TextUtils.isEmpty(selectedScriptSessionId)
                            ? null
                            : ScriptRunner.getSessionSnapshot(selectedScriptSessionId);
                    return snapshot != null && snapshot.state == ScriptSession.State.PAUSED;
                }

                @Override
                public void setPaused(boolean paused) {
                    isPaused = paused;
                }

                @Override
                public void updateRunningPanelStatus(String status, int color) {
                    FloatWindowService.this.updateRunningPanelStatus(status, color);
                }

                @Override
                public void applyBallPresentation() {
                    FloatWindowService.this.applyBallPresentation();
                }

                @Override
                public void syncProjectPanelRuntimeUi() {
                    FloatWindowService.this.syncProjectPanelRuntimeUi();
                }

                @Override
                public void showToast(String message) {
                    Toast.makeText(FloatWindowService.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void recordFailureReason(String reason) {
                    FloatWindowService.this.recordFailureReason(reason);
                }

                @Override
                public void appendRunLog(String log) {
                    FloatWindowService.this.appendRunLog(log);
                }

                @Override
                public void persistCurrentRunLog() {
                    FloatWindowService.this.persistCurrentRunLog();
                }

                @Override
                public void finishRunSession(String reason) {
                    releaseScriptWakeLock();
                    CrashLogger.finishRunSession(FloatWindowService.this, reason);
                }

                @Override
                public void hideProjectPanelDock() {
                    FloatWindowService.this.hideProjectPanelDock();
                }

                @Override
                public void setBallVisible(boolean visible) {
                    FloatWindowService.this.setBallVisible(visible);
                }

                @Override
                public void stopDelayProgress() {
                    FloatWindowService.this.stopDelayProgress();
                }

                @Override
                public void hideStepOverlay() {
                    FloatWindowService.this.hideStepOverlay();
                }

                @Override
                public void clearCurrentRunningOperationState() {
                    currentRunningOperationId = "";
                    currentRunningOperationName = "";
                }

                @Override
                public void clearCurrentOperationRunningPosition() {
                    if (currentOperationAdapter != null) {
                        currentOperationAdapter.clearRunningPosition();
                    }
                }

                @Override
                public void restoreBallAfterRun() {
                    FloatWindowService.this.restoreBallAfterRun();
                }

                @Override
                public void updateNotification(String text) {
                    FloatWindowService.this.updateNotification(text);
                }

                @Override
                public void clearFlowGraphHighlight() {
                    flowGraphPanelHelper.clearHighlight();
                }

                @Override
                public void clearExecutionListener() {
                    ScriptRunner.clearExecutionListener();
                }

                @Override
                public void refreshAppLaunchPollingState() {
                    FloatWindowService.this.refreshAppLaunchPollingState();
                }

                @Override
                public long getCurrentRunStartMs() {
                    return currentRunStartMs;
                }

                @Override
                public void showRuntimeAwareProjectPanel() {
                    FloatWindowService.this.showRuntimeAwareProjectPanel();
                }

                @Override
                public View getProjectPanelView() {
                    return projectPanelView;
                }

                @Override
                public void removeProjectPanel() {
                    FloatWindowService.this.removeProjectPanel();
                }
            });
    private final NodeFloatButtonUiHelper nodeFloatButtonUiHelper =
            new NodeFloatButtonUiHelper(new NodeFloatButtonUiHelper.Host() {
                @Override
                public FloatWindowService getService() {
                    return FloatWindowService.this;
                }

                @Override
                public Context getContext() {
                    return FloatWindowService.this;
                }

                @Override
                public WindowManager getWindowManager() {
                    return wm;
                }

                @Override
                public Handler getUiHandler() {
                    return uiHandler;
                }

                @Override
                public int dp(int value) {
                    return FloatWindowService.this.dp(value);
                }

                @Override
                public String abbreviate(String value, int maxChars) {
                    return FloatWindowService.this.abbreviate(value, maxChars);
                }

                @Override
                public void safeRemoveView(View view) {
                    FloatWindowService.this.safeRemoveView(view);
                }

                @Override
                public int[] getScreenSizePx() {
                    return FloatWindowService.this.getScreenSizePx();
                }

                @Override
                public WindowManager.LayoutParams getBallLayoutParams() {
                    return FloatWindowService.this.getBallLayoutParams();
                }

                @Override
                public File getCurrentProjectDir() {
                    return currentProjectDir;
                }

                @Override
                public File getCurrentTaskDir() {
                    return currentTaskDir;
                }

                @Override
                public NodeFloatButtonManager getNodeFloatButtonManager() {
                    return nodeFloatBtnManager;
                }

                @Override
                public boolean shouldRetainNodeConfigMetadata(@Nullable NodeFloatButtonConfig cfg) {
                    return configUiHelper.shouldRetainNodeConfigMetadata(cfg);
                }

                @Override
                public void showRuntimeConfig(NodeFloatButtonConfig cfg) {
                    configUiHelper.showNodeRuntimeConfigDialog(cfg);
                }

                @Override
                public void showConfigUiDesigner(NodeFloatButtonConfig cfg) {
                    configUiHelper.showConfigUiDesignerDialog(cfg);
                }

                @Override
                public void runNodeFloatButton(NodeFloatButtonConfig cfg) {
                    nodeActionCoordinator.runFromNodeFloatButton(cfg);
                }

                @Override
                public void navigateToNodeInPanel(NodeFloatButtonConfig cfg) {
                    nodeActionCoordinator.navigateToNodeInPanel(cfg);
                }

                @Override
                public boolean isScriptRunning() {
                    return ScriptRunner.isCurrentScriptRunning();
                }

                @Override
                public void onFloatButtonsChanged(Map<String, Integer> colorMap) {
                    if (currentOperationAdapter != null) {
                        currentOperationAdapter.setFloatBtnColors(colorMap);
                    }
                }
            });
    private final ProjectPanelUiHelper projectPanelUiHelper =
            new ProjectPanelUiHelper(new ProjectPanelUiHelper.Host() {
                @Override
                public FloatWindowService getService() {
                    return FloatWindowService.this;
                }

                @Override
                public Context getContext() {
                    return FloatWindowService.this;
                }

                @Override
                public WindowManager getWindowManager() {
                    return wm;
                }

                @Override
                public Handler getUiHandler() {
                    return uiHandler;
                }

                @Override
                public int dp(int value) {
                    return FloatWindowService.this.dp(value);
                }

                @Override
                public int[] getScreenSizePx() {
                    return FloatWindowService.this.getScreenSizePx();
                }

                @Override
                public void initDialogFactory() {
                    FloatWindowService.this.initDialogFactory();
                }

                @Override
                public void clampPanelToScreen(@Nullable WindowManager.LayoutParams lp) {
                    FloatWindowService.this.clampPanelToScreen(lp);
                }

                @Override
                public void adaptProjectPanelSizeToCurrentScreen(@Nullable WindowManager.LayoutParams lp) {
                    FloatWindowService.this.adaptProjectPanelSizeToCurrentScreen(lp);
                }

                @Override
                public int getSharedPanelX() {
                    return FloatWindowService.this.getSharedPanelX();
                }

                @Override
                public int getSharedPanelY() {
                    return FloatWindowService.this.getSharedPanelY();
                }

                @Override
                public void rememberSharedPanelPosition(@Nullable WindowManager.LayoutParams lp) {
                    FloatWindowService.this.rememberSharedPanelPosition(lp);
                }

                @Override
                public boolean shouldRefreshProjectPanelContent() {
                    RecyclerView rv = getProjectPanelRecyclerView();
                    return projectPanelContentDirty || rv == null || rv.getAdapter() == null;
                }

                @Override
                public void refreshCurrentLevelList() {
                    FloatWindowService.this.refreshCurrentLevelList();
                }

                @Override
                public int getProjectPanelLevel() {
                    switch (currentLevel) {
                        case PROJECT:
                            return ProjectPanelUiHelper.LEVEL_PROJECT;
                        case TASK:
                            return ProjectPanelUiHelper.LEVEL_TASK;
                        case OPERATION:
                        default:
                            return ProjectPanelUiHelper.LEVEL_OPERATION;
                    }
                }

                @Override
                public File getCurrentProjectDir() {
                    return currentProjectDir;
                }

                @Override
                public File getCurrentTaskDir() {
                    return currentTaskDir;
                }

                @Override
                public void loadOperations(File taskDir) {
                    FloatWindowService.this.loadOperations(taskDir);
                }

                @Override
                public String getCurrentRunningOperationId() {
                    return currentRunningOperationId;
                }

                @Override
                public boolean isScriptActiveForUi() {
                    return FloatWindowService.this.isScriptActiveForUi();
                }

                @Override
                public boolean isPaused() {
                    return isPaused;
                }

                @Override
                public boolean isOperationBatchMode() {
                    return operationBatchMode;
                }

                @Override
                public int getBatchSelectedOperationCount() {
                    return batchSelectedOperationIds.size();
                }

                @Override
                public String getRuntimeStatusText() {
                    return runtimeStatusText;
                }

                @Override
                public int getRuntimeStatusColor() {
                    return runtimeStatusColor;
                }

                @Override
                public int getCurrentOperationIndex() {
                    return currentOperationIndex;
                }

                @Override
                public int getTotalOperationCount() {
                    return totalOperationCount;
                }

                @Override
                public String getCurrentRunningOperationName() {
                    return currentRunningOperationName;
                }

                @Override
                public String getCurrentRunningProject() {
                    return currentRunningProject;
                }

                @Override
                public String getCurrentRunningTask() {
                    return currentRunningTask;
                }

                @Override
                public boolean selectOperationInProjectPanel(String operationId) {
                    if (currentOperationAdapter == null || TextUtils.isEmpty(operationId)) {
                        return false;
                    }
                    currentOperationAdapter.selectById(operationId);
                    return true;
                }

                @Override
                public int findOperationPositionInProjectPanel(String operationId) {
                    if (currentOperationAdapter == null || TextUtils.isEmpty(operationId)) {
                        return -1;
                    }
                    return currentOperationAdapter.findPositionById(operationId);
                }

                @Override
                public void showToast(String message) {
                    Toast.makeText(FloatWindowService.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void showScriptSessionDialog() {
                    FloatWindowService.this.showScriptSessionDialog();
                }

                @Override
                public void onProjectPanelBackClick() {
                    FloatWindowService.this.navigateBack();
                }

                @Override
                public void onProjectPanelAddClick() {
                    FloatWindowService.this.showAddDialog();
                }

                @Override
                public boolean onProjectPanelAddLongClick() {
                    if (currentLevel == NavigationLevel.PROJECT) {
                        importProjectInteractive();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onProjectPanelRefreshClick() {
                    if (currentLevel == NavigationLevel.OPERATION && currentTaskDir != null) {
                        showFlowGraphDialog();
                    } else {
                        Toast.makeText(FloatWindowService.this, "请在 Operation 列表中打开流程图", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public boolean onProjectPanelRefreshLongClick() {
                    if (currentLevel == NavigationLevel.PROJECT) {
                        invalidateProjectListCache();
                        loadProjects(true);
                        Toast.makeText(FloatWindowService.this, "已刷新项目列表", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    if (currentLevel == NavigationLevel.TASK && currentProjectDir != null) {
                        invalidateTaskListCache(currentProjectDir);
                        loadTasks(currentProjectDir, true);
                        Toast.makeText(FloatWindowService.this, "已刷新 Task 列表", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    if (currentLevel == NavigationLevel.OPERATION && currentTaskDir != null) {
                        invalidateOperationListCache(currentTaskDir);
                        reloadCurrentProject();
                        FloatWindowService.this.loadOperations(currentTaskDir, true);
                        Toast.makeText(FloatWindowService.this, "已刷新列表", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }

                @Override
                public void onProjectPanelSearchQueryChanged(String query) {
                    currentSearchQuery = query == null ? "" : query;
                }

                @Override
                public void cancelPendingProjectPanelSearchRefresh() {
                    uiHandler.removeCallbacks(searchRefreshRunnable);
                }

                @Override
                public void setupProjectPanelBusinessActions(View panelView) {
                    FloatWindowService.this.setupProjectPanelBusinessActions(panelView);
                }

                @Override
                public View getProjectPanelView() {
                    return projectPanelView;
                }

                @Override
                public void setProjectPanelView(@Nullable View view) {
                    projectPanelView = view;
                }

                @Override
                public WindowManager.LayoutParams getProjectPanelLayoutParams() {
                    return projectPanelLp;
                }

                @Override
                public void setProjectPanelLayoutParams(@Nullable WindowManager.LayoutParams lp) {
                    projectPanelLp = lp;
                }

                @Override
                public View getProjectPanelDockView() {
                    return projectPanelDockView;
                }

                @Override
                public void setProjectPanelDockView(@Nullable View view) {
                    projectPanelDockView = view;
                }

                @Override
                public WindowManager.LayoutParams getProjectPanelDockLayoutParams() {
                    return projectPanelDockLp;
                }

                @Override
                public void setProjectPanelDockLayoutParams(@Nullable WindowManager.LayoutParams lp) {
                    projectPanelDockLp = lp;
                }

                @Override
                public boolean isProjectPanelDockOnLeft() {
                    return projectPanelDockOnLeft;
                }

                @Override
                public void setProjectPanelDockOnLeft(boolean onLeft) {
                    projectPanelDockOnLeft = onLeft;
                }

                @Override
                public RecyclerView getProjectPanelRecyclerView() {
                    return projectPanelRecyclerView;
                }

                @Override
                public void setProjectPanelRecyclerView(@Nullable RecyclerView recyclerView) {
                    projectPanelRecyclerView = recyclerView;
                }
            });
    private final FlowGraphPanelHelper flowGraphPanelHelper =
            new FlowGraphPanelHelper(new FlowGraphPanelHelper.Host() {
                @Override
                public Context getContext() {
                    return FloatWindowService.this;
                }

                @Override
                public WindowManager getWindowManager() {
                    return wm;
                }

                @Override
                public Handler getUiHandler() {
                    return uiHandler;
                }

                @Override
                public int dp(int value) {
                    return FloatWindowService.this.dp(value);
                }

                @Override
                public void adaptPanelSizeToScreen(WindowManager.LayoutParams lp, int desiredWidthDp, int desiredHeightDp) {
                    FloatWindowService.this.adaptPanelSizeToScreen(lp, desiredWidthDp, desiredHeightDp);
                }

                @Override
                public int[] getScreenSizePx() {
                    return FloatWindowService.this.getScreenSizePx();
                }

                @Override
                public int getSharedPanelX() {
                    return FloatWindowService.this.getSharedPanelX();
                }

                @Override
                public int getSharedPanelY() {
                    return FloatWindowService.this.getSharedPanelY();
                }

                @Override
                public void rememberSharedPanelPosition(@Nullable WindowManager.LayoutParams lp) {
                    FloatWindowService.this.rememberSharedPanelPosition(lp);
                }

                @Override
                public void safeRemoveView(View view) {
                    FloatWindowService.this.safeRemoveView(view);
                }

                @Override
                public File getCurrentTaskDir() {
                    return currentTaskDir;
                }

                @Override
                public JSONArray readOperationsArray() throws Exception {
                    return FloatWindowService.this.readOperationsArray();
                }

                @Override
                public boolean writeOperationsArray(JSONArray jsonArray, String successText) {
                    return FloatWindowService.this.writeOperationsArray(jsonArray, successText);
                }

                @Override
                public String getOperationTypeName(int type) {
                    return FloatWindowService.this.getOperationTypeName(type);
                }

                @Override
                public void showToast(String message) {
                    Toast.makeText(FloatWindowService.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void editOperationFromFlow(String name, String id, String type, int order) {
                    showEditOperationDialog(new OperationItem(name, id, type, order), currentOperationAdapter);
                }

                @Override
                public void pickOperationId(String title, @Nullable String excludeId, java.util.function.Consumer<String> listener) {
                    FloatWindowService.this.showOperationPickerDialog(title, excludeId, pickedId -> {
                        if (listener != null) {
                            listener.accept(pickedId);
                        }
                    });
                }
            });

    private static final String METHOD_TM_CCOEFF = "TM_CCOEFF (4)";
    private static final String METHOD_TM_CCOEFF_NORMED = "TM_CCOEFF_NORMED (5)";
    private static final String METHOD_TM_CCORR = "TM_CCORR (2)";
    private static final String METHOD_TM_CCORR_NORMED = "TM_CCORR_NORMED (3)";
    private static final String METHOD_TM_SQDIFF = "TM_SQDIFF (0)";
    private static final String METHOD_TM_SQDIFF_NORMED = "TM_SQDIFF_NORMED (1)";
    private static final String METHOD_RANDOM_SAMPLE = "随机mask采样 (99)";
    private static final String METHOD_RANDOM_ROI = "随机 ROI (100)";

    // ---- Extracted helper objects (Phase 2 refactoring) ----
    private OperationCrudHelper crudHelper;
    private DialogHelpers dialogHelpers;
    private OperationDialogFactory dialogFactory;
    private CapturePickerHelper capturePickerHelper;
    private FileIOManager fileIOManager;
    
    // ── 临时悬浮层追踪 ─────────────────────────────────────────────────────
    private View activeBboxOverlayView;
    private WindowManager activeBboxOverlayWm;
    private final Handler bboxDismissHandler = new Handler(Looper.getMainLooper());

    // ── 拆分出的管理器 ──────────────────────────────────────────────────────
    /** 步骤指示器 & 延迟进度悬浮层（从本类拆分，见 StepAndDelayOverlayManager）。 */
    private StepAndDelayOverlayManager stepDelayOverlayManager;
    /** App 启动触发器轮询（从本类拆分，见 AppLaunchTriggerManager）。 */
    private AppLaunchTriggerManager appLaunchTriggerManager;
    /** 触发器管理对话框（从本类拆分，见 TriggerDialogHelper）。 */
    private TriggerDialogHelper triggerDialogHelper;
    /** 节点剪贴板对话框（从本类拆分，见 ClipboardDialogHelper）。 */
    private ClipboardDialogHelper clipboardDialogHelper;
    /** 执行流程对话框（从本类拆分，见 ExecutionDialogHelper）。 */
    private ExecutionDialogHelper executionDialogHelper;
    /** 通用节点前置延迟对话框。 */
    private NodePreDelayDialogHelper nodePreDelayDialogHelper;
    /** 悬浮球与扇形菜单控制器。 */
    private final FloatBallOverlayController floatBallOverlayController =
            new FloatBallOverlayController(new FloatBallOverlayController.Host() {
                @Override
                public FloatWindowService getService() {
                    return FloatWindowService.this;
                }

                @Override
                public Context getContext() {
                    return FloatWindowService.this;
                }

                @Override
                public WindowManager getWindowManager() {
                    return wm;
                }

                @Override
                public int dp(int value) {
                    return FloatWindowService.this.dp(value);
                }

                @Override
                public int[] getScreenSizePx() {
                    return FloatWindowService.this.getScreenSizePx();
                }

                @Override
                public void hideProjectPanelDock() {
                    FloatWindowService.this.hideProjectPanelDock();
                }

                @Override
                public void showRuntimeAwareProjectPanel() {
                    FloatWindowService.this.showRuntimeAwareProjectPanel();
                }

                @Override
                public void toggleActivePauseState() {
                    FloatWindowService.this.focusRunningSessionForQuickAction();
                    FloatWindowService.this.togglePauseState();
                }

                @Override
                public void stopActiveScriptFromUi() {
                    FloatWindowService.this.focusRunningSessionForQuickAction();
                    FloatWindowService.this.stopScriptFromUi();
                }

                @Override
                public void removeProjectPanel() {
                    FloatWindowService.this.removeProjectPanel();
                }

                @Override
                public void showProjectPanel() {
                    FloatWindowService.this.showProjectPanel();
                }

                @Override
                public void toggleRuntimeLogPanel() {
                    FloatWindowService.this.toggleRuntimeLogPanel();
                }

                @Override
                public void showScriptSessionDialog() {
                    FloatWindowService.this.showScriptSessionDialog();
                }

                @Override
                public void captureToolScreenshot() {
                    FloatWindowService.this.captureToolScreenshot();
                }

                @Override
                public void performBackAction() {
                    FloatWindowService.this.performBackAction();
                }

                @Override
                public void showToast(String message) {
                    Toast.makeText(FloatWindowService.this, message, Toast.LENGTH_SHORT).show();
                }

                @Override
                public boolean isPaused() {
                    return isPaused;
                }
            });



    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化WindowManager
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        SystemRuntimeConfig.load(this).applyToRuntime();

        // 初始化IO管理器
        fileIOManager = new FileIOManager();

        // 初始化节点悬浮按钮管理器并恢复已保存的按钮
        nodeFloatBtnManager = new NodeFloatButtonManager(this);
        configUiStore = new ConfigUiStore(this);

        // 初始化拆分出的管理器
        stepDelayOverlayManager = new StepAndDelayOverlayManager(this);
        appLaunchTriggerManager = new AppLaunchTriggerManager(this);
        nodeFloatButtonUiHelper.restoreNodeFloatButtons();
        
        if (!startMyForeground()) {
            stopSelf();
            return;
        }
        showBall();
        prepareProjectPanel();
        prewarmProjectStructure();
        renderProjectItems(filterProjectItems(projectListCache, currentSearchQuery));
        preloadProjectDataAsync();

        appLaunchTriggerManager.initIfNeeded();
    }

    private boolean startMyForeground() {
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

        try {
            startForeground(1, notification);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "启动悬浮窗前台服务失败", e);
            return false;
        }
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
        if (intent != null && ACTION_OPEN_TASK_LIBRARY.equals(intent.getAction())) {
            handleOpenTaskLibraryIntent(intent);
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
        uiHandler.post(projectPanelUiHelper::onConfigurationChanged);
        uiHandler.post(() -> nodeFloatButtonUiHelper.refreshNodeFloatButtonsForCurrentScreen());
    }

    @Override
    public void onDestroy() {
        serviceAlive = false;
        super.onDestroy();
        
        // 清理Handler的pending消息，防止内存泄漏
        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }
        bboxDismissHandler.removeCallbacksAndMessages(null);
        if (activeBboxOverlayView != null) {
            safeRemoveView(activeBboxOverlayWm, activeBboxOverlayView);
            activeBboxOverlayView = null;
            activeBboxOverlayWm = null;
        }
        // appLaunchPollThread / appLaunchPollHandler 已由 AppLaunchTriggerManager.destroy() 清理
        if (taskActionPopupWindow != null) {
            taskActionPopupWindow.dismiss();
            taskActionPopupWindow = null;
        }
        nodeFloatButtonUiHelper.onDestroy();
        removeRuntimeLogPanel();
        removeBall();
        projectPanelUiHelper.onDestroy();
        flowGraphPanelHelper.onDestroy();
        projectPanelAdapter = null;
        taskPanelAdapter = null;
        operationPanelAdapter = null;
        currentOperationAdapter = null;
        if (stepDelayOverlayManager != null) stepDelayOverlayManager.destroy();
        if (appLaunchTriggerManager != null) appLaunchTriggerManager.destroy();
        
        // 关闭IO管理器
        if (fileIOManager != null) {
            fileIOManager.shutdown();
            fileIOManager = null;
        }
        panelDataExecutor.shutdownNow();
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

    private WindowManager.LayoutParams getBallLayoutParams() {
        return floatBallOverlayController.getLayoutParams();
    }

    private void showBall() {
        floatBallOverlayController.show();
    }

    private void dockBallForRunningFromCurrentPosition() {
        floatBallOverlayController.dockForRunningFromCurrentPosition();
    }

    private void restoreBallAfterRun() {
        floatBallOverlayController.restoreAfterRun();
    }

    private void applyBallPresentation() {
        floatBallOverlayController.refreshPresentation();
    }

    private void removeBall() {
        floatBallOverlayController.remove();
    }

    private void setBallVisible(boolean visible) {
        floatBallOverlayController.setVisible(visible);
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

    private void showNodeFloatBtnConfig(OperationItem item) {
        nodeFloatButtonUiHelper.showNodeFloatBtnConfig(item);
    }

    private void showConfigUiDesignerDialog(OperationItem item) {
        configUiHelper.showConfigUiDesignerDialog(item);
    }

    /** 截断字符串，超出 maxChars 时加 "…"。 */
    private static String abbreviate(String s, int maxChars) {
        if (s == null) return "";
        return s.length() <= maxChars ? s : s.substring(0, maxChars - 1) + "…";
    }

    private void togglePauseState() {
        runtimeTransitionCoordinator.togglePauseState();
    }

    private void stopScriptFromUi() {
        runtimeTransitionCoordinator.stopScriptFromUi();
    }

    private void captureToolScreenshot() {
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在准备截图...", Toast.LENGTH_SHORT).show();
        setBallVisible(false);
        Runnable restorePanels = hideViewsForCapture(
                projectPanelView, runtimeLogPanelView, scriptSessionPanelView);
        Runnable restoreAll = () -> {
            restorePanels.run();
            setBallVisible(true);
        };
        runAfterScreenCaptureReady("工具截图", () -> postToUiDelayed(() -> {
            try {
                Bitmap fullBitmap = captureFreshScreenBitmap();
                if (fullBitmap == null || fullBitmap.isRecycled()) {
                    restoreAll.run();
                    Toast.makeText(this, "截图失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                showToolScreenshotEditor(svc, fullBitmap, restoreAll);
            } catch (Exception e) {
                restoreAll.run();
                Log.w(TAG, "工具截图启动失败", e);
                Toast.makeText(this, "截图启动失败", Toast.LENGTH_SHORT).show();
            }
        }, CAPTURE_OVERLAY_LAUNCH_DELAY_MS), restoreAll);
    }

    private void showToolScreenshotEditor(AutoAccessibilityService svc,
                                          Bitmap fullBitmap,
                                          Runnable restoreViews) {
        WindowManager overlayWm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
        if (overlayWm == null) {
            recycleBitmap(fullBitmap);
            restoreViews.run();
            Toast.makeText(this, "截图编辑器不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        SelectionOverlayView overlay = new SelectionOverlayView(svc);
        overlay.setFrozenBackground(fullBitmap, true);
        overlay.setListener(new SelectionOverlayView.Listener() {
            @Override
            public void onConfirm(Rect rectInOverlay, Bitmap ignored) {
                Bitmap cropped = cropToolScreenshot(
                        fullBitmap, rectInOverlay, overlay.getWidth(), overlay.getHeight());
                safeRemoveView(overlayWm, overlay);
                recycleBitmap(fullBitmap);
                restoreViews.run();
                if (cropped == null || cropped.isRecycled()) {
                    Toast.makeText(FloatWindowService.this, "选区无效，未保存", Toast.LENGTH_SHORT).show();
                    return;
                }
                showToolScreenshotSaveChoice(cropped, rectInOverlay);
            }

            @Override
            public void onCancel() {
                safeRemoveView(overlayWm, overlay);
                recycleBitmap(fullBitmap);
                restoreViews.run();
            }
        });
        try {
            overlayWm.addView(overlay, lp);
            Toast.makeText(this, "请框选截图区域，确认后保存", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            recycleBitmap(fullBitmap);
            restoreViews.run();
            Toast.makeText(this, "截图编辑器打开失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    private Bitmap cropToolScreenshot(Bitmap source, Rect overlayRect, int overlayWidth, int overlayHeight) {
        if (source == null || source.isRecycled() || overlayRect == null
                || overlayWidth <= 0 || overlayHeight <= 0) {
            return null;
        }
        Rect normalized = new Rect(overlayRect);
        normalized.sort();
        if (normalized.width() <= 1 || normalized.height() <= 1) {
            return null;
        }
        float scaleX = source.getWidth() / (float) overlayWidth;
        float scaleY = source.getHeight() / (float) overlayHeight;
        int left = Math.max(0, Math.min(source.getWidth() - 1, Math.round(normalized.left * scaleX)));
        int top = Math.max(0, Math.min(source.getHeight() - 1, Math.round(normalized.top * scaleY)));
        int right = Math.max(left + 1, Math.min(source.getWidth(), Math.round(normalized.right * scaleX)));
        int bottom = Math.max(top + 1, Math.min(source.getHeight(), Math.round(normalized.bottom * scaleY)));
        try {
            return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
        } catch (Exception e) {
            Log.w(TAG, "工具截图裁剪失败", e);
            return null;
        }
    }

    private void showToolScreenshotSaveChoice(Bitmap bitmap, Rect screenRect) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackground(makeToolSaveBackground(0xF6FFFFFF, dp(12), 0xFFE2E8F0));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setElevation(dp(10));
        }

        TextView title = new TextView(this);
        title.setText("保存截图");
        title.setTextColor(0xFF0F172A);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("选择保存到系统相册，或保存为当前 Task 的模板。");
        hint.setTextColor(0xFF64748B);
        hint.setTextSize(12);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.setMargins(0, dp(5), 0, dp(10));
        root.addView(hint, hintParams);

        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText(defaultToolScreenshotName());
        nameInput.setSelectAllOnFocus(true);
        nameInput.setHint("文件名");
        root.addView(nameInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.setMargins(0, dp(14), 0, 0);
        root.addView(actions, actionParams);

        TextView albumButton = buildToolSaveButton("相册", 0xFF2563EB, 0xFFFFFFFF);
        TextView templateButton = buildToolSaveButton("模板库", 0xFF0F766E, 0xFFFFFFFF);
        TextView cancelButton = buildToolSaveButton("取消", 0xFFE2E8F0, 0xFF334155);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, dp(40), 1f);
        first.setMargins(0, 0, dp(8), 0);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(40), 1f);
        second.setMargins(0, 0, dp(8), 0);
        actions.addView(albumButton, first);
        actions.addView(templateButton, second);
        actions.addView(cancelButton, new LinearLayout.LayoutParams(0, dp(40), 1f));

        final boolean[] completed = {false};
        Runnable close = () -> {
            completed[0] = true;
            safeRemoveView(root);
        };
        albumButton.setOnClickListener(v -> {
            if (completed[0]) return;
            close.run();
            saveToolScreenshotToAlbum(bitmap, normalizeToolScreenshotFileName(nameInput.getText()));
        });
        templateButton.setOnClickListener(v -> {
            if (completed[0]) return;
            close.run();
            saveToolScreenshotToTemplateLibrary(
                    bitmap, normalizeToolScreenshotFileName(nameInput.getText()), screenRect);
        });
        cancelButton.setOnClickListener(v -> {
            if (completed[0]) return;
            completed[0] = true;
            safeRemoveView(root);
            recycleBitmap(bitmap);
        });

        WindowManager.LayoutParams lp = buildDialogLayoutParams(320, true);
        dialogHelpers.applyAdaptiveDialogViewport(lp, 340, 0.52f, 0.68f);
        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            recycleBitmap(bitmap);
            Toast.makeText(this, "保存选择面板打开失败", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView buildToolSaveButton(String text, int backgroundColor, int textColor) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setBackground(makeToolSaveBackground(backgroundColor, dp(9), 0));
        button.setClickable(true);
        return button;
    }

    private GradientDrawable makeToolSaveBackground(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private String defaultToolScreenshotName() {
        return "tool_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date()) + ".png";
    }

    private String normalizeToolScreenshotFileName(Editable editable) {
        String name = editable == null ? "" : editable.toString().trim();
        if (TextUtils.isEmpty(name)) {
            return defaultToolScreenshotName();
        }
        int extensionStart = name.lastIndexOf('.');
        if (extensionStart > 0) {
            name = name.substring(0, extensionStart);
        }
        return name + ".png";
    }

    private void saveToolScreenshotToAlbum(Bitmap bitmap, String fileName) {
        panelDataExecutor.execute(() -> {
            Uri uri = null;
            try {
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AutoMaster");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    throw new IOException("无法创建相册文件");
                }
                try (OutputStream output = resolver.openOutputStream(uri)) {
                    if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw new IOException("写入图片失败");
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues completed = new ContentValues();
                    completed.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(uri, completed, null, null);
                }
                postToUi(() -> Toast.makeText(this, "已保存到相册: " + fileName,
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.w(TAG, "工具截图保存到相册失败", e);
                if (uri != null) {
                    try {
                        getContentResolver().delete(uri, null, null);
                    } catch (Exception ignored) {
                    }
                }
                postToUi(() -> Toast.makeText(this, "保存到相册失败: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            } finally {
                recycleBitmap(bitmap);
            }
        });
    }

    private void handleOpenTaskLibraryIntent(@NonNull Intent intent) {
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
            Toast.makeText(this, "Task 路径无效，无法打开资源库", Toast.LENGTH_SHORT).show();
            return;
        }
        final File targetProjectDir = projectDir;
        final File targetTaskDir = taskDir;
        final String libraryKind = intent.getStringExtra(EXTRA_TASK_LIBRARY_KIND);
        uiHandler.post(() -> {
            currentProjectDir = targetProjectDir;
            currentTaskDir = targetTaskDir;
            if (TASK_LIBRARY_GESTURE.equals(libraryKind)) {
                showTaskGestureLibraryManagerDialog(targetTaskDir);
            } else {
                showTaskTemplateLibraryManagerDialog(targetTaskDir);
            }
        });
    }

    private void saveToolScreenshotToTemplateLibrary(Bitmap bitmap, String fileName, Rect screenRect) {
        panelDataExecutor.execute(() -> {
            try {
                if (currentTaskDir == null || !currentTaskDir.isDirectory()) {
                    throw new IOException("请先在项目面板中进入目标 Task");
                }
                File imgDir = new File(currentTaskDir, "img");
                File scaleDir = CaptureScaleHelper.getOrCreateScaleImgDir(
                        imgDir, ScreenCaptureManager.CAPTURE_SCALE);
                if (scaleDir == null || (!scaleDir.exists() && !scaleDir.mkdirs())) {
                    throw new IOException("无法创建模板目录");
                }
                File output = new File(scaleDir, fileName);
                BitmapManager.getInstance().removeBitmap(output.getAbsolutePath());
                Template.clearTaskSingleMatCache(
                        currentProjectDir == null ? "" : currentProjectDir.getName(),
                        currentTaskDir.getName(), fileName);
                try (FileOutputStream stream = new FileOutputStream(output, false)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw new IOException("写入图片失败");
                    }
                }
                postToUi(() -> Toast.makeText(this, "已保存到模板库: " + fileName,
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.w(TAG, "工具截图保存到模板库失败", e);
                postToUi(() -> Toast.makeText(this, "保存到模板库失败: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            } finally {
                recycleBitmap(bitmap);
            }
        });
    }

    private void performBackAction() {
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean success = svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        Toast.makeText(this, success ? "已返回上一级" : "返回失败", Toast.LENGTH_SHORT).show();
    }

    private void focusRunningSessionForQuickAction() {
        ScriptSessionSnapshot current = TextUtils.isEmpty(selectedScriptSessionId)
                ? null
                : ScriptRunner.getSessionSnapshot(selectedScriptSessionId);
        if (isQuickActionRunnableSession(current)) {
            return;
        }
        String activeSessionId = ScriptRunner.getActiveSessionId();
        ScriptSessionSnapshot active = TextUtils.isEmpty(activeSessionId)
                ? null
                : ScriptRunner.getSessionSnapshot(activeSessionId);
        if (isQuickActionRunnableSession(active)) {
            bindScriptSession(active.sessionId);
            return;
        }
        for (ScriptSessionSnapshot snapshot : ScriptRunner.listSessionSnapshots()) {
            if (isQuickActionRunnableSession(snapshot)) {
                bindScriptSession(snapshot.sessionId);
                return;
            }
        }
    }

    private boolean isQuickActionRunnableSession(@Nullable ScriptSessionSnapshot snapshot) {
        if (snapshot == null || snapshot.state == null) {
            return false;
        }
        return snapshot.state == ScriptSession.State.QUEUED
                || snapshot.state == ScriptSession.State.RUNNING
                || snapshot.state == ScriptSession.State.PAUSED;
    }

    private void showProjectPanel() {
        projectPanelUiHelper.showProjectPanel();
    }

    private void showRuntimeAwareProjectPanel() {
        // If a script is running, immediately snap to its project/task instead of
        // waiting for the next onOperationStart to trigger followRuntimeTaskIfPanelIsStale.
        if (isScriptActiveForUi() && !TextUtils.isEmpty(currentRunningProject)) {
            if (currentProjectDir == null || !currentProjectDir.getName().equals(currentRunningProject)) {
                File runningProjectDir = new File(getProjectsRootDir(), currentRunningProject);
                if (runningProjectDir.isDirectory()) {
                    currentProjectDir = runningProjectDir;
                    currentTaskDir = null;
                }
            }
            if (!TextUtils.isEmpty(currentRunningOperationId)) {
                followRuntimeTaskIfPanelIsStale(currentRunningOperationId);
            }
        }
        if (currentTaskDir != null) {
            currentLevel = NavigationLevel.OPERATION;
        }
        projectPanelUiHelper.showRuntimeAwareProjectPanel();
    }

    private void removeProjectPanel() {
        projectPanelUiHelper.removeProjectPanel();
    }

    private boolean isScriptActiveForUi() {
        ScriptSessionSnapshot snapshot = TextUtils.isEmpty(selectedScriptSessionId)
                ? null
                : ScriptRunner.getSessionSnapshot(selectedScriptSessionId);
        if (snapshot == null || snapshot.state == null) {
            return false;
        }
        return snapshot.state == ScriptSession.State.QUEUED
                || snapshot.state == ScriptSession.State.RUNNING
                || snapshot.state == ScriptSession.State.PAUSED;
    }

    private boolean hasIdleScriptSession() {
        for (ScriptSessionSnapshot snapshot : ScriptRunner.listSessionSnapshots()) {
            if (snapshot != null && snapshot.state == ScriptSession.State.IDLE) {
                return true;
            }
        }
        return false;
    }

    private void prepareProjectPanel() {
        projectPanelUiHelper.prepareProjectPanel();
    }

    private boolean isProjectPanelAttached() {
        return projectPanelView != null && projectPanelView.getParent() != null;
    }

    private void hideProjectPanelDock() {
        projectPanelUiHelper.hideProjectPanelDock();
    }

    private void setupProjectPanelBusinessActions(View panelView) {
        ensureProjectPanelAdapters();
        projectPanelContentDirty = true;

//       todo  这里加载projects
        loadProjects();

//        todo 绑定运行按钮
        TextView btnRun = panelView.findViewById(R.id.btn_run);
        TextView btnEdit = panelView.findViewById(R.id.btn_edit);
        TextView btnMoveUp = panelView.findViewById(R.id.btn_move_up);
        TextView btnMoveDown = panelView.findViewById(R.id.btn_move_down);
        TextView btnBatch = panelView.findViewById(R.id.btn_batch);
        View btnSystemParams = panelView.findViewById(R.id.btn_system_params);
        if (btnSystemParams != null) {
            btnSystemParams.setOnClickListener(v -> showSystemRuntimeConfigDialog());
        }
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

            showRunModeMenu(v, mode -> startOperationWithMode(
                    launchData.startOperation,
                    launchData.ctx,
                    launchData.projectName,
                    launchData.selectedTaskName,
                    launchData.selectedTaskOperations,
                    mode
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
            Runnable continueRun = () -> showRunModeMenu(v, mode -> startOperationWithMode(
                    launchData.startOperation,
                    launchData.ctx,
                    launchData.projectName,
                    launchData.selectedTaskName,
                    launchData.selectedTaskOperations,
                    mode
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

            RecyclerView rv = panelView.findViewById(R.id.rv_content);
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
        getOperationJsonAsync(selected.id, operationJson -> {
            if (!serviceAlive || operationJson == null) {
                Toast.makeText(this, "无法读取 operation 数据", Toast.LENGTH_SHORT).show();
                return;
            }
            showEditOperationDialogWithJson(selected, adapter, operationJson);
        });
    }

    private void showNodePreDelayDialog(OperationItem item) {
        if (item == null || TextUtils.isEmpty(item.id)) {
            return;
        }
        if (nodePreDelayDialogHelper == null) {
            nodePreDelayDialogHelper = new NodePreDelayDialogHelper(this, wm);
        }
        nodePreDelayDialogHelper.show(
                item.nodePreDelayMs,
                item.nodePreDelayMinMs,
                item.nodePreDelayMaxMs,
                item.nodePreDelayRandom,
                (delayMs, minMs, maxMs, random) ->
                        updateNodePreDelay(item.id, delayMs, minMs, maxMs, random));
    }

    private void updateNodePreDelay(String operationId, long delayMs, long minMs, long maxMs, boolean random) {
        getOperationJsonAsync(operationId, operationJson -> {
            if (!serviceAlive || operationJson == null) {
                Toast.makeText(this, "无法读取 operation 数据", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject operationObject = new JSONObject(operationJson);
                JSONObject inputMap = operationObject.optJSONObject("inputMap");
                if (inputMap == null) {
                    inputMap = new JSONObject();
                    operationObject.put("inputMap", inputMap);
                }
                long safeDelay = Math.max(0L, Math.min(delayMs, MetaOperation.MAX_NODE_PRE_DELAY_MS));
                long safeMin = Math.max(0L, Math.min(minMs, MetaOperation.MAX_NODE_PRE_DELAY_MS));
                long safeMax = Math.max(0L, Math.min(maxMs, MetaOperation.MAX_NODE_PRE_DELAY_MS));
                if (safeMax < safeMin) {
                    long tmp = safeMin;
                    safeMin = safeMax;
                    safeMax = tmp;
                }
                // Always put all 4 preDelay keys so the preservation code in saveOperationJson
                // never copies stale values back from the old inputMap.
                inputMap.put(MetaOperation.NODE_PRE_DELAY_RANDOM, random);
                if (random) {
                    inputMap.put(MetaOperation.NODE_PRE_DELAY_MIN_MS, safeMin);
                    inputMap.put(MetaOperation.NODE_PRE_DELAY_MAX_MS, safeMax);
                    inputMap.put(MetaOperation.NODE_PRE_DELAY_MS, safeMax);
                } else {
                    inputMap.put(MetaOperation.NODE_PRE_DELAY_MS, safeDelay);
                    inputMap.put(MetaOperation.NODE_PRE_DELAY_MIN_MS, 0L);
                    inputMap.put(MetaOperation.NODE_PRE_DELAY_MAX_MS, safeDelay);
                }
                if (saveOperationJson(operationId, operationObject.toString(2), currentOperationAdapter)) {
                    if (currentTaskDir != null) {
                        loadOperations(currentTaskDir, true);
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "保存节点延迟失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditOperationDialogWithJson(OperationItem selected, OperationPanelAdapter adapter, String operationJson) {
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
            if (type == 3) {
                int responseType = operationObject.optInt("responseType", 1);
                if (responseType == 4) {
                    dialogFactory.showEditCropRegionDialog(selected.id, operationObject);
                    return;
                }
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
            if (type == 23) {
                dialogFactory.showEditCloseAppDialog(selected.id, operationObject);
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
                dialogFactory.showEditBackKeyDialog(selected.id, operationObject);
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
            if (type == 20) {
                dialogFactory.showEditHttpRequestDialog(selected.id, operationObject);
                return;
            }
            if (type == 24) {
                dialogFactory.showEditAccessibilityNodeDialog(selected.id, operationObject);
                return;
            }
            if (type == 21) {
                dialogFactory.showEditDynamicDelayDialog(selected.id, operationObject);
                return;
            }
            if (type == 25) {
                dialogFactory.showEditMtryDialog(selected.id, operationObject);
                return;
            }
            if (type == 26) {
                dialogFactory.showEditOcrTextDialog(selected.id, operationObject);
                return;
            }
            if (type == 27) {
                dialogFactory.showEditAiDetectDialog(selected.id, operationObject);
                return;
            }
            if (type == 28) {
                dialogFactory.showEditPlayAudioDialog(selected.id, operationObject);
                return;
            }
            if (type == 29) {
                dialogFactory.showEditSetSystemParamDialog(selected.id, operationObject);
                return;
            }
            if (type == 30) {
                dialogFactory.showEditLogOutputDialog(selected.id, operationObject);
                return;
            }
            if (type == 31) {
                dialogFactory.showEditSetBrightnessDialog(selected.id, operationObject);
                return;
            }
            if (type == 32) {
                dialogFactory.showEditRepeatExecutionDialog(selected.id, operationObject);
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
        postToUiDelayed(() -> {
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
        File taskDirSnapshot = currentTaskDir;
        if (taskDirSnapshot == null) {
            uiHandler.post(() -> callback.onResult(null));
            return;
        }

        File jsonFile = new File(taskDirSnapshot, "operations.json");
        panelDataExecutor.execute(() -> {
            String result = null;
            try {
                String content = readFileContent(jsonFile);
                if (TextUtils.isEmpty(content)) {
                    postOperationJsonResult(taskDirSnapshot, callback, null);
                    return;
                }
                JSONArray jsonArray = new JSONArray(content);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if (operationId.equals(jsonObject.optString("id"))) {
                        result = jsonObject.toString(2);
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse operation JSON", e);
            }
            postOperationJsonResult(taskDirSnapshot, callback, result);
        });
    }

    private void postOperationJsonResult(@NonNull File taskDirSnapshot,
                                         @NonNull OperationJsonCallback callback,
                                         @Nullable String result) {
        uiHandler.post(() -> {
            if (!serviceAlive || !isSameFile(currentTaskDir, taskDirSnapshot)) {
                return;
            }
            callback.onResult(result);
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
                    // 保留旧 inputMap 中由独立入口管理的节点元数据。
                    JSONObject oldInputMap = jsonObject.optJSONObject("inputMap");
                    JSONObject newInputMap = newJsonObj.optJSONObject("inputMap");
                    if (oldInputMap != null && newInputMap != null) {
                        for (String metadataKey : new String[]{
                                MetaOperation.NODE_PRE_DELAY_MS,
                                MetaOperation.NODE_PRE_DELAY_MIN_MS,
                                MetaOperation.NODE_PRE_DELAY_MAX_MS,
                                MetaOperation.NODE_PRE_DELAY_RANDOM,
                                MetaOperation.NODE_BOUND_SESSION_ID,
                                MetaOperation.NODE_BOUND_SESSION_NAME}) {
                            if (oldInputMap.has(metadataKey) && !newInputMap.has(metadataKey)) {
                                newInputMap.put(metadataKey, oldInputMap.opt(metadataKey));
                            }
                        }
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
            
            // 仅刷新当前 Task 的缓存，避免每次保存都整项目重读
            refreshCurrentTaskCache();
            
            // 刷新列表
            loadOperations(currentTaskDir, true);
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

    private void refreshCurrentTaskCache() {
        if (currentProjectDir == null || currentTaskDir == null) {
            reloadCurrentProject();
            return;
        }
        try {
            Template.clearProjectCache(currentProjectDir.getName());
            Task refreshedTask = parseTaskFromDir(currentTaskDir);
            if (refreshedTask == null) {
                return;
            }
            Project cachedProject = findCachedProjectByName(currentProjectDir.getName());
            if (cachedProject == null) {
                Project refreshedProject = loadProjectFromDir(currentProjectDir);
                if (refreshedProject != null) {
                    upsertCachedProject(refreshedProject);
                }
                return;
            }
            if (cachedProject.getTaskMap() == null) {
                cachedProject.setTaskMap(new HashMap<>());
            }
            cachedProject.getTaskMap().put(currentTaskDir.getName(), refreshedTask);
            syncTaskMemoryCaches(currentProjectDir, currentTaskDir, refreshedTask);
        } catch (Exception e) {
            Log.e(TAG, "刷新当前 Task 缓存失败", e);
        }
    }

    @Nullable
    private Task parseTaskFromDir(@Nullable File taskDir) {
        if (taskDir == null || !taskDir.isDirectory()) {
            return null;
        }
        Task task = new Task();
        task.setId(taskDir.getName());
        task.setName(taskDir.getName());

        File opFile = new File(taskDir, "operations.json");
        if (!opFile.exists()) {
            return task;
        }

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
        return task;
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
                    Task task = parseTaskFromDir(taskDir);
                    if (task != null) {
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
        projectPanelUiHelper.updateUIForLevel();
    }

    private void refreshProjectPanelFooterState() {
        projectPanelUiHelper.refreshProjectPanelFooterState();
    }

    private void syncProjectPanelRuntimeUi() {
        projectPanelUiHelper.syncProjectPanelRuntimeUi();
    }

    private void focusCurrentRunningOperation() {
        projectPanelUiHelper.focusCurrentRunningOperation();
    }

    private void showSystemRuntimeConfigDialog() {
        initDialogFactory();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_system_runtime_config, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(430, true);
        if (dialogHelpers != null) {
            dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 430, 0.82f, 0.92f);
            dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 430, 520, null);
        }
        wm.addView(dialogView, dialogLp);

        EditText captureScaleInput = dialogView.findViewById(R.id.et_system_capture_scale);
        EditText idlePauseInput = dialogView.findViewById(R.id.et_system_idle_pause_ms);
        EditText gestureRecordIdleFinishInput = dialogView.findViewById(R.id.et_system_gesture_record_idle_finish_ms);
        CheckBox cbGestureStepReplay = dialogView.findViewById(R.id.cb_gesture_step_replay);
        EditText etGestureStepIntervalMs = dialogView.findViewById(R.id.et_gesture_step_interval_ms);
        CheckBox cbRuntimeLogEnabled = dialogView.findViewById(R.id.cb_runtime_log_enabled);
        View templatePolling = dialogView.findViewById(R.id.include_template_polling);
        View matchMapPolling = dialogView.findViewById(R.id.include_match_map_polling);
        View colorPolling = dialogView.findViewById(R.id.include_color_polling);

        Runnable bindCurrentValues = () -> {
            SystemRuntimeConfig cfg = SystemRuntimeConfig.load(this);
            bindSystemRuntimeConfigValues(
                dialogView,
                captureScaleInput,
                idlePauseInput,
                gestureRecordIdleFinishInput,
                templatePolling,
                matchMapPolling,
                colorPolling,
                cfg);
            if (cbGestureStepReplay != null) cbGestureStepReplay.setChecked(cfg.gestureStepReplay);
            if (etGestureStepIntervalMs != null) etGestureStepIntervalMs.setText(String.valueOf(cfg.gestureStepIntervalMs));
            if (cbRuntimeLogEnabled != null) cbRuntimeLogEnabled.setChecked(cfg.runtimeLogEnabled);
        };
        bindCurrentValues.run();

        dialogView.findViewById(R.id.btn_system_config_close).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_system_config_cancel).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_system_config_reset).setOnClickListener(v -> {
            SystemRuntimeConfig defaults = new SystemRuntimeConfig();
            bindSystemRuntimeConfigValues(
                    dialogView,
                    captureScaleInput,
                    idlePauseInput,
                    gestureRecordIdleFinishInput,
                    templatePolling,
                    matchMapPolling,
                    colorPolling,
                    defaults);
            if (cbGestureStepReplay != null) cbGestureStepReplay.setChecked(defaults.gestureStepReplay);
            if (etGestureStepIntervalMs != null) etGestureStepIntervalMs.setText(String.valueOf(defaults.gestureStepIntervalMs));
            if (cbRuntimeLogEnabled != null) cbRuntimeLogEnabled.setChecked(defaults.runtimeLogEnabled);
        });
        dialogView.findViewById(R.id.btn_system_config_save).setOnClickListener(v -> {
            try {
                SystemRuntimeConfig cfg = readSystemRuntimeConfigFromInputs(
                        captureScaleInput,
                        idlePauseInput,
                        gestureRecordIdleFinishInput,
                        templatePolling,
                        matchMapPolling,
                        colorPolling);
                if (cbGestureStepReplay != null) cfg.gestureStepReplay = cbGestureStepReplay.isChecked();
                if (etGestureStepIntervalMs != null) {
                    try {
                        String txt = etGestureStepIntervalMs.getText() == null ? "" : etGestureStepIntervalMs.getText().toString().trim();
                        cfg.gestureStepIntervalMs = TextUtils.isEmpty(txt) ? 500L : Long.parseLong(txt);
                    } catch (NumberFormatException ignored) {}
                }
                if (cbRuntimeLogEnabled != null) cfg.runtimeLogEnabled = cbRuntimeLogEnabled.isChecked();
                float previousScale = ScreenCaptureManager.CAPTURE_SCALE;
                cfg.save(this);
                cfg.applyToRuntime();
                if (Math.abs(previousScale - cfg.captureScale) > 0.0001f) {
                    ScreenCaptureManager.getInstance().setCaptureScale(cfg.captureScale);
                }
                safeRemoveView(dialogView);
                Toast.makeText(this, "系统参数已保存", Toast.LENGTH_SHORT).show();
            } catch (IllegalArgumentException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindSystemRuntimeConfigValues(View root,
                                               EditText captureScaleInput,
                                               EditText idlePauseInput,
                                               EditText gestureRecordIdleFinishInput,
                                               View templatePolling,
                                               View matchMapPolling,
                                               View colorPolling,
                                               SystemRuntimeConfig cfg) {
        if (cfg == null) {
            cfg = new SystemRuntimeConfig();
        }
        cfg.normalize();
        if (captureScaleInput != null) {
            captureScaleInput.setText(trimSystemFloat(cfg.captureScale));
        }
        if (idlePauseInput != null) {
            idlePauseInput.setText(String.valueOf(cfg.idlePauseThresholdMs));
        }
        if (gestureRecordIdleFinishInput != null) {
            gestureRecordIdleFinishInput.setText(String.valueOf(cfg.gestureRecordIdleFinishMs));
        }
        bindPollingTriplet(templatePolling, cfg.templateFastMs, cfg.templateMediumMs, cfg.templateSlowMs);
        bindPollingTriplet(matchMapPolling, cfg.matchMapFastMs, cfg.matchMapMediumMs, cfg.matchMapSlowMs);
        bindPollingTriplet(colorPolling, cfg.colorFastMs, cfg.colorMediumMs, cfg.colorSlowMs);
    }

    private SystemRuntimeConfig readSystemRuntimeConfigFromInputs(EditText captureScaleInput,
                                                                  EditText idlePauseInput,
                                                                  EditText gestureRecordIdleFinishInput,
                                                                  View templatePolling,
                                                                  View matchMapPolling,
                                                                  View colorPolling) {
        SystemRuntimeConfig cfg = new SystemRuntimeConfig();
        cfg.captureScale = parseFloatInput(captureScaleInput, "CAPTURE_SCALE", 0.25f, 1.0f);
        cfg.idlePauseThresholdMs = parseLongInput(idlePauseInput, "IDLE_PAUSE_THRESHOLD_MS", 0L, 120000L);
        cfg.gestureRecordIdleFinishMs = parseLongInput(
                gestureRecordIdleFinishInput,
                "手势录制空闲结束",
                500L,
                120000L);
        long[] template = readPollingTriplet(templatePolling, "模板匹配");
        cfg.templateFastMs = template[0];
        cfg.templateMediumMs = template[1];
        cfg.templateSlowMs = template[2];
        long[] matchMap = readPollingTriplet(matchMapPolling, "图集匹配");
        cfg.matchMapFastMs = matchMap[0];
        cfg.matchMapMediumMs = matchMap[1];
        cfg.matchMapSlowMs = matchMap[2];
        long[] color = readPollingTriplet(colorPolling, "颜色操作");
        cfg.colorFastMs = color[0];
        cfg.colorMediumMs = color[1];
        cfg.colorSlowMs = color[2];
        cfg.normalize();
        return cfg;
    }

    private void bindPollingTriplet(View group, long fastMs, long mediumMs, long slowMs) {
        if (group == null) {
            return;
        }
        EditText fastInput = group.findViewById(R.id.et_system_poll_fast_ms);
        EditText mediumInput = group.findViewById(R.id.et_system_poll_medium_ms);
        EditText slowInput = group.findViewById(R.id.et_system_poll_slow_ms);
        if (fastInput != null) fastInput.setText(String.valueOf(fastMs));
        if (mediumInput != null) mediumInput.setText(String.valueOf(mediumMs));
        if (slowInput != null) slowInput.setText(String.valueOf(slowMs));
    }

    private long[] readPollingTriplet(View group, String label) {
        if (group == null) {
            throw new IllegalArgumentException(label + "轮询参数缺失");
        }
        return new long[] {
                parseLongInput(group.findViewById(R.id.et_system_poll_fast_ms), label + "快档", 10L, 5000L),
                parseLongInput(group.findViewById(R.id.et_system_poll_medium_ms), label + "中档", 10L, 5000L),
                parseLongInput(group.findViewById(R.id.et_system_poll_slow_ms), label + "慢档", 10L, 5000L)
        };
    }

    private float parseFloatInput(EditText input, String label, float min, float max) {
        String raw = input == null || input.getText() == null ? "" : input.getText().toString().trim();
        try {
            float value = Float.parseFloat(raw);
            if (value < min || value > max) {
                throw new IllegalArgumentException(label + "范围是 " + trimSystemFloat(min) + " - " + trimSystemFloat(max));
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "必须是数字");
        }
    }

    private long parseLongInput(EditText input, String label, long min, long max) {
        String raw = input == null || input.getText() == null ? "" : input.getText().toString().trim();
        try {
            long value = Long.parseLong(raw);
            if (value < min || value > max) {
                throw new IllegalArgumentException(label + "范围是 " + min + " - " + max + " ms");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "必须是整数毫秒");
        }
    }

    private String trimSystemFloat(float value) {
        if (Math.abs(value - Math.round(value)) < 0.0001f) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String formatDurationSecondsText(long ms) {
        long roundedTenths = Math.round(ms / 100f);
        if (roundedTenths % 10L == 0L) {
            return (roundedTenths / 10L) + "秒";
        }
        return String.format(Locale.US, "%.1f秒", roundedTenths / 10f);
    }

    // ==================== Dialog Factory 初始化 ====================

    private void initDialogFactory() {
        if (dialogFactory != null) return; // 已初始化

        crudHelper = new OperationCrudHelper(this);
        dialogHelpers = new DialogHelpers(this);
        dialogFactory = new OperationDialogFactory(this, dialogHelpers, crudHelper);
        capturePickerHelper = new CapturePickerHelper(new CapturePickerHelper.Host() {
            @Override
            public Context getContext() {
                return FloatWindowService.this;
            }

            @Override
            public WindowManager getWindowManager() {
                return wm;
            }

            @Override
            public Handler getUiHandler() {
                return uiHandler;
            }

            @Override
            public int dp(int value) {
                return FloatWindowService.this.dp(value);
            }

            @Override
            public void showToast(String message) {
                Toast.makeText(FloatWindowService.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public Bitmap captureFreshScreenBitmap() {
                return FloatWindowService.this.captureFreshScreenBitmap();
            }

            @Override
            public View getProjectPanelView() {
                return projectPanelView;
            }

            @Override
            public Runnable hideViewsForCapture(View... viewsToHide) {
                return FloatWindowService.this.hideViewsForCapture(viewsToHide);
            }

            @Override
            public void runAfterScreenCaptureReady(String purpose, Runnable onReady, Runnable onCancelled) {
                FloatWindowService.this.runAfterScreenCaptureReady(purpose, onReady, onCancelled);
            }
        });
        triggerDialogHelper = new TriggerDialogHelper(this, dialogHelpers, appLaunchTriggerManager);
        executionDialogHelper = new ExecutionDialogHelper(this, dialogHelpers);
        nodePreDelayDialogHelper = new NodePreDelayDialogHelper(this, wm);
        clipboardDialogHelper = new ClipboardDialogHelper(this, dialogHelpers, new ClipboardDialogHelper.ClipboardCallbacks() {
            @Override public boolean hasOperationClipboard() { return !operationClipboardLibrary.isEmpty(); }
            @Override public java.util.List<OperationClipboardEntry> getClipboardLibrary() { return operationClipboardLibrary; }
            @Override public void removeClipboardEntry(OperationClipboardEntry entry) { operationClipboardLibrary.remove(entry); }
            @Override public void pasteOperationEntryRelative(OperationClipboardEntry entry, String targetId, boolean before) {
                FloatWindowService.this.pasteOperationEntryRelative(entry, targetId, before);
            }
        });

        dialogFactory.setOperationIdGenerator(this::generateOperationId);

        dialogFactory.setOnOperationAddedListener(() -> {
            refreshCurrentTaskCache();
            loadOperations(currentTaskDir, true);
        });

        dialogFactory.setOnOperationUpdatedListener(() -> {
            refreshCurrentTaskCache();
            loadOperations(currentTaskDir, true);
        });

        dialogFactory.setNextOperationBinder((view, excludeId) ->
            bindNextOperationSuggestions(view, excludeId));

        dialogFactory.setPointPickerLauncher((callback, viewsToHide) ->
            capturePickerHelper.showScreenPointPicker((x, y) -> callback.onPointPicked(x, y), viewsToHide));

        dialogFactory.setColorPointPickerLauncher((callback, viewsToHide) ->
            capturePickerHelper.showColorPointPicker((x, y, color) -> callback.onColorPointPicked(x, y, color), viewsToHide));

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
            @Override public void showGestureLibraryDialog(String currentFile, java.util.function.Consumer<String> onFileSelected) { FloatWindowService.this.showGestureLibraryDialogPipeline(currentFile, onFileSelected); }
            @Override public void beginPipelineStepRecord(View d, boolean singleStroke, java.util.function.Consumer<String> onFileSaved) { FloatWindowService.this.beginPipelineStepRecord(d, singleStroke, onFileSaved); }
            @Override public void beginPipelineContinuousRecord(View d, java.util.function.Consumer<String> onFileSaved) { FloatWindowService.this.beginPipelineContinuousRecord(d, onFileSaved); }
            @Override public void rerecordPipelineStep(View d, boolean singleStroke, String oldFile, java.util.function.Consumer<String> onFileSaved) { FloatWindowService.this.rerecordPipelineStep(d, singleStroke, oldFile, onFileSaved); }
            @Override public void rerecordPipelineContinuousRecord(View d, String oldFile, java.util.function.Consumer<String> onFileSaved) { FloatWindowService.this.rerecordPipelineContinuousRecord(d, oldFile, onFileSaved); }
            @Override public void playGestureFile(View d, String fileName, android.widget.TextView statusView) { FloatWindowService.this.playGestureFileByName(d, fileName, statusView); }
            @Override public void playFullPipeline(View d, org.json.JSONArray pipeline, android.widget.TextView statusView) { FloatWindowService.this.playFullPipelineSequence(d, pipeline, statusView); }
            @Override public com.auto.master.auto.GestureOverlayView.GestureNode readGestureFile(String fileName) { return FloatWindowService.this.readGestureFileByName(fileName); }
            @Override public String normalizeGestureFileName(String r) { return FloatWindowService.this.normalizeGestureFileName(r); }
        });

        dialogFactory.setTaskOperationHelper(new OperationDialogFactory.TaskOperationHelper() {
            @Override public java.util.List<String> getCurrentProjectTaskIds() { return FloatWindowService.this.getCurrentProjectTaskIds(); }
            @Override public java.util.List<String> getTaskOperationIds(String taskId) { return FloatWindowService.this.getTaskOperationIds(taskId); }
            @Override public String getOperationDisplayLabel(String operationId) { return FloatWindowService.this.formatCurrentTaskOperationReference(operationId); }
            @Override public java.util.List<OperationIdPickerAdapter.OperationPickItem> getCurrentProjectTaskPickItems() {
                return FloatWindowService.this.getCurrentProjectTaskPickItems();
            }
            @Override public java.util.List<OperationIdPickerAdapter.OperationPickItem> getTaskOperationPickItems(String taskId) {
                return FloatWindowService.this.getTaskOperationPickItems(taskId);
            }
            @Override public String getTaskDisplayLabel(String taskId) {
                return FloatWindowService.this.formatTaskReference(taskId);
            }
            @Override public String getTaskOperationDisplayLabel(String taskId, String operationId) {
                return FloatWindowService.this.formatTaskOperationReference(taskId, operationId);
            }
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

        dialogFactory.setRegionPickHelper(new OperationDialogFactory.RegionPickHelper() {
            @Override public void beginRegionPickFromDialog(View d, android.widget.EditText e, android.widget.TextView s) { FloatWindowService.this.beginRegionPickFromDialog(d, e, s); }
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
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(380, true);
        dialogHelpers.applyAdaptiveDialogViewport(dialogLp, 380, 0.88f, 0.94f);
        wm.addView(dialogView, dialogLp);
        dialogHelpers.setupDialogMoveAndScale(dialogView, dialogLp, 380, 620, null);

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
                        new AddOperationMenuAdapter.MenuItem("dynamic_delay", "动态延时", "由变量决定等待时长", "变", R.color.op_dynamic_delay, true),
                        new AddOperationMenuAdapter.MenuItem("gesture", "手势", "执行录制好的滑动或连点", "手", R.color.op_gesture, true),
                        new AddOperationMenuAdapter.MenuItem("back_key", "返回键", "触发系统返回动作", "返", R.color.op_back_key, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "识别节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("match_template", "模板匹配", "识别单张模板图片", "图", R.color.op_match_template, true),
                        new AddOperationMenuAdapter.MenuItem("match_map_template", "图集匹配", "在图集中查找可命中的模板", "集", R.color.op_match_map, true),
                        new AddOperationMenuAdapter.MenuItem("ocr_text", "OCR识别", "识别指定区域内的文字", "文", R.color.op_ocr_text, true),
                        new AddOperationMenuAdapter.MenuItem("ai_detect", "AI目标检测", "TFLite/YOLO 识别目标坐标", "AI", R.color.op_ai_detect, true),
                        new AddOperationMenuAdapter.MenuItem("color_match", "颜色匹配", "按坐标组校验颜色", "色", R.color.op_color_match, true),
                        new AddOperationMenuAdapter.MenuItem("color_search", "找色", "在区域中搜索目标颜色", "找", R.color.op_color_search, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "流程节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("jump_task", "跳转任务", "切换到目标任务或节点", "跳", R.color.op_jump_task, true),
                        new AddOperationMenuAdapter.MenuItem("mtry", "多次尝试节点", "包裹节点并重试，接管成功/失败流向", "试", R.color.op_mtry, true),
                        new AddOperationMenuAdapter.MenuItem("repeat_execution", "循环执行", "从指定节点连续执行到 Task 结束后重复", "循", R.color.op_repeat_execution, true),
                        new AddOperationMenuAdapter.MenuItem("switch_branch", "分支判断", "根据条件选择不同路径", "支", R.color.op_condition, true),
                        new AddOperationMenuAdapter.MenuItem("loop", "二分之", "二分之判断", "二", R.color.op_condition, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "变量节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("variable_script", "JS变量脚本", "用js脚本进行变量操作", "JS", R.color.op_var_script, true),
                        new AddOperationMenuAdapter.MenuItem("variable_math", "变量运算", "做数值计算与表达式处理", "算", R.color.op_var_math, true),
                        new AddOperationMenuAdapter.MenuItem("variable_template", "模板变量", "从模板结果中提取变量", "模", R.color.op_var_template, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "系统节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("launch_app", "启动应用", "拉起指定应用并等待前台", "启", R.color.op_app_launch, true),
                        new AddOperationMenuAdapter.MenuItem("close_app", "关闭应用", "退回桌面并请求结束后台进程", "关", R.color.op_app_close, true),
                        new AddOperationMenuAdapter.MenuItem("a11y_node", "无障碍节点", "通过无障碍服务查找并操作界面元素", "障", R.color.op_a11y_node, true),
                        new AddOperationMenuAdapter.MenuItem("log_output", "日志输出", "向悬浮日志面板输出文本", "日", R.color.op_set_sys_param, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "网络节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("http_request", "HTTP 请求", "发起 HTTP 请求，响应体存入变量", "网", R.color.op_http_request, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "即将支持",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("crop_region", "截图区域", "截取屏幕区域并将图像编码为 Base64 存入变量", "截", R.color.op_crop, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "多媒体节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("play_audio", "播放音频", "播放指定路径的音频文件", "音", R.color.op_play_audio, true)
                )));

        sections.add(new AddOperationMenuAdapter.MenuSection(
                "系统配置节点",
                null,
                Arrays.asList(
                        new AddOperationMenuAdapter.MenuItem("set_sys_param", "修改系统参数", "运行时修改采集倍率、倒计时颜色或手势颜色", "参", R.color.op_set_sys_param, true),
                        new AddOperationMenuAdapter.MenuItem("set_brightness", "修改屏幕亮度", "通过无障碍修改系统屏幕亮度", "亮", R.color.op_set_brightness, true)
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
            case "dynamic_delay":
                dialogFactory.showAddDynamicDelayDialog();
                return;
            case "gesture":
                dialogFactory.showAddGestureDialog();
                return;
            case "back_key":
                dialogFactory.showAddBackKeyDialog();
                return;
            case "match_template":
                dialogFactory.showAddMatchTemplateDialog();
                return;
            case "match_map_template":
                dialogFactory.showAddMatchMapTemplateDialog();
                return;
            case "ocr_text":
                dialogFactory.showAddOcrTextDialog();
                return;
            case "ai_detect":
                dialogFactory.showAddAiDetectDialog();
                return;
            case "color_match":
                dialogFactory.showAddColorMatchDialog();
                return;
            case "color_search":
                dialogFactory.showAddColorSearchDialog();
                return;
            case "jump_task":
                dialogFactory.showAddJumpTaskDialog();
                return;
            case "mtry":
                dialogFactory.showAddMtryDialog();
                return;
            case "repeat_execution":
                dialogFactory.showAddRepeatExecutionDialog();
                return;
            case "switch_branch":
                dialogFactory.showAddSwitchBranchDialog();
                return;
            case "loop":
                dialogFactory.showAddLoopDialog();
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
            case "close_app":
                dialogFactory.showAddCloseAppDialog();
                return;
            case "http_request":
                dialogFactory.showAddHttpRequestDialog();
                return;
            case "a11y_node":
            case "accessibility_node":
                dialogFactory.showAddAccessibilityNodeDialog();
                return;
            case "crop_region":
                dialogFactory.showAddCropRegionDialog();
                return;
            case "play_audio":
                dialogFactory.showAddPlayAudioDialog();
                return;
            case "set_sys_param":
                dialogFactory.showAddSetSystemParamDialog();
                return;
            case "log_output":
                dialogFactory.showAddLogOutputDialog();
                return;
            case "set_brightness":
                dialogFactory.showAddSetBrightnessDialog();
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
                if (!serviceAlive) return;
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
                if (!serviceAlive) return;
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
        final int loadToken = panelDataLoadToken.incrementAndGet();
        
        if (!(rv.getLayoutManager() instanceof GridLayoutManager)) {
            rv.setLayoutManager(new GridLayoutManager(this, resolveProjectPanelSpanCount()));
            RecyclerViewOptimizer.optimize(rv, true);
        } else {
            GridLayoutManager manager = (GridLayoutManager) rv.getLayoutManager();
            int spanCount = resolveProjectPanelSpanCount();
            if (manager.getSpanCount() != spanCount) {
                manager.setSpanCount(spanCount);
            }
        }

        File root = getProjectsRootDir();
        long version = root.lastModified();
        if (!forceReload && projectListCacheVersion == version) {
            renderProjectItems(filterProjectItems(projectListCache, currentSearchQuery));
            return;
        }

        final File rootSnapshot = root;
        final String querySnapshot = currentSearchQuery;
        panelDataExecutor.execute(() -> {
            List<ProjectListItem> items = new ArrayList<>();
            File[] dirs = rootSnapshot.listFiles(File::isDirectory);
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

            long resolvedVersion = rootSnapshot.lastModified();
            uiHandler.post(() -> {
                if (!serviceAlive
                        || loadToken != panelDataLoadToken.get()
                        || currentLevel != NavigationLevel.PROJECT
                        || projectPanelView == null) {
                    return;
                }
                projectListCache.clear();
                projectListCache.addAll(items);
                projectListCacheVersion = resolvedVersion;
                renderProjectItems(filterProjectItems(projectListCache, querySnapshot));
            });
        });
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
        final int loadToken = panelDataLoadToken.incrementAndGet();
        
        if (!(rv.getLayoutManager() instanceof GridLayoutManager)) {
            rv.setLayoutManager(new GridLayoutManager(this, resolveProjectPanelSpanCount()));
            RecyclerViewOptimizer.optimize(rv, true);
        } else {
            GridLayoutManager manager = (GridLayoutManager) rv.getLayoutManager();
            int spanCount = resolveProjectPanelSpanCount();
            if (manager.getSpanCount() != spanCount) {
                manager.setSpanCount(spanCount);
            }
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

        final File projectDirSnapshot = projectDir;
        final long versionSnapshot = version;
        final String projectKeySnapshot = projectKey;
        final String querySnapshot = currentSearchQuery;
        panelDataExecutor.execute(() -> {
            List<File> items = readTaskItems(projectDirSnapshot);
            uiHandler.post(() -> {
                if (!serviceAlive
                        || loadToken != panelDataLoadToken.get()
                        || currentLevel != NavigationLevel.TASK
                        || !isSameFile(currentProjectDir, projectDirSnapshot)
                        || projectPanelView == null) {
                    return;
                }
                taskListCacheProjectDir = projectDirSnapshot;
                taskListCacheVersion = versionSnapshot;
                taskListCache.clear();
                taskListCache.addAll(items);
                taskItemsMemoryCache.put(projectKeySnapshot, new ArrayList<>(items));
                taskItemsMemoryVersions.put(projectKeySnapshot, versionSnapshot);
                renderTaskItems(filterTaskItems(taskListCache, querySnapshot));
            });
        });
    }

    private int resolveProjectPanelSpanCount() {
        int widthPx = projectPanelLp != null && projectPanelLp.width > 0
                ? projectPanelLp.width
                : getResources().getDisplayMetrics().widthPixels;
        float widthDp = widthPx / getResources().getDisplayMetrics().density;
        return widthDp >= 300f ? 2 : 1;
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
        final int loadToken = panelDataLoadToken.incrementAndGet();
        
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
        if (shouldAnimate) {
            rv.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.op_layout_enter));
        }
        List<OperationItem> allOperations;
        if (!forceReload
                && taskDir.equals(operationListCacheTaskDir)
                && operationListCacheVersion == version) {
            allOperations = new ArrayList<>(operationListCache);
            renderOperationItems(taskDir, allOperations, taskKey);
            return;
        } else if (!forceReload
                && operationItemsMemoryCache.containsKey(taskKey)
                && operationItemsMemoryVersions.containsKey(taskKey)
                && operationItemsMemoryVersions.get(taskKey) == version) {
            allOperations = new ArrayList<>(operationItemsMemoryCache.get(taskKey));
            operationListCacheTaskDir = taskDir;
            operationListCacheVersion = version;
            operationListCache.clear();
            operationListCache.addAll(allOperations);
            renderOperationItems(taskDir, allOperations, taskKey);
            return;
        } else {
            allOperations = null;
        }

        final File taskDirSnapshot = taskDir;
        final long versionSnapshot = version;
        final String taskKeySnapshot = taskKey;
        final String querySnapshot = currentSearchQuery;
        panelDataExecutor.execute(() -> {
            List<OperationItem> loadedOperations = readOperationItems(taskDirSnapshot);
            if (loadedOperations.isEmpty()) {
                List<OperationItem> fallbackOperations = readOperationItemsDirect(taskDirSnapshot);
                if (!fallbackOperations.isEmpty()) {
                    loadedOperations = fallbackOperations;
                }
            }
            final List<OperationItem> resolvedOperations = loadedOperations;
            uiHandler.post(() -> {
                if (!serviceAlive
                        || loadToken != panelDataLoadToken.get()
                        || currentLevel != NavigationLevel.OPERATION
                        || !isSameFile(currentTaskDir, taskDirSnapshot)
                        || projectPanelView == null) {
                    return;
                }
                operationListCacheTaskDir = taskDirSnapshot;
                operationListCacheVersion = versionSnapshot;
                operationListCache.clear();
                operationListCache.addAll(resolvedOperations);
                operationItemsMemoryCache.put(taskKeySnapshot, new ArrayList<>(resolvedOperations));
                operationItemsMemoryVersions.put(taskKeySnapshot, versionSnapshot);
                renderOperationItems(taskDirSnapshot, resolvedOperations, taskKeySnapshot, querySnapshot);
            });
        });
    }

    private void renderOperationItems(@NonNull File taskDir,
                                      @NonNull List<OperationItem> allOperations,
                                      @NonNull String taskKey) {
        renderOperationItems(taskDir, allOperations, taskKey, currentSearchQuery);
    }

    private void renderOperationItems(@NonNull File taskDir,
                                      @NonNull List<OperationItem> allOperations,
                                      @NonNull String taskKey,
                                      @Nullable String query) {
        RecyclerView rv = getProjectPanelRecyclerView();
        if (rv == null || !isSameFile(currentTaskDir, taskDir)) {
            return;
        }
        List<OperationItem> operations = filterOperationItems(allOperations, query);
        ensureProjectPanelAdapters();
        switchProjectPanelAdapter(operationPanelAdapter);
        attachOperationDragHelperIfNeeded(rv);
        operationPanelAdapter.initFloatBtnColors(nodeFloatButtonUiHelper.getFloatBtnColorMap());
        operationPanelAdapter.setTaskDir(taskDir);
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
                                extractDelayDurationMs(op), extractDelayShowCountdown(op),
                                extractNodePreDelayMs(op), extractNodePreDelayMinMs(op),
                                extractNodePreDelayMaxMs(op), extractNodePreDelayRandom(op),
                                extractTemplatePreviewNames(op)));
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
                                    extractDelayDurationMs(value), extractDelayShowCountdown(value),
                                    extractNodePreDelayMs(value), extractNodePreDelayMinMs(value),
                                    extractNodePreDelayMaxMs(value), extractNodePreDelayRandom(value),
                                    extractTemplatePreviewNames(value)));
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
                                    extractDelayDurationMs(op), extractDelayShowCountdown(op),
                                    extractNodePreDelayMs(op), extractNodePreDelayMinMs(op),
                                    extractNodePreDelayMaxMs(op), extractNodePreDelayRandom(op),
                                    extractTemplatePreviewNames(op)));
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
                    public void onConfigUi(OperationItem item) {
                        showConfigUiDesignerDialog(item);
                    }

                    @Override
                    public void onFloatButton(OperationItem item) {
                        showNodeFloatBtnConfig(item);
                    }

                    @Override
                    public void onNodePreDelay(OperationItem item) {
                        showNodePreDelayDialog(item);
                    }

                    @Override
                    public void onBindSession(OperationItem item) {
                        showNodeSessionBindingDialog(item);
                    }
                },
                selectedIds -> {
                    batchSelectedOperationIds.clear();
                    batchSelectedOperationIds.addAll(selectedIds);
                    updateBatchActionCount();
                });
        adapter.setTaskDir(taskDir);
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
        switchProjectPanelAdapter(projectPanelAdapter);
        projectPanelAdapter.submitProjects(items);
        updateEmptyView(items.isEmpty(), "点击右上角 + 创建项目");
        projectPanelContentDirty = false;
    }

    private void renderTaskItems(List<File> items) {
        RecyclerView rv = getProjectPanelRecyclerView();
        if (rv == null) return;
        ensureProjectPanelAdapters();
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
        List<OperationItem> directOperations = readOperationItemsDirect(taskDir);
        if (!directOperations.isEmpty()) {
            return directOperations;
        }

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
                                extractDelayShowCountdown(op),
                                extractNodePreDelayMs(op),
                                extractNodePreDelayMinMs(op),
                                extractNodePreDelayMaxMs(op),
                                extractNodePreDelayRandom(op),
                                extractTemplatePreviewNames(op)));
                    }
                    if (!operations.isEmpty()) {
                        return operations;
                    }
                }
            }
        }

        return operations;
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
                } else if (typeInt == OperationType.DYNAMIC_DELAY.getCode()) {
                    JSONObject inputMap = op.optJSONObject("inputMap");
                    if (inputMap != null) {
                        // 时长运行时才能确定，此处仅记录倒计时开关；实际 duration 由 Handler 回调注入
                        showCountdown = inputMap.optBoolean(MetaOperation.DELAY_SHOW_COUNTDOWN, true);
                    }
                }
                JSONObject inputMap = op.optJSONObject("inputMap");
                long nodePreDelayMs = inputMap == null
                        ? MetaOperation.DEFAULT_NODE_PRE_DELAY_MS
                        : Math.max(0L, Math.min(inputMap.optLong(MetaOperation.NODE_PRE_DELAY_MS,
                                MetaOperation.DEFAULT_NODE_PRE_DELAY_MS), MetaOperation.MAX_NODE_PRE_DELAY_MS));
                long nodePreDelayMinMs = inputMap == null
                        ? 0L
                        : Math.max(0L, Math.min(inputMap.optLong(MetaOperation.NODE_PRE_DELAY_MIN_MS, 0L),
                                MetaOperation.MAX_NODE_PRE_DELAY_MS));
                long nodePreDelayMaxMs = inputMap == null
                        ? nodePreDelayMs
                        : Math.max(0L, Math.min(inputMap.optLong(MetaOperation.NODE_PRE_DELAY_MAX_MS, nodePreDelayMs),
                                MetaOperation.MAX_NODE_PRE_DELAY_MS));
                boolean nodePreDelayRandom = inputMap != null
                        && inputMap.optBoolean(MetaOperation.NODE_PRE_DELAY_RANDOM, false);
                operations.add(new OperationItem(name, id, typeName, i, delayMs, showCountdown,
                        nodePreDelayMs, nodePreDelayMinMs, nodePreDelayMaxMs, nodePreDelayRandom,
                        extractTemplatePreviewNames(typeInt, inputMap)));
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

    private boolean isSameFile(@Nullable File first, @Nullable File second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return TextUtils.equals(first.getAbsolutePath(), second.getAbsolutePath());
    }

    private void clearProjectPanelSearch() {
        projectPanelUiHelper.clearProjectPanelSearch();
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
        List<EntityActionSheetAdapter.Item> actionItems = new ArrayList<>();
        actionItems.add(new EntityActionSheetAdapter.Item(
                1, R.drawable.ic_action_export, "导出", "分享 zip", true));
        actionItems.add(new EntityActionSheetAdapter.Item(
                2, R.drawable.ic_action_edit, "重命名", "修改名称", true));
        actionItems.add(new EntityActionSheetAdapter.Item(
                3, R.drawable.ic_action_delete, "删除", "移除项目", true));
        showEntityActionPanel("项目操作", item.dir.getName(), item.taskCount + " 个 Task", actionItems, action -> {
            if (action.id == 1) {
                exportProjectAsync(item.dir);
            } else if (action.id == 2) {
                promptRenameProject(item.dir);
            } else if (action.id == 3) {
                confirmDeleteProject(item);
            }
        }, anchor);
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
        List<EntityActionSheetAdapter.Item> actionItems = new ArrayList<>();
        actionItems.add(new EntityActionSheetAdapter.Item(
                1, R.drawable.ic_action_open, "打开", "节点列表", true));
        actionItems.add(new EntityActionSheetAdapter.Item(
                2, R.drawable.ic_action_template, "模板库", "模板截图", true));
        actionItems.add(new EntityActionSheetAdapter.Item(
                3, R.drawable.ic_action_gesture, "手势库", "录制文件", true));
        actionItems.add(new EntityActionSheetAdapter.Item(
                4, R.drawable.ic_action_edit, "重命名", "修改名称", true));
        actionItems.add(new EntityActionSheetAdapter.Item(
                5, R.drawable.ic_action_delete, "删除", "移除 Task", true));
        showEntityActionPanel("任务操作", taskDir.getName(), "选择要执行的 Task 操作", actionItems, action -> {
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
                    showTaskGestureLibraryManagerDialog(taskDir);
                    break;
                case 4:
                    promptRenameTask(taskDir);
                    break;
                case 5:
                    confirmDeleteTask(taskDir);
                    break;
                default:
                    break;
            }
        }, anchor);
    }

    private void showTaskGestureLibraryManagerDialog(File taskDir) {
        if (taskDir == null || !taskDir.isDirectory()) {
            Toast.makeText(this, "当前 Task 无效", Toast.LENGTH_SHORT).show();
            return;
        }
        currentTaskDir = taskDir;
        File parent = taskDir.getParentFile();
        if (parent != null && parent.isDirectory()) {
            currentProjectDir = parent;
        }

        List<GestureLibraryAdapter.GestureLibraryItem> items = getCurrentTaskGestureLibraryItems();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gesture_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);

        TextView titleView = dialogView.findViewById(R.id.tv_library_title);
        TextView clearButton = dialogView.findViewById(R.id.btn_library_clear);
        RecyclerView rv = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch = dialogView.findViewById(R.id.edt_library_search);
        if (titleView != null) {
            titleView.setText("手势库 / " + taskDir.getName());
        }
        if (clearButton != null) {
            clearButton.setText("关闭");
        }
        rv.setLayoutManager(new LinearLayoutManager(this));
        GestureLibraryAdapter adapter = new GestureLibraryAdapter(items, item -> {
            if (item == null || TextUtils.isEmpty(item.fileName)) {
                return;
            }
            playGestureFileByName(dialogView, item.fileName, null);
        });
        rv.setAdapter(adapter);

        dialogView.findViewById(R.id.btn_library_close).setOnClickListener(v -> safeRemoveView(dialogView));
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> safeRemoveView(dialogView));
        }
        if (edtSearch != null) {
            edtSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.updateFilter(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void ensureEntityActionPopup() {
        if (entityActionPopupWindow != null) {
            return;
        }
        View popupView = LayoutInflater.from(this).inflate(R.layout.dialog_entity_action_sheet, null);
        View scrim = popupView.findViewById(R.id.entity_action_scrim);
        View panel = popupView.findViewById(R.id.entity_action_panel);
        entityActionTitleView = popupView.findViewById(R.id.tv_entity_action_title);
        entityActionNameView = popupView.findViewById(R.id.tv_entity_action_name);
        entityActionHintView = popupView.findViewById(R.id.tv_entity_action_hint);
        entityActionRecyclerView = popupView.findViewById(R.id.rv_entity_action_grid);
        entityActionPrimaryButton = popupView.findViewById(R.id.btn_entity_action_primary);
        TextView closeTop = popupView.findViewById(R.id.btn_entity_action_close_top);
        TextView closeBottom = popupView.findViewById(R.id.btn_entity_action_close);
        entityActionSheetAdapter = new EntityActionSheetAdapter();
        entityActionRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        entityActionRecyclerView.setAdapter(entityActionSheetAdapter);
        entityActionPrimaryButton.setVisibility(View.GONE);
        View.OnClickListener closeListener = v -> entityActionPopupWindow.dismiss();
        scrim.setOnClickListener(closeListener);
        closeTop.setOnClickListener(closeListener);
        closeBottom.setOnClickListener(closeListener);
        panel.setOnClickListener(v -> {
        });
        entityActionPopupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true);
        entityActionPopupWindow.setOutsideTouchable(true);
        entityActionPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        entityActionPopupWindow.setElevation(14f);
    }

    private void showEntityActionPanel(
            String title,
            String name,
            String hint,
            List<EntityActionSheetAdapter.Item> items,
            EntityActionSheetAdapter.OnActionClickListener handler,
            View anchor
    ) {
        ensureEntityActionPopup();
        if (entityActionPopupWindow == null
                || entityActionTitleView == null
                || entityActionNameView == null
                || entityActionHintView == null
                || entityActionSheetAdapter == null) {
            return;
        }
        entityActionTitleView.setText(title);
        entityActionNameView.setText(name);
        entityActionHintView.setText(hint);
        if (entityActionPrimaryButton != null) {
            entityActionPrimaryButton.setVisibility(View.GONE);
        }
        entityActionSheetAdapter.submitItems(items, action -> {
            entityActionPopupWindow.dismiss();
            if (handler != null) {
                handler.onActionClick(action);
            }
        });
        if (entityActionPopupWindow.isShowing()) {
            entityActionPopupWindow.dismiss();
        }
        View parent = projectPanelView != null ? projectPanelView : anchor.getRootView();
        entityActionPopupWindow.showAtLocation(parent, Gravity.CENTER, 0, 0);
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

        String scaleDirName = getCurrentScaleDirName();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_template_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        applyTemplateLibraryDialogViewport(dialogLp);
        wm.addView(dialogView, dialogLp);

        TextView tvTitle       = dialogView.findViewById(R.id.tv_library_title);
        TextView btnAdd        = dialogView.findViewById(R.id.btn_library_add);
        RecyclerView rv        = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch     = dialogView.findViewById(R.id.edt_library_search);
        TextView btnBatch      = dialogView.findViewById(R.id.btn_library_batch);
        TextView btnDelete     = dialogView.findViewById(R.id.btn_library_delete);
        View manageActions     = dialogView.findViewById(R.id.ly_library_manage_actions);
        View selectActions     = dialogView.findViewById(R.id.ly_library_select_actions);
        View lyBreadcrumb      = dialogView.findViewById(R.id.ly_breadcrumb);

        if (tvTitle != null) tvTitle.setText("模板库 [" + scaleDirName + "]");
        if (selectActions != null) selectActions.setVisibility(View.GONE);
        if (lyBreadcrumb != null) lyBreadcrumb.setVisibility(View.GONE);
        if (btnAdd != null) btnAdd.setVisibility(View.VISIBLE);
        if (manageActions != null) manageActions.setVisibility(View.VISIBLE);

        rv.setLayoutManager(new GridLayoutManager(this, 3));
        final boolean[] batchMode = {false};
        final TemplateLibraryAdapter[] adapterRef = new TemplateLibraryAdapter[1];

        Runnable refreshList = () -> {
            currentTaskDir = taskDir;
            adapterRef[0].replaceData(getCurrentTaskTemplateLibraryItemsInDir(scaleDirName));
            adapterRef[0].setDeleteActionEnabled(true);
            btnDelete.setText("删除(" + adapterRef[0].getSelectedCount() + ")");
            btnBatch.setText(batchMode[0] ? "完成" : "批量");
        };

        TemplateLibraryAdapter adapter = new TemplateLibraryAdapter(
                getCurrentTaskTemplateLibraryItemsInDir(scaleDirName),
                item -> {
                    if (item == null || item.isFolder) return;
                    showTaskTemplateItemActionDialog(taskDir, item, dialogView, refreshList);
                },
                item -> {
                    if (item == null || item.isFolder || TextUtils.isEmpty(item.fileName)) return;
                    currentTaskDir = taskDir;
                    int deleted = deleteTemplateFileInScaleDir(item.fileName, item.scaleDirName);
                    if (deleted <= 0) {
                        Toast.makeText(this, "模板仍被引用或删除失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, "已删除模板 " + item.fileName, Toast.LENGTH_SHORT).show();
                    refreshTemplateCachesForCurrentProject();
                    refreshList.run();
                });
        adapterRef[0] = adapter;
        adapter.setDeleteActionEnabled(true);
        rv.setAdapter(adapter);
        adapter.setSelectionChangedListener(count -> btnDelete.setText("删除(" + count + ")"));
        btnDelete.setText("删除(0)");
        btnBatch.setText("批量");

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
                            launchTemplateCaptureAfterUiSettledInScaleDir(name, scaleDirName);
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
            List<TemplateLibraryAdapter.TemplateLibraryItem> selectedItems = adapter.getSelectedItems();
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show();
                return;
            }
            int deleted = 0;
            for (TemplateLibraryAdapter.TemplateLibraryItem sel : selectedItems) {
                deleted += deleteTemplateFileInScaleDir(sel.fileName, sel.scaleDirName);
            }
            int skipped = selectedItems.size() - deleted;
            if (skipped > 0) {
                Toast.makeText(this, "已删 " + deleted + " 张，" + skipped + " 张仍被节点引用", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已删除 " + deleted + " 张图片", Toast.LENGTH_SHORT).show();
            }
            refreshTemplateCachesForCurrentProject();
            batchMode[0] = false;
            adapter.setBatchMode(false);
            refreshList.run();
        });
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.updateFilter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void showTaskTemplateItemActionDialog(File taskDir,
                                                  TemplateLibraryAdapter.TemplateLibraryItem item,
                                                  @Nullable View managerDialog,
                                                  @Nullable Runnable onChanged) {
        if (taskDir == null || item == null || TextUtils.isEmpty(item.fileName)) {
            return;
        }
        String[] actions = new String[]{"替换截图", "预览搜索区域", "查看/编辑 Mask", "删除 Mask", "重命名模板", "删除模板"};
        android.view.ContextThemeWrapper ctx =
                new android.view.ContextThemeWrapper(this, R.style.Theme_AtomMaster);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx)
                .setTitle(item.getDisplayName())
                .setItems(actions, (d, which) -> {
                    currentTaskDir = taskDir;
                    if (which == 0) {
                        // 替换截图：保存到与原模板相同的 scale 子目录，直接覆盖
                        safeRemoveView(managerDialog);
                        launchTemplateCaptureAfterUiSettledInScaleDir(item.fileName, item.scaleDirName);
                        return;
                    }
                    if (which == 1) {
                        showTemplateBboxPreview(item.fileName);
                        return;
                    }
                    if (which == 2) {
                        showTemplateMaskEditorFromLibrary(taskDir, item, onChanged);
                        return;
                    }
                    if (which == 3) {
                        File dir = getTemplateItemDir(taskDir, item.scaleDirName);
                        File maskFile = new File(dir, CaptureScaleHelper.getTemplateMaskFileName(item.fileName));
                        if (maskFile.exists() && maskFile.isFile() && maskFile.delete()) {
                            Toast.makeText(this, "Mask 已删除", Toast.LENGTH_SHORT).show();
                            Template.clearTaskSingleMatCache(currentProjectDir == null ? "" : currentProjectDir.getName(),
                                    taskDir.getName(),
                                    CaptureScaleHelper.getTemplateMaskFileName(item.fileName));
                            if (onChanged != null) onChanged.run();
                        } else {
                            Toast.makeText(this, "没有可删除的 Mask", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    if (which == 4) {
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
                                    if (renameTemplateFileInScaleDir(taskDir, item.fileName,
                                            normalizeTemplateFileName(newName), item.scaleDirName)) {
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
                    // 删除：只删除该 scale 子目录中的此文件
                    int deleted = deleteTemplateFileInScaleDir(item.fileName, item.scaleDirName);
                    if (deleted > 0) {
                        Toast.makeText(this, "已删除模板 " + item.getDisplayName(), Toast.LENGTH_SHORT).show();
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

    private File getTemplateItemDir(File taskDir, @Nullable String scaleDirName) {
        File imgDir = new File(taskDir, "img");
        return TextUtils.isEmpty(scaleDirName) ? imgDir : new File(imgDir, scaleDirName);
    }

    @Override
    public void showTemplateBboxPreview(String templateFileName) {
        File taskDir = currentTaskDir;
        if (taskDir == null || !taskDir.isDirectory()) {
            Toast.makeText(this, "当前任务不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(templateFileName)) {
            Toast.makeText(this, "请先填写模板文件名", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = templateFileName.endsWith(".png") ? templateFileName : templateFileName + ".png";
        String scaleDirName = getCurrentScaleDirName();
        File templateDir = getTemplateItemDir(taskDir, scaleDirName);
        File manifestFile = new File(templateDir, "manifest.json");

        int[] bbox = null;
        if (manifestFile.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
                if (!TextUtils.isEmpty(content.trim())) {
                    JSONObject manifest = new JSONObject(content);
                    if (manifest.has(name)) {
                        JSONArray arr = manifest.getJSONArray(name);
                        if (arr.length() >= 4) {
                            bbox = new int[]{arr.getInt(0), arr.getInt(1), arr.getInt(2), arr.getInt(3)};
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "读取 manifest 失败", e);
            }
        }

        if (bbox == null) {
            Toast.makeText(this, "该模板没有设置搜索区域，将全屏搜索", Toast.LENGTH_SHORT).show();
            return;
        }
        showBboxOverlay(bbox[0], bbox[1], bbox[2], bbox[3]);
    }

    @Override
    public void showRawBboxPreview(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            Toast.makeText(this, "无效的区域坐标", Toast.LENGTH_SHORT).show();
            return;
        }
        showBboxOverlay(x, y, w, h);
    }

    private void showBboxOverlay(int bx, int by, int bw, int bh) {
        // 先移除上一个还未消失的 overlay，避免叠加
        bboxDismissHandler.removeCallbacksAndMessages(null);
        if (activeBboxOverlayView != null) {
            safeRemoveView(activeBboxOverlayWm, activeBboxOverlayView);
            activeBboxOverlayView = null;
            activeBboxOverlayWm = null;
        }

        final android.graphics.Paint dimPaint = new android.graphics.Paint();
        dimPaint.setColor(0xAA000000);
        dimPaint.setStyle(android.graphics.Paint.Style.FILL);

        final android.graphics.Paint fillPaint = new android.graphics.Paint();
        fillPaint.setColor(0x55FF2222);
        fillPaint.setStyle(android.graphics.Paint.Style.FILL);

        final android.graphics.Paint borderPaint = new android.graphics.Paint();
        borderPaint.setColor(0xFFFF3333);
        borderPaint.setStyle(android.graphics.Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);

        final android.graphics.Paint labelBgPaint = new android.graphics.Paint();
        labelBgPaint.setColor(0xCC000000);
        labelBgPaint.setStyle(android.graphics.Paint.Style.FILL);

        final android.graphics.Paint labelPaint = new android.graphics.Paint();
        labelPaint.setColor(0xFFFFFFFF);
        labelPaint.setTextSize(dp(12));
        labelPaint.setAntiAlias(true);

        View overlayView = new View(this) {
            @Override
            protected void onDraw(android.graphics.Canvas canvas) {
                int vw = getWidth(), vh = getHeight();
                canvas.drawRect(0, 0, vw, by, dimPaint);
                canvas.drawRect(0, by + bh, vw, vh, dimPaint);
                canvas.drawRect(0, by, bx, by + bh, dimPaint);
                canvas.drawRect(bx + bw, by, vw, by + bh, dimPaint);
                canvas.drawRect(bx, by, bx + bw, by + bh, fillPaint);
                canvas.drawRect(bx, by, bx + bw, by + bh, borderPaint);
                String label = "区域: x=" + bx + " y=" + by + " w=" + bw + " h=" + bh;
                float labelY = by > dp(30) ? by - dp(6) : by + bh + dp(20);
                float lx = bx + dp(4);
                float textH = dp(16);
                canvas.drawRect(lx - dp(2), labelY - textH + dp(2),
                        lx + labelPaint.measureText(label) + dp(2), labelY + dp(4), labelBgPaint);
                canvas.drawText(label, lx, labelY, labelPaint);
            }
        };

        // 优先使用 AccessibilityService 的 WindowManager，TYPE_ACCESSIBILITY_OVERLAY
        // 能覆盖导航栏区域（横屏时右侧导航栏也能覆盖），TYPE_APPLICATION_OVERLAY 则不行。
        AutoAccessibilityService accSvc = AutoAccessibilityService.get();
        WindowManager overlayWm;
        int overlayType;
        if (accSvc != null) {
            WindowManager accWm = (WindowManager) accSvc.getSystemService(Context.WINDOW_SERVICE);
            overlayWm = accWm != null ? accWm : wm;
            overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            overlayWm = wm;
            overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
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

        overlayWm.addView(overlayView, lp);
        activeBboxOverlayView = overlayView;
        activeBboxOverlayWm = overlayWm;

        Runnable dismiss = () -> {
            safeRemoveView(activeBboxOverlayWm, activeBboxOverlayView);
            activeBboxOverlayView = null;
            activeBboxOverlayWm = null;
        };
        overlayView.setOnClickListener(v -> {
            bboxDismissHandler.removeCallbacks(dismiss);
            dismiss.run();
        });
        bboxDismissHandler.postDelayed(dismiss, 4000);
    }

    @Override
    public void showTemplateMaskEditorByName(String templateFileName, @Nullable Runnable onSaved) {
        File taskDir = currentTaskDir;
        if (taskDir == null || !taskDir.isDirectory()) {
            Toast.makeText(this, "当前任务不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(templateFileName)) {
            Toast.makeText(this, "请先填写模板文件名", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = templateFileName.endsWith(".png") ? templateFileName : templateFileName + ".png";
        String scaleDirName = getCurrentScaleDirName();
        File templateDir = getTemplateItemDir(taskDir, scaleDirName);
        File templateFile = new File(templateDir, name);
        if (!templateFile.exists()) {
            Toast.makeText(this, "模板文件不存在，请先截图保存: " + name, Toast.LENGTH_SHORT).show();
            return;
        }
        TemplateLibraryAdapter.TemplateLibraryItem item =
                new TemplateLibraryAdapter.TemplateLibraryItem(name, templateFile, 0);
        item.scaleDirName = scaleDirName;
        showTemplateMaskEditorFromLibrary(taskDir, item, onSaved);
    }

    private void showTemplateMaskEditorFromLibrary(File taskDir,
                                                   TemplateLibraryAdapter.TemplateLibraryItem item,
                                                   @Nullable Runnable onChanged) {
        if (taskDir == null || item == null || item.file == null || !item.file.exists()) {
            Toast.makeText(this, "模板文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap templateBitmap = BitmapFactory.decodeFile(item.file.getAbsolutePath());
        if (templateBitmap == null) {
            Toast.makeText(this, "模板读取失败", Toast.LENGTH_SHORT).show();
            return;
        }
        File templateDir = getTemplateItemDir(taskDir, item.scaleDirName);
        File maskFile = new File(templateDir, CaptureScaleHelper.getTemplateMaskFileName(item.fileName));
        Bitmap maskBitmap = maskFile.exists() ? BitmapFactory.decodeFile(maskFile.getAbsolutePath()) : null;

        View overlay = LayoutInflater.from(this).inflate(R.layout.dialog_template_mask_editor, null);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        TemplateMaskEditorView editor = overlay.findViewById(R.id.template_mask_editor_view);
        TextView tvTitle = overlay.findViewById(R.id.tv_mask_editor_title);
        TextView btnDraw = overlay.findViewById(R.id.btn_mask_draw);
        TextView btnErase = overlay.findViewById(R.id.btn_mask_erase);
        TextView btnClear = overlay.findViewById(R.id.btn_mask_clear);
        TextView btnSave = overlay.findViewById(R.id.btn_mask_save);
        android.widget.SeekBar sbBrush = overlay.findViewById(R.id.sb_mask_brush);
        TextView tvBrush = overlay.findViewById(R.id.tv_mask_brush_size);

        if (tvTitle != null) tvTitle.setText("Mask: " + item.fileName);
        editor.setTemplateBitmap(templateBitmap, maskBitmap);
        if (templateBitmap != null && !templateBitmap.isRecycled()) templateBitmap.recycle();
        if (maskBitmap != null && !maskBitmap.isRecycled()) maskBitmap.recycle();

        Runnable updateModeUi = () -> {
            boolean erase = editor.isEraseMode();
            btnDraw.setBackgroundResource(erase ? R.drawable.item_operation_compact_bg : R.drawable.panel_btn_primary_selector);
            btnDraw.setTextColor(erase ? Color.rgb(49, 93, 191) : Color.WHITE);
            btnErase.setBackgroundResource(erase ? R.drawable.panel_btn_primary_selector : R.drawable.item_operation_compact_bg);
            btnErase.setTextColor(erase ? Color.WHITE : Color.rgb(49, 93, 191));
        };
        Runnable updateBrushUi = () -> tvBrush.setText(editor.getBrushSize() + " px");

        btnDraw.setOnClickListener(v -> {
            editor.setEraseMode(false);
            updateModeUi.run();
        });
        btnErase.setOnClickListener(v -> {
            editor.setEraseMode(true);
            updateModeUi.run();
        });
        btnClear.setOnClickListener(v -> editor.clearMask());
        sbBrush.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                editor.setBrushSize(progress + 1); // progress 0‥63 → brush 1‥64
                updateBrushUi.run();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        overlay.findViewById(R.id.btn_mask_zoom_in).setOnClickListener(v -> editor.zoomIn());
        overlay.findViewById(R.id.btn_mask_zoom_out).setOnClickListener(v -> editor.zoomOut());
        overlay.findViewById(R.id.btn_mask_editor_close).setOnClickListener(v -> {
            editor.release();
            safeRemoveView(overlay);
        });
        btnSave.setOnClickListener(v -> {
            Bitmap out = editor.hasMaskPixels() ? editor.exportMaskBitmap() : null;
            try {
                if (out == null) {
                    if (maskFile.exists() && maskFile.isFile()) {
                        maskFile.delete();
                    }
                    Toast.makeText(this, "Mask 已清空", Toast.LENGTH_SHORT).show();
                } else {
                    if (!templateDir.exists()) templateDir.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(maskFile, false)) {
                        out.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush();
                    }
                    Toast.makeText(this, "Mask 已保存", Toast.LENGTH_SHORT).show();
                }
                com.auto.master.utils.BitmapManager.getInstance().removeBitmap(maskFile.getAbsolutePath());
                if (currentProjectDir != null) {
                    Template.clearTaskSingleMatCache(currentProjectDir.getName(),
                            taskDir.getName(),
                            CaptureScaleHelper.getTemplateMaskFileName(item.fileName));
                }
                if (onChanged != null) onChanged.run();
            } catch (Exception e) {
                Log.w(TAG, "保存模板 mask 失败", e);
                Toast.makeText(this, "Mask 保存失败", Toast.LENGTH_SHORT).show();
            } finally {
                if (out != null && !out.isRecycled()) out.recycle();
                editor.release();
                safeRemoveView(overlay);
            }
        });

        updateModeUi.run();
        updateBrushUi.run();
        wm.addView(overlay, lp);
    }

    private void launchTemplateCaptureAfterUiSettled(String templateFileName) {
        launchTemplateCaptureAfterUiSettledInScaleDir(templateFileName, null);
    }

    /**
     * 与 launchTemplateCaptureAfterUiSettled 相同，但将截图保存到指定的 scale 子目录（用于"替换截图"）。
     * @param scaleDirName 目标 scale 子目录，如 "scale_100"；null 表示由当前 CAPTURE_SCALE 决定
     */
    private void launchTemplateCaptureAfterUiSettledInScaleDir(String templateFileName,
                                                                @Nullable String scaleDirName) {
        String normalizedName = normalizeTemplateFileName(templateFileName);
        if (TextUtils.isEmpty(normalizedName)) {
            Toast.makeText(this, "请先输入模板文件名", Toast.LENGTH_SHORT).show();
            return;
        }
        Runnable restoreViews = hideViewsForCapture(projectPanelView);
        registerTemplateCaptureRestore(restoreViews);
        runAfterScreenCaptureReady("制作模板", () -> uiHandler.postDelayed(() -> {
                    boolean started = launchTemplateCaptureInScaleDir(normalizedName, scaleDirName);
                    if (!started) {
                        CropRegionOperationHandler.clearTemplateCaptureEventListener(null);
                        restoreViews.run();
                    }
                }, CAPTURE_OVERLAY_LAUNCH_DELAY_MS),
                () -> {
                    CropRegionOperationHandler.clearTemplateCaptureEventListener(null);
                    restoreViews.run();
                });
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

    private void registerTemplateCaptureRestore(Runnable restoreViews) {
        CropRegionOperationHandler.TemplateCaptureEventListener listener =
                new CropRegionOperationHandler.TemplateCaptureEventListener() {
                    @Override
                    public void onTemplateSaved(String projectName, String taskName, String saveFileName, Rect rect) {
                        uiHandler.post(() -> {
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

    /**
     * 只删除指定 scale 子目录中的该模板文件（不跨目录），并同步更新 manifest.json。
     * @param scaleDirName "scale_100" 等；null 表示删除 flat imgDir 中的文件（legacy）
     */
    private int deleteTemplateFileInScaleDir(String fileName, @Nullable String scaleDirName) {
        if (currentTaskDir == null || TextUtils.isEmpty(fileName)) return 0;
        int deleted = 0;
        try {
            Map<String, Integer> usageMap = getTemplateUsageCountMap();
            if (usageMap.containsKey(fileName) && usageMap.get(fileName) > 0) {
                return 0; // still referenced by a node
            }
            File imgDir = new File(currentTaskDir, "img");
            File targetDir = TextUtils.isEmpty(scaleDirName)
                    ? imgDir
                    : new File(imgDir, scaleDirName);
            File file = new File(targetDir, fileName);
            if (file.exists() && file.isFile() && file.delete()) {
                deleted++;
            }
            File maskFile = new File(targetDir, CaptureScaleHelper.getTemplateMaskFileName(fileName));
            if (maskFile.exists() && maskFile.isFile()) {
                maskFile.delete();
            }
            // Remove from scale-specific manifest
            File manifestFile = new File(targetDir, "manifest.json");
            if (deleted > 0 && manifestFile.exists()
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String content = new String(Files.readAllBytes(manifestFile.toPath()),
                        StandardCharsets.UTF_8);
                if (!TextUtils.isEmpty(content.trim())) {
                    JSONObject manifest = new JSONObject(content);
                    if (manifest.has(fileName)) {
                        manifest.remove(fileName);
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

    /**
     * 只在指定 scale 子目录中重命名模板文件，并同步更新 manifest.json。
     */
    private boolean renameTemplateFileInScaleDir(File taskDir, String oldName, String newName,
                                                  @Nullable String scaleDirName) {
        if (taskDir == null || TextUtils.isEmpty(oldName) || TextUtils.isEmpty(newName)) return false;
        if (TextUtils.equals(oldName, newName)) return true;
        try {
            File imgDir = new File(taskDir, "img");
            File targetDir = TextUtils.isEmpty(scaleDirName) ? imgDir : new File(imgDir, scaleDirName);
            File oldFile = new File(targetDir, oldName);
            File newFile = new File(targetDir, newName);
            if (!oldFile.exists() || newFile.exists()) return false;
            if (!oldFile.renameTo(newFile)) return false;
            renameTemplateMaskFile(targetDir, oldName, newName);
            // Update scale-specific manifest
            File manifestFile = new File(targetDir, "manifest.json");
            if (manifestFile.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String content = new String(Files.readAllBytes(manifestFile.toPath()),
                        StandardCharsets.UTF_8);
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

    private boolean renameTemplateFile(File taskDir, String oldName, String newName) {
        if (taskDir == null || TextUtils.isEmpty(oldName) || TextUtils.isEmpty(newName)) {
            return false;
        }
        if (TextUtils.equals(oldName, newName)) {
            return true;
        }
        try {
            File imgDir = new File(taskDir, "img");
            boolean renamedAny = false;

            // Rename in flat dir (legacy scale=1.0)
            File oldFlat = new File(imgDir, oldName);
            File newFlat = new File(imgDir, newName);
            if (oldFlat.exists() && !newFlat.exists()) {
                renamedAny = oldFlat.renameTo(newFlat);
                if (renamedAny) {
                    renameTemplateMaskFile(imgDir, oldName, newName);
                }
            }

            // Rename in all scale subdirs
            for (float scale : CaptureScaleHelper.SUPPORTED_SCALES) {
                File scaleDir = new File(imgDir, CaptureScaleHelper.getScaleDirName(scale));
                File oldScaleFile = new File(scaleDir, oldName);
                File newScaleFile = new File(scaleDir, newName);
                if (oldScaleFile.exists() && !newScaleFile.exists()) {
                    if (oldScaleFile.renameTo(newScaleFile)) {
                        renamedAny = true;
                        renameTemplateMaskFile(scaleDir, oldName, newName);
                    }
                }
            }

            if (!renamedAny) {
                return false;
            }

            // Update manifest in each affected dir (per-scale + legacy flat)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                List<File> manifestDirs = new ArrayList<>();
                manifestDirs.add(imgDir); // legacy flat
                for (float scale : CaptureScaleHelper.SUPPORTED_SCALES) {
                    File sd = new File(imgDir, CaptureScaleHelper.getScaleDirName(scale));
                    if (sd.isDirectory()) manifestDirs.add(sd);
                }
                for (File mdir : manifestDirs) {
                    File manifestFile = new File(mdir, "manifest.json");
                    if (!manifestFile.exists()) continue;
                    try {
                        String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
                        if (TextUtils.isEmpty(content.trim())) continue;
                        JSONObject manifest = new JSONObject(content);
                        if (manifest.has(oldName)) {
                            Object bbox = manifest.get(oldName);
                            manifest.remove(oldName);
                            manifest.put(newName, bbox);
                            try (FileWriter writer = new FileWriter(manifestFile)) {
                                writer.write(manifest.toString(2));
                            }
                        }
                    } catch (Exception ignore) {}
                }
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "重命名模板失败", e);
            return false;
        }
    }

    private void renameTemplateMaskFile(File dir, String oldName, String newName) {
        if (dir == null || TextUtils.isEmpty(oldName) || TextUtils.isEmpty(newName)) {
            return;
        }
        File oldMask = new File(dir, CaptureScaleHelper.getTemplateMaskFileName(oldName));
        if (!oldMask.exists() || !oldMask.isFile()) {
            return;
        }
        File newMask = new File(dir, CaptureScaleHelper.getTemplateMaskFileName(newName));
        if (newMask.exists()) {
            return;
        }
        if (!oldMask.renameTo(newMask)) {
            Log.w(TAG, "重命名模板 mask 失败: " + oldMask.getName());
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

    // ==================== Phase 4B: Execution Step Overlay ====================
    // 实现已迁移至 StepAndDelayOverlayManager，此处保留薄封装供内部调用。

    private void showStepOverlay(String operationName, String typeLabel, int stepIndex, int total) {
        stepDelayOverlayManager.showStep(operationName, typeLabel, stepIndex, total);
    }

    private void hideStepOverlay() {
        stepDelayOverlayManager.hideStep();
    }

    private int getTypeColorForOverlay(String typeLabel) {
        return stepDelayOverlayManager.getTypeColor(typeLabel);
    }

    private int getStatusBarHeightPx() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            try { return getResources().getDimensionPixelSize(resId); } catch (Exception ignored) {}
        }
        return 0;
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
            File root = AppStorage.getProjectsRoot(this);
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
    // 实现已迁移至 TriggerDialogHelper。

    private void showTriggerManager() {
        triggerDialogHelper.showTriggerManager();
    }

    // ==================== Phase 4D: App-launch polling ====================
    // 实现已迁移至 AppLaunchTriggerManager，此处保留薄封装供内部调用。

    private void refreshAppLaunchPollingState() {
        appLaunchTriggerManager.refreshPollingState();
    }

    private void stopAppLaunchPolling() {
        appLaunchTriggerManager.stopPolling();
    }

    // ==================== Variable Helper Methods ====================

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
        String v = action.trim().toLowerCase(Locale.ROOT);
        return "inc".equals(v) || "dec".equals(v) || "negate".equals(v) || "abs".equals(v);
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
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateVariableSourceInputUi(labelView, valueView, s == null ? "literal" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void bindVariableMathWatcher(AutoCompleteTextView modeView, AutoCompleteTextView actionView,
                                         TextView labelView, EditText valueView) {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String mode   = modeView.getText()   == null ? "literal" : modeView.getText().toString();
                String action = actionView.getText() == null ? "add"     : actionView.getText().toString();
                updateVariableMathOperandUi(labelView, valueView, mode, action);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        modeView.addTextChangedListener(watcher);
        actionView.addTextChangedListener(watcher);
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
    public View getProjectPanelView() {
        return projectPanelView;
    }

    @Override
    public File getProjectsRootDir() {
        return AppStorage.getProjectsRoot(this);
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

    // OperationItem 已提取至独立文件 com.auto.master.floatwin.OperationItem

    // ==================== ScriptExecutionListener 实现 ====================

    private boolean isSelectedScriptSession(@Nullable String sessionId) {
        if (TextUtils.isEmpty(sessionId)) {
            return true;
        }
        if (TextUtils.equals(sessionId, selectedScriptSessionId)) {
            return true;
        }
        return TextUtils.isEmpty(selectedScriptSessionId)
                && TextUtils.equals(sessionId, ScriptRunner.getActiveSessionId());
    }

    @Override
    public void onOperationStart(String sessionId, String operationId, String operationName) {
        if (!isSelectedScriptSession(sessionId)) {
            refreshScriptSessionDialogIfVisible();
            return;
        }
        refreshScriptSessionDialogIfVisible();
        onOperationStart(operationId, operationName);
    }

    @Override
    public void onNodePreDelayStart(String sessionId, String operationId, long durationMs) {
        if (!isSelectedScriptSession(sessionId)) {
            return;
        }
        onNodePreDelayStart(operationId, durationMs);
    }

    @Override
    public void onDelayOperationCountdownStart(String sessionId,
                                               String operationId,
                                               long durationMs,
                                               @Nullable CountDownLatch readySignal) {
        if (!isSelectedScriptSession(sessionId)) {
            if (readySignal != null) {
                readySignal.countDown();
            }
            return;
        }
        onDelayOperationCountdownStart(operationId, durationMs, readySignal);
    }

    @Override
    public void onMtryAttempt(String sessionId, String operationId, int current, int total) {
        if (!isSelectedScriptSession(sessionId)) {
            return;
        }
        onMtryAttempt(operationId, current, total);
    }

    @Override
    public void onMtryRetryDelay(String sessionId, String operationId, long delayMs) {
        if (!isSelectedScriptSession(sessionId)) {
            return;
        }
        onMtryRetryDelay(operationId, delayMs);
    }

    @Override
    public void onOperationComplete(String sessionId, String operationId, boolean success) {
        if (!isSelectedScriptSession(sessionId)) {
            refreshScriptSessionDialogIfVisible();
            return;
        }
        refreshScriptSessionDialogIfVisible();
        onOperationComplete(operationId, success);
    }

    @Override
    public void onScriptComplete(String sessionId) {
        if (!isSelectedScriptSession(sessionId)) {
            refreshScriptSessionDialogIfVisible();
            return;
        }
        uiHandler.postDelayed(() -> {
            bindScriptSession(sessionId);
            refreshScriptSessionDialogIfVisible();
        }, 50L);
        focusRunMode = false;
        refreshAppLaunchPollingState();
        ScriptRunner.clearExecutionListener();
    }

    @Override
    public void onTaskSwitch(String sessionId,
                             String taskId,
                             String taskName,
                             List<OperationItem> operations) {
        if (!isSelectedScriptSession(sessionId)) {
            return;
        }
        onTaskSwitch(taskId, taskName, operations);
    }

    @Override
    public void onOperationStart(String operationId, String operationName) {
        stopAppLaunchPolling();
        if (focusRunMode) {
            return;
        }
        opStartTimeMs.put(operationId, System.currentTimeMillis());
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

        postToUi(() -> {
            followRuntimeTaskIfPanelIsStale(operationId);
            if (currentOperationAdapter != null) {
                currentOperationAdapter.setRunningPosition(operationId);
            }
            scrollRunningOperationIntoView(operationId);
        });
        syncProjectPanelRuntimeUi();
        if (opItem == null || (opItem.nodePreDelayMs <= 0L && opItem.delayDurationMs <= 0L)) {
            maybeStartDelayProgress(opItem);
        }

        // 2. 更新悬浮球状态：运行中切为贴边 handle
        dockBallForRunningFromCurrentPosition();

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

    @Override
    public void onDelayOperationCountdownStart(String operationId, long durationMs) {
        onDelayOperationCountdownStart(operationId, durationMs, null);
    }

    @Override
    public void onDelayOperationCountdownStart(String operationId,
                                               long durationMs,
                                               @Nullable CountDownLatch readySignal) {
        if (focusRunMode) {
            if (readySignal != null) readySignal.countDown();
            return;
        }
        OperationItem opItem = findRunningItem(operationId);
        if (opItem == null || durationMs <= 0L) {
            if (readySignal != null) readySignal.countDown();
            return;
        }
        OperationItem runtimeItem = new OperationItem(
                opItem.name,
                opItem.id,
                opItem.type,
                opItem.index,
                durationMs,
                true,
                0L,
                0L,
                0L,
                false);
        uiHandler.postAtFrontOfQueue(() -> {
            markDelayOperationRunningLight(opItem);
            maybeStartDelayProgress(runtimeItem, () -> {
                if (readySignal != null) readySignal.countDown();
            });
        });
    }

    private void markDelayOperationRunningLight(OperationItem opItem) {
        if (opItem == null) return;
        stopAppLaunchPolling();
        opStartTimeMs.put(opItem.id, System.currentTimeMillis());
        currentRunningOperationId = opItem.id;
        currentRunningOperationName = opItem.name;
        currentOperationIndex = opItem.index + 1;
        CrashLogger.updateRunContext(currentRunningProject, currentRunningTask, opItem.id, opItem.name);
        updateRunningPanelStatus("运行中", 0xFF4CAF50);
        if (currentOperationAdapter != null) {
            currentOperationAdapter.setRunningPosition(opItem.id);
        }
    }

    @Override
    public void onNodePreDelayStart(String operationId, long durationMs) {
        if (focusRunMode) {
            return;
        }
        OperationItem opItem = findRunningItem(operationId);
        if (opItem == null || durationMs <= 0L) {
            return;
        }
        OperationItem runtimeItem = new OperationItem(
                opItem.name,
                opItem.id,
                opItem.type,
                opItem.index,
                opItem.delayDurationMs,
                opItem.delayShowCountdown,
                durationMs,
                durationMs,
                durationMs,
                false);
        maybeStartDelayProgress(runtimeItem);
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

    public static long extractNodePreDelayMs(@Nullable MetaOperation operation) {
        return OperationHandler.getConfiguredNodePreDelayMs(operation);
    }

    public static long extractNodePreDelayMinMs(@Nullable MetaOperation operation) {
        return OperationHandler.getNodePreDelayMinMs(operation);
    }

    public static long extractNodePreDelayMaxMs(@Nullable MetaOperation operation) {
        return OperationHandler.getNodePreDelayMaxMs(operation);
    }

    public static boolean extractNodePreDelayRandom(@Nullable MetaOperation operation) {
        return OperationHandler.isNodePreDelayRandom(operation);
    }

    private static List<String> extractTemplatePreviewNames(@Nullable MetaOperation operation) {
        if (operation == null || operation.getType() == null) {
            return Collections.emptyList();
        }
        return extractTemplatePreviewNames(operation.getType(), operation.getInputMap());
    }

    private static List<String> extractTemplatePreviewNames(int type, @Nullable Map<String, Object> inputMap) {
        if (inputMap == null || inputMap.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        switch (type) {
            case 3:
            case 4:
            case 6:
                addTemplatePreviewName(names, inputMap.get(MetaOperation.SAVEFILENAME));
                break;
            case 7:
                addMatchMapTemplatePreviewNames(names, inputMap.get(MetaOperation.MATCHMAP));
                break;
            default:
                break;
        }
        return names.isEmpty() ? Collections.emptyList() : new ArrayList<>(names);
    }

    private static List<String> extractTemplatePreviewNames(int type, @Nullable JSONObject inputMap) {
        if (inputMap == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        switch (type) {
            case 3:
            case 4:
            case 6:
                addTemplatePreviewName(names, inputMap.opt(MetaOperation.SAVEFILENAME));
                break;
            case 7:
                addMatchMapTemplatePreviewNames(names, inputMap.opt(MetaOperation.MATCHMAP));
                break;
            default:
                break;
        }
        return names.isEmpty() ? Collections.emptyList() : new ArrayList<>(names);
    }

    private static void addTemplatePreviewName(Set<String> names, Object rawName) {
        if (names == null || rawName == null) {
            return;
        }
        String name = String.valueOf(rawName).trim();
        if (TextUtils.isEmpty(name)
                || "null".equalsIgnoreCase(name)
                || CaptureScaleHelper.isTemplateMaskFileName(name)
                || name.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return;
        }
        names.add(name);
        if (!name.contains(".")) {
            names.add(name + ".png");
        }
    }

    @SuppressWarnings("unchecked")
    private static void addMatchMapTemplatePreviewNames(Set<String> names, Object rawMatchMap) {
        if (names == null || rawMatchMap == null) {
            return;
        }
        if (rawMatchMap instanceof JSONObject) {
            addMatchMapTemplatePreviewNames(names, (JSONObject) rawMatchMap);
            return;
        }
        if (rawMatchMap instanceof Map) {
            Map<?, ?> matchMap = (Map<?, ?>) rawMatchMap;
            for (Object templateGroup : matchMap.values()) {
                if (templateGroup instanceof Map) {
                    for (Object templateName : ((Map<?, ?>) templateGroup).keySet()) {
                        addTemplatePreviewName(names, templateName);
                    }
                }
            }
            return;
        }
        String raw = String.valueOf(rawMatchMap).trim();
        if (TextUtils.isEmpty(raw) || "null".equalsIgnoreCase(raw)) {
            return;
        }
        try {
            addMatchMapTemplatePreviewNames(names, new JSONObject(raw));
        } catch (Exception ignored) {
        }
    }

    private static void addMatchMapTemplatePreviewNames(Set<String> names, JSONObject matchMap) {
        if (matchMap == null) {
            return;
        }
        Iterator<String> bboxKeys = matchMap.keys();
        while (bboxKeys.hasNext()) {
            JSONObject templates = matchMap.optJSONObject(bboxKeys.next());
            if (templates == null) {
                continue;
            }
            Iterator<String> templateKeys = templates.keys();
            while (templateKeys.hasNext()) {
                addTemplatePreviewName(names, templateKeys.next());
            }
        }
    }

    // ── 延迟进度悬浮层：实现已迁移至 StepAndDelayOverlayManager，此处仅保留薄封装。

    private void maybeStartDelayProgress(@Nullable OperationItem opItem) {
        stepDelayOverlayManager.maybeStartDelay(opItem);
    }

    private void maybeStartDelayProgress(@Nullable OperationItem opItem, @Nullable Runnable onFirstFrameReady) {
        stepDelayOverlayManager.maybeStartDelay(opItem, onFirstFrameReady);
    }

    private void stopDelayProgress() {
        stepDelayOverlayManager.stopDelay();
    }

    private void renderDelayProgressState() {
        stepDelayOverlayManager.syncDelayState();
    }

    private void updateFlowGraphHighlight(String operationId) {
        flowGraphPanelHelper.highlightOperation(operationId);
    }

    @Override
    public void onMtryAttempt(String operationId, int current, int total) {
        if (focusRunMode) return;
        stepDelayOverlayManager.showMtryAttempt(current, total);
    }

    @Override
    public void onMtryRetryDelay(String operationId, long delayMs) {
        if (focusRunMode) return;
        stepDelayOverlayManager.startRetryDelay(operationId, delayMs);
    }

    @Override
    public void onOperationComplete(String operationId, boolean success) {
        if (focusRunMode) {
            return;
        }
        if (nodeFloatButtonUiHelper != null) {
            nodeFloatButtonUiHelper.onOperationRunComplete(operationId, success);
        }
        stepDelayOverlayManager.stopDelayIfMatch(operationId);
        Long startMs = opStartTimeMs.remove(operationId);
        long cost = startMs == null ? -1 : (System.currentTimeMillis() - startMs);
        if (cost >= 0) {
            opDurationsMs.add(cost);
        }
        if (success) {
            opSuccessCount++;
        } else {
            opFailureCount++;
            recordFailureReason("operation_failed");
        }
        // operation 完成，更新状态
        if (!success) {
            // 如果失败，更新状态为错误
            updateRunningPanelStatus("运行出错", 0xFFF44336);
        }
        syncProjectPanelRuntimeUi();
    }

    @Override
    public void onScriptComplete() {
        if (focusRunMode) {
            handleFocusScriptComplete();
            return;
        }
        runtimeTransitionCoordinator.handleScriptComplete();
    }

    private void handleFocusScriptComplete() {
        refreshAppLaunchPollingState();
        stopDelayProgress();
        hideStepOverlay();
        flowGraphPanelHelper.clearHighlight();
        currentRunningOperationId = "";
        currentRunningOperationName = "";
        isPaused = false;
        setBallVisible(true);
        ScriptRunner.clearExecutionListener();
        focusRunMode = false;
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

            refreshCurrentTaskCache();
            loadOperations(currentTaskDir, true);
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

            // Clean flat dir (legacy) + all scale subdirs
            List<File> dirsToClean = new ArrayList<>();
            dirsToClean.add(imgDir);
            for (float scale : CaptureScaleHelper.SUPPORTED_SCALES) {
                File scaleDir = new File(imgDir, CaptureScaleHelper.getScaleDirName(scale));
                if (scaleDir.isDirectory()) {
                    dirsToClean.add(scaleDir);
                }
            }

            for (File dir : dirsToClean) {
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String name = file.getName();
                    if ("manifest.json".equalsIgnoreCase(name)) {
                        continue;
                    }
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (CaptureScaleHelper.isTemplateMaskFileName(lower)) {
                        continue;
                    }
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

            // Clean manifests in each dir (scale subdirs + legacy flat dir)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for (File dir : dirsToClean) {
                    File manifestFile = new File(dir, "manifest.json");
                    if (!manifestFile.exists()) continue;
                    try {
                        String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
                        if (TextUtils.isEmpty(content.trim())) continue;
                        JSONObject manifest = new JSONObject(content);
                        List<String> keys = new ArrayList<>();
                        java.util.Iterator<String> it = manifest.keys();
                        while (it.hasNext()) keys.add(it.next());
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
                    } catch (Exception ignore) {
                        Log.w(TAG, "清理 manifest 失败: " + dir.getName(), ignore);
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
            refreshCurrentTaskCache();
            loadOperations(currentTaskDir, true);
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
        clipboardDialogHelper.showOperationClipboardDialog(targetOperationId, insertBefore);
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

    private void showFlowGraphDialog() {
        flowGraphPanelHelper.showFlowGraphDialog();
    }

    private void refreshOpenFlowGraphPanel(@Nullable String preferredNodeId) {
        flowGraphPanelHelper.refreshOpenPanel(preferredNodeId);
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

    private List<OperationIdPickerAdapter.OperationPickItem> getCurrentProjectTaskPickItems() {
        List<OperationIdPickerAdapter.OperationPickItem> items = new ArrayList<>();
        List<String> taskIds = getCurrentProjectTaskIds();
        Project project = currentProjectDir == null ? null : findCachedProjectByName(currentProjectDir.getName());
        if (project == null && currentProjectDir != null) {
            project = loadProjectFromDir(currentProjectDir);
        }
        Map<String, Task> taskMap = project == null ? null : project.getTaskMap();
        for (String taskId : taskIds) {
            if (TextUtils.isEmpty(taskId)) {
                continue;
            }
            Task task = taskMap == null ? null : taskMap.get(taskId);
            String name = task == null || TextUtils.isEmpty(task.getName()) ? taskId : task.getName();
            int operationCount = task == null || task.getOperationMap() == null ? getTaskOperationIds(taskId).size() : task.getOperationMap().size();
            String meta = operationCount + " 个节点";
            if (task != null && !TextUtils.isEmpty(task.getStartOperationId())) {
                meta += " · 起点 " + task.getStartOperationId();
            }
            items.add(new OperationIdPickerAdapter.OperationPickItem(items.size() + 1, taskId, name, meta));
        }
        return items;
    }

    private List<OperationIdPickerAdapter.OperationPickItem> getTaskOperationPickItems(String taskId) {
        List<OperationIdPickerAdapter.OperationPickItem> items = new ArrayList<>();
        if (currentProjectDir == null || TextUtils.isEmpty(taskId)) {
            return items;
        }
        Task task = findCurrentProjectTask(taskId);
        List<OperationItem> operationItems = task == null
                ? new ArrayList<>()
                : buildOperationItemsFromTask(task);
        if (operationItems.isEmpty()) {
            File taskDir = new File(currentProjectDir, taskId);
            operationItems = readOperationItemsDirect(taskDir);
        }
        for (OperationItem item : operationItems) {
            if (item == null || TextUtils.isEmpty(item.id)) {
                continue;
            }
            String name = TextUtils.isEmpty(item.name) ? "未命名" : item.name;
            String type = TextUtils.isEmpty(item.type) ? "未知" : item.type;
            items.add(new OperationIdPickerAdapter.OperationPickItem(items.size() + 1, item.id, name, type));
        }
        return items;
    }

    @Nullable
    private Task findCurrentProjectTask(String taskId) {
        if (currentProjectDir == null || TextUtils.isEmpty(taskId)) {
            return null;
        }
        Project project = findCachedProjectByName(currentProjectDir.getName());
        if (project == null) {
            project = loadProjectFromDir(currentProjectDir);
        }
        Map<String, Task> taskMap = project == null ? null : project.getTaskMap();
        return taskMap == null ? null : taskMap.get(taskId);
    }

    private String formatTaskReference(String taskId) {
        if (TextUtils.isEmpty(taskId)) {
            return "";
        }
        List<OperationIdPickerAdapter.OperationPickItem> items = getCurrentProjectTaskPickItems();
        for (OperationIdPickerAdapter.OperationPickItem item : items) {
            if (item != null && TextUtils.equals(taskId, item.id)) {
                return String.format(Locale.getDefault(), "#%02d %s <%s>", item.order, item.name, item.id);
            }
        }
        return taskId;
    }

    private String formatTaskOperationReference(String taskId, String operationId) {
        if (TextUtils.isEmpty(operationId)) {
            return "";
        }
        List<OperationIdPickerAdapter.OperationPickItem> items = getTaskOperationPickItems(taskId);
        for (OperationIdPickerAdapter.OperationPickItem item : items) {
            if (item != null && TextUtils.equals(operationId, item.id)) {
                return formatOperationReferenceLabel(item);
            }
        }
        return operationId;
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

    private void bindDropdownSelect(AutoCompleteTextView view, List<String> options) {
        if (dialogHelpers != null) {
            dialogHelpers.bindDropdownSelect(view, options);
        }
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
        // Collect template names from all scale subdirs + flat dir (deduplicated by name)
        Set<String> seen = new LinkedHashSet<>();
        collectTemplateNamesFromDir(imgDir, seen);
        for (float scale : CaptureScaleHelper.SUPPORTED_SCALES) {
            File scaleDir = new File(imgDir, CaptureScaleHelper.getScaleDirName(scale));
            collectTemplateNamesFromDir(scaleDir, seen);
        }
        files.addAll(seen);
        return files;
    }

    private void collectTemplateNamesFromDir(File dir, Set<String> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] imgFiles = dir.listFiles(f -> {
            if (!f.isFile()) return false;
            String name = f.getName().toLowerCase(Locale.ROOT);
            return !"manifest.json".equals(name)
                    && !CaptureScaleHelper.isTemplateMaskFileName(name)
                    && (name.endsWith(".png") || name.endsWith(".jpg")
                        || name.endsWith(".jpeg") || name.endsWith(".webp"));
        });
        if (imgFiles == null) return;
        java.util.Arrays.sort(imgFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : imgFiles) {
            out.add(f.getName());
        }
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

    /**
     * 第一层：返回当前 task 下所有存在的 scale 子目录（文件夹条目），供模板库首屏展示。
     * isFolder=true，usageCount 存放该目录下的模板数量。
     */
    /** 返回当前 CAPTURE_SCALE 对应的 scale 目录名，如 "scale_100"、"scale_50"。 */
    private String getCurrentScaleDirName() {
        return CaptureScaleHelper.getScaleDirName(ScreenCaptureManager.CAPTURE_SCALE);
    }

    private List<TemplateLibraryAdapter.TemplateLibraryItem> getCurrentTaskTemplateFolderItems() {
        List<TemplateLibraryAdapter.TemplateLibraryItem> items = new ArrayList<>();
        if (currentTaskDir == null) return items;
        File imgDir = new File(currentTaskDir, "img");

        for (float scale : CaptureScaleHelper.SUPPORTED_SCALES) {
            String dirName = CaptureScaleHelper.getScaleDirName(scale);
            File scaleDir = new File(imgDir, dirName);
            if (!scaleDir.isDirectory()) continue;
            File[] files = scaleDir.listFiles(f -> {
                if (!f.isFile()) return false;
                String n = f.getName().toLowerCase(Locale.ROOT);
                return !CaptureScaleHelper.isTemplateMaskFileName(n)
                        && (n.endsWith(".png") || n.endsWith(".jpg")
                        || n.endsWith(".jpeg") || n.endsWith(".webp"));
            });
            int count = files == null ? 0 : files.length;
            // 空目录也显示，便于用户感知当前倍率
            TemplateLibraryAdapter.TemplateLibraryItem folder =
                    new TemplateLibraryAdapter.TemplateLibraryItem(dirName, dirName, null, count);
            folder.isFolder = true;
            items.add(folder);
        }

        // legacy 平铺目录（scale=1.0 旧版文件）
        File[] flatFiles = imgDir.listFiles(f -> {
            if (!f.isFile()) return false;
            String n = f.getName().toLowerCase(Locale.ROOT);
            return !"manifest.json".equals(n)
                    && !CaptureScaleHelper.isTemplateMaskFileName(n)
                    && (n.endsWith(".png") || n.endsWith(".jpg")
                        || n.endsWith(".jpeg") || n.endsWith(".webp"));
        });
        if (flatFiles != null && flatFiles.length > 0) {
            TemplateLibraryAdapter.TemplateLibraryItem legacy =
                    new TemplateLibraryAdapter.TemplateLibraryItem(
                            "legacy (旧版)", null, null, flatFiles.length);
            legacy.isFolder = true;
            items.add(legacy);
        }
        return items;
    }

    /**
     * 第二层：返回指定 scale 子目录下的所有模板文件。
     * @param scaleDirName "scale_100" 等；null 或空字符串表示 legacy 平铺目录
     */
    private List<TemplateLibraryAdapter.TemplateLibraryItem> getCurrentTaskTemplateLibraryItemsInDir(
            @Nullable String scaleDirName) {
        List<TemplateLibraryAdapter.TemplateLibraryItem> items = new ArrayList<>();
        if (currentTaskDir == null) return items;
        Map<String, Integer> usageMap = getTemplateUsageCountMap();
        File imgDir = new File(currentTaskDir, "img");
        File dir = TextUtils.isEmpty(scaleDirName) ? imgDir : new File(imgDir, scaleDirName);
        if (!dir.isDirectory()) return items;
        File[] files = dir.listFiles(f -> {
            if (!f.isFile()) return false;
            String n = f.getName().toLowerCase(Locale.ROOT);
            return !"manifest.json".equals(n)
                    && !CaptureScaleHelper.isTemplateMaskFileName(n)
                    && (n.endsWith(".png") || n.endsWith(".jpg")
                        || n.endsWith(".jpeg") || n.endsWith(".webp"));
        });
        if (files == null) return items;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) {
            int usageCount = usageMap.containsKey(f.getName()) ? usageMap.get(f.getName()) : 0;
            items.add(new TemplateLibraryAdapter.TemplateLibraryItem(
                    f.getName(), scaleDirName, f, usageCount));
        }
        return items;
    }

    private List<TemplateLibraryAdapter.TemplateLibraryItem> getCurrentTaskTemplateLibraryItems() {
        List<TemplateLibraryAdapter.TemplateLibraryItem> items = new ArrayList<>();
        if (currentTaskDir == null) {
            return items;
        }
        Map<String, Integer> usageMap = getTemplateUsageCountMap();
        File imgDir = new File(currentTaskDir, "img");

        // Show one item per (scaleDirName, fileName) pair so the user sees
        // which scale dir each template belongs to. Items are grouped by scale.
        for (float scale : CaptureScaleHelper.SUPPORTED_SCALES) {
            String dirName = CaptureScaleHelper.getScaleDirName(scale);
            File scaleDir = new File(imgDir, dirName);
            if (!scaleDir.isDirectory()) continue;
            File[] files = scaleDir.listFiles(f -> {
                if (!f.isFile()) return false;
                String n = f.getName().toLowerCase(Locale.ROOT);
                return !CaptureScaleHelper.isTemplateMaskFileName(n)
                        && (n.endsWith(".png") || n.endsWith(".jpg")
                        || n.endsWith(".jpeg") || n.endsWith(".webp"));
            });
            if (files == null) continue;
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                int usageCount = usageMap.containsKey(f.getName()) ? usageMap.get(f.getName()) : 0;
                items.add(new TemplateLibraryAdapter.TemplateLibraryItem(
                        f.getName(), dirName, f, usageCount));
            }
        }

        // Legacy flat dir (scale=1.0 created before scale-aware storage)
        File[] flatFiles = imgDir.listFiles(f -> {
            if (!f.isFile()) return false;
            String n = f.getName().toLowerCase(Locale.ROOT);
            return !"manifest.json".equals(n)
                    && !CaptureScaleHelper.isTemplateMaskFileName(n)
                    && (n.endsWith(".png") || n.endsWith(".jpg")
                        || n.endsWith(".jpeg") || n.endsWith(".webp"));
        });
        if (flatFiles != null) {
            java.util.Arrays.sort(flatFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : flatFiles) {
                int usageCount = usageMap.containsKey(f.getName()) ? usageMap.get(f.getName()) : 0;
                // scaleDirName = null for legacy flat files
                items.add(new TemplateLibraryAdapter.TemplateLibraryItem(
                        f.getName(), null, f, usageCount));
            }
        }

        return items;
    }

    /** Searches all scale subdirs + flat dir to find the first existing file with the given name. */
    @Nullable
    private File findTemplateInAnyScaleDir(File imgDir, String name) {
        // flat dir
        File flat = new File(imgDir, name);
        if (flat.exists()) return flat;
        // scale subdirs
        for (float scale : CaptureScaleHelper.SUPPORTED_SCALES) {
            File f = new File(new File(imgDir, CaptureScaleHelper.getScaleDirName(scale)), name);
            if (f.exists()) return f;
        }
        return null;
    }

    private List<String> getMatchMethodOptions() {
        List<String> options = new ArrayList<>();
        options.add(METHOD_TM_CCOEFF_NORMED);
        options.add(METHOD_TM_CCOEFF);
        options.add(METHOD_TM_CCORR_NORMED);
        options.add(METHOD_TM_CCORR);
        options.add(METHOD_TM_SQDIFF_NORMED);
        options.add(METHOD_TM_SQDIFF);
        options.add(METHOD_RANDOM_SAMPLE);
        options.add(METHOD_RANDOM_ROI);
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
        if (methodText.contains("随机 ROI") || methodText.contains("随机ROI") || t.contains("RANDOM_ROI")) {
            return MetaOperation.MATCH_METHOD_RANDOM_ROI;
        }
        if (methodText.contains("随机mask采样") || t.contains("RANDOM")) {
            return MetaOperation.MATCH_METHOD_RANDOM_SAMPLE;
        }
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
            case MetaOperation.MATCH_METHOD_RANDOM_SAMPLE:
                return METHOD_RANDOM_SAMPLE;
            case MetaOperation.MATCH_METHOD_RANDOM_ROI:
                return METHOD_RANDOM_ROI;
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
        // scale-aware lookup: checks scale_{key}/ subdir first, falls back to flat path for scale=1.0
        return CaptureScaleHelper.resolveTemplateFile(imgDir, finalName, ScreenCaptureManager.CAPTURE_SCALE);
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
        Bitmap bitmap = BitmapManager.getInstance().loadThumbnail(
                file.getAbsolutePath(),
                dp(320),
                dp(220));
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
        statusView.setText("状态：已选择 " + file.getName() + " | 步骤" + node.strokes.size() + "步 | " + node.duration + "ms");
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
            postToUi(() -> {
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
        long gestureRecordIdleFinishMs = SystemRuntimeConfig.load(this).gestureRecordIdleFinishMs;
        String idleFinishText = formatDurationSecondsText(gestureRecordIdleFinishMs);

        postToUiDelayed(() -> {
            try {
                if (tvGestureStatus != null) {
                    tvGestureStatus.setText("状态：连续录制中，请完成多次滑动，停顿约" + idleFinishText + "自动保存");
                }
                Toast.makeText(this, "开始连续录制手势，停顿约" + idleFinishText + "自动保存", Toast.LENGTH_SHORT).show();
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

                    postToUiDelayed(() -> {
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

    // ─── Pipeline Gesture Methods ───────────────────────────────────────────────

    private void showGestureLibraryDialogPipeline(String currentFile, java.util.function.Consumer<String> onFileSelected) {
        List<GestureLibraryAdapter.GestureLibraryItem> items = getCurrentTaskGestureLibraryItems();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gesture_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(360, true);
        wm.addView(dialogView, dialogLp);

        RecyclerView rv = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch = dialogView.findViewById(R.id.edt_library_search);
        rv.setLayoutManager(new LinearLayoutManager(this));
        GestureLibraryAdapter adapter = new GestureLibraryAdapter(items, item -> {
            if (onFileSelected != null) onFileSelected.accept(item.fileName);
            safeRemoveView(dialogView);
        });
        rv.setAdapter(adapter);

        dialogView.findViewById(R.id.btn_library_close).setOnClickListener(v -> safeRemoveView(dialogView));
        dialogView.findViewById(R.id.btn_library_clear).setOnClickListener(v -> {
            if (onFileSelected != null) onFileSelected.accept("");
            safeRemoveView(dialogView);
        });
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.updateFilter(s == null ? "" : s.toString());
            }
        });
    }

    private void beginPipelineStepRecord(View dialogView, boolean singleStroke,
                                          java.util.function.Consumer<String> onFileSaved) {
        doPipelineStepRecord(dialogView, singleStroke, generateGestureTimestampName(), onFileSaved, false);
    }

    private void beginPipelineContinuousRecord(View dialogView,
                                                java.util.function.Consumer<String> onFileSaved) {
        doPipelineStepRecord(dialogView, false, generateGestureTimestampName(), onFileSaved, false, true);
    }

    private void rerecordPipelineStep(View dialogView, boolean singleStroke, String oldFile,
                                       java.util.function.Consumer<String> onFileSaved) {
        String fileName = normalizeGestureFileName(TextUtils.isEmpty(oldFile) ? generateGestureTimestampName() : oldFile);
        doPipelineStepRecord(dialogView, singleStroke, fileName, onFileSaved, true);
    }

    private void rerecordPipelineContinuousRecord(View dialogView, String oldFile,
                                                   java.util.function.Consumer<String> onFileSaved) {
        String fileName = normalizeGestureFileName(TextUtils.isEmpty(oldFile) ? generateGestureTimestampName() : oldFile);
        doPipelineStepRecord(dialogView, false, fileName, onFileSaved, true, true);
    }

    private void doPipelineStepRecord(View dialogView, boolean singleStroke, String fileName,
                                       java.util.function.Consumer<String> onFileSaved, boolean isRerecord) {
        doPipelineStepRecord(dialogView, singleStroke, fileName, onFileSaved, isRerecord, false);
    }

    private void doPipelineStepRecord(View dialogView, boolean singleStroke, String fileName,
                                       java.util.function.Consumer<String> onFileSaved, boolean isRerecord,
                                       boolean forceContinuous) {
        if (currentProjectDir == null || currentTaskDir == null) {
            Toast.makeText(this, "当前 Task 无效", Toast.LENGTH_SHORT).show();
            return;
        }
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            Toast.makeText(this, "无障碍服务未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        SystemRuntimeConfig cfg = SystemRuntimeConfig.load(this);
        boolean stepReplay = !forceContinuous && cfg.gestureStepReplay;
        long intervalMs = Math.max(100L, cfg.gestureStepIntervalMs);
        long idleMs = singleStroke ? 0L : cfg.gestureRecordIdleFinishMs;
        Runnable restoreViews = hideViewsForCapture(dialogView, projectPanelView);

        String idleText = formatDurationSecondsText(idleMs);
        String hint;
        if (singleStroke) {
            hint = isRerecord ? "重录单次手势，滑动一次即完成" : "录制单次手势，滑动一次即完成";
        } else if (forceContinuous) {
            hint = "连续录制多步手势，停顿 " + idleText + " 自动保存";
        } else {
            hint = isRerecord ? "重录步进手势，停顿 " + idleText + " 自动保存"
                    : "录制步进手势，停顿 " + idleText + " 自动保存";
        }

        GestureOverlayView.OnGestureRecordedListener doneListener = node -> {
            boolean ok = saveGestureNodeForCurrentTask(fileName, node);
            if (!ok) {
                postToUi(() -> {
                    restoreViews.run();
                    Toast.makeText(this, "手势保存失败", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            if (singleStroke) {
                // 单次模式：保持 dialog 隐藏，回放完成后才恢复，防止 dialog 遮挡手势事件
                AutoAccessibilityService replaySvc = AutoAccessibilityService.get();
                if (replaySvc != null) {
                    postToUiDelayed(() -> {
                        replaySvc.showGestureTrail(node);
                        replaySvc.replayGesture(node, success -> postToUi(() -> {
                            restoreViews.run();
                            if (onFileSaved != null) onFileSaved.accept(fileName);
                            Toast.makeText(this, (isRerecord ? "重录完成: " : "录制完成: ") + fileName, Toast.LENGTH_SHORT).show();
                        }));
                    }, 300);
                } else {
                    postToUi(() -> {
                        restoreViews.run();
                        if (onFileSaved != null) onFileSaved.accept(fileName);
                        Toast.makeText(this, (isRerecord ? "重录完成: " : "录制完成: ") + fileName, Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                postToUi(() -> {
                    restoreViews.run();
                    if (onFileSaved != null) onFileSaved.accept(fileName);
                    Toast.makeText(this, (isRerecord ? "重录完成: " : "录制完成: ") + fileName, Toast.LENGTH_SHORT).show();
                });
            }
        };

        postToUiDelayed(() -> {
            try {
                Toast.makeText(this, hint, Toast.LENGTH_SHORT).show();
                if (singleStroke || !stepReplay) {
                    svc.startGestureRecording(doneListener, idleMs);
                } else {
                    // 多次 + 即时回放
                    // 修复：setGestureOverlayInteractive 是异步 IPC，需要足够延迟让标志生效
                    // 才能保证 dispatchGesture 的事件穿透到底层 App
                    svc.startGestureRecording(doneListener, idleMs, (strokeNode, resumeRecording) -> {
                        AutoAccessibilityService rs = AutoAccessibilityService.get();
                        if (rs == null) { resumeRecording.run(); return; }
                        postToUiDelayed(() -> {
                            rs.setGestureOverlayInteractive(false);
                            // 等待 WM 标志异步生效后再 dispatch，避免事件被 overlay 拦截
                            postToUiDelayed(() -> {
                                rs.showGestureTrail(strokeNode); // 补回轨迹动画
                                rs.replayGesture(strokeNode, success ->
                                    postToUiDelayed(() -> {
                                        rs.setGestureOverlayInteractive(true);
                                        postToUiDelayed(resumeRecording::run, intervalMs);
                                    }, 80)
                                );
                            }, 250);
                        }, 80);
                    });
                }
            } catch (Exception e) {
                restoreViews.run();
                Log.e(TAG, "启动录制失败", e);
                Toast.makeText(this, "启动录制失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, 220);
    }

    private GestureOverlayView.GestureNode readGestureFileByName(String fileName) {
        if (TextUtils.isEmpty(fileName)) return null;
        File file = resolveTaskGestureFile(normalizeGestureFileName(fileName));
        if (file == null) return null;
        return readGestureNodeFromFile(file);
    }

    private void playGestureFileByName(View dialogView, String fileName, android.widget.TextView statusView) {
        if (TextUtils.isEmpty(fileName)) {
            Toast.makeText(this, "请先选择手势文件", Toast.LENGTH_SHORT).show();
            return;
        }
        String normalized = normalizeGestureFileName(fileName);
        File file = resolveTaskGestureFile(normalized);
        if (file == null) {
            Toast.makeText(this, "手势文件不存在: " + normalized, Toast.LENGTH_SHORT).show();
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
            return;
        }
        if (statusView != null) statusView.setText("回放中...");
        Runnable restoreViews = hideViewsForCapture(dialogView, projectPanelView);
        postToUiDelayed(() -> {
            svc.showGestureTrail(node);
            svc.replayGesture(node, success -> {
                postToUi(() -> {
                    restoreViews.run();
                    if (statusView != null) statusView.setText(success ? "回放完成: " + normalized : "回放取消");
                    Toast.makeText(this, success ? "回放完成" : "回放取消", Toast.LENGTH_SHORT).show();
                });
            });
        }, 180);
    }

    private void playFullPipelineSequence(View dialogView, org.json.JSONArray pipeline, android.widget.TextView statusView) {
        if (pipeline == null || pipeline.length() == 0) {
            Toast.makeText(this, "流水线为空", Toast.LENGTH_SHORT).show();
            return;
        }
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) {
            Toast.makeText(this, "无障碍服务未连接", Toast.LENGTH_SHORT).show();
            return;
        }
        if (statusView != null) statusView.setText("回放中...");
        Runnable restoreViews = hideViewsForCapture(dialogView, projectPanelView);
        postToUiDelayed(() -> {
            ExecutorService exec = Executors.newSingleThreadExecutor();
            exec.submit(() -> {
                for (int i = 0; i < pipeline.length(); i++) {
                    org.json.JSONObject step;
                    try { step = pipeline.getJSONObject(i); } catch (Exception e) { continue; }
                    String file = step.optString(MetaOperation.GESTURE_STEP_FILE, "");
                    long delayMs = step.optLong(MetaOperation.GESTURE_STEP_DELAY_MS, 0);
                    if (TextUtils.isEmpty(file)) continue;

                    File gestureFile = resolveTaskGestureFile(normalizeGestureFileName(file));
                    if (gestureFile == null) { Log.w(TAG, "Pipeline 回放：文件不存在 " + file); continue; }
                    GestureOverlayView.GestureNode node = readGestureNodeFromFile(gestureFile);
                    if (node == null || node.strokes == null || node.strokes.isEmpty()) { Log.w(TAG, "Pipeline 回放：数据无效 " + file); continue; }

                    final int stepNum = i + 1;
                    final int total = pipeline.length();
                    postToUi(() -> { if (statusView != null) statusView.setText("回放步骤 " + stepNum + "/" + total); });
                    svc.showGestureTrail(node);

                    final boolean[] done = {false};
                    final Object lock = new Object();
                    synchronized (lock) {
                        svc.replayGestureWithRetry(node,
                            () -> { synchronized (lock) { done[0] = true; lock.notifyAll(); } },
                            () -> { synchronized (lock) { done[0] = true; lock.notifyAll(); } },
                            1);
                        try { if (!done[0]) lock.wait(30000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    }

                    if (delayMs > 0 && i < pipeline.length() - 1) {
                        try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    }
                }
                postToUi(() -> {
                    restoreViews.run();
                    if (statusView != null) statusView.setText("流水线回放完成");
                    Toast.makeText(this, "流水线回放完成", Toast.LENGTH_SHORT).show();
                });
            });
            exec.shutdown();
        }, 180);
    }

    // ─── End Pipeline Gesture Methods ───────────────────────────────────────────

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
            Bitmap bitmap = BitmapManager.getInstance().loadThumbnail(
                    file.getAbsolutePath(),
                    dp(120),
                    dp(84));
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

    @Override
    public Runnable hideViewsForCapture(View... viewsToHide) {
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

    /** 多选模板库：只显示当前 CAPTURE_SCALE 对应目录的模板，进入时已选 currentSelected，确认后回调。 */
    private void showTemplateMultiSelectDialog(
            java.util.List<String> currentSelected,
            OperationDialogFactory.MatchMapHelper.OnMultiSelectConfirmed callback) {

        String scaleDirName = getCurrentScaleDirName();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_template_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        applyTemplateLibraryDialogViewport(dialogLp);
        wm.addView(dialogView, dialogLp);

        ((TextView) dialogView.findViewById(R.id.tv_library_title)).setText(
                "选择模板（可多选）[" + scaleDirName + "]");

        androidx.recyclerview.widget.RecyclerView rv = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch = dialogView.findViewById(R.id.edt_library_search);
        View manageActions = dialogView.findViewById(R.id.ly_library_manage_actions);
        View selectActions = dialogView.findViewById(R.id.ly_library_select_actions);
        View lyBreadcrumb  = dialogView.findViewById(R.id.ly_breadcrumb);
        TextView btnCancel  = dialogView.findViewById(R.id.btn_library_cancel);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_library_confirm);

        if (manageActions != null) manageActions.setVisibility(View.GONE);
        if (selectActions != null) selectActions.setVisibility(View.VISIBLE);
        if (lyBreadcrumb != null) lyBreadcrumb.setVisibility(View.GONE);

        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        // 传 null listener — 点击单项由批量选择控制
        com.auto.master.floatwin.adapter.TemplateLibraryAdapter adapter =
                new com.auto.master.floatwin.adapter.TemplateLibraryAdapter(
                        getCurrentTaskTemplateLibraryItemsInDir(scaleDirName), null);
        rv.setAdapter(adapter);
        adapter.setBatchMode(true);
        if (currentSelected != null) {
            for (String name : currentSelected) adapter.selectItem(name);
        }

        Runnable updateConfirmBtn = () -> {
            int cnt = adapter.getSelectedCount();
            if (btnConfirm != null) btnConfirm.setText(cnt == 0 ? "确定" : "确定(" + cnt + ")");
        };
        adapter.setSelectionChangedListener(count -> updateConfirmBtn.run());
        updateConfirmBtn.run();

        dialogView.findViewById(R.id.btn_library_close).setOnClickListener(v -> safeRemoveView(dialogView));
        if (btnCancel != null) btnCancel.setOnClickListener(v -> safeRemoveView(dialogView));
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

    /** 从模板 manifest 导入 bbox：只显示当前 CAPTURE_SCALE 目录的模板，选中后将 bbox 写入 edtBbox。 */
    private void importBboxFromTemplate(View mainDialogView, EditText edtBbox) {
        String scaleDirName = getCurrentScaleDirName();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_template_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        applyTemplateLibraryDialogViewport(dialogLp);
        wm.addView(dialogView, dialogLp);

        ((TextView) dialogView.findViewById(R.id.tv_library_title)).setText(
                "选择模板以导入其区域 [" + scaleDirName + "]");

        androidx.recyclerview.widget.RecyclerView rv = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch = dialogView.findViewById(R.id.edt_library_search);
        View manageActions = dialogView.findViewById(R.id.ly_library_manage_actions);
        View selectActions = dialogView.findViewById(R.id.ly_library_select_actions);
        View lyBreadcrumb  = dialogView.findViewById(R.id.ly_breadcrumb);

        if (manageActions != null) manageActions.setVisibility(View.GONE);
        if (selectActions != null) selectActions.setVisibility(View.GONE);
        if (lyBreadcrumb != null) lyBreadcrumb.setVisibility(View.GONE);

        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));
        com.auto.master.floatwin.adapter.TemplateLibraryAdapter adapter =
                new com.auto.master.floatwin.adapter.TemplateLibraryAdapter(
                        getCurrentTaskTemplateLibraryItemsInDir(scaleDirName), item -> {
                            if (item == null || item.isFolder) return;
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
        // 2. 读 scale-specific manifest 文件
        try {
            java.io.File imgDir = new java.io.File(currentTaskDir, "img");
            // 优先当前倍率的目录，其次 legacy 平铺目录
            java.io.File mf = new java.io.File(new java.io.File(imgDir, getCurrentScaleDirName()), "manifest.json");
            if (!mf.exists()) {
                mf = new java.io.File(imgDir, "manifest.json");
            }
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

        runAfterScreenCaptureReady("制作模板", () -> postToUiDelayed(() -> {
            long startedAt = System.currentTimeMillis();
            boolean started = launchTemplateCapture(normalizedName);
            if (!started) {
                CropRegionOperationHandler.clearTemplateCaptureEventListener(null);
                restoreViews.run();
                return;
            }
            waitAndRefreshTemplatePreview(dialogView, edtTemplateFile, startedAt, restoreViews);
        }, CAPTURE_OVERLAY_LAUNCH_DELAY_MS), () -> {
            CropRegionOperationHandler.clearTemplateCaptureEventListener(null);
            restoreViews.run();
        });
    }

    private void waitAndRefreshTemplatePreview(View dialogView,
                                               AutoCompleteTextView templateInput,
                                               long startedAt,
                                               Runnable onCaptureFinished) {
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
                    postToUiDelayed(this, 300);
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
        postToUiDelayed(poll, 350);
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
        if (!hasNodeSessionBinding(startOperation)) {
            Toast.makeText(this, "当前节点未绑定会话，请先在节点菜单绑定", Toast.LENGTH_SHORT).show();
            return null;
        }

        List<OperationItem> selectedTaskOperations = buildOperationItemsFromTask(selectedTask);

        OperationContext ctx = new OperationContext();
        ctx.anchorProject = project;
        ctx.runtimeLogSink = line -> {
            String sessionId = ScriptRunner.getCurrentSessionId();
            if (isSelectedScriptSession(sessionId)) {
                appendRunLog(line);
            }
        };

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

    private void syncTaskMemoryCaches(@NonNull File projectDir, @NonNull File taskDir, @NonNull Task task) {
        List<File> taskItems = readTaskItems(projectDir);
        String projectKey = buildFileCacheKey(projectDir);
        taskItemsMemoryCache.put(projectKey, new ArrayList<>(taskItems));
        taskItemsMemoryVersions.put(projectKey, projectDir.lastModified());

        List<OperationItem> operationItems = readOperationItemsDirect(taskDir);
        if (operationItems.isEmpty()) {
            operationItems = buildOperationItemsFromTask(task);
        }
        File opFile = new File(taskDir, "operations.json");
        long opVersion = opFile.exists() ? opFile.lastModified() : Long.MIN_VALUE;
        String taskKey = buildFileCacheKey(taskDir);
        operationItemsMemoryCache.put(taskKey, new ArrayList<>(operationItems));
        operationItemsMemoryVersions.put(taskKey, opVersion);
        if (isSameFile(operationListCacheTaskDir, taskDir)) {
            operationListCacheVersion = opVersion;
            operationListCache.clear();
            operationListCache.addAll(operationItems);
        }
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
                    opIndex++, extractDelayDurationMs(operation), extractDelayShowCountdown(operation),
                    extractNodePreDelayMs(operation), extractNodePreDelayMinMs(operation),
                    extractNodePreDelayMaxMs(operation), extractNodePreDelayRandom(operation),
                    extractTemplatePreviewNames(operation)));
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
                        public void onConfigUi(OperationItem item) {
                            showConfigUiDesignerDialog(item);
                        }

                        @Override
                        public void onFloatButton(OperationItem item) {
                            showNodeFloatBtnConfig(item);
                        }

                        @Override
                    public void onNodePreDelay(OperationItem item) {
                        showNodePreDelayDialog(item);
                    }

                    @Override
                    public void onBindSession(OperationItem item) {
                        showNodeSessionBindingDialog(item);
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
                        // scale-aware check: template may live in a scale subdir
                        if (resolveTaskTemplateFile(fileName) == null) {
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
        executionDialogHelper.showPrecheckDialog(result, continueAction);
    }

    private void showRunModeMenu(View anchor, ExecutionDialogHelper.OnRunModeSelectedListener listener) {
        executionDialogHelper.showRunModeMenu(anchor, listener);
    }

    private void showNodeSessionBindingDialog(@Nullable OperationItem item) {
        if (item == null || TextUtils.isEmpty(item.id)) {
            Toast.makeText(this, "节点无效，无法绑定会话", Toast.LENGTH_SHORT).show();
            return;
        }
        if (wm == null) {
            return;
        }
        getOperationJsonAsync(item.id, operationJson -> {
            if (TextUtils.isEmpty(operationJson)) {
                Toast.makeText(this, "未找到节点数据", Toast.LENGTH_SHORT).show();
                return;
            }
            String currentSessionId = "";
            String currentSessionName = "";
            try {
                JSONObject operation = new JSONObject(operationJson);
                JSONObject inputMap = operation.optJSONObject("inputMap");
                if (inputMap != null) {
                    currentSessionId = inputMap.optString(MetaOperation.NODE_BOUND_SESSION_ID, "");
                    currentSessionName = inputMap.optString(MetaOperation.NODE_BOUND_SESSION_NAME, "");
                }
            } catch (Exception e) {
                Log.w(TAG, "parse node session binding failed", e);
            }

            LinearLayout dialog = buildSessionDialogBase("绑定会话", this::removeRunSessionPickerDialog);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(0, dp(8), 0, 0);

            String currentText = TextUtils.isEmpty(currentSessionName)
                    ? "当前未绑定"
                    : "当前绑定: " + currentSessionName;
            TextView current = buildSessionRowText(currentText, false);
            content.addView(current);

            EditText nameInput = buildSessionNameInput("输入会话名");
            if (!TextUtils.isEmpty(currentSessionName)) {
                nameInput.setText(currentSessionName);
            }
            content.addView(nameInput);

            final String baseOperationJson = operationJson;
            TextView createAndBind = buildSessionDialogAction("创建/更新绑定");
            createAndBind.setOnClickListener(v -> {
                String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                if (TextUtils.isEmpty(name)) {
                    Toast.makeText(this, "请输入会话名", Toast.LENGTH_SHORT).show();
                    return;
                }
                ScriptSessionSnapshot existing = findSessionSnapshotByName(name);
                String sessionId = existing == null ? ScriptRunner.createSession(name) : existing.sessionId;
                persistNodeSessionBinding(item.id, baseOperationJson, sessionId, name);
                removeRunSessionPickerDialog();
            });
            content.addView(createAndBind);

            for (ScriptSessionSnapshot snapshot : ScriptRunner.listSessionSnapshots()) {
                if (snapshot == null) {
                    continue;
                }
                TextView row = buildSessionRowText(formatSessionRow(snapshot),
                        TextUtils.equals(snapshot.sessionId, currentSessionId));
                row.setOnClickListener(v -> {
                    persistNodeSessionBinding(item.id, baseOperationJson, snapshot.sessionId, snapshot.sessionName);
                    removeRunSessionPickerDialog();
                });
                content.addView(row);
            }

            TextView clear = buildSessionDialogAction("清除绑定");
            clear.setTextColor(0xFFFFCDD2);
            clear.setOnClickListener(v -> {
                persistNodeSessionBinding(item.id, baseOperationJson, "", "");
                removeRunSessionPickerDialog();
            });
            content.addView(clear);

            dialog.addView(content, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView resizeHandle = new TextView(this);
            resizeHandle.setText("↘");
            resizeHandle.setTextColor(0xFFCBD5E1);
            resizeHandle.setTextSize(16);
            resizeHandle.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            resizeHandle.setPadding(0, dp(2), dp(2), 0);
            dialog.addView(resizeHandle, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(24)));
            addRunSessionPickerDialogView(dialog);
        });
    }

    private void persistNodeSessionBinding(String operationId,
                                           String operationJson,
                                           @Nullable String sessionId,
                                           @Nullable String sessionName) {
        try {
            JSONObject operation = new JSONObject(operationJson);
            JSONObject inputMap = operation.optJSONObject("inputMap");
            if (inputMap == null) {
                inputMap = new JSONObject();
                operation.put("inputMap", inputMap);
            }
            if (TextUtils.isEmpty(sessionId) && TextUtils.isEmpty(sessionName)) {
                inputMap.remove(MetaOperation.NODE_BOUND_SESSION_ID);
                inputMap.remove(MetaOperation.NODE_BOUND_SESSION_NAME);
            } else {
                inputMap.put(MetaOperation.NODE_BOUND_SESSION_ID, nullToEmpty(sessionId));
                inputMap.put(MetaOperation.NODE_BOUND_SESSION_NAME, nullToEmpty(sessionName));
            }
            if (saveOperationJson(operationId, operation.toString(2), currentOperationAdapter)) {
                Toast.makeText(this,
                        TextUtils.isEmpty(sessionName) ? "已清除会话绑定" : "已绑定会话: " + sessionName,
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.w(TAG, "save node session binding failed", e);
            Toast.makeText(this, "保存会话绑定失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    private ScriptSessionSnapshot findSessionSnapshotByName(String sessionName) {
        if (TextUtils.isEmpty(sessionName)) {
            return null;
        }
        for (ScriptSessionSnapshot snapshot : ScriptRunner.listSessionSnapshots()) {
            if (snapshot != null && TextUtils.equals(sessionName, snapshot.sessionName)) {
                return snapshot;
            }
        }
        return null;
    }

    private boolean hasNodeSessionBinding(@Nullable MetaOperation operation) {
        Map<String, Object> inputMap = operation == null ? null : operation.getInputMap();
        return !TextUtils.isEmpty(getInputString(inputMap, MetaOperation.NODE_BOUND_SESSION_ID))
                || !TextUtils.isEmpty(getInputString(inputMap, MetaOperation.NODE_BOUND_SESSION_NAME));
    }

    @Nullable
    private String resolveBoundSessionForStartOperation(@Nullable MetaOperation operation, boolean showToast) {
        Map<String, Object> inputMap = operation == null ? null : operation.getInputMap();
        String boundSessionId = getInputString(inputMap, MetaOperation.NODE_BOUND_SESSION_ID);
        String boundSessionName = getInputString(inputMap, MetaOperation.NODE_BOUND_SESSION_NAME);
        if (TextUtils.isEmpty(boundSessionId) && TextUtils.isEmpty(boundSessionName)) {
            if (showToast) {
                Toast.makeText(this, "当前节点未绑定会话，请先在节点菜单绑定", Toast.LENGTH_SHORT).show();
            }
            return null;
        }

        if (!TextUtils.isEmpty(boundSessionId)) {
            ScriptSessionSnapshot snapshot = ScriptRunner.getSessionSnapshot(boundSessionId);
            if (snapshot != null) {
                if (snapshot.state == ScriptSession.State.IDLE) {
                    return snapshot.sessionId;
                }
                if (showToast) {
                    Toast.makeText(this, "绑定会话正在运行或暂停: " + snapshot.sessionName, Toast.LENGTH_SHORT).show();
                }
                return null;
            }
        }

        if (!TextUtils.isEmpty(boundSessionName)) {
            ScriptSessionSnapshot namedSnapshot = findSessionSnapshotByName(boundSessionName);
            if (namedSnapshot != null) {
                if (namedSnapshot.state == ScriptSession.State.IDLE) {
                    return namedSnapshot.sessionId;
                }
                if (showToast) {
                    Toast.makeText(this, "绑定会话正在运行或暂停: " + namedSnapshot.sessionName, Toast.LENGTH_SHORT).show();
                }
                return null;
            }
            return ScriptRunner.createSession(boundSessionName);
        }

        if (showToast) {
            Toast.makeText(this, "绑定会话不存在，请重新绑定", Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    private String getInputString(@Nullable Map<String, Object> inputMap, String key) {
        if (inputMap == null || TextUtils.isEmpty(key)) {
            return "";
        }
        Object raw = inputMap.get(key);
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private void startOperationWithMode(MetaOperation startOperation,
                                        OperationContext ctx,
                                        String projectName,
                                        String selectedTaskName,
                                        List<OperationItem> selectedTaskOperations,
                                        boolean openProjectPanelNow) {
        startOperationWithMode(startOperation, ctx, projectName, selectedTaskName, selectedTaskOperations, openProjectPanelNow, null);
    }

    private void startOperationWithMode(MetaOperation startOperation,
                                        OperationContext ctx,
                                        String projectName,
                                        String selectedTaskName,
                                        List<OperationItem> selectedTaskOperations,
                                        boolean openProjectPanelNow,
                                        @Nullable String scriptSessionId) {
        startOperationWithMode(
                startOperation,
                ctx,
                projectName,
                selectedTaskName,
                selectedTaskOperations,
                openProjectPanelNow
                        ? ExecutionDialogHelper.RunMode.WITH_PANEL
                        : ExecutionDialogHelper.RunMode.BACKGROUND,
                scriptSessionId);
    }

    private void startOperationWithMode(MetaOperation startOperation,
                                        OperationContext ctx,
                                        String projectName,
                                        String selectedTaskName,
                                        List<OperationItem> selectedTaskOperations,
                                        ExecutionDialogHelper.RunMode runMode) {
        startOperationWithMode(startOperation, ctx, projectName, selectedTaskName, selectedTaskOperations, runMode, null);
    }

    private void startOperationWithMode(MetaOperation startOperation,
                                        OperationContext ctx,
                                        String projectName,
                                        String selectedTaskName,
                                        List<OperationItem> selectedTaskOperations,
                                        ExecutionDialogHelper.RunMode runMode,
                                        @Nullable String scriptSessionId) {
        String resolvedScriptSessionId = TextUtils.isEmpty(scriptSessionId)
                ? resolveBoundSessionForStartOperation(startOperation, true)
                : scriptSessionId;
        if (TextUtils.isEmpty(resolvedScriptSessionId)) {
            return;
        }
        selectedScriptSessionId = resolvedScriptSessionId;
        ScriptRunner.setActiveSessionId(resolvedScriptSessionId);
        attachRuntimeLogSink(ctx, resolvedScriptSessionId);
        boolean focusMode = runMode == ExecutionDialogHelper.RunMode.FOCUS;
        boolean openProjectPanelNow = runMode == ExecutionDialogHelper.RunMode.WITH_PANEL;
        focusRunMode = focusMode;
        runningOperations.clear();
        runningOperations.addAll(selectedTaskOperations);
        totalOperationCount = selectedTaskOperations.size();
        currentOperationIndex = 0;
        currentRunningProject = projectName;
        currentRunningTask = selectedTaskName;
        isPaused = false;
        ctx.suppressVisualFeedback = focusMode;
        beginRunLog(projectName, selectedTaskName, startOperation, !focusMode);

        try {
            if (!focusMode) {
                Toast.makeText(this, "开始运行: " + startOperation.getName(), Toast.LENGTH_SHORT).show();
                Log.d(TAG, "开始运行 operation: " + startOperation.getName() + " (" + startOperation.getId() + ")");
            }

            ScriptRunner.setExecutionListener(this);

            // 动态延时倒计时回调：Handler 在 sleep 前通知，Service 在主线程启动覆盖层
            ctx.delayCountdownNotifier = focusMode
                    ? null
                    : (operationId, durationMs, showCountdown) -> {
                        if (!showCountdown || durationMs <= 0) return;
                        uiHandler.post(() -> {
                            OperationItem opItem = findRunningItem(operationId);
                            if (opItem != null) {
                                opItem.delayDurationMs = durationMs;
                                opItem.delayShowCountdown = true;
                                maybeStartDelayProgress(opItem);
                            }
                        });
                    };

            ScriptExecuteContext scriptExecuteContext = new ScriptExecuteContext();
            scriptExecuteContext.tobeHandledOperation = startOperation;
            scriptExecuteContext.sharedContext = ctx;
            scriptExecuteContext.running = true;
            scriptExecuteContext.sessionId = resolvedScriptSessionId;
            selectedScriptSessionId = ScriptRunner.runOperation(scriptExecuteContext, resolvedScriptSessionId);
            ScriptRunner.setActiveSessionId(selectedScriptSessionId);
            refreshScriptSessionDialogIfVisible();

            transitionAfterRunStart(openProjectPanelNow, focusMode);

            if (!focusMode) {
                Log.d(TAG, "ScriptRunner.runOperation 已调用 session=" + selectedScriptSessionId);
            }
        } catch (Exception e) {
            Log.d("检验检验检验检验检验检验", "setupProjectPanel: " + e.toString());
            Toast.makeText(this, "运行失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (!focusMode) {
                recordFailureReason("startup_exception");
                appendRunLog("[error] 启动失败: " + e.getMessage());
                persistCurrentRunLog();
                CrashLogger.logHandledException(this, "startOperationWithMode", e);
                CrashLogger.finishRunSession(this, "startup_exception");
            }
            ScriptRunner.clearExecutionListener();
            focusRunMode = false;
        }
    }

    private void acquireScriptWakeLock() {
        try {
            if (scriptWakeLock == null) {
                android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    scriptWakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "AutoFlow:ScriptRun");
                    scriptWakeLock.setReferenceCounted(false);
                }
            }
            if (scriptWakeLock != null && !scriptWakeLock.isHeld()) {
                // 最多持锁 2 小时，防止永久持锁
                scriptWakeLock.acquire(2 * 60 * 60 * 1000L);
                Log.d(TAG, "ScriptWakeLock acquired");
            }
        } catch (Exception e) {
            Log.w(TAG, "acquireScriptWakeLock failed", e);
        }
    }

    private void releaseScriptWakeLock() {
        try {
            if (scriptWakeLock != null && scriptWakeLock.isHeld()) {
                scriptWakeLock.release();
                Log.d(TAG, "ScriptWakeLock released");
            }
        } catch (Exception e) {
            Log.w(TAG, "releaseScriptWakeLock failed", e);
        }
    }

    private void beginRunLog(String projectName, String taskName, MetaOperation startOperation, boolean enableLogging) {
        currentRunStartMs = System.currentTimeMillis();
        acquireScriptWakeLock();
        currentRunLogs.clear();
        if (runtimeLogPanelView != null) {
            runtimeLogPanelView.setLines(currentRunLogs);
        }
        opStartTimeMs.clear();
        opDurationsMs.clear();
        opFailureReasons.clear();
        opSuccessCount = 0;
        opFailureCount = 0;
        latestFailureReason = "-";
        if (!enableLogging) {
            return;
        }
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

    private static final SimpleDateFormat RUN_LOG_FMT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private static final int MAX_RUN_LOG_ENTRIES = 2000;

    private void toggleRuntimeLogPanel() {
        if (runtimeLogPanelView == null) {
            showRuntimeLogPanel();
        } else {
            removeRuntimeLogPanel();
        }
    }

    private void showRuntimeLogPanel() {
        if (wm == null || runtimeLogPanelView != null) {
            return;
        }
        runtimeLogPanelView = new RuntimeLogPanelView(this);
        runtimeLogPanelView.setOnClose(this::removeRuntimeLogPanel);
        runtimeLogPanelView.setOnClear(() -> {
            synchronized (currentRunLogs) {
                currentRunLogs.clear();
            }
            if (runtimeLogPanelView != null) {
                runtimeLogPanelView.setLines(currentRunLogs);
            }
        });
        runtimeLogPanelView.setLines(currentRunLogs);

        int[] screen = getScreenSizePx();
        int width = Math.max(dp(260), (int) (screen[0] * 0.62f));
        int height = Math.max(dp(180), (int) (screen[1] * 0.34f));
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        runtimeLogPanelLp = new WindowManager.LayoutParams(
                width,
                height,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        runtimeLogPanelLp.gravity = Gravity.TOP | Gravity.START;
        runtimeLogPanelLp.x = Math.max(dp(8), (screen[0] - width) / 2);
        runtimeLogPanelLp.y = Math.max(dp(32), (screen[1] - height) / 3);

        bindRuntimeLogPanelGestures(runtimeLogPanelView, runtimeLogPanelLp);
        try {
            wm.addView(runtimeLogPanelView, runtimeLogPanelLp);
        } catch (Exception e) {
            Log.w(TAG, "show runtime log panel failed", e);
            runtimeLogPanelView = null;
            runtimeLogPanelLp = null;
        }
    }

    private void removeRuntimeLogPanel() {
        if (runtimeLogPanelView == null) {
            return;
        }
        safeRemoveView(runtimeLogPanelView);
        runtimeLogPanelView = null;
        runtimeLogPanelLp = null;
    }

    private void bindRuntimeLogPanelGestures(RuntimeLogPanelView panel, WindowManager.LayoutParams lp) {
        if (panel == null || lp == null) {
            return;
        }
        panel.getHeaderView().setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downRawX;
            private float downRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int[] screen = getScreenSizePx();
                        lp.x = Math.max(0, Math.min(startX + (int) (event.getRawX() - downRawX),
                                Math.max(0, screen[0] - lp.width)));
                        lp.y = Math.max(0, Math.min(startY + (int) (event.getRawY() - downRawY),
                                Math.max(0, screen[1] - lp.height)));
                        try {
                            wm.updateViewLayout(panel, lp);
                        } catch (Exception ignored) {
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
        panel.getResizeHandle().setOnTouchListener(new View.OnTouchListener() {
            private int startWidth;
            private int startHeight;
            private float downRawX;
            private float downRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startWidth = lp.width;
                        startHeight = lp.height;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int[] screen = getScreenSizePx();
                        lp.width = Math.max(dp(220), Math.min(startWidth + (int) (event.getRawX() - downRawX),
                                Math.max(dp(220), screen[0] - lp.x)));
                        lp.height = Math.max(dp(140), Math.min(startHeight + (int) (event.getRawY() - downRawY),
                                Math.max(dp(140), screen[1] - lp.y)));
                        try {
                            wm.updateViewLayout(panel, lp);
                        } catch (Exception ignored) {
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private boolean shouldEmitRuntimeLog() {
        SystemRuntimeConfig cfg = SystemRuntimeConfig.load(this);
        return cfg.runtimeLogEnabled && !focusRunMode;
    }

    private void appendRunLog(String line) {
        if (TextUtils.isEmpty(line) || !shouldEmitRuntimeLog()) {
            return;
        }
        String ts = RUN_LOG_FMT.format(new Date());
        String logLine = ts + "  " + line;
        synchronized (currentRunLogs) {
            if (currentRunLogs.size() >= MAX_RUN_LOG_ENTRIES) {
                currentRunLogs.subList(0, MAX_RUN_LOG_ENTRIES / 2).clear();
            }
            currentRunLogs.add(logLine);
        }
        uiHandler.post(() -> {
            if (runtimeLogPanelView != null) {
                runtimeLogPanelView.appendLine(logLine);
            }
        });
    }

    private void showScriptSessionDialog() {
        if (wm == null) {
            return;
        }
        if (scriptSessionPanelView != null) {
            renderScriptSessionDialogContent();
            return;
        }
        LinearLayout dialog = buildSessionDialogBase("会话管理");
        LinearLayout header = (LinearLayout) dialog.getChildAt(0);
        TextView create = buildSessionDialogAction("创建");
        create.setOnClickListener(v -> {
            String name = scriptSessionCreateNameInput == null || scriptSessionCreateNameInput.getText() == null
                    ? ""
                    : scriptSessionCreateNameInput.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "请输入会话名", Toast.LENGTH_SHORT).show();
                return;
            }
            ScriptRunner.createSession(name);
            scriptSessionCreateNameInput.setText("");
            renderScriptSessionDialogContent();
        });
        header.addView(create, Math.max(1, header.getChildCount() - 1));

        scriptSessionCreateNameInput = buildSessionNameInput("输入会话名");
        dialog.addView(scriptSessionCreateNameInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        scriptSessionListContentView = new LinearLayout(this);
        scriptSessionListContentView.setOrientation(LinearLayout.VERTICAL);
        scriptSessionListContentView.setPadding(0, dp(4), 0, 0);
        scrollView.addView(scriptSessionListContentView, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        dialog.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        TextView resizeHandle = new TextView(this);
        resizeHandle.setText("↘");
        resizeHandle.setTextColor(0xFFCBD5E1);
        resizeHandle.setTextSize(16);
        resizeHandle.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        resizeHandle.setPadding(0, dp(2), dp(2), 0);
        dialog.addView(resizeHandle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(24)));

        renderScriptSessionDialogContent();
        addSessionDialogView(dialog);
    }

    private void renderScriptSessionDialogContent() {
        if (scriptSessionListContentView == null) {
            return;
        }
        scriptSessionListContentView.removeAllViews();
        List<ScriptSessionSnapshot> sessions = new ArrayList<>();
        for (ScriptSessionSnapshot snapshot : ScriptRunner.listSessionSnapshots()) {
            if (isVisibleManagedSession(snapshot)) {
                sessions.add(snapshot);
            }
        }
        if (sessions.isEmpty()) {
            TextView empty = buildSessionRowText("暂无会话，点击右上角创建", false);
            scriptSessionListContentView.addView(empty);
        } else {
            for (ScriptSessionSnapshot snapshot : sessions) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView detail = buildSessionRowText(formatSessionRow(snapshot),
                        TextUtils.equals(snapshot.sessionId, selectedScriptSessionId));
                detail.setOnClickListener(v -> {
                    bindScriptSession(snapshot.sessionId);
                    Toast.makeText(this, "已切换会话", Toast.LENGTH_SHORT).show();
                    renderScriptSessionDialogContent();
                });
                row.addView(detail, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView closeSession = buildSessionDialogAction("关闭");
                closeSession.setTextColor(0xFFFFCDD2);
                closeSession.setOnClickListener(v -> {
                    ScriptRunner.closeSession(snapshot.sessionId);
                    if (TextUtils.equals(snapshot.sessionId, selectedScriptSessionId)) {
                        clearSelectedSessionUiState();
                    }
                    renderScriptSessionDialogContent();
                });
                row.addView(closeSession);
                scriptSessionListContentView.addView(row);
            }
        }
    }

    private void attachRuntimeLogSink(@Nullable OperationContext ctx, @Nullable String sessionId) {
        if (ctx == null) {
            return;
        }
        ctx.runtimeLogSink = line -> {
            if (TextUtils.isEmpty(sessionId) || TextUtils.equals(sessionId, selectedScriptSessionId)) {
                appendRunLog(line);
            }
        };
    }

    private void refreshScriptSessionDialog() {
        renderScriptSessionDialogContent();
    }

    private void refreshScriptSessionDialogIfVisible() {
        if (scriptSessionPanelView != null) {
            refreshScriptSessionDialog();
        }
    }

    private void removeScriptSessionDialog() {
        if (scriptSessionPanelView == null) {
            return;
        }
        safeRemoveView(scriptSessionPanelView);
        scriptSessionPanelView = null;
        scriptSessionListContentView = null;
        scriptSessionCreateNameInput = null;
        scriptSessionPanelLp = null;
    }

    private LinearLayout buildSessionDialogBase(String titleText) {
        return buildSessionDialogBase(titleText, this::removeScriptSessionDialog);
    }

    private LinearLayout buildSessionDialogBase(String titleText, Runnable closeAction) {
        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21F2937);
        bg.setStroke(dp(1), 0xAA38BDF8);
        bg.setCornerRadius(dp(8));
        dialog.setBackground(bg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = buildSessionDialogAction("关闭");
        close.setOnClickListener(v -> {
            if (closeAction != null) {
                closeAction.run();
            }
        });
        header.addView(close);
        dialog.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return dialog;
    }

    private EditText buildSessionNameInput(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setHintTextColor(0xFF94A3B8);
        input.setTextColor(Color.WHITE);
        input.setTextSize(12);
        input.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x332D3748);
        bg.setStroke(dp(1), 0x6638BDF8);
        bg.setCornerRadius(dp(6));
        input.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        input.setLayoutParams(lp);
        return input;
    }

    private void addRunSessionPickerDialogView(LinearLayout dialog) {
        if (runSessionPickerView != null) {
            safeRemoveView(runSessionPickerView);
            runSessionPickerView = null;
        }
        int[] screen = getScreenSizePx();
        int width = Math.max(dp(280), (int) (screen[0] * 0.64f));
        int height = Math.max(dp(220), (int) (screen[1] * 0.32f));
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width,
                height,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.max(dp(8), (screen[0] - width) / 2);
        lp.y = Math.max(dp(32), screen[1] / 4);
        bindSessionPanelGestures(dialog, lp);
        try {
            runSessionPickerView = dialog;
            runSessionPickerLp = lp;
            wm.addView(dialog, lp);
        } catch (Exception e) {
            runSessionPickerView = null;
            runSessionPickerLp = null;
            Log.w(TAG, "show run session picker failed", e);
        }
    }

    private void removeRunSessionPickerDialog() {
        if (runSessionPickerView == null) {
            return;
        }
        safeRemoveView(runSessionPickerView);
        runSessionPickerView = null;
        runSessionPickerLp = null;
    }

    private void addSessionDialogView(LinearLayout dialog) {
        WindowManager.LayoutParams previousLp = scriptSessionPanelLp;
        if (scriptSessionPanelView != null) {
            safeRemoveView(scriptSessionPanelView);
            scriptSessionPanelView = null;
        }
        if (previousLp != null) {
            scriptSessionPanelLp = previousLp;
        }
        int[] screen = getScreenSizePx();
        int width = Math.max(dp(240), (int) (screen[0] * 0.58f));
        int height = Math.max(dp(180), (int) (screen[1] * 0.32f));
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = scriptSessionPanelLp;
        if (lp == null) {
            lp = new WindowManager.LayoutParams(
                    width,
                    height,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = Math.max(dp(8), (screen[0] - width) / 2);
            lp.y = Math.max(dp(32), (screen[1] - height) / 3);
        }
        bindSessionPanelGestures(dialog, lp);
        try {
            scriptSessionPanelView = dialog;
            scriptSessionPanelLp = lp;
            wm.addView(dialog, lp);
        } catch (Exception e) {
            scriptSessionPanelView = null;
            scriptSessionListContentView = null;
            scriptSessionCreateNameInput = null;
            scriptSessionPanelLp = null;
            Log.w(TAG, "show session dialog failed", e);
        }
    }

    private void bindSessionPanelGestures(LinearLayout panel, WindowManager.LayoutParams lp) {
        if (panel == null || lp == null || panel.getChildCount() == 0) {
            return;
        }
        View header = panel.getChildAt(0);
        header.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downRawX;
            private float downRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int[] screen = getScreenSizePx();
                        lp.x = Math.max(0, Math.min(startX + (int) (event.getRawX() - downRawX),
                                Math.max(0, screen[0] - lp.width)));
                        lp.y = Math.max(0, Math.min(startY + (int) (event.getRawY() - downRawY),
                                Math.max(0, screen[1] - lp.height)));
                        try {
                            wm.updateViewLayout(panel, lp);
                        } catch (Exception ignored) {
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });

        View resizeHandle = panel.getChildAt(panel.getChildCount() - 1);
        resizeHandle.setOnTouchListener(new View.OnTouchListener() {
            private int startWidth;
            private int startHeight;
            private float downRawX;
            private float downRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startWidth = lp.width;
                        startHeight = lp.height;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int[] screen = getScreenSizePx();
                        lp.width = Math.max(dp(240), Math.min(startWidth + (int) (event.getRawX() - downRawX),
                                Math.max(dp(240), screen[0] - lp.x)));
                        lp.height = Math.max(dp(180), Math.min(startHeight + (int) (event.getRawY() - downRawY),
                                Math.max(dp(180), screen[1] - lp.y)));
                        try {
                            wm.updateViewLayout(panel, lp);
                        } catch (Exception ignored) {
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private TextView buildSessionDialogAction(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(8), dp(4), dp(8), dp(4));
        return view;
    }

    private boolean isVisibleManagedSession(@Nullable ScriptSessionSnapshot snapshot) {
        if (snapshot == null || snapshot.state == null) {
            return false;
        }
        return snapshot.state == ScriptSession.State.IDLE
                || snapshot.state == ScriptSession.State.QUEUED
                || snapshot.state == ScriptSession.State.RUNNING
                || snapshot.state == ScriptSession.State.PAUSED;
    }

    private TextView buildSessionRowText(String text, boolean selected) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextSize(10);
        view.setPadding(dp(6), dp(4), dp(6), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? 0x6638BDF8 : 0x332D3748);
        bg.setStroke(dp(1), selected ? 0xFF38BDF8 : 0x44556677);
        bg.setCornerRadius(dp(6));
        view.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(3));
        view.setLayoutParams(lp);
        return view;
    }

    private String formatSessionRow(ScriptSessionSnapshot snapshot) {
        String project = TextUtils.isEmpty(snapshot.projectName) ? "-" : snapshot.projectName;
        String task = TextUtils.isEmpty(snapshot.taskName) ? "-" : snapshot.taskName;
        String op = TextUtils.isEmpty(snapshot.operationName) ? snapshot.operationId : snapshot.operationName;
        if (TextUtils.isEmpty(op)) {
            op = snapshot.state == ScriptSession.State.IDLE ? "未绑定" : "-";
        }
        String displayName = TextUtils.isEmpty(snapshot.sessionName) ? "未命名会话" : snapshot.sessionName;
        String location = "-".equals(project) && "-".equals(task) ? "-" : project + "/" + task;
        return getSessionStatusText(snapshot.state)
                + "  " + displayName
                + "  " + location
                + "  节点:" + op;
    }

    private void bindScriptSession(String sessionId) {
        ScriptSessionSnapshot snapshot = ScriptRunner.getSessionSnapshot(sessionId);
        if (snapshot == null) {
            return;
        }
        selectedScriptSessionId = snapshot.sessionId;
        ScriptRunner.setActiveSessionId(snapshot.sessionId);
        boolean activeSession = snapshot.state == ScriptSession.State.QUEUED
                || snapshot.state == ScriptSession.State.RUNNING
                || snapshot.state == ScriptSession.State.PAUSED;
        currentRunningProject = activeSession ? snapshot.projectName : "";
        currentRunningTask = activeSession ? snapshot.taskName : "";
        currentRunningOperationId = activeSession ? snapshot.operationId : "";
        currentRunningOperationName = activeSession ? snapshot.operationName : "";
        currentRunStartMs = snapshot.startTimeMs;
        isPaused = snapshot.state == ScriptSession.State.PAUSED;
        stopDelayProgress();
        hideStepOverlay();
        synchronized (currentRunLogs) {
            currentRunLogs.clear();
            currentRunLogs.addAll(snapshot.logs);
        }
        if (runtimeLogPanelView != null) {
            runtimeLogPanelView.setLines(currentRunLogs);
        }
        updateRunningPanelStatus(getSessionStatusText(snapshot.state), getSessionStatusColor(snapshot.state));
        if (!TextUtils.isEmpty(snapshot.operationId) && activeSession && currentOperationAdapter != null) {
            currentOperationAdapter.setRunningPosition(snapshot.operationId);
        } else if (currentOperationAdapter != null) {
            currentOperationAdapter.clearRunningPosition();
        }
        syncProjectPanelRuntimeUi();
        if (activeSession && !TextUtils.isEmpty(snapshot.operationId)) {
            flowGraphPanelHelper.highlightOperation(snapshot.operationId);
        } else {
            flowGraphPanelHelper.clearHighlight();
        }
    }

    private void clearSelectedSessionUiState() {
        selectedScriptSessionId = "";
        currentRunningProject = "";
        currentRunningTask = "";
        currentRunningOperationId = "";
        currentRunningOperationName = "";
        currentOperationIndex = 0;
        totalOperationCount = 0;
        isPaused = false;
        updateRunningPanelStatus("待机", 0xFF4CAF50);
        synchronized (currentRunLogs) {
            currentRunLogs.clear();
        }
        if (runtimeLogPanelView != null) {
            runtimeLogPanelView.setLines(currentRunLogs);
        }
        if (currentOperationAdapter != null) {
            currentOperationAdapter.clearRunningPosition();
        }
        stopDelayProgress();
        hideStepOverlay();
        flowGraphPanelHelper.clearHighlight();
        syncProjectPanelRuntimeUi();
    }

    private String getSessionStatusText(@Nullable ScriptSession.State state) {
        if (state == null) {
            return "待机";
        }
        switch (state) {
            case IDLE:
                return "空闲";
            case QUEUED:
                return "等待中";
            case RUNNING:
                return "运行中";
            case PAUSED:
                return "已暂停";
            case STOPPING:
                return "停止中";
            case STOPPED:
                return "已停止";
            case COMPLETED:
                return "已完成";
            case FAILED:
                return "运行出错";
            default:
                return "待机";
        }
    }

    private int getSessionStatusColor(@Nullable ScriptSession.State state) {
        if (state == null) {
            return 0xFF4CAF50;
        }
        switch (state) {
            case PAUSED:
            case QUEUED:
            case IDLE:
                return 0xFFFF9800;
            case STOPPING:
            case STOPPED:
            case FAILED:
                return 0xFFF44336;
            case RUNNING:
            case COMPLETED:
            default:
                return 0xFF4CAF50;
        }
    }

    private void persistCurrentRunLog() {
        // Runtime logs are overlay-only and in-memory to avoid IO during script execution.
    }

    private void transitionAfterRunStart(boolean openProjectPanelNow, boolean focusMode) {
        if (!focusMode) {
            dockBallForRunningFromCurrentPosition();
        }
        runtimeTransitionCoordinator.transitionAfterRunStart(openProjectPanelNow, focusMode);
    }

    private void showTemplateLibraryDialog(AutoCompleteTextView templateInput, View ownerDialog) {
        String scaleDirName = getCurrentScaleDirName();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_template_library, null);
        WindowManager.LayoutParams dialogLp = buildDialogLayoutParams(350, true);
        applyTemplateLibraryDialogViewport(dialogLp);
        wm.addView(dialogView, dialogLp);

        TextView tvTitle       = dialogView.findViewById(R.id.tv_library_title);
        TextView btnAdd        = dialogView.findViewById(R.id.btn_library_add);
        RecyclerView rv        = dialogView.findViewById(R.id.rv_library);
        EditText edtSearch     = dialogView.findViewById(R.id.edt_library_search);
        TextView btnBatch      = dialogView.findViewById(R.id.btn_library_batch);
        TextView btnDelete     = dialogView.findViewById(R.id.btn_library_delete);
        View manageActions     = dialogView.findViewById(R.id.ly_library_manage_actions);
        View selectActions     = dialogView.findViewById(R.id.ly_library_select_actions);
        View lyBreadcrumb      = dialogView.findViewById(R.id.ly_breadcrumb);

        if (tvTitle != null) tvTitle.setText("选择模板图片 [" + scaleDirName + "]");
        if (btnAdd != null) btnAdd.setVisibility(View.GONE);
        if (selectActions != null) selectActions.setVisibility(View.GONE);
        if (lyBreadcrumb != null) lyBreadcrumb.setVisibility(View.GONE);
        if (manageActions != null) manageActions.setVisibility(View.VISIBLE);

        rv.setLayoutManager(new GridLayoutManager(this, 3));
        final boolean[] batchMode = {false};
        final TemplateLibraryAdapter[] adapterRef = new TemplateLibraryAdapter[1];

        Runnable updateBatchUi = () -> {
            if (adapterRef[0] == null) return;
            btnDelete.setText("删除(" + adapterRef[0].getSelectedCount() + ")");
            btnBatch.setText(batchMode[0] ? "完成" : "批量");
        };
        Runnable refreshList = () -> {
            adapterRef[0].replaceData(getCurrentTaskTemplateLibraryItemsInDir(scaleDirName));
            updateBatchUi.run();
        };

        TemplateLibraryAdapter adapter = new TemplateLibraryAdapter(
                getCurrentTaskTemplateLibraryItemsInDir(scaleDirName),
                item -> {
                    if (item == null || item.isFolder) return;
                    // 选中模板：填入输入框并关闭
                    templateInput.setText(item.fileName, false);
                    refreshTemplateOptions(templateInput);
                    updateTemplatePreview(ownerDialog.findViewById(R.id.iv_template_preview),
                            ownerDialog.findViewById(R.id.tv_template_preview_tip), item.fileName);
                    renderRecentTemplateStrip(ownerDialog, templateInput);
                    safeRemoveView(dialogView);
                },
                item -> {
                    if (item == null || item.isFolder || TextUtils.isEmpty(item.fileName)) return;
                    if (item.usageCount > 0) {
                        Toast.makeText(this, "模板仍被节点引用，暂时不能删除", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int deleted = deleteTemplateFileInScaleDir(item.fileName, item.scaleDirName);
                    if (deleted <= 0) {
                        Toast.makeText(this, "删除失败，请稍后重试", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String cur = templateInput.getText() == null ? "" : templateInput.getText().toString().trim();
                    if (item.fileName.equals(cur)) templateInput.setText("", false);
                    Toast.makeText(this, "已删除模板 " + item.fileName, Toast.LENGTH_SHORT).show();
                    refreshTemplateCachesForCurrentProject();
                    refreshList.run();
                    refreshTemplateOptions(templateInput);
                    renderRecentTemplateStrip(ownerDialog, templateInput);
                    updateTemplatePreview(ownerDialog.findViewById(R.id.iv_template_preview),
                            ownerDialog.findViewById(R.id.tv_template_preview_tip),
                            templateInput.getText() == null ? "" : templateInput.getText().toString().trim());
                });
        adapterRef[0] = adapter;
        adapter.setDeleteActionEnabled(true);
        rv.setAdapter(adapter);
        adapter.setSelectionChangedListener(count -> updateBatchUi.run());
        btnDelete.setText("删除(0)");
        btnBatch.setText("批量");

        dialogView.findViewById(R.id.btn_library_close).setOnClickListener(v -> safeRemoveView(dialogView));
        btnBatch.setOnClickListener(v -> {
            batchMode[0] = !batchMode[0];
            adapter.setBatchMode(batchMode[0]);
            updateBatchUi.run();
        });
        btnDelete.setOnClickListener(v -> {
            List<TemplateLibraryAdapter.TemplateLibraryItem> selectedItems = adapter.getSelectedItems();
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show();
                return;
            }
            int deleted = 0;
            for (TemplateLibraryAdapter.TemplateLibraryItem sel : selectedItems) {
                deleted += deleteTemplateFileInScaleDir(sel.fileName, sel.scaleDirName);
            }
            int skipped = selectedItems.size() - deleted;
            if (skipped > 0) {
                Toast.makeText(this, "已删 " + deleted + " 张，" + skipped + " 张仍被节点引用", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "已删除 " + deleted + " 张图片", Toast.LENGTH_SHORT).show();
            }
            refreshTemplateCachesForCurrentProject();
            batchMode[0] = false;
            adapter.setBatchMode(false);
            refreshList.run();
            refreshTemplateOptions(templateInput);
            renderRecentTemplateStrip(ownerDialog, templateInput);
            updateTemplatePreview(ownerDialog.findViewById(R.id.iv_template_preview),
                    ownerDialog.findViewById(R.id.tv_template_preview_tip),
                    templateInput.getText() == null ? "" : templateInput.getText().toString().trim());
        });
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.updateFilter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
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
                // Delete from flat dir (legacy scale=1.0)
                File flat = new File(imgDir, name);
                if (flat.exists() && flat.isFile() && flat.delete()) {
                    deleted++;
                }
                File flatMask = new File(imgDir, CaptureScaleHelper.getTemplateMaskFileName(name));
                if (flatMask.exists() && flatMask.isFile()) {
                    flatMask.delete();
                }
                // Delete from all scale subdirs
                for (float scale : CaptureScaleHelper.SUPPORTED_SCALES) {
                    File scaleDir = new File(imgDir, CaptureScaleHelper.getScaleDirName(scale));
                    File scaleFile = new File(scaleDir, name);
                    if (scaleFile.exists() && scaleFile.isFile() && scaleFile.delete()) {
                        deleted++;
                    }
                    File scaleMask = new File(scaleDir, CaptureScaleHelper.getTemplateMaskFileName(name));
                    if (scaleMask.exists() && scaleMask.isFile()) {
                        scaleMask.delete();
                    }
                }
            }

            // Clean from per-scale manifests + legacy flat manifest
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                List<File> manifestDirs = new ArrayList<>();
                manifestDirs.add(imgDir); // legacy flat
                for (float scale : CaptureScaleHelper.SUPPORTED_SCALES) {
                    File sd = new File(imgDir, CaptureScaleHelper.getScaleDirName(scale));
                    if (sd.isDirectory()) manifestDirs.add(sd);
                }
                for (File mdir : manifestDirs) {
                    File manifestFile = new File(mdir, "manifest.json");
                    if (!manifestFile.exists()) continue;
                    try {
                        String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
                        if (TextUtils.isEmpty(content.trim())) continue;
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
                    } catch (Exception ignore) {
                        Log.w(TAG, "删除模板 manifest 失败: " + mdir.getName(), ignore);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "删除模板文件失败", e);
        }
        return deleted;
    }

    @Nullable
    private Bitmap captureFreshScreenBitmap() {
        Bitmap cached = takePendingCleanCaptureBitmap();
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        // 注意：此处不需要 Activity。真正取帧由 ScreenCaptureManager 的 MediaProjection 缓冲区提供，
        // Activity 只在首次初始化 ScreenCaptureManager 时才用到，运行中无需重新获取。
        AutoAccessibilityService autoAccessibilityService = AutoAccessibilityService.get();
        if (autoAccessibilityService == null) {
            return null;
        }
        return ScreenCapture.captureLatestBitmap(null, 360L, 60L);
    }

    private void runAfterScreenCaptureReady(String purpose, Runnable onReady, Runnable onCancelled) {
        if (onReady == null) {
            return;
        }
        clearPendingCleanCaptureBitmap();
        if (ScreenCapture.hasProjectionPermission()) {
            waitForCleanCaptureFrame(onReady, onCancelled, false);
            return;
        }
        Toast.makeText(this,
                TextUtils.isEmpty(purpose) ? "请先授予录屏权限" : purpose + "需要先授予录屏权限",
                Toast.LENGTH_SHORT).show();
        ScreenCapturePermissionActivity.request(this, granted -> postToUi(() -> {
            if (!granted) {
                Toast.makeText(this, "录屏授权已取消，无法继续截图", Toast.LENGTH_SHORT).show();
                clearPendingCleanCaptureBitmap();
                if (onCancelled != null) {
                    onCancelled.run();
                }
                return;
            }
            waitForCleanCaptureFrame(onReady, onCancelled, true);
        }));
    }

    private void waitForCleanCaptureFrame(Runnable onReady,
                                          @Nullable Runnable onCancelled,
                                          boolean afterPermissionDialog) {
        final int startFrameSeq = ScreenCapture.getFrameSequence();
        final long minReadyAt = SystemClock.uptimeMillis()
                + (afterPermissionDialog ? CAPTURE_PERMISSION_SETTLE_DELAY_MS : CAPTURE_UI_SETTLE_DELAY_MS);
        final long deadline = SystemClock.uptimeMillis() + CAPTURE_READY_TIMEOUT_MS;
        panelDataExecutor.execute(() -> {
            boolean ready = false;
            Bitmap readyBitmap = null;
            while (serviceAlive && SystemClock.uptimeMillis() <= deadline) {
                Mat frame = ScreenCapture.waitForLatestFrame(null, 180L, 45L);
                int currentSeq = ScreenCapture.getFrameSequence();
                long now = SystemClock.uptimeMillis();
                if (ScreenCaptureManager.getInstance().isRunning()
                        && currentSeq > startFrameSeq
                        && now >= minReadyAt) {
                    if (frame != null && !frame.empty()) {
                        readyBitmap = OpenCVHelper.getInstance().matToBitmap(frame);
                    }
                    ready = true;
                    break;
                }
                SystemClock.sleep(80L);
            }
            final boolean finalReady = ready;
            final Bitmap finalReadyBitmap = readyBitmap;
            postToUi(() -> {
                if (!serviceAlive) {
                    recycleBitmap(finalReadyBitmap);
                    clearPendingCleanCaptureBitmap();
                    if (onCancelled != null) {
                        onCancelled.run();
                    }
                    return;
                }
                if (finalReady) {
                    setPendingCleanCaptureBitmap(finalReadyBitmap);
                    onReady.run();
                    postToUiDelayed(this::clearPendingCleanCaptureBitmap, 2000L);
                } else {
                    recycleBitmap(finalReadyBitmap);
                    clearPendingCleanCaptureBitmap();
                    Toast.makeText(this, "录屏会话启动失败，请重新授权后再试", Toast.LENGTH_SHORT).show();
                    if (onCancelled != null) {
                        onCancelled.run();
                    }
                }
            });
        });
    }

    private void setPendingCleanCaptureBitmap(@Nullable Bitmap bitmap) {
        synchronized (pendingCleanCaptureLock) {
            recycleBitmap(pendingCleanCaptureBitmap);
            pendingCleanCaptureBitmap = bitmap;
        }
    }

    @Nullable
    private Bitmap takePendingCleanCaptureBitmap() {
        synchronized (pendingCleanCaptureLock) {
            Bitmap bitmap = pendingCleanCaptureBitmap;
            pendingCleanCaptureBitmap = null;
            return bitmap;
        }
    }

    private void clearPendingCleanCaptureBitmap() {
        synchronized (pendingCleanCaptureLock) {
            recycleBitmap(pendingCleanCaptureBitmap);
            pendingCleanCaptureBitmap = null;
        }
    }

    private void recycleBitmap(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private void postToUi(Runnable action) {
        if (action != null) {
            uiHandler.post(action);
        }
    }

    private void postToUiDelayed(Runnable action, long delayMs) {
        if (action != null) {
            uiHandler.postDelayed(action, delayMs);
        }
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
        EditText sampleRatioInput = dialogView.findViewById(R.id.edt_match_sample_ratio);
        EditText scaleFactorInput = dialogView.findViewById(R.id.edt_scale_factor);
        AutoCompleteTextView fallbackInput = dialogView.findViewById(R.id.edt_fallback_operation);
        EditText preDelayInput = dialogView.findViewById(R.id.edt_match_pre_delay);
        EditText postDelayInput = dialogView.findViewById(R.id.edt_match_post_delay);
        EditText pollFastInput = dialogView.findViewById(R.id.edt_poll_fast_ms);
        EditText pollMediumInput = dialogView.findViewById(R.id.edt_poll_medium_ms);
        EditText pollSlowInput = dialogView.findViewById(R.id.edt_poll_slow_ms);
        setPollingIntervalHints(
                pollFastInput,
                pollMediumInput,
                pollSlowInput,
                AdaptivePollingController.Profile.TEMPLATE_MATCH);

        bindDropdownSelect(methodInput, getMatchMethodOptions());
        bindAutoComplete(fallbackInput, getCurrentTaskOperationIds(excludeId));
        methodInput.setText(METHOD_TM_CCOEFF_NORMED, false);
        if (sampleRatioInput != null) sampleRatioInput.setText("0.1");
        scaleFactorInput.setText("1.0");
        chkSuccessClick.setChecked(true);
        chkUseCanny.setChecked(false);
        if (preDelayInput != null) preDelayInput.setText("0");
        if (postDelayInput != null) postDelayInput.setText("0");
        if (preDelayInput != null) preDelayInput.setHint("0~" + MetaOperation.MAX_NODE_PRE_DELAY_MS);
        if (postDelayInput != null) postDelayInput.setHint("0~" + MetaOperation.MAX_MATCH_DELAY_MS);

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
                Object sampleRatioObj = inputMap.opt(MetaOperation.MATCH_SAMPLE_RATIO);
                if (sampleRatioObj != null && sampleRatioInput != null) {
                    sampleRatioInput.setText(String.valueOf(sampleRatioObj));
                }
                fallbackInput.setText(inputMap.optString(MetaOperation.FALLBACKOPERATIONID, ""), false);
                Object preDelayObj = inputMap.has(MetaOperation.NODE_PRE_DELAY_MS)
                        ? inputMap.opt(MetaOperation.NODE_PRE_DELAY_MS)
                        : inputMap.opt(MetaOperation.MATCH_PRE_DELAY_MS);
                Object postDelayObj = inputMap.opt(MetaOperation.MATCH_POST_DELAY_MS);
                Object pollFastObj = inputMap.opt(MetaOperation.POLL_FAST_INTERVAL_MS);
                Object pollMediumObj = inputMap.opt(MetaOperation.POLL_MEDIUM_INTERVAL_MS);
                Object pollSlowObj = inputMap.opt(MetaOperation.POLL_SLOW_INTERVAL_MS);
                if (preDelayObj != null && preDelayInput != null) {
                    preDelayInput.setText(String.valueOf(preDelayObj).replace(".0", ""));
                }
                if (postDelayObj != null && postDelayInput != null) {
                    postDelayInput.setText(String.valueOf(postDelayObj).replace(".0", ""));
                }
                if (pollFastObj != null && pollFastInput != null) {
                    pollFastInput.setText(String.valueOf(pollFastObj).replace(".0", ""));
                }
                if (pollMediumObj != null && pollMediumInput != null) {
                    pollMediumInput.setText(String.valueOf(pollMediumObj).replace(".0", ""));
                }
                if (pollSlowObj != null && pollSlowInput != null) {
                    pollSlowInput.setText(String.valueOf(pollSlowObj).replace(".0", ""));
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
        EditText sampleRatioInput = dialogView.findViewById(R.id.edt_match_sample_ratio);
        EditText scaleFactorInput = dialogView.findViewById(R.id.edt_scale_factor);
        EditText preDelayInput = dialogView.findViewById(R.id.edt_match_pre_delay);
        EditText postDelayInput = dialogView.findViewById(R.id.edt_match_post_delay);
        EditText pollFastInput = dialogView.findViewById(R.id.edt_poll_fast_ms);
        EditText pollMediumInput = dialogView.findViewById(R.id.edt_poll_medium_ms);
        EditText pollSlowInput = dialogView.findViewById(R.id.edt_poll_slow_ms);

        String methodText = methodInput == null || methodInput.getText() == null ? "" : methodInput.getText().toString().trim();
        String sampleRatioText = sampleRatioInput == null || sampleRatioInput.getText() == null ? "" : sampleRatioInput.getText().toString().trim();
        String sfText = scaleFactorInput == null || scaleFactorInput.getText() == null ? "" : scaleFactorInput.getText().toString().trim();

        int methodCode = parseMethodCode(methodText);
        double sampleRatio = 0.1d;
        if (!TextUtils.isEmpty(sampleRatioText)) {
            try {
                sampleRatio = Double.parseDouble(sampleRatioText);
            } catch (Exception ignored) {
                sampleRatio = 0.1d;
            }
        }
        if (sampleRatio <= 0.0d) sampleRatio = 0.1d;
        if (sampleRatio > 1.0d) sampleRatio = 1.0d;
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
        if (preDelay > MetaOperation.MAX_NODE_PRE_DELAY_MS) preDelay = MetaOperation.MAX_NODE_PRE_DELAY_MS;
        if (postDelay > MetaOperation.MAX_MATCH_DELAY_MS) postDelay = MetaOperation.MAX_MATCH_DELAY_MS;

        try {
            inputMap.put(MetaOperation.MATCHUSEGRAY, chkGray != null && chkGray.isChecked());
            inputMap.put(MetaOperation.MATCHMETHOD, (double) methodCode);
            if (methodCode == MetaOperation.MATCH_METHOD_RANDOM_SAMPLE
                    || methodCode == MetaOperation.MATCH_METHOD_RANDOM_ROI) {
                inputMap.put(MetaOperation.MATCH_SAMPLE_RATIO, sampleRatio);
            } else {
                inputMap.remove(MetaOperation.MATCH_SAMPLE_RATIO);
            }
            inputMap.put(MetaOperation.MATCHSCALEFACTOR, scaleFactor);
            inputMap.put(MetaOperation.SUCCEESCLICK, chkSuccessClick != null && chkSuccessClick.isChecked());
            inputMap.put(MetaOperation.MATCHUSECANNARY, chkUseCanny != null && chkUseCanny.isChecked());
            if (preDelay > 0) {
                inputMap.put(MetaOperation.NODE_PRE_DELAY_MS, (double) preDelay);
                inputMap.remove(MetaOperation.MATCH_PRE_DELAY_MS);
            } else {
                inputMap.remove(MetaOperation.NODE_PRE_DELAY_MS);
                inputMap.remove(MetaOperation.NODE_PRE_DELAY_MIN_MS);
                inputMap.remove(MetaOperation.NODE_PRE_DELAY_MAX_MS);
                inputMap.remove(MetaOperation.NODE_PRE_DELAY_RANDOM);
                inputMap.remove(MetaOperation.MATCH_PRE_DELAY_MS);
            }
            if (postDelay > 0) {
                inputMap.put(MetaOperation.MATCH_POST_DELAY_MS, (double) postDelay);
            } else {
                inputMap.remove(MetaOperation.MATCH_POST_DELAY_MS);
            }
            putOptionalPollingInterval(inputMap, MetaOperation.POLL_FAST_INTERVAL_MS, pollFastInput);
            putOptionalPollingInterval(inputMap, MetaOperation.POLL_MEDIUM_INTERVAL_MS, pollMediumInput);
            putOptionalPollingInterval(inputMap, MetaOperation.POLL_SLOW_INTERVAL_MS, pollSlowInput);
            if (TextUtils.isEmpty(fallbackId)) {
                inputMap.remove(MetaOperation.FALLBACKOPERATIONID);
            } else {
                inputMap.put(MetaOperation.FALLBACKOPERATIONID, fallbackId);
            }
        } catch (Exception e) {
            Log.w(TAG, "写入高级匹配参数失败", e);
        }
    }

    private void setPollingIntervalHints(EditText fastInput,
                                          EditText mediumInput,
                                          EditText slowInput,
                                          AdaptivePollingController.Profile profile) {
        if (fastInput != null) {
            fastInput.setHint("默认 " + AdaptivePollingController.defaultFastIntervalMs(profile));
        }
        if (mediumInput != null) {
            mediumInput.setHint("默认 " + AdaptivePollingController.defaultMediumIntervalMs(profile));
        }
        if (slowInput != null) {
            slowInput.setHint("默认 " + AdaptivePollingController.defaultSlowIntervalMs(profile));
        }
    }

    private void putOptionalPollingInterval(JSONObject inputMap, String key, EditText input) throws org.json.JSONException {
        String text = input == null || input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            inputMap.remove(key);
            return;
        }
        long value;
        try {
            value = Long.parseLong(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("轮询间隔必须是大于 0 的毫秒数");
        }
        if (value <= 0) {
            throw new IllegalArgumentException("轮询间隔必须是大于 0 的毫秒数");
        }
        inputMap.put(key, Math.max(10L, Math.min(value, 5000L)));
    }

    private boolean launchTemplateCapture(String templateFileName) {
        return launchTemplateCaptureInScaleDir(templateFileName, null);
    }

    /**
     * 启动框选截图并保存模板。
     * @param scaleDirName 目标 scale 子目录（如 "scale_100"），null 表示由当前 CAPTURE_SCALE 决定
     */
    private boolean launchTemplateCaptureInScaleDir(String templateFileName,
                                                     @Nullable String scaleDirName) {
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
        if (!TextUtils.isEmpty(scaleDirName)) {
            // 替换截图时指定目标目录，确保覆盖同 scale 下的旧模板
            inputMap.put(MetaOperation.TARGET_SCALE_DIR, scaleDirName);
        }
        cropOperation.setInputMap(inputMap);

        OperationHandler handler = OperationHandlerManager.getOperationHandler(3);
        if (handler == null) {
            Toast.makeText(this, "截图功能不可用", Toast.LENGTH_SHORT).show();
            return false;
        }
        Bitmap preCaptured = takePendingCleanCaptureBitmap();
        if (preCaptured != null && !preCaptured.isRecycled()) {
            inputMap.put(MetaOperation.PRE_CAPTURED_BITMAP, preCaptured);
        }
        boolean handled = handler.handle(cropOperation, new OperationContext());
        if (!handled) {
            Object unusedBitmap = inputMap.remove(MetaOperation.PRE_CAPTURED_BITMAP);
            if (unusedBitmap instanceof Bitmap) {
                recycleBitmap((Bitmap) unusedBitmap);
            }
            return false;
        }
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

    private void updateRegionPickStatus(TextView statusView, @Nullable List<Integer> bbox) {
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
        runAfterScreenCaptureReady("框选区域", () -> postToUiDelayed(() -> {
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
                                updateRegionPickStatus(statusView, parseBboxInput(text));
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
                toastOnMain("请拖动框选区域，然后点确定");
            } catch (Exception e) {
                restoreViews.run();
                toastOnMain("框选失败: " + e.getMessage());
            }
        }, CAPTURE_OVERLAY_LAUNCH_DELAY_MS), restoreViews);
    }

    @Override
    public void onTaskSwitch(String taskId, String taskName, List<OperationItem> operations) {
        if (focusRunMode) {
            return;
        }
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
        currentRunningTask = TextUtils.isEmpty(taskId) ? taskName : taskId;
        CrashLogger.updateRunContext(currentRunningProject, currentRunningTask, currentRunningOperationId, currentRunningOperationName);

        final List<OperationItem> switchedOperations = new ArrayList<>(runningOperations);
        postToUi(() -> {
            File runtimeTaskDir = resolveRuntimeTaskDir(taskId, taskName);
            if (runtimeTaskDir != null) {
                currentTaskDir = runtimeTaskDir;
                currentLevel = NavigationLevel.OPERATION;
                clearProjectPanelSearch();
                updateUIForLevel();
                renderRuntimeTaskOperations(runtimeTaskDir, switchedOperations);
            }
            syncProjectPanelRuntimeUi();
        });
        
        // 更新通知栏
        updateNotification("已切换到: " + currentRunningTask);
    }

    @Nullable
    private File resolveRuntimeTaskDir(@Nullable String taskId, @Nullable String taskName) {
        if (currentProjectDir == null) {
            return null;
        }
        File directById = resolveExistingTaskDir(taskId);
        if (directById != null) {
            return directById;
        }
        File directByName = resolveExistingTaskDir(taskName);
        if (directByName != null) {
            return directByName;
        }

        Project project = findCachedProjectByName(currentProjectDir.getName());
        if (project != null && project.getTaskMap() != null) {
            for (Map.Entry<String, Task> entry : project.getTaskMap().entrySet()) {
                Task task = entry.getValue();
                if (task == null) {
                    continue;
                }
                boolean matches = TextUtils.equals(entry.getKey(), taskId)
                        || TextUtils.equals(entry.getKey(), taskName)
                        || TextUtils.equals(task.getId(), taskId)
                        || TextUtils.equals(task.getId(), taskName)
                        || TextUtils.equals(task.getName(), taskName)
                        || TextUtils.equals(task.getName(), taskId);
                if (matches) {
                    File dir = resolveExistingTaskDir(entry.getKey());
                    if (dir != null) {
                        return dir;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private File resolveExistingTaskDir(@Nullable String taskKey) {
        if (currentProjectDir == null || TextUtils.isEmpty(taskKey)) {
            return null;
        }
        File taskDir = new File(currentProjectDir, taskKey);
        return taskDir.isDirectory() ? taskDir : null;
    }

    private void renderRuntimeTaskOperations(@NonNull File taskDir, @NonNull List<OperationItem> operations) {
        if (projectPanelView == null || getProjectPanelRecyclerView() == null) {
            return;
        }
        List<OperationItem> items = new ArrayList<>(operations);
        String taskKey = buildFileCacheKey(taskDir);
        File json = new File(taskDir, "operations.json");
        long version = json.exists() ? json.lastModified() : Long.MIN_VALUE;
        operationListCacheTaskDir = taskDir;
        operationListCacheVersion = version;
        operationListCache.clear();
        operationListCache.addAll(items);
        operationItemsMemoryCache.put(taskKey, new ArrayList<>(items));
        operationItemsMemoryVersions.put(taskKey, version);
        renderOperationItems(taskDir, items, taskKey, "");
    }

    private void followRuntimeTaskIfPanelIsStale(@NonNull String operationId) {
        // Only skip if the panel is actually at OPERATION level showing this operation.
        // If the user navigated back to PROJECT/TASK level, the adapter retains stale data
        // but currentLevel no longer reflects OPERATION — we must still navigate down.
        if (currentLevel == NavigationLevel.OPERATION
                && currentOperationAdapter != null
                && currentOperationAdapter.findPositionById(operationId) >= 0) {
            return;
        }
        File ownerTaskDir = findTaskDirOwningOperation(operationId);
        // isSameFile alone is not enough: currentTaskDir may still equal ownerTaskDir even
        // when the user navigated back to TASK/PROJECT level, so also require OPERATION level.
        if (ownerTaskDir == null
                || (currentLevel == NavigationLevel.OPERATION && isSameFile(currentTaskDir, ownerTaskDir))) {
            return;
        }
        currentTaskDir = ownerTaskDir;
        currentLevel = NavigationLevel.OPERATION;
        clearProjectPanelSearch();
        updateUIForLevel();
        loadOperations(ownerTaskDir, true);
    }

    @Nullable
    private File findTaskDirOwningOperation(@Nullable String operationId) {
        if (currentProjectDir == null || TextUtils.isEmpty(operationId)) {
            return null;
        }
        Project project = findCachedProjectByName(currentProjectDir.getName());
        if (project == null || project.getTaskMap() == null) {
            project = loadProjectFromDir(currentProjectDir);
            if (project != null) {
                upsertCachedProject(project);
            }
        }
        if (project == null || project.getTaskMap() == null) {
            return null;
        }
        for (Map.Entry<String, Task> entry : project.getTaskMap().entrySet()) {
            Task task = entry.getValue();
            if (task != null && task.getOperationMap() != null
                    && task.getOperationMap().containsKey(operationId)) {
                return resolveExistingTaskDir(entry.getKey());
            }
        }
        return null;
    }

    private void scrollRunningOperationIntoView(@Nullable String operationId) {
        if (currentOperationAdapter == null || TextUtils.isEmpty(operationId)) {
            return;
        }
        RecyclerView rv = getProjectPanelRecyclerView();
        if (rv == null) {
            return;
        }
        int position = currentOperationAdapter.findPositionById(operationId);
        if (position < 0) {
            return;
        }
        RecyclerView.LayoutManager manager = rv.getLayoutManager();
        if (manager instanceof LinearLayoutManager) {
            LinearLayoutManager linear = (LinearLayoutManager) manager;
            int first = linear.findFirstCompletelyVisibleItemPosition();
            int last = linear.findLastCompletelyVisibleItemPosition();
            if (position < first || position > last) {
                linear.scrollToPositionWithOffset(position, Math.max(dp(24), rv.getHeight() / 3));
            }
        } else {
            rv.scrollToPosition(position);
        }
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
                    extractDelayShowCountdown(op),
                    extractNodePreDelayMs(op),
                    extractNodePreDelayMinMs(op),
                    extractNodePreDelayMaxMs(op),
                    extractNodePreDelayRandom(op)
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
        
        syncProjectPanelRuntimeUi();
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

    private void updateRunningPanelStatus(String status, int color) {
        runtimeStatusText = status;
        runtimeStatusColor = color;
        syncProjectPanelRuntimeUi();
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
