package com.auto.master.floatwin;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages persistence and retrieval of {@link NodeFloatButtonConfig} entries.
 *
 * <p>Each config is stored as an individual JSON file inside the owning task's directory:
 * {@code projects/<projectName>/<taskName>/node_buttons/<operationId>.json}
 *
 * <p>This mirrors how {@code img/} and {@code gesture/} assets live inside task folders,
 * so project export/import (which zips the entire {@code projects/} tree) is complete
 * with no data loss.
 *
 * <p>A one-time migration from the old flat {@code node_float_buttons.json} file is
 * performed on first construction if that legacy file still exists.
 */
public class NodeFloatButtonManager {

    private static final String SUBDIR = "node_buttons";
    private static final String LEGACY_FILE = "node_float_buttons.json";

    private final File projectsRoot;
    private final Gson gson = new Gson();
    /** In-memory map keyed by operationId. */
    private final Map<String, NodeFloatButtonConfig> configs = new HashMap<>();

    public NodeFloatButtonManager(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        projectsRoot = new File(base, "projects");
        load();
        migrateLegacy(base);
    }

    // ── Public API (unchanged from old version) ───────────────────────────────

    public void saveConfig(NodeFloatButtonConfig config) {
        configs.put(config.operationId, config);
        persistOne(config);
    }

    public void removeConfig(String operationId) {
        NodeFloatButtonConfig cfg = configs.remove(operationId);
        if (cfg != null) deleteFile(cfg);
    }

    public NodeFloatButtonConfig getConfig(String operationId) {
        return configs.get(operationId);
    }

    public boolean hasConfig(String operationId) {
        return configs.containsKey(operationId);
    }

    /** Returns an unmodifiable snapshot of all saved configs. */
    public Map<String, NodeFloatButtonConfig> getAllConfigs() {
        return Collections.unmodifiableMap(configs);
    }

    // ── Storage helpers ───────────────────────────────────────────────────────

    private File configFile(NodeFloatButtonConfig cfg) {
        return new File(projectsRoot,
                cfg.projectName + File.separator
                + cfg.taskName   + File.separator
                + SUBDIR         + File.separator
                + cfg.operationId + ".json");
    }

    private void persistOne(NodeFloatButtonConfig cfg) {
        if (cfg.projectName == null || cfg.projectName.isEmpty()
                || cfg.taskName == null || cfg.taskName.isEmpty()) {
            return;
        }
        File f = configFile(cfg);
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            gson.toJson(cfg, w);
        } catch (Exception ignored) {}
    }

    private void deleteFile(NodeFloatButtonConfig cfg) {
        if (cfg.projectName == null || cfg.taskName == null) return;
        //noinspection ResultOfMethodCallIgnored
        configFile(cfg).delete();
    }

    /** Scans every task directory under {@code projects/} for {@code node_buttons/*.json}. */
    private void load() {
        if (!projectsRoot.exists()) return;
        File[] projects = projectsRoot.listFiles(File::isDirectory);
        if (projects == null) return;
        for (File proj : projects) {
            File[] tasks = proj.listFiles(File::isDirectory);
            if (tasks == null) continue;
            for (File task : tasks) {
                File btnDir = new File(task, SUBDIR);
                if (!btnDir.isDirectory()) continue;
                File[] files = btnDir.listFiles(
                        f -> f.isFile() && f.getName().endsWith(".json"));
                if (files == null) continue;
                for (File f : files) {
                    try (FileReader r = new FileReader(f)) {
                        NodeFloatButtonConfig cfg =
                                gson.fromJson(r, NodeFloatButtonConfig.class);
                        if (cfg != null && cfg.operationId != null) {
                            cfg.ensureDefaults();
                            configs.put(cfg.operationId, cfg);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    /**
     * One-time migration: reads the old flat {@code node_float_buttons.json},
     * writes each entry to its per-task file, then deletes the legacy file.
     */
    private void migrateLegacy(File baseDir) {
        File legacy = new File(baseDir, LEGACY_FILE);
        if (!legacy.exists()) return;
        try (FileReader r = new FileReader(legacy)) {
            Type type = new TypeToken<Map<String, NodeFloatButtonConfig>>() {}.getType();
            Map<String, NodeFloatButtonConfig> old = gson.fromJson(r, type);
            if (old != null) {
                for (NodeFloatButtonConfig cfg : old.values()) {
                    if (cfg != null && cfg.operationId != null
                            && cfg.projectName != null && !cfg.projectName.isEmpty()
                            && cfg.taskName    != null && !cfg.taskName.isEmpty()) {
                        cfg.ensureDefaults();
                        // Only migrate if not already loaded from per-task file
                        configs.putIfAbsent(cfg.operationId, cfg);
                        persistOne(cfg);
                    }
                }
            }
        } catch (Exception ignored) {}
        //noinspection ResultOfMethodCallIgnored
        legacy.delete();
    }
}
