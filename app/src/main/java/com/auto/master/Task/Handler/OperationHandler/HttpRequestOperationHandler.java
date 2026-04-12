package com.auto.master.Task.Handler.OperationHandler;

import android.text.TextUtils;
import android.util.Log;

import com.auto.master.Task.Operation.HttpRequestOperation;
import com.auto.master.Task.Operation.MetaOperation;
import com.auto.master.Task.Operation.OperationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 请求操作处理器。
 * 在后台线程中发起 HTTP 请求，将响应体和状态码存入 ctx.variables，
 * 并通过 ctx.currentResponse 中的 MATCHED 字段驱动成功/失败路由。
 *
 * 路由规则：
 *   MATCHED=true  → 响应状态码为 2xx → 跳转 NEXT_OPERATION_ID
 *   MATCHED=false → 响应状态码非 2xx 或网络异常 → 跳转 FALLBACKOPERATIONID
 *
 * handle() 始终返回 true（异常也如此），确保 ScriptRunner 不会在此强制停止，
 * 路由决策交由 ColorMatchResponseHandler（复用其 MATCHED 分支逻辑）。
 */
public class HttpRequestOperationHandler extends OperationHandler {

    private static final String TAG = "HttpRequestHandler";
    private static final int DEFAULT_TIMEOUT_MS = 10000;
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024; // 4MB 限制

    HttpRequestOperationHandler() {
        this.setType(20);
    }

    @Override
    public boolean handle(MetaOperation obj, OperationContext ctx) {
        if (ctx == null) return false;
        if (ctx.variables == null) ctx.variables = new HashMap<>();

        Map<String, Object> inputMap = obj.getInputMap();
        if (inputMap == null) {
            Log.e(TAG, "inputMap 为 null，opId=" + obj.getId());
            putFailureResponse(ctx, obj, "inputMap 为空", 0);
            return true;
        }

        // ——— 读取配置 ———
        String rawUrl     = VariableRuntimeUtils.getString(inputMap, MetaOperation.HTTP_URL, "").trim();
        String method     = VariableRuntimeUtils.getString(inputMap, MetaOperation.HTTP_METHOD, "GET").toUpperCase().trim();
        String rawHeaders = VariableRuntimeUtils.getString(inputMap, MetaOperation.HTTP_HEADERS, "");
        String rawBody    = VariableRuntimeUtils.getString(inputMap, MetaOperation.HTTP_BODY, "");
        String responseVar = VariableRuntimeUtils.getString(inputMap, MetaOperation.HTTP_RESPONSE_VAR, "").trim();
        String statusVar   = VariableRuntimeUtils.getString(inputMap, MetaOperation.HTTP_STATUS_VAR, "").trim();

        int timeoutMs = DEFAULT_TIMEOUT_MS;
        Object rawTimeout = inputMap.get(MetaOperation.HTTP_TIMEOUT_MS);
        if (rawTimeout instanceof Number) {
            timeoutMs = ((Number) rawTimeout).intValue();
        } else if (rawTimeout instanceof String) {
            try { timeoutMs = Integer.parseInt(((String) rawTimeout).trim()); } catch (Exception ignored) {}
        }
        if (timeoutMs < 500)  timeoutMs = 500;
        if (timeoutMs > 120000) timeoutMs = 120000;

        if (TextUtils.isEmpty(rawUrl)) {
            Log.e(TAG, "HTTP_URL 为空，opId=" + obj.getId());
            putFailureResponse(ctx, obj, "URL 为空", 0);
            return true;
        }

        // ——— 变量模板替换 ———
        String url  = VariableRuntimeUtils.applyTemplate(rawUrl, ctx.variables);
        String body = VariableRuntimeUtils.applyTemplate(rawBody, ctx.variables);

        try {
            Map<String, String> headers = parseHeaders(rawHeaders, ctx.variables);
            HttpResult result = executeRequest(url, method, headers, body, timeoutMs);

            // 存入用户指定变量
            if (!TextUtils.isEmpty(responseVar)) {
                ctx.variables.put(responseVar, result.body);
            }
            if (!TextUtils.isEmpty(statusVar)) {
                ctx.variables.put(statusVar, (long) result.statusCode);
            }

            boolean success = result.statusCode >= 200 && result.statusCode < 300;
            HashMap<String, Object> response = new HashMap<>();
            response.put(MetaOperation.MATCHED, success);
            response.put(MetaOperation.RESULT, result.body);
            // 方便脚本节点直接读取
            response.put("http_status_code", (long) result.statusCode);
            response.put("http_body", result.body);
            response.put("http_headers", result.responseHeaders);

            ctx.currentResponse = response;
            ctx.lastOperation   = obj;
            ctx.currentOperation = obj;

        } catch (Exception e) {
            Log.e(TAG, "HTTP 请求失败 url=" + url + " : " + e.getMessage(), e);
            if (!TextUtils.isEmpty(responseVar)) ctx.variables.put(responseVar, "");
            if (!TextUtils.isEmpty(statusVar))   ctx.variables.put(statusVar, 0L);
            putFailureResponse(ctx, obj, e.getMessage(), 0);
        }

        return true; // 始终返回 true，由 ResponseHandler 决定路由
    }

    // ——— 内部工具 ———

    private void putFailureResponse(OperationContext ctx, MetaOperation obj, String error, int statusCode) {
        HashMap<String, Object> response = new HashMap<>();
        response.put(MetaOperation.MATCHED, false);
        response.put(MetaOperation.RESULT, "");
        response.put("http_status_code", (long) statusCode);
        response.put("http_body", "");
        response.put("http_error", error == null ? "" : error);
        ctx.currentResponse  = response;
        ctx.lastOperation    = obj;
        ctx.currentOperation = obj;
    }

    private Map<String, String> parseHeaders(String rawHeaders, Map<String, Object> variables) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (TextUtils.isEmpty(rawHeaders)) return headers;

        for (String line : rawHeaders.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key   = line.substring(0, colon).trim();
                String value = VariableRuntimeUtils.applyTemplate(
                        line.substring(colon + 1).trim(), variables);
                if (!key.isEmpty()) {
                    headers.put(key, value);
                }
            }
        }
        return headers;
    }

    private HttpResult executeRequest(String urlStr, String method,
                                      Map<String, String> headers,
                                      String body, int timeoutMs) throws IOException {
        URL parsedUrl = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) parsedUrl.openConnection();
        try {
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setInstanceFollowRedirects(true);

            for (Map.Entry<String, String> h : headers.entrySet()) {
                conn.setRequestProperty(h.getKey(), h.getValue());
            }

            // 发送请求体
            boolean methodHasBody = method.equals("POST") || method.equals("PUT")
                    || method.equals("PATCH") || method.equals("DELETE");
            if (methodHasBody && !TextUtils.isEmpty(body)) {
                // 如果用户未设置 Content-Type，默认 JSON
                boolean hasContentType = false;
                for (String key : headers.keySet()) {
                    if (key.equalsIgnoreCase("content-type")) { hasContentType = true; break; }
                }
                if (!hasContentType) {
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                }
                byte[] bodyBytes = body.getBytes("UTF-8");
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(bodyBytes.length);
                OutputStream os = conn.getOutputStream();
                os.write(bodyBytes);
                os.flush();
                os.close();
            }

            int statusCode = conn.getResponseCode();

            // 读响应体（成功流 or 错误流）
            String responseBody;
            try {
                InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? conn.getInputStream() : conn.getErrorStream();
                responseBody = is != null ? readStream(is) : "";
            } catch (IOException e) {
                responseBody = "";
            }

            // 收集响应头
            Map<String, String> respHeaders = new LinkedHashMap<>();
            Map<String, List<String>> hf = conn.getHeaderFields();
            if (hf != null) {
                for (Map.Entry<String, List<String>> entry : hf.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                        respHeaders.put(entry.getKey(), entry.getValue().get(0));
                    }
                }
            }

            return new HttpResult(statusCode, responseBody, respHeaders);

        } finally {
            conn.disconnect();
        }
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int read;
        while ((read = reader.read(buf)) != -1) {
            sb.append(buf, 0, read);
            if (sb.length() > MAX_BODY_BYTES) {
                sb.append("\n...[响应体过大，已截断]");
                break;
            }
        }
        return sb.toString();
    }

    private static class HttpResult {
        final int statusCode;
        final String body;
        final Map<String, String> responseHeaders;

        HttpResult(int statusCode, String body, Map<String, String> responseHeaders) {
            this.statusCode      = statusCode;
            this.body            = body;
            this.responseHeaders = responseHeaders;
        }
    }
}
