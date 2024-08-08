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
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaAsyncChannelConnection implements DuplexConnection {
    private static class Connect
            implements Function<@NotNull AsynchronousSocketChannel, @NotNull Lava<@NotNull DuplexConnection>> {
        private final @NotNull InetSocketAddress remoteAddress;

        public Connect(@NotNull InetSocketAddress remoteAddress) {
            this.remoteAddress=Objects.requireNonNull(remoteAddress, "remoteAddress");
        }

        @Override
        public @NotNull Lava<@NotNull DuplexConnection> apply(@NotNull AsynchronousSocketChannel channel) {
            return Lava.context()
                    .compose((context)->{
                        ConnectHandler handler=new ConnectHandler(context);
                        try {
                            channel.connect(remoteAddress, null, handler);
                        }
                        catch (Throwable throwable) {
                            handler.failed(throwable, null);
                        }
                        return handler.wait.await(waitLoop(channel, handler));
                    });
        }

        private @NotNull Function<@NotNull Context, @NotNull Either<@NotNull DuplexConnection, Void>> waitLoop(
                @NotNull AsynchronousSocketChannel channel,
                @NotNull ConnectHandler handler) {
            return (context)->{
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
                else {
                    if (!context.isEndNanosInTheFuture()) {
                        channel.close();
                    }
                    return Either.right(null);
                }
            };
        }
    }

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
        private final @NotNull java.nio.ByteBuffer byteBuffer=java.nio.ByteBuffer.allocate(DuplexConnection.PAGE_SIZE);

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

    public static class Server implements DuplexConnection.Server<@NotNull JavaAsyncChannelConnection> {
        private class Accept
                extends SynchronizedWork<@Nullable JavaAsyncChannelConnection, @Nullable AsynchronousSocketChannel> {
            @Override
            protected @Nullable JavaAsyncChannelConnection completedSynchronized() throws Throwable {
                if (null!=error) {
                    Throwable throwable=error;
                    error=null;
                    throw throwable;
                }
                if (!buffer.isEmpty()) {
                    return new JavaAsyncChannelConnection(buffer.removeFirst());
                }
                if (closed) {
                    throw new ClosedException();
                }
                return null;
            }

            @Override
            protected void completedSynchronized(@Nullable AsynchronousSocketChannel value) {
                if (null==value) {
                    error=Exceptions.join(error, new NullPointerException("value"));
                }
                else if (closed) {
                    error=closeChannel(value, error);
                }
                else {
                    buffer.addLast(value);
                }
            }

            @Override
            protected void failedSynchronized(@NotNull Throwable throwable) {
                error=Exceptions.join(error, throwable);
            }

            @Override
            protected boolean startWorkingSynchronized() {
                return buffer.isEmpty() && (!closed) && (null==error);
            }

            @Override
            protected @Nullable JavaAsyncChannelConnection timeoutSynchronized() {
                return null;
            }
        }

        private class AcceptHandler extends Handler<AsynchronousSocketChannel> {
            public AcceptHandler(@NotNull Context context) {
                super(context, Server.this.accept.wait);
            }

            @Override
            protected void completedSynchronized(AsynchronousSocketChannel result) {
                accept.completed(result);
            }

            @Override
            protected void failedSynchronized(@NotNull Throwable throwable) {
                accept.failed(throwable);
            }
        }
        private static class ServerSocketOptionSetter
                extends DuplexConnection.SocketOptionVisitor.SameObject<AsynchronousServerSocketChannel> {
            @Override
            protected void soKeepAlive2(AsynchronousServerSocketChannel socket, boolean value) throws Throwable {
                socket.setOption(StandardSocketOptions.SO_KEEPALIVE, value);
            }

            @Override
            protected void soLingerSeconds2(AsynchronousServerSocketChannel socket, int value) throws Throwable {
                socket.setOption(StandardSocketOptions.SO_LINGER, value);
            }

            @Override
            protected void soReceiveBuffer2(AsynchronousServerSocketChannel socket, int value) throws Throwable {
                socket.setOption(StandardSocketOptions.SO_RCVBUF, value);
            }

            @Override
            protected void soReuseAddress2(AsynchronousServerSocketChannel socket, boolean value) throws Throwable {
                socket.setOption(StandardSocketOptions.SO_REUSEADDR, value);
            }

            @Override
            protected void soSendBuffer2(AsynchronousServerSocketChannel socket, int value) throws Throwable {
                socket.setOption(StandardSocketOptions.SO_SNDBUF, value);
            }

            @Override
            protected void soTcpNoDelay2(AsynchronousServerSocketChannel socket, boolean value) throws Throwable {
                socket.setOption(StandardSocketOptions.TCP_NODELAY, value);
            }
        }

        private final @NotNull Accept accept=new Accept();
        private final @NotNull Deque<@NotNull AsynchronousSocketChannel> buffer=new LinkedList<>();
        private final @NotNull AsynchronousServerSocketChannel channel;
        private boolean closed;
        private @Nullable Throwable error;

        public Server(@NotNull AsynchronousServerSocketChannel channel) {
            this.channel=Objects.requireNonNull(channel, "channel");
        }

        @Override
        public @NotNull Lava<@Nullable JavaAsyncChannelConnection> accept() {
            return accept.work((context, work)->{
                AcceptHandler handler=new AcceptHandler(context);
                try {
                    channel.accept(null, handler);
                }
                catch (Throwable throwable) {
                    handler.failed(throwable, null);
                }
            });
        }

        @Override
        public @NotNull Lava<Void> close() {
            return Lava.supplier(()->{
                @NotNull Deque<@NotNull AsynchronousSocketChannel> channels;
                synchronized (accept.wait.lock) {
                    accept.wait.signalAll();
                    closed=true;
                    channels=new ArrayDeque<>(buffer);
                    buffer.clear();
                }
                Throwable throwable=closeChannel(channel, null);
                while (!channels.isEmpty()) {
                    throwable=closeChannel(channels.removeFirst(), throwable);
                }
                if (null!=throwable) {
                    return Lava.fail(throwable);
                }
                return Lava.VOID;
            });
        }

        public static @NotNull Lava<JavaAsyncChannelConnection.@NotNull Server> factory(
                @Nullable AsynchronousChannelGroup asynchronousChannelGroup,
                int backlog,
                @Nullable InetSocketAddress localAddress,
                @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
            return Lava.supplier(()->{
                boolean error=true;
                @NotNull AsynchronousServerSocketChannel channel;
                if (null==asynchronousChannelGroup) {
                    channel=AsynchronousServerSocketChannel.open();
                }
                else {
                    channel=AsynchronousServerSocketChannel.open(asynchronousChannelGroup);
                }
                try {
                    DuplexConnection.visitSocketOptions(channel, socketOptions, new ServerSocketOptionSetter());
                    channel.bind(localAddress, backlog);
                    Server server=new Server(channel);
                    error=false;
                    return Lava.complete(server);
                }
                finally {
                    if (error) {
                        channel.close();
                    }
                }
            });
        }

        @Override
        public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
            return Lava.supplier(()->Lava.complete(channel.isOpen()));
        }

        @Override
        public @NotNull Lava<@NotNull InetSocketAddress> localAddress() {
            return Lava.supplier(()->Lava.complete((InetSocketAddress)channel.getLocalAddress()));
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
        private final @NotNull SynchronizedWork<Void, Void> work;

        public WriteHandler(
                @NotNull java.nio.ByteBuffer byteBuffer,
                @NotNull Context context,
                @NotNull SynchronizedWork<Void, Void> work) {
            super(context, work.wait);
            this.byteBuffer=Objects.requireNonNull(byteBuffer, "byteBuffer");
            this.work=Objects.requireNonNull(work, "work");
        }

        @Override
        protected void completedSynchronized(Integer result) {
            if (byteBuffer.hasRemaining()) {
                new WriteHandler(byteBuffer, context, work)
                        .write();
            }
            else {
                work.completed(null);
            }
        }

        @Override
        protected void failedSynchronized(@NotNull Throwable throwable) {
            work.failed(throwable);
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

    private static @Nullable Throwable closeChannel(@NotNull Channel channel, @Nullable Throwable throwable) {
        try {
            channel.close();
        }
        catch (Throwable throwable2) {
            throwable=Exceptions.join(throwable, throwable2);
        }
        return throwable;
    }

    public static @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory(
            @Nullable AsynchronousChannelGroup asynchronousChannelGroup,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
        return (remoteAddress)->{
            Objects.requireNonNull(remoteAddress, "remoteAddress");
            return Lava.checkEndNanos(JavaAsyncChannelConnection.class+" connect timeout")
                    .composeIgnoreResult(()->{
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
                                    new Connect(remoteAddress));
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
    public @NotNull Lava<@NotNull InetSocketAddress> localAddress() {
        return Lava.supplier(()->Lava.complete((InetSocketAddress)channel.getLocalAddress()));
    }

    @Override
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return read.work((context, work)->{
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
    public @NotNull Lava<@NotNull InetSocketAddress> remoteAddress() {
        return Lava.supplier(()->Lava.complete((InetSocketAddress)channel.getRemoteAddress()));
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
                (context, work)->new WriteHandler(value.nioByteBufferCopy(), context, work)
                        .write());
    }
}
