package com.jizhi.modbus.rtu;

import com.jizhi.modbus.message.PduPayload;
import com.jizhi.modbus.utils.ModbusUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class RtuMessage {
    private final byte unitId;
    private final PduPayload payload;
    private final short crc;



    public RtuMessage(byte unitId, PduPayload payload, short crc) {
        this.unitId = unitId;
        this.payload = payload;
        this.crc = crc;
    }

    // 获取完整帧大小
    private int getFrameSize() {
        int size = 3; // unitId(1) + functionCode(1) + CRC(2)
        if (payload.getData() != null) {
            size += payload.getData().length;
        }
        return size;
    }

    // 将RTU消息转换为字节数组
    public byte[] toBytes() {
        int frameSize = getFrameSize();
        ByteBuffer buffer = ByteBuffer.allocate(frameSize);
        buffer.order(ByteOrder.BIG_ENDIAN); // Modbus数据使用大端序

        // 写入从机地址
        buffer.put(unitId);

        // 写入功能码
        buffer.put((byte) (payload.getFunctionCode() & 0xFF));

        // 写入数据
        if (payload.getData() != null && payload.getData().length > 0) {
            buffer.put(payload.getData());
        }

        // 计算CRC (小端序：低字节在前)
        byte[] frameWithoutCrc = Arrays.copyOf(buffer.array(), buffer.position());
        short calculatedCrc = ModbusUtils.calculateCRC(frameWithoutCrc, 0, frameWithoutCrc.length);

        // 写入CRC (小端序)
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(calculatedCrc);

        return buffer.array();
    }

    // 从字节数组解析RTU消息
    public static RtuMessage fromBytes(byte[] bytes) {
        if (bytes.length < 4) throw new IllegalArgumentException("Invalid RTU frame");

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // 解析从机地址
        byte unitId = buffer.get();

        // 解析功能码
        short functionCode = (short) (buffer.get() & 0xFF);

        // 解析数据
        byte[] data = null;
        int dataLength = bytes.length - 4; // 总长度 - (地址1 + 功能码1 + CRC2)
        if (dataLength > 0) {
            data = new byte[dataLength];
            buffer.get(data);
        }

        // 解析CRC (小端序)
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        short receivedCrc = buffer.getShort();

        // 创建Payload
        PduPayload payload = new PduPayload();
        payload.setFunctionCode(functionCode);
        payload.setData(data);
        payload.setDataLength((short) (data != null ? data.length : 0));

        return new RtuMessage(unitId, payload, receivedCrc);
    }

    // Getters
    public byte getUnitId() {
        return unitId;
    }

    public PduPayload getPayload() {
        return payload;
    }

    public short getCrc() {
        return crc;
    }
}
