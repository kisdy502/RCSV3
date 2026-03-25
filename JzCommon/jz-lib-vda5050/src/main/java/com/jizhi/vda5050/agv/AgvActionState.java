package com.jizhi.vda5050.agv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AGV行动状态类 (VDA5050标准)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgvActionState {
    private String actionId;         // 行动ID
    private String actionStatus;     // 行动状态
    private String resultDescription;// 结果描述
    private LocalDateTime startTime; // 开始时间
    private LocalDateTime endTime;   // 结束时间

    public AgvActionState(String actionId, String actionStatus) {
        this.actionId = actionId;
        this.actionStatus = actionStatus;
    }
}
