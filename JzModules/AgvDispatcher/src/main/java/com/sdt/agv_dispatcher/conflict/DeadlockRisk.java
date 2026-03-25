package com.sdt.agv_dispatcher.conflict;

/**
 * 死锁风险级别
 */
public enum DeadlockRisk {
    NONE,                   // 无风险
    TWO_WAY_DEADLOCK,       // 两向死锁（A等B，B等A）
    LOOP_DEADLOCK,          // 环路死锁（A->B->C->A）
    LONG_WAIT_CHAIN         // 长等待链（>=3个AGV循环等待）
}
