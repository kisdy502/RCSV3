package com.jizhi.data;


/**
 * Author: Richile
 * Date: 2024/3/30 6:03 PM
 * Description: 基础响应类
 */
public class RmsResponse<T> {
    private int code = 0;
    private String msg = "";
    private T data = null;

    public RmsResponse() {
    }

    public RmsResponse(int code, String msg) {
        this(code, msg, null);
    }

    public RmsResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // Getter和Setter方法
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}

