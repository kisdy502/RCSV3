package com.sdt.agv_simulator.agv;


import com.jizhi.vda5050.agv.*;
import com.jizhi.vda5050.domain.Node;
import com.sdt.agv_simulator.service.MapService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Component
public class VirtualAgv {

    @Autowired
    private MapService mapService;
    private AgvStatus agvStatus;
    private Double simulatedSpeed = 1.0;
    private Double simulatedBatteryConsumption = 0.1;

    private List<OrderAction> currentActions = new ArrayList<>();

    /**
     * 开始执行订单
     */
    public void startOrder(String orderId, List<String> nodes, List<String> edges) {
        agvStatus.setCurrentOrderId(orderId);
        agvStatus.setAgvState(AgvState.EXECUTING);
        agvStatus.setNodeSequence(nodes);
        agvStatus.setEdgeSequence(edges);
        agvStatus.setCurrentNodeIndex(0);
        agvStatus.setCurrentNodeId(nodes.get(0));

//        agvStatus.setOrderStartTime(LocalDateTime.now());
        agvStatus.setOrderUpdateId(agvStatus.getOrderUpdateId() + 1);
        agvStatus.setLastStateChange(LocalDateTime.now());
//        agvStatus.setLastUpdateTime(LocalDateTime.now());
        if (agvStatus.getNodeSequence().size() > 1) {
            agvStatus.setNextNodeId(agvStatus.getNodeSequence().get(1));
        }
    }

    /**
     * 移动到某个节点,更新agv status的逻辑
     */
    public boolean handleArrivedNode(Node arrivedNode) {
        int arrivedNodeIndex = agvStatus.getNodeSequence().indexOf(arrivedNode.getId());
        if (arrivedNodeIndex == 0) {  // 起点
            agvStatus.setCurrentNodeId(arrivedNode.getId());
            agvStatus.setCurrentNodeIndex(arrivedNodeIndex);
            agvStatus.setLastNodeId(null);
            agvStatus.setLastNodeIndex(-1);
            agvStatus.setNextNodeId(agvStatus.getNodeSequence().get(arrivedNodeIndex + 1));
            agvStatus.setCurrentEdgeId(null);   // 停在节点上，无当前边
            agvStatus.setCurrentEdgeIndex(-1);
        } else if (arrivedNodeIndex == agvStatus.getNodeSequence().size() - 1) {  // 终点
            agvStatus.setCurrentNodeId(arrivedNode.getId());
            agvStatus.setCurrentNodeIndex(arrivedNodeIndex);
            agvStatus.setLastNodeId(agvStatus.getNodeSequence().get(arrivedNodeIndex - 1));
            agvStatus.setLastNodeIndex(arrivedNodeIndex - 1);
            agvStatus.setNextNodeId(null);
            agvStatus.setCurrentEdgeId(null);   // 停在节点上，无当前边
            agvStatus.setCurrentEdgeIndex(-1);
        } else {  // 中间节点
            agvStatus.setCurrentNodeId(arrivedNode.getId());
            agvStatus.setCurrentNodeIndex(arrivedNodeIndex);
            agvStatus.setLastNodeId(agvStatus.getNodeSequence().get(arrivedNodeIndex - 1));
            agvStatus.setLastNodeIndex(arrivedNodeIndex - 1);
            agvStatus.setNextNodeId(agvStatus.getNodeSequence().get(arrivedNodeIndex + 1));
            agvStatus.setCurrentEdgeId(null);   // 关键修正：停在节点上，清除边信息
            agvStatus.setCurrentEdgeIndex(-1);
        }
        log.debug("移动到点位:{},上一个点位id:{},下一个点位id:{},当前点位index:{},上一个点位index:{},路径点位size:{}",
                arrivedNode.getId(),
                agvStatus.getLastNodeId(),
                agvStatus.getNextNodeId(),
                agvStatus.getCurrentNodeIndex(),
                agvStatus.getLastNodeIndex(), // 此时应该是旧的currentIndex
                agvStatus.getNodeSequence().size());
        return true;
    }

    /**
     * 完成订单
     */
    public void completeOrder() {
        agvStatus.setAgvState(AgvState.FINISHED);
        agvStatus.setOrderState("FINISHED");
        agvStatus.setLastStateChange(LocalDateTime.now());
    }

    /**
     * 失败订单
     */
    public void failOrder(String error) {
        agvStatus.setAgvState(AgvState.ERROR);
        agvStatus.setOrderState("FAILED");
        agvStatus.setLastStateChange(LocalDateTime.now());
        agvStatus.getActiveErrors().add(error);
    }

    /**
     * 暂停订单
     */
    public void pauseOrder() {
        agvStatus.setAgvState(AgvState.PAUSED);
        agvStatus.setPaused(true);
        agvStatus.setLastStateChange(LocalDateTime.now());
    }

    /**
     * 恢复订单
     */
    public void resumeOrder() {
        agvStatus.setAgvState(AgvState.EXECUTING);
        agvStatus.setPaused(false);
        agvStatus.setLastStateChange(LocalDateTime.now());
    }

    /**
     * 取消订单
     */
    public void cancelOrder() {
        agvStatus.setAgvState(AgvState.IDLE);
        agvStatus.setOrderState("CANCELLED");
        agvStatus.setNodeSequence(null);
        agvStatus.setEdgeSequence(null);
        agvStatus.setLastStateChange(LocalDateTime.now());

    }

    /**
     * 开始充电
     */
    public void startCharging() {
        agvStatus.setAgvState(AgvState.CHARGING);
        agvStatus.setBatteryState(BatteryState.CHARGING);
        agvStatus.setLastStateChange(LocalDateTime.now());
    }

    /**
     * 停止充电
     */
    public void stopCharging() {
        agvStatus.setAgvState(AgvState.IDLE);
        agvStatus.setBatteryState(BatteryState.DISCHARGING);
        agvStatus.setBatteryLevel(Math.min(100.0, agvStatus.getBatteryLevel() + 20.0));// 模拟充电
        agvStatus.setLastStateChange(LocalDateTime.now());
    }

    public void updateActionState(String actionId, TaskStatus actionStatus, String resultDescription) {
        // 更新行动状态
        AgvActionState actionState = new AgvActionState();
        actionState.setActionId(actionId);
        actionState.setActionStatus(actionStatus.toString());
        actionState.setResultDescription(resultDescription);
        agvStatus.setAgvState(AgvState.EXECUTING);
//        agvStatus.setVelocity(agvStatus.getMaxSpeed() * 0.7); // 70%速度

        // 添加到agv的行动状态列表
        if (agvStatus.getActionStates() != null) {
            agvStatus.getActionStates().removeIf(as -> actionId.equals(as.getActionId()));
        }
        if (agvStatus.getActionStates() != null) {
            agvStatus.getActionStates().add(actionState);
        }
    }

    // 订单行动
    @Data
    public static class OrderAction {
        private String actionId;
        private String actionType;
        private String actionStatus = "WAITING"; // WAITING, RUNNING, FINISHED, FAILED
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String resultDescription;
        private List<Map<String, Object>> actionParameters = new ArrayList<>();
    }

}
