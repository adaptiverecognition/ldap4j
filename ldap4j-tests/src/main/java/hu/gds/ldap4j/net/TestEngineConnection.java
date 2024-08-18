package hu.gds.ldap4j.net;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Closeable;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.Lock;
import hu.gds.ldap4j.trampoline.EngineConnection;
import hu.gds.ldap4j.trampoline.LavaEngine;
import java.net.InetSocketAddress;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestEngineConnection implements DuplexConnection {
    private final @NotNull DuplexConnection connection;
    private final @NotNull EngineConnection engineConnection;
    private final @NotNull LavaEngine lavaEngine;
    private final @NotNull Lock lock=new Lock();
    private final @NotNull Lock.Condition lockCondition=lock.newCondition();

    public TestEngineConnection(
            @NotNull DuplexConnection connection,
            @NotNull EngineConnection engineConnection,
            @NotNull LavaEngine lavaEngine) {
        this.connection=Objects.requireNonNull(connection, "connection");
        this.engineConnection=Objects.requireNonNull(engineConnection, "engineConnection");
        this.lavaEngine=Objects.requireNonNull(lavaEngine, "lavaEngine");
    }

    @Override
    public @NotNull Lava<Void> close() {
        return Lava.finallyGet(
                connection::close,
                ()->lock.enter(
                        ()->lavaEngine.getLocked(
                                engineConnection.close(),
                                lockCondition)));
    }

    public static @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory(
            @NotNull Function<@NotNull InetSocketAddress, @NotNull Lava<@NotNull DuplexConnection>> factory,
            @NotNull Log log) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(log, "log");
        return (remoteAddress)->Closeable.wrapOrClose(
                ()->factory.apply(remoteAddress),
                (connection)->connection.localAddress()
                        .compose((localAddress)->connection.remoteAddress()
                                .compose((connectionRemoteAddress)->Lava.complete(
                                        new TestEngineConnection(
                                                connection,
                                                new EngineConnection(localAddress, connectionRemoteAddress),
                                                new LavaEngine(log))))));
    }

    @Override
    public @NotNull Lava<@NotNull Boolean> isOpenAndNotFailed() {
        return lock.enter(()->lavaEngine.getLocked(engineConnection.isOpenAndNotFailed(), lockCondition));
    }

    private <T> @NotNull Lava<T> leaveAndCatchErrorsLocked(@NotNull Supplier<@NotNull Lava<T>> unlocked) {
        Objects.requireNonNull(unlocked, "unlocked");
        return Lava.catchErrors(
                (throwable)->{
                    engineConnection.failed(throwable);
                    throw throwable;
                },
                ()->lock.leave(unlocked),
                Throwable.class);
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> localAddress() {
        return lock.enter(()->lavaEngine.getLocked(engineConnection.localAddress(), lockCondition));
    }

    @Override
    public @NotNull Lava<@Nullable ByteBuffer> read() {
        return lock.enter(()->leaveAndCatchErrorsLocked(connection::read)
                .compose((readResult)->{
                    engineConnection.addReadBuffer(readResult);
                    return lavaEngine.getLocked(engineConnection.read(), lockCondition);
                }));
    }

    @Override
    public @NotNull Lava<@NotNull InetSocketAddress> remoteAddress() {
        return lock.enter(()->lavaEngine.getLocked(engineConnection.remoteAddress(), lockCondition));
    }

    @Override
    public @NotNull Lava<Void> shutDownOutput() {
        return lock.enter(()->lavaEngine.getLocked(engineConnection.shutDownOutput(), lockCondition)
                .composeIgnoreResult(this::writeLocked));
    }

    @Override
    public @NotNull Lava<Void> write(@NotNull ByteBuffer value) {
        return lock.enter(()->lavaEngine.getLocked(engineConnection.write(value), lockCondition)
                .composeIgnoreResult(this::writeLocked));
    }

    private @NotNull Lava<Void> writeLocked() {
        return Lava.checkEndNanos("write engine data timeout")
                .composeIgnoreResult(()->{
                    @Nullable ByteBuffer data=engineConnection.removeWriteBuffer();
                    if (null==data) {
                        return leaveAndCatchErrorsLocked(connection::shutDownOutput);
                    }
                    else if (data.isEmpty()) {
                        return Lava.VOID;
                    }
                    else {
                        return leaveAndCatchErrorsLocked(()->connection.write(data))
                                .composeIgnoreResult(this::writeLocked);
                    }
                });
    }
}
