package com.jizhi.vda5050.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class Edge {
    private String id="";
    private String sourceId="";
    private String targetId="";
    private Double weight=0.0;           // 距离/成本
    private EdgeDirection direction = EdgeDirection.BIDIRECTIONAL;
    private EdgeType type = EdgeType.STRAIGHT;
    private Double maxSpeed = 1.0;   // 最大速度 (m/s)
    private Integer priority = 1;    // 优先级
    private Boolean enabled = true;

    // 贝塞尔曲线控制点（用于CURVE类型的边）
    private List<ControlPoint> controlPoints = new ArrayList<>(); //不为null，可以是空List

    @Data
    public static class ControlPoint {
        private double x;
        private double y;

        public ControlPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public enum EdgeDirection {
        UNIDIRECTIONAL,  // 单向
        BIDIRECTIONAL    // 双向
    }

    public enum EdgeType {
        STRAIGHT,        // 直线
        CURVE,           // 曲线
        ELEVATION        // 坡道
    }
}
