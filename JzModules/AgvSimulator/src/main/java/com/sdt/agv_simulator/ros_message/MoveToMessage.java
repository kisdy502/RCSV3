package com.sdt.agv_simulator.ros_message;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.jizhi.vda5050.domain.Edge;
import lombok.*;

/**
 * 移动命令消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoveToMessage extends Ros2Message {
    private String agvId;
    private String commandId;
    private String nodeId;
    private double x;
    private double y;
    private double theta;

    private Edge edgeInfo;

    private boolean isEndPoint;

    public MoveToMessage(String requestId) {
        super(requestId, MessageType.MOVE_TO.getValue());
    }
}
