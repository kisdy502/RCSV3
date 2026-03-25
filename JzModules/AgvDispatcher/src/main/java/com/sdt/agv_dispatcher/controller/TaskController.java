package com.sdt.agv_dispatcher.controller;

import com.jizhi.data.CommonResult;
import com.jizhi.data.ResultCode;
import com.jizhi.vda5050.agv.TaskStatus;
import com.sdt.agv_dispatcher.domain.Task;
import com.sdt.agv_dispatcher.scheduler.AgvTaskDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/tasks")
@Slf4j
public class TaskController {

    @Autowired
    private AgvTaskDispatcher agvTaskDispatcher;

    /**
     * 获取任务列表数据（API接口）
     */
    @GetMapping("/api/list")
    public CommonResult<Map<String, Object>> getTaskList() {
        try {
            Collection<Task> tasks = agvTaskDispatcher.getAllTasks();

            // 统计信息
            Map<String, Long> stats = new HashMap<>();
            stats.put("running", tasks.stream().filter(t -> t.getStatus() == TaskStatus.RUNNING).count());
            stats.put("initializing", tasks.stream().filter(t -> t.getStatus() == TaskStatus.INITIALIZING).count());
            stats.put("finished", tasks.stream().filter(t -> t.getStatus() == TaskStatus.FINISHED).count());
            stats.put("failed", tasks.stream().filter(t -> t.getStatus() == TaskStatus.FAILED).count());
            stats.put("cancelled", tasks.stream().filter(t -> t.getStatus() == TaskStatus.CANCELLED).count());
            stats.put("total", (long) tasks.size());

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("tasks", tasks);
            resultData.put("stats", stats);
            resultData.put("total", tasks.size());
            resultData.put("timestamp", System.currentTimeMillis());

            log.info("获取任务列表成功，共{}个任务", tasks.size());
            return CommonResult.success(resultData, "获取任务列表成功");

        } catch (Exception e) {
            log.error("获取任务列表失败", e);
            return CommonResult.failed("获取任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取单个任务详情（API接口）
     */
    @GetMapping("/api/detail")
    public CommonResult<Task> getTaskDetail(@RequestParam String taskId) {
        try {
            if (taskId == null || taskId.trim().isEmpty()) {
                return CommonResult.validateFailed("任务ID不能为空");
            }

            Task task = agvTaskDispatcher.getTaskByTaskId(taskId);
            if (task != null) {
                log.info("获取任务详情成功: taskId={}, status={}", taskId, task.getStatus());
                return CommonResult.success(task, "获取任务详情成功");
            } else {
                log.warn("任务不存在: taskId={}", taskId);
                return CommonResult.failed(ResultCode.FAILED, "任务不存在: " + taskId);
            }

        } catch (Exception e) {
            log.error("获取任务详情失败: taskId={}", taskId, e);
            return CommonResult.failed("获取任务详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务状态统计（API接口）
     */
    @GetMapping("/api/status-stats")
    public CommonResult<Map<String, Object>> getTaskStatusStats() {
        try {
            Collection<Task> tasks = agvTaskDispatcher.getAllTasks();

            Map<String, Long> stats = new HashMap<>();
            long total = 0;
            for (TaskStatus status : TaskStatus.values()) {
                long count = tasks.stream().filter(t -> t.getStatus() == status).count();
                stats.put(status.name().toLowerCase(), count);
                total += count;
            }

            // 添加其他统计信息
            stats.put("total", total);

            // 计算各状态占比 - 分离百分比信息到另一个map
            Map<String, String> percentages = new HashMap<>();
            if (total > 0) {
                for (TaskStatus status : TaskStatus.values()) {
                    long count = stats.get(status.name().toLowerCase());
                    double percentage = (double) count / total * 100;
                    percentages.put(status.name().toLowerCase(), String.format("%.1f%%", percentage));
                }
            }

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("stats", stats);
            resultData.put("percentages", percentages);
            resultData.put("total", total);
            resultData.put("timestamp", System.currentTimeMillis());

            log.info("获取任务状态统计成功，共{}个任务", total);
            return CommonResult.success(resultData, "获取任务状态统计成功");

        } catch (Exception e) {
            log.error("获取任务状态统计失败", e);
            return CommonResult.failed("获取任务状态统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取正在运行的任务列表
     */
    @GetMapping("/api/running")
    public CommonResult<Map<String, Object>> getRunningTasks() {
        try {
            Collection<Task> allTasks = agvTaskDispatcher.getAllTasks();
            Collection<Task> runningTasks = allTasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.RUNNING ||
                            t.getStatus() == TaskStatus.INITIALIZING)
                    .toList();

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("tasks", runningTasks);
            resultData.put("count", runningTasks.size());
            resultData.put("timestamp", System.currentTimeMillis());

            log.info("获取运行中任务列表成功，共{}个运行中任务", runningTasks.size());
            return CommonResult.success(resultData, "获取运行中任务列表成功");

        } catch (Exception e) {
            log.error("获取运行中任务列表失败", e);
            return CommonResult.failed("获取运行中任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取已完成的任务列表
     */
    @GetMapping("/api/completed")
    public CommonResult<Map<String, Object>> getCompletedTasks() {
        try {
            Collection<Task> allTasks = agvTaskDispatcher.getAllTasks();
            Collection<Task> completedTasks = allTasks.stream()
                    .filter(t -> t.getStatus() == TaskStatus.FINISHED ||
                            t.getStatus() == TaskStatus.FAILED ||
                            t.getStatus() == TaskStatus.CANCELLED)
                    .toList();

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("tasks", completedTasks);
            resultData.put("count", completedTasks.size());

            // 分类统计
            Map<String, Long> completedStats = new HashMap<>();
            completedStats.put("finished", completedTasks.stream().filter(t -> t.getStatus() == TaskStatus.FINISHED).count());
            completedStats.put("failed", completedTasks.stream().filter(t -> t.getStatus() == TaskStatus.FAILED).count());
            completedStats.put("cancelled", completedTasks.stream().filter(t -> t.getStatus() == TaskStatus.CANCELLED).count());

            resultData.put("stats", completedStats);
            resultData.put("timestamp", System.currentTimeMillis());

            log.info("获取已完成任务列表成功，共{}个已完成任务", completedTasks.size());
            return CommonResult.success(resultData, "获取已完成任务列表成功");

        } catch (Exception e) {
            log.error("获取已完成任务列表失败", e);
            return CommonResult.failed("获取已完成任务列表失败: " + e.getMessage());
        }
    }
}