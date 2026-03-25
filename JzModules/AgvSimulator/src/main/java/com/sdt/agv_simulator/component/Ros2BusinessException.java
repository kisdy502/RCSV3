package com.sdt.agv_simulator.component;

public class Ros2BusinessException extends RuntimeException {
    private final String errorCode;

    public Ros2BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public Ros2BusinessException(String errorCode, String message, Exception e) {
        super(message, e);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
