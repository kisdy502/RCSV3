package com.sdt.agv_dispatcher.domain;

import com.jizhi.vda5050.domain.MapDetailInfo;
import lombok.Data;


// 路径可视化响应数据模型
@Data
public class RouteVisualizationResponse {
    private String status;
    private String message;
    private MapDetailInfo mapData;
    private RouteInfo route;
    private RouteBounds routeBounds;

    // 成功响应的静态工厂方法
    public static RouteVisualizationResponse success(MapDetailInfo mapData,
                                                     RouteInfo route,
                                                     RouteBounds bounds) {
        RouteVisualizationResponse response = new RouteVisualizationResponse();
        response.setStatus("SUCCESS");
        response.setMessage("路径规划成功");
        response.setMapData(mapData);
        response.setRoute(route);
        response.setRouteBounds(bounds);
        return response;
    }

    // 错误响应的静态工厂方法
    public static RouteVisualizationResponse error(String message) {
        RouteVisualizationResponse response = new RouteVisualizationResponse();
        response.setStatus("ERROR");
        response.setMessage(message);
        return response;
    }
}
