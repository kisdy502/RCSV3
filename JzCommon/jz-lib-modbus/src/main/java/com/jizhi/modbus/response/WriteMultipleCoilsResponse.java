package com.jizhi.modbus.response;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;

public class WriteMultipleCoilsResponse extends BaseModbusResponse<int[]> {

    private byte[] rawData;
    private int[] responseData;

    public WriteMultipleCoilsResponse(byte[] pdu, int unitId) throws ModbusException {
        super(FunctionCodeConstants.ReadCoils, unitId);
        rawData = pdu;
        parseData(pdu);
    }

    public WriteMultipleCoilsResponse(byte[] pdu, int transactionId, int unitId) throws ModbusException {
        super(FunctionCodeConstants.ReadCoils, transactionId, unitId);
        rawData = pdu;
        parseData(pdu);
    }

    private void parseData(byte[] pdu) throws ModbusException {
        if (functionCode == (FunctionCodeConstants.WriteMultipleCoils | 0x80)) {
            byte errorCode = pdu[0];
            throw new ModbusException("Write failed with error code: 0x" +
                    Integer.toHexString(errorCode & 0xFF));
        }

        // 验证响应长度 (应返回4字节：起始地址2B + 寄存器数量2B)
        if (pdu.length != 4) {
            throw new ModbusException("Invalid response length: " + pdu.length);
        }
        // 解析响应数据
        int respAddress = ((pdu[0] & 0xFF) << 8) | (pdu[1] & 0xFF);
        int respQuantity = ((pdu[2] & 0xFF) << 8) | (pdu[3] & 0xFF);
        responseData = new int[2];
        responseData[0] = respAddress;
        responseData[1] = respQuantity;
    }

    @Override
    public int[] getData() {
        return responseData;
    }

    @Override
    public byte[] getRawData() {
        return rawData;
    }
}
