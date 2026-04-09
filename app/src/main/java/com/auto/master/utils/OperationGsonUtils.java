package com.auto.master.utils;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationGsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作（Operation）相关的 JSON 序列化/反序列化工具。
 * 显式按固定字段读写，避免 release 混淆后字段名变化导致节点面板读空。
 */
public class OperationGsonUtils {

    /**
     * 将 JSON 字符串解析为 MetaOperation 列表
     *
     * @param jsonStr operations.json 的内容
     * @return List<MetaOperation> 或空列表（解析失败时）
     */
    public static List<MetaOperation> fromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            List<MetaOperation> operations = OperationGsonHelper.parseOperations(jsonStr);
            return operations != null ? operations : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 将 MetaOperation 列表序列化为 JSON 字符串
     *
     * @param operations 操作列表
     * @return JSON 字符串
     */
    public static String toJson(List<MetaOperation> operations) {
        try {
            return OperationGsonHelper.toJson(operations);
        } catch (Exception e) {
            e.printStackTrace();
            return "[]";
        }
    }

    /**
     * 从单个 JSON 对象字符串创建 MetaOperation
     */
    public static MetaOperation fromSingleJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return null;
        }
        try {
            return OperationGsonHelper.parseOperation(jsonStr);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
