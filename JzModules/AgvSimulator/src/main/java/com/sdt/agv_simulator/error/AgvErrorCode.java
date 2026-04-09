package com.sdt.agv_simulator.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AGV错误码枚举
 * 格式: E[模块][级别][编号]
 * 模块: N导航 M移动 C充电 O作业 R订单 S系统 H硬件
 * 级别: 0警告 1错误 2致命
 */
public enum AgvErrorCode {

    // ==================== 导航相关 (EN) ====================
    // Warning级别 (EN0xxx)
    EN0101("EN0101", ErrorModule.NAVIGATION, ErrorLevel.WARNING,
            "ROS2 WebSocket连接不稳定", "检查网络质量，启用重连机制"),
    EN0102("EN0102", ErrorModule.NAVIGATION, ErrorLevel.WARNING,
            "位置上报频率低", "检查定位节点负载"),
    EN0103("EN0103", ErrorModule.NAVIGATION, ErrorLevel.WARNING,
            "雷达数据频率低", "检查激光雷达驱动"),
    EN0104("EN0104", ErrorModule.NAVIGATION, ErrorLevel.WARNING,
            "AGV状态上报频率低", "检查上位机负载"),
    EN0105("EN0105", ErrorModule.NAVIGATION, ErrorLevel.WARNING,
            "TF变换超时", "检查robot_state_publisher"),
    EN0106("EN0106", ErrorModule.NAVIGATION, ErrorLevel.WARNING,
            "定位质量下降", "清洁雷达，检查反光板"),
    EN0110("EN0110", ErrorModule.NAVIGATION, ErrorLevel.WARNING,
            "全局定位丢失", "触发重定位流程"),

    // Error级别 (EN1xxx)
    EN0201("EN0201", ErrorModule.NAVIGATION, ErrorLevel.ERROR,
            "ROS2 WebSocket连接失败", "检查ROS2桥接器节点"),
    EN0202("EN0202", ErrorModule.NAVIGATION, ErrorLevel.ERROR,
            "定位完全丢失", "停止移动，人工介入"),
    EN0203("EN0203", ErrorModule.NAVIGATION, ErrorLevel.ERROR,
            "地图加载失败", "检查地图文件完整性"),
    EN0204("EN0204", ErrorModule.NAVIGATION, ErrorLevel.ERROR,
            "坐标转换异常", "检查TF发布节点"),
    EN0205("EN0205", ErrorModule.NAVIGATION, ErrorLevel.ERROR,
            "导航目标点无效", "重新下发有效目标"),
    EN0206("EN0206", ErrorModule.NAVIGATION, ErrorLevel.ERROR,
            "路径规划失败", "检查地图连通性"),
    EN0207("EN0207", ErrorModule.NAVIGATION, ErrorLevel.ERROR,
            "局部规划器异常", "检查代价地图配置"),

    // Fatal级别 (EN2xxx)
    EN0301("EN0301", ErrorModule.NAVIGATION, ErrorLevel.FATAL,
            "导航系统核心节点崩溃", "重启导航服务"),
    EN0302("EN0302", ErrorModule.NAVIGATION, ErrorLevel.FATAL,
            "传感器数据完全中断", "紧急停止，检查硬件"),

    // ==================== 移动相关 (EM) ====================
    // Warning级别
    EM0101("EM0101", ErrorModule.MOVEMENT, ErrorLevel.WARNING,
            "速度跟踪偏差大", "检查驱动器PID参数"),
    EM0102("EM0102", ErrorModule.MOVEMENT, ErrorLevel.WARNING,
            "路径跟踪偏差大", "调整局部规划器参数"),
    EM0103("EM0103", ErrorModule.MOVEMENT, ErrorLevel.WARNING,
            "电机温度偏高", "降低负载，检查散热"),
    EM0104("EM0104", ErrorModule.MOVEMENT, ErrorLevel.WARNING,
            "轮速差异警告", "检查轮胎磨损/地面"),
    EM0105("EM0105", ErrorModule.MOVEMENT, ErrorLevel.WARNING,
            "加速度受限", "检查负载是否超重"),

    // Error级别
    EM0201("EM0201", ErrorModule.MOVEMENT, ErrorLevel.ERROR,
            "移动超时", "检查路径障碍物"),
    EM0202("EM0202", ErrorModule.MOVEMENT, ErrorLevel.ERROR,
            "移动被阻塞", "触发脱困策略或求助"),
    EM0203("EM0203", ErrorModule.MOVEMENT, ErrorLevel.ERROR,
            "目标点到达失败", "检查目标点可达性"),
    EM0204("EM0204", ErrorModule.MOVEMENT, ErrorLevel.ERROR,
            "旋转失败", "检查地面摩擦/电机"),
    EM0205("EM0205", ErrorModule.MOVEMENT, ErrorLevel.ERROR,
            "曲线通道跟踪失败", "降低曲线速度限制"),
    EM0206("EM0206", ErrorModule.MOVEMENT, ErrorLevel.ERROR,
            "碰撞风险触发", "检查障碍物清除后恢复"),
    EM0207("EM0207", ErrorModule.MOVEMENT, ErrorLevel.ERROR,
            "轮子打滑检测", "检查地面湿滑/油污"),

    // Fatal级别
    EM0301("EM0301", ErrorModule.MOVEMENT, ErrorLevel.FATAL,
            "移动执行器故障", "停止，切换备用驱动"),
    EM0302("EM0302", ErrorModule.MOVEMENT, ErrorLevel.FATAL,
            "碰撞发生", "立即停止，人工检查"),

    // ==================== 充电相关 (EC) ====================
    EC0101("EC0101", ErrorModule.CHARGING, ErrorLevel.WARNING,
            "电量偏低", "建议规划充电任务"),
    EC0102("EC0102", ErrorModule.CHARGING, ErrorLevel.WARNING,
            "充电电流低于预期", "检查充电接触"),
    EC0103("EC0103", ErrorModule.CHARGING, ErrorLevel.WARNING,
            "电池温度偏高", "暂停充电，检查散热"),
    EC0104("EC0104", ErrorModule.CHARGING, ErrorLevel.WARNING,
            "充电对接偏差大", "重新调整对接位姿"),

    EC0201("EC0201", ErrorModule.CHARGING, ErrorLevel.ERROR,
            "自动回充失败", "检查充电桩占用/地图"),
    EC0202("EC0202", ErrorModule.CHARGING, ErrorLevel.ERROR,
            "充电对接失败", "人工辅助对接"),
    EC0203("EC0203", ErrorModule.CHARGING, ErrorLevel.ERROR,
            "充电异常中断", "检查接触器/线缆"),
    EC0204("EC0204", ErrorModule.CHARGING, ErrorLevel.ERROR,
            "电池过放保护", "立即手动充电"),
    EC0205("EC0205", ErrorModule.CHARGING, ErrorLevel.ERROR,
            "BMS通信故障", "检查BMS CAN通信"),

    EC0301("EC0301", ErrorModule.CHARGING, ErrorLevel.FATAL,
            "电池过热", "紧急停机，远离可燃物"),
    EC0302("EC0302", ErrorModule.CHARGING, ErrorLevel.FATAL,
            "充电系统短路", "断开电源，检修"),

    // ==================== 作业相关 (EO) ====================
    EO0101("EO0101", ErrorModule.OPERATION, ErrorLevel.WARNING,
            "货叉高度偏差", "校准货叉编码器"),
    EO0102("EO0102", ErrorModule.OPERATION, ErrorLevel.WARNING,
            "负载重量接近上限", "提醒轻载运行"),
    EO0103("EO0103", ErrorModule.OPERATION, ErrorLevel.WARNING,
            "机械臂关节超限", "调整作业姿态"),
    EO0104("EO0104", ErrorModule.OPERATION, ErrorLevel.WARNING,
            "IO模块响应慢", "检查Modbus连接"),

    EO0201("EO0201", ErrorModule.OPERATION, ErrorLevel.ERROR,
            "举升失败", "检查液压/电机"),
    EO0202("EO0202", ErrorModule.OPERATION, ErrorLevel.ERROR,
            "叉取失败", "检查货叉对齐"),
    EO0203("EO0203", ErrorModule.OPERATION, ErrorLevel.ERROR,
            "卸货失败", "检查货物卡滞"),
    EO0204("EO0204", ErrorModule.OPERATION, ErrorLevel.ERROR,
            "机械臂执行失败", "重新规划轨迹"),
    EO0205("EO0205", ErrorModule.OPERATION, ErrorLevel.ERROR,
            "PLC指令执行失败", "检查PLC通信"),
    EO0206("EO0206", ErrorModule.OPERATION, ErrorLevel.ERROR,
            "货物掉落检测", "停止，检查货物状态"),

    EO0301("EO0301", ErrorModule.OPERATION, ErrorLevel.FATAL,
            "执行机构失控", "急停，切断动力"),
    EO0302("EO0302", ErrorModule.OPERATION, ErrorLevel.FATAL,
            "货物严重超载", "拒绝执行，卸货"),

    // ==================== 订单相关 (ER) ====================
    ER0101("ER0101", ErrorModule.ORDER, ErrorLevel.WARNING,
            "订单节点跳过", "记录日志，继续执行"),
    ER0102("ER0102", ErrorModule.ORDER, ErrorLevel.WARNING,
            "订单执行延迟", "检查该节点作业效率"),
    ER0103("ER0103", ErrorModule.ORDER, ErrorLevel.WARNING,
            "动作重试", "自动重试，记录警告"),

    ER0201("ER0201", ErrorModule.ORDER, ErrorLevel.ERROR,
            "订单解析失败", "拒绝订单，上报调度"),
    ER0202("ER0202", ErrorModule.ORDER, ErrorLevel.ERROR,
            "节点不存在", "拒绝订单，检查地图"),
    ER0203("ER0203", ErrorModule.ORDER, ErrorLevel.ERROR,
            "通道不存在", "拒绝订单，检查地图"),
    ER0204("ER0204", ErrorModule.ORDER, ErrorLevel.ERROR,
            "动作未定义", "拒绝订单，更新动作库"),
    ER0205("ER0205", ErrorModule.ORDER, ErrorLevel.ERROR,
            "订单冲突", "排队或拒绝新订单"),
    ER0206("ER0206", ErrorModule.ORDER, ErrorLevel.ERROR,
            "订单超时", "评估是否强制完成"),
    ER0207("ER0207", ErrorModule.ORDER, ErrorLevel.ERROR,
            "订单取消失败", "强制停止，人工确认"),
    ER0208("ER0208", ErrorModule.ORDER, ErrorLevel.ERROR,
            "订单恢复失败", "检查当前状态一致性"),

    ER0301("ER0301", ErrorModule.ORDER, ErrorLevel.FATAL,
            "订单系统异常", "重置订单模块"),

    // ==================== 系统相关 (ES) ====================
    ES0101("ES0101", ErrorModule.SYSTEM, ErrorLevel.WARNING,
            "内存使用率偏高", "检查内存泄漏"),
    ES0102("ES0102", ErrorModule.SYSTEM, ErrorLevel.WARNING,
            "CPU负载持续偏高", "检查高消耗进程"),
    ES0201("ES0201", ErrorModule.SYSTEM, ErrorLevel.ERROR,
            "磁盘空间不足", "清理日志文件"),
    ES0301("ES0301", ErrorModule.SYSTEM, ErrorLevel.FATAL,
            "系统核心进程崩溃", "重启上位机服务"),

    // ==================== 硬件相关 (EH) ====================
    EH0101("EH0101", ErrorModule.HARDWARE, ErrorLevel.WARNING,
            "雷达脏污警告", "清洁激光雷达"),
    EH0102("EH0102", ErrorModule.HARDWARE, ErrorLevel.WARNING,
            "相机曝光异常", "调整相机参数"),
    EH0201("EH0201", ErrorModule.HARDWARE, ErrorLevel.ERROR,
            "雷达硬件故障", "更换激光雷达"),
    EH0202("EH0202", ErrorModule.HARDWARE, ErrorLevel.ERROR,
            "编码器数据异常", "检查编码器连接"),
    EH0301("EH0301", ErrorModule.HARDWARE, ErrorLevel.FATAL,
            "主控制器通讯中断", "检查主控板电源");

    @Getter
    private final String code;
    @Getter
    private final ErrorModule module;
    @Getter
    private final ErrorLevel level;
    @Getter
    private final String description;
    @Getter
    private final String suggestion;

    AgvErrorCode(String code, ErrorModule module, ErrorLevel level,
                 String description, String suggestion) {
        this.code = code;
        this.module = module;
        this.level = level;
        this.description = description;
        this.suggestion = suggestion;
    }

    /**
     * 是否为致命错误
     */
    public boolean isFatal() {
        return this.level == ErrorLevel.FATAL;
    }

    /**
     * 是否需要停止当前任务
     */
    public boolean shouldStopTask() {
        return this.level.ordinal() >= ErrorLevel.ERROR.ordinal();
    }

    /**
     * 根据错误码字符串查找枚举
     */
    public static AgvErrorCode fromCode(String code) {
        for (AgvErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return null;
    }

    @RequiredArgsConstructor
    @Getter
    public enum ErrorModule {
        NAVIGATION("N", "导航"),
        MOVEMENT("M", "移动"),
        CHARGING("C", "充电"),
        OPERATION("O", "作业"),
        ORDER("R", "订单"),
        SYSTEM("S", "系统"),
        HARDWARE("H", "硬件");

        private final String code;
        private final String name;
    }

    @RequiredArgsConstructor
    @Getter
    public enum ErrorLevel {
        WARNING(0, "警告", "可继续运行"),
        ERROR(1, "错误", "当前任务中断"),
        FATAL(2, "致命", "需人工干预");

        private final int code;
        private final String name;
        private final String description;
    }
}
