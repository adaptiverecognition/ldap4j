package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Log;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScheduledExecutorContext extends ExecutorContext.Scheduled {
    private final @NotNull ScheduledExecutorService executor;

    public ScheduledExecutorContext(
            @NotNull String debugMagic, @Nullable Long endNanos,
            @NotNull ScheduledExecutorService executor, @NotNull Log log) {
        super(Clock.SYSTEM_NANO_TIME, debugMagic, endNanos, log);
        this.executor=Objects.requireNonNull(executor, "executor");
    }

    @Override
    protected @NotNull ScheduledExecutorService checkClosedAndGetExecutor() {
        return executor;
    }

    @Override
    protected Context context(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
        return new ScheduledExecutorContext(debugMagic, endNanos, executor, log);
    }

    public static ScheduledExecutorContext createDelayNanos(
            long delayNanos, @NotNull ScheduledExecutorService executor, @NotNull Log log) {
        return createEndNanos(Clock.SYSTEM_NANO_TIME.delayNanosToEndNanos(delayNanos), executor, log);
    }

    public static ScheduledExecutorContext createEndNanos(
            @Nullable Long endNanos, @NotNull ScheduledExecutorService executor, @NotNull Log log) {
        return new ScheduledExecutorContext(ScheduledExecutorService.class.getSimpleName(), endNanos, executor, log);
    }
}
