package com.auto.master.importer;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class ScriptPackageManager {
    private static final int BUFFER = 8192;

    private ScriptPackageManager() {
    }

    public static class ImportResult {
        public int importedProjects;
        public int skippedEntries;
        public String message;
    }

    /**
     * Export a single project as a zip archive.
     * The archive preserves the same structure as exportAllProjects so it can be
     * imported with importFromUri without any changes.
     */
    public static File exportProject(Context context, String projectName) throws Exception {
        File projectsRoot = new File(context.getExternalFilesDir(null), "projects");
        File projectDir = new File(projectsRoot, projectName);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("项目不存在: " + projectName);
        }
        File exportDir = new File(context.getExternalFilesDir(null), "exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        String safeName = projectName.replaceAll("[^\\w\\-]", "_");
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File outZip = new File(exportDir, safeName + "_" + ts + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outZip)))) {
            zipDir(projectDir, projectsRoot, zos);
        }
        return outZip;
    }

    public static File exportAllProjects(Context context) throws Exception {
        File projectsRoot = new File(context.getExternalFilesDir(null), "projects");
        if (!projectsRoot.exists()) {
            projectsRoot.mkdirs();
        }
        File exportDir = new File(context.getExternalFilesDir(null), "exports");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File outZip = new File(exportDir, "scripts_" + ts + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outZip)))) {
            zipDir(projectsRoot, projectsRoot.getParentFile(), zos);
        }
        return outZip;
    }

    public static ImportResult importFromUri(Context context, Uri uri) throws Exception {
        ImportResult result = new ImportResult();
        if (context == null || uri == null) {
            result.message = "无效导入参数";
            return result;
        }

        File tempDir = new File(context.getCacheDir(), "import_tmp_" + System.currentTimeMillis());
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File tempZip = new File(tempDir, "package.zip");

        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = resolver.openInputStream(uri);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(tempZip))) {
            if (in == null) {
                throw new IllegalStateException("无法读取导入文件");
            }
            copy(in, out);
        }

        File unzipDir = new File(tempDir, "unzipped");
        unzipSecure(tempZip, unzipDir);

        File targetProjects = new File(context.getExternalFilesDir(null), "projects");
        if (!targetProjects.exists()) {
            targetProjects.mkdirs();
        }

        List<File> projectDirs = findImportProjectDirs(unzipDir);
        if (projectDirs.isEmpty()) {
            result.message = "压缩包内容为空";
            deleteRecursively(tempDir);
            return result;
        }

        for (File entry : projectDirs) {
            if (entry == null || !entry.exists() || !entry.isDirectory()) {
                result.skippedEntries++;
                continue;
            }
            String name = entry.getName();
            if (TextUtils.isEmpty(name)) {
                result.skippedEntries++;
                continue;
            }
            File dst = new File(targetProjects, name);
            if (dst.exists()) {
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                dst = new File(targetProjects, name + "_import_" + ts);
            }
            copyDir(entry, dst);
            result.importedProjects++;
        }

        deleteRecursively(tempDir);
        result.message = "导入完成";
        return result;
    }

    private static List<File> findImportProjectDirs(File root) throws Exception {
        Map<String, File> ordered = new LinkedHashMap<>();
        collectProjectDirs(root, ordered, 0);
        return new ArrayList<>(ordered.values());
    }

    private static void collectProjectDirs(File dir, Map<String, File> out, int depth) throws Exception {
        if (dir == null || !dir.exists() || !dir.isDirectory() || depth > 6) {
            return;
        }

        if ("projects".equalsIgnoreCase(dir.getName())) {
            File[] children = dir.listFiles(File::isDirectory);
            if (children != null) {
                for (File child : children) {
                    if (looksLikeProjectDir(child)) {
                        out.put(child.getCanonicalPath(), child);
                    } else {
                        collectProjectDirs(child, out, depth + 1);
                    }
                }
            }
            return;
        }

        if (looksLikeProjectDir(dir)) {
            out.put(dir.getCanonicalPath(), dir);
            return;
        }

        File[] children = dir.listFiles(File::isDirectory);
        if (children == null || children.length == 0) {
            return;
        }
        for (File child : children) {
            collectProjectDirs(child, out, depth + 1);
        }
    }

    private static boolean looksLikeProjectDir(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        File[] taskDirs = dir.listFiles(File::isDirectory);
        if (taskDirs == null || taskDirs.length == 0) {
            return false;
        }
        for (File taskDir : taskDirs) {
            File operationsFile = new File(taskDir, "operations.json");
            if (operationsFile.isFile()) {
                return true;
            }
        }
        return false;
    }

    private static void zipDir(File src, File baseParent, ZipOutputStream zos) throws Exception {
        if (src == null || !src.exists()) {
            return;
        }
        List<File> stack = new ArrayList<>();
        stack.add(src);
        while (!stack.isEmpty()) {
            File current = stack.remove(stack.size() - 1);
            File[] children = current.listFiles();
            if (children == null || children.length == 0) {
                continue;
            }
            for (File child : children) {
                if (child.isDirectory()) {
                    stack.add(child);
                } else {
                    String rel = child.getAbsolutePath().substring(baseParent.getAbsolutePath().length() + 1)
                            .replace('\\', '/');
                    ZipEntry entry = new ZipEntry(rel);
                    zos.putNextEntry(entry);
                    try (InputStream in = new BufferedInputStream(new FileInputStream(child))) {
                        copy(in, zos);
                    }
                    zos.closeEntry();
                }
            }
        }
    }

    private static void unzipSecure(File zipFile, File outDir) throws Exception {
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        String outCanonical = outDir.getCanonicalPath() + File.separator;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (TextUtils.isEmpty(name) || name.startsWith("__MACOSX/")) {
                    zis.closeEntry();
                    continue;
                }
                File target = new File(outDir, name);
                String canonical = target.getCanonicalPath();
                if (!canonical.startsWith(outCanonical)) {
                    zis.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                        copy(zis, out);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void copyDir(File src, File dst) throws Exception {
        if (!dst.exists()) {
            dst.mkdirs();
        }
        File[] children = src.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File out = new File(dst, child.getName());
            if (child.isDirectory()) {
                copyDir(child, out);
            } else {
                try (InputStream in = new BufferedInputStream(new FileInputStream(child));
                     OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    copy(in, os);
                }
            }
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[BUFFER];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
