package com.sdt.agv_dispatcher.graph;

import com.jizhi.vda5050.domain.Edge;

public class EdgeInfo {
    private final Edge edge;
    private final double weight;

    EdgeInfo(Edge edge, double weight) {
        this.edge = edge;
        this.weight = weight;
    }

    public Edge getEdge() { return edge; }
    public double getWeight() { return weight; }
}
