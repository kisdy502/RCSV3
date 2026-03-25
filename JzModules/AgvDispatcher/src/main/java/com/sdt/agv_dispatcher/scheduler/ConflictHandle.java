//package com.sdt.agv_dispatcher.scheduler;
//
//import com.jizhi.vda5050.agv.AgvStatus;
//import com.jizhi.vda5050.domain.*;
//import com.jizhi.vda5050.message.Vda5050InstantActions;
//import com.sdt.agv_dispatcher.conflict.ResolutionStrategy;
//import com.sdt.agv_dispatcher.mqtt.MqttGateway;
//import com.sdt.agv_dispatcher.utils.Vda5050MessageBuilder;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import java.time.LocalDateTime;
//import java.util.Objects;
//import java.util.UUID;
//
//@Component
//@Slf4j
//public class ConflictHandle {
//
//    @Autowired
//    private MqttGateway mqttGateway;
//
//    @Autowired
//    private Vda5050MessageBuilder vda5050MessageBuilder;
//
//    /**
//     * 自动处理冲突
//     */
//    public void handleAutoConflictResolution(AgvStatus agvStatus, ResolutionStrategy strategy) {
//        try {
//            // 构建控制命令（业务Bean）
//            AgvControlCommand command = buildControlCommand(agvStatus, strategy);
//            // 执行命令
//            executeCommand(agvStatus, command);
//            // 记录审计日志
//        } catch (Exception e) {
//            log.error("自动处理冲突失败: AGV={}", agvStatus.getAgvId(), e);
//            sendEmergencyStop(agvStatus, "CONFLICT_RESOLUTION_FAILED");
//        }
//    }
//
//
//    /**
//     * 构建控制命令（核心转换逻辑）
//     */
//    private AgvControlCommand buildControlCommand(AgvStatus agvStatus, ResolutionStrategy strategy) {
//        Object payload;
//        AgvControlCommand.ControlCommandType commandType;
//
//        switch (strategy.getAction()) {
//            case SLOW_DOWN:
//                payload = SpeedReductionPayload.builder()
//                        .targetSpeed(agvStatus.getMaxSpeed() * (1 - strategy.getSpeedReduction()))
//                        .reductionRatio(strategy.getSpeedReduction())
//                        .temporary(true)
//                        .duration(null)  // 持续到冲突解除
//                        .build();
//                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
//                break;
//
//            case WAIT:
//                payload = PausePayload.builder()
//                        .duration(strategy.getWaitTime())
//                        .resumeAutomatically(true)
//                        .build();
//                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
//                break;
//
//            case STOP:
//                payload = StopPayload.builder()
//                        .emergency(true)
//                        .clearOrder(false)  // 保留订单，只是暂停
//                        .build();
//                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
//                break;
//
//            case REPLAN:
//                payload = RePlanPayload.builder()
//                        .originalOrderId(agvStatus.getCurrentOrderId())
//                        .preserveProgress(true)
//                        .build();
//                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
//                break;
//            default:
//                throw new IllegalArgumentException("Unknown action: " + strategy.getAction());
//        }
//
//        return AgvControlCommand.builder()
//                .agvId(agvStatus.getAgvId())
//                .commandType(commandType)
//                .payload(payload)
//                .timestamp(LocalDateTime.now())
//                .correlationId(UUID.randomUUID().toString())
//                .build();
//    }
//
//    /**
//     * 执行控制命令（转换为VDA5050并发送）
//     */
//    private void executeCommand(AgvStatus agvStatus, AgvControlCommand command) {
//        String agvId = command.getAgvId();
//        Object payload = command.getPayload();
//
//        if (Objects.requireNonNull(command.getCommandType()) == AgvControlCommand.ControlCommandType.INSTANT_ACTION) {
//            Vda5050InstantActions vdaAction = convertToVda5050(agvStatus, payload);
//            mqttGateway.sendVda5050InstantAction(agvId, vdaAction);
//        } else {
//            log.warn("未支持的命令类型: {}", command.getCommandType());
//        }
//    }
//
//    public void sendEmergencyStop(AgvStatus agvStatus, String reason) {
//        StopPayload payload = StopPayload.builder()
//                .reason(reason)
//                .emergency(true)
//                .clearOrder(true)
//                .build();
//
//        AgvControlCommand command = AgvControlCommand.builder()
//                .agvId(agvStatus.getAgvId())
//                .commandType(AgvControlCommand.ControlCommandType.INSTANT_ACTION)
//                .payload(payload)
//                .build();
//
//        executeCommand(agvStatus, command);
//    }
//
//
//    /**
//     * 转换为VDA5050协议消息
//     */
//    private Vda5050InstantActions convertToVda5050(AgvStatus agvStatus, Object payload) {
//        if (payload instanceof SpeedReductionPayload) {
//            return vda5050MessageBuilder.createSpeedLimitAction(agvStatus, (SpeedReductionPayload) payload);
//        } else if (payload instanceof PausePayload) {
//            return vda5050MessageBuilder.createStartPauseAction(agvStatus, (PausePayload) payload);
//        } else if (payload instanceof StopPayload) {
//            return vda5050MessageBuilder.createCancelOrderAction(agvStatus, (StopPayload) payload);
//        } else if (payload instanceof RePlanPayload) {
//            return vda5050MessageBuilder.createRePlanNotification(agvStatus, (RePlanPayload) payload);
//        }
//        throw new IllegalArgumentException("Unknown payload type: " + payload.getClass());
//    }
//
//
//}
