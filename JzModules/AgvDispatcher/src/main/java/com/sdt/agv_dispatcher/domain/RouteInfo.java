package com.sdt.agv_dispatcher.domain;

import com.jizhi.vda5050.domain.PathResult;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

// 路径信息数据模型
@Data
@AllArgsConstructor
public class RouteInfo {
    private String startNode;
    private String endNode;
    private List<String> nodeSequence;
    private List<String> edgeSequence;
    private Double totalDistance;
    private Double estimatedTime;

    public RouteInfo(PathResult path, String startNode, String endNode) {
        this.startNode = startNode;
        this.endNode = endNode;
        this.nodeSequence = path.getNodeSequence();
        this.edgeSequence = path.getEdgeSequence();
        this.totalDistance = path.getTotalDistance();
        this.estimatedTime = path.getEstimatedTime();
    }
}
