package com.sdt.agv_dispatcher.service;

import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.domain.PathResult;
import com.jizhi.vda5050.message.Vda5050ActionParameter;
import com.jizhi.vda5050.message.Vda5050Header;
import com.jizhi.vda5050.message.Vda5050OrderMessage;
import com.sdt.agv_dispatcher.graph.AGVGraph;
import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class Vda5050OrderConverter {

    @Autowired
    private AGVGraph agvGraph;

    @Autowired
    private MapInitializationService mapInitializationService;

    /**
     * 核心方法：将路径结果转换为VDA5050订单
     */
    public Vda5050OrderMessage convertPathToOrder(PathResult path, String taskId, AgvStatus agvStatus) {
        // 使用Builder模式创建订单
        return Vda5050OrderMessage.builder()
                .header(buildHeader(taskId, agvStatus))
                .orderInformation(buildOrderInformation(taskId))
                .nodePositions(buildNodePositions(path))
                .edges(buildEdges(path))
                .actions(buildActions(taskId, path))
                .additionalInformation(buildAdditionalInfo(path))
                .build();
    }

    /**
     * 构建头部信息
     */
    private Vda5050Header buildHeader(String taskId, AgvStatus agvStatus) {
        return Vda5050Header.builder()
                .headerId(System.currentTimeMillis())
                .timestamp(Instant.now())
                .version(agvStatus.getVersion())
                .manufacturer(agvStatus.getManufacturer())
                .serialNumber(agvStatus.getSerialNumber())
                .build();
    }

    /**
     * 构建订单信息
     */
    private Vda5050OrderMessage.OrderInformation buildOrderInformation(String taskId) {
        return Vda5050OrderMessage.OrderInformation.builder()
                .orderId(taskId)
                .orderUpdateId(1)
                .released(true)
                .orderPriority(50)
                .orderMode("AUTOMATIC")
                .build();
    }

    /**
     * 构建节点位置列表
     */
    private List<Vda5050OrderMessage.NodePosition> buildNodePositions(PathResult path) {
        List<Vda5050OrderMessage.NodePosition> nodePositions = new ArrayList<>();

        if (path.getNodeSequence() != null) {
            for (int i = 0; i < path.getNodeSequence().size(); i++) {
                String nodeId = path.getNodeSequence().get(i);
                Node node = agvGraph.getNode(nodeId);

                // 构建节点描述
                Vda5050OrderMessage.NodePosition.NodeDescription nodeDescription = null;
                if (node != null) {
                    nodeDescription = Vda5050OrderMessage.NodePosition.NodeDescription.builder()
                            .name(node.getName())
                            .type(node.getType().name()) // 可以根据node.getType()映射
                            .x(node.getX())
                            .y(node.getY())
                            .theta(node.getTheta()) // 默认方向
                            .mapId(mapInitializationService.getMapConfig().getName())
                            .mapDescription(mapInitializationService.getMapConfig().getName())
                            .radius(0.5) // 节点半径
                            .build();
                }

                // 构建节点位置
                Vda5050OrderMessage.NodePosition nodePosition = Vda5050OrderMessage.NodePosition.builder()
                        .nodeId(nodeId)
                        .sequenceId(i + 1)
                        .released(true)
                        .nodeDescription(nodeDescription)
                        .build();

                nodePositions.add(nodePosition);
            }
        }

        return nodePositions;
    }

    /**
     * 构建边列表
     */
    private List<Vda5050OrderMessage.Edge> buildEdges(PathResult path) {
        List<Vda5050OrderMessage.Edge> edges = new ArrayList<>();

        if (path.getEdgeSequence() != null) {
            for (int i = 0; i < path.getEdgeSequence().size(); i++) {
                String edgeId = path.getEdgeSequence().get(i);
                Edge edgeData = agvGraph.getEdge(edgeId);

                // 构建边描述
                Vda5050OrderMessage.Edge.EdgeDescription edgeDescription = null;
                if (edgeData != null) {
                    edgeDescription = Vda5050OrderMessage.Edge.EdgeDescription.builder()
                            .type(mapEdgeType(edgeData.getType()))
                            .length(edgeData.getWeight())
                            .build();
                }

                // 构建边对象
                Vda5050OrderMessage.Edge edge = Vda5050OrderMessage.Edge.builder()
                        .edgeId(edgeId)
                        .sequenceId(i + 1)
                        .released(true)
                        .startNodeId(edgeData != null ? edgeData.getSourceId() : null)
                        .endNodeId(edgeData != null ? edgeData.getTargetId() : null)
                        .edgeDescription(edgeDescription)
                        .maxSpeed(edgeData != null ? edgeData.getMaxSpeed() : 1.0)
                        .minSpeed(0.5)
                        .direction(mapEdgeDirection(edgeData != null ? edgeData.getDirection() : null))
                        .build();

                edges.add(edge);
            }
        }

        return edges;
    }

    /**
     * 构建行动列表
     */
    private List<Vda5050OrderMessage.Action> buildActions(String taskId, PathResult path) {
        List<Vda5050OrderMessage.Action> actions = new ArrayList<>();

        // 装载行动
        actions.add(Vda5050OrderMessage.Action.builder()
                .actionId(taskId + "_LOAD")
                .actionType("LOAD")
                .actionPriority(1)
                .blockingType(1L)
                .actionDescription("装载物料")
                .actionParameters(buildLoadParameters())
                .build());

        // 如果路径有分段，可以为每个分段添加移动行动
        if (path.getSegments() != null && !path.getSegments().isEmpty()) {
            for (int i = 0; i < path.getSegments().size(); i++) {
                PathResult.PathSegment segment = path.getSegments().get(i);

                List<Vda5050ActionParameter> moveParams = Arrays.asList(
                        Vda5050ActionParameter.builder()
                                .key("fromNode")
                                .value(segment.getFromNodeId())
                                .valueType("STRING")
                                .build(),
                        Vda5050ActionParameter.builder()
                                .key("toNode")
                                .value(segment.getToNodeId())
                                .valueType("STRING")
                                .build(),
                        Vda5050ActionParameter.builder()
                                .key("distance")
                                .value(segment.getDistance())
                                .valueType("FLOAT")
                                .build()
                );

                actions.add(Vda5050OrderMessage.Action.builder()
                        .actionId(taskId + "_MOVE_" + (i + 1))
                        .actionType("MOVE")
                        .actionPriority(50)
                        .blockingType(0L) // 移动是非阻塞的
                        .actionDescription("移动到 " + segment.getToNodeId())
                        .actionParameters(moveParams)
                        .build());
            }
        }

        // 卸载行动
        actions.add(Vda5050OrderMessage.Action.builder()
                .actionId(taskId + "_UNLOAD")
                .actionType("UNLOAD")
                .actionPriority(1)
                .blockingType(1L)
                .actionDescription("卸载物料")
                .actionParameters(buildUnloadParameters())
                .build());

        return actions;
    }

    /**
     * 构建装载参数
     */
    private List<Vda5050ActionParameter> buildLoadParameters() {
        return Arrays.asList(
                Vda5050ActionParameter.builder()
                        .key("materialType")
                        .value("PALLET")
                        .valueType("STRING")
                        .description("物料类型")
                        .build(),
                Vda5050ActionParameter.builder()
                        .key("operation")
                        .value("PICK")
                        .valueType("STRING")
                        .description("操作类型")
                        .build(),
                Vda5050ActionParameter.builder()
                        .key("loadPosition")
                        .value("FRONT")
                        .valueType("STRING")
                        .description("装载位置")
                        .build()
        );
    }

    /**
     * 构建卸载参数
     */
    private List<Vda5050ActionParameter> buildUnloadParameters() {
        return Arrays.asList(
                Vda5050ActionParameter.builder()
                        .key("materialType")
                        .value("PALLET")
                        .valueType("STRING")
                        .description("物料类型")
                        .build(),
                Vda5050ActionParameter.builder()
                        .key("operation")
                        .value("PLACE")
                        .valueType("STRING")
                        .description("操作类型")
                        .build(),
                Vda5050ActionParameter.builder()
                        .key("unloadPosition")
                        .value("FRONT")
                        .valueType("STRING")
                        .description("卸载位置")
                        .build()
        );
    }

    /**
     * 构建附加信息
     */
    private Map<String, Object> buildAdditionalInfo(PathResult path) {
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("totalDistance", path.getTotalDistance());
        additionalInfo.put("estimatedTime", path.getEstimatedTime());
        additionalInfo.put("energyCost", path.getEnergyCost());
        additionalInfo.put("nodeCount", path.getNodeSequence().size());
        additionalInfo.put("edgeCount", path.getEdgeSequence() != null ?
                path.getEdgeSequence().size() : 0);
        additionalInfo.put("generationTime", LocalDateTime.now().toString());
        additionalInfo.put("sourceSystem", "AGV-Dispatch");
        return additionalInfo;
    }

    /**
     * 映射边类型
     */
    private String mapEdgeType(Edge.EdgeType edgeType) {
        if (edgeType == null) return "STRAIGHT";

        switch (edgeType) {
            case STRAIGHT:
                return "STRAIGHT";
            case CURVE:
                // 可以根据边的具体属性决定使用 ARC 还是 CUSTOM
                return "ARC"; // 或 "CUSTOM" 如果使用控制点
            case ELEVATION:
                return "CUSTOM"; // 坡道通常需要自定义描述
            default:
                return "STRAIGHT";
        }
    }

    /**
     * 映射边方向
     */
    private String mapEdgeDirection(Edge.EdgeDirection direction) {
        if (direction == null) return "BIDIRECTIONAL";

        switch (direction) {
            case UNIDIRECTIONAL:
                return "FORWARD";
            case BIDIRECTIONAL:
                return "BIDIRECTIONAL";
            default:
                return "BIDIRECTIONAL";
        }
    }

    // 构建复杂的曲线边描述
    private Vda5050OrderMessage.Edge.EdgeDescription buildCurveEdgeDescription(Edge edgeData) {
        if (edgeData.getType() == Edge.EdgeType.CURVE) {
            // 创建控制点列表（示例：贝塞尔曲线的控制点）
            List<Vda5050OrderMessage.Edge.ControlPoint> controlPoints = Arrays.asList(
                    Vda5050OrderMessage.Edge.ControlPoint.builder().x(0.0).y(0.0).build(),
                    Vda5050OrderMessage.Edge.ControlPoint.builder().x(2.0).y(1.0).build(),
                    Vda5050OrderMessage.Edge.ControlPoint.builder().x(4.0).y(0.0).build()
            );

            return Vda5050OrderMessage.Edge.EdgeDescription.builder()
                    .type("CUSTOM")
                    .length(edgeData.getWeight())
                    .controlPoints(controlPoints)
                    .build();
        }

        // 默认直线边
        return Vda5050OrderMessage.Edge.EdgeDescription.builder()
                .type("STRAIGHT")
                .length(edgeData.getWeight())
                .build();
    }

    /**
     * ================ 新增：便捷创建方法 ================
     */

    /**
     * 创建点到点运输订单（简版）
     */
//    public Vda5050OrderMessage createSimpleTransportOrder(String orderId, String agvId,
//                                                          String startNodeId, String endNodeId,String manufacturer) {
//        return Vda5050OrderMessage.createSimpleTransportOrder(
//                orderId, agvId, startNodeId, endNodeId, manufacturer);
//    }

    /**
     * 创建带详细路径的订单
     */
//    public Vda5050OrderMessage createDetailedPathOrder(String orderId, String agvId,
//                                                       List<String> nodeSequence,
//                                                       List<String> edgeSequence,
//                                                       List<Vda5050OrderMessage.Action> customActions,String
//                                                       manufacturer) {
//
//        // 构建节点描述映射
//        Map<String, Vda5050OrderMessage.NodePosition.NodeDescription> nodeDescriptions = new HashMap<>();
//        for (String nodeId : nodeSequence) {
//            Node node = agvGraph.getNode(nodeId);
//            if (node != null) {
//                nodeDescriptions.put(nodeId, Vda5050OrderMessage.NodePosition.NodeDescription.builder()
//                        .name(node.getName())
//                        .type(mapNodeType(node.getType()))
//                        .x(node.getX())
//                        .y(node.getY())
//                        .theta(0.0)
//                        .mapId("MAP001")
//                        .build());
//            }
//        }
//
//        // 构建边描述映射
//        Map<String, Vda5050OrderMessage.Edge.EdgeDescription> edgeDescriptions = new HashMap<>();
//        for (String edgeId : edgeSequence) {
//            Edge edge = agvGraph.getEdge(edgeId);
//            if (edge != null) {
//                edgeDescriptions.put(edgeId, Vda5050OrderMessage.Edge.EdgeDescription.builder()
//                        .type(mapEdgeType(edge.getType()))
//                        .length(edge.getWeight())
//                        .build());
//            }
//        }
//
//        return Vda5050OrderMessage.createPathOrder(
//                orderId,
//                agvId,
//                nodeSequence,
//                edgeSequence,
//                nodeDescriptions,
//                edgeDescriptions,
//                customActions,
//                manufacturer
//        );
//    }

    /**
     * 映射节点类型
     */
    private String mapNodeType(Node.NodeType nodeType) {
        if (nodeType == null) return "STANDARD";

        switch (nodeType) {
            case STATION:
                return "SOURCE"; // 站点作为起点/终点
            case PATH:
                return "STANDARD";
            case INTERSECTION:
                return "STANDARD";
            case CHARGE:
                return "SINK"; // 充电站作为终点
            case WAIT:
                return "HIDDEN";
            default:
                return "STANDARD";
        }
    }

    /**
     * 创建充电订单
     */
    public Vda5050OrderMessage createChargeOrder(String orderId, String agvId,
                                                 String chargeNodeId, Double targetBatteryLevel, String manufacturer) {
        return Vda5050OrderMessage.createChargeOrder(
                orderId, agvId, chargeNodeId, targetBatteryLevel, manufacturer);
    }

    /**
     * 创建等待订单
     */
    public Vda5050OrderMessage createWaitOrder(String orderId, String agvId,
                                               String waitNodeId, Long waitDurationMs, String manufacturer) {
        return Vda5050OrderMessage.createWaitOrder(
                orderId, agvId, waitNodeId, waitDurationMs, manufacturer);
    }

    /**
     * 从JSON字符串解析订单
     */
    public Vda5050OrderMessage parseFromJson(String json) {
        try {
            return Vda5050OrderMessage.fromJson(json);
        } catch (Exception e) {
            log.error("解析JSON订单失败", e);
            return null;
        }
    }

    /**
     * 验证订单有效性
     */
    public Map<String, Object> validateOrder(Vda5050OrderMessage order) {
        if (order == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("isValid", false);
            result.put("errors", Collections.singletonList("订单对象为空"));
            return result;
        }
        return order.validate();
    }

    /**
     * 获取订单摘要信息
     */
    public Map<String, Object> getOrderSummary(Vda5050OrderMessage order) {
        if (order == null) {
            return Collections.singletonMap("error", "订单对象为空");
        }
        return order.getSummary();
    }

    /**
     * 转换为JSON字符串
     */
    public String toJsonString(Vda5050OrderMessage order) {
        if (order == null) return null;
        return order.toJson();
    }

    /**
     * 批量转换路径为订单
     */
    public List<Vda5050OrderMessage> batchConvert(List<PathResult> paths, String prefix, AgvStatus agvStatus) {
        List<Vda5050OrderMessage> orders = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            PathResult path = paths.get(i);
            String orderId = prefix + "_" + (i + 1) + "_" + System.currentTimeMillis();
            Vda5050OrderMessage order = convertPathToOrder(path, orderId, agvStatus);
            orders.add(order);
        }
        return orders;
    }

    /**
     * 创建自定义行动
     */
    public Vda5050OrderMessage.Action createCustomAction(String actionId, String actionType,
                                                         String description, Map<String, Object> params) {
        List<Vda5050ActionParameter> actionParameters = new ArrayList<>();
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String valueType = determineValueType(entry.getValue());
                actionParameters.add(Vda5050ActionParameter.builder()
                        .key(entry.getKey())
                        .value(entry.getValue())
                        .valueType(valueType)
                        .build());
            }
        }

        return Vda5050OrderMessage.Action.builder()
                .actionId(actionId)
                .actionType(actionType)
                .actionDescription(description)
                .actionPriority(50)
                .blockingType(1L)
                .actionParameters(actionParameters)
                .build();
    }

    /**
     * 确定值的类型
     */
    private String determineValueType(Object value) {
        if (value == null) return "STRING";

        if (value instanceof String) return "STRING";
        if (value instanceof Integer || value instanceof Long) return "INTEGER";
        if (value instanceof Float || value instanceof Double) return "FLOAT";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof List) return "ARRAY";
        if (value instanceof Map) return "OBJECT";

        return "STRING";
    }
}