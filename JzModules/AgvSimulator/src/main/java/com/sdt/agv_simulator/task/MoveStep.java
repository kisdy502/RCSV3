package com.sdt.agv_simulator.task;


import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 移动步骤
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MoveStep extends ExecutionStep {

    /** 目标节点ID */
    private String nodeId;

    /** 目标节点完整信息（包含计算后的角度） */
    private Node targetNode;

    /** 经过的边（用于曲线拟合，首节点为null） */
    private Edge passedEdge;

    /** 是否是订单最后一个移动点 */
    private boolean isEnd;

    /** 是否是第一个节点（用于起点判断） */
    private boolean isFirstNode;

    @Override
    public StepType getType() {
        return StepType.MOVE;
    }
}
