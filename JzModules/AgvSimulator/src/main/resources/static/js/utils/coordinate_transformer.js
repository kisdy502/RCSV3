/**
 * 将像素坐标转换为物理坐标（SLAM坐标）
 * @param {json} mapMetaData - 地图元数据
 * @param {number} pixelX - 像素X坐标
 * @param {number} pixelY - 像素Y坐标
 * @returns {Array<number>} 物理坐标[x, y] 单位米
 */
const pixelToPhysical = (mapMetaData, pixelX, pixelY) => {
    const originX = mapMetaData.origin[0];
    const originY = mapMetaData.origin[1];
    const resolution = mapMetaData.resolution;
    const mapHeight = mapMetaData.height;

    // 正确转换：像素Y需要翻转，因为ROS Y轴向上，Web Y轴向下
    const physicalX = originX + pixelX * resolution;
    const physicalY = (mapHeight - pixelY) * resolution + originY;
    return [physicalX, physicalY];
};

/**
 * 将物理坐标（SLAM坐标）转换为像素坐标
 * @param {json} mapMetaData - 地图元数据
 * @param {number} physicalX - 物理X坐标 单位米
 * @param {number} physicalY - 物理Y坐标 单位米
 * @returns {Array<number>} 像素坐标[x, y]
 */
const physicalToPixel = (mapMetaData, physicalX, physicalY) => {
    const originX = mapMetaData.origin[0];
    const originY = mapMetaData.origin[1];
    const resolution = mapMetaData.resolution;
    const mapHeight = mapMetaData.height;

    // 正确转换：先计算相对坐标，然后翻转Y轴
    const pixelX = Math.round((physicalX - originX) / resolution);
    const pixelY = mapHeight - Math.round((physicalY - originY) / resolution);

    return [pixelX, pixelY];
};

/**
 * 将ROS角度转换为Web页面显示角度
 * @param {number} rosAngle - ROS角度（弧度），逆时针为正
 * @returns {number} Web角度（弧度），顺时针为正
 */
const rosAngleToWeb = (rosAngle) => {
    // ROS逆时针为正 → Web顺时针为正：直接取负
    return -rosAngle;
};

/**
 * 将Web页面角度转换为ROS角度
 * @param {number} webAngle - Web角度（弧度），顺时针为正
 * @returns {number} ROS角度（弧度），逆时针为正
 */
const webAngleToRos = (webAngle) => {
    // Web顺时针为正 → ROS逆时针为正：直接取负
    return -webAngle;
};

/**
 * 将四元数转换为Web页面显示角度（弧度）
 * @param {object} quaternion - 四元数 {x, y, z, w}
 * @returns {number} Web角度（弧度），顺时针为正
 */
const quaternionToWebAngle = (quaternion) => {
    // 从四元数提取偏航角（Yaw）
    const {x, y, z, w} = quaternion;

    // 四元数转欧拉角（偏航角）
    const yaw = Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));

    // 转换为Web角度（顺时针为正）
    return rosAngleToWeb(yaw);
};

/**
 * 完整的机器人位姿转换函数
 * @param {json} mapMetaData - 地图元数据
 * @param {number} rosX - ROS X坐标
 * @param {number} rosY - ROS Y坐标
 * @param {number} rosTheta - ROS角度（弧度）
 * @returns {object} Web显示坐标和角度 {pixelX, pixelY, webAngle}
 */
const convertRobotPoseToWeb = (mapMetaData, rosX, rosY, rosTheta) => {
    const [pixelX, pixelY] = physicalToPixel(mapMetaData, rosX, rosY);
    const webAngle = rosAngleToWeb(rosTheta);

    return {
        pixelX,
        pixelY,
        webAngle, // 弧度
        webAngleDegrees: webAngle * (180 / Math.PI) // 角度制，便于CSS使用
    };
};
