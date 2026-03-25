package com.sdt.agv_dispatcher.service;

import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import java.util.List;

public interface TopologyService {
    Edge getEdge(String edgeId);
    Node getNode(String nodeId);
    List<String> findAlternativePaths(String fromNode, String toNode, String blockedResource);
    boolean isBidirectionalEdge(Edge edge);
    Edge getOppositeEdge(String edgeId);
    double calculateDistance(String node1, String node2);
}