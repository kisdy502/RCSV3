package com.jizhi.vda5050.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vda5050InstantActions {
    private Vda5050Header header;
    private List<Vda5050Action> actions;

    // 便捷方法：获取第一个动作类型
    public String getPrimaryActionType() {
        return actions != null && !actions.isEmpty() ? actions.get(0).getActionType() : null;
    }
}
