package com.auto.master.scheduler;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Persistent storage for AppNotificationTrigger objects.
 * Uses SharedPreferences + Gson, matching the pattern of TaskSchedulerStore.
 */
public final class TriggerStore {

    private static final String SP_NAME = "app_notification_trigger_store";
    private static final String KEY_JSON = "triggers_json";
    private static final Gson GSON = new Gson();

    private TriggerStore() {
    }

    public static synchronized List<AppNotificationTrigger> getAll(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_JSON, "[]");
        try {
            Type type = new TypeToken<List<AppNotificationTrigger>>() {}.getType();
            List<AppNotificationTrigger> list = GSON.fromJson(json, type);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public static synchronized void upsert(Context context, AppNotificationTrigger trigger) {
        if (trigger == null || TextUtils.isEmpty(trigger.id)) return;
        List<AppNotificationTrigger> all = getAll(context);
        boolean updated = false;
        for (int i = 0; i < all.size(); i++) {
            if (trigger.id.equals(all.get(i).id)) {
                all.set(i, trigger);
                updated = true;
                break;
            }
        }
        if (!updated) all.add(trigger);
        saveAll(context, all);
    }

    public static synchronized void remove(Context context, String id) {
        if (TextUtils.isEmpty(id)) return;
        List<AppNotificationTrigger> all = getAll(context);
        Iterator<AppNotificationTrigger> it = all.iterator();
        while (it.hasNext()) {
            if (id.equals(it.next().id)) {
                it.remove();
                break;
            }
        }
        saveAll(context, all);
    }

    public static synchronized List<AppNotificationTrigger> getByType(Context context, int type) {
        List<AppNotificationTrigger> result = new ArrayList<>();
        for (AppNotificationTrigger t : getAll(context)) {
            if (t != null && t.enabled && t.triggerType == type) {
                result.add(t);
            }
        }
        return result;
    }

    public static synchronized void clear(Context context) {
        saveAll(context, new ArrayList<>());
    }

    private static synchronized void saveAll(Context context, List<AppNotificationTrigger> list) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_JSON, GSON.toJson(list))
                .apply();
    }
}
