package com.auto.master.Task.Handler.OperationHandler;

import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import android.text.TextUtils;
import android.util.Log;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VariableScriptOperationHandler extends OperationHandler {
    private static final String TAG = "VariableScriptHandler";

    // Bootstrap 辅助函数源码（只编译一次，放入共享 scope）
    private static final String BOOTSTRAP_SRC = ""
            + "function toNumber(v){ var n = Number(v); return isNaN(n) ? 0 : n; }\n"
            + "function toBool(v){ return !!v; }\n"
            + "function toStr(v){ return String(v); }\n"
            + "function len(v){ return String(v).length; }\n"
            + "function contains(a,b){ return String(a).indexOf(String(b)) >= 0; }\n"
            + "function startsWith(a,b){ a=String(a); b=String(b); return a.indexOf(b)===0; }\n"
            + "function endsWith(a,b){ a=String(a); b=String(b); return a.lastIndexOf(b)===a.length-b.length; }\n"
            + "function substr2(a,s,l){ a=String(a); s=Number(s)||0; if(l===undefined) return a.substr(s); return a.substr(s, Number(l)||0); }\n"
            + "function now(){ return Date.now(); }\n";

    /**
     * 共享 sealed scope：initStandardObjects() + bootstrap 只执行一次。
     * 每次脚本执行时创建一个以此为 prototype 的轻量 execScope，避免重复初始化开销。
     */
    private static volatile ScriptableObject sharedScope = null;
    /** bootstrap 完成后 scope 内已有的 key 集合，用于 sync 时排除内置符号 */
    private static volatile Set<String> bootstrapBaselineKeys = null;
    private static final Object SCOPE_INIT_LOCK = new Object();

    // LRU 脚本缓存：超出上限淘汰最久未用的，避免全量 clear 带来的冷启动抖动
    private static final int MAX_SCRIPT_CACHE = 128;
    private static final Map<String, Script> SCRIPT_CACHE = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, Script>(MAX_SCRIPT_CACHE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Script> eldest) {
                    return size() > MAX_SCRIPT_CACHE;
                }
            });
    private static final ScriptContextFactory SCRIPT_CONTEXT_FACTORY = new ScriptContextFactory();

    VariableScriptOperationHandler() {
        this.setType(11);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (obj == null || ctx == null) {
            return false;
        }
        if (ctx.variables == null) {
            ctx.variables = new HashMap<>();
        }

        String script = VariableRuntimeUtils.getString(obj.getInputMap(), MetaOperation.VAR_SCRIPT_CODE, "");
        String varName = VariableRuntimeUtils.getString(obj.getInputMap(), MetaOperation.VAR_NAME, "").trim();
        String nextId = VariableRuntimeUtils.getString(obj.getInputMap(), MetaOperation.NEXT_OPERATION_ID, "").trim();

        long timeoutMs = parseTimeoutMs(obj);
        Object resultValue;
        try {
            resultValue = executeScript(obj, ctx, script, timeoutMs);
        } catch (Exception e) {
            Log.e(TAG, "executeScript failed for opId=" + obj.getId(), e);
            return false;
        }

        if (!TextUtils.isEmpty(varName)) {
            ctx.variables.put(varName, resultValue);
        }

        HashMap<String, Object> response = new HashMap<>();
        response.put(MetaOperation.RESULT, resultValue);
        response.put(MetaOperation.BRANCH_NEXT_ID, nextId);

        ctx.currentResponse = response;
        ctx.lastOperation = obj;
        ctx.currentOperation = obj;
        return true;
    }

    /**
     * 初始化共享 scope（只执行一次）：
     * - initStandardObjects()：构建 JS 内置对象
     * - 编译并执行 bootstrap 辅助函数
     * - sealObject()：防止意外修改，并允许多线程安全复用
     */
    private static void ensureSharedScope() {
        if (sharedScope != null) return;
        synchronized (SCOPE_INIT_LOCK) {
            if (sharedScope != null) return;
            Context cx = SCRIPT_CONTEXT_FACTORY.enterContext();
            cx.setOptimizationLevel(-1);
            try {
                ScriptableObject scope = cx.initStandardObjects();
                cx.evaluateString(scope, BOOTSTRAP_SRC, "var_bootstrap", 1, null);
                Set<String> keys = new HashSet<>();
                for (Object id : scope.getIds()) {
                    if (id instanceof String) keys.add((String) id);
                }
                // 注意：不能调用 sealObject()，Android 的 DocumentBuilderFactoryImpl 不支持
                // secure-processing 特性，sealObject 会触发 Rhino XML 库懒加载进而崩溃。
                // execScope 以此为 prototype 而不直接写入，共享 scope 实际上不会被污染。
                bootstrapBaselineKeys = java.util.Collections.unmodifiableSet(keys);
                sharedScope = scope;
            } finally {
                Context.exit();
            }
        }
    }

    private Object executeScript(MetaOperation obj, OperationContext ctx, String script, long timeoutMs) {
        ensureSharedScope();
        Context js = SCRIPT_CONTEXT_FACTORY.enterContextWithTimeout(timeoutMs);
        try {
            // 创建轻量 execScope，以 sharedScope 为 prototype 继承所有内置对象和 bootstrap 函数
            Scriptable execScope = js.newObject(sharedScope);
            execScope.setPrototype(sharedScope);
            execScope.setParentScope(null);

            // 直接把变量注入到 execScope 属性，不使用 with(vars){} 包裹。
            // with 语句迫使 Rhino 在每次变量读写时遍历 scope chain，性能很差。
            // 直接注入后脚本可以像局部变量一样直接使用变量名，速度快得多。
            Set<String> injectedVarKeys = new HashSet<>();
            for (Map.Entry<String, Object> entry : ctx.variables.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isEmpty()) continue;
                ScriptableObject.putProperty(execScope, key, toScriptValue(js, sharedScope, entry.getValue()));
                injectedVarKeys.add(key);
            }

            String safeScript = (script == null || script.isEmpty()) ? "null" : script;
            String cacheKey = buildCacheKey(obj, safeScript);
            Script compiled = SCRIPT_CACHE.get(cacheKey);
            if (compiled == null) {
                compiled = js.compileString(safeScript, "var_script", 1, null);
                SCRIPT_CACHE.put(cacheKey, compiled);
            }
            Object result = compiled.exec(js, execScope);

            syncVariablesBackToContext(ctx, execScope, injectedVarKeys, bootstrapBaselineKeys);

            return toPlainJava(result);
        } finally {
            Context.exit();
            SCRIPT_CONTEXT_FACTORY.clearDeadline();
        }
    }

    private long parseTimeoutMs(MetaOperation obj) {
        Object raw = obj == null || obj.getInputMap() == null ? null : obj.getInputMap().get("VAR_SCRIPT_TIMEOUT_MS");
        long timeout = 1500L;
        if (raw instanceof Number) {
            timeout = ((Number) raw).longValue();
        } else if (raw instanceof String) {
            try { timeout = Long.parseLong(((String) raw).trim()); } catch (Exception ignored) {}
        }
        if (timeout < 200L) timeout = 200L;
        if (timeout > 8000L) timeout = 8000L;
        return timeout;
    }

    private static final class ScriptContextFactory extends ContextFactory {
        private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();

        Context enterContextWithTimeout(long timeoutMs) {
            DEADLINE.set(System.currentTimeMillis() + timeoutMs);
            Context cx = enterContext();
            cx.setOptimizationLevel(-1);
            cx.setInstructionObserverThreshold(10000);
            return cx;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            Long deadline = DEADLINE.get();
            if (deadline != null && System.currentTimeMillis() > deadline) {
                throw new RuntimeException("script timeout");
            }
        }

        void clearDeadline() {
            DEADLINE.remove();
        }
    }

    private Object toPlainJava(Object value) {
        if (value == null || value == Undefined.instance) return null;
        if (value instanceof NativeJavaObject) return toPlainJava(((NativeJavaObject) value).unwrap());
        if (value instanceof NativeArray) {
            NativeArray array = (NativeArray) value;
            ArrayList<Object> list = new ArrayList<>();
            long length = array.getLength();
            for (int i = 0; i < length; i++) {
                list.add(toPlainJava(array.get(i, array)));
            }
            return list;
        }
        if (value instanceof NativeObject) {
            NativeObject object = (NativeObject) value;
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            for (Object id : object.getIds()) {
                if (!(id instanceof String)) {
                    continue;
                }
                String key = (String) id;
                map.put(key, toPlainJava(object.get(key, object)));
            }
            return map;
        }
        if (value instanceof Double) return VariableRuntimeUtils.normalizeNumber((Double) value);
        if (value instanceof Float) return VariableRuntimeUtils.normalizeNumber(((Float) value).doubleValue());
        if (value instanceof Number || value instanceof Boolean || value instanceof String) return value;
        return String.valueOf(value);
    }

    private Object toScriptValue(Context js, Scriptable scope, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?>) {
            Scriptable obj = js.newObject(scope);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                Object rawKey = entry.getKey();
                if (rawKey == null) {
                    continue;
                }
                ScriptableObject.putProperty(obj, String.valueOf(rawKey),
                        toScriptValue(js, scope, entry.getValue()));
            }
            return obj;
        }
        if (value instanceof Iterable<?>) {
            ArrayList<Object> items = new ArrayList<>();
            for (Object item : (Iterable<?>) value) {
                items.add(toScriptValue(js, scope, item));
            }
            return js.newArray(scope, items.toArray());
        }
        if (value.getClass().isArray() && value instanceof Object[]) {
            Object[] raw = (Object[]) value;
            Object[] converted = new Object[raw.length];
            for (int i = 0; i < raw.length; i++) {
                converted[i] = toScriptValue(js, scope, raw[i]);
            }
            return js.newArray(scope, converted);
        }
        return Context.javaToJS(value, scope);
    }

    /**
     * 将脚本执行后 execScope 中的变量同步回 ctx.variables。
     *
     * @param injectedVarKeys 执行前注入到 execScope 的变量名集合
     * @param scopeBaselineKeys bootstrap 阶段已有的内置符号（不需回写）
     */
    private void syncVariablesBackToContext(OperationContext ctx,
                                             Scriptable scope,
                                             Set<String> injectedVarKeys,
                                             Set<String> scopeBaselineKeys) {
        // 1. 先更新所有注入过的变量（可能被脚本修改或删除）
        for (String key : injectedVarKeys) {
            Object value = ScriptableObject.getProperty(scope, key);
            if (value == null || value == Undefined.instance || value == Scriptable.NOT_FOUND) {
                ctx.variables.remove(key);
            } else {
                ctx.variables.put(key, toPlainJava(value));
            }
        }

        // 2. 再收集脚本新定义的变量（不在注入集合、不是内置符号）
        Object[] scopeIds = scope.getIds();
        for (Object id : scopeIds) {
            if (!(id instanceof String)) continue;
            String key = (String) id;
            if (injectedVarKeys.contains(key) || scopeBaselineKeys.contains(key)) continue;
            Object value = ScriptableObject.getProperty(scope, key);
            if (shouldPersistScopeValue(value)) {
                ctx.variables.put(key, toPlainJava(value));
            }
        }
    }

    private boolean shouldPersistScopeValue(Object value) {
        if (value == null || value == Undefined.instance || value == Scriptable.NOT_FOUND) {
            return false;
        }
        return !(value instanceof Function);
    }

    private String buildCacheKey(MetaOperation obj, String wrappedScript) {
        String opId = obj == null ? "" : String.valueOf(obj.getId());
        int hash = wrappedScript == null ? 0 : wrappedScript.hashCode();
        return opId + "#" + hash;
    }
}
