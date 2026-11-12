package com.campus.shop.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果封装（AJAX 接口用）
 */
@Data
public class Result<T> implements Serializable {

    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> fail(String message) {
        return error(500, message);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return error(code, message);
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }
}