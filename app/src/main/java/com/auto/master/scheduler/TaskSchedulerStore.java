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

public final class TaskSchedulerStore {
    private static final String SP_NAME = "task_scheduler_store";
    private static final String KEY_ITEMS_JSON = "items_json";
    private static final Gson GSON = new Gson();

    private TaskSchedulerStore() {
    }

    public static synchronized List<ScheduledTask> getAll(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_ITEMS_JSON, "[]");
        try {
            Type type = new TypeToken<List<ScheduledTask>>() {}.getType();
            List<ScheduledTask> list = GSON.fromJson(json, type);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public static synchronized void upsert(Context context, ScheduledTask task) {
        if (task == null || TextUtils.isEmpty(task.id)) {
            return;
        }
        List<ScheduledTask> all = getAll(context);
        boolean updated = false;
        for (int i = 0; i < all.size(); i++) {
            ScheduledTask item = all.get(i);
            if (item != null && task.id.equals(item.id)) {
                all.set(i, task);
                updated = true;
                break;
            }
        }
        if (!updated) {
            all.add(task);
        }
        saveAll(context, all);
    }

    public static synchronized ScheduledTask findById(Context context, String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        List<ScheduledTask> all = getAll(context);
        for (ScheduledTask task : all) {
            if (task != null && id.equals(task.id)) {
                return task;
            }
        }
        return null;
    }

    public static synchronized void remove(Context context, String id) {
        if (TextUtils.isEmpty(id)) {
            return;
        }
        List<ScheduledTask> all = getAll(context);
        Iterator<ScheduledTask> iterator = all.iterator();
        while (iterator.hasNext()) {
            ScheduledTask item = iterator.next();
            if (item != null && id.equals(item.id)) {
                iterator.remove();
                break;
            }
        }
        saveAll(context, all);
    }

    public static synchronized void clear(Context context) {
        saveAll(context, new ArrayList<>());
    }

    private static synchronized void saveAll(Context context, List<ScheduledTask> tasks) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_ITEMS_JSON, GSON.toJson(tasks)).apply();
    }
}
