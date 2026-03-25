package com.jizhi.vda5050.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PausePayload {
    private long duration;            // 暂停时长（秒），0表示无限期
    private String reason;
    private boolean resumeAutomatically; // 是否自动恢复
    private String waitForAgvId;  // 添加这个字段
}
