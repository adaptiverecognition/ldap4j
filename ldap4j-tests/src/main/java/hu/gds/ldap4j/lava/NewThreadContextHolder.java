package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.net.ClosedException;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewThreadContextHolder extends ContextHolder {
    private class ContextImpl extends Context.Abstract {
        public ContextImpl(
                @NotNull String debugMagic,
                @Nullable Long endNanos,
                @NotNull Log log,
                int parallelism) {
            super(Clock.SYSTEM_NANO_TIME, debugMagic, endNanos, log, parallelism);
        }

        @Override
        public @NotNull Runnable awaitEndNanos(@NotNull Callback<Void> callback) {
            Objects.requireNonNull(callback, "callback");
            class Wait implements Runnable {
                private final long endNanos=endNanos();
                private boolean wakeUp;

                @Override
                public void run() {
                    synchronized (lock) {
                        while (true) {
                            checkClosed();
                            if (wakeUp) {
                                break;
                            }
                            long nowNanos=clock().nowNanos();
                            long delayNanos=Clock.endNanosToDelayNanos(endNanos, nowNanos);
                            if (0L>=delayNanos) {
                                break;
                            }
                            try {
                                Clock.synchronizedWaitDelayNanos(delayNanos, lock);
                            }
                            catch (InterruptedException ignore) {
                                break;
                            }
                        }
                    }
                    checkClosed();
                    complete(callback, null);
                }

                private void wakeUp() {
                    synchronized (lock) {
                        wakeUp=true;
                        lock.notifyAll();
                    }
                }
            }
            Wait wait=new Wait();
            Runnable wakeUp=wait::wakeUp;
            thread(wait).start();
            return wakeUp;
        }

        @Override
        protected @NotNull Context context(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            checkClosed();
            return new ContextImpl(debugMagic, endNanos, log, parallelism());
        }

        @Override
        public void execute(@NotNull Runnable command) {
            thread(command).start();
        }
    }

    private boolean closed;
    private final Object lock=new Object();
    private final int parallelism;
    private final @Nullable ThreadFactory threadFactory;

    public NewThreadContextHolder(@NotNull Log log, int parallelism, @Nullable ThreadFactory threadFactory) {
        super(log);
        this.parallelism=parallelism;
        this.threadFactory=threadFactory;
    }

    private void checkClosed() {
        synchronized (lock) {
            if (closed) {
                throw new ClosedException();
            }
        }
    }

    @Override
    public @NotNull Clock clock() {
        return Clock.SYSTEM_NANO_TIME;
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed=true;
            lock.notifyAll();
        }
    }

    @Override
    public @NotNull Context context() {
        checkClosed();
        return new ContextImpl("", null, log, parallelism);
    }

    public static @NotNull Function<@NotNull Log, @NotNull ContextHolder> factory(
            int parallelism,
            @Nullable ThreadFactory threadFactory) {
        return new Function<>() {
            @Override
            public @NotNull ContextHolder apply(@NotNull Log value) {
                Objects.requireNonNull(value, "value");
                return new NewThreadContextHolder(value, parallelism, threadFactory);
            }

            @Override
            public String toString() {
                return "NewThreadContextHolder.factory(threadFactory: "+threadFactory+")";
            }
        };
    }

    @Override
    public <T> T getOrTimeoutEndNanos(long endNanos, Lava<T> supplier) throws Throwable {
        checkClosed();
        JoinCallback<T> callback=Callback.join(clock());
        new ContextImpl("", endNanos, log, parallelism)
                .get(callback, supplier);
        return callback.joinEndNanos(endNanos);
    }

    @Override
    public void start() {
        checkClosed();
    }

    private @NotNull Thread thread(@NotNull Runnable runnable) {
        checkClosed();
        Objects.requireNonNull(runnable, "runnable");
        Runnable runnable2=()->{
            try {
                checkClosed();
                runnable.run();
            }
            catch (Throwable throwable) {
                log.error(getClass(), throwable);
            }
        };
        if (null==threadFactory) {
            return new Thread(runnable2);
        }
        else {
            return threadFactory.newThread(runnable2);
        }
    }
}
