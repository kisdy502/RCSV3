package com.sdt.agv_dispatcher.conflict;

import com.jizhi.vda5050.agv.AgvState;
import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.jizhi.vda5050.domain.PathResult;
import com.sdt.agv_dispatcher.domain.Task;
import com.sdt.agv_dispatcher.scheduler.AgvTaskDispatcher;
import com.sdt.agv_dispatcher.scheduler.PathPlanner;
import com.sdt.agv_dispatcher.service.AgvManagerService;
import com.sdt.agv_dispatcher.service.RedisResourceLockService;
import com.sdt.agv_dispatcher.service.TopologyService;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * 智能冲突解决策略引擎
 */
@Component
@Slf4j
public class IntelligentConflictResolver {

    @Autowired
    private PathPlanner pathPlanner;

    @Autowired
    private AgvTaskDispatcher taskDispatcher;

    @Autowired
    private AgvManagerService agvManagerService;

    @Autowired
    private RedisResourceLockService lockService;

    @Autowired
    private TopologyService topologyService;

    /**
     * 主解决入口：根据冲突分析结果选择策略
     */
    public ResolutionStrategy resolve(ConflictAnalysisResult conflict, Task myTask) {
        log.info("解决冲突: {} vs {} on {}({}),冲突类型:{}", conflict.getMyAgvId(), conflict.getOtherAgvId(),
                conflict.getResourceId(), conflict.getResourceType(), conflict.getConflictType());

        // 策略选择树
        return switch (conflict.getConflictType()) {
            case HEAD_ON -> resolveHeadOnConflict(conflict, myTask);
            case CROSSING -> resolveCrossingConflict(conflict, myTask);
            case NODE_OCCUPIED -> resolveNodeOccupied(conflict, myTask);
            case EDGE_OCCUPIED -> resolveEdgeOccupied(conflict, myTask);
            case LOOP_DEADLOCK_RISK -> resolveDeadlockRisk(conflict, myTask);
            case FOLLOWING_TOO_CLOSE -> resolveFollowingConflict(conflict, myTask);
            default -> resolveGenericConflict(conflict, myTask);
        };
    }


    private ResolutionStrategy resolveCrossingConflict(ConflictAnalysisResult conflict, Task myTask) {
        double myDistance = conflict.getPathIntersection() != null ?
                conflict.getPathIntersection().getMyDistanceToConflict() : 0;
        double otherDistance = conflict.getPathIntersection() != null ?
                conflict.getPathIntersection().getOtherDistanceToConflict() : Double.MAX_VALUE;

        if (myDistance < otherDistance) {
            return ResolutionStrategy.proceedWithCaution(0.6, "距离交叉点更近，优先通过");
        } else if (myDistance > otherDistance) {
            return ResolutionStrategy.waitFor(3);
        } else {
            return resolveByPriority(conflict, myTask);
        }
    }

    /**
     * 对向行驶冲突解决：最高优先级
     * 策略：1. 尝试重新规划路径 2. 优先级低的退让 3. 双方协调
     */
    private ResolutionStrategy resolveHeadOnConflict(ConflictAnalysisResult conflict, Task myTask) {
        String myAgvId = conflict.getMyAgvId();
        String otherAgvId = conflict.getOtherAgvId();

        boolean canReplan = canReplanPath(myAgvId, conflict.getResourceId());
        if (canReplan) {
            // 修正：使用 findAlternativeRoute 替代 findAlternativePath
            Optional<PathResult> alternativePath =
                    pathPlanner.findAlternativeRoute(myAgvId, myTask);
            if (alternativePath.isPresent()) {
                return ResolutionStrategy.replan(alternativePath.get(), "对向冲突，找到替代路径");
            }
        }

        int myPriority = myTask.getPriority();
        Task otherTask = taskDispatcher.getAgvCurrentTask(otherAgvId);
        int otherPriority = otherTask != null ? otherTask.getPriority() : 0;

        if (myPriority > otherPriority) {
            notifyAgvToYield(otherAgvId, conflict.getResourceId());
            return ResolutionStrategy.proceedWithCaution(0.5, "优先级高，继续行驶并通知对方避让");
        } else if (myPriority < otherPriority) {
            return ResolutionStrategy.yieldAndWait(otherAgvId,
                    conflict.getEstimatedWaitTime().plusSeconds(2));
        } else {
            return resolveTieBreaker(conflict, myTask, otherTask);
        }
    }


    private ResolutionStrategy resolveGenericConflict(ConflictAnalysisResult conflict, Task myTask) {
        return resolveByPriority(conflict, myTask);
    }

    private ResolutionStrategy resolveByPriority(ConflictAnalysisResult conflict, Task myTask) {
        String myAgvId = conflict.getMyAgvId();
        String otherAgvId = conflict.getOtherAgvId();

        int myPriority = myTask.getPriority();
        Task otherTask = taskDispatcher.getAgvCurrentTask(otherAgvId);
        int otherPriority = otherTask != null ? otherTask.getPriority() : 0;

        if (myPriority > otherPriority) {
            return ResolutionStrategy.proceedWithNotification();
        } else if (myPriority < otherPriority) {
            return ResolutionStrategy.waitFor(5);
        } else {
            return resolveTieBreaker(conflict, myTask, otherTask);
        }
    }

    /**
     * 节点被占用冲突：考虑是否可以"借道"通过
     */
    private ResolutionStrategy resolveNodeOccupied(ConflictAnalysisResult conflict, Task myTask) {
        String nodeId = conflict.getResourceId();
        String myAgvId = conflict.getMyAgvId();

        AgvStatus occupierStatus = agvManagerService.getAgvStatus(conflict.getOtherAgvId());

        // 修正：使用 getAgvState() 替代 getState()
        if (occupierStatus != null && occupierStatus.getAgvState() == AgvState.EXECUTING) {
            // 修正：移除 estimateOperationTime 和 findWaitPoint 调用，简化处理
            return ResolutionStrategy.waitFor(10);
        }

        if (occupierStatus != null && occupierStatus.getAgvState() == AgvState.MOVING) {
            if (canCoordinatePassing(conflict)) {
                return ResolutionStrategy.coordinateSpeed(0.3, 1.0, "协调通过");
            }
        }

        Optional<PathResult> bypassPath =
                pathPlanner.findBypassPath(myAgvId, nodeId, myTask);
        if (bypassPath.isPresent()) {
            return ResolutionStrategy.replan(bypassPath.get(), "绕过被占用节点");
        }

        return ResolutionStrategy.waitFor(5);
    }

    private ResolutionStrategy resolveEdgeOccupied(ConflictAnalysisResult conflict, Task myTask) {
        String edgeId = conflict.getResourceId();
        String myAgvId = conflict.getMyAgvId();

        Optional<PathResult> bypassPath =
                pathPlanner.findBypassPath(myAgvId, edgeId, myTask);
        if (bypassPath.isPresent()) {
            return ResolutionStrategy.replan(bypassPath.get(), "绕过被占用的边");
        }
        return resolveByPriority(conflict, myTask);
    }

    /**
     * 死锁风险解决：预防性处理
     */
    private ResolutionStrategy resolveDeadlockRisk(ConflictAnalysisResult conflict, Task myTask) {
        DeadlockRisk risk = conflict.getDeadlockRisk();
        String myAgvId = conflict.getMyAgvId();

        switch (risk) {
            case TWO_WAY_DEADLOCK -> {
                return resolveHeadOnConflict(conflict, myTask);
            }
            case LOOP_DEADLOCK -> {
                // 修正：移除 getWaitChain() 调用，直接构造等待链
                List<String> waitChain = new ArrayList<>();
                waitChain.add(myAgvId);
                waitChain.add(conflict.getOtherAgvId());

                String victimAgv = selectDeadlockVictim(waitChain);
                if (victimAgv.equals(myAgvId)) {
                    return ResolutionStrategy.releaseAndReplan("打破环路死锁");
                } else {
                    return ResolutionStrategy.waitFor(10);
                }
            }
            case LONG_WAIT_CHAIN -> {
                return ResolutionStrategy.releaseLastResource("缩短等待链");
            }
            default -> {
                return ResolutionStrategy.waitFor(3);
            }
        }
    }


    /**
     * 跟随冲突解决：保持安全距离
     */
    private ResolutionStrategy resolveFollowingConflict(ConflictAnalysisResult conflict, Task myTask) {
        String myAgvId = conflict.getMyAgvId();
        String frontAgvId = conflict.getOtherAgvId();

        AgvStatus myStatus = agvManagerService.getAgvStatus(myAgvId);
        AgvStatus frontStatus = agvManagerService.getAgvStatus(frontAgvId);

        if (myStatus == null || frontStatus == null) {
            return ResolutionStrategy.proceed();
        }

        // 修正：使用 calculateSafeDistance 和 calculateActualDistance 方法
        double safeDistance = calculateSafeDistance(
                myStatus.getVelocity() != null ? myStatus.getVelocity() : 0.0,
                frontStatus.getVelocity() != null ? frontStatus.getVelocity() : 0.0
        );
        double actualDistance = calculateActualDistance(myStatus, frontStatus);

        if (actualDistance < safeDistance * 0.5) {
            if (frontStatus.getVelocity() != null && frontStatus.getVelocity() < 0.1) {
                return ResolutionStrategy.stop("前车停止");
            } else {
                double targetSpeed = frontStatus.getVelocity() != null ?
                        frontStatus.getVelocity() * 0.8 : 0.5;
                return ResolutionStrategy.adjustSpeed(targetSpeed, "减速跟随");
            }
        }
        return ResolutionStrategy.proceed();
    }


    /**
     * Tie-breaker：优先级相同时的决策
     */
    private ResolutionStrategy resolveTieBreaker(ConflictAnalysisResult conflict, Task myTask, Task otherTask) {
        String myAgvId = conflict.getMyAgvId();
        String otherAgvId = conflict.getOtherAgvId();

        // 规则1：剩余路径短的让行（更快释放资源）
        int myRemaining = myTask.getPathResult().getRemainingNodes().size();
        int otherRemaining = otherTask.getPathResult().getRemainingNodes().size();

        if (myRemaining < otherRemaining) {
            return ResolutionStrategy.yieldAndWait(otherAgvId, Duration.ofSeconds(3));
        } else if (myRemaining > otherRemaining) {
            notifyAgvToYield(otherAgvId, conflict.getResourceId());
            return ResolutionStrategy.proceedWithCaution(0.6, "路径更短，优先通过");
        }

        // 规则2：AGV ID小的优先（确定性规则，避免循环）
        if (myAgvId.compareTo(otherAgvId) < 0) {
            return ResolutionStrategy.proceedWithCaution(0.7, "ID优先");
        } else {
            return ResolutionStrategy.yieldAndWait(otherAgvId, Duration.ofSeconds(3));
        }
    }
    private double calculateSafeDistance(double mySpeed, double frontSpeed) {
        double baseDistance = 2.0;
        double speedDiff = Math.abs(mySpeed - frontSpeed);
        return baseDistance + speedDiff * 2;
    }

    /**
     * 计算实际距离
     */
    private double calculateActualDistance(AgvStatus status1, AgvStatus status2) {
        if (status1 == null || status2 == null) return Double.MAX_VALUE;

        // 简化计算：使用节点距离估算
        String node1 = status1.getLastNodeId();
        String node2 = status2.getLastNodeId();

        if (node1 != null && node1.equals(node2)) {
            return 0.5;
        }
        return 5.0;
    }

    /**
     * 检查是否可以重新规划路径
     */
    private boolean canReplanPath(String agvId, String blockedResourceId) {
        // 修正：使用 Node 和 Edge 类型，而不是 Resource
        Node node = topologyService.getNode(blockedResourceId);
        Edge edge = topologyService.getEdge(blockedResourceId);

        if (node != null && isCriticalNode(blockedResourceId)) {
            return false;
        }

        AgvStatus status = agvManagerService.getAgvStatus(agvId);
        if (status == null) return false;

        // 修正：使用 getLastNodeId() 替代 getCurrentNodeId()
        String currentNode = status.getLastNodeId();
        String targetNode = getTargetNodeId(agvId);

        if (currentNode == null || targetNode == null) return false;

        List<String> alternativeRoutes = topologyService.findAlternativePaths(
                currentNode, targetNode, blockedResourceId);
        return !alternativeRoutes.isEmpty();
    }


    /**
     * 选择死锁牺牲者：选择影响最小的AGV释放资源
     */
    private String selectDeadlockVictim(List<String> waitChain) {
        // 策略：选择优先级最低、剩余任务最少、距离目标最近的AGV
        return waitChain.stream()
                .min(Comparator.comparingInt((String agvId) -> {
                    Task task = taskDispatcher.getAgvCurrentTask(agvId);
                    return task != null ? task.getPriority() : Integer.MAX_VALUE;
                }).thenComparingInt(agvId -> {
                    Task task = taskDispatcher.getAgvCurrentTask(agvId);
                    return task != null ? task.getPathResult().getRemainingNodes().size() : Integer.MAX_VALUE;
                }))
                .orElse(waitChain.get(0));
    }

    /**
     * 判断是否为关键节点
     */
    private boolean isCriticalNode(String nodeId) {
        List<String> neighbors = topologyService.findAlternativePaths(nodeId, nodeId, "");
        return neighbors.size() <= 1 || neighbors.size() > 3;
    }

    private String getTargetNodeId(String agvId) {
        Task task = taskDispatcher.getAgvCurrentTask(agvId);
        if (task == null || task.getPathResult() == null) return null;
        List<String> nodes = task.getPathResult().getNodeSequence();
        return nodes.isEmpty() ? null : nodes.get(nodes.size() - 1);
    }


    private void notifyAgvToYield(String agvId, String resourceId) {
        log.info("通知AGV {} 退让资源 {}", agvId, resourceId);
    }

    private double calculateDistanceBetweenAgvs(AgvStatus status1, AgvStatus status2) {
        if (status1.getCurrentPosition().getX() == null || status1.getCurrentPosition().getY() == null ||
                status2.getCurrentPosition().getX() == null || status2.getCurrentPosition().getY() == null) {
            return Double.MAX_VALUE;
        }
        double dx = status1.getCurrentPosition().getX() - status2.getCurrentPosition().getX();
        double dy = status1.getCurrentPosition().getY() - status2.getCurrentPosition().getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private boolean canCoordinatePassing(ConflictAnalysisResult conflict) {
        return conflict.getPathIntersection() != null &&
                conflict.getPathIntersection().getMyDistanceToConflict() > 3.0;
    }
}
