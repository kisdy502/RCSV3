package com.jizhi.modbus;

public class ModbusException extends Exception {
    public ModbusException(String message) {
        super(message);
    }

    public ModbusException(String message, Exception e) {
        super(message, e);
    }
}
