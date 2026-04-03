package com.sdt.agv_simulator.task;

import com.jizhi.vda5050.message.Vda5050OrderMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 动作步骤
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActionStep extends ExecutionStep {

    /** 动作定义 */
    private Vda5050OrderMessage.Action action;

    /** 在哪个节点后执行（或"ORDER_END"表示订单完成后） */
    private String executeAfterNode;

    @Override
    public StepType getType() {
        return StepType.ACTION;
    }

    /**
     * 获取动作ID
     */
    public String getActionId() {
        return action != null ? action.getActionId() : null;
    }

    /**
     * 获取动作类型
     */
    public String getActionType() {
        return action != null ? action.getActionType() : null;
    }
}
