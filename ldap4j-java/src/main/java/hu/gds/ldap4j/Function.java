package hu.gds.ldap4j;

@FunctionalInterface
public interface Function<T, U> {
    U apply(T value) throws Throwable;

    static <T> T identity(T value) {
        return value;
    }
}
