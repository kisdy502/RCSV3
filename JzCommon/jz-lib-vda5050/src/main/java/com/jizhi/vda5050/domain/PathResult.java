package com.jizhi.vda5050.domain;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@ToString
public class PathResult {

    private String taskId;
    private String agvId;
    private List<String> nodeSequence;      // 节点ID序列
    private List<String> edgeSequence;      // 边ID序列
    private Double totalDistance;           // 总距离
    private Double estimatedTime;           // 预计时间(秒)
    private Double energyCost;              // 能耗估计
    private List<PathSegment> segments;     // 路径分段
     private boolean locked = false;
     private Map<String, Boolean> lockResults;
     private LocalDateTime lockTime;
    private LocalDateTime plannedReleaseTime;

    @Data
    public static class PathSegment {
        private String fromNodeId;
        private String toNodeId;
        private String edgeId;
        private Double distance;
        private Double estimatedTime;
        private String action;  // 动作类型：MOVE, LOAD, UNLOAD, CHARGE等
        private LocalDateTime plannedStartTime;
        private LocalDateTime plannedEndTime;
    }


    public List<String> getRemainingNodes() {
        //TODO 返回剩余未走过的节点
        return nodeSequence;
    }
}
