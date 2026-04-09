package com.auto.master.floatwin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.auto.master.auto.GestureOverlayView;

public class GesturePreviewView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private GestureOverlayView.GestureNode node;

    public GesturePreviewView(Context context) {
        super(context);
        init();
    }

    public GesturePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GesturePreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        linePaint.setColor(0xFF315DBF);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);

        pointPaint.setColor(0xFF4CAF50);
        pointPaint.setStyle(Paint.Style.FILL);
    }

    public void setGestureNode(GestureOverlayView.GestureNode node) {
        this.node = node;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (node == null || node.strokes == null || node.strokes.isEmpty()) {
            return;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;

        for (GestureOverlayView.GestureStroke stroke : node.strokes) {
            if (stroke == null || stroke.points == null) {
                continue;
            }
            for (GestureOverlayView.PointF point : stroke.points) {
                if (point == null) {
                    continue;
                }
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
            }
        }

        if (minX == Float.MAX_VALUE || minY == Float.MAX_VALUE) {
            return;
        }

        float contentWidth = Math.max(1f, maxX - minX);
        float contentHeight = Math.max(1f, maxY - minY);
        float padding = 10f;
        float drawWidth = Math.max(1f, getWidth() - padding * 2f);
        float drawHeight = Math.max(1f, getHeight() - padding * 2f);
        float scale = Math.min(drawWidth / contentWidth, drawHeight / contentHeight);
        float offsetX = (getWidth() - contentWidth * scale) / 2f;
        float offsetY = (getHeight() - contentHeight * scale) / 2f;

        for (GestureOverlayView.GestureStroke stroke : node.strokes) {
            if (stroke == null || stroke.points == null || stroke.points.isEmpty()) {
                continue;
            }
            Path path = new Path();
            GestureOverlayView.PointF first = stroke.points.get(0);
            float startX = offsetX + (first.x - minX) * scale;
            float startY = offsetY + (first.y - minY) * scale;
            path.moveTo(startX, startY);
            for (int i = 1; i < stroke.points.size(); i++) {
                GestureOverlayView.PointF point = stroke.points.get(i);
                float x = offsetX + (point.x - minX) * scale;
                float y = offsetY + (point.y - minY) * scale;
                path.lineTo(x, y);
            }
            canvas.drawPath(path, linePaint);
            canvas.drawCircle(startX, startY, 4f, pointPaint);
        }
    }
}
