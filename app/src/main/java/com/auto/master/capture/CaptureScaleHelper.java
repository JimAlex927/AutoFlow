package com.auto.master.capture;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * 采集倍率工具类：
 * - 管理 CAPTURE_SCALE 的持久化
 * - 提供模板目录的 scale-aware 解析
 * - 管理 VirtualDisplay 尺寸对齐
 */
public final class CaptureScaleHelper {

    private static final String PREFS_NAME = "AutoFlowCaptureScale";
    private static final String PREF_KEY   = "capture_scale";

    /** 支持的倍率列表（供 UI 选择）*/
    public static final float[] SUPPORTED_SCALES = { 0.5f, 0.625f, 0.75f, 0.875f, 1.0f };

    // ─────────────────────────────────────────────────────────────────────────
    //  倍率 ↔ 目录名
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 将 float 倍率转为整数 key（1.0→100, 0.5→50, 0.75→75）。
     */
    public static int getScaleKey(float scale) {
        return Math.round(scale * 100);
    }

    /**
     * 倍率对应的子目录名，例如 scale_100 / scale_50。
     */
    public static String getScaleDirName(float scale) {
        return "scale_" + getScaleKey(scale);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VirtualDisplay 尺寸对齐
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 对齐 VirtualDisplay 边长：
     * - scale = 1.0：对齐到 2 的倍数（保持当前行为）
     * - scale < 1.0：向下对齐到 16 的倍数（规避部分设备 MediaProjection 对齐要求）
     *
     * 示例（1080×2340，scale=0.5）:
     *   rawW=540 → (540/16)*16 = 528
     *   rawH=1170 → (1170/16)*16 = 1168
     * 两者均为 16 的倍数，不会触发 size mismatch。
     */
    public static int alignCaptureDimension(int rawSize, float scale) {
        if (scale >= 1.0f - 0.001f) {
            return rawSize & ~1;      // 对齐到偶数（原有行为）
        }
        return (rawSize / 16) * 16;  // 向下对齐到 16 的倍数
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  持久化
    // ─────────────────────────────────────────────────────────────────────────

    public static void saveScale(Context context, float scale) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
               .edit()
               .putFloat(PREF_KEY, scale)
               .apply();
    }

    public static float loadScale(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                      .getFloat(PREF_KEY, 1.0f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  模板文件解析（scale-aware）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 解析模板文件路径（scale-aware）：
     *  1. 优先从 imgDir/scale_{key}/{templateName} 查找
     *  2. 仅当 scale=1.0 时，回退到 imgDir/{templateName}（向后兼容旧模板）
     *
     * @param imgDir       projects/{P}/{T}/img 目录
     * @param templateName 模板文件名（含扩展名）
     * @param scale        当前 CAPTURE_SCALE
     * @return             存在的 File，或 null（不存在或倍率不匹配）
     */
    public static File resolveTemplateFile(File imgDir, String templateName, float scale) {
        if (imgDir == null || templateName == null || templateName.isEmpty()) {
            return null;
        }
        // 优先：scale 子目录
        File scaleDir = new File(imgDir, getScaleDirName(scale));
        File scaleFile = new File(scaleDir, templateName);
        if (scaleFile.exists()) {
            return scaleFile;
        }
        // 向后兼容：仅 scale=1.0 时回退到 imgDir 平级目录
        if (Math.abs(scale - 1.0f) < 0.001f) {
            File flatFile = new File(imgDir, templateName);
            if (flatFile.exists()) {
                return flatFile;
            }
        }
        return null;
    }

    /**
     * 获取模板保存目录（scale-aware）：
     * 返回 imgDir/scale_{key}/，若不存在则自动创建。
     */
    public static File getOrCreateScaleImgDir(File imgDir, float scale) {
        File scaleDir = new File(imgDir, getScaleDirName(scale));
        if (!scaleDir.exists()) {
            scaleDir.mkdirs();
        }
        return scaleDir;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  倍率格式化（UI 展示）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 格式化倍率为人类可读字符串，例如 "1.0x" / "0.5x"。
     */
    public static String formatScale(float scale) {
        int key = getScaleKey(scale);
        if (key % 100 == 0) {
            return (key / 100) + ".0x";
        }
        return (key / 100.0f) + "x";
    }

    private CaptureScaleHelper() {}
}
