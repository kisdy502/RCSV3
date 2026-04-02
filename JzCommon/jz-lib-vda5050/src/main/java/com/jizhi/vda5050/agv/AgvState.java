package com.jizhi.vda5050.agv;

import lombok.extern.slf4j.Slf4j;

/**
 * AGV状态枚举 (VDA5050标准)
 */
@Slf4j
public enum AgvState {
    IDLE("IDLE"),                    // 空闲

    MOVING("MOVING"),               //移动中
    EXECUTING("EXECUTING"),          // 执行中
    PAUSED("PAUSED"),                // 暂停
    CHARGING("CHARGING"),            // 充电
    ERROR("ERROR"),                  // 错误
    MANUAL("MANUAL"),                // 手动模式

    // VDA5050扩展状态
    WAITING("WAITING"),              // 等待
    //    FINISHED("FINISHED"),            // 已完成
//    ABORTED("ABORTED"),              // 已中止
//    INITIALIZING("INITIALIZING"),    // 初始化中
//    EMERGENCY("EMERGENCY"),          // 紧急状态
    UNKNOWN("UNKNOWN");              // 未知

    private final String value;

    AgvState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 从VDA5050状态值转换
     */
    public static AgvState fromVda5050Value(String vda5050Value) {
        if (vda5050Value == null) return UNKNOWN;

        String normalized = vda5050Value.toUpperCase().trim();

        switch (normalized) {
            case "IDLE":
            case "FINISHED":
                return IDLE;

            case "MOVING":
                return MOVING;

            case "EXECUTING":
            case "DRIVING":
                return EXECUTING;

            case "PAUSED":
            case "STOPPED":
                return PAUSED;

            case "CHARGING":
            case "CHARGERCONNECTED":
                return CHARGING;

            case "ERROR":
            case "FAULT":
            case "FAILURE":
                return ERROR;

            case "MANUAL":
            case "MANUALCONTROL":
                return MANUAL;

            case "WAITING":
            case "WAIT":
                return WAITING;
            default:
                log.warn("未知的VDA5050状态值: {}, 使用默认值 UNKNOWN", vda5050Value);
                return UNKNOWN;
        }
    }

    /**
     * 是否允许接收新任务
     */
    public boolean canAcceptTask() {
        return this == IDLE || this == WAITING;
    }

    /**
     * 是否处于工作中（正在执行任务或充电）
     */
    public boolean isWorking() {
        return this == MOVING || this == EXECUTING || this == CHARGING;
    }


    /**
     * 是否为错误状态
     */
    public boolean isError() {
        return this == ERROR;
    }
}
