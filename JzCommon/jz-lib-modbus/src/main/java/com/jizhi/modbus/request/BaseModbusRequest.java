package com.jizhi.modbus.request;

import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.message.MBAPHeader;
import com.jizhi.modbus.message.PduPayload;
import com.jizhi.modbus.message.TCPModbusMessage;
import com.jizhi.modbus.rtu.RtuMessage;
import com.jizhi.modbus.utils.ModbusUtils;
import com.jizhi.modbus.utils.TransactionIdGenerator;
import io.netty.buffer.ByteBuf;

/**
 * modbus请求基类
 */
public abstract class BaseModbusRequest {

    abstract short getFunctionCode();

    abstract ByteBuf buildBytebuf() throws ModbusException;

    private void checkAddress() throws ModbusException {
        if (startAddress < 0 || startAddress > 65535) {
            throw new ModbusException("Invalid address: " + startAddress);
        }
    }


    public BaseModbusRequest(int startAddress) throws ModbusException {
        this.startAddress = startAddress;
        checkAddress();
    }

    protected int startAddress;

    private static final int PROTOCOL_ID = 0; // 根据实际协议ID修改
    protected byte slaveId = 1;

    public byte getSlaveId() {
        return slaveId;
    }

    public void setSlaveId(byte slaveId) {
        this.slaveId = slaveId;
    }

    public int getStartAddress() {
        return startAddress;
    }

    public void setStartAddress(int startAddress) {
        this.startAddress = startAddress;
    }

    public TCPModbusMessage buildMessage() throws ModbusException {
        ByteBuf byteBuf = buildBytebuf();
        short transactionId = (short) TransactionIdGenerator.generateTid();
        byte[] byteArray = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(byteArray);

        MBAPHeader mbapHeader = new MBAPHeader();
        mbapHeader.setTransactionId(transactionId);
        mbapHeader.setProtocolId((short) PROTOCOL_ID);
        mbapHeader.setUnitId(slaveId);
        mbapHeader.setLength((short) (byteArray.length + 2));

        PduPayload pduPayload = new PduPayload();
        pduPayload.setFunctionCode(getFunctionCode());
        pduPayload.setData(byteArray);

        TCPModbusMessage tcpModbusMessage = new TCPModbusMessage();
        tcpModbusMessage.setMbapHeader(mbapHeader);
        tcpModbusMessage.setPduPayload(pduPayload);
        return tcpModbusMessage;
    }

    public RtuMessage buildRtuMessage() throws ModbusException {
        // 构建PDU负载
        ByteBuf byteBuf = buildBytebuf();
        byte[] pduData = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(pduData);

        PduPayload pduPayload = new PduPayload();
        pduPayload.setFunctionCode(getFunctionCode());
        pduPayload.setData(pduData);

        //TODO 将上面的原始数据写入带byte中
        byte slaveAddress = (byte) (slaveId & 0xFF);
        byte[] messageBytes = new byte[1 + pduData.length];
        messageBytes[0] = slaveAddress;
        System.arraycopy(pduData, 0, messageBytes, 1, pduData.length);

        // 创建RTU消息
        return new RtuMessage(slaveId, pduPayload, ModbusUtils.calculateCRC(messageBytes, 0, messageBytes.length)); // CRC将在toBytes()中计算
    }
}
