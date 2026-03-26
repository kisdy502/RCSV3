package com.sdt.agv_simulator.mqtt;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.vda5050.agv.TaskStatus;
import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.constants.Vda5050TopicConstants;
import com.jizhi.vda5050.message.*;
import com.sdt.agv_simulator.config.AgvSimulatorConfig;
import com.sdt.agv_simulator.service.Vda5050MessageBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class AgvMqttGateway implements MqttCallback, ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy  // 关键：延迟注入处理器
    private IMqttMessageHandler mqttMessageHandler;

    @Autowired
    @Lazy
    private IMqttConnectListener mqttConnectListener;

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.broker.client-id}")
    private String clientId;

    @Value("${mqtt.broker.username}")
    private String username;

    @Value("${mqtt.broker.password}")
    private String password;

    @Value("${mqtt.broker.clean-session:true}")
    private boolean cleanSession;

    @Value("${mqtt.broker.connection-timeout:10}")
    private int connectionTimeout;

    @Value("${mqtt.broker.keep-alive-interval:60}")
    private int keepAliveInterval;

    @Value("${mqtt.broker.automatic-reconnect:true}")
    private boolean automaticReconnect;

    @Autowired
    private Vda5050MessageBuilder vda5050MessageBuilder;

    @Autowired
    private AgvSimulatorConfig agvConfig;

    private MqttClient mqttClient;

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

        log.info("将在 {}ms 后尝试重连 MQTT 服务器 (第{}次尝试)", delayMs, reconnectAttempt.incrementAndGet());

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
            mqttConnectListener.onMqttConnect();
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
     * 订阅主题（使用简化格式）
     */
    private void subscribeTopics() {
        try {
            // 获取当前AGV的ID
            String agvId = agvConfig.getAgvId();

            // 订阅订单主题 - 简化格式
            String orderTopic = Vda5050TopicConstants.buildSimplifiedTopic(agvId, Vda5050TopicConstants.TOPIC_ORDER);
            mqttClient.subscribe(orderTopic, 0, mqttMessageHandler::handleVda5050OrderMessage);

            // 订阅控制命令主题 - 简化格式
            String controlTopic = Vda5050TopicConstants.buildSimplifiedTopic(agvId,
                    Vda5050TopicConstants.TOPIC_CONTROL);
            mqttClient.subscribe(controlTopic, 0, mqttMessageHandler::handleVda5050Control);

            // 订阅路径更新主题 - 简化格式
            String pathUpdateTopic = Vda5050TopicConstants.buildSimplifiedTopic(agvId,
                    Vda5050TopicConstants.TOPIC_PATH_UPDATE);
            mqttClient.subscribe(pathUpdateTopic, 0, mqttMessageHandler::handleVda5050PathUpdate);

            // 订阅广播命令（所有AGV都能接收）
            String agvControlTopic = Vda5050TopicConstants.BROADCAST_CONTROL_TOPIC;
            mqttClient.subscribe(agvControlTopic, 0, mqttMessageHandler::handleVda5050BroadcastControl);

            String orderBroadcastTopic = Vda5050TopicConstants.BROADCAST_ORDER_TOPIC;
            mqttClient.subscribe(orderBroadcastTopic, 0, mqttMessageHandler::handleVda5050BroadcastOrder);

            log.info("已订阅主题（简化格式）:");
            log.info("  - 订单主题: {}", orderTopic);
            log.info("  - 控制命令主题: {}", controlTopic);
            log.info("  - 路径更新主题: {}", pathUpdateTopic);
            log.info("  - 广播控制主题: {}", agvControlTopic);
            log.info("  - 广播订单主题: {}", orderBroadcastTopic);

        } catch (MqttException e) {
            log.error("订阅主题失败", e);
        }
    }

    /**
     * 发送VDA5050状态消息（使用简化主题）
     */
    public void sendAgvState(AgvStatus agvStatus) {
        try {
            // 构建VDA5050状态消息
            Vda5050StateMessage stateMessage = vda5050MessageBuilder.buildStateMessage(agvStatus);
            // 简化主题格式: agv/{agvId}/state
            String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvStatus.getAgvId(),
                    Vda5050TopicConstants.TOPIC_STATE);

            // 发送消息
            String message = objectMapper.writeValueAsString(stateMessage);
            publish(topic, message);
        } catch (Exception e) {
            log.error("发送AGV状态消息失败", e);
        }
    }

    /**
     * 发送订单状态（使用简化主题）
     */
    public void sendOrderState(AgvStatus agvStatus, String orderId, String orderState,
                               Integer orderUpdateId, String resultDescription) {
        try {
            Vda5050OrderStateMessage orderStateMessage =
                    vda5050MessageBuilder.buildOrderStateMessage(agvStatus, orderState,
                            resultDescription);
            // 简化主题格式: agv/{agvId}/order/state
            String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvStatus.getAgvId(),
                    Vda5050TopicConstants.TOPIC_ORDER_STATE);

            String jsonMessage = objectMapper.writeValueAsString(orderStateMessage);
            publish(topic, jsonMessage);
            log.info("发送订单状态: agv={}, order={}, state={}", agvStatus.getAgvId(), orderId, orderState);
        } catch (Exception e) {
            log.error("发送订单状态失败", e);
        }
    }


    /**
     * 发送位置更新（使用简化主题）
     */
    public void sendPositionUpdate(AgvStatus agvStatus) {
        try {
            Vda5050PositionUpdateMessage vda5050PositionUpdateMessage =
                    vda5050MessageBuilder.buildPositionUpdateMessage(agvStatus);
            // 简化主题格式: agv/{agvId}/position
            String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvStatus.getAgvId(),
                    Vda5050TopicConstants.TOPIC_POSITION);
            String jsonMessage = objectMapper.writeValueAsString(vda5050PositionUpdateMessage);
            if (vda5050PositionUpdateMessage.getPosition().getCurrentNodeId() != null || vda5050PositionUpdateMessage.getPosition().getCurrentEdgeId() != null) {
//                log.debug("发送位置更新消息,topic:{},message: {}", topic, vda5050PositionUpdateMessage);
            }
            publish(topic, jsonMessage);
        } catch (Exception e) {
            log.error("发送位置更新失败", e);
        }
    }

    /**
     * 发送任务完成（使用简化主题）
     */
    public void sendTaskComplete(AgvStatus agvStatus, String taskId, TaskStatus status, String message,
                                 LocalDateTime startTime) {
        try {
            Vda5050TaskCompletionMessage vda5050Message =
                    vda5050MessageBuilder.buildStandardTaskCompleteMessage(agvStatus, taskId, status,
                            message, startTime);
            // 简化主题格式: agv/{agvId}/task/complete
            String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvStatus.getAgvId(),
                    Vda5050TopicConstants.TOPIC_TASK_COMPLETE);

            String jsonMessage = objectMapper.writeValueAsString(vda5050Message);
            publish(topic, jsonMessage);

            log.info("发送任务完成: agv={}, task={}, status={}", agvStatus.getAgvId(), taskId, status);
        } catch (Exception e) {
            log.error("发送任务完成失败", e);
        }
    }

    /**
     * 发送AGV上线通知（使用简化主题）
     */
    public void sendAgvOnline(AgvStatus agvStatus, String name, String type) {
        try {
            // 构建上线消息
            String jsonMessage = objectMapper.writeValueAsString(vda5050MessageBuilder.buildOnlineMessage(agvStatus));

            // 简化主题格式: agv/online（广播）和 agv/{agvId}/connection（点对点）
            String broadcastTopic = Vda5050TopicConstants.buildSendBroadcastTopic(Vda5050TopicConstants.TOPIC_ONLINE);
            String pointToPointTopic = Vda5050TopicConstants.buildSimplifiedTopic(agvStatus.getAgvId(),
                    Vda5050TopicConstants.TOPIC_CONNECTION);
            // 发送广播上线消息
            publish(broadcastTopic, jsonMessage);

            // 发送点对点上线消息
            publish(pointToPointTopic, jsonMessage);

            log.info("发送AGV上线通知: {}, 主题: {}", agvStatus.getAgvId(), broadcastTopic);
            agvStatus.setConnected(true);
        } catch (Exception e) {
            log.error("发送AGV上线通知失败", e);
        }
    }

    /**
     * 发送AGV下线通知（使用简化主题）
     */
    public void sendAgvOffline(AgvStatus agvStatus) {
        try {
            // 构建下线消息
            String jsonMessage = objectMapper.writeValueAsString(vda5050MessageBuilder.buildOfflineMessage(agvStatus));
            // 简化主题格式: agv/offline（广播）和 agv/{agvId}/connection（点对点）
            String broadcastTopic = Vda5050TopicConstants.buildBroadcastTopic(Vda5050TopicConstants.TOPIC_OFFLINE);
            String pointToPointTopic = Vda5050TopicConstants.buildSimplifiedTopic(agvStatus.getAgvId(),
                    Vda5050TopicConstants.TOPIC_CONNECTION);
            // 发送广播下线消息
            publish(broadcastTopic, jsonMessage);
            // 发送点对点下线消息
            publish(pointToPointTopic, jsonMessage);
            agvStatus.setConnected(false);
            log.info("发送AGV下线通知: {}, 主题: {}", agvStatus.getAgvId(), broadcastTopic);
        } catch (Exception e) {
            log.error("发送AGV下线通知失败", e);
        }
    }

    /**
     * 发送心跳（使用简化主题）
     */
    public void sendHeartbeat(AgvStatus agvStatus) {
        try {
            // 构建VDA5050标准格式的心跳消息[1,5](@ref)
            Vda5050HeartbeatMessage heartbeatMessage = vda5050MessageBuilder.buildHeartbeatMessage(agvStatus);

            // 简化主题格式: agv/{agvId}/heartbeat
            String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvStatus.getAgvId(),
                    Vda5050TopicConstants.TOPIC_HEARTBEAT);

            String jsonMessage = objectMapper.writeValueAsString(heartbeatMessage);
            publish(topic, jsonMessage);

            log.debug("发送VDA5050心跳: agv={}, battery={}, state={}",
                    agvStatus.getAgvId(), agvStatus.getBatteryLevel(), agvStatus.getAgvState());

        } catch (Exception e) {
            log.error("发送心跳失败", e);
        }
    }

    /**
     * 发送错误信息（使用简化主题）
     */
    public void sendError(AgvStatus agvStatus, String errorCode, String errorDescription) {
        try {
            Vda5050ErrorMessage vda5050ErrorMessage = vda5050MessageBuilder.buildErrorMessage(agvStatus, errorCode,
                    errorDescription, "ERROR");
            // 简化主题格式: agv/{agvId}/error
            String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvStatus.getAgvId(),
                    Vda5050TopicConstants.TOPIC_ERROR);

            String jsonMessage = objectMapper.writeValueAsString(vda5050ErrorMessage);
            publish(topic, jsonMessage);

            log.warn("发送错误信息: agv={}, code={}, desc={}",
                    agvStatus.getAgvId(), errorCode, errorDescription);

        } catch (Exception e) {
            log.error("发送错误信息失败", e);
        }
    }

    /**
     * 发送行动状态（使用简化主题）
     */
    public void sendActionState(AgvStatus agvStatus, String actionId, TaskStatus actionStatus, String rescription) {
        try {
            Vda5050ActionStateMessage vda5050Message =
                    vda5050MessageBuilder.buildStandardActionStateMessage(agvStatus, actionId,
                            actionStatus, rescription);
            // 简化主题格式: agv/{agvId}/action/state
            String topic = Vda5050TopicConstants.buildSimplifiedTopic(agvStatus.getAgvId(),
                    Vda5050TopicConstants.TOPIC_ACTION_STATE);

            String jsonMessage = objectMapper.writeValueAsString(vda5050Message);
            publish(topic, jsonMessage);

            log.info("发送行动状态: agv={}, action={}, status={}", agvStatus.getAgvId(), actionId, actionStatus);

        } catch (Exception e) {
            log.error("发送行动状态失败", e);
        }
    }

    /**
     * 发布MQTT消息
     */
    public void publish(String topic, String message) {
        if (!isConnected()) {
            log.warn("MQTT未连接，无法发布消息: topic={}", topic);
            return;
        }

        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(1);
            mqttMessage.setRetained(false);
            mqttClient.publish(topic, mqttMessage);

            //log.debug("MQTT消息 published: topic={}, 消息长度={}", topic, message.length());
        } catch (MqttException e) {
            log.error("发布MQTT消息时发生MqttException: topic={}, errorCode={}, reason={}",
                    topic, e.getReasonCode(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("发布MQTT消息失败: topic={}", topic, e);
        }
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connected.get() && mqttClient != null && mqttClient.isConnected();
    }

    // MQTT回调接口实现
    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT连接丢失: {}", cause.getMessage());
        connected.set(false);
        mqttConnectListener.onMqttDisConnect();
        if (automaticReconnect && !reconnecting.get()) {
            reconnectAttempt.set(0); // 重置尝试计数
            scheduleReconnect(INITIAL_RECONNECT_DELAY_MS);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload());
//        log.debug("收到MQTT消息: topic={}, payload={}", topic, payload);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
//        log.debug("消息发送完成: messageId={}", token.getMessageId());
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

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Spring 上下文完全启动后再初始化 MQTT
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "agv-client-MQTT-Reconnect-Scheduler");
            t.setDaemon(true);
            return t;
        });
        if (event.getApplicationContext().getParent() == null) {
            // 确保是根上下文，避免触发两次
            boolean success = doReconnect();
            if (!success) {
                log.warn("MQTT首次连接失败，将在后台尝试重连");
                scheduleReconnect(INITIAL_RECONNECT_DELAY_MS);
            }
        }
    }
}
