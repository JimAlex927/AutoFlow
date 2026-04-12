package com.auto.master.configui;

import android.text.TextUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ConfigUiValueCodec {
    private ConfigUiValueCodec() {}

    public static LinkedHashMap<String, String> parse(String raw) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (TextUtils.isEmpty(raw)) {
            return result;
        }
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    public static LinkedHashMap<String, String> merge(
            Map<String, String> baseMap,
            Map<String, String> formValues,
            Set<String> schemaFields) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        if (baseMap != null) {
            merged.putAll(baseMap);
        }
        if (schemaFields != null) {
            for (String key : schemaFields) {
                if (!TextUtils.isEmpty(key)) {
                    merged.remove(key);
                }
            }
        }
        if (formValues != null) {
            for (Map.Entry<String, String> entry : formValues.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                    merged.put(key.trim(), value.trim());
                }
            }
        }
        return merged;
    }

    public static String encode(Map<String, String> valueMap) {
        if (valueMap == null || valueMap.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : valueMap.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(key).append('=').append(value);
        }
        return builder.toString();
    }
}
