package com.jizhi.modbus.request;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.common.FunctionCodeConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class WriteMultipleCoilsRequest extends BaseModbusRequest {
    private byte[] values;

    public WriteMultipleCoilsRequest(int startAddress, byte[] values) throws ModbusException {
        super(startAddress);
        this.values = values;
        if (values.length < 1 || values.length > 1968) {
            throw new ModbusException("Invalid quantity. Must be between 1-1968, got: " + values.length);
        }
    }

    @Override
    short getFunctionCode() {
        return FunctionCodeConstants.WriteMultipleCoils;
    }

    @Override
    ByteBuf buildBytebuf() {
        ByteBuf byteBuf = Unpooled.buffer();
        // 写入起始地址（大端序）
        byteBuf.writeShort(startAddress);
        // 写入线圈数量
        byteBuf.writeShort(values.length);
        // 计算所需字节数（每8个线圈占1字节）
        int byteCount = (values.length + 7) / 8;
        byteBuf.writeByte(byteCount);

        // 将布尔数组打包为字节
        byte packedCoils = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != 0) {
                packedCoils |= (byte) (1 << (i % 8));
            }
            if ((i + 1) % 8 == 0 || i == values.length - 1) {
                byteBuf.writeByte(packedCoils);
                packedCoils = 0;
            }
        }
        return byteBuf;
    }
}
