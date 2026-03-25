package com.jizhi.vda5050.agv;

/**
 * 操作模式枚举 (VDA5050标准)
 */
public enum OperationMode {
    AUTOMATIC("AUTOMATIC"),          // 自动模式
    MANUAL("MANUAL"),                // 手动模式
    SEMIAUTOMATIC("SEMIAUTOMATIC"),  // 半自动模式
    SERVICE("SERVICE"),              // 服务模式
    TEACHIN("TEACHIN");              // 示教模式

    private final String value;

    OperationMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OperationMode fromValue(String value) {
        if (value == null) return AUTOMATIC;

        String normalized = value.toUpperCase().trim();

        switch (normalized) {
            case "MANUAL":
                return MANUAL;
            case "SEMIAUTOMATIC":
                return SEMIAUTOMATIC;
            case "SERVICE":
                return SERVICE;
            case "TEACHIN":
                return TEACHIN;
            default:
                return AUTOMATIC;
        }
    }
}
