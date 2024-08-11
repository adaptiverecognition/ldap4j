package hu.gds.ldap4j.trampoline;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.JoinCallback;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.Lock;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LavaEngine extends AbstractTrampoline<LavaEngine.Context> {
    public static class Context extends AbstractContext {
        public Context(
                @NotNull String debugMagic,
                @Nullable Long endNanos,
                @NotNull Log log,
                @NotNull AbstractTrampoline<?> trampoline) {
            super(debugMagic, endNanos, log, trampoline);
        }

        @Override
        protected @NotNull hu.gds.ldap4j.lava.Context context(
                @NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            return new Context(debugMagic, endNanos, log, trampoline);
        }

        public <T> @NotNull JoinCallback<T> get(@NotNull Lava<T> lava) {
            @NotNull JoinCallback<T> callback=Callback.join(clock());
            execute(()->LavaEngine.Context.this.get(callback, lava));
            return callback;
        }
    }

    public LavaEngine(@NotNull Log log) {
        super(log);
    }

    @Override
    public @NotNull Context contextEndNanos(long endNanos) {
        return new Context(Context.class.getName(), endNanos, log, this);
    }

    public <T> @NotNull Lava<@NotNull JoinCallback<T>> get(@NotNull Lava<T> lava) {
        Objects.requireNonNull(lava, "lava");
        return Lava.context()
                .compose((context)->Lava.complete(
                        contextEndNanos(context.endNanos())
                                .get(lava)));
    }

    public <T> @NotNull Lava<T> getLocked(@NotNull Lava<T> lava, @Nullable Lock.Condition lockCondition) {
        Objects.requireNonNull(lava, "lava");
        Objects.requireNonNull(lockCondition, "lockCondition");
        return get(lava)
                .compose(new Function<>() {
                    @Override
                    public @NotNull Lava<T> apply(@NotNull JoinCallback<T> callback) throws Throwable {
                        runAll();
                        if (callback.completed()) {
                            return callback.lava();
                        }
                        return lockCondition.awaitEndNanos()
                                .composeIgnoreResult(()->apply(callback));
                    }
                });
    }

    public void runAll() throws Throwable {
        while (runOne(null)) {
        }
    }
}
