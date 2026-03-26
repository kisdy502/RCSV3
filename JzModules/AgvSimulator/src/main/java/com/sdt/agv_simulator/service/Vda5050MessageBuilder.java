package com.sdt.agv_simulator.service;

import com.jizhi.vda5050.agv.AgvActionState;
import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.agv.TaskStatus;
import com.jizhi.vda5050.domain.*;
import com.jizhi.vda5050.message.*;
import com.sdt.agv_simulator.domain.AgvPositionResult;
import com.sdt.agv_simulator.domain.AgvPositionState;
import com.sdt.agv_simulator.domain.NearestNodeResult;
import com.sdt.agv_simulator.utils.MathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.List;

@Slf4j
@Service
public class Vda5050MessageBuilder {

    @Autowired
    private MapService mapService;


    // 成员变量：记录自上一个节点后行驶的距离（米）
    private double distanceSinceLastNode = 0.0;

    // 每次里程计更新时调用的方法（由ROS2监听器触发）
    public void updateOdometry(double traveledDistanceDelta) {
        distanceSinceLastNode += traveledDistanceDelta;
    }

    // 当AGV到达节点时，由业务逻辑调用（例如检测到到达后）
    public void resetDistanceSinceLastNode() {
        log.debug("即将重置，已移动里程: {}", distanceSinceLastNode);
        distanceSinceLastNode = 0.0;
    }

    // 构建位置消息时调用
    private double calculateDistanceSinceLastNode() {
        return distanceSinceLastNode;
    }


    /**
     * 构建VDA5050连接消息 - 上线
     */
    public Vda5050ConnectionMessage buildOnlineMessage(AgvStatus agvStatus) {
        return buildConnectionMessage(agvStatus, ConnectionState.ONLINE, null);
    }

    /**
     * 构建VDA5050连接消息 - 下线
     */
    public Vda5050ConnectionMessage buildOfflineMessage(AgvStatus agvStatus) {
        return buildConnectionMessage(agvStatus, ConnectionState.OFFLINE, "正常下线");
    }

    /**
     * 构建VDA5050连接消息 - 带原因的下线
     */
    public Vda5050ConnectionMessage buildOfflineMessage(AgvStatus agvStatus, String reason) {
        return buildConnectionMessage(agvStatus, ConnectionState.OFFLINE, reason);
    }

    /**
     * 构建VDA5050连接消息
     */
    private Vda5050ConnectionMessage buildConnectionMessage(AgvStatus agvStatus, ConnectionState connectionState,
                                                            String reason) {
        Vda5050ConnectionMessage message = new Vda5050ConnectionMessage();

        // 构建标准Header
        Vda5050Header header = buildStandardHeader(agvStatus);
        message.setHeader(header);

        // 构建标准Payload
        ConnectionPayload payload = new ConnectionPayload();
        payload.setAgvId(agvStatus.getAgvId());
        payload.setVersion(agvStatus.getVersion());
        payload.setManufacturer(agvStatus.getManufacturer());
        payload.setSerialNumber(agvStatus.getSerialNumber());
        payload.setConnectionState(connectionState);
        payload.setTimestamp(Instant.now().toString());

        if (reason != null) {
            payload.setReason(reason);
        }

        // 设置标准协议特性
        payload.setSupportedProtocolFeatures(getSupportedProtocolFeatures());
        payload.setEnabledProtocolFeatures(getEnabledProtocolFeatures());

        message.setPayload(payload);

        return message;
    }


    /**
     * 构建VDA5050订单状态消息
     */
    public Vda5050OrderStateMessage buildOrderStateMessage(AgvStatus agvStatus, String state,
                                                           String resultDescription) {
        Vda5050OrderStateMessage message = new Vda5050OrderStateMessage();

        // 构建header
        Vda5050Header header = buildStandardHeader(agvStatus);
        message.setHeader(header);

        // 构建payload
        OrderStatePayload payload = new OrderStatePayload();
        payload.setAgvId(agvStatus.getAgvId());
        payload.setOrderId(agvStatus.getCurrentOrderId());
        payload.setOrderState(state);
        payload.setOrderUpdateId(agvStatus.getOrderUpdateId());
        payload.setResultDescription(resultDescription);

        message.setPayload(payload);
        return message;
    }

    /**
     * 构建VDA5050错误消息（标准化版本）
     */
    public Vda5050ErrorMessage buildErrorMessage(AgvStatus agvStatus, String errorCode,
                                                 String errorDescription, String errorLevel) {
        return buildErrorMessage(agvStatus, errorCode, errorDescription, errorLevel, new ArrayList<>());
    }

    /**
     * 构建带错误引用的VDA5050错误消息
     */
    public Vda5050ErrorMessage buildErrorMessage(AgvStatus agvStatus, String errorCode,
                                                 String errorDescription, String errorLevel,
                                                 List<ErrorReference> errorReferences) {
        Vda5050ErrorMessage message = new Vda5050ErrorMessage();

        // 构建header
        Vda5050Header header = buildStandardHeader(agvStatus);
        message.setHeader(header);

        ErrorPayload payload = new ErrorPayload();
        payload.setAgvId(agvStatus.getAgvId());
        payload.setErrorCode(errorCode);
        payload.setErrorDescription(errorDescription);
        payload.setTimestamp(Instant.now().toString());
        payload.setErrorLevel(errorLevel != null ? errorLevel : "ERROR");
        payload.setErrorReferences(errorReferences);

        message.setPayload(payload);
        return message;
    }

    /**
     * 构建VDA5050心跳消息
     */
    public Vda5050HeartbeatMessage buildHeartbeatMessage(AgvStatus agvStatus) {
        Vda5050HeartbeatMessage message = new Vda5050HeartbeatMessage();

        Vda5050Header header = buildStandardHeader(agvStatus);
        message.setHeader(header);

        // 构建Payload
        HeartbeatPayload payload = new HeartbeatPayload();
        payload.setAgvId(agvStatus.getAgvId());
        payload.setBatteryLevel(agvStatus.getBatteryLevel());
        payload.setTimestamp(Instant.now().toString());
        payload.setAgvState(agvStatus.getAgvState().toString());

        // 添加扩展信息
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("lastUpdateTime", agvStatus.getLastUpdateTime());
        additionalInfo.put("positionX", agvStatus.getCurrentPosition() != null ?
                agvStatus.getCurrentPosition().getX() : 0);
        additionalInfo.put("positionY", agvStatus.getCurrentPosition() != null ?
                agvStatus.getCurrentPosition().getY() : 0);
        additionalInfo.put("activeErrors", agvStatus.getActiveErrors());

        payload.setAdditionalInfo(additionalInfo);
        message.setPayload(payload);

        return message;
    }

    /**
     * 构建详细的位置更新消息
     */
    public Vda5050PositionUpdateMessage buildPositionUpdateMessage(AgvStatus agvStatus) {
        Vda5050PositionUpdateMessage message = new Vda5050PositionUpdateMessage();

        Vda5050Header header = buildStandardHeader(agvStatus);
        message.setHeader(header);

        // 构建position信息
        Vda5050PositionUpdateMessage.PositionInfo position = new Vda5050PositionUpdateMessage.PositionInfo();
        position.setAgvId(agvStatus.getAgvId());
        position.setPositionInitialized(agvStatus.getPositionInitialized());
        position.setX(agvStatus.getCurrentPosition().getX());
        position.setY(agvStatus.getCurrentPosition().getY());
        position.setTheta(agvStatus.getCurrentPosition().getTheta());
        position.setMapId(agvStatus.getCurrentPosition().getMapId());
        position.setTimestamp(Instant.now().toString());
        position.setVelocity(agvStatus.getVelocity());
        position.setDistanceSinceLastNode(calculateDistanceSinceLastNode());
        position.setRotationAngle(agvStatus.getCurrentPosition().getTheta());  // 可根据实际设置
        position.setLastNodeId(agvStatus.getLastNodeId());
        position.setCurrentNodeId(agvStatus.getCurrentNodeId());
        position.setSequenceId(agvStatus.getCurrentNodeIndex());
        position.setCurrentEdgeId(agvStatus.getCurrentEdgeId());
        position.setEdgeSequenceId(agvStatus.getCurrentEdgeIndex());
        position.setOrientation(new Vda5050PositionUpdateMessage.PositionInfo.Orientation(agvStatus.getCurrentPosition().getQx(),
                agvStatus.getCurrentPosition().getQy(),
                agvStatus.getCurrentPosition().getQz(),
                agvStatus.getCurrentPosition().getQw()));
        message.setPosition(position);
        return message;
    }

    /**
     * 构建标准VDA5050任务完成消息
     */
    public Vda5050TaskCompletionMessage buildStandardTaskCompleteMessage(AgvStatus agvStatus,
                                                                         String taskId, TaskStatus actionStatus,
                                                                         String messageStr, LocalDateTime startTime) {

        Vda5050TaskCompletionMessage message = new Vda5050TaskCompletionMessage();
        // 构建标准Header
        Vda5050Header header = buildStandardHeader(agvStatus);
        message.setHeader(header);

        // 构建标准Payload
        TaskCompletionPayload payload = new TaskCompletionPayload();
        payload.setAgvId(agvStatus.getAgvId());
        payload.setTaskId(taskId);
        payload.setOrderId(agvStatus.getCurrentOrderId());
        payload.setStatus(actionStatus.toString());
        payload.setMessage(messageStr);
        payload.setTimestamp(Instant.now().toString());

        // 设置任务时间信息
        if (startTime != null) {
            LocalDateTime endTime = LocalDateTime.now();
            payload.setStartTime(startTime.toString());
            payload.setEndTime(endTime.toString());
            payload.setDuration(endTime.toInstant(ZoneOffset.UTC).toEpochMilli() - startTime.toInstant(ZoneOffset.UTC).toEpochMilli());
        }

        message.setPayload(payload);
        return message;
    }

    /**
     * 构建标准VDA5050动作状态消息
     */
    public Vda5050ActionStateMessage buildStandardActionStateMessage(AgvStatus agvStatus,
                                                                     String actionId, TaskStatus actionStatus,
                                                                     String resultDescription) {

        Vda5050ActionStateMessage message = new Vda5050ActionStateMessage();

        // 构建标准Header
        Vda5050Header header = buildStandardHeader(agvStatus);
        message.setHeader(header);

        // 构建动作状态
        AgvActionState actionState = new AgvActionState();
        actionState.setActionId(actionId);
        actionState.setActionStatus(actionStatus.toString());
        actionState.setResultDescription(resultDescription);
        actionState.setStartTime(LocalDateTime.now());

        // 构建状态Payload
        StatePayload payload = new StatePayload();
        payload.setAgvId(agvStatus.getAgvId());
        payload.setOrderId(agvStatus.getCurrentOrderId());
        payload.setTimestamp(Instant.now().toString());
        payload.getActionStates().add(actionState);

        message.setPayload(payload);
        return message;
    }

    /**
     * 构建批量动作状态消息（支持多个动作状态上报）
     */
    public Vda5050ActionStateMessage buildBatchActionStateMessage(AgvStatus agvStatus,
                                                                  List<AgvActionState> actionStates) {
        Vda5050ActionStateMessage message = new Vda5050ActionStateMessage();

        // 构建标准Header
        Vda5050Header header = buildStandardHeader(agvStatus);
        message.setHeader(header);

        // 构建状态Payload
        StatePayload payload = new StatePayload();
        payload.setAgvId(agvStatus.getAgvId());
        payload.setOrderId(agvStatus.getCurrentOrderId());
        payload.setTimestamp(Instant.now().toString());
        payload.setActionStates(actionStates);

        message.setPayload(payload);
        return message;
    }

    /**
     * 构建VDA5050状态消息
     */
    public Vda5050StateMessage buildStateMessage(AgvStatus agvStatus) {
        Vda5050StateMessage message = new Vda5050StateMessage();

        Vda5050Header header = buildStandardHeader(agvStatus);
        message.setHeader(header);

        // 构建agvInfo
        Vda5050StateMessage.AgvInfo agvInfo = new Vda5050StateMessage.AgvInfo();
        agvInfo.setAgvId(agvStatus.getAgvId());
        agvInfo.setDescription(agvStatus.getName());
        agvInfo.setManufacturer(agvStatus.getManufacturer());
        agvInfo.setSerialNumber(agvStatus.getSerialNumber());
        agvInfo.setVersion("2.0.0");
        message.setAgvInfo(agvInfo);

        // 构建agvState
        Vda5050StateMessage.AgvState agvState5050 = new Vda5050StateMessage.AgvState();
        agvState5050.setAgvState(agvStatus.getAgvState().getValue());
        agvState5050.setOperationMode(agvStatus.getOperationMode().getValue());
        agvState5050.setPaused(agvStatus.getPaused());
        agvState5050.setEmergencyStop(agvStatus.getEmergencyStop());
        agvState5050.setBatteryState(agvStatus.getBatteryState().getValue());
        agvState5050.setSafetyState(agvStatus.getSafetyState());
        message.setAgvState(agvState5050);

        // 构建batteryInfo
        Vda5050StateMessage.BatteryInfo batteryInfo = new Vda5050StateMessage.BatteryInfo();
        batteryInfo.setBatteryLevel(agvStatus.getBatteryLevel());
        batteryInfo.setBatteryVoltage(agvStatus.getBatteryVoltage());
        batteryInfo.setBatteryCurrent(agvStatus.getBatteryCurrent());
        batteryInfo.setBatteryTemperature(agvStatus.getBatteryTemperature());
        message.setBatteryInfo(batteryInfo);

        // 构建velocityInfo
        Vda5050StateMessage.VelocityInfo velocityInfo = new Vda5050StateMessage.VelocityInfo();
        velocityInfo.setVelocity(agvStatus.getVelocity());
        velocityInfo.setMaxVelocity(agvStatus.getMaxSpeed());
        message.setVelocityInfo(velocityInfo);

        // 构建loadInfo
        Vda5050StateMessage.LoadInfo loadInfo = new Vda5050StateMessage.LoadInfo();
        loadInfo.setCurrentLoad(agvStatus.getCurrentLoad());
        loadInfo.setLoadCapacity(agvStatus.getLoadCapacity());
        loadInfo.setLoadHandled(agvStatus.getLoadHandled());
        message.setLoadInfo(loadInfo);

        // 构建nodeInfo
        Vda5050StateMessage.NodeInfo nodeInfo = new Vda5050StateMessage.NodeInfo();
        nodeInfo.setLastNodeId(agvStatus.getLastNodeId());
        nodeInfo.setLastNodeSequenceId(agvStatus.getLastNodeIndex());
        nodeInfo.setNextNodeId(agvStatus.getNextNodeId());
        message.setNodeInfo(nodeInfo);

        // 构建orderInfo
        Vda5050StateMessage.OrderInfo orderInfo = new Vda5050StateMessage.OrderInfo();
        orderInfo.setOrderId(agvStatus.getCurrentOrderId());
        orderInfo.setOrderState(agvStatus.getOrderState());
        orderInfo.setOrderUpdateId(agvStatus.getOrderUpdateId());
        message.setOrderInfo(orderInfo);

        // 构建agvPosition
        if (agvStatus.getCurrentPosition() != null) {
            message.setAgvPosition(agvStatus.getCurrentPosition());
        }

        // 设置actionStates和activeErrors
        message.setActionStates(agvStatus.getActionStates());
        if (agvStatus.getActiveErrors() != null) {
            List<Vda5050StateMessage.Error> errorList = new ArrayList<>();
            for (String errorCode : agvStatus.getActiveErrors()) {
                Vda5050StateMessage.Error vError = new Vda5050StateMessage.Error(errorCode, "", "WARNING");
                errorList.add(vError);
            }
            message.setActiveErrors(errorList);
        }
        return message;
    }

    /**
     * 构建标准Header（复用方法）
     */
    private Vda5050Header buildStandardHeader(AgvStatus agvStatus) {
        Vda5050Header header = new Vda5050Header();
        header.setHeaderId(System.currentTimeMillis());
        header.setTimestamp(Instant.now());
        header.setVersion(agvStatus.getVersion());
        header.setManufacturer(agvStatus.getManufacturer());
        header.setSerialNumber(agvStatus.getSerialNumber());
        return header;
    }

    /**
     * 获取支持的协议特性
     */
    private List<ProtocolFeature> getSupportedProtocolFeatures() {
        List<ProtocolFeature> features = new ArrayList<>();
        features.add(new ProtocolFeature("instantActions", true));
        features.add(new ProtocolFeature("visualization", true));
        features.add(new ProtocolFeature("factsheet", true));
        return features;
    }

    /**
     * 获取启用的协议特性
     */
    private List<String> getEnabledProtocolFeatures() {
        return Arrays.asList("instantActions", "visualization", "factsheet");
    }


    /**
     * 判断AGV当前位置状态
     */
    public AgvPositionResult determinePositionState(AgvStatus agvStatus) {
        AgvPositionResult agvPositionResult = new AgvPositionResult();
        // 方法1：根据与节点的距离判断
        NearestNodeResult nearestNodeResult = calculateDistanceToNearestNode(agvStatus);
        double tolerance = 0.3; // 容忍距离，单位米

        if (nearestNodeResult.getNode() != null && nearestNodeResult.getDistance() <= tolerance) {
            agvPositionResult.setAgvPositionState(AgvPositionState.ON_NODE);
            agvPositionResult.setNearestNodeResult(nearestNodeResult);
            return agvPositionResult;
        }
        Edge edge = calculateDistanceInEdge(agvStatus);
        if (edge != null) {
            agvPositionResult.setAgvPositionState(AgvPositionState.ON_EDGE);
            agvPositionResult.setInEdgeInfo(edge);
            return agvPositionResult;
        }
        return agvPositionResult;
    }

    /**
     * 判断是否在已知边上
     */
    private Edge calculateDistanceInEdge(AgvStatus agvStatus) {
        double x = agvStatus.getCurrentPosition().getX();
        double y = agvStatus.getCurrentPosition().getY();
        double tolerance = 0.3; // 10cm 容差，可配置

        Edge edge = findEdgeIdByPosition(x, y, tolerance);
        if (edge != null) {
            return edge;
        }
        return null;
    }


    /**
     * 根据位置查找所在的边ID
     */
    private Edge findEdgeIdByPosition(double x, double y, double tolerance) {
        if (mapService == null || !mapService.isMapLoaded()) {
            return null;
        }

        MapDetailInfo mapDetail = mapService.getMapDetailInfo();
        if (mapDetail == null) {
            return null;
        }

        // 获取所有边和节点
        List<Edge> edges = mapDetail.getEdges();
        // 或者我们可以在Vda5050MessageBuilder中维护一个节点坐标缓存

        for (Edge edge : edges) {
            Node sourceNode = mapService.getNode(edge.getSourceId());
            Node targetNode = mapService.getNode(edge.getTargetId());
            if (sourceNode == null || targetNode == null) {
                continue;
            }
            if (MathUtils.isPointOnSegment(x, y, sourceNode.getX(), sourceNode.getY(), targetNode.getX(),
                    targetNode.getY(), tolerance)) {
                return edge;
            }
        }
        return null;
    }

    /**
     * 计算到最近节点的距离
     */
    private NearestNodeResult calculateDistanceToNearestNode(AgvStatus agvStatus) {
        Node nearestNode = null;
        // 获取当前位置
        double currentX = agvStatus.getCurrentPosition().getX(); // 假设有AgvStatus对象
        double currentY = agvStatus.getCurrentPosition().getY();

        if (mapService == null || !mapService.isMapLoaded()) {
            log.warn("地图服务未加载，无法计算到最近节点的距离，返回默认值0.0");
            return new NearestNodeResult(null, 0.0);
        }

        List<Node> nodes = mapService.getAllNodes();
        if (nodes == null || nodes.isEmpty()) {
            log.warn("地图节点列表为空，返回默认值0.0");
            return new NearestNodeResult(null, 0.0);
        }

        double minDistance = Double.MAX_VALUE;
        for (Node node : nodes) {
            double dx = currentX - node.getX();
            double dy = currentY - node.getY();
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance < minDistance) {
                minDistance = distance;
                nearestNode = node;
            }
        }
        return new NearestNodeResult(nearestNode, minDistance);
    }

    /**
     * 判断机器人是否位于某个点位上
     */
    public boolean calcAgvAtNode(AgvStatus agvStatus, Node targetNode) {
        double currentX = agvStatus.getCurrentPosition().getX(); // 假设有AgvStatus对象
        double currentY = agvStatus.getCurrentPosition().getY();
        double dx = currentX - targetNode.getX();
        double dy = currentY - targetNode.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        return distance < 0.3; //0.3米就认为在点位上
    }
}