package com.sdt.agv_dispatcher.conflict;

public enum ConflictType {

    // 1. 空间冲突 (Spatial Conflicts)
    HEAD_ON(10, "对向行驶冲突"),           // 双向通道对向行驶
    CROSSING(8, "交叉路口冲突"),           // 十字路口路径交叉
    MERGING(6, "汇入冲突"),                // 汇入同一路段
    DIVERGING(4, "分流冲突"),               // 从同一节点分出

    // 2. 资源竞争冲突 (Resource Conflicts)
    NODE_OCCUPIED(9, "节点被占用"),        // 目标节点被其他AGV占据
    EDGE_OCCUPIED(7, "边被占用"),          // 通道被其他AGV占用
    ZONE_LOCKED(8, "区域锁定"),            // 区域控制锁定

    // 3. 时序冲突 (Temporal Conflicts)
    TIME_WINDOW_OVERLAP(5, "时间窗重叠"),   // 预计到达时间冲突
    FOLLOWING_TOO_CLOSE(6, "跟随过近"),     // 后车速度过快追尾风险

    // 4. 死锁风险 (Deadlock Risks)
    LOOP_DEADLOCK_RISK(10, "环路死锁风险"), // 四向死锁风险
    WAIT_CHAIN(9, "等待链"),               // 多车循环等待
    STARVATION(7, "饥饿风险");              // 低优先级长期等待

    private final int type;
    private final String name;

    // 显式构造器（自动为private）
    ConflictType(int level, String description) {
        this.type = level;
        this.name = description;
    }
}


