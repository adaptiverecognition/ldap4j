package hu.gds.ldap4j.reactor;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Clock;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.ExecutorContext;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.scheduler.Schedulers;

public class ReactorContext extends ExecutorContext {
    public ReactorContext(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
        super(Clock.SYSTEM_NANO_TIME, debugMagic, endNanos, log);
    }

    @Override
    protected void checkClosedAndExecute(@NotNull Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        Schedulers.parallel().schedule(runnable);
    }

    @Override
    protected @NotNull Runnable checkClosedAndSchedule(long delayNanos, @NotNull Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        if (0L<delayNanos) {
            return Schedulers.parallel().schedule(runnable, delayNanos, TimeUnit.NANOSECONDS)::dispose;
        }
        else {
            Schedulers.parallel().schedule(runnable);
            return ()->{
            };
        }
    }

    @Override
    protected Context context(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
        return new ReactorContext(debugMagic, endNanos, log);
    }

    public static ReactorContext createTimeoutNanos(long timeoutNanos) {
        return new ReactorContext(
                ReactorContext.class.getSimpleName(),
                Clock.SYSTEM_NANO_TIME.delayNanosToEndNanos(timeoutNanos),
                ReactorLog.create());
    }
}
