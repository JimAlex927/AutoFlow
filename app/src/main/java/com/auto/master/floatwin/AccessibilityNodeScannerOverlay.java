package com.auto.master.floatwin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.auto.master.R;
import com.auto.master.auto.AutoAccessibilityService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 无障碍节点扫描器。
 *
 * 工作方式：
 * 1. 调用 show(dialogView) 时将传入的 dialog 隐藏，300ms 后扫描节点树；
 * 2. 在真实 App 界面上叠加半透明遮罩，用彩色边框勾勒每个节点的屏幕位置；
 * 3. 用户点击任意区域 → 自动选中该位置面积最小的节点，底部弹出详情面板；
 * 4. 点"填入对话框" → 关闭遮罩、恢复 dialog、回调填充数据。
 */
public class AccessibilityNodeScannerOverlay {

    // ──────────────────────────────────────────────────────────────
    //  数据模型
    // ──────────────────────────────────────────────────────────────

    public static class NodeInfo {
        public int    order;
        public int    depth;
        public int    indexInParent;
        public String shortClassName = "";
        public String fullClassName  = "";
        public String text           = "";
        public String contentDesc    = "";
        public String viewId         = "";
        public Rect   boundsScreen   = new Rect();
        public boolean clickable;
        public boolean longClickable;
        public boolean scrollable;
        public boolean editable;
        public boolean enabled       = true;
        public int    childCount;
        public String packageName    = "";

        public boolean hasText()        { return !TextUtils.isEmpty(text); }
        public boolean hasViewId()      { return !TextUtils.isEmpty(viewId) && viewId.contains(":id/"); }
        public boolean hasContentDesc() { return !TextUtils.isEmpty(contentDesc); }

        /** 推荐的查找方式（优先 viewId > text > contentDesc > className） */
        public String suggestedFindMode() {
            if (hasViewId())      return "viewId";
            if (hasText())        return "text";
            if (hasContentDesc()) return "contentDesc";
            return "className";
        }

        /** 对应给定模式的查找值 */
        public String findValueFor(String mode) {
            switch (mode) {
                case "viewId":           return viewId;
                case "text":             return text;
                case "textContains":     return text;
                case "textStartsWith":   return text;
                case "contentDesc":      return contentDesc;
                case "contentDescContains": return contentDesc;
                case "className":        return fullClassName;
                default:                 return "";
            }
        }

        /** 节点有哪些可用的查找模式 */
        public List<String> availableModes() {
            List<String> m = new ArrayList<>();
            if (hasViewId())      m.add("viewId");
            if (hasText())        { m.add("text"); m.add("textContains"); }
            if (hasContentDesc()) { m.add("contentDesc"); m.add("contentDescContains"); }
            m.add("className");
            return m;
        }

        public String displayName() {
            if (hasText()) return text;
            if (hasContentDesc()) return contentDesc;
            if (hasViewId()) return viewId;
            return shortClassName;
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  回调
    // ──────────────────────────────────────────────────────────────

    public interface OnNodeSelectedCallback {
        void onNodeSelected(String findMode, String findValue, int matchIndex, NodeInfo node);
    }

    // ──────────────────────────────────────────────────────────────
    //  字段
    // ──────────────────────────────────────────────────────────────

    private final Context              context;
    private final WindowManager        wm;
    private final OnNodeSelectedCallback callback;
    private final Handler              mainHandler = new Handler(Looper.getMainLooper());

    private View         dialogView;   // 调用方的 dialog（扫描期间隐藏）
    private View         overlayView;
    private WindowManager activeWm;
    private final List<View> hiddenViews = new ArrayList<>();
    private final List<Integer> hiddenStates = new ArrayList<>();
    private NodeCanvas   nodeCanvas;

    private List<NodeInfo> allNodes      = new ArrayList<>();
    private List<NodeInfo> filteredNodes = new ArrayList<>();
    private NodeInfo       selectedNode;
    private String         selectedMode;         // 当前用户选择的填入模式
    private List<String>   selectedAvailModes;   // 选中节点的可用模式

    private boolean listModeVisible = false;
    private boolean toolbarCollapsed = false;
    private int statusBarInset;
    private int navigationBarInset;
    private NodeTreeAdapter listAdapter;

    // 颜色
    private static final int C_CLICKABLE  = 0xFF1565C0;
    private static final int C_SCROLLABLE = 0xFF2E7D32;
    private static final int C_EDITABLE   = 0xFF6A1B9A;
    private static final int C_CONTAINER  = 0xFF37474F;
    private static final int C_OTHER      = 0xFF00695C;

    // ──────────────────────────────────────────────────────────────
    //  公开入口
    // ──────────────────────────────────────────────────────────────

    public AccessibilityNodeScannerOverlay(Context context, WindowManager wm,
                                           OnNodeSelectedCallback callback) {
        this.context  = context;
        this.wm       = wm;
        this.callback = callback;
    }

    /** 隐藏 dialogView，延迟扫描并展示覆盖层 */
    public void show(View dialogViewToHide) {
        show(dialogViewToHide, (View[]) null);
    }

    /** 隐藏 dialogView 和其它悬浮面板，延迟扫描并展示覆盖层 */
    public void show(View dialogViewToHide, View... extraViewsToHide) {
        this.dialogView = dialogViewToHide;
        hideView(dialogView);
        if (extraViewsToHide != null) {
            for (View view : extraViewsToHide) {
                hideView(view);
            }
        }
        // 等 dialog 隐藏、屏幕回到真实 App 后再扫描
        mainHandler.postDelayed(this::buildAndScan, 350);
    }

    public void dismiss() {
        try { if (overlayView != null) (activeWm != null ? activeWm : wm).removeView(overlayView); }
        catch (Exception ignored) {}
        overlayView = null;
        activeWm = null;
        restoreHiddenViews();
    }

    private void hideView(View view) {
        if (view == null || hiddenViews.contains(view)) return;
        hiddenViews.add(view);
        hiddenStates.add(view.getVisibility());
        view.setVisibility(View.GONE);
    }

    private void restoreHiddenViews() {
        for (int i = 0; i < hiddenViews.size(); i++) {
            View view = hiddenViews.get(i);
            Integer state = hiddenStates.get(i);
            if (view != null) {
                view.setVisibility(state == null ? View.VISIBLE : state);
            }
        }
        hiddenViews.clear();
        hiddenStates.clear();
    }

    // ──────────────────────────────────────────────────────────────
    //  构建覆盖层 + 扫描
    // ──────────────────────────────────────────────────────────────

    private void buildAndScan() {
        overlayView = LayoutInflater.from(context)
                .inflate(R.layout.overlay_a11y_scanner, null);

        // NodeCanvas 填满 fl_node_canvas
        nodeCanvas = new NodeCanvas(context);
        FrameLayout canvasFrame = overlayView.findViewById(R.id.fl_node_canvas);
        canvasFrame.addView(nodeCanvas,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        AutoAccessibilityService svc = AutoAccessibilityService.get();
        activeWm = svc == null ? wm : (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
        if (activeWm == null) activeWm = wm;
        int type = svc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        try {
            activeWm.addView(overlayView, lp);
        } catch (Throwable t) {
            Toast.makeText(context, "节点分析器打开失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }
        applySystemBarSafeAreas();
        bindTopBar();
        bindDetailPanel();
        bindListPanel();
        startScan();
    }

    // ──────────────────────────────────────────────────────────────
    //  绑定视图事件
    // ──────────────────────────────────────────────────────────────

    private void bindTopBar() {
        overlayView.findViewById(R.id.btn_close_scanner)
                .setOnClickListener(v -> dismiss());
        overlayView.findViewById(R.id.btn_collapse_scanner_toolbar)
                .setOnClickListener(v -> setToolbarCollapsed(true));
        overlayView.findViewById(R.id.btn_show_scanner_toolbar)
                .setOnClickListener(v -> setToolbarCollapsed(false));
        bindCompactToolbarDrag();
        overlayView.findViewById(R.id.btn_exit_scanner)
                .setOnClickListener(v -> dismiss());
        overlayView.findViewById(R.id.btn_refresh)
                .setOnClickListener(v -> startScan());
        overlayView.findViewById(R.id.btn_list_mode)
                .setOnClickListener(v -> setListMode(true));
    }

    private void bindDetailPanel() {
        overlayView.findViewById(R.id.btn_deselect)
                .setOnClickListener(v -> clearSelection());
        overlayView.findViewById(R.id.btn_apply_node)
                .setOnClickListener(v -> applyNode());
    }

    private void bindListPanel() {
        View panelList = overlayView.findViewById(R.id.panel_list);

        overlayView.findViewById(R.id.btn_back_highlight)
                .setOnClickListener(v -> setListMode(false));

        RecyclerView rv = overlayView.findViewById(R.id.recycler_nodes);
        listAdapter = new NodeTreeAdapter();
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setAdapter(listAdapter);

        EditText edt = overlayView.findViewById(R.id.edt_filter);
        edt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                applyFilter(s.toString().trim().toLowerCase());
            }
        });
    }

    private void setListMode(boolean on) {
        listModeVisible = on;
        View panelList = overlayView.findViewById(R.id.panel_list);
        panelList.setVisibility(on ? View.VISIBLE : View.GONE);
        setFallbackExitVisible(selectedNode == null || on);
    }

    /**
     * Accessibility overlays can extend behind system bars. Keep every exit path in the
     * application touch area so a notch or status bar can never trap the user in the scanner.
     */
    private void applySystemBarSafeAreas() {
        if (overlayView == null) return;
        int statusBar = getSystemBarDimension("status_bar_height");
        int navigationBar = getSystemBarDimension("navigation_bar_height");
        statusBarInset = statusBar;
        navigationBarInset = navigationBar;

        View topBar = overlayView.findViewById(R.id.top_bar);
        if (topBar != null && topBar.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) topBar.getLayoutParams();
            params.topMargin = Math.max(params.topMargin, statusBar);
            topBar.setLayoutParams(params);
        }

        View detailPanel = overlayView.findViewById(R.id.panel_detail);
        if (detailPanel != null && detailPanel.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) detailPanel.getLayoutParams();
            params.bottomMargin = Math.max(params.bottomMargin, navigationBar);
            detailPanel.setLayoutParams(params);
        }

        View exitButton = overlayView.findViewById(R.id.btn_exit_scanner);
        if (exitButton != null && exitButton.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) exitButton.getLayoutParams();
            params.bottomMargin = Math.max(params.bottomMargin, navigationBar + dp(12));
            exitButton.setLayoutParams(params);
        }

        View showToolbarButton = overlayView.findViewById(R.id.btn_show_scanner_toolbar);
        if (showToolbarButton != null
                && showToolbarButton.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) showToolbarButton.getLayoutParams();
            params.topMargin = Math.max(params.topMargin, statusBar + dp(8));
            showToolbarButton.setLayoutParams(params);
        }

        View listPanel = overlayView.findViewById(R.id.panel_list);
        if (listPanel != null) {
            listPanel.setPadding(listPanel.getPaddingLeft(), statusBar,
                    listPanel.getPaddingRight(), navigationBar);
        }
    }

    private int getSystemBarDimension(String resourceName) {
        int resourceId = context.getResources().getIdentifier(
                resourceName, "dimen", "android");
        return resourceId > 0 ? context.getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private void bindCompactToolbarDrag() {
        View button = overlayView.findViewById(R.id.btn_show_scanner_toolbar);
        if (button == null) return;
        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        button.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int startLeft;
            private int startTop;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startLeft = v.getLeft();
                        startTop = v.getTop();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;
                        if (!moved && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                            moved = true;
                        }
                        if (moved) {
                            moveCompactToolbar(v, Math.round(startLeft + deltaX),
                                    Math.round(startTop + deltaY));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) v.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void moveCompactToolbar(View button, int requestedLeft, int requestedTop) {
        if (overlayView == null || !(button.getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return;
        }
        int width = overlayView.getWidth();
        int height = overlayView.getHeight();
        if (width <= 0 || height <= 0) return;
        int maxLeft = Math.max(0, width - button.getWidth());
        int minTop = statusBarInset + dp(8);
        int maxTop = Math.max(minTop, height - navigationBarInset - dp(12) - button.getHeight());
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) button.getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = Math.max(0, Math.min(requestedLeft, maxLeft));
        params.topMargin = Math.max(minTop, Math.min(requestedTop, maxTop));
        button.setLayoutParams(params);
    }

    // ──────────────────────────────────────────────────────────────
    //  扫描
    // ──────────────────────────────────────────────────────────────

    private void startScan() {
        TextView tvCount = overlayView.findViewById(R.id.tv_node_count);
        if (tvCount != null) tvCount.setText("扫描中...");
        setToolbarCollapsed(false);

        new Thread(() -> {
            List<NodeInfo> nodes = new ArrayList<>();
            try {
                AccessibilityNodeInfo root = getAppRoot();
                if (root != null) {
                    traverseTree(root, 0, 0, nodes);
                    root.recycle();
                }
            } catch (Exception ignored) {}

            mainHandler.post(() -> {
                allNodes      = nodes;
                filteredNodes = new ArrayList<>(nodes);
                nodeCanvas.setNodes(filteredNodes);
                listAdapter.setData(filteredNodes);
                TextView tv = overlayView.findViewById(R.id.tv_node_count);
                if (tv != null) tv.setText(nodes.size() + " 个节点");
                TextView hint = overlayView.findViewById(R.id.tv_hint);
                if (hint != null) hint.setText(nodes.isEmpty()
                        ? "未扫描到节点，请刷新" : "点击界面元素选中节点");
                // Do not cover app controls while selecting a scanned node.
                setToolbarCollapsed(!nodes.isEmpty());
            });
        }).start();
    }

    private void setToolbarCollapsed(boolean collapsed) {
        if (overlayView == null) return;
        toolbarCollapsed = collapsed;
        View topBar = overlayView.findViewById(R.id.top_bar);
        View showToolbarButton = overlayView.findViewById(R.id.btn_show_scanner_toolbar);
        if (topBar != null) topBar.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        if (showToolbarButton != null) {
            showToolbarButton.setVisibility(collapsed ? View.VISIBLE : View.GONE);
        }
    }

    /** 优先获取前台 App（非本包）的根节点 */
    private AccessibilityNodeInfo getAppRoot() {
        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                List<AccessibilityWindowInfo> windows = svc.getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo win : windows) {
                        if (win.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                            AccessibilityNodeInfo r = win.getRoot();
                            if (r != null) {
                                CharSequence pkg = r.getPackageName();
                                if (pkg != null && !pkg.toString().equals(context.getPackageName()))
                                    return r;
                                r.recycle();
                            }
                        }
                    }
                    // fallback：取任意 APPLICATION 窗口
                    for (AccessibilityWindowInfo win : windows) {
                        if (win.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                            return win.getRoot();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return svc.getRootInActiveWindow();
    }

    private void traverseTree(AccessibilityNodeInfo node, int depth, int indexInParent, List<NodeInfo> out) {
        if (node == null || out.size() > 2000) return;

        NodeInfo info       = new NodeInfo();
        info.order          = out.size();
        info.depth          = depth;
        info.indexInParent  = indexInParent;
        CharSequence cn     = node.getClassName();
        info.fullClassName  = cn  != null ? cn.toString()  : "";
        info.shortClassName = shortClass(info.fullClassName);
        CharSequence t      = node.getText();
        info.text           = t   != null ? t.toString()   : "";
        CharSequence d      = node.getContentDescription();
        info.contentDesc    = d   != null ? d.toString()   : "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            String vid = node.getViewIdResourceName();
            info.viewId = vid != null ? vid : "";
        }
        node.getBoundsInScreen(info.boundsScreen);
        info.clickable     = node.isClickable();
        info.longClickable = node.isLongClickable();
        info.scrollable    = node.isScrollable();
        info.enabled       = node.isEnabled();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            info.editable  = node.isEditable();
        info.childCount    = node.getChildCount();
        CharSequence pkg   = node.getPackageName();
        info.packageName   = pkg != null ? pkg.toString() : "";

        out.add(info);

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseTree(child, depth + 1, i, out);
                child.recycle();
            }
        }
    }

    private static String shortClass(String full) {
        if (TextUtils.isEmpty(full)) return "?";
        int dot = full.lastIndexOf('.');
        return dot >= 0 ? full.substring(dot + 1) : full;
    }

    private void applyFilter(String q) {
        if (TextUtils.isEmpty(q)) {
            filteredNodes = new ArrayList<>(allNodes);
        } else {
            filteredNodes = new ArrayList<>();
            for (NodeInfo n : allNodes) {
                if (n.text.toLowerCase().contains(q)
                        || n.viewId.toLowerCase().contains(q)
                        || n.contentDesc.toLowerCase().contains(q)
                        || n.shortClassName.toLowerCase().contains(q)
                        || n.fullClassName.toLowerCase().contains(q)) {
                    filteredNodes.add(n);
                }
            }
        }
        nodeCanvas.setNodes(filteredNodes);
        listAdapter.setData(filteredNodes);
    }

    // ──────────────────────────────────────────────────────────────
    //  选中 / 详情面板
    // ──────────────────────────────────────────────────────────────

    void selectNode(NodeInfo node) {
        selectedNode       = node;
        selectedAvailModes = node.availableModes();
        selectedMode       = node.suggestedFindMode();
        nodeCanvas.setSelected(node);
        showDetailPanel(node);
    }

    private void clearSelection() {
        selectedNode = null;
        nodeCanvas.setSelected(null);
        overlayView.findViewById(R.id.panel_detail).setVisibility(View.GONE);
        setFallbackExitVisible(true);
    }

    private void showDetailPanel(NodeInfo n) {
        LinearLayout panel = overlayView.findViewById(R.id.panel_detail);
        panel.setVisibility(View.VISIBLE);
        setFallbackExitVisible(false);

        // 类名 badge
        TextView tvClass = panel.findViewById(R.id.tv_sel_class);
        tvClass.setText(n.shortClassName);
        tvClass.setBackgroundColor(nodeColor(n));

        // 属性标签
        List<String> props = new ArrayList<>();
        if (n.clickable)     props.add("可点击");
        if (n.longClickable) props.add("可长按");
        if (n.scrollable)    props.add("可滚动");
        if (n.editable)      props.add("可编辑");
        if (!n.enabled)      props.add("已禁用");
        TextView tvProps = panel.findViewById(R.id.tv_sel_props);
        tvProps.setText((props.isEmpty() ? "" : TextUtils.join("  ", props) + "  ")
                + "depth=" + n.depth + "  index=" + n.indexInParent);

        // 内容字段
        bindDetailRow(panel.findViewById(R.id.tv_sel_text),
                "文字: " + n.text, n.hasText());
        bindDetailRow(panel.findViewById(R.id.tv_sel_view_id),
                "ID:  " + n.viewId, n.hasViewId());
        bindDetailRow(panel.findViewById(R.id.tv_sel_desc),
                "描述: " + n.contentDesc, n.hasContentDesc());

        ((TextView) panel.findViewById(R.id.tv_sel_bounds))
                .setText("位置: " + n.boundsScreen.toShortString()
                        + "   子节点: " + n.childCount);

        // 填入方式 chips
        LinearLayout llChips = panel.findViewById(R.id.ll_mode_chips);
        llChips.removeAllViews();
        for (String mode : selectedAvailModes) {
            TextView chip = new TextView(context);
            chip.setText(modeLabel(mode));
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            chip.setTextSize(12f);
            refreshChip(chip, mode);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                selectedMode = mode;
                // 刷新所有 chip 颜色
                for (int i = 0; i < llChips.getChildCount(); i++) {
                    View c = llChips.getChildAt(i);
                    if (c instanceof TextView)
                        refreshChip((TextView) c, selectedAvailModes.get(i));
                }
                refreshSelectorAnalysis(panel);
            });
            llChips.addView(chip);
        }
        refreshSelectorAnalysis(panel);
    }

    private void setFallbackExitVisible(boolean visible) {
        if (overlayView == null) return;
        View exitButton = overlayView.findViewById(R.id.btn_exit_scanner);
        if (exitButton != null) {
            exitButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void bindDetailRow(TextView tv, String text, boolean visible) {
        tv.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) tv.setText(text);
    }

    private void refreshChip(TextView chip, String mode) {
        boolean active = mode.equals(selectedMode);
        chip.setTextColor(active ? Color.WHITE : Color.parseColor("#78909C"));
        chip.setBackgroundColor(active ? nodeColor(selectedNode) : Color.parseColor("#1AFFFFFF"));
    }

    private void applyNode() {
        if (selectedNode == null || callback == null) return;
        String value = selectedNode.findValueFor(selectedMode);
        callback.onNodeSelected(selectedMode, value, matchIndexFor(selectedNode, selectedMode), selectedNode);
        dismiss();
    }

    private void refreshSelectorAnalysis(View panel) {
        if (selectedNode == null || TextUtils.isEmpty(selectedMode)) return;
        String value = selectedNode.findValueFor(selectedMode);
        int matchCount = countMatches(selectedNode, selectedMode);
        int matchIndex = matchIndexFor(selectedNode, selectedMode);
        TextView quality = panel.findViewById(R.id.tv_sel_quality);
        if (quality != null) {
            quality.setText("推荐: " + selectorQuality(selectedMode, matchCount)
                    + "   命中: " + matchCount + " 个   当前序号: " + matchIndex);
            quality.setTextColor(matchCount <= 1 ? Color.parseColor("#A5D6A7") : Color.parseColor("#FFE082"));
        }
        TextView equivalent = panel.findViewById(R.id.tv_sel_equivalent);
        if (equivalent != null) {
            equivalent.setText("等价配置: findNode({mode: \"" + selectedMode
                    + "\", value: \"" + escapeInline(value)
                    + "\", package: \"" + escapeInline(selectedNode.packageName)
                    + "\", index: " + matchIndex + "})");
        }
    }

    private String selectorQuality(String mode, int matchCount) {
        if ("viewId".equals(mode) && matchCount <= 1) return "很稳";
        if (("viewId".equals(mode) || "text".equals(mode) || "contentDesc".equals(mode)) && matchCount <= 3) return "可用";
        if (matchCount > 1) return "需用序号区分";
        if ("className".equals(mode)) return "偏脆弱";
        return "一般";
    }

    private int countMatches(NodeInfo node, String mode) {
        if (node == null) return 0;
        String value = node.findValueFor(mode);
        int count = 0;
        for (NodeInfo n : allNodes) {
            if (sameSelector(node, n, mode, value)) count++;
        }
        return count;
    }

    private int matchIndexFor(NodeInfo node, String mode) {
        if (node == null) return 0;
        String value = node.findValueFor(mode);
        int index = 0;
        for (NodeInfo n : allNodes) {
            if (!sameSelector(node, n, mode, value)) continue;
            if (n == node || n.order == node.order) return index;
            index++;
        }
        return 0;
    }

    private boolean sameSelector(NodeInfo selected, NodeInfo candidate, String mode, String value) {
        if (candidate == null) return false;
        if (!TextUtils.equals(selected.packageName, candidate.packageName)) return false;
        return TextUtils.equals(value, candidate.findValueFor(mode));
    }

    private static String escapeInline(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ──────────────────────────────────────────────────────────────
    //  辅助
    // ──────────────────────────────────────────────────────────────

    private int nodeColor(NodeInfo n) {
        if (n == null) return C_OTHER;
        if (n.editable)   return C_EDITABLE;
        if (n.clickable)  return C_CLICKABLE;
        if (n.scrollable) return C_SCROLLABLE;
        if (n.childCount > 0) return C_CONTAINER;
        return C_OTHER;
    }

    private static String modeLabel(String mode) {
        switch (mode) {
            case "viewId":           return "资源ID";
            case "text":             return "文字精确";
            case "textContains":     return "文字包含";
            case "textStartsWith":   return "文字开头";
            case "contentDesc":      return "描述精确";
            case "contentDescContains": return "描述包含";
            case "className":        return "类名";
            default:                 return mode;
        }
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    // ══════════════════════════════════════════════════════════════
    //  NodeCanvas — 透明覆盖画布，绘制节点边框并响应触摸
    // ══════════════════════════════════════════════════════════════

    private class NodeCanvas extends View {
        private List<NodeInfo> nodes    = new ArrayList<>();
        private NodeInfo       selected = null;

        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelBg     = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelTxt    = new Paint(Paint.ANTI_ALIAS_FLAG);

        NodeCanvas(Context ctx) {
            super(ctx);
            setBackgroundColor(Color.TRANSPARENT);
            borderPaint.setStyle(Paint.Style.STROKE);
            fillPaint.setStyle(Paint.Style.FILL);
            labelBg.setStyle(Paint.Style.FILL);
            labelBg.setColor(0xCC000000);
            labelTxt.setStyle(Paint.Style.FILL);
            labelTxt.setColor(Color.WHITE);
            labelTxt.setTextSize(28f);
        }

        void setNodes(List<NodeInfo> list) {
            nodes = list != null ? list : new ArrayList<>();
            invalidate();
        }

        void setSelected(NodeInfo n) {
            selected = n;
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float tx = e.getX(), ty = e.getY();
            NodeInfo best = null;
            long bestArea = Long.MAX_VALUE;
            for (NodeInfo n : nodes) {
                Rect b = n.boundsScreen;
                if (tx >= b.left && tx <= b.right && ty >= b.top && ty <= b.bottom) {
                    long area = (long) b.width() * b.height();
                    if (area < bestArea) { bestArea = area; best = n; }
                }
            }
            if (best != null) selectNode(best);
            return true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // 不绘制背景 → 真实 App 界面透过来
            if (nodes.isEmpty()) return;

            for (int i = nodes.size() - 1; i >= 0; i--) {
                NodeInfo n = nodes.get(i);
                Rect b = n.boundsScreen;
                if (b.isEmpty()) continue;

                boolean isSel = n == selected;
                int color = nodeColor(n);

                // 填充（仅选中节点更明显）
                fillPaint.setColor(isSel
                        ? (color & 0x00FFFFFF | 0x55000000)
                        : (color & 0x00FFFFFF | 0x18000000));
                canvas.drawRect(b.left, b.top, b.right, b.bottom, fillPaint);

                // 边框
                borderPaint.setColor(isSel ? color : (color & 0x00FFFFFF | 0xBB000000));
                borderPaint.setStrokeWidth(isSel ? 4f : 1.5f);
                canvas.drawRect(b.left, b.top, b.right, b.bottom, borderPaint);

                // 标签（仅选中 或 足够大的节点）
                if (isSel || (b.width() > 80 && b.height() > 24)) {
                    String label = n.shortClassName;
                    if (n.hasText() && b.width() > 160)
                        label += "  \"" + n.text + "\"";
                    else if (n.hasViewId() && b.width() > 160) {
                        String id = n.viewId.contains(":id/")
                                ? n.viewId.substring(n.viewId.indexOf(":id/") + 4)
                                : n.viewId;
                        label += "  #" + id;
                    }
                    float ts = Math.min(28f, Math.max(18f, b.height() * 0.35f));
                    labelTxt.setTextSize(ts);
                    float tw = labelTxt.measureText(label);
                    if (tw < b.width() - 8) {
                        float tx2 = b.left + 4;
                        float ty2 = b.top + ts + 2;
                        canvas.drawRect(tx2 - 2, ty2 - ts - 2,
                                tx2 + tw + 4, ty2 + 4, labelBg);
                        canvas.drawText(label, tx2, ty2, labelTxt);
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  NodeTreeAdapter — 列表模式适配器
    // ══════════════════════════════════════════════════════════════

    private class NodeTreeAdapter
            extends RecyclerView.Adapter<NodeTreeAdapter.VH> {

        private List<NodeInfo> data = new ArrayList<>();

        void setData(List<NodeInfo> list) {
            data = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context)
                    .inflate(R.layout.item_a11y_node_tree, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            NodeInfo n = data.get(pos);

            h.itemView.setPadding(dp(4 + n.depth * 10), 0, 0, 0);
            h.viewDot.setBackgroundColor(nodeColor(n));

            h.tvClass.setText(n.shortClassName);

            if (n.hasText()) {
                h.tvText.setVisibility(View.VISIBLE);
                h.tvText.setText("\"" + n.text + "\"");
            } else if (n.hasContentDesc()) {
                h.tvText.setVisibility(View.VISIBLE);
                h.tvText.setText("[描述] " + n.contentDesc);
            } else {
                h.tvText.setVisibility(View.GONE);
            }

            if (n.hasViewId()) {
                h.tvViewId.setVisibility(View.VISIBLE);
                String id = n.viewId.contains(":id/")
                        ? n.viewId.substring(n.viewId.indexOf(":id/") + 4) : n.viewId;
                h.tvViewId.setText("#" + id);
            } else {
                h.tvViewId.setVisibility(View.GONE);
            }

            h.tvTagClickable.setVisibility(n.clickable  ? View.VISIBLE : View.GONE);
            h.tvTagScrollable.setVisibility(n.scrollable ? View.VISIBLE : View.GONE);
            h.tvTagEditable.setVisibility(n.editable   ? View.VISIBLE : View.GONE);
            h.tvChildCount.setVisibility(n.childCount > 0 ? View.VISIBLE : View.GONE);
            if (n.childCount > 0) h.tvChildCount.setText("+" + n.childCount);

            boolean isSel = n == selectedNode;
            h.itemView.setBackgroundColor(isSel ? 0x33FFFFFF : Color.TRANSPARENT);

            h.itemView.setOnClickListener(v -> {
                selectNode(n);
                setListMode(false); // 切回高亮模式，便于在真实界面上看位置
            });
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            View viewDot;
            TextView tvClass, tvText, tvViewId, tvChildCount;
            TextView tvTagClickable, tvTagScrollable, tvTagEditable;
            VH(View v) {
                super(v);
                viewDot          = v.findViewById(R.id.view_type_dot);
                tvClass          = v.findViewById(R.id.tv_class_name);
                tvText           = v.findViewById(R.id.tv_node_text);
                tvViewId         = v.findViewById(R.id.tv_node_view_id);
                tvChildCount     = v.findViewById(R.id.tv_child_count);
                tvTagClickable   = v.findViewById(R.id.tv_tag_clickable);
                tvTagScrollable  = v.findViewById(R.id.tv_tag_scrollable);
                tvTagEditable    = v.findViewById(R.id.tv_tag_editable);
            }
        }
    }
}
