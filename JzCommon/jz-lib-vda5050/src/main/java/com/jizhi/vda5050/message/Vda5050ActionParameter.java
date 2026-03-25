package com.jizhi.vda5050.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行动参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Vda5050ActionParameter {
        /**
         * 参数键 (必需)
         */
        @JsonProperty("key")
        private String key;

        /**
         * 参数值 (必需)
         */
        @JsonProperty("value")
        private Object value;

        /**
         * 值类型 (可选)
         * 可选值: STRING, INTEGER, FLOAT, BOOLEAN, OBJECT, ARRAY
         */
        @JsonProperty("valueType")
        private String valueType;

        /**
         * 参数描述 (可选)
         */
        @JsonProperty("description")
        private String description;
    }