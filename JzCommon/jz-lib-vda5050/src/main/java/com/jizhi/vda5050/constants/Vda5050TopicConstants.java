package com.jizhi.vda5050.constants;

/**
 * VDA5050 MQTT主题常量定义
 * 遵循VDA5050协议规范的主题命名规则
 */
public final class Vda5050TopicConstants {

    private Vda5050TopicConstants() {
        // 防止实例化
    }

    // ================ 基础主题结构 ================
    public static final String INTERFACE_NAME = "uagv";
    public static final String MAJOR_VERSION = "v2";

    // ================ 主题模板 ================
    /** AGV特定主题模板 - 标准VDA5050格式 */
    public static final String AGV_SPECIFIC_TOPIC_TEMPLATE = "%s/%s/%s/%s/%s"; // interface/version/manufacturer/serialNumber/topicType

    /** 简化主题模板 - 兼容旧格式 */
    public static final String SIMPLIFIED_TOPIC_TEMPLATE = "agv/%s/%s";

    /** 监听广播主题模板 */
    public static final String BROADCAST_TOPIC_TEMPLATE = "agv/%s/%s";

    /** 发送广播主题模板 */
    public static final String SEND_BROADCAST_TOPIC_TEMPLATE = "agv/%s";

    // ================ 主题类型常量 ================
    public static final String TOPIC_ORDER = "order";
    public static final String TOPIC_STATE = "state";
    public static final String TOPIC_CONTROL = "control";
    public static final String TOPIC_POSITION = "position";
    public static final String TOPIC_ORDER_STATE = "order/state";

    public static final String TOPIC_ORDER_FINISH = "order/finish";
    public static final String TOPIC_ACTION_STATE = "action/state";
    public static final String TOPIC_TASK_COMPLETE = "task/complete";
    public static final String TOPIC_TASK_ASSIGNED = "task/assigned";
    public static final String TOPIC_CONNECTION = "connection";
    public static final String TOPIC_HEARTBEAT = "heartbeat";
    public static final String TOPIC_ERROR = "error";
    public static final String TOPIC_PATH_UPDATE = "path/update";
    public static final String TOPIC_MONITOR_ALERTS = "monitor/alerts";
    public static final String TOPIC_MONITOR_REPLAN = "monitor/replan";
    public static final String TOPIC_ONLINE = "online";
    public static final String TOPIC_OFFLINE = "offline";

    // ================ 通配符主题 ================
    public static final String WILDCARD_ALL_AGVS = "+";
    public static final String BROADCAST_CONTROL_TOPIC = "agv/+/control";
    public static final String BROADCAST_ORDER_TOPIC = "agv/+/order";

    public static final String TOPIC_INSTANT_ACTIONS = "instantActions";

    // ================ 主题构建方法 ================

    /**
     * 构建标准VDA5050主题
     */
    public static String buildStandardTopic(String manufacturer, String serialNumber, String topicType) {
        return String.format(AGV_SPECIFIC_TOPIC_TEMPLATE,
                INTERFACE_NAME, MAJOR_VERSION, manufacturer, serialNumber, topicType);
    }

    /**
     * 构建简化主题（兼容旧格式）
     */
    public static String buildSimplifiedTopic(String agvId, String topicType) {
        return String.format(SIMPLIFIED_TOPIC_TEMPLATE, agvId, topicType);
    }

    /**
     * 构建广播主题
     */
    public static String buildSendBroadcastTopic(String topicType) {
        return String.format(SEND_BROADCAST_TOPIC_TEMPLATE, topicType);
    }

    /**
     * 构建广播主题
     */
    public static String buildBroadcastTopic(String topicType) {
        return String.format(BROADCAST_TOPIC_TEMPLATE, WILDCARD_ALL_AGVS, topicType);
    }

}
