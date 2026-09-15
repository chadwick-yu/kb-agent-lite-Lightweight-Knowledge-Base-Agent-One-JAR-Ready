package com.kblite.common;

import lombok.Data;

/**
 * 统一响应体
 *
 * @author kb-agent-lite
 */
@Data
public class ApiResult<T> {

    /** 0-成功 非0-失败 */
    private int code;
    private String msg;
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.code = 0;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> ApiResult<T> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> fail(String msg) {
        return fail(500, msg);
    }

    public static <T> ApiResult<T> fail(int code, String msg) {
        ApiResult<T> r = new ApiResult<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
