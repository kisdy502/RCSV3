package com.sdt.agv_simulator.utils;

import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.sdt.agv_simulator.ros_message.*;
import org.springframework.stereotype.Component;

import java.util.List;
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

    /**
     * 创建二阶贝塞尔曲线消息
     */
    public MoveBezierCurveMessage createQuadraticBezier(
            String agvId, String commandId,
            String startNodeId, String endNodeId,
            double startX, double startY, double startTheta,
            double endX, double endY, double endTheta,
            double controlX, double controlY) {

        MoveBezierCurveMessage message = new MoveBezierCurveMessage(generateRequestId("bezier_q"));
        message.setAgvId(agvId);
        message.setCommandId(commandId);
        message.setStartNodeId(startNodeId);
        message.setEndNodeId(endNodeId);
        message.setStartX(startX);
        message.setStartY(startY);
        message.setStartTheta(startTheta);
        message.setEndX(endX);
        message.setEndY(endY);
        message.setEndTheta(endTheta);

        BezierControlPoint controlPoint = new BezierControlPoint(controlX, controlY);
        message.setControlPoints(List.of(controlPoint));
        message.setStep(0.1);

        return message;
    }

    /**
     * 创建三阶贝塞尔曲线消息
     */
    public MoveBezierCurveMessage createCubicBezier(
            String agvId, String commandId,
            String startNodeId, String endNodeId,
            double startX, double startY, double startTheta,
            double endX, double endY, double endTheta,
            double controlX1, double controlY1,
            double controlX2, double controlY2) {

        MoveBezierCurveMessage message = new MoveBezierCurveMessage(generateRequestId("bezier_c"));
        message.setAgvId(agvId);
        message.setCommandId(commandId);
        message.setStartNodeId(startNodeId);
        message.setEndNodeId(endNodeId);
        message.setStartX(startX);
        message.setStartY(startY);
        message.setStartTheta(startTheta);
        message.setEndX(endX);
        message.setEndY(endY);
        message.setEndTheta(endTheta);

        BezierControlPoint controlPoint1 = new BezierControlPoint(controlX1, controlY1);
        BezierControlPoint controlPoint2 = new BezierControlPoint(controlX2, controlY2);
        message.setControlPoints(List.of(controlPoint1, controlPoint2));
        message.setStep(0.1);

        return message;
    }

    /**
     * 创建通用贝塞尔曲线消息
     */
    public MoveBezierCurveMessage createBezierCurve(
            String agvId, String commandId,
            String startNodeId, String endNodeId,
            double startX, double startY, double startTheta,
            double endX, double endY, double endTheta,
            List<BezierControlPoint> controlPoints) {

        MoveBezierCurveMessage message = new MoveBezierCurveMessage(generateRequestId("bezier"));
        message.setAgvId(agvId);
        message.setCommandId(commandId);
        message.setStartNodeId(startNodeId);
        message.setEndNodeId(endNodeId);
        message.setStartX(startX);
        message.setStartY(startY);
        message.setStartTheta(startTheta);
        message.setEndX(endX);
        message.setEndY(endY);
        message.setEndTheta(endTheta);
        message.setControlPoints(controlPoints);
        message.setStep(0.1);

        return message;
    }

    /**
     * 根据Edge创建贝塞尔曲线消息
     */
    public MoveBezierCurveMessage createBezierCurveFromEdge(
            String agvId, String commandId,
            Node startNode,
            Node endNode,
            Edge edge) {

        MoveBezierCurveMessage message = new MoveBezierCurveMessage(generateRequestId("bezier"));
        message.setAgvId(agvId);
        message.setCommandId(commandId);
        message.setStartNodeId(startNode.getId());
        message.setEndNodeId(endNode.getId());
        message.setStartX(startNode.getX());
        message.setStartY(startNode.getY());
        message.setStartTheta(startNode.getTheta());
        message.setEndX(endNode.getX());
        message.setEndY(endNode.getY());
        message.setEndTheta(endNode.getTheta());
        message.setStep(0.1);

        if (edge.getMaxSpeed() != null) {
            message.setMaxSpeed(edge.getMaxSpeed());
        }

        // 转换控制点
        if (edge.getControlPoints() != null) {
            List<BezierControlPoint> controlPoints = edge.getControlPoints().stream()
                    .map(cp -> new BezierControlPoint(cp.getX(), cp.getY()))
                    .toList();
            message.setControlPoints(controlPoints);
        }

        return message;
    }

    /**
     * 创建多段路径消息
     */
    public MoveMultiSegmentMessage createMultiSegmentPath(
            String agvId, String commandId,
            List<PathPointBean> pathPoints) {

        MoveMultiSegmentMessage message = new MoveMultiSegmentMessage(generateRequestId("multi_segment"));
        message.setAgvId(agvId);
        message.setCommandId(commandId);
        message.setPathPoints(pathPoints);
        message.setStep(0.1);

        return message;
    }
}