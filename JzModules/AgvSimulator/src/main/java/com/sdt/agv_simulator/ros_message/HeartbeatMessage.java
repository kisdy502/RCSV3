package com.sdt.agv_simulator.ros_message;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;


/**
 * 心跳消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeartbeatMessage extends Ros2Message {
    private String source = "spring_boot_agv_simulator";

    public HeartbeatMessage(String requestId) {
        super(requestId, MessageType.HEARTBEAT.getValue());
    }
}
