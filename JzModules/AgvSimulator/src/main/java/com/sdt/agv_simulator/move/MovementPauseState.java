package com.sdt.agv_simulator.move;

import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 移动暂停状态 - 记录暂停时的上下文，用于恢复
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovementPauseState implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 原命令ID */
    private String commandId;

    /** 目标节点（暂停时正在前往的节点） */
    private Node targetNode;

    /** 经过的边 */
    private Edge passedEdge;

    /** 是否是终点 */
    private boolean wasEnd;

    /** 已行驶时间（毫秒） */
    private long elapsedTime;

    /** 暂停时距离目标的剩余距离（如果ROS2能提供） */
    private Double remainingDistance;

    /** 暂停时间戳 */
    private long pausedTimestamp;

    public MovementPauseState(String commandId, Node targetNode, Edge passedEdge,
                              boolean wasEnd, long elapsedTime) {
        this.commandId = commandId;
        this.targetNode = targetNode;
        this.passedEdge = passedEdge;
        this.wasEnd = wasEnd;
        this.elapsedTime = elapsedTime;
        this.pausedTimestamp = System.currentTimeMillis();
    }

    /**
     * 获取暂停时长
     */
    public long getPausedDuration() {
        return System.currentTimeMillis() - pausedTimestamp;
    }
}
