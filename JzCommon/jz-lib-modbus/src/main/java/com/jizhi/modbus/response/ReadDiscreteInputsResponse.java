package com.jizhi.modbus.response;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReadDiscreteInputsResponse extends BaseModbusResponse<byte[]> {

    private byte[] rawData;
    private byte[] responseData;
    private int expectedQuantity;

    public ReadDiscreteInputsResponse(byte[] pdu, int unitId, int readCount) throws ModbusException {
        super(FunctionCodeConstants.ReadCoils, unitId);
        rawData = pdu;
        this.expectedQuantity = readCount;
        parseData(pdu);
    }

    public ReadDiscreteInputsResponse(byte[] pdu, int transactionId, int unitId, int readCount) throws ModbusException {
        super(FunctionCodeConstants.ReadCoils, transactionId, unitId);
        rawData = pdu;
        this.expectedQuantity = readCount;
        parseData(pdu);
    }

    private void parseData(byte[] pdu) throws ModbusException {
        if (functionCode == (FunctionCodeConstants.ReadDiscreteInputs | 0x80)) {
            byte errorCode = pdu[0];
            throw new ModbusException("Write failed with error code: 0x" +
                    Integer.toHexString(errorCode & 0xFF));
        }
        byte byteCount = pdu[0];
        log.debug("数据长度为:{}", byteCount);
        if (expectedQuantity != -1) {
            responseData = new byte[expectedQuantity];
        } else {
            responseData = new byte[byteCount * 8];
        }
        for (int byteIndex = 0; byteIndex < byteCount; byteIndex++) {
            for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
                int position = (byteIndex * 8 + bitIndex);
                if (position == expectedQuantity - 1) {
                    break;
                }
                int value = pdu[byteIndex + 1] >> bitIndex & 0x01;
                responseData[position] = (byte) value;
                //log.debug("离散输入位{}值:{} ", (byteIndex * 8 + bitIndex), value);
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
