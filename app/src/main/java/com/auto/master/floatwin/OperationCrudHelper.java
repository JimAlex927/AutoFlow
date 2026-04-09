package com.auto.master.floatwin;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.auto.master.Task.Operation.MetaOperation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Helper class for operation CRUD operations (Create, Read, Update, Delete).
 * Handles JSON file operations for operations.json and related cleanup.
 */
public class OperationCrudHelper {

    private static final String TAG = "OperationCrudHelper";
    private final FloatWindowHost host;

    public OperationCrudHelper(FloatWindowHost host) {
        this.host = host;
    }

    public JSONArray readOperationsArray() throws Exception {
        File currentTaskDir = host.getCurrentTaskDir();
        if (currentTaskDir == null) {
            return new JSONArray();
        }
        File jsonFile = new File(currentTaskDir, "operations.json");
        if (!jsonFile.exists()) {
            return new JSONArray();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return new JSONArray();
        }
        String content = new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8);
        if (TextUtils.isEmpty(content.trim())) {
            return new JSONArray();
        }
        return new JSONArray(content);
    }

    public boolean writeOperationsArray(JSONArray jsonArray, String successText, Runnable onSuccess) {
        File currentTaskDir = host.getCurrentTaskDir();
        if (currentTaskDir == null) {
            return false;
        }
        try {
            File jsonFile = new File(currentTaskDir, "operations.json");
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write(jsonArray.toString(2));
            }

            if (onSuccess != null) {
                onSuccess.run();
            }

            if (!TextUtils.isEmpty(successText)) {
                host.showToast(successText);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "写入 operations.json 失败", e);
            host.showToast("保存失败: " + e.getMessage());
            return false;
        }
    }

    public int findOperationIndex(JSONArray jsonArray, String operationId) {
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.optJSONObject(i);
            if (jsonObject != null && operationId.equals(jsonObject.optString("id"))) {
                return i;
            }
        }
        return -1;
    }

    public void deleteOperation(String operationId, Runnable onSuccess) {
        File currentTaskDir = host.getCurrentTaskDir();
        if (currentTaskDir == null || TextUtils.isEmpty(operationId)) {
            return;
        }
        try {
            JSONArray original = readOperationsArray();
            JSONArray result = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < original.length(); i++) {
                JSONObject item = original.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                if (!removed && operationId.equals(item.optString("id"))) {
                    removed = true;
                    continue;
                }
                result.put(item);
            }
            if (!removed) {
                host.showToast("未找到要删除的操作");
                return;
            }
            writeOperationsArray(result, "已删除操作", onSuccess);
        } catch (Exception e) {
            Log.e(TAG, "删除操作失败", e);
            host.showToast("删除失败: " + e.getMessage());
        }
    }

    public void duplicateOperation(String operationId, String newIdGenerator, Runnable onSuccess) {
        File currentTaskDir = host.getCurrentTaskDir();
        if (currentTaskDir == null || TextUtils.isEmpty(operationId)) {
            return;
        }
        try {
            JSONArray original = readOperationsArray();
            int index = findOperationIndex(original, operationId);
            if (index < 0) {
                host.showToast("未找到要复制的操作");
                return;
            }

            JSONObject source = original.getJSONObject(index);
            JSONObject copy = new JSONObject(source.toString());
            copy.put("id", newIdGenerator);
            String oldName = source.optString("name", "操作");
            copy.put("name", oldName + " - 副本");

            JSONArray result = new JSONArray();
            for (int i = 0; i < original.length(); i++) {
                JSONObject item = original.optJSONObject(i);
                if (item != null) {
                    result.put(item);
                }
                if (i == index) {
                    result.put(copy);
                }
            }
            writeOperationsArray(result, "已复制操作", onSuccess);
        } catch (Exception e) {
            Log.e(TAG, "复制操作失败", e);
            host.showToast("复制失败: " + e.getMessage());
        }
    }

    public void moveOperation(String operationId, int direction, Runnable onSuccess) {
        File currentTaskDir = host.getCurrentTaskDir();
        if (currentTaskDir == null || TextUtils.isEmpty(operationId) || direction == 0) {
            return;
        }
        try {
            JSONArray original = readOperationsArray();
            int currentIndex = findOperationIndex(original, operationId);
            if (currentIndex < 0) {
                host.showToast("未找到要移动的操作");
                return;
            }

            int targetIndex = currentIndex + direction;
            if (targetIndex < 0 || targetIndex >= original.length()) {
                host.showToast("已经到边界了");
                return;
            }

            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < original.length(); i++) {
                JSONObject item = original.optJSONObject(i);
                if (item != null) {
                    list.add(item);
                }
            }
            if (currentIndex >= list.size() || targetIndex >= list.size()) {
                return;
            }

            JSONObject current = list.get(currentIndex);
            list.set(currentIndex, list.get(targetIndex));
            list.set(targetIndex, current);

            JSONArray result = new JSONArray();
            for (JSONObject item : list) {
                result.put(item);
            }
            writeOperationsArray(result, direction < 0 ? "已上移" : "已下移", onSuccess);
        } catch (Exception e) {
            Log.e(TAG, "移动操作失败", e);
            host.showToast("移动失败: " + e.getMessage());
        }
    }

    public boolean appendOperation(JSONObject operationObject, Runnable onSuccess) {
        try {
            JSONArray jsonArray = readOperationsArray();
            jsonArray.put(operationObject);
            return writeOperationsArray(jsonArray, "已添加操作", onSuccess);
        } catch (Exception e) {
            Log.e(TAG, "添加 operation 失败", e);
            host.showToast("添加失败: " + e.getMessage());
            return false;
        }
    }

    private Set<String> collectReferencedImageFiles(JSONArray operationsArray) {
        Set<String> refs = new HashSet<>();
        if (operationsArray == null) {
            return refs;
        }
        for (int i = 0; i < operationsArray.length(); i++) {
            JSONObject op = operationsArray.optJSONObject(i);
            if (op == null) {
                continue;
            }
            JSONObject inputMap = op.optJSONObject("inputMap");
            if (inputMap == null) {
                continue;
            }
            String name = inputMap.optString(MetaOperation.SAVEFILENAME, "").trim();
            if (TextUtils.isEmpty(name)) {
                continue;
            }
            refs.add(name);
            if (!name.contains(".")) {
                refs.add(name + ".png");
            }
        }
        return refs;
    }

    private int cleanupUnusedTaskImages(JSONArray operationsArray) {
        File currentTaskDir = host.getCurrentTaskDir();
        if (currentTaskDir == null) {
            return 0;
        }
        try {
            Set<String> refs = collectReferencedImageFiles(operationsArray);
            File imgDir = new File(currentTaskDir, "img");
            if (!imgDir.exists()) {
                return 0;
            }

            int deleted = 0;
            File[] files = imgDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String name = file.getName();
                    if (TextUtils.isEmpty(name)) {
                        continue;
                    }
                    String lower = name.toLowerCase(Locale.ROOT);
                    boolean isImage = lower.endsWith(".png") || lower.endsWith(".jpg")
                            || lower.endsWith(".jpeg") || lower.endsWith(".webp");
                    if (!isImage) {
                        continue;
                    }
                    if (!refs.contains(name) && file.delete()) {
                        deleted++;
                    }
                }
            }

            File manifestFile = new File(imgDir, "manifest.json");
            if (manifestFile.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String content = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
                if (!TextUtils.isEmpty(content.trim())) {
                    JSONObject manifest = new JSONObject(content);
                    List<String> keys = new ArrayList<>();
                    java.util.Iterator<String> it = manifest.keys();
                    while (it.hasNext()) {
                        keys.add(it.next());
                    }
                    boolean changed = false;
                    for (String key : keys) {
                        if (!refs.contains(key)) {
                            manifest.remove(key);
                            changed = true;
                        }
                    }
                    if (changed) {
                        try (FileWriter writer = new FileWriter(manifestFile)) {
                            writer.write(manifest.toString(2));
                        }
                    }
                }
            }

            return deleted;
        } catch (Exception e) {
            Log.w(TAG, "清理未使用模板失败", e);
            return 0;
        }
    }
}
