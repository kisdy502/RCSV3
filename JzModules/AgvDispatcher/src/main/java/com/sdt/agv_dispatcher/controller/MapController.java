package com.sdt.agv_dispatcher.controller;

import com.sdt.agv_dispatcher.config.MapConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/map")
@Slf4j
public class MapController {

    @Autowired
    private MapConfig mapConfig;

    @GetMapping("/image")
    public ResponseEntity<byte[]> getMapImage() {
        try {
            // 读取PGM文件
            Path mapPath = Paths.get(mapConfig.getDir(), mapConfig.getImageFile());
            byte[] imageData = Files.readAllBytes(mapPath);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG) // 或者 MediaType.IMAGE_PNG
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(imageData);
        } catch (Exception e) {
            log.error("获取地图数据出错:", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
