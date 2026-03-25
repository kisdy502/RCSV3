package com.jizhi.vda5050.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StopPayload {
    private String reason;
    private boolean emergency;       // 是否紧急停止
    private boolean clearOrder;      // 是否清除当前订单
}
