package hu.gds.ldap4j.future;

import hu.gds.ldap4j.lava.Callback;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

public class CompletableFutureCallback<T> implements Callback<T> {
    private final @NotNull CompletableFuture<T> future;

    public CompletableFutureCallback(@NotNull CompletableFuture<T> future) {
        this.future=Objects.requireNonNull(future, "future");
    }

    @Override
    public void completed(T value) {
        future.complete(value);
    }

    @Override
    public void failed(@NotNull Throwable throwable) {
        future.completeExceptionally(throwable);
    }
}
