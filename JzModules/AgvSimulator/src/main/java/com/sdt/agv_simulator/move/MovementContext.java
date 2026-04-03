package com.sdt.agv_simulator.move;

import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import lombok.Data;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

/**
 * 移动上下文 - 包含一次移动的完整信息
 */
@Data
public  class MovementContext {
    private final String commandId;
    private final Node targetNode;
    private final Edge passedEdge;
    private final boolean isEnd;
    private final MovementResultCallback callback;
    private final CompletableFuture<Boolean> future;
    private final long startTime;
    private volatile String status = "PENDING"; // PENDING/ACCEPTED/EXECUTING/SUCCESS/FAILED/PAUSED/RESUMING

    public MovementContext(String commandId, Node targetNode, Edge passedEdge,
                           boolean isEnd, MovementResultCallback callback) {
        this.commandId = commandId;
        this.targetNode = targetNode;
        this.passedEdge = passedEdge;
        this.isEnd = isEnd;
        this.callback = callback;
        this.future = new CompletableFuture<>();
        this.startTime = System.currentTimeMillis();
    }
}
