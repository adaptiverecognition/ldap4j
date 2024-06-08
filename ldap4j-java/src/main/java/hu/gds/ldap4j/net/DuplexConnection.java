package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Consumer;
import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.SynchronizedWait;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exceptions are unrecoverable errors.
 * Read timeouts return empty buffers.
 * Shut down output and write timeouts throw exceptions.
 */
public interface DuplexConnection extends Connection {
    int PAGE_SIZE=1<<14;

    class Read {
        private @NotNull ByteBuffer buffer=ByteBuffer.EMPTY;
        private boolean endOfStream;
        private @Nullable Throwable error;
        private boolean reading;
        public final SynchronizedWait wait=new SynchronizedWait();

        private void completedSynchronized() {
            wait.signalAll();
            reading=false;
        }

        public void completed(@Nullable ByteBuffer value) {
            synchronized (wait.lock) {
                completedSynchronized();
                if (null==value) {
                    endOfStream=true;
                }
                else {
                    buffer=buffer.append(value);
                }
            }
        }

        public void failed(@NotNull Throwable throwable) {
            if (Exceptions.isConnectionClosedException(throwable)) {
                completed(null);
            }
            else {
                synchronized (wait.lock) {
                    completedSynchronized();
                    error=Exceptions.join(error, throwable);
                }
            }
        }

        public @NotNull Lava<@Nullable ByteBuffer> read(Consumer<Context> read) {
            return Lava.context()
                    .compose((context)->{
                        boolean read2;
                        synchronized (wait.lock) {
                            read2=buffer.isEmpty() && (!endOfStream) && (null==error) && (!reading);
                            if (read2) {
                                reading=true;
                            }
                        }
                        if (read2) {
                            read.accept(context);
                        }
                        return wait.await((context2)->{
                            if (reading) {
                                if (context2.isEndNanosInTheFuture()) {
                                    return Either.right(null);
                                }
                                return Either.left(ByteBuffer.EMPTY);
                            }
                            if (null!=error) {
                                Throwable throwable=error;
                                error=null;
                                throw throwable;
                            }
                            if (buffer.isEmpty() && endOfStream) {
                                return Either.left(null);
                            }
                            ByteBuffer byteBuffer=buffer;
                            buffer=ByteBuffer.EMPTY;
                            return Either.left(byteBuffer);
                        });
                    });
        }
    }

    interface SocketOptionVisitor<T> {
        abstract class SameObject<T> implements SocketOptionVisitor<T> {
            @Override
            public T soKeepAlive(T socket, boolean value) throws Throwable {
                soKeepAlive2(socket, value);
                return socket;
            }

            protected abstract void soKeepAlive2(T socket, boolean value) throws Throwable;

            @Override
            public T soLingerSeconds(T socket, int value) throws Throwable {
                soLingerSeconds2(socket, value);
                return socket;
            }

            protected abstract void soLingerSeconds2(T socket, int value) throws Throwable;

            @Override
            public T soReceiveBuffer(T socket, int value) throws Throwable {
                soReceiveBuffer2(socket, value);
                return socket;
            }

            protected abstract void soReceiveBuffer2(T socket, int value) throws Throwable;

            @Override
            public T soReuseAddress(T socket, boolean value) throws Throwable {
                soReuseAddress2(socket, value);
                return socket;
            }

            protected abstract void soReuseAddress2(T socket, boolean value) throws Throwable;

            @Override
            public T soSendBuffer(T socket, int value) throws Throwable {
                soSendBuffer2(socket, value);
                return socket;
            }

            protected abstract void soSendBuffer2(T socket, int value) throws Throwable;

            @Override
            public T soTcpNoDelay(T socket, boolean value) throws Throwable {
                soTcpNoDelay2(socket, value);
                return socket;
            }

            protected abstract void soTcpNoDelay2(T socket, boolean value) throws Throwable;
        }

        T soKeepAlive(T socket, boolean value) throws Throwable;

        T soLingerSeconds(T socket, int value) throws Throwable;

        T soReceiveBuffer(T socket, int value) throws Throwable;

        T soReuseAddress(T socket, boolean value) throws Throwable;

        T soSendBuffer(T socket, int value) throws Throwable;

        T soTcpNoDelay(T socket, boolean value) throws Throwable;

        default T unknown(SocketOption<?> socketOption, Object value) throws Throwable {
            throw new RuntimeException("unsupported socket option %s, value %s".formatted(socketOption, value));
        }
    }
    
    class Write {
        private @Nullable Throwable error;
        public final @NotNull SynchronizedWait wait=new SynchronizedWait();
        private boolean written;

        public void completed() {
            synchronized (wait.lock) {
                wait.signal();
                written=true;
            }
        }

        public void failed(@NotNull Throwable throwable) {
            synchronized (wait.lock) {
                wait.signal();
                written=true;
                this.error=Exceptions.join(error, throwable);
            }
        }

        public @NotNull Lava<Void> write(
                @NotNull Function<@NotNull Context, @NotNull Consumer<@NotNull Write>> write) {
            return Lava.context()
                    .compose((context)->{
                        write.apply(context).accept(this);
                        return wait.await((context2)->{
                            if (written) {
                                if (null==error) {
                                    return Either.left(null);
                                }
                                throw error;
                            }
                            if (context2.isEndNanosInTheFuture()) {
                                return Either.right(null);
                            }
                            throw new TimeoutException();
                        });
                    });
        }

        public static  @NotNull Lava<Void> writeStatic(
                @NotNull Function<@NotNull Context, @NotNull Consumer<@NotNull Write>> write) {
            return new Write().write(write);
        }
    }

    /**
     * result == null => end of stream
     */
    @NotNull Lava<@Nullable ByteBuffer> read();

    default @NotNull Lava<@Nullable ByteBuffer> readNonEmpty() {
        return Lava.checkEndNanos("read timeout")
                .composeIgnoreResult(this::read)
                .compose((readResult)->{
                    if ((null!=readResult) && readResult.isEmpty()) {
                        return readNonEmpty();
                    }
                    return Lava.complete(readResult);
                });
    }

    @NotNull Lava<Void> shutDownOutput();

    default @NotNull Lava<Void> shutDownOutputSafe() {
        return supportsShutDownOutput()
                .compose((supportsShutDownOutput)->supportsShutDownOutput
                        ?shutDownOutput()
                        :Lava.VOID);
    }

    default @NotNull Lava<@NotNull Boolean> supportsShutDownOutput() {
        return Lava.complete(true);
    }

    static <T> T visitSocketOptions(
            T socket,
            @NotNull Map<? extends @NotNull SocketOption<?>, @NotNull Object> socketOptions,
            @NotNull SocketOptionVisitor<T> visitor)
            throws Throwable {
        for (Map.Entry<? extends SocketOption<?>, Object> entry: socketOptions.entrySet()) {
            SocketOption<?> key=Objects.requireNonNull(entry.getKey(), "key");
            Object value=Objects.requireNonNull(entry.getValue(), "value");
            if (StandardSocketOptions.SO_KEEPALIVE.equals(key)) {
                socket=visitor.soKeepAlive(socket, (Boolean)value);
            }
            else if (StandardSocketOptions.SO_LINGER.equals(key)) {
                socket=visitor.soLingerSeconds(socket, (Integer)value);
            }
            else if (StandardSocketOptions.SO_RCVBUF.equals(key)) {
                socket=visitor.soReceiveBuffer(socket, (Integer)value);
            }
            else if (StandardSocketOptions.SO_REUSEADDR.equals(key)) {
                socket=visitor.soReuseAddress(socket, (Boolean)value);
            }
            else if (StandardSocketOptions.SO_SNDBUF.equals(key)) {
                socket=visitor.soSendBuffer(socket, (Integer)value);
            }
            else if (StandardSocketOptions.TCP_NODELAY.equals(key)) {
                socket=visitor.soTcpNoDelay(socket, (Boolean)value);
            }
            else {
                socket=visitor.unknown(key, value);
            }
        }
        return socket;
    }

    @NotNull Lava<Void> write(@NotNull ByteBuffer value);
}
