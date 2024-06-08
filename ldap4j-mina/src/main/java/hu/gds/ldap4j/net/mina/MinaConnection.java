package hu.gds.ldap4j.net.mina;

import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.ByteBuffer;
import hu.gds.ldap4j.net.ClosedException;
import hu.gds.ldap4j.net.DuplexConnection;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.nio.channels.ClosedChannelException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteToClosedSessionException;
import org.apache.mina.filter.FilterEvent;
import org.apache.mina.transport.socket.nio.NioSession;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MinaConnection implements DuplexConnection {
    private static abstract class CallbackListener<F extends IoFuture, T> extends ErrorListener<F> {
        protected final @NotNull Callback<T> callback;
        protected final @NotNull Context context;

        public CallbackListener(@NotNull Callback<T> callback, @NotNull Context context, @NotNull Log log) {
            super(log);
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
        }

        @Override
        protected void operationCompleteImpl(F future) {
            try {
                operationCompleteImpl2(future);
            }
            catch (Throwable throwable) {
                context.fail(callback, throwable);
            }
        }

        protected abstract void operationCompleteImpl2(F future);
    }

    private static abstract class ErrorListener<F extends IoFuture> implements IoFutureListener<F> {
        protected final @NotNull Log log;

        public ErrorListener(@NotNull Log log) {
            this.log=Objects.requireNonNull(log, "log");
        }

        @Override
        public void operationComplete(F future) {
            try {
                operationCompleteImpl(future);
            }
            catch (Throwable throwable) {
                log.error(getClass(), throwable);
            }
        }

        protected abstract void operationCompleteImpl(F future);
    }

    private class SessionHandler implements IoHandler {
        @Override
        public void event(IoSession ioSession, FilterEvent filterEvent) {
        }

        @Override
        public void exceptionCaught(IoSession ioSession, Throwable throwable) {
            try {
                read.failed(throwable);
            }
            catch (Throwable throwable2) {
                log.error(getClass(), throwable2);
            }
        }

        @Override
        public void inputClosed(IoSession ioSession) {
            try {
                read.completed(null);
            }
            catch (Throwable throwable) {
                log.error(getClass(), throwable);
            }
        }

        @Override
        public void messageReceived(IoSession ioSession, Object object) {
            try {
                Objects.requireNonNull(object, "object");
                IoBuffer buffer=(IoBuffer)object;
                byte[] array=new byte[buffer.remaining()];
                buffer.get(array);
                ByteBuffer byteBuffer=ByteBuffer.create(array);
                read.completed(byteBuffer);
            }
            catch (Throwable throwable) {
                log.error(getClass(), throwable);
            }
        }

        @Override
        public void messageSent(IoSession ioSession, Object object) {
        }

        @Override
        public void sessionClosed(IoSession ioSession) {
            synchronized (lock) {
                sessionClosed=true;
                for (Iterator<Write> iterator=writes.iterator(); iterator.hasNext(); ) {
                    iterator.next().failed(new ClosedException());
                    iterator.remove();
                }
            }
        }

        @Override
        public void sessionCreated(IoSession ioSession) {
        }

        @Override
        public void sessionIdle(IoSession ioSession, IdleStatus idleStatus) {
        }

        @Override
        public void sessionOpened(IoSession ioSession) {
        }
    }

    private static class SocketOptionSetter extends SocketOptionVisitor.SameObject<NioSocketConnector> {
        @Override
        protected void soKeepAlive2(NioSocketConnector socket, boolean value) {
            socket.getSessionConfig().setKeepAlive(value);
        }

        @Override
        protected void soLingerSeconds2(NioSocketConnector socket, int value) {
            socket.getSessionConfig().setSoLinger(value);
        }

        @Override
        protected void soReceiveBuffer2(NioSocketConnector socket, int value) {
            socket.getSessionConfig().setReceiveBufferSize(value);
        }

        @Override
        protected void soReuseAddress2(NioSocketConnector socket, boolean value) {
            socket.getSessionConfig().setReuseAddress(value);
        }

        @Override
        protected void soSendBuffer2(NioSocketConnector socket, int value) {
            socket.getSessionConfig().setSendBufferSize(value);
        }

        @Override
        protected void soTcpNoDelay2(NioSocketConnector socket, boolean value) {
            socket.getSessionConfig().setTcpNoDelay(value);
        }
    }

    private final @NotNull NioSocketConnector connector;
    private final Object lock=new Object();
    private final @NotNull Log log;
    private final Read read=new Read();
    private IoSession session;
    private boolean sessionClosed;
    private final Set<Write> writes=new HashSet<>();

    private MinaConnection(@NotNull NioSocketConnector connector, @NotNull Log log) {
        this.connector=Objects.requireNonNull(connector, "connector");
        this.log=Objects.requireNonNull(log, "log");
    }

    @Override
    public @NotNull Lava<Void> close() {
        return Lava.finallyGet(
                ()->{
                    connector.dispose();
                    return Lava.VOID;
                },
                ()->(callback, context)->session.closeNow()
                        .addListener(new CallbackListener<>(callback, context, log) {
                            @Override
                            protected void operationCompleteImpl2(IoFuture future) {
                                context.complete(callback, null);
                            }
                        }));
    }

    public static @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory(
            @NotNull IoProcessor<NioSession> ioProcessor,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
        Objects.requireNonNull(ioProcessor, "ioProcessor");
        Objects.requireNonNull(socketOptions, "socketOptions");
        return (remoteAddress)->{
            Objects.requireNonNull(remoteAddress, "remoteAddress");
            class Connect {
                private MinaConnection connection;
                private NioSocketConnector connector;
                private volatile boolean error=true;
                private Log log;
                private IoSession session;

                private Lava<Void> closeOnError() {
                    return Lava.finallyGet(
                            ()->{
                                if (error && (null!=connector)) {
                                    connector.dispose();
                                }
                                return Lava.VOID;
                            },
                            ()->(callback, context)->{
                                if (error && (null!=session)) {
                                    session.closeNow()
                                            .addListener(new CallbackListener<>(callback, context, log) {
                                                @Override
                                                protected void operationCompleteImpl2(IoFuture future) {
                                                    context.complete(callback, null);
                                                }
                                            });
                                }
                                else {
                                    context.complete(callback, null);
                                }
                            });
                }

                private void connect(Callback<DuplexConnection> callback, Context context) throws Throwable {
                    log=context.log();
                    connector=new NioSocketConnector(ioProcessor);
                    connection=new MinaConnection(connector, log);
                    connector.setConnectTimeoutMillis(
                            context.clock().checkDelayMillis(
                                    context.checkEndNanos(MinaConnection.class+" connect timeout 1"),
                                    MinaConnection.class+" connect timeout 2"));
                    connector.setHandler(connection.new SessionHandler());
                    DuplexConnection.visitSocketOptions(connector, socketOptions, new SocketOptionSetter());
                    connector.connect(remoteAddress)
                            .addListener(new CallbackListener<ConnectFuture, DuplexConnection>(
                                    callback, context, log) {
                                @Override
                                protected void operationCompleteImpl2(ConnectFuture future) {
                                    Throwable throwable=future.getException();
                                    if (null!=throwable) {
                                        if (Exceptions.isTimeoutException(throwable)) {
                                            throwable=Exceptions.asTimeoutException(throwable);
                                        }
                                        else if (Exceptions.isUnknownHostException(throwable)) {
                                            throwable=Exceptions.asUnknownHostException(throwable);
                                        }
                                        context.fail(callback, throwable);
                                        return;
                                    }
                                    if (!future.isConnected()) {
                                        context.fail(callback, new IllegalStateException("not connected"));
                                        return;
                                    }
                                    session=future.getSession();
                                    if (null==session) {
                                        context.fail(callback, new IllegalStateException("no session"));
                                        return;
                                    }
                                    connection.session=session;
                                    error=false;
                                    context.complete(callback, connection);
                                }
                            });
                }
            }
            Connect connect=new Connect();
            return Lava.finallyGet(connect::closeOnError, ()->connect::connect);
        };
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
        return Lava.supplier(()->Lava.complete(!connector.isDisposing()));
    }

    @Override
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return read.read((context)->{
        });
    }

    @Override
    public @NotNull Lava<Void> shutDownOutput() {
        return Lava.fail(new UnsupportedOperationException());
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> supportsShutDownOutput() {
        return Lava.complete(false);
    }

    @Override
    public @NotNull Lava<Void> write(@NotNull ByteBuffer value) {
        return Write.writeStatic(
                (context)->(write)->{
                    if (value.isEmpty()) {
                        write.completed();
                    }
                    else {
                        synchronized (lock) {
                            if (sessionClosed) {
                                throw new ClosedException();
                            }
                            writes.add(write);
                        }
                        session.write(IoBuffer.wrap(value.arrayCopy()))
                                .addListener(new ErrorListener<WriteFuture>(log) {
                                    @Override
                                    protected void operationCompleteImpl(WriteFuture future) {
                                        synchronized (lock) {
                                            if (!writes.remove(write)) {
                                                return;
                                            }
                                        }
                                        Throwable throwable=future.getException();
                                        if (null!=throwable) {
                                            if (throwable instanceof WriteToClosedSessionException) {
                                                throwable=new ClosedChannelException();
                                            }
                                            write.failed(throwable);
                                            return;
                                        }
                                        if (!future.isWritten()) {
                                            write.failed(new IllegalStateException("not written"));
                                            return;
                                        }
                                        write.completed();
                                    }
                                });
                    }
                }
        );
    }
}
