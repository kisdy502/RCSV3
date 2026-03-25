package com.sdt.agv_dispatcher.service;

import com.jizhi.vda5050.domain.MapMetaData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@Slf4j
public class YamlMapReader {

    public MapMetaData readMapConfig(String mapYamlPath) {
        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new PathResource(mapYamlPath));
        Properties properties = yamlFactory.getObject();

        MapMetaData metaData = new MapMetaData();
        metaData.setResolution(Float.parseFloat(properties.getProperty("resolution")));
        metaData.setNegate(Integer.parseInt(properties.getProperty("negate")));
        metaData.setOccupiedThresh(Double.parseDouble(properties.getProperty("occupied_thresh")));
        metaData.setFreeThresh(Double.parseDouble(properties.getProperty("free_thresh")));

        // 直接读取已拆分的origin数组元素
        float[] origin = new float[3];
        origin[0] = Float.parseFloat(properties.getProperty("origin[0]"));
        origin[1] = Float.parseFloat(properties.getProperty("origin[1]"));
        origin[2] = Float.parseFloat(properties.getProperty("origin[2]"));
        metaData.setOrigin(origin);

        return metaData;
    }
}
