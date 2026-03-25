package com.sdt.agv_dispatcher.scheduler;

import com.jizhi.vda5050.domain.MapMetaData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoordinateTransformer {

    /**
     * 将像素坐标转换为物理坐标（SLAM坐标）
     *
     * @param pixelX 像素X坐标
     * @param pixelY 像素Y坐标
     * @return 物理坐标[x, y] 单位m
     */
    public static double[] pixelToPhysical(MapMetaData mapMetaData, int pixelX, int pixelY) {
        double originX = mapMetaData.getOrigin()[0];
        double originY = mapMetaData.getOrigin()[1];
        double resolution = mapMetaData.getResolution();
        int mapHeight = mapMetaData.getHeight();

        // 修复：像素Y需要翻转，因为ROS Y轴向上，Web Y轴向下
        double x = originX + pixelX * resolution;
        double y = originY + (mapHeight - pixelY) * resolution;

        return new double[]{x, y};
    }

    /**
     * 将物理坐标（SLAM坐标）转换为像素坐标
     *
     * @param physicalX 物理X坐标 单位m
     * @param physicalY 物理Y坐标 单位m
     * @return 像素坐标[x, y]
     */
    public static int[] physicalToPixel(MapMetaData mapMetaData, double physicalX, double physicalY) {
        double originX = mapMetaData.getOrigin()[0];
        double originY = mapMetaData.getOrigin()[1];
        double resolution = mapMetaData.getResolution();
        int mapHeight = mapMetaData.getHeight();

        // 修复：正确计算像素坐标并翻转Y轴
        int pixelX = (int) Math.round((physicalX - originX) / resolution);
        int pixelY = mapHeight - (int) Math.round((physicalY - originY) / resolution);

        return new int[]{pixelX, pixelY};
    }

    /**
     * 将网格角度转换为SLAM角度（修复角度方向）
     *
     * @param gridAngle 网格角度（弧度）
     * @return SLAM角度（弧度）
     */
    public static float gridAngleToSlam(MapMetaData mapMetaData, float gridAngle) {
        float originTheta = mapMetaData.getOrigin()[2];
        return gridAngle + originTheta;
    }

    /**
     * 新增：将SLAM角度转换为Web显示角度
     * ROS角度（逆时针为正）→ Web角度（顺时针为正）
     *
     * @param slamAngle SLAM角度（弧度）
     * @return Web角度（弧度），顺时针为正
     */
    public static float slamAngleToWeb(float slamAngle) {
        // ROS逆时针为正 → Web顺时针为正：直接取负
        return -slamAngle;
    }

    /**
     * 新增：将Web角度转换为SLAM角度
     * Web角度（顺时针为正）→ ROS角度（逆时针为正）
     *
     * @param webAngle Web角度（弧度）
     * @return SLAM角度（弧度），逆时针为正
     */
    public static float webAngleToSlam(float webAngle) {
        // Web顺时针为正 → ROS逆时针为正：直接取负
        return -webAngle;
    }

    /**
     * 新增：完整的机器人位姿转换方法
     *
     * @param rosX     ROS X坐标
     * @param rosY     ROS Y坐标
     * @param rosTheta ROS角度（弧度）
     * @return 包含像素坐标和Web角度的数组 [pixelX, pixelY, webAngle]
     */
    public static float[] convertRobotPoseToWeb(MapMetaData mapMetaData,
                                                double rosX, double rosY, float rosTheta) {
        // 转换坐标
        int[] pixelCoords = physicalToPixel(mapMetaData, rosX, rosY);
        // 转换角度：ROS → Web
        float webAngle = slamAngleToWeb(rosTheta);
        return new float[]{pixelCoords[0], pixelCoords[1], webAngle};
    }

    /**
     * 新增：从四元数提取Web角度（用于处理ROS的orientation数据）
     *
     * @param x 四元数x分量
     * @param y 四元数y分量
     * @param z 四元数z分量
     * @param w 四元数w分量
     * @return Web角度（弧度），顺时针为正
     */
    public static float quaternionToWebAngle(float x, float y, float z, float w) {
        // 从四元数提取偏航角（Yaw）
        float yaw = (float) Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));
        // 转换为Web角度
        return slamAngleToWeb(yaw);
    }

    public static void main(String[] args) {
        MapMetaData mapMetaData = new MapMetaData();
        mapMetaData.setOrigin(new float[]{-37.5f, -19f, 0f});
        mapMetaData.setResolution(0.05f);
        mapMetaData.setHeight(1025);
        mapMetaData.setWidth(703);

        CoordinateTransformer transformer = new CoordinateTransformer();
        float[] result = transformer.convertRobotPoseToWeb(mapMetaData, -33.95305479882237, 13.238349182042617,
                -0.8052941135758661f);
        System.out.printf("页面显示像素位置:%f,%f,%f", result[0], result[1], result[2]);

    }
}