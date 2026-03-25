package com.sdt.agv_dispatcher.conflict;

import java.util.*;

/**
 * 等待图：用于死锁检测
 */
public class WaitGraph {

    // 等待关系：AGV -> 它在等待哪个AGV
    private Map<String, String> waitRelations = new HashMap<>();

    /**
     * 添加等待关系
     */
    public void addWaitRelation(String waitingAgv, String blockingAgv) {
        waitRelations.put(waitingAgv, blockingAgv);
    }

    /**
     * 移除等待关系
     */
    public void removeWaitRelation(String agvId) {
        waitRelations.remove(agvId);
    }

    /**
     * 检查是否会形成环（死锁）
     */
    public boolean wouldCreateCycle(String newAgv, String blockingAgv) {
        // 临时添加关系
        waitRelations.put(newAgv, blockingAgv);
        boolean hasCycle = detectCycle(newAgv);
        // 恢复（实际由调用方决定是否添加）
        waitRelations.remove(newAgv);
        return hasCycle;
    }

    /**
     * 从指定AGV开始检测环
     */
    private boolean detectCycle(String startAgv) {
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        return detectCycleUtil(startAgv, visited, recStack);
    }

    private boolean detectCycleUtil(String agv, Set<String> visited, Set<String> recStack) {
        visited.add(agv);
        recStack.add(agv);

        String next = waitRelations.get(agv);
        if (next != null) {
            if (!visited.contains(next)) {
                if (detectCycleUtil(next, visited, recStack)) {
                    return true;
                }
            } else if (recStack.contains(next)) {
                return true;  // 发现环
            }
        }

        recStack.remove(agv);
        return false;
    }

    /**
     * 查找包含指定AGV的环
     */
    public List<String> findCycle(String agvId) {
        List<String> cycle = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        findCycleUtil(agvId, visited, cycle);
        return cycle;
    }

    private boolean findCycleUtil(String agv, Set<String> visited, List<String> path) {
        if (visited.contains(agv)) {
            // 发现环，提取环路径
            int index = path.indexOf(agv);
            if (index != -1) {
                return true;
            }
            return false;
        }

        visited.add(agv);
        path.add(agv);

        String next = waitRelations.get(agv);
        if (next != null) {
            if (findCycleUtil(next, visited, path)) {
                return true;
            }
        }

        path.remove(path.size() - 1);
        return false;
    }

    /**
     * 获取等待链长度
     */
    public int getWaitChainLength(String agvId) {
        int length = 0;
        String current = agvId;
        Set<String> visited = new HashSet<>();

        while (current != null && !visited.contains(current)) {
            visited.add(current);
            current = waitRelations.get(current);
            length++;
        }

        return length;
    }

    /**
     * 获取完整的等待链
     */
    public List<String> getWaitChain(String agvId) {
        List<String> chain = new ArrayList<>();
        String current = agvId;
        Set<String> visited = new HashSet<>();

        while (current != null && !visited.contains(current)) {
            visited.add(current);
            chain.add(current);
            current = waitRelations.get(current);
        }

        return chain;
    }
}