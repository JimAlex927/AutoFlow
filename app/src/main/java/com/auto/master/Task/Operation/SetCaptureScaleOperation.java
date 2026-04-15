package com.auto.master.Task.Operation;

/**
 * 设置采集倍率（CAPTURE_SCALE）节点。
 * <p>
 * inputMap 参数：
 *   CAPTURE_SCALE_VALUE  float  目标倍率（0.25~1.0），推荐值：0.5、0.625、0.75、0.875、1.0
 * <p>
 * 效果：
 *   - 持久化保存新倍率到 SharedPreferences
 *   - 清空全部模板缓存（旧倍率模板不再有效）
 *   - 重建 VirtualDisplay（采用 16 对齐尺寸，规避设备兼容问题）
 *   - 节点执行完成后等待约 1.5 秒使 VD 稳定
 * <p>
 * 注意：切换倍率后，模板匹配/图集匹配所需的模板文件必须是在新倍率下制作的，
 * 否则匹配会因找不到对应 scale 子目录下的模板而失败。
 */
public class SetCaptureScaleOperation extends MetaOperation {
    public SetCaptureScaleOperation() {}
}
