package com.jizhi.vda5050.domain;

import lombok.Data;
// 任务完成消息Payload
@Data
public class TaskCompletionPayload {
    private String agvId;
    private String taskId;
    private String orderId;
    private String status;     // COMPLETED, FAILED, CANCELLED
    private String message;
    private String timestamp;
    private String startTime;  // ISO8601格式
    private String endTime;    // ISO8601格式
    private Long duration;     // 毫秒
}
