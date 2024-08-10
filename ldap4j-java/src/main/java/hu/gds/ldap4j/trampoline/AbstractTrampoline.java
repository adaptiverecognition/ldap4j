package hu.gds.ldap4j.trampoline;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.CallbackContext;
import hu.gds.ldap4j.lava.Clock;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.MinHeap;
import hu.gds.ldap4j.net.ClosedException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.LongPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractTrampoline<C extends AbstractTrampoline.AbstractContext> implements AutoCloseable {
    public static abstract class AbstractContext extends Context.Abstract {
        protected final @NotNull AbstractTrampoline<?> trampoline;

        public AbstractContext(
                @NotNull String debugMagic,
                @Nullable Long endNanos,
                @NotNull Log log,
                @NotNull AbstractTrampoline<?> trampoline) {
            super(trampoline.clock(), debugMagic, endNanos, log);
            this.trampoline=Objects.requireNonNull(trampoline, "trampoline");
        }

        @Override
        public @NotNull Runnable awaitEndNanos(@NotNull Callback<Void> callback) {
            return trampoline.awaitEndNanos(callback, this);
        }

        @Override
        public void execute(@NotNull Runnable command) {
            trampoline.execute(command);
        }
    }

    private static class WakeUp implements Runnable {
        private final @NotNull CallbackContext<Void> wait;

        public WakeUp(@NotNull CallbackContext<Void> wait) {
            this.wait=Objects.requireNonNull(wait, "wait");
        }

        @Override
        public void run() {
            wait.completed(null);
        }
    }

    private boolean closed;
    protected final @NotNull Object lock=new Object();
    protected final @NotNull Log log;
    private final @NotNull Deque<@NotNull Runnable> runnableQueue=new LinkedList<>();
    private final @NotNull MinHeap<@NotNull CallbackContext<Void>> waitQueue=new MinHeap<>(
            1, (callbackContext)->callbackContext.context().endNanos());

    public AbstractTrampoline(@NotNull Log log) {
        this.log=Objects.requireNonNull(log, "log");
    }

    protected void assertNoResidueSynchronized(boolean assertNoResidue) {
        if (assertNoResidue && ((!runnableQueue.isEmpty()) || (!waitQueue.isEmpty()))) {
            throw new RuntimeException("trampoline has residue");
        }
    }

    private @NotNull Runnable awaitEndNanos(@NotNull Callback<Void> callback, @NotNull AbstractContext context) {
        LongPredicate remove;
        synchronized (lock) {
            checkClosedSynchronized();
            remove=waitQueue.add(Clock.SYSTEM_NANO_TIME.nowNanos(), new CallbackContext<>(callback, context));
            lock.notifyAll();
        }
        return ()->{
            synchronized (lock) {
                checkClosedSynchronized();
                if (!remove.test(Clock.SYSTEM_NANO_TIME.nowNanos())) {
                    return;
                }
                execute(()->context.complete(callback, null));
            }
        };
    }

    protected void checkClosedSynchronized() {
        if (closed) {
            throw new ClosedException();
        }
    }

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

    public abstract @NotNull C contextEndNanos(long endNanos);

    private void execute(@NotNull Runnable command) {
        Objects.requireNonNull(command, "command");
        synchronized (lock) {
            checkClosedSynchronized();
            runnableQueue.addLast(command);
            lock.notifyAll();
        }
    }

    public Long minAwaitEndNanos() {
        synchronized (lock) {
            if (waitQueue.isEmpty()) {
                return null;
            }
            return waitQueue.peekMin().context().endNanos();
        }
    }

    protected boolean runOne(Long waitEndNanos) throws Throwable {
        @NotNull Runnable runnable;
        synchronized (lock) {
            checkClosedSynchronized();
            long nowNanos=clock().nowNanos();
            while (!waitQueue.isEmpty()
                    && (!waitQueue.peekMin().context().isEndNanosInTheFuture(nowNanos))) {
                CallbackContext<Void> wait=waitQueue.removeMin(nowNanos);
                runnableQueue.addLast(new WakeUp(wait));
            }
            if (runnableQueue.isEmpty()) {
                if (null==waitEndNanos) {
                    return false;
                }
                else {
                    if (!waitQueue.isEmpty()) {
                        long minWaitEndNanos=waitQueue.peekMin().context().endNanos();
                        if (0<Clock.compareEndNanos(waitEndNanos, minWaitEndNanos, nowNanos)) {
                            waitEndNanos=minWaitEndNanos;
                        }
                    }
                    Clock.synchronizedWaitDelayNanos(waitEndNanos-nowNanos, lock);
                    return true;
                }
            }
            runnable=runnableQueue.removeFirst();
        }
        runnable.run();
        return true;
    }
}
