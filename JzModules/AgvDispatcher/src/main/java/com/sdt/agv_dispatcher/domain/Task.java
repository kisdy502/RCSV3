package com.sdt.agv_dispatcher.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jizhi.vda5050.agv.TaskStatus;
import com.jizhi.vda5050.domain.PathResult;
import com.mybatisflex.annotation.Id;
import com.sdt.agv_dispatcher.service.RedisResourceLockService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ToString
@EqualsAndHashCode(callSuper = false)
public class Task {

    @Id
    private String id;

    private String name;           // 任务编号

    private String taskNo;           // 任务编号

    private String agvId;            // 分配的AGV ID

    private TaskType type;           // 任务类型

    private TaskStatus status = TaskStatus.WAITING;  // 任务状态

    private String startNodeId;      // 起始节点ID

    private String endNodeId;        // 目标节点ID

    // 当前位置信息
    private String currentNodeId;    // 当前所在节点ID
    private String currentEdgeId;
    private Integer currentNodeSequenceId;
    private Integer currentEdgeSequenceId;
    private String lastNodeId; // 上一个节点
    private String lastEdgeId; // 上一个边

    private Integer priority = 1;    // 优先级 (1-10, 越高越优先)

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private LocalDateTime assignedTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completeTime;

    private Integer estimatedDuration;  // 预计耗时(秒)

    private Integer actualDuration;     // 实际耗时(秒)

    private Double distance;            // 行驶距离

    private String errorMessage;        // 错误信息

    private Integer retryCount = 0;     // 重试次数

    private Integer maxRetries = 3;     // 最大重试次数

    private String createdBy;           // 创建人

    private String callbackUrl;         // 回调URL


    private String extData;             // 扩展数据(JSON格式)

    private PathResult pathResult;        // 路径信息(不持久化到数据库)

    List<RedisResourceLockService.ResourceLockInfo> resourceLockList;

    private Map<String, Object> properties = new HashMap<>();  // 运行时属性

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * 任务类型枚举
     */
    public enum TaskType {
        TRANSPORT("物料搬运"),
        CHARGE("充电"),
        WAIT("等待"),
        RETURN("返航"),
        MAINTENANCE("维护"),
        INSPECTION("巡检");

        private final String description;

        TaskType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }



    /**
     * 生成任务ID
     */
    public void prePersist() {
        if (this.id == null) {
            this.id = generateTaskId();
        }
        if (this.taskNo == null) {
            this.taskNo = generateTaskNo();
        }
        if (this.createTime == null) {
            this.createTime = LocalDateTime.now();
        }
    }

    /**
     * 生成任务ID
     */
    private String generateTaskId() {
        return "TASK_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    /**
     * 生成任务编号
     */
    private String generateTaskNo() {
        return "T" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", (int)(Math.random() * 10000));
    }

    /**
     * 更新任务状态为执行中
     */
    public void start() {
        this.status = TaskStatus.RUNNING;
        this.startTime = LocalDateTime.now();
    }

    /**
     * 更新任务状态为完成
     */
    public void complete() {
        this.status = TaskStatus.FINISHED;
        this.completeTime = LocalDateTime.now();
        if (this.startTime != null) {
            this.actualDuration = (int) Duration.between(this.startTime, this.completeTime).getSeconds();
        }
    }

    /**
     * 更新任务状态为失败
     */
    public void fail(String errorMessage) {
        this.status = TaskStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completeTime = LocalDateTime.now();
    }

    /**
     * 更新任务状态为完成
     */
    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        this.completeTime = LocalDateTime.now();
        if (this.startTime != null) {
            this.actualDuration = (int) Duration.between(this.startTime, this.completeTime).getSeconds();
        }
    }

    /**
     * 重试任务
     */
    public boolean retry() {
        if (this.retryCount < this.maxRetries) {
            this.retryCount++;
            this.status = TaskStatus.WAITING;
            this.assignedTime = null;
            this.agvId = null;
            this.pathResult = null;
            return true;
        }
        return false;
    }

    /**
     * 验证任务是否可执行
     */
    public boolean isValid() {
        return this.status == TaskStatus.WAITING
                && this.startNodeId != null
                && this.endNodeId != null
                && !this.startNodeId.equals(this.endNodeId);
    }

    /**
     * 获取属性值
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * 设置属性值
     */
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
}
