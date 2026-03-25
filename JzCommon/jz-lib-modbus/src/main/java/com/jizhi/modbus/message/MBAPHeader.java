package com.jizhi.modbus.message;

public class MBAPHeader {
    //事务处理标识符 递增
    private short transactionId;
    //协议标识符 0x00 标识modbus协议
    private short protocolId;
    //长度,unitId + pdu长度
    private short length;
    //单元标识符,从机地址
    private byte unitId;

    public MBAPHeader(short transactionId, short protocolId, short length, byte unitId) {
        this.transactionId = transactionId;
    }

    public MBAPHeader() {

    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(short transactionId) {
        this.transactionId = transactionId;
    }

    public int getProtocolId() {
        return protocolId;
    }

    public void setProtocolId(short protocolId) {
        this.protocolId = protocolId;
    }

    public int getLength() {
        return length;
    }

    public void setLength(short length) {
        this.length = length;
    }

    public short getUnitId() {
        return unitId;
    }

    public void setUnitId(byte unitId) {
        this.unitId = unitId;
    }

    public String toString() {
        return "transactionId:" + transactionId
                + ",protocolId:" + protocolId
                + ",length:" + length
                + ",unitId:" + unitId;

    }
}


