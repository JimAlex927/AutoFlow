package com.auto.master.floatwin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Counts how many gesture nodes reference each task-local gesture file. */
final class GestureReferenceCounter {
    private static final int GESTURE_OPERATION_TYPE = 5;
    private static final String INPUT_MAP = "inputMap";
    private static final String SAVE_FILE_NAME = "SAVEFILENAME";
    private static final String GESTURE_TEMPLATE_ID = "GESTURE_TEMPLATE_ID";
    private static final String GESTURE_PIPELINE = "GESTURE_PIPELINE";
    private static final String GESTURE_STEP_FILE = "file";

    private GestureReferenceCounter() {
    }

    static Map<String, Integer> countFromOperationsJson(String json) {
        Map<String, Integer> usage = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return usage;
        }
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) {
            return usage;
        }
        JsonArray operations = root.getAsJsonArray();
        for (JsonElement element : operations) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject operation = element.getAsJsonObject();
            if (intValue(operation.get("type"), -1) != GESTURE_OPERATION_TYPE) {
                continue;
            }
            JsonObject input = objectValue(operation.get(INPUT_MAP));
            if (input == null) {
                continue;
            }
            Set<String> nodeReferences = new HashSet<>();
            addReference(nodeReferences, stringValue(input.get(GESTURE_TEMPLATE_ID)));
            addReference(nodeReferences, stringValue(input.get(SAVE_FILE_NAME)));
            JsonArray pipeline = arrayValue(input.get(GESTURE_PIPELINE));
            if (pipeline != null) {
                for (JsonElement stepElement : pipeline) {
                    JsonObject step = objectValue(stepElement);
                    if (step != null) {
                        addReference(nodeReferences, stringValue(step.get(GESTURE_STEP_FILE)));
                    }
                }
            }
            for (String fileName : nodeReferences) {
                usage.put(fileName, usage.getOrDefault(fileName, 0) + 1);
            }
        }
        return usage;
    }

    static String normalizeFileName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String name = rawName.trim();
        if (name.isEmpty()) {
            return "";
        }
        return name.toLowerCase().endsWith(".json") ? name : name + ".json";
    }

    private static void addReference(Set<String> references, String rawName) {
        String normalized = normalizeFileName(rawName);
        if (!normalized.isEmpty()) {
            references.add(normalized);
        }
    }

    private static JsonObject objectValue(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray arrayValue(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String stringValue(JsonElement element) {
        try {
            return element == null || element.isJsonNull() ? "" : element.getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int intValue(JsonElement element, int fallback) {
        try {
            return element == null || element.isJsonNull() ? fallback : element.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
