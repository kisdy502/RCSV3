package com.jizhi.modbus.response;

import com.jizhi.modbus.common.FunctionCodeConstants;

// 错误响应类
public class ErrorResponse extends BaseModbusResponse<Void> {
    private short exceptionCode;

    public ErrorResponse(short functionCode, byte[] pdu, int unitId) {
        super((short) (functionCode | FunctionCodeConstants.ERROR_CODE), unitId);
        this.setResult(false);
        this.exceptionCode = pdu[1]; // 异常码在PDU的第2个字节
    }

    public ErrorResponse(short functionCode, byte[] pdu, int transactionId, int unitId) {
        super((short) (functionCode | FunctionCodeConstants.ERROR_CODE), transactionId, unitId);
        this.setResult(false);
        this.exceptionCode = pdu[1]; // 异常码在PDU的第2个字节
    }

    public short getExceptionCode() {
        return exceptionCode;
    }

    @Override
    public Void getData() {
        return null; // 错误响应没有数据
    }

    @Override
    public byte[] getRawData() {
        return new byte[]{(byte) getFunctionCode(), (byte) exceptionCode};
    }
}
