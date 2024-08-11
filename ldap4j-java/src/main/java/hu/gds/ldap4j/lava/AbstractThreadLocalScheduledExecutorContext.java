package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Log;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractThreadLocalScheduledExecutorContext extends ExecutorContext {
    public static class LocalData {
        private LocalData() {
        }

        private int remaining;
        private @Nullable Runnable runnable;
        private boolean running;
    }

    private class LocalTrampoline implements Runnable {
        private final @NotNull Runnable runnable;

        public LocalTrampoline(@NotNull Runnable runnable) {
            this.runnable=Objects.requireNonNull(runnable, "runnable");
        }

        @Override
        public void run() {
            LocalData localData=threadLocal.get();
            if (null==localData) {
                localData=new LocalData();
                threadLocal.set(localData);
            }
            if (null!=localData.runnable) {
                throw new IllegalStateException();
            }
            if (localData.running) {
                throw new IllegalStateException();
            }
            localData.remaining=localSize;
            localData.running=true;
            try {
                runnable.run();
                for (; (0<localData.remaining) && (null!=localData.runnable); --localData.remaining) {
                    @NotNull Runnable runnable2=localData.runnable;
                    localData.runnable=null;
                    runnable2.run();
                }
            }
            finally {
                if (!localData.running) {
                    throw new IllegalStateException();
                }
                @Nullable Runnable runnable3=localData.runnable;
                localData.runnable=null;
                localData.running=false;
                if (null!=runnable3) {
                    checkClosedAndGetExecutor().execute(new LocalTrampoline(runnable3));
                }
            }
        }
    }

    public static final int DEFAULT_LOCAL_SIZE=16;

    protected final int localSize;
    protected final @NotNull ThreadLocal<@Nullable LocalData> threadLocal;

    public AbstractThreadLocalScheduledExecutorContext(
            @NotNull String debugMagic,
            @Nullable Long endNanos,
            int localSize,
            @NotNull Log log,
            int parallelism,
            @NotNull ThreadLocal<@Nullable LocalData> threadLocal) {
        super(Clock.SYSTEM_NANO_TIME, debugMagic, endNanos, log, parallelism);
        if (0>=localSize) {
            throw new IllegalArgumentException("0 >= localSize %,d".formatted(localSize));
        }
        this.localSize=localSize;
        this.threadLocal=Objects.requireNonNull(threadLocal, "executor");
    }

    @Override
    protected void checkClosedAndExecute(@NotNull Runnable runnable) {
        @Nullable LocalData localData=threadLocal.get();
        if ((null==localData)
                || (null!=localData.runnable)
                || (!localData.running)) {
            checkClosedAndGetExecutor().execute(new LocalTrampoline(runnable));
        }
        else {
            localData.runnable=runnable;
        }
    }
    
    protected abstract @NotNull ScheduledExecutorService checkClosedAndGetExecutor();

    @Override
    protected @NotNull Runnable checkClosedAndSchedule(long delayNanos, @NotNull Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        if (0L<delayNanos) {
            Future<?> future=checkClosedAndGetExecutor()
                    .schedule(new LocalTrampoline(runnable), delayNanos, TimeUnit.NANOSECONDS);
            return ()->future.cancel(false);
        }
        else {
            checkClosedAndExecute(runnable);
            return ()->{
            };
        }
    }
}
