package com.jizhi.modbus.netty;


import com.jizhi.modbus.common.FunctionCodeConstants;
import com.jizhi.modbus.message.MBAPHeader;
import com.jizhi.modbus.message.PduPayload;
import com.jizhi.modbus.message.TCPModbusMessage;
import com.jizhi.modbus.utils.TransactionIdGenerator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IdleClientHandler extends ChannelInboundHandlerAdapter {

    private final static String TAG = "IdleClientHandler";

    private int heartbeatCount = 0;

    public IdleClientHandler() {
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn(TAG, "READER_IDLE");
            } else if (event.state() == IdleState.WRITER_IDLE) {
                sendPingMsg(ctx, event);
            } else if (event.state() == IdleState.ALL_IDLE) {
                log.warn(TAG, "ALL_IDLE");
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * 读心跳数据
     */
    protected void sendPingMsg(ChannelHandlerContext context, IdleStateEvent event) {
//        TCPModbusMessage tcpModbusMessage = buildPing2();
//        if (heartbeatCount % 3 == 0) {
//            tcpModbusMessage = buildPing1();
//        } else if (heartbeatCount % 3 == 1) {
//            tcpModbusMessage = buildPing2();
//        } else {
//            tcpModbusMessage = buildPing3();
//        }
//        context.channel().writeAndFlush(tcpModbusMessage).addListener(new ChannelFutureListener() {
//            @Override
//            public void operationComplete(ChannelFuture channelFuture) throws Exception {
//                Log.d(TAG, "send heartbeat:" + ++heartbeatCount + ",超时类型:" + event.state());
//            }
//        });
    }

    private TCPModbusMessage buildPing1() {
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeShort(0x54);   //start
        byteBuf.writeShort(0x01);    //length

        byte[] byteArray = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(byteArray);

        MBAPHeader mbapHeader = new MBAPHeader();
        mbapHeader.setTransactionId((short) TransactionIdGenerator.generateTid());
        mbapHeader.setProtocolId((short) 0);
        mbapHeader.setUnitId((byte) 1);  //slaveId
        mbapHeader.setLength((short) (byteArray.length + 2));  //要把slaveId长度加上

        PduPayload pduPayload = new PduPayload();
        pduPayload.setFunctionCode(FunctionCodeConstants.ReadHoldingRegisters);
        pduPayload.setData(byteArray);

        TCPModbusMessage tcpModbusMessage = new TCPModbusMessage();
        tcpModbusMessage.mbapHeader = mbapHeader;
        tcpModbusMessage.pduPayload = pduPayload;
        return tcpModbusMessage;
    }

    private TCPModbusMessage buildPing2() {
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeShort(0x55);   //start
        byteBuf.writeShort(0x01);    //length

        byte[] byteArray = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(byteArray);

        MBAPHeader mbapHeader = new MBAPHeader();
        mbapHeader.setTransactionId((short) TransactionIdGenerator.generateTid());
        mbapHeader.setProtocolId((short) 0);
        mbapHeader.setUnitId((byte) 1);  //slaveId
        mbapHeader.setLength((short) (byteArray.length + 2));  //要把slaveId长度加上

        PduPayload pduPayload = new PduPayload();
        pduPayload.setFunctionCode(FunctionCodeConstants.ReadHoldingRegisters);
        pduPayload.setData(byteArray);

        TCPModbusMessage tcpModbusMessage = new TCPModbusMessage();
        tcpModbusMessage.mbapHeader = mbapHeader;
        tcpModbusMessage.pduPayload = pduPayload;
        return tcpModbusMessage;
    }

    private TCPModbusMessage buildPing3() {
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeShort(0x56);   //start
        byteBuf.writeShort(0x03);    //length

        byte[] byteArray = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(byteArray);

        MBAPHeader mbapHeader = new MBAPHeader();
        mbapHeader.setTransactionId((short) TransactionIdGenerator.generateTid());
        mbapHeader.setProtocolId((short) 0);
        mbapHeader.setUnitId((byte) 1);  //slaveId
        mbapHeader.setLength((short) (byteArray.length + 2));  //要把slaveId长度加上

        PduPayload pduPayload = new PduPayload();
        pduPayload.setFunctionCode(FunctionCodeConstants.ReadHoldingRegisters);
        pduPayload.setData(byteArray);

        TCPModbusMessage tcpModbusMessage = new TCPModbusMessage();
        tcpModbusMessage.mbapHeader = mbapHeader;
        tcpModbusMessage.pduPayload = pduPayload;
        return tcpModbusMessage;
    }

}
