package com.sdt.agv_simulator.ros_message;


import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EmergencyStopMessage extends Ros2Message {
    private String agvId;

    public EmergencyStopMessage() {
        setType("emergency_stop");
    }
}
