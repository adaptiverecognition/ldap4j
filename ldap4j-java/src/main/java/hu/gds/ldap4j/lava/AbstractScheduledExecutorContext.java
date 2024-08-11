package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Log;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractScheduledExecutorContext extends ExecutorContext {
    public AbstractScheduledExecutorContext(
            @NotNull Clock clock,
            @NotNull String debugMagic,
            @Nullable Long endNanos,
            @NotNull Log log,
            int parallelism) {
        super(clock, debugMagic, endNanos, log, parallelism);
    }

    @Override
    protected void checkClosedAndExecute(@NotNull Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        checkClosedAndGetExecutor().execute(runnable);
    }

    protected abstract @NotNull ScheduledExecutorService checkClosedAndGetExecutor();

    @Override
    protected @NotNull Runnable checkClosedAndSchedule(long delayNanos, @NotNull Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        if (0L<delayNanos) {
            Future<?> future=checkClosedAndGetExecutor().schedule(runnable, delayNanos, TimeUnit.NANOSECONDS);
            return ()->future.cancel(false);
        }
        else {
            checkClosedAndGetExecutor().execute(runnable);
            return ()->{
            };
        }
    }
}
