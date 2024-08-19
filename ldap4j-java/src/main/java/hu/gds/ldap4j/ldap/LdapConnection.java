package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Consumer;
import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.ByteBuffer;
import hu.gds.ldap4j.net.ClosedException;
import hu.gds.ldap4j.net.Connection;
import hu.gds.ldap4j.net.DuplexConnection;
import hu.gds.ldap4j.net.TlsConnection;
import hu.gds.ldap4j.net.TlsHandshakeRestartNeededException;
import hu.gds.ldap4j.net.TlsSettings;
import java.io.EOFException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LdapConnection implements Connection {
    private @Nullable TlsConnection connection;
    private boolean failed;
    private final boolean ldaps;
    private final Object lock=new Object();
    private final @NotNull MessageIdGenerator messageIdGenerator;
    private ByteBuffer readBuffer=ByteBuffer.EMPTY;
    private boolean usingTls;

    public LdapConnection(
            @NotNull TlsConnection connection, boolean ldaps, @NotNull MessageIdGenerator messageIdGenerator) {
        this.connection=Objects.requireNonNull(connection, "connection");
        this.ldaps=ldaps;
        this.messageIdGenerator=Objects.requireNonNull(messageIdGenerator, "messageIdGenerator");
        usingTls=ldaps;
    }

    @Override
    public @NotNull Lava<Void> close() {
        return Lava.supplier(()->{
            TlsConnection connection2;
            boolean failed2;
            synchronized (lock) {
                connection2=connection;
                connection=null;
                failed2=failed;
                if (null==connection2) {
                    return Lava.VOID;
                }
            }
            return connection2.finallyClose(
                    ()->{
                        if (failed2) {
                            return Lava.VOID;
                        }
                        return connection2.isOpenAndNotFailed()
                                .compose((openAndNotFailed)->{
                                    if (!openAndNotFailed) {
                                        return Lava.VOID;
                                    }
                                    return Lava.catchErrors(
                                            (throwable)->{
                                                if (Exceptions.isConnectionClosedException(throwable)) {
                                                    return Lava.VOID;
                                                }
                                                return Lava.fail(throwable);
                                            },
                                            ()->writeMessage(
                                                    connection2,
                                                    new UnbindRequest()
                                                            .controlsEmpty(),
                                                    messageIdGenerator)
                                                    .composeIgnoreResult(connection2::shutDownOutputSafe),
                                            Throwable.class);
                                });
                    });
        });
    }

    private @NotNull TlsConnection connection() {
        @Nullable TlsConnection connection2;
        synchronized (lock) {
            connection2=connection;
            if (null==connection2) {
                throw new ClosedException();
            }
            if (failed) {
                throw new RuntimeException("ldap connection failed");
            }
        }
        return connection2;
    }

    public static @NotNull Lava<@NotNull LdapConnection> factory(
            boolean explicitTlsRenegotiation,
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @Nullable Executor handshakeExecutor,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull TlsSettings tlsSettings) {
        return factory(
                explicitTlsRenegotiation,
                factory,
                handshakeExecutor,
                MessageIdGenerator.smallValues(),
                remoteAddress,
                tlsSettings);
    }

    public static @NotNull Lava<@NotNull LdapConnection> factory(
            boolean explicitTlsRenegotiation,
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @Nullable Executor handshakeExecutor,
            @NotNull MessageIdGenerator messageIdGenerator,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull TlsSettings tlsSettings) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(messageIdGenerator, "messageIdGenerator");
        Objects.requireNonNull(tlsSettings, "tlsSettings");
        return Closeable.wrapOrClose(
                ()->TlsConnection.factory(explicitTlsRenegotiation, factory).apply(remoteAddress),
                (connection)->{
                    if (tlsSettings.isTls()) {
                        if (tlsSettings.isStarttls()) {
                            return Closeable.wrapOrClose(
                                    ()->Lava.complete(new LdapConnection(connection, false, messageIdGenerator)),
                                    (connection2)->connection2.startTls(handshakeExecutor, tlsSettings.asTls())
                                            .composeIgnoreResult(()->Lava.complete(connection2)));
                        }
                        else {
                            return connection.startTlsHandshake(handshakeExecutor, tlsSettings.asTls())
                                    .composeIgnoreResult(()->Lava.complete(
                                            new LdapConnection(connection, true, messageIdGenerator)));
                        }
                    }
                    else {
                        return Lava.complete(new LdapConnection(connection, false, messageIdGenerator));
                    }
                });
    }

    public static @NotNull Lava<@NotNull LdapConnection> factory(
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @Nullable Executor handshakeExecutor,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull TlsSettings tlsSettings) {
        return factory(
                TlsConnection.DEFAULT_EXPLICIT_TLS_RENEGOTIATION,
                factory,
                handshakeExecutor,
                remoteAddress,
                tlsSettings);
    }

    public static @NotNull Lava<@NotNull LdapConnection> factory(
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull TlsSettings tlsSettings) {
        return factory(
                factory,
                null,
                remoteAddress,
                tlsSettings);
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
        return Lava.supplier(()->{
            @Nullable TlsConnection connection2;
            synchronized (lock) {
                connection2=connection;
                if (null==connection2) {
                    return Lava.complete(false);
                }
                if (failed) {
                    return Lava.complete(false);
                }
            }
            return connection2.isOpenAndNotFailed();
        });
    }

    public @NotNull Lava<@NotNull InetSocketAddress> localAddress() {
        return Lava.supplier(()->connection().localAddress());
    }

    public @NotNull MessageIdGenerator messageIdGenerator() {
        return messageIdGenerator;
    }

    private <T> @NotNull Lava<@NotNull Lava<T>> readMessage(
            @NotNull Function<ByteBuffer.@NotNull Reader, @NotNull Lava<T>> function) {
        return Lava.checkEndNanos(LdapConnection.class+"readMessage() timeout")
                .composeIgnoreResult(()->{
                    ByteBuffer.Reader reader=readBuffer.reader();
                    @NotNull Lava<T> checkPhase;
                    try {
                        checkPhase=function.apply(reader);
                    }
                    catch (EOFException ex) {
                        return connection().read()
                                .compose((readResult)->{
                                    if (null==readResult) {
                                        throw new EOFException();
                                    }
                                    else {
                                        readBuffer=readBuffer.append(readResult);
                                        return readMessage(function);
                                    }
                                });
                    }
                    catch (ExtendedLdapException ex) {
                        throw ex;
                    }
                    catch (Throwable throwable) {
                        try (StringWriter writer=new StringWriter();
                             PrintWriter printWriter=new PrintWriter(writer)) {
                            BERDump.hexDump(
                                    readBuffer,
                                    "        ",
                                    32,
                                    80,
                                    8,
                                    true,
                                    true,
                                    printWriter);
                            throw new RuntimeException(writer.toString(), throwable);
                        }
                    }
                    readBuffer=reader.readReaminingByteBuffer();
                    return Lava.complete(checkPhase);
                });
    }

    public <T> @NotNull Lava<@NotNull LdapMessage<@NotNull T>> readMessageChecked(
            int messageId, @NotNull MessageReader<T> messageReader) {
        return readMessageCheckedParallel(Map.of(messageId, messageReader.parallel(Function::identity))::get);
    }

    public <T> @NotNull Lava<T> readMessageCheckedParallel(
            @NotNull Function<@NotNull Integer, @Nullable ParallelMessageReader<?, T>> messageReadersByMessageId) {
        Objects.requireNonNull(messageReadersByMessageId, "messageReadersByMessageId");
        return Lava.catchErrors(
                (throwable)->{
                    if ((!(throwable instanceof EOFException))
                            && (!(throwable instanceof LdapException))
                            && (!(throwable instanceof TimeoutException))
                            && (!(throwable instanceof TlsHandshakeRestartNeededException))) {
                        synchronized (lock) {
                            failed=true;
                        }
                    }
                    return Lava.fail(throwable);
                },
                ()->readMessage(LdapMessage.readCheckedParallel(messageReadersByMessageId))
                        .compose(Function::identity),
                Throwable.class);
    }

    public @NotNull Lava<@NotNull InetSocketAddress> remoteAddress() {
        return Lava.supplier(()->connection().remoteAddress());
    }

    public @NotNull Lava<Void> restartTlsHandshake() {
        return Lava.supplier(()->connection().restartTlsHandshake());
    }

    public @NotNull Lava<Void> restartTlsHandshake(@NotNull Consumer<@NotNull SSLEngine> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return Lava.supplier(()->connection().restartTlsHandshake(consumer));
    }

    public @NotNull Lava<@NotNull List<@NotNull ControlsMessage<SearchResult>>> search(
            @NotNull ControlsMessage<SearchRequest> request) {
        return search(messageIdGenerator, request);
    }

    public @NotNull Lava<@NotNull List<@NotNull ControlsMessage<SearchResult>>> search(
            @NotNull MessageIdGenerator messageIdGenerator,
            @NotNull ControlsMessage<SearchRequest> request) {
        return writeMessage(request, messageIdGenerator)
                .compose((messageId)->search(messageId, new ArrayList<>()));
    }

    private @NotNull Lava<@NotNull List<@NotNull ControlsMessage<SearchResult>>> search(
            int messageId, @NotNull List<@NotNull ControlsMessage<SearchResult>> result) {
        return readMessageChecked(messageId, SearchResult.READER)
                .compose((searchResult)->{
                    result.add(new ControlsMessage<>(searchResult.controls(), searchResult.message()));
                    if (searchResult.message().isDone()) {
                        return Lava.complete(result);
                    }
                    else {
                        return search(messageId, result);
                    }
                });
    }

    public @NotNull Lava<Void> startTls(
            @NotNull Function<@Nullable InetSocketAddress, @NotNull SSLEngine> function,
            @Nullable Executor handshakeExecutor) {
        Objects.requireNonNull(function, "function");
        return Lava.supplier(()->{
            if (ldaps) {
                throw new RuntimeException("cannot start tls on ldaps");
            }
            if (usingTls) {
                throw new RuntimeException("already using tls");
            }
            usingTls=true;
            return writeRequestReadResponseChecked(ExtendedRequest.START_TLS.controlsEmpty())
                    .composeIgnoreResult(()->connection().startTlsHandshake(function, handshakeExecutor));
        });
    }

    public @NotNull Lava<Void> startTls(@Nullable Executor handshakeExecutor, @NotNull TlsSettings.Tls tls) {
        return startTls(tls::createSSLEngine, handshakeExecutor);
    }

    public @NotNull Lava<@Nullable SSLSession> tlsSession() {
        return Lava.supplier(()->connection()
                .tlsSession());
    }

    /**
     * @return messageId
     */
    public <M extends Message<M>> @NotNull Lava<@NotNull Integer> writeMessage(@NotNull ControlsMessage<M> message) {
        return Lava.supplier(()->writeMessage(connection(), message, messageIdGenerator));
    }

    /**
     * @return messageId
     */
    public <M extends Message<M>> @NotNull Lava<@NotNull Integer> writeMessage(
            @NotNull ControlsMessage<M> message, @NotNull MessageIdGenerator messageIdGenerator) {
        return Lava.supplier(()->writeMessage(connection(), message, messageIdGenerator));
    }

    /**
     * @return messageId
     */
    private <M extends Message<M>> @NotNull Lava<@NotNull Integer> writeMessage(
            @NotNull TlsConnection connection, @NotNull ControlsMessage<M> message,
            @NotNull MessageIdGenerator messageIdGenerator) {
        return Lava.supplier(()->{
            int messageId=messageIdGenerator.next();
            ByteBuffer byteBuffer=new LdapMessage<>(
                    message.controls(),
                    message.message(),
                    messageId)
                    .write(Message::write);
            return Lava.catchErrors(
                            (throwable)->{
                                if (!(throwable instanceof TlsHandshakeRestartNeededException)) {
                                    synchronized (lock) {
                                        failed=true;
                                    }
                                }
                                return Lava.fail(throwable);
                            },
                            ()->connection.write(byteBuffer),
                            Throwable.class)
                    .composeIgnoreResult(()->Lava.complete(messageId));
        });
    }

    public <M extends Request<M, R>, R extends Response>
    @NotNull Lava<@NotNull ControlsMessage<R>> writeRequestReadResponseChecked(
            @NotNull ControlsMessage<M> request) {
        return writeMessage(request)
                .compose((messageId)->readMessageChecked(messageId, request.message().responseReader()))
                .compose((response)->Lava.complete(new ControlsMessage<>(
                        response.controls(),
                        response.message())));
    }
}
