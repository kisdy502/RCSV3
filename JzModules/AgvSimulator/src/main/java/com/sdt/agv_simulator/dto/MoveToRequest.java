package com.sdt.agv_simulator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveToRequest {
    private String commandId;
    private String nodeId;
    private double x;
    private double y;
    private Double theta;
}
