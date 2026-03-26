package com.sdt.agv_simulator.component;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.jizhi.vda5050.agv.AgvPosition;
import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.jizhi.vda5050.message.Vda5050StateMessage;
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
import java.util.concurrent.atomic.AtomicInteger;

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

        log.info("发送移动命令: AGV={}, 命令ID={}, 目标({}, {}, {})",agvId, commandId, node.getX(), node.getY(), node.getTheta());
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
     * 发送AGV控制命令
     */
    public void sendAgvControl(String agvId, String action) throws Ros2BusinessException {
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
     * 发送二阶贝塞尔曲线命令
     */
    public void sendQuadraticBezierCommand(
            String agvId, String commandId,
            String startNodeId, String endNodeId,
            double startX, double startY, double startTheta,
            double endX, double endY, double endTheta,
            double controlX, double controlY) throws Ros2BusinessException {

        MoveBezierCurveMessage bezierCmd = messageFactory.createQuadraticBezier(
                agvId, commandId, startNodeId, endNodeId,
                startX, startY, startTheta,
                endX, endY, endTheta,
                controlX, controlY
        );

        sendMessage(bezierCmd);

        log.info("发送二阶贝塞尔曲线命令: AGV={}, 起点({}, {}), 终点({}, {}), 控制点({}, {})",
                agvId, startX, startY, endX, endY, controlX, controlY);
    }

    /**
     * 发送三阶贝塞尔曲线命令
     */
    public void sendCubicBezierCommand(
            String agvId, String commandId,
            String startNodeId, String endNodeId,
            double startX, double startY, double startTheta,
            double endX, double endY, double endTheta,
            double controlX1, double controlY1,
            double controlX2, double controlY2) throws Ros2BusinessException {

        MoveBezierCurveMessage bezierCmd = messageFactory.createCubicBezier(
                agvId, commandId, startNodeId, endNodeId,
                startX, startY, startTheta,
                endX, endY, endTheta,
                controlX1, controlY1, controlX2, controlY2
        );

        sendMessage(bezierCmd);

        log.info("发送三阶贝塞尔曲线命令: AGV={}, 起点({}, {}), 终点({}, {}), 控制点1({}, {}), 控制点2({}, {})",
                agvId, startX, startY, endX, endY,
                controlX1, controlY1, controlX2, controlY2);
    }

    /**
     * 发送多段路径命令
     */
    public void sendMultiSegmentCommand(
            String agvId, String commandId,
            List<PathPointBean> pathPoints) throws Ros2BusinessException {

        MoveMultiSegmentMessage multiSegmentCmd = messageFactory.createMultiSegmentPath(
                agvId, commandId, pathPoints
        );

        sendMessage(multiSegmentCmd);

        log.info("发送多段路径命令: AGV={}, 路径点数量={}", agvId, pathPoints.size());
    }

    /**
     * 根据Edge发送贝塞尔曲线命令
     */
    public void sendBezierCurveFromEdge(
            String agvId, String commandId,
            Node startNode, Node endNode,
            Edge edge) throws Ros2BusinessException {

        MoveBezierCurveMessage bezierCmd = messageFactory.createBezierCurveFromEdge(
                agvId, commandId, startNode, endNode, edge
        );

        sendMessage(bezierCmd);

        log.info("发送贝塞尔曲线命令: AGV={}, 边={}, 控制点数量={}",
                agvId, edge.getId(),
                edge.getControlPoints() != null ? edge.getControlPoints().size() : 0);
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


    public interface MessageListener {
        void onMessage(String message);
    }

    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();

    // 提供注册监听器的方法
    public void addMessageListener(MessageListener listener) {
        listeners.add(listener);
    }
}