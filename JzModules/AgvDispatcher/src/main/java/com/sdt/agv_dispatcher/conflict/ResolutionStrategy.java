package com.sdt.agv_dispatcher.conflict;

import com.jizhi.vda5050.domain.PathResult;
import lombok.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Builder
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResolutionStrategy {

    public enum StrategyType {
        PROCEED,                    // 继续行驶
        PROCEED_WITH_CAUTION,       // 谨慎通过（减速）
        STOP,                       // 停车等待
        WAIT,                       // 等待指定时间
        YIELD_AND_WAIT,             // 退让并等待
        REPLAN_PATH,               // 重新规划路径
        RELEASE_AND_REPLAN,         // 释放资源并重新规划
        ADJUST_SPEED,               // 调整速度
        COORDINATE_PASSING,         // 协调通过
        RELEASE_LAST_RESOURCE,      // 释放最后资源
        WAIT_AT_POINT  ,             // 在指定点等待
        REVERSE_AND_WAIT             // 倒退并等待
    }

    private StrategyType type;
    private double targetSpeed;           // 目标速度（比例0-1）
    private Duration waitTime;            // 等待时间
    private PathResult alternativePath;   // 替代路径
    private String waitPoint;             // 等待点
    private String yieldToAgvId;          // 退让给哪个AGV
    private String reason;                // 决策原因
    private Map<String, Object> metadata;   // 扩展信息

    // 工厂方法
    public static ResolutionStrategy proceed() {
        return new ResolutionStrategy(StrategyType.PROCEED, 1.0, null, null, null, null, "正常行驶", new HashMap<>());
    }

    public static ResolutionStrategy proceedWithCaution(double speed, String reason) {
        return new ResolutionStrategy(StrategyType.PROCEED_WITH_CAUTION, speed, null, null, null, null, reason,
                new HashMap<>());
    }

    public static ResolutionStrategy proceedWithNotification() {
        return new ResolutionStrategy(StrategyType.PROCEED_WITH_CAUTION, 0.8, null, null, null, null, "通知对方后继续",
                new HashMap<>());
    }

    public static ResolutionStrategy stop(String reason) {
        return new ResolutionStrategy(StrategyType.STOP, 0.0, null, null, null, null, reason, new HashMap<>());
    }

    public static ResolutionStrategy waitFor(int seconds) {
        return new ResolutionStrategy(StrategyType.WAIT, 0.0, Duration.ofSeconds(seconds), null, null, null,
                "等待" + seconds + "秒", new HashMap<>());
    }

    public static ResolutionStrategy yieldAndWait(String yieldToAgvId, Duration waitTime) {
        return new ResolutionStrategy(StrategyType.YIELD_AND_WAIT, 0.0, waitTime, null, null, yieldToAgvId,
                "退让给AGV " + yieldToAgvId, new HashMap<>());
    }

    public static ResolutionStrategy replan(PathResult newPath, String reason) {
        return new ResolutionStrategy(StrategyType.REPLAN_PATH, 1.0, null, newPath, null, null, reason,
                new HashMap<>());
    }

    public static ResolutionStrategy releaseAndReplan(String reason) {
        return new ResolutionStrategy(StrategyType.RELEASE_AND_REPLAN, 0.0, null, null, null, null, reason,
                new HashMap<>());
    }

    public static ResolutionStrategy adjustSpeed(double targetSpeed, String reason) {
        return new ResolutionStrategy(StrategyType.ADJUST_SPEED, targetSpeed, null, null, null, null, reason,
                new HashMap<>());
    }

    public static ResolutionStrategy coordinateSpeed(double mySpeed, double otherSpeed, String reason) {
        ResolutionStrategy strategy = new ResolutionStrategy(StrategyType.COORDINATE_PASSING, mySpeed, null, null,
                null, null, reason, new HashMap<>());
        strategy.metadata.put("otherTargetSpeed", otherSpeed);
        return strategy;
    }

    public static ResolutionStrategy releaseLastResource(String reason) {
        return new ResolutionStrategy(StrategyType.RELEASE_LAST_RESOURCE, 0.0, null, null, null, null, reason,
                new HashMap<>());
    }

    public static ResolutionStrategy waitAtPoint(String waitPoint, Duration waitTime) {
        return new ResolutionStrategy(StrategyType.WAIT_AT_POINT, 0.0, waitTime, null, waitPoint, null,
                "在" + waitPoint + "等待", new HashMap<>());
    }

    // 新增策略类型
    public static ResolutionStrategy reverseAndWait(String exitPoint, String reason) {
        return new ResolutionStrategy(
                ResolutionStrategy.StrategyType.REVERSE_AND_WAIT,
                0.0,
                Duration.ofSeconds(10),
                null,
                exitPoint,  // 倒车目标点
                null,
                reason,
                Map.of("reverse", true)
        );
    }
}