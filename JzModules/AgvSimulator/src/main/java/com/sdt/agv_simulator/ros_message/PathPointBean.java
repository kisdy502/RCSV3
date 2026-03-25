package com.sdt.agv_simulator.ros_message;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * 路径点
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PathPointBean {
    private String nodeId;
    private double x;
    private double y;
    private double theta;
    private boolean isCurve = false;
    private List<BezierControlPoint> controlPoints;
}
