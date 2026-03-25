package com.jizhi.modbus.request;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class WriteMultipleRegistersRequest extends BaseModbusRequest {

    private int[] values;

    public WriteMultipleRegistersRequest(int startAddress, int[] values) throws ModbusException {
        super(startAddress);
        this.values = values;
        if (values == null || values.length == 0) {
            throw new ModbusException("No values provided");
        }
        if (values.length > 123) { // Modbus 协议限制
            throw new ModbusException("Too many registers (max 123): " + values.length);
        }
    }

    @Override
    short getFunctionCode() {
        return FunctionCodeConstants.WriteMultipleRegisters;
    }

    @Override
    ByteBuf buildBytebuf() throws ModbusException {
        ByteBuf byteBuf = Unpooled.buffer();
        // 写入起始地址（大端序）
        byteBuf.writeShort(startAddress);
        // 写入寄存器数量
        byteBuf.writeShort(values.length);
        // 写入字节数（寄存器数量*2）
        byteBuf.writeByte(values.length * 2);

        // 写入每个寄存器的值（大端序）
        for (int value : values) {
            if (value < 0 || value > 65535) {
                throw new ModbusException("Value out of range (0-65535): " + value);
            }
            byteBuf.writeShort(value);
        }
        return byteBuf;
    }
}
