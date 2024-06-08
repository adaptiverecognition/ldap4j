package hu.gds.ldap4j.trampoline;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.CallbackContext;
import hu.gds.ldap4j.lava.Clock;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.lava.MinHeap;
import hu.gds.ldap4j.net.ClosedException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.LongPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Trampoline implements AutoCloseable {
    public class Context extends hu.gds.ldap4j.lava.Context.Abstract {
        public Context(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            super(Trampoline.this.clock(), debugMagic, endNanos, log);
        }

        @Override
        public @NotNull Runnable awaitEndNanos(@NotNull Callback<Void> callback) {
            return Trampoline.this.awaitEndNanos(callback, this);
        }

        @Override
        protected hu.gds.ldap4j.lava.Context context(
                @NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            return new Context(debugMagic, endNanos, log);
        }

        @Override
        public void execute(@NotNull Runnable command) {
            Trampoline.this.execute(command);
        }

        public <T> T get(
                boolean assertNoResidueIn, boolean assertNoResidueOut, @NotNull Lava<T> lava) throws Throwable {
            Objects.requireNonNull(lava, "lava");
            class Get extends Callback.AbstractSingleRunCallback<T> {
                boolean completed;
                T result;
                @Nullable Throwable throwable;

                private void assertNoResidueSynchronized(boolean assertNoResidue) {
                    if (assertNoResidue && ((!runnableQueue.isEmpty()) || (!waitQueue.isEmpty()))) {
                        throw new RuntimeException("trampoline has residue");
                    }
                }

                @Override
                protected void completedImpl(T value) {
                    synchronized (lock) {
                        completed=true;
                        result=value;
                        lock.notifyAll();
                    }
                }

                @Override
                protected void failedImpl(@NotNull Throwable throwable) {
                    synchronized (lock) {
                        completed=true;
                        this.throwable=throwable;
                        lock.notifyAll();
                    }
                }

                private T get() throws Throwable {
                    synchronized (lock) {
                        assertNoResidueSynchronized(assertNoResidueIn);
                        runnableQueue.addLast(()->Context.this.get(this, lava));
                    }
                    while (true) {
                        @NotNull Runnable runnable;
                        synchronized (lock) {
                            while (true) {
                                if (closed) {
                                    throw new ClosedException();
                                }
                                if (completed) {
                                    assertNoResidueSynchronized(assertNoResidueOut);
                                    if (null==throwable) {
                                        return result;
                                    }
                                    throw new RuntimeException(throwable);
                                }
                                long nowNanos=clock().nowNanos();
                                checkEndNanos(nowNanos, "computation timeout");
                                while (!waitQueue.isEmpty()
                                        && (completed
                                        || (!waitQueue.peekMin().context().isEndNanosInTheFuture(nowNanos)))) {
                                    CallbackContext<Void> wait=waitQueue.removeMin(nowNanos);
                                    runnableQueue.addLast(new WakeUp(wait));
                                }
                                if (!runnableQueue.isEmpty()) {
                                    runnable=runnableQueue.removeFirst();
                                    break;
                                }
                                long endNanos2=endNanos();
                                if (!waitQueue.isEmpty()) {
                                    long endNanos3=waitQueue.peekMin().context().endNanos();
                                    if (0<Clock.compareEndNanos(endNanos2, endNanos3, nowNanos)) {
                                        endNanos2=endNanos3;
                                    }
                                }
                                Clock.synchronizedWaitEndNanosTimeout(endNanos2, nowNanos, lock);
                            }
                        }
                        runnable.run();
                    }
                }
            }
            return new Get()
                    .get();
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
    private final Object lock=new Object();
    private final @NotNull Log log;
    private final Deque<Runnable> runnableQueue=new LinkedList<>();
    private final MinHeap<CallbackContext<Void>> waitQueue=new MinHeap<>(
            1, (callbackContext)->callbackContext.context().endNanos());

    public Trampoline(@NotNull Log log) {
        this.log=Objects.requireNonNull(log, "log");
    }

    private @NotNull Runnable awaitEndNanos(@NotNull Callback<Void> callback, @NotNull Context context) {
        LongPredicate remove;
        synchronized (lock) {
            if (closed) {
                throw new ClosedException();
            }
            remove=waitQueue.add(Clock.SYSTEM_NANO_TIME.nowNanos(), new CallbackContext<>(callback, context));
            lock.notifyAll();
        }
        return ()->{
            synchronized (lock) {
                if (closed) {
                    throw new ClosedException();
                }
                if (!remove.test(Clock.SYSTEM_NANO_TIME.nowNanos())) {
                    return;
                }
                execute(()->context.complete(callback, null));
            }
        };
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

    public @NotNull Context contextEndNanos(long endNanos) {
        return new Context(Context.class.getName(), endNanos, log);
    }

    private void execute(@NotNull Runnable command) {
        Objects.requireNonNull(command, "command");
        synchronized (lock) {
            if (closed) {
                throw new ClosedException();
            }
            runnableQueue.addLast(command);
            lock.notifyAll();
        }
    }
}
