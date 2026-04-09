package com.auto.master.floatwin;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文件IO管理器 - 统一处理所有文件操作，避免主线程IO
 * 所有文件读写操作都在后台线程执行，通过回调返回结果到主线程
 */
public class FileIOManager {
    
    private static final String TAG = "FileIOManager";
    private final ExecutorService ioExecutor;
    private final Handler mainHandler;
    
    public FileIOManager() {
        // 使用固定大小的线程池，避免创建过多线程
        this.ioExecutor = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * 异步读取文件内容
     */
    public void readFileAsync(File file, FileReadCallback callback) {
        if (file == null || !file.exists()) {
            mainHandler.post(() -> callback.onResult(null, new IOException("File not found")));
            return;
        }
        
        ioExecutor.execute(() -> {
            try {
                String content = readFileSync(file);
                mainHandler.post(() -> callback.onResult(content, null));
            } catch (Exception e) {
                Log.e(TAG, "Failed to read file: " + file.getAbsolutePath(), e);
                mainHandler.post(() -> callback.onResult(null, e));
            }
        });
    }
    
    /**
     * 同步读取文件（仅在后台线程调用）
     */
    private String readFileSync(File file) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return new String(bytes, StandardCharsets.UTF_8);
        } else {
            // 兼容旧版本
            StringBuilder sb = new StringBuilder();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        }
    }
    
    /**
     * 异步写入文件
     */
    public void writeFileAsync(File file, String content, FileWriteCallback callback) {
        if (file == null) {
            mainHandler.post(() -> callback.onResult(false, new IOException("File is null")));
            return;
        }
        
        ioExecutor.execute(() -> {
            try {
                writeFileSync(file, content);
                mainHandler.post(() -> callback.onResult(true, null));
            } catch (Exception e) {
                Log.e(TAG, "Failed to write file: " + file.getAbsolutePath(), e);
                mainHandler.post(() -> callback.onResult(false, e));
            }
        });
    }
    
    /**
     * 同步写入文件（仅在后台线程调用）
     */
    private void writeFileSync(File file, String content) throws IOException {
        // 确保父目录存在
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
    
    /**
     * 异步列出目录文件
     */
    public void listFilesAsync(File directory, FileListCallback callback) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            mainHandler.post(() -> callback.onResult(new File[0], null));
            return;
        }
        
        ioExecutor.execute(() -> {
            try {
                File[] files = directory.listFiles();
                File[] result = files != null ? files : new File[0];
                mainHandler.post(() -> callback.onResult(result, null));
            } catch (Exception e) {
                Log.e(TAG, "Failed to list files: " + directory.getAbsolutePath(), e);
                mainHandler.post(() -> callback.onResult(new File[0], e));
            }
        });
    }
    
    /**
     * 异步递归删除文件/目录
     */
    public void deleteRecursivelyAsync(File file, DeleteCallback callback) {
        if (file == null) {
            mainHandler.post(() -> callback.onResult(false, new IOException("File is null")));
            return;
        }
        
        ioExecutor.execute(() -> {
            try {
                boolean success = deleteRecursivelySync(file);
                mainHandler.post(() -> callback.onResult(success, null));
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete: " + file.getAbsolutePath(), e);
                mainHandler.post(() -> callback.onResult(false, e));
            }
        });
    }
    
    /**
     * 同步递归删除（仅在后台线程调用）
     */
    private boolean deleteRecursivelySync(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursivelySync(child)) {
                        return false;
                    }
                }
            }
        }
        
        return file.delete();
    }
    
    /**
     * 关闭IO管理器，释放资源
     */
    public void shutdown() {
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
        }
    }
    
    // ========== 回调接口 ==========
    
    public interface FileReadCallback {
        void onResult(String content, Exception error);
    }
    
    public interface FileWriteCallback {
        void onResult(boolean success, Exception error);
    }
    
    public interface FileListCallback {
        void onResult(File[] files, Exception error);
    }
    
    public interface DeleteCallback {
        void onResult(boolean success, Exception error);
    }
}
