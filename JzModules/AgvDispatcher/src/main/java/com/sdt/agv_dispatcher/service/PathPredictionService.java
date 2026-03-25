package com.sdt.agv_dispatcher.service;

import java.util.Set;

public interface PathPredictionService {
    Set<String> predictFutureNodes(String agvId, int steps);
    double predictArrivalTime(String agvId, String targetNode);
    boolean predictTimeWindowConflict(String agv1, String agv2, String sharedResource);
}