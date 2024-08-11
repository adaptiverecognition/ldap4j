package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Log;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScheduledExecutorContext extends AbstractScheduledExecutorContext {
    private final @NotNull ScheduledExecutorService executor;

    public ScheduledExecutorContext(
            @NotNull String debugMagic,
            @Nullable Long endNanos,
            @NotNull ScheduledExecutorService executor,
            @NotNull Log log,
            int parallelism) {
        super(Clock.SYSTEM_NANO_TIME, debugMagic, endNanos, log, parallelism);
        this.executor=Objects.requireNonNull(executor, "executor");
    }

    @Override
    protected @NotNull ScheduledExecutorService checkClosedAndGetExecutor() {
        return executor;
    }

    @Override
    protected @NotNull Context context(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
        return new ScheduledExecutorContext(debugMagic, endNanos, executor, log, parallelism());
    }

    public static @NotNull ScheduledExecutorContext createDelayNanos(
            long delayNanos,
            @NotNull ScheduledExecutorService executor,
            @NotNull Log log,
            int parallelism) {
        return createEndNanos(Clock.SYSTEM_NANO_TIME.delayNanosToEndNanos(delayNanos), executor, log, parallelism);
    }

    public static @NotNull ScheduledExecutorContext createEndNanos(
            @Nullable Long endNanos,
            @NotNull ScheduledExecutorService executor,
            @NotNull Log log,
            int parallelism) {
        return new ScheduledExecutorContext(
                ScheduledExecutorService.class.getSimpleName(), endNanos, executor, log, parallelism);
    }
}
