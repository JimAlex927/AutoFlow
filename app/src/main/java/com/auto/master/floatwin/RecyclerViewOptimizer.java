package com.auto.master.floatwin;

import android.content.Context;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView优化工具类
 * 统一配置RecyclerView的性能优化参数
 */
public class RecyclerViewOptimizer {
    
    // 共享的RecycledViewPool，用于相同类型的RecyclerView
    private static final RecyclerView.RecycledViewPool SHARED_POOL = new RecyclerView.RecycledViewPool();
    
    static {
        // 为不同ViewType设置缓存大小
        SHARED_POOL.setMaxRecycledViews(0, 20); // 默认类型
    }
    
    /**
     * 优化RecyclerView性能
     * @param rv RecyclerView实例
     * @param useSharedPool 是否使用共享的RecycledViewPool
     */
    public static void optimize(RecyclerView rv, boolean useSharedPool) {
        if (rv == null) {
            return;
        }
        
        // 1. 如果item高度固定，设置为true可以提升性能
        rv.setHasFixedSize(true);
        
        // 2. 设置item缓存大小（默认是2，增加可以减少onBindViewHolder调用）
        rv.setItemViewCacheSize(20);
        
        // 3. 使用共享的RecycledViewPool（适用于多个相同类型的RecyclerView）
        if (useSharedPool) {
            rv.setRecycledViewPool(SHARED_POOL);
        }
        
        // 4. 嵌套滚动优化
        rv.setNestedScrollingEnabled(false);
    }
    
    /**
     * 优化RecyclerView（默认使用共享Pool）
     */
    public static void optimize(RecyclerView rv) {
        optimize(rv, true);
    }
    
    /**
     * 创建优化的LinearLayoutManager
     * 启用预测动画和平滑滚动
     */
    public static LinearLayoutManager createOptimizedLinearLayoutManager(Context context) {
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        // 启用预测动画，使item变化更流畅
        layoutManager.setItemPrefetchEnabled(true);
        // 设置预加载item数量
        layoutManager.setInitialPrefetchItemCount(4);
        return layoutManager;
    }
    
    /**
     * 为项目/任务列表优化（垂直列表）
     */
    public static void optimizeForProjectList(RecyclerView rv, Context context) {
        rv.setLayoutManager(createOptimizedLinearLayoutManager(context));
        optimize(rv, true);
    }
    
    /**
     * 为操作列表优化（可能有动画）
     */
    public static void optimizeForOperationList(RecyclerView rv, Context context) {
        rv.setLayoutManager(createOptimizedLinearLayoutManager(context));
        // 操作列表不使用共享Pool，因为可能有特殊的ViewType
        optimize(rv, false);
    }
    
    /**
     * 清理RecyclerView，释放资源
     */
    public static void cleanup(RecyclerView rv) {
        if (rv != null) {
            rv.setAdapter(null);
            rv.setLayoutManager(null);
            rv.setRecycledViewPool(null);
        }
    }
}
