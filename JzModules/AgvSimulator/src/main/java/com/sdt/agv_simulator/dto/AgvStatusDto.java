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
public class AgvStatusDto {
    @JsonProperty("agv_id") // 映射JSON中的 "agv_id" 字段
    private String agvId;
    @JsonProperty("state") // 映射JSON中的 "agv_id" 字段
    private String state;
    @JsonProperty("battery") // 映射JSON中的 "agv_id" 字段
    private double battery;

    @JsonProperty("timestamp") // 映射JSON中的 "agv_id" 字段
    private long timestamp; // 此字段名与JSON一致，无需注解

    @JsonProperty("pose_initialized") // 映射JSON中的 "pose_initialized" 字段
    private Boolean poseInitialized; // 此字段名与JSON一致，无需注解
}