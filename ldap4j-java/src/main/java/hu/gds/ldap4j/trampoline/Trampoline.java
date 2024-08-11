package hu.gds.ldap4j.trampoline;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Lava;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Trampoline extends AbstractTrampoline<Trampoline.Context> {
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
            return new Trampoline.Context(debugMagic, endNanos, log, trampoline);
        }

        public <T> T get(
                boolean assertNoResidueIn, boolean assertNoResidueOut, @NotNull Lava<T> lava) throws Throwable {
            Objects.requireNonNull(lava, "lava");
            class Get extends Callback.AbstractSingleRunCallback<T> {
                boolean completed;
                T result;
                @Nullable Throwable throwable;

                @Override
                protected void completedImpl(T value) {
                    synchronized (trampoline.lock) {
                        completed=true;
                        result=value;
                        trampoline.lock.notifyAll();
                    }
                }

                @Override
                protected void failedImpl(@NotNull Throwable throwable) {
                    synchronized (trampoline.lock) {
                        completed=true;
                        this.throwable=throwable;
                        trampoline.lock.notifyAll();
                    }
                }

                private T get() throws Throwable {
                    synchronized (trampoline.lock) {
                        trampoline.assertNoResidueSynchronized(assertNoResidueIn);
                        execute(()->Trampoline.Context.this.get(this, lava));
                    }
                    while (true) {
                        synchronized (trampoline.lock) {
                            trampoline.checkClosedSynchronized();
                            if (completed) {
                                trampoline.assertNoResidueSynchronized(assertNoResidueOut);
                                if (null==throwable) {
                                    return result;
                                }
                                throw new RuntimeException(throwable);
                            }
                        }
                        checkEndNanos("computation timeout");
                        if (!trampoline.runOne(endNanos())) {
                            throw new IllegalStateException();
                        }
                    }
                }
            }
            return new Get()
                    .get();
        }
    }

    public Trampoline(@NotNull Log log) {
        super(log);
    }

    @Override
    public @NotNull Context contextEndNanos(long endNanos) {
        return new Context(Context.class.getName(), endNanos, log, this);
    }
}
