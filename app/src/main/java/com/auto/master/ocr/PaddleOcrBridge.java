package com.auto.master.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.lang.reflect.Method;

public final class PaddleOcrBridge {
    private static final String TAG = "PaddleOcrBridge";
    private static Object predictor;
    private static Method mInit;
    private static Method mSetInputImage;
    private static Method mRunModel;
    private static Method mOutputResult;
    private static boolean triedInit = false;

    private PaddleOcrBridge() {
    }

    public static synchronized boolean ensureReady(Context context) {
        if (predictor != null) {
            return true;
        }
        if (triedInit) {
            return false;
        }
        triedInit = true;
        try {
            Class<?> clz = Class.forName("com.litongjava.android.paddle.ocr.Predictor");
            Object instance = clz.newInstance();
            Method init = clz.getMethod("init", Context.class);
            Method setInputImage = clz.getMethod("setInputImage", Bitmap.class);
            Method runModel = clz.getMethod("runModel");
            Method outputResult = clz.getMethod("outputResult");

            Object ok = init.invoke(instance, context.getApplicationContext());
            if (!(ok instanceof Boolean) || !((Boolean) ok)) {
                Log.w(TAG, "paddle predictor init failed");
                return false;
            }
            predictor = instance;
            mInit = init;
            mSetInputImage = setInputImage;
            mRunModel = runModel;
            mOutputResult = outputResult;
            Log.i(TAG, "paddle predictor ready");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "paddle sdk unavailable", t);
            return false;
        }
    }

    public static synchronized String recognize(Context context, Bitmap bitmap) {
        if (bitmap == null) {
            return "";
        }
        if (!ensureReady(context)) {
            return "";
        }
        try {
            mSetInputImage.invoke(predictor, bitmap);
            Object ran = mRunModel.invoke(predictor);
            if (ran instanceof Boolean && !((Boolean) ran)) {
                return "";
            }
            Object out = mOutputResult.invoke(predictor);
            return out == null ? "" : String.valueOf(out);
        } catch (Throwable t) {
            Log.w(TAG, "paddle run failed", t);
            return "";
        }
    }
}
