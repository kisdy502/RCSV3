package com.jizhi.modbus;

public class ModbusConnectionException extends RuntimeException{
    public ModbusConnectionException(String message) {
        super(message);
    }

    public ModbusConnectionException(String message, Exception e) {
        super(message, e);
    }

}
