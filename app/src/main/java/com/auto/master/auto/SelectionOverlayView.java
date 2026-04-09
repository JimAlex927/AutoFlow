package com.auto.master.auto;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.os.SystemClock;
import androidx.annotation.Nullable;

public class SelectionOverlayView extends FrameLayout {

    // ===================== 兼容旧接口（你旧代码里的 setOnRegionSelectedListener） =====================
    public interface OnRegionSelectedListener {
        void onSelected(Rect rect, Bitmap bitmap);
    }
    private OnRegionSelectedListener oldListener;

    /** 兼容旧代码：框选结束立刻回调（这里改为点击“确定”才回调旧接口，以更符合 Windows 风格） */
    public void setOnRegionSelectedListener(OnRegionSelectedListener l) {
        this.oldListener = l;
    }

    // ===================== 新接口（确认/取消） =====================
    public interface Listener {
        void onConfirm(Rect rectInOverlay, Bitmap croppedBitmap);

        void onCancel();
    }
    public interface OnRefineRequestedListener {
        void onRefineRequested(Rect rectInOverlay);
    }
    private Listener listener;
    private OnRefineRequestedListener refineRequestedListener;
    private boolean refineEnabled = false;

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setOnRefineRequestedListener(@Nullable OnRefineRequestedListener listener) {
        this.refineRequestedListener = listener;
    }

    public void setRefineEnabled(boolean enabled) {
        this.refineEnabled = enabled;
        if (btnRefine != null) {
            btnRefine.setVisibility(enabled ? VISIBLE : GONE);
        }
    }

    // ===================== 绘制相关 =====================
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Rect selection = new Rect();
    private boolean hasSelection = false;

    private int topInsetPx = 0;

    // buttons
    private LinearLayout actionBar;
    private Button btnOk, btnCancel, btnReset, btnFull, btnRefine;

    // magnifier (Android 9+)
    private android.widget.Magnifier magnifier;

    // interaction
    private enum Mode { NONE, CREATE, MOVE, RESIZE_LT, RESIZE_RT, RESIZE_LB, RESIZE_RB, RESIZE_L, RESIZE_R, RESIZE_T, RESIZE_B }
    private Mode mode = Mode.NONE;

    private float downX, downY;
    private final Rect startRect = new Rect();

    // sizes
    private int handleRadius;
    private int handleTouchSlop;
    private int minSize;
    private int borderWidth;
    private int precisionMagnifierThreshold;

    private final OverlayCanvasView canvasView;

    private Bitmap frozenBackground;   // 新增

    public SelectionOverlayView(Context context) {
        this(context, null);
    }

    private long lastMoveUiTs = 0L;
    private long lastMagTs = 0L;
    // 或通过 setter
    public void setFrozenBackground(Bitmap bmp) {
        if (this.frozenBackground != bmp && this.frozenBackground != null) {
            this.frozenBackground.recycle();
        }
        this.frozenBackground = bmp;
        canvasView.invalidate();
    }

    public SelectionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        setWillNotDraw(false);
        setClickable(true);
        setFocusable(false);

        handleRadius = dp(6);
        handleTouchSlop = dp(18);
        //那个框的大小限制
        minSize = dp(10);
        borderWidth = dp(2);
        precisionMagnifierThreshold = dp(96);

        dimPaint.setColor(0x88000000);
        dimPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(borderWidth);

        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        // 画布层
        canvasView = new OverlayCanvasView(context);
        addView(canvasView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 按钮条
        actionBar = new LinearLayout(context);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        actionBar.setBackground(makeRoundBg(0xCC222222, dp(10)));
        actionBar.setVisibility(GONE);

        btnCancel = new Button(context);
        btnCancel.setText("取消");
        btnReset = new Button(context);
        btnReset.setText("重选");
        btnFull = new Button(context);
        btnFull.setText("全屏");
        btnRefine = new Button(context);
        btnRefine.setText("精选");
        btnOk = new Button(context);
        btnOk.setText("确定");

        btnCancel.setMinHeight(0);
        btnReset.setMinHeight(0);
        btnFull.setMinHeight(0);
        btnRefine.setMinHeight(0);
        btnOk.setMinHeight(0);
        btnCancel.setMinimumHeight(0);
        btnReset.setMinimumHeight(0);
        btnFull.setMinimumHeight(0);
        btnRefine.setMinimumHeight(0);
        btnOk.setMinimumHeight(0);
        btnCancel.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnReset.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnFull.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnRefine.setPadding(dp(10), dp(6), dp(10), dp(6));
        btnOk.setPadding(dp(10), dp(6), dp(10), dp(6));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        actionBar.addView(btnCancel, lp);

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp2.leftMargin = dp(8);
        actionBar.addView(btnReset, lp2);

        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp3.leftMargin = dp(8);
        actionBar.addView(btnFull, lp3);

        LinearLayout.LayoutParams lp4 = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp4.leftMargin = dp(8);
        actionBar.addView(btnRefine, lp4);

        LinearLayout.LayoutParams lp5 = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp5.leftMargin = dp(8);
        actionBar.addView(btnOk, lp5);

        btnRefine.setVisibility(GONE);

        addView(actionBar, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        btnCancel.setOnClickListener(v -> {
            hideMagnifier();
            if (listener != null) listener.onCancel();
            // 兼容旧接口：取消不回调 onSelected
        });

        btnReset.setOnClickListener(v -> {
            hasSelection = false;
            selection.setEmpty();
            actionBar.setVisibility(GONE);
            hideMagnifier();
            canvasView.invalidate();
        });

        btnFull.setOnClickListener(v -> {
            selection.set(0, 0, getWidth(), getHeight());
            hasSelection = true;
            positionActionBarNearSelection();
            hideMagnifier();
            canvasView.invalidate();
        });

        btnOk.setOnClickListener(v -> {
            if (!hasSelection) return;
            hideMagnifier();
            Rect normalized = new Rect(selection);
            normalize(normalized);

            // 动态背景：不在 View 内截图，交给外部在后台截全屏再裁剪
            if (listener != null) listener.onConfirm(normalized, null);
            if (oldListener != null) oldListener.onSelected(normalized, null);
        });

        btnRefine.setOnClickListener(v -> {
            if (!hasSelection || !refineEnabled || refineRequestedListener == null) {
                return;
            }
            hideMagnifier();
            Rect normalized = new Rect(selection);
            normalize(normalized);
            refineRequestedListener.onRefineRequested(normalized);
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            magnifier = new android.widget.Magnifier(canvasView);
        }

        setFitsSystemWindows(true);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            topInsetPx = bars.top;
        } else {
            topInsetPx = insets.getSystemWindowInsetTop();
        }
        Log.d("SelectionOverlayView", "topInsetPx=" + topInsetPx);
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
    }

    // ===================== 内部画布 View =====================
    private class OverlayCanvasView extends View {

        private int screenWidth = 0;
        private int screenHeight = 0;


        private boolean frameScheduled = false;
        private float pendingX, pendingY;
        private boolean actionBarHidden = false;
        private void hideActionBarIfNeeded() {
            if (!actionBarHidden) {
                actionBar.setVisibility(GONE);
                actionBarHidden = true;
            }
        }

        private void scheduleFrameUpdate() {
            if (frameScheduled) return;
            frameScheduled = true;
            postOnAnimation(this::runFrameUpdate);
        }

        private void runFrameUpdate() {
            frameScheduled = false;

            if (mode == Mode.NONE) {
                postInvalidateOnAnimation();
                return;
            }

            final float x = pendingX;
            final float y = pendingY;

            final float dx = x - downX;
            final float dy = y - downY;

            if (mode == Mode.CREATE) {
                selection.left = (int) Math.min(downX, x);
                selection.top = (int) Math.min(downY, y);
                selection.right = (int) Math.max(downX, x);
                selection.bottom = (int) Math.max(downY, y);

            } else if (mode == Mode.MOVE) {
                // 必须基于 startRect
                selection.set(startRect);
                selection.offset((int) dx, (int) dy);

            } else {
                selection.set(startRect);
                resizeByMode(selection, mode, x, y);
            }

            // MOVE 时做轻量 clamp，确保不飞出屏幕
            clampToBounds(selection);

            postInvalidateOnAnimation();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            screenWidth = w;
            screenHeight = h;
        }
        public OverlayCanvasView(Context context) { super(context); }

        // 记录最后一次有效重绘的时间
        private long lastInvalidateTime = 0;
        private static final long MIN_INVALIDATE_INTERVAL_MS = 33;  // ≈30fps，横屏最稳
        // 如果想更丝滑可改 16（60fps），但容易掉帧；卡就改 50（20fps）

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            final int w = getWidth();
            final int h = getHeight();

            if (frozenBackground != null && !frozenBackground.isRecycled()) {
                canvas.drawBitmap(frozenBackground, null, new Rect(0, 0, w, h), null);
            }

            if (!hasSelection || selection.isEmpty() || selection.width() <= 0 || selection.height() <= 0) {
                // 没选区：整屏遮罩（底下动态透出）
                canvas.drawRect(0, 0, w, h, dimPaint);
                return;
            }

            int l = selection.left;
            int t = selection.top;
            int r = selection.right;
            int b = selection.bottom;

            // 四边遮罩（极快）
            canvas.drawRect(0, 0, w, t, dimPaint);   // 上
            canvas.drawRect(0, b, w, h, dimPaint);   // 下
            canvas.drawRect(0, t, l, b, dimPaint);   // 左
            canvas.drawRect(r, t, w, b, dimPaint);   // 右

            canvas.drawRect(selection, borderPaint);
        }

        private void maybeUpdateMagnifier(long now, float x, float y) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || magnifier == null) {
                return;
            }
            if (now - lastMagTs < 16L) {
                return;
            }
            lastMagTs = now;

            if (!shouldShowPrecisionMagnifier()) {
                hideMagnifier();
                return;
            }

            float clampedX = Math.max(0f, Math.min(x, getWidth()));
            float clampedY = Math.max(0f, Math.min(y, getHeight()));
            showMagnifier(clampedX, clampedY);
        }

        private boolean shouldShowPrecisionMagnifier() {
            if (mode == Mode.NONE) {
                return false;
            }
            if (!hasSelection || mode == Mode.CREATE) {
                return true;
            }
            if (mode != Mode.MOVE) {
                return selection.width() <= precisionMagnifierThreshold
                        || selection.height() <= precisionMagnifierThreshold;
            }
            return selection.width() <= precisionMagnifierThreshold / 2
                    || selection.height() <= precisionMagnifierThreshold / 2;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final float x = event.getX();
            final float y = event.getY();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    downX = x;
                    downY = y;
                    startRect.set(selection);

                    if (!hasSelection) {
                        hasSelection = true;
                        mode = Mode.CREATE;
                        selection.set((int) x, (int) y, (int) x, (int) y);
                        hideActionBarIfNeeded();
                    } else {
                        mode = hitTest(x, y);
                        if (mode == Mode.NONE) {
                            mode = Mode.CREATE;
                            selection.set((int) x, (int) y, (int) x, (int) y);
                            hideActionBarIfNeeded();
                        }
                    }

                    // 记录最新点，并安排一帧刷新
                    pendingX = x;
                    pendingY = y;
                    maybeUpdateMagnifier(SystemClock.uptimeMillis(), x, y);
                    scheduleFrameUpdate();
                    return true;
                }

                case MotionEvent.ACTION_MOVE: {
                    if (mode == Mode.NONE) return true;

                    // MOVE 不做任何重活：只记录最新坐标 + 确保帧回调已安排
                    pendingX = x;
                    pendingY = y;
                    hideActionBarIfNeeded();
                    maybeUpdateMagnifier(SystemClock.uptimeMillis(), x, y);
                    scheduleFrameUpdate();
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    // 结束时，强制用“最后位置”做一次最终更新
                    pendingX = x;
                    pendingY = y;
                    runFrameUpdate(); // 立即更新一次（不等下一帧）
                    hideMagnifier();

                    // 收尾：完整修正
                    if (hasSelection) {
                        normalize(selection);
                        clampToBounds(selection);

                        if (mode == Mode.CREATE) {
                            ensureMinSize(selection, (int) downX, (int) downY);
                        } else {
                            ensureMinSize(selection, mode, startRect);
                        }

                        if (selection.width() < minSize || selection.height() < minSize) {
                            hasSelection = false;
                            actionBar.setVisibility(GONE);
                            actionBarHidden = true;
                            postInvalidateOnAnimation();
                            mode = Mode.NONE;
                            return true;
                        }

                        positionActionBarNearSelection();
                        actionBar.setVisibility(VISIBLE);
                        actionBarHidden = false;
                        postInvalidateOnAnimation();
                    }

                    mode = Mode.NONE;
                    return true;
                }
            }

            return super.onTouchEvent(event);
        }

    }

    // ===================== hit test =====================
    private Mode hitTest(float x, float y) {
        if (!hasSelection) return Mode.NONE;

        if (near(x, y, selection.left, selection.top)) return Mode.RESIZE_LT;
        if (near(x, y, selection.right, selection.top)) return Mode.RESIZE_RT;
        if (near(x, y, selection.left, selection.bottom)) return Mode.RESIZE_LB;
        if (near(x, y, selection.right, selection.bottom)) return Mode.RESIZE_RB;

        int cx = (selection.left + selection.right) / 2;
        int cy = (selection.top + selection.bottom) / 2;

        if (near(x, y, selection.left, cy)) return Mode.RESIZE_L;
        if (near(x, y, selection.right, cy)) return Mode.RESIZE_R;
        if (near(x, y, cx, selection.top)) return Mode.RESIZE_T;
        if (near(x, y, cx, selection.bottom)) return Mode.RESIZE_B;

        if (selection.contains((int)x, (int)y)) return Mode.MOVE;

        return Mode.NONE;
    }

    private boolean near(float x, float y, int px, int py) {
        return Math.abs(x - px) <= handleTouchSlop && Math.abs(y - py) <= handleTouchSlop;
    }

    private void drawHandles(Canvas canvas) {
        int l = selection.left, t = selection.top, r = selection.right, b = selection.bottom;
        int cx = (l + r) / 2, cy = (t + b) / 2;

        canvas.drawCircle(l, t, handleRadius, handlePaint);
        canvas.drawCircle(r, t, handleRadius, handlePaint);
        canvas.drawCircle(l, b, handleRadius, handlePaint);
        canvas.drawCircle(r, b, handleRadius, handlePaint);

        canvas.drawCircle(l, cy, handleRadius, handlePaint);
        canvas.drawCircle(r, cy, handleRadius, handlePaint);
        canvas.drawCircle(cx, t, handleRadius, handlePaint);
        canvas.drawCircle(cx, b, handleRadius, handlePaint);
    }

    // ===================== geometry helpers =====================
    private void normalize(Rect r) {
        int l = Math.min(r.left, r.right);
        int rr = Math.max(r.left, r.right);
        int t = Math.min(r.top, r.bottom);
        int bb = Math.max(r.top, r.bottom);
        r.set(l, t, rr, bb);
    }

    private void clampToBounds(Rect r) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        r.left = clamp(r.left, 0, w);
        r.right = clamp(r.right, 0, w);
        r.top = clamp(r.top, 0, h);
        r.bottom = clamp(r.bottom, 0, h);
        normalize(r);
    }

    private void clampMove(Rect r) {
        int w = getWidth();
        int h = getHeight();
        int rw = r.width();
        int rh = r.height();

        int left = r.left, top = r.top;

        if (left < 0) left = 0;
        if (top < 0) top = 0;
        if (left + rw > w) left = w - rw;
        if (top + rh > h) top = h - rh;

        r.set(left, top, left + rw, top + rh);
    }

    private void ensureMinSize(Rect r, int anchorX, int anchorY) {
        normalize(r);
        if (r.width() < minSize) {
            if (anchorX <= r.left) r.right = r.left + minSize;
            else r.left = r.right - minSize;
        }
        if (r.height() < minSize) {
            if (anchorY <= r.top) r.bottom = r.top + minSize;
            else r.top = r.bottom - minSize;
        }
        normalize(r);
        clampToBounds(r);
    }

    private void ensureMinSize(Rect r, Mode m, Rect start) {
        normalize(r);
        if (r.width() < minSize) {
            switch (m) {
                case RESIZE_L:
                case RESIZE_LT:
                case RESIZE_LB:
                    r.left = r.right - minSize; break;
                case RESIZE_R:
                case RESIZE_RT:
                case RESIZE_RB:
                    r.right = r.left + minSize; break;
            }
        }
        if (r.height() < minSize) {
            switch (m) {
                case RESIZE_T:
                case RESIZE_LT:
                case RESIZE_RT:
                    r.top = r.bottom - minSize; break;
                case RESIZE_B:
                case RESIZE_LB:
                case RESIZE_RB:
                    r.bottom = r.top + minSize; break;
            }
        }
        normalize(r);
        clampToBounds(r);
    }

    private void resizeByMode(Rect r, Mode m, float x, float y) {
        switch (m) {
            case RESIZE_LT: r.left = (int) x; r.top = (int) y; break;
            case RESIZE_RT: r.right = (int) x; r.top = (int) y; break;
            case RESIZE_LB: r.left = (int) x; r.bottom = (int) y; break;
            case RESIZE_RB: r.right = (int) x; r.bottom = (int) y; break;

            case RESIZE_L:  r.left = (int) x; break;
            case RESIZE_R:  r.right = (int) x; break;
            case RESIZE_T:  r.top = (int) y; break;
            case RESIZE_B:  r.bottom = (int) y; break;
        }
    }

    // ===================== action bar position =====================
    private void positionActionBarNearSelection() {
        actionBar.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));

        int barW = actionBar.getMeasuredWidth();
        int barH = actionBar.getMeasuredHeight();
        int margin = dp(8);

        int x = selection.right - barW;
        int y = selection.bottom + margin;

        if (y + barH > getHeight()) {
            y = selection.top - barH - margin;
        }
        if (y < 0) {
            y = selection.top + margin;
        }

        if (x < margin) x = margin;
        if (x + barW > getWidth() - margin) x = getWidth() - margin - barW;

        LayoutParams lp = (LayoutParams) actionBar.getLayoutParams();
        lp.leftMargin = x;
        lp.topMargin = y;
        actionBar.setLayoutParams(lp);
    }

    // ===================== capture & crop =====================
    @Nullable
    private Bitmap captureAndCrop(Rect selRectInOverlay) {
        if (frozenBackground == null || frozenBackground.isRecycled()) {
            Log.w("SelectionOverlayView", "No frozen background, fallback to live capture");
            return null; // 或者返回 null
        }

        Rect crop = new Rect(selRectInOverlay);

        // 如果 frozenBackground 是从 MediaProjection 拿的，它通常已经包含了状态栏（topInsetPx 已经包含在内）
        // 所以一般不需要再手动 offset topInsetPx
        // 但为了兼容某些设备，可以保留判断
        boolean needsTopOffset = (frozenBackground.getHeight() >= getHeight() + topInsetPx - dp(2));
        if (needsTopOffset && topInsetPx > 0) {
            crop.offset(0, topInsetPx);
        }

        crop.left   = clamp(crop.left,   0, frozenBackground.getWidth());
        crop.right  = clamp(crop.right,  0, frozenBackground.getWidth());
        crop.top    = clamp(crop.top,    0, frozenBackground.getHeight());
        crop.bottom = clamp(crop.bottom, 0, frozenBackground.getHeight());
        normalize(crop);

        if (crop.width() <= 0 || crop.height() <= 0) return null;

        try {
            return Bitmap.createBitmap(frozenBackground,
                    crop.left, crop.top, crop.width(), crop.height());
        } catch (Exception e) {
            Log.e("SelectionOverlayView", "Crop failed from frozenBackground", e);
            return null;
        }
    }
    // ===================== magnifier =====================
    private void showMagnifier(float x, float y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && magnifier != null) {
            magnifier.show(x, y);
        }
    }

    private void hideMagnifier() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && magnifier != null) {
            magnifier.dismiss();
        }
    }

    // ===================== utils =====================
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private Drawable makeRoundBg(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }
}
