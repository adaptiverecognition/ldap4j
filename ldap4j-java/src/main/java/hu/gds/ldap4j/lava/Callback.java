package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.PrettyPrinter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

public interface Callback<T> {
    abstract class AbstractSingleRunCallback<T> implements Callback<T>, SingleRun {
        private final AtomicBoolean completed=new AtomicBoolean();

        @Override
        public final void completed(T value) {
            if (completed.compareAndSet(false, true)) {
                completedImpl(value);
            }
        }

        protected abstract void completedImpl(T value);

        @Override
        public final void failed(@NotNull Throwable throwable) {
            Objects.requireNonNull(throwable, "throwable");
            if (completed.compareAndSet(false, true)) {
                failedImpl(throwable);
            }
        }

        protected abstract void failedImpl(@NotNull Throwable throwable);

        @Override
        public @NotNull Callback<T> singleRun() {
            return this;
        }
    }

    class AddSuppressedExceptionCallback<T> extends AbstractSingleRunCallback<T> {
        private final @NotNull Callback<T> callback;
        private final @NotNull Context context;
        private final @NotNull Throwable throwable;

        public AddSuppressedExceptionCallback(
                @NotNull Callback<T> callback, @NotNull Context context, @NotNull Throwable throwable) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
            this.throwable=Objects.requireNonNull(throwable, "throwable");
        }

        @Override
        protected void completedImpl(T value) {
            context.complete(callback, value);
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            try {
                if (throwable!=this.throwable) {
                    throwable.addSuppressed(this.throwable);
                }
            }
            finally {
                context.fail(callback, throwable);
            }
        }

        @Override
        public String toString() {
            return "AddSuppressedExceptionCallback(callback: "+callback
                    +", context: "+context
                    +", throwable: "+throwable+")";
        }
    }

    class CatchErrorsCallback<E extends Throwable, T> extends AbstractSingleRunCallback<T> {
        private final @NotNull Context context;
        private final @NotNull Function<@NotNull E, @NotNull Lava<T>> function;
        private final @NotNull Callback<T> callback;
        private final @NotNull Class<E> type;

        public CatchErrorsCallback(
                @NotNull Callback<T> callback,
                @NotNull Context context,
                @NotNull Function<@NotNull E, @NotNull Lava<T>> function,
                @NotNull Class<E> type) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
            this.function=Objects.requireNonNull(function, "function");
            this.type=Objects.requireNonNull(type, "type");
        }

        @Override
        protected void completedImpl(T value) {
            context.complete(callback, value);
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            Callback<T> callback2=callback;
            try {
                callback2=callback2.singleRun();
                callback2=callback2.addSuppressedException(context, throwable);
                if (type.isAssignableFrom(throwable.getClass())) {
                    context.apply(callback2, function, type.cast(throwable));
                }
                else {
                    context.fail(callback2, throwable);
                }
            }
            catch (Throwable throwable2) {
                context.fail(callback2, throwable2);
            }
        }

        @Override
        public String toString() {
            return "CatchErrorsCallback(callback: "+callback
                    +", context: "+context
                    +", function: "+function
                    +", type: "+type+")";
        }
    }

    class CompositionCallback<T, U> extends AbstractSingleRunCallback<T> implements PrettyPrinter.PrettyPrintable {
        private final @NotNull Callback<U> callback;
        private final @NotNull Context context;
        private final @NotNull Function<T, @NotNull Lava<U>> function;

        public CompositionCallback(
                @NotNull Callback<U> callback,
                @NotNull Context context,
                @NotNull Function<T, @NotNull Lava<U>> function) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
            this.function=Objects.requireNonNull(function, "function");
        }

        @Override
        protected void completedImpl(T value) {
            context.apply(callback, function, value);
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            context.fail(callback, throwable);
        }

        @Override
        public void prettyPrint(PrettyPrinter printer) {
            printer.printInstance(this);
            printer.printObject("callback", callback);
            printer.printObject("context", context);
            printer.printObject("function", function);
        }

        @Override
        public String toString() {
            return "%s@%x(callback: %s, context: %s, function: %s)".formatted(
                    getClass(), System.identityHashCode(this), callback, context, function);
        }
    }

    class ContextCallback<T> extends AbstractSingleRunCallback<T> {
        private final @NotNull Context context;
        private final @NotNull Callback<T> callback;

        public ContextCallback(@NotNull Callback<T> callback, @NotNull Context context) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
        }

        @Override
        protected void completedImpl(T value) {
            context.complete(callback, value);
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            context.fail(callback, throwable);
        }

        @Override
        public String toString() {
            return "ContextCallback(callback: "+callback+", context: "+context+")";
        }
    }

    class FinallyCompletedCallback<T, U> extends AbstractSingleRunCallback<T> {
        private final @NotNull Context context;
        private final @NotNull Callback<U> callback;
        private final U value;

        public FinallyCompletedCallback(@NotNull Callback<U> callback, @NotNull Context context, U value) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
            this.value=value;
        }

        @Override
        protected void completedImpl(T value) {
            context.complete(callback, this.value);
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            context.fail(callback, throwable);
        }

        @Override
        public String toString() {
            return "FinallyCompletedCallback(callback: "+callback+", context: "+context+", value: "+value+")";
        }
    }

    class FinallyFailedCallback<T, U> extends AbstractSingleRunCallback<T> {
        private final @NotNull Context context;
        private final @NotNull Callback<U> callback;
        private final @NotNull Throwable throwable;

        public FinallyFailedCallback(
                @NotNull Callback<U> callback, @NotNull Context context, @NotNull Throwable throwable) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
            this.throwable=Objects.requireNonNull(throwable, "throwable");
        }

        @Override
        protected void completedImpl(T value) {
            context.fail(callback, throwable);
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            try {
                throwable.addSuppressed(this.throwable);
            }
            finally {
                context.fail(callback, throwable);
            }
        }

        @Override
        public String toString() {
            return "FinallyFailedCallback(callback: "+callback+", context: "+context+", throwable: "+throwable+")";
        }
    }

    class FinallyGetCallback<T> extends AbstractSingleRunCallback<T> implements PrettyPrinter.PrettyPrintable {
        private final @NotNull Callback<T> callback;
        private final @NotNull Context context;
        private final @NotNull Lava<Void> supplier;

        public FinallyGetCallback(
                @NotNull Callback<T> callback,
                @NotNull Context context,
                @NotNull Lava<Void> supplier) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
            this.supplier=Objects.requireNonNull(supplier, "supplier");
        }

        @Override
        protected void completedImpl(T value) {
            Callback<T> callback2=callback;
            try {
                callback2=callback2.singleRun();
                context.get(new FinallyCompletedCallback<>(callback2, context, value), supplier);
            }
            catch (Throwable throwable) {
                context.fail(callback2, throwable);
            }
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            Callback<T> callback2=callback;
            try {
                callback2=callback2.singleRun();
                callback2=callback2.addSuppressedException(context, throwable);
                context.get(new FinallyFailedCallback<>(callback2, context, throwable), supplier);
            }
            catch (Throwable throwable2) {
                context.fail(callback2, throwable2);
            }
        }

        @Override
        public void prettyPrint(PrettyPrinter printer) {
            printer.printInstance(this);
            printer.printObject("callback", callback);
            printer.printObject("context", context);
            printer.printObject("supplier", supplier);
        }

        @Override
        public String toString() {
            return "%s@%x(callback: %s, context: %s, supplier: %s)".formatted(
                    getClass(), System.identityHashCode(this), callback, context, supplier);
        }
    }

    class PatternMatchCallback<T, U> extends AbstractSingleRunCallback<T> {
        private final @NotNull Function<T, @NotNull Lava<U>> completed;
        private final @NotNull Context context;
        private final @NotNull Function<@NotNull Throwable, @NotNull Lava<U>> failed;
        private final @NotNull Callback<U> callback;

        public PatternMatchCallback(
                @NotNull Callback<U> callback,
                @NotNull Function<T, @NotNull Lava<U>> completed,
                @NotNull Context context,
                @NotNull Function<@NotNull Throwable, @NotNull Lava<U>> failed) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.completed=Objects.requireNonNull(completed, "completed");
            this.context=Objects.requireNonNull(context, "context");
            this.failed=Objects.requireNonNull(failed, "failed");
        }

        @Override
        protected void completedImpl(T value) {
            @NotNull Callback<U> callback2=callback;
            try {
                callback2=callback2.singleRun();
                context.apply(callback2, completed, value);
            }
            catch (Throwable throwable) {
                context.fail(callback2, throwable);
            }
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            @NotNull Callback<U> callback2=callback;
            try {
                callback2=callback2.singleRun();
                callback2=callback2.addSuppressedException(context, throwable);
                context.apply(callback2, failed, throwable);
            }
            catch (Throwable throwable2) {
                context.fail(callback2, throwable2);
            }
        }

        @Override
        public String toString() {
            return "PatternMatchCallback(callback: +callback"
                    +", completed: "+completed
                    +", context: "+context
                    +", failed: "+failed+")";
        }
    }

    class SingleRunCallback<T> extends AbstractSingleRunCallback<T> {
        private final @NotNull Callback<T> callback;

        public SingleRunCallback(@NotNull Callback<T> callback) {
            this.callback=Objects.requireNonNull(callback, "callback");
        }

        @Override
        protected void completedImpl(T value) {
            callback.completed(value);
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            callback.failed(throwable);
        }

        @Override
        public String toString() {
            return "SingleRunCallback("+callback+")";
        }
    }

    default @NotNull Callback<T> addSuppressedException(
            @NotNull Context context, @NotNull Throwable throwable) {
        return new AddSuppressedExceptionCallback<>(this, context, throwable);
    }

    default <E extends Throwable> @NotNull Callback<T> catchErrors(
            @NotNull Context context,
            @NotNull Function<@NotNull E, @NotNull Lava<T>> function,
            @NotNull Class<E> type) {
        return new CatchErrorsCallback<>(this, context, function, type);
    }

    /**
     * most times you should call this through Context.complete()
     */
    void completed(T value);

    default <S> @NotNull Callback<S> compose(
            @NotNull Context context, @NotNull Function<S, Lava<T>> function) {
        return new CompositionCallback<>(this, context, function);
    }

    default @NotNull Callback<T> context(@NotNull Context context) {
        return new ContextCallback<>(this, context);
    }

    /**
     * most times you should call this through Context.fail()
     */
    void failed(@NotNull Throwable throwable);

    default @NotNull Callback<T> finallyGet(@NotNull Context context, Lava<Void> supplier) {
        return new FinallyGetCallback<>(this, context, supplier);
    }

    static <T> JoinCallback<T> join(@NotNull Clock clock) {
        return new JoinCallback<>(clock);
    }

    static <T> JoinCallback<T> join(@NotNull Context context) {
        return join(context.clock());
    }

    default <S> @NotNull Callback<S> patternMatch(
            @NotNull Function<S, @NotNull Lava<T>> completed,
            @NotNull Context context,
            @NotNull Function<@NotNull Throwable, @NotNull Lava<T>> failed) {
        return new PatternMatchCallback<>(this, completed, context, failed);
    }

    default @NotNull Callback<T> singleRun() {
        return new SingleRunCallback<>(this);
    }
}
