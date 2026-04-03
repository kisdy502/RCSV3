package com.sdt.agv_simulator.move;

/**
 * 移动异常
 */
public class MovementException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private String errorCode;

    /** 失败的命令ID */
    private String commandId;

    /** 失败的节点ID */
    private String nodeId;

    public MovementException(String message) {
        super(message);
    }

    public MovementException(String message, Throwable cause) {
        super(message, cause);
    }

    public MovementException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MovementException(String commandId, String nodeId, String message) {
        super(message);
        this.commandId = commandId;
        this.nodeId = nodeId;
    }

    public MovementException(String commandId, String nodeId, String message, Throwable cause) {
        super(message, cause);
        this.commandId = commandId;
        this.nodeId = nodeId;
    }
}
