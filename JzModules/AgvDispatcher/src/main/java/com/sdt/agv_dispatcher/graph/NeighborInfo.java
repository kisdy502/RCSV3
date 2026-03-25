package com.sdt.agv_dispatcher.graph;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NeighborInfo {
    private final String nodeId;
    private final double weight;
    private final String edgeId;


}
