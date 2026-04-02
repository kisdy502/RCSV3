package com.sdt.agv_simulator.agv;


import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AGV执行状态快照 - 用于暂停/恢复
 */
@Data
@Builder
public class ExecutionSnapshot {
    // 订单信息
    private String orderId;
    private Integer orderUpdateId;

    // 路径进度
    private int currentNodeIndex;      // 当前执行到的节点索引
    private int currentEdgeIndex;      // 当前执行到的通道索引
    private String currentNodeId;      // 当前节点ID
    private String nextNodeId;         // 下一个目标节点ID

    // 位置信息（暂停时的精确位置）
    private Double pausedX;
    private Double pausedY;
    private Double pausedTheta;
    private Double distanceSinceLastNode;  // 距离上一个节点有多远

    // 动作执行状态
    private ActionExecutionState actionState;
    private String currentActionId;
    private String currentActionType;
    private int actionProgressPercent;   // 动作完成百分比（如顶升到50%）

    // 剩余路径
    private List<Node> remainingNodes;
    private List<Edge> remainingEdges;

    // 时间戳
    private LocalDateTime pausedTime;
    private Long pausedTimestampNs;

    // 暂停原因
    private String pauseReason;

    @Data
    @Builder
    public static class ActionExecutionState {
        private String actionId;
        private String actionType;
        private String status;           // RUNNING, PAUSED, COMPLETED
        private int progressPercent;     // 0-100
        private Object actionContext;    // 动作特定的上下文（如顶升高度、机械臂关节角度等）
    }
}
