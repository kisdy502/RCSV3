package com.sdt.agv_simulator.move;

import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Edge.EdgeDirection;
import com.jizhi.vda5050.domain.Edge.EdgeType;
import com.jizhi.vda5050.message.Vda5050OrderMessage;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class EdgeConvertor {

    /**
     * 将VDA5050 Edge转换为内部Edge对象
     */
    public static Edge convertVda5050Edge(Vda5050OrderMessage.Edge vda5050Edge) {
        Edge internalEdge = new Edge();

        if (vda5050Edge == null) {
            return createDefaultEdge();
        }

        // 1. 基础ID转换
        internalEdge.setId(Optional.ofNullable(vda5050Edge.getEdgeId()).orElse(""));
        internalEdge.setSourceId(Optional.ofNullable(vda5050Edge.getStartNodeId()).orElse(""));
        internalEdge.setTargetId(Optional.ofNullable(vda5050Edge.getEndNodeId()).orElse(""));

        // 2. 计算权重（长度）
        Double weight = calculateEdgeWeight(vda5050Edge);
        internalEdge.setWeight(weight);

        // 3. 方向转换
        EdgeDirection direction = convertDirection(vda5050Edge.getDirection());
        internalEdge.setDirection(direction);

        // 4. 边类型转换
        EdgeType type = convertEdgeType(vda5050Edge);
        internalEdge.setType(type);

        // 5. 速度限制
        Double maxSpeed = calculateMaxSpeed(vda5050Edge);
        internalEdge.setMaxSpeed(maxSpeed);

        // 6. 优先级（从附加信息中获取，或使用默认值）
        Integer priority = extractPriority(vda5050Edge.getAdditionalInformation());
        internalEdge.setPriority(priority);

        // 7. 控制点转换
        List<Edge.ControlPoint> controlPoints = convertControlPoints(vda5050Edge);
        internalEdge.setControlPoints(controlPoints);

        // 8. 其他属性
        internalEdge.setEnabled(isEdgeEnabled(vda5050Edge));
        internalEdge.setBackUp(isBackUp(vda5050Edge.getAdditionalInformation()));

        return internalEdge;
    }

    /**
     * 创建默认边
     */
    private static Edge createDefaultEdge() {
        Edge edge = new Edge();
        edge.setEnabled(true);
        edge.setBackUp(false);
        edge.setWeight(0.0);
        edge.setMaxSpeed(1.0);
        edge.setPriority(1);
        edge.setDirection(EdgeDirection.BIDIRECTIONAL);
        edge.setType(EdgeType.STRAIGHT);
        edge.setControlPoints(new ArrayList<>());
        return edge;
    }

    /**
     * 计算边的权重（长度）
     */
    private static Double calculateEdgeWeight(Vda5050OrderMessage.Edge vda5050Edge) {
        if (vda5050Edge == null) {
            return 0.0;
        }

        // 优先从edgeDescription.length获取
        Vda5050OrderMessage.Edge.EdgeDescription description = vda5050Edge.getEdgeDescription();
        if (description != null && description.getLength() != null && description.getLength() > 0) {
            return description.getLength();
        }

        // 从附加信息中获取长度
        Map<String, Object> additionalInfo = vda5050Edge.getAdditionalInformation();
        if (additionalInfo != null) {
            Object lengthObj = additionalInfo.get("length");
            if (lengthObj instanceof Number) {
                return ((Number) lengthObj).doubleValue();
            } else if (lengthObj instanceof String) {
                try {
                    return Double.parseDouble((String) lengthObj);
                } catch (NumberFormatException e) {
                    // 忽略转换异常
                }
            }
        }

        // 默认返回0.0
        return 0.0;
    }

    /**
     * 转换方向
     */
    private static EdgeDirection convertDirection(String vda5050Direction) {
        if (!StringUtils.hasText(vda5050Direction)) {
            return EdgeDirection.BIDIRECTIONAL;
        }

        switch (vda5050Direction.toUpperCase()) {
            case "FORWARD":
            case "BACKWARD":
                return EdgeDirection.UNIDIRECTIONAL;
            case "BIDIRECTIONAL":
                return EdgeDirection.BIDIRECTIONAL;
            default:
                // 尝试从附加信息中解析
                return EdgeDirection.BIDIRECTIONAL;
        }
    }

    /**
     * 转换边类型
     */
    private static EdgeType convertEdgeType(Vda5050OrderMessage.Edge vda5050Edge) {
        if (vda5050Edge == null) {
            return EdgeType.STRAIGHT;
        }

        Vda5050OrderMessage.Edge.EdgeDescription description = vda5050Edge.getEdgeDescription();
        if (description == null || !StringUtils.hasText(description.getType())) {
            return EdgeType.STRAIGHT;
        }

        switch (description.getType().toUpperCase()) {
            case "STRAIGHT":
                return EdgeType.STRAIGHT;
            case "ARC":
            case "CLOTHOID":
            case "CURVE":
                return EdgeType.CURVE;
            case "ELEVATION":
            case "CUSTOM":
                // 自定义类型可以映射为坡道或其他
                Map<String, Object> additionalInfo = vda5050Edge.getAdditionalInformation();
                if (additionalInfo != null && "ELEVATION".equalsIgnoreCase(
                        Optional.ofNullable(additionalInfo.get("type")).map(Object::toString).orElse(""))) {
                    return EdgeType.ELEVATION;
                }
                return EdgeType.CURVE;
            default:
                return EdgeType.STRAIGHT;
        }
    }

    /**
     * 计算最大速度
     */
    private static Double calculateMaxSpeed(Vda5050OrderMessage.Edge vda5050Edge) {
        if (vda5050Edge == null) {
            return 1.0;
        }

        // 优先使用maxSpeed
        if (vda5050Edge.getMaxSpeed() != null && vda5050Edge.getMaxSpeed() > 0) {
            return vda5050Edge.getMaxSpeed();
        }

        // 从附加信息中获取
        Map<String, Object> additionalInfo = vda5050Edge.getAdditionalInformation();
        if (additionalInfo != null) {
            Object speedObj = additionalInfo.get("maxSpeed");
            if (speedObj instanceof Number) {
                double speed = ((Number) speedObj).doubleValue();
                if (speed > 0) {
                    return speed;
                }
            }
        }

        // 默认速度
        return 1.0;
    }

    /**
     * 提取优先级
     */
    private static Integer extractPriority(Map<String, Object> additionalInformation) {
        if (additionalInformation == null) {
            return 1;
        }

        Object priorityObj = additionalInformation.get("priority");
        if (priorityObj instanceof Number) {
            int priority = ((Number) priorityObj).intValue();
            return priority > 0 ? priority : 1;
        } else if (priorityObj instanceof String) {
            try {
                int priority = Integer.parseInt((String) priorityObj);
                return priority > 0 ? priority : 1;
            } catch (NumberFormatException e) {
                return 1;
            }
        }

        return 1;
    }

    /**
     * 转换控制点
     */
    private static List<Edge.ControlPoint> convertControlPoints(Vda5050OrderMessage.Edge vda5050Edge) {
        List<Edge.ControlPoint> controlPoints = new ArrayList<>();

        if (vda5050Edge == null || vda5050Edge.getEdgeDescription() == null) {
            return controlPoints;
        }

        Vda5050OrderMessage.Edge.EdgeDescription description = vda5050Edge.getEdgeDescription();

        // 1. 优先从controlPoints转换
        if (!CollectionUtils.isEmpty(description.getControlPoints())) {
            controlPoints = description.getControlPoints().stream()
                    .map(cp -> new Edge.ControlPoint(
                            Optional.ofNullable(cp.getX()).orElse(0.0),
                            Optional.ofNullable(cp.getY()).orElse(0.0)
                    ))
                    .collect(Collectors.toList());
        }

        // 2. 如果没有控制点，但有半径和角度，可以生成圆弧控制点
        if (controlPoints.isEmpty() && description.getRadius() != null && description.getAngle() != null) {
            controlPoints = generateArcControlPoints(
                    description.getRadius(),
                    description.getAngle()
            );
        }

        return controlPoints;
    }

    /**
     * 生成圆弧的控制点（简化实现）
     */
    private static List<Edge.ControlPoint> generateArcControlPoints(Double radius, Double angle) {
        List<Edge.ControlPoint> points = new ArrayList<>();
        if (radius == null || angle == null || radius == 0 || angle == 0) {
            return points;
        }

        // 简化的圆弧控制点生成逻辑
        // 实际实现可能需要更复杂的几何计算
        int segments = Math.max(3, (int) (Math.abs(angle) * 5)); // 根据角度决定分段数
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double theta = angle * t;
            double x = radius * Math.cos(theta);
            double y = radius * Math.sin(theta);
            points.add(new Edge.ControlPoint(x, y));
        }

        return points;
    }

    /**
     * 检查边是否启用
     */
    private static Boolean isEdgeEnabled(Vda5050OrderMessage.Edge vda5050Edge) {
        if (vda5050Edge == null) {
            return true;
        }

        // released为true表示可以行驶
        if (vda5050Edge.getReleased() != null) {
            return vda5050Edge.getReleased();
        }

        // 从附加信息中获取
        Map<String, Object> additionalInfo = vda5050Edge.getAdditionalInformation();
        if (additionalInfo != null) {
            Object enabledObj = additionalInfo.get("enabled");
            if (enabledObj instanceof Boolean) {
                return (Boolean) enabledObj;
            } else if (enabledObj instanceof String) {
                return Boolean.parseBoolean((String) enabledObj);
            }
        }

        return true; // 默认启用
    }

    /**
     * 检查是否倒车行驶
     */
    private static Boolean isBackUp(Map<String, Object> additionalInformation) {
        if (additionalInformation == null) {
            return false;
        }

        Object backupObj = additionalInformation.get("backUp");
        if (backupObj instanceof Boolean) {
            return (Boolean) backupObj;
        } else if (backupObj instanceof String) {
            return Boolean.parseBoolean((String) backupObj);
        }

        return false;
    }

    /**
     * 批量转换方法
     */
    public static List<Edge> convertVda5050Edges(List<Vda5050OrderMessage.Edge> vda5050Edges) {
        if (CollectionUtils.isEmpty(vda5050Edges)) {
            return new ArrayList<>();
        }
        return vda5050Edges.stream()
                .map(EdgeConvertor::convertVda5050Edge)
                .collect(Collectors.toList());
    }
}
