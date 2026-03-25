package com.sdt.agv_simulator.config;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "map")
public class MapConfig {

    private String dir = "";
    private String name = "";
    private String imageFile = "";
    private String yamlFile = "";
}
