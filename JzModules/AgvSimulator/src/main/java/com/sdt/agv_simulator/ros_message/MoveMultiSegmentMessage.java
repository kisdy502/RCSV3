package com.sdt.agv_simulator.ros_message;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * 多段路径移动消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoveMultiSegmentMessage extends Ros2Message {
    private String agvId;
    private String commandId;
    private List<PathPointBean> pathPoints;
    private double step = 0.1;  // 路径点间隔（米）
    private Double maxSpeed;     // 最大速度（可选）

    public MoveMultiSegmentMessage(String requestId) {
        super(requestId, MessageType.MOVE_MULTI_SEGMENT.getValue());
    }
}
