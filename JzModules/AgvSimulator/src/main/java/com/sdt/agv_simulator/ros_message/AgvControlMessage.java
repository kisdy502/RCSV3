package com.sdt.agv_simulator.ros_message;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
/**
 * AGV控制消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgvControlMessage extends Ros2Message {
    private String agvId;
    private String action;  // start, stop, pause, resume, emergency_stop

    public AgvControlMessage(String requestId) {
        super(requestId, MessageType.AGV_CONTROL.getValue());
    }
}
