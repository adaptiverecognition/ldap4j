package hu.gds.ldap4j;

@FunctionalInterface
public interface Supplier<T> {
    T get() throws Throwable;
}
