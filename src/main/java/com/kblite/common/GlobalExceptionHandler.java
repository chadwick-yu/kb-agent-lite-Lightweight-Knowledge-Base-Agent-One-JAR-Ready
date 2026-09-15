package com.kblite.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理
 *
 * @author kb-agent-lite
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBiz(BizException e) {
        log.warn("[业务异常] {}", e.getMessage());
        return ApiResult.fail(400, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[参数异常] {}", e.getMessage());
        return ApiResult.fail(400, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResult<Void> handleMaxUpload(MaxUploadSizeExceededException e) {
        return ApiResult.fail(400, "文件大小超过限制");
    }

    /** 静态资源/路径不存在时返回 404，而非 500（如访问 / 无 welcome page） */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ApiResult.fail(404, "资源不存在: " + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResult<Void> handleOther(Exception e) {
        log.error("[系统异常]", e);
        return ApiResult.fail(500, "系统异常: " + e.getMessage());
    }
}
