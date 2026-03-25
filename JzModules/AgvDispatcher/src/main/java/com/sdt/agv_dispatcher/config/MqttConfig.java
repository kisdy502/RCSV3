package com.sdt.agv_dispatcher.config;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;

@Slf4j
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url:tcp://192.168.3.3:1883}")
    private String brokerUrl;

    @Value("${mqtt.client.id:agv-scheduler}")
    private String clientId;

    @Value("${mqtt.username:}")  // 可选：如果broker需要认证
    private String username;

    @Value("${mqtt.password:}")  // 可选：如果broker需要认证
    private String password;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();

        options.setServerURIs(new String[]{brokerUrl});
        options.setCleanSession(true);
        options.setConnectionTimeout(10); // 连接超时时间（秒）
        options.setKeepAliveInterval(30); // 心跳间隔（秒）
        options.setAutomaticReconnect(true); // 自动重连


        // 如果配置了用户名密码
        if (username != null && !username.trim().isEmpty()) {
            options.setUserName(username);
            if (password != null && !password.trim().isEmpty()) {
                options.setPassword(password.toCharArray());
            }
        }

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MqttClient mqttClient() {
        try {
            log.info("正在连接MQTT Broker: {}", brokerUrl);

            // 创建客户端
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

            // 获取连接选项并连接
            MqttConnectOptions options = mqttClientFactory().getConnectionOptions();
            client.connect(options);

            // 验证连接状态
            if (client.isConnected()) {
                log.info("✅ 成功连接到MQTT Broker: {}", brokerUrl);
            } else {
                log.error("❌ 连接MQTT Broker失败");
            }

            return client;

        } catch (MqttException e) {
            log.error("初始化MQTT客户端失败: {}", e.getMessage(), e);
            // 不抛出异常，让应用继续启动，但MQTT功能将不可用
            return null;
        }
    }
}
