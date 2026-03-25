package com.sdt.agv_dispatcher.service;


import com.jizhi.vda5050.domain.PathResult;
import com.sdt.agv_dispatcher.domain.Task;
import com.sdt.agv_dispatcher.scheduler.AgvTaskDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class PathPredictionServiceImpl implements PathPredictionService {

    @Autowired
    private AgvTaskDispatcher taskDispatcher;

    @Override
    public Set<String> predictFutureNodes(String agvId, int steps) {
        Set<String> futureNodes = new HashSet<>();

        Task task = taskDispatcher.getAgvCurrentTask(agvId);
        if (task == null || task.getPathResult() == null) {
            return futureNodes;
        }

        PathResult path = task.getPathResult();
        List<String> nodeSequence = path.getNodeSequence();

        // 获取当前节点位置
        Integer currentSeqId = task.getCurrentNodeSequenceId();
        if (currentSeqId == null) {
            currentSeqId = 0;
        }

        // 预测接下来steps个节点
        for (int i = 1; i <= steps; i++) {
            int nextIndex = currentSeqId + i;
            if (nextIndex < nodeSequence.size()) {
                futureNodes.add(nodeSequence.get(nextIndex));
            } else {
                break; // 已经到达路径终点
            }
        }

        return futureNodes;
    }

    @Override
    public double predictArrivalTime(String agvId, String targetNode) {
        Task task = taskDispatcher.getAgvCurrentTask(agvId);
        if (task == null || task.getPathResult() == null) {
            return Double.MAX_VALUE;
        }

        PathResult path = task.getPathResult();
        List<String> nodeSequence = path.getNodeSequence();
        List<com.jizhi.vda5050.domain.PathResult.PathSegment> segments = path.getSegments();

        Integer currentSeqId = task.getCurrentNodeSequenceId();
        if (currentSeqId == null) currentSeqId = 0;

        double totalTime = 0.0;

        // 累加从当前位置到目标节点的时间
        for (int i = currentSeqId; i < nodeSequence.size() - 1; i++) {
            String fromNode = nodeSequence.get(i);
            String toNode = nodeSequence.get(i + 1);

            // 找到对应的segment
            for (com.jizhi.vda5050.domain.PathResult.PathSegment segment : segments) {
                if (segment.getFromNodeId().equals(fromNode) &&
                        segment.getToNodeId().equals(toNode)) {
                    totalTime += segment.getEstimatedTime();
                    break;
                }
            }

            // 如果到达目标节点，停止累加
            if (toNode.equals(targetNode)) {
                break;
            }
        }

        return totalTime;
    }

    @Override
    public boolean predictTimeWindowConflict(String agv1, String agv2, String sharedResource) {
        // 获取两车到达共享资源的时间
        double time1 = predictArrivalTime(agv1, sharedResource);
        double time2 = predictArrivalTime(agv2, sharedResource);

        // 如果时间差小于阈值（如5秒），认为有时间窗冲突
        double timeWindow = 5.0; // 5秒时间窗

        return Math.abs(time1 - time2) < timeWindow;
    }
}
