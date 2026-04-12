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

public class ConfigUiStore {
    private static final String FILE_NAME = "config_ui_schemas.json";

    private final File storeFile;
    private final Gson gson = new Gson();
    private final Type mapType = new TypeToken<Map<String, ConfigUiSchema>>() {}.getType();
    private Map<String, ConfigUiSchema> schemaMap = new HashMap<>();

    public ConfigUiStore(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        storeFile = new File(dir, FILE_NAME);
        load();
    }

    public synchronized ConfigUiSchema getSchema(String schemaId) {
        ConfigUiSchema schema = schemaMap.get(schemaId);
        if (schema != null) {
            schema.ensureDefaults();
        }
        return schema;
    }

    public synchronized void saveSchema(ConfigUiSchema schema) {
        if (schema == null || schema.schemaId == null || schema.schemaId.trim().isEmpty()) {
            return;
        }
        schema.updatedAt = System.currentTimeMillis();
        schema.ensureDefaults();
        schemaMap.put(schema.schemaId, schema);
        persist();
    }

    private void load() {
        if (!storeFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(storeFile)) {
            Map<String, ConfigUiSchema> loaded = gson.fromJson(reader, mapType);
            if (loaded != null) {
                schemaMap = new HashMap<>(loaded);
                for (ConfigUiSchema schema : schemaMap.values()) {
                    if (schema != null) {
                        schema.ensureDefaults();
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        try (FileWriter writer = new FileWriter(storeFile)) {
            gson.toJson(schemaMap, mapType, writer);
        } catch (Exception ignored) {
        }
    }
}
