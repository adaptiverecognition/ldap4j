package hu.gds.ldap4j.trampoline;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.SynchronizedWait;
import hu.gds.ldap4j.net.ByteBuffer;
import hu.gds.ldap4j.net.ClosedException;
import hu.gds.ldap4j.net.DuplexConnection;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EngineConnection implements DuplexConnection {
    private boolean closed;
    private boolean endOfStream;
    private @Nullable Throwable error;
    private final @NotNull InetSocketAddress localAddress;
    private boolean outputShutDown;
    private @NotNull ByteBuffer readBuffer=ByteBuffer.EMPTY;
    private int readBuffers;
    private final @NotNull InetSocketAddress remoteAddress;
    private final @NotNull SynchronizedWait wait=new SynchronizedWait();
    private @NotNull ByteBuffer writeBuffer=ByteBuffer.EMPTY;

    public EngineConnection(
            @NotNull InetSocketAddress localAddress,
            @NotNull InetSocketAddress remoteAddress) {
        this.localAddress=Objects.requireNonNull(localAddress, "localAddress");
        this.remoteAddress=Objects.requireNonNull(remoteAddress, "remoteAddress");
    }

    public void addReadBuffer(@Nullable ByteBuffer value) throws Throwable {
        synchronized (wait.lock) {
            checkClosedAndErrorSynchronized();
            if (null==value) {
                endOfStream=true;
            }
            else if (endOfStream) {
                throw new EOFException();
            }
            else {
                readBuffer=readBuffer.append(value);
                ++readBuffers;
            }
            wait.signalAll();
        }
    }

    private void checkClosedAndErrorSynchronized() throws Throwable {
        if (closed) {
            throw new ClosedException();
        }
        if (null!=error) {
            throw new IOException("connection failed", error);
        }
    }

    @Override
    public @NotNull Lava<Void> close() {
        return Lava.supplier(()->{
            synchronized (wait.lock) {
                closed=true;
                endOfStream=true;
                readBuffer=ByteBuffer.EMPTY;
                outputShutDown=true;
                wait.signalAll();
            }
            return Lava.VOID;
        });
    }

    public boolean endOfStream() throws Throwable {
        synchronized (wait.lock) {
            checkClosedAndErrorSynchronized();
            return endOfStream;
        }
    }

    public void failed(@NotNull Throwable throwable) {
        synchronized (wait.lock) {
            if (closed) {
                throw new ClosedException();
            }
            error=Exceptions.join(error, throwable);
        }
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
        return Lava.supplier(()->{
            synchronized (wait.lock) {
                return Lava.complete((!closed) && (null==error));
            }
        });
    }

    public boolean isReadBufferEmpty() throws Throwable {
        synchronized (wait.lock) {
            checkClosedAndErrorSynchronized();
            return readBuffer.isEmpty();
        }
    }

    public boolean isWriteBufferEmpty() throws Throwable {
        synchronized (wait.lock) {
            checkClosedAndErrorSynchronized();
            return writeBuffer.isEmpty();
        }
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> localAddress() {
        return Lava.supplier(()->{
            checkClosedAndErrorSynchronized();
            return Lava.complete(localAddress);
        });
    }

    public boolean outputShutDown() {
        synchronized (wait.lock) {
            return outputShutDown;
        }
    }

    @Override
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return wait.await((context)->{
            checkClosedAndErrorSynchronized();
            if (!readBuffer.isEmpty()) {
                @NotNull ByteBuffer result=readBuffer;
                readBuffer=ByteBuffer.EMPTY;
                --readBuffers;
                if (0>readBuffers) {
                    readBuffers=0;
                }
                return Either.left(result);
            }
            if (endOfStream) {
                return Either.left(null);
            }
            if (0<readBuffers) {
                --readBuffers;
                return Either.left(ByteBuffer.EMPTY);
            }
            if (!context.isEndNanosInTheFuture()) {
                return Either.left(ByteBuffer.EMPTY);
            }
            return Either.right(null);
        });
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> remoteAddress() {
        return Lava.supplier(()->{
            checkClosedAndErrorSynchronized();
            return Lava.complete(remoteAddress);
        });
    }

    public @Nullable ByteBuffer removeWriteBuffer() {
        synchronized (wait.lock) {
            if (!writeBuffer.isEmpty()) {
                @NotNull ByteBuffer result=writeBuffer;
                writeBuffer=ByteBuffer.EMPTY;
                return result;
            }
            if (outputShutDown) {
                return null;
            }
            return ByteBuffer.EMPTY;
        }
    }

    @Override
    public @NotNull Lava<Void> shutDownOutput() {
        return Lava.supplier(()->{
            synchronized (wait.lock) {
                checkClosedAndErrorSynchronized();
                outputShutDown=true;
            }
            return Lava.VOID;
        });
    }

    @Override
    public @NotNull Lava<Void> write(@NotNull ByteBuffer value) {
        return Lava.supplier(()->{
            synchronized (wait.lock) {
                checkClosedAndErrorSynchronized();
                if (outputShutDown) {
                    throw new IOException("output has been shut down");
                }
                writeBuffer=writeBuffer.append(value);
            }
            return Lava.VOID;
        });
    }
}
