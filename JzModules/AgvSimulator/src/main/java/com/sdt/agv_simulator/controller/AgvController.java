package com.sdt.agv_simulator.controller;


import com.jizhi.data.CommonResult;
import com.jizhi.vda5050.agv.AgvPosition;
import com.jizhi.vda5050.agv.AgvStatus;
import com.jizhi.vda5050.domain.Edge;
import com.jizhi.vda5050.domain.Node;
import com.sdt.agv_simulator.agv.VirtualAgv;
import com.sdt.agv_simulator.component.Ros2WebSocketClient;
import com.sdt.agv_simulator.dto.AgvStatusResponse;
import com.sdt.agv_simulator.dto.LaserScanDto;
import com.sdt.agv_simulator.dto.MoveToRequest;
import com.sdt.agv_simulator.dto.PositionUpdateDto;
import com.sdt.agv_simulator.service.AgvStatusManager;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/simulator")
@Slf4j
public class AgvController {

    @Autowired
    private AgvStatusManager agvStatusManager;

    @Autowired
    private VirtualAgv virtualAgv;

    @Autowired
    private Ros2WebSocketClient rosWebSocketClient;

    @GetMapping("/agv")
    @ResponseBody
    public CommonResult<AgvStatusResponse> getAgvInfo() {
        AgvStatusResponse agvStatusResponse = new AgvStatusResponse();
        agvStatusResponse.setAgvStatus(virtualAgv.getAgvStatus());
        LaserScanDto laserScan = agvStatusManager.getLastLaserScan();
        agvStatusResponse.setLaserScan(laserScan);
        return CommonResult.success(agvStatusResponse);
    }

    @GetMapping("/init-pose")
    @ResponseBody
//    @ApiOperation("设置机器人初始点位")
    public CommonResult<?> initRobotPose(@RequestParam(name = "x") double x,
                                         @RequestParam(name = "y") double y,
                                         @RequestParam(name = "theta") double theta) {
        agvStatusManager.initAgvPosition(x, y, theta);
        return CommonResult.success(null, "设置机器人初始点位成功!");
    }

    @GetMapping("/init-pose-node")
    @ResponseBody
//    @ApiOperation("设置机器人初始点位")
    public CommonResult<?> initRobotPoseByNodeId(@RequestParam(name = "nodeId") String nodeId) {
        boolean res = agvStatusManager.initAgvPosition(nodeId);
        if (res) {
            return CommonResult.success(null, "设置机器人初始点位成功!");
        } else {
            return CommonResult.failed("设置机器人初始点位失败，不存在站点:" + nodeId);
        }
    }

    @PostMapping("/simulate-fault")
    @ResponseBody
//    @ApiOperation("模拟故障")
    public CommonResult<Map<String, Object>> simulateFault(@RequestBody FaultRequest request) {
        agvStatusManager.simulateFault(request.getErrorCode(), request.getDescription());
        Map<String, Object> result = new HashMap<>();
        result.put("errorCode", request.getErrorCode());
        result.put("simulated", true);

        return CommonResult.success(result);
    }

    @PostMapping("/clear-fault")
    @ResponseBody
//    @ApiOperation("清除故障")
    public CommonResult<Map<String, Object>> clearFault(@PathVariable String agvId) {
        agvStatusManager.clearFault(agvId);

        Map<String, Object> result = new HashMap<>();
        result.put("agvId", agvId);
        result.put("faultCleared", true);
        return CommonResult.success(result);
    }

    @PostMapping("/control")
    @ResponseBody
//    @ApiOperation("发送控制命令")
    public CommonResult<Map<String, Object>> sendControlCommand(@RequestBody ControlCommand request) {
        agvStatusManager.processControlCommand(request.getCommand(), request.getParameters());
        Map<String, Object> result = new HashMap<>();
        result.put("command", request.getCommand());
        result.put("sent", true);
        return CommonResult.success(result);
    }

    // 控制接口
    @PostMapping("/move_to")
    public CommonResult<?> moveTo(@RequestBody MoveToRequest request) {
        Node node = new Node();
        node.setId(request.getNodeId());
        node.setX(request.getX());
        node.setY(request.getY());
        node.setTheta(request.getTheta());
        rosWebSocketClient.sendMoveCommand(
                virtualAgv.getAgvStatus().getAgvId(),
                request.getCommandId(),node, new Edge(), true);


        return CommonResult.success("", "移动控制指令已发送");
    }

    @PostMapping("/reset")
    @ResponseBody
//    @ApiOperation("重置模拟器")
    public CommonResult<Map<String, Object>> resetSimulator() {
        // 这里可以实现重置逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("reset", true);
        result.put("message", "模拟器已重置");
        return CommonResult.success(result);
    }

    @Data
    public static class FaultRequest {
        private String errorCode;
        private String description;
    }

    @Data
    public static class ControlCommand {
        private String command;
        private Map<String, Object> parameters = new HashMap<>();
    }
}
