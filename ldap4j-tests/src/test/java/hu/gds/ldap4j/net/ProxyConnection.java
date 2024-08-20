package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.Lock;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProxyConnection implements DuplexConnection {
    public enum Mode {
        DROP_WRITE, NORMAL, TIMEOUT_WRITE
    }

    public static class Session {
        private final @NotNull AtomicBoolean endOfStream=new AtomicBoolean(false);
        private final @NotNull AtomicReference<@NotNull ByteBuffer> reads=new AtomicReference<>(ByteBuffer.empty());
        private final @NotNull AtomicReference<@NotNull Mode> mode;
        private final @NotNull AtomicBoolean outputShutDown=new AtomicBoolean(false);
        private final @NotNull AtomicBoolean supportsShutDownOutput=new AtomicBoolean(false);
        private final @NotNull AtomicReference<@NotNull ByteBuffer> writes=new AtomicReference<>(ByteBuffer.empty());

        public Session(@NotNull Mode mode) {
            Objects.requireNonNull(mode, "mode");
            this.mode=new AtomicReference<>(mode);
        }

        public Session() {
            this(Mode.NORMAL);
        }

        public boolean endOfStream() {
            return endOfStream.get();
        }

        public void mode(@NotNull Mode mode) {
            Objects.requireNonNull(mode, "mode");
            this.mode.set(mode);
        }

        public @NotNull ByteBuffer reads() {
            return reads.get();
        }

        public boolean outputShutDown() {
            return outputShutDown.get();
        }

        public boolean supportsShutDownOutput() {
            return supportsShutDownOutput.get();
        }

        public @NotNull ByteBuffer writes() {
            return writes.get();
        }
    }

    private final @NotNull DuplexConnection connection;
    private final @NotNull Session session;

    public ProxyConnection(
            @NotNull DuplexConnection connection, @NotNull Session session, boolean supportsShutDownOutput) {
        this.connection=Objects.requireNonNull(connection, "connection");
        this.session=Objects.requireNonNull(session, "session");
        session.supportsShutDownOutput.set(supportsShutDownOutput);
    }

    @Override
    public @NotNull Lava<Void> close() {
        return connection.close();
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
        return connection.isOpenAndNotFailed();
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> localAddress() {
        return connection.localAddress();
    }

    @Override
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return connection.read()
                .compose((result)->{
                    if (null==result) {
                        session.endOfStream.set(true);
                    }
                    else {
                        session.reads.updateAndGet((buffer)->buffer.append(result));
                    }
                    return Lava.complete(result);
                });
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> remoteAddress() {
        return connection.remoteAddress();
    }

    @Override
    public @NotNull Lava<Void> shutDownOutput() {
        return Lava.supplier(()->{
            session.outputShutDown.set(true);
            return switch (session.mode.get()) {
                case DROP_WRITE -> connection.write(ByteBuffer.empty());
                case NORMAL -> connection.shutDownOutput();
                case TIMEOUT_WRITE -> timeout("shut down output timeout");
            };
        });
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> supportsShutDownOutput() {
        return connection.supportsShutDownOutput();
    }

    private static @NotNull Lava<Void> timeout(@NotNull String message) {
        return Lava.checkEndNanos(message)
                .composeIgnoreResult(()->{
                    Lock lock=new Lock();
                    return lock.enter(lock.newCondition()::awaitEndNanos);
                })
                .composeIgnoreResult(()->timeout(message));
    }

    static @NotNull Function<@NotNull DuplexConnection, @NotNull Lava<@NotNull DuplexConnection>> wrap(
            @NotNull Session session) {
        Objects.requireNonNull(session, "session");
        return (connection)->connection.supportsShutDownOutput()
                .compose((supportsShutDownOutput)->Lava.complete(
                        new ProxyConnection(connection, session, supportsShutDownOutput)));
    }

    @Override
    public @NotNull Lava<Void> write(@NotNull ByteBuffer value) {
        Objects.requireNonNull(value, "value");
        return Lava.supplier(()->{
            session.writes.updateAndGet((buffer)->buffer.append(value));
            return switch (session.mode.get()) {
                case DROP_WRITE -> connection.write(ByteBuffer.empty());
                case NORMAL -> connection.write(value);
                case TIMEOUT_WRITE -> timeout("write timeout");
            };
        });
    }
}
