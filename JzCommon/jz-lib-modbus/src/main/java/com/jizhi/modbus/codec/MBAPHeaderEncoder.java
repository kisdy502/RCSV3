package com.jizhi.modbus.codec;


import com.jizhi.modbus.message.MBAPHeader;
import io.netty.buffer.ByteBuf;

public class MBAPHeaderEncoder {
    public static void encode(ByteBuf byteBuf, MBAPHeader mbapHeader) {
        byteBuf.writeShort(mbapHeader.getTransactionId());
        byteBuf.writeShort(mbapHeader.getProtocolId());
        byteBuf.writeShort(mbapHeader.getLength());
        byteBuf.writeByte(mbapHeader.getUnitId());
    }
}
