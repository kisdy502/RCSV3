package com.jizhi.vda5050.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReleaseAndReplanPayload {
    private boolean releaseAllResources;
    private boolean preserveOrder;
    private String reason;
}
