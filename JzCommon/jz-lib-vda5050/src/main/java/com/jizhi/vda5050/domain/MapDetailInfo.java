package com.jizhi.vda5050.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapDetailInfo {
    private MapMetaData mapInfo;
    private String mapName;
    private List<Node> nodes;
    private List<Edge> edges;
}
