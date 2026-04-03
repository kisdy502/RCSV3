package com.sdt.agv_simulator.task;

import com.jizhi.vda5050.agv.AgvState;
import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.agv.TaskStatus;
import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.jizhi.vda5050.message.Vda5050OrderMessage;
import com.sdt.agv_simulator.agv.ExecutionSnapshot;
import com.sdt.agv_simulator.agv.VirtualAgv;
import com.sdt.agv_simulator.move.EdgeConvertor;
import com.sdt.agv_simulator.move.MovementException;
import com.sdt.agv_simulator.move.MovementManager;
import com.sdt.agv_simulator.mqtt.AgvMqttGateway;
import com.sdt.agv_simulator.service.MapService;
import com.sdt.agv_simulator.service.Vda5050MessageBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 订单执行器 - 负责编排移动和动作的完整流程
 * 替代原来 AgvStatusManager 里的 processRealOrder 逻辑
 */
@Slf4j
@Component
public class OrderExecutor {

    @Autowired
    private MovementManager movementManager;
    @Autowired
    private ActionManager actionManager;
    @Autowired
    private VirtualAgv virtualAgv;
    @Autowired
    private AgvMqttGateway agvMqttGateway;
    @Autowired
    private MapService mapService;
    @Autowired
    private Vda5050MessageBuilder vda5050MessageBuilder;  // 用于判断AGV是否在节点上

    // 当前执行的订单信息（用于断点续作）
    private volatile Vda5050OrderMessage currentOrderMessage;
    private volatile int currentStepIndex = 0;

    /**
     * 执行订单 - 主入口
     */
    public void executeOrder(Vda5050OrderMessage orderMessage) {
        String agvId = virtualAgv.getAgvStatus().getAgvId();

        // 构建执行计划
        OrderExecutionPlan plan = buildExecutionPlan(orderMessage);

        // 异步执行
        CompletableFuture.runAsync(() -> {
            try {
                executePlan(agvId, plan, orderMessage);
            } catch (Exception e) {
                log.error("订单执行异常", e);
                handleExecutionError(e.getMessage());
            }
        });
    }

    /**
     * 从暂停状态恢复执行
     */
    public void resumeFromSnapshot(ExecutionSnapshot snapshot, Vda5050OrderMessage orderMessage) {
        String agvId = virtualAgv.getAgvStatus().getAgvId();

        OrderExecutionPlan plan = buildExecutionPlan(orderMessage);
        // 从断点位置开始
        plan.setStartNodeIndex(snapshot.getCurrentNodeIndex());
        plan.setResumeActionState(snapshot.getActionState());

        CompletableFuture.runAsync(() -> {
            try {
                executePlan(agvId, plan, orderMessage);
            } catch (Exception e) {
                log.error("恢复订单执行异常", e);
                handleExecutionError("恢复失败: " + e.getMessage());
            }
        });
    }

    /**
     * 构建执行计划 - 将订单解析为可执行的步骤序列
     */
    private OrderExecutionPlan buildExecutionPlan(Vda5050OrderMessage order) {
        OrderExecutionPlan plan = new OrderExecutionPlan();
        plan.setOrderId(order.getOrderInformation().getOrderId());

        List<ExecutionStep> steps = new ArrayList<>();

        // 1. 构建移动步骤
        List<Vda5050OrderMessage.NodePosition> nodes = order.getNodePositions();
        for (int i = 0; i < nodes.size(); i++) {
            Vda5050OrderMessage.NodePosition nodePos = nodes.get(i);

            MoveStep moveStep = new MoveStep();
            moveStep.setStepIndex(i);
            moveStep.setNodeId(nodePos.getNodeId());
            moveStep.setTargetNode(convertToNode(nodePos, i, nodes));
            moveStep.setEnd(i == nodes.size() - 1);
            moveStep.setFirstNode(i == 0);  // 标记是否是第一个节点（用于起点判断）
            Edge edge = null;
            // 计算经过的边
            if (i == 0) {
                edge = new Edge(); //起点没有需要经过的边
            } else {
                //点的数量永远比边多一条
                edge = mapService.getEdge(virtualAgv.getAgvStatus().getEdgeSequence().get(i - 1));
            }

            moveStep.setPassedEdge(edge);
            steps.add(moveStep);

            // 2. 节点上的动作（如果有）
            if (nodePos.getActions() != null && !nodePos.getActions().isEmpty()) {
                for (var action : nodePos.getActions()) {
                    ActionStep actionStep = new ActionStep();
                    actionStep.setStepIndex(steps.size());
                    actionStep.setAction(action);
                    actionStep.setExecuteAfterNode(nodePos.getNodeId());
                    steps.add(actionStep);
                }
            }
        }

        // 3. 订单级动作（最后执行）
        if (order.getActions() != null) {
            for (var action : order.getActions()) {
                ActionStep actionStep = new ActionStep();
                actionStep.setStepIndex(steps.size());
                actionStep.setAction(action);
                actionStep.setExecuteAfterNode("ORDER_END");
                steps.add(actionStep);
            }
        }

        plan.setSteps(steps);
        return plan;
    }

    /**
     * 执行计划 - 核心编排逻辑
     */
    private void executePlan(String agvId, OrderExecutionPlan plan, Vda5050OrderMessage originalOrder) {
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        List<ExecutionStep> steps = plan.getSteps();

        // 确定起始位置（支持断点续作）
        int startIndex = plan.getStartNodeIndex();
        if (startIndex > 0) {
            log.info("从断点恢复执行: orderId={}, startIndex={}", plan.getOrderId(), startIndex);
            // 跳过已完成的步骤
            steps = steps.subList(startIndex, steps.size());
        }

        for (ExecutionStep step : steps) {
            // 检查是否暂停/取消
            if (agvStatus.getAgvState() == AgvState.PAUSED) {
                log.info("执行计划被暂停: step={}", step.getStepIndex());
                return; // 暂停时退出，等待恢复
            }

            if (agvStatus.getAgvState() == AgvState.ERROR) {
                throw new RuntimeException("AGV处于错误状态");
            }

            if (step instanceof MoveStep) {
                agvStatus.setAgvState(AgvState.MOVING);
                executeMoveStep(agvId, (MoveStep) step, agvStatus);
            } else if (step instanceof ActionStep) {
                agvStatus.setAgvState(AgvState.EXECUTING);
                executeActionStep(agvId, (ActionStep) step, agvStatus);
            }
        }

        // 全部完成
        completeOrder(agvStatus, plan.getOrderId());
    }

    /**
     * 执行移动步骤 - 使用 MovementManager，通过回调处理结果
     * <p>
     * 【关键修复】如果是第一个节点且AGV已在起点位置，直接更新状态，不执行真实移动
     */
    private void executeMoveStep(String agvId, MoveStep step, AgvStatus agvStatus) {
        log.info("执行移动步骤: nodeId={}, isEnd={}, isFirstNode={}",
                step.getNodeId(), step.isEnd(), step.isFirstNode());

        // 【关键逻辑】判断AGV是否在起点位置，如果在起点位置，直接更新状态即可，不用执行真实移动操作
        if (step.isFirstNode()) {
            boolean agvAtStartNode = vda5050MessageBuilder.calcAgvAtNode(agvStatus, step.getTargetNode());
            if (agvAtStartNode) {
                log.info("AGV已在起点位置[{}]，跳过移动，直接更新状态", step.getNodeId());
                virtualAgv.handleArrivedNode(step.getTargetNode());

                // 发送节点完成状态（如果不是最后一个点）
                if (!step.isEnd()) {
                    agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(), "RUNNING",
                            agvStatus.getOrderUpdateId(), "完成节点 " + step.getNodeId());
                }
                return;  // 跳过后续移动逻辑
            }
        }

        // 执行真实移动
        boolean success = movementManager.executeMovementSync(
                agvId,
                step.getTargetNode(),
                step.getPassedEdge(),
                step.isEnd(),
                300000 // 5分钟超时
        );

        if (!success) {
            throw new MovementException("移动到节点 " + step.getNodeId() + " 失败");
        }

        // 更新AGV状态
        virtualAgv.handleArrivedNode(step.getTargetNode());

        // 发送进度（如果不是最后一个点）
        if (!step.isEnd()) {
            agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(), "RUNNING",
                    agvStatus.getOrderUpdateId(), "完成节点 " + step.getNodeId());
        }
    }


    /**
     * 执行动作步骤
     */
    private void executeActionStep(String agvId, ActionStep step, AgvStatus agvStatus) {
        log.info("执行动作步骤: actionType={}, node={}",
                step.getAction().getActionType(), step.getExecuteAfterNode());

        boolean success = actionManager.executeActionSync(step.getAction(), 60000);

        if (!success) {
            throw new ActionException("动作执行失败: " + step.getAction().getActionId());
        }
    }

    /**
     * 计算目标节点角度（处理途经点和终点）
     */
    private Node convertToNode(Vda5050OrderMessage.NodePosition nodePos, int index,
                               List<Vda5050OrderMessage.NodePosition> allNodes) {
        Node node = new Node();
        node.setId(nodePos.getNodeId());
        node.setX(nodePos.getNodeDescription().getX());
        node.setY(nodePos.getNodeDescription().getY());

        if (index == allNodes.size() - 1) {
            // 终点：使用指定角度
            node.setTheta(nodePos.getNodeDescription().getTheta());
        } else {
            // 途经点：指向下一个点
            Vda5050OrderMessage.NodePosition next = allNodes.get(index + 1);
            double dx = next.getNodeDescription().getX() - node.getX();
            double dy = next.getNodeDescription().getY() - node.getY();
            node.setTheta(Math.atan2(dy, dx));
        }

        return node;
    }

    private Vda5050OrderMessage.Edge calculateEdge(Vda5050OrderMessage.NodePosition from,
                                                   Vda5050OrderMessage.NodePosition to,
                                                   List<Vda5050OrderMessage.Edge> edges) {
        // 从边列表中找到对应的边
        // 简化实现，实际需要根据sourceId/targetId匹配
        return edges.stream()
                .filter(e -> (e.getStartNodeId().equals(from.getNodeId()) && e.getEdgeId().equals(to.getNodeId())) ||
                        (e.getStartNodeId().equals(to.getNodeId()) && e.getEdgeId().equals(from.getNodeId())))
                .findFirst()
                .orElse(new Vda5050OrderMessage.Edge()); // 默认边
    }

    private void completeOrder(AgvStatus agvStatus, String orderId) {
        virtualAgv.completeOrder();
        agvStatus.setAgvState(AgvState.IDLE);

        agvMqttGateway.sendOrderState(agvStatus, orderId, "FINISHED",
                agvStatus.getOrderUpdateId() + 1, "订单执行完成");
        agvMqttGateway.sendTaskComplete(agvStatus, orderId, TaskStatus.FINISHED,
                "订单执行完成", agvStatus.getOrderStartTime());
    }

    private void handleExecutionError(String message) {
        AgvStatus agvStatus = virtualAgv.getAgvStatus();
        agvStatus.setOrderState("FAILED");
        agvStatus.setAgvState(AgvState.ERROR);

        agvMqttGateway.sendOrderState(agvStatus, agvStatus.getCurrentOrderId(),
                "FAILED", agvStatus.getOrderUpdateId(), message);

        virtualAgv.cancelOrder();
    }
}
