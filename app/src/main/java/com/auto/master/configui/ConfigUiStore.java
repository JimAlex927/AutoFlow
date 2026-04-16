package com.auto.master.configui;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists {@link ConfigUiSchema} objects one-file-per-schema inside the owning task directory:
 * {@code projects/<projectName>/<taskName>/config_ui/<schemaId>.json}
 *
 * <p>This mirrors how {@code img/} and {@code gesture/} live inside task folders,
 * so project export/import (which zips the entire {@code projects/} tree) automatically
 * includes all ConfigUI schemas with no extra packaging logic.
 *
 * <p>A one-time migration from the old flat {@code config_ui_schemas.json} file is
 * performed on first construction if that legacy file still exists.
 */
public class ConfigUiStore {

    private static final String SUBDIR = "config_ui";
    private static final String LEGACY_FILE = "config_ui_schemas.json";

    private final File projectsRoot;
    private final Gson gson = new Gson();
    private final Map<String, ConfigUiSchema> schemaMap = new HashMap<>();

    public ConfigUiStore(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        projectsRoot = new File(base, "projects");
        load();
        migrateLegacy(base);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public synchronized ConfigUiSchema getSchema(String schemaId) {
        ConfigUiSchema schema = schemaMap.get(schemaId);
        if (schema != null) schema.ensureDefaults();
        return schema;
    }

    public synchronized void saveSchema(ConfigUiSchema schema) {
        if (schema == null || schema.schemaId == null || schema.schemaId.trim().isEmpty()) {
            return;
        }
        if (isBlank(schema.projectName) || isBlank(schema.taskName)) {
            return;
        }
        schema.updatedAt = System.currentTimeMillis();
        schema.ensureDefaults();
        schemaMap.put(schema.schemaId, schema);
        persistOne(schema);
    }

    // ── Storage helpers ───────────────────────────────────────────────────────

    private File schemaFile(ConfigUiSchema schema) {
        return new File(projectsRoot,
                schema.projectName + File.separator
                + schema.taskName  + File.separator
                + SUBDIR           + File.separator
                + schema.schemaId  + ".json");
    }

    private void persistOne(ConfigUiSchema schema) {
        if (schema.projectName == null || schema.projectName.isEmpty()
                || schema.taskName == null || schema.taskName.isEmpty()) {
            return;
        }
        File f = schemaFile(schema);
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            gson.toJson(schema, w);
        } catch (Exception ignored) {}
    }

    /** Scans every task directory under {@code projects/} for {@code config_ui/*.json}. */
    private void load() {
        if (!projectsRoot.exists()) return;
        File[] projects = projectsRoot.listFiles(File::isDirectory);
        if (projects == null) return;
        for (File proj : projects) {
            File[] tasks = proj.listFiles(File::isDirectory);
            if (tasks == null) continue;
            for (File task : tasks) {
                File cfgDir = new File(task, SUBDIR);
                if (!cfgDir.isDirectory()) continue;
                File[] files = cfgDir.listFiles(
                        f -> f.isFile() && f.getName().endsWith(".json"));
                if (files == null) continue;
                for (File f : files) {
                    try (FileReader r = new FileReader(f)) {
                        ConfigUiSchema schema = gson.fromJson(r, ConfigUiSchema.class);
                        if (schema != null && schema.schemaId != null) {
                            boolean ownershipPatched = false;
                            if (isBlank(schema.projectName)) {
                                schema.projectName = proj.getName();
                                ownershipPatched = true;
                            }
                            if (isBlank(schema.taskName)) {
                                schema.taskName = task.getName();
                                ownershipPatched = true;
                            }
                            schema.ensureDefaults();
                            schemaMap.put(schema.schemaId, schema);
                            if (ownershipPatched) {
                                persistOne(schema);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    /**
     * One-time migration: reads the old flat {@code config_ui_schemas.json},
     * writes each entry that has projectName+taskName to its per-task file,
     * then deletes the legacy file.
     */
    private void migrateLegacy(File baseDir) {
        File legacy = new File(baseDir, LEGACY_FILE);
        if (!legacy.exists()) return;
        try (FileReader r = new FileReader(legacy)) {
            Type mapType = new TypeToken<Map<String, ConfigUiSchema>>() {}.getType();
            Map<String, ConfigUiSchema> old = gson.fromJson(r, mapType);
            if (old != null) {
                for (ConfigUiSchema schema : old.values()) {
                    if (schema != null && schema.schemaId != null
                            && schema.projectName != null && !schema.projectName.isEmpty()
                            && schema.taskName    != null && !schema.taskName.isEmpty()) {
                        schema.ensureDefaults();
                        schemaMap.putIfAbsent(schema.schemaId, schema);
                        persistOne(schema);
                    }
                }
            }
        } catch (Exception ignored) {}
        //noinspection ResultOfMethodCallIgnored
        legacy.delete();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
