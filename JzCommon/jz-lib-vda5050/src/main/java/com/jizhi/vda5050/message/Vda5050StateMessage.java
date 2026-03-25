package com.jizhi.vda5050.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jizhi.vda5050.agv.AgvActionState;
import com.jizhi.vda5050.agv.AgvPosition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vda5050StateMessage {

    @JsonProperty("header")
    private Vda5050Header header;

    @JsonProperty("agvInfo")
    private AgvInfo agvInfo;

    @JsonProperty("agvState")
    private AgvState agvState;

    @JsonProperty("batteryInfo")
    private BatteryInfo batteryInfo;

    @JsonProperty("velocityInfo")
    private VelocityInfo velocityInfo;

    @JsonProperty("loadInfo")
    private LoadInfo loadInfo;

    @JsonProperty("nodeInfo")
    private NodeInfo nodeInfo;

    @JsonProperty("orderInfo")
    private OrderInfo orderInfo;

    @JsonProperty("actionStates")
    private List<AgvActionState> actionStates = new ArrayList<>();

    @JsonProperty("activeErrors")
    private List<Error> activeErrors = new ArrayList<>();

    @JsonProperty("agvPosition")
    private AgvPosition agvPosition;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgvInfo {
        private String agvId;
        private String manufacturer;             // 制造商
        private String serialNumber;             // 序列号
        private String description;
        private String version = "2.0.0";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgvState {
        private String agvState;
        private String operationMode = "AUTOMATIC";
        private Boolean paused = false;
        private Boolean emergencyStop = false;
        private String batteryState = "DISCHARGING";
        private Integer safetyState = 0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatteryInfo {
        private Double batteryLevel;
        private Double batteryVoltage = 48.0;
        private Double batteryCurrent = 5.0;
        private Double batteryTemperature = 25.0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityInfo {
        private Double velocity;
        private Double maxVelocity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoadInfo {
        private Double currentLoad;
        private Double loadCapacity;
        private Boolean loadHandled = false;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeInfo {
        private String lastNodeId;
        private Integer lastNodeSequenceId = 0;
        private String nextNodeId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderInfo {
        private String orderId;
        private String orderState;
        private Integer orderUpdateId = 0;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Error {
        private String errorCode;
        private String errorDescription;
        private String errorLevel = "WARNING";
    }
}
