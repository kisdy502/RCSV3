package com.sdt.agv_dispatcher.conflict;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConflictResult {
    private String agvId;
    private boolean hasConflict;
    private List<ConflictInfo> conflicts = new ArrayList<>();

}
