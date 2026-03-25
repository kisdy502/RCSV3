package com.jizhi.modbus.message;

import com.jizhi.modbus.utils.TransactionIdGenerator;
import lombok.Getter;
import lombok.Setter;

import static com.jizhi.modbus.common.ModbusConstants.PROTOCOL_ID;

public class TCPModbusMessage {
    @Getter
    @Setter
    public MBAPHeader mbapHeader; //消息头
    @Getter
    @Setter
    public PduPayload pduPayload; //消息负载

    public TCPModbusMessage() {
    }

    public TCPModbusMessage(MBAPHeader mbapHeader, PduPayload pduPayload) {
        this.mbapHeader = mbapHeader;
        this.pduPayload = pduPayload;
    }


    public static TCPModbusMessage buildMessage(int slaveId, short functionCode, byte[] data) {
        short transactionId = (short) TransactionIdGenerator.generateTid();
        MBAPHeader mbapHeader = new MBAPHeader();
        mbapHeader.setTransactionId(transactionId);
        mbapHeader.setProtocolId(PROTOCOL_ID);
        mbapHeader.setUnitId((byte) slaveId);               //slaveId
        mbapHeader.setLength((short) (data.length + 2));     //要把slaveId长度加上
        PduPayload pduPayload = new PduPayload();
        pduPayload.setFunctionCode(functionCode);
        pduPayload.setData(data);
        return new TCPModbusMessage(mbapHeader, pduPayload);
    }
}


