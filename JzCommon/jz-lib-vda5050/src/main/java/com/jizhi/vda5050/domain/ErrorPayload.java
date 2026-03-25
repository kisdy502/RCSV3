package com.jizhi.vda5050.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
// 错误消息的Payload专用类
public class ErrorPayload {
    private String agvId;
    private String errorCode;
    private String errorDescription;
    private String timestamp;
    private String errorLevel; // 错误级别：ERROR, WARNING, FATAL等
    private List<ErrorReference> errorReferences; // 错误引用信息[2](@ref)

    // 默认构造函数
    public ErrorPayload() {
        this.errorReferences = new ArrayList<>();
    }
}
