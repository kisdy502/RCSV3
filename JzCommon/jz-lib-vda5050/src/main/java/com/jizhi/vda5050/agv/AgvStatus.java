package com.jizhi.vda5050.agv;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jizhi.vda5050.domain.*;
import com.jizhi.vda5050.message.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static com.jizhi.vda5050.agv.AgvState.*;

@Slf4j
@Data
public class AgvStatus {

    private String agvId;                    // AGV标识符
    private String name;                     // AGV名称
    private String manufacturer;             // 制造商
    private String serialNumber;             // 序列号
    private String version;                  // 软件版本

    // 位置信息
    private AgvPosition currentPosition;        // 当前位置
    private Double deviation = 0.0;                // 位置偏差
    private double distanceSinceLastNode = 0.0;      // 成员变量：记录自上一个节点后行驶的距离（米）

    // 状态信息
    private AgvState agvState;               // AGV状态
    private String errorCode;

    private BatteryState batteryState;       // 电池状态
    private OperationMode operationMode;     // 操作模式
    private Boolean enabled;                 // 是否启用

    // 电池信息
    private Double batteryLevel = 0.0;             // 电池电量 (0-100)
    private Double batteryVoltage = 0.0;           // 电池电压
    private Double batteryCurrent = 0.0;           // 电池电流
    private Double batteryTemperature = 0.0;       // 电池温度

    // 速度和负载
    private Double velocity = 0.0;                 // 当前速度 (m/s)
    private Double loadCapacity = 0.0;             // 负载能力 (kg)
    private Double currentLoad = 0.0;              // 当前负载 (kg)
    private Boolean loadHandled;             // 负载是否已处理

    // 时间和状态
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime lastUpdateTime;    // 最后更新时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime lastStateChange;   // 最后状态变更时间
    private LocalDateTime lastMessageTime;   // 最后消息时间

    // 安全信息
    private Boolean emergencyStop;           // 急停状态
    private Integer safetyState;             // 安全状态 (0-正常, 1-警告, 2-停止)
    private List<String> activeErrors;           // 活动错误代码

    // 连接信息
    private Boolean connected;               // 是否连接
    private Integer sequenceId;              // 消息序列号
    private Long messageId;                  // 消息ID

    // 任务信息
    private String orderState;
    private String currentOrderId;           // 当前订单ID
    private Integer orderUpdateId;           // 订单更新ID
    private LocalDateTime orderStartTime;
    private String resultDescription;

    private String lastNodeId;               // 上一个节点ID
    private Integer lastNodeIndex;      //上一个节点的序号

    private String currentNodeId;            // 当前节点ID
    private Integer currentNodeIndex = 0;

    private String nextNodeId;               // 下一个节点ID

    private String currentEdgeId;                   // 当前边ID
    private Integer currentEdgeIndex;

    private List<String> nodeSequence = new ArrayList<>();
    private List<String> edgeSequence = new ArrayList<>();


    // 行动信息
    private List<AgvAction> actions = new ArrayList<>();             // 当前行动列表
    private List<AgvActionState> actionStates = new ArrayList<>();   // 行动状态列表
    private Vda5050OrderMessage.Action currentAction;

    // 扩展信息
    private Map<String, Object> additionalInfo = new HashMap<>(); // 附加信息


    private String agvType;
    private Double maxSpeed;

    private Double loadWeight;
    private Boolean positionInitialized = false;

    private String lastReportNodeId = "";
    private String lastReportEdgeId = "";


    /**
     * 构造函数
     */
    public AgvStatus(String agvId) {
        this.agvId = agvId;
        this.agvState = IDLE;
        this.batteryState = BatteryState.UNKNOWN;
        this.operationMode = OperationMode.AUTOMATIC;
        this.enabled = true;
//        this.paused = false;
        this.batteryLevel = 100.0;
        this.velocity = 0.0;
        this.connected = true;
        this.emergencyStop = false;
        this.safetyState = 0;
        this.lastUpdateTime = LocalDateTime.now();
        this.lastStateChange = LocalDateTime.now();
    }


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
     * 更新状态
     */
    public void updateStatus(AgvState newState) {
        if (this.agvState != newState) {
            this.agvState = newState;
            this.lastStateChange = LocalDateTime.now();
        }
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 更新位置
     */
    public void updatePosition(AgvPosition position) {
        this.currentPosition = position;
        this.lastUpdateTime = LocalDateTime.now();
        this.positionInitialized = position.getPositionInitialized();
    }

    /**
     * 更新电池信息
     */
    public void updateBattery(Double level, BatteryState state) {
        this.batteryLevel = level;
        this.batteryState = state;
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 检查AGV是否可用
     */
    public boolean isAvailable() {
        return enabled &&
                connected &&
                !emergencyStop &&
                batteryLevel > 20.0 &&
                (agvState.canAcceptTask() || agvState == PAUSED);
    }

    /**
     * 检查AGV是否在工作中
     */
    public boolean isWorking() {
        return agvState.isWorking();
    }

    /**
     * 检查AGV是否在错误状态
     */
    public boolean isError() {
        return agvState.isError();
    }

    /**
     * 设置急停状态
     */
    public void setEmergencyStop(boolean emergencyStop) {
        this.emergencyStop = emergencyStop;
        if (emergencyStop) {
            // 主状态改为 ERROR（VDA5050标准）
            this.agvState = ERROR;
            // 设置自定义错误码，便于区分急停
            this.errorCode = "EMERGENCY_STOP";
            this.lastStateChange = LocalDateTime.now();
        }
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * 添加附加信息
     */
    public void addAdditionalInfo(String key, Object value) {
        if (additionalInfo == null) {
            additionalInfo = new HashMap<>();
        }
        additionalInfo.put(key, value);
    }

    /**
     * 获取附加信息
     */
    public Object getAdditionalInfo(String key) {
        if (additionalInfo == null) {
            return null;
        }
        return additionalInfo.get(key);
    }


    /**
     * 从VDA5050状态消息更新
     */
    public void updateFromVda5050StateMessage(Vda5050StateMessage stateMessage) {
        if (stateMessage == null) return;
        Vda5050Header header = stateMessage.getHeader();

        // 更新头部信息
        if (header != null) {
            this.messageId = header.getHeaderId();
        }

        // 更新AGV信息
        if (stateMessage.getAgvInfo() != null) {
            Vda5050StateMessage.AgvInfo agvInfo = stateMessage.getAgvInfo();
            this.agvId = agvInfo.getAgvId() != null ? agvInfo.getAgvId() : this.agvId;
            this.name = this.agvId;
            this.manufacturer = agvInfo.getManufacturer() != null ? agvInfo.getManufacturer() : this.manufacturer;
            this.serialNumber = agvInfo.getSerialNumber() != null ? agvInfo.getSerialNumber() : this.serialNumber;
            this.version = agvInfo.getVersion() != null ? agvInfo.getVersion() : this.version;
        }

        // 更新位置信息
//        this.currentPosition = stateMessage.getAgvPosition();
//        log.debug("Vda5050State positionInfo:{}",stateMessage.getAgvPosition());
        // 更新状态信息
        if (stateMessage.getAgvState() != null) {
            Vda5050StateMessage.AgvState agvState = stateMessage.getAgvState();
            this.agvState = agvState.getAgvState() != null ?
                    fromVda5050Value(agvState.getAgvState()) : this.agvState;
            this.batteryState = agvState.getBatteryState() != null ?
                    BatteryState.fromValue(agvState.getBatteryState()) : this.batteryState;
            this.operationMode = agvState.getOperationMode() != null ?
                    OperationMode.fromValue(agvState.getOperationMode()) : this.operationMode;
//            this.paused = agvState.getPaused() != null ? agvState.getPaused() : this.paused;
            this.emergencyStop = agvState.getEmergencyStop() != null ? agvState.getEmergencyStop() : this.emergencyStop;
            this.safetyState = agvState.getSafetyState() != null ? agvState.getSafetyState() : this.safetyState;
        }

        if (stateMessage.getVelocityInfo() != null) {
            this.velocity = stateMessage.getVelocityInfo().getVelocity();
            this.maxSpeed = stateMessage.getVelocityInfo().getMaxVelocity();
        }

        // 更新电池信息
        if (stateMessage.getBatteryInfo() != null) {
            Vda5050StateMessage.BatteryInfo batteryInfo = stateMessage.getBatteryInfo();
            this.batteryLevel = batteryInfo.getBatteryLevel() != null ?
                    batteryInfo.getBatteryLevel() : this.batteryLevel;
            this.batteryVoltage = batteryInfo.getBatteryVoltage() != null ?
                    batteryInfo.getBatteryVoltage() : this.batteryVoltage;
            this.batteryCurrent = batteryInfo.getBatteryCurrent() != null ?
                    batteryInfo.getBatteryCurrent() : this.batteryCurrent;
            this.batteryTemperature = batteryInfo.getBatteryTemperature() != null ?
                    batteryInfo.getBatteryTemperature() : this.batteryTemperature;
        }

        // 更新订单信息
        if (stateMessage.getOrderInfo() != null) {
            Vda5050StateMessage.OrderInfo orderInfo = stateMessage.getOrderInfo();
            this.currentOrderId = orderInfo.getOrderId() != null ? orderInfo.getOrderId() : this.currentOrderId;
            this.orderUpdateId = orderInfo.getOrderUpdateId() != null ? orderInfo.getOrderUpdateId() :
                    this.orderUpdateId;
            this.orderState = orderInfo.getOrderState();
        }

        // 更新节点信息
        if (stateMessage.getNodeInfo() != null) {
            Vda5050StateMessage.NodeInfo nodeInfo = stateMessage.getNodeInfo();
            this.lastNodeId = nodeInfo.getLastNodeId() != null ? nodeInfo.getLastNodeId() : this.lastNodeId;
            this.nextNodeId = nodeInfo.getNextNodeId() != null ? nodeInfo.getNextNodeId() : this.nextNodeId;
            this.sequenceId = nodeInfo.getLastNodeSequenceId() != null ?
                    nodeInfo.getLastNodeSequenceId() : this.sequenceId;
        }

        // 更新行动状态信息
        if (stateMessage.getActionStates() != null) {
            // 转换为AgvStatus需要的AgvActionState列表
            this.actionStates = stateMessage.getActionStates().stream()
                    .map(vdaActionState -> {
                        AgvActionState actionState = new AgvActionState();
                        actionState.setActionId(vdaActionState.getActionId());
                        actionState.setActionStatus(vdaActionState.getActionStatus());
                        actionState.setResultDescription(vdaActionState.getResultDescription());
                        return actionState;
                    })
                    .collect(Collectors.toList());
        }

        // 更新错误信息
        if (stateMessage.getActiveErrors() != null) {
            // 将Error对象转换为错误代码字符串列表
            List<String> list = new ArrayList<>();
            for (Vda5050StateMessage.Error error : stateMessage.getActiveErrors()) {
                String errorCode = error.getErrorCode();
                list.add(errorCode);
            }
            this.activeErrors = list;
        }

        // 注意：Vda5050StateMessage中没有直接的actions字段，如果需要保留原逻辑，需要调整
        // 如果Vda5050StateMessage中有actions字段，请添加相应的getter
        // this.actions = stateMessage.getActions() != null ? stateMessage.getActions() : this.actions;

        // 更新时间戳
        this.lastUpdateTime = LocalDateTime.now();
        this.lastMessageTime = LocalDateTime.now();
    }


    /**
     * 从VDA5050订单消息更新
     */
    public void updateFromVda5050OrderMessage(Vda5050OrderMessage vda5050OrderMessage) {
        if (vda5050OrderMessage == null) return;

        Vda5050Header header = vda5050OrderMessage.getHeader();

        // 更新头部信息
        if (header != null) {
            this.messageId = header.getHeaderId();
//            this.version = header.getVersion() != null ? header.getVersion() : this.version;
//            this.manufacturer = header.getManufacturer() != null ? header.getManufacturer() : this.manufacturer;
//            this.serialNumber = header.getSerialNumber() != null ? header.getSerialNumber() : this.serialNumber;
        }

        // 更新订单信息
        if (vda5050OrderMessage.getOrderInformation() != null) {
            Vda5050OrderMessage.OrderInformation orderInfo = vda5050OrderMessage.getOrderInformation();
            this.currentOrderId = orderInfo.getOrderId() != null ? orderInfo.getOrderId() : this.currentOrderId;
            this.orderUpdateId = orderInfo.getOrderUpdateId() != null ? orderInfo.getOrderUpdateId() :
                    this.orderUpdateId;

            // 订单优先级（如果有对应字段）
            // 如果AgvStatus有orderPriority字段，可以这样更新：
            // this.orderPriority = orderInfo.getOrderPriority() != null ? orderInfo.getOrderPriority() : this
            // .orderPriority;

            // 订单模式（如果有对应字段）
            // this.orderMode = orderInfo.getOrderMode() != null ? orderInfo.getOrderMode() : this.orderMode;
        }

        // 更新节点信息
        if (vda5050OrderMessage.getNodePositions() != null && !vda5050OrderMessage.getNodePositions().isEmpty()) {
            // 获取第一个节点（起始节点）
            Vda5050OrderMessage.NodePosition firstNode = vda5050OrderMessage.getNodePositions().get(0);

            this.lastNodeId = firstNode.getNodeId() != null ? firstNode.getNodeId() : this.lastNodeId;

            this.currentNodeIndex = 0;
            this.currentNodeId = firstNode.getNodeId();

            // 获取下一个节点（如果有多个节点）
            if (vda5050OrderMessage.getNodePositions().size() > 1) {
                Vda5050OrderMessage.NodePosition secondNode = vda5050OrderMessage.getNodePositions().get(1);
                this.nextNodeId = secondNode.getNodeId() != null ? secondNode.getNodeId() : this.nextNodeId;
            }

            // 更新序列ID
            this.sequenceId = firstNode.getSequenceId() != null ? firstNode.getSequenceId() : this.sequenceId;

            // 如果有节点位置信息，更新当前位置
//            if (firstNode.getNodePosition() != null) {
//                Vda5050OrderMessage.NodePosition.NodePositionDetail positionDetail = firstNode.getNodePosition();
//                if (this.currentPosition == null) {
//                    this.currentPosition = new AgvPosition();
//                }
//                this.currentPosition.setX(positionDetail.getX() != null ? positionDetail.getX() :
//                        this.currentPosition.getX());
//                this.currentPosition.setY(positionDetail.getY() != null ? positionDetail.getY() :
//                        this.currentPosition.getY());
//                this.currentPosition.setTheta(positionDetail.getTheta() != null ? positionDetail.getTheta() :
//                        this.currentPosition.getTheta());
//                this.currentPosition.setMapId(positionDetail.getMapId() != null ? positionDetail.getMapId() :
//                        this.currentPosition.getMapId());
//            }

            this.nodeSequence = vda5050OrderMessage.getNodePositions().stream()
                    .map(Vda5050OrderMessage.NodePosition::getNodeId)
                    .collect(Collectors.toList());

        }

        // 更新行动信息（将Vda5050OrderMessage.Action转换为AgvAction）
        if (vda5050OrderMessage.getActions() != null && !vda5050OrderMessage.getActions().isEmpty()) {

            this.actions = vda5050OrderMessage.getActions().stream()
                    .map(vdaAction -> {
                        AgvAction agvAction = new AgvAction();
                        agvAction.setActionId(vdaAction.getActionId());
                        agvAction.setActionType(vdaAction.getActionType());
                        agvAction.setActionDescription(vdaAction.getActionDescription());
                        agvAction.setActionPriority(vdaAction.getActionPriority() != null ?
                                vdaAction.getActionPriority() : 50);
                        agvAction.setBlockingType(vdaAction.getBlockingType() != null ?
                                vdaAction.getBlockingType().intValue() : 1L);

                        // 转换行动参数
                        if (vdaAction.getActionParameters() != null) {
                            Map<String, Object> parameters = new HashMap<>();
                            for (Vda5050ActionParameter param : vdaAction.getActionParameters()) {
                                if (param.getKey() != null && param.getValue() != null) {
                                    parameters.put(param.getKey(), param.getValue());
                                }
                            }
                            agvAction.setActionParameters(parameters);
                        }

                        return agvAction;
                    })
                    .collect(Collectors.toList());
        }

        // 更新边信息（如果有对应字段需要存储）
        if (vda5050OrderMessage.getEdges() != null && !vda5050OrderMessage.getEdges().isEmpty()) {
            // 如果AgvStatus有edges字段，可以这样更新：
            this.edgeSequence = vda5050OrderMessage.getEdges().stream()
                    .map(Vda5050OrderMessage.Edge::getEdgeId)
                    .collect(Collectors.toList());
        }

        // 更新附加信息（如果有）
        if (vda5050OrderMessage.getAdditionalInformation() != null) {
            // 可以将附加信息存储到AgvStatus的某个字段中
            this.additionalInfo = vda5050OrderMessage.getAdditionalInformation();
        }

        this.orderStartTime = LocalDateTime.now();
        // 更新时间戳
        this.lastUpdateTime = LocalDateTime.now();
        this.lastMessageTime = LocalDateTime.now();
    }

    /**
     * 转换为JSON字符串
     */
    @Override
    public String toString() {
        return String.format("AGV[%s] State: %s, Battery: %.1f%%, Position: %s, Connected: %s",
                agvId, agvState != null ? agvState.getValue() : "UNKNOWN",
                batteryLevel != null ? batteryLevel : 0.0,
                currentPosition != null ? String.format("(%.2f, %.2f)", currentPosition.getX(),
                        currentPosition.getY()) : "null",
                connected != null ? connected : false);
    }


    /**
     * 获取支持的协议特性（标准方式替代capabilities）
     */
    private List<ProtocolFeature> getSupportedProtocolFeatures() {
        List<ProtocolFeature> features = new ArrayList<>();

        // 根据VDA5050标准定义支持的协议特性
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
}