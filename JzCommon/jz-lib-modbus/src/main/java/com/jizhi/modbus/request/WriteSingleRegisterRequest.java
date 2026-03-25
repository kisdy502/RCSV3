package com.jizhi.modbus.request;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class WriteSingleRegisterRequest extends BaseModbusRequest {
    private int value;

    public WriteSingleRegisterRequest(int startAddress, int value) throws ModbusException {
        super(startAddress);
        this.value = value;
    }

    @Override
    short getFunctionCode() {
        return FunctionCodeConstants.WriteSingleRegister;
    }

    @Override
    ByteBuf buildBytebuf() {
        ByteBuf byteBuf = Unpooled.buffer();
        // 写入起始地址（大端序）
        byteBuf.writeShort(startAddress);
        byteBuf.writeShort(value);
        return byteBuf;
    }
}
