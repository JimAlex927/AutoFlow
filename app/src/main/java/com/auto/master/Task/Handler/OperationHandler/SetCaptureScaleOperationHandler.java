package com.auto.master.Task.Handler.OperationHandler;

import android.os.SystemClock;
import android.util.Log;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.capture.CaptureScaleHelper;
import com.auto.master.capture.ScreenCaptureManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 采集倍率节点（operationType=22）处理器。
 * <p>
 * 读取 CAPTURE_SCALE_VALUE 参数，调用 ScreenCaptureManager.setCaptureScale()
 * 并等待 VirtualDisplay 稳定后返回。
 */
public class SetCaptureScaleOperationHandler extends OperationHandler {

    private static final String TAG = "SetCaptureScaleOp";

    /**
     * VD 重建后的等待时间。
     * VD 重建是异步的，调用 setCaptureScale 后需稍等才能正常出帧。
     */
    private static final long STABILIZE_WAIT_MS = 1500L;

    SetCaptureScaleOperationHandler() {
        this.setType(22);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        ctx.currentOperation = obj;

        Map<String, Object> inputMap = obj.getInputMap();
        float requestedScale = parseFloat(
                inputMap == null ? null : inputMap.get(MetaOperation.CAPTURE_SCALE_VALUE),
                1.0f);

        // 夹紧到合理范围
        requestedScale = Math.max(0.25f, Math.min(1.0f, requestedScale));

        float prevScale = ScreenCaptureManager.CAPTURE_SCALE;

        if (Math.abs(requestedScale - prevScale) < 0.001f) {
            Log.d(TAG, "CAPTURE_SCALE 已是 " + requestedScale + "，无需切换");
            ctx.currentResponse = buildResponse(true, prevScale, requestedScale);
            ctx.lastOperation = obj;
            return true;
        }

        Log.i(TAG, "切换 CAPTURE_SCALE: " + prevScale + " → " + requestedScale
                + "  scale目录: " + CaptureScaleHelper.getScaleDirName(requestedScale));

        // 执行切换（内部：保存持久化 + 清 Template 缓存 + 异步重建 VD）
        ScreenCaptureManager.getInstance().setCaptureScale(requestedScale);

        // 清空 MatchMap 编译计划缓存
        MatchMaptemplateOperationHandler.clearMatchPlanCache();

        // 若采集正在运行，等待 VD 稳定
        if (ScreenCaptureManager.getInstance().isRunning()) {
            Log.d(TAG, "等待 VirtualDisplay 稳定 " + STABILIZE_WAIT_MS + "ms...");
            SystemClock.sleep(STABILIZE_WAIT_MS);
        }

        ctx.currentResponse = buildResponse(true, prevScale, requestedScale);
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    private Map<String, Object> buildResponse(boolean success, float prevScale, float newScale) {
        Map<String, Object> resp = new HashMap<>();
        resp.put(MetaOperation.MATCHED, success);
        resp.put("prevScale", prevScale);
        resp.put("newScale", newScale);
        resp.put("scaleDirName", CaptureScaleHelper.getScaleDirName(newScale));
        return resp;
    }

    private float parseFloat(Object raw, float def) {
        if (raw instanceof Number) return ((Number) raw).floatValue();
        if (raw instanceof String) {
            try { return Float.parseFloat(((String) raw).trim()); } catch (Exception ignored) {}
        }
        return def;
    }
}
