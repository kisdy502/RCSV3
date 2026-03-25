package com.sdt.agv_dispatcher.conflict;

import com.sdt.agv_dispatcher.domain.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class ConflictResolver {

    public ResolutionStrategy resolve(List<ConflictInfo> conflicts, Task task) {
        if (conflicts.isEmpty()) {
            return ResolutionStrategy.proceed();
        }

        // 取第一个冲突（通常只有一个）
        ConflictInfo conflict = conflicts.get(0);

        int myPriority = task.getPriority();
        int otherPriority = conflict.getOtherAgvPriority() != null ? conflict.getOtherAgvPriority() : 0;

        if (myPriority < otherPriority) {
            // 我方优先级低，等待冲突资源释放
            // 由于锁没有预计释放时间，可以设定一个固定等待时间（如5秒）
            return ResolutionStrategy.waitFor(5);
        } else if (myPriority > otherPriority) {
            // 我方优先级高，继续执行，但可通知对方
            return ResolutionStrategy.proceedWithNotification();
        } else {
            // 优先级相同，根据资源类型简单处理
            if ("EDGE".equals(conflict.getResourceType())) {
                // 边冲突，通常等待
                return ResolutionStrategy.waitFor(5);
            } else {
                // 节点冲突，可减速或等待
                return ResolutionStrategy.adjustSpeed(0.3, "节点冲突减速");
            }
        }
    }


}
