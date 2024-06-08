package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public interface Closeable {
    @NotNull Lava<Void> close();

    default <T> @NotNull Lava<T> finallyClose(@NotNull Supplier<@NotNull Lava<T>> supplier) {
        return Lava.finallyGet(this::close, supplier);
    }

    static <C, T> @NotNull Lava<T> withClose(
            @NotNull Function<C, @NotNull Lava<Void>> close,
            @NotNull Supplier<@NotNull Lava<C>> factory,
            @NotNull Function<C, @NotNull Lava<T>> function) {
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(function, "function");
        return Lava.supplier(factory)
                .compose((object)->Lava.finallyGet(
                        ()->close.apply(object),
                        ()->function.apply(object)));
    }

    static <C extends Closeable, T> @NotNull Lava<T> withCloseable(
            @NotNull Supplier<@NotNull Lava<C>> factory,
            @NotNull Function<C, @NotNull Lava<T>> function) {
        return withClose(Closeable::close, factory, function);
    }

    static <T, U> @NotNull Lava<U> wrapOrClose(
            @NotNull Function<T, @NotNull Lava<Void>> close,
            @NotNull Supplier<@NotNull Lava<T>> factory,
            @NotNull Function<T, @NotNull Lava<U>> wrap) {
        return Lava.supplier(factory)
                .compose((object)->Lava.patternMatch(
                        Lava::complete,
                        (throwable)->Lava.finallyGet(
                                ()->Lava.fail(throwable),
                                ()->close.apply(object).composeIgnoreResult(()->Lava.complete(null))),
                        ()->wrap.apply(object)));
    }

    static <T extends Closeable, U> @NotNull Lava<U> wrapOrClose(
            @NotNull Supplier<@NotNull Lava<@NotNull T>> factory,
            @NotNull Function<@NotNull T, @NotNull Lava<U>> wrap) {
        return wrapOrClose(Closeable::close, factory, wrap);
    }
}
