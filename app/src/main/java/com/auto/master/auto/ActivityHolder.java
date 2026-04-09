package com.auto.master.auto;

import static android.content.ContentValues.TAG;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

public final class ActivityHolder implements Application.ActivityLifecycleCallbacks {

    private static volatile Activity sTop;

    public static Activity getTopActivity() { return sTop; }


    /**
     * 🔥 添加注册方法
     */
    public static void register(Application app) {
        app.registerActivityLifecycleCallbacks(new ActivityHolder());
        Log.i(TAG, "✅ ActivityHolder 已注册到 Application");
    }
    @Override public void onActivityResumed(Activity activity) {
        sTop = activity; }
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {
        if (sTop == activity) sTop = null;
    }
}
