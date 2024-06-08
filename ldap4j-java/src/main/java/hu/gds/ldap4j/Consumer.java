package hu.gds.ldap4j;

@FunctionalInterface
public interface Consumer<T> {
    void accept(T value) throws Throwable;
}
