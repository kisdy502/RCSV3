package com.sdt.agv_dispatcher.service;

import com.jizhi.vda5050.domain.PathResult;
import com.sdt.agv_dispatcher.conflict.ConflictInfo;
import com.sdt.agv_dispatcher.conflict.ConflictSeverity;
import com.sdt.agv_dispatcher.domain.Task;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RedisResourceLockService {

    @Autowired
    private RedissonClient redissonClient;

    // 记录每个AGV当前占用的资源，用于续期
    private final ConcurrentHashMap<String, Set<ResourceLockInfo>> agvResources = new ConcurrentHashMap<>();

    private static final String LOCK_KEY_PREFIX = "resource_lock:";
    private static final long LOCK_EXPIRE_SECONDS = 5;      // 锁过期时间
    private static final long RENEWAL_INTERVAL_SECONDS = 3; // 续期间隔（需小于过期时间）

    /**
     * 尝试锁定单个资源
     */
    public boolean tryLockResource(String resourceId, String type, String agvId) {
        String key = buildKey(resourceId, type);
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        boolean success = bucket.setIfAbsent(agvId, Duration.ofSeconds(LOCK_EXPIRE_SECONDS));
        if (success) {
            agvResources.computeIfAbsent(agvId, k -> ConcurrentHashMap.newKeySet()).add(new ResourceLockInfo(resourceId, type));
            log.info("redis 创建 {} key {} 成功", agvId, key);
        }
        return success;
    }

    /**
     * 释放单个资源
     */
    public void releaseResource(String resourceId, String type, String agvId) {
        String key = buildKey(resourceId, type);
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        String current = bucket.get();
        if (agvId.equals(current)) {
            bucket.delete();
            log.info("redis 删除 {} key {} 成功", agvId, key);
        }
        Set<ResourceLockInfo> resources = agvResources.get(agvId);
        if (resources != null) {
            resources.removeIf(info -> info.resourceId.equals(resourceId) && info.type.equals(type));
        }
    }

    /**
     * 释放AGV所有资源（任务结束或离线时调用）
     */
    public void releaseAllByAgv(String agvId) {
        Set<ResourceLockInfo> resources = agvResources.remove(agvId);
        if (resources != null) {
            for (ResourceLockInfo info : resources) {
                String key = buildKey(info.resourceId, info.type);
                RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                String current = bucket.get();
                if (agvId.equals(current)) {
                    bucket.delete();
                    log.info("AGV {} 释放资源 {}:{} (批量)", agvId, info.type, info.resourceId);
                }
            }
        }
    }

    /**
     * 检查资源是否被其他AGV锁定
     */
    public boolean isResourceLockedByOthers(String resourceId, String type, String agvId) {
        String key = buildKey(resourceId, type);
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        String holder = bucket.get();
        return holder != null && !holder.equals(agvId);
    }

    /**
     * 尝试锁定路径资源，返回锁定结果，包含成功列表和冲突信息
     */
    public LockPathResult tryLockPathWithDetail(List<ResourceLockInfo> resourceList, String agvId) {
        List<ResourceLockInfo> locked = new ArrayList<>();
        ConflictInfo firstConflict = null;
        for (ResourceLockInfo info : resourceList) {
            boolean lockedSuccess = tryLockResource(info.getResourceId(), info.getType(), agvId);
            if (lockedSuccess) {
                locked.add(info);
            } else {
                String holder = getLockHolder(info.getResourceId(), info.getType());
                if (holder != null && !holder.equals(agvId)) {
                    firstConflict = new ConflictInfo();
                    firstConflict.setResourceId(info.getResourceId());
                    firstConflict.setResourceType(info.getType());
                    firstConflict.setConflictingAgvId(holder);
                    firstConflict.setSeverity(ConflictSeverity.INFO);
                    firstConflict.setResourceInfo(new ResourceLockInfo(info.getResourceId(), info.getType()));
                }
                break;
            }
        }
        if (firstConflict == null) {
            return new LockPathResult(true, locked, null);
        } else {
            for (ResourceLockInfo lockedInfo : locked) {
                releaseResource(lockedInfo.getResourceId(), lockedInfo.getType(), agvId);
            }
            return new LockPathResult(false, Collections.emptyList(), firstConflict);
        }
    }

    /**
     * 批量尝试锁定路径上的资源（按顺序锁定，任一失败则回滚）
     *
     * @param resourceList 资源列表，顺序与路径一致
     * @param agvId        AGV ID
     * @return 是否全部锁定成功
     */
    public boolean tryLockPath(List<ResourceLockInfo> resourceList, String agvId) {
        List<ResourceLockInfo> locked = new ArrayList<>();
        for (ResourceLockInfo info : resourceList) {
            if (tryLockResource(info.resourceId, info.type, agvId)) {
                locked.add(info);
            } else {
                // 回滚已锁定的资源
                log.warn("锁定资源 {}:{} 失败，回滚已锁定的 {} 个资源", info.type, info.resourceId, locked.size());
                for (ResourceLockInfo lockedInfo : locked) {
                    releaseResource(lockedInfo.resourceId, lockedInfo.type, agvId);
                }
                return false;
            }
        }
        return true;
    }

    /**
     * 根据任务当前进度，构造需要锁定的后续资源列表
     * 因为每条路径都是这样的格式【起点-边-点-边-点-边-点-边-终点】所以index存在一定的关系，点比边多1
     *
     * @param task     当前任务
     * @param maxCount 最大锁定数量
     * @return 需要锁定的资源列表
     */
    public List<ResourceLockInfo> buildNextResources(Task task, int maxCount) {
        List<ResourceLockInfo> result = new ArrayList<>();
        PathResult path = task.getPathResult();
        if (path == null) return result;

        int limitCount = maxCount - task.getResourceLockList().size(); // 剩余数量 可锁定资源数量
        if (limitCount <= 0) {
            return null;
        }

        // 检查边序列是否有效
        if (path.getEdgeSequence() == null || path.getEdgeSequence().isEmpty()) {
            return null;
        }
        RedisResourceLockService.ResourceLockInfo resourceLockInfo = null;
        if (!task.getResourceLockList().isEmpty()) {
            resourceLockInfo =
                    task.getResourceLockList().get(task.getResourceLockList().size() - 1);  //最后一个资源
        }
        if (resourceLockInfo != null) {
            // 锁定的资源是节点，则从当前节点开始
            int lastResourceIndex = -1;
            String lastResourceType = null;
            int nextResourceIndex = -1;
            if (resourceLockInfo.getType().equals("NODE")) {
                lastResourceIndex = path.getNodeSequence().indexOf(resourceLockInfo.getResourceId());
                lastResourceType = "NODE";

            } else if (resourceLockInfo.getType().equals("EDGE")) {
                lastResourceIndex = path.getEdgeSequence().indexOf(resourceLockInfo.getResourceId());
                lastResourceType = "EDGE";

            }
            //锁定资源逻辑，最后一个资源是节点，下一个就锁定边，再下一个又锁定节点
            //最后一个节点是通道，下一个锁定节点，再下一个又锁定通道
            while (result.size() < limitCount) {
                if (Objects.equals(lastResourceType, "NODE")) {
                    //优化最后一个点，终点的话后面无需再锁定了，提前结束
                    if (lastResourceIndex == path.getNodeSequence().size() - 1) {
                        break;
                    }
                    nextResourceIndex = lastResourceIndex;
                    String edgeId = path.getEdgeSequence().get(nextResourceIndex);
                    result.add(new ResourceLockInfo(edgeId, "EDGE"));
                    lastResourceIndex = nextResourceIndex;
                    lastResourceType = "EDGE";
                } else if (Objects.equals(lastResourceType, "EDGE")) {
                    nextResourceIndex = lastResourceIndex + 1;
                    String nodeId = path.getNodeSequence().get(nextResourceIndex);
                    result.add(new ResourceLockInfo(nodeId, "NODE"));
                    lastResourceIndex = nextResourceIndex;
                    lastResourceType = "NODE";
                }
            }
        }
        return result;
    }

    /**
     * 获取资源当前占用者
     */
    public String getLockHolder(String resourceId, String type) {
        String key = buildKey(resourceId, type);
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        return bucket.get();
    }

    /**
     * 续期任务：定时刷新所有占用资源的过期时间
     */
    @Scheduled(fixedDelay = RENEWAL_INTERVAL_SECONDS * 1000)
    public void renewLocks() {
        if (agvResources.isEmpty()) {
            return;
        }
        log.debug("开始续期任务，当前有 {} 个AGV占用资源", agvResources.size());

        agvResources.forEach((agvId, resources) -> {
            resources.removeIf(info -> {
                String key = info.getRedisKey();
                RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                String holder = bucket.get();
                if (agvId.equals(holder)) {
                    // 重新设置过期时间
                    bucket.expire(Duration.ofSeconds(LOCK_EXPIRE_SECONDS));
                    return false; // 保留
                } else {
                    log.warn("续期失败，资源可能已被抢占或释放: key={}, holder={}", key, holder);
                    return true; // 移除
                }
            });
        });
    }

    private String buildKey(String resourceId, String type) {
        return LOCK_KEY_PREFIX + type + ":" + resourceId;
    }

    @Data
    @AllArgsConstructor
    public static class ResourceLockInfo {
        private String resourceId;
        private String type;       // "NODE" 或 "EDGE"
        private String redisKey;   // 缓存的key，避免重复构建
        private int sequence;           // 在路径中的顺序
        private long lockTimestamp;     // 锁定时间戳

        public ResourceLockInfo(String resourceId, String type) {
            this.resourceId = resourceId;
            this.type = type;
            this.redisKey = LOCK_KEY_PREFIX + type + ":" + resourceId;
        }
    }

    /**
     * 锁定结果包装类
     */
    @Data
    public static class LockPathResult {
        private boolean success;
        private List<ResourceLockInfo> locked;
        private ConflictInfo conflict;

        public LockPathResult(boolean success, List<ResourceLockInfo> locked, ConflictInfo conflict) {
            this.success = success;
            this.locked = locked != null ? locked : new ArrayList<>();
            this.conflict = conflict;
        }

        public static LockPathResult success(List<ResourceLockInfo> locked) {
            return new LockPathResult(true, locked, null);
        }

        public static LockPathResult failure(ConflictInfo conflict) {
            return new LockPathResult(false, Collections.emptyList(), conflict);
        }
    }
}
