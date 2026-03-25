package com.jizhi.modbus.request;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class WriteSingleCoilRequest extends BaseModbusRequest {
    private byte value;

    public WriteSingleCoilRequest(short startAddress, byte value) throws ModbusException {
        super(startAddress);
        this.value = value;
    }

    @Override
    short getFunctionCode() {
        return FunctionCodeConstants.WriteSingleCoil;
    }

    @Override
    ByteBuf buildBytebuf() {
        ByteBuf byteBuf = Unpooled.buffer();
        // 写入起始地址（大端序）
        byteBuf.writeShort(startAddress);
        byteBuf.writeShort(value > 0 ? 0xFF00 : 0x0000);      // writeValue
        return byteBuf;
    }
}
