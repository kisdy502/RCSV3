package com.jizhi.vda5050.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VDA5050 位置更新消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vda5050PositionUpdateMessage {

    @JsonProperty("header")
    private Vda5050Header header;

    @JsonProperty("position")
    private PositionInfo position;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionInfo {
        @JsonProperty("agvId")
        private String agvId;

        @JsonProperty("currentNodeId")
        private String currentNodeId;

        @JsonProperty("sequenceId")
        private Integer sequenceId;

        @JsonProperty("lastNodeId")
        private String lastNodeId;

        @JsonProperty("currentEdgeId")
        private String currentEdgeId;

        @JsonProperty("nodeSequenceId")
        private Integer nodeSequenceId;

        @JsonProperty("edgeSequenceId")
        private Integer edgeSequenceId;

        @JsonProperty("positionInitialized")
        private Boolean positionInitialized;

        @JsonProperty("x")
        private Double x;

        @JsonProperty("y")
        private Double y;

        // 保留 theta 字段（弧度），用于向后兼容
        @JsonProperty("theta")
        private Double theta;

        // 新增 orientation 字段（四元数），符合 VDA5050 标准推荐
        @JsonProperty("orientation")
        private Orientation orientation;

        @JsonProperty("mapId")
        private String mapId;

        @JsonProperty("timestamp")
        private String timestamp;

        @JsonProperty("velocity")
        private Double velocity;

        @JsonProperty("distanceSinceLastNode")
        private Double distanceSinceLastNode;

        @JsonProperty("rotationAngle")
        private Double rotationAngle;  // 旋转角度（用于叉车等）

        /**
         * 四元数表示的方向
         */
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Orientation {
            @JsonProperty("x")
            private Double x;

            @JsonProperty("y")
            private Double y;

            @JsonProperty("z")
            private Double z;

            @JsonProperty("w")
            private Double w;
        }
    }
}