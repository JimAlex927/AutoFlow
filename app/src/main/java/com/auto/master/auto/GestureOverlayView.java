package com.auto.master.auto;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GestureOverlayView extends FrameLayout {
    private Paint paint;
    private Map<Integer, Path> activePaths = new HashMap<>();
    private List<GestureStroke> allStrokes = new ArrayList<>();
    private long startTime;
    private OnGestureRecordedListener listener;

    // 遮罩 View（最稳定方式：独立子 View 画全屏灰色）
    public View maskView;
    private static final int MASK_COLOR = 0x88000000; // 50% 灰

    public interface OnGestureRecordedListener {
        void onGestureRecorded(GestureNode node);
    }

    public GestureOverlayView(Context context) {
        super(context);

        // 初始化轨迹画笔
        paint = new Paint();
        paint.setColor(0x88FF0000);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(45f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);

        // 创建遮罩层（全屏灰色）
        maskView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawColor(MASK_COLOR);
                Log.d("GestureOverlay", "遮罩 onDraw 已执行，尺寸: " + getWidth() + "x" + getHeight());
            }
            
            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                Log.d("GestureOverlay", "遮罩尺寸变化: " + w + "x" + h);
            }
        };
        LayoutParams maskParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        addView(maskView, maskParams);
        maskView.setVisibility(VISIBLE); // 默认显示遮罩

        Log.d("GestureOverlay", "新建 GestureOverlayView 实例");
    }

    // 显示遮罩（还没按下时）
    public void showMask() {
        maskView.setVisibility(VISIBLE);
        maskView.invalidate();
    }

    // 隐藏遮罩（开始滑动后）
    public void hideMask() {
        maskView.setVisibility(GONE);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        for (Path path : activePaths.values()) {
            canvas.drawPath(path, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int pointerIndex = ev.getActionIndex();
                int pointerId = ev.getPointerId(pointerIndex);
                float x = ev.getX(pointerIndex);
                float y = ev.getY(pointerIndex);

                if (activePaths.isEmpty()) {
                    startTime = System.currentTimeMillis();
                    Log.d("GestureOverlay", "开始录制，按下坐标: (" + x + "," + y + ")");
                }

                Path path = new Path();
                path.moveTo(x, y);
                activePaths.put(pointerId, path);

                List<PointF> points = new ArrayList<>();
                points.add(new PointF(x, y));
                allStrokes.add(new GestureStroke(pointerId, points, 0));
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // 第一次移动时隐藏遮罩
                if (!activePaths.isEmpty() && maskView.getVisibility() == VISIBLE) {
                    hideMask();
                    Log.d("GestureOverlay", "开始滑动，隐藏遮罩");
                }

                for (int i = 0; i < ev.getPointerCount(); i++) {
                    int pointerId = ev.getPointerId(i);
                    Path path = activePaths.get(pointerId);
                    if (path != null) {
                        path.lineTo(ev.getX(i), ev.getY(i));

                        for (GestureStroke stroke : allStrokes) {
                            if (stroke.pointerId == pointerId) {
                                stroke.points.add(new PointF(ev.getX(i), ev.getY(i)));
                                break;
                            }
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int pointerIndex = ev.getActionIndex();
                int pointerId = ev.getPointerId(pointerIndex);

                activePaths.remove(pointerId);
                if (activePaths.isEmpty()) {
                    long duration = System.currentTimeMillis() - startTime;
                    GestureNode node = new GestureNode(allStrokes, duration);
                    if (listener != null) {
                        listener.onGestureRecorded(node);
                    }
                    hideMask();
                    setVisibility(GONE);
                    Log.d("GestureOverlay", "录制结束，时长: " + duration + "ms");
                }
                break;
            }

        }
        // ── 所有事件都转发给底层 ──


        invalidate();
        return true;
    }



    public void setOnGestureRecordedListener(OnGestureRecordedListener l) {
        this.listener = l;
    }

    // 内部类保持 public static
    public static class GestureStroke {
        public int pointerId;
        public List<PointF> points = new ArrayList<>();
        public long relativeStartTime;

        public GestureStroke(int id, List<PointF> pts, long start) {
            this.pointerId = id;
            this.points = new ArrayList<>(pts);
            this.relativeStartTime = start;
        }
    }

    public static class GestureNode {
        public List<GestureStroke> strokes;
        public long duration;
        public int type;

        public GestureNode(List<GestureStroke> s, long d) {
            this.strokes = s;
            this.duration = d;
            if (strokes.size() == 1) {
                type = strokes.get(0).points.size() > 2 ? 2 : 1;
            } else {
                type = 26;
            }
        }
    }

    public static class PointF {
        public float x, y;

        public PointF(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}