package com.auto.master.capture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.media.projection.MediaProjectionManager;
import android.os.SystemClock;
import android.util.Log;

import com.auto.master.auto.ActivityHolder;

import org.opencv.core.Mat;

public final class ScreenCapture {

    private static final String TAG = "ScreenCapture";

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

        //如果启动成功了这个时候返回可能是null  manager.getLatestScreenshot()
        Mat mat = null;
        long start1 = System.currentTimeMillis();
        while (System.currentTimeMillis() - start1 < 3000) {
            mat = manager.getLatestMat(false); // 内部会 poll
            if (mat != null && !mat.empty()) {
                return mat;
            }
            SystemClock.sleep(199);
        }
        //超时返回null
        return null;
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

        long start = System.currentTimeMillis();
        Mat mat = null;
        while (System.currentTimeMillis() - start < 3000) {
            mat = manager.getLatestRoiMat(roi, false);
            if (mat != null && !mat.empty()) {
                return mat;
            }
            SystemClock.sleep(199);
        }
        return null;
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
    public enum Method {
        MEDIA_PROJECTION,
        MEDIA_PROJECTION_SINGLE_SHOOT,
        PIXEL_COPY,
        A11Y_DUMP
    }
}
