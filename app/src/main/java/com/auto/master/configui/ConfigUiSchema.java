package com.auto.master.configui;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConfigUiSchema {
    public String schemaId;
    public String name;
    public List<ConfigUiPage> pages = new ArrayList<>();
    public long updatedAt;

    public ConfigUiSchema() {}

    public void ensureDefaults() {
        if (name == null) {
            name = "";
        }
        if (pages == null) {
            pages = new ArrayList<>();
        }
        if (pages.isEmpty()) {
            pages.add(ConfigUiPage.createDefault("页面 1"));
        }
        for (int i = 0; i < pages.size(); i++) {
            ConfigUiPage page = pages.get(i);
            if (page == null) {
                page = ConfigUiPage.createDefault("页面 " + (i + 1));
                pages.set(i, page);
            }
            page.ensureDefaults();
            if (TextUtils.isEmpty(page.title)) {
                page.title = "页面 " + (i + 1);
            }
        }
    }

    public Set<String> collectFieldKeys() {
        ensureDefaults();
        Set<String> keys = new LinkedHashSet<>();
        for (ConfigUiPage page : pages) {
            if (page == null || page.components == null) {
                continue;
            }
            for (ConfigUiComponent component : page.components) {
                if (component == null) {
                    continue;
                }
                component.ensureDefaults();
                if (component.bindsValue()) {
                    keys.add(component.fieldKey.trim());
                }
            }
        }
        return keys;
    }

    public static ConfigUiSchema createDefault(String schemaId, String name) {
        ConfigUiSchema schema = new ConfigUiSchema();
        schema.schemaId = schemaId;
        schema.name = name;
        schema.pages = new ArrayList<>();
        schema.pages.add(ConfigUiPage.createDefault("页面 1"));
        schema.updatedAt = System.currentTimeMillis();
        schema.ensureDefaults();
        return schema;
    }
}
