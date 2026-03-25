package com.sdt.agv_simulator.ros_message;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ROS2 WebSocket 消息基类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Ros2Message {
    protected String requestId;
    protected String type;

    private Long timestamp = 0L;

    protected Ros2Message(String requestId, String type) {
        this.requestId = requestId;
        this.type = type;
        timestamp = System.currentTimeMillis();
    }
}
