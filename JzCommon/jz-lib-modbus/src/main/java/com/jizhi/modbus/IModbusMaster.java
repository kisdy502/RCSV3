package com.jizhi.modbus;

import com.jizhi.modbus.request.*;
import com.jizhi.modbus.response.BaseModbusResponse;

/**
 * 写单个线圈，写多个线圈
 * 写单个保持寄存器，写多个保持寄存器
 * 读线圈
 * 读保持寄存器
 * 读离散输入
 * 读输入寄存器
 */
public interface IModbusMaster {

    /**
     * 写单个线圈
     *
     * @param request
     * @return
     * @throws ModbusException
     */
    BaseModbusResponse<?> writeSingleCoil(WriteSingleCoilRequest request) throws ModbusException;

    /**
     * 写多个线圈
     *
     * @param request
     * @return
     * @throws ModbusException
     */
    BaseModbusResponse<?> writeMultipleCoils(WriteMultipleCoilsRequest request) throws ModbusException;

    /**
     * 写单个（保持）寄存器
     *
     * @param request
     * @return
     * @throws ModbusException
     */
    BaseModbusResponse<?> writeSingleRegisters(WriteSingleRegisterRequest request) throws ModbusException;

    /**
     * 写多个（保持）寄存器
     *
     * @param request
     * @return
     * @throws ModbusException
     */
    BaseModbusResponse<?> writeMultipleRegisters(WriteMultipleRegistersRequest request) throws ModbusException;

    /**
     * 读线圈
     *
     * @param request
     * @return
     * @throws ModbusException
     */
    BaseModbusResponse<?> readCoils(ReadCoilsRequest request) throws ModbusException;

    /**
     * 读保持寄存器
     *
     * @param request
     * @return
     * @throws ModbusException
     */
    BaseModbusResponse<?> readHoldingRegisters(ReadHoldingRegistersRequest request) throws ModbusException;

    /**
     * 读输入寄存器
     *
     * @param request
     * @return
     * @throws ModbusException
     */
    BaseModbusResponse<?> readInputRegisters(ReadInputRegistersRequest request) throws ModbusException;

    /**
     * 读离散输入
     *
     * @param request
     * @return
     * @throws ModbusException
     */
    BaseModbusResponse<?> readDiscreteInputRegisters(ReadDiscreteInputsRequest request) throws ModbusException;
}
