package com.jizhi.vda5050.domain;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
// 心跳消息的Payload专用类
public class HeartbeatPayload {
    private String agvId;
    private Double batteryLevel;
    private String timestamp;
    private String agvState;
    private Map<String, Object> additionalInfo; // 扩展信息

    // 默认构造函数
    public HeartbeatPayload() {
        this.additionalInfo = new HashMap<>();
    }
}
