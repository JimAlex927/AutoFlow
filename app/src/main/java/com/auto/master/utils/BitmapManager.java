package com.auto.master.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

/**
 * Bitmap内存管理器
 * 使用LruCache自动管理Bitmap内存，防止OOM
 */
public class BitmapManager {
    
    private static final String TAG = "BitmapManager";
    private static final int MAX_CACHE_SIZE = 10 * 1024 * 1024; // 10MB缓存
    
    private static BitmapManager instance;
    private final LruCache<String, Bitmap> bitmapCache;
    
    private BitmapManager() {
        bitmapCache = new LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // 返回Bitmap占用的字节数
                return bitmap.getByteCount();
            }
            
            @Override
            protected void entryRemoved(boolean evicted, String key, 
                                       Bitmap oldValue, Bitmap newValue) {
                // 当Bitmap被移除时，自动回收
                if (oldValue != null && !oldValue.isRecycled()) {
                    oldValue.recycle();
                    Log.d(TAG, "Bitmap recycled: " + key);
                }
            }
        };
    }
    
    public static synchronized BitmapManager getInstance() {
        if (instance == null) {
            instance = new BitmapManager();
        }
        return instance;
    }
    
    /**
     * 加载Bitmap（带缓存和压缩）
     * @param path 文件路径
     * @return Bitmap对象，如果加载失败返回null
     */
    public Bitmap loadBitmap(String path) {
        return loadBitmap(path, 1);
    }
    
    /**
     * 加载Bitmap（带缓存和压缩）
     * @param path 文件路径
     * @param sampleSize 采样率（1=原始大小，2=1/2大小，4=1/4大小）
     * @return Bitmap对象，如果加载失败返回null
     */
    public Bitmap loadBitmap(String path, int sampleSize) {
        if (path == null) {
            return null;
        }
        
        // 生成缓存键（包含采样率）
        String cacheKey = path + "_" + sampleSize;
        
        // 先从缓存获取
        Bitmap cached = bitmapCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            Log.d(TAG, "Bitmap cache hit: " + path);
            return cached;
        }
        
        // 缓存未命中，从文件加载
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            // 使用RGB_565减少内存占用（ARGB_8888的一半）
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            if (bitmap != null) {
                bitmapCache.put(cacheKey, bitmap);
                Log.d(TAG, "Bitmap loaded and cached: " + path + 
                          ", size: " + bitmap.getByteCount() / 1024 + "KB");
            }
            return bitmap;
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM loading bitmap: " + path, e);
            // OOM时清理缓存重试
            clearCache();
            try {
                return BitmapFactory.decodeFile(path);
            } catch (OutOfMemoryError e2) {
                Log.e(TAG, "OOM again after cache clear", e2);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading bitmap: " + path, e);
            return null;
        }
    }
    
    /**
     * 加载缩略图（自动计算合适的采样率）
     * @param path 文件路径
     * @param reqWidth 目标宽度
     * @param reqHeight 目标高度
     * @return Bitmap对象
     */
    public Bitmap loadThumbnail(String path, int reqWidth, int reqHeight) {
        if (path == null) {
            return null;
        }
        
        // 先获取图片尺寸
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        
        // 计算采样率
        int sampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        
        return loadBitmap(path, sampleSize);
    }
    
    /**
     * 计算合适的采样率
     */
    private int calculateInSampleSize(BitmapFactory.Options options, 
                                      int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }
    
    /**
     * 从缓存中移除Bitmap
     */
    public void removeBitmap(String path) {
        bitmapCache.remove(path);
    }
    
    /**
     * 清空所有缓存
     */
    public void clearCache() {
        Log.d(TAG, "Clearing bitmap cache");
        bitmapCache.evictAll();
    }
    
    /**
     * 获取缓存使用情况
     */
    public String getCacheInfo() {
        int size = bitmapCache.size();
        int maxSize = bitmapCache.maxSize();
        return String.format("Bitmap cache: %dKB / %dKB (%.1f%%)", 
                           size / 1024, maxSize / 1024, 
                           (size * 100.0f / maxSize));
    }
}
