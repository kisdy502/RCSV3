//
//
//import com.jizhi.vda5050.agv.AgvPosition;
//import lombok.extern.slf4j.Slf4j;
//
//import java.util.*;
//
////有bug，没有解决，不能用
//@Slf4j
////@Service
//public class MultiAgentPathPlanner {
//
//
//
//    // 路径预留表：时间步 -> 位置集合
//    private Map<Integer, Set<AgvPosition>> timeReservations = new HashMap<>();
//
//    // AGV路径表：AGV ID -> 路径（按时间步索引）
//    private Map<String, List<AgvPosition>> agvPaths = new HashMap<>();
//
//    /**
//     * 多车协同路径规划（基于时间窗的冲突避免）
//     */
//    public Map<String, List<AgvPosition>> planMultiAgentPaths(
//            Map<String, AgvPosition> startPositions,
//            String mainAgvId,
//            AgvPosition mainGoal,
//            Map<String, List<AgvPosition>> existingPaths) {
//
//        Map<String, List<AgvPosition>> result = new HashMap<>();
//
//        // 1. 先为主AGV规划初始路径
//        List<AgvPosition> mainPath = aStarWithTimeWindows(
//                startPositions.get(mainAgvId),
//                mainGoal,
//                mainAgvId
//        );
//
//        if (mainPath.isEmpty()) {
//            log.warn("无法为主AGV {} 规划路径，从 {} 到 {}", mainAgvId, startPositions.get(mainAgvId), mainGoal);
//            return result; // 规划失败
//        }
//
//        // 2. 为主AGV预留路径
//        reservePath(mainAgvId, mainPath);
//        result.put(mainAgvId, mainPath);
//
//        // 3. 为其他AGV检查并重新规划（如果有冲突）
//        for (Map.Entry<String, AgvPosition> entry : startPositions.entrySet()) {
//            String agvId = entry.getKey();
//            if (agvId.equals(mainAgvId)) continue;
//
//            AgvPosition start = entry.getValue();
//
//            // 获取现有目标（如果有现有路径）
//            AgvPosition goal = getCurrentGoal(agvId, existingPaths);
//            if (goal == null) continue; // 该AGV没有任务
//
//            // 检查现有路径是否有冲突
//            List<AgvPosition> existingPath = existingPaths.get(agvId);
//            if (existingPath != null && !hasConflictWithMainPath(existingPath, mainPath)) {
//                // 无冲突，保持原有路径
//                result.put(agvId, existingPath);
//                reservePath(agvId, existingPath);
//            } else {
//                // 有冲突，重新规划
//                List<AgvPosition> newPath = aStarWithTimeWindows(start, goal, agvId);
//                if (!newPath.isEmpty()) {
//                    result.put(agvId, newPath);
//                    reservePath(agvId, newPath);
//                }
//            }
//        }
//
//        // 4. 最终冲突解决（基于优先级）
//        resolveRemainingConflicts(result);
//
//        return result;
//    }
//
//    /**
//     * 带时间窗的A*算法（核心路径规划）
//     */
//    private List<AgvPosition> aStarWithTimeWindows(AgvPosition start, AgvPosition goal, String agvId) {
//        // 节点状态：位置 + 时间步
//
//
//        // 优先队列
//        PriorityQueue<NodeState> openSet = new PriorityQueue<>(
//                Comparator.comparingDouble(node -> node.f)
//        );
//
//        // 访问记录
//        Map<String, NodeState> visited = new HashMap<>();
//
//        // 启发式函数（曼哈顿距离）
//        double h = heuristic(start, goal);
//        NodeState startNode = new NodeState(start, 0, 0, h, null);
//        openSet.add(startNode);
//        visited.put(getKey(start, 0), startNode);
//
//        // 最大搜索步数
//        int maxSteps = 10000;
//
//        while (!openSet.isEmpty() && maxSteps-- > 0) {
//            NodeState current = openSet.poll();
//
//            // 到达目标
//            if (current.pos.equals(goal) && !isReserved(goal, current.time)) {
//                return reconstructPath(current);
//            }
//
//            // 扩展邻居
//            for (AgvPosition neighbor : getNeighbors(current.pos)) {
//                int nextTime = current.time + 1;
//
//                // 检查约束：1. 是否可通过 2. 是否被预留
////                if (!mapGrid.isPassable(neighbor) || isReserved(neighbor, nextTime)) {
////                    continue;
////                }
//
//                // 计算代价
//                double g = current.g + 1; // 每步代价为1
//                double hNew = heuristic(neighbor, goal);
//                double f = g + hNew;
//
//                String key = getKey(neighbor, nextTime);
//                NodeState existing = visited.get(key);
//
//                if (existing == null || g < existing.g) {
//                    NodeState newNode = new NodeState(neighbor, nextTime, g, f, current);
//                    openSet.add(newNode);
//                    visited.put(key, newNode);
//                }
//            }
//        }
//        log.debug("搜索步数剩余:{}", maxSteps);
//
//        return Collections.emptyList(); // 未找到路径
//    }
//
//    /**
//     * 检查预留冲突
//     */
//    private boolean isReserved(AgvPosition pos, int time) {
//        Set<AgvPosition> reservations = timeReservations.get(time);
//        return reservations != null && reservations.contains(pos);
//    }
//
//    /**
//     * 预留路径
//     */
//    private void reservePath(String agvId, List<AgvPosition> path) {
//        // 清除旧预留
//        List<AgvPosition> oldPath = agvPaths.get(agvId);
//        if (oldPath != null) {
//            for (int t = 0; t < oldPath.size(); t++) {
//                Set<AgvPosition> reservations = timeReservations.get(t);
//                if (reservations != null) {
//                    reservations.remove(oldPath.get(t));
//                }
//            }
//        }
//
//        // 添加新预留
//        for (int t = 0; t < path.size(); t++) {
//            timeReservations.computeIfAbsent(t, k -> new HashSet<>()).add(path.get(t));
//        }
//
//        agvPaths.put(agvId, path);
//
//        // 清理过时的预留
//        cleanupOldReservations();
//    }
//
//    /**
//     * 冲突解决算法（基于优先级和等待）
//     */
//    private void resolveRemainingConflicts(Map<String, List<AgvPosition>> paths) {
//        // 检测冲突
//        Map<Integer, Map<AgvPosition, List<String>>> timePositionMap = new HashMap<>();
//
//        for (Map.Entry<String, List<AgvPosition>> entry : paths.entrySet()) {
//            String agvId = entry.getKey();
//            List<AgvPosition> path = entry.getValue();
//
//            for (int t = 0; t < path.size(); t++) {
//                AgvPosition pos = path.get(t);
//                timePositionMap.computeIfAbsent(t, k -> new HashMap<>())
//                        .computeIfAbsent(pos, k -> new ArrayList<>())
//                        .add(agvId);
//            }
//        }
//
//        // 解决冲突（简单策略：让优先级低的AGV等待）
//        for (Map.Entry<Integer, Map<AgvPosition, List<String>>> timeEntry : timePositionMap.entrySet()) {
//            int time = timeEntry.getKey();
//            Map<AgvPosition, List<String>> positionAgvs = timeEntry.getValue();
//
//            for (Map.Entry<AgvPosition, List<String>> posEntry : positionAgvs.entrySet()) {
//                List<String> agvIds = posEntry.getValue();
//                if (agvIds.size() > 1) {
//                    // 冲突检测：多个AGV在同一时间占据同一位置
//                    resolveConflict(agvIds, paths, time);
//                }
//            }
//        }
//    }
//
//    private void resolveConflict(List<String> conflictingAgvs,
//                                 Map<String, List<AgvPosition>> paths,
//                                 int conflictTime) {
//        // 简单策略：让除第一个AGV外的其他AGV插入等待
//        String primaryAgv = conflictingAgvs.get(0);
//
//        for (int i = 1; i < conflictingAgvs.size(); i++) {
//            String agvId = conflictingAgvs.get(i);
//            List<AgvPosition> path = paths.get(agvId);
//
//            if (path != null && conflictTime < path.size()) {
//                // 在冲突时间前插入等待点
//                AgvPosition waitPos = path.get(Math.max(0, conflictTime - 1));
//                for (int j = conflictTime; j < path.size(); j++) {
//                    // 向后移动路径
//                    if (j + 1 < path.size()) {
//                        path.set(j, path.get(j + 1));
//                    }
//                }
//                // 在冲突位置插入额外的等待
//                path.add(conflictTime, waitPos);
//                path.add(conflictTime + 1, waitPos); // 等待一个时间步
//            }
//        }
//    }
//
//    // 工具方法
//    private double heuristic(AgvPosition a, AgvPosition b) {
//        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
//    }
//
//    private List<AgvPosition> getNeighbors(AgvPosition pos) {
//        List<AgvPosition> neighbors = new ArrayList<>();
//        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}; // 4方向
//
//        for (int[] dir : directions) {
//            AgvPosition neighbor = new AgvPosition(
//                    pos.getX() + dir[0],
//                    pos.getY() + dir[1],
//                    0.0
//            );
//            neighbors.add(neighbor);
//        }
//
//        return neighbors;
//    }
//
//    private List<AgvPosition> reconstructPath(NodeState goalNode) {
//        List<AgvPosition> path = new ArrayList<>();
//        NodeState current = goalNode;
//
//        while (current != null) {
//            path.add(0, current.pos);
//            current = current.parent;
//        }
//
//        return path;
//    }
//
//    private String getKey(AgvPosition pos, int time) {
//        return pos.getX() + "," + pos.getY() + "@" + time;
//    }
//
//    private AgvPosition getCurrentGoal(String agvId, Map<String, List<AgvPosition>> existingPaths) {
//        List<AgvPosition> path = existingPaths.get(agvId);
//        if (path != null && !path.isEmpty()) {
//            return path.get(path.size() - 1);
//        }
//        return null;
//    }
//
//    private boolean hasConflictWithMainPath(List<AgvPosition> existingPath, List<AgvPosition> mainPath) {
//        int minLength = Math.min(existingPath.size(), mainPath.size());
//
//        for (int t = 0; t < minLength; t++) {
//            if (existingPath.get(t).equals(mainPath.get(t))) {
//                return true; // 位置冲突
//            }
//
//            // 检查交换冲突（A到B，B到A）
//            if (t > 0 && t < minLength) {
//                if (existingPath.get(t).equals(mainPath.get(t - 1)) &&
//                        existingPath.get(t - 1).equals(mainPath.get(t))) {
//                    return true; // 交换冲突
//                }
//            }
//        }
//
//        return false;
//    }
//
//    private void cleanupOldReservations() {
//        int currentMaxTime = timeReservations.keySet().stream()
//                .max(Integer::compareTo)
//                .orElse(0);
//
//        // 保留最近100个时间步
//        timeReservations.keySet().removeIf(time -> time < currentMaxTime - 100);
//    }
//
//    static class NodeState {
//        AgvPosition pos;
//        int time;
//        double g; // 实际代价
//        double f; // 估计总代价
//        NodeState parent;
//
//        NodeState(AgvPosition pos, int time, double g, double f, NodeState parent) {
//            this.pos = pos;
//            this.time = time;
//            this.g = g;
//            this.f = f;
//            this.parent = parent;
//        }
//    }
//}
