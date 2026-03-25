package com.sdt.agv_dispatcher.controller;

import com.jizhi.data.CommonResult;
import com.jizhi.data.ResultCode;
import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.agv.TaskStatus;
import com.jizhi.vda5050.domain.MapDetailInfo;
import com.jizhi.vda5050.domain.PathResult;
import com.jizhi.vda5050.message.Vda5050OrderMessage;
import com.sdt.agv_dispatcher.domain.RouteBounds;
import com.sdt.agv_dispatcher.domain.RouteInfo;
import com.sdt.agv_dispatcher.domain.RouteVisualizationResponse;
import com.sdt.agv_dispatcher.domain.Task;
import com.sdt.agv_dispatcher.graph.AGVGraph;
import com.sdt.agv_dispatcher.mqtt.MqttGateway;
import com.sdt.agv_dispatcher.scheduler.AgvTaskDispatcher;
import com.sdt.agv_dispatcher.scheduler.PathPlanner;
import com.sdt.agv_dispatcher.service.AgvManagerService;
import com.sdt.agv_dispatcher.service.MapInitializationService;
import com.sdt.agv_dispatcher.service.RedisResourceLockService;
import com.sdt.agv_dispatcher.service.Vda5050OrderConverter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/dispatch/path")
@Slf4j
public class PathPlanningController {

    @Autowired
    private AGVGraph agvGraph;

    @Autowired
    private MapInitializationService mapInitializationService;

    @Autowired
    private AgvManagerService agvManagerService;

    @Autowired
    private AgvTaskDispatcher agvScheduler;

    @Autowired
    private Vda5050OrderConverter vda5050OrderConverter;

    @Autowired
    private MqttGateway mqttGateway;

    @Autowired
    private PathPlanner pathPlanner;

    @Autowired
    private RedisResourceLockService redisResourceLockService;


    @GetMapping("/map/info")
    public CommonResult<MapDetailInfo> getMapInfo() {
        try {
            MapDetailInfo mapDetailInfo = mapInitializationService.getMapVisualizationData();
            return CommonResult.success(mapDetailInfo);
        } catch (Exception e) {
            log.error("获取地图信息失败", e);
            return CommonResult.failed("获取地图信息失败: " + e.getMessage());
        }
    }

    @GetMapping("/graph/validate")
    public CommonResult<Map<String, Object>> validateGraph() {
        try {
            boolean isValid = mapInitializationService.validateGraphConnectivity();
            Map<String, Object> result = new HashMap<>();
            result.put("graphValid", isValid);
            result.put("nodeCount", agvGraph.getAllNodes().size());
            result.put("edgeCount", agvGraph.getAllEdges().size());
            result.put("validationTime", new Date().toString());
            return CommonResult.success(result);
        } catch (Exception e) {
            log.error("验证图连接性失败", e);
            return CommonResult.failed("验证图连接性失败: " + e.getMessage());
        }
    }

    /**
     * 发送调度任务（带时间窗口)
     */
    @PostMapping("/task/send")
    public CommonResult<?> scheduleTaskWithTimeWindow(@RequestBody SimulateTaskRequest request) {
        try {
            log.info("调度任务（带避障检测）: {} -> {}，AGV: {}", request.getStartNode(), request.getEndNode(), request.getAgvId());
            // 1. 检查AGV状态
            AgvStatus agvStatus = agvManagerService.getAgvStatus(request.getAgvId());
            if (agvStatus == null) {
                return CommonResult.validateFailed("AGV不存在: " + request.getAgvId());
            }

            // 2. 检查AGV是否已有任务
            String currentTaskId = agvScheduler.getAgvCurrentTaskId(request.getAgvId());
            if (currentTaskId != null) {
                return CommonResult.failed("AGV已有任务: " + currentTaskId);
            }

            PathResult fullPath = pathPlanner.findOptimalPath(request.getStartNode(), request.getEndNode(),
                    request.getAgvId());
            if (fullPath == null) {
                return CommonResult.failed("无可行路径");
            }

            // 构造需要锁定的资源列表（例如前5个节点+边）
            List<RedisResourceLockService.ResourceLockInfo> toLock = new ArrayList<>();
            int count = 0;
            int MAX_LOCK = 3; // 可配置
            for (int i = 0; i < fullPath.getNodeSequence().size() && count < MAX_LOCK; i++) {
                String nodeId = fullPath.getNodeSequence().get(i);
                toLock.add(new RedisResourceLockService.ResourceLockInfo(nodeId, "NODE"));
                count++;
                if (i < fullPath.getEdgeSequence().size() && count < MAX_LOCK) {
                    String edgeId = fullPath.getEdgeSequence().get(i);
                    toLock.add(new RedisResourceLockService.ResourceLockInfo(edgeId, "EDGE"));
                    count++;
                }
            }

            // 尝试锁定
            boolean lockSuccess = redisResourceLockService.tryLockPath(toLock, request.getAgvId());
            if (!lockSuccess) {
                return CommonResult.failed("资源被占用，无法调度");
            }

            Task task = new Task();
            task.prePersist();  //生成一些基本信息，taskId,taskNo,创建时间等
            task.setName(request.getTaskName());
            task.setStartNodeId(request.getStartNode());
            task.setEndNodeId(request.getEndNode());
            task.setType(Task.TaskType.TRANSPORT);
            task.setStatus(TaskStatus.WAITING);
            task.setPriority(1);
            task.setCreateTime(LocalDateTime.now());
            task.setCreatedBy("SYSTEM");
            // 设置任务属性
            task.setProperties(new HashMap<>());
            task.getProperties().put("generatedBy", "PointToPointGenerator");
            task.getProperties().put("generationTime", System.currentTimeMillis());
            task.setPathResult(fullPath);
            task.setResourceLockList(toLock); // 需要在Task中添加字段
            task.setEstimatedDuration(fullPath.getEstimatedTime().intValue());
            task.setDistance(fullPath.getTotalDistance());

            // 将路径信息添加到任务属性
            task.getProperties().put("pathNodes", fullPath.getNodeSequence());
            task.getProperties().put("totalDistance", fullPath.getTotalDistance());
            task.getProperties().put("estimatedTime", fullPath.getEstimatedTime());

            fullPath.setTaskId(task.getId());

            // 6. 发送到MQTT
            try {
                // 1. 生成VDA5050订单消息
                Vda5050OrderMessage vda5050OrderMessage =
                        vda5050OrderConverter.convertPathToOrder(task.getPathResult(),task.getId(), agvStatus);
                // 2. 发送到MQTT（模拟）
                mqttGateway.sendVda5050Order(agvStatus.getAgvId(), vda5050OrderMessage);
            } catch (Exception e) {
                log.error("模拟发送任务失败", e);
                return CommonResult.failed("任务调度失败: " + e.getMessage());
            }

            // 7. 保存任务并发送

            task.setAgvId(request.getAgvId());
            task.setAssignedTime(LocalDateTime.now());
            agvScheduler.assignTask(task, request.getAgvId());
            agvStatus.setNodeSequence(fullPath.getNodeSequence());
            agvStatus.setEdgeSequence(fullPath.getEdgeSequence());
            // 8. 返回结果
            return CommonResult.success(fullPath, "任务调度成功");

        } catch (Exception e) {
            log.error("任务调度失败", e);
            return CommonResult.failed("任务调度失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有AGV的实时位置
     */
    @GetMapping("/agvs")
    public CommonResult<List<AgvStatus>> getAllAgvPositions() {
        try {
            List<AgvStatus> agvStatusList = agvManagerService.getAllAgvStatus();
            if (agvStatusList == null || agvStatusList.isEmpty()) {
                return CommonResult.success(Collections.emptyList(), "暂无AGV在线");
            }
            return CommonResult.success(agvStatusList);
        } catch (Exception e) {
            log.error("获取AGV位置失败", e);
            return CommonResult.failed("获取AGV位置失败: " + e.getMessage());
        }
    }

    @GetMapping("/route/visualize")
    public CommonResult<RouteVisualizationResponse> visualizeRoute(
            @RequestParam String startNode,
            @RequestParam String endNode) {

        try {
            PathResult pathResult = pathPlanner.findOptimalPath(startNode, endNode, "VISUALIZATION");
            if (pathResult == null) {
                return CommonResult.failed(ResultCode.EMPTY, "无法规划路径");
            }

            // 获取可视化数据
            MapDetailInfo mapDetailInfo = mapInitializationService.getMapVisualizationData();
            RouteVisualizationResponse visualizationResponse = new RouteVisualizationResponse();
            visualizationResponse.setMapData(mapDetailInfo);

            // 构建路径信息
            RouteInfo routeInfo = new RouteInfo(pathResult, startNode, endNode);
            visualizationResponse.setRoute(routeInfo);

            // 计算路径边界
            RouteBounds routeBounds = new RouteBounds(pathResult.getNodeSequence(), agvGraph);
            visualizationResponse.setRouteBounds(routeBounds);

            return CommonResult.success(visualizationResponse, "路线可视化成功");

        } catch (Exception e) {
            log.error("可视化路线失败", e);
            return CommonResult.failed("可视化路线失败: " + e.getMessage());
        }
    }

    @Data
    public static class SimulateTaskRequest {
        private String taskName;
        private String startNode;
        private String endNode;
        private String agvId;
    }
}