package com.sdt.agv_dispatcher.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.vda5050.agv.AgvState;
import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.agv.TaskStatus;
import com.jizhi.vda5050.constants.Vda5050TopicConstants;
import com.jizhi.vda5050.domain.*;
import com.jizhi.vda5050.message.*;
import com.sdt.agv_dispatcher.conflict.*;
import com.sdt.agv_dispatcher.domain.Task;
import com.sdt.agv_dispatcher.scheduler.AgvTaskDispatcher;
import com.sdt.agv_dispatcher.service.AgvManagerService;
import com.sdt.agv_dispatcher.service.MessageDeduplicationService;
import com.sdt.agv_dispatcher.service.RedisResourceLockService;
import com.sdt.agv_dispatcher.utils.Vda5050MessageBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.sdt.agv_dispatcher.conflict.ResolutionAction.PROCEED_WITH_NOTIFICATION;
import static com.sdt.agv_dispatcher.conflict.ResolutionAction.SLOW_DOWN;

@Slf4j
@Component
public class MqttGateway implements MqttCallback {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgvManagerService agvStatusService;

    @Autowired
    private MessageDeduplicationService deduplicationService;

    @Autowired
    private AgvTaskDispatcher agvTaskDispatcher;


//    @Autowired
//    private ConflictResolver conflictResolver;

    @Autowired
    private Vda5050MessageBuilder vda5050MessageBuilder;

    @Autowired
    private RedisResourceLockService redisResourceLockService;

    @Autowired
    private EnhancedConflictDetector enhancedConflictDetector;

    @Autowired
    private IntelligentConflictResolver intelligentConflictResolver;

    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.broker.client-id:agv-scheduler-server}")
    private String clientId;

    @Value("${mqtt.broker.username:}")
    private String username;

    @Value("${mqtt.broker.password:}")
    private String password;

    @Value("${mqtt.broker.automatic-reconnect:true}")
    private boolean automaticReconnect;

    @Value("${mqtt.broker.clean-session:true}")
    private boolean cleanSession;

    @Value("${mqtt.broker.connection-timeout:10}")
    private int connectionTimeout;

    @Value("${mqtt.broker.keep-alive-interval:60}")
    private int keepAliveInterval;

    private MqttClient mqttClient;
    private static int positionUpdateCount = 0;
    private static int agvStateUpdateCount = 0;

    // 使用线程安全的原子变量
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);

    // 使用单线程调度器替代手动线程管理
    private ScheduledExecutorService reconnectExecutor;
    private ScheduledFuture<?> reconnectFuture;

    // 重连配置
    private static final int INITIAL_RECONNECT_DELAY_MS = 5000;
    private static final int MAX_RECONNECT_DELAY_MS = 300000; // 5分钟


    // 在类中维护一个 Map，每个 AGV 一个单线程的 Executor
    private final Map<String, ExecutorService> agvExecutors = new ConcurrentHashMap<>();


    /**
     * 初始化MQTT连接
     */
    @PostConstruct
    public void init() {
        // 初始化单线程调度器
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MQTT-Reconnect-Scheduler");
            t.setDaemon(true);
            return t;
        });

        // 初始连接
        scheduleReconnect(0);
    }

    /**
     * 调度重连任务，确保只有一个重连任务在执行
     */
    private synchronized void scheduleReconnect(long delayMs) {
        // 如果已经在重连中，不要重复调度
        if (reconnecting.get() && delayMs > 0) {
            log.debug("重连任务已在进行中，跳过本次调度");
            return;
        }

        // 取消之前的任务（如果有）
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(false);
        }

        reconnecting.set(true);

        log.info("将在 {}ms 后尝试重连 MQTT 服务器 (第{}次尝试)",
                delayMs, reconnectAttempt.incrementAndGet());

        reconnectFuture = reconnectExecutor.schedule(() -> {
            try {
                if (doReconnect()) {
                    reconnectAttempt.set(0); // 重置计数器
                    reconnecting.set(false);
                } else {
                    // 计算下次重连延迟（指数退避）
                    int nextDelay = calculateNextDelay();
                    reconnecting.set(false);
                    scheduleReconnect(nextDelay);
                }
            } catch (Exception e) {
                log.error("重连过程中发生异常", e);
                reconnecting.set(false);
                scheduleReconnect(calculateNextDelay());
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void destroy() {
        log.info("正在关闭MQTT网关...");

        // 取消所有重连任务
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(false);
        }

        // 关闭线程池
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdown();
            try {
                if (!reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    reconnectExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                reconnectExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("正在关闭所有 AGV 位置处理线程池...");
        for (Map.Entry<String, ExecutorService> entry : agvExecutors.entrySet()) {
            String agvId = entry.getKey();
            ExecutorService executor = entry.getValue();
            if (executor != null && !executor.isShutdown()) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.warn("AGV {} 线程池未在5秒内终止，强制关闭", agvId);
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    log.warn("等待 AGV {} 线程池终止时被中断", agvId);
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
        agvExecutors.clear();
        log.info("所有 AGV 位置处理线程池已关闭");

        disconnect();
    }

    /**
     * 计算下次重连延迟
     */
    private int calculateNextDelay() {
        int attempt = reconnectAttempt.get();
        // 指数退避: 5s, 10s, 20s, 40s, 80s, 160s, 300s(max)
        long delay = INITIAL_RECONNECT_DELAY_MS * (1L << Math.min(attempt - 1, 6));
        return (int) Math.min(delay, MAX_RECONNECT_DELAY_MS);
    }

    /**
     * 执行实际重连逻辑（同步方法防止并发）
     */
    private synchronized boolean doReconnect() {
        // 双重检查：如果已经连接，直接返回
        if (mqttClient != null && mqttClient.isConnected()) {
            connected.set(true);
            return true;
        }

        try {
            // 清理旧客户端
            closeExistingClient();

            log.info("正在创建新的MQTT客户端实例...");

            // 创建新客户端
            mqttClient = new MqttClient(brokerUrl, clientId + "-" + System.currentTimeMillis(),
                    new MemoryPersistence());
            mqttClient.setCallback(this);

            MqttConnectOptions options = createConnectOptions();

            log.info("正在连接到MQTT broker: {}", brokerUrl);
            mqttClient.connect(options);

            // 验证连接状态
            if (!mqttClient.isConnected()) {
                throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
            }

            connected.set(true);

            // 重新订阅主题
            subscribeTopics();

            log.info("MQTT重连成功！当前客户端ID: {}", mqttClient.getClientId());
            return true;

        } catch (MqttException e) {
            log.error("MQTT连接失败，错误码: {}, 原因: {}", e.getReasonCode(), e.getMessage());
            connected.set(false);
            return false;
        } catch (Exception e) {
            log.error("重连时发生未知错误", e);
            connected.set(false);
            return false;
        }
    }

    /**
     * 创建连接选项
     */
    private MqttConnectOptions createConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();

        // 关键：禁用Paho的自动重连，我们自己管理
        options.setAutomaticReconnect(false);
        options.setCleanSession(cleanSession);
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);

        // 设置遗嘱消息，便于服务器检测客户端异常断开
        options.setWill("agv/scheduler/status", "offline".getBytes(), 1, true);

        if (username != null && !username.isEmpty()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }

        return options;
    }


    /**
     * 安全关闭现有客户端
     */
    private void closeExistingClient() {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnectForcibly(3000, 3000);
                }
            } catch (Exception e) {
                log.warn("断开旧连接时出错", e);
            } finally {
                try {
                    mqttClient.close(true);
                } catch (Exception e) {
                    log.warn("关闭旧客户端时出错", e);
                }
                mqttClient = null;
            }
        }
    }

    /**
     * 发布消息
     */
    public void publish(String topic, Object payload) {
        try {
            String message = objectMapper.writeValueAsString(payload);
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(1);
            mqttMessage.setRetained(false);

            if (isMqttConnected()) {
                log.warn("MQTT未连接，无法发布消息");
                return;
            }
            mqttClient.publish(topic, mqttMessage);
            log.debug("MQTT发布成功: topic={}, payload={}", topic, message);
        } catch (Exception e) {
            log.error("MQTT发布失败: topic={}", topic, e);
        }
    }

    /**
     * 订阅主题
     */
    public void subscribe(String topic, IMqttMessageListener listener) {
        try {
            if (isMqttConnected()) {
                log.warn("MQTT未连接，无法订阅主题");
                return;
            }

            mqttClient.subscribe(topic, 0, listener);
            log.debug("订阅主题成功: {}", topic);

        } catch (MqttException e) {
            log.error("订阅主题失败: {}", topic, e);
        }
    }

    /**
     * 发送VDA5050订单
     */
    public void sendVda5050Order(String agvId, Vda5050OrderMessage order) {
        try {
            String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvId, Vda5050TopicConstants.TOPIC_ORDER);
            publish(topic, order);
            log.info("发送VDA5050订单到AGV {}: {}", agvId, order.getOrderInformation().getOrderId());
        } catch (Exception e) {
            log.error("发送VDA5050订单失败: AGV={}", agvId, e);
        }
    }

    /**
     * 处理AGV状态消息
     */
    private void handleAgvStateMessage(String topic, String message) {
        if (agvStateUpdateCount % 100 == 0) {
            log.info("收到AGV状态消息,topic:{},message: {}", topic, message);
        }
        agvStateUpdateCount++;
        try {
            // 解析JSON消息
            Vda5050StateMessage stateMessage = objectMapper.readValue(message, Vda5050StateMessage.class);
            String messageId = String.valueOf(stateMessage.getHeader().getHeaderId());
            if (deduplicationService.isDuplicate(messageId)) {
                log.warn("检测到重复Agv状态消息，已跳过处理: messageId={}", messageId);
                return;
            }
            String agvId = stateMessage.getAgvInfo().getAgvId();
            // 更新AGV状态
            agvStatusService.updateFromVda5050Message(agvId, stateMessage);
            // 记录调试信息
            if (log.isTraceEnabled()) {
                log.trace("AGV {} 状态更新: {}", agvId, stateMessage);
            }
        } catch (Exception e) {
            log.error("解析AGV状态消息失败:", e);
        }
    }

    /**
     * 订阅任务相关主题
     */
    private void subscribeTopics() {
        try {
            // 订阅AGV连接状态
            subscribe(Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_CONNECTION),
                    (topic, message) -> handleConnectionMessage(topic, new String(message.getPayload())));

            // 订阅AGV状态
            subscribe(Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_STATE),
                    (topic, message) -> handleAgvStateMessage(topic, new String(message.getPayload())));

            // 订阅任务完成通知
            subscribe(Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_TASK_COMPLETE),
                    (topic, message) -> handleTaskCompleteMessage(topic, new String(message.getPayload())));

            // 订阅订单完成通知（假设已添加 TOPIC_ORDER_FINISH）
            subscribe(Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_ORDER_FINISH),
                    (topic, message) -> handleOrderFinishMessage(topic, new String(message.getPayload())));

            // 订阅订单状态
            subscribe(Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_ORDER_STATE),
                    (topic, message) -> handleOrderStateMessage(topic, new String(message.getPayload())));

            // 订阅位置更新
            subscribe(Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_POSITION),
                    (topic, message) -> handlePositionUpdateMessage(topic, new String(message.getPayload())));

            // 订阅错误信息
            subscribe(Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_ERROR),
                    (topic, message) -> handleErrorMessage(topic, new String(message.getPayload())));

            // 订阅心跳
            subscribe(Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_HEARTBEAT),
                    (topic, message) -> handleHeartbeatMessage(topic, new String(message.getPayload())));

            log.info("MQTT主题订阅完成");

        } catch (Exception e) {
            log.error("订阅MQTT主题失败", e);
        }
    }


    /**
     * 处理连接消息
     *
     * @param topic   话题
     * @param message 消息内容
     */
    private void handleConnectionMessage(String topic, String message) {
        log.info("收到AGV上线/下线消息,topic:{},message: {}", topic, message);
        try {
            Vda5050ConnectionMessage connectionMessage = objectMapper.readValue(
                    message, Vda5050ConnectionMessage.class);
            ConnectionState connectionState = connectionMessage.getPayload().getConnectionState();

            if (connectionState == null) {
                log.warn("AGV [{}] 连接状态为空, 使用默认状态ONLINE", connectionMessage.getPayload().getAgvId());
                connectionState = ConnectionState.ONLINE;
            }
            switch (connectionState) {
                case ONLINE:
                    agvStatusService.setAgvOnline(connectionMessage.getPayload().getAgvId(), connectionMessage);
                    break;
                case CONNECTING:
                    log.error("正在连接中!");
                    break;
                case ERROR:
                    log.error("连接异常!");
                    break;
                default:
                case OFFLINE:
                    agvStatusService.setAgvOffline(connectionMessage.getPayload().getAgvId());
                    break;
            }


        } catch (JsonProcessingException e) {
            log.error("解析连接消息失败, topic: {}, message: {}", topic, message, e);
        }
    }

    /**
     * 处理标准VDA5050任务完成消息（优化版）
     */
    private void handleTaskCompleteMessage(String topic, String message) {
        log.info("收到任务完成消息, topic: {}, message: {}", topic, message);

        try {
            // 使用标准Bean解析消息
            Vda5050TaskCompletionMessage completionMessage = objectMapper.readValue(message,
                    Vda5050TaskCompletionMessage.class);
            TaskCompletionPayload payload = completionMessage.getPayload();
            log.info("任务完成: AGV={}, Task={},Status={}", payload.getAgvId(), payload.getTaskId(), payload.getStatus());
            processTaskCompletion(payload);  // 根据VDA5050标准状态更新任务
            cleanupAfterTaskCompletion(payload);
        } catch (Exception e) {
            log.error("处理任务完成消息失败, message: {}", message, e);
        }
    }

    /**
     * 处理任务完成状态
     */
    private void processTaskCompletion(TaskCompletionPayload payload) {
        try {
            String status = payload.getStatus();
            // 根据VDA5050标准状态映射到内部状态
            switch (TaskStatus.valueOf(status)) {
                case FINISHED:
                    handleTaskCompleted(payload);
                    break;
                case FAILED:
                    handleTaskFailed(payload);
                    break;
                case CANCELLED:
                    handleTaskCancelled(payload);
                    break;
                default:
                    log.warn("未知的任务状态: AGV={}, Task={}, Status={}", payload.getAgvId(), payload.getTaskId(), status);
                    handleUnknownTaskStatus(payload);
            }
        } catch (IllegalArgumentException e) {
            log.warn("无效的任务状态值: AGV={}, Task={}, Status={}",
                    payload.getAgvId(), payload.getTaskId(), payload.getStatus(), e);
            handleInvalidTaskStatus(payload.getAgvId(), payload.getTaskId(), payload);
        }
    }

    /**
     * 处理任务完成状态
     */
    private void handleTaskCompleted(TaskCompletionPayload payload) {
        try {
            // 更新AGV状态为空闲
            agvStatusService.setAgvTaskStatus(payload.getAgvId(), payload.getTaskId(), AgvState.IDLE);
            // 记录任务执行时间信息
            if (payload.getStartTime() != null && payload.getEndTime() != null) {
                log.info("任务完成: AGV={}, Task={}, 耗时={}ms, 消息: {}", payload.getAgvId(), payload.getTaskId(),
                        payload.getDuration(),
                        payload.getMessage());
            } else {
                log.info("任务完成: AGV={}, Task={}, 消息: {}", payload.getAgvId(), payload.getTaskId(),
                        payload.getMessage());
            }
            // 触发后续清理操作
        } catch (Exception e) {
            log.error("处理任务完成状态失败: AGV={}, Task={}", payload.getAgvId(), payload.getTaskId(), e);
        }
    }

    /**
     * 处理任务失败状态
     */
    private void handleTaskFailed(TaskCompletionPayload payload) {
        try {
            // 更新AGV状态为错误
            agvStatusService.setAgvTaskStatus(payload.getAgvId(), payload.getTaskId(), AgvState.ERROR);
            log.error("任务失败: AGV={}, Task={}, 错误信息: {}", payload.getAgvId(), payload.getTaskId(), payload.getMessage());
            // 记录错误详细信息
            recordTaskFailure(payload.getAgvId(), payload.getTaskId(), payload);
            // 触发错误恢复流程
            scheduleErrorRecovery(payload.getAgvId(), payload.getTaskId(), payload.getMessage());
        } catch (Exception e) {
            log.error("处理任务失败状态异常: AGV={}, Task={}", payload.getAgvId(), payload.getTaskId(), e);
        }
    }

    /**
     * 处理任务取消状态
     */
    private void handleTaskCancelled(TaskCompletionPayload payload) {
        try {
            // 更新AGV状态为暂停或空闲（根据业务逻辑）
            agvStatusService.setAgvTaskStatus(payload.getAgvId(), payload.getTaskId(), AgvState.PAUSED);
            log.info("任务取消: AGV={}, Task={}, 原因: {}", payload.getAgvId(), payload.getTaskId(), payload.getMessage());
            // 清理任务资源
            cleanupCancelledTask(payload.getAgvId(), payload.getTaskId());

        } catch (Exception e) {
            log.error("处理任务取消状态失败: AGV={}, Task={}", payload.getAgvId(), payload.getTaskId(), e);
        }
    }

    /**
     * 处理未知任务状态
     */
    private void handleUnknownTaskStatus(TaskCompletionPayload payload) {
        log.warn("处理未知任务状态: AGV={}, Task={}, Status={}",
                payload.getAgvId(), payload.getTaskId(), payload.getStatus());
        // 安全处理：将AGV状态设置为需要人工干预
        agvStatusService.setAgvTaskStatus(payload.getAgvId(), payload.getTaskId(), AgvState.ERROR);
    }

    /**
     * 处理无效任务状态
     */
    private void handleInvalidTaskStatus(String agvId, String taskId, TaskCompletionPayload payload) {
        log.error("收到无效的任务状态: AGV={}, Task={}, Status={}", agvId, taskId, payload.getStatus());
        // 安全处理：将AGV状态设置为错误
        agvStatusService.setAgvTaskStatus(agvId, taskId, AgvState.ERROR);
    }

    /**
     * 任务完成后的清理操作
     */
    private void cleanupAfterTaskCompletion(TaskCompletionPayload payload) {
        try {
            // 清理任务相关资源
            // 例如：释放路径锁、更新统计信息等
            log.debug("清理任务资源: AGV={}, Task={}", payload.getAgvId(), payload.getTaskId());
            AgvStatus agvStatus = agvStatusService.getAgvStatus(payload.getAgvId());
            redisResourceLockService.releaseAllByAgv(payload.getAgvId());
            agvTaskDispatcher.finishTask(payload.getAgvId());

        } catch (Exception e) {
            log.warn("清理任务资源异常: AGV={}, Task={}", payload.getAgvId(), payload.getTaskId(), e);
        }
    }

    /**
     * 记录任务失败信息
     */
    private void recordTaskFailure(String agvId, String taskId, TaskCompletionPayload payload) {
        try {
            // 记录详细的失败信息，用于后续分析
            // 可以存储到数据库或日志系统
            log.debug("记录任务失败详情: AGV={}, Task={}, Message={}", agvId, taskId, payload.getMessage());
            redisResourceLockService.releaseAllByAgv(payload.getAgvId());
            agvTaskDispatcher.failTask(agvId, payload.getMessage());
        } catch (Exception e) {
            log.warn("记录任务失败信息异常: AGV={}, Task={}", agvId, taskId, e);
        }
    }

    /**
     * 调度错误恢复
     */
    private void scheduleErrorRecovery(String agvId, String taskId, String errorMessage) {
        try {
            // 可以根据错误类型调度不同的恢复策略
            log.info("调度错误恢复流程: AGV={}, Task={}, Error={}", agvId, taskId, errorMessage);
            // 这里可以添加具体的恢复逻辑，如重试、通知运维等
        } catch (Exception e) {
            log.error("调度错误恢复异常: AGV={}, Task={}", agvId, taskId, e);
        }
    }

    /**
     * 清理已取消的任务
     */
    private void cleanupCancelledTask(String agvId, String taskId) {
        try {
            // 清理取消任务的相关资源
            log.debug("清理已取消任务: AGV={}, Task={}", agvId, taskId);
            redisResourceLockService.releaseAllByAgv(agvId);
            agvTaskDispatcher.cancelTask(agvId);
        } catch (Exception e) {
            log.warn("清理取消任务异常: AGV={}, Task={}", agvId, taskId, e);
        }
    }

    /**
     * 处理订单完成消息
     */
    private void handleOrderFinishMessage(String topic, String message) {
        try {
            log.info("收到任务完成消息,topic:{},message: {}", topic, message);
            Map finishMessage = objectMapper.readValue(message, Map.class);
            String orderId = (String) finishMessage.get("orderId");
            String agvId = (String) finishMessage.get("agvId");
            // 更新订单状态
            // 这里可以根据需要实现订单状态更新逻辑
            redisResourceLockService.releaseAllByAgv(agvId);
            agvTaskDispatcher.finishTask(agvId);
        } catch (Exception e) {
            log.error("处理订单完成消息失败", e);
        }
    }

    /**
     * 处理订单状态消息
     */
    private void handleOrderStateMessage(String topic, String message) {
        try {
            Vda5050OrderStateMessage stateMessage = objectMapper.readValue(message, Vda5050OrderStateMessage.class);
            String messageId = String.valueOf(stateMessage.getHeader().getHeaderId());
            if (deduplicationService.isDuplicate(messageId)) {
                log.warn("检测到重复订单状态消息，已跳过处理: messageId={}", messageId);
                return;
            }

            OrderStatePayload payload = stateMessage.getPayload();
            String orderId = payload.getOrderId();
            String agvId = payload.getAgvId();
            String orderState = payload.getOrderState();
            Integer orderUpdateId = payload.getOrderUpdateId();
            String resultDescription = payload.getResultDescription();

            log.info("收到订单状态更新,topic:{}, AGV={}, Order={}, State={}, UpdateId={}, Desc={}",
                    topic, agvId, orderId, orderState, orderUpdateId, resultDescription);

            if (orderId != null && agvId != null) {
                // 1. 更新任务状态（用于历史任务记录）
                AgvState taskState = mapOrderStateToTaskState(orderState);
                if (taskState != null) {
                    agvStatusService.setAgvTaskStatus(agvId, orderId, taskState);
                }
                agvTaskDispatcher.updateTaskStatus(orderId, orderState);

                // 2. 根据终态清理 AGV 的当前任务
                switch (orderState) {
                    case "FINISHED":
                        agvTaskDispatcher.finishTask(agvId);
                        redisResourceLockService.releaseAllByAgv(agvId);
                        break;
                    case "FAILED":
                        agvTaskDispatcher.failTask(agvId, resultDescription);
                        redisResourceLockService.releaseAllByAgv(agvId);
                        break;
                    case "CANCELED":
                        agvTaskDispatcher.cancelTask(agvId);
                        redisResourceLockService.releaseAllByAgv(agvId);
                        break;
                    // 其他状态（如 INITIALIZED、RUNNING 等）不需要移除
                    default:
                        // 无需操作
                        break;
                }
            }
        } catch (Exception e) {
            log.error("处理订单状态消息失败, message: {}", message, e);
        }
    }

    /**
     * 将VDA5050订单状态映射到内部任务状态
     */
    private AgvState mapOrderStateToTaskState(String orderState) {
        if (orderState == null) return null;

        switch (orderState.toUpperCase()) {
            case "ACCEPTED":   // 新增：订单已接受
            case "RUNNING":    // 订单开始执行
                return AgvState.EXECUTING;
            case "FINISHED":   // 订单完成
                return AgvState.IDLE;
            case "PAUSED":     // 订单暂停
                return AgvState.PAUSED;
            case "FAILED":     // 订单失败
                return AgvState.ERROR;
            default:
                log.warn("未知的订单状态: {}", orderState);
                return null;
        }
    }

    /**
     * 处理位置更新消息
     */
    private void handlePositionUpdateMessage(String topic, String message) {
        try {
            Vda5050PositionUpdateMessage positionMessage = objectMapper.readValue(message,
                    Vda5050PositionUpdateMessage.class);
            String agvId = positionMessage.getPosition().getAgvId();
            // 获取该 AGV 的专用线程池（单线程）
            ExecutorService executor = agvExecutors.computeIfAbsent(agvId,
                    k -> Executors.newSingleThreadExecutor(r -> new Thread(r, "AGV-" + agvId + "-PosHandler")));
            // 将实际处理逻辑提交到该线程池
            executor.execute(() -> doHandlePositionUpdate(topic, message, positionMessage));
        } catch (Exception e) {
            log.error("处理位置更新消息失败", e);
        }

    }

    private void doHandlePositionUpdate(String topic, String message, Vda5050PositionUpdateMessage positionMessage) {
        String messageId = String.valueOf(positionMessage.getHeader().getHeaderId());
        if (deduplicationService.isDuplicate(messageId)) {
            log.warn("检测到重复位置更新消息，已跳过处理: messageId={}", messageId);
            return;
        }
        if (positionMessage.getPosition().getCurrentNodeId() != null || positionMessage.getPosition().getCurrentEdgeId() != null) {
            //log.debug("收到位置更新消息,topic:{},message: {}", topic, positionMessage);
        }

        agvStatusService.updateAgvPosition(positionMessage);
        String agvId = positionMessage.getPosition().getAgvId();
        AgvStatus agvStatus = agvStatusService.getAgvStatus(agvId);
        if (agvStatus == null) {
            return;
        }

        String currentNodeId = positionMessage.getPosition().getLastNodeId();
        String currentEdgeId = positionMessage.getPosition().getCurrentEdgeId();
        Integer nodeSequenceId = positionMessage.getPosition().getNodeSequenceId();
        Integer edgeSequenceId = positionMessage.getPosition().getEdgeSequenceId();

        // 获取上一次报告的节点ID（需在AgvStatus中新增字段）
        String lastNodeId = agvStatus.getLastReportNodeId();
        String lastEdgeId = agvStatus.getLastReportEdgeId();

        boolean isPositionChange = false;
        Task task = agvTaskDispatcher.getAgvCurrentTask(agvId);

        if (lastNodeId == null || lastNodeId.isEmpty()) {
            // 首次上报，记录但不释放
            agvStatus.setLastReportNodeId(currentNodeId);
        } else if (!lastNodeId.equals(currentNodeId)) {
            // 节点真正发生变化，释放上一个节点
            log.debug("AGV {} 节点变化: {} -> {}", agvId, lastNodeId, currentNodeId);
            if (task != null) {
                // 释放上一个节点
                redisResourceLockService.releaseResource(lastNodeId, "NODE", agvId);
                // 从任务的锁定列表中移除（可选）
                task.getResourceLockList().removeIf(info -> info.getResourceId().equals(lastNodeId) && "NODE".equals
                        (info.getType()));
            }

            agvStatus.setLastReportNodeId(currentNodeId);
            isPositionChange = true;
        }

        // 边变化判断
        if (lastEdgeId == null || lastEdgeId.isEmpty()) {
            // 首次上报，记录但不释放
            agvStatus.setLastReportEdgeId(currentEdgeId);
        } else if (!lastEdgeId.equals(currentEdgeId)) {
            // 边发生变化（可能变为null，表示进入节点）
            log.debug("AGV {} 边变化: {} -> {}", agvId, lastEdgeId, currentEdgeId);
            if (task != null) {
                redisResourceLockService.releaseResource(lastEdgeId, "EDGE", agvId);
                task.getResourceLockList().removeIf(info -> info.getResourceId().equals(lastEdgeId) && "EDGE".equals
                        (info.getType()));
            }

            agvStatus.setLastReportEdgeId(currentEdgeId);
            isPositionChange = true;
        }

        if (task == null || task.getPathResult() == null) {
            return;
        }

        // 记录当前位置
        task.setCurrentNodeId(currentNodeId);
        task.setCurrentEdgeId(currentEdgeId);
        if (nodeSequenceId != null) {
            task.setCurrentNodeSequenceId(nodeSequenceId);
        }
        if (edgeSequenceId != null) {
            task.setCurrentEdgeSequenceId(edgeSequenceId);
        }
        if (isPositionChange) {
            if (task.getResourceLockList().size() < 3) {
                List<RedisResourceLockService.ResourceLockInfo> nextResources =
                        redisResourceLockService.buildNextResources(task, 3);
                RedisResourceLockService.LockPathResult result =
                        redisResourceLockService.tryLockPathWithDetail(nextResources, agvId);
                if (result.isSuccess()) {
                    task.getResourceLockList().addAll(result.getLocked());
                } else {
                    //handleReservationFailure(agvStatus, task, result.getConflict()); // 锁定失败，触发冲突处理
                    ConflictAnalysisResult conflict = enhancedConflictDetector.analyzeConflict(
                            agvId, result.getConflict().getResourceInfo(), result.getConflict().getConflictingAgvId());
                    // 使用智能解决策略
                    ResolutionStrategy strategy = intelligentConflictResolver.resolve(conflict, task);
                    log.debug("AGV {} 冲突解决策略: {}", agvId, strategy);
                    // 执行策略
                    handleAutoConflictResolution(agvStatus, task, strategy);

                }
            }
        }

    }


//    private void handleReservationFailure(AgvStatus agvStatus, Task task, ConflictInfo conflict) {
//        if (conflict == null) return;
//        List<ConflictInfo> conflicts = Collections.singletonList(conflict);
//        ResolutionStrategy strategy = conflictResolver.resolve(conflicts, agvStatus.getAgvId(), task);
//        handleAutoConflictResolution(agvStatus, strategy);
//    }

    /**
     * 自动处理冲突
     */
    public void handleAutoConflictResolution(AgvStatus agvStatus, Task task, ResolutionStrategy strategy) {
        try {
            // 构建控制命令（业务Bean）
            AgvControlCommand command = buildControlCommand(agvStatus, strategy);
            // 执行命令
            executeCommand(agvStatus, command);
            // 记录审计日志
        } catch (Exception e) {
            log.error("自动处理冲突失败: AGV={}", agvStatus.getAgvId(), e);
            sendEmergencyStop(agvStatus, "CONFLICT_RESOLUTION_FAILED");
        }
    }


    /**
     * 构建控制命令（核心转换逻辑）- 完善版
     */
    private AgvControlCommand buildControlCommand(AgvStatus agvStatus, ResolutionStrategy strategy) {
        Object payload;
        AgvControlCommand.ControlCommandType commandType;

        switch (strategy.getType()) {
            case PROCEED:
                // 正常行驶，无需特殊处理
                payload = ProceedPayload.builder()
                        .normalSpeed(true)
                        .reason(strategy.getReason())
                        .build();
                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
                break;

            case PROCEED_WITH_CAUTION:
                payload = SpeedReductionPayload.builder()
                        .targetSpeed(agvStatus.getMaxSpeed() * strategy.getTargetSpeed())
                        .reductionRatio(0.5)
                        .temporary(true)
                        .duration(strategy.getWaitTime() != null ?
                                strategy.getWaitTime() : null)
                        .reason(strategy.getReason())
                        .build();
                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
                break;

            case STOP:
                payload = StopPayload.builder()
                        .emergency(true)
                        .clearOrder(false)
                        .reason(strategy.getReason())
                        .build();
                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
                break;

            case WAIT:
            case YIELD_AND_WAIT:
                payload = PausePayload.builder()
                        .duration(strategy.getWaitTime() != null ?
                                strategy.getWaitTime().getSeconds() : 5)
                        .resumeAutomatically(true)
                        .waitForAgvId(strategy.getYieldToAgvId())
                        .reason(strategy.getReason())
                        .build();
                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
                break;

            case WAIT_AT_POINT:
                payload = WaitAtPointPayload.builder()
                        .waitPointId(strategy.getWaitPoint())
                        .duration(strategy.getWaitTime() != null ?
                                strategy.getWaitTime().getSeconds() : 10)
                        .reason(strategy.getReason())
                        .build();
                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
                break;

            case REPLAN_PATH:
                payload = RePlanPayload.builder()
                        .originalOrderId(agvStatus.getCurrentOrderId())
                        .newPath(strategy.getAlternativePath())
                        .preserveProgress(true)
                        .reason(strategy.getReason())
                        .build();
                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
                break;

            case RELEASE_AND_REPLAN:
                payload = ReleaseAndReplanPayload.builder()
                        .releaseAllResources(true)
                        .preserveOrder(true)
                        .reason(strategy.getReason())
                        .build();
                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
                break;

            case RELEASE_LAST_RESOURCE:
                payload = ReleaseResourcePayload.builder()
                        .releaseLastOnly(true)
                        .reason(strategy.getReason())
                        .build();
                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
                break;

            case ADJUST_SPEED:
            case COORDINATE_PASSING:
                payload = SpeedAdjustmentPayload.builder()
                        .targetSpeed(strategy.getTargetSpeed())
                        .temporary(true)
                        .reason(strategy.getReason())
                        .build();
                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
                break;

//            case SLOW_DOWN:
//                payload = SpeedReductionPayload.builder()
//                        .targetSpeed(agvStatus.getMaxSpeed() * 0.5)
//                        .reductionRatio(0.5)
//                        .temporary(true)
//                        .reason(strategy.getReason())
//                        .build();
//                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
//                break;

            default:
                log.warn("未知的策略类型: {}, 使用默认处理", strategy.getType());
                payload = ProceedPayload.builder()
                        .normalSpeed(true)
                        .reason("默认处理: " + strategy.getReason())
                        .build();
                commandType = AgvControlCommand.ControlCommandType.INSTANT_ACTION;
        }

        return AgvControlCommand.builder()
                .agvId(agvStatus.getAgvId())
                .commandType(commandType)
                .payload(payload)
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .priority(calculateCommandPriority(strategy))
                .build();
    }

    /**
     * 根据策略计算命令优先级
     */
    private int calculateCommandPriority(ResolutionStrategy strategy) {
        return switch (strategy.getType()) {
            case STOP -> 10;           // 最高优先级
            case RELEASE_AND_REPLAN -> 9;
            case REPLAN_PATH -> 8;
            case PROCEED_WITH_CAUTION -> 5;
            case WAIT, YIELD_AND_WAIT -> 4;
            case ADJUST_SPEED -> 3;
            default -> 1;
        };
    }

    /**
     * 执行控制命令（转换为VDA5050并发送）
     */
    private void executeCommand(AgvStatus agvStatus, AgvControlCommand command) {
        String agvId = command.getAgvId();
        Object payload = command.getPayload();

        if (Objects.requireNonNull(command.getCommandType()) == AgvControlCommand.ControlCommandType.INSTANT_ACTION) {
            Vda5050InstantActions vdaAction = convertToVda5050(agvStatus, payload);
            sendVda5050InstantAction(agvId, vdaAction);
        } else {
            log.warn("未支持的命令类型: {}", command.getCommandType());
        }
    }

    public void sendEmergencyStop(AgvStatus agvStatus, String reason) {
        StopPayload payload = StopPayload.builder()
                .reason(reason)
                .emergency(true)
                .clearOrder(true)
                .build();

        AgvControlCommand command = AgvControlCommand.builder()
                .agvId(agvStatus.getAgvId())
                .commandType(AgvControlCommand.ControlCommandType.INSTANT_ACTION)
                .payload(payload)
                .build();

        executeCommand(agvStatus, command);
    }


    /**
     * 转换为VDA5050协议消息
     */
    private Vda5050InstantActions convertToVda5050(AgvStatus agvStatus, Object payload) {
        if (payload instanceof SpeedReductionPayload) {
            return vda5050MessageBuilder.createSpeedLimitAction(agvStatus, (SpeedReductionPayload) payload);
        } else if (payload instanceof PausePayload) {
            return vda5050MessageBuilder.createStartPauseAction(agvStatus, (PausePayload) payload);
        } else if (payload instanceof StopPayload) {
            return vda5050MessageBuilder.createCancelOrderAction(agvStatus, (StopPayload) payload);
        } else if (payload instanceof RePlanPayload) {
            return vda5050MessageBuilder.createRePlanNotification(agvStatus, (RePlanPayload) payload);
        }
        throw new IllegalArgumentException("Unknown payload type: " + payload.getClass());
    }

    /**
     * 处理错误消息
     */
    private void handleErrorMessage(String topic, String message) {
        log.info("收到AGV出错消息,topic:{},message: {}", topic, message);
        try {
            // 使用ObjectMapper将JSON字符串直接映射到Vda5050ErrorMessage对象
            Vda5050ErrorMessage errorMessage = objectMapper.readValue(message, Vda5050ErrorMessage.class);
            ErrorPayload payload = errorMessage.getPayload();

            String agvId = payload.getAgvId();
            String errorCode = payload.getErrorCode();
            String errorDescription = payload.getErrorDescription();
            String errorLevel = payload.getErrorLevel();

            log.error("AGV错误:AGV={},Code={},Level={},Description={}", agvId, errorCode, errorLevel, errorDescription);

            // 记录错误引用信息（VDA5050协议特色）[2]
            if (payload.getErrorReferences() != null && !payload.getErrorReferences().isEmpty()) {
                log.debug("错误引用信息:");
                for (ErrorReference ref : payload.getErrorReferences()) {
                    log.debug("  {}: {}", ref.getReferenceKey(), ref.getReferenceValue());
                }
            }

            // 更新AGV状态
            if (agvId != null) {
                AgvStatus agvStatus = agvStatusService.getAgvStatus(agvId);
                if (agvStatus != null) {
                    // 根据错误级别设置不同的状态
                    AgvState newState = mapErrorLevelToState(errorLevel);
                    agvStatus.setAgvState(newState);

                    // 更新活跃错误列表
                    if (agvStatus.getActiveErrors() != null) {
                        String fullErrorInfo = String.format("%s|%s|%s", errorCode, errorLevel, errorDescription);
                        agvStatus.getActiveErrors().removeIf(error -> error.startsWith(errorCode + "|"));
                        agvStatus.getActiveErrors().add(fullErrorInfo);
                    }

                    agvStatusService.updateAgvStatus(agvStatus);
                    log.info("已更新AGV状态: AGV={}, 新状态={}", agvId, newState);
                }
            }
        } catch (Exception e) {
            log.error("处理错误消息失败, message: {}", message, e);
        }
    }

    /**
     * 将错误级别映射到AGV状态
     */
    private AgvState mapErrorLevelToState(String errorLevel) {
        if (errorLevel == null) return AgvState.ERROR;

        switch (errorLevel.toUpperCase()) {
            case "WARNING":
                return AgvState.PAUSED; // 警告，可能暂停运行
            case "FATAL":
            case "ERROR":
            default:
                return AgvState.ERROR;
        }
    }

    /**
     * 处理心跳消息
     */
    private void handleHeartbeatMessage(String topic, String message) {
        try {
            Vda5050HeartbeatMessage heartbeatMessage = objectMapper.readValue(message,
                    Vda5050HeartbeatMessage.class);
            HeartbeatPayload payload = heartbeatMessage.getPayload();

            // 更新AGV最后活跃时间
            if (payload.getAgvId() != null) {
                AgvStatus agvStatus = agvStatusService.getAgvStatus(payload.getAgvId());
                if (agvStatus != null) {
                    agvStatus.setLastUpdateTime(java.time.LocalDateTime.now());
                    // 记录调试信息
                    if (log.isTraceEnabled()) {
                        log.trace("心跳响应处理完成: AGV={}, battery={}", payload.getAgvId(), payload.getBatteryLevel());
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析心跳响应消息失败", e);
        }
    }

    /**
     * 发送任务分配通知
     */
    public void sendTaskAssigned(String taskId, String agvId, Task taskInfo) {
        Map<String, Object> message = new HashMap<>();
        message.put("taskId", taskId);
        message.put("agvId", agvId);
        message.put("taskInfo", taskInfo);
        message.put("timestamp", System.currentTimeMillis());
        String topic = Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_TASK_ASSIGNED);
        publish(topic, message);
    }

    /**
     * 发送任务完成广播
     */
    public void broadcastTaskComplete(String taskId, String agvId, String status) {
        Map<String, Object> message = new HashMap<>();
        message.put("taskId", taskId);
        message.put("agvId", agvId);
        message.put("status", status);
        message.put("timestamp", System.currentTimeMillis());

        String topic = Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_TASK_COMPLETE);
        publish(topic, message);
    }

    /**
     * 发送路径更新
     */
    public void sendPathUpdate(String agvId, String taskId, Map<String, Object> pathInfo) {
        Map<String, Object> message = new HashMap<>();
        message.put("command", "UPDATE_PATH");
        message.put("taskId", taskId);
        message.put("path", pathInfo);
        message.put("timestamp", System.currentTimeMillis());

        String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvId, Vda5050TopicConstants.TOPIC_PATH_UPDATE);
        publish(topic, message);
    }

    /**
     * 发送控制命令
     */
    public void sendControlCommand(String agvId, String command, Map<String, Object> parameters) {
        Map<String, Object> message = new HashMap<>();
        message.put("command", command);
        message.put("parameters", parameters);
        message.put("timestamp", System.currentTimeMillis());
        String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvId, Vda5050TopicConstants.TOPIC_CONTROL);
        publish(topic, message);
    }

    public void sendVda5050InstantAction(String agvId, Vda5050InstantActions vda5050InstantActions) {
        String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvId, Vda5050TopicConstants.TOPIC_INSTANT_ACTIONS);
        publish(topic, vda5050InstantActions);
    }

    /**
     * 检查连接状态
     */
    public boolean isMqttConnected() {
        return !connected.get() || mqttClient == null || !mqttClient.isConnected();

    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
        }

        closeExistingClient();
        connected.set(false);
        reconnecting.set(false);
        log.info("MQTT连接已手动断开");
    }


    // MQTT回调接口实现
    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT连接丢失: {}", cause.getMessage());
        connected.set(false);

        if (automaticReconnect && !reconnecting.get()) {
            reconnectAttempt.set(0); // 重置尝试计数
            scheduleReconnect(INITIAL_RECONNECT_DELAY_MS);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // 消息处理由各监听器完成
        // 这里只记录日志
        log.debug("收到MQTT消息: topic={}, payload={}", topic, new String(message.getPayload()));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 消息发布完成回调
        log.debug("消息发送完成: messageId={}", token.getMessageId());
    }
}