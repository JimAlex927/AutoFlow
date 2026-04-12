package com.auto.master.Task.Operation;

/**
 * HTTP 请求操作
 * 发送 HTTP 请求并将响应体、状态码存入指定变量，供后续节点使用。
 */
public class HttpRequestOperation extends MetaOperation {

    public HttpRequestOperation() {
        this.setType(20);
    }
}
