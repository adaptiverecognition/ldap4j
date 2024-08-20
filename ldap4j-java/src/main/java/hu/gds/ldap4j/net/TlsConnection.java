package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Consumer;
import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.Lock;
import java.io.EOFException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This will buffer reads during handshake.
 */
public class TlsConnection implements DuplexConnection {
    private static class Closed extends State {
        @Override
        boolean closeLocked() {
            return false;
        }

        @Override
        void failedLocked(@NotNull Throwable throwable) {
        }

        @Override
        @NotNull Lava<Void> handshakeLoopLocked() {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<@Nullable Boolean> isOpenAndNotFailedLocked() {
            return Lava.complete(false);
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readLocked() {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<Void> restartTlsHandshakeLocked(@NotNull Consumer<@NotNull SSLEngine> consumer) {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<Void> shutDownOutputLocked() {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<Void> startTlsHandshakeLocked(
                @NotNull Function<@NotNull InetSocketAddress, @NotNull SSLEngine> function,
                @Nullable Executor handshakeExecutor,
                @NotNull InetSocketAddress remoteAddress) {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputLocked() {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<@NotNull SSLSession> tlsSession() {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<Void> writeLocked(@NotNull ByteBuffer value) {
            throw new ClosedException();
        }
    }

    private class Failed extends NotClosed {
        private final @NotNull Throwable throwable;

        public Failed(@NotNull Throwable throwable) {
            this.throwable=Objects.requireNonNull(throwable, "throwable");
        }

        @Override
        void failedLocked(@NotNull Throwable throwable) {
            Exceptions.addSuppressed(throwable, this.throwable);
        }

        @Override
        @NotNull Lava<Void> handshakeLoopLocked() {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedLocked() {
            return Lava.complete(false);
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readLocked() {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<Void> restartTlsHandshakeLocked(@NotNull Consumer<@NotNull SSLEngine> consumer) {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<Void> shutDownOutputLocked() {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<Void> startTlsHandshakeLocked(
                @NotNull Function<@NotNull InetSocketAddress, @NotNull SSLEngine> function,
                @Nullable Executor handshakeExecutor,
                @NotNull InetSocketAddress remoteAddress) {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputLocked() {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<@Nullable SSLSession> tlsSession() {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<Void> writeLocked(@NotNull ByteBuffer value) {
            throw new RuntimeException("connection already failed", throwable);
        }
    }

    private abstract class NotClosed extends State {
        @Override
        boolean closeLocked() {
            state=new Closed();
            return true;
        }
    }

    private abstract class NotFailed extends NotClosed {
        protected boolean gettingOpenAndNotFailed;
        protected boolean netEndOfStream;
        protected boolean netOutputShutDown;
        protected boolean reading;
        protected boolean shuttingDownNetOutput;
        protected boolean writing;

        public NotFailed(
                boolean gettingOpenAndNotFailed,
                boolean netEndOfStream,
                boolean netOutputShutDown,
                boolean reading,
                boolean shuttingDownNetOutput,
                boolean writing) {
            this.gettingOpenAndNotFailed=gettingOpenAndNotFailed;
            this.netEndOfStream=netEndOfStream;
            this.netOutputShutDown=netOutputShutDown;
            this.reading=reading;
            this.shuttingDownNetOutput=shuttingDownNetOutput;
            this.writing=writing;
            if (gettingOpenAndNotFailed) {
                throw new IllegalStateException("should not be getting open and not failed while changing state");
            }
            if (reading) {
                throw new IllegalStateException("should not be reading while changing state");
            }
            if (shuttingDownNetOutput) {
                throw new IllegalStateException("should not be shutting down net output while changing state");
            }
            if (writing) {
                throw new IllegalStateException("should not be writing while changing state");
            }
        }

        protected static void checkStartTlsHandShake(
                boolean gettingOpenAndNotFailed,
                boolean outputShutDown,
                boolean reading,
                boolean shuttingDownOutput,
                boolean writing) {
            if (gettingOpenAndNotFailed) {
                throw new RuntimeException("already getting whether connection is open and not failed");
            }
            if (outputShutDown) {
                throw new RuntimeException("output shut down");
            }
            if (reading) {
                throw new RuntimeException("already reading");
            }
            if (shuttingDownOutput) {
                throw new RuntimeException("already shutting down output");
            }
            if (writing) {
                throw new RuntimeException("already writing");
            }
        }

        @Override
        void failedLocked(@NotNull Throwable throwable) {
            state=new Failed(throwable);
        }

        protected @NotNull Lava<@NotNull Boolean> isConnectionOpenAndNotFailedLocked() {
            if (gettingOpenAndNotFailed) {
                throw new RuntimeException("already getting whether connection is open and not failed");
            }
            gettingOpenAndNotFailed=true;
            return Lava.finallyGet(
                    ()->{
                        gettingOpenAndNotFailed=false;
                        return Lava.VOID;
                    },
                    ()->lock.leave(connection::isOpenAndNotFailed));
        }

        protected @NotNull Lava<@Nullable ByteBuffer> readConnectionLocked() {
            reading=true;
            return Lava.finallyGet(
                    ()->{
                        reading=false;
                        return Lava.VOID;
                    },
                    ()->lock.leave(connection::read));
        }
    }

    private class Plaintext extends NotFailed {
        private boolean gettingSupportsShutdownOutput;

        public Plaintext(
                boolean gettingOpenAndNotFailed,
                boolean netEndOfStream,
                boolean netOutputShutDown,
                boolean reading,
                boolean shuttingDownNetOutput,
                boolean writing) {
            super(
                    gettingOpenAndNotFailed,
                    netEndOfStream,
                    netOutputShutDown,
                    reading,
                    shuttingDownNetOutput,
                    writing);
        }

        @Override
        @NotNull Lava<Void> handshakeLoopLocked() {
            throw new UnsupportedOperationException();
        }

        @Override
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedLocked() {
            return isConnectionOpenAndNotFailedLocked();
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readLocked() {
            if (reading) {
                throw new RuntimeException("already reading");
            }
            if (netEndOfStream) {
                return Lava.complete(null);
            }
            reading=true;
            return readConnectionLocked()
                    .compose((result)->{
                        if (null==result) {
                            netEndOfStream=true;
                        }
                        return Lava.complete(result);
                    });
        }

        @Override
        @NotNull Lava<Void> restartTlsHandshakeLocked(@NotNull Consumer<@NotNull SSLEngine> consumer) {
            throw new RuntimeException("tls not started");
        }

        @Override
        @NotNull Lava<Void> shutDownOutputLocked() {
            if (shuttingDownNetOutput) {
                throw new RuntimeException("already shutting down output");
            }
            if (writing) {
                throw new RuntimeException("already writing");
            }
            if (netOutputShutDown) {
                return Lava.VOID;
            }
            shuttingDownNetOutput=true;
            return Lava.finallyGet(
                    ()->{
                        netOutputShutDown=true;
                        shuttingDownNetOutput=false;
                        return Lava.VOID;
                    },
                    ()->lock.leave(connection::shutDownOutput));
        }

        @Override
        @NotNull Lava<Void> startTlsHandshakeLocked(
                @NotNull Function<@NotNull InetSocketAddress, @NotNull SSLEngine> function,
                @Nullable Executor handshakeExecutor,
                @NotNull InetSocketAddress remoteAddress) throws Throwable {
            checkStartTlsHandShake(
                    gettingOpenAndNotFailed,
                    netOutputShutDown,
                    reading,
                    shuttingDownNetOutput,
                    writing);
            if (netEndOfStream) {
                throw new EOFException();
            }
            if (gettingSupportsShutdownOutput) {
                throw new RuntimeException("already getting whether connection supports shutting down output");
            }
            SSLEngine sslEngine=Objects.requireNonNull(function.apply(remoteAddress), "sslEngine");
            sslEngine.beginHandshake();
            TlsHandshake tlsHandshake=new TlsHandshake(
                    ByteBuffer.empty(),
                    gettingOpenAndNotFailed,
                    handshakeExecutor,
                    netEndOfStream,
                    netOutputShutDown,
                    ByteBuffer.empty(),
                    reading,
                    shuttingDownNetOutput,
                    sslEngine,
                    writing);
            state=tlsHandshake;
            return tlsHandshake.handshakeLoopLocked();
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputLocked() {
            if (gettingSupportsShutdownOutput) {
                throw new RuntimeException("already getting whether connection supports shutting down output");
            }
            gettingSupportsShutdownOutput=true;
            return Lava.finallyGet(
                    ()->{
                        gettingSupportsShutdownOutput=false;
                        return Lava.VOID;
                    },
                    ()->lock.leave(connection::supportsShutDownOutput));
        }

        @Override
        @NotNull Lava<@Nullable SSLSession> tlsSession() {
            return Lava.complete(null);
        }

        @Override
        @NotNull Lava<Void> writeLocked(@NotNull ByteBuffer value) {
            if (netOutputShutDown) {
                throw new RuntimeException("output already shut down");
            }
            if (shuttingDownNetOutput) {
                throw new RuntimeException("already shutting down output");
            }
            if (writing) {
                throw new RuntimeException("already writing");
            }
            writing=true;
            return Lava.finallyGet(
                    ()->{
                        writing=false;
                        return Lava.VOID;
                    },
                    ()->lock.leave(()->connection.write(value)));

        }
    }

    private abstract static class State {
        abstract boolean closeLocked();

        abstract void failedLocked(@NotNull Throwable throwable);

        abstract @NotNull Lava<Void> handshakeLoopLocked();

        abstract @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedLocked();

        abstract @NotNull Lava<@Nullable ByteBuffer> readLocked();

        abstract @NotNull Lava<Void> restartTlsHandshakeLocked(
                @NotNull Consumer<@NotNull SSLEngine> consumer) throws Throwable;

        abstract @NotNull Lava<Void> shutDownOutputLocked();

        abstract @NotNull Lava<Void> startTlsHandshakeLocked(
                @NotNull Function<@NotNull InetSocketAddress, @NotNull SSLEngine> function,
                @Nullable Executor handshakeExecutor,
                @NotNull InetSocketAddress remoteAddress)
                throws Throwable;

        abstract @NotNull Lava<@NotNull Boolean> supportsShutDownOutputLocked();

        abstract @NotNull Lava<@Nullable SSLSession> tlsSession();

        abstract @NotNull Lava<Void> writeLocked(@NotNull ByteBuffer value);
    }

    private abstract class Tls extends NotFailed {
        protected @NotNull ByteBuffer appReadBuffer;
        protected final @Nullable Executor handshakeExecutor;
        protected @NotNull ByteBuffer netReadBuffer;
        protected final @NotNull SSLEngine sslEngine;

        public Tls(
                @NotNull ByteBuffer appReadBuffer,
                boolean gettingOpenAndNotFailed,
                @Nullable Executor handshakeExecutor,
                boolean netEndOfStream,
                boolean netOutputShutDown,
                @NotNull ByteBuffer netReadBuffer,
                boolean reading,
                boolean shuttingDownNetOutput,
                @NotNull SSLEngine sslEngine,
                boolean writing) {
            super(
                    gettingOpenAndNotFailed,
                    netEndOfStream,
                    netOutputShutDown,
                    reading,
                    shuttingDownNetOutput,
                    writing);
            this.appReadBuffer=Objects.requireNonNull(appReadBuffer, "appReadBuffer");
            this.handshakeExecutor=handshakeExecutor;
            this.netReadBuffer=Objects.requireNonNull(netReadBuffer, "netReadBuffer");
            this.sslEngine=Objects.requireNonNull(sslEngine, "sslEngine");
        }

        protected @NotNull Lava<@Nullable ByteBuffer> unwrap(boolean handshake) {
            return Lava.context()
                    .compose((context)->{
                        if (!context.isEndNanosInTheFuture()) {
                            if (handshake) {
                                throw new TimeoutException("handshake timeout");
                            }
                            return Lava.complete(ByteBuffer.empty());
                        }
                        java.nio.ByteBuffer appBuffer
                                =java.nio.ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
                        java.nio.ByteBuffer netBuffer=netReadBuffer.nioByteBufferCopy();
                        return lock.leave(()->Lava.complete(sslEngine.unwrap(netBuffer, appBuffer)))
                                .compose((result)->{
                                    SSLEngineResult.Status status=result.getStatus();
                                    return switch (status) {
                                        case BUFFER_OVERFLOW -> throw new IllegalStateException();
                                        case BUFFER_UNDERFLOW -> {
                                            if (netEndOfStream) {
                                                netReadBuffer=ByteBuffer.empty();
                                                sslEngine.closeInbound();
                                                yield Lava.complete(null);
                                            }
                                            yield unwrapRead(handshake);
                                        }
                                        case CLOSED, OK -> {
                                            boolean closed=SSLEngineResult.Status.CLOSED.equals(status);
                                            netReadBuffer=netReadBuffer.subBuffer(
                                                    netReadBuffer.size()-netBuffer.remaining(),
                                                    netReadBuffer.size());
                                            if (closed && (!netReadBuffer.isEmpty())) {
                                                throw new RuntimeException(
                                                        "unexpected data after tls engine close %s"
                                                                .formatted(netReadBuffer));
                                            }
                                            ByteBuffer app=ByteBuffer.create(appBuffer.flip());
                                            if ((!closed) || (!app.isEmpty())) {
                                                yield Lava.complete(app);
                                            }
                                            if (netEndOfStream) {
                                                yield Lava.complete(null);
                                            }
                                            yield unwrapRead(handshake);
                                        }
                                    };
                                });
                    });
        }

        private @NotNull Lava<@Nullable ByteBuffer> unwrapRead(boolean handshake) {
            return readConnectionLocked()
                    .compose((result)->{
                        if (null==result) {
                            netEndOfStream=true;
                        }
                        else {
                            netReadBuffer=netReadBuffer.append(result);
                        }
                        return unwrap(handshake);
                    });
        }

        protected @NotNull Lava<Void> wrap(boolean force, @NotNull ByteBuffer app) {
            Objects.requireNonNull(app, "app");
            return Lava.checkEndNanos("wrap timeout")
                    .composeIgnoreResult(()->{
                        if ((!force) && app.isEmpty()) {
                            return Lava.VOID;
                        }
                        java.nio.ByteBuffer appBuffer=app.nioByteBufferCopy();
                        java.nio.ByteBuffer netBuffer
                                =java.nio.ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
                        return lock.leave(()->Lava.complete(sslEngine.wrap(appBuffer, netBuffer)))
                                .compose((result)->{
                                    SSLEngineResult.Status status=result.getStatus();
                                    return switch (status) {
                                        case BUFFER_OVERFLOW, BUFFER_UNDERFLOW -> throw new IllegalStateException();
                                        case CLOSED, OK -> {
                                            ByteBuffer net=ByteBuffer.create(netBuffer.flip());
                                            ByteBuffer app2=app.subBuffer(
                                                    app.size()-appBuffer.remaining(),
                                                    app.size());
                                            yield lock.leave(()->connection.write(net))
                                                    .composeIgnoreResult(()->wrap(false, app2));
                                        }
                                    };
                                });
                    });
        }
    }

    private class TlsConnected extends Tls {
        public TlsConnected(
                @NotNull ByteBuffer appReadBuffer,
                boolean gettingOpenAndNotFailed,
                @Nullable Executor handshakeExecutor,
                boolean netEndOfStream,
                boolean netOutputShutDown,
                @NotNull ByteBuffer netReadBuffer,
                boolean reading,
                boolean shuttingDownNetOutput,
                @NotNull SSLEngine sslEngine,
                boolean writing) {
            super(
                    appReadBuffer,
                    gettingOpenAndNotFailed,
                    handshakeExecutor,
                    netEndOfStream,
                    netOutputShutDown,
                    netReadBuffer,
                    reading,
                    shuttingDownNetOutput,
                    sslEngine,
                    writing);
        }

        private void checkHandshakeLocked() {
            if (explicitTlsRenegotiation) {
                switch (sslEngine.getHandshakeStatus()) {
                    case NEED_TASK, NEED_UNWRAP, NEED_UNWRAP_AGAIN, NEED_WRAP ->
                            throw new TlsHandshakeRestartNeededException();
                }
            }
        }

        @Override
        @NotNull Lava<Void> handshakeLoopLocked() {
            throw new UnsupportedOperationException();
        }

        @Override
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedLocked() {
            return isConnectionOpenAndNotFailedLocked();
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readLocked() {
            if (reading) {
                throw new RuntimeException("already reading");
            }
            checkHandshakeLocked();
            if (!appReadBuffer.isEmpty()) {
                @NotNull ByteBuffer result=appReadBuffer;
                appReadBuffer=ByteBuffer.empty();
                return Lava.complete(result);
            }
            reading=true;
            return Lava.finallyGet(
                    ()->{
                        reading=false;
                        return Lava.VOID;
                    },
                    ()->unwrap(false));
        }

        @Override
        @NotNull Lava<Void> restartTlsHandshakeLocked(
                @NotNull Consumer<@NotNull SSLEngine> consumer) throws Throwable {
            checkStartTlsHandShake(
                    gettingOpenAndNotFailed,
                    netOutputShutDown,
                    reading,
                    shuttingDownNetOutput,
                    writing);
            consumer.accept(sslEngine);
            switch (sslEngine.getHandshakeStatus()) {
                case FINISHED, NOT_HANDSHAKING -> sslEngine.beginHandshake();
            }
            TlsHandshake tlsHandshake=new TlsHandshake(
                    appReadBuffer,
                    gettingOpenAndNotFailed,
                    handshakeExecutor,
                    netEndOfStream,
                    netOutputShutDown,
                    netReadBuffer,
                    reading,
                    shuttingDownNetOutput,
                    sslEngine,
                    writing);
            state=tlsHandshake;
            return tlsHandshake.handshakeLoopLocked();
        }

        @Override
        @NotNull Lava<Void> shutDownOutputLocked() {
            if (shuttingDownNetOutput) {
                throw new RuntimeException("already shutting down output");
            }
            if (writing) {
                throw new RuntimeException("already writing");
            }
            if (netOutputShutDown) {
                return Lava.VOID;
            }
            shuttingDownNetOutput=true;
            sslEngine.closeOutbound();
            return Lava.finallyGet(
                    ()->{
                        netOutputShutDown=true;
                        shuttingDownNetOutput=false;
                        return Lava.VOID;
                    },
                    this::shutDownOutputLoopLocked);
        }

        private @NotNull Lava<Void> shutDownOutputLoopLocked() {
            return Lava.checkEndNanos("shut down output timeout")
                    .composeIgnoreResult(()->{
                        if (sslEngine.isOutboundDone()) {
                            return lock.leave(connection::shutDownOutputSafe);
                        }
                        return wrap(true, ByteBuffer.empty())
                                .composeIgnoreResult(this::shutDownOutputLoopLocked);
                    });
        }

        @Override
        @NotNull Lava<Void> startTlsHandshakeLocked(
                @NotNull Function<@NotNull InetSocketAddress, @NotNull SSLEngine> function,
                @Nullable Executor handshakeExecutor,
                @NotNull InetSocketAddress remoteAddress) {
            throw new RuntimeException("already using tls");
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputLocked() {
            return Lava.complete(true);
        }

        @Override
        @NotNull Lava<@Nullable SSLSession> tlsSession() {
            return Lava.supplier(()->Lava.complete(sslEngine.getSession()));
        }

        @Override
        @NotNull Lava<Void> writeLocked(@NotNull ByteBuffer value) {
            if (netOutputShutDown) {
                throw new RuntimeException("output already shut down");
            }
            if (shuttingDownNetOutput) {
                throw new RuntimeException("already shutting down output");
            }
            if (writing) {
                throw new RuntimeException("already writing");
            }
            checkHandshakeLocked();
            writing=true;
            return Lava.finallyGet(
                    ()->{
                        writing=false;
                        return Lava.VOID;
                    },
                    ()->wrap(false, value));
        }
    }

    private class TlsHandshake extends Tls {
        private boolean task;

        public TlsHandshake(
                @NotNull ByteBuffer appReadBuffer,
                boolean gettingOpenAndNotFailed,
                @Nullable Executor handshakeExecutor,
                boolean netEndOfStream,
                boolean netOutputShutDown,
                @NotNull ByteBuffer netReadBuffer,
                boolean reading,
                boolean shuttingDownNetOutput,
                @NotNull SSLEngine sslEngine,
                boolean writing) {
            super(
                    appReadBuffer,
                    gettingOpenAndNotFailed,
                    handshakeExecutor,
                    netEndOfStream,
                    netOutputShutDown,
                    netReadBuffer,
                    reading,
                    shuttingDownNetOutput,
                    sslEngine,
                    writing);
            if (netOutputShutDown) {
                throw new IllegalStateException("handshaking while output is shut down");
            }
        }

        @NotNull Lava<Void> handshakeLoopLocked() {
            return Lava.checkEndNanos("handshake timeout")
                    .composeIgnoreResult(()->{
                        if (task) {
                            Runnable runnable=sslEngine.getDelegatedTask();
                            if (null!=runnable) {
                                return lock.<Void>leave(()->{
                                            if (null==handshakeExecutor) {
                                                runnable.run();
                                                return Lava.VOID;
                                            }
                                            else {
                                                return (callback, context)->handshakeExecutor.execute(()->{
                                                    try {
                                                        runnable.run();
                                                        context.complete(callback, null);
                                                    }
                                                    catch (Throwable throwable) {
                                                        context.fail(callback, throwable);
                                                    }
                                                });
                                            }
                                        })
                                        .composeIgnoreResult(()->state.handshakeLoopLocked());
                            }
                            task=false;
                        }
                        return switch (sslEngine.getHandshakeStatus()) {
                            case FINISHED, NOT_HANDSHAKING -> {
                                state=new TlsConnected(
                                        appReadBuffer,
                                        gettingOpenAndNotFailed,
                                        handshakeExecutor,
                                        netEndOfStream,
                                        netOutputShutDown,
                                        netReadBuffer,
                                        reading,
                                        shuttingDownNetOutput,
                                        sslEngine,
                                        writing);
                                yield Lava.VOID;
                            }
                            case NEED_TASK -> {
                                task=true;
                                yield handshakeLoopLocked();
                            }
                            case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> unwrap(true)
                                    .compose((result)->{
                                        if (null==result) {
                                            throw new EOFException();
                                        }
                                        appReadBuffer=appReadBuffer.append(result);
                                        return state.handshakeLoopLocked();
                                    });
                            case NEED_WRAP -> wrap(true, ByteBuffer.empty())
                                    .composeIgnoreResult(()->state.handshakeLoopLocked());
                        };
                    });
        }

        @Override
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedLocked() {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readLocked() {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<Void> restartTlsHandshakeLocked(@NotNull Consumer<@NotNull SSLEngine> consumer) {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<Void> shutDownOutputLocked() {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<Void> startTlsHandshakeLocked(
                @NotNull Function<@NotNull InetSocketAddress, @NotNull SSLEngine> function,
                @Nullable Executor handshakeExecutor,
                @NotNull InetSocketAddress remoteAddress) {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputLocked() {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<@Nullable SSLSession> tlsSession() {
            return Lava.complete(sslEngine.getHandshakeSession());
        }

        @Override
        @NotNull Lava<Void> writeLocked(@NotNull ByteBuffer value) {
            throw new RuntimeException("tls handshaking");
        }
    }

    public static final boolean DEFAULT_EXPLICIT_TLS_RENEGOTIATION=false;

    private final @NotNull DuplexConnection connection;
    private final boolean explicitTlsRenegotiation;
    private final Lock lock=new Lock();
    private @NotNull State state=new Plaintext(false, false, false, false, false, false);

    public TlsConnection(@NotNull DuplexConnection connection) {
        this(connection, DEFAULT_EXPLICIT_TLS_RENEGOTIATION);
    }

    public TlsConnection(@NotNull DuplexConnection connection, boolean explicitTlsRenegotiation) {
        this.connection=Objects.requireNonNull(connection, "connection");
        this.explicitTlsRenegotiation=explicitTlsRenegotiation;
    }

    @Override
    public @NotNull Lava<Void> close() {
        return lock.enter(()->Lava.complete(state.closeLocked()))
                .compose((closed)->{
                    if (closed) {
                        return connection.close();
                    }
                    return Lava.VOID;
                });
    }

    public static @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull TlsConnection>> factory(
            boolean explicitTlsRenegotiation,
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory) {
        Objects.requireNonNull(factory, "factory");
        return (remoteAddress)->{
            Objects.requireNonNull(remoteAddress, "remoteAddress");
            return Closeable.wrapOrClose(
                    ()->factory.apply(remoteAddress),
                    (connection)->Lava.complete(new TlsConnection(connection, explicitTlsRenegotiation)));
        };
    }

    public static @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull TlsConnection>> factory(
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory) {
        return factory(DEFAULT_EXPLICIT_TLS_RENEGOTIATION, factory);
    }

    private <T> @NotNull Lava<T> getGuardedLock(@NotNull Supplier<@NotNull Lava<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return lock.enter(()->Lava.catchErrors(
                (throwable)->{
                    if (!(throwable instanceof TlsHandshakeRestartNeededException)) {
                        state.failedLocked(throwable);
                    }
                    throw throwable;
                },
                supplier,
                Throwable.class));
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
        return getGuardedLock(()->state.isOpenAndNotFailedLocked());
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> localAddress() {
        return connection.localAddress();
    }

    @Override
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return getGuardedLock(()->state.readLocked());
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> remoteAddress() {
        return connection.remoteAddress();
    }

    public @NotNull Lava<Void> restartTlsHandshake() {
        return restartTlsHandshake((sslEngine)->{
        });
    }

    public @NotNull Lava<Void> restartTlsHandshake(@NotNull Consumer<@NotNull SSLEngine> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return getGuardedLock(()->state.restartTlsHandshakeLocked(consumer));
    }

    public @NotNull Lava<Void> shutDownOutput() {
        return getGuardedLock(()->state.shutDownOutputLocked());
    }

    public @NotNull Lava<Void> startTlsHandshake(
            @NotNull Function<@Nullable InetSocketAddress, @NotNull SSLEngine> function,
            @Nullable Executor handshakeExecutor) {
        Objects.requireNonNull(function, "function");
        return connection.remoteAddress()
                .compose((remoteAddress)->getGuardedLock(
                        ()->state.startTlsHandshakeLocked(function, handshakeExecutor, remoteAddress)));
    }

    public @NotNull Lava<Void> startTlsHandshake(
            @Nullable Executor handshakeExecutor,
            @NotNull TlsSettings.Tls tlsSettings) {
        return startTlsHandshake(tlsSettings::createSSLEngine, handshakeExecutor);
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> supportsShutDownOutput() {
        return getGuardedLock(()->state.supportsShutDownOutputLocked());
    }

    public @NotNull Lava<@Nullable SSLSession> tlsSession() {
        return getGuardedLock(()->state.tlsSession());
    }

    @Override
    public @NotNull Lava<Void> write(@NotNull ByteBuffer value) {
        Objects.requireNonNull(value, "value");
        return getGuardedLock(()->state.writeLocked(value));
    }
}
