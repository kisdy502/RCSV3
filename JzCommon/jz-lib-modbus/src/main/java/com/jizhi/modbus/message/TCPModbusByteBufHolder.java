package com.jizhi.modbus.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

public class TCPModbusByteBufHolder extends DefaultByteBufHolder {
    public int functionCode;

    public TCPModbusByteBufHolder(ByteBuf data) {
        super(data) ;
    }

    public int getFunctionCode() {
        return functionCode;
    }

    public void setFunctionCode(int functionCode) {
        this.functionCode = functionCode;
    }
}
