package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.SynchronizedWait;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaAsyncChannelConnection implements DuplexConnection {
    private static class ConnectHandler extends Handler<Void> {
        private boolean connected;
        private @Nullable Throwable connectError;

        public ConnectHandler(@NotNull Context context) {
            super(context, new SynchronizedWait());
        }

        @Override
        protected void completedSynchronized(Void result) {
            wait.signal();
            connected=true;
        }

        @Override
        protected void failedSynchronized(@NotNull Throwable throwable) {
            wait.signal();
            connected=true;
            connectError=Exceptions.join(connectError, throwable);
        }
    }

    private static abstract class Handler<T> implements CompletionHandler<T, Void> {
        private boolean completed;
        protected final @NotNull Context context;
        protected final @NotNull SynchronizedWait wait;

        public Handler(@NotNull Context context, @NotNull SynchronizedWait wait) {
            this.context=Objects.requireNonNull(context, "context");
            this.wait=Objects.requireNonNull(wait, "wait");
        }

        @Override
        public void completed(T result, Void attachment) {
            try {
                synchronized (wait.lock) {
                    if (completed) {
                        return;
                    }
                    completed=true;
                    completedSynchronized(result);
                }
            }
            catch (Throwable throwable) {
                context.log().error(getClass(), throwable);
            }
        }

        protected abstract void completedSynchronized(T result);

        @Override
        public void failed(Throwable exc, Void attachment) {
            try {
                synchronized (wait.lock) {
                    if (completed) {
                        return;
                    }
                    completed=true;
                    failedSynchronized((null==exc)?new NullPointerException("exc"):exc);
                }
            }
            catch (Throwable throwable) {
                context.log().error(getClass(), throwable);
            }
        }

        protected abstract void failedSynchronized(@NotNull Throwable throwable);
    }

    private class ReadHandler extends Handler<Integer> {
        private final java.nio.ByteBuffer byteBuffer=java.nio.ByteBuffer.allocate(DuplexConnection.PAGE_SIZE);

        public ReadHandler(@NotNull Context context, @NotNull SynchronizedWait readWait) {
            super(context, readWait);
        }

        @Override
        protected void completedSynchronized(Integer result) {
            if (null==result) {
                read.failed(new NullPointerException("result"));
            }
            else if (0>result) {
                read.completed(null);
            }
            else {
                byteBuffer.flip();
                read.completed(ByteBuffer.create(byteBuffer));
            }
        }

        @Override
        protected void failedSynchronized(@NotNull Throwable throwable) {
            read.failed(throwable);
        }
    }

    private static class SocketOptionSetter
            extends DuplexConnection.SocketOptionVisitor.SameObject<AsynchronousSocketChannel> {
        @Override
        protected void soKeepAlive2(AsynchronousSocketChannel socket, boolean value) throws Throwable {
            socket.setOption(StandardSocketOptions.SO_KEEPALIVE, value);
        }

        @Override
        protected void soLingerSeconds2(AsynchronousSocketChannel socket, int value) throws Throwable {
            socket.setOption(StandardSocketOptions.SO_LINGER, value);
        }

        @Override
        protected void soReceiveBuffer2(AsynchronousSocketChannel socket, int value) throws Throwable {
            socket.setOption(StandardSocketOptions.SO_RCVBUF, value);
        }

        @Override
        protected void soReuseAddress2(AsynchronousSocketChannel socket, boolean value) throws Throwable {
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, value);
        }

        @Override
        protected void soSendBuffer2(AsynchronousSocketChannel socket, int value) throws Throwable {
            socket.setOption(StandardSocketOptions.SO_SNDBUF, value);
        }

        @Override
        protected void soTcpNoDelay2(AsynchronousSocketChannel socket, boolean value) throws Throwable {
            socket.setOption(StandardSocketOptions.TCP_NODELAY, value);
        }
    }

    private class WriteHandler extends Handler<Integer> {
        private final @NotNull java.nio.ByteBuffer byteBuffer;
        private final @NotNull Write write;

        public WriteHandler(@NotNull java.nio.ByteBuffer byteBuffer, @NotNull Context context, @NotNull Write write) {
            super(context, write.wait);
            this.byteBuffer=Objects.requireNonNull(byteBuffer, "byteBuffer");
            this.write=Objects.requireNonNull(write, "write");
        }

        @Override
        protected void completedSynchronized(Integer result) {
            if (byteBuffer.hasRemaining()) {
                new WriteHandler(byteBuffer, context, write).write();
            }
            else {
                write.completed();
            }
        }

        @Override
        protected void failedSynchronized(@NotNull Throwable throwable) {
            write.failed(throwable);
        }

        public void write() {
            try {
                channel.write(byteBuffer, null, this);
            }
            catch (Throwable throwable) {
                failed(throwable, null);
            }
        }
    }

    private final @NotNull AsynchronousSocketChannel channel;
    private final Read read=new Read();

    public JavaAsyncChannelConnection(@NotNull AsynchronousSocketChannel channel) {
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
            @Nullable AsynchronousChannelGroup asynchronousChannelGroup,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
        return (remoteAddress)->{
            Objects.requireNonNull(remoteAddress, "remoteAddress");
            return Lava.context()
                    .compose((context)->{
                        context.checkEndNanos(JavaAsyncChannelConnection.class+" connect timeout");
                        boolean error=true;
                        AsynchronousSocketChannel channel=(null==asynchronousChannelGroup)
                                ?AsynchronousSocketChannel.open()
                                :AsynchronousSocketChannel.open(asynchronousChannelGroup);
                        try {
                            DuplexConnection.visitSocketOptions(channel, socketOptions, new SocketOptionSetter());
                            Lava<@NotNull DuplexConnection> result=Closeable.wrapOrClose(
                                    (channel2)->{
                                        channel2.close();
                                        return Lava.VOID;
                                    },
                                    ()->Lava.complete(channel),
                                    new Function<>() {
                                        @Override
                                        public @NotNull Lava<DuplexConnection> apply(
                                                @NotNull AsynchronousSocketChannel channel) {
                                            ConnectHandler handler=new ConnectHandler(context);
                                            try {
                                                channel.connect(remoteAddress, null, handler);
                                            }
                                            catch (Throwable throwable) {
                                                handler.failed(throwable, null);
                                            }
                                            return handler.wait.await((context)->waitLoop(channel, context, handler));
                                        }

                                        private @NotNull Either<@NotNull DuplexConnection, Void> waitLoop(
                                                @NotNull AsynchronousSocketChannel channel,
                                                @NotNull Context context,
                                                @NotNull ConnectHandler handler)
                                                throws Throwable {
                                            if (handler.connected) {
                                                if (null==handler.connectError) {
                                                    return Either.left(new JavaAsyncChannelConnection(channel));
                                                }
                                                Throwable throwable=handler.connectError;
                                                if (throwable instanceof AsynchronousCloseException) {
                                                    if (!context.isEndNanosInTheFuture()) {
                                                        throwable=new TimeoutException(
                                                                JavaAsyncChannelConnection.class
                                                                        +" connect timeout");
                                                    }
                                                }
                                                else if (Exceptions.isUnknownHostException(throwable)) {
                                                    throwable=Exceptions.asUnknownHostException(throwable);
                                                }
                                                throw throwable;
                                            }
                                            if (!context.isEndNanosInTheFuture()) {
                                                channel.close();
                                            }
                                            return Either.right(null);
                                        }
                                    });
                            error=false;
                            return result;
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

    @Override
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return read.read((context)->{
            ReadHandler handler=new ReadHandler(context, read.wait);
            try {
                channel.read(handler.byteBuffer, null, handler);
            }
            catch (Throwable throwable) {
                handler.failed(throwable, null);
            }
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
        return Write.writeStatic(
                (context)->(write)->new WriteHandler(value.nioByteBufferCopy(), context, write).write());
    }
}
