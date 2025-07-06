package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Exceptions;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NoAcceptServer implements Closeable {
    public static final int CONNECT_TIMEOUT_MILLIS=100;
    private static final int MAX_CONNECTIONS=8;

    private final @NotNull InetSocketAddress bindAddress;
    private final @NotNull List<@NotNull Closeable> closeables;
    private final long connectTimeoutMillis;
    private final @NotNull ServerSocket serverSocket;

    private NoAcceptServer(
            @NotNull InetSocketAddress bindAddress,
            @NotNull List<@NotNull Closeable> closeables,
            long connectTimeoutMillis,
            @NotNull ServerSocket serverSocket) {
        if ((0L>=connectTimeoutMillis) || (Integer.MAX_VALUE<connectTimeoutMillis)) {
            throw new IllegalArgumentException("invalid connectTimeoutMillis %,d".formatted(connectTimeoutMillis));
        }
        this.bindAddress=Objects.requireNonNull(bindAddress, "bindAddress");
        this.closeables=Objects.requireNonNull(closeables, "closeables");
        this.connectTimeoutMillis=connectTimeoutMillis;
        this.serverSocket=Objects.requireNonNull(serverSocket, "serverSocket");
    }

    @Override
    public void close() throws IOException {
        closeAll(closeables);
    }

    private static void closeAll(
            @NotNull List<@NotNull Closeable> closeables)
            throws IOException {
        Throwable throwable=null;
        for (int ii=closeables.size()-1; 0<=ii; --ii) {
            try {
                closeables.get(ii)
                        .close();
            }
            catch (Throwable throwable2) {
                throwable=Exceptions.join(throwable, throwable2);
            }
        }
        if (null!=throwable) {
            if (throwable instanceof IOException ex) {
                throw ex;
            }
            else {
                throw new IOException(throwable);
            }
        }
    }

    public static @NotNull NoAcceptServer create() throws IOException {
        return create(null, CONNECT_TIMEOUT_MILLIS);
    }

    public static @NotNull NoAcceptServer create(
            @Nullable InetSocketAddress bindAddress,
            long connectTimeoutMillis)
            throws IOException {
        if (null==bindAddress) {
            bindAddress=new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        }
        var closeables=new ArrayList<@NotNull Closeable>(MAX_CONNECTIONS+1);
        boolean error=true;
        try {
            var serverSocket=new ServerSocket();
            closeables.add(serverSocket);
            var result=new NoAcceptServer(bindAddress, closeables, connectTimeoutMillis, serverSocket);
            error=false;
            return result;
        }
        finally {
            if (error) {
                closeAll(closeables);
            }
        }
    }

    public @NotNull InetSocketAddress localAddress() {
        return (InetSocketAddress)serverSocket.getLocalSocketAddress();
    }

    public void start() throws Throwable {
        serverSocket.bind(bindAddress, 1);
        for (int cc=MAX_CONNECTIONS; ; --cc) {
            if (0>=cc) {
                throw new IOException("no connection timed out");
            }
            var socket=new Socket();
            closeables.add(socket);
            try {
                socket.connect(
                        serverSocket.getLocalSocketAddress(),
                        (int)connectTimeoutMillis);
            }
            catch (SocketTimeoutException ex) {
                break;
            }
        }
    }
}
