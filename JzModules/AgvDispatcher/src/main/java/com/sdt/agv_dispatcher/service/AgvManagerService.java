package com.sdt.agv_dispatcher.service;


import com.jizhi.vda5050.agv.AgvState;
import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.agv.BatteryState;
import com.jizhi.vda5050.message.Vda5050ConnectionMessage;
import com.jizhi.vda5050.message.Vda5050PositionUpdateMessage;
import com.jizhi.vda5050.message.Vda5050StateMessage;
import com.jizhi.vda5050.agv.AgvPosition;
import com.sdt.agv_dispatcher.graph.AGVGraph;
import com.jizhi.vda5050.domain.Node;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AgvManagerService {

    // AGV状态存储
    private final Map<String, AgvStatus> agvStatusMap = new ConcurrentHashMap<>();

    @Autowired
    private AGVGraph agvGraph;

    @PostConstruct
    public void init() {
    }

    /**
     * 获取所有AGV状态
     */
    public List<AgvStatus> getAllAgvStatus() {
        return new ArrayList<>(agvStatusMap.values());
    }

    /**
     * 获取AGV状态
     */
    public AgvStatus getAgvStatus(String agvId) {
        return agvStatusMap.get(agvId);
    }

    /**
     * 更新AGV状态
     */
    public void updateAgvStatus(AgvStatus agvStatus) {
        if (agvStatus == null || agvStatus.getAgvId() == null) {
            log.warn("无效的AGV状态更新");
            return;
        }

        AgvStatus existing = agvStatusMap.get(agvStatus.getAgvId());
        if (existing == null) {
            // 新AGV注册
            agvStatus.setLastUpdateTime(LocalDateTime.now());
            agvStatusMap.put(agvStatus.getAgvId(), agvStatus);
            log.info("新AGV注册: {}", agvStatus.getAgvId());
        } else {
            // 更新现有AGV状态
            agvStatus.setLastUpdateTime(LocalDateTime.now());
            agvStatusMap.put(agvStatus.getAgvId(), agvStatus);
        }
    }

    /**
     * 从VDA5050消息更新AGV状态
     */
    public void updateFromVda5050Message(String agvId, Vda5050StateMessage stateMessage) {
        AgvStatus agvStatus = getAgvStatus(agvId);
        if (agvStatus == null) {
            // 创建新的AGV状态
            agvStatus = new AgvStatus(agvId);
            agvStatusMap.put(agvId, agvStatus);
        }

        // 从VDA5050消息更新状态
        agvStatus.updateFromVda5050StateMessage(stateMessage);
    }

    /**
     * 获取可用AGV列表
     */
    public List<AgvStatus> getAvailableAgvs() {
        List<AgvStatus> availableAgvs = new ArrayList<>();

        for (AgvStatus agvStatus : agvStatusMap.values()) {
            if (agvStatus.isAvailable()) {
                availableAgvs.add(agvStatus);
            }
        }

        return availableAgvs;
    }

    /**
     * 获取正在工作中的AGV列表
     */
    public List<AgvStatus> getWorkingAgvs() {
        List<AgvStatus> workingAgvs = new ArrayList<>();

        for (AgvStatus agvStatus : agvStatusMap.values()) {
            if (agvStatus.isWorking()) {
                workingAgvs.add(agvStatus);
            }
        }

        return workingAgvs;
    }

    /**
     * 更新AGV位置
     */
    public void updateAgvPosition(Vda5050PositionUpdateMessage positionUpdateMessage) {
        Vda5050PositionUpdateMessage.PositionInfo positionInfo = positionUpdateMessage.getPosition();
        String agvId = positionInfo.getAgvId();
        String nodeId = positionInfo.getCurrentNodeId();

        AgvStatus agvStatus = getAgvStatus(agvId);
        if (agvStatus == null) {
            log.warn("AGV不存在: {}", agvId);
            return;
        }
        AgvPosition position = new AgvPosition(
                positionInfo.getX(),
                positionInfo.getY(),
                positionInfo.getTheta(),
                positionInfo.getMapId(),
                positionInfo.getOrientation().getX(),
                positionInfo.getOrientation().getY(),
                positionInfo.getOrientation().getZ(),
                positionInfo.getOrientation().getW()
        );
        agvStatus.updatePosition(position);
        if (nodeId != null) {
            agvStatus.setCurrentNodeId(nodeId);
            agvStatus.setSequenceId(positionInfo.getNodeSequenceId());
        }
        agvStatus.setLastNodeId(positionInfo.getLastNodeId());
        if(positionInfo.getCurrentEdgeId()!=null){
            agvStatus.setCurrentEdgeId(positionInfo.getCurrentEdgeId());
            agvStatus.setCurrentEdgeIndex(positionInfo.getEdgeSequenceId());
        }
    }

    /**
     * 更新AGV电池状态
     */
    public void updateAgvBattery(String agvId, Double batteryLevel, BatteryState batteryState) {
        AgvStatus agvStatus = getAgvStatus(agvId);
        if (agvStatus == null) {
            log.warn("AGV不存在: {}", agvId);
            return;
        }

        agvStatus.updateBattery(batteryLevel, batteryState);
        log.debug("更新AGV电池: {} -> {:.1f}%", agvId, batteryLevel);
    }

    /**
     * 设置AGV任务状态
     */
    public void setAgvTaskStatus(String agvId, String orderId, AgvState state) {
        AgvStatus agvStatus = getAgvStatus(agvId);
        if (agvStatus == null) {
            log.warn("AGV不存在: {}", agvId);
            return;
        }

        agvStatus.setCurrentOrderId(orderId);
        agvStatus.updateStatus(state);

        if (state == AgvState.EXECUTING) {
            log.info("AGV开始执行任务: {}, Order: {}", agvId, orderId);
        } else if (state == AgvState.IDLE) {
            log.info("AGV完成任务: {}, Order: {}", agvId, orderId);
        }
    }

    /**
     * 设置急停状态
     */
    public void setEmergencyStop(String agvId, boolean emergencyStop) {
        AgvStatus agvStatus = getAgvStatus(agvId);
        if (agvStatus == null) {
            log.warn("AGV不存在: {}", agvId);
            return;
        }

        agvStatus.setEmergencyStop(emergencyStop);
        log.warn("AGV急停状态更新: {} -> {}", agvId, emergencyStop);
    }

    /**
     * 获取AGV位置信息
     */
    public String getAgvPosition(String agvId) {
        AgvStatus agvStatus = getAgvStatus(agvId);
        if (agvStatus == null) {
            return null;
        }
        return agvStatus.getCurrentNodeId();
    }

    /**
     * 检查AGV是否在线
     */
    public boolean isAgvOnline(String agvId) {
        AgvStatus agvStatus = getAgvStatus(agvId);
        if (agvStatus == null) {
            return false;
        }

        // 检查最后更新时间，超过30秒认为离线
        LocalDateTime lastUpdate = agvStatus.getLastUpdateTime();
        if (lastUpdate == null) {
            return false;
        }

        return LocalDateTime.now().minusSeconds(30).isBefore(lastUpdate);
    }

    /**
     * 清理离线AGV
     */
    public void cleanupOfflineAgvs() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(5);
        List<String> offlineAgvs = new ArrayList<>();

        for (Map.Entry<String, AgvStatus> entry : agvStatusMap.entrySet()) {
            LocalDateTime lastUpdate = entry.getValue().getLastUpdateTime();
            if (lastUpdate == null || lastUpdate.isBefore(cutoffTime)) {
                offlineAgvs.add(entry.getKey());
            }
        }

        for (String agvId : offlineAgvs) {
            agvStatusMap.remove(agvId);
            log.info("清理离线AGV: {}", agvId);
        }
    }

    /**
     * 统计AGV状态
     */
    public Map<String, Integer> getAgvStatistics() {
        Map<String, Integer> stats = new HashMap<>();

        stats.put("total", agvStatusMap.size());
        stats.put("available", getAvailableAgvs().size());
        stats.put("working", getWorkingAgvs().size());
        stats.put("error", 0);
        stats.put("offline", 0);

        // 统计各个状态的数量
        Map<AgvState, Integer> stateCounts = new HashMap<>();
        for (AgvStatus agvStatus : agvStatusMap.values()) {
            AgvState state = agvStatus.getAgvState();
            stateCounts.put(state, stateCounts.getOrDefault(state, 0) + 1);
        }

        for (Map.Entry<AgvState, Integer> entry : stateCounts.entrySet()) {
            stats.put("state_" + entry.getKey().getValue().toLowerCase(), entry.getValue());
        }

        return stats;
    }

    /**
     * 设置AGV在线状态
     */
    public void setAgvOnline(String agvId, Vda5050ConnectionMessage connectionMessage) {
        AgvStatus status = agvStatusMap.get(agvId);
        if (status == null) {
            status = new AgvStatus(agvId);
            status.setManufacturer(connectionMessage.getPayload().getManufacturer());
            status.setSerialNumber(connectionMessage.getPayload().getSerialNumber());
            status.setVersion(connectionMessage.getPayload().getVersion());
            agvStatusMap.put(agvId, status);
        }
        status.setConnected(true);
        status.setLastUpdateTime(LocalDateTime.now());
    }

    /**
     * 设置AGV离线状态
     */
    public void setAgvOffline(String agvId) {
        AgvStatus status = agvStatusMap.get(agvId);
        if (status != null) {
            status.setConnected(false);
            status.setLastUpdateTime(LocalDateTime.now());
        }
    }
}
