package com.sdt.agv_simulator.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

public interface IMqttConnectListener {
    void onMqttConnect();
    void onMqttDisConnect();


}
