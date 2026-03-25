package com.jizhi.modbus.response;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReadInputRegistersResponse extends BaseModbusResponse<int[]> {

    private byte[] rawData;
    private int[] responseData;

    public ReadInputRegistersResponse(byte[] pdu, int unitId) throws ModbusException {
        super(FunctionCodeConstants.ReadCoils, unitId);
        rawData = pdu;
        parseData(pdu);
    }


    public ReadInputRegistersResponse(byte[] pdu, int transactionId, int unitId) throws ModbusException {
        super(FunctionCodeConstants.ReadCoils, transactionId, unitId);
        rawData = pdu;
        parseData(pdu);
    }

    private void parseData(byte[] pdu) throws ModbusException {
        if (functionCode == (FunctionCodeConstants.ReadInputRegisters | 0x80)) {
            byte errorCode = pdu[0];
            throw new ModbusException("Write failed with error code: 0x" +
                    Integer.toHexString(errorCode & 0xFF));
        }
        if (pdu == null || pdu.length < 1) {
            throw new ModbusException("Invalid data");
        }
        int byteCount = pdu[0] & 0xFF; // 获取字节数(无符号)
        log.debug("数据长度为:{}", byteCount);
        if (byteCount % 2 != 0 || pdu.length < byteCount + 1) {
            throw new IllegalArgumentException("Invalid data length");
        }
        responseData = new int[byteCount / 2];
        for (int i = 0; i < responseData.length; i++) {
            int offset = 1 + i * 2;
            responseData[i] = ((pdu[offset] & 0xFF) << 8) | (pdu[offset + 1] & 0xFF);
            log.debug("输入寄存器位{}值={}", i, responseData[i]);
        }
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
