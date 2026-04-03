package com.sdt.agv_simulator.task;

/**
 * 动作执行异常
 */
public class ActionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private String actionId;
    private String actionType;
    private String errorCode;

    public ActionException(String message) {
        super(message);
    }

    public ActionException(String actionId, String actionType, String message) {
        super(message);
        this.actionId = actionId;
        this.actionType = actionType;
    }

    public ActionException(String actionId, String actionType, String message, Throwable cause) {
        super(message, cause);
        this.actionId = actionId;
        this.actionType = actionType;
    }
}
