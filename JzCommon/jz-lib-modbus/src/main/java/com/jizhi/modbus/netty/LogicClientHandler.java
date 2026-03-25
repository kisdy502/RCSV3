package com.jizhi.modbus.netty;


import com.jizhi.modbus.codec.MBAPHeaderDecoder;
import com.jizhi.modbus.message.MBAPHeader;
import com.jizhi.modbus.message.PduPayload;
import com.jizhi.modbus.message.TCPModbusByteBufHolder;
import com.jizhi.modbus.message.TCPModbusMessage;
import com.jizhi.modbus.utils.ByteUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class LogicClientHandler extends SimpleChannelInboundHandler<TCPModbusByteBufHolder> {
    private final static String TAG = "LogicClientHandler";

    public static final int HEADER_LENGTH = 8;// // transactionId(2) + protocolId(2) + length(2) + unitId(1)+ functionCode(1)

    private ModbusTcpClient nettyClient;


    /**
     * @param nettyClient
     */
    public LogicClientHandler(ModbusTcpClient nettyClient) {
        this.nettyClient = nettyClient;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        log.debug("channelActive:" + ctx.channel().isActive());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        log.info("channelInactive:" + ctx.channel().isActive());
        ModbusTcpClient.OnConnectChangeListener onConnectChangeListener = nettyClient.getOnConnectChangeListener();
        if (onConnectChangeListener != null) {
            onConnectChangeListener.onConnectChange(false);
        }
        if (nettyClient.isAutoReConnect()) {
            nettyClient.doConnect(0);
        }
    }


    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        log.debug("handlerRemoved: " + ctx.channel().isActive());
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        log.info("channelReadComplete");
        ctx.flush();
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("exceptionCaught:{} ", ctx.channel().isActive());
        cause.printStackTrace();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        super.channelWritabilityChanged(ctx);
        log.error("channel 是否可写:" + ctx.channel().isWritable());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TCPModbusByteBufHolder modbusByteBufHolder) throws Exception {
        log.debug("channelRead0:{} ", modbusByteBufHolder != null);
        TCPModbusMessage tcpModbusMessage = readTcpModbusMessage(modbusByteBufHolder);
        if (tcpModbusMessage == null) {
            return;  //忽略不完整的modbus包
        }



//        RecMessageStrategy messageStrategy = RecMessageStrategyManager.getMessageStrategy(tcpModbusMessage.pduPayload.getFunctionCode());
//        if (messageStrategy != null) {
//            messageStrategy.recMessage(ctx.channel(), tcpModbusMessage.mbapHeader, tcpModbusMessage.pduPayload);
//        } else {
//            log.info("not support function code...");
//        }

        CompletableFuture<TCPModbusMessage> future = nettyClient.pendingRequests.remove(tcpModbusMessage.mbapHeader.getTransactionId());
        if (future != null) {
            future.complete(tcpModbusMessage);
        } else {
            if (nettyClient.getOnDispatchMessageListener() != null) {
                nettyClient.getOnDispatchMessageListener().onDispatchModbusMessage(tcpModbusMessage);
            }
        }
    }

    private TCPModbusMessage readTcpModbusMessage(TCPModbusByteBufHolder modbusByteBufHolder) {
        int totalLen = modbusByteBufHolder.content().readableBytes();
        if (totalLen < HEADER_LENGTH) {
            log.warn("not modbus TCP protocol:" + totalLen);
            return null;
        }

        MBAPHeader mbapHeader = MBAPHeaderDecoder.decode(modbusByteBufHolder.content());
        PduPayload pduPayload = new PduPayload();
        short functionCode = modbusByteBufHolder.content().readUnsignedByte();
        pduPayload.setFunctionCode(functionCode);
        int dataLength = 0;
        if (totalLen > HEADER_LENGTH) {
            dataLength = totalLen - HEADER_LENGTH;
        }
        pduPayload.setDataLength((short) dataLength);
        byte[] data = new byte[dataLength];
        modbusByteBufHolder.content().readBytes(data);
        pduPayload.setData(data);
        log.info("header:" + mbapHeader + ",fun code:" + functionCode + ",len:" + dataLength + ",hex data:" + ByteUtil.bytesToHexString(pduPayload.getData()));
        return new TCPModbusMessage(mbapHeader, pduPayload);
    }


}
