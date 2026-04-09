package com.auto.master.auto;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;


import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

public class CropSelectionView extends View {

    private final Bitmap screenshot;

    // 映射：bitmap -> view
    private final RectF dst = new RectF();

    // 选区（view 坐标）
    private final RectF sel = new RectF();
    private boolean hasSelection = false;

    // 交互状态
    private enum Mode { NONE, NEW, MOVE, RESIZE }
    private Mode mode = Mode.NONE;

    // resize 句柄类型（8向）
    private enum Handle {
        NONE,
        LEFT, TOP, RIGHT, BOTTOM,
        LT, RT, LB, RB
    }
    private Handle activeHandle = Handle.NONE;

    private float downX, downY;
    private final RectF selAtDown = new RectF();

    // 视觉参数
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 放大镜
    private boolean magnifierEnabled = true;
    private final Paint magnifierBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect magnifierSrc = new Rect();
    private final RectF magnifierDst = new RectF();

    // 配置
    private final float handleRadius;
    private final float touchSlop;
    private final float minSelSize;
    private final float magnifierSize;
    private final float magnifierZoom; // 2~4 比较舒服
    private final float textPadding;

    // 最近触点，用于放大镜
    private float lastX, lastY;
    private boolean showMagnifier = false;

    public CropSelectionView(Context ctx, Bitmap screenshot) {
        super(ctx);
        this.screenshot = screenshot;

        float d = getResources().getDisplayMetrics().density;
        handleRadius = 2f * d;
        touchSlop = 18f * d;
        minSelSize = 30f * d;
        magnifierSize = 140f * d;
        magnifierZoom = 3.0f;
        textPadding = 6f * d;

        maskPaint.setColor(0x99000000);

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2.5f * d);

        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);

        textBgPaint.setColor(0xCC000000);
        textBgPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(14f * d);

        magnifierBorderPaint.setColor(Color.WHITE);
        magnifierBorderPaint.setStyle(Paint.Style.STROKE);
        magnifierBorderPaint.setStrokeWidth(2f * d);

        setClickable(true);
    }

    public void setMagnifierEnabled(boolean enabled) {
        magnifierEnabled = enabled;
        invalidate();
    }

    /** 返回 bitmap 坐标系 rect；没选区则返回 null */
    public Rect getSelectedRectOnBitmap() {
        if (!hasSelection || sel.width() < 1 || sel.height() < 1) return null;

        RectF clipped = new RectF(sel);
        if (!clipped.intersect(dst)) return null;

        float sx = screenshot.getWidth() / dst.width();
        float sy = screenshot.getHeight() / dst.height();

        int left = (int) ((clipped.left - dst.left) * sx);
        int top = (int) ((clipped.top - dst.top) * sy);
        int right = (int) ((clipped.right - dst.left) * sx);
        int bottom = (int) ((clipped.bottom - dst.top) * sy);

        // clamp + 至少1px
        left = clamp(left, 0, screenshot.getWidth() - 1);
        top = clamp(top, 0, screenshot.getHeight() - 1);
        right = clamp(right, left + 1, screenshot.getWidth());
        bottom = clamp(bottom, top + 1, screenshot.getHeight());

        return new Rect(left, top, right, bottom);
    }

    /** 清空选区 */
    public void clearSelection() {
        hasSelection = false;
        sel.setEmpty();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutDst(w, h);
    }

    private void layoutDst(int vw, int vh) {
        float bw = screenshot.getWidth();
        float bh = screenshot.getHeight();
        float scale = Math.min(vw / bw, vh / bh);
        float dw = bw * scale;
        float dh = bh * scale;
        float left = (vw - dw) / 2f;
        float top = (vh - dh) / 2f;
        dst.set(left, top, left + dw, top + dh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (screenshot == null || screenshot.isRecycled()) return;

        // 画底图
        canvas.drawBitmap(screenshot, null, dst, bitmapPaint);

        // 遮罩（用离屏层才能 CLEAR “挖洞”）
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), maskPaint);

        if (hasSelection) {
            canvas.drawRect(sel, clearPaint); // 挖洞
        }
        canvas.restoreToCount(save);

        if (hasSelection) {
            // 边框
            canvas.drawRect(sel, borderPaint);
            // 尺寸
            drawSizeLabel(canvas);
        }

        if (magnifierEnabled && showMagnifier) {
            drawMagnifier(canvas, lastX, lastY);
        }
    }

    private void drawHandles(Canvas canvas) {
        // 8 个点
        float l = sel.left, t = sel.top, r = sel.right, b = sel.bottom;
        float cx = (l + r) / 2f;
        float cy = (t + b) / 2f;

        canvas.drawCircle(l, t, handleRadius, handlePaint); // LT
        canvas.drawCircle(r, t, handleRadius, handlePaint); // RT
        canvas.drawCircle(l, b, handleRadius, handlePaint); // LB
        canvas.drawCircle(r, b, handleRadius, handlePaint); // RB

        canvas.drawCircle(cx, t, handleRadius, handlePaint); // TOP
        canvas.drawCircle(cx, b, handleRadius, handlePaint); // BOTTOM
        canvas.drawCircle(l, cy, handleRadius, handlePaint); // LEFT
        canvas.drawCircle(r, cy, handleRadius, handlePaint); // RIGHT
    }

    private void drawSizeLabel(Canvas canvas) {
        Rect br = getSelectedRectOnBitmap();
        if (br == null) return;
        String txt = br.width() + "×" + br.height();

        float tw = textPaint.measureText(txt);
        float th = textPaint.getFontMetrics().bottom - textPaint.getFontMetrics().top;

        float x = sel.left;
        float y = sel.top - (th + 2 * textPadding);
        if (y < dst.top) y = sel.bottom + 2 * textPadding; // 上方放不下就放下方

        RectF bg = new RectF(
                x,
                y,
                x + tw + 2 * textPadding,
                y + th + 2 * textPadding
        );
        canvas.drawRoundRect(bg, 8, 8, textBgPaint);
        canvas.drawText(txt, bg.left + textPadding, bg.top + textPadding - textPaint.getFontMetrics().top, textPaint);
    }

    private void drawMagnifier(Canvas canvas, float vx, float vy) {
        // 触点必须在 dst 内，否则不显示
        if (!dst.contains(vx, vy)) return;

        // 计算放大镜放哪：放在触点上方偏右，避免挡手
        float left = vx + 16f;
        float top = vy - magnifierSize - 16f;
        if (left + magnifierSize > getWidth()) left = vx - magnifierSize - 16f;
        if (top < 0) top = vy + 16f;

        magnifierDst.set(left, top, left + magnifierSize, top + magnifierSize);

        // 计算 src：从 bitmap 中取一块
        float sx = screenshot.getWidth() / dst.width();
        float sy = screenshot.getHeight() / dst.height();

        int bx = (int) ((vx - dst.left) * sx);
        int by = (int) ((vy - dst.top) * sy);

        int halfW = (int) (magnifierSize / magnifierZoom / 2f);
        int halfH = (int) (magnifierSize / magnifierZoom / 2f);

        int sl = clamp(bx - halfW, 0, screenshot.getWidth() - 1);
        int st = clamp(by - halfH, 0, screenshot.getHeight() - 1);
        int sr = clamp(bx + halfW, sl + 1, screenshot.getWidth());
        int sb = clamp(by + halfH, st + 1, screenshot.getHeight());

        magnifierSrc.set(sl, st, sr, sb);

        // 画放大镜内容（圆形裁剪）
        int save = canvas.save();
        Path clip = new Path();
        clip.addOval(magnifierDst, Path.Direction.CW);
        canvas.clipPath(clip);

        canvas.drawColor(0xFF000000); // 背色
        canvas.drawBitmap(screenshot, magnifierSrc, magnifierDst, bitmapPaint);

        // 十字线（像素对齐辅助）
        Paint cross = borderPaint;
        float cx = magnifierDst.centerX();
        float cy = magnifierDst.centerY();
        canvas.drawLine(cx, magnifierDst.top, cx, magnifierDst.bottom, cross);
        canvas.drawLine(magnifierDst.left, cy, magnifierDst.right, cy, cross);

        canvas.restoreToCount(save);

        // 边框
        canvas.drawOval(magnifierDst, magnifierBorderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        lastX = x; lastY = y;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x; downY = y;
                selAtDown.set(sel);

                showMagnifier = true;

                if (!dst.contains(x, y)) {
                    // 点击截图区域外：当作取消选择
                    clearSelection();
                    mode = Mode.NONE;
                    activeHandle = Handle.NONE;
                    invalidate();
                    return true;
                }

                if (hasSelection) {
                    activeHandle = hitTestHandle(x, y);
                    if (activeHandle != Handle.NONE) {
                        mode = Mode.RESIZE;
                        invalidate();
                        return true;
                    }
                    if (sel.contains(x, y)) {
                        mode = Mode.MOVE;
                        invalidate();
                        return true;
                    }

                    // 点击空白：取消选区（你要求的）
                    clearSelection();
                    mode = Mode.NONE;
                    activeHandle = Handle.NONE;
                    invalidate();
                    return true;
                } else {
                    // 新建选区
                    mode = Mode.NEW;
                    hasSelection = true;
                    sel.set(x, y, x, y);
                    clampSelToDst();
                    invalidate();
                    return true;
                }

            case MotionEvent.ACTION_MOVE:
                if (!dst.contains(x, y) && mode == Mode.NEW) {
                    // 新建时允许拖出边界，但 clamp 后仍在 dst 内
                }

                if (mode == Mode.NEW) {
                    sel.set(
                            Math.min(downX, x),
                            Math.min(downY, y),
                            Math.max(downX, x),
                            Math.max(downY, y)
                    );
                    clampSelToDst();
                    invalidate();
                    return true;
                }

                if (mode == Mode.MOVE) {
                    float dx = x - downX;
                    float dy = y - downY;
                    sel.set(selAtDown);
                    sel.offset(dx, dy);
                    clampSelToDstKeepSize();
                    invalidate();
                    return true;
                }

                if (mode == Mode.RESIZE) {
                    sel.set(selAtDown);
                    applyResize(activeHandle, x, y);
                    enforceMinSize();
                    clampSelToDst();
                    invalidate();
                    return true;
                }

                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                showMagnifier = false;

                if (hasSelection && (sel.width() < minSelSize || sel.height() < minSelSize)) {
                    // 太小就当无效
                    clearSelection();
                }
                mode = Mode.NONE;
                activeHandle = Handle.NONE;
                invalidate();
                return true;
        }

        return super.onTouchEvent(e);
    }

    private Handle hitTestHandle(float x, float y) {
        float l = sel.left, t = sel.top, r = sel.right, b = sel.bottom;
        float cx = (l + r) / 2f;
        float cy = (t + b) / 2f;

        // 先测四角
        if (dist(x, y, l, t) <= touchSlop) return Handle.LT;
        if (dist(x, y, r, t) <= touchSlop) return Handle.RT;
        if (dist(x, y, l, b) <= touchSlop) return Handle.LB;
        if (dist(x, y, r, b) <= touchSlop) return Handle.RB;

        // 再测四边中点
        if (dist(x, y, cx, t) <= touchSlop) return Handle.TOP;
        if (dist(x, y, cx, b) <= touchSlop) return Handle.BOTTOM;
        if (dist(x, y, l, cy) <= touchSlop) return Handle.LEFT;
        if (dist(x, y, r, cy) <= touchSlop) return Handle.RIGHT;

        return Handle.NONE;
    }

    private void applyResize(Handle h, float x, float y) {
        switch (h) {
            case LEFT:
                sel.left = x; break;
            case RIGHT:
                sel.right = x; break;
            case TOP:
                sel.top = y; break;
            case BOTTOM:
                sel.bottom = y; break;

            case LT:
                sel.left = x; sel.top = y; break;
            case RT:
                sel.right = x; sel.top = y; break;
            case LB:
                sel.left = x; sel.bottom = y; break;
            case RB:
                sel.right = x; sel.bottom = y; break;
        }

        // 保证 left<=right / top<=bottom
        if (sel.left > sel.right) {
            float mid = (sel.left + sel.right) / 2f;
            sel.left = mid; sel.right = mid;
        }
        if (sel.top > sel.bottom) {
            float mid = (sel.top + sel.bottom) / 2f;
            sel.top = mid; sel.bottom = mid;
        }
    }

    private void enforceMinSize() {
        if (sel.width() < minSelSize) {
            float cx = sel.centerX();
            sel.left = cx - minSelSize / 2f;
            sel.right = cx + minSelSize / 2f;
        }
        if (sel.height() < minSelSize) {
            float cy = sel.centerY();
            sel.top = cy - minSelSize / 2f;
            sel.bottom = cy + minSelSize / 2f;
        }
    }

    private void clampSelToDst() {
        // 保证选区在 dst 内（允许缩小）
        if (sel.left < dst.left) sel.left = dst.left;
        if (sel.top < dst.top) sel.top = dst.top;
        if (sel.right > dst.right) sel.right = dst.right;
        if (sel.bottom > dst.bottom) sel.bottom = dst.bottom;

        // 如果被夹到反转，再修正
        if (sel.right < sel.left) sel.right = sel.left;
        if (sel.bottom < sel.top) sel.bottom = sel.top;
    }

    private void clampSelToDstKeepSize() {
        // 移动时保持 size，只做边界推回
        float w = sel.width();
        float h = sel.height();

        if (sel.left < dst.left) {
            sel.left = dst.left;
            sel.right = sel.left + w;
        }
        if (sel.top < dst.top) {
            sel.top = dst.top;
            sel.bottom = sel.top + h;
        }
        if (sel.right > dst.right) {
            sel.right = dst.right;
            sel.left = sel.right - w;
        }
        if (sel.bottom > dst.bottom) {
            sel.bottom = dst.bottom;
            sel.top = sel.bottom - h;
        }
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.hypot(dx, dy);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
