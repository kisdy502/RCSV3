package com.jizhi.vda5050.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RePlanPayload {
    private String originalOrderId;
    private String reason;
    private boolean preserveProgress; // 是否保留当前进度
    private PathResult newPath;
}
