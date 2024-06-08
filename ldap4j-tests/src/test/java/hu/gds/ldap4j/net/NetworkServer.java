package hu.gds.ldap4j.net;

import hu.gds.ldap4j.TestContext;
import hu.gds.ldap4j.lava.Clock;
import hu.gds.ldap4j.ldap.LdapServer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NetworkServer<K, V> implements AutoCloseable, Runnable {
    public static class State<K, V> {
        public final Object lock=new Object();
        private boolean closed;
        private final Map<K, V> map=new HashMap<>();
        private final Deque<@NotNull Runnable> signals=new LinkedList<>();

        private State() {
        }

        public V await(long endNanos, K key) throws InterruptedException {
            synchronized (lock) {
                while (true) {
                    if (closed) {
                        return null;
                    }
                    if (map.containsKey(key)) {
                        return map.remove(key);
                    }
                    long delayNanos=Clock.SYSTEM_NANO_TIME.endNanosToDelayNanos(endNanos);
                    if (0L>=delayNanos) {
                        return null;
                    }
                    Clock.synchronizedWaitDelayNanos(delayNanos, lock);
                }
            }
        }

        public boolean close() {
            synchronized (lock) {
                if (closed) {
                    return false;
                }
                closed=true;
                lock.notifyAll();
                fireSignals();
                return true;
            }
        }

        private void fireSignals() {
            while (!signals.isEmpty()) {
                signals.removeFirst().run();
            }
        }

        public boolean open() {
            synchronized (lock) {
                return !closed;
            }
        }

        public void put(K key, V value) {
            synchronized (lock) {
                map.put(key, value);
                lock.notifyAll();
                fireSignals();
            }
        }
    }

    @FunctionalInterface
    interface Worker<K, V> {
        void run(
                @NotNull TestContext<NetworkTestParameters> context, @NotNull DataInputStream input,
                @NotNull DataOutputStream output, @NotNull Socket socket, @NotNull State<K, V> state)
                throws Throwable;
    }

    private class WorkerRunnable implements Runnable {
        private final @NotNull Socket socket;

        public WorkerRunnable(@NotNull Socket socket) {
            this.socket=Objects.requireNonNull(socket, "socket");
        }

        @Override
        public void run() {
            try {
                try (InputStream sis=socket.getInputStream();
                     InputStream bis=new BufferedInputStream(sis);
                     DataInputStream dis=new DataInputStream(bis);
                     OutputStream sos=socket.getOutputStream();
                     OutputStream bos=new BufferedOutputStream(sos);
                     DataOutputStream dos=new DataOutputStream(bos)) {
                    worker.run(context, dis, dos, socket, state);
                }
                finally {
                    socket.close();
                }
            }
            catch (Throwable throwable) {
                context.log().error(getClass(), throwable);
            }
        }
    }

    private final @NotNull TestContext<NetworkTestParameters> context;
    private final @NotNull Worker<K, V> worker;
    private final ServerSocket serverSocket;
    public final State<K, V> state=new State<>();

    public NetworkServer(
            @NotNull TestContext<NetworkTestParameters> context, @NotNull Worker<K, V> worker) throws Throwable {
        this(false, context, worker);
    }

    public NetworkServer(
            boolean badCertificate, @NotNull TestContext<NetworkTestParameters> context,
            @NotNull Worker<K, V> worker) throws Throwable {
        this.context=Objects.requireNonNull(context, "context");
        this.worker=Objects.requireNonNull(worker, "worker");
        if (NetworkTestParameters.Tls.USE_TLS.equals(context.parameters().tls)) {
            serverSocket=LdapServer.serverTls(badCertificate)
                    .createSSLServerSocketFactory(null)
                    .createServerSocket();
        }
        else {
            serverSocket=new ServerSocket();
        }
    }

    @Override
    public void close() throws IOException {
        if (state.close()) {
            serverSocket.close();
        }
    }

    public InetSocketAddress localAddress() {
        return (InetSocketAddress)serverSocket.getLocalSocketAddress();
    }

    @Override
    public void run() {
        try {
            try {
                while (!serverSocket.isClosed()) {
                    boolean error=true;
                    Socket socket=serverSocket.accept();
                    try {
                        Thread thread=new Thread(new WorkerRunnable(socket));
                        thread.setDaemon(true);
                        thread.start();
                        error=false;
                    }
                    finally {
                        if (error) {
                            socket.close();
                        }
                    }
                }
            }
            catch (SocketException ex) {
                assertNotNull(ex.getMessage());
                String message=ex.getMessage().toLowerCase();
                if (!(message.contains("socket closed")
                        || message.contains("socket is closed"))) {
                    throw ex;
                }
            }
        }
        catch (Throwable throwable) {
            context.log().error(getClass(), throwable);
        }
    }

    public void start() throws Throwable {
        serverSocket.bind(new InetSocketAddress(Inet4Address.getLoopbackAddress(), context.parameters().serverPort));
        Thread thread=new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }
}
