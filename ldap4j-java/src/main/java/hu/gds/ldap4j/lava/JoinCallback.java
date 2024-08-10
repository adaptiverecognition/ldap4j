package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Either;
import java.io.Serial;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JoinCallback<T> implements Callback<T>, SingleRun {
    public abstract static class JoinException extends Exception {
        @Serial
        private static final long serialVersionUID=0L;

        private JoinException() {
        }

        private JoinException(Throwable cause) {
            super(cause);
        }
    }

    public static class JoinFailedException extends JoinException {
        @Serial
        private static final long serialVersionUID=0L;

        public JoinFailedException(Throwable cause) {
            super(cause);
        }
    }

    public static class JoinNotCompletedException extends JoinException {
        @Serial
        private static final long serialVersionUID=0L;
    }

    private @Nullable Either<T, Throwable> either;
    public final @NotNull Object lock;

    private final @NotNull Clock clock;

    public JoinCallback(@NotNull Clock clock) {
        this(clock, new Object());
    }

    public JoinCallback(@NotNull Clock clock, @NotNull Object lock) {
        this.clock=Objects.requireNonNull(clock, "clock");
        this.lock=Objects.requireNonNull(lock, "lock");
    }

    public boolean completed() {
        synchronized (lock) {
            return null!=either;
        }
    }

    @Override
    public void completed(T value) {
        synchronized (lock) {
            if (null==either) {
                either=Either.left(value);
                lock.notifyAll();
            }
        }
    }

    public @NotNull Either<T, @NotNull Throwable> either() {
        synchronized (lock) {
            if (null==either) {
                throw new RuntimeException("not completed");
            }
            return either;
        }
    }

    @Override
    public void failed(@NotNull Throwable throwable) {
        synchronized (lock) {
            if (null==either) {
                either=Either.right(throwable);
                lock.notifyAll();
            }
        }
    }

    public T joinDelayNanos(long delayNanos) throws InterruptedException, JoinFailedException, TimeoutException {
        return joinEndNanos(clock.delayNanosToEndNanos(delayNanos));
    }

    public T joinEndNanos(long endNanos) throws InterruptedException, JoinFailedException, TimeoutException {
        synchronized (lock) {
            while (true) {
                if (null!=either) {
                    if (either.isLeft()) {
                        return either.left();
                    }
                    else {
                        throw new JoinFailedException(either.right());
                    }
                }
                clock.synchronizedWaitEndNanosTimeout(endNanos, lock);
            }
        }
    }

    public @NotNull Lava<T> lava() {
        @NotNull Either<T, @NotNull Throwable> either=either();
        if (either.isRight()) {
            return Lava.fail(either.right());
        }
        return Lava.complete(either.left());
    }

    public T result() throws JoinFailedException, JoinNotCompletedException {
        synchronized (lock) {
            if (null==either) {
                throw new JoinNotCompletedException();
            }
            else if (either.isRight()) {
                throw new JoinFailedException(either.right());
            }
            else {
                return either.left();
            }
        }
    }

    @Override
    public @NotNull Callback<T> singleRun() {
        return this;
    }
}
