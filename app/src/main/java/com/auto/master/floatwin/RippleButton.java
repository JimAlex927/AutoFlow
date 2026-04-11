package com.auto.master.floatwin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * 带涟漪效果的按钮容器
 * 点击时会产生水波纹扩散效果
 */
public class RippleButton extends FrameLayout {

    // 静态复用，避免每次点击 new 一个 Interpolator
    private static final android.view.animation.Interpolator RIPPLE_INTERPOLATOR =
            new AccelerateDecelerateInterpolator();

    private Paint ripplePaint;
    private float rippleRadius = 0f;
    private float rippleX = 0f;
    private float rippleY = 0f;
    private boolean isRippling = false;
    private int rippleColor = 0x80FFFFFF; // 半透明白色
    private ValueAnimator rippleAnimator;
    
    public RippleButton(Context context) {
        super(context);
        init();
    }
    
    public RippleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public RippleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ripplePaint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
        setClickable(true);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 记录触摸位置
                rippleX = event.getX();
                rippleY = event.getY();
                startRipple();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 抬起时继续扩散
                break;
        }
        return super.onTouchEvent(event);
    }
    
    private void startRipple() {
        if (rippleAnimator != null && rippleAnimator.isRunning()) {
            rippleAnimator.cancel();
        }
        
        // 计算最大半径（对角线距离）
        float maxRadius = (float) Math.sqrt(
            Math.pow(Math.max(rippleX, getWidth() - rippleX), 2) + 
            Math.pow(Math.max(rippleY, getHeight() - rippleY), 2)
        );
        
        isRippling = true;
        rippleRadius = 0f;
        
        rippleAnimator = ValueAnimator.ofFloat(0f, maxRadius);
        rippleAnimator.setDuration(400);
        rippleAnimator.setInterpolator(RIPPLE_INTERPOLATOR);
        rippleAnimator.addUpdateListener(animation -> {
            rippleRadius = (float) animation.getAnimatedValue();
            // 渐变透明度
            int alpha = (int) (128 * (1 - animation.getAnimatedFraction()));
            ripplePaint.setColor((alpha << 24) | 0xFFFFFF);
            invalidate();
        });
        rippleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isRippling = false;
                rippleRadius = 0f;
                invalidate();
            }
        });
        rippleAnimator.start();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (isRippling && rippleRadius > 0) {
            // 绘制涟漪
            canvas.drawCircle(rippleX, rippleY, rippleRadius, ripplePaint);
        }
    }
    
    /**
     * 触发涟漪效果（用于代码触发）
     */
    public void performRipple() {
        rippleX = getWidth() / 2f;
        rippleY = getHeight() / 2f;
        startRipple();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (rippleAnimator != null) {
            rippleAnimator.cancel();
        }
    }
}
