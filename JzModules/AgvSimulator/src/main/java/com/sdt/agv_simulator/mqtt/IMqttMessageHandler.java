package com.sdt.agv_simulator.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public interface IMqttMessageHandler {
    void handleVda5050PathUpdate(String topic, MqttMessage mqttMessage);

    void handleVda5050Control(String topic, MqttMessage mqttMessage);
    void handleVda5050BroadcastControl(String topic, MqttMessage mqttMessage);

    void handleVda5050OrderMessage(String topic, MqttMessage mqttMessage);

    void handleVda5050BroadcastOrder(String topic, MqttMessage mqttMessage);
}
