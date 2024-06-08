package hu.gds.ldap4j.lava;

import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;

public interface Clock {
    class SystemNanoTime implements Clock {
        @Override
        public long nowNanos() {
            return System.nanoTime();
        }
    }

    SystemNanoTime SYSTEM_NANO_TIME=new SystemNanoTime();

    default int checkDelayMillis(long endNanos, String timeoutMessage) throws Throwable {
        long delayNanos=checkDelayNanos(endNanos, timeoutMessage);
        int delayMillis=(int)Math.max(1L, Math.min(Integer.MAX_VALUE, delayNanos/1_000_000L));
        if (1_000_000L*delayMillis<delayNanos) {
            ++delayMillis;
        }
        return delayMillis;
    }

    default long checkDelayNanos(long endNanos, String timeoutMessage) throws TimeoutException {
        return checkDelayNanos(endNanos, nowNanos(), timeoutMessage);
    }

    static long checkDelayNanos(long endNanos, long nowNanos, String timeoutMessage) throws TimeoutException {
        long delayNanos=endNanosToDelayNanos(endNanos, nowNanos);
        if (0>=delayNanos) {
            throw new TimeoutException(timeoutMessage);
        }
        return delayNanos;
    }

    default void checkEndNanos(long endNanos, String timeoutMessage) throws TimeoutException {
        checkEndNanos(endNanos, nowNanos(), timeoutMessage);
    }

    static void checkEndNanos(long endNanos, long nowNanos, String timeoutMessage) throws TimeoutException {
        checkDelayNanos(endNanos, nowNanos, timeoutMessage);
    }

    default int compareEndNanos(long endNanos0, long endNanos1) {
        return compareEndNanos(endNanos0, endNanos1, nowNanos());
    }

    static int compareEndNanos(long endNanos0, long endNanos1, long nowNanos) {
        return Long.compare(endNanos0-nowNanos, endNanos1-nowNanos);
    }

    default long delayNanosToEndNanos(long delayNanos) {
        return delayNanosToEndNanos(delayNanos, nowNanos());
    }

    static long delayNanosToEndNanos(long delayNanos, long nowNanos) {
        return nowNanos+Math.max(0L, delayNanos);
    }

    default long endNanosToDelayNanos(long endNanos) {
        return endNanosToDelayNanos(endNanos, nowNanos());
    }

    static long endNanosToDelayNanos(long endNanos, long nowNanos) {
        return Math.max(0L, endNanos-nowNanos);
    }

    default boolean isEndNanosInTheFuture(long endNanos) {
        return isEndNanosInTheFuture(endNanos, nowNanos());
    }

    static boolean isEndNanosInTheFuture(long endNanos, long nowNanos) {
        return 0<endNanosToDelayNanos(endNanos, nowNanos);
    }

    long nowNanos();

    static void synchronizedWaitDelayNanos(long delayNanos, @NotNull Object object) throws InterruptedException {
        if (0L<delayNanos) {
            object.wait(delayNanos/1_000_000L, (int)(delayNanos%1_000_000L));
        }
    }

    static void synchronizedWaitEndNanosTimeout(
            long endNanos, long nowNanos, @NotNull Object object) throws InterruptedException, TimeoutException {
        if (endNanos<=nowNanos) {
            throw new TimeoutException();
        }
        synchronizedWaitDelayNanos(endNanos-nowNanos, object);
    }

    default void synchronizedWaitEndNanosTimeout(
            long endNanos, @NotNull Object object) throws InterruptedException, TimeoutException {
        synchronizedWaitEndNanosTimeout(endNanos, nowNanos(), object);
    }

    static void threadJoinDelayNanos(long delayNanos, @NotNull Thread thread) throws InterruptedException {
        if (0L<delayNanos) {
            thread.join(delayNanos/1_000_000L, (int)(delayNanos%1_000_000L));
        }
    }
}
