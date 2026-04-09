package com.auto.master.utils;  // 你可以改成自己的包名

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 支持根据字段值自动选择子类的 TypeAdapterFactory
 * 使用方式：
 * RuntimeTypeAdapterFactory<MetaOperation> factory = RuntimeTypeAdapterFactory
 *     .of(MetaOperation.class, "type")
 *     .registerSubtype(ClickOperation.class, 1)
 *     .registerSubtype(DelayOperation.class, 2);
 *
 * Gson gson = new GsonBuilder()
 *     .registerTypeAdapterFactory(factory)
 *     .create();
 */
public final class RuntimeTypeAdapterFactory<T> implements TypeAdapterFactory {
    private final Class<?> baseType;
    private final String typeFieldName;
    private final Map<String, Class<?>> labelToSubtype = new HashMap<>();
    private final Map<Class<?>, String> subtypeToLabel = new HashMap<>();

    private RuntimeTypeAdapterFactory(Class<?> baseType, String typeFieldName) {
        this.baseType = baseType;
        this.typeFieldName = typeFieldName;
    }

    /**
     * 创建工厂，指定基类和用于区分类型的字段名
     */
    public static <T> RuntimeTypeAdapterFactory<T> of(Class<T> baseType, String typeFieldName) {
        return new RuntimeTypeAdapterFactory<>(baseType, typeFieldName);
    }

    /**
     * 注册一个子类型和对应的标签值（可以是字符串或数字）
     */
    public RuntimeTypeAdapterFactory<T> registerSubtype(Class<? extends T> type, Object label) {
        labelToSubtype.put(label.toString(), type);
        subtypeToLabel.put(type, label.toString());
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> TypeAdapter<R> create(Gson gson, TypeToken<R> type) {
        if (!baseType.isAssignableFrom(type.getRawType())) {
            return null;
        }

        final Map<String, TypeAdapter<? extends T>> labelToDelegate = new HashMap<>();
        final Map<TypeAdapter<? extends T>, String> delegateToLabel = new HashMap<>();

        for (Map.Entry<String, Class<?>> entry : labelToSubtype.entrySet()) {
            TypeAdapter<? extends T> delegate = (TypeAdapter<? extends T>) gson.getDelegateAdapter(this, TypeToken.get(entry.getValue()));
            labelToDelegate.put(entry.getKey(), delegate);
            delegateToLabel.put(delegate, entry.getKey());
        }

        return new TypeAdapter<R>() {
            @Override
            public R read(JsonReader in) throws IOException {
                JsonElement jsonElement = gson.fromJson(in, JsonElement.class);
                JsonElement labelJsonElement = jsonElement.getAsJsonObject().get(typeFieldName);
                if (labelJsonElement == null) {
                    throw new JsonParseException("cannot deserialize " + baseType + " because it does not define a field named " + typeFieldName);
                }
                String label = labelJsonElement.getAsString();  // 如果你的 type 是 Integer，这里改成 getAsInt() + ""
                @SuppressWarnings("unchecked") // registration requires that subtype extends T
                TypeAdapter<T> delegate = (TypeAdapter<T>) labelToDelegate.get(label);
                if (delegate == null) {
                    throw new JsonParseException("cannot deserialize " + baseType + " subtype named " + label + "; did not match any subtype");
                }
                return (R) delegate.fromJsonTree(jsonElement);
            }

            @Override
            public void write(JsonWriter out, R value) throws IOException {
                @SuppressWarnings("unchecked") // registration requires that subtype extends T
                Class<? extends T> srcType = (Class<? extends T>) value.getClass();
                String label = subtypeToLabel.get(srcType);
                @SuppressWarnings("unchecked") // registration requires that subtype extends T
                TypeAdapter<T> delegate = (TypeAdapter<T>) labelToDelegate.get(label);
                if (delegate == null) {
                    throw new JsonParseException("cannot serialize " + srcType.getName() + "; did not match any registered subtype");
                }
                JsonObject jsonObject = delegate.toJsonTree((T) value).getAsJsonObject();
                if (jsonObject.has(typeFieldName)) {
                    throw new JsonParseException("serialized object has a field named " + typeFieldName + " which cannot be serialized");
                }
                JsonObject clone = new JsonObject();
                clone.add(typeFieldName, gson.toJsonTree(label));
                for (Map.Entry<String, JsonElement> e : jsonObject.entrySet()) {
                    clone.add(e.getKey(), e.getValue());
                }
                gson.toJson(clone, out);
            }
        }.nullSafe();
    }
}