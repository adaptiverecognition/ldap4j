package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Log;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreadLocalScheduledExecutorContext extends AbstractThreadLocalScheduledExecutorContext {
    private final @NotNull ScheduledExecutorService executor;

    public ThreadLocalScheduledExecutorContext(
            @NotNull String debugMagic,
            @Nullable Long endNanos,
            @NotNull ScheduledExecutorService executor,
            int localSize,
            @NotNull Log log,
            int parallelism,
            @NotNull ThreadLocal<@Nullable LocalData> threadLocal) {
        super(debugMagic, endNanos, localSize, log, parallelism, threadLocal);
        this.executor=Objects.requireNonNull(executor, "executor");
    }

    @Override
    protected @NotNull ScheduledExecutorService checkClosedAndGetExecutor() {
        return executor;
    }

    @Override
    protected @NotNull Context context(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
        return new ThreadLocalScheduledExecutorContext(
                debugMagic,
                endNanos,
                executor,
                localSize,
                log,
                parallelism(),
                threadLocal);
    }

    public static @NotNull Context createDelayNanos(
            long delayNanos,
            @NotNull ScheduledExecutorService executor,
            int localSize,
            @NotNull Log log,
            int parallelism,
            @NotNull ThreadLocal<@Nullable LocalData> threadLocal) {
        return createEndNanos(
                Clock.SYSTEM_NANO_TIME.delayNanosToEndNanos(delayNanos),
                executor,
                localSize,
                log,
                parallelism,
                threadLocal);
    }

    public static @NotNull Context createDelayNanos(
            long delayNanos,
            @NotNull ScheduledExecutorService executor,
            @NotNull Log log,
            int parallelism) {
        return createDelayNanos(
                delayNanos,
                executor,
                ThreadLocalScheduledExecutorContext.DEFAULT_LOCAL_SIZE,
                log,
                parallelism,
                new ThreadLocal<>());
    }

    public static @NotNull Context createEndNanos(
            @Nullable Long endNanos,
            @NotNull ScheduledExecutorService executor,
            int localSize,
            @NotNull Log log,
            int parallelism,
            @NotNull ThreadLocal<@Nullable LocalData> threadLocal) {
        return new ThreadLocalScheduledExecutorContext(
                ScheduledExecutorService.class.getSimpleName(),
                endNanos,
                executor,
                localSize,
                log,
                parallelism,
                threadLocal);
    }
}
