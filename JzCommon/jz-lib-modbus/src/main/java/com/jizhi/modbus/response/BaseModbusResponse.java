package com.jizhi.modbus.response;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;
import com.jizhi.modbus.request.BaseModbusRequest;
import com.jizhi.modbus.request.ReadCoilsRequest;
import com.jizhi.modbus.request.ReadDiscreteInputsRequest;
import lombok.Getter;

/**
 * modbus响应基类
 *
 * @param <T>
 */
public abstract class BaseModbusResponse<T> {
    private boolean result;
    @Getter
    protected short functionCode;
    @Getter
    protected int transactionId;
    @Getter
    protected int unitId;


    public boolean isSuccess() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public BaseModbusResponse(short functionCode, int unitId) {
        this.functionCode = functionCode;
        this.unitId = unitId;
        this.result = true; // 默认成功
    }

    public BaseModbusResponse(short functionCode, int transactionId, int unitId) {
        this.functionCode = functionCode;
        this.transactionId = transactionId;
        this.unitId = unitId;
        this.result = true; // 默认成功
    }

    // 抽象方法 - 获取特定类型的数据
    public abstract T getData();

    // 抽象方法 - 获取原始字节数据
    public abstract byte[] getRawData();


    // 工厂方法 - 根据功能码创建响应对象
    public static BaseModbusResponse<?> createResponse(short functionCode, byte[] pdu, int transactionId, int unitId,
                                                       BaseModbusRequest request) throws ModbusException {
        switch (functionCode) {
            case FunctionCodeConstants.ReadCoils:
                return new ReadCoilsResponse(pdu, transactionId, unitId, ((ReadCoilsRequest) request).getReadLength());
            case FunctionCodeConstants.ReadDiscreteInputs:
                return new ReadDiscreteInputsResponse(pdu, transactionId, unitId,
                        request != null ? ((ReadDiscreteInputsRequest) request).getReadLength() : -1);
            case FunctionCodeConstants.ReadHoldingRegisters:
                return new ReadHoldingRegistersResponse(pdu, transactionId, unitId);
            case FunctionCodeConstants.ReadInputRegisters:
                return new ReadInputRegistersResponse(pdu, transactionId, unitId);
            case FunctionCodeConstants.WriteSingleCoil:
                return new WriteSingleCoilResponse(pdu, transactionId, unitId);
            case FunctionCodeConstants.WriteSingleRegister:
                return new WriteSingleRegisterResponse(pdu, transactionId, unitId);
            case FunctionCodeConstants.WriteMultipleCoils:
                return new WriteMultipleCoilsResponse(pdu, transactionId, unitId);
            case FunctionCodeConstants.WriteMultipleRegisters:
                return new WriteMultipleRegistersResponse(pdu, transactionId, unitId);
            case FunctionCodeConstants.ERROR_CODE:
            default:
                return new ErrorResponse(functionCode, pdu, transactionId, unitId);
        }
    }

    public static BaseModbusResponse<?> createResponseFromRtu(short functionCode, int unitId, byte[] pdu,
                                                              BaseModbusRequest request) throws ModbusException {
        switch (functionCode) {
            case FunctionCodeConstants.ReadCoils:
                return new ReadCoilsResponse(pdu, unitId,((ReadCoilsRequest) request).getReadLength());
            case FunctionCodeConstants.ReadDiscreteInputs:
                return new ReadDiscreteInputsResponse(pdu,unitId,
                        request != null ? ((ReadDiscreteInputsRequest) request).getReadLength() : -1);
            case FunctionCodeConstants.ReadHoldingRegisters:
                return new ReadHoldingRegistersResponse(pdu,unitId);
            case FunctionCodeConstants.ReadInputRegisters:
                return new ReadInputRegistersResponse(pdu,unitId);
            case FunctionCodeConstants.WriteSingleCoil:
                return new WriteSingleCoilResponse(pdu,unitId);
            case FunctionCodeConstants.WriteSingleRegister:
                return new WriteSingleRegisterResponse(pdu,unitId);
            case FunctionCodeConstants.WriteMultipleCoils:
                return new WriteMultipleCoilsResponse(pdu,unitId);
            case FunctionCodeConstants.WriteMultipleRegisters:
                return new WriteMultipleRegistersResponse(pdu,unitId);
            case FunctionCodeConstants.ERROR_CODE:
            default:
                return new ErrorResponse(functionCode, pdu,unitId);
        }
    }

}
