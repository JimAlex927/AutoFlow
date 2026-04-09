package com.auto.master.homepanel;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class HomeProjectRepository {

    public static final class ProjectSummary {
        public final File dir;
        public final String name;
        public final int taskCount;
        public final int runnableTaskCount;
        public final long lastModified;

        ProjectSummary(File dir, int taskCount, int runnableTaskCount) {
            this.dir = dir;
            this.name = dir.getName();
            this.taskCount = taskCount;
            this.runnableTaskCount = runnableTaskCount;
            this.lastModified = dir.lastModified();
        }
    }

    public static final class TaskSummary {
        public final File dir;
        public final String name;
        public final int operationCount;
        public final int assetCount;
        public final long lastModified;

        TaskSummary(File dir, int operationCount, int assetCount) {
            this.dir = dir;
            this.name = dir.getName();
            this.operationCount = operationCount;
            this.assetCount = assetCount;
            this.lastModified = dir.lastModified();
        }
    }

    public static final class OperationSummary {
        public final String id;
        public final String name;
        public final String type;
        public final int index;
        public final String summary;

        OperationSummary(String id, String name, String type, int index, String summary) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.index = index;
            this.summary = summary;
        }
    }

    public File getProjectRoot(Context context) {
        File root = new File(context.getExternalFilesDir(null), "projects");
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

    public List<ProjectSummary> loadProjects(Context context) {
        File[] dirs = getProjectRoot(context).listFiles(File::isDirectory);
        List<ProjectSummary> items = new ArrayList<>();
        if (dirs == null) {
            return items;
        }
        Arrays.sort(dirs, Comparator.comparingLong(File::lastModified).reversed());
        for (File dir : dirs) {
            File[] taskDirs = dir.listFiles(File::isDirectory);
            int taskCount = taskDirs == null ? 0 : taskDirs.length;
            int runnableTaskCount = 0;
            if (taskDirs != null) {
                for (File taskDir : taskDirs) {
                    if (new File(taskDir, "operations.json").exists()) {
                        runnableTaskCount++;
                    }
                }
            }
            items.add(new ProjectSummary(dir, taskCount, runnableTaskCount));
        }
        return items;
    }

    public List<TaskSummary> loadTasks(File projectDir) {
        List<TaskSummary> items = new ArrayList<>();
        if (projectDir == null || !projectDir.isDirectory()) {
            return items;
        }
        File[] dirs = projectDir.listFiles(File::isDirectory);
        if (dirs == null) {
            return items;
        }
        Arrays.sort(dirs, Comparator.comparingLong(File::lastModified).reversed());
        for (File dir : dirs) {
            items.add(new TaskSummary(dir, countOperations(dir), countAssets(dir)));
        }
        return items;
    }

    public List<OperationSummary> loadOperations(File taskDir) {
        List<OperationSummary> items = new ArrayList<>();
        if (taskDir == null || !taskDir.isDirectory()) {
            return items;
        }
        File operationsFile = new File(taskDir, "operations.json");
        if (!operationsFile.exists()) {
            return items;
        }
        try {
            String content = readFile(operationsFile);
            JSONArray arr = new JSONArray(content);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                String id = obj.optString("id", "");
                String name = obj.optString("name", "未命名节点");
                String type = obj.optString("type", "unknown");
                items.add(new OperationSummary(id, name, type, i, buildOperationSummary(obj)));
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    public File createProject(Context context, String projectName) {
        String safeName = safeName(projectName);
        if (TextUtils.isEmpty(safeName)) {
            return null;
        }
        File dir = new File(getProjectRoot(context), safeName);
        if (dir.exists()) {
            return null;
        }
        return dir.mkdirs() ? dir : null;
    }

    public File createTask(File projectDir, String taskName) {
        if (projectDir == null || !projectDir.isDirectory()) {
            return null;
        }
        String safeName = safeName(taskName);
        if (TextUtils.isEmpty(safeName)) {
            return null;
        }
        File dir = new File(projectDir, safeName);
        if (dir.exists()) {
            return null;
        }
        if (!dir.mkdirs()) {
            return null;
        }
        File operationsFile = new File(dir, "operations.json");
        if (!operationsFile.exists()) {
            try {
                Files.write(operationsFile.toPath(), "[]".getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        return dir;
    }

    public boolean rename(File dir, String newName) {
        if (dir == null || !dir.exists()) {
            return false;
        }
        String safeName = safeName(newName);
        if (TextUtils.isEmpty(safeName) || TextUtils.equals(dir.getName(), safeName)) {
            return false;
        }
        File target = new File(dir.getParentFile(), safeName);
        return !target.exists() && dir.renameTo(target);
    }

    public boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    public boolean createOperation(File taskDir, String name, String type) {
        if (taskDir == null || !taskDir.isDirectory()) {
            return false;
        }
        try {
            JSONArray arr = readOperationsArray(taskDir);
            JSONObject obj = new JSONObject();
            obj.put("id", generateOperationId(arr.length()));
            obj.put("name", TextUtils.isEmpty(name) ? "未命名节点" : name.trim());
            obj.put("type", TextUtils.isEmpty(type) ? "click" : type.trim().toLowerCase(Locale.ROOT));
            applyDefaultFields(obj);
            arr.put(obj);
            writeOperationsArray(taskDir, arr);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public String loadOperationJson(File taskDir, int index) {
        try {
            JSONArray arr = readOperationsArray(taskDir);
            JSONObject obj = arr.optJSONObject(index);
            return obj == null ? null : obj.toString(2);
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean updateOperationJson(File taskDir, int index, String rawJson) {
        if (taskDir == null || !taskDir.isDirectory() || TextUtils.isEmpty(rawJson)) {
            return false;
        }
        try {
            JSONArray arr = readOperationsArray(taskDir);
            if (index < 0 || index >= arr.length()) {
                return false;
            }
            JSONObject oldObj = arr.optJSONObject(index);
            JSONObject newObj = new JSONObject(rawJson);
            if (TextUtils.isEmpty(newObj.optString("id"))) {
                newObj.put("id", oldObj != null ? oldObj.optString("id", generateOperationId(index)) : generateOperationId(index));
            }
            if (TextUtils.isEmpty(newObj.optString("name"))) {
                newObj.put("name", oldObj != null ? oldObj.optString("name", "未命名节点") : "未命名节点");
            }
            if (TextUtils.isEmpty(newObj.optString("type"))) {
                newObj.put("type", oldObj != null ? oldObj.optString("type", "click") : "click");
            }
            arr.put(index, newObj);
            writeOperationsArray(taskDir, arr);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean renameOperation(File taskDir, int index, String newName) {
        if (TextUtils.isEmpty(newName)) {
            return false;
        }
        try {
            JSONArray arr = readOperationsArray(taskDir);
            JSONObject obj = arr.optJSONObject(index);
            if (obj == null) {
                return false;
            }
            obj.put("name", newName.trim());
            arr.put(index, obj);
            writeOperationsArray(taskDir, arr);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean duplicateOperation(File taskDir, int index) {
        try {
            JSONArray arr = readOperationsArray(taskDir);
            JSONObject obj = arr.optJSONObject(index);
            if (obj == null) {
                return false;
            }
            JSONObject copy = new JSONObject(obj.toString());
            copy.put("id", generateOperationId(arr.length()));
            copy.put("name", obj.optString("name", "未命名节点") + "_Copy");
            arr.put(index + 1, copy);
            writeOperationsArray(taskDir, arr);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean deleteOperation(File taskDir, int index) {
        try {
            JSONArray arr = readOperationsArray(taskDir);
            if (index < 0 || index >= arr.length()) {
                return false;
            }
            JSONArray next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (i != index) {
                    next.put(arr.opt(i));
                }
            }
            writeOperationsArray(taskDir, next);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean moveOperation(File taskDir, int index, int direction) {
        try {
            JSONArray arr = readOperationsArray(taskDir);
            int target = index + direction;
            if (index < 0 || index >= arr.length() || target < 0 || target >= arr.length()) {
                return false;
            }
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                items.add(arr.opt(i));
            }
            Object temp = items.get(index);
            items.set(index, items.get(target));
            items.set(target, temp);
            JSONArray next = new JSONArray();
            for (Object item : items) {
                next.put(item);
            }
            writeOperationsArray(taskDir, next);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int countOperations(File taskDir) {
        File operationsFile = new File(taskDir, "operations.json");
        if (!operationsFile.exists()) {
            return 0;
        }
        try {
            JSONArray arr = new JSONArray(readFile(operationsFile));
            return arr.length();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int countAssets(File taskDir) {
        File[] files = taskDir.listFiles(file -> file.isFile() && !"operations.json".equals(file.getName()));
        return files == null ? 0 : files.length;
    }

    private String buildOperationSummary(JSONObject obj) {
        String type = obj.optString("type", "unknown");
        if ("sleep".equalsIgnoreCase(type)) {
            return "等待 " + obj.optLong("duration", 0L) + "ms";
        }
        if ("click".equalsIgnoreCase(type)) {
            return "点击节点";
        }
        if ("ocr".equalsIgnoreCase(type)) {
            return "OCR 识别";
        }
        if ("swipe".equalsIgnoreCase(type)) {
            return "滑动节点";
        }
        if ("input".equalsIgnoreCase(type)) {
            return "输入节点";
        }
        return "类型: " + type;
    }

    private void applyDefaultFields(JSONObject obj) throws Exception {
        String type = obj.optString("type", "click").toLowerCase(Locale.ROOT);
        if ("sleep".equals(type) && !obj.has("duration")) {
            obj.put("duration", 1000);
        }
        if ("input".equals(type) && !obj.has("text")) {
            obj.put("text", "");
        }
        if ("click".equals(type) && !obj.has("bbox")) {
            obj.put("bbox", "");
        }
    }

    private JSONArray readOperationsArray(File taskDir) throws Exception {
        File operationsFile = new File(taskDir, "operations.json");
        if (!operationsFile.exists()) {
            Files.write(operationsFile.toPath(), "[]".getBytes(StandardCharsets.UTF_8));
        }
        return new JSONArray(readFile(operationsFile));
    }

    private void writeOperationsArray(File taskDir, JSONArray arr) throws Exception {
        File operationsFile = new File(taskDir, "operations.json");
        Files.write(operationsFile.toPath(), arr.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private String readFile(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private String generateOperationId(int seed) {
        return "op_" + System.currentTimeMillis() + "_" + seed;
    }

    private String safeName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replace("/", "_").replace("\\", "_");
    }
}
