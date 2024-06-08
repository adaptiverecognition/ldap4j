package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Context extends Executor {
    abstract class Abstract implements Context {
        private final @NotNull Clock clock;
        private final @NotNull String debugMagic;
        private final @Nullable Long endNanos;
        private final @NotNull Log log;

        public Abstract(@NotNull Clock clock, @NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            this.clock=Objects.requireNonNull(clock, "clock");
            this.debugMagic=Objects.requireNonNull(debugMagic, "debugMagic");
            this.endNanos=endNanos;
            this.log=Objects.requireNonNull(log, "log");
        }

        @Override
        public @NotNull Clock clock() {
            return clock;
        }

        protected abstract Context context(
                @NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) throws Throwable;

        @Override
        public @NotNull String debugMagic() {
            return debugMagic;
        }

        @Override
        public @NotNull Context debugMagic(@NotNull String debugMagic) throws Throwable {
            Objects.requireNonNull(debugMagic, "debugMagic");
            if (this.debugMagic.equals(debugMagic)) {
                return this;
            }
            return context(debugMagic, endNanos, log);
        }

        @Override
        public long endNanos() {
            return (null==endNanos)
                    ?clock.delayNanosToEndNanos(Long.MAX_VALUE)
                    :endNanos;
        }

        @Override
        public Context endNanos(long endNanos) throws Throwable {
            if ((null!=this.endNanos)
                    && (0>clock.compareEndNanos(this.endNanos, endNanos))) {
                return this;
            }
            return context(debugMagic, endNanos, log);
        }

        @Override
        public @NotNull Log log() {
            return log;
        }

        @Override
        public String toString() {
            return getClass().getName()+"(debugMagic: "+debugMagic+", endNanos: "+endNanos+", log: "+log+")";
        }
    }

    class Apply<T, U> implements Runnable {
        private final @NotNull Context context;
        private final @NotNull Function<T, Lava<U>> function;
        private final @NotNull Callback<U> callback;
        private final T value;

        public Apply(
                @NotNull Callback<U> callback,
                @NotNull Context context,
                @NotNull Function<T, Lava<U>> function,
                T value) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
            this.function=Objects.requireNonNull(function, "function");
            this.value=value;
        }

        @Override
        public void run() {
            Callback<U> callback2=callback;
            try {
                Lava<U> newSupplier=Objects.requireNonNull(function.apply(value), "newSupplier");
                callback2=callback2.singleRun();
                context.get(callback2, newSupplier);
            }
            catch (Throwable throwable) {
                callback2.failed(throwable);
            }
        }

        @Override
        public String toString() {
            return "Apply(callback: "+callback+", executor: "+context+", function: "+function+", value: "+value+")";
        }
    }

    class Complete<T> implements Runnable {
        private final @NotNull Callback<T> callback;
        private final T value;

        public Complete(@NotNull Callback<T> callback, T value) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.value=value;
        }

        @Override
        public void run() {
            callback.completed(value);
        }

        @Override
        public String toString() {
            return "Complete(callback: "+callback+", value: "+value+")";
        }
    }

    class Fail<T> implements Runnable {
        private final @NotNull Callback<T> callback;
        private final @NotNull Throwable throwable;

        public Fail(@NotNull Callback<T> callback, @NotNull Throwable throwable) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.throwable=Objects.requireNonNull(throwable, "throwable");
        }

        @Override
        public void run() {
            callback.failed(throwable);
        }

        @Override
        public String toString() {
            return "Fail(callback: "+callback+", throwable: "+throwable+")";
        }
    }

    class Get<T> implements Runnable {
        private final @NotNull Context context;
        private final @NotNull Callback<T> callback;
        private final @NotNull Lava<T> supplier;

        public Get(
                @NotNull Callback<T> callback,
                @NotNull Context context,
                @NotNull Lava<T> supplier) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
            this.supplier=Objects.requireNonNull(supplier, "supplier");
        }

        @Override
        public void run() {
            Callback<T> callback2=callback;
            try {
                callback2=callback2.singleRun();
                supplier.get(callback2, context);
            }
            catch (Throwable throwable) {
                callback2.failed(throwable);
            }
        }

        @Override
        public String toString() {
            return "Get(callback: "+callback+", context: "+context+", supplier="+supplier+")";
        }
    }

    default @NotNull Context addDebugMagic(@NotNull String debugMagic) throws Throwable {
        String debugMagic2=debugMagic();
        if (debugMagic2.isEmpty()) {
            debugMagic2=debugMagic;
        }
        else {
            debugMagic2=debugMagic2+" / "+debugMagic;
        }
        return debugMagic(debugMagic2);
    }

    default <T, U> void apply(
            @NotNull Callback<U> callback, @NotNull Function<T, Lava<U>> function, T value) {
        execute(new Apply<>(callback, this, function, value));
    }

    /**
     * @return wakes up the callback early,
     * the result is already associated with this context,
     * it's not necessary to use a Context.execute(Runnable) indirection.
     */
    @NotNull Runnable awaitEndNanos(@NotNull Callback<Void> callback);

    /**
     * @return endNanos
     * @throws TimeoutException when timed out
     */
    default long checkEndNanos(String timeoutMessage) throws TimeoutException {
        return checkEndNanos(clock().nowNanos(), timeoutMessage);
    }

    /**
     * @return endNanos
     * @throws TimeoutException when timed out
     */
    default long checkEndNanos(long nowNanos, String timeoutMessage) throws TimeoutException {
        long endNanos=endNanos();
        Clock.checkEndNanos(endNanos, nowNanos, timeoutMessage);
        return endNanos;
    }

    @NotNull Clock clock();

    default <T> void complete(@NotNull Callback<T> callback, T value) {
        execute(new Complete<>(callback, value));
    }

    @NotNull String debugMagic();

    @NotNull Context debugMagic(@NotNull String debugMagic) throws Throwable;

    /**
     * This is advisory. Computations shouldn't be terminated willy-nilly.
     */
    long endNanos();

    @NotNull Context endNanos(long endNanos) throws Throwable;

    @Override
    void execute(@NotNull Runnable command);

    default <T> void fail(@NotNull Callback<T> callback, @NotNull Throwable throwable) {
        execute(new Fail<>(callback, throwable));
    }

    default <T> void get(@NotNull Callback<T> callback, @NotNull Lava<T> supplier) {
        execute(new Get<>(callback, this, supplier));
    }

    default boolean isEndNanosInTheFuture() {
        return isEndNanosInTheFuture(clock().nowNanos());
    }

    default boolean isEndNanosInTheFuture(long nowNanos) {
        return Clock.isEndNanosInTheFuture(endNanos(), nowNanos);
    }

    @NotNull Log log();
}
