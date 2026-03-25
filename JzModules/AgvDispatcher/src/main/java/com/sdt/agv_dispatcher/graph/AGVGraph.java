package com.sdt.agv_dispatcher.graph;


import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class AGVGraph {

    // 节点和边存储
    private final Map<String, Node> nodes = new ConcurrentHashMap<>();
    private final Map<String, Edge> edges = new ConcurrentHashMap<>();
    private final Map<String, Map<String, EdgeInfo>> adjacencyList = new ConcurrentHashMap<>();

    // 图结构缓存
    @Getter
    private volatile boolean isGraphBuilt = false;
    private final Object lock = new Object();

    @PostConstruct
    public void init() {
    }

    /**
     * 添加节点
     */
    public void addNode(Node node) {
        synchronized (lock) {
            nodes.put(node.getId(), node);
            adjacencyList.putIfAbsent(node.getId(), new HashMap<>());
            isGraphBuilt = false;
        }
    }

    /**
     * 添加边
     */
    public void addEdge(Edge edge) {
        synchronized (lock) {
            Node source = nodes.get(edge.getSourceId());
            Node target = nodes.get(edge.getTargetId());

            if (source == null || target == null) {
                log.warn("边 {} 的节点不存在: {} -> {}",
                        edge.getId(), edge.getSourceId(), edge.getTargetId());
                return;
            }

            edges.put(edge.getId(), edge);

            // 计算实际权重（考虑距离和类型）
            double weight = calculateEdgeWeight(edge, source, target);

            // 添加到邻接表
            EdgeInfo forwardInfo = new EdgeInfo(edge, weight);
            adjacencyList.get(edge.getSourceId()).put(edge.getTargetId(), forwardInfo);

            // 如果是双向边，添加反向
            if (edge.getDirection() == Edge.EdgeDirection.BIDIRECTIONAL) {
                EdgeInfo backwardInfo = new EdgeInfo(edge, weight);
                adjacencyList.get(edge.getTargetId()).put(edge.getSourceId(), backwardInfo);
            }

            isGraphBuilt = false;
        }
    }

    /**
     * 批量构建图
     */
    public void buildGraph(List<Node> nodeList, List<Edge> edgeList) {
        synchronized (lock) {
            nodes.clear();
            edges.clear();
            adjacencyList.clear();

            nodeList.forEach(this::addNode);
            edgeList.forEach(this::addEdge);

            isGraphBuilt = true;
            log.info("图构建完成，节点数: {}, 边数: {}", nodes.size(), edges.size());
        }
    }

    /**
     * 计算边的权重
     */
    private double calculateEdgeWeight(Edge edge, Node source, Node target) {
        double baseWeight = edge.getWeight();
        if (baseWeight <= 0) {
            // 如果没有设置权重，计算欧几里得距离
            baseWeight = Math.sqrt(
                    Math.pow(target.getX() - source.getX(), 2) +
                            Math.pow(target.getY() - source.getY(), 2)
            );
        }

        // 根据边类型调整权重
        double typeMultiplier = 1.0;
        switch (edge.getType()) {
            case CURVE:
                typeMultiplier = 1.2;  // 转弯成本更高
                break;
            case ELEVATION:
                typeMultiplier = 1.5;  // 坡道成本更高
                break;
            default:
                typeMultiplier = 1.0;
        }

        // 考虑优先级（优先级越高，权重越低）
        double priorityFactor = 1.0 / edge.getPriority();

        return baseWeight * typeMultiplier * priorityFactor;
    }

    /**
     * 获取节点
     */
    public Node getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * 获取边
     */
    public Edge getEdge(String edgeId) {
        return edges.get(edgeId);
    }

    /**
     * 获取相邻节点
     */
    public List<NeighborInfo> getNeighbors(String nodeId) {
        List<NeighborInfo> neighbors = new ArrayList<>();
        Map<String, EdgeInfo> adjacent = adjacencyList.get(nodeId);

        if (adjacent != null) {
            adjacent.forEach((neighborId, edgeInfo) -> {
                if (edgeInfo.getEdge().getEnabled()) {
                    // 关键：双向通道，或者通道起点==节点Id，则加入邻居列表
                    if (edgeInfo.getEdge().getDirection() == Edge.EdgeDirection.BIDIRECTIONAL || edgeInfo.getEdge().getSourceId() == nodeId) {
                        neighbors.add(new NeighborInfo(neighborId, edgeInfo.getWeight(), edgeInfo.getEdge().getId()));
                    }
                }
            });
        }

        return neighbors;
    }

    /**
     * 验证两点是否可达
     */
    public boolean isReachable(String startId, String endId) {
        if (!nodes.containsKey(startId) || !nodes.containsKey(endId)) {
            return false;
        }

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.offer(startId);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            if (current.equals(endId)) {
                return true;
            }

            if (!visited.contains(current)) {
                visited.add(current);
                List<NeighborInfo> neighbors = getNeighbors(current);
                neighbors.forEach(neighbor -> {
                    if (!visited.contains(neighbor.getNodeId())) {
                        queue.offer(neighbor.getNodeId());
                    }
                });
            }
        }

        return false;
    }

    /**
     * 获取所有节点
     */
    public Collection<Node> getAllNodes() {
        return nodes.values();
    }

    /**
     * 获取所有边
     */
    public Collection<Edge> getAllEdges() {
        return edges.values();
    }

}
