package com.jizhi.vda5050.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaitAtPointPayload {
    private String waitPointId;
    private long duration;
    private String reason;
}
