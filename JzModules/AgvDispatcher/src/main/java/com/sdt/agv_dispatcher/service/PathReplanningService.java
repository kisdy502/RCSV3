package com.sdt.agv_dispatcher.service;


import com.jizhi.vda5050.domain.PathResult;
import com.sdt.agv_dispatcher.domain.Task;
import java.util.Optional;

/**
 * 路径重规划服务
 */
public interface PathReplanningService {

    /**
     * 为AGV重新规划路径
     * @param agvId AGV ID
     * @param task 当前任务
     * @param blockedResources 需要避开的资源
     * @return 新的路径结果
     */
    Optional<PathResult> replanPath(String agvId, Task task, String... blockedResources);

    /**
     * 查找绕过指定节点的路径
     */
    Optional<PathResult> findBypassPath(String agvId, String blockedNode, Task task);

    /**
     * 查找替代路径（完全不同的路线）
     */
    Optional<PathResult> findAlternativeRoute(String agvId, Task task);
}

