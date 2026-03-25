package com.jizhi.vda5050.agv;

/**
 * 电池状态枚举 (VDA5050标准)
 */
public enum BatteryState {
    UNKNOWN("UNKNOWN"),              // 未知
    CHARGING("CHARGING"),            // 充电中
    DISCHARGING("DISCHARGING"),      // 放电中
    FULL("FULL"),                    // 已充满
    EMPTY("EMPTY"),                  // 空电池
    FAULT("FAULT");                  // 电池故障

    private final String value;

    BatteryState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static BatteryState fromValue(String value) {
        if (value == null) return UNKNOWN;

        String normalized = value.toUpperCase().trim();

        switch (normalized) {
            case "CHARGING":
                return CHARGING;
            case "DISCHARGING":
                return DISCHARGING;
            case "FULL":
                return FULL;
            case "EMPTY":
                return EMPTY;
            case "FAULT":
                return FAULT;
            default:
                return UNKNOWN;
        }
    }
}
