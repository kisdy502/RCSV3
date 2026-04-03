package com.sdt.agv_simulator.agv;

import com.sdt.agv_simulator.move.MovementPauseState;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行快照 - 用于暂停/恢复订单执行
 */
@Data
@Builder
public class ExecutionSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 快照ID */
    private String snapshotId;

    /** 订单ID */
    private String orderId;

    /** 当前节点索引（下一个要执行的节点） */
    private int currentNodeIndex;

    /** 当前步骤索引 */
    private int currentStepIndex;

    /** 暂停原因 */
    private String pauseReason;

    /** 暂停时间 */
    private LocalDateTime pauseTime;

    /** 暂停时的位置 */
    private double pausedX;
    private double pausedY;
    private double pausedTheta;

    /** 移动暂停状态 */
    private MovementPauseState movementPauseState;

    /** 动作执行状态 */
    private ActionExecutionState actionState;

    /** 已完成的节点序列 */
    private List<String> completedNodes;

    /** 原始订单消息（JSON序列化后存储，用于恢复） */
    private String originalOrderJson;

    @Data
    @Builder
    public static class ActionExecutionState implements Serializable {
        private static final long serialVersionUID = 1L;

        private String actionId;
        private String actionType;
        /** 动作执行进度 0-100 */
        private int progress;
        /** 已执行时间（毫秒） */
        private long elapsedTime;
        /** 动作参数快照 */
        private String parametersJson;
        private volatile String status;
    }
}