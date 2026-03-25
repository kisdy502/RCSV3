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
public class PositionUpdateDto {
    @JsonProperty("agv_id")
    private String agvId;

    @JsonProperty("x")
    private double x;

    @JsonProperty("y")
    private double y;

    @JsonProperty("qx")
    private double qx;

    @JsonProperty("qy")
    private double qy;

    @JsonProperty("qz")
    private double qz;

    @JsonProperty("qw")
    private double qw;

    @JsonProperty("theta")
    private double theta;

    @JsonProperty("vx")
    private double vx;

    @JsonProperty("vy")
    private double vy;

    @JsonProperty("omega")
    private double omega;

    @JsonProperty("timestamp_ns")
    private long timestampNs;  // 纳秒级时间戳，用于排序

    @JsonProperty("timestamp")
    private long timestamp;     // 毫秒级时间戳（可选，可由timestampNs计算）
}