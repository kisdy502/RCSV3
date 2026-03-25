package com.sdt.agv_dispatcher.component;


import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.MapMetaData;
import com.jizhi.vda5050.domain.Node;
import com.sdt.agv_dispatcher.scheduler.CoordinateTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 模拟给地图增加站点和通道，真机时候，增加部署模块，将站点通道存db读写改查
 */
@Component
@Slf4j
public class GraphDataGenerator {

    /**
     * 生成模拟节点数据 - 扩展1.5倍版本（精简点位定义）
     */
    public List<Node> generateNodes(MapMetaData mapMetaData) {
        List<Node> nodes = new ArrayList<>();

        // ============ 站点 (Station) - 14个 ============
        // 原有站点
        nodes.add(createNode("S001", Node.NodeType.STATION, "装卸站1", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 50, 150), -1.57));
        nodes.add(createNode("S002", Node.NodeType.STATION, "加工站2", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 150, 150), 1.57));
        nodes.add(createNode("S003", Node.NodeType.STATION, "包装站3", CoordinateTransformer.pixelToPhysical(mapMetaData,
                320, 240), 0.0));
        nodes.add(createNode("S004", Node.NodeType.STATION, "质检站4", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 350, 150), 2.35));
        nodes.add(createNode("S005", Node.NodeType.STATION, "仓储站5", CoordinateTransformer.pixelToPhysical(mapMetaData,
                450, 180), 0.0));
        nodes.add(createNode("S006", Node.NodeType.STATION, "充电站6", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 250, 100), 0.78));
        nodes.add(createNode("S007", Node.NodeType.STATION, "等待站7", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 540, 50), 0.47));

        nodes.add(createNode("S008", Node.NodeType.STATION, "侧通道点3", CoordinateTransformer.pixelToPhysical(mapMetaData,
                584, 220), 0.0));


        nodes.add(createNode("S009", Node.NodeType.STATION, "出库站9", CoordinateTransformer.pixelToPhysical(mapMetaData,
                960, 320), 2.0));
        nodes.add(createNode("S010", Node.NodeType.STATION, "原料站10", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 50, 50), -0.2));
        nodes.add(createNode("S011", Node.NodeType.STATION, "入库站11", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 150, 50), 1.0));
        nodes.add(createNode("S012", Node.NodeType.STATION, "废料站12", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 850, 650), 0.0));
        nodes.add(createNode("S013", Node.NodeType.STATION, "充电站13", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 200, 550), 1.9));
        nodes.add(createNode("S014", Node.NodeType.STATION, "等待站14", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 750, 320), 0.70));
        nodes.add(createNode("S015", Node.NodeType.STATION, "充电站15", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 772, 72), 0.70));
        nodes.add(createNode("S016", Node.NodeType.STATION, "出库站16", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 560, 440), 0.70));
        nodes.add(createNode("S017", Node.NodeType.STATION, "出库站17", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 160, 280), -0.35));
        nodes.add(createNode("S018", Node.NodeType.STATION, "成品站18", CoordinateTransformer.pixelToPhysical(mapMetaData
                , 320, 640), 0.57));

        // ============ 通道点 (Path) - 16个 ============
        // 原有通道点
        nodes.add(createNode("P001", Node.NodeType.PATH, "主通道点1", CoordinateTransformer.pixelToPhysical(mapMetaData,
                100, 150), 0.0));
//        nodes.add(createNode("P002", Node.NodeType.PATH, "主通道点2", CoordinateTransformer.pixelToPhysical
//        (mapMetaData, 200, 150), 0.40));
//        nodes.add(createNode("P003", Node.NodeType.PATH, "主通道点3", CoordinateTransformer.pixelToPhysical
//        (mapMetaData, 300, 150), -2.0));
        nodes.add(createNode("P004", Node.NodeType.PATH, "主通道点4", CoordinateTransformer.pixelToPhysical(mapMetaData,
                400, 150), 0.0));
        nodes.add(createNode("P005", Node.NodeType.PATH, "侧通道点1", CoordinateTransformer.pixelToPhysical(mapMetaData,
                250, 200), 0.0));
        nodes.add(createNode("P006", Node.NodeType.PATH, "侧通道点2", CoordinateTransformer.pixelToPhysical(mapMetaData,
                350, 200), 0.0));
        nodes.add(createNode("P007", Node.NodeType.PATH, "分拣站8", CoordinateTransformer.pixelToPhysical(mapMetaData,
                584, 150), 0.70));
        nodes.add(createNode("P008", Node.NodeType.PATH, "东通道点1", CoordinateTransformer.pixelToPhysical(mapMetaData,
                500, 150), 0.0));
        nodes.add(createNode("P009", Node.NodeType.PATH, "东通道点2", CoordinateTransformer.pixelToPhysical(mapMetaData,
                960, 150), 0.0));
        nodes.add(createNode("P010", Node.NodeType.PATH, "北通道点1", CoordinateTransformer.pixelToPhysical(mapMetaData,
                100, 50), 0.0));
        nodes.add(createNode("P011", Node.NodeType.PATH, "北通道点2", CoordinateTransformer.pixelToPhysical(mapMetaData,
                160, 100), 0.0));
        nodes.add(createNode("P012", Node.NodeType.PATH, "北通道点3", CoordinateTransformer.pixelToPhysical(mapMetaData,
                316, 50), 0.0));
        nodes.add(createNode("P013", Node.NodeType.PATH, "北通道点4", CoordinateTransformer.pixelToPhysical(mapMetaData,
                440, 50), 0.0));
        nodes.add(createNode("P014", Node.NodeType.PATH, "南通道点1", CoordinateTransformer.pixelToPhysical(mapMetaData,
                100, 280), 0.0));
        nodes.add(createNode("P015", Node.NodeType.PATH, "南通道点2", CoordinateTransformer.pixelToPhysical(mapMetaData,
                300, 350), 0.0));
        nodes.add(createNode("P016", Node.NodeType.PATH, "南通道点3", CoordinateTransformer.pixelToPhysical(mapMetaData,
                400, 350), 0.0));
        nodes.add(createNode("P017", Node.NodeType.PATH, "南通道点4", CoordinateTransformer.pixelToPhysical(mapMetaData,
                720, 200), 0.0));
        nodes.add(createNode("P018", Node.NodeType.PATH, "南通道点5", CoordinateTransformer.pixelToPhysical(mapMetaData,
                320, 520), 0.0));
        nodes.add(createNode("P019", Node.NodeType.PATH, "南通道点6", CoordinateTransformer.pixelToPhysical(mapMetaData,
                560, 620), 0.0));

        nodes.add(createNode("P019", Node.NodeType.PATH, "南通道点7", CoordinateTransformer.pixelToPhysical(mapMetaData,
                520, 600), 0.0));
        nodes.add(createNode("P020", Node.NodeType.PATH, "南通道点8", CoordinateTransformer.pixelToPhysical(mapMetaData,
                640, 600), 0.0));

        nodes.add(createNode("P021", Node.NodeType.PATH, "南通道点9", CoordinateTransformer.pixelToPhysical(mapMetaData,
                720, 600), 0.0));
        nodes.add(createNode("P022", Node.NodeType.PATH, "南通道点10", CoordinateTransformer.pixelToPhysical(mapMetaData,
                720, 644), 0.0));
        nodes.add(createNode("P023", Node.NodeType.PATH, "南通道点11", CoordinateTransformer.pixelToPhysical(mapMetaData,
                240, 440), 0.0));

        nodes.add(createNode("I002", Node.NodeType.INTERSECTION, "交叉点2",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 250, 150), 0.0));
        nodes.add(createNode("I003", Node.NodeType.INTERSECTION, "交叉点3",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 400, 240), 1.0));
        // 新增交叉点
        nodes.add(createNode("I004", Node.NodeType.INTERSECTION, "交叉点4",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 175, 200), 0.0));
        nodes.add(createNode("I005", Node.NodeType.INTERSECTION, "交叉点5",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 350, 100), 0.0));
        nodes.add(createNode("I006", Node.NodeType.INTERSECTION, "交叉点6",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 584, 350), 0.0));
        nodes.add(createNode("I007", Node.NodeType.INTERSECTION, "交叉点7",
                CoordinateTransformer.pixelToPhysical(mapMetaData
                        , 400, 440), 0.70));
        nodes.add(createNode("I008", Node.NodeType.INTERSECTION, "交叉点8",
                CoordinateTransformer.pixelToPhysical(mapMetaData
                        , 320, 440), 0.70));

        log.info("生成 {} 个模拟节点 (扩展1.5倍版本)", nodes.size());
        return nodes;
    }

    /**
     * 生成模拟边数据 - 扩展1.5倍版本
     */
    public List<Edge> generateEdges(MapMetaData mapMetaData, List<Node> nodeList) {
        List<Edge> edges = new ArrayList<>();

        // ============ 主干线1：东西横向主线 ============
        // 从西到东的主干线 (y=150水平线)
        edges.add(createEdge(nodeList, "E001", "S010", "P010"));   // 北区连接
        edges.add(createEdge(nodeList, "E002", "P010", "S011"));   // 北区连接
        edges.add(createEdge(nodeList, "E003", "P001", "P010"));   // 进入主线
        edges.add(createEdge(nodeList, "E004", "S001", "P001"));   // 主线
        edges.add(createEdge(nodeList, "E005", "P001", "S002"));   // 主线
        edges.add(createEdge(nodeList, "E006", "S002", "I002"));   // 主线
        edges.add(createEdge(nodeList, "E008", "I002", "S004"));   // 主线
        edges.add(createEdge(nodeList, "E010", "S004", "P004"));   // 主线
        edges.add(createEdge(nodeList, "E011", "P004", "S005"));   // 主线
        edges.add(createEdge(nodeList, "E012", "S005", "P008"));   // 向东延伸
        edges.add(createEdge(nodeList, "E013", "P008", "P007"));   // 向东延伸
        // 修改E014：P009到S015的边改为二阶贝塞尔曲线
        edges.add(createQuadraticBezierEdge(nodeList, "E014", "P009", "S015",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 816, 48)));  // 控制点

        edges.add(createEdge(nodeList, "E015", "P009", "S009"));   // 主线终点

        // ============ 主干线2：南北纵向主线 ============
        // 从北到南的干线 (x=250垂直线)
        edges.add(createEdge(nodeList, "E020", "P011", "S006"));   // 北区
        edges.add(createEdge(nodeList, "E021", "S006", "I005"));   // 向下
        edges.add(createEdge(nodeList, "E022", "I002", "S006"));   // 到主交叉点
        edges.add(createEdge(nodeList, "E023", "P008", "S007"));   // 向下
        edges.add(createEdge(nodeList, "E024", "I004", "P005"));   // 向下
        edges.add(createEdge(nodeList, "E025", "P005", "S003"));   // 到包装站
        edges.add(createEdge(nodeList, "E028", "I007", "I008"));   // 到充电站2

        // ============ 主干线3：另一条南北干线 ============
        // 从北到南的干线 (x=350垂直线)
        edges.add(createEdge(nodeList, "E030", "P012", "I005"));   // 北区连接
        edges.add(createEdge(nodeList, "E031", "I005", "P006"));   // 向下
        edges.add(createEdge(nodeList, "E032", "P006", "S003"));   // 到交叉点3
        edges.add(createCubicBezierEdge(nodeList, "E033", "I003", "S008",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 450, 212),
                CoordinateTransformer.pixelToPhysical(mapMetaData, 520, 212)));   // 向下
        edges.add(createEdge(nodeList, "E034", "P007", "S008"));   // 连接到仓储站
        edges.add(createEdge(nodeList, "E035", "I003", "P016"));   // 继续向下
        edges.add(createEdge(nodeList, "E036", "I006", "P016"));   // 到南通道
        edges.add(createCubicBezierEdge(nodeList, "E037", "I006", "S014",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 612, 360),
                CoordinateTransformer.pixelToPhysical(mapMetaData, 724, 352)));   // 到等待站2

        edges.add(createEdge(nodeList, "E040", "S011", "P011"));   // 连接北通道
        //edges.add(createEdge(nodeList,"E041", "P011", "I005"));   // 连接到交叉点5
        edges.add(createEdge(nodeList, "E042", "I005", "P012"));   // 连接到北通道3
        edges.add(createEdge(nodeList, "E043", "P012", "P013"));   // 连接到北通道4
        edges.add(createEdge(nodeList, "E044", "P013", "S007"));   // 连接到等待站1

        edges.add(createEdge(nodeList, "E046", "I003", "P004"));   // 连接到主通道4

        edges.add(createEdge(nodeList, "E047", "I007", "P016"));   // 废料站连接
        edges.add(createEdge(nodeList, "E049", "I004", "S017"));   // 已连接
        edges.add(createCubicBezierEdge(nodeList, "E050", "P015", "P023",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 240, 360),  // 控制点1
                CoordinateTransformer.pixelToPhysical(mapMetaData, 260, 400)));   // 连接南通道点
        edges.add(createEdge(nodeList, "E051", "P023", "S013"));   // 已连接
        edges.add(createEdge(nodeList, "E052", "I007", "S016"));   // 废料站连接
        edges.add(createEdge(nodeList, "E053", "P016", "I007"));

        // 修改E054：S016到P019的边改为三阶贝塞尔曲线
        edges.add(createCubicBezierEdge(nodeList, "E054", "S016", "P019",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 576, 506),  // 控制点1
                CoordinateTransformer.pixelToPhysical(mapMetaData, 458, 532))); // 控制点2

        edges.add(createEdge(nodeList, "E055", "P019", "P020"));
        edges.add(createEdge(nodeList, "E056", "P020", "P021"));
        edges.add(createEdge(nodeList, "E057", "P021", "P022"));
        edges.add(createEdge(nodeList, "E058", "P022", "S012"));
        edges.add(createEdge(nodeList, "E059", "P014", "S017"));   // 已连接


        // ============ 斜向连接和分支 ============
        edges.add(createEdge(nodeList, "E060", "S002", "P011"));
//        edges.add(createEdge(nodeList,"E061", "S007", "S004"));   // 等待站1连接到质检站
        edges.add(createEdge(nodeList, "E062", "I002", "P005"));   // 交叉点2到侧通道点1
        edges.add(createEdge(nodeList, "E063", "I003", "S003"));   // 交叉点3到包装站（备用）
        edges.add(createEdge(nodeList, "E064", "I004", "S002"));   // 交叉点4到加工站（备用）
        edges.add(createEdge(nodeList, "E066", "I006", "S008"));   // 交叉点6连接到侧通道点3
        edges.add(createEdge(nodeList, "E067", "I008", "P018"));
//        edges.add(createEdge(nodeList,"E068", "P018", "S013"));

        // ============ 备用路径和冗余连接 ============
        edges.add(createEdge(nodeList, "E071", "P001", "P014"));   // 入库站到南通道点1
        edges.add(createEdge(nodeList, "E072", "S009", "S014"));   // 出库站到等待站2（外围通道）
        edges.add(createEdge(nodeList, "E073", "S014", "P017"));
        edges.add(createEdge(nodeList, "E074", "P017", "S015"));
        edges.add(createCubicBezierEdge(nodeList, "E075", "P015", "I008",
                CoordinateTransformer.pixelToPhysical(mapMetaData, 340, 358),  // 控制点1
                CoordinateTransformer.pixelToPhysical(mapMetaData, 332, 420)));
        edges.add(createEdge(nodeList, "E076", "P022", "S018"));
        edges.add(createEdge(nodeList, "E077", "S016", "I006"));

        log.info("生成 {} 条模拟边", edges.size());
        return edges;
    }

    /**
     * 创建节点
     */
    private Node createNode(String id, Node.NodeType type, String name, double[] pos, double theta) {
        Node node = new Node();
        node.setId(id);
        node.setType(type);
        node.setName(name);
        node.setX(pos[0]);
        node.setY(pos[1]);
        node.setTheta(theta);
        node.setEnabled(true);
        node.setZoneId("Z001");
        node.setAgvType("STANDARD");
        return node;
    }

    /**
     * 创建直线边
     */
    private Edge createEdge(List<Node> nodeList, String id, String sourceId, String targetId) {
        Edge edge = new Edge();
        edge.setId(id);
        edge.setSourceId(sourceId);
        edge.setTargetId(targetId);
        // 计算实际距离作为权重
        double distance = calculateDistance(nodeList, sourceId, targetId);
        edge.setWeight(distance);
        edge.setDirection(Edge.EdgeDirection.BIDIRECTIONAL);
        edge.setType(Edge.EdgeType.STRAIGHT);
        edge.setMaxSpeed(1.5);
        edge.setPriority(1);
        edge.setEnabled(true);
        return edge;
    }

    /**
     * 计算两点之间的欧几里得距离[1,2](@ref)
     * 使用公式：distance = √[(x₂-x₁)² + (y₂-y₁)²]
     */
    private double calculateDistance(List<Node> nodeList, String sourceId, String targetId) {
        Optional<Node> sourceNode = findNodeById(nodeList, sourceId);
        Optional<Node> targetNode = findNodeById(nodeList, targetId);

        if (sourceNode.isPresent() && targetNode.isPresent()) {
            Node s = sourceNode.get();
            Node t = targetNode.get();

            // 应用欧几里得距离公式[1,2](@ref)
            double deltaX = t.getX() - s.getX();
            double deltaY = t.getY() - s.getY();
            double distance = Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));

            log.debug("计算距离: {} ({}, {}) -> {} ({}, {}) = {}",
                    sourceId, s.getX(), s.getY(),
                    targetId, t.getX(), t.getY(),
                    distance);

            return distance;
        } else {
            log.warn("未找到节点: {} 或 {}，使用默认距离50.0", sourceId, targetId);
            return 50.0; // 默认值
        }
    }

    /**
     * 创建二阶贝塞尔曲线边（二次贝塞尔曲线）
     * P(t) = (1-t)²P₀ + 2(1-t)tP₁ + t²P₂, 其中t∈[0,1]
     * P₀: 起点, P₁: 控制点, P₂: 终点
     */
    private Edge createQuadraticBezierEdge(List<Node> nodeList, String id, String sourceId,
                                           String targetId, double[] controlPoint) {
        Edge edge = new Edge();
        edge.setId(id);
        edge.setSourceId(sourceId);
        edge.setTargetId(targetId);

        // 计算贝塞尔曲线长度作为权重（近似计算）
        double distance = calculateBezierLength(nodeList, sourceId, targetId, controlPoint, null);
        edge.setWeight(distance);
        edge.setDirection(Edge.EdgeDirection.BIDIRECTIONAL);
        edge.setType(Edge.EdgeType.CURVE);
        edge.setMaxSpeed(0.8); // 曲线边速度稍微降低
        edge.setPriority(1);
        edge.setEnabled(true);

        // 设置贝塞尔曲线控制点
        List<Edge.ControlPoint> controlPoints = new ArrayList<>();
        controlPoints.add(new Edge.ControlPoint(controlPoint[0], controlPoint[1]));
        edge.setControlPoints(controlPoints);

        log.debug("创建二阶贝塞尔曲线边 {}: {} -> {}，控制点: ({}, {})",
                id, sourceId, targetId, controlPoint[0], controlPoint[1]);

        return edge;
    }

    /**
     * 创建三阶贝塞尔曲线边（三次贝塞尔曲线）
     * P(t) = (1-t)³P₀ + 3(1-t)²tP₁ + 3(1-t)t²P₂ + t³P₃, 其中t∈[0,1]
     * P₀: 起点, P₁: 控制点1, P₂: 控制点2, P₃: 终点
     */
    private Edge createCubicBezierEdge(List<Node> nodeList, String id, String sourceId,
                                       String targetId, double[] controlPoint1, double[] controlPoint2) {
        Edge edge = new Edge();
        edge.setId(id);
        edge.setSourceId(sourceId);
        edge.setTargetId(targetId);

        // 计算贝塞尔曲线长度作为权重（近似计算）
        double distance = calculateBezierLength(nodeList, sourceId, targetId, controlPoint1, controlPoint2);
        edge.setWeight(distance);
        edge.setDirection(Edge.EdgeDirection.BIDIRECTIONAL);
        edge.setType(Edge.EdgeType.CURVE);
        edge.setMaxSpeed(0.7); // 曲线边速度稍微降低
        edge.setPriority(1);
        edge.setEnabled(true);

        // 设置贝塞尔曲线控制点
        List<Edge.ControlPoint> controlPoints = new ArrayList<>();
        controlPoints.add(new Edge.ControlPoint(controlPoint1[0], controlPoint1[1]));
        controlPoints.add(new Edge.ControlPoint(controlPoint2[0], controlPoint2[1]));
        edge.setControlPoints(controlPoints);

        log.debug("创建三阶贝塞尔曲线边 {}: {} -> {}，控制点1: ({}, {}), 控制点2: ({}, {})",
                id, sourceId, targetId,
                controlPoint1[0], controlPoint1[1],
                controlPoint2[0], controlPoint2[1]);

        return edge;
    }

    /**
     * 计算贝塞尔曲线的近似长度（使用多段直线近似）
     */
    private double calculateBezierLength(List<Node> nodeList, String sourceId,
                                         String targetId, double[] cp1, double[] cp2) {
        Optional<Node> sourceNode = findNodeById(nodeList, sourceId);
        Optional<Node> targetNode = findNodeById(nodeList, targetId);

        if (sourceNode.isPresent() && targetNode.isPresent()) {
            Node start = sourceNode.get();
            Node end = targetNode.get();

            // 如果只有一个控制点，是二阶贝塞尔曲线
            if (cp2 == null) {
                return calculateQuadraticBezierLength(
                        start.getX(), start.getY(),
                        cp1[0], cp1[1],
                        end.getX(), end.getY()
                );
            }
            // 如果有两个控制点，是三阶贝塞尔曲线
            else {
                return calculateCubicBezierLength(
                        start.getX(), start.getY(),
                        cp1[0], cp1[1],
                        cp2[0], cp2[1],
                        end.getX(), end.getY()
                );
            }
        } else {
            log.warn("未找到节点: {} 或 {}，使用默认距离", sourceId, targetId);
            return 100.0; // 默认值，曲线通常比直线长
        }
    }

    /**
     * 计算二阶贝塞尔曲线长度（二次贝塞尔曲线）
     */
    private double calculateQuadraticBezierLength(double x0, double y0,
                                                  double x1, double y1,
                                                  double x2, double y2) {
        // 使用多段直线近似计算曲线长度
        int segments = 20; // 分段数，越多越精确
        double length = 0.0;
        double prevX = x0;
        double prevY = y0;

        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            // 二阶贝塞尔曲线公式
            double xt = Math.pow(1 - t, 2) * x0 + 2 * (1 - t) * t * x1 + Math.pow(t, 2) * x2;
            double yt = Math.pow(1 - t, 2) * y0 + 2 * (1 - t) * t * y1 + Math.pow(t, 2) * y2;

            double dx = xt - prevX;
            double dy = yt - prevY;
            length += Math.sqrt(dx * dx + dy * dy);

            prevX = xt;
            prevY = yt;
        }

        return length;
    }

    /**
     * 计算三阶贝塞尔曲线长度（三次贝塞尔曲线）
     */
    private double calculateCubicBezierLength(double x0, double y0,
                                              double x1, double y1,
                                              double x2, double y2,
                                              double x3, double y3) {
        // 使用多段直线近似计算曲线长度
        int segments = 20; // 分段数，越多越精确
        double length = 0.0;
        double prevX = x0;
        double prevY = y0;

        for (int i = 1; i <= segments; i++) {
            double t = i / (double) segments;
            double t2 = t * t;
            double t3 = t2 * t;
            double u = 1 - t;
            double u2 = u * u;
            double u3 = u2 * u;

            // 三阶贝塞尔曲线公式
            double xt = u3 * x0 + 3 * u2 * t * x1 + 3 * u * t2 * x2 + t3 * x3;
            double yt = u3 * y0 + 3 * u2 * t * y1 + 3 * u * t2 * y2 + t3 * y3;

            double dx = xt - prevX;
            double dy = yt - prevY;
            length += Math.sqrt(dx * dx + dy * dy);

            prevX = xt;
            prevY = yt;
        }

        return length;
    }


    /**
     * 根据节点ID查找节点
     */
    private Optional<Node> findNodeById(List<Node> nodeList, String nodeId) {
        return nodeList.stream()
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst();
    }
}
