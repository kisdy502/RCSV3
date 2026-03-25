package com.sdt.agv_simulator.service;

import com.jizhi.data.CommonResult;
import com.jizhi.vda5050.domain.*;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MapService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${agv.simulator.map.server-url}")
    private String serverUrl;

    private MapDetailInfo mapDetailInfo;

    @Getter
    private final Map<String, Node> nodeCache = new HashMap<>();
    private final Map<String, Edge> edgeCache = new HashMap<>();
    private final List<String> chargeNodes = new ArrayList<>();

    /**
     * 从调度系统加载地图信息
     */
    @PostConstruct
    public void loadMapFromServer() {
        try {
            log.info("正在从调度系统加载地图信息...");

            // 调用接口获取CommonResult<MapDetailInfo>
            String mapInfoUrl = serverUrl + "/map/info";

            // 使用ParameterizedTypeReference来处理泛型响应
            ResponseEntity<CommonResult<MapDetailInfo>> response = restTemplate.exchange(
                    mapInfoUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<CommonResult<MapDetailInfo>>() {
                    }
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                CommonResult<MapDetailInfo> commonResult = response.getBody();

                // 检查返回码是否为成功
                if (commonResult.isSuccess() && commonResult.getData() != null) {
                    mapDetailInfo = commonResult.getData();

                    // 初始化缓存
                    initializeCaches();

                    log.info("地图加载完成: {} 个节点，{} 条边，{} 个充电站",
                            nodeCache.size(), edgeCache.size(), chargeNodes.size());
                    log.info("地图名称: {}", mapDetailInfo.getMapName());
                } else {
                    log.error("加载地图信息失败: code={}, msg={}",
                            commonResult.getCode(), commonResult.getMessage());
                }
            } else {
                log.error("HTTP请求失败: status={}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("加载地图信息失败", e);
        }
    }

    /**
     * 初始化缓存数据
     */
    private void initializeCaches() {
        if (mapDetailInfo == null) {
            log.warn("地图详情信息为空，无法初始化缓存");
            return;
        }

        // 清空现有缓存
        nodeCache.clear();
        edgeCache.clear();
        chargeNodes.clear();

        // 缓存节点
        if (mapDetailInfo.getNodes() != null) {
            for (Node node : mapDetailInfo.getNodes()) {
                nodeCache.put(node.getId(), node);

                // 识别充电站
                if (node.getType() == Node.NodeType.STATION &&
                        node.getName() != null && node.getName().contains("充电")) {
                    chargeNodes.add(node.getId());
                }
            }
        }

        // 缓存边
        if (mapDetailInfo.getEdges() != null) {
            for (Edge edge : mapDetailInfo.getEdges()) {
                edgeCache.put(edge.getId(), edge);
            }
        }
    }


    /**
     * 获取完整的地图详情信息
     */
    public MapDetailInfo getMapDetailInfo() {
        return mapDetailInfo;
    }

    /**
     * 获取地图基础信息
     */
    public MapMetaData getMapBaseInfo() {
        return mapDetailInfo != null ? mapDetailInfo.getMapInfo() : null;
    }

    /**
     * 获取节点信息
     */
    public Node getNode(String nodeId) {
        return nodeCache.get(nodeId);
    }

    /**
     * 获取所有节点
     */
    public List<Node> getAllNodes() {
        return mapDetailInfo != null ? mapDetailInfo.getNodes() : new ArrayList<>();
    }


    /**
     * 获取边信息
     */
    public Edge getEdge(String edgeId) {
        return edgeCache.get(edgeId);
    }

    /**
     * 获取所有边
     */
    public List<Edge> getAllEdges() {
        return mapDetailInfo != null ? mapDetailInfo.getEdges() : new ArrayList<>();
    }

    /**
     * 查找路径（调用调度系统API）
     */
    public PathResult findPath(String agvId, String startNodeId, String endNodeId) {
        try {
            // 构建URL，确保URL编码
            String encodedStartNode = URLEncoder.encode(startNodeId, StandardCharsets.UTF_8.toString());
            String encodedEndNode = URLEncoder.encode(endNodeId, StandardCharsets.UTF_8.toString());
            String encodedAgvId = URLEncoder.encode(agvId, StandardCharsets.UTF_8.toString());

            String pathUrl = String.format("%s/api/dispatch/path/plan/generate?agvId=%s&startNode=%s&endNode=%s" +
                            "&algorithm=astar",
                    serverUrl, encodedAgvId, encodedStartNode, encodedEndNode);

            log.info("调用路径规划API: {}", pathUrl);

            // 使用ParameterizedTypeReference来处理泛型响应
            ResponseEntity<CommonResult<PathResult>> response = restTemplate.exchange(
                    pathUrl,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<CommonResult<PathResult>>() {
                    }
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                CommonResult<PathResult> commonResult = response.getBody();

                if (commonResult != null) {
                    if (commonResult.isSuccess()) {
                        PathResult pathResult = commonResult.getData();
                        if (pathResult != null) {
                            log.info("路径规划成功: {} -> {}，距离: {}，节点数: {}",
                                    startNodeId, endNodeId,
                                    pathResult.getTotalDistance(),
                                    pathResult.getNodeSequence().size());
                            return pathResult;
                        } else {
                            log.warn("路径规划API返回数据为空");
                        }
                    } else {
                        // 如果是参数验证失败，可能有特殊含义
                        if (commonResult.getCode() == 404L) { // VALIDATE_FAILED
                            log.warn("路径参数验证失败: {} -> {}", startNodeId, endNodeId);
                        } else if (commonResult.getCode() == 2L) { // EMPTY
                            log.warn("未找到有效路径: {} -> {}", startNodeId, endNodeId);
                        } else {
                            log.warn("路径规划API返回失败: code={}, msg={}",
                                    commonResult.getCode(), commonResult.getMessage());
                        }
                    }
                } else {
                    log.warn("路径规划API返回空响应");
                }
            } else {
                log.warn("HTTP请求失败: status={}, body={}",
                        response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.warn("调用路径规划API失败: {} -> {}，异常: {}",
                    startNodeId, endNodeId, e.getMessage());
        }

        // 默认路径（直接连接）
        return null;
    }

    /**
     * 获取最近的充电站
     */
    public String getNearestChargeNode(String currentNodeId) {
        if (chargeNodes.isEmpty()) {
            return "ST006"; // 默认充电站
        }
        return chargeNodes.get(0);
    }

    /**
     * 检查节点是否可达
     */
    public boolean isReachable(String startNodeId, String endNodeId) {
        for (Edge edge : edgeCache.values()) {
            if ((edge.getSourceId().equals(startNodeId) && edge.getTargetId().equals(endNodeId)) ||
                    (edge.getSourceId().equals(endNodeId) && edge.getTargetId().equals(startNodeId))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取地图可视化数据
     */
    public MapDetailInfo getMapVisualization() {
        return mapDetailInfo;
    }

    /**
     * 根据类型获取节点
     */
    public List<Node> getNodesByType(Node.NodeType type) {
        if (mapDetailInfo == null || mapDetailInfo.getNodes() == null) {
            return new ArrayList<>();
        }

        return mapDetailInfo.getNodes().stream()
                .filter(node -> node.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * 获取连接到指定节点的所有边
     */
    public List<Edge> getConnectedEdges(String nodeId) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edgeCache.values()) {
            if (edge.getSourceId().equals(nodeId) || edge.getTargetId().equals(nodeId)) {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * 刷新地图数据
     */
    public void refreshMapData() {
        log.info("刷新地图数据...");
        loadMapFromServer();
    }

    /**
     * 检查地图数据是否已加载
     */
    public boolean isMapLoaded() {
        return mapDetailInfo != null &&
                mapDetailInfo.getNodes() != null &&
                mapDetailInfo.getEdges() != null;
    }

    /**
     * 获取地图统计信息
     */
    public Map<String, Object> getMapStatistics() {
        Map<String, Object> stats = new HashMap<>();

        if (mapDetailInfo != null) {
            stats.put("totalNodes", nodeCache.size());
            stats.put("totalEdges", edgeCache.size());
            stats.put("chargeStations", chargeNodes.size());
            stats.put("mapName", mapDetailInfo.getMapInfo() != null ?
                    mapDetailInfo.getMapName() : "Unknown");
        }

        return stats;
    }
}