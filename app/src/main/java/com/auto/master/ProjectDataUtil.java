package com.auto.master;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Utilities for creating and managing sample Projects data on disk. */
public class ProjectDataUtil {
    public static boolean createSampleProject(Context ctx, String projectName) {
        if (ctx == null || projectName == null || projectName.trim().isEmpty()) return false;
        File root = getProjectsRoot(ctx);
        if (!root.exists()) {
            if (!root.mkdirs()) return false;
        }
        File projectDir = new File(root, projectName.trim());
        if (projectDir.exists()) return false; // avoid overwrite
        if (!projectDir.mkdirs()) return false;
        String[] tasks = {"Task01", "Task02"};
        for (String t : tasks) {
            File dir = new File(projectDir, t);
            if (!dir.mkdirs()) return false;
            File json = new File(dir, "operations.json");
            String content = "[ {\"name\":\"Operation1\",\"type\":\"click\"}, {\"name\":\"Operation2\",\"type\":\"sleep\",\"duration\":2000} ]";
            try (FileOutputStream fos = new FileOutputStream(json)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                return false;
            }
            new File(dir, "assets").mkdirs();
        }
        return true;
    }

    public static boolean projectExists(Context ctx, String projectName) {
        if (ctx == null || projectName == null) return false;
        File root = new File(ctx.getFilesDir(), "projects");
        File projectDir = new File(root, projectName.trim());
        return projectDir.exists();
    }

    // Returns the root directory for projects, using external storage when available
    public static File getProjectsRoot(Context ctx) {
        File ext = ctx.getExternalFilesDir(null);
        if (ext != null) {
            File root = new File(ext, "projects");
            if (!root.exists()) {
                root.mkdirs();
            }
            return root;
        }
        // Fallback to internal storage if external not available
        File internal = new File(ctx.getFilesDir(), "projects");
        if (!internal.exists()) {
            internal.mkdirs();
        }
        return internal;
    }

    // Ensure sample data exists under the appropriate root
    public static void ensureSampleData(Context ctx) {
        File root = getProjectsRoot(ctx);
        if (!root.exists()) {
            root.mkdirs();
        }
        // Check a simple marker: if a known directory is missing, recreate samples
        File sampleMarker = new File(root, "ProjectA");
        if (!sampleMarker.exists()) {
            createSampleProject(ctx, "ProjectA");
            createSampleProject(ctx, "ProjectB");
        }
    }
}
