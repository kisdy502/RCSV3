/**
 * 地图绘制器
 */
export class MapDrawer {
    constructor(ctx, mapInfo, zoomLevel) {
        this.ctx = ctx;
        this.mapInfo = mapInfo;
        this.zoomLevel = zoomLevel;

        // 绘图状态
        this.canvas = null;
        this.bounds = null;
        this.dataView = null;

        // 配置
        this.config = {
            showCoordinates: true,
            gridColor: 'rgba(75, 75, 75, 1)',
            gridSize: 100,

            // 节点配置
            nodeColors: {
                STATION: '#00868B',
                INTERSECTION: '#9b59b6',
                PATH: '#3498db'
            },
            nodeRadius: {
                STATION: 8,
                INTERSECTION: 6,
                PATH: 6
            },

            // 边配置
            edgeColor: '#88888F',
            edgeWidth: 2,
            routeColor: '#139d4f',
            routeWidth: 4,

            // 箭头配置
            arrowHeadLength: 10,
            arrowColor: '#95a5a6',

            // 图例配置
            legendBackground: 'rgba(255, 255, 255, 0.85)',
            legendBorderColor: 'rgba(0, 0, 0, 0.2)',
            legendTextColor: '#2c3e50',
            legendLineHeight: 15,
            legendPadding: 10,

            debugMode: true
        };

        // 数据
        this.nodes = [];
        this.edges = [];
        this.currentRoute = null;
        this.agvStatusList = [];
    }

    // ================== 设置方法 ==================

    setContext(ctx) {
        this.ctx = ctx;
    }

    setZoomLevel(zoomLevel) {
        this.zoomLevel = zoomLevel;
    }

    setMapInfo(mapInfo) {
        this.mapInfo = mapInfo;
    }

    setDataView(dataView) {
        this.dataView = dataView;
    }

    setBounds(bounds) {
        this.bounds = bounds;
    }

    setCanvas(canvas) {
        this.canvas = canvas;
    }

    setNodes(nodes) {
        this.nodes = nodes || [];
    }

    setEdges(edges) {
        this.edges = edges || [];
    }

    setCurrentRoute(route) {
        this.currentRoute = route;
    }

    setAgvStatusList(list) {
        this.agvStatusList = list;
    }

    updateConfig(newConfig) {
        this.config = {...this.config, ...newConfig};
    }

    // ================== 主要绘制方法 ==================

    drawMap(dataView, nodes, edges, currentRoute) {
        if (!this.ctx || !this.canvas) return;

        this.setDataView(dataView);
        this.setNodes(nodes);
        this.setEdges(edges);
        this.setCurrentRoute(currentRoute);

        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

        if (!this.dataView) {
            this.drawPlaceholder();
            return;
        }

        convertPGMtoCanvas(this.canvas, this.dataView, this.zoomLevel)

        if (this.nodes.length === 0) {
            this.drawPlaceholder();
            return;
        }

        this.drawGrid();
        this.drawLegend();
        this.drawEdges();
        this.drawRoute();
        this.drawNodes();
        this.drawControlPoints();
    }

    // ================== 核心绘制方法 ==================

    drawPlaceholder() {
        if (!this.ctx || !this.canvas) return;

        this.ctx.fillStyle = '#666';
        this.ctx.font = '16px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.fillText(
            '没有地图数据!',
            this.canvas.width / 2,
            this.canvas.height / 2
        );
    }

    drawGrid() {
        if (!this.ctx || !this.bounds) return;

        this.ctx.save();
        this.ctx.strokeStyle = this.config.gridColor;
        this.ctx.lineWidth = 0.5;

        const gridSize = this.config.gridSize;
        const startX = Math.floor(this.bounds.minX / gridSize) * gridSize;
        const startY = Math.floor(this.bounds.minY / gridSize) * gridSize;
        const endX = Math.ceil(this.bounds.maxX / gridSize) * gridSize;
        const endY = Math.ceil(this.bounds.maxY / gridSize) * gridSize;

        // 绘制垂直线
        for (let x = startX; x <= endX; x += gridSize) {
            const screenX = Math.round(x * this.zoomLevel);
            this.ctx.beginPath();
            this.ctx.moveTo(screenX, this.bounds.minY * this.zoomLevel);
            this.ctx.lineTo(screenX, endY * this.zoomLevel);
            this.ctx.stroke();

            if (this.config.showCoordinates && x !== 0) {
                this.ctx.fillStyle = 'rgba(0, 135, 0, 1)';
                this.ctx.font = '10px Arial';
                this.ctx.textAlign = 'center';
                this.ctx.fillText(x.toString(), screenX, this.bounds.minY * this.zoomLevel + 5);
            }
        }

        // 绘制水平线
        for (let y = startY; y <= endY; y += gridSize) {
            const screenY = y * this.zoomLevel;
            this.ctx.beginPath();
            this.ctx.moveTo(this.bounds.minX * this.zoomLevel, screenY);
            this.ctx.lineTo(endX * this.zoomLevel, screenY);
            this.ctx.stroke();

            if (this.config.showCoordinates && y !== 0) {
                this.ctx.fillStyle = 'rgba(135, 0, 0, 1)';
                this.ctx.font = '10px Arial';
                this.ctx.textAlign = 'center';
                this.ctx.fillText(y.toString(), this.bounds.minX * this.zoomLevel + 10, screenY);
            }
        }

        this.ctx.restore();
    }

    drawLegend() {
        if (!this.ctx || !this.canvas) return;

        const legends = [
            {color: this.config.nodeColors.STATION, text: '站点 (STATION)'},
            {color: this.config.nodeColors.INTERSECTION, text: '交叉点 (INTERSECTION)'},
            {color: this.config.nodeColors.PATH, text: '通道点 (PATH)'},
            {color: this.config.routeColor, text: '规划路线'},
            {color: this.config.arrowColor, text: '通道 (双向)'}
        ];

        this.ctx.font = '10px Arial';
        const lineHeight = this.config.legendLineHeight;
        const colorBlockWidth = 12;
        const textPadding = 5;
        const itemPadding = 5;

        // 计算图例尺寸
        let maxTextWidth = 0;
        legends.forEach(legend => {
            const width = this.ctx.measureText(legend.text).width;
            if (width > maxTextWidth) maxTextWidth = width;
        });

        const legendWidth = colorBlockWidth + textPadding + maxTextWidth;
        const legendHeight = legends.length * lineHeight + (legends.length - 1) * itemPadding;

        // 计算位置
        const padding = this.config.legendPadding;
        const startX = this.canvas.width - legendWidth - padding;
        const startY = padding;

        // 绘制背景
        this.ctx.save();
        this.ctx.fillStyle = this.config.legendBackground;
        this.ctx.strokeStyle = this.config.legendBorderColor;
        this.ctx.lineWidth = 1;
        this.ctx.fillRect(startX - 5, startY - 5, legendWidth + 10, legendHeight + 10);
        this.ctx.strokeRect(startX - 5, startY - 5, legendWidth + 10, legendHeight + 10);
        this.ctx.restore();

        // 绘制图例项
        legends.forEach((legend, index) => {
            const yPos = startY + index * (lineHeight + itemPadding);

            // 绘制颜色块
            this.ctx.fillStyle = legend.color;
            this.ctx.fillRect(startX, yPos, colorBlockWidth, lineHeight - 5);

            // 绘制文本
            this.ctx.fillStyle = this.config.legendTextColor;
            this.ctx.textAlign = 'left';
            this.ctx.textBaseline = 'middle';
            this.ctx.fillText(
                legend.text,
                startX + colorBlockWidth + textPadding,
                yPos + (lineHeight - 5) / 2
            );
        });
    }

    drawRoute() {
        if (!this.currentRoute || this.currentRoute.nodes.length < 2) return;
        if (!this.currentRoute.edges || this.currentRoute.edges.length === 0) {
            return;
        }

        this.ctx.save();
        this.ctx.strokeStyle = this.config.routeColor;
        this.ctx.lineWidth = this.config.routeWidth;
        this.ctx.setLineDash([6, 4]);

        // 使用边序列绘制路线
        for (let i = 0; i < this.currentRoute.edges.length; i++) {
            const edgeId = this.currentRoute.edges[i];
            const edge = this.edges.find(e => e.id === edgeId);

            if (!edge) continue;

            const sourceNode = this.nodes.find(n => n.id === edge.sourceId);
            const targetNode = this.nodes.find(n => n.id === edge.targetId);

            if (!sourceNode || !targetNode) continue;

            this.drawRouteEdge(edge, sourceNode, targetNode);
        }

        this.ctx.restore();
    }

    /**
     * 绘制单个路线边
     */
    drawRouteEdge(edge, sourceNode, targetNode) {
        const sourcePos = physicalToPixel(this.mapInfo, sourceNode.x, sourceNode.y);
        const targetPos = physicalToPixel(this.mapInfo, targetNode.x, targetNode.y);

        const startX = sourcePos[0] * this.zoomLevel;
        const startY = sourcePos[1] * this.zoomLevel;
        const endX = targetPos[0] * this.zoomLevel;
        const endY = targetPos[1] * this.zoomLevel;

        this.ctx.beginPath();

        if (edge.type === 'CURVE' && edge.controlPoints) {
            // 绘制曲线边
            this.drawCurveRoute(edge, startX, startY, endX, endY);
        } else {
            // 绘制直线边
            this.ctx.moveTo(startX, startY);
            this.ctx.lineTo(endX, endY);
        }

        this.ctx.stroke();
    }

    /**
     * 绘制曲线路线
     */
    drawCurveRoute(edge, startX, startY, endX, endY) {
        const controlPoints = edge.controlPoints || [];

        if (controlPoints.length === 1) {
            // 二阶贝塞尔曲线
            const cpPos = physicalToPixel(this.mapInfo, controlPoints[0].x, controlPoints[0].y);
            const cpX = cpPos[0] * this.zoomLevel;
            const cpY = cpPos[1] * this.zoomLevel;

            this.ctx.moveTo(startX, startY);
            this.ctx.quadraticCurveTo(cpX, cpY, endX, endY);
        } else if (controlPoints.length === 2) {
            // 三阶贝塞尔曲线
            const cp1Pos = physicalToPixel(this.mapInfo, controlPoints[0].x, controlPoints[0].y);
            const cp2Pos = physicalToPixel(this.mapInfo, controlPoints[1].x, controlPoints[1].y);
            const cp1X = cp1Pos[0] * this.zoomLevel;
            const cp1Y = cp1Pos[1] * this.zoomLevel;
            const cp2X = cp2Pos[0] * this.zoomLevel;
            const cp2Y = cp2Pos[1] * this.zoomLevel;

            this.ctx.moveTo(startX, startY);
            this.ctx.bezierCurveTo(cp1X, cp1Y, cp2X, cp2Y, endX, endY);
        } else {
            // 回退到直线
            this.ctx.moveTo(startX, startY);
            this.ctx.lineTo(endX, endY);
        }
    }

    drawNodes() {
        if (!this.ctx || this.nodes.length === 0) return;

        this.nodes.forEach(node => {
            const nodePos = convertRobotPoseToWeb(this.mapInfo, node.x, node.y, node.theta);
            const x = nodePos.pixelX * this.zoomLevel;
            const y = nodePos.pixelY * this.zoomLevel;
            const angle = nodePos.webAngle;

            const nodeColor = this.config.nodeColors[node.type] || this.config.nodeColors.PATH;
            const nodeRadius = this.config.nodeRadius[node.type] || this.config.nodeRadius.PATH;

            // 高亮规划路线上的节点
            if (this.currentRoute && this.currentRoute.nodes.includes(node.id)) {
                this.ctx.fillStyle = '#2ecc71';
                this.ctx.beginPath();
                this.ctx.arc(x, y, nodeRadius + 2, 0, Math.PI * 2);
                this.ctx.fill();
            }

            // 绘制节点
            this.ctx.fillStyle = nodeColor;
            this.ctx.beginPath();
            this.ctx.arc(x, y, nodeRadius, 0, Math.PI * 2);
            this.ctx.fill();

            // 绘制节点ID
            this.ctx.fillStyle = '#cc206e';
            this.ctx.font = '12px Arial';
            this.ctx.textAlign = 'center';
            this.ctx.fillText(node.id, x, y - 14);

            // 绘制站点名称
            if (node.name && node.type === 'STATION') {
                this.ctx.font = '10px Arial';
                this.ctx.fillText(node.name, x, y + 16);
            }

            // 绘制节点方向箭头
            this.drawNodeDirectionArrow(x, y, angle, nodeRadius, nodeColor);
        });
    }

    drawAgvStatusList() {
        if (!this.agvStatusList || this.agvStatusList.length === 0) return;

        const agvWidth = 48;
        const agvHeight = 32;

        for (let i = 0; i < this.agvStatusList.length; i++) {
            let agv = this.agvStatusList[i];
            // 计算AGV的屏幕坐标
            let screenX, screenY;
            let agvAngle = 0;

            if (agv.currentPosition && agv.currentPosition.x !== undefined && agv.currentPosition.y !== undefined) {
                const agvPos = convertRobotPoseToWeb(
                    this.mapInfo,
                    agv.currentPosition.x,
                    agv.currentPosition.y,
                    agv.currentPosition?.theta || 0
                );
                screenX = agvPos.pixelX * this.zoomLevel;
                screenY = agvPos.pixelY * this.zoomLevel;
                agvAngle = agvPos.webAngle;
            } else if (agv.currentNodeId) {
                const node = this.nodes.find(n => n.id === agv.currentNodeId);
                if (node) {
                    const agvPos = convertRobotPoseToWeb(this.mapInfo, node.x, node.y, node.theta);
                    screenX = agvPos.pixelX * this.zoomLevel;
                    screenY = agvPos.pixelY * this.zoomLevel;
                    agvAngle = agvPos.webAngle;
                } else {
                    continue; // 节点不存在
                }
            } else {
                continue; // 没有位置信息
            }

            // 根据AGV状态选择颜色
            const agvColor = this.getAgvColor(agv.agvState);
            this.ctx.save();
            this.ctx.translate(screenX, screenY);
            this.ctx.rotate(agvAngle);

            // 绘制AGV底盘
            this.ctx.fillStyle = "#F0F0F000";
            this.ctx.fillRect(-agvWidth / 2, -agvHeight / 2, agvWidth, agvHeight);

            // 绘制AGV边框
            this.ctx.strokeStyle = agvColor;
            this.ctx.lineWidth = 1;
            this.ctx.strokeRect(-agvWidth / 2, -agvHeight / 2, agvWidth, agvHeight);

            // 绘制方向指示器
            this.ctx.strokeStyle = agvColor;
            this.ctx.beginPath();
            this.ctx.moveTo(agvWidth / 2, agvHeight / 2);
            this.ctx.lineTo(agvWidth / 2, -agvHeight / 2);
            this.ctx.lineTo(agvWidth / 2 + agvWidth / 4, 0);
            this.ctx.closePath();
            this.ctx.stroke();

            // 绘制AGV中心点
            this.ctx.fillStyle = 'orange';
            const pointWidth = 8;
            const pointHeight = 6;
            this.ctx.fillRect(-pointWidth / 2, -pointHeight / 2, pointWidth, pointHeight);

            // 绘制电池电量和状态信息
            this.drawAgvBattery(agv, 0, 0, agvHeight / 2 + 10);
            this.drawAgvState(agv, 0, 0, agvHeight / 2 + 10);
            this.ctx.restore();
        }


    }

    // ================== 辅助绘制方法 ==================

    drawArrow(fromX, fromY, toX, toY, color = this.config.arrowColor) {
        const headLength = this.config.arrowHeadLength;
        const dx = toX - fromX;
        const dy = toY - fromY;
        const angle = Math.atan2(dy, dx);

        this.ctx.save();
        this.ctx.strokeStyle = color;
        this.ctx.fillStyle = color;

        this.ctx.translate(toX, toY);
        this.ctx.rotate(angle);

        this.ctx.beginPath();
        this.ctx.moveTo(0, 0);
        this.ctx.lineTo(-headLength, -headLength / 2);
        this.ctx.lineTo(-headLength, headLength / 2);
        this.ctx.closePath();
        this.ctx.fill();
        this.ctx.restore();
    }

    drawNodeDirectionArrow(centerX, centerY, theta, nodeRadius, color) {
        this.ctx.save();
        this.ctx.strokeStyle = color;
        this.ctx.fillStyle = color;
        this.ctx.lineWidth = 1;

        const arrowLength = nodeRadius * 3;
        const endX = centerX + Math.cos(theta) * arrowLength;
        const endY = centerY + Math.sin(theta) * arrowLength;

        const arrowHeadLength = nodeRadius * 1.2;
        const arrowAngle = Math.PI / 6;

        const arrowLeftX = endX - Math.cos(theta - arrowAngle) * arrowHeadLength;
        const arrowLeftY = endY - Math.sin(theta - arrowAngle) * arrowHeadLength;
        const arrowRightX = endX - Math.cos(theta + arrowAngle) * arrowHeadLength;
        const arrowRightY = endY - Math.sin(theta + arrowAngle) * arrowHeadLength;

        // 绘制箭头线
        this.ctx.beginPath();
        this.ctx.moveTo(centerX, centerY);
        this.ctx.lineTo(endX, endY);
        this.ctx.stroke();

        // 绘制箭头头部
        this.ctx.beginPath();
        this.ctx.moveTo(endX, endY);
        this.ctx.lineTo(arrowLeftX, arrowLeftY);
        this.ctx.lineTo(arrowRightX, arrowRightY);
        this.ctx.closePath();
        this.ctx.fill();

        this.ctx.restore();
    }

    drawAgvBattery(agv, screenX, screenY, offsetY) {
        if (agv.batteryLevel === undefined) return;

        const batteryWidth = 20;
        const batteryHeight = 8;
        const batteryX = screenX - batteryWidth / 2;
        const batteryY = screenY - offsetY;

        // 电池外壳
        this.ctx.strokeStyle = '#2c3e50';
        this.ctx.lineWidth = 1;
        this.ctx.strokeRect(batteryX, batteryY, batteryWidth, batteryHeight);

        // 电池电量
        const batteryLevel = Math.max(0, Math.min(100, agv.batteryLevel));
        const fillWidth = (batteryWidth - 2) * (batteryLevel / 100);

        // 电量颜色
        let batteryColor;
        if (batteryLevel > 60) batteryColor = '#27ae6066';
        else if (batteryLevel > 20) batteryColor = '#f39c1266';
        else batteryColor = '#e74c3c66';

        this.ctx.fillStyle = batteryColor;
        this.ctx.fillRect(batteryX + 1, batteryY + 1, fillWidth, batteryHeight - 2);

        // 电量百分比
        this.ctx.fillStyle = batteryColor;
        this.ctx.font = 'bold 12px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.fillText(`${Math.round(batteryLevel)}%`, screenX, batteryY + batteryHeight - 14);
    }

    drawAgvState(agv, screenX, screenY, offsetY) {
        const agvColor = this.getAgvColor(agv.agvState);
        this.ctx.fillStyle = agvColor;
        this.ctx.font = '10px Arial';
        this.ctx.textAlign = 'center';
        if (agv.agvState == "EXECUTING") {
            this.ctx.fillText(agv.agvId + " " + agv.velocity.toFixed(1) + "m/s", screenX, screenY + offsetY);
        } else {
            this.ctx.fillText(agv.agvId + " " + agv.agvState, screenX, screenY + offsetY);
        }
    }

    getAgvColor(state) {
        const colors = {
            'IDLE': '#1b86ce',
            'EXECUTING': '#27ae60',
            'CHARGING': '#f39c12',
            'ERROR': '#ab1b0d',
            'PAUSED': '#95a5a6',
            'EMERGENCY': '#9b59b6'
        };
        return colors[state] || '#bdc3c7';
    }

    // 绘制边
    drawEdges() {
        if (!this.ctx || this.edges.length === 0) return;

        this.ctx.save();
        this.ctx.lineWidth = this.config.edgeWidth;

        // 定义不同曲线类型的样式
        const edgeStyles = {
            'STRAIGHT': {
                color: '#888888',
                lineWidth: 2
            },
            'CURVE': {
                color: '#7b68ee',
                lineWidth: 2.5
            },
            'ELEVATION': {
                color: '#ff7f50',
                lineWidth: 3
            }
        };

        this.edges.forEach(edge => {
            const source = this.nodes.find(n => n.id === edge.sourceId);
            const target = this.nodes.find(node => node.id === edge.targetId);

            if (!source || !target) return;

            const sourcePos = physicalToPixel(this.mapInfo, source.x, source.y);
            const targetPos = physicalToPixel(this.mapInfo, target.x, target.y);

            // 获取边的样式
            const style = edgeStyles[edge.type] || edgeStyles.STRAIGHT;
            this.ctx.strokeStyle = style.color;
            this.ctx.lineWidth = style.lineWidth;
            this.ctx.lineCap = 'round';

            // 设置虚线效果用于区分曲线
            if (edge.type === 'CURVE') {
                this.ctx.setLineDash([5, 3]);
            } else {
                this.ctx.setLineDash([]);
            }

            this.ctx.beginPath();

            // 根据边类型绘制不同的路径
            switch (edge.type) {
                case 'CURVE':
                    this.drawBezierEdge(edge, sourcePos, targetPos);
                    break;
                default:
                    this.drawStraightEdge(sourcePos, targetPos);
                    break;
            }

            // 绘制箭头
            if (edge.direction === 'UNIDIRECTIONAL') {
                this.drawArrow(sourcePos, targetPos, edge)
            }

            this.ctx.stroke();


        });

        this.ctx.restore();
    }

    // 绘制直线边
    drawStraightEdge(sourcePos, targetPos) {
        this.ctx.moveTo(sourcePos[0] * this.zoomLevel, sourcePos[1] * this.zoomLevel);
        this.ctx.lineTo(targetPos[0] * this.zoomLevel, targetPos[1] * this.zoomLevel);
    }

    // 绘制贝塞尔曲线边
    drawBezierEdge(edge, sourcePos, targetPos) {
        const controlPoints = edge.controlPoints || [];
        const zoom = this.zoomLevel;

        // 转换为像素坐标
        const startX = sourcePos[0] * zoom;
        const startY = sourcePos[1] * zoom;
        const endX = targetPos[0] * zoom;
        const endY = targetPos[1] * zoom;

        this.ctx.moveTo(startX, startY);

        // 根据控制点数量判断贝塞尔曲线阶数
        switch (controlPoints.length) {
            case 1:
                // 二阶贝塞尔曲线（1个控制点）
                this.drawQuadraticBezier(startX, startY, endX, endY, controlPoints[0], zoom);
                break;

            case 2:
                // 三阶贝塞尔曲线（2个控制点）
                this.drawCubicBezier(startX, startY, endX, endY, controlPoints[0], controlPoints[1], zoom);
                break;

            default:
                // 默认绘制直线
                console.warn(`边 ${edge.id} 控制点数量不匹配: ${controlPoints.length}，将绘制为直线`);
                this.drawStraightEdge(sourcePos, targetPos);
                break;
        }

    }

    // 绘制二阶贝塞尔曲线
    drawQuadraticBezier(startX, startY, endX, endY, controlPoint, zoom) {
        // 转换控制点坐标为像素坐标
        const controlPos = physicalToPixel(this.mapInfo, controlPoint.x, controlPoint.y);
        const cpX = controlPos[0] * zoom;
        const cpY = controlPos[1] * zoom;

        // 计算起始控制点（用于绘制箭头时的方向计算）
        this.curveStartControlPoint = {x: cpX, y: cpY};

        // 绘制二阶贝塞尔曲线
        this.ctx.quadraticCurveTo(cpX, cpY, endX, endY);
    }

    // 绘制三阶贝塞尔曲线
    drawCubicBezier(startX, startY, endX, endY, controlPoint1, controlPoint2, zoom) {
        // 转换控制点坐标为像素坐标
        const cp1Pos = physicalToPixel(this.mapInfo, controlPoint1.x, controlPoint1.y);
        const cp2Pos = physicalToPixel(this.mapInfo, controlPoint2.x, controlPoint2.y);

        const cp1X = cp1Pos[0] * zoom;
        const cp1Y = cp1Pos[1] * zoom;
        const cp2X = cp2Pos[0] * zoom;
        const cp2Y = cp2Pos[1] * zoom;

        // 计算起始控制点（用于绘制箭头时的方向计算）
        this.curveStartControlPoint = {x: cp1X, y: cp1Y};
        this.curveEndControlPoint = {x: cp2X, y: cp2Y};

        // 绘制三阶贝塞尔曲线
        this.ctx.bezierCurveTo(cp1X, cp1Y, cp2X, cp2Y, endX, endY);
    }

    // 计算贝塞尔曲线在特定t值的点坐标
    getBezierPoint(t, points) {
        if (points.length === 3) {
            // 二阶贝塞尔曲线
            const [p0, cp1, p1] = points;
            const u = 1 - t;
            return {
                x: u * u * p0.x + 2 * u * t * cp1.x + t * t * p1.x,
                y: u * u * p0.y + 2 * u * t * cp1.y + t * t * p1.y
            };
        } else if (points.length === 4) {
            // 三阶贝塞尔曲线
            const [p0, cp1, cp2, p1] = points;
            const u = 1 - t;
            const uu = u * u;
            const uuu = uu * u;
            const tt = t * t;
            const ttt = tt * t;

            return {
                x: uuu * p0.x + 3 * uu * t * cp1.x + 3 * u * tt * cp2.x + ttt * p1.x,
                y: uuu * p0.y + 3 * uu * t * cp1.y + 3 * u * tt * cp2.y + ttt * p1.y
            };
        }
        return null;
    }

    // 计算贝塞尔曲线在特定t值的切线角度
    getBezierTangent(t, points) {
        if (points.length === 3) {
            // 二阶贝塞尔曲线导数
            const [p0, cp1, p1] = points;
            const u = 1 - t;
            const dx = 2 * (1 - t) * (cp1.x - p0.x) + 2 * t * (p1.x - cp1.x);
            const dy = 2 * (1 - t) * (cp1.y - p0.y) + 2 * t * (p1.y - cp1.y);
            return Math.atan2(dy, dx);
        } else if (points.length === 4) {
            // 三阶贝塞尔曲线导数
            const [p0, cp1, cp2, p1] = points;
            const u = 1 - t;
            const uu = u * u;
            const tt = t * t;

            const dx = 3 * uu * (cp1.x - p0.x) + 6 * u * t * (cp2.x - cp1.x) + 3 * tt * (p1.x - cp2.x);
            const dy = 3 * uu * (cp1.y - p0.y) + 6 * u * t * (cp2.y - cp1.y) + 3 * tt * (p1.y - cp2.y);
            return Math.atan2(dy, dx);
        }
        return 0;
    }

    // 优化箭头绘制函数，支持曲线边的箭头
    drawArrow(sourcePos, targetPos, edge) {
        const startX = sourcePos[0] * this.zoomLevel;
        const startY = sourcePos[1] * this.zoomLevel;
        const endX = targetPos[0] * this.zoomLevel;
        const endY = targetPos[1] * this.zoomLevel;

        let angle, arrowX, arrowY;

        if (edge && edge.type === 'CURVE' && edge.controlPoints) {
            // 对于曲线边，箭头绘制在曲线末端
            const controlPoints = edge.controlPoints;
            const t = 0.8; // 在曲线的80%处绘制箭头

            if (controlPoints.length === 1) {
                // 二阶贝塞尔曲线
                const cpPos = physicalToPixel(this.mapInfo, controlPoints[0].x, controlPoints[0].y);
                const points = [
                    {x: startX, y: startY},
                    {x: cpPos[0] * this.zoomLevel, y: cpPos[1] * this.zoomLevel},
                    {x: endX, y: endY}
                ];
                const point = this.getBezierPoint(t, points);
                arrowX = point.x;
                arrowY = point.y;
                angle = this.getBezierTangent(t, points);
            } else if (controlPoints.length === 2) {
                // 三阶贝塞尔曲线
                const cp1Pos = physicalToPixel(this.mapInfo, controlPoints[0].x, controlPoints[0].y);
                const cp2Pos = physicalToPixel(this.mapInfo, controlPoints[1].x, controlPoints[1].y);
                const points = [
                    {x: startX, y: startY},
                    {x: cp1Pos[0] * this.zoomLevel, y: cp1Pos[1] * this.zoomLevel},
                    {x: cp2Pos[0] * this.zoomLevel, y: cp2Pos[1] * this.zoomLevel},
                    {x: endX, y: endY}
                ];
                const point = this.getBezierPoint(t, points);
                arrowX = point.x;
                arrowY = point.y;
                angle = this.getBezierTangent(t, points);
            } else {
                // 默认直线箭头
                angle = Math.atan2(endY - startY, endX - startX);
                arrowX = endX - 10 * Math.cos(angle);
                arrowY = endY - 10 * Math.sin(angle);
            }
        } else {
            // 直线边
            angle = Math.atan2(endY - startY, endX - startX);
            arrowX = endX - 10 * Math.cos(angle);
            arrowY = endY - 10 * Math.sin(angle);
        }

        // 绘制箭头
        this.ctx.save();
        this.ctx.strokeStyle = '#EC0000';
        this.ctx.translate(arrowX, arrowY);
        this.ctx.rotate(angle);

        this.ctx.beginPath();
        this.ctx.moveTo(0, 0);
        this.ctx.lineTo(-10, 4);
        this.ctx.lineTo(-10, -4);
        this.ctx.closePath();
        this.ctx.stroke();

        this.ctx.restore();
    }

    // 绘制控制点（调试用）
    drawControlPoints() {
        if (!this.ctx || !this.config.debugMode) return;

        this.edges.forEach(edge => {
            if (edge.type === 'CURVE' && edge.controlPoints) {
                const source = this.nodes.find(n => n.id === edge.sourceId);
                const target = this.nodes.find(n => n.id === edge.targetId);

                if (!source || !target) return;

                const targetPos = physicalToPixel(this.mapInfo, target.x, target.y);

                edge.controlPoints.forEach((cp, index) => {
                    const cpPos = physicalToPixel(this.mapInfo, cp.x, cp.y);
                    const x = cpPos[0] * this.zoomLevel;
                    const y = cpPos[1] * this.zoomLevel;

                    // 绘制控制点
                    this.ctx.beginPath();
                    this.ctx.arc(x, y, 4, 0, Math.PI * 2);
                    this.ctx.fillStyle = '#ff4444';
                    this.ctx.fill();
                    this.ctx.strokeStyle = '#ffffff';
                    this.ctx.lineWidth = 1;
                    this.ctx.stroke();

                    // 绘制控制线
                    this.ctx.beginPath();
                    this.ctx.setLineDash([2, 2]);
                    this.ctx.strokeStyle = '#888888';

                    if (index === 0) {
                        const sourcePos = physicalToPixel(this.mapInfo, source.x, source.y);
                        this.ctx.moveTo(sourcePos[0] * this.zoomLevel, sourcePos[1] * this.zoomLevel);
                    } else {
                        const prevCP = physicalToPixel(this.mapInfo, edge.controlPoints[index - 1].x, edge.controlPoints[index - 1].y);
                        this.ctx.moveTo(prevCP[0] * this.zoomLevel, prevCP[1] * this.zoomLevel);
                    }
                    this.ctx.lineTo(x, y);
                    this.ctx.stroke();

                    if (index === edge.controlPoints.length - 1) {
                        this.ctx.moveTo(x, y);
                        this.ctx.lineTo(targetPos[0] * this.zoomLevel, targetPos[1] * this.zoomLevel);
                        this.ctx.stroke();
                    }

                    this.ctx.setLineDash([]);

                    // 显示控制点编号
                    this.ctx.fillStyle = '#ffffff';
                    this.ctx.font = '10px Arial';
                    this.ctx.textAlign = 'center';
                    this.ctx.fillText(`CP${index + 1}`, x, y - 8);
                });
            }
        });
    }

    clear() {
        if (this.ctx && this.canvas) {
            this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        }
    }
}