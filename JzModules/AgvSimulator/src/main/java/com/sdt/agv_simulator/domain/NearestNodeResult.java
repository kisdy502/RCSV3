package com.sdt.agv_simulator.domain;

import com.jizhi.vda5050.domain.Node;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NearestNodeResult {

    private Node node; // 最近的节点
    private double distance; // 到该节点的距离
    @Override
    public String toString() {
        return "NearestNodeResult{node=" + node + ", distance=" + distance + '}';
    }
}
