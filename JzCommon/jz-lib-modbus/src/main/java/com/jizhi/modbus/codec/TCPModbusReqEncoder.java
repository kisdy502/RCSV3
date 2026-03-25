package com.jizhi.modbus.codec;


import com.jizhi.modbus.message.TCPModbusMessage;
import com.jizhi.modbus.strategy.SendMessageStrategy;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TCPModbusReqEncoder extends MessageToByteEncoder<TCPModbusMessage> {

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, TCPModbusMessage tcpModbusMessage, ByteBuf byteBuf) throws Exception {
        log.debug("-----------TCPModbusReqEncoder encode begin------------");
        //header
        MBAPHeaderEncoder.encode(byteBuf, tcpModbusMessage.mbapHeader);
        //functionCode
        int functionCode = tcpModbusMessage.pduPayload.getFunctionCode();
        log.debug("header:{},functionCode:{}", tcpModbusMessage.mbapHeader.toString(), functionCode);

        SendMessageStrategy.sendMessage(byteBuf, tcpModbusMessage.pduPayload);
        /****打印发送的原始数据内容(调试用) START******/
        log.debug("real send:{}", ByteBufUtil.hexDump(byteBuf));
        /****打印发送的原始数据内容(调试用) END******/
        log.debug("-----------TCPModbusReqEncoder encode end------------");
    }
}


