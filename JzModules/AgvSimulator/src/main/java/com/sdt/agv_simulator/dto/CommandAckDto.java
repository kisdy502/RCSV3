package com.sdt.agv_simulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandAckDto {
    @JsonProperty("agv_id") // 映射JSON中的 "agv_id" 字段
    private String agvId;
    @JsonProperty("command_id") // 映射JSON中的 "agv_id" 字段
    private String commandId;
    @JsonProperty("node_id") // 映射JSON中的 "agv_id" 字段
    private String nodeId;
    @JsonProperty("type") // 映射JSON中的 "agv_id" 字段
    private String type;           // 消息类型：command_ack, move_result
    @JsonProperty("status") // 映射JSON中的 "agv_id" 字段
    private String status;
    @JsonProperty("message") // 映射JSON中的 "agv_id" 字段
    private String message;        // 可选，状态消息
    @JsonProperty("timestamp") // 映射JSON中的 "agv_id" 字段
    private long timestamp;
}
