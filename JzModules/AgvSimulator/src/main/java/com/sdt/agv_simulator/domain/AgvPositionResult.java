package com.sdt.agv_simulator.domain;

import com.jizhi.vda5050.domain.Edge;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 根据agv的位置信息计算出agv是在点位上，还是通道上，或者未知位置
 */
@Data
@NoArgsConstructor
public class AgvPositionResult {

    private AgvPositionState agvPositionState = AgvPositionState.UNKNOWN;
    private NearestNodeResult nearestNodeResult;
    private Edge inEdgeInfo;
}
