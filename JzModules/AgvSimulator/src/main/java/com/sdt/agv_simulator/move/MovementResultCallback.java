package com.sdt.agv_simulator.move;

import com.jizhi.vda5050.domain.Node;

/**
 * 移动结果回调 - 解耦 MovementManager 和 AgvStatusManager
 */
public interface MovementResultCallback {
    /**
     * 移动完成（成功到达目标点）
     */
    void onMovementSuccess(String commandId, String nodeId, Node reachedNode);

    /**
     * 移动失败
     */
    void onMovementFailed(String commandId, String nodeId, String status,String reason);

    /**
     * 移动状态变更（ACCEPTED/EXECUTING等）
     */
    void onMovementStateChanged(String commandId, String nodeId, String state);
}
