package com.sdt.agv_dispatcher.domain;

import com.sdt.agv_dispatcher.graph.AGVGraph;
import com.jizhi.vda5050.domain.Node;
import lombok.Data;

import java.util.List;

// 路径边界框数据模型
@Data
public class RouteBounds {
    private Double minX;
    private Double maxX;
    private Double minY;
    private Double maxY;
    private Double width;
    private Double height;

    public RouteBounds(List<String> nodeSequence, AGVGraph agvGraph) {
        this.minX = Double.MAX_VALUE;
        this.maxX = Double.MIN_VALUE;
        this.minY = Double.MAX_VALUE;
        this.maxY = Double.MIN_VALUE;

        // 计算边界
        for (String nodeId : nodeSequence) {
            Node node = agvGraph.getNode(nodeId);
            if (node != null) {
                this.minX = Math.min(this.minX, node.getX());
                this.maxX = Math.max(this.maxX, node.getX());
                this.minY = Math.min(this.minY, node.getY());
                this.maxY = Math.max(this.maxY, node.getY());
            }
        }

        this.width = this.maxX - this.minX;
        this.height = this.maxY - this.minY;
    }
}

