package com.sdt.agv_dispatcher.conflict;

import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.PathResult;
import com.sdt.agv_dispatcher.domain.Task;
import com.sdt.agv_dispatcher.scheduler.AgvTaskDispatcher;
import com.sdt.agv_dispatcher.service.AgvManagerService;
import com.sdt.agv_dispatcher.service.PathPredictionService;
import com.sdt.agv_dispatcher.service.RedisResourceLockService;
import com.sdt.agv_dispatcher.service.TopologyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 增强版冲突检测引擎
 */
@Component
@Slf4j
public class EnhancedConflictDetector {

    @Autowired
    private AgvTaskDispatcher agvTaskDispatcher;

    @Autowired
    private TopologyService topologyService;

    @Autowired
    private PathPredictionService pathPredictionService;

    @Autowired
    private AgvManagerService agvManagerService;

    /**
     * 多维度冲突检测
     */
    public ConflictAnalysisResult analyzeConflict(
            String agvId,
            RedisResourceLockService.ResourceLockInfo requestedResource,
            String currentHolder) {

        ConflictAnalysisResult result = new ConflictAnalysisResult();
        AgvStatus myStatus = agvManagerService.getAgvStatus(agvId);
        AgvStatus otherStatus = agvManagerService.getAgvStatus(currentHolder);

        // 1. 基础信息收集
        result.setMyAgvId(agvId);
        result.setOtherAgvId(currentHolder);
        result.setResourceId(requestedResource.getResourceId());
        result.setResourceType(requestedResource.getType());
        result.setTimestamp(LocalDateTime.now());

        // 2. 方向关系分析
        DirectionRelation direction = analyzeDirectionRelation(
                agvId, currentHolder, requestedResource);
        result.setDirectionRelation(direction);

        // 路径交叉 - 从Task获取路径
        PathIntersection intersection = analyzePathIntersection(agvId, currentHolder);
        result.setPathIntersection(intersection);

        // 4. 死锁风险检测
        DeadlockRisk deadlockRisk = detectDeadlockRisk(agvId, currentHolder);
        result.setDeadlockRisk(deadlockRisk);

        // 5. 综合判断冲突类型和严重级别
        ConflictType type = determineConflictType(result);
        ConflictSeverity severity = calculateSeverity(result, type);

        result.setConflictType(type);
        result.setSeverity(severity);

        // 6. 计算预计等待时间
        Duration estimatedWait = estimateWaitTime(result);
        result.setEstimatedWaitTime(estimatedWait);

        return result;
    }

    /**
     * 方向关系分析：判断是否对向行驶
     */
    private DirectionRelation analyzeDirectionRelation(
            String agvId, String otherAgvId, RedisResourceLockService.ResourceLockInfo resource) {

        AgvStatus myStatus = agvManagerService.getAgvStatus(agvId);
        AgvStatus otherStatus = agvManagerService.getAgvStatus(otherAgvId);

        String myEdgeId = myStatus.getLastReportEdgeId();
        String otherEdgeId = otherStatus.getLastReportEdgeId();

        // 如果都在边上，检查是否同一条边的相反方向
        if (myEdgeId != null && otherEdgeId != null) {
            Edge myEdge = topologyService.getEdge(myEdgeId);
            Edge otherEdge = topologyService.getEdge(otherEdgeId);

            if (myEdge != null && otherEdge != null) {
                // 检查对向边
                if (isOppositeDirection(myEdge, otherEdge)) {
                    return DirectionRelation.HEAD_ON_APPROACHING;
                }
                if (myEdgeId.equals(otherEdgeId)) {
                    return DirectionRelation.SAME_DIRECTION;
                }
            }
        }

        // 检查路径交叉
        Set<String> myFutureNodes = pathPredictionService.predictFutureNodes(agvId, 3);
        Set<String> otherFutureNodes = pathPredictionService.predictFutureNodes(otherAgvId, 3);

        if (Collections.disjoint(myFutureNodes, otherFutureNodes)) {
            return DirectionRelation.DIVERGENT; // 路径分离，无冲突
        }

        return DirectionRelation.CONVERGENT; // 路径汇聚
    }

    private PathIntersection analyzePathIntersection(String agvId, String otherAgvId) {
        PathIntersection result = new PathIntersection();
        Task myTask = agvTaskDispatcher.getAgvCurrentTask(agvId);
        Task otherTask = agvTaskDispatcher.getAgvCurrentTask(otherAgvId);

        if (myTask == null || otherTask == null ||
                myTask.getPathResult() == null || otherTask.getPathResult() == null) {
            result.setHasIntersection(false);
            return result;
        }

        PathResult myPath = myTask.getPathResult();
        PathResult otherPath = otherTask.getPathResult();

        Set<String> myNodes = new HashSet<>(myPath.getNodeSequence());
        Set<String> otherNodes = new HashSet<>(otherPath.getNodeSequence());

        Set<String> common = new HashSet<>(myNodes);
        common.retainAll(otherNodes);

        result.setHasIntersection(!common.isEmpty());
        result.setCommonNodes(common);

        if (!common.isEmpty()) {
            result.setConflictPoint(common.iterator().next());
            result.setType(PathIntersection.IntersectionType.NODE_SHARE);
        }

        return result;
    }

    /**
     * 死锁风险检测：使用等待图算法
     */
    private DeadlockRisk detectDeadlockRisk(String agvId, String blockingAgvId) {
        WaitGraph waitGraph = new WaitGraph();
        if (waitGraph.wouldCreateCycle(agvId, blockingAgvId)) {
            return DeadlockRisk.TWO_WAY_DEADLOCK;
        }
        return DeadlockRisk.NONE;

    }

    private ConflictType determineConflictType(ConflictAnalysisResult result) {
        // 根据方向关系、路径交叉、死锁风险等综合判断
        if (result.getDeadlockRisk() != DeadlockRisk.NONE) {
            return ConflictType.LOOP_DEADLOCK_RISK;
        }
        if (result.getDirectionRelation() == DirectionRelation.HEAD_ON_APPROACHING) {
            return ConflictType.HEAD_ON;
        }
        if (result.getPathIntersection() != null && result.getPathIntersection().isHasIntersection()) {
            return ConflictType.CROSSING;
        }
        return ConflictType.NODE_OCCUPIED;
    }

    private ConflictSeverity calculateSeverity(ConflictAnalysisResult result, ConflictType type) {
        // 根据冲突类型、距离、优先级等计算严重级别
        switch (type) {
            case HEAD_ON:
                return ConflictSeverity.HIGH;
            case LOOP_DEADLOCK_RISK:
                return ConflictSeverity.CRITICAL;
            case EDGE_OCCUPIED:
                return ConflictSeverity.MODERATE;
            default:
                return ConflictSeverity.LOW;
        }
    }

    private Duration estimateWaitTime(ConflictAnalysisResult result) {
        // 根据速度、距离等估算等待时间
        // 这里只是示例，返回固定值
        return Duration.ofSeconds(5);
    }

    private boolean isBidirectionalEdge(Edge edge) {
        // 假设 Edge 有 isBidirectional() 方法
        return edge != null && edge.getDirection() == Edge.EdgeDirection.BIDIRECTIONAL;
    }


    /**
     * 判断两条边是否为对向行驶（在同一条物理通道上相向而行）
     */
    private boolean isOppositeDirection(Edge myEdge, Edge otherEdge) {
        // 1. 首先必须是双向边
        if (myEdge.getDirection() != Edge.EdgeDirection.BIDIRECTIONAL ||
                otherEdge.getDirection() != Edge.EdgeDirection.BIDIRECTIONAL) {
            return false; // 单向边不可能对向行驶
        }

        // 2. 判断是否为同一条物理通道（起点终点互换）
        // 边A: A->B, 边B: B->A  => 对向
        boolean isOpposite = myEdge.getSourceId().equals(otherEdge.getTargetId()) &&
                myEdge.getTargetId().equals(otherEdge.getSourceId());

        return isOpposite;
    }

    /**
     * 更通用的判断：判断两个AGV在边上是否为对向行驶
     */
    private boolean isHeadOnApproach(String myEdgeId, String otherEdgeId,
                                     String myFromNode, String otherFromNode) {
        Edge myEdge = topologyService.getEdge(myEdgeId);
        Edge otherEdge = topologyService.getEdge(otherEdgeId);

        if (myEdge == null || otherEdge == null) return false;

        // 情况1：同一条边，方向相反（通过fromNode判断）
        if (myEdgeId.equals(otherEdgeId)) {
            // 如果都在同一条边上，检查行驶方向
            // 通过比较当前节点和上一节点来判断实际行驶方向
            return !myFromNode.equals(otherFromNode); // 从不同方向进入同一条边
        }

        // 情况2：互为反向边（A->B 和 B->A）
        return isOppositeDirection(myEdge, otherEdge);
    }
}
