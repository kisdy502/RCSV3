package com.sdt.agv_simulator.ros_message;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
/**
 * 速度命令消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VelocityCommandMessage extends Ros2Message {
    private String agvId;
    private double vx;
    private double vy;
    private double omega;

    public VelocityCommandMessage(String requestId) {
        super(requestId, MessageType.VELOCITY_COMMAND.getValue());
    }
}
