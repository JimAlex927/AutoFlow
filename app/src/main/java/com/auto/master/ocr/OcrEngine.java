package com.auto.master.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

/**
 * OCR is currently disabled to keep the app lightweight.
 * Leave this compatibility shim in place so older OCR nodes don't crash.
 */
public final class OcrEngine {
    private static final String TAG = "OcrEngine";

    private OcrEngine() {
    }

    public static boolean isAvailable() {
        return false;
    }

    public static String recognize(Context context, Bitmap src, String engine, boolean accurateMode, long timeoutMs) {
        Log.w(TAG, "OCR is disabled in this build");
        return "";
    }
}
