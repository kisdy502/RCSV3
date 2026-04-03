package com.sdt.agv_simulator.task;


import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 执行步骤 - 抽象基类
 */
@Data
public abstract class ExecutionStep {

    /** 步骤索引（执行顺序） */
    private int stepIndex;

    /** 步骤类型 */
    public abstract StepType getType();

    public enum StepType {
        MOVE,      // 移动
        ACTION     // 动作
    }
}
