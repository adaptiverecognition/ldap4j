package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.SynchronizedWait;
import java.net.InetSocketAddress;
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

    class Read extends SynchronizedWork<@Nullable ByteBuffer, @Nullable ByteBuffer> {
        private @NotNull ByteBuffer buffer=ByteBuffer.EMPTY;
        private boolean endOfStream;
        private @Nullable Throwable error;

        @Override
        protected @Nullable ByteBuffer completedSynchronized() throws Throwable {
            if (null!=error) {
                Throwable throwable=error;
                error=null;
                throw throwable;
            }
            if (buffer.isEmpty() && endOfStream) {
                return null;
            }
            ByteBuffer byteBuffer=buffer;
            buffer=ByteBuffer.EMPTY;
            return byteBuffer;
        }

        @Override
        protected void completedSynchronized(@Nullable ByteBuffer value) {
            if (null==value) {
                endOfStream=true;
            }
            else {
                buffer=buffer.append(value);
            }
        }

        @Override
        protected void failedSynchronized(@NotNull Throwable throwable) {
            if (Exceptions.isConnectionClosedException(throwable)) {
                endOfStream=true;
            }
            else {
                error=Exceptions.join(error, throwable);
            }
        }

        @Override
        protected boolean startWorkingSynchronized() {
            return buffer.isEmpty() && (!endOfStream) && (null==error);
        }

        @Override
        protected @Nullable ByteBuffer timeoutSynchronized() {
            return ByteBuffer.EMPTY;
        }
    }

    interface Server<T> extends Connection {
        @NotNull Lava<@Nullable T> accept();

        default @NotNull Lava<@NotNull T> acceptNotNull() {
            return Lava.checkEndNanos("accept timeout")
                    .composeIgnoreResult(this::accept)
                    .compose((result)->{
                        if (null==result) {
                            return acceptNotNull();
                        }
                        return Lava.complete(result);
                    });
        }

        @NotNull Lava<@NotNull InetSocketAddress> localAddress();
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

    abstract class SynchronizedWork<T, U> {
        @FunctionalInterface
        public interface Work<T, U> {
            void work(@NotNull Context context, @NotNull SynchronizedWork<T, U> work) throws Throwable;
        }

        public final @NotNull SynchronizedWait wait=new SynchronizedWait();
        private boolean working;
        
        public void completed(U value) {
            synchronized (wait.lock) {
                wait.signalAll();
                working=false;
                completedSynchronized(value);
            }
        }

        protected abstract T completedSynchronized() throws Throwable;

        protected abstract void completedSynchronized(U value);

        public void failed(@NotNull Throwable throwable) {
            synchronized (wait.lock) {
                wait.signalAll();
                working=false;
                failedSynchronized(throwable);
            }
        }

        protected abstract void failedSynchronized(@NotNull Throwable throwable);

        protected abstract boolean startWorkingSynchronized() throws Throwable;

        protected abstract T timeoutSynchronized() throws Throwable;

        public @NotNull Lava<T> work(@NotNull Work<T, U> work) {
            Objects.requireNonNull(work, "work");
            return Lava.context()
                    .compose((context)->{
                        boolean startWorking;
                        synchronized (wait.lock) {
                            startWorking=(!working) && startWorkingSynchronized();
                            if (startWorking) {
                                working=true;
                            }
                        }
                        if (startWorking) {
                            work.work(context, this);
                        }
                        return wait.await((context2)->{
                            if (working) {
                                if (context2.isEndNanosInTheFuture()) {
                                    return Either.right(null);
                                }
                                return Either.left(timeoutSynchronized());
                            }
                            return Either.left(completedSynchronized());
                        });
                    });
        }
    }
    
    class Write extends SynchronizedWork<Void, Void> {
        private @Nullable Throwable error;

        @Override
        protected Void completedSynchronized() throws Throwable {
            if (null==error) {
                return null;
            }
            else {
                throw error;
            }
        }

        @Override
        protected void completedSynchronized(Void value) {
        }

        @Override
        protected void failedSynchronized(@NotNull Throwable throwable) {
            this.error=Exceptions.join(error, throwable);
        }

        @Override
        protected boolean startWorkingSynchronized() {
            return true;
        }

        @Override
        protected Void timeoutSynchronized() throws Throwable {
            throw new TimeoutException();
        }

        public static  @NotNull Lava<Void> writeStatic(@NotNull Work<Void, Void> write) {
            return new Write()
                    .work(write);
        }
    }

    @NotNull Lava<@NotNull InetSocketAddress> localAddress();

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

    @NotNull Lava<@NotNull InetSocketAddress> remoteAddress();

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
