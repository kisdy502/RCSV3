package com.sdt.agv_dispatcher.conflict;

import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 冲突分析结果
 */
@Data
public class ConflictAnalysisResult {
    private String myAgvId;                 // 我方AGV ID
    private String otherAgvId;              // 对方AGV ID
    private String resourceId;              // 冲突资源ID
    private String resourceType;            // 资源类型（NODE/EDGE）

    private ConflictType conflictType;      // 冲突类型
    private ConflictSeverity severity;      // 严重级别

    private DirectionRelation directionRelation;  // 方向关系
    private PathIntersection pathIntersection;    // 路径交叉情况
    private DeadlockRisk deadlockRisk;            // 死锁风险

    private Duration estimatedWaitTime;     // 预计等待时间
    private LocalDateTime timestamp;        // 分析时间戳

    // 扩展信息
    private double distanceToConflict;      // 距离冲突点距离
    private int myPriority;                 // 我方优先级
    private Integer otherPriority;           // 对方优先级

}
