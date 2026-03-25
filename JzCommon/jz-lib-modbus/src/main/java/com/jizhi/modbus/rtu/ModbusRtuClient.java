package com.jizhi.modbus.rtu;

import com.fazecast.jSerialComm.SerialPort;
import com.jizhi.modbus.IModbusMaster;
import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.request.*;
import com.jizhi.modbus.response.*;
import com.jizhi.modbus.utils.ModbusUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
public class ModbusRtuClient implements IModbusMaster {

    private SerialPort serialPort;
    private byte[] buffer = new byte[256];
    private int bufferIndex = 0;
    private static final int MAX_FRAME_SIZE = 256;
    private static final int MIN_FRAME_SIZE = 4; // 地址1 + 功能码1 + CRC2

    final Map<Integer, CompletableFuture<RtuMessage>> pendingRequests = new ConcurrentHashMap<>();

    public void init(String portName, int baudRate, int dataBits, int stopBits, int parity, int timeout) {
        // 获取可用串口列表
        SerialPort[] ports = SerialPort.getCommPorts();
        System.out.println("Available ports:");
        for (SerialPort port : ports) {
            System.out.println(port.getSystemPortName());
        }

        // 查找并配置指定串口
        for (SerialPort port : ports) {
            if (port.getSystemPortName().equals(portName)) {
                serialPort = port;
                serialPort.setBaudRate(baudRate);
                serialPort.setNumDataBits(dataBits);
                serialPort.setNumStopBits(stopBits);
                serialPort.setParity(parity);
                serialPort.setComPortTimeouts(
                        SerialPort.TIMEOUT_READ_BLOCKING,
                        timeout,
                        0
                );

                if (serialPort.openPort()) {
                    System.out.println("Port opened: " + serialPort.getSystemPortName());
                    startListening();
                    return;
                }
            }
        }
        throw new RuntimeException("Failed to open specified port: " + portName);
    }

    public boolean isConnected() {
        return serialPort != null && serialPort.isOpen();
    }

    @Async
    public void startListening() {
        while (serialPort != null && serialPort.isOpen()) {
            try {
                byte[] readBuffer = new byte[serialPort.bytesAvailable()];
                int numRead = serialPort.readBytes(readBuffer, readBuffer.length);

                if (numRead > 0) {
                    processReceivedData(readBuffer, numRead);
                }

                Thread.sleep(10);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized void processReceivedData(byte[] data, int length) {
        // 将新数据添加到缓冲区
        if (bufferIndex + length > buffer.length) {
            // 缓冲区扩容策略：双倍增长
            buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, bufferIndex + length));
        }
        System.arraycopy(data, 0, buffer, bufferIndex, length);
        bufferIndex += length;

        // 尝试从缓冲区提取完整帧
        while (bufferIndex >= MIN_FRAME_SIZE) {
            // 尝试检测完整帧 (基于CRC校验)
            boolean foundFrame = false;

            for (int end = MIN_FRAME_SIZE; end <= bufferIndex; end++) {
                byte[] candidate = Arrays.copyOfRange(buffer, 0, end);

                if (isValidFrame(candidate)) {
                    // 找到有效帧
                    RtuMessage rtuMessage = parseFrame(candidate);
                    try {
                        handleMessage(rtuMessage);
                    } catch (ModbusException e) {
                        throw new RuntimeException(e);
                    }

                    // 从缓冲区移除已处理数据
                    int frameLength = candidate.length;
                    System.arraycopy(
                            buffer, frameLength,
                            buffer, 0,
                            bufferIndex - frameLength
                    );
                    bufferIndex -= frameLength;
                    foundFrame = true;
                    break;
                }
            }

            // 如果没有找到有效帧，等待更多数据
            if (!foundFrame) break;
        }
    }

    // 使用ByteBuffer解析帧
    private RtuMessage parseFrame(byte[] frame) {
        return RtuMessage.fromBytes(frame);
    }

    // 验证帧CRC
    private boolean isValidFrame(byte[] frame) {
        if (frame.length < MIN_FRAME_SIZE) return false;

        // 计算CRC校验
        int dataLength = frame.length - 2;
        short calculatedCrc = ModbusUtils.calculateCRC(frame, 0, dataLength);

        // 使用ByteBuffer解析帧中的CRC (小端序)
        ByteBuffer buffer = ByteBuffer.wrap(frame, dataLength, 2)
                .order(ByteOrder.LITTLE_ENDIAN);
        short receivedCrc = buffer.getShort();

        return calculatedCrc == receivedCrc;
    }

    public RtuMessage sendRtuMessage(RtuMessage message) {
        if (serialPort != null && serialPort.isOpen()) {
            byte[] frame = message.toBytes();
            serialPort.writeBytes(frame, frame.length);
            CompletableFuture<RtuMessage> future = new CompletableFuture<>();
            pendingRequests.put(1, future);
            try {
                return (RtuMessage) future.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private void handleMessage(RtuMessage message) throws ModbusException {
        // 处理接收到的消息 (业务逻辑)
        System.out.println("Received RTU message:");
        System.out.println("Unit ID: " + message.getUnitId());
        System.out.println("Function Code: 0x" +
                Integer.toHexString(message.getPayload().getFunctionCode()));

        if (message.getPayload().getData() != null) {
            System.out.println("Data: " +
                    bytesToHex(message.getPayload().getData()));
        }

        CompletableFuture<RtuMessage> future = pendingRequests.remove(1);
        if (future != null) {
            future.complete(message);
        } else {
            if (onModbusMessageListener != null) {
                onModbusMessageListener.onReceiveModbusMessage(message);
            }
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    private OnModbusMessageListener onModbusMessageListener;

    public void setOnModbusMessageListener(OnModbusMessageListener onModbusMessageListener) {
        this.onModbusMessageListener = onModbusMessageListener;
    }

    @Override
    public BaseModbusResponse<?> writeSingleCoil(WriteSingleCoilRequest request) throws ModbusException {
        RtuMessage rtuMessage = request.buildRtuMessage();
        RtuMessage response = sendRtuMessage(rtuMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponseFromRtu(response.getPayload().getFunctionCode(), response.getUnitId(),
                        response.getPayload().getData(), request);
        return (WriteMultipleCoilsResponse) modbusResponse;
    }

    @Override
    public BaseModbusResponse<?> writeMultipleCoils(WriteMultipleCoilsRequest request) throws ModbusException {
        RtuMessage rtuMessage = request.buildRtuMessage();
        RtuMessage response = sendRtuMessage(rtuMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponseFromRtu(response.getPayload().getFunctionCode(), response.getUnitId(),
                        response.getPayload().getData(), request);
        return (WriteSingleCoilResponse) modbusResponse;
    }

    @Override
    public BaseModbusResponse<?> writeSingleRegisters(WriteSingleRegisterRequest request) throws ModbusException {
        RtuMessage rtuMessage = request.buildRtuMessage();
        RtuMessage response = sendRtuMessage(rtuMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponseFromRtu(response.getPayload().getFunctionCode(), response.getUnitId(),
                        response.getPayload().getData(), request);
        return (WriteSingleRegisterResponse) modbusResponse;
    }

    @Override
    public WriteMultipleRegistersResponse writeMultipleRegisters(WriteMultipleRegistersRequest request) throws ModbusException {
        RtuMessage rtuMessage = request.buildRtuMessage();
        RtuMessage response = sendRtuMessage(rtuMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponseFromRtu(response.getPayload().getFunctionCode(), response.getUnitId(),
                        response.getPayload().getData(), request);
        return (WriteMultipleRegistersResponse) modbusResponse;
    }

    @Override
    public BaseModbusResponse<?> readCoils(ReadCoilsRequest request) throws ModbusException {
        RtuMessage rtuMessage = request.buildRtuMessage();
        RtuMessage response = sendRtuMessage(rtuMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponseFromRtu(response.getPayload().getFunctionCode(), response.getUnitId(),
                        response.getPayload().getData(), request);
        return (ReadCoilsResponse) modbusResponse;
    }

    @Override
    public BaseModbusResponse<?> readHoldingRegisters(ReadHoldingRegistersRequest request) throws ModbusException {
        RtuMessage rtuMessage = request.buildRtuMessage();
        RtuMessage response = sendRtuMessage(rtuMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponseFromRtu(response.getPayload().getFunctionCode(), response.getUnitId(),
                        response.getPayload().getData(), request);
        return (ReadHoldingRegistersResponse) modbusResponse;
    }

    @Override
    public BaseModbusResponse<?> readInputRegisters(ReadInputRegistersRequest request) throws ModbusException {
        RtuMessage rtuMessage = request.buildRtuMessage();
        RtuMessage response = sendRtuMessage(rtuMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponseFromRtu(response.getPayload().getFunctionCode(), response.getUnitId(),
                        response.getPayload().getData(), request);
        return (ReadInputRegistersResponse) modbusResponse;
    }

    @Override
    public BaseModbusResponse<?> readDiscreteInputRegisters(ReadDiscreteInputsRequest request) throws ModbusException {
        RtuMessage rtuMessage = request.buildRtuMessage();
        RtuMessage response = sendRtuMessage(rtuMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponseFromRtu(response.getPayload().getFunctionCode(), response.getUnitId(),
                        response.getPayload().getData(), request);
        return (ReadDiscreteInputsResponse) modbusResponse;
    }

    public interface OnModbusMessageListener {
        void onReceiveModbusMessage(RtuMessage rtuMessage) throws ModbusException;
    }
}