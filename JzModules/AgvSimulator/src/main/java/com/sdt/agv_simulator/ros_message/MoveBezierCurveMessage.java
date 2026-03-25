package com.sdt.agv_simulator.ros_message;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * 贝塞尔曲线移动消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoveBezierCurveMessage extends Ros2Message {
    private String agvId;
    private String commandId;
    private String startNodeId;
    private String endNodeId;

    // 起点
    private double startX;
    private double startY;
    private double startTheta;

    // 终点
    private double endX;
    private double endY;
    private double endTheta;

    // 控制点
    private List<BezierControlPoint> controlPoints;

    // 路径参数
    private double step = 0.1;  // 路径点间隔（米）
    private Double maxSpeed;     // 最大速度（可选）

    public MoveBezierCurveMessage(String requestId) {
        super(requestId, MessageType.MOVE_BEZIER_CURVE.getValue());
    }
}

