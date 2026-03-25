package com.jizhi.vda5050.message;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.jizhi.vda5050.agv.AgvActionState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * VDA5050 行动状态消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vda5050ActionMessage {

    @JsonProperty("header")
    private Vda5050Header header;

    @JsonProperty("actionStates")
    private List<AgvActionState> actionStates;


}
