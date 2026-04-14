package com.auto.master.capture;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import com.auto.master.auto.AutoAccessibilityService;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pull 模式 ScreenCaptureManager：
 * - 不使用 OnImageAvailableListener（避免主线程被回调轰炸）
 * - 由业务侧通过 getLatestMat()/pollLatestMat() 主动拉取
 * - Image -> Mat：无 Bitmap、双缓冲复用，降低 GC/卡顿
 * - getLatestMat() 保持你当前 API：返回 clone（线程安全，兼容旧代码）
 */
@TargetApi(21)
public class ScreenCaptureManager {

    private static final String TAG = "ScreenCaptureManager";
    private static volatile ScreenCaptureManager instance;

    public static ScreenCaptureManager getInstance() {
        if (instance == null) {
            synchronized (ScreenCaptureManager.class) {
                if (instance == null) instance = new ScreenCaptureManager();
            }
        }
        return instance;
    }

    // ===== 状态 =====
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger frameSeq = new AtomicInteger(0);
    private final Object frameLock = new Object();

    private Context appContext;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    // ===== 采集缩放比（降分辨率采集，减少 GPU 管线和内存带宽开销）=====
    // 注意：部分设备 MediaProjection 实现会将 VirtualDisplay 尺寸对齐到 16/32 的倍数，
    // 若 captureWidth/captureHeight 不是 16 的倍数，会导致 size mismatch → 无限 reset → 无帧。
    // 0.5f 在 1080x2340 等分辨率上会产生 1170（非16的倍数）而报错，故暂时设为 1.0f。
    // 待后续用 "对齐到16的倍数 + size 自适应更新" 方案再重新启用半分辨率。
    public static final float CAPTURE_SCALE = 1.0f;

    // ===== 屏幕参数 =====
    private int screenWidth, screenHeight, screenDpi;
    /** 实际采集分辨率（= screen × CAPTURE_SCALE，用于 ImageReader / VirtualDisplay）*/
    private int captureWidth, captureHeight;
    private volatile int lastRotation = Surface.ROTATION_0;

    // ===== 线程 =====
    private HandlerThread captureThread;
    private Handler captureHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ===== Mat 缓冲（单份）=====
    private Mat directMat;      // rowStride == width*4 快路径
    private Mat paddedMat;      // rowStride > width*4 慢路径（包含 padding）
    private Mat roiMat;         // paddedMat 的 colRange(0,width)，作为最终输出视图
    private Mat directRoiMat;   // ROI 专用小图，避免整屏转 Mat

    // ===== 字节缓冲复用 =====
    private byte[] frameBytes;
    private byte[] roiFrameBytes;
    private final Rect lastRoiRect = new Rect();

    // Pull 模式建议 1，避免堆积
    private static final int IMAGE_READER_MAX_IMAGES = 1;

    // 软限频（可选）
    private long lastLimitResetTime = 0;
    private int pollCountThisSecond = 0;
    /**
     * 1s 4个frames = 1000/4 = 250ms，降低采集频率减少发热
     */
    private static final int MAX_POLLS_PER_SECOND = 4;
    private static final int MAX_CONSECUTIVE_FRAME_ERRORS = 7;
    private static final long MAX_STALE_FRAME_AGE_MS = 500L;
    private static final long FRAME_HEALTH_CHECK_INTERVAL_MS = 1200L;
    private static final int BORDER_HEALTH_CHECK_WARMUP_PASSES = 3;
    private int consecutiveFrameFailures = 0;
    private long lastSuccessfulFrameMs = 0L;
    private long lastFrameHealthCheckMs = 0L;
    private int pendingBorderHealthChecks = BORDER_HEALTH_CHECK_WARMUP_PASSES;
    private volatile boolean resetScheduled = false;

    // 空闲自动暂停 VirtualDisplay：无人 poll 超过此时长则暂停，下次 poll 自动恢复。
    // 注意：不能设太小——VD 恢复是异步重建，期间 acquireLatestImage() 持续返回 null，
    // 若阈值小于常见操作（点击/手势/短延时）的耗时，会导致下次匹配一开始就在等 VD 重建，
    // 消耗大量超时时间，出现"一直拿到 null"的假性卡死。
    // 3000ms：手势/短延时通常 <2s，留足余量避免频繁暂停/恢复。
    private static final long IDLE_PAUSE_THRESHOLD_MS = 3000L;
    private static final long FULL_CLEANUP_IDLE_THRESHOLD_MS = 45_000L;
    private static final long RESUME_GRACE_WINDOW_MS = 800L;
    private volatile boolean displayPaused = false;
    private volatile long lastPollMs = 0;
    private volatile long lastResumeAttemptMs = 0L;
    private final Runnable idleCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning.get()) return;
            long now = System.currentTimeMillis();
            long idleMs = now - lastPollMs;
            if (idleMs > FULL_CLEANUP_IDLE_THRESHOLD_MS) {
                Log.i(TAG, "capture idle too long, cleanup session: " + idleMs + "ms");
                cleanup();
                return;
            }
            if (!displayPaused && virtualDisplay != null
                    && idleMs > IDLE_PAUSE_THRESHOLD_MS) {
                displayPaused = true;
                try {
                    virtualDisplay.setSurface(null);
                    Log.d(TAG, "VirtualDisplay paused (idle " + idleMs + "ms)");
                } catch (Throwable t) {
                    Log.w(TAG, "pause surface failed", t);
                }
            }
            if (captureHandler != null) {
                captureHandler.postDelayed(this, IDLE_PAUSE_THRESHOLD_MS);
            }
        }
    };

    // DisplayListener（替代 heartbeat）
    private DisplayManager displayManager;
    /**
     * 这里是屏幕旋转的监听器
     */
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId) {}
        @Override public void onDisplayRemoved(int displayId) {}

        @Override public void onDisplayChanged(int displayId) {
            if (!isRunning.get()) return;
            // 这里用你自己的 rotation 获取方法
            int rot = getCurrentPhysicalRotation(appContext);
            if (rot != lastRotation) {
                lastRotation = rot;
                Log.w(TAG, "Rotation changed -> resetVirtualDisplay(): " + rotationToString(rot));
                // 放到 capture 线程做 reset
                if (captureHandler != null) captureHandler.post(ScreenCaptureManager.this::resetVirtualDisplay);
            }
        }
    };

    private ScreenCaptureManager() {}

    public void init(Activity activity) {
        if (activity == null) return;

        appContext = activity.getApplicationContext();
        projectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        displayManager = (DisplayManager) activity.getSystemService(Context.DISPLAY_SERVICE);

        updateScreenMetrics(activity);
        lastRotation = getCurrentPhysicalRotation(activity);

        Log.i(TAG, "init ok rot=" + rotationToString(lastRotation)
                + " size=" + screenWidth + "x" + screenHeight + " dpi=" + screenDpi);
    }

    public boolean startCapture(int resultCode, Intent data) {
        if (isRunning.get()) {
            Log.w(TAG, "already running");
            return true;
        }
        if (projectionManager == null) {
            Log.e(TAG, "not init(Activity)");
            return false;
        }

        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection null");
                return false;
            }

            // capture thread 这里单独弄了个 新线程
            captureThread = new HandlerThread("ScreenCaptureThread");
            captureThread.start();
            captureHandler = new Handler(captureThread.getLooper());

            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    Log.w(TAG, "MediaProjection onStop()");
                    cleanup();
                }
            }, mainHandler);

            // 监听旋转 这里放在主线程
            if (displayManager != null) {
                displayManager.registerDisplayListener(displayListener, mainHandler);
            }

            // 在 capture 线程创建 VD
            captureHandler.post(this::resetVirtualDisplay);

            isRunning.set(true);
            frameSeq.set(0);
            lastPollMs = System.currentTimeMillis(); // 避免刚启动就被暂停
            lastSuccessfulFrameMs = 0L;
            lastFrameHealthCheckMs = 0L;
            consecutiveFrameFailures = 0;
            pendingBorderHealthChecks = BORDER_HEALTH_CHECK_WARMUP_PASSES;
            resetScheduled = false;
            captureHandler.postDelayed(idleCheckRunnable, IDLE_PAUSE_THRESHOLD_MS);

            Log.i(TAG, "startCapture ok");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "startCapture failed", t);
            cleanup();
            return false;
        }
    }

    public void stop() {
        cleanup();
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public int getFrameSeq() {
        return frameSeq.get();
    }

    /**
     * Pull：主动拉取最新帧并写入内部 Mat（单缓冲）
     */
    public boolean pollLatestMat() {
        synchronized (frameLock) {
            return pollLatestMatLocked();
        }
    }

    private boolean hasTransparentBorders(Image image) {
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            if (w < 20 || h < 20) return true;

            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int ps = plane.getPixelStride();
            int rs = plane.getRowStride();
            if (ps < 4) return true;

            int sampleCount = 40;
            int transparent = 0;
            int total = 0;

            // top / bottom
            for (int i = 0; i < sampleCount; i++) {
                int x = i * (w - 1) / (sampleCount - 1);

                total++;
                if ((buf.get(0 * rs + x * ps + 3) & 0xFF) <= 8) transparent++;

                total++;
                if ((buf.get((h - 1) * rs + x * ps + 3) & 0xFF) <= 8) transparent++;
            }

            // left / right
            for (int i = 1; i < sampleCount - 1; i++) {
                int y = i * (h - 1) / (sampleCount - 1);

                total++;
                if ((buf.get(y * rs + 0 * ps + 3) & 0xFF) <= 8) transparent++;

                total++;
                if ((buf.get(y * rs + (w - 1) * ps + 3) & 0xFF) <= 8) transparent++;
            }

            float ratio = total == 0 ? 1f : (float) transparent / total;
            return ratio > 0.85f;
        } catch (Throwable t) {
            return true;
        }
    }

    private boolean pollLatestMatLocked() {
        if (!isRunning.get() || imageReader == null) return false;

        // 更新最后 poll 时间（用于空闲检测）
        long now = System.currentTimeMillis();
        lastPollMs = now;

        // 如果 VirtualDisplay 被暂停，优先原地恢复 surface，避免整套重建。
        if (displayPaused && virtualDisplay != null && imageReader != null) {
            resumeVirtualDisplaySurface("resume_from_idle");
            return false; // 给恢复后的 producer 一点时间出首帧
        }

        // 软限频
        if (now - lastLimitResetTime >= 1000) {
            pollCountThisSecond = 0;
            lastLimitResetTime = now;
        }
        if (pollCountThisSecond++ >= MAX_POLLS_PER_SECOND) return false;

        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                if (!isInResumeGraceWindow(now)) {
                    recordFrameFailure("acquireLatestImage_null");
                }
                return false;
            }

            // 尺寸不一致直接 reset（旋转/分辨率变化时会发生）
            if (image.getWidth() != captureWidth || image.getHeight() != captureHeight) {
                Log.w(TAG, "size mismatch img=" + image.getWidth() + "x" + image.getHeight()
                        + " capture=" + captureWidth + "x" + captureHeight);
                recordFrameFailure("size_mismatch");
                return false;
            }

            if (shouldRunFrameHealthCheck(now)) {
                if (isImageBlackOrEmptyCenter(image)) {
                    recordFrameFailure("black_or_empty_center");
                    return false;
                }
                if (shouldCheckTransparentBorders() && hasTransparentBorders(image)) {
                    recordFrameFailure("has transparent border");
                    return false;
                }
            }

            if (!copyImageToMatFast(image)) {
                recordFrameFailure("copyImageToMatFast_failed");
                return false;
            }

            consecutiveFrameFailures = 0;
            lastSuccessfulFrameMs = now;
            if (pendingBorderHealthChecks > 0) {
                pendingBorderHealthChecks--;
            }
            frameSeq.incrementAndGet();
            return true;

        } catch (Throwable t) {
            Log.e(TAG, "pollLatestMat exception", t);
            recordFrameFailure("poll_exception");
            return false;
        } finally {
            if (image != null) image.close();
        }
    }

    /**
     * 最快：返回内部 Mat 引用（注意：下一次 poll 会覆盖内容）
     * 如果你要跨线程/长期持有：业务侧自行 clone()
     */
    public Mat getLatestMat(boolean clone) {
        synchronized (frameLock) {
            // ImageReader 的 maxImages=1，必须串行 acquire/close，避免并发拉帧刷 warning。
            boolean freshFrameLoaded = pollLatestMatLocked();
            Mat prototype = (roiMat != null) ? roiMat : directMat;
            if (prototype == null) {
                return null;
            }
            long now = System.currentTimeMillis();
            if (!freshFrameLoaded) {
                if (lastSuccessfulFrameMs <= 0L || (now - lastSuccessfulFrameMs) > MAX_STALE_FRAME_AGE_MS) {
                    return null;
                }
            }
            return clone ? prototype.clone() : prototype;
        }

    }

    /**
     * ROI Pull：只把 image 的指定区域拷贝进一个小 Mat，避免整屏 put。
     * 这是新增能力，不影响旧的 getLatestMat() 兼容性。
     */
    public Mat getLatestRoiMat(Rect roi, boolean clone) {
        synchronized (frameLock) {
            Rect safeRoi = sanitizeRoi(roi);
            if (safeRoi == null) {
                return getLatestMat(clone);
            }
            if (isFullScreenRoi(safeRoi)) {
                return getLatestMat(clone);
            }

            boolean freshFrameLoaded = pollLatestRoiMatLocked(safeRoi);
            if (directRoiMat == null || directRoiMat.empty()) {
                return null;
            }
            long now = System.currentTimeMillis();
            if (!freshFrameLoaded) {
                if (lastSuccessfulFrameMs <= 0L
                        || (now - lastSuccessfulFrameMs) > MAX_STALE_FRAME_AGE_MS
                        || !lastRoiRect.equals(safeRoi)) {
                    return null;
                }
            }
            return clone ? directRoiMat.clone() : directRoiMat;
        }
    }

    // ===== 核心：整帧一次性 put（快路径/慢路径）=====
    private boolean copyImageToMatFast(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();

        if (pixelStride != 4) {
            Log.w(TAG, "unexpected pixelStride=" + pixelStride);
            return false;
        }

        int w = captureWidth, h = captureHeight;
        int tightRowBytes = w * 4;

        // 需要拷贝的总字节数：h * rowStride（包含 padding）
        int totalBytes = h * rowStride;
        if (frameBytes == null || frameBytes.length < totalBytes) {
            frameBytes = new byte[totalBytes];
        }

        // 一次性读出
        ByteBuffer dup = buf.duplicate();
        dup.rewind();
        dup.get(frameBytes, 0, totalBytes);

        if (rowStride == tightRowBytes) {
            // 快路径：无 padding
            ensureDirectMat(w, h);
            directMat.put(0, 0, frameBytes, 0, h * tightRowBytes);
            roiMat = null; // 当前输出是 directMat
            return true;
        } else {
            // 慢路径：有 padding，用 paddedMat + ROI，仍然只 put 一次
            int paddedCols = rowStride / 4; // 每像素 4 字节
            ensurePaddedMat(paddedCols, h, w);
            paddedMat.put(0, 0, frameBytes, 0, totalBytes);
            // roiMat 已是 paddedMat 的 colRange(0,w)
            return true;
        }
    }

    private boolean pollLatestRoiMatLocked(Rect roi) {
        if (!isRunning.get() || imageReader == null) return false;

        long now = System.currentTimeMillis();
        lastPollMs = now;

        if (displayPaused && virtualDisplay != null && imageReader != null) {
            resumeVirtualDisplaySurface("resume_from_idle_roi");
            return false;
        }

        if (now - lastLimitResetTime >= 1000) {
            pollCountThisSecond = 0;
            lastLimitResetTime = now;
        }
        if (pollCountThisSecond++ >= MAX_POLLS_PER_SECOND) return false;

        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                if (!isInResumeGraceWindow(now)) {
                    recordFrameFailure("acquireLatestImage_null_roi");
                }
                return false;
            }
            if (image.getWidth() != captureWidth || image.getHeight() != captureHeight) {
                Log.w(TAG, "ROI size mismatch img=" + image.getWidth() + "x" + image.getHeight()
                        + " capture=" + captureWidth + "x" + captureHeight);
                recordFrameFailure("size_mismatch_roi");
                return false;
            }
            if (shouldRunFrameHealthCheck(now) && isImageBlackOrEmptyCenter(image)) {
                recordFrameFailure("black_or_empty_center_roi");
                return false;
            }
            if (!copyImageToRoiMatFast(image, roi)) {
                recordFrameFailure("copyImageToRoiMatFast_failed");
                return false;
            }

            lastRoiRect.set(roi);
            consecutiveFrameFailures = 0;
            lastSuccessfulFrameMs = now;
            frameSeq.incrementAndGet();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "pollLatestRoiMat exception", t);
            recordFrameFailure("poll_roi_exception");
            return false;
        } finally {
            if (image != null) image.close();
        }
    }

    private boolean copyImageToRoiMatFast(Image image, Rect roi) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();

        if (pixelStride != 4) {
            Log.w(TAG, "unexpected pixelStride for roi=" + pixelStride);
            return false;
        }

        int w = roi.width();
        int h = roi.height();
        if (w <= 0 || h <= 0) {
            return false;
        }

        int tightRowBytes = w * 4;
        int totalBytes = h * tightRowBytes;
        if (roiFrameBytes == null || roiFrameBytes.length < totalBytes) {
            roiFrameBytes = new byte[totalBytes];
        }

        ByteBuffer dup = buf.duplicate();
        if (roi.left == 0 && w == captureWidth && rowStride == captureWidth * pixelStride) {
            dup.position(roi.top * rowStride);
            dup.get(roiFrameBytes, 0, totalBytes);
        } else {
            for (int row = 0; row < h; row++) {
                int srcOffset = (roi.top + row) * rowStride + roi.left * pixelStride;
                dup.position(srcOffset);
                dup.get(roiFrameBytes, row * tightRowBytes, tightRowBytes);
            }
        }

        ensureDirectRoiMat(w, h);
        directRoiMat.put(0, 0, roiFrameBytes, 0, totalBytes);
        return true;
    }

    private void ensureDirectMat(int w, int h) {
        if (directMat != null && !directMat.empty()
                && directMat.width() == w && directMat.height() == h
                && directMat.type() == CvType.CV_8UC4) {
            return;
        }
        if (directMat != null) directMat.release();
        directMat = new Mat(h, w, CvType.CV_8UC4);
    }

    private void ensureDirectRoiMat(int w, int h) {
        if (directRoiMat != null && !directRoiMat.empty()
                && directRoiMat.width() == w && directRoiMat.height() == h
                && directRoiMat.type() == CvType.CV_8UC4) {
            return;
        }
        if (directRoiMat != null) directRoiMat.release();
        directRoiMat = new Mat(h, w, CvType.CV_8UC4);
    }

    private void ensurePaddedMat(int paddedCols, int h, int w) {
        // paddedMat 的列数可能随 rowStride 变化
        if (paddedMat != null && !paddedMat.empty()
                && paddedMat.width() == paddedCols && paddedMat.height() == h
                && paddedMat.type() == CvType.CV_8UC4) {
            // 确保 roiMat 正确
            if (roiMat == null || roiMat.width() != w || roiMat.height() != h) {
                roiMat = paddedMat.colRange(0, w);
            }
            return;
        }
        if (paddedMat != null) paddedMat.release();
        paddedMat = new Mat(h, paddedCols, CvType.CV_8UC4);
        roiMat = paddedMat.colRange(0, w);
    }

    // ===== VirtualDisplay 管理 =====
    private void resetVirtualDisplay() {
        if (mediaProjection == null) return;

        cleanupDisplayOnly();

        updateScreenMetrics(appContext);
        lastRotation = getCurrentPhysicalRotation(appContext);
        displayPaused = false;
        lastResumeAttemptMs = 0L;
        consecutiveFrameFailures = 0;
        lastFrameHealthCheckMs = 0L;
        pendingBorderHealthChecks = BORDER_HEALTH_CHECK_WARMUP_PASSES;

        Log.i(TAG, "resetVirtualDisplay rot=" + rotationToString(lastRotation)
                + " size=" + screenWidth + "x" + screenHeight);

        try {
            imageReader = ImageReader.newInstance(
                    captureWidth, captureHeight,
                    PixelFormat.RGBA_8888,
                    IMAGE_READER_MAX_IMAGES
            );

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenCapture",
                    captureWidth, captureHeight, screenDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null, captureHandler
            );

            Log.i(TAG, "VirtualDisplay reset ok");
        } catch (Throwable t) {
            Log.e(TAG, "resetVirtualDisplay failed", t);
            cleanup();
        }
    }

    private void recordFrameFailure(String reason) {
        pendingBorderHealthChecks = BORDER_HEALTH_CHECK_WARMUP_PASSES;
        int failureCount = ++consecutiveFrameFailures;
        if (failureCount >= MAX_CONSECUTIVE_FRAME_ERRORS) {
            consecutiveFrameFailures = 0;
            scheduleVirtualDisplayReset(reason + "_x" + MAX_CONSECUTIVE_FRAME_ERRORS);
        }
    }

    private void scheduleVirtualDisplayReset(String reason) {
        if (captureHandler == null) {
            return;
        }
        if (resetScheduled) {
            return;
        }
        resetScheduled = true;
        captureHandler.post(() -> {
            try {
                Log.w(TAG, "schedule resetVirtualDisplay: " + reason);
                resetVirtualDisplay();
            } finally {
                resetScheduled = false;
            }
        });
    }

    private void cleanupDisplayOnly() {
        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
        } catch (Throwable ignored) {}

        try {
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Throwable ignored) {}
    }

    public void cleanup() {
        isRunning.set(false);
        displayPaused = false;
        lastResumeAttemptMs = 0L;
        resetScheduled = false;
        consecutiveFrameFailures = 0;
        lastSuccessfulFrameMs = 0L;
        pendingBorderHealthChecks = BORDER_HEALTH_CHECK_WARMUP_PASSES;

        // 停止空闲检查
        try {
            if (captureHandler != null) captureHandler.removeCallbacks(idleCheckRunnable);
        } catch (Throwable ignored) {}

        // 取消监听
        try {
            if (displayManager != null) displayManager.unregisterDisplayListener(displayListener);
        } catch (Throwable ignored) {}

        cleanupDisplayOnly();

        try {
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
        } catch (Throwable ignored) {}

        try {
            if (directMat != null) { directMat.release(); directMat = null; }
            if (roiMat != null) { roiMat.release(); roiMat = null; } // 注意：roiMat 是 view，release 可选；不想踩坑可不 release
            if (paddedMat != null) { paddedMat.release(); paddedMat = null; }
            if (directRoiMat != null) { directRoiMat.release(); directRoiMat = null; }
        } catch (Throwable ignored) {}

        frameBytes = null;
        roiFrameBytes = null;
        lastRoiRect.setEmpty();

        // 线程退出
        try {
            if (captureThread != null) {
                captureThread.quitSafely();
                captureThread = null;
                captureHandler = null;
            }
        } catch (Throwable ignored) {}

        Log.i(TAG, "cleanup done");
    }

    private boolean shouldRunFrameHealthCheck(long nowMs) {
        if (lastSuccessfulFrameMs <= 0L || consecutiveFrameFailures > 0) {
            lastFrameHealthCheckMs = nowMs;
            return true;
        }
        if ((nowMs - lastFrameHealthCheckMs) >= FRAME_HEALTH_CHECK_INTERVAL_MS) {
            lastFrameHealthCheckMs = nowMs;
            return true;
        }
        return false;
    }

    private boolean shouldCheckTransparentBorders() {
        return pendingBorderHealthChecks > 0 || consecutiveFrameFailures > 0 || lastSuccessfulFrameMs <= 0L;
    }

    private boolean resumeVirtualDisplaySurface(String reason) {
        if (!displayPaused || virtualDisplay == null || imageReader == null) {
            return false;
        }
        try {
            Surface surface = imageReader.getSurface();
            if (surface == null || !surface.isValid()) {
                throw new IllegalStateException("imageReader surface invalid");
            }
            virtualDisplay.setSurface(surface);
            displayPaused = false;
            lastResumeAttemptMs = System.currentTimeMillis();
            lastPollMs = lastResumeAttemptMs;
            consecutiveFrameFailures = 0;
            pendingBorderHealthChecks = BORDER_HEALTH_CHECK_WARMUP_PASSES;
            Log.d(TAG, "VirtualDisplay resumed in-place: " + reason);
            return true;
        } catch (Throwable t) {
            displayPaused = false;
            Log.w(TAG, "resume surface failed, fallback to reset: " + reason, t);
            scheduleVirtualDisplayReset(reason + "_surface_resume_failed");
            return false;
        }
    }

    private boolean isInResumeGraceWindow(long nowMs) {
        return lastResumeAttemptMs > 0L && (nowMs - lastResumeAttemptMs) < RESUME_GRACE_WINDOW_MS;
    }

    // ===== 轻量黑帧检测：可关掉换极限性能 =====
    private boolean isImageBlackOrEmptyCenter(Image image) {
        try {
            int w = image.getWidth();
            int h = image.getHeight();
            if (w < 100 || h < 100) return true;

            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int ps = plane.getPixelStride();
            int rs = plane.getRowStride();
            if (ps < 4) return true;

            int cx = w / 2, cy = h / 2;
            int idx = cy * rs + cx * ps;

            int a = buf.get(idx + 3) & 0xFF;
            if (a == 0) return true;

            int r = buf.get(idx) & 0xFF;
            int g = buf.get(idx + 1) & 0xFF;
            int b = buf.get(idx + 2) & 0xFF;

            return (r < 10 && g < 10 && b < 10);
        } catch (Throwable t) {
            return true;
        }
    }

    // ===== util =====
    private void updateScreenMetrics(Context context) {
        if (context == null) return;
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDpi = metrics.densityDpi;
        // 计算采集分辨率（偶数对齐，避免编解码对齐问题）
        captureWidth  = (int)(screenWidth  * CAPTURE_SCALE) & ~1;
        captureHeight = (int)(screenHeight * CAPTURE_SCALE) & ~1;
    }

    private int getCurrentPhysicalRotation(Context context) {
        return AutoAccessibilityService.getCurrentPhysicalRotation(context);
    }

    private String rotationToString(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0:   return "竖屏(0)";
            case Surface.ROTATION_90:  return "横屏(90)";
            case Surface.ROTATION_180: return "倒竖(180)";
            case Surface.ROTATION_270: return "横屏(270)";
            default: return "未知(" + rotation + ")";
        }
    }

    /**
     * 将 screen 坐标系的 ROI 转换为 capture 坐标系，并夹紧到 capture 分辨率。
     * handlers 传入的 roi 始终使用 screen 坐标；ScreenCaptureManager 内部统一用 capture 坐标。
     */
    private Rect sanitizeRoi(Rect roi) {
        if (roi == null) {
            return null;
        }
        int left   = Math.max(0, (int)(roi.left   * CAPTURE_SCALE));
        int top    = Math.max(0, (int)(roi.top    * CAPTURE_SCALE));
        int right  = Math.min(captureWidth,  (int)(roi.right  * CAPTURE_SCALE));
        int bottom = Math.min(captureHeight, (int)(roi.bottom * CAPTURE_SCALE));
        if (right <= left || bottom <= top) {
            return null;
        }
        return new Rect(left, top, right, bottom);
    }

    private boolean isFullScreenRoi(Rect roi) {
        return roi.left == 0
                && roi.top == 0
                && roi.right == captureWidth
                && roi.bottom == captureHeight;
    }
}
