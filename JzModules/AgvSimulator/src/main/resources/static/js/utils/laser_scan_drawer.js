// /js/utils/laser_scan_drawer.js

/**
 * 激光雷达数据绘制器
 */
export class LaserScanDrawer {
    constructor(ctx, mapInfo, zoomLevel) {
        this.ctx = ctx;
        this.mapInfo = mapInfo;
        this.zoomLevel = zoomLevel;

        // 绘制配置
        this.config = {
            pointRadius: 3,        // 点半径
            maxPointRadius: 6,      // 最大点半径（用于动态大小）
            opacity: 0.8,           // 透明度
            colorGradient: [         // 距离渐变颜色
                { pos: 0, color: '#ff0000' },   // 最近：红色
                { pos: 0.3, color: '#ee0000' }, // 较近：橙色
                { pos: 0.6, color: '#dd0000' }, // 中等：黄色
                { pos: 1, color: '#cc0000' }    // 最远：绿色
            ],
            showDistance: false,     // 是否显示距离标签
            smoothing: true,         // 是否启用平滑绘制
            maxPoints: 360           // 最大显示点数
        };
    }

    /**
     * 设置上下文
     */
    setContext(ctx) {
        this.ctx = ctx;
    }

    /**
     * 设置缩放级别
     */
    setZoomLevel(zoomLevel) {
        this.zoomLevel = zoomLevel;
    }

    /**
     * 设置地图信息
     */
    setMapInfo(mapInfo) {
        this.mapInfo = mapInfo;
    }

    /**
     * 更新配置
     */
    updateConfig(newConfig) {
        this.config = { ...this.config, ...newConfig };
    }

    /**
     * 绘制激光雷达数据
     */
    drawScan(scanData) {
        if (!scanData || !this.ctx) return;

        if (!scanData.ranges || scanData.ranges.length === 0) return;
        // 保存当前上下文状态
        this.ctx.save();

        const len= scanData.ranges.length;
        // 计算角度步长
        const angleStep = (scanData.angle_max - scanData.angle_min) / len;


        // 限制绘制的点数以提高性能
        const step = Math.max(1, Math.floor(len / this.config.maxPoints));

        // 批量绘制点以提高性能
        this.ctx.beginPath();

        for (let i = 0; i < len; i += step) {
            const range = scanData.ranges[i];

            // 过滤无效值
            if (range === null || range === undefined || range < scanData.range_min || range > scanData.range_max) {
                continue;
            }

            // 计算角度
            const angle = scanData.angle_min + i * angleStep + (scanData.pose_theta || 0);

            // 计算点的物理坐标
            const pointX = scanData.pose_x + range * Math.cos(angle);
            const pointY = scanData.pose_y + range * Math.sin(angle);

            // 转换为屏幕坐标
            const screenPos = physicalToPixel(this.mapInfo,pointX, pointY);

            // 计算距离比例用于颜色和大小
            const distanceRatio = (range - scanData.range_min) / (scanData.range_max - scanData.range_min);

            // 设置颜色
            const color = this.getGradientColor(distanceRatio);

            // 计算点的大小（根据距离调整）
            const pointRadius = this.calculatePointRadius(distanceRatio);

            // 绘制点
            this.drawPoint(screenPos[0]*this.zoomLevel, screenPos[1]*this.zoomLevel, pointRadius, color);
        }

        this.ctx.restore();
    }

    /**
     * 绘制单个点
     */
    drawPoint(x, y, radius, color) {
        this.ctx.fillStyle = color;
        this.ctx.globalAlpha = this.config.opacity;
        this.ctx.beginPath();
        this.ctx.arc(x, y, radius, 0, Math.PI * 2);
        this.ctx.fill();
    }

    /**
     * 根据距离比例获取渐变颜色
     */
    getGradientColor(ratio) {
        // 查找颜色区间
        for (let i = 0; i < this.config.colorGradient.length - 1; i++) {
            const start = this.config.colorGradient[i];
            const end = this.config.colorGradient[i + 1];

            if (ratio >= start.pos && ratio <= end.pos) {
                // 线性插值
                const t = (ratio - start.pos) / (end.pos - start.pos);
                return this.interpolateColor(start.color, end.color, t);
            }
        }
        return this.config.colorGradient[this.config.colorGradient.length - 1].color;
    }

    /**
     * 颜色插值
     */
    interpolateColor(color1, color2, t) {
        // 解析颜色
        const c1 = this.hexToRgb(color1);
        const c2 = this.hexToRgb(color2);

        // 插值
        const r = Math.round(c1.r + (c2.r - c1.r) * t);
        const g = Math.round(c1.g + (c2.g - c1.g) * t);
        const b = Math.round(c1.b + (c2.b - c1.b) * t);

        return `rgb(${r}, ${g}, ${b})`;
    }

    /**
     * 十六进制颜色转RGB
     */
    hexToRgb(hex) {
        const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
        return result ? {
            r: parseInt(result[1], 16),
            g: parseInt(result[2], 16),
            b: parseInt(result[3], 16)
        } : { r: 0, g: 255, b: 0 };
    }

    /**
     * 计算点半径
     */
    calculatePointRadius(distanceRatio) {
        // 近距离点大，远距离点小
        const radius = this.config.pointRadius * (1 + (1 - distanceRatio) * 0.5);
        return Math.min(radius, this.config.maxPointRadius);
    }

    /**
     * 清除绘制
     */
    clear() {
        // 由调用者负责清除canvas
    }
}