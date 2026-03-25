package com.jizhi.vda5050.domain;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Node {
    private String id;
    private NodeType type;
    private String name;
    private Double x;
    private Double y;
    private Double theta;           // 新增：角度（弧度制，范围为[-π, π]）
    private String zoneId;           // 所属区域
    private Boolean enabled = true;  // 是否启用
    private String agvType;          // 支持的AGV类型
    private Map<String, Object> properties = new HashMap<>();

    public enum NodeType {
        STATION("站点"),
        PATH("通道点"),
        INTERSECTION("交叉点"),
        CHARGE("充电点"),
        WAIT("等待点"),
        DOCKING("对接点"),
        LOAD("装载点"),
        UNLOAD("卸载点");

        private final String description;

        NodeType(String description) {
            this.description = description;
        }
    }
}
