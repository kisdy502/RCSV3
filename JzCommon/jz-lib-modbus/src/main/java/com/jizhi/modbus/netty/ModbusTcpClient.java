package com.jizhi.modbus.netty;

import com.jizhi.modbus.IModbusMaster;
import com.jizhi.modbus.ModbusException;
import com.jizhi.modbus.codec.TCPModbusReqEncoder;
import com.jizhi.modbus.codec.TCPModbusResDecoder;
import com.jizhi.modbus.message.TCPModbusMessage;
import com.jizhi.modbus.request.*;
import com.jizhi.modbus.response.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Modbus Netty Tcp客户端
 */
@Slf4j
public class ModbusTcpClient implements IModbusMaster {

    public ModbusTcpClient() {
    }

    public ModbusTcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Getter
    @Setter
    private String host = "192.168.1.56";
    @Getter
    @Setter
    private int port = 502;

    @Getter
    @Setter
    private boolean autoReConnect = false;

    private final static int READER_IDLE_TIME_SECONDS = 90;//读操作空闲?秒
    private final static int WRITER_IDLE_TIME_SECONDS = 30;//写操作空闲?秒

    private Bootstrap bootstrap;
    private Channel channel;
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    final Map<Integer, CompletableFuture<TCPModbusMessage>> pendingRequests = new ConcurrentHashMap<>();

    public boolean isConnect() {
        return channel != null && channel.isActive();
    }


    private void sendMessage(ByteBuf byteBuf) {
        if (channel == null || !channel.isActive()) {
            log.warn("netty connect bad channel is null or not active!");
            return;
        }
        try {
            channel.writeAndFlush(byteBuf).addListener((ChannelFutureListener) channelFuture -> {
                if (channelFuture.isSuccess()) {
                    log.debug("sendMessage to netty server success!");
                } else {
                    log.error("sendMessage to netty server failed!");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TCPModbusMessage sendTcpModbusMessage(TCPModbusMessage tcpModbusMessage) throws ModbusException {
        if (channel == null || !channel.isActive()) {
            log.warn("netty connect bad channel is null or not active!");
            throw new ModbusException("modbus tcp与" + host + "连接未建立!");
        }
        int tid = tcpModbusMessage.mbapHeader.getTransactionId();
        CompletableFuture<TCPModbusMessage> future = new CompletableFuture<>();
        pendingRequests.put(tid, future);

        channel.writeAndFlush(tcpModbusMessage);
        try {
            return (TCPModbusMessage) future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(tid);
            throw new ModbusException("Request timed out");
        } catch (Exception e) {
            throw new ModbusException("Request failed", e);
        }
    }

    public void releaseConnect() {
        autoReConnect = false;
        disconnect();
    }

    public void disconnect() {
        if (channel != null) {
            removeHandler(IdleClientHandler.class.getSimpleName());
            removeHandler(LogicClientHandler.class.getSimpleName());
            removeHandler(IdleStateHandler.class.getSimpleName());
            channel.close();
            channel.eventLoop().shutdownGracefully();
            channel = null;
            log.debug("close Netty Channel");
        }
        if (bootstrap != null) {
            bootstrap = null;
        }

    }

//    public void connectAfter(int delay) {
//        scheduledExecutorService.scheduleWithFixedDelay(() -> {
//            disconnect();
//            realConnect();
//        },0, delay, TimeUnit.MILLISECONDS);
//    }

    private void removeHandler(String handlerName) {
        if (channel.pipeline().get(handlerName) != null) {
            channel.pipeline().remove(handlerName);
        }
    }


    /**
     * netty client 连接，连接失败10秒后重试连接
     */
    public void doConnect(int delay) {
        scheduledExecutorService.scheduleWithFixedDelay(() -> {
            disconnect();
            realConnect();
        },0, delay, TimeUnit.MILLISECONDS);
    }

    private void realConnect() {
        EventLoopGroup loop = new NioEventLoopGroup();
        try {
            bootstrap = new Bootstrap();
            bootstrap.group(loop);
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.option(ChannelOption.SO_KEEPALIVE, false);
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 8000);
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast(IdleStateHandler.class.getSimpleName(),
                            new IdleStateHandler(READER_IDLE_TIME_SECONDS, WRITER_IDLE_TIME_SECONDS, 0,
                                    TimeUnit.SECONDS));
                    pipeline.addLast(IdleClientHandler.class.getSimpleName(), new IdleClientHandler());
                    pipeline.addLast("decoder", new TCPModbusResDecoder());
                    pipeline.addLast("encoder", new TCPModbusReqEncoder());
                    pipeline.addLast(LogicClientHandler.class.getSimpleName(),
                            new LogicClientHandler(ModbusTcpClient.this));
                }
            });
            bootstrap.remoteAddress(host, port);
            log.info("real connect to remote:{}", host);
            ChannelFuture future = bootstrap.connect().sync();
            if (future.isSuccess()) {
                channel = future.channel();
                log.debug("连接服务器成功:{} ", channel.id());
                if (onConnectChangeListener != null) {
                    onConnectChangeListener.onConnectChange(true);
                }
                channel.closeFuture().sync();
                log.debug("channel close success!");
            } else {
                log.error("连接服务器{}:{}失败: ", host, port);
                if (autoReConnect) {
                    doConnect(5000);
                }
            }
        } catch (InterruptedException e) {
            log.error("连接被中断！", e);
        } catch (Exception e) {
            log.error("连接服务器发生异常:", e);
            log.error("异常类型:{}", e.getClass().getName());
            if (autoReConnect) {
                doConnect(5000);
            }
        } finally {
            loop.shutdownGracefully();
        }
    }


    @Override
    public WriteSingleCoilResponse writeSingleCoil(WriteSingleCoilRequest request) throws ModbusException {
        TCPModbusMessage modbusMessage = request.buildMessage();
        TCPModbusMessage response = sendTcpModbusMessage(modbusMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponse(response.getPduPayload().getFunctionCode(),
                        response.getPduPayload().getData(),
                        response.getMbapHeader().getTransactionId(), response.getMbapHeader().getUnitId(), request);
        return (WriteSingleCoilResponse) modbusResponse;
    }

    @Override
    public WriteMultipleCoilsResponse writeMultipleCoils(WriteMultipleCoilsRequest request) throws ModbusException {
        TCPModbusMessage modbusMessage = request.buildMessage();
        TCPModbusMessage response = sendTcpModbusMessage(modbusMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponse(response.getPduPayload().getFunctionCode(),
                        response.getPduPayload().getData(),
                        response.getMbapHeader().getTransactionId(), response.getMbapHeader().getUnitId(), request);
        return (WriteMultipleCoilsResponse) modbusResponse;
    }

    @Override
    public WriteSingleRegisterResponse writeSingleRegisters(WriteSingleRegisterRequest request) throws ModbusException {
        TCPModbusMessage modbusMessage = request.buildMessage();
        TCPModbusMessage response = sendTcpModbusMessage(modbusMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponse(response.getPduPayload().getFunctionCode(),
                        response.getPduPayload().getData(),
                        response.getMbapHeader().getTransactionId(), response.getMbapHeader().getUnitId(), request);
        return (WriteSingleRegisterResponse) modbusResponse;
    }

    @Override
    public WriteMultipleRegistersResponse writeMultipleRegisters(WriteMultipleRegistersRequest request) throws ModbusException {
        TCPModbusMessage modbusMessage = request.buildMessage();
        TCPModbusMessage response = sendTcpModbusMessage(modbusMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponse(response.getPduPayload().getFunctionCode(),
                        response.getPduPayload().getData(),
                        response.getMbapHeader().getTransactionId(), response.getMbapHeader().getUnitId(), request);
        return (WriteMultipleRegistersResponse) modbusResponse;
    }

    @Override
    public ReadCoilsResponse readCoils(ReadCoilsRequest request) throws ModbusException {
        TCPModbusMessage modbusMessage = request.buildMessage();
        TCPModbusMessage response = sendTcpModbusMessage(modbusMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponse(response.getPduPayload().getFunctionCode(),
                        response.getPduPayload().getData(),
                        response.getMbapHeader().getTransactionId(), response.getMbapHeader().getUnitId(), request);
        return (ReadCoilsResponse) modbusResponse;
    }

    @Override
    public ReadHoldingRegistersResponse readHoldingRegisters(ReadHoldingRegistersRequest request) throws ModbusException {
        TCPModbusMessage modbusMessage = request.buildMessage();
        TCPModbusMessage response = sendTcpModbusMessage(modbusMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponse(response.getPduPayload().getFunctionCode(),
                        response.getPduPayload().getData(),
                        response.getMbapHeader().getTransactionId(), response.getMbapHeader().getUnitId(), request);
        return (ReadHoldingRegistersResponse) modbusResponse;
    }

    @Override
    public ReadInputRegistersResponse readInputRegisters(ReadInputRegistersRequest request) throws ModbusException {
        TCPModbusMessage modbusMessage = request.buildMessage();
        TCPModbusMessage response = sendTcpModbusMessage(modbusMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponse(response.getPduPayload().getFunctionCode(),
                        response.getPduPayload().getData(),
                        response.getMbapHeader().getTransactionId(), response.getMbapHeader().getUnitId(), request);
        return (ReadInputRegistersResponse) modbusResponse;
    }

    @Override
    public ReadDiscreteInputsResponse readDiscreteInputRegisters(ReadDiscreteInputsRequest request) throws ModbusException {
        TCPModbusMessage modbusMessage = request.buildMessage();
        TCPModbusMessage response = sendTcpModbusMessage(modbusMessage);
        BaseModbusResponse<?> modbusResponse =
                BaseModbusResponse.createResponse(response.getPduPayload().getFunctionCode(),
                        response.getPduPayload().getData(),
                        response.getMbapHeader().getTransactionId(), response.getMbapHeader().getUnitId(), request);
        return (ReadDiscreteInputsResponse) modbusResponse;
    }


    @Getter
    @Setter
    private OnConnectChangeListener onConnectChangeListener;

    public interface OnConnectChangeListener {
        void onConnectChange(boolean isConnect);
    }

    @Getter
    @Setter
    private OnDispatchMessageListener onDispatchMessageListener;

    public interface OnDispatchMessageListener {
        void onDispatchModbusMessage(TCPModbusMessage tcpModbusMessage) throws ModbusException;
    }


}
