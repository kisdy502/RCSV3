package com.sdt.agv_dispatcher.conflict;

public enum ResolutionAction {

    SLOW_DOWN("减速避让"),
    WAIT("暂停等待"),
    REPLAN("重新规划"),
    STOP("紧急停止"),
    PROCEED("继续执行"),
    PROCEED_WITH_NOTIFICATION("继续并通知对方"); // 新增
    private final String description;

    ResolutionAction(String desc) {
        this.description = desc;
    }

    }
