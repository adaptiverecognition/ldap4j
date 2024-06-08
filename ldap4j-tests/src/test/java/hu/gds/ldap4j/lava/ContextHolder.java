package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Log;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public abstract class ContextHolder implements AutoCloseable {
    protected final @NotNull Log log;

    public ContextHolder(@NotNull Log log) {
        this.log=Objects.requireNonNull(log, "log");
    }

    public void assertSize(int size) {
        assertSize(size, size);
    }

    public void assertSize(int maxSize, int minSize) {
    }

    public abstract @NotNull Clock clock();

    @Override
    public abstract void close();

    public abstract @NotNull Context context();

    /**
     * This is for junit tests.
     */
    public void getOrTimeoutDelayNanos(long delayNanos, Lava<Void> supplier) throws Throwable {
        getOrTimeoutEndNanos(clock().delayNanosToEndNanos(delayNanos), supplier);
    }

    /**
     * This is for junit tests.
     */
    public abstract <T> T getOrTimeoutEndNanos(long endNanos, Lava<T> supplier) throws Throwable;

    public abstract void start();
}
