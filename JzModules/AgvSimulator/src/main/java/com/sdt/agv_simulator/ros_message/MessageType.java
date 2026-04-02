package com.sdt.agv_simulator.ros_message;

/**
 * ROS2 WebSocket 消息类型
 */
public enum MessageType {
    // 心跳
    HEARTBEAT("heartbeat"),

    // 注册
    REGISTER("register"),

    // 移动命令
    MOVE_TO("move_to"),

    // 初始位姿
    SET_INITIAL_POSE("set_initial_pose"),

    // 速度命令
    VELOCITY_COMMAND("velocity_command"),

    // AGV控制
    AGV_CONTROL("agv_control"),

    // 状态上报
    STATUS_REPORT("status_report");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
