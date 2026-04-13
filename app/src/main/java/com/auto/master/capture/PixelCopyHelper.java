package com.auto.master.capture;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;

public final class PixelCopyHelper {

    private static final String TAG = "PixelCopyHelper";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private PixelCopyHelper() {}

    public static void captureWindowToFile(Activity activity, String fileName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.e(TAG, "PixelCopy Window requires API 26+");
            return;
        }

        // 等布局完成，否则宽高可能还没正确
        final View root = activity.getWindow().getDecorView();
        root.post(() -> {
            int w, h;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
                Rect b = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
                w = b.width();
                h = b.height();
            } else {
                DisplayMetrics dm = new DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
                w = dm.widthPixels;
                h = dm.heightPixels;
            }

            if (w <= 0 || h <= 0) {
                Log.e(TAG, "Invalid window size w=" + w + " h=" + h);
                return;
            }

            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

            PixelCopy.request(activity.getWindow(), bmp, result -> {
                if (result == PixelCopy.SUCCESS) {
                    File out = FileUtil.makeCaptureFile(activity.getApplicationContext(), fileName);
                    if (saveBitmap(bmp, out)) {
                        Log.d(TAG, "Saved PixelCopy screenshot: " + out.getAbsolutePath() + " size=" + w + "x" + h);
                    }
                } else {
                    Log.e(TAG, "PixelCopy failed code=" + result);
                }
            }, MAIN_HANDLER);
        });
    }

    private static boolean saveBitmap(Bitmap bmp, File file) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "saveBitmap error", e);
            return false;
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
        }
    }
}
