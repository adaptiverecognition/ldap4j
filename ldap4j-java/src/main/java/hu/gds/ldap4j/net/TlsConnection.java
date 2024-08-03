package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.Lock;
import java.io.EOFException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedResultLocked(boolean openAndNotFailed) {
            return Lava.complete(false);
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readLocked() {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readResultLocked(@Nullable ByteBuffer result) {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<Void> shutDownOutputLocked() {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<Void> shutDownOutputResultLocked() {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<Void> startTlsLocked(TlsSettings.@NotNull Tls tlsSettings) {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputLocked() {
            throw new ClosedException();
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputResultLocked(boolean supportsShutdownOutput) {
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

        @Override
        @NotNull Lava<Void> writeResultLocked() {
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
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedResultLocked(boolean openAndNotFailed) {
            return Lava.complete(false);
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readLocked() {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readResultLocked(@Nullable ByteBuffer result) {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<Void> shutDownOutputLocked() {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<Void> shutDownOutputResultLocked() {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<Void> startTlsLocked(TlsSettings.@NotNull Tls tlsSettings) {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputLocked() {
            throw new RuntimeException("connection already failed", throwable);
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputResultLocked(boolean supportsShutdownOutput) {
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

        @Override
        @NotNull Lava<Void> writeResultLocked() {
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
        @Override
        void failedLocked(@NotNull Throwable throwable) {
            state=new Failed(throwable);
        }
    }

    private class Plaintext extends NotFailed {
        private boolean endOfStream;
        private boolean gettingOpenAndNotFailed;
        private boolean gettingSupportsShutdownOutput;
        private boolean outputShutDown;
        private boolean reading;
        private boolean shuttingDownOutput;
        private boolean writing;

        @Override
        @NotNull Lava<Void> handshakeLoopLocked() {
            throw new UnsupportedOperationException();
        }

        @Override
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedLocked() {
            if (gettingOpenAndNotFailed) {
                throw new RuntimeException("already getting whether connection is open and not failed");
            }
            gettingOpenAndNotFailed=true;
            return lock.leave(connection::isOpenAndNotFailed)
                    .compose((openAndNotFailed)->state.isOpenAndNotFailedResultLocked(openAndNotFailed));
        }

        @Override
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedResultLocked(boolean openAndNotFailed) {
            gettingOpenAndNotFailed=false;
            return Lava.complete(openAndNotFailed);
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readLocked() {
            if (reading) {
                throw new RuntimeException("already reading");
            }
            if (endOfStream) {
                return Lava.complete(null);
            }
            reading=true;
            return readConnectionLocked();
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readResultLocked(@Nullable ByteBuffer result) {
            reading=false;
            if (null==result) {
                endOfStream=true;
            }
            return Lava.complete(result);
        }

        @Override
        @NotNull Lava<Void> shutDownOutputLocked() {
            if (shuttingDownOutput) {
                throw new RuntimeException("already shutting down output");
            }
            if (writing) {
                throw new RuntimeException("already writing");
            }
            if (outputShutDown) {
                return Lava.VOID;
            }
            shuttingDownOutput=true;
            return shutDownOutputConnectionLocked(false);
        }

        @Override
        @NotNull Lava<Void> shutDownOutputResultLocked() {
            outputShutDown=true;
            shuttingDownOutput=false;
            return Lava.VOID;
        }

        @Override
        @NotNull Lava<Void> startTlsLocked(TlsSettings.@NotNull Tls tlsSettings) throws Throwable {
            if (endOfStream) {
                throw new EOFException();
            }
            if (gettingOpenAndNotFailed) {
                throw new RuntimeException("already getting whether connection is open and not failed");
            }
            if (gettingSupportsShutdownOutput) {
                throw new RuntimeException("already getting whether connection supports shutting down output");
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
            SSLEngine sslEngine=tlsSettings.createSSLEngine(remoteAddress);
            sslEngine.beginHandshake();
            TlsHandshake tlsHandshake=new TlsHandshake(sslEngine);
            state=tlsHandshake;
            return tlsHandshake.handshakeLoopLocked();
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputLocked() {
            if (gettingSupportsShutdownOutput) {
                throw new RuntimeException("already getting whether connection supports shutting down output");
            }
            gettingSupportsShutdownOutput=true;
            return lock.leave(connection::supportsShutDownOutput)
                    .compose((supportsShutDownOutput)->
                            state.supportsShutDownOutputResultLocked(supportsShutDownOutput));
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputResultLocked(boolean supportsShutdownOutput) {
            gettingSupportsShutdownOutput=false;
            return Lava.complete(supportsShutdownOutput);
        }

        @Override
        @NotNull Lava<@Nullable SSLSession> tlsSession() {
            return Lava.complete(null);
        }

        @Override
        @NotNull Lava<Void> writeLocked(@NotNull ByteBuffer value) {
            if (outputShutDown) {
                throw new RuntimeException("output already shut down");
            }
            if (shuttingDownOutput) {
                throw new RuntimeException("already shutting down output");
            }
            if (writing) {
                throw new RuntimeException("already writing");
            }
            writing=true;
            return writeConnectionLocked(value);
        }

        @Override
        @NotNull Lava<Void> writeResultLocked() {
            writing=false;
            return Lava.VOID;
        }
    }

    private abstract static class State {
        abstract boolean closeLocked();

        abstract void failedLocked(@NotNull Throwable throwable);

        abstract @NotNull Lava<Void> handshakeLoopLocked();

        abstract @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedLocked();

        abstract @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedResultLocked(boolean openAndNotFailed);

        abstract @NotNull Lava<@Nullable ByteBuffer> readLocked();

        abstract @NotNull Lava<@Nullable ByteBuffer> readResultLocked(@Nullable ByteBuffer result);

        abstract @NotNull Lava<Void> shutDownOutputLocked();

        abstract @NotNull Lava<Void> shutDownOutputResultLocked();

        abstract @NotNull Lava<Void> startTlsLocked(@NotNull TlsSettings.Tls tlsSettings) throws Throwable;

        abstract @NotNull Lava<@NotNull Boolean> supportsShutDownOutputLocked();

        abstract @NotNull Lava<@NotNull Boolean> supportsShutDownOutputResultLocked(boolean supportsShutdownOutput);

        abstract @NotNull Lava<@Nullable SSLSession> tlsSession();

        abstract @NotNull Lava<Void> writeLocked(@NotNull ByteBuffer value);

        abstract @NotNull Lava<Void> writeResultLocked();
    }

    private abstract class Tls extends NotFailed {
        protected boolean cipherEndOfStream;
        protected @NotNull ByteBuffer cipherReadBuffer;
        protected final @NotNull SSLEngine sslEngine;

        public Tls(boolean cipherEndOfStream, @NotNull ByteBuffer cipherReadBuffer, @NotNull SSLEngine sslEngine) {
            this.cipherEndOfStream=cipherEndOfStream;
            this.cipherReadBuffer=Objects.requireNonNull(cipherReadBuffer, "cipherReadBuffer");
            this.sslEngine=Objects.requireNonNull(sslEngine, "sslEngine");
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readResultLocked(@Nullable ByteBuffer result) {
            if (null==result) {
                cipherEndOfStream=true;
            }
            else {
                cipherReadBuffer=cipherReadBuffer.append(result);
            }
            return Lava.complete(result);
        }

        @Override
        @NotNull Lava<@NotNull Boolean> supportsShutDownOutputResultLocked(boolean supportsShutdownOutput) {
            return Lava.complete(supportsShutdownOutput);
        }

        protected @NotNull Lava<@Nullable ByteBuffer> unwrap(boolean handshake) {
            return Lava.context()
                    .compose((context)->{
                        if (!context.isEndNanosInTheFuture()) {
                            if (handshake) {
                                throw new TimeoutException("handshake timeout");
                            }
                            return Lava.complete(ByteBuffer.EMPTY);
                        }
                        java.nio.ByteBuffer plainBuffer
                                =java.nio.ByteBuffer.allocate(sslEngine.getSession().getApplicationBufferSize());
                        java.nio.ByteBuffer cipherBuffer=cipherReadBuffer.nioByteBufferCopy();
                        return lock.leave(()->Lava.complete(sslEngine.unwrap(cipherBuffer, plainBuffer)))
                                .compose((result)->{
                                    SSLEngineResult.Status status=result.getStatus();
                                    return switch (status) {
                                        case BUFFER_OVERFLOW -> throw new IllegalStateException();
                                        case BUFFER_UNDERFLOW -> {
                                            if (cipherEndOfStream) {
                                                cipherReadBuffer=ByteBuffer.EMPTY;
                                                sslEngine.closeInbound();
                                                yield Lava.complete(null);
                                            }
                                            yield readConnectionLocked()
                                                    .composeIgnoreResult(()->unwrap(handshake));
                                        }
                                        case CLOSED, OK -> {
                                            boolean closed=SSLEngineResult.Status.CLOSED.equals(status);
                                            cipherReadBuffer=cipherReadBuffer.subBuffer(
                                                    cipherReadBuffer.size()-cipherBuffer.remaining(),
                                                    cipherReadBuffer.size());
                                            if (closed && (!cipherReadBuffer.isEmpty())) {
                                                throw new RuntimeException(
                                                        "unexpected data after tls engine close %s"
                                                                .formatted(cipherReadBuffer));
                                            }
                                            ByteBuffer plain=ByteBuffer.create(plainBuffer.flip());
                                            if ((!closed) || (!plain.isEmpty())) {
                                                yield Lava.complete(plain);
                                            }
                                            if (cipherEndOfStream) {
                                                yield Lava.complete(null);
                                            }
                                            yield readConnectionLocked()
                                                    .composeIgnoreResult(()->unwrap(handshake));
                                        }
                                    };
                                });
                    });
        }

        protected @NotNull Lava<Void> wrap(boolean force, @NotNull ByteBuffer plain) {
            Objects.requireNonNull(plain, "plain");
            return Lava.checkEndNanos("wrap timeout")
                    .composeIgnoreResult(()->{
                        if ((!force) && plain.isEmpty()) {
                            return Lava.VOID;
                        }
                        java.nio.ByteBuffer plainBuffer=plain.nioByteBufferCopy();
                        java.nio.ByteBuffer cipherBuffer
                                =java.nio.ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
                        return lock.leave(()->Lava.complete(sslEngine.wrap(plainBuffer, cipherBuffer)))
                                .compose((result)->{
                                    SSLEngineResult.Status status=result.getStatus();
                                    return switch (status) {
                                        case BUFFER_OVERFLOW, BUFFER_UNDERFLOW -> throw new IllegalStateException();
                                        case CLOSED, OK -> {
                                            ByteBuffer cipher=ByteBuffer.create(cipherBuffer.flip());
                                            ByteBuffer plain2=plain.subBuffer(
                                                    plain.size()-plainBuffer.remaining(),
                                                    plain.size());
                                            yield writeConnectionLocked(cipher)
                                                    .composeIgnoreResult(()->wrap(false, plain2));
                                        }
                                    };
                                });
                    });
        }

        @Override
        @NotNull Lava<Void> writeResultLocked() {
            return Lava.VOID;
        }
    }

    private class TlsConnected extends Tls {
        private boolean gettingOpenAndNotFailed;
        private boolean outputShutDown;
        private boolean reading;
        private boolean shuttingDownOutput;
        private boolean writing;

        public TlsConnected(
                boolean cipherEndOfStream, @NotNull ByteBuffer cipherReadBuffer, @NotNull SSLEngine sslEngine) {
            super(cipherEndOfStream, cipherReadBuffer, sslEngine);
        }

        @Override
        @NotNull Lava<Void> handshakeLoopLocked() {
            throw new UnsupportedOperationException();
        }

        @Override
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedLocked() {
            if (gettingOpenAndNotFailed) {
                throw new RuntimeException("already getting whether connection is open and not failed");
            }
            gettingOpenAndNotFailed=true;
            return lock.leave(connection::isOpenAndNotFailed)
                    .compose((openAndNotFailed)->state.isOpenAndNotFailedResultLocked(openAndNotFailed));
        }

        @Override
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedResultLocked(boolean openAndNotFailed) {
            gettingOpenAndNotFailed=false;
            return Lava.complete(openAndNotFailed);
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readLocked() {
            if (reading) {
                throw new RuntimeException("already reading");
            }
            reading=true;
            return unwrap(false)
                    .compose((result)->{
                        reading=false;
                        return Lava.complete(result);
                    });
        }

        @Override
        @NotNull Lava<Void> shutDownOutputLocked() {
            if (shuttingDownOutput) {
                throw new RuntimeException("already shutting down output");
            }
            if (writing) {
                throw new RuntimeException("already writing");
            }
            if (outputShutDown) {
                return Lava.VOID;
            }
            shuttingDownOutput=true;
            sslEngine.closeOutbound();
            return shutDownOutputLoopLocked();
        }

        private @NotNull Lava<Void> shutDownOutputLoopLocked() {
            return Lava.checkEndNanos("shut down output timeout")
                    .composeIgnoreResult(()->{
                        if (sslEngine.isOutboundDone()) {
                            return shutDownOutputConnectionLocked(true);
                        }
                        return wrap(true, ByteBuffer.EMPTY)
                                .composeIgnoreResult(this::shutDownOutputLoopLocked);
                    });
        }

        @Override
        @NotNull Lava<Void> shutDownOutputResultLocked() {
            outputShutDown=true;
            shuttingDownOutput=false;
            return Lava.VOID;
        }

        @Override
        @NotNull Lava<Void> startTlsLocked(TlsSettings.@NotNull Tls tlsSettings) {
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
            if (outputShutDown) {
                throw new RuntimeException("output already shut down");
            }
            if (shuttingDownOutput) {
                throw new RuntimeException("already shutting down output");
            }
            if (writing) {
                throw new RuntimeException("already writing");
            }
            writing=true;
            return wrap(false, value)
                    .compose((result)->{
                        writing=false;
                        return Lava.complete(result);
                    });
        }
    }

    private class TlsHandshake extends Tls {
        private boolean task;

        public TlsHandshake(@NotNull SSLEngine sslEngine) {
            super(false, ByteBuffer.EMPTY, sslEngine);
        }

        @NotNull Lava<Void> handshakeLoopLocked() {
            return Lava.checkEndNanos("handshake timeout")
                    .composeIgnoreResult(()->{
                        if (task) {
                            Runnable runnable=sslEngine.getDelegatedTask();
                            if (null!=runnable) {
                                return lock.leave(()->{
                                            runnable.run();
                                            return Lava.VOID;
                                        })
                                        .composeIgnoreResult(()->state.handshakeLoopLocked());
                            }
                            task=false;
                        }
                        return switch (sslEngine.getHandshakeStatus()) {
                            case FINISHED, NOT_HANDSHAKING -> {
                                state=new TlsConnected(cipherEndOfStream, cipherReadBuffer, sslEngine);
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
                                        if (!result.isEmpty()) {
                                            throw new RuntimeException("unexpected unwrap result");
                                        }
                                        return state.handshakeLoopLocked();
                                    });
                            case NEED_WRAP -> wrap(true, ByteBuffer.EMPTY)
                                    .composeIgnoreResult(()->state.handshakeLoopLocked());
                        };
                    });
        }

        @Override
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedLocked() {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<@NotNull Boolean> isOpenAndNotFailedResultLocked(boolean openAndNotFailed) {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<@Nullable ByteBuffer> readLocked() {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<Void> shutDownOutputLocked() {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<Void> shutDownOutputResultLocked() {
            throw new RuntimeException("tls handshaking");
        }

        @Override
        @NotNull Lava<Void> startTlsLocked(TlsSettings.@NotNull Tls tlsSettings) {
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

    private final @NotNull DuplexConnection connection;
    private final Lock lock=new Lock();
    private final @NotNull InetSocketAddress remoteAddress;
    private @NotNull State state=new Plaintext();

    public TlsConnection(@NotNull DuplexConnection connection, @NotNull InetSocketAddress remoteAddress) {
        this.connection=Objects.requireNonNull(connection, "connection");
        this.remoteAddress=Objects.requireNonNull(remoteAddress, "remoteAddress");
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
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory) {
        Objects.requireNonNull(factory, "factory");
        return (remoteAddress)->{
            Objects.requireNonNull(remoteAddress, "remoteAddress");
            return Closeable.wrapOrClose(
                    ()->factory.apply(remoteAddress),
                    (connection)->Lava.complete(new TlsConnection(connection, remoteAddress)));
        };
    }

    private <T> @NotNull Lava<T> getGuardedLock(@NotNull Supplier<@NotNull Lava<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return lock.enter(()->Lava.catchErrors(
                (throwable)->{
                    state.failedLocked(throwable);
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
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return getGuardedLock(()->state.readLocked());
    }

    private @NotNull Lava<@Nullable ByteBuffer> readConnectionLocked() {
        return lock.leave(connection::read)
                .compose((result)->state.readResultLocked(result));
    }

    public @NotNull Lava<Void> shutDownOutput() {
        return getGuardedLock(()->state.shutDownOutputLocked());
    }

    private @NotNull Lava<Void> shutDownOutputConnectionLocked(boolean safe) {
        return lock.leave(()->safe
                        ?connection.shutDownOutputSafe()
                        :connection.shutDownOutput())
                .composeIgnoreResult(()->state.shutDownOutputResultLocked());
    }

    public @NotNull Lava<Void> startTls(@NotNull TlsSettings.Tls tlsSettings) {
        Objects.requireNonNull(tlsSettings, "tlsSettings");
        return getGuardedLock(()->state.startTlsLocked(tlsSettings));
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

    private @NotNull Lava<Void> writeConnectionLocked(@NotNull ByteBuffer value) {
        return lock.leave(()->connection.write(value))
                .composeIgnoreResult(()->state.writeResultLocked());
    }
}
