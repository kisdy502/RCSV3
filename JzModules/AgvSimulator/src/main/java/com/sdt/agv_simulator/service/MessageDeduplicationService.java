package com.sdt.agv_simulator.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageDeduplicationService {

    // 使用ConcurrentHashMap存储已处理消息ID（适合单机部署）
    private final ConcurrentHashMap<String, Long> processedMessageIds = new ConcurrentHashMap<>();

    private static final long DEDUP_WINDOW_MS = 60 * 1000; // (x)Time去重窗口

    /**
     * 检查并记录消息是否重复
     *
     * @param messageId 消息唯一标识
     * @return true-新消息, false-重复消息
     */
    public boolean isDuplicate(String messageId) {
        // 或者使用内存去重（单机版）
        return processedMessageIds.putIfAbsent(messageId, System.currentTimeMillis()) != null;
    }

    /**
     * 清理过期消息ID（定期任务）
     */
    @Scheduled(fixedRate = 300 * 1000) //300秒
    public void cleanupExpiredMessages() {
        long cutoffTime = System.currentTimeMillis() - DEDUP_WINDOW_MS;
        processedMessageIds.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }
}
