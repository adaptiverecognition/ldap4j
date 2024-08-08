package hu.gds.ldap4j.net;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.TestContext;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Clock;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.JoinCallback;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.ldap.LdapServer;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DuplexConnectionTest {
    private static boolean contains(byte[] sub, byte[] sup) {
        for (int pi=sup.length-sub.length; 0<=pi; --pi) {
            boolean contains2=true;
            for (int bi=sub.length-1; 0<=bi; --bi) {
                if (sub[bi]!=sup[pi+bi]) {
                    contains2=false;
                    break;
                }
            }
            if (contains2) {
                return true;
            }
        }
        return false;
    }

    private static <T, U> @NotNull Pair<T, U> forkJoin(
            @NotNull TestContext<?> context, @NotNull Supplier<T> supplier0, @NotNull Supplier<U> supplier1)
            throws Throwable {
        Objects.requireNonNull(supplier0, "supplier0");
        Objects.requireNonNull(supplier1, "supplier1");
        JoinCallback<T> join0=Callback.join(context.contextHolder().clock());
        JoinCallback<U> join1=Callback.join(context.contextHolder().clock());
        Thread thread0=Thread.currentThread();
        Thread thread1=new Thread(()->{
            try {
                join1.completed(supplier1.get());
            }
            catch (Throwable throwable) {
                join1.failed(throwable);
                thread0.interrupt();
            }
        });
        try {
            thread1.setDaemon(true);
            thread1.start();
            try {
                join0.completed(supplier0.get());
            }
            catch (Throwable throwable) {
                join0.failed(throwable);
                thread1.interrupt();
            }
            Clock.threadJoinDelayNanos(context.parameters().timeoutNanos, thread1);
            T result0;
            U result1;
            try {
                result0=join0.joinDelayNanos(0L);
            }
            finally {
                result1=join1.joinDelayNanos(0L);
            }
            return new Pair<>(result0, result1);
        }
        finally {
            thread1.interrupt();
        }
    }

    private NetworkServer.Worker<Boolean, Object> noWriteIgnoreReadWorker(boolean ignoreSslHandshakeError) {
        return (context, input, output, socket, state)->{
            byte[] buf=new byte[DuplexConnection.PAGE_SIZE];
            while (state.open()) {
                long timeoutNanos=context.contextHolder().clock().endNanosToDelayNanos(context.endNanos());
                if (0>=timeoutNanos) {
                    break;
                }
                socket.setSoTimeout(Math.max(1, (int)(timeoutNanos/1_000_000L)));
                try {
                    int rr=input.read(buf);
                    if (0>rr) {
                        break;
                    }
                    boolean signal=false;
                    for (int ii=rr-1; 0<=ii; --ii) {
                        if (0!=buf[ii]) {
                            signal=true;
                            break;
                        }
                    }
                    if (signal) {
                        state.put(true, new Object());
                    }
                }
                catch (SocketException ex) {
                    if (Exceptions.isConnectionClosedException(ex)) {
                        break;
                    }
                    throw ex;
                }
                catch (SocketTimeoutException ignore) {
                }
                catch (SSLHandshakeException ex) {
                    if (ignoreSslHandshakeError) {
                        break;
                    }
                    throw ex;
                }
            }
        };
    }

    private @NotNull Lava<@NotNull ByteBuffer> readFully(
            @NotNull DuplexConnection connection, @NotNull ByteBuffer buffer, @Nullable Integer size) {
        return Lava.supplier(()->{
            if (null!=size) {
                if (buffer.size()>size) {
                    throw new RuntimeException("buffer.size() %,d > size %,d".formatted(buffer.size(), size));
                }
                if (buffer.size()==size) {
                    return Lava.complete(buffer);
                }
            }
            return connection.read()
                    .compose((readResult)->{
                        if (null==readResult) {
                            if (null==size) {
                                return Lava.complete(buffer);
                            }
                            throw new RuntimeException("buffer.size() %,d < size %,d".formatted(buffer.size(), size));
                        }
                        else {
                            return readFully(connection, buffer.append(readResult), size);
                        }
                    });
        });
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetwork")
    public void testConnectTimeout(NetworkTestParameters parameters) throws Throwable {
        final long timeoutNanos=parameters.networkTimeoutNanos();
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters)) {
            for (int ii=32; ; --ii) {
                if (0>=ii) {
                    throw new RuntimeException("should have timed out at least once");
                }
                try {
                    context.get(
                            Lava.supplier(()->{
                                long nowNanos=context.contextHolder().clock().nowNanos();
                                long endNanos=Clock.delayNanosToEndNanos(timeoutNanos, nowNanos);
                                long endNanosMax=Clock.delayNanosToEndNanos(2*timeoutNanos, nowNanos);
                                long endNanosMin=Clock.delayNanosToEndNanos(timeoutNanos/2, nowNanos);
                                return Lava.catchErrors(
                                        (timeout)->{
                                            long nowNanos2=context.contextHolder().clock().nowNanos();
                                            if (!Clock.isEndNanosInTheFuture(endNanosMax, nowNanos2)) {
                                                throw new TimeoutException();
                                            }
                                            if (Clock.isEndNanosInTheFuture(endNanosMin, nowNanos2)) {
                                                throw new TimeoutException("too early");
                                            }
                                            return Lava.VOID;
                                        },
                                        ()->Lava.endNanos(
                                                endNanos,
                                                ()->Closeable.withCloseable(
                                                        ()->context.parameters().connectionFactory(
                                                                context, AbstractTest.BLACK_HOLE, Map.of()),
                                                        (connection)->Lava.fail(new IllegalStateException()))),
                                        TimeoutException.class);
                            }));
                    break;
                }
                catch (Throwable throwable) {
                    if (null==Exceptions.findCause(NoRouteToHostException.class, throwable)) {
                        String message=throwable.toString().toLowerCase();
                        if (!message.contains("network is unreachable")) {
                            throw throwable;
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetwork")
    public void testConnectUnknownHost(NetworkTestParameters parameters) throws Throwable {
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters)) {
            context.get(
                    Lava.supplier(()->Lava.catchErrors(
                            (unknownHost)->Lava.VOID,
                            ()->Closeable.withCloseable(
                                    ()->context.parameters().connectionFactory(
                                            context, AbstractTest.UNKNOWN_HOST, Map.of()),
                                    (connection)->Lava.fail(new IllegalStateException())),
                            UnknownHostException.class)));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetwork")
    public void testParallelReadWrite(NetworkTestParameters parameters) throws Throwable {
        Random random=new Random(1234);
        byte[] clientWriteBuf=new byte[8*DuplexConnection.PAGE_SIZE];
        byte[] serverWriteBuf=new byte[128*DuplexConnection.PAGE_SIZE];
        random.nextBytes(clientWriteBuf);
        random.nextBytes(serverWriteBuf);
        byte[] clientReadBuf;
        byte[] serverReadBuf;
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters)) {
            JoinCallback<byte[]> serverReadBuf2=Callback.join(context.contextHolder().clock());
            try (NetworkServer<Void, Void> testServer=new NetworkServer<>(
                    context,
                    (context2, input, output, socket, state2)->{
                        try {
                            ByteArrayOutputStream readBuf=new ByteArrayOutputStream(clientWriteBuf.length);
                            Pair<Void, Void> pair=forkJoin(
                                    context2,
                                    ()->{
                                        output.write(serverWriteBuf);
                                        output.flush();
                                        if (!context.networkConnectionFactory().mayCloseOnEof()) {
                                            socket.shutdownOutput();
                                        }
                                        return null;
                                    },
                                    ()->{
                                        byte[] buf=new byte[DuplexConnection.PAGE_SIZE];
                                        for (int rr; 0<=(rr=input.read(buf)); ) {
                                            readBuf.write(buf, 0, rr);
                                        }
                                        return null;
                                    });
                            assertNotNull(pair);
                            assertNull(pair.first());
                            assertNull(pair.second());
                            serverReadBuf2.completed(readBuf.toByteArray());
                        }
                        catch (Throwable throwable) {
                            serverReadBuf2.failed(throwable);
                        }
                    })) {
                testServer.start();
                clientReadBuf=context.get(Closeable.withCloseable(
                        ()->context.parameters().connectionFactory(context, testServer.localAddress(), Map.of()),
                        (connection)->Lava.forkJoin(
                                        ()->readFully(
                                                connection,
                                                ByteBuffer.EMPTY,
                                                context.networkConnectionFactory().mayCloseOnEof()
                                                        ?serverWriteBuf.length
                                                        :null),
                                        ()->connection.write(ByteBuffer.create(clientWriteBuf))
                                                .composeIgnoreResult(connection::shutDownOutputSafe))
                                .compose((pair)->Lava.complete(pair.first().arrayCopy()))));
                serverReadBuf=serverReadBuf2.joinEndNanos(context.endNanos());
            }
        }
        assertArrayEquals(clientReadBuf, serverWriteBuf);
        assertArrayEquals(serverReadBuf, clientWriteBuf);
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetwork")
    public void testPlainCipherText(NetworkTestParameters parameters) throws Throwable {
        ProxyConnection.Session proxySession=new ProxyConnection.Session();
        Random random=new Random(1234L);
        byte[] request=new byte[128];
        byte[] response=new byte[128];
        random.nextBytes(request);
        random.nextBytes(response);
        AtomicBoolean supportsShutDownOutput=new AtomicBoolean(false);
        boolean tls=NetworkTestParameters.Tls.USE_TLS.equals(parameters.tls);
        boolean mayCloseOnEof;
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters);
             NetworkServer<Void, Void> testServer=new NetworkServer<>(
                     context,
                     (context2, input, output, socket, state)->{
                         byte[] request2=new byte[request.length];
                         input.readFully(request2);
                         assertArrayEquals(request, request2);
                         output.write(response);
                         output.flush();
                         if (!context.networkConnectionFactory().mayCloseOnEof()) {
                             socket.shutdownOutput();
                         }
                         while (0<=input.read()) {
                             input.skipNBytes(0L);
                         }
                     })) {
            testServer.start();
            mayCloseOnEof=context.networkConnectionFactory().mayCloseOnEof();
            context.get(Closeable.withCloseable(
                    ()->context.parameters().connectionFactory(
                            context,
                            testServer.localAddress(),
                            Map.of(),
                            ProxyConnection.wrap(proxySession)),
                    (connection)->connection.write(ByteBuffer.create(request))
                            .composeIgnoreResult(connection::supportsShutDownOutput)
                            .compose((supportsShutDownOutput2)->{
                                supportsShutDownOutput.set(supportsShutDownOutput2);
                                return connection.shutDownOutputSafe();
                            })
                            .composeIgnoreResult(()->readFully(
                                    connection,
                                    ByteBuffer.EMPTY,
                                    context.networkConnectionFactory().mayCloseOnEof()
                                            ?response.length
                                            :null))
                            .compose((readResult)->{
                                assertArrayEquals(response, readResult.arrayCopy());
                                return Lava.VOID;
                            })));
        }
        if (!supportsShutDownOutput.get()) {
            assertFalse(proxySession.supportsShutDownOutput());
        }
        if (!mayCloseOnEof) {
            assertTrue(proxySession.endOfStream());
        }
        assertEquals(proxySession.supportsShutDownOutput(), proxySession.outputShutDown());
        byte[] request2=proxySession.writes().arrayCopy();
        byte[] response2=proxySession.reads().arrayCopy();
        if (tls) {
            assertTrue(request.length<request2.length, ""+request2.length);
            assertTrue(response.length<response2.length, ""+response2.length);
            assertFalse(contains(request, request2));
            assertFalse(contains(request, response2));
            assertFalse(contains(response, request2));
            assertFalse(contains(response, response2));
        }
        else {
            assertArrayEquals(request, request2);
            assertArrayEquals(response, response2);
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetwork")
    public void testReadTimeout(NetworkTestParameters parameters) throws Throwable {
        final long timeoutNanos=parameters.networkTimeoutNanos();
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters);
             NetworkServer<Boolean, Object> testServer=new NetworkServer<>(
                     context,
                     noWriteIgnoreReadWorker(false))) {
            testServer.start();
            context.get(Closeable.withCloseable(
                    ()->context.parameters().connectionFactory(
                            context,
                            testServer.localAddress(),
                            Map.of()),
                    (connection)->{
                        long nowNanos=context.contextHolder().clock().nowNanos();
                        long endNanosSoft=Clock.delayNanosToEndNanos(timeoutNanos, nowNanos);
                        long endNanosHard=Clock.delayNanosToEndNanos(2*timeoutNanos, nowNanos);
                        return Lava.endNanos(
                                endNanosSoft,
                                new Supplier<Lava<Void>>() {
                                    @Override
                                    public @NotNull Lava<Void> get() throws Throwable {
                                        return loop(
                                                connection, endNanosHard, endNanosSoft, true, ByteBuffer.EMPTY);
                                    }

                                    private @NotNull Lava<Void> loop(
                                            @NotNull DuplexConnection connection, long endNanosHard, long endNanosSoft,
                                            boolean first, ByteBuffer readResult) throws Throwable {
                                        long nowNanos=context.contextHolder().clock().nowNanos();
                                        if (null==readResult) {
                                            throw new EOFException();
                                        }
                                        if (!readResult.isEmpty()) {
                                            throw new RuntimeException(readResult.toString());
                                        }
                                        if (!Clock.isEndNanosInTheFuture(endNanosHard, nowNanos)) {
                                            throw new TimeoutException();
                                        }
                                        if (!Clock.isEndNanosInTheFuture(endNanosSoft, nowNanos)) {
                                            if (first) {
                                                throw new TimeoutException();
                                            }
                                            return Lava.VOID;
                                        }
                                        return connection.read()
                                                .compose((readResult2)->loop(
                                                        connection, endNanosHard, endNanosSoft,
                                                        false, readResult2));
                                    }
                                });
                    }));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetwork")
    public void testRequestResponse(NetworkTestParameters parameters) throws Throwable {
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters);
             NetworkServer<Void, Void> testServer=new NetworkServer<>(
                     context,
                     (context2, input, output, socket, state)->{
                         try {
                             while (true) {
                                 long aa=input.readLong();
                                 if (-1L==aa) {
                                     break;
                                 }
                                 long bb=input.readLong();
                                 long cc=aa+bb;
                                 output.writeLong(cc);
                                 output.flush();
                             }
                         }
                         catch (EOFException ignore) {
                         }
                         output.writeLong(-1L);
                         output.flush();
                     })) {
            testServer.start();
            context.get(Closeable.withCloseable(
                    ()->context.parameters().connectionFactory(
                            context,
                            testServer.localAddress(),
                            Map.of()),
                    new Function<DuplexConnection, Lava<Void>>() {
                        @Override
                        public @NotNull Lava<Void> apply(@NotNull DuplexConnection connection) {
                            return loop(connection, 0L, 1L, 55L, 10L, new ReadBuffer());
                        }

                        private @NotNull Lava<Void> loop(
                                @NotNull DuplexConnection connection, long aa, long bb, long ee, long nn,
                                @NotNull ReadBuffer readBuffer) {
                            context.assertSize(0);
                            if (0L>=nn) {
                                assertEquals(aa, ee);
                                assertTrue(readBuffer.isEmpty());
                                return connection.supportsShutDownOutput()
                                        .compose((supportsShutDownOutput)->supportsShutDownOutput
                                                ?connection.shutDownOutput()
                                                :connection.write(ByteBuffer.createLong(-1L)))
                                        .composeIgnoreResult(()->readBuffer.readLong(connection))
                                        .compose((readResult)->{
                                            assertEquals(Long.valueOf(-1L), readResult);
                                            context.assertSize(0);
                                            return connection.readNonEmpty();
                                        })
                                        .compose((readResult)->{
                                            assertNull(readResult);
                                            context.assertSize(0);
                                            return Lava.addDebugMagic("useless", ()->Lava.VOID);
                                        });
                            }
                            return connection.write(ByteBuffer.createLong(aa).appendLong(bb))
                                    .composeIgnoreResult(()->{
                                        context.assertSize(0);
                                        return readBuffer.readLong(connection);
                                    })
                                    .compose((cc)->loop(connection, bb, cc, ee, nn-1, readBuffer));
                        }
                    }));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetworkUseTls")
    public void testTlsCloseHandshakeTimeout(NetworkTestParameters parameters) throws Throwable {
        final long timeoutNanos=parameters.networkTimeoutNanos();
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters);
             NetworkServer<Boolean, Object> testServer=new NetworkServer<>(
                     context,
                     noWriteIgnoreReadWorker(true))) {
            testServer.start();
            ProxyConnection.Session proxySession=new ProxyConnection.Session();
            context.get(Closeable.withCloseable(
                    ()->context.parameters().connectionFactory(
                            context,
                            testServer.localAddress(),
                            Map.of(),
                            ProxyConnection.wrap(proxySession)),
                    (connection)->{
                        proxySession.mode(ProxyConnection.Mode.TIMEOUT_WRITE);
                        long nowNanos=context.contextHolder().clock().nowNanos();
                        long endNanos=Clock.delayNanosToEndNanos(timeoutNanos, nowNanos);
                        long endNanosMax=Clock.delayNanosToEndNanos(2*timeoutNanos, nowNanos);
                        long endNanosMin=Clock.delayNanosToEndNanos(timeoutNanos/2, nowNanos);
                        return Lava.catchErrors(
                                (timeout)->{
                                    long nowNanos2=context.contextHolder().clock().nowNanos();
                                    if (!Clock.isEndNanosInTheFuture(endNanosMax, nowNanos2)) {
                                        throw new TimeoutException();
                                    }
                                    if (Clock.isEndNanosInTheFuture(endNanosMin, nowNanos2)) {
                                        throw new TimeoutException("too early");
                                    }
                                    return Lava.VOID;
                                },
                                ()->Lava.endNanos(endNanos, ()->connection.supportsShutDownOutput()
                                        .compose((supportsShutDownOutput)->{
                                            assertTrue(supportsShutDownOutput);
                                            return connection.shutDownOutput()
                                                    .composeIgnoreResult(()->Lava.fail(new IllegalStateException()));
                                        })),
                                TimeoutException.class);
                    }));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetworkUseTls")
    public void testTlsConnectHandshakeTimeout(NetworkTestParameters parameters) throws Throwable {
        final long timeoutNanos=parameters.networkTimeoutNanos();
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters);
             NetworkServer<Boolean, Object> testServer=new NetworkServer<>(
                     context,
                     noWriteIgnoreReadWorker(true))) {
            testServer.start();
            context.get(
                    Lava.supplier(()->{
                        long nowNanos=context.contextHolder().clock().nowNanos();
                        long endNanos=Clock.delayNanosToEndNanos(timeoutNanos, nowNanos);
                        long endNanosMax=Clock.delayNanosToEndNanos(2*timeoutNanos, nowNanos);
                        long endNanosMin=Clock.delayNanosToEndNanos(timeoutNanos/2, nowNanos);
                        return Lava.catchErrors(
                                (timeout)->{
                                    long nowNanos2=context.contextHolder().clock().nowNanos();
                                    if (!Clock.isEndNanosInTheFuture(endNanosMax, nowNanos2)) {
                                        throw new TimeoutException();
                                    }
                                    if (Clock.isEndNanosInTheFuture(endNanosMin, nowNanos2)) {
                                        throw new TimeoutException("too early");
                                    }
                                    return Lava.VOID;
                                },
                                ()->Lava.endNanos(
                                        endNanos,
                                        ()->Closeable.withCloseable(
                                                ()->context.parameters().connectionFactory(
                                                        context,
                                                        testServer.localAddress(),
                                                        Map.of(),
                                                        ProxyConnection.wrap(new ProxyConnection.Session(
                                                                ProxyConnection.Mode.DROP_WRITE))),
                                                (connection)->Lava.fail(new IllegalStateException()))),
                                TimeoutException.class);
                    }));
        }
    }

    private void testTlsRenegotiation(
            boolean explicitTlsRenegotiation, NetworkTestParameters parameters) throws Throwable {
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters)) {
            class TlsRenegotiationTest {
                private final @NotNull JavaAsyncChannelConnection.Server server;

                public TlsRenegotiationTest(@NotNull JavaAsyncChannelConnection.Server server) {
                    this.server=Objects.requireNonNull(server, "server");
                }

                private @NotNull Lava<Void> accept() {
                    return Closeable.withCloseable(
                            server::acceptNotNull,
                            (connection)->server(new TlsConnection(connection)));
                }

                public @NotNull Lava<Void> client(@NotNull TlsConnection connection) {
                    return connection.write(ByteBuffer.create((byte)1))
                            .composeIgnoreResult(()->{
                                if (explicitTlsRenegotiation) {
                                    return Lava.catchErrors(
                                            (throwable)->connection.restartTlsHandshake(),
                                            ()->connection.readNonEmpty()
                                                    .composeIgnoreResult(()->Lava.fail(
                                                            new RuntimeException("should have failed"))),
                                            TlsHandshakeRestartNeededException.class);
                                }
                                else {
                                    return Lava.VOID;
                                }
                            })
                            .composeIgnoreResult(connection::readNonEmpty)
                            .compose((readResult)->{
                                assertNotNull(readResult);
                                assertArrayEquals(new byte[]{2}, readResult.arrayCopy());
                                return connection.write(ByteBuffer.create((byte)3));
                            });
                }

                private @NotNull Lava<Void> connect() {
                    return server.localAddress()
                            .compose((localAddress)->Closeable.withCloseable(
                                    ()->context.parameters().connectionFactory(
                                            context,
                                            explicitTlsRenegotiation,
                                            localAddress,
                                            Map.of()),
                                    (connection)->client((TlsConnection)connection)));
                }

                public @NotNull Lava<Void> server(@NotNull TlsConnection connection) throws Throwable {
                    @NotNull TlsConnection tlsConnection=new TlsConnection(connection);
                    return tlsConnection.startTlsHandshake(LdapServer.serverTls(false))
                            .composeIgnoreResult(tlsConnection::readNonEmpty)
                            .compose((readResult)->{
                                assertNotNull(readResult);
                                assertArrayEquals(new byte[]{1}, readResult.arrayCopy());
                                return tlsConnection.restartTlsHandshake();
                            })
                            .composeIgnoreResult(()->tlsConnection.write(ByteBuffer.create((byte)2)))
                            .composeIgnoreResult(tlsConnection::readNonEmpty)
                            .compose((readResult)->{
                                assertNotNull(readResult);
                                assertArrayEquals(new byte[]{3}, readResult.arrayCopy());
                                return tlsConnection.close();
                            });
                }

                public @NotNull Lava<Void> test() {
                    return Lava.forkJoin(this::accept, this::connect)
                            .composeIgnoreResult(()->Lava.VOID);
                }
            }
            context.get(Closeable.withCloseable(
                    ()->JavaAsyncChannelConnection.Server.factory(
                            null,
                            1,
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                            Map.of()),
                    (server)->new TlsRenegotiationTest(server)
                            .test()));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetworkUseTls")
    public void testTlsRenegotiationExplicit(NetworkTestParameters parameters) throws Throwable {
        testTlsRenegotiation(true, parameters);
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetworkUseTls")
    public void testTlsRenegotiationImplicit(NetworkTestParameters parameters) throws Throwable {
        testTlsRenegotiation(false, parameters);
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetwork")
    public void testTlsSession(NetworkTestParameters parameters) throws Throwable {
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters);
             NetworkServer<Void, Void> testServer=new NetworkServer<>(
                     context,
                     (context2, input, output, socket, state)->{
                         output.write(-1);
                         output.flush();
                     })) {
            testServer.start();
            context.get(Closeable.withCloseable(
                    ()->context.parameters().connectionFactory(
                            context,
                            testServer.localAddress(),
                            Map.of()),
                    new Function<DuplexConnection, Lava<Void>>() {
                        @Override
                        public @NotNull Lava<Void> apply(@NotNull DuplexConnection connection) {
                            TlsConnection tlsConnection=(connection instanceof TlsConnection connection2)
                                    ?connection2
                                    :null;
                            return readFully(connection, ByteBuffer.EMPTY, 1)
                                    .compose((readResult)->{
                                        assertArrayEquals(new byte[]{-1}, readResult.arrayCopy());
                                        return (null==tlsConnection)
                                                ?(Lava.complete(null))
                                                :(tlsConnection.tlsSession())
                                                .compose((tlsSession)->{
                                                    assertEquals(
                                                            NetworkTestParameters.Tls.USE_TLS.equals(parameters.tls),
                                                            null!=tlsSession);
                                                    return Lava.VOID;
                                                });
                                    });
                        }
                    }));
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetworkUseTls")
    public void testTrust(NetworkTestParameters parameters) throws Throwable {
        testTrust(false, parameters, false, false);
        testTrust(false, parameters, false, true);
        testTrust(false, parameters, true, false);
        testTrust(false, parameters, true, true);
        testTrust(true, parameters, false, false);
        testTrust(true, parameters, false, true);
        testTrust(true, parameters, true, false);
        testTrust(true, parameters, true, true);
    }

    private void testTrust(
            boolean clientBad, NetworkTestParameters parameters, boolean serverBad, boolean verifyHostname)
            throws Throwable {
        boolean trust=(clientBad==serverBad) && ((!serverBad) || (!verifyHostname));
        try {
            try (TestContext<NetworkTestParameters> context=TestContext.create(parameters);
                 NetworkServer<Void, Void> testServer=new NetworkServer<>(
                         serverBad,
                         context,
                         (context2, input, output, socket, state)->{
                             assertEquals(-1L, input.readLong());
                             output.writeLong(0);
                             output.flush();
                             if (!context.networkConnectionFactory().mayCloseOnEof()) {
                                 socket.shutdownOutput();
                             }
                             while (0<=input.read()) {
                                 input.skipNBytes(0L);
                             }
                         })) {
                testServer.start();
                context.get(Closeable.withCloseable(
                        ()->context.parameters().connectionFactory(
                                clientBad,
                                context,
                                testServer.localAddress(),
                                Map.of(),
                                verifyHostname,
                                Lava::complete),
                        (connection)->connection.write(ByteBuffer.createLong(-1L))
                                .composeIgnoreResult(connection::shutDownOutputSafe)
                                .composeIgnoreResult(()->
                                        readFully(
                                                connection,
                                                ByteBuffer.EMPTY,
                                                context.networkConnectionFactory().mayCloseOnEof()
                                                        ?8
                                                        :null))
                                .compose((readResult)->{
                                    assertArrayEquals(
                                            ByteBuffer.createLong(0L).arrayCopy(),
                                            readResult.arrayCopy());
                                    return Lava.VOID;
                                })));
            }
            if (!trust) {
                fail("shouldn't be trusted");
            }
        }
        catch (Throwable throwable) {
            if (trust) {
                throw throwable;
            }
            boolean sslHandshake=false;
            for (Throwable throwable2=throwable; null!=throwable2; throwable2=throwable2.getCause()) {
                if (throwable2 instanceof SSLHandshakeException) {
                    sslHandshake=true;
                    break;
                }
            }
            if (!sslHandshake) {
                throw throwable;
            }
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.net.NetworkTestParameters#streamNetwork")
    public void testWriteTimeout(NetworkTestParameters parameters) throws Throwable {
        final long timeoutNanos=parameters.networkTimeoutNanos();
        AtomicInteger sentPages=new AtomicInteger();
        try (TestContext<NetworkTestParameters> context=TestContext.create(parameters);
             NetworkServer<Boolean, Object> testServer=new NetworkServer<>(
                     context,
                     (context2, input, output, socket, state)->{
                         if (socket instanceof SSLSocket sslSocket) {
                             sslSocket.startHandshake();
                         }
                         socket.setReceiveBufferSize(1024);
                         while (state.open() && context.contextHolder().clock().isEndNanosInTheFuture(context2.endNanos())) {
                             Object object=state.await(context2.endNanos(), false);
                             if (null!=object) {
                                 break;
                             }
                         }
                     })) {
            testServer.start();
            context.get(Closeable.withCloseable(
                    ()->context.parameters().connectionFactory(
                            context,
                            testServer.localAddress(),
                            Map.of(StandardSocketOptions.SO_SNDBUF, 1024)),
                    (connection)->{
                        long nowNanos=context.contextHolder().clock().nowNanos();
                        long endNanosSoft=Clock.delayNanosToEndNanos(timeoutNanos, nowNanos);
                        long endNanosHard=Clock.delayNanosToEndNanos(2*timeoutNanos, nowNanos);
                        return Lava.endNanos(
                                        endNanosSoft,
                                        new Supplier<Lava<Void>>() {
                                            @Override
                                            public Lava<Void> get() throws Throwable {
                                                return loop(connection, endNanosHard, endNanosSoft);
                                            }

                                            private Lava<Void> loop(
                                                    DuplexConnection connection, long endNanosHard,
                                                    long endNanosSoft) throws Throwable {
                                                long nowNanos=context.contextHolder().clock().nowNanos();
                                                if (!Clock.isEndNanosInTheFuture(endNanosHard, nowNanos)) {
                                                    throw new TimeoutException();
                                                }
                                                if (!Clock.isEndNanosInTheFuture(endNanosSoft, nowNanos)) {
                                                    return Lava.VOID;
                                                }
                                                return Lava.catchErrors(
                                                                (timeout)->{
                                                                    long nowNanos2=context.contextHolder()
                                                                            .clock().nowNanos();
                                                                    if (!Clock.isEndNanosInTheFuture(
                                                                            endNanosHard, nowNanos2)) {
                                                                        throw new TimeoutException(
                                                                                "hard timeout, %s".formatted(timeout));
                                                                    }
                                                                    if (Clock.isEndNanosInTheFuture(
                                                                            endNanosSoft, nowNanos2)) {
                                                                        throw new TimeoutException(
                                                                                "too early, %s".formatted(timeout));
                                                                    }
                                                                    return Lava.complete(true);
                                                                },
                                                                ()->connection.write(ByteBuffer.create(new byte[
                                                                                DuplexConnection.PAGE_SIZE]))
                                                                        .composeIgnoreResult(()->{
                                                                            sentPages.incrementAndGet();
                                                                            return Lava.complete(false);
                                                                        }),
                                                                TimeoutException.class)
                                                        .compose((timeout)->{
                                                            if (timeout) {
                                                                return Lava.VOID;
                                                            }
                                                            return loop(connection, endNanosHard, endNanosSoft);
                                                        });
                                            }
                                        })
                                .composeIgnoreResult(()->{
                                    testServer.state.put(false, new Object());
                                    return Lava.checkEndNanos(
                                                    DuplexConnectionTest.class
                                                            +".testWriteTimeout() timeout")
                                            .composeIgnoreResult(()->Lava.VOID);
                                });
                    }));
        }
        int sentPages2=sentPages.get();
        int maxPages=AbstractTest.isWindows()?200:9;
        assertTrue((0<sentPages2) && (maxPages>sentPages2), Integer.toString(sentPages2));
    }
}
