package com.sdt.agv_simulator.utils;

public class MathUtils {

    /**
     * 判断点(x, y)是否在线段(p1->p2)上，容差tolerance
     */
    public static boolean isPointOnSegment(double x, double y, double x1, double y1, double x2, double y2, double tolerance) {
        // 计算点到线段的最短距离
        double dx = x2 - x1;
        double dy = y2 - y1;

        // 线段长度为0的特殊情况
        if (dx == 0 && dy == 0) {
            double dist = Math.hypot(x - x1, y - y1);
            return dist <= tolerance;
        }
        // 计算投影参数t
        double t = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy);

        // 投影点在线段上的最近点
        double nearestX, nearestY;
        if (t < 0) {
            nearestX = x1;
            nearestY = y1;
        } else if (t > 1) {
            nearestX = x2;
            nearestY = y2;
        } else {
            nearestX = x1 + t * dx;
            nearestY = y1 + t * dy;
        }

        double distance = Math.hypot(x - nearestX, y - nearestY);
        return distance <= tolerance;
    }
}
