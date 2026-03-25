package com.sdt.agv_dispatcher.scheduler;

import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.jizhi.vda5050.domain.PathResult;
import com.sdt.agv_dispatcher.domain.Task;
import com.sdt.agv_dispatcher.graph.AGVGraph;
import com.sdt.agv_dispatcher.graph.NeighborInfo;
import com.sdt.agv_dispatcher.service.PathReplanningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class PathPlanner implements PathReplanningService {

    @Autowired
    private AGVGraph agvGraph;

    /**
     * Dijkstra算法 - 最短路径
     */
    public PathResult findShortestPath(String startId, String endId, String agvId) {
        long startTime = System.currentTimeMillis();
        // 验证节点存在
        if (!agvGraph.getNode(startId).getEnabled() || !agvGraph.getNode(endId).getEnabled()) {
            log.error("节点未启用: {} -> {}", startId, endId);
            return null;
        }
        // 初始化数据结构
        Map<String, Double> distances = new HashMap<>();
        Map<String, String> previousNodes = new HashMap<>();      // 前驱节点
        Map<String, String> previousEdges = new HashMap<>();
        Set<String> unvisited = new HashSet<>();

        // 1. 初始化所有节点距离为无穷大
        agvGraph.getAllNodes().forEach(node -> {
            if (node.getEnabled()) {
                String nodeId = node.getId();
                distances.put(nodeId, Double.MAX_VALUE);
                previousNodes.put(nodeId, null);
                previousEdges.put(nodeId, null);
                unvisited.add(nodeId);
            }
        });
        // 2. 从起点开始，距离为0
        distances.put(startId, 0.0);

        // 3. 每次从未访问节点中选择距离最小的节点
        while (!unvisited.isEmpty()) {
            // 找到当前距离最小的节点
            String currentNode = null;
            double minDistance = Double.MAX_VALUE;

            for (String nodeId : unvisited) {
                double dist = distances.get(nodeId);
                if (dist < minDistance) {
                    minDistance = dist;
                    currentNode = nodeId;
                }
            }

            if (currentNode == null || currentNode.equals(endId)) {
                break;
            }

            unvisited.remove(currentNode);

            // 4. 更新邻居节点的距离
            List<NeighborInfo> neighbors = agvGraph.getNeighbors(currentNode);

            for (NeighborInfo neighbor : neighbors) {
                if (unvisited.contains(neighbor.getNodeId())) {
                    double alt = distances.get(currentNode) + neighbor.getWeight();

                    if (alt < distances.get(neighbor.getNodeId())) {
                        distances.put(neighbor.getNodeId(), alt);
                        previousNodes.put(neighbor.getNodeId(), currentNode);
                        previousEdges.put(neighbor.getNodeId(), neighbor.getEdgeId());
                    }
                }
            }
        }

        // 构建路径
        PathResult path = buildPath(startId, endId, previousNodes, previousEdges, distances, agvId);

        long endTime = System.currentTimeMillis();
        log.debug("Dijkstra算法耗时: {}ms", endTime - startTime);
        return path;
    }

    /**
     * A*算法 - 启发式搜索
     */
    public PathResult findOptimalPath(String startId, String endId, String agvId) {
        long startTime = System.currentTimeMillis();

        Node startNode = agvGraph.getNode(startId);
        Node endNode = agvGraph.getNode(endId);

        if (startNode == null || endNode == null ||
                !startNode.getEnabled() || !endNode.getEnabled()) {
            return null;
        }

        // 初始化
        Set<String> openSet = new HashSet<>();
        openSet.add(startId);

        Map<String, String> cameFrom = new HashMap<>();
        Map<String, String> cameFromEdge = new HashMap<>();

        // 1. 初始化gScore和fScore
        Map<String, Double> gScore = new HashMap<>();  // 实际代价
        Map<String, Double> fScore = new HashMap<>();  // 估计总代价

        // 初始化所有节点的分数
        agvGraph.getAllNodes().forEach(node -> {
            if (node.getEnabled()) {
                gScore.put(node.getId(), Double.MAX_VALUE);
                fScore.put(node.getId(), Double.MAX_VALUE);
            }
        });

        gScore.put(startId, 0.0);
        fScore.put(startId, heuristic(startNode, endNode));  // 启发式估计

        while (!openSet.isEmpty()) {
            // 找到fScore最小的节点
            String current = null;
            double minFScore = Double.MAX_VALUE;

            for (String nodeId : openSet) {
                double score = fScore.get(nodeId);
                if (score < minFScore) {
                    minFScore = score;
                    current = nodeId;
                }
            }

            assert current != null;
            if (current.equals(endId)) {
                PathResult path = buildPath(startId, endId, cameFrom, cameFromEdge, gScore, agvId);
                long endTime = System.currentTimeMillis();
                log.debug("A*算法耗时: {}ms", endTime - startTime);
                log.debug("生成的path: {}", path);
                return path;
            }

            openSet.remove(current);

            // 处理邻居节点
            List<NeighborInfo> neighbors = agvGraph.getNeighbors(current);

            for (NeighborInfo neighbor : neighbors) {
                // 计算新的gScore（实际代价）
                double tentativeGScore = gScore.get(current) + neighbor.getWeight();

                if (tentativeGScore < gScore.get(neighbor.getNodeId())) {
                    cameFrom.put(neighbor.getNodeId(), current);
                    cameFromEdge.put(neighbor.getNodeId(), neighbor.getEdgeId());
                    gScore.put(neighbor.getNodeId(), tentativeGScore);

                    Node neighborNode = agvGraph.getNode(neighbor.getNodeId());
                    fScore.put(neighbor.getNodeId(), tentativeGScore + heuristic(neighborNode, endNode));

                    if (!openSet.contains(neighbor.getNodeId())) {
                        openSet.add(neighbor.getNodeId());
                    }
                }
            }
        }

        return null;
    }




    /**
     * 启发式函数 - 欧几里得距离
     */
    private double heuristic(Node from, Node to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 构建路径结果
     */
    private PathResult buildPath(String startId, String endId, Map<String, String> previousNodes,
                                 Map<String, String> previousEdges, Map<String, Double> distances, String agvId) {
        if (distances.get(endId) == Double.MAX_VALUE) {
            return null;
        }

        // 反向构建路径
        List<String> nodeSequence = new ArrayList<>();
        List<String> edgeSequence = new ArrayList<>();
        List<PathResult.PathSegment> segments = new ArrayList<>();

        String currentNode = endId;

        while (currentNode != null && !currentNode.equals(startId)) {
            nodeSequence.add(0, currentNode);

            String edgeId = previousEdges.get(currentNode);
            if (edgeId != null) {
                edgeSequence.add(0, edgeId);
            }

            String prevNode = previousNodes.get(currentNode);
            if (prevNode != null) {
                // 创建路径段
                PathResult.PathSegment segment = new PathResult.PathSegment();
                segment.setFromNodeId(prevNode);
                segment.setToNodeId(currentNode);
                segment.setEdgeId(edgeId);
                segment.setDistance(distances.get(currentNode) - distances.get(prevNode));
                segment.setEstimatedTime(calculateSegmentTime(segment));
                segment.setAction("MOVE");
                segments.add(0, segment);
            }

            currentNode = prevNode;
        }

        if (currentNode == null) {
            return null;
        }

        nodeSequence.add(0, startId);
        PathResult path = new PathResult();
        path.setAgvId(agvId);
        path.setNodeSequence(nodeSequence);
        path.setEdgeSequence(edgeSequence);
        path.setTotalDistance(distances.get(endId));
        path.setEstimatedTime(calculateTotalTime(distances.get(endId)));
        path.setEnergyCost(calculateEnergyCost(distances.get(endId)));
        path.setSegments(segments);
        return path;
    }

    /**
     * 计算分段预计时间
     */
    private Double calculateSegmentTime(PathResult.PathSegment segment) {
        Edge edge = agvGraph.getEdge(segment.getEdgeId());
        double speed = edge != null ? edge.getType() == Edge.EdgeType.STRAIGHT ? edge.getMaxSpeed() * 0.7 :
                edge.getMaxSpeed() * 0.5 : 0.7;
        //不可能一直走最大速度，先用0.5系数，算成平均速度
        return segment.getDistance() / speed;
    }

    /**
     * 计算总预计时间
     */
    private Double calculateTotalTime(double totalDistance) {
        // 假设平均速度1.2m/s，加上每个节点的停留时间
        double travelTime = totalDistance / 1.2 * 0.5;  //速度适当放小点，因为不可能一直高速移动
        return travelTime;
    }

    /**
     * 计算能耗
     */
    private Double calculateEnergyCost(double totalDistance) {
        // 简单能耗模型：每米消耗0.1单位能量
        return totalDistance * 0.1;
    }

    /**
     * 多路径规划（备用路径）
     */
    public List<PathResult> findAlternativePaths(String startId, String endId, String agvId, int maxPaths) {

        List<PathResult> paths = new ArrayList<>();
        // 主要路径
        PathResult primaryPath = findOptimalPath(startId, endId, agvId);
        if (primaryPath != null) {
            paths.add(primaryPath);
        }

        // 这里可以实现Yen's K最短路径算法
        // 简化为基于不同算法生成备用路径
        if (paths.size() < maxPaths) {
            PathResult secondaryPath = findShortestPath(startId, endId, agvId);
            if (secondaryPath != null && !secondaryPath.equals(primaryPath)) {
                paths.add(secondaryPath);
            }
        }

        return paths;
    }

    /**
     * 实时更新图（处理临时障碍）
     */
    public void updateEdgeWeight(String edgeId, double newWeight) {
        Edge edge = agvGraph.getEdge(edgeId);
        if (edge != null) {
            edge.setWeight(newWeight);
            // 这里需要更新邻接表中的权重信息
            // 在实际实现中，AGVGraph需要提供更新权重的方法
        }
    }


    /**
     * 绕过指定资源的替代路径规划
     */
    public PathResult findAlternativePathAvoidingResources(String startId, String endId,
                                                           Set<String> avoidNodes,
                                                           Set<String> avoidEdges,
                                                           String agvId, String taskId) {
        long startTime = System.currentTimeMillis();

        // 验证节点存在
        if (!agvGraph.getNode(startId).getEnabled() || !agvGraph.getNode(endId).getEnabled()) {
            log.error("节点未启用: {} -> {}", startId, endId);
            return null;
        }

        // 使用修改的A*算法，避免特定资源
        Node startNode = agvGraph.getNode(startId);
        Node endNode = agvGraph.getNode(endId);

        if (startNode == null || endNode == null) {
            return null;
        }

        // 初始化
        Set<String> openSet = new HashSet<>();
        openSet.add(startId);

        Map<String, String> cameFrom = new HashMap<>();
        Map<String, String> cameFromEdge = new HashMap<>();

        Map<String, Double> gScore = new HashMap<>();
        Map<String, Double> fScore = new HashMap<>();

        // 初始化所有节点的分数
        agvGraph.getAllNodes().forEach(node -> {
            if (node.getEnabled()) {
                gScore.put(node.getId(), Double.MAX_VALUE);
                fScore.put(node.getId(), Double.MAX_VALUE);
            }
        });

        gScore.put(startId, 0.0);
        fScore.put(startId, heuristic(startNode, endNode));

        while (!openSet.isEmpty()) {
            // 找到fScore最小的节点
            String current = null;
            double minFScore = Double.MAX_VALUE;

            for (String nodeId : openSet) {
                double score = fScore.get(nodeId);
                if (score < minFScore) {
                    minFScore = score;
                    current = nodeId;
                }
            }

            assert current != null;
            if (current.equals(endId)) {
                PathResult path = buildPath(startId, endId, cameFrom, cameFromEdge, gScore, agvId);
                long endTime = System.currentTimeMillis();
                log.debug("绕开资源A*算法耗时: {}ms", endTime - startTime);
                return path;
            }

            openSet.remove(current);

            // 处理邻居节点
            List<NeighborInfo> neighbors = agvGraph.getNeighbors(current);

            for (NeighborInfo neighbor : neighbors) {
                // 检查是否要避免此节点
                if (avoidNodes != null && avoidNodes.contains(neighbor.getNodeId())) {
                    continue; // 跳过要避免的节点
                }

                // 检查是否要避免此边
                if (avoidEdges != null && avoidEdges.contains(neighbor.getEdgeId())) {
                    continue; // 跳过要避免的边
                }

                // 增加惩罚项：如果资源被占用，增加权重
                double penalty = 0.0;

                // 计算新的G分数
                double tentativeGScore = gScore.get(current) + neighbor.getWeight() + penalty;

                if (tentativeGScore < gScore.get(neighbor.getNodeId())) {
                    cameFrom.put(neighbor.getNodeId(), current);
                    cameFromEdge.put(neighbor.getNodeId(), neighbor.getEdgeId());
                    gScore.put(neighbor.getNodeId(), tentativeGScore);

                    Node neighborNode = agvGraph.getNode(neighbor.getNodeId());
                    fScore.put(neighbor.getNodeId(), tentativeGScore + heuristic(neighborNode, endNode));

                    openSet.add(neighbor.getNodeId());
                }
            }
        }

        return null;
    }


    @Override
    public Optional<PathResult> replanPath(String agvId, Task task, String... blockedResources) {
        if (task == null || task.getPathResult() == null) {
            log.warn("任务或路径为空，无法重新规划: agvId={}", agvId);
            return Optional.empty();
        }

        // 获取当前位置和目标
        String currentNode = task.getCurrentNodeId();
        if (currentNode == null || currentNode.isEmpty()) {
            currentNode = task.getPathResult().getNodeSequence().get(0);
        }

        String targetNode = task.getEndNodeId();
        if (targetNode == null || targetNode.isEmpty()) {
            List<String> nodes = task.getPathResult().getNodeSequence();
            targetNode = nodes.get(nodes.size() - 1);
        }

        // 解析需要避开的资源
        Set<String> avoidNodes = new HashSet<>();
        Set<String> avoidEdges = new HashSet<>();

        for (String resource : blockedResources) {
            if (resource != null && !resource.isEmpty()) {
                // 根据你的资源ID命名规则判断类型
                if (resource.toLowerCase().contains("edge")) {
                    avoidEdges.add(resource);
                } else {
                    avoidNodes.add(resource);
                }
            }
        }

        log.info("AGV {} 重新规划路径: {} -> {}, 避开节点: {}, 避开边: {}",
                agvId, currentNode, targetNode, avoidNodes, avoidEdges);

        // 使用绕开资源的A*算法
        PathResult newPath = findAlternativePathAvoidingResources(
                currentNode, targetNode, avoidNodes, avoidEdges, agvId, task.getId()
        );

        return Optional.ofNullable(convertToServicePathResult(newPath));
    }

    @Override
    public Optional<PathResult> findBypassPath(String agvId, String blockedNode, Task task) {
        if (task == null || task.getPathResult() == null) {
            return Optional.empty();
        }

        String currentNode = task.getCurrentNodeId();
        if (currentNode == null || currentNode.isEmpty()) {
            currentNode = task.getPathResult().getNodeSequence().get(0);
        }

        String targetNode = task.getEndNodeId();
        if (targetNode == null || targetNode.isEmpty()) {
            List<String> nodes = task.getPathResult().getNodeSequence();
            targetNode = nodes.get(nodes.size() - 1);
        }

        log.info("AGV {} 寻找绕过节点 {} 的路径: {} -> {}", agvId, blockedNode, currentNode, targetNode);

        Set<String> avoidNodes = new HashSet<>();
        avoidNodes.add(blockedNode);

        PathResult newPath = findAlternativePathAvoidingResources(
                currentNode, targetNode, avoidNodes, null, agvId, task.getId()
        );

        return Optional.ofNullable(convertToServicePathResult(newPath));
    }

    @Override
    public Optional<PathResult> findAlternativeRoute(String agvId, Task task) {
        if (task == null || task.getPathResult() == null) {
            return Optional.empty();
        }

        String startId = task.getCurrentNodeId();
        if (startId == null || startId.isEmpty()) {
            startId = task.getPathResult().getNodeSequence().get(0);
        }

        String endId = task.getEndNodeId();
        if (endId == null || endId.isEmpty()) {
            List<String> nodes = task.getPathResult().getNodeSequence();
            endId = nodes.get(nodes.size() - 1);
        }

        log.info("AGV {} 寻找完全不同的替代路线: {} -> {}", agvId, startId, endId);

        // 获取原始路径的所有边，全部避开
        Set<String> avoidEdges = new HashSet<>();
        if (task.getPathResult().getEdgeSequence() != null) {
            avoidEdges.addAll(task.getPathResult().getEdgeSequence());
        }

        // 尝试避开所有原路径的边
        PathResult newPath = findAlternativePathAvoidingResources(
                startId, endId, null, avoidEdges, agvId, task.getId()
        );

        // 如果失败，尝试使用Dijkstra算法
        if (newPath == null) {
            newPath = findShortestPath(startId, endId, agvId);
        }

        // 如果还是失败，尝试A*算法
        if (newPath == null) {
            newPath = findOptimalPath(startId, endId, agvId);
        }

        return Optional.ofNullable(convertToServicePathResult(newPath));
    }

    /**
     * 将 scheduler 包的 PathResult 转换为 service 包的 PathResult
     */
    private PathResult convertToServicePathResult(PathResult plannerPath) {
        if (plannerPath == null) return null;

        PathResult result = new PathResult();
        result.setNodeSequence(plannerPath.getNodeSequence());
        result.setEdgeSequence(plannerPath.getEdgeSequence());
        result.setTotalDistance(plannerPath.getTotalDistance());
        result.setEstimatedTime(plannerPath.getEstimatedTime());
        return result;
    }

}
