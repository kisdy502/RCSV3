package com.jizhi.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

@Configuration
public class JacksonConfig {

    /**
     * 自定义 LocalDateTime 反序列化器
     * 支持多种时间格式，包括带微秒的 ISO 8601
     */
    public static class CustomLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        // 支持多种时间格式
        private static final DateTimeFormatter[] FORMATTERS = {
                // ISO 8601 带微秒（最多9位小数）
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .append(DateTimeFormatter.ISO_LOCAL_DATE)
                        .appendLiteral('T')
                        .append(DateTimeFormatter.ISO_LOCAL_TIME)
                        .toFormatter(),
                // ISO 8601 带时区
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                // ISO 8601 带微秒和时区
                new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .append(DateTimeFormatter.ISO_LOCAL_DATE)
                        .appendLiteral('T')
                        .append(DateTimeFormatter.ISO_LOCAL_TIME)
                        .appendOffsetId()
                        .toFormatter()
        };

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            String dateStr = p.getText();

            // 尝试所有格式
            for (DateTimeFormatter formatter : FORMATTERS) {
                try {
                    return LocalDateTime.parse(dateStr, formatter);
                } catch (Exception e) {
                    // 继续尝试下一个格式
                }
            }

            // 如果都不行，尝试移除纳秒部分
            try {
                // 移除纳秒部分，只保留到毫秒
                String truncated = dateStr;
                int dotIndex = dateStr.indexOf('.');
                if (dotIndex > 0) {
                    int zIndex = dateStr.indexOf('Z', dotIndex);
                    if (zIndex > 0) {
                        // 处理带Z的情况，如 "2026-01-29T14:38:52.268440Z"
                        truncated = dateStr.substring(0, Math.min(dotIndex + 4, zIndex)) + "Z";
                    } else {
                        // 处理不带Z的情况，如 "2026-01-29T14:38:52.268440"
                        int plusIndex = dateStr.indexOf('+', dotIndex);
                        if (plusIndex > 0) {
                            truncated = dateStr.substring(0, Math.min(dotIndex + 4, plusIndex)) +
                                    dateStr.substring(plusIndex);
                        } else {
                            truncated = dateStr.substring(0, Math.min(dotIndex + 4, dateStr.length()));
                        }
                    }
                }
                return LocalDateTime.parse(truncated, FORMATTERS[0]);
            } catch (Exception e) {
                throw new IOException("无法解析时间字符串: " + dateStr, e);
            }
        }
    }

    /**
     * 自定义 LocalDateTime 序列化器
     */
    public static class CustomLocalDateTimeSerializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            String dateStr = p.getText();

            // 移除纳秒部分，只保留到毫秒
            String truncated = dateStr;
            int dotIndex = dateStr.indexOf('.');
            if (dotIndex > 0) {
                int zIndex = dateStr.indexOf('Z', dotIndex);
                if (zIndex > 0) {
                    truncated = dateStr.substring(0, Math.min(dotIndex + 4, zIndex)) + "Z";
                } else {
                    int plusIndex = dateStr.indexOf('+', dotIndex);
                    if (plusIndex > 0) {
                        truncated = dateStr.substring(0, Math.min(dotIndex + 4, plusIndex)) +
                                dateStr.substring(plusIndex);
                    } else {
                        truncated = dateStr.substring(0, Math.min(dotIndex + 4, dateStr.length()));
                    }
                }
            }

            return LocalDateTime.parse(truncated, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    /**
     * 配置 ObjectMapper，支持 VDA5050 时间格式
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 注册 JavaTimeModule
        JavaTimeModule javaTimeModule = new JavaTimeModule();

        // 注册自定义的反序列化器
        SimpleModule module = new SimpleModule();
        module.addDeserializer(LocalDateTime.class, new CustomLocalDateTimeDeserializer());
        objectMapper.registerModule(javaTimeModule);
        objectMapper.registerModule(module);

        // 配置日期格式
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return objectMapper;
    }
}
