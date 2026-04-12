package com.auto.master.utils;

import static org.opencv.imgproc.Imgproc.TM_CCOEFF_NORMED;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;
import android.view.Surface;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OpenCVHelper {

    private static final String TAG = "OpenCVHelper";

    // 单例模式（线程安全懒加载）
    private static volatile OpenCVHelper instance;

    private OpenCVHelper() {
        // 私有构造，防止外部实例化
    }

    public static OpenCVHelper getInstance() {
        if (instance == null) {
            synchronized (OpenCVHelper.class) {
                if (instance == null) {
                    instance = new OpenCVHelper();
                }
            }
        }
        return instance;
    }

    private boolean kleidiCVAvailable = false;

    // 复用模板匹配的 result Mat：避免轮询热路径上每次 new/release native 内存
    private static final ThreadLocal<Mat> sResultMat = new ThreadLocal<>();

    // 复用 NMS 抑制 mask Mat，避免 while 循环内每次 new Mat.zeros()
    private static final ThreadLocal<Mat> sSuppressionMask = new ThreadLocal<>();

    // 复用灰度搜索图 Mat：fastSingleMatch 热路径灰度转换时避免每次分配
    private static final ThreadLocal<Mat> sGraySearchMat = new ThreadLocal<>();

    // 灰度模板缓存上限：超过 MAX_GRAY_CACHE 条时淘汰最旧的（LinkedHashMap accessOrder）
    private static final int MAX_GRAY_CACHE = 40;
    private final Map<Long, Mat> grayTemplateCache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<Long, Mat>(MAX_GRAY_CACHE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<Long, Mat> eldest) {
                    if (size() > MAX_GRAY_CACHE) {
                        Mat m = eldest.getValue();
                        try { if (m != null && !m.empty()) m.release(); } catch (Throwable ignored) {}
                        return true;
                    }
                    return false;
                }
            });

    /** 清除灰度模板缓存（模板文件更新后调用）*/
    public void clearGrayTemplateCache() {
        synchronized (grayTemplateCache) {
            for (Mat m : grayTemplateCache.values()) {
                try { if (m != null && !m.empty()) m.release(); } catch (Throwable ignored) {}
            }
            grayTemplateCache.clear();
        }
    }
    

    
    private boolean checkKleidiCVAvailability() {
        try {
            // 检查OpenCV版本是否支持KleidiCV
            String version = Core.VERSION;
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            
            // KleidiCV从OpenCV 4.11开始支持
            return (major > 4) || (major == 4 && minor >= 11);
        } catch (Exception e) {
            Log.w(TAG, "检查KleidiCV可用性失败: " + e.getMessage());
            return false;
        }
    }
    
    public boolean isKleidiCVAvailable() {
        return kleidiCVAvailable;
    }
    
    public Point fastSingleMatchKleidiCV(Mat screenMat, Mat templateMat, Rect region, double minScore, String method) {
        Mat searchMat = null;
        // 复用 ThreadLocal result Mat，避免热路径 new/release native 内存
        Mat result = sResultMat.get();
        if (result == null) { result = new Mat(); sResultMat.set(result); }

        try {
            Point roiOffset = new Point(0, 0);

            if (region != null && region.x >= 0 && region.y >= 0 && region.width > 0 && region.height > 0) {
                searchMat = new Mat(screenMat, region);
                roiOffset = new Point(region.x, region.y);
            } else {
                searchMat = screenMat;
            }

            int matchMethod = getMatchMethod(method);
            Imgproc.matchTemplate(searchMat, templateMat, result, matchMethod);

            Core.MinMaxLocResult mm = Core.minMaxLoc(result);
            if (mm.maxVal < minScore) {
                return new Point(-1, -1);
            }

            Point loc = mm.maxLoc;
            loc.x += roiOffset.x;
            loc.y += roiOffset.y;
            return loc;

        } catch (Exception e) {
            Log.e(TAG, "KleidiCV匹配失败: " + e.getMessage());
            return new Point(-1, -1);
        } finally {
            // result 来自 ThreadLocal，不 release，留给下次复用
            if (searchMat != null && searchMat != screenMat) {
                searchMat.release();
            }
        }
    }
    
    private int getMatchMethod(String method) {
        switch (method) {
            case "TM_CCORR":
                return Imgproc.TM_CCORR_NORMED;
            case "TM_CCOEFF":
                return Imgproc.TM_CCOEFF_NORMED;
            default:
                return Imgproc.TM_CCOEFF_NORMED;
        }
    }

    /**
     * 从 assets 加载模板图片
     * @param context 用于访问 assets
     * @param assetPath 如 "templates/icon.png"
     * @param grayscale 是否转为灰度图（推荐用于模板匹配，速度更快）
     * @return Mat 或 null（失败时）
     */
    public Mat loadTemplateFromAssets(Context context, String assetPath, boolean grayscale) {
        InputStream input = null;
        try {
            input = context.getAssets().open(assetPath);
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                return null;
            }
            Mat mat = new Mat();
            try {
                Utils.bitmapToMat(bitmap, mat);
            } finally {
                bitmap.recycle();
            }

            if (grayscale) {
                Mat gray = new Mat();
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
                mat.release();
                return gray;
            }
            return mat;
        } catch (IOException e) {
            Log.e(TAG, "加载模板失败: " + assetPath, e);
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static Bitmap rotateBitmap(Bitmap src, int rotation) {
        if (src == null) return null;

        Matrix matrix = new Matrix();
        switch (rotation) {
            case Surface.ROTATION_90:
                matrix.postRotate(-90);
                break;
            case Surface.ROTATION_180:
                matrix.postRotate(180);
                break;
            case Surface.ROTATION_270:
                matrix.postRotate(-270);
                break;
            default:
                return src;
        }

        Bitmap rotated = Bitmap.createBitmap(
                src, 0, 0,
                src.getWidth(), src.getHeight(),
                matrix, true
        );

//        if (rotated != src) {
//            src.recycle();
//        }

        return rotated;
    }

    public static Bitmap normalizeCapturedBitmapForDisplay(Bitmap src, int rotation, int targetW, int targetH) {
        if (src == null || src.isRecycled()) {
            return null;
        }
        if (targetW <= 0 || targetH <= 0) {
            return src;
        }

        Bitmap best = src;
        float bestScore = scoreBitmapForDisplay(src, targetW, targetH);

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_180 || rotation == Surface.ROTATION_270) {
            Bitmap rotated = rotateBitmap(src, rotation);
            if (rotated != null && rotated != src && !rotated.isRecycled()) {
                float rotatedScore = scoreBitmapForDisplay(rotated, targetW, targetH);
                if (rotatedScore > bestScore + 0.18f) {
                    best = rotated;
                } else {
                    rotated.recycle();
                }
            }
        }

        if (best == src) {
            return src;
        }

        Bitmap adjusted = centerCropToSize(best, targetW, targetH);
        if (adjusted != best && best != src && !best.isRecycled()) {
            best.recycle();
        }
        return adjusted;
    }

    public static boolean isCapturedBitmapUsableForDisplay(Bitmap bitmap, int targetW, int targetH) {
        if (bitmap == null || bitmap.isRecycled() || targetW <= 0 || targetH <= 0) {
            return false;
        }
        android.graphics.Rect bounds = detectActiveContentBounds(bitmap);
        if (bounds == null || bounds.isEmpty()) {
            return false;
        }

        float areaRatio = (bounds.width() * bounds.height()) / (float) (bitmap.getWidth() * bitmap.getHeight());
        float widthFill = bounds.width() / (float) bitmap.getWidth();
        float heightFill = bounds.height() / (float) bitmap.getHeight();
        boolean targetLandscape = targetW > targetH;
        boolean targetPortrait = targetH > targetW;
        boolean contentLandscape = bounds.width() > bounds.height() * 1.05f;
        boolean contentPortrait = bounds.height() > bounds.width() * 1.05f;

        if (areaRatio < 0.32f) {
            return false;
        }
        if (targetLandscape) {
            if (widthFill < 0.58f || heightFill < 0.40f) {
                return false;
            }
            if (!contentLandscape && areaRatio < 0.72f) {
                return false;
            }
        } else if (targetPortrait) {
            if (heightFill < 0.58f || widthFill < 0.40f) {
                return false;
            }
            if (!contentPortrait && areaRatio < 0.72f) {
                return false;
            }
        }
        return true;
    }

    private static float scoreBitmapForDisplay(Bitmap bitmap, int targetW, int targetH) {
        if (bitmap == null || bitmap.isRecycled()) {
            return -100f;
        }
        android.graphics.Rect bounds = detectActiveContentBounds(bitmap);
        if (bounds == null || bounds.isEmpty()) {
            return -100f;
        }

        float areaRatio = (bounds.width() * bounds.height()) / (float) (bitmap.getWidth() * bitmap.getHeight());
        boolean targetLandscape = targetW > targetH;
        boolean targetPortrait = targetH > targetW;
        boolean contentLandscape = bounds.width() > bounds.height() * 1.05f;
        boolean contentPortrait = bounds.height() > bounds.width() * 1.05f;

        float orientationScore = 0.6f;
        if ((targetLandscape && contentLandscape) || (targetPortrait && contentPortrait)) {
            orientationScore = 2.0f;
        } else if (Math.abs(bounds.width() - bounds.height()) <= Math.max(8, Math.min(bounds.width(), bounds.height()) / 12)) {
            orientationScore = 0.9f;
        }

        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float centerPenaltyX = Math.abs(cx - bitmap.getWidth() / 2f) / Math.max(1f, bitmap.getWidth() / 2f);
        float centerPenaltyY = Math.abs(cy - bitmap.getHeight() / 2f) / Math.max(1f, bitmap.getHeight() / 2f);
        float centeredScore = 1f - Math.min(1f, (centerPenaltyX + centerPenaltyY) / 2f);

        return orientationScore + areaRatio + centeredScore * 0.25f;
    }

    private static android.graphics.Rect detectActiveContentBounds(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 2 || height <= 2) {
            return new android.graphics.Rect(0, 0, width, height);
        }

        int referenceColor = estimatePaddingColor(bitmap);
        int step = Math.max(1, Math.min(width, height) / 180);
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int color = bitmap.getPixel(x, y);
                if (!isLikelyPaddingPixel(color, referenceColor)) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }

        if (right < left || bottom < top) {
            return new android.graphics.Rect(0, 0, width, height);
        }

        left = Math.max(0, left - step);
        top = Math.max(0, top - step);
        right = Math.min(width, right + step + 1);
        bottom = Math.min(height, bottom + step + 1);
        return new android.graphics.Rect(left, top, right, bottom);
    }

    private static int estimatePaddingColor(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int insetX = Math.max(0, Math.min(width - 1, width / 20));
        int insetY = Math.max(0, Math.min(height - 1, height / 20));
        int[] colors = new int[]{
                bitmap.getPixel(insetX, insetY),
                bitmap.getPixel(width - 1 - insetX, insetY),
                bitmap.getPixel(insetX, height - 1 - insetY),
                bitmap.getPixel(width - 1 - insetX, height - 1 - insetY)
        };

        int a = 0;
        int r = 0;
        int g = 0;
        int b = 0;
        for (int color : colors) {
            a += Color.alpha(color);
            r += Color.red(color);
            g += Color.green(color);
            b += Color.blue(color);
        }
        return Color.argb(a / colors.length, r / colors.length, g / colors.length, b / colors.length);
    }

    private static boolean isLikelyPaddingPixel(int color, int referenceColor) {
        int da = Math.abs(Color.alpha(color) - Color.alpha(referenceColor));
        int dr = Math.abs(Color.red(color) - Color.red(referenceColor));
        int dg = Math.abs(Color.green(color) - Color.green(referenceColor));
        int db = Math.abs(Color.blue(color) - Color.blue(referenceColor));
        int maxDiff = Math.max(Math.max(dr, dg), db);
        return da <= 32 && (dr + dg + db) <= 72 && maxDiff <= 28;
    }

    private static Bitmap centerCropToSize(Bitmap src, int dstW, int dstH) {
        if (src == null || src.isRecycled()) return null;
        if (dstW <= 0 || dstH <= 0) return src;

        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) return src;
        if (sw == dstW && sh == dstH) return src;

        float scale = Math.max(dstW / (float) sw, dstH / (float) sh);
        float scaledW = sw * scale;
        float scaledH = sh * scale;
        float dx = (dstW - scaledW) / 2f;
        float dy = (dstH - scaledH) / 2f;

        Bitmap out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, matrix, paint);
        return out;
    }

    /**
     * 快速单峰匹配，支持 mask（只匹配 mask=255 的区域，速度快，适合背景减法）
     * @param screenMat     源图
     * @param templateMat   模板
     * @param roi           可选搜索区域
     * @param minScore      最小相似度阈值
     * @param mask          可选 mask（255 表示参与匹配的像素，null 则全图匹配）
     * @return 最佳匹配位置，或 (-1,-1) 表示没找到
     */
    public Point fastSingleMatch(Mat screenMat, Mat templateMat, Rect roi, double minScore, Mat mask) {
        Mat searchMat = null;
        // 复用 ThreadLocal result Mat，避免热路径 new/release native 内存
        Mat result = sResultMat.get();
        if (result == null) { result = new Mat(); sResultMat.set(result); }
        Point roiOffset = new Point(0, 0);

        try {
            if (roi != null && roi.x >= 0 && roi.y >= 0 && roi.width > 0 && roi.height > 0) {
                searchMat = new Mat(screenMat, roi);
                roiOffset = new Point(roi.x, roi.y);
            } else {
                searchMat = screenMat;
            }

            Imgproc.matchTemplate(searchMat, templateMat, result, Imgproc.TM_CCOEFF_NORMED, mask);

            Core.MinMaxLocResult mm = Core.minMaxLoc(result);
            if (mm.maxVal < minScore) {
                return new Point(-1, -1);
            }

            Point loc = mm.maxLoc;
            loc.x += roiOffset.x;
            loc.y += roiOffset.y;
            return loc;

        } catch (Exception e) {
            Log.d(TAG, "fastSingleMatch: " + e.getMessage());
            return new Point(-1, -1);
        } finally {
            // result 来自 ThreadLocal，不 release，留给下次复用
            if (searchMat != null && searchMat != screenMat) {
                searchMat.release();
            }
        }
    }

    /**
     * 快速单峰匹配（模仿别人写法，极快，适合小模板找唯一目标）
     * @param screenMat     全屏或搜索区域
     * @param templateMat   模板
     * @param roi           可选搜索区域
     * @param threshold     最小相关值（TM_CCORR 非归一化，建议 0.5~0.8，根据模板大小调）
     * @return 最佳匹配位置，或 (-1,-1) 表示没找到
     */
    public Point fastSingleMatch(Mat screenMat, Mat templateMat, Rect roi, double threshold) {
        Mat searchMat = null;
        // 复用 ThreadLocal Mat，避免轮询热路径每次分配原生内存
        Mat result = sResultMat.get();
        if (result == null) { result = new Mat(); sResultMat.set(result); }
        Mat graySearch = sGraySearchMat.get();
        if (graySearch == null) { graySearch = new Mat(); sGraySearchMat.set(graySearch); }
        Point roiOffset = new Point(0, 0);

        Mat grayTemplate = null;

        try {
            if (screenMat == null || screenMat.empty() || templateMat == null || templateMat.empty()) {
                return new Point(-1, -1);
            }

            if (roi != null && roi.width > 0 && roi.height > 0) {
                int x = Math.max(0, roi.x);
                int y = Math.max(0, roi.y);
                int w = Math.min(roi.width, screenMat.cols() - x);
                int h = Math.min(roi.height, screenMat.rows() - y);

                if (w <= 0 || h <= 0) {
                    Log.e(TAG, "ROI越界: roi=" + roi
                            + ", screen=" + screenMat.cols() + "x" + screenMat.rows());
                    return new Point(-1, -1);
                }
                if (templateMat.cols() > w || templateMat.rows() > h) {
                    Log.e(TAG, "模板比ROI大: tpl=" + templateMat.cols() + "x" + templateMat.rows()
                            + ", roi=" + w + "x" + h);
                    return new Point(-1, -1);
                }
                searchMat = new Mat(screenMat, new Rect(x, y, w, h));
                roiOffset = new Point(x, y);
            } else {
                if (templateMat.cols() > screenMat.cols() || templateMat.rows() > screenMat.rows()) {
                    Log.e(TAG, "模板比screen大");
                    return new Point(-1, -1);
                }
                searchMat = screenMat;
            }

            // 灰度匹配：单通道 matchTemplate 约为 RGBA 4通道的 4 倍速
            // 屏幕帧和模板均为 CV_8UC4（RGBA），统一用 COLOR_RGBA2GRAY 转换
            int colorCode = (searchMat.channels() == 3)
                    ? Imgproc.COLOR_BGR2GRAY : Imgproc.COLOR_RGBA2GRAY;
            Imgproc.cvtColor(searchMat, graySearch, colorCode);

            // 从缓存取灰度模板，避免每帧重复转换（key = nativeObj 地址）
            long cacheKey = templateMat.nativeObj;
            Mat cached = grayTemplateCache.get(cacheKey);
            if (cached != null && !cached.empty()) {
                grayTemplate = cached;
            } else {
                grayTemplate = new Mat();
                int tplCode = (templateMat.channels() == 3)
                        ? Imgproc.COLOR_BGR2GRAY : Imgproc.COLOR_RGBA2GRAY;
                Imgproc.cvtColor(templateMat, grayTemplate, tplCode);
                grayTemplateCache.put(cacheKey, grayTemplate);
                // 灰度模板现由 grayTemplateCache 持有，不在此 release
            }

            Imgproc.matchTemplate(graySearch, grayTemplate, result, TM_CCOEFF_NORMED);

            Core.MinMaxLocResult minMax = Core.minMaxLoc(result);
            if (minMax.maxVal < threshold) {
                return new Point(-1, -1);
            }

            return new Point(minMax.maxLoc.x + roiOffset.x, minMax.maxLoc.y + roiOffset.y);

        } catch (Throwable t) {
            Log.e(TAG, "fastSingleMatch异常", t);
            return new Point(-1, -1);
        } finally {
            // result、graySearch 来自 ThreadLocal，不 release，留给下次复用
            // grayTemplate 由 grayTemplateCache 管理，不 release
            if (searchMat != null && searchMat != screenMat) searchMat.release();
        }
    }


    /**
     * 模板匹配，支持指定搜索区域（ROI）提速
     * @param screenMat 全屏截图 Mat
     * @param templateMat 模板 Mat
     * @param roi 可选：搜索的矩形区域（null 则全屏）。示例：new Rect(x, y, width, height)
     * @param method 匹配方法，默认 TM_SQDIFF_NORMED（效率最高）
     * @param threshold 阈值，例如 0.15（对于 SQDIFF_NORMED，越小越匹配）
     * @param scaleFactor 缩放因子（<1.0 提速，如 0.5），匹配后坐标会自动放大回原图
     * @return 匹配结果列表（位置已调整为原图坐标）
     */
    public List<MatchResult> matchTemplate(
            Mat screenMat,
            Mat templateMat,
            Rect roi,
            int method,
            double threshold,
            double scaleFactor,
            Boolean useGray
            ) {

//        if (!ensureInitialized()) {
//            return new ArrayList<>();
//        }

//        if (method == 0) method = Imgproc.TM_SQDIFF_NORMED;  // 默认最高效率方法
//        if (method == 0) method = Imgproc.TM_CCOEFF_NORMED;  // 默认最高效率方法

        // 处理 ROI
        Mat searchMat;
        Point roiOffset = new Point(0, 0);
        if (roi != null && roi.x >= 0 && roi.y >= 0 && roi.width > 0 && roi.height > 0) {
            roi.x = Math.max(0, roi.x);
            roi.y = Math.max(0, roi.y);
            roi.width = Math.min(roi.width, screenMat.cols() - roi.x);
            roi.height = Math.min(roi.height, screenMat.rows() - roi.y);

            searchMat = new Mat(screenMat, roi);
            roiOffset = new Point(roi.x, roi.y);
        } else {
            searchMat = screenMat;
        }

        // 缩放
        Mat scaledSearch = new Mat();
        Mat scaledTemplate = new Mat();
        boolean scaled = (scaleFactor < 1.0 && scaleFactor > 0.1);
        if (scaled) {
            Imgproc.resize(searchMat, scaledSearch, new Size(), scaleFactor, scaleFactor, Imgproc.INTER_LINEAR);
            Imgproc.resize(templateMat, scaledTemplate, new Size(), scaleFactor, scaleFactor, Imgproc.INTER_LINEAR);
        } else {
            scaledSearch = searchMat;
            scaledTemplate = templateMat;
        }

        Mat graySearch = new Mat();
        Mat grayTemplate = new Mat();
        boolean grayTemplateFromCache = false;
        List<MatchResult> matches = new ArrayList<>();
        // 复用 ThreadLocal result Mat，避免每次 new/release native 内存
        Mat result = sResultMat.get();
        if (result == null) { result = new Mat(); sResultMat.set(result); }
        try {
            // 在 try 块开始后，记录实际使用的模板尺寸
            int tplWidth;
            int tplHeight;

            if (useGray){
                Imgproc.cvtColor(scaledSearch, graySearch, Imgproc.COLOR_RGBA2GRAY);
                if (scaled) {
                    // 有缩放时 scaledTemplate 是临时对象，无法缓存，直接转换
                    Imgproc.cvtColor(scaledTemplate, grayTemplate, Imgproc.COLOR_RGBA2GRAY);
                } else {
                    // 无缩放：用 nativeObj 作 key 缓存灰度模板，避免每次重复 cvtColor
                    long cacheKey = templateMat.nativeObj;
                    Mat cached = grayTemplateCache.get(cacheKey);
                    if (cached != null && !cached.empty()) {
                        grayTemplate = cached;
                        grayTemplateFromCache = true;
                    } else {
                        Imgproc.cvtColor(templateMat, grayTemplate, Imgproc.COLOR_RGBA2GRAY);
                        grayTemplateCache.put(cacheKey, grayTemplate);
                        grayTemplateFromCache = true; // 放入缓存后由缓存持有，不 release
                    }
                }
                Imgproc.matchTemplate(graySearch, grayTemplate, result, method);
                tplWidth  = grayTemplate.cols();
                tplHeight = grayTemplate.rows();

            }else {
                // 这里是直接用彩色
                Imgproc.matchTemplate(scaledSearch, scaledTemplate, result, method);
                tplWidth  = scaledTemplate.cols();
                tplHeight = scaledTemplate.rows();
            }

            boolean isSqDiff = (method == Imgproc.TM_SQDIFF || method == Imgproc.TM_SQDIFF_NORMED);
            double suppressionValue = isSqDiff ? 1e10 : -1e10;  // SQDIFF 用大正数抑制，其他用负大数

            // 复用 NMS suppressionMask，避免每次 new Mat.zeros()（热路径）
            Mat suppressionMask = sSuppressionMask.get();
            if (suppressionMask == null) { suppressionMask = new Mat(); sSuppressionMask.set(suppressionMask); }

            while (true) {
                Core.MinMaxLocResult minMax = Core.minMaxLoc(result);

                // 根据方法判断阈值（SQDIFF 越小越好，其他越大越好）
                double matchValue = isSqDiff ? minMax.minVal : minMax.maxVal;
                boolean isMatch = isSqDiff ? (matchValue <= threshold) : (matchValue >= threshold);
                if (!isMatch) break;

                // 位置（SQDIFF 用 minLoc，其他用 maxLoc）
                Point loc = isSqDiff ? minMax.minLoc : minMax.maxLoc;
                double origX = (loc.x / scaleFactor) + roiOffset.x;
                double origY = (loc.y / scaleFactor) + roiOffset.y;
                Point correctedLoc = new Point(origX, origY);

                // 相似度统一转成 0~1（越接近1越好）
                double confidence = isSqDiff ? (1 - matchValue) : matchValue;
                matches.add(new MatchResult(correctedLoc, confidence));

                // NMS 抑制：复用 suppressionMask，create() 只在尺寸/类型变化时重新分配 native 内存
                suppressionMask.create(result.rows(), result.cols(), CvType.CV_8UC1);
                suppressionMask.setTo(new Scalar(0));
                int pad = Math.max(5, Math.min(tplWidth / 4, tplHeight / 4));   // 20px模板 → pad ≈ 5
                Imgproc.rectangle(
                        suppressionMask,
                        new Point(loc.x - pad, loc.y - pad),
                        new Point(loc.x + tplWidth + pad, loc.y + tplHeight + pad),
                        new Scalar(255),
                        -1
                );

                result.setTo(new Scalar(suppressionValue), suppressionMask);
                // suppressionMask 来自 ThreadLocal，不 release，留给下次复用

                if (matches.size() >= 10) break;
            }

            return matches;

        } finally {
            graySearch.release();
            if (!grayTemplateFromCache) grayTemplate.release(); // 来自缓存则不 release
            if (scaled) {
                scaledSearch.release();
                scaledTemplate.release();
            }
            if (searchMat != screenMat) searchMat.release();
            // result 来自 ThreadLocal，不 release，留给下次复用；
            // 此处曾错误地 release，导致每次调用均重新分配原生内存。
        }
    }

    /**
     * Bitmap 转 Mat（常用于截屏结果）
     */
    public Mat bitmapToMat(Bitmap bitmap) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bitmap, mat);
        return mat;
    }

    public Bitmap matToBitmap(Mat mat) {
        if (mat == null || mat.empty()) {
            return null;
        }
        Mat safeMat = mat;
        Mat clonedMat = null;
        try {
            // Some Android 12 emulator frames come from a padded submat (non-contiguous rows).
            // Clone once before conversion so Bitmap output does not mix in stale stride data.
            if (!mat.isContinuous() || mat.isSubmatrix()) {
                clonedMat = mat.clone();
                safeMat = clonedMat;
            }
            Bitmap bitmap = Bitmap.createBitmap(safeMat.width(), safeMat.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(safeMat, bitmap);
            return bitmap;
        } finally {
            if (clonedMat != null) {
                clonedMat.release();
            }
        }
    }

    /**
     * 在原图上绘制匹配结果（调试用）
     * @param screenMat 要绘制的 Mat
     * @param matches 匹配结果
     * @param templateSize 模板尺寸
     */
    public void drawMatches(Mat screenMat, List<MatchResult> matches, Size templateSize) {
        for (MatchResult match : matches) {
            Point topLeft = match.getLocation();
            Point bottomRight = new Point(
                    topLeft.x + templateSize.width,
                    topLeft.y + templateSize.height
            );
            Imgproc.rectangle(screenMat, topLeft, bottomRight, new Scalar(0, 255, 0), 2);
        }
    }


    // ============ 手势假成功校验：相位相关位移 ============

    public static class ShiftResult1 {
        public final double dx;       // after 相对 before 的平移（缩小图像坐标系）
        public final double dy;
        public final double response; // 相关性质量，越大越可靠（经验：>0.15~0.25较靠谱）

        public ShiftResult1(double dx, double dy, double response) {
            this.dx = dx;
            this.dy = dy;
            this.response = response;
        }
    }
    /**
     * 高效计算两帧整体平移（用于判断滑动是否真的发生）
     * 核心：缩小 -> 灰度 -> CV_32F -> phaseCorrelate
     *
     * @param beforeMat 最新帧（手势前）
     * @param afterMat  最新帧（手势后）
     * @param targetW   缩放后宽度（建议 240~480，越小越快）
     * @return ShiftResult
     */
    public ShiftResult1 calcGlobalShiftPhase(Mat beforeMat, Mat afterMat, int targetW) {
        if (beforeMat == null || afterMat == null || beforeMat.empty() || afterMat.empty()) {
            return new ShiftResult1(0, 0, 0);
        }

        Mat b = null, a = null;
        Mat bGray = null, aGray = null;
        Mat b32 = null, a32 = null;
        Mat hann = null;

        try {
            // 1) 缩小（只处理缩小后的临时 Mat，避免对原图做重活）
            b = resizeToWidth(beforeMat, targetW);
            a = resizeToWidth(afterMat, targetW);

            // 2) 灰度（你的 Mat 很可能是 RGBA 或 BGR，这里做兼容）
            bGray = toGrayFast(b);
            aGray = toGrayFast(a);

            // 3) 转 float
            b32 = new Mat();
            a32 = new Mat();
            bGray.convertTo(b32, CvType.CV_32F);
            aGray.convertTo(a32, CvType.CV_32F);

            // 可选：轻微模糊去噪（代价很小，提高稳定性）
            Imgproc.GaussianBlur(b32, b32, new Size(3, 3), 0);
            Imgproc.GaussianBlur(a32, a32, new Size(3, 3), 0);

            // 4) 汉宁窗（提高相位相关稳定性）
            hann = new Mat();
            Imgproc.createHanningWindow(hann, b32.size(), CvType.CV_32F);

            double[] resp = new double[1];
            Point shift = Imgproc.phaseCorrelate(b32, a32, hann, resp);

            return new ShiftResult1(shift.x, shift.y, resp[0]);

        } catch (Throwable t) {
            Log.w(TAG, "calcGlobalShiftPhase error", t);
            return new ShiftResult1(0, 0, 0);
        } finally {
            safeRelease(hann);
            safeRelease(b32); safeRelease(a32);
            safeRelease(bGray); safeRelease(aGray);
            safeRelease(b); safeRelease(a);
        }
    }
    /**
     * 判断手势是否“逻辑成功”（画面发生了与手势相关的整体位移）
     *
     * @param gestureDx     手势向量dx（屏幕坐标，终点-起点）
     * @param gestureDy     手势向量dy
     * @param shift         相位相关返回的位移（缩小图坐标系）
     * @param sameDirection true=要求画面位移与手势同向；false=要求反向；null=不强制方向，只要有位移
     * @param minPixels     最小位移阈值（缩小图像素，建议 5~10）
     * @param minResponse   最小相关性质量阈值（建议 0.15~0.25）
     */
    public boolean verifyShiftForGesture(
            float gestureDx, float gestureDy,
            ShiftResult1 shift,
            Boolean sameDirection,
            double minPixels,
            double minResponse
    ) {
        if (shift == null) return false;
        if (shift.response < minResponse) return false;

        double mag = Math.hypot(shift.dx, shift.dy);
        if (mag < minPixels) return false; // 没明显位移

        // 手势太短（点击/短抖动）不适合位移判定，直接放过
        double gmag = Math.hypot(gestureDx, gestureDy);
        if (gmag < 30) return true;

        // 主方向一致性：先看手势是横向还是纵向
        boolean horizontal = Math.abs(gestureDx) >= Math.abs(gestureDy);

        // 方向不强制：只要有位移 + response 合格
        if (sameDirection == null) return true;

        // 点积判断同向/反向（在主方向上更稳）
        double dot;
        if (horizontal) {
            dot = gestureDx * shift.dx;
        } else {
            dot = gestureDy * shift.dy;
        }

        return sameDirection ? (dot > 0) : (dot < 0);
    }

// ----------- 小工具：缩放/转灰/释放 -----------

    private Mat resizeToWidth(Mat src, int targetW) {
        if (src.cols() <= targetW) {
            // 返回一个浅拷贝引用也行，但为了生命周期安全，这里返回 clone 的小图更稳
            return src.clone();
        }
        double scale = targetW / (double) src.cols();
        int targetH = Math.max(1, (int) Math.round(src.rows() * scale));
        Mat dst = new Mat();
        Imgproc.resize(src, dst, new Size(targetW, targetH), 0, 0, Imgproc.INTER_LINEAR);
        return dst;
    }

    private Mat toGrayFast(Mat src) {
        Mat gray = new Mat();
        int ch = src.channels();
        if (ch == 1) {
            src.copyTo(gray);
        } else if (ch == 3) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            // 常见是 RGBA
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);
        }
        return gray;
    }

    private void safeRelease(Mat m) {
        if (m != null) {
            try { m.release(); } catch (Throwable ignored) {}
        }
    }

    // ======================= ROI-based Change / Shift Utils =======================

    public static class ChangeResult {
        /** 变化像素占比 0~1 */
        public final double changeRatio;
        /** 灰度差的平均强度 0~255 */
        public final double meanDiff;
        /** ROI 实际使用区域（原图坐标） */
        public final Rect roi;

        public ChangeResult(double changeRatio, double meanDiff, Rect roi) {
            this.changeRatio = changeRatio;
            this.meanDiff = meanDiff;
            this.roi = roi;
        }
    }

    public static class ShiftResult {
        /** after 相对 before 的整体平移（在缩小后的 ROI 坐标系） */
        public final double dx;
        public final double dy;
        /** 相关性质量，越大越可靠 */
        public final double response;
        /** ROI 实际使用区域（原图坐标） */
        public final Rect roi;

        public ShiftResult(double dx, double dy, double response, Rect roi) {
            this.dx = dx;
            this.dy = dy;
            this.response = response;
            this.roi = roi;
        }
    }

    /**
     * 计算中心 ROI：例如 roiScale=0.6 表示取中间 60% 宽高
     */
    public Rect computeCenterRoi(Mat src, double roiScale) {
        if (src == null || src.empty()) return new Rect(0, 0, 0, 0);
        roiScale = clamp(roiScale, 0.2, 1.0);

        int w = src.cols();
        int h = src.rows();
        int rw = (int) Math.round(w * roiScale);
        int rh = (int) Math.round(h * roiScale);
        int rx = (w - rw) / 2;
        int ry = (h - rh) / 2;

        // 防越界
        rx = Math.max(0, rx);
        ry = Math.max(0, ry);
        rw = Math.min(rw, w - rx);
        rh = Math.min(rh, h - ry);

        return new Rect(rx, ry, rw, rh);
    }

    /**
     * ROI 变化检测（推荐用于：滑动后翻页/页面切换/弹窗/刷新等“整体变化”）
     *
     * @param beforeMat 手势前帧
     * @param afterMat  手势后帧
     * @param targetW   ROI 缩放后的宽度（建议 240~480，越小越快）
     * @param diffTh    灰度差阈值（建议 12~25）
     * @param roiScale  取中心 ROI 的比例（建议 0.55~0.75，避开边缘 overlay）
     */
    public ChangeResult calcChangeScoreCenterRoi(
            Mat beforeMat,
            Mat afterMat,
            int targetW,
            int diffTh,
            double roiScale
    ) {
        if (beforeMat == null || afterMat == null || beforeMat.empty() || afterMat.empty()) {
            return new ChangeResult(1.0, 255.0, new Rect(0,0,0,0));
        }

        Mat bRoi = null, aRoi = null;
        Mat bSmall = null, aSmall = null;
        Mat bGray = null, aGray = null;
        Mat diff = null, bin = null;

        Rect roi = null;

        try {
            // 1) 取中心 ROI（用 before 的尺寸）
            roi = computeCenterRoi(beforeMat, roiScale);
            if (roi.width <= 0 || roi.height <= 0) {
                return new ChangeResult(1.0, 255.0, roi);
            }

            // 2) 裁 ROI（注意：new Mat(src, roi) 是 view，不会复制数据）
            bRoi = new Mat(beforeMat, roi);
            aRoi = new Mat(afterMat, roi);

            // 3) 缩放（先裁再缩，省很多）
            bSmall = resizeToWidth(bRoi, targetW);
            aSmall = resizeToWidth(aRoi, targetW);

            // 4) 灰度
            bGray = toGrayFast(bSmall);
            aGray = toGrayFast(aSmall);

            // 5) absdiff
            diff = new Mat();
            Core.absdiff(bGray, aGray, diff);

            // mean diff
            Scalar m = Core.mean(diff);
            double meanDiff = m.val[0];

            // threshold + countNonZero
            bin = new Mat();
            Imgproc.threshold(diff, bin, diffTh, 255, Imgproc.THRESH_BINARY);

            double changed = Core.countNonZero(bin);
            double total = (double) bin.rows() * (double) bin.cols();
            double ratio = total > 0 ? (changed / total) : 0.0;

            return new ChangeResult(ratio, meanDiff, roi);

        } catch (Throwable t) {
            Log.w(TAG, "calcChangeScoreCenterRoi error", t);
            return new ChangeResult(0.0, 0.0, roi == null ? new Rect(0,0,0,0) : roi);
        } finally {
            safeRelease(bin);
            safeRelease(diff);
            safeRelease(bGray); safeRelease(aGray);
            safeRelease(bSmall); safeRelease(aSmall);
            safeRelease(bRoi); safeRelease(aRoi);
        }
    }

    /**
     * ROI 位移检测（相位相关）：
     * 用于判断 “确实发生了整体位移/滑动”
     *
     * ⚠️ 如果你 OpenCV 没有 phaseCorrelate，就不要用这个方法，改用变化检测即可。
     *
     * @param beforeMat 手势前帧
     * @param afterMat  手势后帧
     * @param targetW   ROI 缩放后的宽度（建议 240~480）
     * @param roiScale  中心 ROI 比例（建议 0.55~0.75）
     */
    public ShiftResult calcGlobalShiftPhaseCenterRoi(
            Mat beforeMat,
            Mat afterMat,
            int targetW,
            double roiScale
    ) {
        if (beforeMat == null || afterMat == null || beforeMat.empty() || afterMat.empty()) {
            return new ShiftResult(0, 0, 0, new Rect(0,0,0,0));
        }

        Mat bRoi = null, aRoi = null;
        Mat bSmall = null, aSmall = null;
        Mat bGray = null, aGray = null;
        Mat b32 = null, a32 = null;
        Mat hann = null;

        Rect roi = null;

        try {
            roi = computeCenterRoi(beforeMat, roiScale);
            if (roi.width <= 0 || roi.height <= 0) {
                return new ShiftResult(0, 0, 0, roi);
            }

            bRoi = new Mat(beforeMat, roi);
            aRoi = new Mat(afterMat, roi);

            bSmall = resizeToWidth(bRoi, targetW);
            aSmall = resizeToWidth(aRoi, targetW);

            bGray = toGrayFast(bSmall);
            aGray = toGrayFast(aSmall);

            b32 = new Mat();
            a32 = new Mat();
            bGray.convertTo(b32, CvType.CV_32F);
            aGray.convertTo(a32, CvType.CV_32F);

            // 去噪（可选但建议）
            Imgproc.GaussianBlur(b32, b32, new Size(3,3), 0);
            Imgproc.GaussianBlur(a32, a32, new Size(3,3), 0);

            hann = new Mat();
            Imgproc.createHanningWindow(hann, b32.size(), CvType.CV_32F);

            double[] resp = new double[1];

            // 注意：有的 OpenCV 把 phaseCorrelate 放在 Imgproc，有的放在 Core
            // 你先试 Imgproc.phaseCorrelate；不行再改 Core.phaseCorrelate
            Point shift = Imgproc.phaseCorrelate(b32, a32, hann, resp);

            return new ShiftResult(shift.x, shift.y, resp[0], roi);

        } catch (Throwable t) {
            Log.w(TAG, "calcGlobalShiftPhaseCenterRoi error", t);
            return new ShiftResult(0, 0, 0, roi == null ? new Rect(0,0,0,0) : roi);
        } finally {
            safeRelease(hann);
            safeRelease(b32); safeRelease(a32);
            safeRelease(bGray); safeRelease(aGray);
            safeRelease(bSmall); safeRelease(aSmall);
            safeRelease(bRoi); safeRelease(aRoi);
        }
    }
    // ======================= FAST ROI Change (Sampled) =======================

    /**
     * 极速版变化检测：只在中心 ROI 上做“采样点”灰度差统计
     * 不做 absdiff 全矩阵、不做 threshold、不做 countNonZero
     *
     * 适用于：滑动/翻页后的快速真假成功校验
     *
     * @param beforeMat  手势前帧
     * @param afterMat   手势后帧
     * @param targetW    ROI 缩放后的宽度（建议 240~360；越小越快）
     * @param roiScale   中心 ROI 比例（建议 0.60~0.75）
     * @param step       采样步长（建议 6~14；越大越快但越粗）
     *
     * @return ChangeResult(changeRatio, meanDiff, roi)
     *         其中 changeRatio 是“采样点里超过 diffTh 的比例”（不是全像素）
     */
    public ChangeResult calcChangeScoreCenterRoiSampled(
            Mat beforeMat,
            Mat afterMat,
            int targetW,
            double roiScale,
            int step,
            int diffTh
    ) {
        if (beforeMat == null || afterMat == null || beforeMat.empty() || afterMat.empty()) {
            return new ChangeResult(1.0, 255.0, new Rect(0,0,0,0));
        }

        Mat bRoi = null, aRoi = null;
        Mat bSmall = null, aSmall = null;
        Mat bGray = null, aGray = null;
        Rect roi = null;

        try {
            roi = computeCenterRoi(beforeMat, roiScale);
            if (roi.width <= 0 || roi.height <= 0) {
                return new ChangeResult(1.0, 255.0, roi);
            }

            bRoi = new Mat(beforeMat, roi);
            aRoi = new Mat(afterMat, roi);

            // 先裁再缩：省很多
            bSmall = resizeToWidth(bRoi, targetW);
            aSmall = resizeToWidth(aRoi, targetW);

            // 灰度
            bGray = toGrayFast(bSmall);
            aGray = toGrayFast(aSmall);

            // 保证连续内存（避免 get 慢）
            Mat bCont = bGray.isContinuous() ? bGray : bGray.clone();
            Mat aCont = aGray.isContinuous() ? aGray : aGray.clone();

            int rows = bCont.rows();
            int cols = bCont.cols();
            if (rows <= 0 || cols <= 0) {
                safeRelease(bCont);
                safeRelease(aCont);
                return new ChangeResult(0.0, 0.0, roi);
            }

            // step 保护
            step = Math.max(1, step);

            // 采样统计
            long sumAbs = 0;
            long cnt = 0;
            long changed = 0;

            byte[] bRow = new byte[cols];
            byte[] aRow = new byte[cols];

            for (int y = 0; y < rows; y += step) {
                bCont.get(y, 0, bRow);
                aCont.get(y, 0, aRow);

                for (int x = 0; x < cols; x += step) {
                    int bv = bRow[x] & 0xFF;
                    int av = aRow[x] & 0xFF;
                    int d = Math.abs(bv - av);

                    sumAbs += d;
                    cnt++;

                    if (d >= diffTh) changed++;
                }
            }

            safeRelease(bCont);
            safeRelease(aCont);

            double meanDiff = cnt > 0 ? (sumAbs * 1.0 / cnt) : 0.0;
            double ratio = cnt > 0 ? (changed * 1.0 / cnt) : 0.0;

            return new ChangeResult(ratio, meanDiff, roi);

        } catch (Throwable t) {
            Log.w(TAG, "calcChangeScoreCenterRoiSampled error", t);
            return new ChangeResult(0.0, 0.0, roi == null ? new Rect(0,0,0,0) : roi);
        } finally {
            safeRelease(bGray); safeRelease(aGray);
            safeRelease(bSmall); safeRelease(aSmall);
            safeRelease(bRoi); safeRelease(aRoi);
        }
    }

    /**
     * 滑动优先的“真假成功”判定（快 + 稳）：
     * 1) 优先用 phaseCorrelate 判定位移（适合滑动）
     * 2) 位移不可靠时，用采样变化检测兜底（很快）
     *
     * @param beforeMat 手势前帧
     * @param afterMat  手势后帧
     * @param gestureDx 手势向量（终点-起点）
     * @param gestureDy 手势向量
     */
    public boolean verifySwipeFast(
            Mat beforeMat,
            Mat afterMat,
            float gestureDx,
            float gestureDy
    ) {
        // ------- 参数你可以按设备调 -------
        final double roiScale = 0.68;      // 中心 ROI 避开边缘 overlay
        final int shiftW = 300;            // phaseCorrelate 用的缩放宽度（越小越快）
        final int changeW = 260;           // sampled-change 用的缩放宽度
        final double minShiftResp = 0.18;  // phaseCorrelate 质量阈值
        final double minShiftPx = 6.0;     // 缩小后像素位移阈值
        final int sampleStep = 10;         // 采样步长（越大越快）
        final int diffTh = 18;             // 单点灰度差阈值
        final double minChangeRatio = 0.10; // 采样点变化比例阈值（滑动一般较明显）
        final double minMeanDiff = 6.0;    // 平均差阈值

        // 1) 位移判定（滑动优先）
        ShiftResult shift = calcGlobalShiftPhaseCenterRoi(beforeMat, afterMat, shiftW, roiScale);
        boolean moved = false;
        if (shift != null) {
            double mag = Math.hypot(shift.dx, shift.dy);
            if (shift.response >= minShiftResp && mag >= minShiftPx) {
                // 方向一致性：滑动时一般画面位移与手势“同向”或“反向”取决于内容滚动/手指方向
                // 实际上：手指向上滑 => 内容向下移（画面特征往下）=> shift.dy 可能与 gestureDy 反向
                // 所以这里不强制同向，只要有可靠位移即可
                moved = true;
            }
        }
        if (moved) return true;

        // 2) 兜底：极速采样变化
        ChangeResult ch = calcChangeScoreCenterRoiSampled(beforeMat, afterMat, changeW, roiScale, sampleStep, diffTh);
        if (ch == null) return false;

        boolean changed = (ch.changeRatio >= minChangeRatio) || (ch.meanDiff >= minMeanDiff);
        return changed;
    }


    /**
     * 最终“逻辑成功”判定：只要【位移成立】或【变化成立】即可。
     * 你说的“滑动后页面变化也算成功”，就用这个组合判定。
     */
    public boolean isGestureLogicallyOkByShiftOrChange(
            ShiftResult shift,
            ChangeResult change,
            // 位移阈值（缩小后像素）
            double minShiftPixels,
            // shift 质量阈值
            double minShiftResp,
            // 变化阈值：变化占比
            double minChangeRatio,
            // 变化阈值：平均差
            double minMeanDiff
    ) {
        boolean moved = false;
        if (shift != null) {
            double mag = Math.hypot(shift.dx, shift.dy);
            moved = (shift.response >= minShiftResp) && (mag >= minShiftPixels);
        }

        boolean changed = false;
        if (change != null) {
            changed = (change.changeRatio >= minChangeRatio) || (change.meanDiff >= minMeanDiff);
        }

        return moved || changed;
    }

// -------------------- private helpers --------------------

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }



}
