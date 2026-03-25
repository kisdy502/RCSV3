package com.jizhi.vda5050.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgvControlCommand {
    private String agvId;
    private ControlCommandType commandType;
    private Object payload;           // 具体参数
    private LocalDateTime timestamp;
    private String correlationId;     // 用于追踪
    private int priority;

    public enum ControlCommandType {
        INSTANT_ACTION,    // VDA5050即时动作
        ORDER,             // VDA5050订单
        STATE_QUERY,       // 状态查询
        CONFIG_UPDATE      // 配置更新
    }
}
