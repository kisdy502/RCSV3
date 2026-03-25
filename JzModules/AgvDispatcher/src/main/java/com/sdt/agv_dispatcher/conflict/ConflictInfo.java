package com.sdt.agv_dispatcher.conflict;

import com.sdt.agv_dispatcher.service.RedisResourceLockService;
import lombok.Data;

/**
 * 冲突信息封装类
 */
@Data
public class ConflictInfo {

    private String resourceId;
    private String resourceType; // NODE / EDGE
    private String conflictingAgvId;
    private ConflictSeverity severity;

    private ConflictType conflictType;  // 冲突类型
    private RedisResourceLockService.ResourceLockInfo resourceInfo;  // 资源信息

    // 对方信息
    private Integer otherAgvPriority;   // 对方优先级
    private String otherAgvState;       // 对方状态

    // 扩展信息
    private boolean canReplan;          // 是否可以重新规划
    private double estimatedWaitSeconds;  // 预计等待时间

    // ========== 以下方法用于冲突决策，需要根据实际业务完善 ==========

    /**
     * 判断是否为对向冲突（例如在双向通道中两车相向而行）
     */
    public boolean isHeadOn() {
        // TODO: 根据资源类型、方向等信息判断
        // 示例：如果是边，且两车行驶方向相反，返回 true
        // 此处需要地图边方向数据支持，暂返回 false
        return false;
    }

    /**
     * 判断是否为交叉口冲突（节点处多车交汇）
     */
    public boolean isIntersection() {
        // TODO: 根据节点拓扑判断（如节点连接多条边）
        // 暂返回 false
        return false;
    }


}