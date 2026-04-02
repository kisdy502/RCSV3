package com.sdt.agv_simulator.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class Ros2WebSocketHandler extends TextWebSocketHandler {

    @Getter
    private final AtomicBoolean isServiceAvailable = new AtomicBoolean(false);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 存储所有活跃会话（可选）
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    private final Consumer<WebSocketSession> onConnectedCallback;
    private final Consumer<WebSocketSession> onDisconnectCallback;

    private final Consumer<String> onReceiveMessageCallback;

    // JSON处理工具

    public Ros2WebSocketHandler(Consumer<WebSocketSession> connectedCallback,
                                Consumer<WebSocketSession> onDisconnectCallback,
                                Consumer<String> onMessageCallback) {
        this.onConnectedCallback = connectedCallback;
        this.onDisconnectCallback = onDisconnectCallback;
        this.onReceiveMessageCallback = onMessageCallback;
    }

    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("ROS2 WebSocket 连接成功: {}", session.getId());
        isServiceAvailable.set(true);
        if (onConnectedCallback != null) {
            onConnectedCallback.accept(session);
        }


    }

    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        sessions.remove(session);
        log.warn("ROS2 WebSocket 连接断开: {}, 原因: {}", session.getId(), status);
        isServiceAvailable.set(false);
        if (onDisconnectCallback != null) {
            onDisconnectCallback.accept(session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("ROS2 WebSocket 传输错误: {}, 异常: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
        isServiceAvailable.set(false);
        session.close(CloseStatus.SERVER_ERROR);
        if (onDisconnectCallback != null) {
            onDisconnectCallback.accept(session);
        }
    }

    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("收到ROS2消息: {}", payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);


        if (onReceiveMessageCallback != null) {
            onReceiveMessageCallback.accept(payload);
        }

    }




}
