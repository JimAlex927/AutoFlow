package com.auto.master.Template;

import android.util.Log;

import com.auto.master.auto.GestureOverlayView;
import com.auto.master.utils.OpenCVHelper;

import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//这个类负责对Task中的 模板 template进行操作 线程安全
public class Template {

    private static final String TAG = "Template";
    
    // 缓存大小限制
    private static final int MAX_CACHE_ENTRIES = 10; // 最多缓存10个任务的模板
    private static final int MAX_MATS_PER_TASK = 50; // 每个任务最多50个模板

    // 使用 LinkedHashMap 实现 LRU 缓存
    private static final Map<String, Map<String, Mat>> matCache = new LinkedHashMap<String, Map<String, Mat>>(MAX_CACHE_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Map<String, Mat>> eldest) {
            if (size() > MAX_CACHE_ENTRIES) {
                // 清理最旧的 Mat 缓存
                cleanupMats(eldest.getValue());
                Log.d(TAG, "LRU清理旧任务缓存: " + eldest.getKey());
                return true;
            }
            return false;
        }
    };

    private static final Map<String, Map<String, List<Integer>>> manifestCache = new LinkedHashMap<>();
    private static final Map<String, Map<String, GestureOverlayView.GestureNode>> gestureCache = new LinkedHashMap<>();
    private static final Map<String, Long> taskSnapshotCache = new HashMap<>();
    
    // 缓存大小统计（仅在 synchronized 块内访问，volatile 冗余）
    private static int totalCachedMats = 0;

    private static Map<String, Mat> newTaskMatCache() {
        // accessOrder=false（插入序）：内层 map 没有 removeEldestEntry，
        // 用 accessOrder=true 只会在每次 get() 时无谓地重排链表节点。
        return new LinkedHashMap<>(16, 0.75f, false);
    }

    /**
     * 拿到task的所有的 manifest
     * @param projectName
     * @param taskName
     * @param taskBbox
     */
    public static synchronized void putTaskManifestCache(String projectName,String taskName,Map<String,List<Integer>> taskBbox){
        manifestCache.put(projectName+"_"+taskName,taskBbox);

    }

    /**
     * 获取 task的 manifest
     * @param projectName
     * @param taskName
     * @return
     */
    public static synchronized  Map<String,List<Integer>>  getTaskManifestCache(String projectName,String taskName){

        return manifestCache.get(projectName+"_"+taskName);

    }

    /**
     * 获取task的 matcache
     * @param projectName
     * @param taskName
     * @return
     */
    public static synchronized  Map<String, Mat>  getTaskMatCache(String projectName, String taskName){

        return matCache.get(projectName+"_"+taskName);

    }


    /**
     * 拿到task的所有的mat
     * @param projectName
     * @param taskName
     * @param matMap
     */
    public static synchronized void putTaskMatCache(String projectName,String taskName,Map<String,Mat> matMap){
        String cacheKey = projectName + "_" + taskName;
        Map<String, Mat> oldMap = matCache.get(cacheKey);
        if (oldMap != null && oldMap != matMap) {
            cleanupMats(oldMap);
        }
        Map<String, Mat> cacheToStore = matMap;
        if (matMap != null && !(matMap instanceof LinkedHashMap)) {
            cacheToStore = newTaskMatCache();
            cacheToStore.putAll(matMap);
        }
        if (oldMap != matMap) {
            int newCount = cacheToStore == null ? 0 : cacheToStore.size();
            totalCachedMats += Math.max(0, newCount);
        }
        matCache.put(cacheKey, cacheToStore);

    }



    /**
     * 获取task的 gesturecache
     * @param projectName
     * @param taskName
     * @return
     */
    public static synchronized  Map<String, GestureOverlayView.GestureNode>  getTaskGestureCache(String projectName, String taskName){

        return gestureCache.get(projectName+"_"+taskName);

    }


    public static synchronized  GestureOverlayView.GestureNode  getTaskSingleGestureCache(String projectName, String taskName,String key){

        Map<String, GestureOverlayView.GestureNode> stringGestureNodeMap = gestureCache.get(projectName + "_" + taskName);
        if (stringGestureNodeMap==null){
            return null;
        }
        GestureOverlayView.GestureNode gestureNode = stringGestureNodeMap.get(key);

        return gestureNode;

    }




    /**
     * 拿到task的所有的gesture
     * @param projectName
     * @param taskName
     * @param gestureMap
     */
    public static synchronized void putTaskGestureCache(String projectName,String taskName,Map<String, GestureOverlayView.GestureNode> gestureMap){

        gestureCache.put(projectName+"_"+taskName,gestureMap);

    }

    public static synchronized void putTaskSingleGestureCache(String projectName, String taskName, String key, GestureOverlayView.GestureNode gestureNode) {
        String cacheKey = projectName + "_" + taskName;
        Map<String, GestureOverlayView.GestureNode> gestureMap = gestureCache.get(cacheKey);
        if (gestureMap == null) {
            gestureMap = new HashMap<>();
            gestureCache.put(cacheKey, gestureMap);
        }
        gestureMap.put(key, gestureNode);
    }

    public static synchronized void putTaskSingleManifestCache(String projectName,String taskName,String key,List<Integer> bbox){
        String cacheKey = projectName + "_" + taskName;
        Map<String, List<Integer>> projectTaskManifestMap = manifestCache.get(cacheKey);
        if (projectTaskManifestMap==null){
            projectTaskManifestMap = new HashMap<>();
            manifestCache.put(cacheKey, projectTaskManifestMap);
        }
        projectTaskManifestMap.put(key,bbox);

    }

    // 不需要 synchronized：纯字符串拼接，无共享状态修改
    public static String taskCacheKey(String projectName, String taskName) {
        return projectName + "_" + taskName;
    }

    public static synchronized boolean shouldReloadTaskCache(String projectName, String taskName, long snapshotToken) {
        String cacheKey = taskCacheKey(projectName, taskName);
        Long lastSnapshot = taskSnapshotCache.get(cacheKey);
        if (lastSnapshot == null) {
            return true;
        }
        if (snapshotToken <= 0) {
            return false;
        }
        return snapshotToken > lastSnapshot;
    }

    public static synchronized void markTaskCacheSnapshot(String projectName, String taskName, long snapshotToken) {
        if (snapshotToken <= 0) {
            return;
        }
        taskSnapshotCache.put(taskCacheKey(projectName, taskName), snapshotToken);
    }

    public static synchronized void invalidateTaskCache(String projectName, String taskName) {
        String cacheKey = taskCacheKey(projectName, taskName);
        cleanupMats(matCache.remove(cacheKey));
        manifestCache.remove(cacheKey);
        gestureCache.remove(cacheKey);
        taskSnapshotCache.remove(cacheKey);
    }

    public static synchronized void putTaskSingleMatCache(String projectName,String taskName,String key,Mat value){
        String cacheKey = projectName+"_"+taskName;
        Map<String, Mat> projectTaskMatMap = matCache.get(cacheKey);

        if (projectTaskMatMap == null) {
            projectTaskMatMap = newTaskMatCache();
            matCache.put(cacheKey, projectTaskMatMap);
        }
        
        // 如果该任务缓存超过限制，清理旧的
        if (projectTaskMatMap.size() >= MAX_MATS_PER_TASK) {
            Log.w(TAG, "任务模板缓存超过限制，清理最旧的模板");
            cleanupOldestMats(projectTaskMatMap, 10); // 清理10个最旧的
        }
        
        // 如果已存在同名模板，先释放
        Mat oldMat = projectTaskMatMap.get(key);
        if (oldMat != null && !oldMat.empty()) {
            oldMat.release();
            totalCachedMats--;
        }
        
        projectTaskMatMap.put(key, value);
        totalCachedMats++;
        
        Log.d(TAG, "缓存模板: " + cacheKey + "/" + key + ", 总缓存: " + totalCachedMats);
    }
    
    private static void cleanupMats(Map<String, Mat> matMap) {
        if (matMap == null) return;
        for (Mat mat : matMap.values()) {
            if (mat != null && !mat.empty()) {
                mat.release();
                totalCachedMats--;
            }
        }
        matMap.clear();
        // 模板 Mat 释放后，其 nativeObj 地址可能被新 Mat 复用，
        // 导致 OpenCVHelper 中的灰度模板缓存命中错误条目。
        // 模板重载是低频事件，此处清空灰度缓存确保下次重建正确。
        try {
            OpenCVHelper.getInstance().clearGrayTemplateCache();
        } catch (Throwable ignored) {}
    }
    
    private static void cleanupOldestMats(Map<String, Mat> matMap, int count) {
        List<String> keysToRemove = new ArrayList<>();
        int removed = 0;
        for (String key : matMap.keySet()) {
            if (removed >= count) break;
            keysToRemove.add(key);
            removed++;
        }
        for (String key : keysToRemove) {
            Mat mat = matMap.remove(key);
            if (mat != null && !mat.empty()) {
                mat.release();
                totalCachedMats--;
            }
        }
    }
    
    /**
     * 清理指定项目的所有缓存
     */
    public static synchronized void clearProjectCache(String projectName) {
        Log.d(TAG, "清理项目缓存: " + projectName);
        
        // 清理 Mat 缓存
        List<String> keysToRemove = new ArrayList<>();
        for (String key : matCache.keySet()) {
            if (key.startsWith(projectName + "_")) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            cleanupMats(matCache.remove(key));
        }
        
        // 清理 manifest 缓存
        manifestCache.keySet().removeIf(key -> key.startsWith(projectName + "_"));
        
        // 清理 gesture 缓存
        gestureCache.keySet().removeIf(key -> key.startsWith(projectName + "_"));
        taskSnapshotCache.keySet().removeIf(key -> key.startsWith(projectName + "_"));
        
        Log.d(TAG, "清理完成，当前缓存Mat数量: " + totalCachedMats);
    }
    
    /**
     * 清理所有缓存（应用退出时调用）
     */
    public static synchronized void clearAllCache() {
        Log.d(TAG, "清理所有缓存");
        
        for (Map<String, Mat> matMap : matCache.values()) {
            cleanupMats(matMap);
        }
        matCache.clear();
        manifestCache.clear();
        gestureCache.clear();
        taskSnapshotCache.clear();
        totalCachedMats = 0;
        
        Log.d(TAG, "所有缓存已清理");
    }
    
    /**
     * 清除指定模板的 mat 缓存（替换截图后调用，确保下次匹配加载新图片）。
     */
    public static synchronized void clearTaskSingleMatCache(String projectName, String taskName, String key) {
        String cacheKey = projectName + "_" + taskName;
        Map<String, Mat> projectTaskMatMap = matCache.get(cacheKey);
        if (projectTaskMatMap == null) return;
        Mat old = projectTaskMatMap.remove(key);
        if (old != null && !old.empty()) {
            old.release();
            totalCachedMats--;
        }
    }

    /**
     * 获取缓存统计信息
     */
    public static synchronized String getCacheStats() {
        return "任务缓存数: " + matCache.size() + ", 总Mat数: " + totalCachedMats;
    }

    public static synchronized Mat getTaskSingleMutCache(String projectName,String taskName,String key){
        Map<String, Mat> projectMatMap = matCache.get(projectName+"_"+taskName);
        if (projectMatMap == null) {
            return null;
        }else {
            return projectMatMap.get(key);
        }
    }

    public static synchronized List<Integer> getManifestSingleCache(String projectName,String taskName,String key){
        Map<String, List<Integer>> projectTaskManifestMap = manifestCache.get(projectName + "_" + taskName);
        if (projectTaskManifestMap == null) {
            return null;
        }else {
            return projectTaskManifestMap.get(key);
        }
    }

}
