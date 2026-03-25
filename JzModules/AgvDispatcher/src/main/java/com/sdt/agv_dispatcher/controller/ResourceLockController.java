//package com.sdt.agv_dispatcher.controller;
//
//import com.jizhi.data.CommonResult;
//import com.jizhi.data.ResultCode;
//import com.sdt.agv_dispatcher.domain.ResourceLock;
//import com.sdt.agv_dispatcher.scheduler.ResourceLockManager;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/resources")
//@Slf4j
//public class ResourceLockController {
//
//    @Autowired
//    private ResourceLockManager resourceLockManager;
//
//    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//
//    /**
//     * 获取资源锁定状态数据（API接口）
//     */
//    @GetMapping("/api/lock-status")
//    public CommonResult<Map<String, Object>> getResourceLockStatus() {
//        try {
//            Map<String, ResourceLock> nodeLocks = getNodeLocks();
//            Map<String, ResourceLock> edgeLocks = getEdgeLocks();
//
//            // 统计信息
//            int totalLocks = nodeLocks.size() + edgeLocks.size();
//
//            // AGV锁定统计
//            Map<String, Integer> agvLockStats = getAgvLockStatistics(nodeLocks, edgeLocks);
//
//            // 按资源类型分组
//            List<Map<String, Object>> nodes = new ArrayList<>();
//            List<Map<String, Object>> edges = new ArrayList<>();
//
//            nodeLocks.forEach((id, lock) -> nodes.add(convertToMap(lock)));
//            edgeLocks.forEach((id, lock) -> edges.add(convertToMap(lock)));
//
//            Map<String, Object> data = new HashMap<>();
//            data.put("nodes", nodes);
//            data.put("edges", edges);
//            data.put("totalLocks", totalLocks);
//            data.put("nodeLockCount", nodeLocks.size());
//            data.put("edgeLockCount", edgeLocks.size());
//
//            Map<String, Object> stats = new HashMap<>();
//            stats.put("agvLockStats", agvLockStats);
//            stats.put("nodeLockCount", nodeLocks.size());
//            stats.put("edgeLockCount", edgeLocks.size());
//            stats.put("totalLocks", totalLocks);
//
//            data.put("stats", stats);
//            data.put("timestamp", System.currentTimeMillis());
//
//            log.info("获取资源锁定状态成功，共{}个锁定资源", totalLocks);
//            return CommonResult.success(data, "获取资源锁定状态成功");
//
//        } catch (Exception e) {
//            log.error("获取资源锁定状态失败", e);
//            return CommonResult.failed("获取资源锁定状态失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 获取资源详情（API接口）
//     */
//    @GetMapping("/api/resource-detail")
//    public CommonResult<Map<String, Object>> getResourceDetail(
//            @RequestParam String resourceId,
//            @RequestParam String resourceType) {
//
//        try {
//            if (resourceId == null || resourceId.trim().isEmpty()) {
//                return CommonResult.validateFailed("资源ID不能为空");
//            }
//
//            if (resourceType == null || resourceType.trim().isEmpty()) {
//                return CommonResult.validateFailed("资源类型不能为空");
//            }
//
//            ResourceLock lock = resourceLockManager.getResourceStatus(resourceId, resourceType);
//
//            if (lock != null) {
//                Map<String, Object> data = convertToMap(lock);
//
//                // 添加额外的计算信息
//                Map<String, Object> extraInfo = new HashMap<>();
//                LocalDateTime now = LocalDateTime.now();
//
//                if (lock.getExpectedReleaseTime() != null) {
//                    long remainingSeconds = java.time.Duration.between(now, lock.getExpectedReleaseTime()).getSeconds();
//                    extraInfo.put("remainingSeconds", Math.max(0, remainingSeconds));
//                    extraInfo.put("isExpired", remainingSeconds < 0);
//                }
//
//                extraInfo.put("lockDuration", lock.getLockTime() != null ?
//                        java.time.Duration.between(lock.getLockTime(), now).getSeconds() : 0);
//
//                data.put("extraInfo", extraInfo);
//
//                log.info("获取资源详情成功: resourceId={}, resourceType={}", resourceId, resourceType);
//                return CommonResult.success(data, "获取资源详情成功");
//
//            } else {
//                log.warn("资源未锁定或不存在: resourceId={}, resourceType={}", resourceId, resourceType);
//                return CommonResult.failed(ResultCode.FAILED, "资源未锁定或不存在: " + resourceId);
//            }
//
//        } catch (Exception e) {
//            log.error("获取资源详情失败: resourceId={}, resourceType={}", resourceId, resourceType, e);
//            return CommonResult.failed("获取资源详情失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 获取所有锁定的资源（API接口）
//     */
//    @GetMapping("/api/all-locks")
//    public CommonResult<Map<String, Object>> getAllLocks() {
//        try {
//            Map<String, ResourceLock> nodeLocks = getNodeLocks();
//            Map<String, ResourceLock> edgeLocks = getEdgeLocks();
//
//            Map<String, Object> allLocks = new HashMap<>();
//            allLocks.put("nodes", nodeLocks);
//            allLocks.put("edges", edgeLocks);
//
//            Map<String, Object> data = new HashMap<>();
//            data.put("allLocks", allLocks);
//            data.put("nodeLockCount", nodeLocks.size());
//            data.put("edgeLockCount", edgeLocks.size());
//            data.put("totalLocks", nodeLocks.size() + edgeLocks.size());
//            data.put("timestamp", System.currentTimeMillis());
//
//            log.info("获取所有锁定资源成功，共{}个节点锁定，{}个边锁定",
//                    nodeLocks.size(), edgeLocks.size());
//            return CommonResult.success(data, "获取所有锁定资源成功");
//
//        } catch (Exception e) {
//            log.error("获取所有锁定资源失败", e);
//            return CommonResult.failed("获取所有锁定资源失败: " + e.getMessage());
//        }
//    }
//
//    // 私有辅助方法
//
//    private Map<String, ResourceLock> getNodeLocks() {
//        // 调用ResourceLockManager的新方法
//        return resourceLockManager.getAllNodeLocks();
//    }
//
//    private Map<String, ResourceLock> getEdgeLocks() {
//        // 调用ResourceLockManager的新方法
//        return resourceLockManager.getAllEdgeLocks();
//    }
//
//    private Map<String, Integer> getAgvLockStatistics(
//            Map<String, ResourceLock> nodeLocks,
//            Map<String, ResourceLock> edgeLocks) {
//
//        // 直接调用ResourceLockManager的统计方法
//        return resourceLockManager.getAllAgvLockStatistics();
//    }
//
//    private List<Map<String, Object>> convertToViewModels(Map<String, ResourceLock> locks) {
//        List<Map<String, Object>> viewModels = new ArrayList<>();
//
//        locks.forEach((id, lock) -> {
//            Map<String, Object> viewModel = new HashMap<>();
//            viewModel.put("resourceId", lock.getResourceId());
//            viewModel.put("resourceType", lock.getResourceType());
//            viewModel.put("agvId", lock.getAgvId());
//            viewModel.put("taskId", lock.getTaskId());
//            viewModel.put("lockTime", lock.getLockTime() != null ?
//                    lock.getLockTime().format(formatter) : "N/A");
//            viewModel.put("expectedReleaseTime", lock.getExpectedReleaseTime() != null ?
//                    lock.getExpectedReleaseTime().format(formatter) : "N/A");
//
//            // 计算剩余时间
//            if (lock.getExpectedReleaseTime() != null) {
//                LocalDateTime now = LocalDateTime.now();
//                long remainingSeconds = java.time.Duration.between(now, lock.getExpectedReleaseTime()).getSeconds();
//                viewModel.put("remainingTime", Math.max(0, remainingSeconds) + "秒");
//                viewModel.put("isExpired", remainingSeconds < 0);
//            } else {
//                viewModel.put("remainingTime", "未知");
//                viewModel.put("isExpired", false);
//            }
//
//            viewModels.add(viewModel);
//        });
//
//        return viewModels;
//    }
//
//    private Map<String, Object> convertToMap(ResourceLock lock) {
//        Map<String, Object> map = new HashMap<>();
//        map.put("resourceId", lock.getResourceId());
//        map.put("resourceType", lock.getResourceType());
//        map.put("agvId", lock.getAgvId());
//        map.put("taskId", lock.getTaskId());
//        map.put("lockTime", lock.getLockTime() != null ?
//                lock.getLockTime().format(formatter) : null);
//        map.put("expectedReleaseTime", lock.getExpectedReleaseTime() != null ?
//                lock.getExpectedReleaseTime().format(formatter) : null);
//        map.put("lockDuration", lock.getLockTime() != null ?
//                java.time.Duration.between(lock.getLockTime(), LocalDateTime.now()).getSeconds() : 0);
//        return map;
//    }
//}