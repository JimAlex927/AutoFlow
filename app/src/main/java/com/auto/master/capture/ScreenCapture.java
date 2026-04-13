package com.auto.master.capture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.projection.MediaProjectionManager;
import android.os.SystemClock;
import android.util.Log;

import com.auto.master.auto.ActivityHolder;
import com.auto.master.utils.AdaptivePollingController;
import com.auto.master.utils.OpenCVHelper;

import org.opencv.core.Mat;

public final class ScreenCapture {

    private static final String TAG = "ScreenCapture";
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 3000L;
    private static final long DEFAULT_WAIT_INTERVAL_MS = 80L;

    // 静态保存授权（保持兼容）
    private static volatile int sResultCode = 0;
    private static volatile Intent sResultData = null;

    public static Intent createProjectionIntent(Activity a) {
        MediaProjectionManager mpm = (MediaProjectionManager) a.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        return mpm.createScreenCaptureIntent();
    }

    public static void saveProjectionPermission(int resultCode, Intent data) {
        sResultCode = resultCode;
        sResultData = data;
    }

    public static boolean hasProjectionPermission() {
        return sResultCode != 0 && sResultData != null;
    }

    private static boolean ensureCaptureSession(Activity activity) {
        if (!hasProjectionPermission()) {
            Log.e(TAG, "缺少录屏权限");
            return false;
        }
        ScreenCaptureManager manager = ScreenCaptureManager.getInstance();
        if (activity != null) {
            manager.init(activity);
        }
        if (manager.isRunning()) {
            return true;
        }
        if (sResultData == null) {
            Log.e(TAG, "录屏权限数据为空");
            return false;
        }
        boolean started = manager.startCapture(sResultCode, sResultData);
        if (!started) {
            Log.e(TAG, "启动录屏失败");
        }
        return started;
    }

    /**
     * 统一截图入口 实际开启录屏的 入口 初始化
     */
    public static Mat captureNow(Activity activity, Method method, String outName) {
        Log.d(TAG, "captureNow: method=" + method + ", name=" + outName);

        if (method != Method.MEDIA_PROJECTION_SINGLE_SHOOT && method != Method.MEDIA_PROJECTION) {
            Log.e(TAG, "不支持的截图方式或服务未启动");
            return null;
        }

        ScreenCaptureManager manager = ScreenCaptureManager.getInstance();
        if (!ensureCaptureSession(activity)) {
            return null;
        }
        return waitForLatestFrame(null, DEFAULT_WAIT_TIMEOUT_MS, DEFAULT_WAIT_INTERVAL_MS);
    }

    public static Mat captureRoiNow(Activity activity, Method method, Rect roi, String outName) {
        Log.d(TAG, "captureRoiNow: method=" + method + ", roi=" + roi + ", name=" + outName);

        if (roi == null || roi.isEmpty()) {
            return captureNow(activity, method, outName);
        }
        if (method != Method.MEDIA_PROJECTION_SINGLE_SHOOT && method != Method.MEDIA_PROJECTION) {
            Log.e(TAG, "不支持的截图方式或服务未启动");
            return null;
        }

        ScreenCaptureManager manager = ScreenCaptureManager.getInstance();
        if (!ensureCaptureSession(activity)) {
            return null;
        }
        return waitForLatestFrame(roi, DEFAULT_WAIT_TIMEOUT_MS, DEFAULT_WAIT_INTERVAL_MS);
    }


    /**
     * 这里能拿到正确图片的前提是 1、有权限 2、captureNow开启了 3、尺寸纠正了
     * @return
     */
    public static Mat getSingleBitMapWhileInContinous(boolean clone){

        if (!ScreenCaptureManager.getInstance().isRunning()) {
            ensureCaptureSession(ActivityHolder.getTopActivity());
        }

        /**
         * 如果clone 为 true ，多线程安全，则消费者自行release
         * 如果clone为 false，只允许单线程使用，消费者无需release。
         */
        Mat mat = ScreenCaptureManager.getInstance().getLatestMat(clone);


        return mat;

    }

    public static Mat getSingleBitMapRoiWhileInContinous(Rect roi, boolean clone) {
        if (roi == null || roi.isEmpty()) {
            return getSingleBitMapWhileInContinous(clone);
        }
        if (!ScreenCaptureManager.getInstance().isRunning()) {
            ensureCaptureSession(ActivityHolder.getTopActivity());
        }
        return ScreenCaptureManager.getInstance().getLatestRoiMat(roi, clone);
    }

    public static int getFrameSequence() {
        return ScreenCaptureManager.getInstance().getFrameSeq();
    }

    public static Mat waitForLatestFrame(Rect roi, long timeoutMs, long intervalMs) {
        if (!ScreenCaptureManager.getInstance().isRunning()) {
            ensureCaptureSession(ActivityHolder.getTopActivity());
        }
        AdaptivePollingController pollingController = AdaptivePollingController.forTemplateMatch();
        long safeTimeoutMs = Math.max(intervalMs, timeoutMs);
        long safeIntervalMs = Math.max(10L, intervalMs);
        long deadline = SystemClock.uptimeMillis() + safeTimeoutMs;
        Mat fallback = null;
        while (SystemClock.uptimeMillis() <= deadline) {
            Mat frame = (roi == null || roi.isEmpty())
                    ? pollingController.acquireFrame()
                    : pollingController.acquireFrame(roi);
            if (frame != null && !frame.empty()) {
                if (pollingController.hasFreshFrame()) {
                    return frame;
                }
                if (fallback == null) {
                    fallback = frame;
                }
            }
            SystemClock.sleep(safeIntervalMs);
        }
        return fallback;
    }

    public static Bitmap captureLatestBitmap(Rect roi, long timeoutMs, long intervalMs) {
        Mat mat = waitForLatestFrame(roi, timeoutMs, intervalMs);
        if (mat == null || mat.empty()) {
            return null;
        }
        return OpenCVHelper.getInstance().matToBitmap(mat);
    }

    public enum Method {
        MEDIA_PROJECTION,
        MEDIA_PROJECTION_SINGLE_SHOOT,
        PIXEL_COPY,
        A11Y_DUMP
    }
}
