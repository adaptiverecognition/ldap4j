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
    private class ContextImpl extends ExecutorContext.Scheduled {
        public ContextImpl(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            super(Clock.SYSTEM_NANO_TIME, debugMagic, endNanos, log);
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
        protected Context context(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            return new ContextImpl(debugMagic, endNanos, log);
        }
    }

    private final Context context;
    private final AtomicReference<ScheduledExecutorService> executor=new AtomicReference<>();
    private final int poolSize;
    private final @Nullable ThreadFactory threadFactory;

    public ThreadPoolContextHolder(
            @NotNull Log log, int poolSize, @Nullable ThreadFactory threadFactory) {
        super(log);
        if (0>=poolSize) {
            throw new IllegalArgumentException("0 >= poolSize %,d".formatted(poolSize));
        }
        this.poolSize=poolSize;
        this.threadFactory=threadFactory;
        context=new ContextImpl("", null, log);
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
        return new ContextImpl("", null, log);
    }

    public static @NotNull Function<@NotNull Log, @NotNull ContextHolder> factory(
            int poolSize, @Nullable ThreadFactory threadFactory) {
        if (0>=poolSize) {
            throw new IllegalArgumentException("0 >= poolSize %,d".formatted(poolSize));
        }
        return new Function<>() {
            @Override
            public ContextHolder apply(@NotNull Log value) {
                Objects.requireNonNull(value, "value");
                return new ThreadPoolContextHolder(value, poolSize, threadFactory);
            }

            @Override
            public String toString() {
                return "ThreadPoolContextHolder.factory(poolSize: "+poolSize
                        +", threadFactory: "+threadFactory+")";
            }
        };
    }

    @Override
    public <T> T getOrTimeoutEndNanos(long endNanos, Lava<T> supplier) throws Throwable {
        JoinCallback<T> callback=Callback.join(clock());
        context.endNanos(endNanos).get(callback, supplier);
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
        return "ThreadPoolContextHolder(log: "+log+", poolSize: "+poolSize+", threadFactory: "+threadFactory+")";
    }
}
