package hu.gds.ldap4j.lava;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record CallbackContext<T>(
        @NotNull Callback<T> callback,
        @NotNull Context context) {
    public CallbackContext(@NotNull Callback<T> callback, @NotNull Context context) {
        this.callback=Objects.requireNonNull(callback, "callback");
        this.context=Objects.requireNonNull(context, "context");
    }

    public void completed(T value) {
        context.complete(callback, value);
    }

    public void fail(@NotNull Throwable throwable) {
        context.fail(callback, throwable);
    }

    public void get(@NotNull Lava<T> lava) {
        context.get(callback, lava);
    }
}
