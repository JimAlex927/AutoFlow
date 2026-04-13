package com.auto.master.floatwin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.TextUtils;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interactive flow graph canvas.
 *
 * Features:
 *  - Material-style node cards with type color bar
 *  - Pan (drag canvas) / Pinch-to-zoom
 *  - Long-press node body → drag to reposition node
 *  - Drag from port dot → connect to another node (set next/fallback)
 *  - Double-tap node → edit callback
 *  - Running highlight (green pulsing border)
 */
public class FlowGraphView extends View {

    // ──────────────── Data model ────────────────

    public static class Node {
        public static class Edge {
            public String toId;
            public String kind;
            public boolean fromFallbackPort;
            public int sourceSlotIndex = -1;
            public int sourceSlotCount = 0;
        }

        public int    order;
        public String id;
        public String name;
        public String type;
        public int    typeCode;
        public String nextId;
        public String fallbackId;
        public final List<Edge> extraEdges = new ArrayList<>();
    }

    // ──────────────── Callbacks ────────────────

    public interface OnNodeSelectListener   { void onSelected(@Nullable Node node); }
    public interface OnNodeDoubleTapListener { void onNodeDoubleTap(Node node); }
    /** Fired when user drags a connection between two nodes. */
    public interface OnConnectListener {
        /** @param isFallback true = fallback port, false = main next port */
        void onConnect(String fromId, String toId, boolean isFallback);
    }

    // ──────────────── Constants (dp values, converted in init) ────────────────

    private float NODE_W, NODE_H, NODE_GAP;
    private float BAR_W;          // left type-color bar
    private float CORNER_R;
    private float PORT_R;         // drawn radius of port dot
    private float PORT_HIT;       // touch hit radius for port
    private float ARROW_W, ARROW_H;

    // ──────────────── Paints ────────────────

    private final Paint pNodeBg    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStroke    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSelStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRunFill   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRunStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTitle     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSub       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIdxBg     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIdxTxt    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBar       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMainLine  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBranchLine  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFallLine  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPortMain  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPortFall  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pConnLine  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pEmpty     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pJumpFill  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pJumpText  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pJumpDivider = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPortRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBranchDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBranchRail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBranchCountBg = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ──────────────── Node state ────────────────

    private final List<Node>              nodes         = new ArrayList<>();
    private final Map<String, Node>       nodeMap       = new HashMap<>();
    private final Map<String, PointF>     nodePositions = new HashMap<>();
    private final Map<String, RectF>      nodeRects     = new HashMap<>();
    private boolean positionsReady = false;

    // ──────────────── View transform ────────────────

    private float scale   = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;

    // ──────────────── Touch / drag state ────────────────

    private enum DragMode { NONE, PAN, MOVE_NODE, CONNECT_MAIN, CONNECT_FALLBACK }
    private DragMode dragMode = DragMode.NONE;

    private float lastTouchX, lastTouchY;
    private float downScreenX, downScreenY;
    private boolean didMove;

    private String draggingNodeId;
    private String connectFromId;
    private float  connectTipX, connectTipY;

    private final Handler     longPressHandler  = new Handler(Looper.getMainLooper());
    private Runnable          longPressRunnable;
    private String            longPressCandidateId;
    private static final long LONG_PRESS_MS     = 380L;
    private static final float MOVE_SLOP_DP     = 6f;

    // ──────────────── Selection / highlight ────────────────

    private String selectedNodeId;
    private String highlightedNodeId;

    // ──────────────── Orthogonal connection routing ────────────────

    /** One rendered connection between two nodes, stored as ordered waypoints. */
    private static class ConnRoute {
        String  fromId, toId;
        String  kind;
        boolean fromFallbackPort;
        int     sourceSlotIndex = -1;
        int     sourceSlotCount = 0;
        int     lane = -1;   // -1 = direct vertical, ≥0 = right-side bypass lane
        int     span;        // toOrder - fromOrder (negative = backward edge)
        final List<PointF> pts = new ArrayList<>();
    }

    private final List<ConnRoute> routes       = new ArrayList<>();
    private       ConnRoute       selectedRoute = null;
    private       PointF          selectedRouteActionCenter = null;
    private final Paint           pHaloConn    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int      DIMMED_ALPHA = 58;

    private static final float LANE_W_DP    = 20f;  // spacing between adjacent lanes
    private static final float LANE_BASE_DP = 22f;  // gap from node right edge to lane 0
    private static final float EXIT_DP      = 14f;  // vertical stub before first H-turn
    private static final float ENTER_DP     = 14f;  // vertical stub before arriving at port
    private static final float CONN_HIT_DP  = 12f;  // tap hit tolerance on a line segment

    // ──────────────── Callbacks ────────────────

    private OnNodeSelectListener    selectListener;
    private OnNodeDoubleTapListener doubleTapListener;
    private OnConnectListener       connectListener;
    private boolean interactionReadOnly = false;

    // ──────────────── Detectors ────────────────

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector      gestureDetector;

    // ──────────────── Reusable path ────────────────

    private final Path reusablePath = new Path();
    private final RectF tempRect = new RectF();
    private final TextPaint titleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private float density;

    // ═══════════════════════════════════════════════════════
    //  Constructor & init
    // ═══════════════════════════════════════════════════════

    public FlowGraphView(Context context) {
        super(context);
        scaleDetector  = new ScaleGestureDetector(context, new PinchListener());
        gestureDetector = new GestureDetector(context, new TapListener());
        gestureDetector.setIsLongpressEnabled(false); // manual long-press
        init();
    }

    public FlowGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        scaleDetector  = new ScaleGestureDetector(context, new PinchListener());
        gestureDetector = new GestureDetector(context, new TapListener());
        gestureDetector.setIsLongpressEnabled(false);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        float d = density;
        float s = getResources().getDisplayMetrics().scaledDensity;

        NODE_W   = 248 * d;
        NODE_H   = 72  * d;
        NODE_GAP = 36  * d;
        BAR_W    = 5   * d;
        CORNER_R = 12  * d;
        PORT_R   = 7   * d;
        PORT_HIT = 22  * d;
        ARROW_W  = 8   * d;
        ARROW_H  = 10  * d;

        // Node card background + shadow
        pNodeBg.setColor(0xFFF8FAFF);
        pNodeBg.setStyle(Paint.Style.FILL);

        pStroke.setColor(0xFFDDE4F0);
        pStroke.setStyle(Paint.Style.STROKE);
        pStroke.setStrokeWidth(1.5f * d);

        pSelStroke.setColor(0xFF3C6DE4);
        pSelStroke.setStyle(Paint.Style.STROKE);
        pSelStroke.setStrokeWidth(2.5f * d);

        pRunFill.setColor(0x1A00C853);
        pRunFill.setStyle(Paint.Style.FILL);

        pRunStroke.setColor(0xFF00C853);
        pRunStroke.setStyle(Paint.Style.STROKE);
        pRunStroke.setStrokeWidth(2.5f * d);

        pTitle.setColor(0xFF1A2537);
        pTitle.setTextSize(13 * s);
        pTitle.setFakeBoldText(true);
        titleTextPaint.set(pTitle);

        pSub.setColor(0xFF7A8798);
        pSub.setTextSize(10 * s);
        subTextPaint.set(pSub);

        pIdxBg.setColor(0xFF3C6DE4);
        pIdxBg.setStyle(Paint.Style.FILL);

        pIdxTxt.setColor(0xFFFFFFFF);
        pIdxTxt.setTextSize(10 * s);
        pIdxTxt.setFakeBoldText(true);
        pIdxTxt.setTextAlign(Paint.Align.CENTER);

        pBar.setStyle(Paint.Style.FILL);

        pMainLine.setColor(0xFF3C9E5F);
        pMainLine.setStyle(Paint.Style.STROKE);
        pMainLine.setStrokeWidth(2 * d);
        pMainLine.setStrokeCap(Paint.Cap.ROUND);

        pBranchLine.setColor(0xFF9A6B2F);
        pBranchLine.setStyle(Paint.Style.STROKE);
        pBranchLine.setStrokeWidth(2 * d);
        pBranchLine.setStrokeCap(Paint.Cap.ROUND);

        pFallLine.setColor(0xFFB23B3B);
        pFallLine.setStyle(Paint.Style.STROKE);
        pFallLine.setStrokeWidth(2 * d);
        pFallLine.setStrokeCap(Paint.Cap.ROUND);
        pFallLine.setPathEffect(new DashPathEffect(new float[]{10 * d, 6 * d}, 0));

        pPortMain.setColor(0xFF3C9E5F);
        pPortMain.setStyle(Paint.Style.FILL);

        pPortFall.setColor(0xFFB23B3B);
        pPortFall.setStyle(Paint.Style.FILL);

        pConnLine.setColor(0xFF3C6DE4);
        pConnLine.setStyle(Paint.Style.STROKE);
        pConnLine.setStrokeWidth(2 * d);
        pConnLine.setPathEffect(new DashPathEffect(new float[]{8 * d, 5 * d}, 0));

        pEmpty.setColor(0xFF9AA4B2);
        pEmpty.setTextSize(14 * s);
        pEmpty.setTextAlign(Paint.Align.CENTER);

        pJumpFill.setColor(0xFF1F2937);
        pJumpFill.setStyle(Paint.Style.FILL);

        pJumpText.setColor(0xFFFFFFFF);
        pJumpText.setTextSize(10 * s);
        pJumpText.setFakeBoldText(true);
        pJumpText.setTextAlign(Paint.Align.CENTER);

        pJumpDivider.setColor(0x55FFFFFF);
        pJumpDivider.setStrokeWidth(dp(1));

        pPortRing.setColor(0xFFFFFFFF);
        pPortRing.setStyle(Paint.Style.FILL);

        pBranchDot.setColor(0xFF9A6B2F);
        pBranchDot.setStyle(Paint.Style.FILL);

        pBranchRail.setColor(0x7F9A6B2F);
        pBranchRail.setStrokeWidth(dp(2));
        pBranchRail.setStrokeCap(Paint.Cap.ROUND);

        pBranchCountBg.setColor(0xFF9A6B2F);
        pBranchCountBg.setStyle(Paint.Style.FILL);

        pHaloConn.setColor(0x503C6DE4);
        pHaloConn.setStyle(Paint.Style.STROKE);
        pHaloConn.setStrokeWidth(9 * d);
        pHaloConn.setStrokeCap(Paint.Cap.BUTT);
        pHaloConn.setStrokeJoin(Paint.Join.MITER);
    }

    // ═══════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════

    public void setNodes(List<Node> data) {
        nodes.clear();
        nodeMap.clear();
        nodePositions.clear();
        nodeRects.clear();
        routes.clear();
        selectedRoute = null;
        selectedRouteActionCenter = null;
        positionsReady = false;
        if (data != null) {
            nodes.addAll(data);
            for (Node node : data) {
                if (node != null && !TextUtils.isEmpty(node.id)) {
                    nodeMap.put(node.id, node);
                }
            }
        }
        if (selectedNodeId != null && findById(selectedNodeId) == null) selectedNodeId = null;
        invalidateView();
    }

    public void setSelectedNodeId(@Nullable String id) {
        selectedNodeId = id;
        invalidateView();
        if (selectListener != null) selectListener.onSelected(findById(id));
    }

    public void setHighlightedNodeId(@Nullable String id) {
        highlightedNodeId = id;
        invalidateView();
    }

    public @Nullable Node getSelectedNode() { return findById(selectedNodeId); }

    public void setOnNodeSelectListener(OnNodeSelectListener l)    { selectListener    = l; }
    public void setOnNodeDoubleTapListener(OnNodeDoubleTapListener l) { doubleTapListener = l; }
    public void setOnConnectListener(OnConnectListener l)          { connectListener   = l; }
    public void setInteractionReadOnly(boolean readOnly) {
        interactionReadOnly = readOnly;
        if (readOnly) {
            cancelPendingLongPress();
            dragMode = DragMode.NONE;
            draggingNodeId = null;
            connectFromId = null;
            invalidateView();
        }
    }

    public void resetViewTransform() {
        scale   = 1f;
        offsetX = 0f;
        offsetY = 0f;
        invalidateView();
    }

    public void autoArrange() {
        positionsReady = false;
        nodePositions.clear();
        initPositions();
        invalidateView();
    }

    // ═══════════════════════════════════════════════════════
    //  Layout
    // ═══════════════════════════════════════════════════════

    private void initPositions() {
        if (getWidth() == 0) return; // defer to first onDraw
        float canvasW = getWidth() / scale;
        float startX  = (canvasW - NODE_W) / 2f;
        float y = 24 * density;

        // Simple 1-column linear layout ordered by node.order
        for (Node n : nodes) {
            if (!nodePositions.containsKey(n.id)) {
                nodePositions.put(n.id, new PointF(startX, y));
                y += NODE_H + NODE_GAP;
            }
        }
        positionsReady = true;
        updateRects();
    }

    private void updateRects() {
        for (Node n : nodes) {
            PointF p = nodePositions.get(n.id);
            if (p != null) nodeRects.put(n.id, new RectF(p.x, p.y, p.x + NODE_W, p.y + NODE_H));
        }
        if (positionsReady) rebuildRoutes();
    }

    // ═══════════════════════════════════════════════════════
    //  Port geometry
    // ═══════════════════════════════════════════════════════

    /** Main output (next) port — bottom-center of node */
    private PointF mainPort(String id) {
        PointF p = nodePositions.get(id);
        if (p == null) return new PointF(0, 0);
        return new PointF(p.x + NODE_W * 0.5f, p.y + NODE_H);
    }

    /** Fallback port — bottom-right of node */
    private PointF fallPort(String id) {
        PointF p = nodePositions.get(id);
        if (p == null) return new PointF(0, 0);
        return new PointF(p.x + NODE_W - 24 * density, p.y + NODE_H);
    }

    private PointF switchBranchPort(String id, int slotIndex, int slotCount) {
        PointF p = nodePositions.get(id);
        if (p == null) return new PointF(0, 0);
        int safeCount = Math.max(1, slotCount);
        int safeIndex = Math.max(0, Math.min(slotIndex, safeCount - 1));
        if (safeCount <= 4) {
            float fraction = (safeIndex + 1f) / (safeCount + 1f);
            return new PointF(p.x + NODE_W + dp(4), p.y + NODE_H * fraction);
        }
        float minGap = dp(4);
        float maxSpread = dp(220);
        float spread = Math.min(maxSpread, Math.max(NODE_H * 0.6f, (safeCount - 1) * minGap));
        float startY = p.y + NODE_H * 0.5f - spread * 0.5f;
        return new PointF(p.x + NODE_W + dp(8), startY + safeIndex * (spread / Math.max(1, safeCount - 1)));
    }

    private float switchBranchRailTop(String id, int slotCount) {
        return switchBranchPort(id, 0, slotCount).y;
    }

    private float switchBranchRailBottom(String id, int slotCount) {
        return switchBranchPort(id, Math.max(0, slotCount - 1), slotCount).y;
    }

    /** Input port — top-center of node */
    private PointF inputPort(String id) {
        PointF p = nodePositions.get(id);
        if (p == null) return new PointF(0, 0);
        return new PointF(p.x + NODE_W * 0.5f, p.y);
    }

    private boolean nearPort(float wx, float wy, PointF port) {
        float dx = wx - port.x, dy = wy - port.y;
        return dx * dx + dy * dy <= PORT_HIT * PORT_HIT;
    }

    private boolean isSwitchBranchNode(@Nullable Node node) {
        return node != null && node.typeCode == 15;
    }

    @Nullable
    private String findNodeNear(float wx, float wy) {
        for (Node n : nodes) {
            RectF r = nodeRects.get(n.id);
            if (r != null && r.contains(wx, wy)) return n.id;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════
    //  Touch handling
    // ═══════════════════════════════════════════════════════

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        scaleDetector.onTouchEvent(ev);
        gestureDetector.onTouchEvent(ev);

        float sx = ev.getX(), sy = ev.getY();
        float wx = (sx - offsetX) / scale, wy = (sy - offsetY) / scale;

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downScreenX  = sx; downScreenY = sy;
                lastTouchX   = sx; lastTouchY  = sy;
                didMove      = false;
                cancelPendingLongPress();

                if (!interactionReadOnly) {
                    // Port hit check first
                    for (Node n : nodes) {
                        if (nearPort(wx, wy, mainPort(n.id))) {
                            dragMode      = DragMode.CONNECT_MAIN;
                            connectFromId = n.id;
                            connectTipX   = wx; connectTipY = wy;
                            return true;
                        }
                        if (!isSwitchBranchNode(n) && nearPort(wx, wy, fallPort(n.id))) {
                            dragMode      = DragMode.CONNECT_FALLBACK;
                            connectFromId = n.id;
                            connectTipX   = wx; connectTipY = wy;
                            return true;
                        }
                    }

                    // Node body → schedule long-press for MOVE_NODE
                    String nodeId = findNodeNear(wx, wy);
                    longPressCandidateId = nodeId;
                    if (nodeId != null) {
                        longPressRunnable = () -> {
                            if (!didMove) {
                                dragMode        = DragMode.MOVE_NODE;
                                draggingNodeId  = longPressCandidateId;
                                vibrate();
                                invalidateView();
                            }
                        };
                        longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                    }
                }

                dragMode = DragMode.NONE;
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (ev.getPointerCount() > 1) {
                    // Pinch-zoom handled by ScaleDetector; cancel other modes
                    cancelPendingLongPress();
                    dragMode = DragMode.NONE;
                    lastTouchX = sx; lastTouchY = sy;
                    return true;
                }

                float dsx = sx - lastTouchX, dsy = sy - lastTouchY;
                float distFromDown = dist(sx - downScreenX, sy - downScreenY);
                if (distFromDown > dp(MOVE_SLOP_DP)) {
                    didMove = true;
                    cancelPendingLongPress();
                }

                switch (dragMode) {
                    case CONNECT_MAIN:
                    case CONNECT_FALLBACK:
                        connectTipX = wx; connectTipY = wy;
                        invalidateView();
                        break;

                    case MOVE_NODE:
                        if (draggingNodeId != null) {
                            PointF p = nodePositions.get(draggingNodeId);
                            if (p != null) { p.x += dsx / scale; p.y += dsy / scale; }
                            updateRects();
                            invalidateView();
                        }
                        break;

                    default:
                        if (didMove && !scaleDetector.isInProgress()) {
                            dragMode  = DragMode.PAN;
                            offsetX  += dsx;
                            offsetY  += dsy;
                            invalidateView();
                        }
                        break;
                }

                lastTouchX = sx; lastTouchY = sy;
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                cancelPendingLongPress();

                if (dragMode == DragMode.CONNECT_MAIN || dragMode == DragMode.CONNECT_FALLBACK) {
                    boolean isFallback = (dragMode == DragMode.CONNECT_FALLBACK);
                    String target = findNodeNear(wx, wy);
                    if (target != null && !target.equals(connectFromId) && connectListener != null) {
                        connectListener.onConnect(connectFromId, target, isFallback);
                    }
                }

                if (dragMode == DragMode.MOVE_NODE) {
                    draggingNodeId = null;
                }

                if (!didMove && dragMode == DragMode.NONE) {
                    handleTap(wx, wy);
                }

                dragMode = DragMode.NONE;
                invalidateView();
                return true;
            }
        }
        return super.onTouchEvent(ev);
    }

    private void handleTap(float wx, float wy) {
        int jumpDirection = selectedRoute == null ? 0 : hitJumpButton(wx, wy);
        if (selectedRoute != null && jumpDirection != 0) {
            String nodeId = jumpDirection < 0 ? selectedRoute.fromId : selectedRoute.toId;
            centerOnNode(nodeId);
            setSelectedNodeId(nodeId);
            return;
        }
        // Nodes take priority (rendered on top)
        String nodeId = findNodeNear(wx, wy);
        if (nodeId != null) {
            selectedRoute = null;
            selectedRouteActionCenter = null;
            setSelectedNodeId(nodeId);
            return;
        }
        // Check connection lines
        ConnRoute conn = findConnectionNear(wx, wy);
        if (conn != null) {
            selectedRoute = (selectedRoute == conn) ? null : conn; // toggle
            selectedRouteActionCenter = selectedRoute == null ? null : new PointF(wx, wy);
            selectedNodeId = null;
            invalidateView();
            if (selectListener != null) selectListener.onSelected(null);
            return;
        }
        // Tap on empty canvas
        selectedRoute = null;
        selectedRouteActionCenter = null;
        setSelectedNodeId(null);
    }

    private int hitJumpButton(float wx, float wy) {
        PointF c = selectedRouteActionCenter;
        if (c == null) {
            return 0;
        }
        float radius = dp(14);
        float offset = dp(18);
        if (dist(wx - (c.x - offset), wy - c.y) <= radius) {
            return -1;
        }
        if (dist(wx - (c.x + offset), wy - c.y) <= radius) {
            return 1;
        }
        return 0;
    }

    private void centerOnNode(@Nullable String nodeId) {
        RectF rect = nodeId == null ? null : nodeRects.get(nodeId);
        if (rect == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        offsetX = getWidth() * 0.5f - rect.centerX() * scale;
        offsetY = getHeight() * 0.5f - rect.centerY() * scale;
        invalidateView();
    }

    private void cancelPendingLongPress() {
        if (longPressRunnable != null) {
            longPressHandler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(30);
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════
    //  Drawing
    // ═══════════════════════════════════════════════════════

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (nodes.isEmpty()) {
            canvas.drawText("暂无流程节点，请先在 Operation 列表中添加操作",
                    getWidth() / 2f, getHeight() / 2f, pEmpty);
            return;
        }

        if (!positionsReady && getWidth() > 0) initPositions();

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        // 1. Draw connections (under nodes)
        drawConnections(canvas);
        if (selectedRoute != null) {
            drawRouteJumpButton(canvas, selectedRoute);
        }

        // 2. Draw in-progress connection line
        if ((dragMode == DragMode.CONNECT_MAIN || dragMode == DragMode.CONNECT_FALLBACK)
                && connectFromId != null) {
            PointF from = (dragMode == DragMode.CONNECT_FALLBACK)
                    ? fallPort(connectFromId) : mainPort(connectFromId);
            drawPreviewConn(canvas, from.x, from.y, connectTipX, connectTipY);
        }

        // 3. Draw nodes (on top)
        for (Node n : nodes) {
            drawNode(canvas, n);
        }

        // 4. Port dots (above nodes)
        for (Node n : nodes) {
            drawPorts(canvas, n);
        }

        canvas.restore();
    }

    // ═══════════════════════════════════════════════════════
    //  Orthogonal connection routing
    // ═══════════════════════════════════════════════════════

    /**
     * Rebuilds all routed connections from the current node graph.
     *
     * Routing strategy:
     *  - Adjacent forward connections (span=1, same column) → straight vertical line.
     *  - All other connections → right-side bypass lane.
     *    Each bypass connection gets the innermost available lane whose Y-interval
     *    does not conflict with any already-assigned connection (interval-graph coloring).
     *    This guarantees no two connections share a visible vertical lane segment.
     */
    private void rebuildRoutes() {
        routes.clear();
        if (!positionsReady || nodes.isEmpty()) return;

        List<ConnRoute> all    = new ArrayList<>();
        List<ConnRoute> bypass = new ArrayList<>();

        for (Node n : nodes) {
            if (!TextUtils.isEmpty(n.nextId) && nodePositions.containsKey(n.nextId)) {
                Node tgt = findById(n.nextId);
                if (tgt != null) {
                    ConnRoute r = new ConnRoute();
                    r.fromId = n.id; r.toId = n.nextId; r.kind = "next"; r.fromFallbackPort = false;
                    r.span = tgt.order - n.order;
                    all.add(r);
                }
            }
            if (!TextUtils.isEmpty(n.fallbackId) && nodePositions.containsKey(n.fallbackId)) {
                Node tgt = findById(n.fallbackId);
                if (tgt != null) {
                    ConnRoute r = new ConnRoute();
                    r.fromId = n.id; r.toId = n.fallbackId; r.kind = "fallback"; r.fromFallbackPort = true;
                    r.span = tgt.order - n.order;
                    all.add(r);
                }
            }
            for (Node.Edge edge : n.extraEdges) {
                if (edge == null || TextUtils.isEmpty(edge.toId) || !nodePositions.containsKey(edge.toId)) {
                    continue;
                }
                Node tgt = findById(edge.toId);
                if (tgt == null) {
                    continue;
                }
                ConnRoute r = new ConnRoute();
                r.fromId = n.id;
                r.toId = edge.toId;
                r.kind = TextUtils.isEmpty(edge.kind) ? "branch" : edge.kind;
                r.fromFallbackPort = edge.fromFallbackPort;
                r.sourceSlotIndex = edge.sourceSlotIndex;
                r.sourceSlotCount = edge.sourceSlotCount;
                r.span = tgt.order - n.order;
                all.add(r);
            }
        }

        Map<String, Integer> outgoingPerPort = new HashMap<>();
        for (ConnRoute r : all) {
            String portKey = portKey(r);
            outgoingPerPort.put(portKey, outgoingPerPort.getOrDefault(portKey, 0) + 1);
        }

        // Classify: direct vs bypass
        for (ConnRoute r : all) {
            PointF from = portFrom(r);
            PointF to   = inputPort(r.toId);
            String portKey = portKey(r);
            boolean multiOutgoingSamePort = outgoingPerPort.getOrDefault(portKey, 0) > 1;
            boolean sameCol  = Math.abs(from.x - to.x) < NODE_W * 0.35f;
            boolean forward  = to.y > from.y;
            boolean adjacent = forward && (to.y - from.y) < (NODE_H + NODE_GAP) * 1.3f;
            if (isSwitchBranchRoute(r)) {
                bypass.add(r);
            } else if (!multiOutgoingSamePort && sameCol && adjacent) {
                r.lane = -1; // direct
            } else {
                bypass.add(r);
            }
        }

        // Assign minimum non-conflicting lane index to each bypass connection
        for (ConnRoute r : bypass) {
            r.lane = findMinLane(r, bypass);
        }

        // Compute right edge of all nodes for lane base
        float rightEdge = 0;
        for (Node n : nodes) {
            PointF p = nodePositions.get(n.id);
            if (p != null) rightEdge = Math.max(rightEdge, p.x + NODE_W);
        }
        float laneBase = rightEdge + dp(LANE_BASE_DP);
        float laneW    = dp(LANE_W_DP);
        float exitGap  = dp(EXIT_DP);
        float enterGap = dp(ENTER_DP);

        // Build waypoints
        for (ConnRoute r : all) {
            PointF from = portFrom(r);
            PointF to   = inputPort(r.toId);
            r.pts.clear();

            if (r.lane < 0) {
                // Direct: vertical, with optional horizontal jog if columns differ
                r.pts.add(new PointF(from.x, from.y));
                if (Math.abs(from.x - to.x) > dp(3)) {
                    float midY = (from.y + to.y) * 0.5f;
                    r.pts.add(new PointF(from.x, midY));
                    r.pts.add(new PointF(to.x,   midY));
                }
                r.pts.add(new PointF(to.x, to.y));
            } else {
                float laneX  = laneBase + r.lane * laneW;
                float eyTop  = from.y + exitGap;
                float eyBot  = to.y   - enterGap;
                // Guard: if nodes are too close forward (eyBot < eyTop), degrade to T-shape
                if (to.y > from.y && eyBot < eyTop) {
                    float midY = (from.y + to.y) * 0.5f;
                    r.pts.add(new PointF(from.x, from.y));
                    r.pts.add(new PointF(from.x, midY));
                    r.pts.add(new PointF(to.x,   midY));
                    r.pts.add(new PointF(to.x,   to.y));
                } else {
                    // Standard 5-segment orthogonal: down → right → up/down → left → down
                    r.pts.add(new PointF(from.x, from.y));
                    r.pts.add(new PointF(from.x, eyTop));
                    r.pts.add(new PointF(laneX,  eyTop));
                    r.pts.add(new PointF(laneX,  eyBot));
                    r.pts.add(new PointF(to.x,   eyBot));
                    r.pts.add(new PointF(to.x,   to.y));
                }
            }
        }
        routes.addAll(all);
    }

    /**
     * Returns the smallest lane index whose vertical Y-interval doesn't overlap
     * with any already-assigned connection in {@code all}.
     */
    private int findMinLane(ConnRoute target, List<ConnRoute> all) {
        PointF tf = portFrom(target);
        PointF tt = inputPort(target.toId);
        float tYMin = Math.min(tf.y, tt.y);
        float tYMax = Math.max(tf.y, tt.y);

        java.util.Set<Integer> occupied = new java.util.HashSet<>();
        for (ConnRoute other : all) {
            if (other == target || other.lane < 0) continue;
            PointF ef = portFrom(other);
            PointF et = inputPort(other.toId);
            float eYMin = Math.min(ef.y, et.y);
            float eYMax = Math.max(ef.y, et.y);
            if (tYMin < eYMax && tYMax > eYMin) occupied.add(other.lane);
        }
        int lane = 0;
        while (occupied.contains(lane)) lane++;
        return lane;
    }

    private PointF portFrom(ConnRoute r) {
        if (isSwitchBranchRoute(r)) {
            return switchBranchPort(r.fromId, r.sourceSlotIndex, r.sourceSlotCount);
        }
        return r.fromFallbackPort ? fallPort(r.fromId) : mainPort(r.fromId);
    }

    private boolean isSwitchBranchRoute(ConnRoute route) {
        if (route == null || !"branch".equals(route.kind)) {
            return false;
        }
        Node node = findById(route.fromId);
        return isSwitchBranchNode(node) && route.sourceSlotIndex >= 0 && route.sourceSlotCount > 0;
    }

    private String portKey(ConnRoute route) {
        if (isSwitchBranchRoute(route)) {
            return route.fromId + "|SB|" + route.sourceSlotIndex;
        }
        return route.fromId + "|" + (route.fromFallbackPort ? "F" : "N");
    }

    // ── Drawing ──────────────────────────────────────────

    private void drawConnections(Canvas canvas) {
        for (ConnRoute r : routes) {
            if (r == selectedRoute) continue;
            drawSingleRoute(canvas, r, false, selectedRoute != null);
        }
        // Draw selected connection last so it renders on top
        if (selectedRoute != null) drawSingleRoute(canvas, selectedRoute, true, false);
    }

    private void drawSingleRoute(Canvas canvas, ConnRoute r, boolean selected, boolean dimmed) {
        if (r.pts.size() < 2) return;
        Paint line = paintForRoute(r);
        int originalAlpha = line.getAlpha();
        if (dimmed) {
            line.setAlpha(DIMMED_ALPHA);
        }
        if (selected) {
            drawRoutePath(canvas, r.pts, pHaloConn); // wide glow underneath
        }
        drawRoutePath(canvas, r.pts, line);
        PointF last = r.pts.get(r.pts.size() - 1);
        drawArrow(canvas, last.x, last.y, line);
        line.setAlpha(originalAlpha);
    }

    private Paint paintForRoute(ConnRoute route) {
        if ("fallback".equals(route.kind)) {
            return pFallLine;
        }
        if ("branch".equals(route.kind)) {
            return pBranchLine;
        }
        return pMainLine;
    }

    private void drawRouteJumpButton(Canvas canvas, ConnRoute route) {
        PointF c = selectedRouteActionCenter;
        if (c == null) {
            return;
        }
        float radius = dp(14);
        float offset = dp(18);
        float pillHalfW = dp(28);
        float pillHalfH = dp(16);
        tempRect.set(c.x - pillHalfW, c.y - pillHalfH, c.x + pillHalfW, c.y + pillHalfH);
        canvas.drawRoundRect(tempRect, dp(16), dp(16), pJumpFill);
        canvas.drawLine(c.x, c.y - dp(10), c.x, c.y + dp(10), pJumpDivider);
        canvas.drawCircle(c.x - offset, c.y, radius, pJumpFill);
        canvas.drawCircle(c.x + offset, c.y, radius, pJumpFill);
        canvas.drawText("←", c.x - offset, c.y + dp(4), pJumpText);
        canvas.drawText("→", c.x + offset, c.y + dp(4), pJumpText);
    }

    private void drawRoutePath(Canvas canvas, List<PointF> pts, Paint paint) {
        reusablePath.reset();
        reusablePath.moveTo(pts.get(0).x, pts.get(0).y);
        for (int i = 1; i < pts.size(); i++) reusablePath.lineTo(pts.get(i).x, pts.get(i).y);
        canvas.drawPath(reusablePath, paint);
    }

    /** Orthogonal preview line drawn while the user drags a new connection. */
    private void drawPreviewConn(Canvas canvas, float fx, float fy, float tx, float ty) {
        reusablePath.reset();
        reusablePath.moveTo(fx, fy);
        float midY = (fy + ty) * 0.5f;
        reusablePath.lineTo(fx, midY);
        reusablePath.lineTo(tx, midY);
        reusablePath.lineTo(tx, ty);
        canvas.drawPath(reusablePath, pConnLine);
    }

    // ── Connection hit testing ───────────────────────────

    @Nullable
    private ConnRoute findConnectionNear(float wx, float wy) {
        float tol = dp(CONN_HIT_DP);
        ConnRoute best  = null;
        float     bestD = Float.MAX_VALUE;
        for (ConnRoute r : routes) {
            for (int i = 0; i < r.pts.size() - 1; i++) {
                PointF a = r.pts.get(i), b = r.pts.get(i + 1);
                float d = distToSegment(wx, wy, a.x, a.y, b.x, b.y);
                if (d < tol && d < bestD) { bestD = d; best = r; }
            }
        }
        return best;
    }

    private float distToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float lenSq = dx * dx + dy * dy;
        if (lenSq < 1f) return dist(px - ax, py - ay);
        float t = Math.max(0f, Math.min(1f, ((px - ax) * dx + (py - ay) * dy) / lenSq));
        return dist(px - (ax + t * dx), py - (ay + t * dy));
    }

    private void drawArrow(Canvas canvas, float tipX, float tipY, Paint paint) {
        // Arrow pointing straight down into the input port
        android.graphics.PathEffect savedEffect = paint.getPathEffect();
        paint.setPathEffect(null); // no dash for arrowhead
        paint.setStyle(Paint.Style.FILL);
        reusablePath.reset();
        reusablePath.moveTo(tipX, tipY);
        reusablePath.lineTo(tipX - ARROW_W / 2f, tipY - ARROW_H);
        reusablePath.lineTo(tipX + ARROW_W / 2f, tipY - ARROW_H);
        reusablePath.close();
        canvas.drawPath(reusablePath, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(savedEffect);
    }

    private void drawNode(Canvas canvas, Node n) {
        RectF r = nodeRects.get(n.id);
        if (r == null) return;

        boolean isSelected    = TextUtils.equals(selectedNodeId,    n.id);
        boolean isHighlighted = TextUtils.equals(highlightedNodeId, n.id);
        boolean isDragging    = TextUtils.equals(draggingNodeId,    n.id);

        // Slight lift while dragging
        if (isDragging) canvas.save();
        if (isDragging) canvas.scale(1.04f, 1.04f, r.centerX(), r.centerY());

        // Shadow + card background
        canvas.drawRoundRect(r, CORNER_R, CORNER_R, pNodeBg);

        // Running state overlay
        if (isHighlighted) {
            canvas.drawRoundRect(r, CORNER_R, CORNER_R, pRunFill);
            canvas.drawRoundRect(r, CORNER_R, CORNER_R, pRunStroke);
        } else if (isSelected) {
            canvas.drawRoundRect(r, CORNER_R, CORNER_R, pSelStroke);
        } else {
            canvas.drawRoundRect(r, CORNER_R, CORNER_R, pStroke);
        }

        // Left color bar (clip to left rounded region)
        int typeColor = typeColor(n.type);
        pBar.setColor(typeColor);
        tempRect.set(r.left, r.top, r.left + BAR_W, r.bottom);
        canvas.save();
        canvas.clipRect(tempRect);
        canvas.drawRoundRect(r, CORNER_R, CORNER_R, pBar);
        canvas.restore();

        float textLeft = r.left + BAR_W + dp(42);
        float textMaxW = r.width() - BAR_W - dp(42) - dp(8);

        // Index badge (circle)
        float badgeR   = dp(14);
        float badgeCX  = r.left + BAR_W + dp(8) + badgeR;
        float badgeCY  = r.top  + NODE_H / 2f;
        pIdxBg.setColor(typeColor);
        canvas.drawCircle(badgeCX, badgeCY, badgeR, pIdxBg);
        canvas.drawText(formatOrder(n.order), badgeCX, badgeCY + dp(4), pIdxTxt);

        // Operation name
        float titleY = r.top + NODE_H * 0.40f;
        canvas.drawText(ellipsize(safe(n.name), textMaxW, pTitle),
                textLeft, titleY, pTitle);

        // Type + ID subtext
        String sub = safe(n.type) + "  " + shortId(n.id);
        canvas.drawText(ellipsize(sub, textMaxW, pSub),
                textLeft, r.top + NODE_H * 0.72f, pSub);

        if (isDragging) canvas.restore();
    }

    private void drawPorts(Canvas canvas, Node n) {
        if (interactionReadOnly) {
            return;
        }
        PointF mp = mainPort(n.id);

        // White ring + colored fill
        canvas.drawCircle(mp.x, mp.y, PORT_R + dp(2), pPortRing);
        canvas.drawCircle(mp.x, mp.y, PORT_R, pPortMain);

        if (isSwitchBranchNode(n) && !n.extraEdges.isEmpty()) {
            int branchCount = 0;
            for (Node.Edge edge : n.extraEdges) {
                if (edge != null && edge.sourceSlotIndex >= 0) {
                    branchCount = Math.max(branchCount, edge.sourceSlotCount);
                }
            }
            branchCount = Math.max(branchCount, n.extraEdges.size());
            if (branchCount > 1) {
                float railX = switchBranchPort(n.id, 0, branchCount).x;
                float topY = switchBranchRailTop(n.id, branchCount);
                float bottomY = switchBranchRailBottom(n.id, branchCount);
                canvas.drawLine(railX, topY, railX, bottomY, pBranchRail);
            }
            int visibleDots = branchCount > 12 ? 3 : branchCount;
            for (int i = 0; i < visibleDots; i++) {
                int slotIndex;
                if (visibleDots == branchCount) {
                    slotIndex = i;
                } else if (i == 0) {
                    slotIndex = 0;
                } else if (i == visibleDots - 1) {
                    slotIndex = branchCount - 1;
                } else {
                    slotIndex = branchCount / 2;
                }
                PointF bp = switchBranchPort(n.id, slotIndex, branchCount);
                canvas.drawCircle(bp.x, bp.y, PORT_R + dp(2), pPortRing);
                canvas.drawCircle(bp.x, bp.y, PORT_R, pBranchDot);
            }
            if (branchCount > 12) {
                float badgeCx = switchBranchPort(n.id, branchCount / 2, branchCount).x + dp(18);
                float badgeCy = n.extraEdges.isEmpty() ? mp.y : switchBranchPort(n.id, branchCount / 2, branchCount).y;
                tempRect.set(badgeCx - dp(16), badgeCy - dp(10), badgeCx + dp(16), badgeCy + dp(10));
                canvas.drawRoundRect(tempRect, dp(10), dp(10), pBranchCountBg);
                canvas.drawText(String.valueOf(branchCount), badgeCx, badgeCy + dp(4), pJumpText);
            }
        } else {
            PointF fp = fallPort(n.id);
            canvas.drawCircle(fp.x, fp.y, PORT_R + dp(2), pPortRing);
            canvas.drawCircle(fp.x, fp.y, PORT_R, pPortFall);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════

    private int typeColor(String type) {
        if (type == null) return 0xFF78909C;
        switch (type) {
            case "点击":   return 0xFF1E88E5;
            case "延时":   return 0xFFFB8C00;
            case "手势":   return 0xFF8E24AA;
            case "截图区域": return 0xFF039BE5;
            case "加载图":  return 0xFF546E7A;
            case "模板匹配": return 0xFF00897B;
            case "地图匹配": return 0xFF00695C;
            case "跳转Task": return 0xFFE53935;
            case "OCR":    return 0xFF3949AB;
            case "条件分支": return 0xFFFF6F00;
            case "变量脚本": return 0xFFAD1457;
            case "变量计算": return 0xFF6D4C41;
            case "变量模板": return 0xFF00838F;
            case "启动应用": return 0xFF43A047;
            default:       return 0xFF78909C;
        }
    }

    private @Nullable Node findById(@Nullable String id) {
        if (TextUtils.isEmpty(id)) return null;
        return nodeMap.get(id);
    }

    private String safe(String s)  { return s == null ? "" : s; }
    private String formatOrder(int order) {
        if (order >= 0 && order < 10) {
            return "0" + order;
        }
        return String.valueOf(order);
    }
    private String shortId(String s) {
        if (s == null || s.length() <= 8) return safe(s);
        return s.substring(0, 8) + "…";
    }

    private float dp(float v) { return v * density; }

    private static float dist(float dx, float dy) { return (float) Math.sqrt(dx * dx + dy * dy); }

    private String ellipsize(String text, float maxW, Paint p) {
        if (TextUtils.isEmpty(text) || p.measureText(text) <= maxW) return safe(text);
        TextPaint textPaint = p == pTitle ? titleTextPaint : subTextPaint;
        return TextUtils.ellipsize(text, textPaint, maxW, TextUtils.TruncateAt.END).toString();
    }

    // ═══════════════════════════════════════════════════════
    //  Gesture listeners
    // ═══════════════════════════════════════════════════════

    private class TapListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (doubleTapListener == null) return false;
            float wx = (e.getX() - offsetX) / scale;
            float wy = (e.getY() - offsetY) / scale;
            String id = findNodeNear(wx, wy);
            if (id != null) {
                Node n = findById(id);
                if (n != null) { doubleTapListener.onNodeDoubleTap(n); return true; }
            }
            return false;
        }
    }

    private class PinchListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector d) {
            float factor = d.getScaleFactor();
            float newScale = Math.max(0.4f, Math.min(scale * factor, 3.0f));
            // Zoom toward pinch center
            float focusX = d.getFocusX(), focusY = d.getFocusY();
            offsetX = focusX - (focusX - offsetX) * (newScale / scale);
            offsetY = focusY - (focusY - offsetY) * (newScale / scale);
            scale   = newScale;
            invalidateView();
            return true;
        }
    }

    private void invalidateView() {
        postInvalidateOnAnimation();
    }

}
