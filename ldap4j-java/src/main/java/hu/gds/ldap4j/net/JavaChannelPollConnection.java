package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Clock;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.Lock;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaChannelPollConnection implements DuplexConnection {
    private static class SocketOptionSetter extends SocketOptionVisitor.SameObject<SocketChannel> {
        @Override
        protected void soKeepAlive2(SocketChannel socket, boolean value) throws Throwable {
            socket.setOption(StandardSocketOptions.SO_KEEPALIVE, value);
        }

        @Override
        protected void soLingerSeconds2(SocketChannel socket, int value) throws Throwable {
            socket.setOption(StandardSocketOptions.SO_LINGER, value);
        }

        @Override
        protected void soReceiveBuffer2(SocketChannel socket, int value) throws Throwable {
            socket.setOption(StandardSocketOptions.SO_RCVBUF, value);
        }

        @Override
        protected void soReuseAddress2(SocketChannel socket, boolean value) throws Throwable {
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, value);
        }

        @Override
        protected void soSendBuffer2(SocketChannel socket, int value) throws Throwable {
            socket.setOption(StandardSocketOptions.SO_SNDBUF, value);
        }

        @Override
        protected void soTcpNoDelay2(SocketChannel socket, boolean value) throws Throwable {
            socket.setOption(StandardSocketOptions.TCP_NODELAY, value);
        }
    }

    private final @NotNull SocketChannel channel;

    public JavaChannelPollConnection(@NotNull SocketChannel channel) {
        this.channel=Objects.requireNonNull(channel, "channel");
    }

    @Override
    public @NotNull Lava<Void> close() {
        return Lava.supplier(()->{
            channel.close();
            return Lava.VOID;
        });
    }

    public static @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory(
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
        return (remoteAddress)->{
            Objects.requireNonNull(remoteAddress, "remoteAddress");
            return Lava.checkEndNanos(JavaChannelPollConnection.class+" connect timeout")
                    .composeIgnoreResult(()->{
                        boolean error=true;
                        SocketChannel channel=SocketChannel.open();
                        try {
                            channel.configureBlocking(false);
                            DuplexConnection.visitSocketOptions(channel, socketOptions, new SocketOptionSetter());
                            Lava<@NotNull DuplexConnection> result;
                            if (channel.connect(remoteAddress)) {
                                result=Lava.complete(new JavaChannelPollConnection(channel));
                            }
                            else {
                                result=Closeable.wrapOrClose(
                                        (channel2)->{
                                            channel2.close();
                                            return Lava.VOID;
                                        },
                                        ()->Lava.complete(channel),
                                        (channel2)->poll(
                                                ()->{
                                                    if (channel.finishConnect()) {
                                                        return Either.left(Lava.complete(
                                                                new JavaChannelPollConnection(channel)));
                                                    }
                                                    return Either.right(false);
                                                },
                                                ()->{
                                                    throw new TimeoutException();
                                                }));
                            }
                            error=false;
                            return result;
                        }
                        catch (UnresolvedAddressException ex) {
                            throw new UnknownHostException(remoteAddress+", "+ex);
                        }
                        finally {
                            if (error) {
                                channel.close();
                            }
                        }
                    });
        };
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
        return Lava.supplier(()->Lava.complete(channel.isOpen()));
    }

    private static <T> @NotNull Lava<T> poll(
            @NotNull Supplier<@NotNull Either<@NotNull Lava<T>, @NotNull Boolean>> poll,
            @NotNull Supplier<@NotNull Lava<T>> timeout) {
        Objects.requireNonNull(poll, "poll");
        Objects.requireNonNull(timeout, "timeout");
        Lock lock=new Lock();
        return lock.enter(new Supplier<>() {
            @Override
            public Lava<T> get() {
                return Lava.nowNanos()
                        .compose(this::loop);
            }

            private @NotNull Lava<T> loop(long startNanos) {
                return Lava.supplier(()->{
                    @NotNull Either<@NotNull Lava<T>, @NotNull Boolean> either=poll.get();
                    if (either.isLeft()) {
                        return either.left();
                    }
                    return Lava.context()
                            .compose((context)->{
                                long nowNanos=context.clock().nowNanos();
                                long delayNanos=Clock.endNanosToDelayNanos(context.endNanos(), nowNanos);
                                if (0L>=delayNanos) {
                                    return Lava.supplier(timeout);
                                }
                                if (either.right()) {
                                    return loop(nowNanos);
                                }
                                long elapsedNanos=Clock.endNanosToDelayNanos(nowNanos, startNanos);
                                return Lava.endNanos(
                                                Clock.delayNanosToEndNanos(
                                                        Math.min(delayNanos, elapsedNanos/2),
                                                        nowNanos),
                                                ()->lock.newCondition().awaitEndNanos())
                                        .composeIgnoreResult(()->loop(startNanos));
                            });
                });
            }
        });
    }

    @Override
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return Lava.supplier(()->{
            java.nio.ByteBuffer buffer=java.nio.ByteBuffer.allocate(DuplexConnection.PAGE_SIZE);
            return poll(
                    ()->{
                        int read=channel.read(buffer);
                        if (0>read) {
                            return Either.left(Lava.complete(null));
                        }
                        if (0<read) {
                            buffer.flip();
                            return Either.left(Lava.complete(ByteBuffer.create(buffer)));
                        }
                        return Either.right(false);
                    },
                    ()->Lava.complete(ByteBuffer.EMPTY));
        });
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
        return Lava.supplier(()->{
            java.nio.ByteBuffer buffer=value.nioByteBufferCopy();
            return poll(
                    ()->{
                        int remaining0=buffer.remaining();
                        channel.write(buffer);
                        int remaining1=buffer.remaining();
                        if (0<remaining1) {
                            return Either.right(remaining0!=remaining1);
                        }
                        return Either.left(Lava.VOID);
                    },
                    ()->{
                        throw new TimeoutException();
                    });
        });
    }
}
