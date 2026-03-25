package com.sdt.agv_dispatcher.service;


import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.MapDetailInfo;
import com.jizhi.vda5050.domain.MapMetaData;
import com.jizhi.vda5050.domain.Node;
import com.sdt.agv_dispatcher.component.GraphDataGenerator;
import com.sdt.agv_dispatcher.config.MapConfig;
import com.sdt.agv_dispatcher.graph.AGVGraph;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MapInitializationService {

    @Autowired
    @Getter
    private MapConfig mapConfig;

    @Autowired
    private AGVGraph agvGraph;


    @Autowired
    private GraphDataGenerator graphDataGenerator;

    @Autowired
    private YamlMapReader yamlMapReader;


    @Autowired
    private PgmFileReader pgmFileReader; // 新增PGM文件读取器


    @Getter
    List<Node> nodes;

    @Getter
    List<Edge> edges;

    private MapDetailInfo mapDetailInfo;

    /**
     * 初始化地图节点和边
     */
    @PostConstruct
    public void initMap() {
        try {
            log.info("开始初始化地图: {}", mapConfig.getName());
            //TODO 读取地图PGM文件

            // 读取地图YAML文件（如果存在）
            String mapYamlPath = mapConfig.getDir() + File.separator + mapConfig.getYamlFile();
            MapMetaData mapMetaData = yamlMapReader.readMapConfig(mapYamlPath);

            String pgmFilePath = mapConfig.getDir() + File.separator + mapConfig.getImageFile();
            int[] dimensions = readPgmFileDimensions(pgmFilePath);

            // 更新MapMetaData的宽度和高度
            mapMetaData.setWidth(dimensions[0]);
            mapMetaData.setHeight(dimensions[1]);

            log.info("地图尺寸: {}x{} 像素", dimensions[0], dimensions[1]);


            // 创建节点
            nodes = graphDataGenerator.generateNodes(mapMetaData);
            log.info("创建了 {} 个地图节点", nodes.size());

            // 创建边
            edges = graphDataGenerator.generateEdges(mapMetaData,nodes);
            log.info("创建了 {} 条地图边", edges.size());

            // 构建图
            agvGraph.buildGraph(nodes, edges);

            mapDetailInfo = new MapDetailInfo(mapMetaData, mapConfig.getName(),
                    agvGraph.getAllNodes().stream().toList(),
                    agvGraph.getAllEdges().stream().toList());
            // 打印图结构信息
            printMapInfo();

        } catch (Exception e) {
            log.error("地图初始化失败", e);
        }
    }

    /**
     * 读取PGM文件尺寸信息
     */
    private int[] readPgmFileDimensions(String pgmFilePath) {
        try {
            return pgmFileReader.readPgmDimensions(pgmFilePath);
        } catch (IOException e) {
            log.error("读取PGM文件失败: {}", pgmFilePath, e);
            // 提供默认值或抛出运行时异常
            throw new RuntimeException("无法读取PGM文件尺寸: " + pgmFilePath, e);
        }
    }

    /**
     * 打印地图信息
     */
    private void printMapInfo() {
        log.info("========== 地图信息 ==========");
        log.info("地图名称: {}", mapConfig.getName());
        log.info("图像文件: {}", mapConfig.getImageFile());
        log.info("节点总数: {}", agvGraph.getAllNodes().size());
        log.info("边总数: {}", agvGraph.getAllEdges().size());
        log.info("==============================");
    }

    /**
     * 验证图连通性
     */
    public boolean validateGraphConnectivity() {
        List<Node> nodes = new ArrayList<>(agvGraph.getAllNodes());

        if (nodes.isEmpty()) {
            log.warn("图中没有节点");
            return false;
        }

        // 检查所有节点是否都连通
        String firstNodeId = nodes.get(0).getId();
        int connectedCount = 0;

        for (Node node : nodes) {
            boolean reachable = agvGraph.isReachable(firstNodeId, node.getId());
            if (reachable) {
                connectedCount++;
            }
        }

        boolean fullyConnected = connectedCount == nodes.size();

        log.info("图连通性检查: {}/{} 个节点连通", connectedCount, nodes.size());

        if (!fullyConnected) {
            log.warn("图不是完全连通的，可能存在孤立的节点或区域");
        }

        return fullyConnected;
    }

    /**
     * 获取地图可视化数据
     */
    public MapDetailInfo getMapVisualizationData() {
        return mapDetailInfo;
    }
}
