package com.sdt.agv_dispatcher.conflict;

/**
 * AGV之间的方向关系
 */
public enum DirectionRelation {
    SAME_DIRECTION,         // 同向行驶（跟随）
    HEAD_ON_APPROACHING,    // 对向接近
    CONVERGENT,             // 路径汇聚（可能冲突）
    DIVERGENT,              // 路径分离（无冲突）
    CROSSING,               // 路径交叉（十字路口）
    STATIONARY              // 对方静止
}
