package com.auto.master.configui;

import android.text.TextUtils;

import com.auto.master.utils.UUIDGenerator;

import java.util.ArrayList;
import java.util.List;

public class ConfigUiPage {
    public String id;
    public String title;
    public int canvasHeightDp;
    public int scalePercent;
    public List<ConfigUiComponent> components = new ArrayList<>();

    public ConfigUiPage() {}

    public void ensureDefaults() {
        if (TextUtils.isEmpty(id)) {
            id = UUIDGenerator.prefixedUUID("cfgui_page");
        }
        if (title == null) {
            title = "";
        }
        if (canvasHeightDp <= 0) {
            canvasHeightDp = 560;
        }
        if (scalePercent <= 0) {
            scalePercent = 100;
        }
        if (components == null) {
            components = new ArrayList<>();
        }
        for (ConfigUiComponent component : components) {
            if (component != null) {
                component.ensureDefaults();
            }
        }
    }

    public static ConfigUiPage createDefault(String title) {
        ConfigUiPage page = new ConfigUiPage();
        page.id = UUIDGenerator.prefixedUUID("cfgui_page");
        page.title = title;
        page.ensureDefaults();
        return page;
    }
}
