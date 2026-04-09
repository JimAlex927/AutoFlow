package com.auto.master.auto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

public class ColorPointPickerView extends View {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int x, int y, int color);
    }

    public interface OnMagnifierLayoutChangedListener {
        void onMagnifierLayoutChanged(RectF magnifierBounds);
    }

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF dstRect = new RectF();
    private final RectF magnifierRect = new RectF();

    private Bitmap screenshot;
    private boolean ownsBitmap;
    private int selectedX = -1;
    private int selectedY = -1;
    private OnSelectionChangedListener selectionChangedListener;
    private OnMagnifierLayoutChangedListener magnifierLayoutChangedListener;
    private final ScaleGestureDetector scaleGestureDetector;
    private static final float MAGNIFIER_MIN_ZOOM = 1f;
    private static final float MAGNIFIER_MAX_ZOOM = 4f;
    private static final int MAGNIFIER_BASE_RADIUS = 7;
    private static final int MAGNIFIER_MIN_RADIUS = 2;
    private float magnifierZoom = 1f;
    private boolean gestureStartedInMagnifier = false;
    private boolean scaleGestureInMagnifier = false;
    private boolean scaleGestureConsumed = false;

    public ColorPointPickerView(Context context) {
        this(context, null);
    }

    public ColorPointPickerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPointPickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        dimPaint.setColor(0x66000000);
        crossPaint.setColor(0xFFFF5252);
        crossPaint.setStrokeWidth(dp(1.25f));
        centerPaint.setColor(Color.WHITE);
        centerPaint.setStyle(Paint.Style.STROKE);
        centerPaint.setStrokeWidth(dp(1.25f));
        gridPaint.setColor(0x66FFFFFF);
        gridPaint.setStrokeWidth(dp(0.75f));
        framePaint.setColor(Color.WHITE);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(1.5f));
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return scaleGestureInMagnifier;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (!scaleGestureInMagnifier) {
                    return false;
                }
                scaleGestureConsumed = true;
                magnifierZoom = clamp(detector.getScaleFactor() * magnifierZoom, MAGNIFIER_MIN_ZOOM, MAGNIFIER_MAX_ZOOM);
                dispatchMagnifierLayoutChanged();
                invalidate();
                return true;
            }
        });
        setClickable(true);
    }

    public void setScreenshot(@Nullable Bitmap bitmap, boolean ownsBitmap) {
        release();
        this.screenshot = bitmap;
        this.ownsBitmap = ownsBitmap;
        if (bitmap != null) {
            if (selectedX < 0 || selectedY < 0) {
                selectedX = bitmap.getWidth() / 2;
                selectedY = bitmap.getHeight() / 2;
            } else {
                selectedX = Math.max(0, Math.min(selectedX, bitmap.getWidth() - 1));
                selectedY = Math.max(0, Math.min(selectedY, bitmap.getHeight() - 1));
            }
        } else {
            selectedX = -1;
            selectedY = -1;
        }
        requestLayout();
        invalidate();
        notifySelectionChanged();
        post(this::dispatchMagnifierLayoutChanged);
    }

    public void setOnSelectionChangedListener(@Nullable OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public void setOnMagnifierLayoutChangedListener(@Nullable OnMagnifierLayoutChangedListener listener) {
        this.magnifierLayoutChangedListener = listener;
        dispatchMagnifierLayoutChanged();
    }

    public int getSelectedX() {
        return selectedX;
    }

    public int getSelectedY() {
        return selectedY;
    }

    public void setSelection(int x, int y) {
        if (screenshot == null || screenshot.isRecycled()) {
            return;
        }
        int newX = clamp(x, 0, screenshot.getWidth() - 1);
        int newY = clamp(y, 0, screenshot.getHeight() - 1);
        if (newX == selectedX && newY == selectedY) {
            return;
        }
        selectedX = newX;
        selectedY = newY;
        notifySelectionChanged();
        dispatchMagnifierLayoutChanged();
        invalidate();
    }

    public int getSelectedColor() {
        if (!hasSelection()) {
            return Color.TRANSPARENT;
        }
        return screenshot.getPixel(selectedX, selectedY);
    }

    public boolean hasSelection() {
        return screenshot != null
                && !screenshot.isRecycled()
                && selectedX >= 0
                && selectedY >= 0
                && selectedX < screenshot.getWidth()
                && selectedY < screenshot.getHeight();
    }

    public void release() {
        if (ownsBitmap && screenshot != null && !screenshot.isRecycled()) {
            screenshot.recycle();
        }
        screenshot = null;
        ownsBitmap = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutScreenshotRect(w, h);
        dispatchMagnifierLayoutChanged();
    }

    private void layoutScreenshotRect(int width, int height) {
        if (screenshot == null || screenshot.isRecycled() || width <= 0 || height <= 0) {
            dstRect.setEmpty();
            return;
        }
        // Match template-capture overlay behavior: stretch the frozen screenshot to the full
        // overlay bounds so touch coordinates map 1:1 to on-screen positions even when the
        // captured frame aspect ratio differs slightly from the overlay container.
        dstRect.set(0f, 0f, width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xB0000000);
        if (screenshot == null || screenshot.isRecycled()) {
            return;
        }

        canvas.drawBitmap(screenshot, null, dstRect, bitmapPaint);
        canvas.drawRect(dstRect, dimPaint);
        if (!hasSelection()) {
            return;
        }

        float viewX = dstRect.left + ((selectedX + 0.5f) / screenshot.getWidth()) * dstRect.width();
        float viewY = dstRect.top + ((selectedY + 0.5f) / screenshot.getHeight()) * dstRect.height();
        drawCrosshair(canvas, viewX, viewY);
        drawMagnifier(canvas, viewX, viewY);
    }

    private void drawCrosshair(Canvas canvas, float x, float y) {
        float radius = dp(10f);
        canvas.drawLine(x - radius, y, x - dp(2f), y, crossPaint);
        canvas.drawLine(x + dp(2f), y, x + radius, y, crossPaint);
        canvas.drawLine(x, y - radius, x, y - dp(2f), crossPaint);
        canvas.drawLine(x, y + dp(2f), x, y + radius, crossPaint);
        canvas.drawCircle(x, y, dp(7f), centerPaint);
        canvas.drawCircle(x, y, dp(1.4f), crossPaint);
    }

    private void drawMagnifier(Canvas canvas, float anchorX, float anchorY) {
        final int sampleRadius = getVisibleSampleRadius();
        final int visibleCells = sampleRadius * 2 + 1;
        final float boxSize = dp(150f);
        final float cellSize = boxSize / visibleCells;
        drawMagnifierGeometry(anchorX, anchorY);

        canvas.drawRoundRect(magnifierRect, dp(12f), dp(12f), dimPaint);
        canvas.drawRoundRect(magnifierRect, dp(12f), dp(12f), framePaint);

        for (int row = -sampleRadius; row <= sampleRadius; row++) {
            for (int col = -sampleRadius; col <= sampleRadius; col++) {
                int px = clamp(selectedX + col, 0, screenshot.getWidth() - 1);
                int py = clamp(selectedY + row, 0, screenshot.getHeight() - 1);
                cellPaint.setColor(screenshot.getPixel(px, py));
                float cellLeft = magnifierRect.left + (col + sampleRadius) * cellSize;
                float cellTop = magnifierRect.top + (row + sampleRadius) * cellSize;
                canvas.drawRect(cellLeft, cellTop, cellLeft + cellSize, cellTop + cellSize, cellPaint);
            }
        }

        for (int i = 1; i < visibleCells; i++) {
            float x = magnifierRect.left + i * cellSize;
            float y = magnifierRect.top + i * cellSize;
            canvas.drawLine(x, magnifierRect.top, x, magnifierRect.bottom, gridPaint);
            canvas.drawLine(magnifierRect.left, y, magnifierRect.right, y, gridPaint);
        }

        Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.WHITE);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(dp(2f));
        float centerLeft = magnifierRect.left + sampleRadius * cellSize;
        float centerTop = magnifierRect.top + sampleRadius * cellSize;
        canvas.drawRect(centerLeft, centerTop, centerLeft + cellSize, centerTop + cellSize, highlightPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (screenshot == null || screenshot.isRecycled()) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            scaleGestureInMagnifier = areAllPointersInsideMagnifier(event);
        }
        scaleGestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scaleGestureConsumed = false;
                gestureStartedInMagnifier = isInMagnifier(event.getX(), event.getY());
                if (!gestureStartedInMagnifier) {
                    updateSelection(event.getX(), event.getY());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleGestureDetector.isInProgress() && !gestureStartedInMagnifier) {
                    updateSelection(event.getX(), event.getY());
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!scaleGestureDetector.isInProgress() && !scaleGestureConsumed) {
                    if (gestureStartedInMagnifier && isInMagnifier(event.getX(), event.getY())) {
                        updateSelectionFromMagnifier(event.getX(), event.getY());
                    } else if (!gestureStartedInMagnifier) {
                        updateSelection(event.getX(), event.getY());
                    }
                }
                gestureStartedInMagnifier = false;
                scaleGestureInMagnifier = false;
                scaleGestureConsumed = false;
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                gestureStartedInMagnifier = false;
                scaleGestureInMagnifier = false;
                scaleGestureConsumed = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void updateSelection(float viewX, float viewY) {
        if (dstRect.isEmpty()) {
            return;
        }
        float clampedX = Math.max(dstRect.left, Math.min(viewX, dstRect.right - 1f));
        float clampedY = Math.max(dstRect.top, Math.min(viewY, dstRect.bottom - 1f));
        int newX = clamp((int) ((clampedX - dstRect.left) / dstRect.width() * screenshot.getWidth()), 0, screenshot.getWidth() - 1);
        int newY = clamp((int) ((clampedY - dstRect.top) / dstRect.height() * screenshot.getHeight()), 0, screenshot.getHeight() - 1);
        if (newX == selectedX && newY == selectedY) {
            return;
        }
        selectedX = newX;
        selectedY = newY;
        notifySelectionChanged();
        dispatchMagnifierLayoutChanged();
        invalidate();
    }

    private void updateSelectionFromMagnifier(float touchX, float touchY) {
        if (!hasSelection()) {
            return;
        }
        float anchorX = getSelectedViewX();
        float anchorY = getSelectedViewY();
        drawMagnifierGeometry(anchorX, anchorY);
        if (magnifierRect.isEmpty() || !magnifierRect.contains(touchX, touchY)) {
            return;
        }

        int sampleRadius = getVisibleSampleRadius();
        int visibleCells = sampleRadius * 2 + 1;
        float cellSize = magnifierRect.width() / visibleCells;
        int col = clamp((int) ((touchX - magnifierRect.left) / cellSize), 0, visibleCells - 1);
        int row = clamp((int) ((touchY - magnifierRect.top) / cellSize), 0, visibleCells - 1);
        int newX = clamp(selectedX + col - sampleRadius, 0, screenshot.getWidth() - 1);
        int newY = clamp(selectedY + row - sampleRadius, 0, screenshot.getHeight() - 1);
        if (newX == selectedX && newY == selectedY) {
            return;
        }
        selectedX = newX;
        selectedY = newY;
        notifySelectionChanged();
        dispatchMagnifierLayoutChanged();
        invalidate();
    }

    private boolean isInMagnifier(float x, float y) {
        if (!hasSelection()) {
            return false;
        }
        drawMagnifierGeometry(getSelectedViewX(), getSelectedViewY());
        return magnifierRect.contains(x, y);
    }

    private boolean areAllPointersInsideMagnifier(MotionEvent event) {
        if (!hasSelection() || event == null || event.getPointerCount() < 2) {
            return false;
        }
        drawMagnifierGeometry(getSelectedViewX(), getSelectedViewY());
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (!magnifierRect.contains(event.getX(i), event.getY(i))) {
                return false;
            }
        }
        return true;
    }

    private float getSelectedViewX() {
        return dstRect.left + ((selectedX + 0.5f) / screenshot.getWidth()) * dstRect.width();
    }

    private float getSelectedViewY() {
        return dstRect.top + ((selectedY + 0.5f) / screenshot.getHeight()) * dstRect.height();
    }

    private int getVisibleSampleRadius() {
        return clamp(Math.round(MAGNIFIER_BASE_RADIUS / magnifierZoom), MAGNIFIER_MIN_RADIUS, MAGNIFIER_BASE_RADIUS);
    }

    private void drawMagnifierGeometry(float anchorX, float anchorY) {
        float boxSize = dp(150f);
        float left = anchorX + dp(18f);
        float top = anchorY - boxSize - dp(18f);
        if (left + boxSize > getWidth() - dp(8f)) {
            left = anchorX - boxSize - dp(18f);
        }
        if (top < dp(8f)) {
            top = anchorY + dp(18f);
        }
        magnifierRect.set(left, top, left + boxSize, top + boxSize);
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null && hasSelection()) {
            selectionChangedListener.onSelectionChanged(selectedX, selectedY, getSelectedColor());
        }
    }

    private void dispatchMagnifierLayoutChanged() {
        if (magnifierLayoutChangedListener == null || !hasSelection() || dstRect.isEmpty()) {
            return;
        }
        drawMagnifierGeometry(getSelectedViewX(), getSelectedViewY());
        magnifierLayoutChangedListener.onMagnifierLayoutChanged(new RectF(magnifierRect));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
