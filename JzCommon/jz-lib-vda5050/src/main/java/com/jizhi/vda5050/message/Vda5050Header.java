package com.jizhi.vda5050.message;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 消息头部 (Header)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public  class Vda5050Header {
    /**
     * 消息ID (唯一标识)
     */
    @JsonProperty("headerId")
    private Long headerId;

//    @JsonProperty("timestamp")
//    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSS][.SSS][XXXXX][XXXX][X]", timezone = "UTC")
//    private LocalDateTime timestamp;

    /**
     * 时间戳 (UTC时间)
     */
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            timezone = "UTC")
    private Instant timestamp;

    /**
     * 协议版本 (默认2.0.0)
     */
    @JsonProperty("version")
    @Builder.Default
    private String version = "2.0.0";

    /**
     * 制造商
     */
    @JsonProperty("manufacturer")
    private String manufacturer;

    /**
     * 序列号
     */
    @JsonProperty("serialNumber")
    private String serialNumber;

    /**
     * 获取当前时间的UTC时间戳
     */
    public static LocalDateTime currentTimestamp() {
        return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }

    /**
     * 格式化为ISO 8601字符串
     */
    public String formatTimestamp() {
        if (timestamp == null) {
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(timestamp);
    }
}
