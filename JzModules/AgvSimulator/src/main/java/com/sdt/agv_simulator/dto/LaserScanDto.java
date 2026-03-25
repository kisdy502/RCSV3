package com.sdt.agv_simulator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LaserScanDto {

    @JsonProperty("agv_id") // 映射JSON中的 "agv_id" 字段
    private String agvId;

    private long timestamp; // 字段名一致，无需注解

    @JsonProperty("range_min")
    private double rangeMin;

    @JsonProperty("range_max")
    private double rangeMax;

    @JsonProperty("angle_min")
    private double angleMin;

    @JsonProperty("angle_max")
    private double angleMax;

    @JsonProperty("ranges")
    private List<Float> ranges; // 字段名一致，无需注解
    @JsonProperty("pose_x")
    double poseX;
    @JsonProperty("pose_y")
    double poseY;
    @JsonProperty("pose_theta")
    double poseTheta;  // 弧度
}