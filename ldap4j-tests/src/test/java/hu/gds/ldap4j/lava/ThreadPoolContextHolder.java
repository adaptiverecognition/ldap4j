package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreadPoolContextHolder extends ContextHolder {
    private class ScheduledContextImpl extends AbstractScheduledExecutorContext {
        public ScheduledContextImpl(
                @NotNull String debugMagic,
                @Nullable Long endNanos,
                @NotNull Log log,
                int parallelism) {
            super(Clock.SYSTEM_NANO_TIME, debugMagic, endNanos, log, parallelism);
        }

        @Override
        protected @NotNull ScheduledExecutorService checkClosedAndGetExecutor() {
            ScheduledExecutorService executor2=executor.get();
            if (null==executor2) {
                throw new RuntimeException("executor not running");
            }
            return executor2;
        }

        @Override
        protected @NotNull Context context(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            return new ScheduledContextImpl(debugMagic, endNanos, log, poolSize);
        }
    }

    private class ThreadLocalScheduledContextImpl extends AbstractThreadLocalScheduledExecutorContext {
        public ThreadLocalScheduledContextImpl(
                @NotNull String debugMagic,
                @Nullable Long endNanos,
                @NotNull Log log,
                int parallelism) {
            super(
                    debugMagic,
                    endNanos,
                    AbstractThreadLocalScheduledExecutorContext.DEFAULT_LOCAL_SIZE,
                    log,
                    parallelism,
                    ThreadPoolContextHolder.this.threadLocal2);
        }

        @Override
        protected @NotNull ScheduledExecutorService checkClosedAndGetExecutor() {
            ScheduledExecutorService executor2=executor.get();
            if (null==executor2) {
                throw new RuntimeException("executor not running");
            }
            return executor2;
        }

        @Override
        protected @NotNull Context context(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            return new ThreadLocalScheduledContextImpl(debugMagic, endNanos, log, poolSize);
        }
    }

    private final @NotNull AtomicReference<ScheduledExecutorService> executor=new AtomicReference<>();
    private final int parallelism;
    private final int poolSize;
    private final @Nullable ThreadFactory threadFactory;
    private final boolean threadLocal;
    private final @NotNull ThreadLocal<AbstractThreadLocalScheduledExecutorContext.@Nullable LocalData> threadLocal2
            =new ThreadLocal<>();

    public ThreadPoolContextHolder(
            @NotNull Log log,
            int parallelism,
            int poolSize,
            @Nullable ThreadFactory threadFactory,
            boolean threadLocal) {
        super(log);
        if (0>=poolSize) {
            throw new IllegalArgumentException("0 >= poolSize %,d".formatted(poolSize));
        }
        this.parallelism=parallelism;
        this.poolSize=poolSize;
        this.threadFactory=threadFactory;
        this.threadLocal=threadLocal;
    }

    @Override
    public @NotNull Clock clock() {
        return Clock.SYSTEM_NANO_TIME;
    }

    @Override
    public void close() {
        ScheduledExecutorService executor2=executor.getAndSet(null);
        if (null!=executor2) {
            executor2.shutdownNow();
        }
    }

    @Override
    public @NotNull Context context() {
        return threadLocal
                ?new ThreadLocalScheduledContextImpl("", null, log, parallelism)
                :new ScheduledContextImpl("", null, log, parallelism);
    }

    public static @NotNull Function<@NotNull Log, @NotNull ContextHolder> factory(
            int parallelism,
            int poolSize,
            @Nullable ThreadFactory threadFactory,
            boolean threadLocal) {
        if (0>=poolSize) {
            throw new IllegalArgumentException("0 >= poolSize %,d".formatted(poolSize));
        }
        return new Function<>() {
            @Override
            public ContextHolder apply(@NotNull Log value) {
                Objects.requireNonNull(value, "value");
                return new ThreadPoolContextHolder(value, parallelism, poolSize, threadFactory, threadLocal);
            }

            @Override
            public String toString() {
                return "ThreadPoolContextHolder.factory(poolSize: %,d, threadFactory: %s, threadLocal: %s)"
                        .formatted(
                                poolSize,
                                threadFactory,
                                threadLocal);
            }
        };
    }

    @Override
    public <T> T getOrTimeoutEndNanos(long endNanos, Lava<T> supplier) throws Throwable {
        JoinCallback<T> callback=Callback.join(clock());
        context().endNanos(endNanos).get(callback, supplier);
        return callback.joinEndNanos(endNanos);
    }

    @Override
    public void start() {
        boolean error=true;
        ScheduledExecutorService executor2=(null==threadFactory)
                ?Executors.newScheduledThreadPool(poolSize)
                :Executors.newScheduledThreadPool(poolSize, threadFactory);
        try {
            if (!executor.compareAndSet(null, executor2)) {
                throw new RuntimeException("executor already running");
            }
            error=false;
        }
        finally {
            if (error) {
                executor2.shutdownNow();
            }
        }
    }

    @Override
    public String toString() {
        return "ThreadPoolContextHolder(log: %s, poolSize: %,d, threadFactory: %s, threadLocal: %s)"
                .formatted(
                        log,
                        poolSize,
                        threadFactory,
                        threadLocal);
    }
}
