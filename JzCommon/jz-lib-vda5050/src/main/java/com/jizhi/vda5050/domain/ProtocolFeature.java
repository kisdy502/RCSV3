package com.jizhi.vda5050.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolFeature {
    private String feature; // 特性名称
    private Boolean enabled; // 是否启用
}
