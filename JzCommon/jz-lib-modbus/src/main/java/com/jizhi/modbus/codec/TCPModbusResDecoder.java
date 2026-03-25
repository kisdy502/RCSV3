package com.jizhi.modbus.codec;


import com.jizhi.modbus.message.TCPModbusByteBufHolder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class TCPModbusResDecoder extends ByteToMessageDecoder {
    private static final int MBAP_HEADER_SIZE = 6; // 事务ID4B + 长度2B
    private static final int LENGTH_FIELD_OFFSET = 4;

    //解决收到modbus 消息粘包的问题
    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
        // 1. 检查基础长度
        if (byteBuf.readableBytes() < MBAP_HEADER_SIZE) {
            return;
        }

        // 2. 标记读指针
        byteBuf.markReaderIndex();

        // 3. 读取长度字段（大端序）
        int frameLength = byteBuf.getUnsignedShort(byteBuf.readerIndex() + LENGTH_FIELD_OFFSET) + 6;

        // 4. 检查数据完整性
        if (byteBuf.readableBytes() < frameLength) {
            byteBuf.resetReaderIndex(); // 等待更多数据
            return;
        }

        // 5. 提取完整帧
        ByteBuf frame = byteBuf.readRetainedSlice(frameLength);
        log.debug("Received完整帧: {}", ByteBufUtil.hexDump(frame));

        // 6. 构造业务对象
        list.add(new TCPModbusByteBufHolder(frame));

//这段代码存在粘包问题，收到的byteBuf可能包含两个modbus tcp消息包
//        try {
//            byteBuf.resetReaderIndex();
//            log.debug("real receive:{}", ByteBufUtil.hexDump(byteBuf));
//            ByteBuf byteBuf1 = Unpooled.copiedBuffer(byteBuf);
//            TCPModbusByteBufHolder tcpModbusByteBufHolder = new TCPModbusByteBufHolder(byteBuf1);
//            list.add(tcpModbusByteBufHolder);
//            byteBuf.clear();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
}


