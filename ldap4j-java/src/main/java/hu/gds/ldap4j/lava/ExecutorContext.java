package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Log;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ExecutorContext extends Context.Abstract {
    public ExecutorContext(
            @NotNull Clock clock,
            @NotNull String debugMagic,
            @Nullable Long endNanos,
            @NotNull Log log,
            int parallelism) {
        super(clock, debugMagic, endNanos, log, parallelism);
    }

    @Override
    public @NotNull Runnable awaitEndNanos(@NotNull Callback<Void> callback) {
        Objects.requireNonNull(callback, "callback");
        long delayNanos=clock().endNanosToDelayNanos(endNanos());
        if (0>=delayNanos) {
            complete(callback, null);
            return ()->{
            };
        }
        else {
            AtomicBoolean get=new AtomicBoolean();
            Runnable runnable=()->{
                if (get.compareAndSet(false, true)) {
                    checkClosedAndExecute(()->complete(callback, null));
                }
            };
            Runnable cancel=checkClosedAndSchedule(delayNanos, runnable);
            return ()->{
                try {
                    cancel.run();
                }
                finally {
                    runnable.run();
                }
            };
        }
    }

    protected abstract void checkClosedAndExecute(@NotNull Runnable runnable);

    protected abstract @NotNull Runnable checkClosedAndSchedule(long delayNanos, @NotNull Runnable runnable);

    @Override
    public void execute(@NotNull Runnable command) {
        Objects.requireNonNull(command, "command");
        checkClosedAndExecute(()->{
            try {
                command.run();
            }
            catch (Throwable throwable) {
                log().error(getClass(), throwable);
            }
        });
    }
}
