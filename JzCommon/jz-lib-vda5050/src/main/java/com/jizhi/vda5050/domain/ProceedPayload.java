package com.jizhi.vda5050.domain;

import lombok.Builder;
import lombok.Data;
@Data
@Builder
public class ProceedPayload {
    private boolean normalSpeed;
    private String reason;
}
