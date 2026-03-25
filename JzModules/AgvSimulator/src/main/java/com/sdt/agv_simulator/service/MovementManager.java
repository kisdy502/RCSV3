package com.sdt.agv_simulator.service;


import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MovementManager {

    @Autowired
    private RedissonClient redissonClient;

    private static final String MOVEMENT_KEY_PREFIX = "movement:";
    private static final int KEY_EXPIRE_SECONDS = 60;          // Redis key 过期时间
    private static final long CHECK_INTERVAL_MS = 30000;       // 超时检查间隔（30秒）

    private final Map<String, CompletableFuture<Boolean>> pendingMovements = new ConcurrentHashMap<>();
    private final Map<String, String> commandStatus = new ConcurrentHashMap<>();

    /**
     * 创建移动任务
     */
    public CompletableFuture<Boolean> createMovementTask(String commandId, Node node, Edge passedEdge) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        // 设置超时
        pendingMovements.put(commandId, future);
        commandStatus.put(commandId, "PENDING");

        // 向 Redis 写入 key，设置过期时间
        String redisKey = MOVEMENT_KEY_PREFIX + commandId;
        RBucket<String> bucket = redissonClient.getBucket(redisKey);
        bucket.set(commandId, KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);

        log.info("移动任务创建: commandId={}, 目标({}, {}, {}), Redis key={} 过期时间={}秒",
                commandId, node.getX(), node.getY(), node.getTheta(), redisKey, KEY_EXPIRE_SECONDS);
        return future;
    }

    /**
     * 更新移动状态
     */
    public void updateMovementStatus(String commandId, String nodeId, String status, String message) {
        CompletableFuture<Boolean> future = pendingMovements.get(commandId);
        String redisKey = MOVEMENT_KEY_PREFIX + commandId;
        RBucket<String> bucket = redissonClient.getBucket(redisKey);
        commandStatus.put(commandId, status);

        switch (status) {
            case "SUCCESS":
                if (future != null) {
                    future.complete(true);
                    log.info("移动命令成功: commandId={}, nodeId={}", commandId, nodeId);
                }
                // 删除 Redis key
                bucket.delete();
                cleanupCommand(commandId);
                break;
            case "FAILED":
                if (future != null) {
                    future.completeExceptionally(new RuntimeException(message));
                    log.error("移动命令失败: commandId={}, message={}", commandId, message);
                }
                bucket.delete();
                cleanupCommand(commandId);
                break;
            case "ACCEPTED":
            case "EXECUTING":
                bucket.expire(KEY_EXPIRE_SECONDS, TimeUnit.SECONDS);
                log.info("移动命令续期: commandId={}, 状态={}, Redis key={} 新过期时间={}秒",
                        commandId, status, redisKey, KEY_EXPIRE_SECONDS);
                break;
            default:
                log.warn("未知的移动状态: commandId={}, status={}", commandId, status);
                break;
        }
    }

    /**
     * 获取命令状态
     */
    public String getCommandStatus(String commandId) {
        return commandStatus.getOrDefault(commandId, "UNKNOWN");
    }

    /**
     * 清理命令
     */
    private void cleanupCommand(String commandId) {
        pendingMovements.remove(commandId);
        // 保留状态记录一段时间，便于查询
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(30000); // 30秒后清理状态记录
                commandStatus.remove(commandId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 定时检查超时任务
     * 每30秒执行一次，扫描本地 pendingMovements，检查 Redis key 是否存在
     */
    @Scheduled(fixedDelay = CHECK_INTERVAL_MS)
    public void checkTimeoutTasks() {
        if (pendingMovements.isEmpty()) {
            return;
        }

        log.debug("开始检查移动任务超时，当前待处理任务数: {}", pendingMovements.size());
        for (Map.Entry<String, CompletableFuture<Boolean>> entry : pendingMovements.entrySet()) {
            String commandId = entry.getKey();
            CompletableFuture<Boolean> future = entry.getValue();

            // 如果 future 已完成，忽略（理论上不应出现在 pendingMovements 中，但以防万一）
            if (future.isDone()) {
                continue;
            }

            String redisKey = MOVEMENT_KEY_PREFIX + commandId;
            RBucket<String> bucket = redissonClient.getBucket(redisKey);
            if (!bucket.isExists()) {
                // Redis key 不存在，说明长时间未续期，任务超时
                log.warn("检测到移动任务超时: commandId={}, Redis key={} 已过期", commandId, redisKey);
                future.completeExceptionally(new RuntimeException("移动超时，未收到状态更新"));
                // 清理本地记录和可能的残留 key
                bucket.delete(); // 确保删除
                cleanupCommand(commandId);
            }
        }
    }
}
