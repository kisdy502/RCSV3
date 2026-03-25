package com.sdt.agv_simulator.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agv.simulator")
public class AgvSimulatorConfig {
    private String agvId;
    private String name;
    private String manufacturer;
    private String serialNumber;
    private String version = "2.0.0";
    private String agvType;
    private Double batteryLevel;
    private Double maxSpeed;
    private Double loadCapacity;
    private Boolean connected;
    private Boolean enabled;
//    private String initialNode;

}