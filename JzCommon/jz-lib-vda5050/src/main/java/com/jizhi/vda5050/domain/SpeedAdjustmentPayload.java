package com.jizhi.vda5050.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpeedAdjustmentPayload {
    private double targetSpeed;
    private boolean temporary;
    private String reason;
}

