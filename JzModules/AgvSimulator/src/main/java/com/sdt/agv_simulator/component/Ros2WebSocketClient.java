package com.sdt.agv_simulator.component;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.sdt.agv_simulator.ros_message.*;
import com.sdt.agv_simulator.utils.Ros2MessageFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class Ros2WebSocketClient {

    @Value("${ros2.websocket.url}")
    private String rosWebSocketUrl;

    @Value("${ros2.websocket.reconnect-interval:5000}")
    private int reconnectInterval;

    // 消息队列相关
    private final BlockingQueue<Ros2Message> messageQueue = new LinkedBlockingQueue<>(512);
    private volatile boolean messageSenderRunning = true;
    private ScheduledFuture<?> heartbeatFuture;

    private Ros2WebSocketHandler ros2WebSocketHandler;
    private WebSocketSession session;

    private final ScheduledExecutorService messageSenderExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();

    @Getter
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    @Autowired
    private Ros2MessageFactory messageFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 存储等待响应的请求
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();


    @PostConstruct
    public void init() {
        ros2WebSocketHandler = new Ros2WebSocketHandler(this::handleConnected, this::handleDisconnect,
                this::handleRos2Message);
        startMessageSender();  // 先启动消息发送器
        reconnectExecutor.schedule(this::connectWithRetry, 1, TimeUnit.SECONDS);
    }

    private void handleConnected(WebSocketSession session) {
        this.session = session;
        sendRegistrationMessage();
        startHeartbeat();
    }

    private void handleDisconnect(WebSocketSession session) {
        reconnect();
    }

    private void handleRos2Message(String message) {
        String requestId = extractRequestId(message);
        if (requestId != null) {
            CompletableFuture<String> future = pendingRequests.remove(requestId);
            if (future != null) {
                future.complete(message);
                return;
            }
        }
        for (MessageListener listener : listeners) {
            listener.onMessage(message);
        }
    }

    /**
     * 重连机制
     */
    private void reconnect() {
        log.debug("ROS2 WebSocket执行重连！");
        // 清理现有连接
        if (session != null) {
            try {
                session.close();
            } catch (IOException e) {
                log.debug("Close session error during reconnect: ", e);
            }
            session = null;
        }

        // 检查线程池状态后再提交任务
        if (reconnectExecutor.isShutdown() || reconnectExecutor.isTerminated()) {
            log.warn("重连线程池已终止，无法执行重连任务");
            return;
        }

        log.info("2秒后重新连接...");
        reconnectExecutor.schedule(this::connectWithRetry, 2, TimeUnit.SECONDS);
    }

    private void connectWithRetry() {
        if (!isConnecting.compareAndSet(false, true)) {
            log.debug("已有连接任务在执行，跳过本次重连");
            return;
        }
        try {
            log.info("正在连接ROS2 WebSocket服务器: {}", rosWebSocketUrl);
            WebSocketClient client = new StandardWebSocketClient();
            this.session = client.execute(ros2WebSocketHandler, new WebSocketHttpHeaders(),
                    new URI(rosWebSocketUrl)).get(10, TimeUnit.SECONDS);

            if (heartbeatExecutor.isShutdown() || heartbeatExecutor.isTerminated()) {
                log.warn("重连线程池已终止，无法执行重连任务");
                return;
            }
        } catch (Exception e) {
            log.error("WebSocket连接失败，10秒后重试. 原因: {}", e.getMessage());
            reconnectExecutor.schedule(this::connectWithRetry, 10, TimeUnit.SECONDS);
        } finally {
            isConnecting.set(false);
        }
    }

    /**
     * 启动心跳检测
     */
    private void startHeartbeat() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);  // false表示不中断正在执行的任务
        }

        heartbeatFuture = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (!isServiceReady()) {
                log.warn("ROS2连接没有建立，本次不发送心跳消息");
                return;
            }
            try {
                sendHeartbeatMessage();
            } catch (Ros2BusinessException e) {
                log.error("心跳消息发送失败，准备重连", e);
                reconnect();
            }
        }, 10, 15, TimeUnit.SECONDS);
    }

    /**
     * 发送心跳消息
     */
    private void sendHeartbeatMessage() throws Ros2BusinessException {
        HeartbeatMessage heartbeat = messageFactory.createHeartbeat();
        sendMessage(heartbeat);

    }

    /**
     * 发送移动命令
     */
    public void sendMoveCommand(String agvId, String commandId, Node node, Edge passedEdge, boolean isEnd)
            throws Ros2BusinessException {
        MoveToMessage moveCommand = messageFactory.createMoveCommand(agvId, commandId, node, passedEdge, isEnd);
        sendMessage(moveCommand);

        log.info("发送移动命令: AGV={}, 命令ID={}, 目标({}, {}, {})", agvId, commandId, node.getX(), node.getY(),
                node.getTheta());
    }

    /**
     * 发送初始位置设置
     */
    public void sendInitialPose(String agvId, double x, double y, double theta)
            throws Ros2BusinessException {
        SetInitialPoseMessage initPose = messageFactory.createInitialPose(agvId, x, y, theta);
        sendMessage(initPose);

        log.info("设置初始位姿: AGV={}, 位置({}, {}, {})", agvId, x, y, theta);

    }

    /**
     * 发送速度命令
     */
    public void sendVelocityCommand(String agvId, double vx, double vy, double omega)
            throws Ros2BusinessException {
        VelocityCommandMessage velocityCmd = messageFactory.createVelocityCommand(agvId, vx, vy, omega);
        sendMessage(velocityCmd);

        log.debug("发送速度命令: AGV={}, v({}, {}, {})", agvId, vx, vy, omega);
    }

    /**
     * 发送导航控制命令（暂停/恢复/取消）
     */
    public void sendNavigationControl(String agvId, String action) throws Ros2BusinessException {
        AgvControlMessage controlCmd = messageFactory.createAgvControl(agvId, action);
        sendMessage(controlCmd);
        log.info("发送AGV控制命令: AGV={}, 动作={}", agvId, action);
    }

    private void sendRegistrationMessage() {
        try {
            RegisterMessage register = messageFactory.createRegister();
            sendMessage(register);
            log.info("已发送注册消息到ROS2服务器: {}", register.getRequestId());
        } catch (Exception e) {
            log.error("发送注册消息失败", e);
            throw new Ros2BusinessException("1000", "发送注册消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送急停命令
     */
    public void sendEmergencyStop(String agvId) throws Ros2BusinessException {
        EmergencyStopMessage emergencyMsg = messageFactory.createEmergencyStop(agvId);
        sendMessage(emergencyMsg);
        log.info("发送急停命令: AGV={}", agvId);
    }

    /**
     * 发送清除急停命令
     */
    public void sendClearEmergency(String agvId) throws Ros2BusinessException {
        ClearEmergencyMessage clearMsg = messageFactory.createClearEmergency(agvId);
        sendMessage(clearMsg);
        log.info("发送清除急停命令: AGV={}", agvId);
    }

    /**
     * 发送速度限制命令
     *
     * @param agvId    AGV ID
     * @param maxSpeed 最大线速度 (m/s)
     * @throws Ros2BusinessException
     */
    public void sendSpeedLimit(String agvId, Double maxSpeed) throws Ros2BusinessException {
        SpeedLimitMessage speedLimitMsg = messageFactory.createSpeedLimit(agvId, maxSpeed);
        sendMessage(speedLimitMsg);
        log.info("发送速度限制命令: AGV={}, maxSpeed={} m/s", agvId, maxSpeed);
    }

    /**
     * 发送速度限制命令（带角速度）
     *
     * @param agvId           AGV ID
     * @param maxSpeed        最大线速度 (m/s)
     * @param maxAngularSpeed 最大角速度 (rad/s)
     * @throws Ros2BusinessException
     */
    public void sendSpeedLimit(String agvId, Double maxSpeed, Double maxAngularSpeed) throws Ros2BusinessException {
        SpeedLimitMessage speedLimitMsg = messageFactory.createSpeedLimit(agvId, maxSpeed, maxAngularSpeed);
        sendMessage(speedLimitMsg);
        log.info("发送速度限制命令: AGV={}, maxSpeed={} m/s, maxAngularSpeed={} rad/s",
                agvId, maxSpeed, maxAngularSpeed);
    }

    /**
     * 发送消息到队列（核心方法）
     */
    private void sendMessage(Ros2Message message) throws Ros2BusinessException {
        if (!isServiceReady()) {
            throw new Ros2BusinessException("1000", "ROS2 WebSocket未连接，无法发送消息");
        }

        try {
            // 将消息放入队列
            boolean success = messageQueue.offer(message, 3, TimeUnit.SECONDS);
            if (!success) {
                throw new Ros2BusinessException("1000", "消息队列已满，无法发送消息");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Ros2BusinessException("1000", "消息入队被中断", e);
        }
    }

    /**
     * 同步发送消息并等待响应
     *
     * @param message 请求消息（应包含 requestId，若没有会自动生成）
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 响应消息的 JSON 字符串
     */
    public String sendMessageSync(Ros2Message message, long timeout, TimeUnit unit)
            throws Ros2BusinessException, TimeoutException, InterruptedException {
        final String requestId = message.getRequestId();

        // 创建 future 并注册
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            // 异步发送消息（不阻塞）
            sendMessage(message);
            // 阻塞等待响应
            return future.get(timeout, unit);
        } catch (ExecutionException e) {
            throw new Ros2BusinessException("1001", "同步调用异常: " + e.getCause().getMessage(), e);
        } catch (TimeoutException e) {
            throw e;
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    /**
     * 启动单线程消息发送器
     */
    private void startMessageSender() {
        messageSenderExecutor.execute(() -> {
            while (messageSenderRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    Ros2Message ros2Message = messageQueue.poll(1, TimeUnit.SECONDS); // 带超时的poll
                    if (ros2Message != null) {
                        sendMessageSafely(ros2Message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Message sender error: ", e);
                }
            }
        });
    }

    /**
     * 安全发送消息
     */
    private void sendMessageSafely(Ros2Message message) {
        try {
            synchronized (this) { // 同步块确保串行发送
                if (session != null && session.isOpen()) {
                    String content = objectMapper.writeValueAsString(message);
                    session.sendMessage(new TextMessage(content));
                    log.debug("消息已发送: {}", content.length() > 512 ?
                            content.substring(0, 512) + "..." : content);
                } else {
                    log.warn("WebSocket会话未打开，无法发送消息");
                    reconnect();
                }
            }
        } catch (Exception e) {
            log.error("发送消息失败", e);
            reconnect();
        }
    }

    @PreDestroy
    public void destroy() {
        // 1. 设置停止标志
        messageSenderRunning = false;
        messageSenderExecutor.shutdown(); // 关闭消息发送器

        try {
            if (!messageSenderExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                messageSenderExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            messageSenderExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);  // false表示不中断正在执行的任务
        }
        // 关闭心跳任务
        heartbeatExecutor.shutdown();
        reconnectExecutor.shutdown();

        // 清理消息队列
        messageQueue.clear();
        // 关闭 WebSocket 连接
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.error("关闭 WebSocket 连接失败: ", e);
            }
        }
    }

    public boolean isServiceReady() {
        return ros2WebSocketHandler.getIsServiceAvailable().get() &&
                session != null && session.isOpen();
    }

    @PreDestroy
    public void onDestroy() {
        log.info("开始销毁ROS2 WebSocket客户端资源...");

        if (!reconnectExecutor.isShutdown()) {
            reconnectExecutor.shutdown();
            try {
                if (!reconnectExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    reconnectExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                reconnectExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (!heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (!messageSenderExecutor.isShutdown()) {
            messageSenderExecutor.shutdown();
            try {
                if (!messageSenderExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    messageSenderExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                messageSenderExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        listeners.clear();
        log.info("ROS2 WebSocket客户端销毁完成");
    }

    private String extractRequestId(String jsonMessage) {
        // 使用 Jackson 解析 JSON，提取 requestId 字段
        try {
            JsonNode node = objectMapper.readTree(jsonMessage);
            JsonNode ridNode = node.get("requestId");
            return ridNode != null ? ridNode.asText() : null;
        } catch (IOException e) {
            log.warn("解析响应消息 requestId 失败: {}", e.getMessage());
            return null;
        }
    }

    public interface MessageListener {
        void onMessage(String message);
    }

    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();

    // 提供注册监听器的方法
    public void addMessageListener(MessageListener listener) {
        listeners.add(listener);
    }
}