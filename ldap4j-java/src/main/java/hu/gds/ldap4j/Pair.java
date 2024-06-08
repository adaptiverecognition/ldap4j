package hu.gds.ldap4j;

import org.jetbrains.annotations.NotNull;

public record Pair<T, U>(T first, U second) {
    public static <T, U> @NotNull Pair<T, U> of(T first, U second) {
        return new Pair<>(first, second);
    }
}
