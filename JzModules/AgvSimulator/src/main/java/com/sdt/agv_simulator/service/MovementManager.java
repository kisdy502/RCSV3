//package com.sdt.agv_simulator.service;
//
//import com.jizhi.vda5050.domain.Edge;
//import com.jizhi.vda5050.domain.Node;
//import lombok.Getter;
//import lombok.extern.slf4j.Slf4j;
//import org.redisson.api.RBucket;
//import org.redisson.api.RedissonClient;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.TimeUnit;
//
//@Slf4j
//@Service
//public class MovementManager {
//
//    @Autowired
//    private RedissonClient redissonClient;
//
//    private static final String MOVEMENT_KEY_PREFIX = "movement:";
//    private static final int KEY_EXPIRE_SECONDS = 60;          // Redis key 过期时间
//    private static final long CHECK_INTERVAL_MS = 30000;       // 超时检查间隔（30秒）
//
//    // 当前活跃的移动任务
//    private volatile CompletableFuture<Boolean> currentFuture;
//    @Getter
//    private volatile String currentCommandId;
//    @Getter
//    private volatile String currentStatus = "IDLE"; //IDLE/ PENDING / ACCEPTED / EXECUTING /PAUSED / SUCCESS / FAILED
//
//    // 暂停时保存的移动状态
//    @Getter
//    private volatile MovementPauseState pauseState;
//
//    /**
//     * 暂停状态记录
//     */
//    @Getter
//    public static class MovementPauseState {
//        private final String commandId;
//        private final Node targetNode;
//        private final Edge passedEdge;
//        private final boolean wasEnd;
//        private final double pausedDistance;
//        private final long pausedTimestamp;
//
//        public MovementPauseState(String commandId, Node targetNode, Edge passedEdge,
//                                  boolean wasEnd, double pausedDistance) {
//            this.commandId = commandId;
//            this.targetNode = targetNode;
//            this.passedEdge = passedEdge;
//            this.wasEnd = wasEnd;
//            this.pausedDistance = pausedDistance;
//            this.pausedTimestamp = System.currentTimeMillis();
//        }
//    }
//
//    /**
//     * 创建移动任务
//     * 如果已有未完成的任务，会先取消并清理
//     */
//    public synchronized CompletableFuture<Boolean> createMovementTask(String commandId, Node node, Edge passedEdge) {
//        // 取消已有任务（如果有）
//        cancelCurrentMovement();
//
//        CompletableFuture<Boolean> future = new CompletableFuture<>();
//        currentFuture = future;
//        currentCommandId = commandId;
//        currentStatus = "PENDING";
//
//        // 向 Redis 写入 key，设置过期时间（用于超时检测）
//        String redisKey = MOVEMENT_KEY_PREFIX + commandId;
//        RBucket<String> bucket = redissonClient.getBucket(redisKey);
//        bucket.set(commandId, KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);
//
//        log.info("移动任务创建: commandId={}, 目标({}, {}, {}), Redis key={} 过期时间={}秒",
//                commandId, node.getX(), node.getY(), node.getTheta(), redisKey, KEY_EXPIRE_SECONDS);
//        return future;
//    }
//
//    /**
//     * 更新当前移动任务的状态
//     *
//     * @param commandId 命令ID，必须与当前任务ID一致，否则忽略
//     * @param nodeId    节点ID（可选）
//     * @param status    新状态
//     * @param message   附加消息（失败时使用）
//     */
//    public synchronized void updateMovementStatus(String commandId, String nodeId, String status, String message) {
//        if (!commandId.equals(currentCommandId) &&
//                (pauseState == null || !commandId.equals(pauseState.getCommandId()))) {
//            log.warn("收到非当前任务的状态更新: commandId={}, 当前任务={}, 暂停任务={}",
//                    commandId, currentCommandId,
//                    pauseState != null ? pauseState.getCommandId() : "null");
//            return;
//        }
//
//        String redisKey = MOVEMENT_KEY_PREFIX + commandId;
//        RBucket<String> bucket = redissonClient.getBucket(redisKey);
//        currentStatus = status;
//
//        switch (status) {
//            case "SUCCESS":
//                if (currentFuture != null && !currentFuture.isDone()) {
//                    currentFuture.complete(true);
//                }
//                log.info("移动命令成功: commandId={}, nodeId={}", commandId, nodeId);
//                bucket.delete();
//                cleanupCurrentCommand();
//                break;
//            case "FAILED":
//                if (currentFuture != null && !currentFuture.isDone()) {
//                    currentFuture.completeExceptionally(new RuntimeException(message));
//                }
//                log.error("移动命令失败: commandId={}, message={}", commandId, message);
//                bucket.delete();
//                cleanupCurrentCommand();
//                break;
//            case "PAUSED":  // 新增：ROS2确认暂停
//                log.info("移动命令已暂停(ROS2确认): commandId={}", commandId);
//                break;
//            case "RESUMED": // 新增：ROS2确认恢复
//                log.info("移动命令已恢复(ROS2确认): commandId={}", commandId);
//                bucket.expire(KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);
//                break;
//            case "ACCEPTED":
//            case "EXECUTING":
//                bucket.expire(KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);
//                break;
//            default:
//                log.warn("未知的移动状态: commandId={}, status={}", commandId, status);
//        }
//    }
//
//
//    /**
//     * 暂停当前移动 - 记录状态但不取消Future
//     */
//    public synchronized boolean pauseCurrentMovement(Node targetNode, Edge passedEdge, boolean wasEnd) {
//        if (currentFuture == null || currentFuture.isDone()) {
//            log.warn("没有正在进行的移动任务可以暂停");
//            return false;
//        }
//
//        // 记录暂停状态，但不完成Future（保持任务挂起）
//        pauseState = new MovementPauseState(currentCommandId, targetNode, passedEdge, wasEnd, 0.0);
//        currentStatus = "PAUSED";
//
//        log.info("移动任务已暂停: commandId={}, 目标节点={}", currentCommandId, targetNode.getId());
//        return true;
//    }
//
//    /**
//     * 恢复暂停的移动
//     */
//    public synchronized CompletableFuture<Boolean> resumeMovement(Node currentPosition) {
//        if (pauseState == null) {
//            log.warn("没有可恢复的暂停状态");
//            return null;
//        }
//
//        // 创建新的Future用于恢复后的移动
//        String resumeCommandId = pauseState.getCommandId() + "_resume";
//        CompletableFuture<Boolean> future = new CompletableFuture<>();
//        currentFuture = future;
//        currentCommandId = resumeCommandId;
//        currentStatus = "RESUMING";
//
//        // 根据当前位置到目标重新规划或继续原路径
//        log.info("移动任务恢复: 原commandId={}, 新commandId={}, 从({}, {})到({}, {})",
//                pauseState.getCommandId(), resumeCommandId,
//                currentPosition.getX(), currentPosition.getY(),
//                pauseState.getTargetNode().getX(), pauseState.getTargetNode().getY());
//
//        // 清除暂停状态
//        MovementPauseState oldState = pauseState;
//        pauseState = null;
//
//        return future;
//    }
//
//    /**
//     * 取消当前移动任务（如果存在且未完成）
//     */
//    public synchronized void cancelCurrentMovement() {
//        if (currentFuture != null && !currentFuture.isDone()) {
//            boolean cancelled = currentFuture.cancel(true);
//            log.info("取消移动任务: commandId={}, 结果={}", currentCommandId, cancelled);
//        }
//        cleanupCurrentCommand();
//    }
//
//    /**
//     * 清理当前任务的状态（本地和Redis）
//     */
//    private void cleanupCurrentCommand() {
//        // 删除 Redis key（如果有）
//        if (currentCommandId != null) {
//            String redisKey = MOVEMENT_KEY_PREFIX + currentCommandId;
//            RBucket<String> bucket = redissonClient.getBucket(redisKey);
//            if (bucket.isExists()) {
//                bucket.delete();
//            }
//        }
//        // 清空本地状态
//        currentFuture = null;
//        currentCommandId = null;
//        currentStatus = "IDLE";
//    }
//
//    /**
//     * 定时检查当前任务是否超时
//     */
//    @Scheduled(fixedDelay = CHECK_INTERVAL_MS)
//    public synchronized void checkTimeout() {
//        // 没有活跃任务或任务已完成，无需检查
//        if (currentFuture == null || currentFuture.isDone()) {
//            return;
//        }
//
//        String redisKey = MOVEMENT_KEY_PREFIX + currentCommandId;
//        RBucket<String> bucket = redissonClient.getBucket(redisKey);
//        if (!bucket.isExists()) {
//            // Redis key 已过期，说明长时间未收到状态更新，任务超时
//            log.warn("当前移动任务超时: commandId={}, Redis key={} 已过期", currentCommandId, redisKey);
//            currentFuture.completeExceptionally(new RuntimeException("移动超时，未收到状态更新"));
//            cleanupCurrentCommand();
//        }
//    }
//}