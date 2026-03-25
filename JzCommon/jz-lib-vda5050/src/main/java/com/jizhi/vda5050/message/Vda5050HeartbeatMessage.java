package com.jizhi.vda5050.message;

import com.jizhi.vda5050.domain.HeartbeatPayload;
import lombok.Data;

@Data
// 用于表示完整的VDA5050心跳消息
public class Vda5050HeartbeatMessage {
    private Vda5050Header header;
    private HeartbeatPayload payload; // 对应JSON中的payload对象
}
