package com.auto.master.Task.Handler.OperationHandler;

import android.os.SystemClock;
import android.util.Log;

import com.auto.master.Task.Operation.LoadImgToMatOperation;
import com.auto.master.Task.Operation.MatchMapTemplateOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;
import com.auto.master.Template.Template;
import com.auto.master.auto.AutoAccessibilityService;
import com.auto.master.capture.ScreenCapture;
import com.auto.master.utils.AdaptivePollingController;
import com.auto.master.utils.MatchResult;
import com.auto.master.utils.OpenCVHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 图集匹配处理器（优化版）
 *
 * 主要优化点：
 * 1. 去掉每帧的 Mat.clone()，改为对 ROI 子区域直接操作（submat 是零拷贝视图）
 * 2. 线程池改为固定数量，不随模板数量动态创建
 * 3. 用 AtomicReference + volatile 替代 synchronized MatchResultHolder，减少锁竞争
 * 4. 优先级最高的任务（priority=0）串行快速路径，命中则跳过线程池
 * 5. 预加载失败时复用同一个 OperationContext，避免重复 new
 * 6. 线程池生命周期提到方法外（静态共享），避免每次匹配创建/销毁线程
 * 7. 循环间隔从固定 30ms 改为自适应：本轮匹配耗时已够长则不额外 sleep
 */
public class MatchMaptemplateOperationHandler extends OperationHandler {

    private static final String TAG = "MatchMapOp";
    private static final long MIN_LOOP_INTERVAL_MS = 16;   // 约 60fps 节拍，避免过快轮询
    private static final long MAX_PRE_DELAY_MS     = 5000;
    private static final double DEFAULT_SIMILARITY  = 0.88;
    private static final int CAPTURE_ROI_PADDING_PX = 12;
    private static final long RECT_FEEDBACK_DELAY_MS = 24L;
    private static final android.os.Handler MAIN_HANDLER =
            new android.os.Handler(android.os.Looper.getMainLooper());

    // ── 静态共享线程池：整个应用生命周期共享，不在每次匹配时创建/销毁 ──
    private static final int POOL_SIZE = 2;
    private static final ExecutorService SHARED_POOL =
            Executors.newFixedThreadPool(POOL_SIZE);

    // 复用的 fallback 加载上下文，避免 preloadTemplates 里重复 new
    private static final OperationContext FALLBACK_CTX = new OperationContext();

    MatchMaptemplateOperationHandler() {
        this.setType(7);
    }

    // ─────────────────────────── 公共入口 ────────────────────────────────────

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        ctx.currentOperation = obj;
        MatchMapTemplateOperation op = (MatchMapTemplateOperation) obj;

        AutoAccessibilityService svc = AutoAccessibilityService.get();
        if (svc == null) return false;

        Map<String, Object> inputMap = op.getInputMap();
        String projectName = getStringSafe(inputMap, MetaOperation.PROJECT, "fallback");
        String taskName    = getStringSafe(inputMap, MetaOperation.TASK, "");

        double  duration    = parseDouble(inputMap.get(MetaOperation.MATCHTIMEOUT), 5000d);
        long    preDelayMs  = parseDelayMs(inputMap.get(MetaOperation.MATCH_PRE_DELAY_MS));

        Map<String, Map<String, Double>> matchMap =
                parseMatchMap(inputMap.get(MetaOperation.MATCHMAP));
        if (matchMap == null) return createTimeoutResponse(ctx, obj);

        // 1. 预加载所有模板（保持插入顺序 = 用户配置的优先级）
        Map<String, Map<String, TemplateInfo>> templateCache =
                preloadTemplates(matchMap, projectName, taskName);
        if (templateCache.isEmpty()) return createTimeoutResponse(ctx, obj);

        // 2. 构建有序任务列表（循环外一次性完成，避免每帧重新解析 bbox）
        List<MatchTask> taskList = buildOrderedTaskList(templateCache);
        android.graphics.Rect captureRoi = buildCaptureRoi(taskList);

        // 3. 轮询匹配
        if (preDelayMs > 0) SystemClock.sleep(preDelayMs);

        long start = System.currentTimeMillis();
        AtomicReference<MatchTaskResult> winnerRef = new AtomicReference<>(null);
        AdaptivePollingController pollingController = AdaptivePollingController.forMatchMap();

        while ((duration - (System.currentTimeMillis() - start)) > 0) {
            long loopStart = SystemClock.uptimeMillis();

            // ── 截图（不在主线程调用；ScreenCapture 内部应自行保证线程安全）──
            Mat screenMat = pollingController.acquireFrame(captureRoi);
            if (screenMat == null || screenMat.empty()) {
                pollingController.onMiss();
                pollingController.sleepUntilNextIteration(loopStart);
                continue;
            }

            // ── 快速串行路径：priority=0 的任务先跑，命中就不必开线程池 ──
            boolean hitByFastPath = false;
            if (!taskList.isEmpty()) {
                MatchTask first = taskList.get(0);
                if (first.priority == 0) {
                    // submat 是零拷贝视图，不分配新内存
                    MatchTaskResult r = performMatchOnSubmat(screenMat, first, captureRoi);
                    if (r.matched) {
                        winnerRef.set(r);
                        hitByFastPath = true;
                    }
                }
            }

            // ── 并行路径：对剩余任务使用共享线程池 ──
            if (!hitByFastPath && taskList.size() > 1) {
                long remaining = (long) duration - (System.currentTimeMillis() - start);
                if (remaining > 0) {
                    runParallelMatch(screenMat, taskList, captureRoi, remaining, winnerRef);
                }
            }

            // ── 注意：screenMat 属于 ScreenCapture，不在此处 release ──

            if (winnerRef.get() != null) {
                pollingController.onHit();
                return handleMatchSuccess(obj, ctx, svc, winnerRef.get());
            }

            pollingController.onMiss();
            pollingController.sleepUntilNextIteration(loopStart);
        }

        return createTimeoutResponse(ctx, obj);
    }

    // ─────────────────────────── 匹配核心 ────────────────────────────────────

    /**
     * 直接在 screenMat 的 ROI 子区域上匹配，零拷贝。
     * submat() 返回的是视图，不分配新 Mat 内存。
     */
    private static MatchTaskResult performMatchOnSubmat(Mat screen, MatchTask mt,
                                                        android.graphics.Rect captureRoi) {
        // submat 用完后调用 release() 仅释放 Java 侧引用，不影响底层数据
        Mat roi = null;
        try {
            roi = safeSubmat(screen, toLocalRect(mt.region, captureRoi));
            if (roi == null || roi.empty()) {
                return new MatchTaskResult(false, null, mt);
            }
            // 在 roi 上匹配，返回的 pos 是相对于 roi 的坐标
            Point posInRoi = OpenCVHelper.getInstance()
                    .fastSingleMatch(roi, mt.info.mat, null, mt.info.similarity);
            if (posInRoi != null && posInRoi.x >= 0) {
                // 转换为全屏坐标
                Point posGlobal = new Point(
                        posInRoi.x + mt.region.x,
                        posInRoi.y + mt.region.y);
                return new MatchTaskResult(true, posGlobal, mt);
            }
        } catch (Exception e) {
            Log.w(TAG, "submat match error: " + e.getMessage());
        } finally {
            if (roi != null) roi.release();
        }
        return new MatchTaskResult(false, null, mt);
    }

    /**
     * 并行匹配：将除 priority=0 之外的任务提交到共享线程池。
     * 一旦找到 priority 最小的命中结果即更新 winnerRef。
     */
    private void runParallelMatch(Mat screen, List<MatchTask> tasks,
                                  android.graphics.Rect captureRoi,
                                  long remainingMs, AtomicReference<MatchTaskResult> winnerRef) {
        // 跳过已由快速路径检查过的 priority=0 任务
        int startIdx = (tasks.get(0).priority == 0) ? 1 : 0;
        if (startIdx >= tasks.size()) return;

        List<Future<MatchTaskResult>> futures = new ArrayList<>(tasks.size() - startIdx);
        for (int i = startIdx; i < tasks.size(); i++) {
            final MatchTask mt = tasks.get(i);
            futures.add(SHARED_POOL.submit(() -> performMatchOnSubmat(screen, mt, captureRoi)));
        }

        MatchTaskResult best = winnerRef.get(); // 可能已有 fast-path 结果（此处为 null）
        long deadline = System.currentTimeMillis() + remainingMs;

        for (int i = 0; i < futures.size(); i++) {
            Future<MatchTaskResult> f = futures.get(i);
            long wait = deadline - System.currentTimeMillis();
            if (wait <= 0) {
                cancelPendingFutures(futures, i);
                break;
            }
            try {
                MatchTaskResult r = f.get(wait, TimeUnit.MILLISECONDS);
                if (r.matched && (best == null || r.priority < best.priority)) {
                    best = r;
                    // futures 的顺序与 priority 顺序一致。
                    // 当前命中一旦成立，后续任务优先级只会更低，直接短路并取消剩余任务。
                    cancelPendingFutures(futures, i + 1);
                    break;
                }
            } catch (java.util.concurrent.TimeoutException ignored) {
                cancelPendingFutures(futures, i);
                break;
            } catch (Exception e) {
                Log.w(TAG, "parallel match error: " + e.getMessage());
            }
        }

        if (best != null) {
            // CAS 写入，防止多轮并发覆盖
            winnerRef.compareAndSet(null, best);
        }
    }

    private void cancelPendingFutures(List<Future<MatchTaskResult>> futures, int startIndex) {
        if (futures == null || futures.isEmpty()) {
            return;
        }
        int begin = Math.max(0, startIndex);
        for (int i = begin; i < futures.size(); i++) {
            Future<MatchTaskResult> future = futures.get(i);
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
    }

    /**
     * 安全创建 submat：若 region 超出 screen 边界则裁剪，超界则返回 null。
     */
    private static Mat safeSubmat(Mat screen, Rect region) {
        int x = Math.max(0, region.x);
        int y = Math.max(0, region.y);
        int w = Math.min(region.width,  screen.width()  - x);
        int h = Math.min(region.height, screen.height() - y);
        if (w <= 0 || h <= 0) return null;
        return screen.submat(new Rect(x, y, w, h));
    }

    private static Rect toLocalRect(Rect region, android.graphics.Rect captureRoi) {
        if (captureRoi == null) {
            return region;
        }
        return new Rect(
                region.x - captureRoi.left,
                region.y - captureRoi.top,
                region.width,
                region.height);
    }

    // ─────────────────────────── 内部数据类 ──────────────────────────────────

    private static class MatchTask {
        final int          priority;
        final String       templateName;
        final List<Integer> bboxList;
        final Rect         region;
        final TemplateInfo info;

        MatchTask(int priority, String templateName,
                  List<Integer> bboxList, Rect region, TemplateInfo info) {
            this.priority     = priority;
            this.templateName = templateName;
            this.bboxList     = bboxList;
            this.region       = region;
            this.info         = info;
        }
    }

    private static class TemplateInfo {
        final Mat    mat;
        final double similarity;
        final int    width;
        final int    height;

        TemplateInfo(Mat mat, double similarity) {
            this.mat        = mat;
            this.similarity = similarity;
            this.width      = mat.width();
            this.height     = mat.height();
        }
    }

    private static class MatchTaskResult {
        final boolean       matched;
        final Point         position;
        final MatchTask     task;
        final int           priority;

        MatchTaskResult(boolean matched, Point position, MatchTask task) {
            this.matched  = matched;
            this.position = position;
            this.task     = task;
            this.priority = task.priority;
        }
    }

    // ─────────────────────────── 构建任务列表 ─────────────────────────────────

    private List<MatchTask> buildOrderedTaskList(
            Map<String, Map<String, TemplateInfo>> cache) {
        List<MatchTask> tasks = new ArrayList<>();
        int priority = 0;
        for (Map.Entry<String, Map<String, TemplateInfo>> bboxEntry : cache.entrySet()) {
            List<Integer> bboxList = parseBbox(bboxEntry.getKey());
            if (bboxList.size() < 4) continue;
            Rect region = new Rect(bboxList.get(0), bboxList.get(1),
                    bboxList.get(2), bboxList.get(3));
            for (Map.Entry<String, TemplateInfo> tplEntry : bboxEntry.getValue().entrySet()) {
                tasks.add(new MatchTask(priority++,
                        tplEntry.getKey(), bboxList, region, tplEntry.getValue()));
            }
        }
        return tasks;
    }

    private android.graphics.Rect buildCaptureRoi(List<MatchTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (MatchTask task : tasks) {
            if (task == null || task.region == null) {
                continue;
            }
            left = Math.min(left, task.region.x);
            top = Math.min(top, task.region.y);
            right = Math.max(right, task.region.x + task.region.width);
            bottom = Math.max(bottom, task.region.y + task.region.height);
        }
        if (left >= right || top >= bottom) {
            return null;
        }
        return new android.graphics.Rect(
                left - CAPTURE_ROI_PADDING_PX,
                top - CAPTURE_ROI_PADDING_PX,
                right + CAPTURE_ROI_PADDING_PX,
                bottom + CAPTURE_ROI_PADDING_PX);
    }

    // ─────────────────────────── 模板预加载 ──────────────────────────────────

    private Map<String, Map<String, TemplateInfo>> preloadTemplates(
            Map<String, Map<String, Double>> matchMap,
            String projectName, String taskName) {

        Map<String, Map<String, TemplateInfo>> cache = new java.util.LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Double>> entry : matchMap.entrySet()) {
            String bboxString = entry.getKey();
            Map<String, TemplateInfo> bboxTemplates = new HashMap<>();

            for (Map.Entry<String, Double> item : entry.getValue().entrySet()) {
                String templateName       = item.getKey();
                Double templateSimilarity = item.getValue();

                Mat templateMat = Template.getTaskSingleMutCache(projectName, taskName, templateName);

                // Fallback：尝试加载一次（复用静态 FALLBACK_CTX，避免重复 new）
                if (templateMat == null || templateMat.empty()) {
                    templateMat = tryLoadTemplate(projectName, templateName);
                    // 加载后再取缓存
                    if (templateMat == null || templateMat.empty()) {
                        templateMat = Template.getTaskSingleMutCache(projectName, taskName, templateName);
                    }
                }

                if (templateMat != null && !templateMat.empty()) {
                    double minScore = (templateSimilarity != null)
                            ? templateSimilarity : DEFAULT_SIMILARITY;
                    bboxTemplates.put(templateName, new TemplateInfo(templateMat, minScore));
                } else {
                    Log.w(TAG, "模板加载失败: " + templateName);
                }
            }

            if (!bboxTemplates.isEmpty()) {
                cache.put(bboxString, bboxTemplates);
            }
        }
        return cache;
    }

    /** 单次模板加载，复用静态 context 避免 GC 压力 */
    private static Mat tryLoadTemplate(String projectName, String templateName) {
        try {
            LoadImgToMatOperation loadOp = new LoadImgToMatOperation();
            loadOp.setId("tmp_" + templateName);
            loadOp.setResponseType(1);
            HashMap<String, Object> tmpMap = new HashMap<>();
            tmpMap.put(MetaOperation.PROJECT, projectName);
            loadOp.setInputMap(tmpMap);
            new LoadImgToMatOperationHandler().handle(loadOp, FALLBACK_CTX);
        } catch (Exception e) {
            Log.e(TAG, "tryLoadTemplate error: " + e.getMessage());
        }
        return null; // 调用方从 cache 重新取
    }

    // ─────────────────────────── 响应处理 ────────────────────────────────────

    private boolean handleMatchSuccess(MetaOperation obj, OperationContext ctx,
                                       AutoAccessibilityService svc,
                                       MatchTaskResult result) {
        Map<String, Object> resMap = new HashMap<>();
        MatchResult matchResult = new MatchResult(result.position, 1.0);

        Integer responseType = obj.getResponseType();
        if (responseType != null && responseType == 1) {
            List<Integer> matchedBbox = Arrays.asList(
                    (int) result.position.x,
                    (int) result.position.y,
                    result.task.info.width,
                    result.task.info.height);
            resMap.put(MetaOperation.RESULT,  matchResult);
            resMap.put(MetaOperation.BBOX,    matchedBbox);
            resMap.put(MetaOperation.MATCHED, true);

            MAIN_HANDLER.postDelayed(() ->
                    svc.showRectFeedback(
                            (int) result.position.x, (int) result.position.y,
                            result.task.info.width,  result.task.info.height,
                            120, 0xFFCD0C0C, 1.5f, 0x00000000), RECT_FEEDBACK_DELAY_MS);
        }

        ctx.currentResponse = resMap;
        ctx.lastOperation   = obj;
        ctx.currentOperation = obj;
        return true;
    }

    private boolean createTimeoutResponse(OperationContext ctx, MetaOperation obj) {
        HashMap<String, Object> resMap = new HashMap<>();
        resMap.put(MetaOperation.RESULT,  null);
        resMap.put(MetaOperation.BBOX,    null);
        resMap.put(MetaOperation.MATCHED, false);
        ctx.currentResponse  = resMap;
        ctx.lastOperation    = obj;
        ctx.currentOperation = obj;
        return true;
    }

    // ─────────────────────────── 解析工具 ────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Double>> parseMatchMap(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map) return (Map<String, Map<String, Double>>) raw;
        if (raw instanceof String) {
            try {
                Type type = new TypeToken<Map<String, Map<String, Double>>>() {}.getType();
                return new Gson().fromJson((String) raw, type);
            } catch (Exception e) {
                Log.e(TAG, "解析 MATCHMAP JSON 失败: " + e.getMessage());
            }
        }
        return null;
    }

    public static List<Integer> parseBbox(String str) {
        if (str == null || str.trim().isEmpty()) return List.of();
        String clean = str.replaceAll("[\\[\\]\\s]", "");
        try {
            return Arrays.stream(clean.split(","))
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的 bbox 格式: " + str, e);
        }
    }

    private String getStringSafe(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return (v instanceof String) ? (String) v : def;
    }

    private long parseDelayMs(Object raw) {
        if (raw instanceof Number) {
            return Math.max(0, Math.min(((Number) raw).longValue(), MAX_PRE_DELAY_MS));
        }
        if (raw instanceof String) {
            try {
                return Math.max(0, Math.min(Long.parseLong(((String) raw).trim()), MAX_PRE_DELAY_MS));
            } catch (Exception ignored) {}
        }
        return 0L;
    }

    private double parseDouble(Object raw, double def) {
        if (raw instanceof Number) return ((Number) raw).doubleValue();
        if (raw instanceof String) {
            try { return Double.parseDouble(((String) raw).trim()); }
            catch (Exception ignored) {}
        }
        return def;
    }
}
