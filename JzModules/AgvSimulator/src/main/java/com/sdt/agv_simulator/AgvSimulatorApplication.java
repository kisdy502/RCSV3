package com.sdt.agv_simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgvSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgvSimulatorApplication.class, args);
        System.out.println("===========================================");
        System.out.println("AGV模拟器启动成功！");
        System.out.println("功能：");
        System.out.println("1. 模拟多个AGV设备上线/下线");
        System.out.println("2. 接收调度任务并模拟执行");
        System.out.println("3. 实时上报AGV状态和位置");
        System.out.println("4. 支持VDA5050协议通信");
    }

}
