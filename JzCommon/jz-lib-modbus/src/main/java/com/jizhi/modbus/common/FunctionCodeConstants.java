package com.jizhi.modbus.common;

/**
 * modbus 功能码
 */
public class FunctionCodeConstants {
    //读线圈
    public static final short ReadCoils = 1;//0x01
    //读离散量输入
    public static final short ReadDiscreteInputs = 2;//0x02
    //读保持寄存器
    public static final short ReadHoldingRegisters = 3;//0x03
    //读输入寄存器
    public static final short ReadInputRegisters = 4;//0x04
    //写单个线圈
    public static final short WriteSingleCoil = 5;//0x05
    //写单个保持寄存器
    public static final short WriteSingleRegister = 6;//0x06
    //写多个线圈
    public static final short WriteMultipleCoils = 15;//0x0F
    //写多个寄存器
    public static final short WriteMultipleRegisters = 16;//0x10
    public static final short ERROR_CODE = 0x80;
}
