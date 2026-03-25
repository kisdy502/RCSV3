package com.jizhi.vda5050.message;

import com.jizhi.vda5050.domain.OrderStatePayload;
import lombok.Data;

@Data
// 用于表示完整的VDA5050订单状态消息
public class Vda5050OrderStateMessage {
    private Vda5050Header header;
    private OrderStatePayload payload; // 对应JSON中的payload对象
}
