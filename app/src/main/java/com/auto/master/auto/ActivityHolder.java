package com.auto.master.auto;

import static android.content.ContentValues.TAG;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;

public final class ActivityHolder implements Application.ActivityLifecycleCallbacks {

    // 弱引用：Activity 销毁后可被 GC，不阻塞内存回收
    private static volatile WeakReference<Activity> sTopRef = new WeakReference<>(null);

    /** 返回当前前台 Activity，已销毁或未初始化时返回 null */
    public static Activity getTopActivity() {
        WeakReference<Activity> ref = sTopRef;
        return ref != null ? ref.get() : null;
    }

    public static void register(Application app) {
        app.registerActivityLifecycleCallbacks(new ActivityHolder());
        Log.i(TAG, "ActivityHolder 已注册到 Application");
    }

    @Override public void onActivityResumed(Activity activity)  { sTopRef = new WeakReference<>(activity); }
    @Override public void onActivityStopped(Activity activity)  {
        // 注意：不在此处清除引用。
        // 本 app 以 FloatWindowService 悬浮窗为主 UI，主 Activity 长期处于 stopped（后台）但未销毁。
        // 在 stopped 时清除会导致悬浮窗内的取色/框选等功能拿不到 Activity，改为只在 destroyed 时清。
    }
    @Override public void onActivityDestroyed(Activity activity) {
        WeakReference<Activity> ref = sTopRef;
        if (ref != null && ref.get() == activity) sTopRef = new WeakReference<>(null);
    }
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
}
