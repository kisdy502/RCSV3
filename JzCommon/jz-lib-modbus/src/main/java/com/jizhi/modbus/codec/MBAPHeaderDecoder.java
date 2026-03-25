package com.jizhi.modbus.codec;


import com.jizhi.modbus.message.MBAPHeader;
import io.netty.buffer.ByteBuf;

public class MBAPHeaderDecoder {

    public static MBAPHeader decode(ByteBuf byteBuf) {
        MBAPHeader mbapHeader = new MBAPHeader();
        short transactionId = byteBuf.readShort();
        mbapHeader.setTransactionId(transactionId);
        short protocolId = (short) byteBuf.readShort();
        mbapHeader.setProtocolId(protocolId);
        short length = (short) byteBuf.readUnsignedShort();
        mbapHeader.setLength(length);
        byte unitId = (byte) byteBuf.readUnsignedByte();
        mbapHeader.setUnitId(unitId);
        return mbapHeader;
    }
}
