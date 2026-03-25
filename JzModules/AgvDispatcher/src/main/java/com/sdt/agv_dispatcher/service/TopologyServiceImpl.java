package com.sdt.agv_dispatcher.service;

import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.sdt.agv_dispatcher.graph.AGVGraph;
import com.sdt.agv_dispatcher.graph.NeighborInfo;
import com.sdt.agv_dispatcher.service.TopologyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TopologyServiceImpl implements TopologyService {

    @Autowired
    private AGVGraph agvGraph;

    @Override
    public Edge getEdge(String edgeId) {
        return agvGraph.getEdge(edgeId);
    }

    @Override
    public Node getNode(String nodeId) {
        return agvGraph.getNode(nodeId);
    }

    @Override
    public List<String> findAlternativePaths(String fromNode, String toNode, String blockedResource) {
        List<String> alternatives = new ArrayList<>();
        List<NeighborInfo> neighbors = agvGraph.getNeighbors(fromNode);

        for (NeighborInfo neighbor : neighbors) {
            if (!neighbor.getEdgeId().equals(blockedResource) &&
                    !neighbor.getNodeId().equals(blockedResource)) {
                alternatives.add(neighbor.getNodeId());
            }
        }
        return alternatives;
    }

    @Override
    public boolean isBidirectionalEdge(Edge edge) {
        if (edge == null) return false;
        String reverseId = edge.getTargetId() + "_" + edge.getSourceId();
        return agvGraph.getEdge(reverseId) != null;
    }

    @Override
    public Edge getOppositeEdge(String edgeId) {
        Edge current = agvGraph.getEdge(edgeId);
        if (current == null) return null;
        String oppositeId = current.getTargetId() + "_" + current.getSourceId();
        return agvGraph.getEdge(oppositeId);
    }

    @Override
    public double calculateDistance(String node1, String node2) {
        Node n1 = agvGraph.getNode(node1);
        Node n2 = agvGraph.getNode(node2);
        if (n1 == null || n2 == null) return Double.MAX_VALUE;

        double dx = n2.getX() - n1.getX();
        double dy = n2.getY() - n1.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
}