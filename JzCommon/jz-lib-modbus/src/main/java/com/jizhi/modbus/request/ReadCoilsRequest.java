package com.jizhi.modbus.request;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class ReadCoilsRequest extends BaseModbusRequest {

    private int readLength;

    public ReadCoilsRequest(int startAddress, int readLength) throws ModbusException {
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
        return FunctionCodeConstants.ReadCoils;
    }

    @Override
    ByteBuf buildBytebuf() {
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeShort(startAddress);      // writeOffset
        byteBuf.writeShort(readLength);
        return byteBuf;
    }
}
