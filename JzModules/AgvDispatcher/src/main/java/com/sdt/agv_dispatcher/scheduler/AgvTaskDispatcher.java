package com.sdt.agv_dispatcher.scheduler;


import com.jizhi.vda5050.agv.TaskStatus;
import com.sdt.agv_dispatcher.domain.Task;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AgvTaskDispatcher {

    // 任务存储
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    private final Map<String, String> agvCurrentTask = new ConcurrentHashMap<>();

    /**
     * 分配任务给 AGV
     *
     * @param task  待分配的任务（需已生成 ID）
     * @param agvId 目标 AGV 编号
     */
    public void assignTask(Task task, String agvId) {
        if (task == null || StringUtils.isBlank(task.getId()) || StringUtils.isBlank(agvId)) {
            throw new IllegalArgumentException("Task and AGV ID must be valid");
        }
        tasks.put(task.getId(), task);
        agvCurrentTask.put(agvId, task.getId());
        log.info("Task {} assigned to AGV {}", task.getId(), agvId);
    }

    /**
     * 获取任务状态
     */
    public Task getTaskByTaskId(String taskId) {
        return tasks.get(taskId);
    }


    public Task getAgvCurrentTask(String agvId) {
        String taskId = agvCurrentTask.get(agvId);
        if (StringUtils.isNotEmpty(taskId)) {
            return tasks.get(taskId);
        }
        return null;
    }

    /**
     * 获取所有任务
     */
    public Collection<Task> getAllTasks() {
        return tasks.values();
    }

    /**
     * 获取AGV的当前任务
     */
    public String getAgvCurrentTaskId(String agvId) {
        return agvCurrentTask.get(agvId);
    }

    public String removeAgvTask(String agvId) {
        return agvCurrentTask.remove(agvId);
    }

    public void updateTaskStatus(String taskId, String state) {
        Task task = tasks.get(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.valueOf(state));
        }
    }

    public void finishTask(String agvId) {
        if (agvCurrentTask.containsKey(agvId)) {
            String taskId = agvCurrentTask.remove(agvId);
            if (tasks.containsKey(taskId)) {
                Task task = tasks.get(taskId);
                task.complete();
            }
        }
    }

    public void failTask(String agvId, String errorMessage) {
        if (agvCurrentTask.containsKey(agvId)) {
            String taskId = agvCurrentTask.remove(agvId);
            if (tasks.containsKey(taskId)) {
                Task task = tasks.get(taskId);
                task.fail(errorMessage);
            }
        }
    }

    public void cancelTask(String agvId) {
        if (agvCurrentTask.containsKey(agvId)) {
            String taskId = agvCurrentTask.remove(agvId);
            if (tasks.containsKey(taskId)) {
                Task task = tasks.get(taskId);
                task.cancel();
            }
        }
    }
}
