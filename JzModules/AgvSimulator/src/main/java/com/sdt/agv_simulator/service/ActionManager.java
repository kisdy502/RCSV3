package com.sdt.agv_simulator.service;

import com.jizhi.vda5050.message.Vda5050OrderMessage;
import com.sdt.agv_simulator.agv.ExecutionSnapshot;
import com.sdt.agv_simulator.component.Ros2WebSocketClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动作执行管理器 - 处理顶升、下降、机械臂等动作
 */
@Slf4j
@Service
public class ActionManager {

    // 当前执行的动作
    @Getter
    private volatile Vda5050OrderMessage.Action currentAction;
    private volatile CompletableFuture<Boolean> currentActionFuture;

    // 动作执行状态
    private final Map<String, ActionExecutionContext> actionContexts = new ConcurrentHashMap<>();

    /**
     * 动作执行上下文
     */
    @Getter
    public static class ActionExecutionContext {
        private final String actionId;
        private final String actionType;
        private final long startTime;
        private volatile String status;      // PENDING, RUNNING, PAUSED, COMPLETED, FAILED
        private volatile int progressPercent;
        private volatile Object customData;   // 动作特定数据

        public ActionExecutionContext(String actionId, String actionType) {
            this.actionId = actionId;
            this.actionType = actionType;
            this.startTime = System.currentTimeMillis();
            this.status = "PENDING";
            this.progressPercent = 0;
        }
    }

    /**
     * 开始执行动作
     */
    public CompletableFuture<Boolean> startAction(Vda5050OrderMessage.Action action) {
        currentAction = action;
        ActionExecutionContext context = new ActionExecutionContext(
                action.getActionId(), action.getActionType());
        actionContexts.put(action.getActionId(), context);

        currentActionFuture = new CompletableFuture<>();

        // TODO 具体做什么动作需要解析


        return currentActionFuture;
    }

    /**
     * 暂停当前动作
     */
    public ExecutionSnapshot.ActionExecutionState pauseCurrentAction() {
        if (currentAction == null) {
            return null;
        }

        ActionExecutionContext context = actionContexts.get(currentAction.getActionId());
        if (context == null) {
            return null;
        }

        // 发送暂停命令
        try {


            return ExecutionSnapshot.ActionExecutionState.builder()
                    .actionId(currentAction.getActionId())
                    .actionType(currentAction.getActionType())
                    .status("PAUSED")
                    .progressPercent(context.progressPercent)
                    .actionContext(context.customData)
                    .build();

        } catch (Exception e) {
            log.error("暂停动作失败", e);
            return null;
        }
    }

    /**
     * 恢复动作执行
     */
    public boolean resumeAction(ExecutionSnapshot.ActionExecutionState pausedState) {
        if (pausedState == null) {
            return false;
        }

        String actionId = pausedState.getActionId();
        ActionExecutionContext context = actionContexts.get(actionId);

        if (context == null) {
            // 如果上下文丢失，重新创建
            context = new ActionExecutionContext(actionId, pausedState.getActionType());
            context.progressPercent = pausedState.getProgressPercent();
            context.customData = pausedState.getActionContext();
            actionContexts.put(actionId, context);
        }

        try {
            //TODO 发送恢复命令，带上进度信息
            pausedState.getProgressPercent();
            context.status = "RUNNING";

            log.info("动作已恢复: actionId={}, 从{}%继续", actionId, pausedState.getProgressPercent());
            return true;

        } catch (Exception e) {
            log.error("恢复动作失败", e);
            return false;
        }
    }

    /**
     * 更新动作进度（从ROS2回调）
     */
    public void updateActionProgress(String actionId, int progressPercent, Object customData) {
        ActionExecutionContext context = actionContexts.get(actionId);
        if (context != null) {
            context.progressPercent = progressPercent;
            context.customData = customData;
            log.debug("动作进度更新: actionId={}, {}%", actionId, progressPercent);
        }
    }

    /**
     * 完成动作
     */
    public void completeAction(String actionId, boolean success, String message) {
        ActionExecutionContext context = actionContexts.remove(actionId);

        if (currentActionFuture != null && !currentActionFuture.isDone()) {
            if (success) {
                currentActionFuture.complete(true);
            } else {
                currentActionFuture.completeExceptionally(new RuntimeException(message));
            }
        }

        if (success) {
            log.info("动作完成: actionId={}", actionId);
        } else {
            log.error("动作失败: actionId={}, 原因={}", actionId, message);
        }

        currentAction = null;
        currentActionFuture = null;
    }

    /**
     * 取消当前动作
     */
    public void cancelCurrentAction() {
        if (currentAction == null) {
            return;
        }

//        try {
//            ros2WebSocketClient.sendActionControl(currentAction.getActionId(), "CANCEL");
//            actionContexts.remove(currentAction.getActionId());
//
//            if (currentActionFuture != null && !currentActionFuture.isDone()) {
//                currentActionFuture.cancel(true);
//            }
//
//            log.info("动作已取消: actionId={}", currentAction.getActionId());
//        } catch (Exception e) {
//            log.error("取消动作失败", e);
//        }

        currentAction = null;
        currentActionFuture = null;
    }
}
