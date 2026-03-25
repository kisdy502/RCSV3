package com.jizhi.vda5050.agv;

// VDA5050标准状态枚举
public enum TaskStatus {
    WAITING,
    ACCEPTED,       // 等待执行
    INITIALIZING,  // 初始化中
    PAUSED,
    RUNNING,       // 执行中
    FINISHED,      // 已完成
    FAILED,         // 已失败
    CANCELLED         // 已失败
}
