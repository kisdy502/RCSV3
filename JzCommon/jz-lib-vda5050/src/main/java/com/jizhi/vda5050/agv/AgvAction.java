package com.jizhi.vda5050.agv;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AGV行动类 (VDA5050标准)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgvAction {
    private String actionId;         // 行动ID
    private String actionType;       // 行动类型
    private Integer actionPriority;  // 优先级
    private Object actionParameters; // 行动参数
    private Long blockingType;       // 阻塞类型
    private String actionDescription;// 行动描述



    public AgvAction(String actionId, String actionType) {
        this.actionId = actionId;
        this.actionType = actionType;
    }
}
