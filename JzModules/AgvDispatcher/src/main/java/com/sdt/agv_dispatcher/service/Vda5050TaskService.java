//package com.sdt.agv_dispatcher.service;
//
//import com.jizhi.vda5050.agv.AgvStatus;
//import com.jizhi.vda5050.agv.TaskStatus;
//import com.jizhi.vda5050.message.Vda5050OrderMessage;
//import com.sdt.agv_dispatcher.graph.AGVGraph;
//import com.jizhi.vda5050.domain.PathResult;
//import com.sdt.agv_dispatcher.domain.Task;
//import com.sdt.agv_dispatcher.mqtt.MqttGateway;
//import com.sdt.agv_dispatcher.scheduler.PathPlanner;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.*;
//
//@Service
//@Slf4j
//public class Vda5050TaskService {
//
//    @Autowired
//    private PathPlanner pathPlanner;
//
//    @Autowired
//    private MqttGateway mqttService;
//
//    @Autowired
//    private AgvManagerService agvManagerService;
//
//    @Autowired
//    private Vda5050OrderConverter vda5050OrderConverter;
//
//    @Autowired
//    private AGVGraph agvGraph;
//
//    @Autowired
//    private ConflictAvoidanceScheduler conflictAvoidanceScheduler;
//
//    /**
//     * 生成点到点运输任务
//     */
//    public Task generatePointToPointTask(String taskName, String startNodeId, String endNodeId, String agvId) {
//        Task task = new Task();
//        task.prePersist();  //生成一些基本信息，taskId,taskNo,创建时间等
//        task.setName(taskName);
//        task.setStartNodeId(startNodeId);
//        task.setEndNodeId(endNodeId);
//        task.setType(Task.TaskType.TRANSPORT);
//        task.setStatus(TaskStatus.WAITING);
//        task.setPriority(1);
//        task.setCreateTime(LocalDateTime.now());
//        task.setCreatedBy("SYSTEM");
//
//        // 设置任务属性
//        task.setProperties(new HashMap<>());
//        task.getProperties().put("generatedBy", "PointToPointGenerator");
//        task.getProperties().put("generationTime", System.currentTimeMillis());
//
//        // 规划路径
//        PathResult path = pathPlanner.findOptimalPath(startNodeId, endNodeId, agvId);
//        if (path != null) {
//            // 2. 尝试前向预留
//            int lookAheadCount = 5; // 预留前5个段
//            if (!conflictAvoidanceScheduler.tryForwardReservation(agvId, task.getId(), path, lookAheadCount)) {
//                log.warn("AGV {} 无法预留路径，可能存在冲突", agvId);
//
//                // 尝试备用路径
//                List<PathResult> alternativePaths = pathPlanner.findAlternativePaths(startNodeId, endNodeId, agvId,lookAheadCount);
//
//
//                for (PathResult altPath : alternativePaths) {
//                    if (!altPath.equals(path) &&
//                            conflictAvoidanceScheduler.tryForwardReservation(agvId, task.getId(), altPath,
//                                    lookAheadCount)) {
//                        path = altPath;
//                        log.info("AGV {} 使用备用路径", agvId);
//                        break;
//                    }
//                }
//            }
//
//            task.setPathInfo(path);
//            task.setEstimatedDuration(path.getEstimatedTime().intValue());
//            task.setDistance(path.getTotalDistance());
//
//            // 将路径信息添加到任务属性
//            task.getProperties().put("pathNodes", path.getNodeSequence());
//            task.getProperties().put("totalDistance", path.getTotalDistance());
//            task.getProperties().put("estimatedTime", path.getEstimatedTime());
//            log.info("生成点到点任务: {} ({} -> {})", task.getId(), startNodeId, endNodeId);
//        }
//        return task;
//    }
//
//
//    /**
//     * 模拟发送VDA5050任务
//     */
//    public Map<String, Object> simulateTaskDispatch(Task task, String agvId) {
//        Map<String, Object> result = new HashMap<>();
//
//        AgvStatus agvStatus = agvManagerService.getAgvStatus(agvId);
//        try {
//            // 1. 生成VDA5050订单消息
//            Vda5050OrderMessage vda5050OrderMessage = vda5050OrderConverter.convertPathToOrder(task.getPathInfo(),
//                    task.getId(), agvStatus);
//
//            // 2. 发送到MQTT（模拟）
//            mqttService.sendVda5050Order(agvStatus.getAgvId(), vda5050OrderMessage);
//
//            result.put("status", "SUCCESS");
//            result.put("message", "任务已发送");
//            result.put("taskId", task.getId());
//            result.put("agvId", agvId);
//            result.put("orderId", task.getId());
//            result.put("timestamp", LocalDateTime.now());
//            result.put("orderDetails", vda5050OrderMessage);
//        } catch (Exception e) {
//            log.error("模拟发送任务失败", e);
//            result.put("status", "ERROR");
//            result.put("message", e.getMessage());
//        }
//
//        return result;
//    }
//
//
//    /**
//     * 验证路径规划结果
//     */
//    public Map<String, Object> validatePathPlanning(Task task) {
//        Map<String, Object> result = new HashMap<>();
//
//        if (task.getPathInfo() == null) {
//            result.put("status", "ERROR");
//            result.put("message", "任务没有路径信息");
//            return result;
//        }
//
//        PathResult path = task.getPathInfo();
//
//        // 验证路径连通性
//        boolean startToEndReachable = agvGraph.isReachable(
//                task.getStartNodeId(), task.getEndNodeId());
//
//        // 验证路径节点序列
//        List<String> nodeSequence = path.getNodeSequence();
//        boolean sequenceValid = validateNodeSequence(nodeSequence);
//
//        // 验证路径完整性
//        boolean pathComplete = false;
//        if (nodeSequence != null && nodeSequence.size() >= 2) {
//            String firstNode = nodeSequence.get(0);
//            String lastNode = nodeSequence.get(nodeSequence.size() - 1);
//            pathComplete = firstNode.equals(task.getStartNodeId()) &&
//                    lastNode.equals(task.getEndNodeId());
//        }
//
//        result.put("status", "SUCCESS");
//        result.put("taskId", task.getId());
//        result.put("startNode", task.getStartNodeId());
//        result.put("endNode", task.getEndNodeId());
//        result.put("pathNodes", nodeSequence != null ? nodeSequence.size() : 0);
//        result.put("totalDistance", path.getTotalDistance());
//        result.put("estimatedTime", path.getEstimatedTime());
//        result.put("startToEndReachable", startToEndReachable);
//        result.put("sequenceValid", sequenceValid);
//        result.put("pathComplete", pathComplete);
//        result.put("validationTime", LocalDateTime.now().toString());
//
//        if (!startToEndReachable) {
//            result.put("status", "WARNING");
//            result.put("message", "起点和终点不可达，但存在替代路径");
//        }
//
//        if (!sequenceValid || !pathComplete) {
//            result.put("status", "ERROR");
//            result.put("message", "路径节点序列无效或不完整");
//        }
//
//        return result;
//    }
//
//    /**
//     * 验证节点序列连通性
//     */
//    private boolean validateNodeSequence(List<String> nodeSequence) {
//        if (nodeSequence == null || nodeSequence.size() < 2) {
//            return false;
//        }
//
//        for (int i = 0; i < nodeSequence.size() - 1; i++) {
//            String currentNode = nodeSequence.get(i);
//            String nextNode = nodeSequence.get(i + 1);
//
//            if (!agvGraph.isReachable(currentNode, nextNode)) {
//                log.warn("节点序列验证失败: {} -> {} 不可达", currentNode, nextNode);
//                return false;
//            }
//        }
//
//        return true;
//    }
//
//}
