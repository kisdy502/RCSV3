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
public class LocationStatusDto {
    @JsonProperty("agv_id") // 映射JSON中的 "agv_id" 字段
    private String agvId;
    @JsonProperty("type") // 映射JSON中的 "agv_id" 字段
    private String type;
    @JsonProperty("message") // 映射JSON中的 "message" 字段
    private String message;
    @JsonProperty("is_initialized") // 映射JSON中的 "is_initialized" 字段
    private boolean isInitialized;
}