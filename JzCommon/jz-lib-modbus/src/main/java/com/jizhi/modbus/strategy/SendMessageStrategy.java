package com.jizhi.modbus.strategy;



import com.jizhi.modbus.message.PduPayload;
import io.netty.buffer.ByteBuf;

public class SendMessageStrategy {

    public static void sendMessage(ByteBuf byteBuf, PduPayload pduPayload) {
        byteBuf.writeByte(pduPayload.getFunctionCode());
        byteBuf.writeBytes(pduPayload.getData());
    }
}


