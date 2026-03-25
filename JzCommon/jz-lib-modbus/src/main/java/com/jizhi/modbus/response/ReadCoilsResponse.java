package com.jizhi.modbus.response;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;
import lombok.extern.slf4j.Slf4j;

// 读线圈状态响应
@Slf4j
public class ReadCoilsResponse extends BaseModbusResponse<byte[]> {
    private byte[] rawData;
    private byte[] responseData;

    private int expectedQuantity;

    public ReadCoilsResponse(byte[] pdu,  int unitId, int readCount) throws ModbusException {
        super(FunctionCodeConstants.ReadCoils, unitId);
        rawData = pdu;
        this.expectedQuantity = readCount;
        parseData(pdu);
    }

    public ReadCoilsResponse(byte[] pdu, int transactionId, int unitId, int readCount) throws ModbusException {
        super(FunctionCodeConstants.ReadCoils, transactionId, unitId);
        rawData = pdu;
        this.expectedQuantity = readCount;
        parseData(pdu);
    }

    private void parseData(byte[] pdu) throws ModbusException {
        if (functionCode == (FunctionCodeConstants.ReadCoils | 0x80)) {
            byte errorCode = pdu[0];
            throw new ModbusException("Write failed with error code: 0x" +
                    Integer.toHexString(errorCode & 0xFF));
        }

        // 验证响应长度
        if (pdu.length < 2) {
            throw new ModbusException("Response too short. Expected at least 2 bytes, got: " + pdu.length);
        }

        int byteCount = pdu[0] & 0xFF;
        log.debug("byteCount:{}", byteCount);
        log.debug("byteCount:{},expectedQuantity:{}", byteCount, expectedQuantity);
        responseData = new byte[expectedQuantity];
        for (int byteIndex = 0; byteIndex < byteCount; byteIndex++) {
            for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
                int position = (byteIndex * 8 + bitIndex);
                if (position == expectedQuantity - 1) {
                    break;
                }
                int value = pdu[byteIndex + 1] >> bitIndex & 0x01;
                responseData[position] = (byte) value;
                //log.debug("读线圈{},位状态:{} ", (byteIndex * 8 + bitIndex), value);
            }
        }
    }

    @Override
    public byte[] getData() {
        return responseData;
    }

    @Override
    public byte[] getRawData() {
        return rawData;
    }

}
