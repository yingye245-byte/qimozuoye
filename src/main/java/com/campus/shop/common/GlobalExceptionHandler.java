package com.campus.shop.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理
 * <p>接口请求返回 JSON，普通页面请求返回错误页。</p>
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static boolean isApi(HttpServletRequest request, Object handler) {
        if (request.getRequestURI().startsWith("/api/")) return true;
        if (handler instanceof HandlerMethod hm) {
            return hm.getBeanType().isAnnotationPresent(RestController.class);
        }
        return false;
    }

    @ExceptionHandler(BizException.class)
    public Object handleBiz(BizException e, HttpServletRequest request, Object handler) {
        log.warn("[业务异常] {} {}", request.getRequestURI(), e.getMessage());
        return build(e.getCode(), e.getMessage(), request, handler);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValid(MethodArgumentNotValidException e,
                              HttpServletRequest request, Object handler) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getDefaultMessage()).orElse("参数校验失败");
        return build(400, msg, request, handler);
    }

    @ExceptionHandler(Exception.class)
    public Object handleOther(Exception e, HttpServletRequest request, Object handler) {
        if (e instanceof NoResourceFoundException nrf) {
            // 静态资源（favicon 等）不存在属于正常情况，不打 ERROR 堆栈
            log.debug("[资源不存在] {}", request.getRequestURI());
            ModelAndView mv = new ModelAndView("error/error");
            mv.setStatus(HttpStatus.NOT_FOUND);
            mv.addObject("code", 404);
            mv.addObject("message", "页面或资源不存在：" + request.getRequestURI());
            return mv;
        }
        if (e instanceof MissingServletRequestParameterException
                || e instanceof MethodArgumentTypeMismatchException
                || e instanceof HttpRequestMethodNotSupportedException
                || e instanceof org.springframework.web.HttpSessionRequiredException) {
            // 表单参数缺失/类型不匹配属于客户端输入问题，只记 WARN，不打 500 堆栈
            log.warn("[请求参数异常] {} {}", request.getRequestURI(), e.getMessage());
            return build(400, "请求参数有误，请返回上一页重新提交", request, handler);
        }
        if (e instanceof org.springframework.web.servlet.resource.NoResourceFoundException
                || e instanceof org.springframework.web.HttpRequestMethodNotSupportedException
                || e instanceof org.springframework.web.servlet.NoHandlerFoundException) {
            // 地址不存在 / 请求方法不匹配：属于客户端访问问题，只记 WARN 并返回 404
            log.warn("[访问异常] {} {}", request.getRequestURI(), e.getClass().getSimpleName());
            return build(404, "页面或接口不存在，请检查地址是否正确", request, handler);
        }
        log.error("[系统异常] {}", request.getRequestURI(), e);
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return build(500, "系统繁忙：" + msg, request, handler);
    }

    private Object build(int code, String msg, HttpServletRequest request, Object handler) {
        if (isApi(request, handler)) {
            return ResponseEntity.ok(Result.error(code, msg));
        }
        ModelAndView mv = new ModelAndView("error/error");
        mv.addObject("code", code);
        mv.addObject("message", msg);
        return mv;
    }
}