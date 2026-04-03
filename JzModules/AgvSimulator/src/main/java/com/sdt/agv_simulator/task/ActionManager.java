package com.sdt.agv_simulator.task;

import com.jizhi.vda5050.message.Vda5050ActionParameter;
import com.jizhi.vda5050.message.Vda5050OrderMessage;
import com.sdt.agv_simulator.agv.ExecutionSnapshot;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 动作执行管理器 - 处理顶升、下降、机械臂等动作
 *
 * 注意：当前为模拟实现，1秒后返回成功
 * 后续接入真实硬件时，替换 executeActionInternal 方法即可
 */
@Slf4j
@Service
public class ActionManager {

    // 动作执行线程池
    private ThreadPoolTaskExecutor actionExecutor;

    // 当前执行的动作上下文
    @Getter
    private volatile ActionExecutionContext currentContext;

    // 所有动作执行记录（按actionId索引）
    private final Map<String, ActionExecutionContext> actionHistory = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化动作执行线程池
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("action-");
        executor.initialize();
        this.actionExecutor = executor;

        log.info("ActionManager 初始化完成");
    }

    /**
     * 动作执行上下文
     */
    @Getter
    public static class ActionExecutionContext {
        private final String executionId;      // 本次执行实例ID
        private final String actionId;         // VDA5050动作ID
        private final String actionType;       // 动作类型：LIFT, LOWER, etc.
        private final List<Vda5050ActionParameter> parameters;  // 动作参数（VDA5050格式）
        private final LocalDateTime startTime;
        private volatile String status;        // PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
        private volatile int progressPercent;  // 0-100
        private volatile String resultMessage;
        private volatile Object customData;    // 硬件特定数据
        private volatile CompletableFuture<Boolean> future;
        private volatile long elapsedTimeMs;   // 已执行时间

        public ActionExecutionContext(String actionId, String actionType,
                                      List<Vda5050ActionParameter> parameters) {
            this.executionId = UUID.randomUUID().toString().substring(0, 8);
            this.actionId = actionId;
            this.actionType = actionType;
            this.parameters = parameters;
            this.startTime = LocalDateTime.now();
            this.status = "PENDING";
            this.progressPercent = 0;
            this.elapsedTimeMs = 0;
        }
    }

    /**
     * 异步执行动作 - 立即返回Future，通过回调通知结果
     *
     * @param action VDA5050动作定义
     * @return 执行结果Future
     */
    public CompletableFuture<Boolean> executeAction(Vda5050OrderMessage.Action action) {
        if (action == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("动作不能为空"));
        }

        // 如果有正在执行的动作，先取消
        if (currentContext != null && !currentContext.getFuture().isDone()) {
            log.warn("取消之前的动作: {}", currentContext.getActionId());
            cancelCurrentAction();
        }

        // 创建新的执行上下文
        ActionExecutionContext context = new ActionExecutionContext(
                action.getActionId(),
                action.getActionType(),
                action.getActionParameters()  // 这是 List<ActionParameter>
        );

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        context.future = future;
        this.currentContext = context;
        this.actionHistory.put(action.getActionId(), context);

        // 异步执行
        actionExecutor.submit(() -> {
            try {
                executeActionInternal(context);
            } catch (Exception e) {
                log.error("动作执行异常: actionId={}", action.getActionId(), e);
                context.status = "FAILED";
                context.resultMessage = e.getMessage();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * 同步执行动作 - 阻塞直到完成或超时
     *
     * @param action 动作定义
     * @param timeoutMs 超时时间（毫秒）
     * @return 是否成功
     * @throws RuntimeException 执行失败或超时
     */
    public boolean executeActionSync(Vda5050OrderMessage.Action action, long timeoutMs) throws RuntimeException {
        try {
            return executeAction(action).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            cancelCurrentAction();
            throw new RuntimeException("动作执行超时: " + action.getActionId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelCurrentAction();
            throw new RuntimeException("动作被中断: " + action.getActionId());
        } catch (Exception e) {
            throw new RuntimeException("动作执行失败: " + action.getActionId() + ", " + e.getMessage(), e);
        }
    }

    /**
     * 暂停当前动作
     *
     * @return 暂停状态，用于后续恢复；如果没有执行中的动作返回null
     */
    public ExecutionSnapshot.ActionExecutionState pauseCurrentAction() {
        if (currentContext == null) {
            log.debug("没有正在执行的动作，无需暂停");
            return null;
        }

        String status = currentContext.getStatus();
        if ("COMPLETED".equals(status) ||
                "FAILED".equals(status) ||
                "CANCELLED".equals(status)) {
            log.debug("动作已结束，无法暂停: status={}", status);
            return null;
        }

        // 记录暂停状态
        currentContext.status = "PAUSED";
        currentContext.elapsedTimeMs = System.currentTimeMillis() -
                java.sql.Timestamp.valueOf(currentContext.getStartTime()).getTime();

        // 使用 Builder 模式创建状态对象
        ExecutionSnapshot.ActionExecutionState state = ExecutionSnapshot.ActionExecutionState.builder()
                .actionId(currentContext.getActionId())
                .actionType(currentContext.getActionType())
                .status(currentContext.getStatus())
                .progress(currentContext.getProgressPercent())
                .build();

        log.info("动作已暂停: actionId={}, 进度={}%, 已执行{}ms",
                currentContext.getActionId(),
                currentContext.getProgressPercent(),
                currentContext.getElapsedTimeMs());

        return state;
    }

    /**
     * 恢复动作执行
     *
     * @param pausedState 暂停时保存的状态
     * @return 是否成功恢复
     */
    public boolean resumeAction(ExecutionSnapshot.ActionExecutionState pausedState) {
        if (pausedState == null) {
            log.warn("暂停状态为空，无法恢复");
            return false;
        }

        // 从pausedState中获取属性（假设有getter方法）
        String actionId = pausedState.getActionId();
        String actionType = pausedState.getActionType();
        int progressPercent = pausedState.getProgress();

        // 查找原上下文或创建新的
        ActionExecutionContext context = actionHistory.get(actionId);
        if (context == null) {
            log.warn("未找到动作历史记录，创建新上下文: actionId={}", actionId);
            // 创建新的执行上下文，继承暂停时的状态
            context = new ActionExecutionContext(actionId, actionType, null);
            context.progressPercent = progressPercent;
            actionHistory.put(actionId, context);
        }

        // 检查当前是否有其他动作在执行
        if (currentContext != null && currentContext != context &&
                !currentContext.getFuture().isDone()) {
            log.warn("有其他动作在执行，先取消: {}", currentContext.getActionId());
            cancelCurrentAction();
        }

        this.currentContext = context;
        context.status = "RUNNING";

        // 重新提交执行（从当前进度继续）
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        context.future = future;

        // 使用 final 局部变量
        final ActionExecutionContext finalContext = context;

        actionExecutor.submit(() -> {
            try {
                // 模拟从暂停点继续执行
                resumeActionInternal(finalContext, progressPercent);
            } catch (Exception e) {
                log.error("动作恢复执行异常: actionId={}", actionId, e);
                finalContext.status = "FAILED";
                future.completeExceptionally(e);
            }
        });

        log.info("动作已恢复: actionId={}, 从{}%继续", actionId, progressPercent);
        return true;
    }

    /**
     * 取消当前动作
     */
    public void cancelCurrentAction() {
        if (currentContext == null) {
            return;
        }

        ActionExecutionContext context = currentContext;

        // 如果已完成，无需取消
        if (context.getFuture().isDone()) {
            return;
        }

        context.status = "CANCELLED";
        context.resultMessage = "用户取消";

        // 取消Future
        if (!context.getFuture().isDone()) {
            context.getFuture().cancel(true);
        }

        log.info("动作已取消: actionId={}, executionId={}",
                context.getActionId(), context.getExecutionId());

        // 清理当前上下文（但保留在历史记录中）
        if (currentContext == context) {
            currentContext = null;
        }
    }

    /**
     * 更新动作进度（外部调用，如硬件回调）
     *
     * @param actionId 动作ID
     * @param progressPercent 进度 0-100
     */
    public void updateProgress(String actionId, int progressPercent) {
        ActionExecutionContext context = actionHistory.get(actionId);
        if (context != null) {
            context.progressPercent = Math.min(100, Math.max(0, progressPercent));
            log.debug("动作进度更新: actionId={} -> {}%", actionId, progressPercent);
        }
    }

    /**
     * 标记动作完成（外部调用，如硬件回调）
     *
     * @param actionId 动作ID
     * @param success 是否成功
     * @param message 结果消息
     */
    public void completeAction(String actionId, boolean success, String message) {
        ActionExecutionContext context = actionHistory.get(actionId);
        if (context == null) {
            log.warn("完成未知动作: actionId={}", actionId);
            return;
        }

        if (success) {
            context.status = "COMPLETED";
            context.progressPercent = 100;
            context.resultMessage = message;
            if (!context.getFuture().isDone()) {
                context.getFuture().complete(true);
            }
            log.info("动作完成: actionId={}, executionId={}", actionId, context.getExecutionId());
        } else {
            context.status = "FAILED";
            context.resultMessage = message;
            if (!context.getFuture().isDone()) {
                context.getFuture().completeExceptionally(
                        new RuntimeException("动作失败: " + actionId + ", " + message));
            }
            log.error("动作失败: actionId={}, reason={}", actionId, message);
        }

        // 清理当前上下文引用
        if (currentContext == context) {
            currentContext = null;
        }
    }

    /**
     * 获取动作执行历史
     */
    public Map<String, ActionExecutionContext> getActionHistory() {
        return new ConcurrentHashMap<>(actionHistory);
    }

    /**
     * 清理历史记录（防止内存泄漏）
     */
    public void clearHistory(String actionId) {
        actionHistory.remove(actionId);
    }

    // ==================== 模拟硬件执行方法（后续替换为真实实现） ====================

    /**
     * 内部执行方法 - 模拟硬件执行
     *
     * TODO: 接入真实硬件时，替换此方法
     */
    private void executeActionInternal(ActionExecutionContext context) throws Exception {
        String actionType = context.getActionType();
        log.info("开始执行动作[模拟]: executionId={}, actionId={}, type={}",
                context.getExecutionId(), context.getActionId(), actionType);

        context.status = "RUNNING";

        // 模拟执行过程：分10个阶段，每100ms更新一次进度，总共1秒
        int stages = 10;
        int stageDurationMs = 100;  // 每阶段100ms，总共1秒

        for (int i = 1; i <= stages; i++) {
            // 检查是否被取消
            if ("CANCELLED".equals(context.getStatus())) {
                log.info("动作执行被取消: executionId={}", context.getExecutionId());
                throw new InterruptedException("动作被取消");
            }

            // 检查是否被暂停
            if ("PAUSED".equals(context.getStatus())) {
                log.info("动作执行被暂停，等待恢复: executionId={}", context.getExecutionId());
                // 阻塞直到状态改变（实际硬件可能需要发送暂停指令）
                while ("PAUSED".equals(context.getStatus())) {
                    Thread.sleep(100);
                }
                log.info("动作恢复执行: executionId={}", context.getExecutionId());
            }

            // 更新进度
            context.progressPercent = i * 10;
            log.debug("动作执行进度: executionId={} -> {}%",
                    context.getExecutionId(), context.progressPercent);

            // 模拟硬件执行时间
            Thread.sleep(stageDurationMs);
        }

        // 执行完成
        context.status = "COMPLETED";
        context.progressPercent = 100;
        context.resultMessage = "模拟执行成功";

        if (!context.getFuture().isDone()) {
            context.getFuture().complete(true);
        }

        log.info("动作执行完成[模拟]: executionId={}, actionId={}",
                context.getExecutionId(), context.getActionId());
    }

    /**
     * 恢复执行方法 - 模拟从暂停点继续
     *
     * TODO: 接入真实硬件时，替换此方法
     */
    private void resumeActionInternal(ActionExecutionContext context, int resumeFromPercent) throws Exception {
        log.info("恢复执行动作[模拟]: executionId={}, 从{}%继续",
                context.getExecutionId(), resumeFromPercent);

        int remainingStages = (100 - resumeFromPercent) / 10;
        if (remainingStages <= 0) {
            remainingStages = 1;
        }
        int stageDurationMs = 100;

        for (int i = 1; i <= remainingStages; i++) {
            if ("CANCELLED".equals(context.getStatus())) {
                throw new InterruptedException("动作被取消");
            }

            context.progressPercent = resumeFromPercent + (i * 100 / remainingStages);
            if (context.progressPercent > 100) {
                context.progressPercent = 100;
            }
            Thread.sleep(stageDurationMs);
        }

        context.status = "COMPLETED";
        context.progressPercent = 100;
        context.resultMessage = "恢复执行成功";

        if (!context.getFuture().isDone()) {
            context.getFuture().complete(true);
        }

        log.info("动作恢复执行完成[模拟]: executionId={}", context.getExecutionId());
    }
}