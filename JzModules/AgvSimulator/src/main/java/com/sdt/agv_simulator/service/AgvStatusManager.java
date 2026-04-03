package com.sdt.agv_simulator.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.jizhi.vda5050.agv.AgvPosition;
import com.jizhi.vda5050.agv.AgvState;
import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.agv.TaskStatus;
import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.jizhi.vda5050.domain.PathResult;
import com.jizhi.vda5050.message.Vda5050OrderMessage;
import com.sdt.agv_simulator.agv.ExecutionSnapshot;
import com.sdt.agv_simulator.agv.VirtualAgv;
import com.sdt.agv_simulator.component.Ros2BusinessException;
import com.sdt.agv_simulator.component.Ros2WebSocketClient;
import com.sdt.agv_simulator.config.AgvSimulatorConfig;
import com.sdt.agv_simulator.dto.AgvStatusDto;
import com.sdt.agv_simulator.dto.CommandAckDto;
import com.sdt.agv_simulator.dto.LaserScanDto;
import com.sdt.agv_simulator.dto.PositionUpdateDto;
import com.sdt.agv_simulator.move.MovementManager;
import com.sdt.agv_simulator.move.MovementPauseState;
import com.sdt.agv_simulator.move.MovementResultCallback;
import com.sdt.agv_simulator.mqtt.AgvMqttGateway;
import com.sdt.agv_simulator.mqtt.IMqttConnectListener;
import com.sdt.agv_simulator.mqtt.IMqttMessageHandler;
import com.sdt.agv_simulator.task.ActionManager;
import com.sdt.agv_simulator.task.OrderExecutor;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class AgvStatusManager implements IMqttMessageHandler, IMqttConnectListener {

    @Autowired
    private AgvSimulatorConfig agvConfig;

    @Autowired
    @Lazy
    private AgvMqttGateway agvMqttGateway;

    @Autowired
    private MapService mapService;

    @Autowired
    private MovementManager movementManager;

    @Autowired
    private ActionManager actionManager;

    @Autowired
    private OrderExecutor orderExecutor;

    @Autowired
    private VirtualAgv virtualAgv;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    @Autowired
    private MessageDeduplicationService deduplicationService;

    @Autowired
    private Ros2WebSocketClient ros2WebSocketClient;

    @Autowired
    private Vda5050MessageBuilder vda5050MessageBuilder;

    private final Gson gson = new Gson();

    @Autowired
    private ObjectMapper objectMapper;

    @Getter
    @Setter
    private LaserScanDto lastLaserScan = null;


    @PostConstruct
    protected void init() {
        log.info("AGV管理器初始化");
        intSimulatedAgvStatus();
        ros2WebSocketClient.addMessageListener(messageListener);
    }

    /**
     * 初始化 仿真agv的状态信息
     */
    private void intSimulatedAgvStatus() {
        AgvStatus agvStatus = new AgvStatus(agvConfig.getAgvId());
        agvStatus.setName(agvConfig.getName());
        agvStatus.setManufacturer(agvConfig.getManufacturer());
        agvStatus.setSerialNumber(agvConfig.getSerialNumber());
        agvStatus.setAgvType(agvConfig.getAgvType());
        agvStatus.setBatteryLevel(agvConfig.getBatteryLevel()); // 80-100%电量
        agvStatus.setMaxSpeed(agvConfig.getMaxSpeed());
        agvStatus.setLoadCapacity(100.0);
        virtualAgv.setAgvStatus(agvStatus);
    }


    /**
     * 启动AGV模拟
     */
    private void startAgvSimulation() {
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        if (agvStatus == null) return;
        // 定期发送心跳
        scheduler.scheduleAtFixedRate(() -> {
            try {
                agvMqttGateway.sendHeartbeat(agvStatus);
            } catch (Exception e) {
                log.error("发送心跳失败: {}", agvStatus.getAgvId(), e);
            }
        }, 5, 15, TimeUnit.SECONDS);

        log.info("启动AGV模拟: {}", agvStatus.getAgvId());
    }

    /**
     * 处理VDA5050可视化消息（可选）
     */
//    public void processVda5050Visualization(Vda5050Message message) {
//        try {
//            log.debug("处理VDA5050可视化消息: headerId={}", message.getHeader().getHeaderId());
//
//            // 可视化消息通常用于调试，不需要处理业务逻辑
//            Map<String, Object> payload = (Map<String, Object>) message.getPayload();
//            log.debug("可视化消息内容: {}", payload);
//
//        } catch (Exception e) {
//            log.error("处理VDA5050可视化消息失败", e);
//        }
//    }
    private void updateAndSendActionState(String actionId, TaskStatus actionStatus, String resultDescription) {
        virtualAgv.updateActionState(actionId, actionStatus, resultDescription);
        agvMqttGateway.sendActionState(virtualAgv.getAgvStatus(), actionId, actionStatus, resultDescription);
    }

    /**
     * 处理VDA5050订单 - 现在只需交给 OrderExecutor
     */
    public void processVda5050Order(Vda5050OrderMessage orderMessage) {
        try {
            log.info("处理VDA5050订单: orderId={}", orderMessage.getOrderInformation().getOrderId());
            virtualAgv.setCurrentOrderMessage(orderMessage);
            AgvStatus agvStatus = virtualAgv.getAgvStatus();
            agvStatus.updateFromVda5050OrderMessage(orderMessage);
            agvStatus.setOrderState("ACCEPTED");

            // 发送接受确认
            agvMqttGateway.sendOrderState(agvStatus, orderMessage.getOrderInformation().getOrderId(), "ACCEPTED",
                    orderMessage.getOrderInformation().getOrderUpdateId(), "订单已接受");
            // 交给执行器
            orderExecutor.executeOrder(orderMessage);

        } catch (Exception e) {
            log.error("处理VDA5050订单失败", e);
        }
    }

    /**
     * 检查是否为充电订单
     */
    private boolean isChargingOrder(List<Map<String, Object>> actions) {
        if (actions == null) return false;

        for (Map<String, Object> action : actions) {
            String actionType = (String) action.get("actionType");
            if ("CHARGE".equalsIgnoreCase(actionType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理充电订单
     */
    private void handleChargingOrder(String orderId, Integer orderUpdateId) {
        virtualAgv.startCharging();
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        // 模拟充电过程
        scheduler.schedule(() -> {
            virtualAgv.stopCharging();
            virtualAgv.completeOrder();

            // 发送订单完成
            agvMqttGateway.sendOrderState(agvStatus, orderId, "FINISHED", orderUpdateId + 1, "充电完成");
            agvMqttGateway.sendTaskComplete(agvStatus, orderId, TaskStatus.FINISHED, "充电任务完成",
                    agvStatus.getOrderStartTime());

            log.info("AGV {} 充电完成", agvStatus.getAgvId());
        }, 2, TimeUnit.MINUTES); // 模拟2分钟充电
    }

    /**
     * 处理控制命令 - 修复版本
     */
    public void processControlCommand(String command, Map<String, Object> parameters) {
        if (virtualAgv == null) {
            log.warn("AGV不存在!");
            return;
        }

        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        String agvId = agvStatus.getAgvId();
        log.info("AGV {} 收到控制命令: {}", agvId, command);

        try {
            switch (command.toUpperCase()) {
                case "PAUSE":
                    if (agvStatus.getAgvState() == AgvState.PAUSED) {
                        log.info("AGV已暂停");
                        return;
                    }
                    handlePauseCommand(agvId, agvStatus);
                    break;
                case "RESUME":
                    if (agvStatus.getAgvState() != AgvState.PAUSED) {
                        log.info("AGV未处于暂停状态，无需恢复");
                        return;
                    }
                    handleResumeCommand(agvId, agvStatus);
                    break;
                case "CANCEL":
                    // 1. 发送取消指令给ROS2
                    ros2WebSocketClient.sendStopMoveCommand(agvId);
                    // 2. 取消当前移动任务
                    movementManager.cancelCurrentMovement("调度取消任务");
                    // 3. 更新本地状态
                    virtualAgv.cancelOrder();
                    agvStatus.setOrderState("CANCELLED");
                    agvStatus.setAgvState(AgvState.IDLE);
                    agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(), "CANCELLED",
                            agvStatus.getOrderUpdateId() + 1, "订单已取消");
                    break;

                case "EMERGENCY_STOP":
                    // 1. 发送急停指令给ROS2
                    ros2WebSocketClient.sendEmergencyStop(agvId);
                    // 2. 立即取消当前移动任务
                    movementManager.cancelCurrentMovement("上层应用控制急停，取消任务");
                    // 3. 更新本地状态
                    agvStatus.setEmergencyStop(true);
                    agvStatus.setAgvState(AgvState.ERROR);
                    agvStatus.setErrorCode("EMERGENCY_STOP");
                    agvMqttGateway.sendError(agvStatus, "EMERGENCY_STOP", "紧急停止激活");
                    break;

                case "RESET_EMERGENCY":
                    // 1. 发送清除急停指令给ROS2
                    ros2WebSocketClient.sendClearEmergency(agvId);
                    // 2. 更新本地状态
                    agvStatus.setEmergencyStop(false);
                    if (agvStatus.getAgvState() == AgvState.ERROR && Objects.equals(agvStatus.getErrorCode(),
                            "RESET_EMERGENCY")) {
                        agvStatus.setAgvState(AgvState.IDLE);
                        agvStatus.setErrorCode("");
                    }
                    break;

                case "CHANGE_SPEED":
                    if (parameters != null && parameters.containsKey("speed")) {
                        Double speed = Double.parseDouble(parameters.get("speed").toString());
                        // 发送速度限制给ROS2
                        ros2WebSocketClient.sendSpeedLimit(agvId, speed);
                        agvStatus.setMaxSpeed(speed);
                    }
                    break;

                case "CHANGE_BATTERY":
                    if (parameters != null && parameters.containsKey("level")) {
                        Double level = Double.parseDouble(parameters.get("level").toString());
                        agvStatus.setBatteryLevel(Math.max(0, Math.min(100, level)));
                    }
                    break;

                default:
                    log.warn("未知的控制命令: {}", command);
                    break;
            }
        } catch (Ros2BusinessException e) {
            log.error("发送控制命令到ROS2失败: command={}, error={}", command, e.getMessage());
            // 发送错误状态
            agvMqttGateway.sendError(agvStatus, "CONTROL_ERROR", "控制命令执行失败: " + e.getMessage());
        }
    }

    /**
     * 处理暂停命令
     */
    private void handlePauseCommand(String agvId, AgvStatus agvStatus) {
        // 创建快照
        ExecutionSnapshot snapshot = virtualAgv.createPauseSnapshot("USER_PAUSE");

        // 暂停移动
        MovementPauseState pauseState = movementManager.pauseMovement("用户暂停");
        if (snapshot != null && pauseState != null) {
            snapshot.setMovementPauseState(pauseState);
            //TODO 同步获取停止结果，成功了才能修改上位机状态
            ros2WebSocketClient.sendStopMoveCommand(agvId);
        }

        // 暂停动作
        if (actionManager.getCurrentContext() != null) {
            //TODO 仿真环境，可以先不管
            snapshot.setActionState(actionManager.pauseCurrentAction());
        }

        // 更新状态
        virtualAgv.pauseOrder();
        agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(), "PAUSED",
                agvStatus.getOrderUpdateId(), "订单已暂停");
    }

    /**
     * 处理恢复命令
     */
    private void handleResumeCommand(String agvId, AgvStatus agvStatus) {
        ExecutionSnapshot snapshot = virtualAgv.getPauseSnapshot();
        if (snapshot == null) {
            log.error("没有找到暂停快照");
            return;
        }

        // 恢复移动
        if (snapshot.getMovementPauseState() != null) {
            movementManager.resumeMovement(agvId, snapshot.getMovementPauseState(), new MovementResultCallback() {
                @Override
                public void onMovementSuccess(String cmdId, String nodeId, Node node) {
                    // 恢复后继续执行剩余订单
                    orderExecutor.resumeFromSnapshot(snapshot, virtualAgv.getCurrentOrderMessage());
                }

                @Override
                public void onMovementFailed(String cmdId, String nodeId, String status, String reason) {
                    handleMoveFailed(status, "恢复移动失败: " + reason);
                }

                @Override
                public void onMovementStateChanged(String cmdId, String nodeId, String state) {
                }
            });
            agvStatus.setAgvState(AgvState.MOVING);
        }

        // 更新状态
        //
        agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(), "RUNNING",
                agvStatus.getOrderUpdateId(), "订单已恢复");
    }

    /**
     * 从断点恢复订单执行
     */
    private void resumeOrderExecution(ExecutionSnapshot snapshot) {
        // 启动新线程继续执行剩余订单
        new Thread(() -> {
            try {
                Vda5050OrderMessage orderMessage = virtualAgv.getCurrentOrderMessage();
                if (orderMessage == null) {
                    log.error("无法恢复：没有找到原始订单消息");
                    return;
                }
                AgvStatus agvStatus = virtualAgv.getAgvStatus();
            } catch (Exception e) {
                log.error("恢复订单执行失败", e);
                handleMoveFailed("FAILED", "恢复执行异常: " + e.getMessage());
            }
        }).start();
    }

    /**
     * 处理路径更新
     */
    public void processPathUpdate(Map<String, Object> pathUpdate) {
        if (virtualAgv == null) return;
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        try {
            List<String> newNodes = (List<String>) pathUpdate.get("nodeSequence");
            List<String> newEdges = (List<String>) pathUpdate.get("edgeSequence");

            if (newNodes != null && newNodes.size() >= 2) {
                // 更新路径，从当前位置开始
                int currentIndex = agvStatus.getNodeSequence() != null ?
                        agvStatus.getNodeSequence().indexOf(agvStatus.getCurrentNodeId()) : 0;

                if (currentIndex >= 0) {
                    List<String> updatedNodes = new ArrayList<>();
                    updatedNodes.add(agvStatus.getCurrentNodeId());
                    updatedNodes.addAll(newNodes.subList(currentIndex + 1, newNodes.size()));

                    agvStatus.setNodeSequence(updatedNodes);
                    agvStatus.setEdgeSequence(newEdges);
                    log.info("AGV {} 路径已更新，新节点数: {}", agvStatus.getAgvId(), updatedNodes.size());
                }
            }
        } catch (Exception e) {
            log.error("处理路径更新失败: AGV={}", agvStatus.getAgvId(), e);
        }
    }

    /**
     * 完成订单模拟
     */
    private void completeOrderSimulation() {
        if (virtualAgv == null) return;
        virtualAgv.completeOrder();
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        // 发送订单完成状态
        agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(), "FINISHED",
                agvStatus.getOrderUpdateId() + 1, "订单执行完成");
        agvMqttGateway.sendTaskComplete(agvStatus, agvStatus.getCurrentOrderId(), TaskStatus.FINISHED, "订单执行完成",
                agvStatus.getOrderStartTime());

        log.info("AGV {} 完成订单: {}", agvStatus.getAgvId(), agvStatus.getCurrentOrderId());
    }

    private void agvToIdle() {
        if (virtualAgv == null) return;
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        agvStatus.setCurrentOrderId(null);
        agvStatus.setLastNodeId(null);  //到达终点后，空闲后，上一个节点就没有意义了，不然再发任务过来时候会有问题的
        agvStatus.setLastNodeIndex(-1);
        agvStatus.setOrderState("IDLE");  // 或先 FINISHED 再 IDLE
        agvStatus.setAgvState(AgvState.IDLE);
    }

    /**
     * 更新AGV状态并发送
     */
    public void updateAgvStatus(AgvStatusDto dto) {
        if (virtualAgv == null) return;

        virtualAgv.getAgvStatus().setBatteryLevel(dto.getBattery());
        virtualAgv.getAgvStatus().setPositionInitialized(dto.getPoseInitialized());
        agvMqttGateway.sendAgvState(virtualAgv.getAgvStatus());      // 发送状态
    }

    /**
     * 更新AGV位置
     */
    public void initAgvPosition(double x, double y, double theta) {
        String agvId = virtualAgv.getAgvStatus().getAgvId();
        virtualAgv.getAgvStatus().setCurrentNodeId(null);
        ros2WebSocketClient.sendInitialPose(agvId, x, y, theta);
    }

    /**
     * 更新AGV位置
     */
    public boolean initAgvPosition(String nodeId) {
        String agvId = virtualAgv.getAgvStatus().getAgvId();
        virtualAgv.getAgvStatus().setCurrentNodeId(nodeId);

        Node node = mapService.getNode(nodeId);
        if (node != null) {
            ros2WebSocketClient.sendInitialPose(agvId, node.getX(), node.getY(), node.getTheta());
            return true;
        } else {
            log.error("初始化位置失败:{}", nodeId);
            return false;
        }
    }

    /**
     * 模拟故障
     */
    public void simulateFault(String errorCode, String errorDescription) {
        if (virtualAgv == null) return;
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        agvStatus.setAgvState(AgvState.ERROR);
        agvStatus.getActiveErrors().add(errorCode);
//        agvStatus.setSimulationRunning(false);

        agvMqttGateway.sendError(agvStatus, errorCode, errorDescription);
        agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(), "FAILED",
                agvStatus.getOrderUpdateId() + 1, "模拟故障: " + errorDescription);

        log.warn("AGV {} 模拟故障: {}", agvStatus.getAgvId(), errorDescription);
    }

    /**
     * 清除故障
     */
    public void clearFault(String agvId) {
        if (virtualAgv == null) return;
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        agvStatus.setAgvState(AgvState.IDLE);
        agvStatus.setErrorCode("");
        agvStatus.setActiveErrors(null);

        log.info("AGV {} 故障已清除", agvId);
    }

    @Scheduled(fixedDelay = 60000) // 每分钟检查一次
    public void checkBatteryAndAutoCharge() {
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        if (agvStatus.getBatteryLevel() <= 20 && agvStatus.getAgvState() == AgvState.IDLE) {
            // 自动前往充电站
            String chargeNode = mapService.getNearestChargeNode(agvStatus.getCurrentNodeId());
            if (chargeNode != null) {
                // 模拟创建充电任务
                PathResult pathResult = mapService.findPath(agvStatus.getAgvId(), agvStatus.getCurrentNodeId(),
                        chargeNode);
                if (pathResult != null && pathResult.getNodeSequence().size() > 1) {
                    String chargeOrderId = "AUTO_CHARGE_" + System.currentTimeMillis();
                    virtualAgv.startOrder(chargeOrderId, pathResult.getNodeSequence(), pathResult.getEdgeSequence());
                    virtualAgv.startCharging();

                    log.info("AGV {} 电量低，自动前往充电: {}", agvStatus.getAgvId(), chargeNode);
                }
            }
        }
    }

    public void processPositionUpdate(PositionUpdateDto dto) {
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        AgvPosition currentPos = agvStatus.getCurrentPosition();

        // 数据新旧判断（优先纳秒，降级毫秒）
        if (currentPos != null) {
            // 获取新旧时间戳（优先纳秒）
            long newTime = dto.getTimestampNs() > 0 ? dto.getTimestampNs() : dto.getTimestamp();
            long oldTime = currentPos.getTimestampNs() != null && currentPos.getTimestampNs() > 0 ?
                    currentPos.getTimestampNs() : currentPos.getTimestamp();

            if (newTime <= oldTime) {
                log.debug("丢弃旧位置更新: 新时间戳={}, 当前时间戳={} (使用{}级比较)", newTime, oldTime, (dto.getTimestampNs() > 0 ? "纳秒"
                        : "毫秒"));
                return;
            }

            // ✅ 先算距离！此时currentPos还是旧值
            double dx = dto.getX() - currentPos.getX();
            double dy = dto.getY() - currentPos.getY();
            double deltaDistance = Math.hypot(dx, dy);
            // 累加里程
            agvStatus.updateOdometry(deltaDistance);
        }

        // 更新速度
        agvStatus.setVelocity(dto.getVx());

        // 获取或创建位置对象
        AgvPosition agvPosition = fillAgvPosition(dto, currentPos, agvStatus);

        // 如果是新建的位置对象，设置到AGV状态
        if (currentPos == null) {
            agvStatus.setCurrentPosition(agvPosition);
        }

        agvStatus.setLastUpdateTime(LocalDateTime.now());
        agvMqttGateway.sendPositionUpdate(agvStatus);
    }

    @NotNull
    private static AgvPosition fillAgvPosition(PositionUpdateDto dto, AgvPosition currentPos, AgvStatus agvStatus) {
        AgvPosition agvPosition = currentPos;
        if (agvPosition == null) {
            agvPosition = new AgvPosition();
        }

        // 更新位置信息
        agvPosition.setX(dto.getX());
        agvPosition.setY(dto.getY());
        agvPosition.setTheta(dto.getTheta());
        agvPosition.setQx(dto.getQx());
        agvPosition.setQy(dto.getQy());
        agvPosition.setQz(dto.getQz());
        agvPosition.setQw(dto.getQw());
        agvPosition.setTimestamp(dto.getTimestamp());
        agvPosition.setTimestampNs(dto.getTimestampNs());
        agvPosition.setPositionInitialized(agvStatus.getPositionInitialized());
        return agvPosition;
    }

//    /**
//     * 真实的移动到目标点
//     * passedEdge 到达点位前经过的通道信息，如果是曲线通道，需要进行贝塞尔曲线拟合
//     */
//    private boolean moveToPosition(Node node, Edge passedEdge, boolean isEnd) throws Exception {
//        String commandId = "move_" + UUID.randomUUID();
//
//        log.info("开始移动到节点: {}, 目标位置({}, {}, {})", node.getId(), node.getX(), node.getY(), node.getTheta());
//
//        // 创建移动任务
//        CompletableFuture<Boolean> movementFuture = movementManager.createMovementTask(commandId, node, passedEdge);
//
//        try {
//            // 发送移动命令到ROS2
//            ros2WebSocketClient.sendMoveCommand(virtualAgv.getAgvStatus().getAgvId(), commandId, node, passedEdge,
//                    isEnd);
//
//            // 等待移动完成（阻塞当前线程）
//            return movementFuture.get();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            log.error("移动被中断: commandId={}, 节点={}", commandId, node.getId());
//            return false;
//        } catch (ExecutionException e) {
//            log.error("移动失败: commandId={}, 节点={}, 错误: {}", commandId, node.getId(), e.getCause().getMessage(),
//                    e.getCause());
//            return false;
//        }
//    }

    /**
     * 处理移动结果回调
     */
    public void handleMovementResult(String commandId, String nodeId, String status, String message) {
        // 转发给 MovementManager
        movementManager.handleMovementResult(commandId, nodeId, status, message);
        // 更新AGV状态（如果需要）
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        if ("SUCCESS".equals(status)) {
            agvStatus.setCurrentNodeId(nodeId);
        } else if ("FAILED".equals(status)) {
            agvStatus.setAgvState(AgvState.ERROR);
        }
//        movementManager.updateMovementStatus(commandId, nodeId, status, message);
//        // 更新AGV状态
//        if ("SUCCESS".equals(status)) {
//            // 更新位置信息
//            updatePositionFromCommand(commandId);
//            virtualAgv.getAgvStatus().setCurrentNodeId(nodeId); //到了目标点
//        } else if ("FAILED".equals(status)) {
//            // 处理移动失败
//            handleMovementFailure(commandId, message);
//            virtualAgv.getAgvStatus().setAgvState(AgvState.IDLE);
//        } else if ("ACCEPTED".equals(status)) {
//            AgvStatus agvStatus = virtualAgv.getAgvStatus();
//            int currentIdx = agvStatus.getCurrentNodeIndex();  // 当前所在的节点索引（起点）
//            if (currentIdx >= 0 && currentIdx < agvStatus.getNodeSequence().size() - 1) {
//                agvStatus.setLastNodeId(agvStatus.getCurrentNodeId());
//                agvStatus.setLastNodeIndex(currentIdx);
//                agvStatus.setCurrentNodeId(null);
//                agvStatus.setCurrentNodeIndex(-1);
//                // 边序列索引通常与节点索引相同：第 i 条边连接节点 i 和 i+1
//                String edgeId = agvStatus.getEdgeSequence().get(currentIdx);
//                agvStatus.setCurrentEdgeId(edgeId);
//                agvStatus.setCurrentEdgeIndex(agvStatus.getEdgeSequence().indexOf(edgeId));
//                // nextNodeId 应该已经是路径中的下一个节点，但可重新确认
//                agvStatus.setNextNodeId(agvStatus.getNodeSequence().get(currentIdx + 1));
//                agvStatus.setAgvState(AgvState.EXECUTING);
//            } else {
//                // 异常处理：索引无效
//                log.error("无法离开节点：当前索引无效");
//            }
//        }
    }

    /**
     * 从命令更新位置信息
     */
    private void updatePositionFromCommand(String commandId) {
        virtualAgv.getAgvStatus().resetDistanceSinceLastNode();
//        MoveCommandInfo commandInfo = movementManager.getCommandInfo(commandId);
//        if (commandInfo != null) {
//            // 更新当前位置
//            currentX = commandInfo.getTargetX();
//            currentY = commandInfo.getTargetY();
//            if (commandInfo.getTargetTheta() != null) {
//                currentTheta = commandInfo.getTargetTheta();
//            }
//            lastPositionUpdate = LocalDateTime.now();
//
//            // 更新AGV状态中的位置
//            if (virtualAgv.getAgvStatus() != null) {
//                virtualAgv.getAgvStatus().setX(currentX);
//                virtualAgv.getAgvStatus().setY(currentY);
//                virtualAgv.getAgvStatus().setTheta(currentTheta);
//            }
//
//            // 如果命令中有节点ID，更新节点位置
//            if (commandInfo.getNodeId() != null) {
//                Node node = mapService.getNodeById(commandInfo.getNodeId());
//                if (node != null) {
//                    updateAgvPosition(node);
//                }
//            }
//
//            log.info("更新AGV位置: commandId={}, 新位置({}, {}, {})",
//                    commandId, currentX, currentY, currentTheta);
//        }
    }

    /**
     * 处理移动失败
     */
    private void handleMovementFailure(String commandId, String message) {
//        MoveCommandInfo commandInfo = movementManager.getCommandInfo(commandId);
//        String nodeId = commandInfo != null ? commandInfo.getNodeId() : "未知节点";
//
//        log.error("移动失败处理: commandId={}, 节点={}, 原因={}",
//                commandId, nodeId, message);
//
//        // 更新AGV状态为错误
//        if (virtualAgv.getAgvStatus() != null) {
//            virtualAgv.getAgvStatus().setAgvState(AgvState.ERROR);
//
//            // 如果有当前订单，更新订单状态
//            if (virtualAgv.getAgvStatus().getCurrentOrderId() != null) {
//                mqttClient.sendOrderState(
//                        virtualAgv.getAgvStatus().getCurrentOrderId(),
//                        "FAILED",
//                        virtualAgv.getAgvStatus().getOrderUpdateId(),
//                        "移动到节点 " + nodeId + " 失败: " + message
//                );
//            }
//        }
//
//        // 可以在这里实现重试逻辑
//        if (commandInfo != null && agvConfig.isRetryOnFailure()) {
//            scheduleRetry(commandInfo);
//        }
    }

//    /**
//     * 新的订单处理流程
//     */
//    private void processRealOrder(Vda5050OrderMessage orderMessage) {
//        new Thread(() -> {
//            try {
//                AgvStatus agvStatus = virtualAgv.getAgvStatus();
//                agvStatus.setOrderState("RUNNING");
//                agvStatus.setAgvState(AgvState.MOVING);
//                // 发送订单开始状态
//                agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(), "RUNNING",
//                        agvStatus.getOrderUpdateId(), "开始执行订单");
//                for (int i = 0; i < orderMessage.getNodePositions().size(); i++) {
//
//                    Vda5050OrderMessage.NodePosition nodePosition = orderMessage.getNodePositions().get(i);
//                    // 创建目标节点
//                    Node targetNode = new Node();
//                    targetNode.setId(nodePosition.getNodeId());
//                    targetNode.setX(nodePosition.getNodeDescription().getX());
//                    targetNode.setY(nodePosition.getNodeDescription().getY());
//
//                    //判断AGV是否在起点位置,如果在起点位置，直接更新状态即可，不用执行真实移动操作
//                    if (i == 0) {
//                        boolean agvAtStartNode = vda5050MessageBuilder.calcAgvAtNode(agvStatus, targetNode);
//                        if (agvAtStartNode) {
//                            virtualAgv.handleArrivedNode(targetNode);
//                            // 发送节点完成状态
//                            if (i < orderMessage.getNodePositions().size() - 1) {
//                                agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(), "RUNNING",
//                                        agvStatus.getOrderUpdateId(), "完成节点 " + nodePosition.getNodeId());
//                            }
//                            continue;
//                        }
//                    }
//
//                    if (i == orderMessage.getNodePositions().size() - 1) {
//                        targetNode.setTheta(nodePosition.getNodeDescription().getTheta());
//                    } else {
//                        //非终点的一律视为途经点，忽略其点位设置的方向，让机器人旋转到目标点和下一个点的角度方向
//                        Vda5050OrderMessage.NodePosition nextPose = orderMessage.getNodePositions().get(i + 1);
//                        double dx = nextPose.getNodeDescription().getX() - nodePosition.getNodeDescription().getX();
//                        double dy = nextPose.getNodeDescription().getY() - nodePosition.getNodeDescription().getY();
//                        double theta = Math.atan2(dy, dx);
//                        log.debug("起点:{},结束点:{},夹角:{}", nodePosition.getNodeId(), nextPose.getNodeId(), theta);
//                        targetNode.setTheta(theta);
//                    }
//
//                    //除了第一个点，需要计算通道的旋转角度
//                    Edge edge = null;
//                    if (i > 0) {
//                        edge = mapService.getEdge(agvStatus.getEdgeSequence().get(agvStatus.getCurrentNodeIndex()));
//                        Node startNode = null;
//                        Node endNode = null;
//                        if (Objects.equals(edge.getTargetId(), targetNode.getId())) {
//                            startNode = mapService.getNode(edge.getSourceId());
//                            endNode = mapService.getNode(edge.getTargetId());
//                        } else {
//                            startNode = mapService.getNode(edge.getTargetId());
//                            endNode = mapService.getNode(edge.getSourceId());
//                        }
//
//                        double dx = endNode.getX() - startNode.getX();
//                        double dy = endNode.getY() - startNode.getY();
//                        double theta = Math.atan2(dy, dx);
//                        log.debug("重新计算，通道{}，启点{}，终点{},通道方向:{}", edge.getId(), edge.getSourceId(), edge.getTargetId()
//                                , theta);
//
//                    }
//
//                    if (edge == null) {
//                        edge = new Edge();  //用默认边
//                    }
//
//                    // 执行真实移动
//                    boolean moveSuccess = moveToPosition(targetNode, edge,
//                            i == orderMessage.getNodePositions().size() - 1);
//
//                    if (!moveSuccess) {
//                        handleMoveFailed("移动到节点 " + nodePosition.getNodeId() + " 失败");  // 移动失败，发送错误状态
//                        return;
//                    } else {
//                        virtualAgv.handleArrivedNode(targetNode);   // 移动成功，更新位置
//                    }
//
//                    // 发送节点完成状态
//                    if (i < orderMessage.getNodePositions().size() - 1) {
//                        agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(), "RUNNING",
//                                agvStatus.getOrderUpdateId(), "完成节点 " + nodePosition.getNodeId());
//                    }
//                }
//
//                agvStatus.setAgvState(AgvState.EXECUTING);
//                if (orderMessage.getActions() != null && !orderMessage.getActions().isEmpty()) {
//                    for (int i = 0; i < orderMessage.getActions().size(); i++) {
//                        Vda5050OrderMessage.Action action = orderMessage.getActions().get(i);
//                        agvStatus.setCurrentAction(action);
//                        actionManager.startAction(action);
//                    }
//                }
//
//                completeOrderSimulation();
//                agvToIdle();
//            } catch (Exception e) {
//                log.error("订单处理异常:", e);
//                // 发送错误状态
//                handleMoveFailed("订单处理异常: " + e.getMessage());
//            }
//        }).start();
//    }

    private void handleMoveFailed(String status, String message) {
        if (Objects.equals(status, "FAILED")) {
            virtualAgv.getAgvStatus().setOrderState(status);
            virtualAgv.getAgvStatus().setAgvState(AgvState.ERROR);
            agvMqttGateway.sendOrderState(virtualAgv.getAgvStatus(), virtualAgv.getAgvStatus().getCurrentOrderId(),
                    "FAILED", virtualAgv.getAgvStatus().getOrderUpdateId(), message);
            agvToIdle();
        } else if (Objects.equals(status, "CANCELED")) {
            virtualAgv.getAgvStatus().setOrderState(status);
            virtualAgv.getAgvStatus().setAgvState(AgvState.IDLE);
            agvMqttGateway.sendOrderState(virtualAgv.getAgvStatus(), virtualAgv.getAgvStatus().getCurrentOrderId(),
                    "CANCELED", virtualAgv.getAgvStatus().getOrderUpdateId(), message);
            agvToIdle();
        }
    }


    private final Ros2WebSocketClient.MessageListener messageListener = new Ros2WebSocketClient.MessageListener() {

        @Override
        public void onMessage(String message) {
            log.debug("收到ROS2消息: {}", message.length() > 200 ? message.substring(0, 200) + "..." : message);
            // 使用Gson解析JSON
            try {
                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                handleRosMessage(json);
            } catch (Exception e) {
                log.warn("Gson解析失败，尝试Jackson解析: {}", e.getMessage());
            }
        }
    };

    private void handleRosMessage(JsonObject message) {
        try {
            String type = message.has("type") ? message.get("type").getAsString() : "";

            switch (type) {
                case "position_update":
                    handlePositionUpdate(message);
                    break;
                case "status":
                    handleStatusUpdate(message);
                    break;
                case "laser_scan":
                    handleLaserScan(message);
                    break;
                case "command_ack":
                    handleCommandAck(message);
                    break;
                case "navigation_status":
                    handleNavigationStatus(message);
                    break;
                case "map_update":
                    handleMapUpdate(message);
                    break;
                case "heartbeat":
                    handleHeartbeat(message);
                    break;
                case "error":
                    handleErrorMessage(message);
                    break;
                case "move_result":
                    handleMoveResult(message);
                    break;
                default:
                    log.warn("未知的消息类型: {}", type);
                    log.debug("原始消息: {}", message);
            }
        } catch (Exception e) {
            log.error("处理ROS2消息失败", e);
        }
    }

    /**
     * 处理移动结果
     */
    private void handleMoveResult(JsonObject message) {
        String commandId = message.get("command_id").getAsString();
        String nodeId = message.get("node_id").getAsString();
        String status = message.get("status").getAsString();
        String messageText = message.has("message") ? message.get("message").getAsString() : "";
        log.info("收到移动结果: commandId={}, status={}, message={}", commandId, status, messageText);
        // 转发给状态管理器
        handleMovementResult(commandId, nodeId, status, messageText);
    }

    private void handleMessageWithJackson(String payload) {
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            String type = rootNode.has("type") ? rootNode.get("type").asText() : "";

            // 这里可以添加Jackson处理逻辑，但建议统一使用Gson
            log.debug("使用Jackson解析的消息类型: {}", type);

            // 转换为Gson格式处理
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            handleRosMessage(json);

        } catch (JsonProcessingException e) {
            log.error("ROS2消息转JSON发生异常: {}", e.getMessage());
        } catch (Exception e) {
            log.error("处理ROS2消息失败", e);
        }
    }

    private void handlePositionUpdate(JsonObject message) {
        try {
            String agvId = message.get("agv_id").getAsString();
            JsonObject position = message.getAsJsonObject("position");
            JsonObject velocity = message.getAsJsonObject("velocity");
        } catch (Exception e) {
            log.error("处理位置更新失败", e);
        }
    }

    private void handleStatusUpdate(JsonObject message) {
        try {
            String agvId = message.get("agv_id").getAsString();
            String state = message.get("state").getAsString();
            double battery = message.get("battery").getAsDouble();

            AgvStatusDto dto =
                    AgvStatusDto.builder().agvId(agvId).state(state).battery(battery).timestamp(message.get(
                            "timestamp").getAsLong()).build();

//            if (agvStatusManager != null) {
            //agvStatusManager.updateAgvStatus(dto);
//            }

        } catch (Exception e) {
            log.error("处理状态更新失败", e);
        }
    }

    private void handleLaserScan(JsonObject message) {
        try {
            String agvId = message.get("agv_id").getAsString();

            LaserScanDto dto =
                    LaserScanDto.builder().agvId(agvId).timestamp(message.get("timestamp").getAsLong()).rangeMin(message.get("range_min").getAsDouble()).rangeMax(message.get("range_max").getAsDouble()).angleMin(message.get("angle_min").getAsDouble()).angleMax(message.get("angle_max").getAsDouble()).ranges(gson.fromJson(message.get("ranges"), new TypeToken<List<Float>>() {
                    }.getType())).build();

//            if (agvStatusManager != null) {
//                agvStatusManager.processLaserScan(dto);
//            }

        } catch (Exception e) {
            log.error("处理雷达扫描数据失败", e);
        }
    }

    private void handleCommandAck(JsonObject message) {
        try {
            String agvId = message.get("agv_id").getAsString();
            String commandId = message.get("command_id").getAsString();
            String status = message.get("status").getAsString();

            CommandAckDto dto =
                    CommandAckDto.builder().agvId(agvId).commandId(commandId).status(status).timestamp(message.get(
                            "timestamp").getAsLong()).build();

//            if (agvStatusManager != null) {
//                agvStatusManager.updateCommandStatus(dto);
//            }

            log.info("命令确认: AGV={}, 命令={}, 状态={}", agvId, commandId, status);

        } catch (Exception e) {
            log.error("处理命令确认失败", e);
        }
    }

    private void handleNavigationStatus(JsonObject message) {
        try {
            String agvId = message.get("agv_id").getAsString();
            String status = message.get("status").getAsString();

            log.debug("导航状态更新: AGV={}, 状态={}", agvId, status);

//            if (agvStatusManager != null) {
            //agvStatusManager.updateNavigationStatus(agvId, status);
//            }

        } catch (Exception e) {
            log.error("处理导航状态失败", e);
        }
    }

    private void handleMapUpdate(JsonObject message) {
        try {
            log.debug("收到地图更新消息");
            // 地图数据较大，根据需要处理
        } catch (Exception e) {
            log.error("处理地图更新失败", e);
        }
    }

    private void handleHeartbeat(JsonObject message) {
        try {
            String agvId = message.get("agv_id").getAsString();
            log.debug("收到心跳: AGV={}", agvId);

//            if (agvStatusManager != null) {
            //agvStatusManager.updateAgvLastSeen(agvId);
//            }

        } catch (Exception e) {
            log.error("处理心跳失败", e);
        }
    }

    private void handleErrorMessage(JsonObject message) {
        try {
            String errorCode = message.get("error_code").getAsString();
            String errorMsg = message.get("error_message").getAsString();
            String agvId = message.has("agv_id") ? message.get("agv_id").getAsString() : "unknown";

            log.error("ROS2错误消息: AGV={}, 错误码={}, 错误信息={}", agvId, errorCode, errorMsg);

        } catch (Exception e) {
            log.error("处理错误消息失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        // 取消所有重连任务
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


    @Override
    public void handleVda5050PathUpdate(String topic, MqttMessage mqttMessage) {
        try {
            String payload = new String(mqttMessage.getPayload());
            log.info("收到路径更新: topic={}, payload={}", topic, payload);

            String[] parts = topic.split("/");
            if (parts.length >= 2) {
                String agvId = parts[1];
                // 验证是否为当前AGV
                String currentAgvId = virtualAgv.getAgvStatus().getAgvId();
                if (!agvId.equals(currentAgvId)) {
                    log.debug("路径更新不是发给当前AGV的，跳过");
                    return;
                }
                Map<String, Object> pathUpdate = objectMapper.readValue(payload, Map.class);
                processPathUpdate(pathUpdate);
            }
        } catch (Exception e) {
            log.error("处理路径更新失败", e);
        }
    }

    @Override
    public void handleVda5050Control(String topic, MqttMessage mqttMessage) {
        try {
            String payload = new String(mqttMessage.getPayload());
            log.info("收到控制消息: topic={}, payload={}", topic, payload);

            String[] parts = topic.split("/");
            if (parts.length >= 2) {
                String agvId = parts[1];

                // 验证是否为当前AGV
                String currentAgvId = virtualAgv.getAgvStatus().getAgvId();
                if (!agvId.equals(currentAgvId)) {
                    log.debug("控制消息不是发给当前AGV的，跳过");
                    return;
                }
                Map<String, Object> controlMessage = objectMapper.readValue(payload, Map.class);
                String command = (String) controlMessage.get("command");
                Map<String, Object> parameters = (Map<String, Object>) controlMessage.get("parameters");

                processControlCommand(command, parameters);
            }

        } catch (Exception e) {
            log.error("处理控制消息失败", e);
        }
    }

    @Override
    public void handleVda5050BroadcastControl(String topic, MqttMessage mqttMessage) {
        try {
            String payload = new String(mqttMessage.getPayload());
            log.debug("收到广播控制消息: topic={}, payload={}", topic, payload);

            // 广播消息，所有AGV都处理
            Map<String, Object> controlMessage = objectMapper.readValue(payload, Map.class);
            String command = (String) controlMessage.get("command");

            // 检查是否为广播命令（如所有AGV暂停）
            if ("PAUSE_ALL".equals(command) || "STOP_ALL".equals(command) || "RESUME_ALL".equals(command)) {
                Map<String, Object> parameters = (Map<String, Object>) controlMessage.get("parameters");
                processControlCommand(command, parameters);
            }
        } catch (Exception e) {
            log.error("处理广播控制消息失败", e);
        }
    }

    @Override
    public void handleVda5050OrderMessage(String topic, MqttMessage mqttMessage) {
        try {
            String payload = new String(mqttMessage.getPayload());
            log.info("收到订单消息: topic={}, payload={}", topic, payload);
            // 解析为VDA5050订单消息
            try {
                Vda5050OrderMessage orderMessage = objectMapper.readValue(payload, Vda5050OrderMessage.class);
                String messageId = String.valueOf(orderMessage.getHeader().getHeaderId());
                if (deduplicationService.isDuplicate(messageId)) {
                    log.warn("检测到重复Agv状态消息，已跳过处理: messageId={}", messageId);
                    return;
                }
                processVda5050Order(orderMessage);
            } catch (Exception e) {
                log.error("解析成Vda5050OrderMessage出错:{}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("处理订单消息失败", e);
        }
    }

    @Override
    public void handleVda5050BroadcastOrder(String topic, MqttMessage mqttMessage) {
        try {
            String payload = new String(mqttMessage.getPayload());
            log.debug("收到广播订单消息: topic={}, payload={}", topic, "太长了，暂无显示内容");
            // 如果是广播订单，需要AGV自己判断是否接受
            // 这里可以根据订单要求的能力、位置等条件判断
        } catch (Exception e) {
            log.error("处理广播订单消息失败", e);
        }
    }

    @Override
    public void onMqttConnect() {
        agvMqttGateway.sendAgvOnline(virtualAgv.getAgvStatus(), virtualAgv.getAgvStatus().getName(),
                virtualAgv.getAgvStatus().getAgvType());
        startAgvSimulation();
    }

    @Override
    public void onMqttDisConnect() {
        if (virtualAgv != null) {
            AgvStatus agvStatus = virtualAgv.getAgvStatus();
            agvMqttGateway.sendAgvOffline(agvStatus);
            log.info("AGV已下线: {}", agvStatus.getAgvId());
        }
    }


}
