package com.sdt.agv_simulator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class MessageFrequencyService {

    private final AtomicLong positionUpdateCount = new AtomicLong(0);
    private final AtomicLong statusUpdateCount = new AtomicLong(0);
    private final AtomicLong laserScanCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        log.info("消息频率统计服务已启动");
    }

    @Scheduled(fixedRate = 24000L)
    public void calculateAndLogFrequency() {
        float timeCount = 24f;
        float posCount = positionUpdateCount.getAndSet(0) / timeCount;
        float statusCount = statusUpdateCount.getAndSet(0) / timeCount;
        float laserCount = laserScanCount.getAndSet(0) / timeCount;

        if (posCount > 0 || statusCount > 0 || laserCount > 0) {
            log.info("消息频率-位置更新:{} Hz,状态更新:{} Hz,雷达数据: {} Hz", posCount, statusCount, laserCount);
        }
    }

    public void recordPositionUpdate() {
        positionUpdateCount.incrementAndGet();
    }

    public void recordStatusUpdate() {
        statusUpdateCount.incrementAndGet();
    }

    public void recordLaserScan() {
        laserScanCount.incrementAndGet();
    }
}