package com.sdt.agv_simulator.ros_message;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SpeedLimitMessage extends Ros2Message {
    private String agvId;
    private Double maxSpeed;      // 最大线速度 (m/s)
    private Double maxAngularSpeed; // 最大角速度 (rad/s)，可选

    public SpeedLimitMessage() {
        setType("speed_limit");
    }
}
