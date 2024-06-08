package hu.gds.ldap4j.future;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

public class Futures {
    private Futures() {
    }

    public static <T> @NotNull CompletableFuture<@NotNull Either<T, @NotNull Throwable>> capture(
            @NotNull CompletableFuture<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.<@NotNull Either<T, @NotNull Throwable>>thenApply(Either::left)
                .exceptionally(Either::right);
    }

    public static <T> @NotNull CompletableFuture<T> catchErrors(
            @NotNull Function<@NotNull Throwable, @NotNull CompletableFuture<T>> catchBlock,
            @NotNull Supplier<@NotNull CompletableFuture<T>> tryBlock) {
        return start(tryBlock)
                .thenApply(CompletableFuture::completedFuture)
                .exceptionally((throwable)->{
                    try {
                        return catchBlock.apply(throwable);
                    }
                    catch (Throwable throwable2) {
                        return CompletableFuture.failedFuture(throwable2);
                    }
                })
                .thenCompose(Function::identity);
    }

    public static <T, U> @NotNull CompletableFuture<U> compose(
            @NotNull Function<T, @NotNull CompletableFuture<U>> function,
            @NotNull Supplier<@NotNull CompletableFuture<T>> supplier) {
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(supplier, "supplier");
        return start(supplier)
                .thenComposeAsync((value)->{
                    try {
                        return function.apply(value);
                    }
                    catch (Throwable throwable) {
                        return CompletableFuture.failedFuture(throwable);
                    }
                });
    }

    public static <T> @NotNull CompletableFuture<T> finallyGet(
            @NotNull Supplier<@NotNull CompletableFuture<Void>> finallyBlock,
            @NotNull Supplier<@NotNull CompletableFuture<T>> tryBlock) {
        Objects.requireNonNull(finallyBlock, "finallyBlock");
        Objects.requireNonNull(tryBlock, "tryBlock");
        return compose(
                (tryResult)->compose(
                        (finallyResult)->{
                            if (finallyResult.isLeft()) {
                                return replay(tryResult);
                            }
                            if (tryResult.isLeft()) {
                                return CompletableFuture.failedFuture(finallyResult.right());
                            }
                            Throwable throwable0=tryResult.right();
                            Throwable throwable1=finallyResult.right();
                            if (!throwable0.equals(throwable1)) {
                                throwable0.addSuppressed(throwable1);
                            }
                            return CompletableFuture.failedFuture(throwable0);
                        },
                        ()->capture(start(finallyBlock))),
                ()->capture(start(tryBlock)));
    }

    public static <T> void handle(
            @NotNull Callback<T> callback,
            @NotNull Context context,
            @NotNull CompletableFuture<T> stage) {
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(stage, "stage");
        stage.whenCompleteAsync(
                (value, throwable)->{
                    if (null==throwable) {
                        context.complete(callback, value);
                    }
                    else {
                        context.fail(callback, throwable);
                    }
                },
                context);
    }

    public static <T> @NotNull Lava<T> handle(@NotNull Supplier<@NotNull CompletableFuture<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return (callback, context)->{
            @NotNull CompletableFuture<T> stage=Objects.requireNonNull(supplier.get(), "supplier.get()");
            handle(callback, context, stage);
        };
    }


    public static <T> @NotNull CompletableFuture<T> replay(@NotNull Either<T, @NotNull Throwable> either) {
        Objects.requireNonNull(either, "either");
        if (either.isRight()) {
            return CompletableFuture.failedFuture(either.right());
        }
        return CompletableFuture.completedFuture(either.left());
    }

    public static <T> @NotNull CompletableFuture<T> start(@NotNull Context context, @NotNull Lava<T> lava) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(lava, "lava");
        CompletableFuture<T> future=new CompletableFuture<>();
        context.get(new CompletableFutureCallback<>(future), lava);
        return future;
    }

    public static <T> @NotNull CompletableFuture<T> start(@NotNull Supplier<@NotNull CompletableFuture<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        try {
            return supplier.get();
        }
        catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    public static <T, U> @NotNull CompletableFuture<U> withClose(
            @NotNull Function<T, @NotNull CompletableFuture<Void>> close,
            @NotNull Supplier<@NotNull CompletableFuture<T>> create,
            @NotNull Function<T, @NotNull CompletableFuture<U>> function) {
        return compose(
                (object)->finallyGet(
                        ()->close.apply(object),
                        ()->function.apply(object)),
                create);
    }

    public static <T, U> @NotNull CompletableFuture<U> wrapOrClose(
            @NotNull Function<T, @NotNull CompletableFuture<Void>> close,
            @NotNull Supplier<@NotNull CompletableFuture<T>> create,
            @NotNull Function<T, @NotNull CompletableFuture<U>> function) {
        return compose(
                (object)->catchErrors(
                        (throwable)->Futures.compose(
                                (ignore)->CompletableFuture.failedFuture(throwable),
                                ()->close.apply(object)),
                        ()->function.apply(object)),
                create);
    }
}
