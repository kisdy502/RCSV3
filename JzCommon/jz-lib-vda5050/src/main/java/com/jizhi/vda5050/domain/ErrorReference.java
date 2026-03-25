package com.jizhi.vda5050.domain;

import lombok.Data;

@Data
// 错误引用信息（VDA5050协议特色设计）[2](@ref)
public class ErrorReference {
    private String referenceKey;   // 引用键：如"topic", "key", "value"等
    private String referenceValue; // 引用值：具体的引用内容

    public ErrorReference(String key, String value) {
        this.referenceKey = key;
        this.referenceValue = value;
    }
}
