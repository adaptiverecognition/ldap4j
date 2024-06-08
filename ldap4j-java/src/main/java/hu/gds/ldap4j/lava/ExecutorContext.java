package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Log;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ExecutorContext extends Context.Abstract {
    public static abstract class Scheduled extends ExecutorContext {
        public Scheduled(@NotNull Clock clock, @NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            super(clock, debugMagic, endNanos, log);
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

    public ExecutorContext(
            @NotNull Clock clock, @NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
        super(clock, debugMagic, endNanos, log);
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
