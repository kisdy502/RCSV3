package com.jizhi.vda5050.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReleaseResourcePayload {
    private boolean releaseLastOnly;
    private String reason;
}

