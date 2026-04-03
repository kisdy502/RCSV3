package com.sdt.agv_simulator.move;

import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.sdt.agv_simulator.component.Ros2WebSocketClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class MovementManager {

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private Ros2WebSocketClient ros2WebSocketClient;

    private static final String MOVEMENT_KEY_PREFIX = "movement:";
    private static final int KEY_EXPIRE_SECONDS = 60;          // Redis key 过期时间
    private static final long CHECK_INTERVAL_MS = 30000;       // 超时检查间隔（30秒）

    // 当前移动上下文
    private volatile MovementContext currentContext;

    // 暂停时保存的移动状态
    @Getter
    private volatile MovementPauseState pauseState;

    /**
     * 执行移动 - 异步方式，通过回调通知结果
     *
     * @param agvId      AGV ID
     * @param targetNode 目标节点
     * @param passedEdge 经过的边（用于曲线拟合）
     * @param isEnd      是否是订单最后一个点
     * @param callback   结果回调
     * @return 命令ID
     */
    public synchronized String executeMovement(String agvId, Node targetNode, Edge passedEdge,
                                               boolean isEnd, MovementResultCallback callback) {
        // 取消当前任务（如果有）
        cancelCurrentMovement("新移动任务开始");

        String commandId = generateCommandId();
        currentContext = new MovementContext(commandId, targetNode, passedEdge, isEnd, callback);

        // 发送到ROS2
        try {
            ros2WebSocketClient.sendMoveCommand(agvId, commandId, targetNode, passedEdge, isEnd);
            currentContext.setStatus("PENDING");
            // 设置Redis超时检测
            setRedisTimeout(commandId);
        } catch (Exception e) {
            log.error("发送移动命令失败", e);
            currentContext.getFuture().completeExceptionally(e);
            if (callback != null) {
                callback.onMovementFailed(commandId, targetNode.getId(), "发送命令失败: " + e.getMessage());
            }
            cleanup();
        }

        return commandId;
    }

    /**
     * 同步执行移动 - 阻塞直到完成或失败
     */
    public boolean executeMovementSync(String agvId, Node targetNode, Edge passedEdge,
                                       boolean isEnd, long timeoutMs) throws MovementException {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        String commandId = executeMovement(agvId, targetNode, passedEdge, isEnd,
                new MovementResultCallback() {
                    @Override
                    public void onMovementSuccess(String cmdId, String nodeId, Node reached) {
                        future.complete(true);
                    }

                    @Override
                    public void onMovementFailed(String cmdId, String nodeId, String reason) {
                        future.completeExceptionally(new MovementException(reason));
                    }

                    @Override
                    public void onMovementStateChanged(String cmdId, String nodeId, String state) {
                    }
                });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelCurrentMovement("线程中断");
            throw new MovementException("移动被中断");
        } catch (ExecutionException e) {
            throw new MovementException("移动失败: " + e.getCause().getMessage());
        } catch (TimeoutException e) {
            cancelCurrentMovement("超时");
            throw new MovementException("移动超时");
        }
    }

    /**
     * 处理移动结果回调（从HTTP接口调用）
     */
    public synchronized void handleMovementResult(String commandId, String nodeId,
                                                  String status, String message) {
        // 检查是否是当前任务或暂停的任务
        if (currentContext == null || !commandId.equals(currentContext.getCommandId())) {
            // 可能是暂停状态的任务收到回调
            if (pauseState != null && commandId.equals(pauseState.getCommandId())) {
                log.debug("收到已暂停任务的回调: commandId={}, status={}", commandId, status);
                // 暂停状态下只记录，不处理
                return;
            }
            log.warn("收到过期/未知的移动结果: commandId={}, 当前={}",
                    commandId, currentContext != null ? currentContext.getCommandId() : "null");
            return;
        }

        MovementContext ctx = currentContext;
        ctx.setStatus(status);

        log.info("移动结果更新: commandId={}, status={}, message={}", commandId, status, message);

        switch (status) {
            case "ACCEPTED":
                refreshRedisTimeout(commandId);
                notifyStateChanged(ctx, nodeId, "ACCEPTED");
                break;
            case "EXECUTING":
                refreshRedisTimeout(commandId);
                notifyStateChanged(ctx, nodeId, "EXECUTING");
                break;
            case "SUCCESS":
                clearRedisTimeout(commandId);
                if (!ctx.getFuture().isDone()) {
                    ctx.getFuture().complete(true);
                }
                if (ctx.getCallback() != null) {
                    ctx.getCallback().onMovementSuccess(commandId, nodeId, ctx.getTargetNode());
                }
                cleanup();
                break;
            case "FAILED":
                clearRedisTimeout(commandId);
                if (!ctx.getFuture().isDone()) {
                    ctx.getFuture().complete(false);
                }
                if (ctx.getCallback() != null) {
                    ctx.getCallback().onMovementFailed(commandId, nodeId, message);
                }
                cleanup();
                break;
            case "CANCELLED":
                break;
            case "PAUSED":  // ROS2确认暂停
                log.info("移动命令已暂停(ROS2确认): commandId={}", commandId);
                break;
            case "RESUMED": // ROS2确认恢复
                log.info("移动命令已恢复(ROS2确认): commandId={}", commandId);
                refreshRedisTimeout(commandId);
                break;
            default:
                log.warn("未知的移动状态: commandId={}, status={}", commandId, status);
        }
    }

    /**
     * 暂停当前移动 - 记录状态但不取消Future
     */
    public synchronized MovementPauseState pauseMovement(String reason) {
        if (currentContext == null || currentContext.getFuture().isDone()) {
            log.warn("没有正在进行的移动可以暂停");
            return null;
        }

        // 记录暂停状态，但不完成Future（保持任务挂起）
        pauseState = new MovementPauseState(
                currentContext.getCommandId(),
                currentContext.getTargetNode(),
                currentContext.getPassedEdge(),
                currentContext.isEnd(),
                System.currentTimeMillis() - currentContext.getStartTime()
        );
        currentContext.setStatus("PAUSED");
        log.info("移动已暂停: commandId={}, reason={}", currentContext.getCommandId(), reason);

        // 【关键】取消当前的 Future（真正的暂停）
        if (!currentContext.getFuture().isDone()) {
            currentContext.getFuture().cancel(true);
            log.info("移动任务已取消: commandId={}", currentContext.getCommandId());
        }
        // 清理当前上下文（因为已经取消了）
        cleanup();


        return pauseState;
    }

    /**
     * 恢复移动（从暂停点继续）
     * 【关键】直接创建新上下文，不调用 executeMovement（避免取消自己）
     */
    public synchronized String resumeMovement(String agvId,
                                              MovementPauseState pauseState,
                                              MovementResultCallback callback) {
        if (pauseState == null) {
            throw new IllegalArgumentException("暂停状态不能为空");
        }

        // 确保没有正在进行的任务
        if (currentContext != null && !currentContext.getFuture().isDone()) {
            log.warn("恢复移动时发现有进行中的任务，先取消: commandId={}", currentContext.getCommandId());
            cancelCurrentMovement("恢复前清理");
        }

        log.info("恢复移动: 原commandId={}, 从节点{}继续",
                pauseState.getCommandId(), pauseState.getTargetNode().getId());

        // 清除暂停状态
        this.pauseState = null;

        // 【关键】直接创建新的移动上下文，不复用 executeMovement
        String commandId = generateCommandId();
        currentContext = new MovementContext(commandId, pauseState.getTargetNode(),
                pauseState.getPassedEdge(), pauseState.isWasEnd(), callback);

        // 发送到ROS2
        try {
            ros2WebSocketClient.sendMoveCommand(agvId, commandId,
                    pauseState.getTargetNode(),
                    pauseState.getPassedEdge(),
                    pauseState.isWasEnd());

            currentContext.setStatus("PENDING");
            setRedisTimeout(commandId);

            log.info("恢复移动任务启动: commandId={}, 目标({}, {})",
                    commandId,
                    pauseState.getTargetNode().getX(),
                    pauseState.getTargetNode().getY());

        } catch (Exception e) {
            log.error("发送恢复移动命令失败", e);
            currentContext.getFuture().completeExceptionally(e);
            if (callback != null) {
                callback.onMovementFailed(commandId, pauseState.getTargetNode().getId(),
                        "发送命令失败: " + e.getMessage());
            }
            cleanup();
        }

        return commandId;
    }

    /**
     * 取消当前移动
     */
    public synchronized void cancelCurrentMovement(String reason) {
        if (currentContext != null && !currentContext.getFuture().isDone()) {
            log.info("取消移动: commandId={}, reason={}", currentContext.getCommandId(), reason);

            // 取消Future
            boolean cancelled = currentContext.getFuture().cancel(true);
            log.debug("Future取消结果: {}", cancelled);

//            if (currentContext.getCallback() != null) {
//                currentContext.getCallback().onMovementFailed(
//                        currentContext.getCommandId(),
//                        currentContext.getTargetNode().getId(),"CANCEL",
//                        "取消: " + reason
//                );
//            }
        }
        cleanup();
    }

    /**
     * 获取当前命令ID
     */
    public String getCurrentCommandId() {
        return currentContext != null ? currentContext.getCommandId() : null;
    }

    /**
     * 获取当前状态
     */
    public String getCurrentStatus() {
        return currentContext != null ? currentContext.getStatus() : "IDLE";
    }

    // ============ 私有方法 ============

    private String generateCommandId() {
        return "move_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void cleanup() {
        if (currentContext != null) {
            clearRedisTimeout(currentContext.getCommandId());
        }
        currentContext = null;
    }

    private void notifyStateChanged(MovementContext ctx, String nodeId, String state) {
        if (ctx.getCallback() != null) {
            ctx.getCallback().onMovementStateChanged(ctx.getCommandId(), nodeId, state);
        }
    }

    // ============ Redis 超时检测 ============

    private void setRedisTimeout(String commandId) {
        try {
            String redisKey = MOVEMENT_KEY_PREFIX + commandId;
            RBucket<String> bucket = redissonClient.getBucket(redisKey);
            bucket.set(commandId, KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("设置Redis超时检测失败: {}", e.getMessage());
        }
    }

    private void refreshRedisTimeout(String commandId) {
        try {
            String redisKey = MOVEMENT_KEY_PREFIX + commandId;
            RBucket<String> bucket = redissonClient.getBucket(redisKey);
            if (bucket.isExists()) {
                bucket.expire(KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("刷新Redis超时时间失败: {}", e.getMessage());
        }
    }

    private void clearRedisTimeout(String commandId) {
        try {
            if (commandId != null) {
                String redisKey = MOVEMENT_KEY_PREFIX + commandId;
                RBucket<String> bucket = redissonClient.getBucket(redisKey);
                if (bucket.isExists()) {
                    bucket.delete();
                }
            }
        } catch (Exception e) {
            log.warn("清除Redis超时检测失败: {}", e.getMessage());
        }
    }

    /**
     * 定时检查当前任务是否超时
     */
    @Scheduled(fixedDelay = CHECK_INTERVAL_MS)
    public synchronized void checkTimeout() {
        // 没有活跃任务或任务已完成，无需检查
        if (currentContext == null || currentContext.getFuture().isDone()) {
            return;
        }

        try {
            String redisKey = MOVEMENT_KEY_PREFIX + currentContext.getCommandId();
            RBucket<String> bucket = redissonClient.getBucket(redisKey);

            if (!bucket.isExists()) {
                // Redis key 已过期，说明长时间未收到状态更新，任务超时
                log.warn("当前移动任务超时: commandId={}, Redis key={} 已过期",
                        currentContext.getCommandId(), redisKey);

                if (!currentContext.getFuture().isDone()) {
                    currentContext.getFuture().completeExceptionally(
                            new RuntimeException("移动超时，未收到状态更新"));
                }

//                if (currentContext.getCallback() != null) {
//                    currentContext.getCallback().onMovementFailed(
//                            currentContext.getCommandId(),
//                            currentContext.getTargetNode().getId(),
//                            "移动超时，未收到状态更新"
//                    );
//                }

                cleanup();
            }
        } catch (Exception e) {
            log.error("检查移动任务超时异常", e);
        }
    }
}