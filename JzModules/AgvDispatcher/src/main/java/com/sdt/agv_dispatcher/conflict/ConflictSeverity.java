package com.sdt.agv_dispatcher.conflict;

public enum ConflictSeverity {
    CRITICAL(10, "紧急", 0),      // 立即停车
    HIGH(8, "高", 1),             // 重新规划路径
    MODERATE(5, "中", 2),         // 减速等待
    LOW(3, "低", 3),              // 观察调整
    INFO(1, "信息", 4);            // 记录日志 ← 注意分号

    private final int level;
    private final String description;
    private final int priority;

    // 显式构造器（自动为private）
    ConflictSeverity(int level, String description, int priority) {
        this.level = level;
        this.description = description;
        this.priority = priority;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }
}
