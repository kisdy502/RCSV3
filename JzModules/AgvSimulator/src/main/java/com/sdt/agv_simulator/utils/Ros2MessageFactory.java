package com.sdt.agv_simulator.utils;

import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.sdt.agv_simulator.ros_message.*;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ROS2 消息工厂类
 */
@Component
public class Ros2MessageFactory {
    private final AtomicLong requestCounter = new AtomicLong(0);

    /**
     * 生成唯一的请求ID
     */
    public String generateRequestId(String prefix) {
        return String.format("%s_%d_%s",
                prefix,
                requestCounter.incrementAndGet(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 创建心跳消息
     */
    public HeartbeatMessage createHeartbeat() {
        HeartbeatMessage message = new HeartbeatMessage(generateRequestId("heartbeat"));
        message.setSource("spring_boot_agv_simulator");
        return message;
    }

    /**
     * 创建注册消息
     */
    public RegisterMessage createRegister() {
        RegisterMessage message = new RegisterMessage(generateRequestId("register"));
        message.setClientType("spring_boot_agv_simulator");
        message.setVersion("1.0.0");
        return message;
    }

    /**
     * 创建移动命令消息
     */
    public MoveToMessage createMoveCommand(String agvId, String commandId, Node node,Edge passedEdge,boolean isEnd) {
        MoveToMessage message = new MoveToMessage(generateRequestId("move"));
        message.setAgvId(agvId);
        message.setCommandId(commandId);
        message.setNodeId(node.getId());
        message.setX(node.getX());
        message.setY(node.getY());
        message.setTheta(node.getTheta());
        message.setEdgeInfo(passedEdge);
        message.setEndPoint(isEnd);
        return message;
    }

    /**
     * 创建初始位姿消息
     */
    public SetInitialPoseMessage createInitialPose(String agvId, double x, double y, double theta) {
        SetInitialPoseMessage message = new SetInitialPoseMessage(generateRequestId("init_pose"));
        message.setAgvId(agvId);
        message.setX(x);
        message.setY(y);
        message.setTheta(theta);
        return message;
    }

    /**
     * 创建速度命令消息
     */
    public VelocityCommandMessage createVelocityCommand(String agvId, double vx, double vy, double omega) {
        VelocityCommandMessage message = new VelocityCommandMessage(generateRequestId("velocity"));
        message.setAgvId(agvId);
        message.setVx(vx);
        message.setVy(vy);
        message.setOmega(omega);
        return message;
    }

    /**
     * 创建AGV控制消息
     */
    public AgvControlMessage createAgvControl(String agvId, String action) {
        AgvControlMessage message = new AgvControlMessage(generateRequestId("control"));
        message.setAgvId(agvId);
        message.setAction(action);
        return message;
    }

    public EmergencyStopMessage createEmergencyStop(String agvId) {
        EmergencyStopMessage msg = new EmergencyStopMessage();
        msg.setRequestId(generateRequestId("createEmergencyStop"));
        msg.setTimestamp(System.currentTimeMillis());
        msg.setAgvId(agvId);
        return msg;
    }

    public ClearEmergencyMessage createClearEmergency(String agvId) {
        ClearEmergencyMessage msg = new ClearEmergencyMessage();
        msg.setRequestId(generateRequestId("createClearEmergency"));
        msg.setTimestamp(System.currentTimeMillis());
        msg.setAgvId(agvId);
        return msg;
    }

    /**
     * 创建速度限制命令
     * @param agvId AGV ID
     * @param maxSpeed 最大线速度 (m/s)
     * @return SpeedLimitMessage
     */
    public SpeedLimitMessage createSpeedLimit(String agvId, Double maxSpeed) {
        SpeedLimitMessage msg = new SpeedLimitMessage();
        msg.setRequestId(generateRequestId("createSpeedLimit"));
        msg.setTimestamp(System.currentTimeMillis());
        msg.setAgvId(agvId);
        msg.setMaxSpeed(maxSpeed);
        // 角速度可以按比例设置，或传入null让ROS2使用默认值
        msg.setMaxAngularSpeed(maxSpeed != null ? maxSpeed * 0.5 : null);
        return msg;
    }

    /**
     * 创建速度限制命令（带角速度）
     * @param agvId AGV ID
     * @param maxSpeed 最大线速度 (m/s)
     * @param maxAngularSpeed 最大角速度 (rad/s)
     * @return SpeedLimitMessage
     */
    public SpeedLimitMessage createSpeedLimit(String agvId, Double maxSpeed, Double maxAngularSpeed) {
        SpeedLimitMessage msg = new SpeedLimitMessage();
        msg.setRequestId(generateRequestId("createSpeedLimit"));
        msg.setTimestamp(System.currentTimeMillis());
        msg.setAgvId(agvId);
        msg.setMaxSpeed(maxSpeed);
        msg.setMaxAngularSpeed(maxAngularSpeed);
        return msg;
    }









}