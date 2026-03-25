package com.sdt.agv_dispatcher.component;


import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AgvDispatcherRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("==============================");
        log.info("AGV路径规划测试系统启动完成");
        log.info("==============================");

        // 测试API端点
        printTestEndpoints();
    }

    private void printTestEndpoints() {
        log.info("");
        log.info("测试API端点:");
        log.info("--------------------------------");
        log.info("1. GET  /api/dispatch/path/map/info - 获取地图信息");
        log.info("2. GET  /api/dispatch/path/graph/validate - 验证图连通性");
        log.info("5. POST /api/dispatch/path/task/simulate - 模拟发送任务");
        log.info("7. GET  /api/dispatch/path/route/visualize?startNode=ST001&endNode=ST005 - 可视化路线");
        log.info("--------------------------------");
        log.info("前端测试页面: http://localhost:11555/index.html");
        log.info("==============================");
    }
}
