//package com.sdt.agv_dispatcher.scheduler;
//
//import com.jizhi.vda5050.agv.AgvStatus;
//import com.jizhi.vda5050.domain.ResourceTimeSlot;
//import com.sdt.agv_dispatcher.conflict.ConflictDetector;
//import com.sdt.agv_dispatcher.conflict.ConflictInfo;
//import com.sdt.agv_dispatcher.conflict.ConflictResolver;
//import com.sdt.agv_dispatcher.conflict.ResolutionStrategy;
//import com.sdt.agv_dispatcher.mqtt.MqttGateway;
//import com.sdt.agv_dispatcher.service.AgvManagerService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
//@Component
//@Slf4j
//public class DynamicConflictMonitor {
//    @Autowired
//    private AgvManagerService agvManager;
//    @Autowired
//    private ResourceTimeWindowManager windowManager;
//    @Autowired
//    private ConflictDetector conflictDetector;
//    @Autowired
//    private ConflictResolver conflictResolver;
//    @Autowired
//    private MqttGateway mqttGateway;
//
//    @Scheduled(fixedDelay = 5000) // 每5秒执行一次
//    public void monitorAndAdjust() {
//        List<AgvStatus> allAgv = agvManager.getAllAgvStatus();
//        for (AgvStatus agv : allAgv) {
//            if (agv.getCurrentOrderId() == null) continue; // 无任务
//            // 获取该AGV的当前预留路径（需从缓存中读取）
//            List<ResourceTimeSlot> currentReservation = getCurrentReservation(agv.getAgvId());
//            if (currentReservation == null) continue;
//
//            // 根据AGV最新位置和速度，重新计算剩余路径的预计时间
//            List<ResourceTimeSlot> updated = recalcRemainingSlots(agv, currentReservation);
//            if (isSignificantlyDifferent(updated, currentReservation)) {
//                // 尝试更新时间窗（原子操作：释放旧的，申请新的）
//                boolean success = windowManager.tryUpdateReservation(agv.getAgvId(),
//                        currentReservation, updated);
//                if (!success) {
//                    // 更新失败，存在冲突，需要解决
//                    List<ConflictInfo> conflicts = conflictDetector.detectConflicts(agv.getAgvId(), updated);
//                    ResolutionStrategy strategy = conflictResolver.resolve(conflicts, agv.getAgvId(),
//                            getTask(agv.getCurrentOrderId()));
//                    // 执行策略（发送指令）
//                    executeStrategy(agv.getAgvId(), strategy);
//                }
//            }
//        }
//    }
//}
