package com.sdt.agv_simulator.ros_message;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ClearEmergencyMessage extends Ros2Message {
    private String agvId;

    public ClearEmergencyMessage() {
        setType("clear_emergency");
    }
}
