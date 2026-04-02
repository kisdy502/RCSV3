package com.jizhi.vda5050.agv;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jizhi.vda5050.domain.Node;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 位置信息类
 */
@Data
@Builder
@AllArgsConstructor
public class AgvPosition {
    private Double x;                // X坐标 (m)
    private Double y;                // Y坐标 (m)
    //四元数
    private Double qx;                //
    private Double qy;                //
    private Double qz;                //
    private Double qw;               //
    private Double theta;            // 方向角 (弧度)
    private String mapId;            // 地图ID
    private String mapDescription;   // 地图描述
    private Double accuracy;         // 定位精度
    private Long timestamp;          // 时间戳
    private Long timestampNs;          // 时间戳
    @Builder.Default
    private Boolean positionInitialized = false;
//    @JsonProperty("zoneSetId")
//    private String zoneSetId;

    public AgvPosition() {
    }

    public AgvPosition(Double x, Double y, Double theta) {
        this.x = x;
        this.y = y;
        this.theta = theta;
    }

    public AgvPosition(Double x, Double y, Double theta, String mapId, Double px, Double py, Double pz, Double pw) {
        this.x = x;
        this.y = y;
        this.theta = theta;
        this.mapId = mapId;
        this.qx = px;
        this.qy = py;
        this.qz = pz;
        this.qw = pw;
    }

    /**
     * 计算到另一个位置的距离
     */
    public Double distanceTo(AgvPosition other) {
        if (other == null || other.x == null || other.y == null) {
            return null;
        }
        double dx = other.x - this.x;
        double dy = other.y - this.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 计算需要旋转到目标节点的角度
     */
    public Double calculateRotationToNode(Node targetNode) {
        if (targetNode.getTheta() == null) {
            return 0.0;  // 如果节点没有指定角度，则不旋转
        }

        double delta = targetNode.getTheta() - this.theta;

        // 规范化到[-π, π]范围
        while (delta > Math.PI) {
            delta -= 2 * Math.PI;
        }
        while (delta < -Math.PI) {
            delta += 2 * Math.PI;
        }

        return delta;
    }


}
