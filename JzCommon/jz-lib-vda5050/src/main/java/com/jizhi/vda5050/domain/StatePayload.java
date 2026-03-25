package com.jizhi.vda5050.domain;

import com.jizhi.vda5050.agv.AgvActionState;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

// 标准VDA5050状态消息Payload
@Data
public class StatePayload {
    private String agvId;
    private String orderId;
    private List<AgvActionState> actionStates; // 动作状态列表
    private String timestamp;

    public StatePayload() {
        this.actionStates = new ArrayList<>();
    }
}
