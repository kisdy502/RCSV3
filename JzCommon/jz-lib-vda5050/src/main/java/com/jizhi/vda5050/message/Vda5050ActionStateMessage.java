package com.jizhi.vda5050.message;

import com.jizhi.vda5050.domain.StatePayload;
import lombok.Data;

// 动作状态消息
@Data
public class Vda5050ActionStateMessage {
    private Vda5050Header header;
    private StatePayload payload;
}
