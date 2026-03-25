package com.jizhi.vda5050.message;


import com.jizhi.vda5050.domain.ConnectionPayload;
import lombok.Data;

// 标准VDA5050连接消息
@Data
public class Vda5050ConnectionMessage {
    private Vda5050Header header;
    private ConnectionPayload payload;
}
