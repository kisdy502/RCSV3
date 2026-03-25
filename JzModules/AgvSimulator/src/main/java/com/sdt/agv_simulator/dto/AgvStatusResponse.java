package com.sdt.agv_simulator.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jizhi.vda5050.agv.AgvPosition;
import com.jizhi.vda5050.agv.AgvStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgvStatusResponse {
    private AgvStatus agvStatus;
    private LaserScanDto laserScan;
}
