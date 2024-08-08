package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Lava;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaBlockingSocketConnection implements DuplexConnection {
    private static class SocketOptionSetter extends SocketOptionVisitor.SameObject<Socket> {
        @Override
        protected void soKeepAlive2(Socket socket, boolean value) throws Throwable {
            socket.setKeepAlive(value);
        }

        @Override
        protected void soLingerSeconds2(Socket socket, int value) throws Throwable {
            if (0<value) {
                socket.setSoLinger(true, value);
            }
            else {
                socket.setSoLinger(false, 0);
            }
        }

        @Override
        protected void soReceiveBuffer2(Socket socket, int value) throws Throwable {
            socket.setReceiveBufferSize(value);
        }

        @Override
        protected void soReuseAddress2(Socket socket, boolean value) throws Throwable {
            socket.setReuseAddress(true);
        }

        @Override
        protected void soSendBuffer2(Socket socket, int value) throws Throwable {
            socket.setSendBufferSize(value);
        }

        @Override
        protected void soTcpNoDelay2(Socket socket, boolean value) throws Throwable {
            socket.setTcpNoDelay(value);
        }
    }

    private final @NotNull Executor blockingIoExecutor;
    private final @NotNull InputStream inputStream;
    private final @NotNull Log log;
    private final @NotNull OutputStream outputStream;
    private final @NotNull Read read=new Read();
    private final @NotNull Socket socket;

    public JavaBlockingSocketConnection(
            @NotNull Executor blockingIoExecutor, @NotNull InputStream inputStream,
            @NotNull Log log, @NotNull OutputStream outputStream, @NotNull Socket socket) {
        this.blockingIoExecutor=Objects.requireNonNull(blockingIoExecutor, "executor");
        this.inputStream=Objects.requireNonNull(inputStream, "inputStream");
        this.log=Objects.requireNonNull(log, "log");
        this.outputStream=Objects.requireNonNull(outputStream, "outputStream");
        this.socket=Objects.requireNonNull(socket, "socket");
    }

    private void blockingRun(@NotNull Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        blockingIoExecutor.execute(()->{
            try {
                runnable.run();
            }
            catch (Throwable throwable) {
                log.error(getClass(), throwable);
            }
        });
    }

    @Override
    public @NotNull Lava<Void> close() {
        return Lava.supplier(()->{
            try {
                try {
                    outputStream.close();
                }
                finally {
                    inputStream.close();
                }
            }
            finally {
                socket.close();
            }
            return Lava.VOID;
        });
    }

    public static @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory(
            @NotNull Executor blockingIoExecutor,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
        Objects.requireNonNull(blockingIoExecutor, "blockingIoExecutor");
        Objects.requireNonNull(socketOptions, "socketOptions");
        return (remoteAddress)->{
            Objects.requireNonNull(remoteAddress, "remoteAddress");
            return (callback, context)->{
                @NotNull Log log=context.log();
                long endNanos=context.checkEndNanos(JavaBlockingSocketConnection.class+" connect timeout 1");
                blockingIoExecutor.execute(()->{
                    try {
                        try {
                            int delayMillis=context.clock().checkDelayMillis(
                                    endNanos, JavaBlockingSocketConnection.class+" connect timeout 2");
                            boolean error=true;
                            Socket socket=new Socket();
                            try {
                                DuplexConnection.visitSocketOptions(socket, socketOptions, new SocketOptionSetter());
                                try {
                                    socket.connect(remoteAddress, delayMillis);
                                }
                                catch (SocketTimeoutException ex) {
                                    throw new TimeoutException(ex.toString());
                                }
                                InputStream inputStream=socket.getInputStream();
                                try {
                                    OutputStream outputStream=socket.getOutputStream();
                                    try {
                                        JavaBlockingSocketConnection connection=new JavaBlockingSocketConnection(
                                                blockingIoExecutor, inputStream, log, outputStream, socket);
                                        error=false;
                                        context.complete(callback, connection);
                                    }
                                    finally {
                                        if (error) {
                                            inputStream.close();
                                        }
                                    }
                                }
                                finally {
                                    if (error) {
                                        inputStream.close();
                                    }
                                }
                            }
                            finally {
                                if (error) {
                                    socket.close();
                                }
                            }
                        }
                        catch (Throwable throwable) {
                            context.fail(callback, throwable);
                        }
                    }
                    catch (Throwable throwable) {
                        log.error(JavaBlockingSocketConnection.class, throwable);
                    }
                });
            };
        };
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
        return Lava.supplier(()->Lava.complete(!socket.isClosed()));
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> localAddress() {
        return Lava.supplier(()->Lava.complete((InetSocketAddress)socket.getLocalSocketAddress()));
    }

    @Override
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return read.work((context, work)->blockingRun(()->{
            try {
                byte[] array=new byte[PAGE_SIZE];
                int length=inputStream.read(array);
                if (0>length) {
                    read.completed(null);
                }
                else {
                    read.completed(ByteBuffer.create(array, 0, length));
                }
            }
            catch (Throwable throwable) {
                read.failed(throwable);
            }
        }));
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> remoteAddress() {
        return Lava.supplier(()->Lava.complete((InetSocketAddress)socket.getRemoteSocketAddress()));
    }

    @Override
    public @NotNull Lava<Void> shutDownOutput() {
        return Lava.supplier(()->{
            socket.shutdownOutput();
            return Lava.VOID;
        });
    }

    @Override
    public @NotNull Lava<Void> write(@NotNull ByteBuffer value) {
        return Write.writeStatic(
                (context, work)->blockingRun(()->{
                    try {
                        byte[] array=value.arrayCopy();
                        outputStream.write(array);
                        outputStream.flush();
                        work.completed(null);
                    }
                    catch (Throwable throwable) {
                        work.failed(throwable);
                    }
                }));
    }
}
