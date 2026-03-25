package com.jizhi.vda5050.message;

import com.jizhi.vda5050.domain.TaskCompletionPayload;
import lombok.Data;

// 任务完成消息
@Data
public class Vda5050TaskCompletionMessage {
    private Vda5050Header header;
    private TaskCompletionPayload payload;
}
