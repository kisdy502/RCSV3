package com.sdt.agv_simulator.ros_message;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StopMoveMessage extends Ros2Message {
    private String agvId;
    public StopMoveMessage(String requestId, String agvId) {
        super(requestId, MessageType.STOP_MOVE.getValue());
        this.agvId = agvId;
    }
}
