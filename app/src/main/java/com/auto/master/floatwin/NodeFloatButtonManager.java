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
 * Configs are saved as a single JSON file alongside project data.
 */
public class NodeFloatButtonManager {
    private static final String FILE_NAME = "node_float_buttons.json";

    private final File configFile;
    private final Gson gson = new Gson();
    private Map<String, NodeFloatButtonConfig> configs = new HashMap<>();

    public NodeFloatButtonManager(Context context) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) dir = context.getFilesDir();
        configFile = new File(dir, FILE_NAME);
        load();
    }

    public void saveConfig(NodeFloatButtonConfig config) {
        configs.put(config.operationId, config);
        persist();
    }

    public void removeConfig(String operationId) {
        configs.remove(operationId);
        persist();
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

    private void load() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<Map<String, NodeFloatButtonConfig>>() {}.getType();
            Map<String, NodeFloatButtonConfig> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                configs = new HashMap<>(loaded);
                for (NodeFloatButtonConfig cfg : configs.values()) {
                    if (cfg != null) cfg.ensureDefaults();
                }
            }
        } catch (Exception ignored) {}
    }

    private void persist() {
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(configs, writer);
        } catch (Exception ignored) {}
    }
}
