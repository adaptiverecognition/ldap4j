package hu.gds.ldap4j.net.netty;

import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.ByteBuffer;
import hu.gds.ldap4j.net.DuplexConnection;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.ChannelOutputShutdownException;
import io.netty.channel.socket.DuplexChannel;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NettyConnection implements DuplexConnection {
    private class ChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        public void channelInactive(ChannelHandlerContext channelHandlerContext) {
            try {
                read.completed(null);
            }
            catch (Throwable throwable) {
                log.error(getClass(), throwable);
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) {
            try {
                Objects.requireNonNull(byteBuf, "byteBuf");
                byte[] array=new byte[byteBuf.readableBytes()];
                byteBuf.readBytes(array);
                ByteBuffer byteBuffer=ByteBuffer.create(array);
                read.completed(byteBuffer);
            }
            catch (Throwable throwable) {
                log.error(getClass(), throwable);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            try {
                read.failed(cause);
            }
            catch (Throwable throwable) {
                log.error(getClass(), throwable);
            }
        }
    }

    private static abstract class ErrorListener implements ChannelFutureListener {
        protected final @NotNull Log log;

        public ErrorListener(@NotNull Log log) {
            this.log=Objects.requireNonNull(log, "log");
        }

        protected abstract void completed();

        protected abstract void failed(@NotNull Throwable throwable);

        @Override
        public void operationComplete(ChannelFuture channelFuture) {
            try {
                try {
                    if (channelFuture.isSuccess()) {
                        completed();
                    }
                    else {
                        failed(Objects.requireNonNull(channelFuture.cause(), "channelFuture.cause()"));
                    }
                }
                catch (Throwable throwable) {
                    failed(throwable);
                }
            }
            catch (Throwable throwable) {
                log.error(getClass(), throwable);
            }
        }
    }

    private static class SocketOptionSetter implements SocketOptionVisitor<Bootstrap> {
        @Override
        public Bootstrap soKeepAlive(Bootstrap socket, boolean value) {
            return socket.option(ChannelOption.SO_KEEPALIVE, value);
        }

        @Override
        public Bootstrap soLingerSeconds(Bootstrap socket, int value) {
            return socket.option(ChannelOption.SO_LINGER, value);
        }

        @Override
        public Bootstrap soReceiveBuffer(Bootstrap socket, int value) {
            return socket.option(ChannelOption.SO_RCVBUF, value);
        }

        @Override
        public Bootstrap soReuseAddress(Bootstrap socket, boolean value) {
            return socket.option(ChannelOption.SO_REUSEADDR, value);
        }

        @Override
        public Bootstrap soSendBuffer(Bootstrap socket, int value) {
            return socket.option(ChannelOption.SO_SNDBUF, value);
        }

        @Override
        public Bootstrap soTcpNoDelay(Bootstrap socket, boolean value) {
            return socket.option(ChannelOption.TCP_NODELAY, value);
        }
    }

    private static abstract class CallbackListener<T> extends ErrorListener {
        protected final @NotNull Callback<T> callback;
        protected final @NotNull Context context;

        public CallbackListener(@NotNull Callback<T> callback, @NotNull Context context, @NotNull Log log) {
            super(log);
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
        }

        @Override
        protected void failed(@NotNull Throwable throwable) {
            context.fail(callback, throwable);
        }
    }

    private DuplexChannel channel;
    private final @NotNull Log log;
    private final Read read=new Read();

    private NettyConnection(@NotNull Log log) {
        this.log=Objects.requireNonNull(log, "log");
    }

    @Override
    public @NotNull Lava<Void> close() {
        return (callback, context)->channel.close()
                .addListener(new CallbackListener<>(callback, context, log) {
                    @Override
                    protected void completed() {
                        context.complete(callback, null);
                    }
                });
    }

    public static @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory(
            @NotNull Class<? extends DuplexChannel> channelType,
            @NotNull EventLoopGroup eventLoopGroup,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
        Objects.requireNonNull(channelType, "channelType");
        Objects.requireNonNull(eventLoopGroup, "eventLoopGroup");
        Objects.requireNonNull(socketOptions, "socketOptions");
        return (remoteAddress)->{
            Objects.requireNonNull(remoteAddress, "remoteAddress");
            return (callback, context)->{
                class Connect {
                    volatile boolean error0=true;
                    volatile boolean error1=true;
                }
                Connect connect=new Connect();
                @NotNull Log log=context.log();
                NettyConnection connection=new NettyConnection(log);
                Bootstrap bootstrap=new Bootstrap()
                        .group(eventLoopGroup)
                        .channel(channelType)
                        .option(ChannelOption.AUTO_CLOSE, false)
                        .option(ChannelOption.AUTO_READ, false)
                        .option(
                                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                context.clock().checkDelayMillis(
                                        context.checkEndNanos(NettyConnection.class+" connect timeout 1"),
                                        NettyConnection.class+" connect timeout 2"))
                        .handler(connection.new ChannelHandler());
                bootstrap=DuplexConnection.visitSocketOptions(bootstrap, socketOptions, new SocketOptionSetter());
                ChannelFuture channelFuture=bootstrap.connect(remoteAddress);
                connection.channel=(DuplexChannel)channelFuture.channel();
                try {
                    callback=callback.finallyGet(
                            context,
                            Lava.supplier(()->{
                                if (connect.error1) {
                                    connection.channel.close();
                                }
                                return Lava.VOID;
                            }));
                    try {
                        channelFuture.addListener(new CallbackListener<>(callback, context, log) {
                            @Override
                            protected void completed() {
                                connect.error1=false;
                                context.complete(callback, connection);
                            }

                            @Override
                            protected void failed(@NotNull Throwable throwable) {
                                if (throwable instanceof ConnectTimeoutException) {
                                    throwable=new TimeoutException(throwable.toString());
                                }
                                super.failed(throwable);
                            }
                        });
                        connect.error0=false;
                    }
                    catch (Throwable throwable) {
                        context.fail(callback, throwable);
                    }
                }
                finally {
                    if (connect.error0) {
                        connection.channel.close();
                    }
                }
            };
        };
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
        return Lava.supplier(()->Lava.complete(channel.isOpen()));
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> localAddress() {
        return Lava.supplier(()->Lava.complete((InetSocketAddress)channel.localAddress()));
    }

    @Override
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return read.work((context, work)->channel.read());
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> remoteAddress() {
        return Lava.supplier(()->Lava.complete((InetSocketAddress)channel.remoteAddress()));
    }

    @Override
    public @NotNull Lava<Void> shutDownOutput() {
        return Lava.supplier(()->{
            channel.shutdownOutput();
            return Lava.VOID;
        });
    }

    @Override
    public @NotNull Lava<Void> write(@NotNull ByteBuffer value) {
        return Write.writeStatic(
                (context, work)->{
                    ByteBuf byteBuf=Unpooled.buffer(value.size());
                    value.write(byteBuf::writeBytes);
                    channel.writeAndFlush(byteBuf)
                            .addListener(new ErrorListener(log) {
                                @Override
                                protected void completed() {
                                    work.completed(null);
                                }

                                @Override
                                protected void failed(@NotNull Throwable throwable) {
                                    if ((throwable instanceof ChannelOutputShutdownException)
                                            && (null!=throwable.getCause())
                                            && Exceptions.isConnectionClosedException(throwable.getCause())) {
                                        throwable=throwable.getCause();
                                    }
                                    work.failed(throwable);
                                }
                            });
                }
        );
    }
}
