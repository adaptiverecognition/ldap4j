package hu.gds.ldap4j;

import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class TestLog implements Log {
    private final Collection<@NotNull Throwable> errors=new LinkedList<>();
    private final Object lock=new Object();

    public void assertEmpty() {
        synchronized (lock) {
            if (!errors.isEmpty()) {
                System.err.printf("TestLog errors: %,d%n", errors.size());
                errors.forEach((throwable)->{
                    System.err.printf("TestLog error%n");
                    throwable.printStackTrace(System.err);
                });
                fail(errors.toString());
            }
        }
    }

    @Override
    public void error(@NotNull Class<?> component, @NotNull Throwable throwable) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(throwable, "throwable");
        throwable=new RuntimeException(
                "%s, component: %s, instant: %s, throwable: %s".formatted(
                        TestLog.class.getSimpleName(),
                        component,
                        Instant.now(),
                        throwable),
                throwable);
        synchronized (lock) {
            errors.add(throwable);
            throwable.printStackTrace(System.err);
        }
    }

    public boolean removeError(@NotNull Predicate<Throwable> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        boolean found=false;
        synchronized (lock) {
            for (Iterator<@NotNull Throwable> iterator=errors.iterator(); iterator.hasNext(); ) {
                if (predicate.test(iterator.next())) {
                    iterator.remove();
                    found=true;
                }
            }
        }
        return found;
    }

    @Override
    public String toString() {
        return "TestLog()";
    }
}
