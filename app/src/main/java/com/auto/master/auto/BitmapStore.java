package com.auto.master.auto;

import android.graphics.Bitmap;

// BitmapStore.java
public final class BitmapStore {
    private static Bitmap bitmap;
    private BitmapStore() {}

    public static synchronized void put(Bitmap b) {
        // 不要在这里 recycle 旧的！否则可能把正在显示的那张回收掉
        bitmap = b;
    }

    public static synchronized Bitmap get() {
        return bitmap;
    }

    public static synchronized Bitmap take() {
        // 取走所有权：Activity 拿到后自己负责 recycle
        Bitmap b = bitmap;
        bitmap = null;
        return b;
    }

    public static synchronized void clearAndRecycle() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
    }
}
