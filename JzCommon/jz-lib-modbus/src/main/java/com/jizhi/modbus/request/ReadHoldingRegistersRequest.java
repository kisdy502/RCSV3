package com.jizhi.modbus.request;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ReadHoldingRegistersRequest extends BaseModbusRequest {
    private int readLength;

    public ReadHoldingRegistersRequest(int startAddress, int readLength) throws ModbusException {
        super(startAddress);
        this.readLength = readLength;
    }

    public int getReadLength() {
        return readLength;
    }

    public void setReadLength(int readLength) {
        this.readLength = readLength;
    }

    @Override
    short getFunctionCode() {
        return FunctionCodeConstants.ReadHoldingRegisters;
    }

    @Override
    ByteBuf buildBytebuf() {
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeShort(startAddress);      // writeOffset
        byteBuf.writeShort(readLength);
        return byteBuf;
    }
}
