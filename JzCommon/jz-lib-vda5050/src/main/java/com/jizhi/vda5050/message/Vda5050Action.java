package com.jizhi.vda5050.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vda5050Action {
    private String actionType;           // 动作类型
    private String actionId;             // 唯一标识
    private String blockingType;           // NONE, SOFT, HARD
    private String actionDescription;    // 可选描述
    private List<Vda5050ActionParameter> actionParameters;

    // 预定义阻塞类型常量
    public static final String BLOCKING_NONE = "NONE";
    public static final String BLOCKING_SOFT = "SOFT";
    public static final String BLOCKING_HARD = "HARD";
}
