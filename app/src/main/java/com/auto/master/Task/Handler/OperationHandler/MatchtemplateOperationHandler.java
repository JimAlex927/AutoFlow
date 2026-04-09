package com.auto.master.Task.Handler.OperationHandler;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.auto.master.MainActivity;
import com.auto.master.Task.Operation.DelayOperation;
import com.auto.master.Task.Operation.LoadImgToMatOperation;
import com.auto.master.Task.Operation.MatchTemplateOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Template.Template;
import com.auto.master.auto.ActivityHolder;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.capture.ScreenCapture;
import com.auto.master.utils.AdaptivePollingController;
import com.auto.master.utils.MatchResult;
import com.auto.master.utils.OpenCVHelper;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 这是一个 click 的处理器
 * 它的作用就只是调用无障碍然后点击那个点
 */
public class MatchtemplateOperationHandler extends OperationHandler {

    public static Integer inited = 0;

    MatchtemplateOperationHandler() {
        this.setType(6);
    }


    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        ctx.currentOperation = obj;
        MatchTemplateOperation matchTemplateOperation = (MatchTemplateOperation) obj;

        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) return false;


//      todo 这里 project、task也可能从 ctx拿  我考虑 如果从ctx拿 最好也预先往 inputmap里面写入 这样更兼容
        Map<String, Object> inputMap = matchTemplateOperation.getInputMap();
        String projectName = getStringSafe(inputMap, MetaOperation.PROJECT, "fallback");
        String taskName = getStringSafe(inputMap, MetaOperation.TASK, "");
        String saveFileName = getStringSafe(inputMap, MetaOperation.SAVEFILENAME, "gesture_" + System.currentTimeMillis() + ".json");
//        String saveFileName = getStringSafe(inputMap, MetaOperation.MATCHSIMILARITY, );
        Object o = inputMap.get(MetaOperation.MATCHSIMILARITY);
        Double similarity = parseDouble(o, 0.8d);
        Object o1 = inputMap.get(MetaOperation.MATCHTIMEOUT);
        Double duration = parseDouble(o1, 5000d);
        long preDelayMs = parseDelayMs(inputMap.get(MetaOperation.MATCH_PRE_DELAY_MS));

        Double scaleFactor = 1d;
        Object o2 = inputMap.get(MetaOperation.MATCHSCALEFACTOR);
        scaleFactor = parseDouble(o2, 1d);
        Double matchMethod = 1d;
        Object o3 = inputMap.get(MetaOperation.MATCHMETHOD);
        matchMethod = parseDouble(o3, 1d);
//        匹配方法
        int method = matchMethod.intValue();

        Boolean useGray = false;
        Object o4 = inputMap.get(MetaOperation.MATCHUSEGRAY);
        if (o4 instanceof Boolean) {
            useGray = (Boolean) o4;
        }

        //调用结果  产生一个 response  放在 obj 或者 ctx里面
        Integer responseType = obj.getResponseType();
        // response =1 进行匹配 返回结果 是列表吧 而且是 region匹配
//        if (responseType==null){
        final Activity a = ActivityHolder.getTopActivity();

        /*
        加载模板
         */
        OpenCVHelper instance = OpenCVHelper.getInstance();
        List<Integer> bbox = Template.getManifestSingleCache(projectName, taskName, saveFileName);
        if (bbox == null) {
//            重新加载下项目的资源文件到内存 这里用内置的 加载资源的operation做
            LoadImgToMatOperation loadImgToMatOperation = new LoadImgToMatOperation();
            loadImgToMatOperation.setId("tmp");
            loadImgToMatOperation.setResponseType(1);
            HashMap<String, Object> tmpInputMap = new HashMap<>();
            tmpInputMap.put(MetaOperation.PROJECT, projectName);
            loadImgToMatOperation.setInputMap(tmpInputMap);
            new LoadImgToMatOperationHandler().handle(loadImgToMatOperation, new OperationContext());
        }
        bbox = Template.getManifestSingleCache(projectName, taskName, saveFileName);
        Mat templateMat = Template.getTaskSingleMutCache(projectName, taskName, saveFileName);
        android.graphics.Rect captureRoi = null;
        if (bbox != null && bbox.size() >= 4) {
            captureRoi = new android.graphics.Rect(
                    bbox.get(0),
                    bbox.get(1),
                    bbox.get(0) + bbox.get(2),
                    bbox.get(1) + bbox.get(3));
        }

        boolean matched = false;
        List<MatchResult> matchResults = new ArrayList<>();
        AdaptivePollingController pollingController = AdaptivePollingController.forTemplateMatch();
        if (preDelayMs > 0) {
            SystemClock.sleep(preDelayMs);
        }
        // 这里的超时只统计“匹配过程”，不包含节点前延迟
        long start = System.currentTimeMillis();
        while (duration > System.currentTimeMillis() - start) {
            long loopStartMs = SystemClock.uptimeMillis();

            Mat screenMat = pollingController.acquireFrame(captureRoi);
            if (screenMat == null || screenMat.empty()) {
                pollingController.onMiss();
                pollingController.sleepUntilNextIteration(loopStartMs);
                continue;
            }
//            匹配
            try {

                Point pos = instance.fastSingleMatch(screenMat, templateMat, null, similarity);
                if (pos.x < 0) {
                    pollingController.onMiss();
                    pollingController.sleepUntilNextIteration(loopStartMs);
                    continue;
                }
                if (captureRoi != null) {
                    pos.x += captureRoi.left;
                    pos.y += captureRoi.top;
                }
                matchResults.add(new MatchResult(pos, 1));
                matched = true;
                pollingController.onHit();
//           否则就break
                break;
            } catch (Exception e) {
                Log.d("1", "handle: " + e);
                pollingController.onMiss();
            }
            pollingController.sleepUntilNextIteration(loopStartMs);
        }

        Map<String, Object> resMap = new HashMap<>();
        if (responseType != null && responseType == 1) {
//            分化类型是1，如果匹配到了，就点击，跳转下一个node
//            没有匹配到，就跳转fallback，fallback 是  operation在inputmap中定义的
            if (matched) {
                resMap.put(MetaOperation.RESULT, matchResults.get(0));
                Point p = matchResults.get(0).getLocation();
                List<Integer> matchedBbox = java.util.Arrays.asList(
                        (int) p.x,
                        (int) p.y,
                        templateMat.width(),
                        templateMat.height());
                resMap.put(MetaOperation.BBOX, matchedBbox);
//                告诉handler有没有匹配到
                resMap.put(MetaOperation.MATCHED, true);
//                    先画区域
                List<Integer> finalBbox = matchedBbox;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    svc.showRectFeedback((int) p.x, (int) p.y, finalBbox.get(2), finalBbox.get(3), 500, 0x00000000, 0, 0x44CD0C0C);
                });
            } else {
                String failPath = "/sdcard/Download/fail_" + System.currentTimeMillis() + ".png";

                resMap.put(MetaOperation.RESULT, null);
                resMap.put(MetaOperation.BBOX, bbox);
                resMap.put(MetaOperation.MATCHED, false);
            }
        }

        ctx.currentResponse = resMap;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        //      否则没有匹配到


        return true;

    }

    private String getStringSafe(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v instanceof String) ? (String) v : def;
    }

    private long parseDelayMs(Object raw) {
        if (raw instanceof Number) {
            long v = ((Number) raw).longValue();
            return Math.max(0, Math.min(v, 5000));
        }
        if (raw instanceof String) {
            try {
                long v = Long.parseLong(((String) raw).trim());
                return Math.max(0, Math.min(v, 5000));
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private double parseDouble(Object raw, double def) {
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        if (raw instanceof String) {
            try {
                return Double.parseDouble(((String) raw).trim());
            } catch (Exception ignored) {
            }
        }
        return def;
    }


}
