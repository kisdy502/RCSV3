package com.sdt.agv_dispatcher.conflict;

import lombok.Data;
import java.util.Set;

/**
 * 路径交叉分析结果
 */
@Data
public class PathIntersection {
    private boolean hasIntersection;        // 是否有交叉
    private Set<String> commonNodes;        // 共同节点
    private Set<String> commonEdges;        // 共同边
    private String conflictPoint;           // 冲突点
    private double myDistanceToConflict;    // 我方到冲突点距离
    private double otherDistanceToConflict; // 对方到冲突点距离
    private IntersectionType type;          // 交叉类型

    public enum IntersectionType {
        NONE,           // 无交叉
        NODE_SHARE,     // 共享节点
        EDGE_SHARE,     // 共享边
        CROSSING,       // 交叉通过
        MERGING,        // 汇入
        DIVERGING       // 分流
    }

    public boolean isHasIntersection() {
        return hasIntersection;
    }

    public Set<String> getCommonNodes() {
        return commonNodes;
    }

    public Set<String> getCommonEdges() {
        return commonEdges;
    }

    public String getConflictPoint() {
        return conflictPoint;
    }

    public double getMyDistanceToConflict() {
        return myDistanceToConflict;
    }

    public double getOtherDistanceToConflict() {
        return otherDistanceToConflict;
    }

    public IntersectionType getType() {
        return type;
    }
}
