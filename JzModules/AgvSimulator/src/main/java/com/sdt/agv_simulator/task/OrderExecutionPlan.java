package com.sdt.agv_simulator.task;


import com.sdt.agv_simulator.agv.ExecutionSnapshot;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 订单执行计划 - 将VDA5050订单解析为可执行的步骤序列
 */
@Data
public class OrderExecutionPlan {

    /** 订单ID */
    private String orderId;

    /** 执行步骤列表（按顺序） */
    private List<ExecutionStep> steps;

    /** 从指定节点索引开始执行（用于断点续作） */
    private int startNodeIndex = 0;

    /** 恢复时的动作状态（如果有） */
    private ExecutionSnapshot.ActionExecutionState resumeActionState;

    /**
     * 获取指定步骤
     */
    public ExecutionStep getStep(int index) {
        if (steps == null || index < 0 || index >= steps.size()) {
            return null;
        }
        return steps.get(index);
    }

    /**
     * 获取移动步骤数量
     */
    public int getMoveStepCount() {
        if (steps == null) return 0;
        return (int) steps.stream().filter(s -> s instanceof MoveStep).count();
    }

    /**
     * 查找指定节点ID对应的步骤索引
     */
    public int findStepIndexByNodeId(String nodeId) {
        if (steps == null) return -1;
        for (int i = 0; i < steps.size(); i++) {
            ExecutionStep step = steps.get(i);
            if (step instanceof MoveStep && ((MoveStep) step).getNodeId().equals(nodeId)) {
                return i;
            }
        }
        return -1;
    }
}
