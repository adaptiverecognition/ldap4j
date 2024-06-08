package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Pair;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class ForkJoinCallback<T, U> {
    private class Fork<V> implements Callback<V>, SingleRun {
        Either<V, @NotNull Throwable> either;

        @Override
        public void completed(V value) {
            synchronized (lock) {
                try {
                    if (null==either) {
                        either=Either.left(value);
                        complete();
                    }
                }
                catch (Throwable throwable) {
                    completed=true;
                    context.fail(callback, throwable);
                }
            }
        }

        @Override
        public void failed(@NotNull Throwable throwable) {
            synchronized (lock) {
                try {
                    if (null==either) {
                        either=Either.right(throwable);
                        complete();
                    }
                }
                catch (Throwable throwable2) {
                    try {
                        if (throwable!=throwable2) {
                            throwable2.addSuppressed(throwable);
                        }
                    }
                    finally {
                        completed=true;
                        context.fail(callback, throwable2);
                    }
                }
            }
        }

        @Override
        public @NotNull Callback<V> singleRun() {
            return this;
        }

        @Override
        public String toString() {
            return "ForkJoinCallback(callback: "+callback+", context: "+context+").Fork()";
        }
    }

    public static class Supplier<T, U> implements Lava<Pair<T, U>> {
        private final @NotNull Lava<T> left;
        private final @NotNull Lava<U> right;

        public Supplier(@NotNull Lava<T> left, @NotNull Lava<U> right) {
            this.left=Objects.requireNonNull(left, "left");
            this.right=Objects.requireNonNull(right, "right");
        }

        @Override
        public void get(@NotNull Callback<Pair<T, U>> callback, @NotNull Context context) {
            ForkJoinCallback<T, U> forkJoinCallback=new ForkJoinCallback<>(callback, context);
            context.get(forkJoinCallback.left(), left);
            context.get(forkJoinCallback.right(), right);
        }

        @Override
        public String toString() {
            return "ForkJoinCallback.Supplier(left: "+left+", right: "+right+")";
        }
    }

    private final @NotNull Callback<@NotNull Pair<T, U>> callback;
    private boolean completed;
    private final @NotNull Context context;
    private final Fork<T> left=new Fork<>();
    private final Object lock=new Object();
    private final Fork<U> right=new Fork<>();

    public ForkJoinCallback(@NotNull Callback<@NotNull Pair<T, U>> callback, @NotNull Context context) {
        this.callback=Objects.requireNonNull(callback, "callback");
        this.context=Objects.requireNonNull(context, "context");
    }

    private void complete() {
        if ((!completed) && (null!=left.either) && (null!=right.either)) {
            completed=true;
            if (left.either.isLeft()) {
                if (right.either.isLeft()) {
                    context.complete(callback, Pair.of(left.either.left(), right.either.left()));
                }
                else {
                    context.fail(callback, right.either.right());
                }
            }
            else {
                if (right.either.isLeft()) {
                    context.fail(callback, left.either.right());
                }
                else {
                    try {
                        if (left.either.right()!=right.either.right()) {
                            left.either.right().addSuppressed(right.either.right());
                        }
                    }
                    finally {
                        context.fail(callback, left.either.right());
                    }
                }
            }
        }
    }

    public @NotNull Callback<T> left() {
        return left;
    }

    public @NotNull Callback<U> right() {
        return right;
    }

    @Override
    public String toString() {
        return "ForkJoinCallback(callback: "+callback+", context: "+context+")";
    }
}
