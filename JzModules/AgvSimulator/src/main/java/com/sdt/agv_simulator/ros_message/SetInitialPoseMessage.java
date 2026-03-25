package com.sdt.agv_simulator.ros_message;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
/**
 * 初始位姿消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SetInitialPoseMessage extends Ros2Message {
    private String agvId;
    private double x;
    private double y;
    private double theta;

    public SetInitialPoseMessage(String requestId) {
        super(requestId, MessageType.SET_INITIAL_POSE.getValue());
    }
}
