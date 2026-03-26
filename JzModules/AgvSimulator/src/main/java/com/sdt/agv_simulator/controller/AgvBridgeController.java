package com.sdt.agv_simulator.controller;

import com.jizhi.data.CommonResult;
import com.jizhi.vda5050.agv.AgvStatus;
import com.sdt.agv_simulator.component.Ros2WebSocketClient;
import com.sdt.agv_simulator.dto.*;
import com.sdt.agv_simulator.service.AgvStatusManager;
import com.sdt.agv_simulator.service.MessageFrequencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agv")
@Slf4j
public class AgvBridgeController {

    @Autowired
    private Ros2WebSocketClient rosWebSocketClient;

    @Autowired
    private AgvStatusManager agvStatusManager;

    @Autowired
    private MessageFrequencyService frequencyService; // 注入频率统计服务

    @PostMapping("/handle_position_update")
    public CommonResult<?> positionUpdate(@RequestBody PositionUpdateDto dto) {
        // 移除了详细日志，记录调用次数
        frequencyService.recordPositionUpdate();
        // 处理位置更新（存储到同步服务）
        agvStatusManager.processPositionUpdate(dto);
        return CommonResult.success("位置更新处理成功!");
    }

    @PostMapping("/handle_status")
    public CommonResult<?> statusUpdate(@RequestBody AgvStatusDto dto) {
        frequencyService.recordStatusUpdate();
        agvStatusManager.updateAgvStatus(dto);
        return CommonResult.success(dto, "状态设置成功");
    }

    @PostMapping("/handle_laser_scan")
    public CommonResult<?> laserScan(@RequestBody LaserScanDto dto) {
        frequencyService.recordLaserScan();
        // 处理雷达数据（存储到同步服务）
        agvStatusManager.setLastLaserScan(dto);
        return CommonResult.success(null, "雷达数据已收到");
    }

    // 命令确认
    @PostMapping("/handle_command_ack")
    public CommonResult<?> commandAck(@RequestBody CommandAckDto dto) {
        log.debug("命令确认: {}", dto);
        if ("move_result".equals(dto.getType())) {
            // 处理导航结果
            log.info("收到导航结果: AGV={}, 命令ID={},目标点id={}, 状态={}", dto.getAgvId(), dto.getCommandId(),
                    dto.getNodeId(), dto.getStatus());
            // 更新导航结果状态
            agvStatusManager.handleMovementResult(dto.getCommandId(), dto.getNodeId(), dto.getStatus(),
                    dto.getMessage());
        } else {
            // 处理普通的命令确认
            log.debug("命令确认: {}", dto);
        }
        return CommonResult.success(dto, "移动控制指令已发送");
    }

    @PostMapping("/control")
    public CommonResult<?> controlAgv(
            @RequestBody ControlRequest request) {
        rosWebSocketClient.sendAgvControl(agvStatusManager.getVirtualAgv().getAgvStatus().getAgvId(),
                request.getAction());
        return CommonResult.success("", "控制指令已发送");
    }
}
