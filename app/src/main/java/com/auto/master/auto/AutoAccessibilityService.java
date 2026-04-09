package com.auto.master.auto;


import static android.view.View.VISIBLE;


import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import java.util.List;

public class AutoAccessibilityService extends AccessibilityService {
    private static final long CLICK_GESTURE_DURATION_MS = 16L;

    private static final String TAG = "AutoAccService";
    private static volatile AutoAccessibilityService sInstance;

    // 新增一个独立的轨迹展示 overlay（内部类或单独类都可以，这里用内部类简化）
    private GestureTrailOverlay gestureTrailOverlay = null;



    // 内部类：专门用于绘制轨迹动画的 View
    private static class GestureTrailOverlay extends View {
        private final Paint paint = new Paint();
        private final List<GestureOverlayView.GestureStroke> strokes;
        private final long duration;
        private long startTime = 0;
        private boolean isAnimating = false;
        private final Handler handler = new Handler(Looper.getMainLooper());

        public GestureTrailOverlay(Context context, GestureOverlayView.GestureNode node) {
            super(context);
            setBackgroundColor(Color.TRANSPARENT);

            paint.setColor(0x44FF0000); // 亮绿色，不透明，便于观察
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(35f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setAntiAlias(true);

            this.strokes = node.strokes;
            this.duration = node.duration > 0 ? node.duration : 2000; // 防止 duration 为 0
        }

        public void startAnimation() {
            if (isAnimating) return;
            isAnimating = true;
            startTime = System.currentTimeMillis();
            invalidate();
            handler.post(this::animate);
        }

        public ViewPropertyAnimator animate() {
            if (!isAnimating) return null;

            long elapsed = System.currentTimeMillis() - startTime;
            float progress = Math.min(1f, (float) elapsed / duration);

            invalidate(); // 强制重绘

            if (progress < 1f) {
                handler.postDelayed(this::animate, 16); // ~60fps
            } else {
                // 动画结束，延迟 800ms 后移除
                handler.postDelayed(() -> {
                    isAnimating = false;
                    Context ctx = getContext();
                    if (ctx instanceof AutoAccessibilityService) {
                        AutoAccessibilityService service = (AutoAccessibilityService) ctx;
                        WindowManager wm = (WindowManager) service.getSystemService(WINDOW_SERVICE);
                        if (wm != null) {
                            try {
                                wm.removeView(this);
                                Log.d(TAG, "轨迹 overlay 已移除");
                            } catch (Exception ignored) {}
                        }
                    }
                }, 800);
            }
            return null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (!isAnimating || strokes == null) return;

            long elapsed = System.currentTimeMillis() - startTime;
            float progress = Math.min(1f, (float) elapsed / duration);

            for (GestureOverlayView.GestureStroke stroke : strokes) {
                Path path = new Path();
                List<GestureOverlayView.PointF> points = stroke.points;
                if (points == null || points.size() < 2) continue;

                path.moveTo(points.get(0).x, points.get(0).y);

                int targetIndex = (int) (progress * (points.size() - 1));
                targetIndex = Math.min(targetIndex, points.size() - 1);

                for (int i = 1; i <= targetIndex; i++) {
                    path.lineTo(points.get(i).x, points.get(i).y);
                }

                canvas.drawPath(path, paint);
            }
        }
    }

    // 在 onServiceConnected 或需要时初始化 handler（如果你还没）
    private Handler mainHandler = new Handler(Looper.getMainLooper());



    // 独立的方法：展示手势轨迹动画
    public void showGestureTrail(GestureOverlayView.GestureNode node) {
        if (node == null || node.strokes == null || node.strokes.isEmpty()) {
            Log.w(TAG, "无效的手势节点，无法展示轨迹");
            return;
        }

        mainHandler.post(() -> {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm == null) {
                Log.e(TAG, "WindowManager 为 null，无法展示轨迹");
                return;
            }

            // 如果已有，先移除旧的（避免叠加）
            if (gestureTrailOverlay != null) {
                try {
                    wm.removeView(gestureTrailOverlay);
                } catch (Exception ignored) {}
                gestureTrailOverlay = null;
            }

            // 创建新的轨迹 overlay
            gestureTrailOverlay = new GestureTrailOverlay(this, node);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

            try {
                wm.addView(gestureTrailOverlay, params);
                Log.d(TAG, "轨迹 overlay 已添加");
                gestureTrailOverlay.startAnimation();
            } catch (Exception e) {
                Log.e(TAG, "添加轨迹 overlay 失败", e);
            }
        });
    }

    /**
     * 获取当前设备的物理旋转方向（0, 90, 180, 270）
     *
     * @param context 必须是非空的 Context（推荐 ApplicationContext）
     * @return Surface.ROTATION_0 / 90 / 180 / 270
     *         如果获取失败，默认返回 ROTATION_0（竖屏）
     */
    public static int getCurrentPhysicalRotation(@NonNull Context context) {
        if (context == null) {
            Log.w(TAG, "Context 为 null，无法获取旋转方向，默认返回竖屏");
            return Surface.ROTATION_0;
        }

        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                Log.w(TAG, "WindowManager 为 null，默认返回竖屏");
                return Surface.ROTATION_0;
            }

            Display display = wm.getDefaultDisplay();
            if (display == null) {
                Log.w(TAG, "Default Display 为 null，默认返回竖屏");
                return Surface.ROTATION_0;
            }

            int rotation = display.getRotation();
            Log.v(TAG, "当前物理旋转方向: " + rotationToString(rotation));  // 可选：调试时打开
            return rotation;

        } catch (SecurityException e) {
            // 权限问题（极少见，但 Android 11+ 有可能）
            Log.w(TAG, "获取旋转方向缺少权限，默认返回竖屏", e);
            return Surface.ROTATION_0;
        } catch (Exception e) {
            // 其他异常（比如 display 已释放、系统服务异常）
            Log.w(TAG, "获取旋转方向异常，默认返回竖屏", e);
            return Surface.ROTATION_0;
        }
    }

    /**
     * 可选辅助方法：把旋转角度转成人类可读的字符串（调试用）
     */
    private static String rotationToString(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0:   return "竖屏 (0°)";
            case Surface.ROTATION_90:  return "横屏 Home右 (90°)";
            case Surface.ROTATION_180: return "倒竖屏 (180°)";
            case Surface.ROTATION_270: return "横屏 Home左 (270°)";
            default:                   return "未知 (" + rotation + ")";
        }
    }

    //手势录制
    private GestureOverlayView gestureOverlay;

    public void startGestureRecording(GestureOverlayView.OnGestureRecordedListener listener) {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return;

        // 每次都新建，避免任何残留
        // 只在确实有实例时才停止
        if (gestureOverlay != null) {
            stopGestureRecording();
        }

        if (gestureOverlay == null) {
            // 第一次创建
            gestureOverlay = new GestureOverlayView(this);
            
            // 获取实际屏幕尺寸（使用 getRealMetrics，兼容横竖屏和修改分辨率）
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            
            // 添加到窗口（参考 SelectionOverlayView 的配置）
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY :
                            WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            
            wm.addView(gestureOverlay, params);
        } else {
            // 已有实例 → 重置状态 + 重新显示
//            gestureOverlay.reset();  // ← 新增 reset 方法
        }

        gestureOverlay.setOnGestureRecordedListener(listener);
        gestureOverlay.setVisibility(VISIBLE);

        gestureOverlay.post(() -> {
            gestureOverlay.invalidate();
            gestureOverlay.maskView.invalidate(); // 强制遮罩子 View 重绘
            Log.d(TAG, "手势录制遮罩已启动");
        });
    }

    public void stopGestureRecording() {
        if (gestureOverlay != null) {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            wm.removeView(gestureOverlay);
            gestureOverlay = null;
        }
    }

    // Handler 用于延迟检查（参考 GestureMultiGestureOption 代码）
    private final Handler gestureHandler = new Handler(Looper.getMainLooper());
    private static final int GESTURE_RETRY_DELAY = 1200; // 延迟检查时间
    private static final int GESTURE_TIMEOUT_MSG = 1002303; // 消息标识

    /**
     * 手势结果回调封装类 - 参考 GestureMultiGestureOption.a
     * 内部处理重试逻辑和延迟检查
     */
    private class GestureResultWrapper extends GestureResultCallback {
        final java.util.concurrent.atomic.AtomicReference<Boolean> completedRef;
        final Runnable onSuccess;
        final Runnable onFail;
        final long startTime;
        final int maxRetry;
        final java.util.concurrent.atomic.AtomicInteger retryCount;
        final GestureDescription gestureDesc;
        final GestureOverlayView.GestureNode gestureNode;

        GestureResultWrapper(GestureDescription gestureDesc,
                            GestureOverlayView.GestureNode node,
                            Runnable onSuccess,
                            Runnable onFail,
                            int maxRetry) {
            this.completedRef = new java.util.concurrent.atomic.AtomicReference<>(false);
            this.gestureDesc = gestureDesc;
            this.gestureNode = node;
            this.onSuccess = onSuccess;
            this.onFail = onFail;
            this.maxRetry = maxRetry;
            this.retryCount = new java.util.concurrent.atomic.AtomicInteger(0);
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            super.onCompleted(gestureDescription);
            Log.d(TAG, "手势执行完成");
            completedRef.set(true);
            // 取消延迟检查
            gestureHandler.removeMessages(GESTURE_TIMEOUT_MSG);
            if (onSuccess != null) {
                onSuccess.run();
            }
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            super.onCancelled(gestureDescription);
            Log.w(TAG, "手势被取消，准备延迟检查...");
            // 延迟检查是否真的失败
            gestureHandler.postDelayed(() -> checkAndRetry(), GESTURE_RETRY_DELAY);
        }

        private void checkAndRetry() {
            // 如果已经标记为完成，说明 onCompleted 已执行，不需要重试
            if (completedRef.get()) {
                Log.d(TAG, "手势实际上已成功，忽略取消回调");
                return;
            }

            // 检查是否还有重试次数
            int currentRetry = retryCount.incrementAndGet();
            if (currentRetry <= maxRetry) {
                Log.d(TAG, "手势失败，第 " + currentRetry + "/" + maxRetry + " 次重试");
                // 执行重试
                retryGesture();
            } else {
                Log.e(TAG, "手势执行失败，已达最大重试次数");
                if (onFail != null) {
                    onFail.run();
                }
            }
        }

        private void retryGesture() {
            // 重新派发相同的手势
            dispatchGesture(gestureDesc, this, null);
        }
    }

    /**
     * 回放手势 - 带重试机制的优化版本
     * 参考 GestureMultiGestureOption 实现
     * 
     * @param node 手势节点
     * @param onSuccess 成功回调
     * @param onFail 失败回调（所有重试都失败后）
     * @param maxRetry 最大重试次数
     */
    public void replayGestureWithRetry(GestureOverlayView.GestureNode node,
                                       Runnable onSuccess,
                                       Runnable onFail,
                                       int maxRetry) {
        GestureDescription.Builder builder = new GestureDescription.Builder();

        for (GestureOverlayView.GestureStroke stroke : node.strokes) {
            Path path = new Path();
            if (!stroke.points.isEmpty()) {
                path.moveTo(stroke.points.get(0).x, stroke.points.get(0).y);
                for (int i = 1; i < stroke.points.size(); i++) {
                    path.lineTo(stroke.points.get(i).x, stroke.points.get(i).y);
                }
            }
            GestureDescription.StrokeDescription desc = new GestureDescription.StrokeDescription(
                    path, stroke.relativeStartTime, node.duration);
            builder.addStroke(desc);
        }

        GestureDescription gestureDesc = builder.build();
        GestureResultWrapper callback = new GestureResultWrapper(
            gestureDesc, node, onSuccess, onFail, maxRetry);
        
        dispatchGesture(gestureDesc, callback, null);
    }

    /**
     * 简化版回放手势（兼容旧接口）
     */
    public void replayGesture(GestureOverlayView.GestureNode node,
                              Consumer<Boolean> resultCallback) {
        GestureDescription.Builder builder = new GestureDescription.Builder();
        for (GestureOverlayView.GestureStroke stroke : node.strokes) {
            Path path = new Path();
            if (!stroke.points.isEmpty()) {
                path.moveTo(stroke.points.get(0).x, stroke.points.get(0).y);
                for (int i = 1; i < stroke.points.size(); i++) {
                    path.lineTo(stroke.points.get(i).x, stroke.points.get(i).y);
                }
            }
            GestureDescription.StrokeDescription desc = new GestureDescription.StrokeDescription(
                    path, stroke.relativeStartTime, node.duration);
            builder.addStroke(desc);
        }
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "手势回放完成");
                if (resultCallback != null) {
                    resultCallback.accept(true);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, "手势回放取消");
                if (resultCallback != null) {
                    resultCallback.accept(false);
                }
            }
        }, null);
    }

    public static boolean isConnected() {
        return sInstance != null;
    }

    public static AutoAccessibilityService get() {
        return sInstance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 这里不强依赖事件；你后续可以在这里做窗口变化监听、节点缓存等
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        Log.d(TAG, "Service connected");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(hideRectFeedbackRunnable);
        if (rectFeedbackView != null) {
            try {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (wm != null) {
                    wm.removeView(rectFeedbackView);
                }
            } catch (Exception ignored) {
            }
            rectFeedbackView = null;
            rectFeedbackParams = null;
        }
        sInstance = null;
        Log.d(TAG, "Service destroyed");
    }

    // 在 AutoAccessibilityService.java

    // 复用的 Paint 对象，避免每次 onDraw 都创建
    private static final Paint CIRCLE_FEEDBACK_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint RECT_FEEDBACK_FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint RECT_FEEDBACK_BORDER_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectFeedbackView rectFeedbackView;
    private WindowManager.LayoutParams rectFeedbackParams;
    private final Runnable hideRectFeedbackRunnable = () -> {
        if (rectFeedbackView != null) {
            rectFeedbackView.setVisibility(View.GONE);
        }
    };
    
    static {
        CIRCLE_FEEDBACK_PAINT.setColor(0x88FF0000);
        RECT_FEEDBACK_FILL_PAINT.setStyle(Paint.Style.FILL);
        RECT_FEEDBACK_BORDER_PAINT.setStyle(Paint.Style.STROKE);
    }

    private static class RectFeedbackView extends View {
        private int fillColor = Color.TRANSPARENT;
        private int borderColor = Color.TRANSPARENT;
        private int borderWidthPx = 0;

        RectFeedbackView(Context context) {
            super(context);
            setBackgroundColor(Color.TRANSPARENT);
            setVisibility(View.GONE);
        }

        void updateStyle(int fillColor, int borderColor, int borderWidthPx) {
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.borderWidthPx = borderWidthPx;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (fillColor != Color.TRANSPARENT) {
                RECT_FEEDBACK_FILL_PAINT.setColor(fillColor);
                canvas.drawRect(0, 0, getWidth(), getHeight(), RECT_FEEDBACK_FILL_PAINT);
            }

            if (borderWidthPx > 0 && borderColor != Color.TRANSPARENT) {
                RECT_FEEDBACK_BORDER_PAINT.setColor(borderColor);
                RECT_FEEDBACK_BORDER_PAINT.setStrokeWidth(borderWidthPx);
                canvas.drawRect(
                        borderWidthPx / 2f,
                        borderWidthPx / 2f,
                        getWidth() - borderWidthPx / 2f,
                        getHeight() - borderWidthPx / 2f,
                        RECT_FEEDBACK_BORDER_PAINT
                );
            }
        }
    }

    private void ensureRectFeedbackView(WindowManager wm) {
        if (wm == null || rectFeedbackView != null) {
            return;
        }

        rectFeedbackView = new RectFeedbackView(this);

        int windowType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            windowType = WindowManager.LayoutParams.TYPE_TOAST;
        }

        rectFeedbackParams = new WindowManager.LayoutParams(
                1,
                1,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        rectFeedbackParams.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            rectFeedbackParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        try {
            wm.addView(rectFeedbackView, rectFeedbackParams);
        } catch (Exception e) {
            Log.w(TAG, "添加可复用矩形反馈失败", e);
            rectFeedbackView = null;
            rectFeedbackParams = null;
        }
    }

    /**
     * 在 x,y 展示一个圆圈 并延迟删除（优化版）
     * 使用静态 Paint 对象避免频繁 GC
     * @param x
     * @param y
     * @param visibleDurationMs
     */
    public void showClickFeedback(int x, int y, long visibleDurationMs) {
        if (!isConnected()) return;

        final Context ctx = this;
        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return;

        // 小圆圈大小（dp）
        final int diameterDp = 7;
        final float density = ctx.getResources().getDisplayMetrics().density;
        final int diameterPx = (int) (diameterDp * density + 0.5f);

        // 创建圆形 View - 使用静态 Paint
        View dot = new View(ctx) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                // 使用静态 Paint，避免每次 new Paint()
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, CIRCLE_FEEDBACK_PAINT);
            }
        };
        dot.setBackgroundColor(Color.TRANSPARENT);

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_TOAST;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                diameterPx,
                diameterPx,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        params.x = x - diameterPx / 2;
        params.y = y - diameterPx / 2;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        try {
            Log.d("ClickFeedback", "addView at (" + params.x + "," + params.y + "), raw=(" + x + "," + y + ")");
            wm.addView(dot, params);

            dot.postDelayed(() -> {
                try {
                    wm.removeView(dot);
                } catch (Exception ignore) {}
            }, visibleDurationMs);

        } catch (Exception e) {
            Log.w("ClickFeedback", "添加点击反馈失败", e);
        }
    }

    /**
     * 在指定的矩形區域顯示一個可見的矩形框（邊框或半透明填充），用於調試或反饋
     * 框會在 visibleDurationMs 毫秒後自動消失
     * 优化：复用 Paint 对象，避免频繁 GC
     *
     * @param x                  矩形左上角 X 座標（螢幕絕對座標）
     * @param y                  矩形左上角 Y 座標（螢幕絕對座標）
     * @param width              矩形寬度（像素）
     * @param height             矩形高度（像素）
     * @param visibleDurationMs  顯示持續時間（毫秒），建議 800~2000
     * @param borderColor        邊框顏色，例如 0xFF00FF00（綠色不透明）
     * @param borderWidthDp      邊框粗細（dp），建議 2~4
     * @param fillColor          填充顏色（可設為 Color.TRANSPARENT 只要邊框）
     */
    public void showRectFeedback(int x, int y, int width, int height,
                                 long visibleDurationMs,
                                 int borderColor,
                                 float borderWidthDp,
                                 int fillColor) {

        if (!isConnected()) {
            Log.w(TAG, "AccessibilityService 未連接，無法顯示矩形反饋");
            return;
        }

        final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm == null) return;

        // 密度轉換
        final float density = getResources().getDisplayMetrics().density;
        final int borderWidthPx = (int) (borderWidthDp * density + 0.5f);

        try {
            ensureRectFeedbackView(wm);
            if (rectFeedbackView == null || rectFeedbackParams == null) {
                return;
            }

            mainHandler.removeCallbacks(hideRectFeedbackRunnable);
            rectFeedbackView.updateStyle(fillColor, borderColor, borderWidthPx);
            rectFeedbackParams.width = Math.max(1, width);
            rectFeedbackParams.height = Math.max(1, height);
            rectFeedbackParams.x = x;
            rectFeedbackParams.y = y;
            rectFeedbackView.setVisibility(View.VISIBLE);

            Log.d(TAG, "顯示矩形反饋: (" + x + "," + y + ") " + width + "x" + height);
            wm.updateViewLayout(rectFeedbackView, rectFeedbackParams);
            mainHandler.postDelayed(hideRectFeedbackRunnable, visibleDurationMs);

        } catch (Exception e) {
            Log.w(TAG, "添加矩形反饋失敗", e);
        }
    }

    private int getWidth() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            return metrics.widthPixels;
        }
        return 1080; // 兜底
    }

    private int getHeight() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            return metrics.heightPixels;
        }
        return 2400; // 兜底
    }
    /** 手势点击 */
    public boolean click(int x, int y, Runnable onDone, Runnable onFail) {
        int rotation = getCurrentPhysicalRotation(this);
//        int realX = x;
//        int realY = y;

        // 根据旋转方向转换坐标 todo 之前以为 无障碍需要转换坐标 但是后来发现不需要
//        switch (rotation) {
//            case Surface.ROTATION_0:
//                // 竖屏：坐标不变
//                break;
//            case Surface.ROTATION_90:
//                // 顺时针90°（Home键在右）
//                realX = y;
//                realY = getWidth() - x;  // 注意：getWidth() 是当前屏幕宽（已随旋转变化）
//                break;
//            case Surface.ROTATION_180:
//                realX = getWidth() - x;
//                realY = getHeight() - y;
//                break;
//            case Surface.ROTATION_270:
//                // 顺时针270°（Home键在左）
//                realX = getHeight() - y;
//                realY = x;
//                break;
//        }
//        x = realX;
//        y = realY;

        Path p = new Path();
        p.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(p, 0, CLICK_GESTURE_DURATION_MS);

        GestureDescription gd = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        boolean ok = dispatchGesture(gd, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                if (onDone != null) onDone.run();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                if (onFail != null) onFail.run();
            }
        }, null);

        if (!ok && onFail != null) onFail.run();
        return ok;
    }
}
