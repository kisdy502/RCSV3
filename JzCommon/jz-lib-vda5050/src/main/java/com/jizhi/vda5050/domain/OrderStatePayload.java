package com.jizhi.vda5050.domain;

import lombok.Data;

@Data
public class OrderStatePayload {
    private String agvId;
    private String orderId;
    private String orderState;
    private Integer orderUpdateId;
    private String resultDescription;

    // 默认构造函数、全参构造函数、getter和setter省略
}

