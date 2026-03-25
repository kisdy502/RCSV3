package com.sdt.agv_simulator.ros_message;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * 注册消息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterMessage extends Ros2Message {
    private String clientType = "spring_boot_agv_simulator";
    private String version = "1.0.0";

    public RegisterMessage(String requestId) {
        super(requestId, MessageType.REGISTER.getValue());
    }
}