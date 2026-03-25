package com.jizhi.vda5050.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

/**
 * VDA5050 订单消息定义 (基于 VDA5050 2.0.0 版本)
 * 用于向 AGV 发送任务订单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Vda5050OrderMessage {

    /**
     * 订单消息的头部信息
     */
    private Vda5050Header header;

    /**
     * 订单信息
     */
    @JsonProperty("orderInformation")
    private OrderInformation orderInformation;

    /**
     * 节点位置列表
     */
    private List<NodePosition> nodePositions;

    /**
     * 边列表
     */
    private List<Edge> edges;

    /**
     * 行动列表
     */
    private List<Action> actions;

    /**
     * 附加信息
     */
    private Map<String, Object> additionalInformation;

    // ================ 内部类定义 ================

    /**
     * 订单信息 (OrderInformation)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OrderInformation {
        /**
         * 订单ID (必需)
         */
        @JsonProperty("orderId")
        private String orderId;

        /**
         * 订单更新ID (必需，从1开始，每次更新递增)
         */
        @JsonProperty("orderUpdateId")
        @Builder.Default
        private Integer orderUpdateId = 1;

        /**
         * 区域集ID (可选)
         */
        @JsonProperty("zoneSetId")
        private String zoneSetId;

        /**
         * 是否为释放订单 (可选)
         */
        @JsonProperty("released")
        @Builder.Default
        private Boolean released = true;

        /**
         * 订单优先级 (可选，1-100，值越小优先级越高)
         */
        @JsonProperty("orderPriority")
        @Builder.Default
        private Integer orderPriority = 50;

        /**
         * 订单模式 (可选)
         * 可选值: AUTOMATIC, SEMIAUTOMATIC, MANUAL, SERVICE
         */
        @JsonProperty("orderMode")
        @Builder.Default
        private String orderMode = "AUTOMATIC";

        /**
         * 订单组ID (可选，用于分组相关订单)
         */
        @JsonProperty("orderGroupId")
        private String orderGroupId;
    }

    /**
     * 节点位置 (NodePosition)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NodePosition {
        /**
         * 节点ID (必需)
         */
        @JsonProperty("nodeId")
        private String nodeId;

        /**
         * 序列ID (必需，从1开始递增)
         */
        @JsonProperty("sequenceId")
        private Integer sequenceId;

        /**
         * 是否为释放节点 (可选)
         */
        @JsonProperty("released")
        @Builder.Default
        private Boolean released = true;

        /**
         * 节点描述 (可选)
         */
        @JsonProperty("nodeDescription")
        private NodeDescription nodeDescription;

        /**
         * 节点位置 (可选)
         */
        @JsonProperty("nodePosition")
        private NodePositionDetail nodePosition;

        /**
         * 附加信息 (可选)
         */
        @JsonProperty("additionalInformation")
        private Map<String, Object> additionalInformation;

        /**
         * 允许的车辆方向 (可选)
         * 可选值: FORWARD, BACKWARD, BIDIRECTIONAL, UNDEFINED
         */
        @JsonProperty("allowedVehicles")
        private List<String> allowedVehicles;

        /**
         * 允许的AGV类型 (可选)
         */
        @JsonProperty("allowedAGVTypes")
        private List<String> allowedAGVTypes;

        /**
         * 节点动作列表 (可选)
         */
        @JsonProperty("actions")
        private List<Action> actions;

        /**
         * 节点描述信息
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class NodeDescription {
            /**
             * 节点名称
             */
            @JsonProperty("name")
            private String name;

            /**
             * 节点类型
             * 可选值: STANDARD, SOURCE, SINK, HIDDEN
             */
            @JsonProperty("type")
            @Builder.Default
            private String type = "STANDARD";

            /**
             * 节点坐标X
             */
            @JsonProperty("x")
            private Double x;

            /**
             * 节点坐标Y
             */
            @JsonProperty("y")
            private Double y;

            /**
             * 节点方向 (弧度)
             */
            @JsonProperty("theta")
            private Double theta;

            /**
             * 地图ID
             */
            @JsonProperty("mapId")
            private String mapId;

            /**
             * 地图描述
             */
            @JsonProperty("mapDescription")
            private String mapDescription;

            /**
             * 节点半径 (米)
             */
            @JsonProperty("radius")
            private Double radius;
        }

        /**
         * 节点位置详情
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class NodePositionDetail {
            /**
             * 位置初始化状态
             */
            @JsonProperty("positionInitialized")
            private Boolean positionInitialized;

            /**
             * X坐标 (米)
             */
            @JsonProperty("x")
            private Double x;

            /**
             * Y坐标 (米)
             */
            @JsonProperty("y")
            private Double y;

            /**
             * 方向 (弧度)
             */
            @JsonProperty("theta")
            private Double theta;

            /**
             * 地图ID
             */
            @JsonProperty("mapId")
            private String mapId;

            /**
             * 地图描述
             */
            @JsonProperty("mapDescription")
            private String mapDescription;

            /**
             * 位置精度 (米)
             */
            @JsonProperty("accuracy")
            private Double accuracy;

            /**
             * 位置匹配度 (0-1)
             */
            @JsonProperty("matchQuality")
            private Double matchQuality;
        }
    }

    /**
     * 边 (Edge)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Edge {
        /**
         * 边ID (必需)
         */
        @JsonProperty("edgeId")
        private String edgeId;

        /**
         * 序列ID (必需，从1开始递增)
         */
        @JsonProperty("sequenceId")
        private Integer sequenceId;

        /**
         * 是否为释放边 (可选)
         */
        @JsonProperty("released")
        @Builder.Default
        private Boolean released = true;

        /**
         * 起始节点ID (必需)
         */
        @JsonProperty("startNodeId")
        private String startNodeId;

        /**
         * 结束节点ID (必需)
         */
        @JsonProperty("endNodeId")
        private String endNodeId;

        /**
         * 边描述 (可选)
         */
        @JsonProperty("edgeDescription")
        private EdgeDescription edgeDescription;

        /**
         * 最大速度 (米/秒) (可选)
         */
        @JsonProperty("maxSpeed")
        private Double maxSpeed;

        /**
         * 最小速度 (米/秒) (可选)
         */
        @JsonProperty("minSpeed")
        private Double minSpeed;

        /**
         * 方向 (可选)
         * 可选值: FORWARD, BACKWARD, BIDIRECTIONAL
         */
        @JsonProperty("direction")
        private String direction;

        /**
         * 边的行动列表 (可选)
         */
        @JsonProperty("actions")
        private List<Action> actions;

        /**
         * 附加信息 (可选)
         */
        @JsonProperty("additionalInformation")
        private Map<String, Object> additionalInformation;

        /**
         * 边描述信息
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class EdgeDescription {
            /**
             * 边类型
             * 可选值: STRAIGHT, ARC, CLOTHOID, CUSTOM
             */
            @JsonProperty("type")
            private String type;

            /**
             * 边长度 (米)
             */
            @JsonProperty("length")
            private Double length;

            /**
             * 曲率半径 (米，正值表示左转，负值表示右转)
             */
            @JsonProperty("radius")
            private Double radius;

            /**
             * 弧的角度 (弧度)
             */
            @JsonProperty("angle")
            private Double angle;

            /**
             * 控制点 (用于自定义曲线)
             */
            @JsonProperty("controlPoints")
            private List<ControlPoint> controlPoints;

            /**
             * 轨迹ID
             */
            @JsonProperty("trajectoryId")
            private String trajectoryId;

            /**
             * 轨迹描述
             */
            @JsonProperty("trajectoryDescription")
            private TrajectoryDescription trajectoryDescription;

        }

        /**
         * 控制点 (用于自定义曲线)
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ControlPoint {
            @JsonProperty("x")
            private Double x;

            @JsonProperty("y")
            private Double y;
        }

        /**
         * 轨迹描述
         */
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TrajectoryDescription {
            @JsonProperty("name")
            private String name;

            @JsonProperty("type")
            private String type;

            @JsonProperty("points")
            private List<TrajectoryPoint> points;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TrajectoryPoint {
            @JsonProperty("x")
            private Double x;

            @JsonProperty("y")
            private Double y;

            @JsonProperty("theta")
            private Double theta;

            @JsonProperty("speed")
            private Double speed;
        }
    }

    /**
     * 行动 (Action)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Action {
        /**
         * 行动ID (必需，在订单内唯一)
         */
        @JsonProperty("actionId")
        private String actionId;

        /**
         * 行动类型 (必需)
         * 常用值: LOAD, UNLOAD, PICK, PLACE, CHARGE, WAIT, CUSTOM
         */
        @JsonProperty("actionType")
        private String actionType;

        /**
         * 行动优先级 (可选，1-100，值越小优先级越高)
         */
        @JsonProperty("actionPriority")
        @Builder.Default
        private Integer actionPriority = 50;

        /**
         * 阻塞类型 (可选)
         * 0: 非阻塞，AGV可继续执行后续行动
         * 1: 阻塞，必须完成此行动才能继续
         */
        @JsonProperty("blockingType")
        @Builder.Default
        private Long blockingType = 1L;

        /**
         * 行动描述 (可选)
         */
        @JsonProperty("actionDescription")
        private String actionDescription;

        /**
         * 行动参数 (可选)
         */
        @JsonProperty("actionParameters")
        private List<Vda5050ActionParameter> actionParameters;

        /**
         * 附加信息 (可选)
         */
        @JsonProperty("additionalInformation")
        private Map<String, Object> additionalInformation;

        /**
         * 行动状态 (可选，由AGV返回)
         */
        @JsonProperty("actionStatus")
        private String actionStatus;

        /**
         * 结果描述 (可选，由AGV返回)
         */
        @JsonProperty("resultDescription")
        private String resultDescription;

        /**
         * 失败类型 (可选，由AGV返回)
         */
        @JsonProperty("failureType")
        private String failureType;
    }



    // ================ 构建方法 ================

//    /**
//     * 创建简单的点到点运输订单
//     */
//    public static Vda5050OrderMessage createSimpleTransportOrder(
//            String orderId,
//            String agvId,
//            String startNodeId,
//            String endNodeId,
//            String manufacturer) {
//
//        // 创建节点位置
//        List<NodePosition> nodePositions = Arrays.asList(
//                NodePosition.builder()
//                        .nodeId(startNodeId)
//                        .sequenceId(1)
//                        .released(true)
//                        .build(),
//                NodePosition.builder()
//                        .nodeId(endNodeId)
//                        .sequenceId(2)
//                        .released(true)
//                        .build()
//        );
//
//        // 创建行动
//        List<Action> actions = Arrays.asList(
//                Action.builder()
//                        .actionId(orderId + "_LOAD")
//                        .actionType("LOAD")
//                        .actionDescription("装载物料")
//                        .actionPriority(1)
//                        .blockingType(1L)
//                        .actionParameters(createLoadParameters())
//                        .build(),
//                Action.builder()
//                        .actionId(orderId + "_UNLOAD")
//                        .actionType("UNLOAD")
//                        .actionDescription("卸载物料")
//                        .actionPriority(1)
//                        .blockingType(1L)
//                        .actionParameters(createUnloadParameters())
//                        .build()
//        );
//
//        return Vda5050OrderMessage.builder()
//                .header(Vda5050Header.builder()
//                        .headerId(System.currentTimeMillis())
//                        .timestamp(Instant.now())
//                        .version("2.0.0")
//                        .manufacturer(manufacturer)
//                        .serialNumber("SYS-" + orderId)
//                        .build())
//                .orderInformation(OrderInformation.builder()
//                        .orderId(orderId)
//                        .orderUpdateId(1)
//                        .released(true)
//                        .orderPriority(50)
//                        .build())
//                .nodePositions(nodePositions)
//                .actions(actions)
//                .build();
//    }

    /**
     * 创建带路径的复杂订单
     */
//    public static Vda5050OrderMessage createPathOrder(
//            String orderId,
//            String agvId,
//            List<String> nodeSequence,
//            List<String> edgeSequence,
//            Map<String, NodePosition.NodeDescription> nodeDescriptions,
//            Map<String, Edge.EdgeDescription> edgeDescriptions,
//            List<Action> customActions,
//            String manufacturer) {
//
//        // 创建节点位置
//        List<NodePosition> nodePositions = new ArrayList<>();
//        for (int i = 0; i < nodeSequence.size(); i++) {
//            String nodeId = nodeSequence.get(i);
//            NodePosition.NodeDescription desc = nodeDescriptions != null ?
//                    nodeDescriptions.get(nodeId) : null;
//
//            NodePosition nodePosition = NodePosition.builder()
//                    .nodeId(nodeId)
//                    .sequenceId(i + 1)
//                    .released(true)
//                    .nodeDescription(desc)
//                    .build();
//
//            nodePositions.add(nodePosition);
//        }
//
//        // 创建边
//        List<Edge> edges = new ArrayList<>();
//        if (edgeSequence != null) {
//            for (int i = 0; i < edgeSequence.size(); i++) {
//                String edgeId = edgeSequence.get(i);
//                Edge.EdgeDescription desc = edgeDescriptions != null ?
//                        edgeDescriptions.get(edgeId) : null;
//
//                Edge edge = Edge.builder()
//                        .edgeId(edgeId)
//                        .sequenceId(i + 1)
//                        .released(true)
//                        .edgeDescription(desc)
//                        .build();
//
//                edges.add(edge);
//            }
//        }
//
//        return Vda5050OrderMessage.builder()
//                .header(Vda5050Header.builder()
//                        .headerId(System.currentTimeMillis())
//                        .timestamp(Instant.now())
//                        .version("2.0.0")
//                        .manufacturer(manufacturer)
//                        .serialNumber("SYS-" + orderId)
//                        .build())
//                .orderInformation(OrderInformation.builder()
//                        .orderId(orderId)
//                        .orderUpdateId(1)
//                        .released(true)
//                        .build())
//                .nodePositions(nodePositions)
//                .edges(edges)
//                .actions(customActions)
//                .build();
//    }

    /**
     * 创建充电订单
     */
    public static Vda5050OrderMessage createChargeOrder(
            String orderId,
            String agvId,
            String chargeNodeId,
            Double targetBatteryLevel,
            String manufacturer) {

        List<NodePosition> nodePositions = Arrays.asList(
                NodePosition.builder()
                        .nodeId(chargeNodeId)
                        .sequenceId(1)
                        .released(true)
                        .build()
        );

        List<Action> actions = Arrays.asList(
                Action.builder()
                        .actionId(orderId + "_CHARGE")
                        .actionType("CHARGE")
                        .actionDescription("充电")
                        .actionPriority(1)
                        .blockingType(1L)
                        .actionParameters(createChargeParameters(targetBatteryLevel))
                        .build()
        );

        return Vda5050OrderMessage.builder()
                .header(Vda5050Header.builder()
                        .headerId(System.currentTimeMillis())
                        .timestamp(Instant.now())
                        .version("2.0.0")
                        .manufacturer(manufacturer)
                        .serialNumber("SYS-" + orderId)
                        .build())
                .orderInformation(OrderInformation.builder()
                        .orderId(orderId)
                        .orderUpdateId(1)
                        .released(true)
                        .orderPriority(10) // 充电任务优先级较高
                        .build())
                .nodePositions(nodePositions)
                .actions(actions)
                .build();
    }

    /**
     * 创建等待订单
     */
    public static Vda5050OrderMessage createWaitOrder(
            String orderId,
            String agvId,
            String waitNodeId,
            Long waitDuration, // 毫秒
            String manufacturer) {

        List<NodePosition> nodePositions = Arrays.asList(
                NodePosition.builder()
                        .nodeId(waitNodeId)
                        .sequenceId(1)
                        .released(true)
                        .build()
        );

        List<Action> actions = Arrays.asList(
                Action.builder()
                        .actionId(orderId + "_WAIT")
                        .actionType("WAIT")
                        .actionDescription("等待")
                        .actionPriority(1)
                        .blockingType(1L)
                        .actionParameters(createWaitParameters(waitDuration))
                        .build()
        );

        return Vda5050OrderMessage.builder()
                .header(Vda5050Header.builder()
                        .headerId(System.currentTimeMillis())
                        .timestamp(Instant.now())
                        .version("2.0.0")
                        .manufacturer(manufacturer)
                        .serialNumber("SYS-" + orderId)
                        .build())
                .orderInformation(OrderInformation.builder()
                        .orderId(orderId)
                        .orderUpdateId(1)
                        .released(true)
                        .build())
                .nodePositions(nodePositions)
                .actions(actions)
                .build();
    }

    // ================ 参数创建方法 ================

    private static List<Vda5050ActionParameter> createLoadParameters() {
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

    private static List<Vda5050ActionParameter> createUnloadParameters() {
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

    private static List<Vda5050ActionParameter> createChargeParameters(Double targetBatteryLevel) {
        List<Vda5050ActionParameter> params = new ArrayList<>();
        params.add(Vda5050ActionParameter.builder()
                .key("chargeType")
                .value("AUTO")
                .valueType("STRING")
                .description("充电类型")
                .build());

        if (targetBatteryLevel != null) {
            params.add(Vda5050ActionParameter.builder()
                    .key("targetBatteryLevel")
                    .value(targetBatteryLevel)
                    .valueType("FLOAT")
                    .description("目标电量")
                    .build());
        }

        return params;
    }

    private static List<Vda5050ActionParameter> createWaitParameters(Long waitDuration) {
        List<Vda5050ActionParameter> params = new ArrayList<>();

        if (waitDuration != null) {
            params.add(Vda5050ActionParameter.builder()
                    .key("duration")
                    .value(waitDuration)
                    .valueType("INTEGER")
                    .description("等待时长(毫秒)")
                    .build());
        }

        return params;
    }

    // ================ 验证方法 ================

    /**
     * 验证订单是否有效
     */
    public Map<String, Object> validate() {
        Map<String, Object> validation = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 验证头部
        if (header == null) {
            errors.add("缺少头部信息");
        } else {
            if (header.getHeaderId() == null) {
                warnings.add("头部缺少headerId");
            }
            if (header.getTimestamp() == null) {
                header.setTimestamp(Instant.now());
                warnings.add("头部时间戳为空，已设置为当前时间");
            }
            if (header.getVersion() == null) {
                header.setVersion("2.0.0");
                warnings.add("头部版本为空，已设置为2.0.0");
            }
        }

        // 验证订单信息
        if (orderInformation == null) {
            errors.add("缺少订单信息");
        } else {
            if (orderInformation.getOrderId() == null || orderInformation.getOrderId().isEmpty()) {
                errors.add("订单ID为空");
            }
            if (orderInformation.getOrderUpdateId() == null) {
                orderInformation.setOrderUpdateId(1);
                warnings.add("订单更新ID为空，已设置为1");
            }
        }

        // 验证节点
        if (nodePositions == null || nodePositions.isEmpty()) {
            errors.add("缺少节点信息");
        } else {
            Set<Integer> sequenceIds = new HashSet<>();
            for (NodePosition node : nodePositions) {
                if (node.getNodeId() == null || node.getNodeId().isEmpty()) {
                    errors.add("节点缺少nodeId");
                }
                if (node.getSequenceId() == null) {
                    errors.add("节点缺少sequenceId");
                } else if (sequenceIds.contains(node.getSequenceId())) {
                    errors.add("节点序列号重复: " + node.getSequenceId());
                } else {
                    sequenceIds.add(node.getSequenceId());
                }
            }
        }

        // 验证边（如果有）
        if (edges != null) {
            Set<Integer> edgeSequenceIds = new HashSet<>();
            for (Edge edge : edges) {
                if (edge.getEdgeId() == null || edge.getEdgeId().isEmpty()) {
                    warnings.add("边缺少edgeId");
                }
                if (edge.getSequenceId() == null) {
                    errors.add("边缺少sequenceId");
                } else if (edgeSequenceIds.contains(edge.getSequenceId())) {
                    errors.add("边序列号重复: " + edge.getSequenceId());
                } else {
                    edgeSequenceIds.add(edge.getSequenceId());
                }
            }
        }

        // 验证行动（如果有）
        if (actions != null) {
            Set<String> actionIds = new HashSet<>();
            for (Action action : actions) {
                if (action.getActionId() == null || action.getActionId().isEmpty()) {
                    errors.add("行动缺少actionId");
                } else if (actionIds.contains(action.getActionId())) {
                    errors.add("行动ID重复: " + action.getActionId());
                } else {
                    actionIds.add(action.getActionId());
                }
                if (action.getActionType() == null || action.getActionType().isEmpty()) {
                    errors.add("行动缺少actionType");
                }
            }
        }

        validation.put("isValid", errors.isEmpty());
        validation.put("errors", errors);
        validation.put("warnings", warnings);
        validation.put("nodeCount", nodePositions != null ? nodePositions.size() : 0);
        validation.put("edgeCount", edges != null ? edges.size() : 0);
        validation.put("actionCount", actions != null ? actions.size() : 0);

        return validation;
    }

    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("转换为JSON失败", e);
        }
    }

    /**
     * 从JSON字符串创建订单
     */
    public static Vda5050OrderMessage fromJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(json, Vda5050OrderMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("从JSON解析订单失败", e);
        }
    }

    /**
     * 获取订单摘要信息
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("orderId", orderInformation != null ? orderInformation.getOrderId() : null);
        summary.put("orderUpdateId", orderInformation != null ? orderInformation.getOrderUpdateId() : null);
        summary.put("nodeCount", nodePositions != null ? nodePositions.size() : 0);
        summary.put("edgeCount", edges != null ? edges.size() : 0);
        summary.put("actionCount", actions != null ? actions.size() : 0);
        summary.put("version", header != null ? header.getVersion() : null);
        summary.put("timestamp", header != null ? header.getTimestamp() : null);

        // 节点预览
        if (nodePositions != null && !nodePositions.isEmpty()) {
            List<String> nodePreview = new ArrayList<>();
            int previewSize = Math.min(3, nodePositions.size());
            for (int i = 0; i < previewSize; i++) {
                nodePreview.add(nodePositions.get(i).getNodeId());
            }
            if (nodePositions.size() > 3) {
                nodePreview.add("...");
                nodePreview.add(nodePositions.get(nodePositions.size() - 1).getNodeId());
            }
            summary.put("nodePreview", nodePreview);
        }

        return summary;
    }
}
