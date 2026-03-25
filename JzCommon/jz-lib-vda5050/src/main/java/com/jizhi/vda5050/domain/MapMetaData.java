package com.jizhi.vda5050.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 地图元数据类
public class MapMetaData {
    private float resolution; // 米/像素
    private float[] origin = new float[3]; // [x, y, yaw]
    private int width;      // 栅格宽度
    private int height;     // 栅格高度
    private Integer negate;
    private Double occupiedThresh;
    private Double freeThresh;
}
