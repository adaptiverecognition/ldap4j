package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.ByteBuffer;
import hu.gds.ldap4j.net.ClosedException;
import hu.gds.ldap4j.net.Connection;
import hu.gds.ldap4j.net.DuplexConnection;
import hu.gds.ldap4j.net.TlsConnection;
import hu.gds.ldap4j.net.TlsSettings;
import java.io.EOFException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
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

    private LdapConnection(
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
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull TlsSettings tlsSettings) {
        return factory(
                factory,
                MessageIdGenerator.smallValues(true),
                remoteAddress,
                tlsSettings);
    }

    public static @NotNull Lava<@NotNull LdapConnection> factory(
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @NotNull MessageIdGenerator messageIdGenerator,
            @NotNull InetSocketAddress remoteAddress,
            @NotNull TlsSettings tlsSettings) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(messageIdGenerator, "messageIdGenerator");
        Objects.requireNonNull(tlsSettings, "tlsSettings");
        return Closeable.wrapOrClose(
                ()->TlsConnection.factory(factory).apply(remoteAddress),
                (connection)->{
                    if (tlsSettings.isTls()) {
                        if (tlsSettings.isStarttls()) {
                            return Closeable.wrapOrClose(
                                    ()->Lava.complete(new LdapConnection(connection, false, messageIdGenerator)),
                                    (connection2)->connection2.startTls(tlsSettings.asTls())
                                            .composeIgnoreResult(()->Lava.complete(connection2)));
                        }
                        else {
                            return connection.startTls(tlsSettings.asTls())
                                    .composeIgnoreResult(()->Lava.complete(
                                            new LdapConnection(connection, true, messageIdGenerator)));
                        }
                    }
                    else {
                        return Lava.complete(new LdapConnection(connection, false, messageIdGenerator));
                    }
                });
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

    public @NotNull MessageIdGenerator messageIdGenerator() {
        return messageIdGenerator;
    }

    public <T> @NotNull Lava<@NotNull LdapMessage<T>> readMessageChecked(
            int messageId, @NotNull MessageReader<T> messageReader) {
        return Lava.catchErrors(
                (throwable)->{
                    if ((!(throwable instanceof EOFException))
                            && (!(throwable instanceof LdapException))
                            && (!(throwable instanceof TimeoutException))) {
                        synchronized (lock) {
                            failed=true;
                        }
                    }
                    return Lava.fail(throwable);
                },
                ()->readMessage(LdapMessage.read(messageId, messageReader))
                        .compose((message)->{
                            messageReader.check(message.message());
                            return Lava.complete(message);
                        }),
                Throwable.class);
    }

    private <T> @NotNull Lava<T> readMessage(@NotNull Function<ByteBuffer.Reader, T> function) {
        return Lava.checkEndNanos(LdapConnection.class+"readMessage() timeout")
                .composeIgnoreResult(()->{
                    ByteBuffer.Reader reader=readBuffer.reader();
                    T value;
                    try {
                        value=function.apply(reader);
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
                            printWriter.printf(
                                    "error reading message, size: %,d bytes, %s%n", readBuffer.size(), throwable);
                            DERDump.hexDump(
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
                    return Lava.complete(value);
                });
    }

    public @NotNull Lava<@NotNull List<@NotNull SearchResult>> search(
            @NotNull ControlsMessage<SearchRequest> request) {
        return search(messageIdGenerator, request);
    }

    public @NotNull Lava<@NotNull List<@NotNull SearchResult>> search(
            @NotNull MessageIdGenerator messageIdGenerator,
            @NotNull ControlsMessage<SearchRequest> request) {
        return writeMessage(request, messageIdGenerator)
                .compose((messageId)->search(messageId, new ArrayList<>()));
    }

    private @NotNull Lava<@NotNull List<@NotNull SearchResult>> search(
            int messageId, @NotNull List<@NotNull SearchResult> result) {
        return readMessageChecked(messageId, SearchResult.READER)
                .compose((searchResult)->{
                    result.add(searchResult.message());
                    if (searchResult.message().isDone()) {
                        return Lava.complete(result);
                    }
                    else {
                        return search(messageId, result);
                    }
                });
    }

    public @NotNull Lava<Void> startTls(@NotNull TlsSettings.Tls tls) {
        Objects.requireNonNull(tls, "tls");
        return Lava.supplier(()->{
            if (ldaps) {
                throw new RuntimeException("cannot start tls on ldaps");
            }
            if (usingTls) {
                throw new RuntimeException("already using tls");
            }
            usingTls=true;
            return writeRequestReadResponseChecked(ExtendedRequest.START_TLS.controlsEmpty())
                    .composeIgnoreResult(()->connection().startTls(tls));
        });
    }

    /**
     * @return messageId
     */
    public <M extends Message<M>> @NotNull Lava<@NotNull Integer> writeMessage(@NotNull ControlsMessage<M> message) {
        return writeMessage(connection(), message, messageIdGenerator);
    }

    /**
     * @return messageId
     */
    public <M extends Message<M>> @NotNull Lava<@NotNull Integer> writeMessage(
            @NotNull ControlsMessage<M> message, @NotNull MessageIdGenerator messageIdGenerator) {
        return writeMessage(connection(), message, messageIdGenerator);
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
                    messageId,
                    messageIdGenerator.signKludge())
                    .write(Message::write);
            return Lava.catchErrors(
                            (throwable)->{
                                synchronized (lock) {
                                    failed=true;
                                }
                                return Lava.fail(throwable);
                            },
                            ()->connection.write(byteBuffer),
                            Throwable.class)
                    .composeIgnoreResult(()->Lava.complete(messageId));
        });
    }

    public <M extends Request<M, R>, R> @NotNull Lava<@NotNull ControlsMessage<R>> writeRequestReadResponseChecked(
            @NotNull ControlsMessage<M> request) {
        return writeMessage(request)
                .compose((messageId)->readMessageChecked(messageId, request.message().responseReader()))
                .compose((response)->Lava.complete(new ControlsMessage<>(
                        response.controls(),
                        response.message())));
    }
}
