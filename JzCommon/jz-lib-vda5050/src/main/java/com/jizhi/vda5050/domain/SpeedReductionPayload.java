package com.jizhi.vda5050.domain;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

@Data
@Builder
public class SpeedReductionPayload {
    private double targetSpeed;      // 目标速度 m/s
    private double reductionRatio;   // 减速比例 0.0-1.0
    private String reason;
    private boolean temporary;       // 是否临时限制
    private Duration duration;        // 持续时间（秒），null表示永久
}
