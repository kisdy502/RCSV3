package com.jizhi.vda5050.message;

import com.jizhi.vda5050.domain.ErrorPayload;
import lombok.Data;

@Data
// 用于表示完整的VDA5050错误消息
public class Vda5050ErrorMessage {
    private Vda5050Header header;
    private ErrorPayload payload; // 对应JSON中的payload对象
}
