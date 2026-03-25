package com.jizhi.vda5050.domain;

import lombok.Data;

import java.util.List;

// 连接消息的Payload（符合VDA5050标准）
@Data
public class ConnectionPayload {
    // VDA5050标准字段
    private String agvId;           // AGV标识符 [标准]
    private String version;         // 协议版本 [标准]
    private String manufacturer;    // 制造商 [标准]
    private String serialNumber;    // 序列号 [标准]

    // 连接状态相关字段
    private ConnectionState connectionState; // 连接状态 [标准]
    private String timestamp;      // 时间戳 [标准]

    // 可选的标准字段
    private List<ProtocolFeature> supportedProtocolFeatures; // 支持的协议特性 [标准可选]
    private List<String> enabledProtocolFeatures; // 启用的协议特性 [标准可选]

    // 非标准字段（建议移除或映射到标准字段）
    // private String name;        // 非标准 - 建议使用agvId替代
    // private String type;        // 非标准 - 建议在factsheet中定义
    // private Object capabilities; // 非标准 - 建议使用supportedProtocolFeatures
     private String reason;      // 非标准 - 建议使用connectionState状态描述
}
