package com.auto.master.Task.Handler.ResponseHandler;


import static org.opencv.android.NativeCameraView.TAG;

import android.os.SystemClock;
import android.util.Log;


import com.auto.master.Task.Handler.OperationHandler.OperationHandler;
import com.auto.master.Task.Handler.OperationHandler.OperationHandlerManager;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Task.Project.Project;
import com.auto.master.Task.Task;
import com.auto.master.Task.model.Point;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.auto.ScriptExecuteContext;
import com.auto.master.utils.MatchResult;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

//@TYPE = 1   用于 模板匹配专用
public class MatchTemplateDynamicJumpResponseHandler extends DefaultResponseHandler {

    public MatchTemplateDynamicJumpResponseHandler() {
        this.type = 1;
    }


    /**
     * 在 BBox 区域内生成一个随机点
     *
     * @param bbox BBox 列表，格式为 [x, y, width, height]
     * @return 随机点 [randomX, randomY]，如果 BBox 无效返回 null
     */
    public List<Integer> generateRandomPointInBBox(List<Integer> bbox) {
        // 参数校验
        if (bbox == null || bbox.size() != 4) {
            Log.e(TAG, "无效的 BBox 参数");
            return null;
        }

        try {
            int x = bbox.get(0);
            int y = bbox.get(1);
            int width = bbox.get(2);
            int height = bbox.get(3);

            // 检查 BBox 是否有效
            if (width <= 0 || height <= 0) {
                Log.e(TAG, "BBox 宽度或高度无效: " + width + "x" + height);
                return null;
            }

            // 确保坐标非负
            x = Math.max(0, x);
            y = Math.max(0, y);
            width = Math.max(1, width);  // 最小宽度为1
            height = Math.max(1, height); // 最小高度为1

            // 生成随机坐标
            Random random = new Random();
            int randomX = x + random.nextInt(width);   // x ~ x+width-1
            int randomY = y + random.nextInt(height);  // y ~ y+height-1

            // 返回结果
            List<Integer> randomPoint = new ArrayList<>();
            randomPoint.add(randomX);
            randomPoint.add(randomY);

            Log.d(TAG, String.format("在 BBox [%d,%d,%d,%d] 内生成随机点: (%d,%d)",
                    x, y, width, height, randomX, randomY));

            return randomPoint;

        } catch (Exception e) {
            Log.e(TAG, "生成随机点失败", e);
            return null;
        }
    }

    @Override
    public void process(Object response, ScriptExecuteContext scriptExecuteContext) {
        if (scriptExecuteContext == null || scriptExecuteContext.sharedContext == null) {
            return;
        }
        // 默认终止，只有找到有效下一节点才覆盖
        scriptExecuteContext.tobeHandledOperation = null;

//        这种机制是：
//        如果匹配结果有效，则点击或者不惦记，跳转到下一个节点
//        如果匹配结果无效，则跳转某个节点 fallBack。
        OperationContext ctx = scriptExecuteContext.sharedContext;
        if (ctx.currentOperation == null || ctx.lastOperation == null) {
            return;
        }
//        对应 matchTemplate 的responsetype =1
        Map<String, Object> inputMap = ctx.currentOperation.getInputMap();
        if (inputMap == null) {
            return;
        }
        // 每种responseHandler只处理一种response
        String targetId = null;
        if (response instanceof Map) {
            Map result = (Map) response;
            if (result == null) {
//                没有结果 跳转 fallbackOperation 根据 fallbackOperationId

                Object o = inputMap.get(MetaOperation.FALLBACKOPERATIONID);
                String fallBackId = (String) o;
                if (!StringUtils.isNotEmpty(fallBackId)) {
                    Log.w(TAG, "匹配失败且 fallback 为空，终止脚本");
                    return;
                }
                targetId = fallBackId;
            } else {
                AutoAccessibilityService svc = AutoAccessibilityService.get();
                if (svc == null) return;
                Map<String, Object> currentResponse = ctx.currentResponse;
                Object o = currentResponse.get(MetaOperation.RESULT);
                if (o == null) {

                    Object o1 = inputMap.get(MetaOperation.FALLBACKOPERATIONID);
                    String fallBackId = (String) o1;
                    if (!StringUtils.isNotEmpty(fallBackId)) {
                        Log.w(TAG, "匹配结果为空且 fallback 为空，终止脚本");
                        return;
                    }
                    targetId = fallBackId;
                } else {
                    Boolean click = false;
//                    MatchResult matchResult = (MatchResult) o;
//                    找到点了 就点击bbox todo  这里是否点击从 inputMap里面看
                    Object o1 = inputMap.get(MetaOperation.SUCCEESCLICK);
                    Boolean o2 = (Boolean) o1;
                    if (o2 != null) {
                        click = o2;
                    }
                    if (click) {
                        Object box = ctx.currentResponse.get(MetaOperation.BBOX);
                        List<Integer> bbox = (List<Integer>) box;
//                    点击bbox里面任意一点
                        List<Integer> integers = generateRandomPointInBBox(bbox);
//                    List<Integer> integers = bbox;
                        Point target;
                        if (!integers.isEmpty()) {
                            Integer x = integers.get(0);
                            Integer y = integers.get(1);
                            target = new Point(x, y);
                        } else {
                            target = new Point(bbox.get(0), bbox.get(1));
                        }
                        //        先画区域
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            svc.showClickFeedback((int) target.x, (int) target.y, 280);
                        });
                        try {
                            Thread.sleep(100);
                        } catch (Exception e) {

                        }
                        svc.click((int) target.x, (int) target.y,
                                () -> {
//                                    notifyResult(result, true);
                                    Log.d(TAG, "点击成功回调");
                                },
                                () -> {
//                                    notifyResult(result, false);
                                    Log.w(TAG, "点击失败回调");
                                }
                        );
                        try {
                            Thread.sleep(100);
                        } catch (Exception e) {

                        }
//                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
//
//                        });
                    }


                }


            }


//            todo 不校验这个result的内容了
//            直接跳到下一步即可
            String nextOpId = (String) inputMap.get(MetaOperation.NEXT_OPERATION_ID);
            if (StringUtils.isNotEmpty(targetId)) {
//                todo 这里很重要
                nextOpId = targetId;
            }
            if (!StringUtils.isNotEmpty(nextOpId)) {
                /*
            不能把cpu打满 这里延时 10ms
             */
                SystemClock.sleep(10);
                return;
            }
            long postDelayMs = parseDelayMs(inputMap.get(MetaOperation.MATCH_POST_DELAY_MS));
            if (postDelayMs > 0) {
                SystemClock.sleep(postDelayMs);
            }
            // 跳转下一个operation 注意这里是 隶属的 task里面的 没有跳转 task 所以注意这个reponseHandler

            Project anchorProject = ctx.anchorProject;
            if (anchorProject == null || anchorProject.getTaskMap() == null) {
                return;
            }
            Map<String, Task> taskMap = anchorProject.getTaskMap();
            Task task = taskMap.get(ctx.lastOperation.taskId);
            if (task == null) {
                task = resolveTaskByOperationId(taskMap, ctx.lastOperation.getId());
            }
            if (task == null || task.getOperationMap() == null) {
                return;
            }

//            todo 这里有问题  这里 拿到下一个operation 然后 处理了 这个 operation 但是结果并没有自动处理！！！
//            怎么做呢
//            boolean notEnd = true;

            MetaOperation metaOperation = task.getOperationMap().get(nextOpId);
            if (metaOperation == null) {
                Log.e(TAG, "fallback/next 节点不存在，终止脚本: " + nextOpId);
                return;
            }

            scriptExecuteContext.tobeHandledOperation = metaOperation;
            /*
            不能把cpu打满 这里延时 10ms
             */
            SystemClock.sleep(10);


        }
        // 非 Map 响应直接终止，避免沿用旧节点造成假死

    }

    private Task resolveTaskByOperationId(Map<String, Task> taskMap, String operationId) {
        if (taskMap == null || operationId == null) {
            return null;
        }
        for (Task t : taskMap.values()) {
            if (t != null && t.getOperationMap() != null && t.getOperationMap().containsKey(operationId)) {
                return t;
            }
        }
        return null;
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
}
