package com.sdt.agv_dispatcher.utils;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.domain.PausePayload;
import com.jizhi.vda5050.domain.RePlanPayload;
import com.jizhi.vda5050.domain.SpeedReductionPayload;
import com.jizhi.vda5050.domain.StopPayload;
import com.jizhi.vda5050.message.Vda5050Action;
import com.jizhi.vda5050.message.Vda5050ActionParameter;
import com.jizhi.vda5050.message.Vda5050Header;
import com.jizhi.vda5050.message.Vda5050InstantActions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class Vda5050MessageBuilder {

    // ==================== 标准 VDA5050 动作生成 ====================
    /**
     * 构建标准Header（复用方法）
     */
    private Vda5050Header buildStandardHeader(AgvStatus agvStatus) {
        Vda5050Header header = new Vda5050Header();
        header.setHeaderId(System.currentTimeMillis());
        header.setTimestamp(Instant.now());
        header.setVersion(agvStatus.getVersion());
        header.setManufacturer(agvStatus.getManufacturer());
        header.setSerialNumber(agvStatus.getSerialNumber());
        return header;
    }

    /**
     * 生成标准 startPause 动作（VDA5050标准）
     */
    public Vda5050InstantActions createStartPauseAction(AgvStatus agvStatus, PausePayload payload) {
        List<Vda5050ActionParameter> params = new ArrayList<>();

        if (payload.getDuration() > 0) {
            params.add(Vda5050ActionParameter.builder()
                    .key("duration")
                    .value(payload.getDuration())
                    .build());
        }
        params.add(Vda5050ActionParameter.builder()
                .key("reason")
                .value(payload.getReason())
                .build());

        return Vda5050InstantActions.builder()
                .header(buildStandardHeader(agvStatus))
                .actions(Collections.singletonList(
                        Vda5050Action.builder()
                                .actionType("startPause")  // VDA5050标准动作
                                .actionId(generateActionId("pause"))
                                .blockingType(Vda5050Action.BLOCKING_HARD)
                                .actionDescription("Conflict avoidance pause")
                                .actionParameters(params)
                                .build()
                ))
                .build();
    }

    /**
     * 生成标准 stopPause 动作（VDA5050标准）
     */
    public Vda5050InstantActions createStopPauseAction(AgvStatus agvStatus) {
        return Vda5050InstantActions.builder()
                .header(buildStandardHeader(agvStatus))
                .actions(Collections.singletonList(
                        Vda5050Action.builder()
                                .actionType("stopPause")  // VDA5050标准动作
                                .actionId(generateActionId("resume"))
                                .blockingType(Vda5050Action.BLOCKING_NONE)
                                .actionDescription("Resume from pause")
                                .build()
                ))
                .build();
    }

    /**
     * 生成自定义 setSpeedLimit 动作（VDA5050扩展）
     */
    public Vda5050InstantActions createSpeedLimitAction(AgvStatus agvStatus, SpeedReductionPayload payload) {
        return Vda5050InstantActions.builder()
                .header(buildStandardHeader(agvStatus))
                .actions(Collections.singletonList(
                        Vda5050Action.builder()
                                .actionType("setSpeedLimit")  // 自定义扩展动作
                                .actionId(generateActionId("speed"))
                                .blockingType(Vda5050Action.BLOCKING_SOFT)
                                .actionDescription("Automatic speed reduction for conflict avoidance")
                                .actionParameters(Arrays.asList(
                                        Vda5050ActionParameter.builder()
                                                .key("maxSpeed")
                                                .value(payload.getTargetSpeed())
                                                .build(),
                                        Vda5050ActionParameter.builder()
                                                .key("reductionRatio")
                                                .value(payload.getReductionRatio())
                                                .build(),
                                        Vda5050ActionParameter.builder()
                                                .key("temporary")
                                                .value(payload.isTemporary())
                                                .build(),
                                        Vda5050ActionParameter.builder()
                                                .key("duration")
                                                .value(payload.getDuration())
                                                .build(),
                                        Vda5050ActionParameter.builder()
                                                .key("reason")
                                                .value(payload.getReason())
                                                .build()
                                ))
                                .build()
                ))
                .build();
    }

    /**
     * 生成标准 cancelOrder 动作（VDA5050标准）
     */
    public Vda5050InstantActions createCancelOrderAction(AgvStatus agvStatus, StopPayload payload) {
        List<Vda5050ActionParameter> params = new ArrayList<>();
        params.add(Vda5050ActionParameter.builder()
                .key("reason")
                .value(payload.getReason())
                .build());
        params.add(Vda5050ActionParameter.builder()
                .key("clearOrder")
                .value(payload.isClearOrder())
                .build());

        return Vda5050InstantActions.builder()
                .header(buildStandardHeader(agvStatus))
                .actions(Collections.singletonList(
                        Vda5050Action.builder()
                                .actionType("cancelOrder")  // VDA5050标准动作
                                .actionId(generateActionId("stop"))
                                .blockingType(Vda5050Action.BLOCKING_HARD)
                                .actionDescription(payload.isEmergency() ? "Emergency stop" : "Controlled stop")
                                .actionParameters(params)
                                .build()
                ))
                .build();
    }

    /**
     * 生成重新规划通知（非VDA5050标准，业务扩展）
     */
    public Vda5050InstantActions createRePlanNotification(AgvStatus agvStatus, RePlanPayload payload) {
        // 使用自定义 instantAction 通知AGV需要重新规划
        return Vda5050InstantActions.builder()
                .header(buildStandardHeader(agvStatus))
                .actions(Collections.singletonList(
                        Vda5050Action.builder()
                                .actionType("triggerReplan")  // 自定义动作
                                .actionId(generateActionId("replan"))
                                .blockingType(Vda5050Action.BLOCKING_NONE)
                                .actionDescription("Trigger path replanning")
                                .actionParameters(Arrays.asList(
                                        Vda5050ActionParameter.builder()
                                                .key("originalOrderId")
                                                .value(payload.getOriginalOrderId())
                                                .build(),
                                        Vda5050ActionParameter.builder()
                                                .key("preserveProgress")
                                                .value(payload.isPreserveProgress())
                                                .build()
                                ))
                                .build()
                ))
                .build();
    }


    private String generateActionId(String prefix) {
        return String.format("%s_%d_%s",
                prefix,
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * 转换为JSON字符串
     */
    public String toJson(Vda5050InstantActions actions) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(actions);
        } catch (JsonProcessingException e) {
            log.error("VDA5050消息序列化失败", e);
            throw new RuntimeException("消息序列化失败", e);
        }
    }
}