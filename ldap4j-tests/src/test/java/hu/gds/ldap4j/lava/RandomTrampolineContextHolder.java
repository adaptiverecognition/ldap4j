package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.PrettyPrinter;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This scheduler doesn't respect time.
 */
public class RandomTrampolineContextHolder extends ContextHolder {
    private class ContextImpl extends Context.Abstract implements AutoCloseable {
        private final @NotNull AtomicBoolean closed;

        public ContextImpl(
                @NotNull AtomicBoolean closed, @NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            super(Clock.SYSTEM_NANO_TIME, debugMagic, endNanos, log);
            this.closed=Objects.requireNonNull(closed, "closed");
        }

        @Override
        public @NotNull Runnable awaitEndNanos(@NotNull Callback<Void> callback) {
            complete(callback, null);
            class WakeUp implements PrettyPrinter.PrettyPrintable, Runnable {
                @Override
                public void prettyPrint(PrettyPrinter printer) {
                    printer.printInstance(this);
                    printer.printObject("callback", callback);
                }

                @Override
                public void run() {
                }

                @Override
                public String toString() {
                    return "%s@%x.awaitEndNanos() no-op wake-up, callback: %s"
                            .formatted(
                                    getClass(),
                                    System.identityHashCode(this),
                                    callback);
                }
            }
            return new WakeUp();
        }

        private void check2() {
            check();
            if (closed.get()) {
                throw new RuntimeException("context closed");
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                closed.set(true);
                lock.notifyAll();
            }
        }

        @Override
        protected Context context(@NotNull String debugMagic, @Nullable Long endNanos, @NotNull Log log) {
            return new ContextImpl(closed, debugMagic, endNanos, log);
        }

        @Override
        public void execute(@NotNull Runnable command) {
            Objects.requireNonNull(command, "command");
            synchronized (lock) {
                check2();
                deque.addLast(command);
                lock.notifyAll();
            }
        }

        private boolean runOne(Long endNanos) throws Throwable {
            Runnable runnable;
            synchronized (lock) {
                while (true) {
                    check2();
                    if (!deque.isEmpty()) {
                        if ((1<deque.size()) && (null!=random)) {
                            for (int ii=deque.size()-1; 0<ii; --ii) {
                                if (random.nextBoolean()) {
                                    break;
                                }
                                deque.addLast(deque.removeFirst());
                            }
                        }
                        runnable=deque.removeFirst();
                        break;
                    }
                    if (null==endNanos) {
                        return false;
                    }
                    long delayNanos=clock().endNanosToDelayNanos(endNanos);
                    if (0>=delayNanos) {
                        return false;
                    }
                    Clock.synchronizedWaitDelayNanos(delayNanos, lock);
                }
            }
            runnable.run();
            return true;
        }
    }

    private boolean closed;
    private final Deque<@NotNull Runnable> deque=new LinkedList<>();
    private final Object lock=new Object();
    private final @Nullable Random random;

    public RandomTrampolineContextHolder(@NotNull Log log, @Nullable Random random) {
        super(log);
        this.random=random;
    }

    @Override
    public void assertSize(int maxSize, int minSize) {
        synchronized (lock) {
            if ((maxSize<deque.size()) || (minSize>deque.size())) {
                System.err.printf(
                        "expected max size %,d, min size %,d, actual size %,d", maxSize, minSize, deque.size());
                deque.forEach((runnable)->PrettyPrinter.create(System.err).printObject("runnable", runnable));
                fail("expected max size %,d, min size %,d, actual size %,d".formatted(maxSize, minSize, deque.size()));
            }
        }
    }

    private void check() {
        if (closed) {
            throw new RuntimeException("context closed");
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
        throw new UnsupportedOperationException();
    }

    public static @NotNull Function<@NotNull Log, @NotNull ContextHolder> factory(Long randomSeed) {
        return new Function<>() {
            @Override
            public ContextHolder apply(@NotNull Log log) {
                return new RandomTrampolineContextHolder(log, (null==randomSeed)?null:new Random(randomSeed));
            }

            @Override
            public String toString() {
                return "RandomSynchronousContextHolder.factory(randomSeed: "+randomSeed+")";
            }
        };
    }

    @Override
    public <T> T getOrTimeoutEndNanos(long endNanos, Lava<T> supplier) throws Throwable {
        Objects.requireNonNull(supplier, "supplier");
        check();
        JoinCallback<T> callback=Callback.join(Clock.SYSTEM_NANO_TIME);
        try (ContextImpl context=new ContextImpl(new AtomicBoolean(), "", endNanos, log)) {
            context.get(callback, supplier);
            while (!callback.completed()) {
                Clock.SYSTEM_NANO_TIME.checkEndNanos(
                        endNanos,
                        RandomTrampolineContextHolder.class+" get timeout");
                if (!context.runOne(endNanos)) {
                    Thread.yield();
                }
            }
            assertSize(0);
        }
        return callback.result();
    }

    @Override
    public void start() {
    }

    @Override
    public String toString() {
        return "RandomSynchronousContextHolder()";
    }
}
