package hu.gds.ldap4j.reactor;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public class Monos {
    private Monos() {
    }

    public static <T> @NotNull Mono<@NotNull Either<T, @NotNull Throwable>> capture(
            @NotNull Supplier<@NotNull Mono<T>> mono) {
        return supplier(mono)
                .<@NotNull Either<T, @NotNull Throwable>>map(Either::left)
                .switchIfEmpty(Mono.just(Either.left(null)))
                .onErrorResume((throwable)->Mono.just(Either.right(throwable)));
    }

    public static <T, U> @NotNull Mono<U> compose(
            @NotNull Mono<T> mono, @NotNull Function<T, @NotNull Mono<U>> function) {
        return mono.<Mono<U>>map((value)->{
                    try {
                        return function.apply(value);
                    }
                    catch (Throwable throwable) {
                        return Mono.error(throwable);
                    }
                })
                .switchIfEmpty(Monos.supplier(()->Mono.just(function.apply(null))))
                .flatMap(Function::identity);
    }

    public static <T> @NotNull Mono<T> finallyGet(
            @NotNull Supplier<@NotNull Mono<Void>> finallyBlock, @NotNull Supplier<@NotNull Mono<T>> tryBlock) {
        Objects.requireNonNull(finallyBlock, "finallyBlock");
        Objects.requireNonNull(tryBlock, "tryBlock");
        return compose(
                capture(tryBlock),
                (tryResult)->compose(
                        capture(finallyBlock),
                        (finallyResult)->{
                            if (finallyResult.isLeft()) {
                                return replay(tryResult);
                            }
                            if (tryResult.isLeft()) {
                                return Mono.error(finallyResult.right());
                            }
                            Throwable throwable0=tryResult.right();
                            Throwable throwable1=finallyResult.right();
                            if (!throwable0.equals(throwable1)) {
                                throwable0.addSuppressed(throwable1);
                            }
                            return Mono.error(throwable0);
                        }));
    }

    public static <T> @NotNull Mono<T> replay(@NotNull Either<T, @NotNull Throwable> either) {
        Objects.requireNonNull(either, "either");
        if (either.isLeft()) {
            return Mono.justOrEmpty(either.left());
        }
        return Mono.error(either.right());
    }

    public static <T> @NotNull Mono<T> supplier(@NotNull Supplier<@NotNull Mono<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return Mono.just("")
                .<Mono<T>>map((ignore)->{
                    try {
                        return supplier.get();
                    }
                    catch (Throwable throwable) {
                        return Mono.error(throwable);
                    }
                })
                .flatMap(Function::identity);
    }
}
