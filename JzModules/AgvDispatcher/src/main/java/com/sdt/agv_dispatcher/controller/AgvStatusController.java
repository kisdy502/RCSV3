package com.sdt.agv_dispatcher.controller;

import com.jizhi.vda5050.domain.PathResult;
import com.sdt.agv_dispatcher.mqtt.MqttGateway;
import com.sdt.agv_dispatcher.scheduler.AgvTaskDispatcher;
import com.sdt.agv_dispatcher.service.AgvManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/agv")
public class AgvStatusController {

    @Autowired
    private AgvManagerService agvManagerService;

    @Autowired
    private AgvTaskDispatcher agvScheduler;

    @Autowired
    private MqttGateway mqttService;

//    /**
//     * AGV位置更新（模拟器调用）
//     * 当AGV到达新节点时调用此接口
//     */
//    @PostMapping("/position/update")
//    public CommonResult<Map<String, Object>> updateAgvPosition(
//            @RequestBody AgvPositionUpdateRequest request) {
//
//        try {
//            log.debug("AGV位置更新: AGV={}, 节点={}",
//                    request.getAgvId(), request.getCurrentNodeId());
//
//            // 1. 验证请求参数
//            if (request.getAgvId() == null || request.getAgvId().trim().isEmpty()) {
//                return CommonResult.validateFailed("AGV ID不能为空");
//            }
//
//            if (request.getCurrentNodeId() == null || request.getCurrentNodeId().trim().isEmpty()) {
//                return CommonResult.validateFailed("当前节点ID不能为空");
//            }
//
//            // 2. 更新AGV状态
//            AgvStatus agvStatus = agvManagerService.getAgvStatus(request.getAgvId());
//            if (agvStatus != null) {
//                agvStatus.setCurrentNodeId(request.getCurrentNodeId());
//                agvStatus.setLastUpdateTime(LocalDateTime.now());
//
//                // 如果有坐标信息，更新位置
//                if (request.getX() != null && request.getY() != null) {
//                    // 这里可以添加逻辑来更新AGV的坐标位置
//                     agvStatus.setCurrentPosition(new AgvPosition(request.getX(), request.getY(),request.getTheta()));
//                }
//                if (request.getVelocity() != null) {
//                    agvStatus.setVelocity(request.getVelocity());
//                }
//
//                log.info("AGV {} 位置已更新: 节点={}, 时间={}",
//                        request.getAgvId(), request.getCurrentNodeId(), LocalDateTime.now());
//            } else {
//                log.warn("AGV {} 状态不存在", request.getAgvId());
//                return CommonResult.failed("AGV状态不存在: " + request.getAgvId());
//            }
//
//            // 3. 触发避障预留更新
//            conflictAvoidanceScheduler.updateReservationOnMovement(
//                    request.getAgvId(),
//                    request.getCurrentNodeId());
//
//
//
//            // 4. 检测当前位置的潜在冲突
//            String currentTaskId = agvScheduler.getAgvCurrentTaskId(request.getAgvId());
//            boolean hasConflict = false;
//            if (currentTaskId != null) {
//                Task currentTask = agvScheduler.getTasks().get(currentTaskId);
//                if (currentTask != null && currentTask.getPathInfo() != null) {
//                    ConflictResult conflictResult = conflictDetectionService
//                            .detectPotentialConflicts(request.getAgvId(), currentTask.getPathInfo());
//
//                    if (conflictResult.isHasConflict()) {
//                        hasConflict = true;
//                        log.warn("AGV {} 当前位置检测到冲突，冲突数量: {}",
//                                request.getAgvId(), conflictResult.getConflicts().size());
//
//                        // 处理实时冲突
//                        handleRealTimeConflict(request.getAgvId(), conflictResult);
//                    }
//                }
//            }
//
//            // 5. 构建返回结果
//            Map<String, Object> resultData = new HashMap<>();
//            resultData.put("status", "SUCCESS");
//            resultData.put("agvId", request.getAgvId());
//            resultData.put("currentNodeId", request.getCurrentNodeId());
//            resultData.put("timestamp", LocalDateTime.now().toString());
//            resultData.put("hasConflict", hasConflict);
//            resultData.put("updateTime", LocalDateTime.now());
//
//            return CommonResult.success(resultData, "AGV位置更新成功");
//
//        } catch (Exception e) {
//            log.error("AGV位置更新失败", e);
//            return CommonResult.failed("AGV位置更新失败: " + e.getMessage());
//        }
//    }

//    /**
//     * 处理实时冲突
//     */
//    private void handleRealTimeConflict(String agvId, ConflictResult conflictResult) {
//        try {
//            ResolutionStrategy strategy = conflictDetectionService.resolveConflict(conflictResult);
//
//            Map<String, Object> conflictAction = new HashMap<>();
//            conflictAction.put("agvId", agvId);
//            conflictAction.put("conflictCount", conflictResult.getConflicts().size());
//            conflictAction.put("resolutionAction", strategy.getAction().name());
//            conflictAction.put("timestamp", LocalDateTime.now());
//
//            switch (strategy.getAction()) {
//                case SLOW_DOWN:
//                    // 发送减速指令到AGV
//                    conflictAction.put("speedReduction", strategy.getSpeedReduction());
//                    sendSpeedReductionCommand(agvId, strategy.getSpeedReduction());
//                    break;
//                case WAIT:
//                    // 发送暂停指令
//                    conflictAction.put("waitTime", strategy.getWaitTime());
//                    sendPauseCommand(agvId, strategy.getWaitTime());
//                    break;
//                case STOP:
//                    // 发送停止指令
//                    sendStopCommand(agvId);
//                    break;
//                case REPLAN:
//                    // 重新规划路径
//                    rePlanPath(agvId);
//                    conflictAction.put("replanned", true);
//                    break;
//                case PROCEED:
//                    // 继续执行
//                    conflictAction.put("proceedWithCaution", true);
//                    break;
//                default:
//                    log.warn("未知的冲突解决策略: {}", strategy.getAction());
//            }
//            log.info("处理AGV {} 的冲突: {}", agvId, conflictAction);
//        } catch (Exception e) {
//            log.error("处理实时冲突失败: AGV={}", agvId, e);
//        }
//    }

    /**
     * 发送停止指令
     */
    private void sendStopCommand(String agvId) {
        Map<String, Object> stopCommand = new HashMap<>();
        stopCommand.put("command", "STOP");
        stopCommand.put("agvId", agvId);
        stopCommand.put("timestamp", LocalDateTime.now().toString());

        try {
            mqttService.sendControlCommand(agvId, "command/stop", stopCommand);
            log.info("发送停止指令: AGV={}", agvId);
        } catch (Exception e) {
            log.error("发送停止指令失败: AGV={}", agvId, e);
        }
    }

    /**
     * 发送减速指令
     */
    private void sendSpeedReductionCommand(String agvId, double reduction) {
        Map<String, Object> speedCommand = new HashMap<>();
        speedCommand.put("command", "SET_SPEED");
        speedCommand.put("agvId", agvId);
        speedCommand.put("speedReduction", reduction);
        speedCommand.put("timestamp", LocalDateTime.now().toString());

        try {
            mqttService.sendControlCommand(agvId, "command/speed", speedCommand);
            log.info("发送减速指令: AGV={}, 减速比例={}", agvId, reduction);
        } catch (Exception e) {
            log.error("发送减速指令失败: AGV={}", agvId, e);
        }
    }

    /**
     * 发送暂停指令
     */
    private void sendPauseCommand(String agvId, int waitTime) {
        Map<String, Object> pauseCommand = new HashMap<>();
        pauseCommand.put("command", "PAUSE");
        pauseCommand.put("agvId", agvId);
        pauseCommand.put("duration", waitTime);
        pauseCommand.put("timestamp", LocalDateTime.now().toString());

        try {
            mqttService.sendControlCommand(agvId, "command/pause", pauseCommand);
            log.info("发送暂停指令: AGV={}, 等待时间={}s", agvId, waitTime);
        } catch (Exception e) {
            log.error("发送暂停指令失败: AGV={}", agvId, e);
        }
    }

//    /**
//     * 重新规划路径
//     */
//    private void rePlanPath(String agvId) {
//        try {
//            String taskId = agvScheduler.getAgvCurrentTaskId(agvId);
//            Task task = agvScheduler.getTasks().get(taskId);
//
//            if (task != null) {
//                // 生成新的无冲突路径
//                PathResult newPath = conflictDetectionService.generateConflictFreePath(
//                        agvId,
//                        task.getStartNodeId(),
//                        task.getEndNodeId(),
//                        task.getPathInfo());
//
//                if (newPath != null) {
//                    // 更新任务路径
//                    task.setPathInfo(newPath);
//                    // 发送新的路径到AGV
//                    sendNewPathToAgv(agvId, newPath);
//                    log.info("重新规划路径成功: AGV={}, 新路径节点数={}",agvId, newPath.getNodeSequence().size());
//                } else {
//                    log.warn("重新规划路径失败: 无法生成无冲突路径, AGV={}", agvId);
//                }
//            }
//        } catch (Exception e) {
//            log.error("重新规划路径失败: AGV={}", agvId, e);
//        }
//    }

    /**
     * 发送新路径到AGV
     */
    private void sendNewPathToAgv(String agvId, PathResult newPath) {
        Map<String, Object> newPathCommand = new HashMap<>();
        newPathCommand.put("command", "UPDATE_PATH");
        newPathCommand.put("agvId", agvId);
        newPathCommand.put("path", newPath);
        newPathCommand.put("timestamp", LocalDateTime.now().toString());

        try {
            mqttService.sendControlCommand(agvId, "command/path/update", newPathCommand);
            log.info("发送新路径到AGV: AGV={}, 路径节点数={}",
                    agvId, newPath.getNodeSequence().size());
        } catch (Exception e) {
            log.error("发送新路径到AGV失败: AGV={}", agvId, e);
        }
    }
}