package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.PrettyPrinter;
import hu.gds.ldap4j.Supplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface Lava<T> {
    class CatchErrorsSupplier<E extends Throwable, T> implements Lava<T> {
        private final @NotNull Function<@NotNull E, @NotNull Lava<T>> function;
        private final @NotNull Lava<T> supplier;
        private final @NotNull Class<E> type;

        public CatchErrorsSupplier(
                @NotNull Function<@NotNull E, @NotNull Lava<T>> function,
                @NotNull Lava<T> supplier,
                @NotNull Class<E> type) {
            this.function=Objects.requireNonNull(function, "function");
            this.supplier=Objects.requireNonNull(supplier, "supplier");
            this.type=Objects.requireNonNull(type, "type");
        }

        @Override
        public void get(@NotNull Callback<T> callback, @NotNull Context context) {
            try {
                callback=callback.singleRun();
                callback=callback.catchErrors(context, function, type);
                context.get(callback, supplier);
            }
            catch (Throwable throwable) {
                context.fail(callback, throwable);
            }
        }
    }

    class CompleteSupplier<T> implements Lava<T> {
        private final T value;

        public CompleteSupplier(T value) {
            this.value=value;
        }

        @Override
        public void get(@NotNull Callback<T> callback, @NotNull Context context) {
            context.complete(callback, value);
        }

        @Override
        public String toString() {
            return "CompleteSupplier("+value+")";
        }
    }

    class CompositionSupplier<T, U> implements Lava<U> {
        private final @NotNull Function<T, Lava<U>> function;
        private final @NotNull Lava<T> supplier;

        public CompositionSupplier(
                @NotNull Function<T, Lava<U>> function, @NotNull Lava<T> supplier) {
            this.function=Objects.requireNonNull(function, "function");
            this.supplier=Objects.requireNonNull(supplier, "supplier");
        }

        @Override
        public void get(@NotNull Callback<U> callback, @NotNull Context context) {
            context.get(callback.compose(context, function), supplier);
        }

        @Override
        public String toString() {
            return "CompositionSupplier(function: "+function+", supplier: "+supplier+")";
        }
    }

    class FailSupplier<T> implements Lava<T> {
        private final @NotNull Throwable throwable;

        public FailSupplier(@NotNull Throwable throwable) {
            this.throwable=Objects.requireNonNull(throwable, "throwable");
        }

        @Override
        public void get(@NotNull Callback<T> callback, @NotNull Context context) {
            context.fail(callback, throwable);
        }

        @Override
        public String toString() {
            return "FailSupplier("+throwable+")";
        }
    }

    class FinallyGetSupplier<T> implements Lava<T> {
        private final @NotNull Lava<Void> finallyBlock;
        private final @NotNull Lava<T> tryBlock;

        public FinallyGetSupplier(
                @NotNull Lava<Void> finallyBlock, @NotNull Lava<T> tryBlock) {
            this.finallyBlock=Objects.requireNonNull(finallyBlock, "finallyBlock");
            this.tryBlock=Objects.requireNonNull(tryBlock, "tryBlock");
        }

        @Override
        public void get(@NotNull Callback<T> callback, @NotNull Context context) {
            Callback<T> callback2=callback;
            try {
                callback2=callback2.singleRun();
                callback2=callback2.finallyGet(context, finallyBlock);
                context.get(callback2, tryBlock);
            }
            catch (Throwable throwable) {
                context.fail(callback2, throwable);
            }
        }

        @Override
        public String toString() {
            return "FinallyGetSupplier(finallyBlock: "+finallyBlock+", tryBlock: "+tryBlock+")";
        }
    }

    class GetContextSupplier implements Lava<@NotNull Context> {
        @Override
        public void get(@NotNull Callback<@NotNull Context> callback, @NotNull Context context) {
            context.complete(callback, context);
        }

        @Override
        public String toString() {
            return "GetContextSupplier()";
        }
    }

    class PatternMatchSupplier<T, U> implements Lava<U> {
        private final @NotNull Function<T, @NotNull Lava<U>> completed;
        private final @NotNull Function<@NotNull Throwable, @NotNull Lava<U>> failed;
        private final @NotNull Lava<T> supplier;

        public PatternMatchSupplier(
                @NotNull Function<T, @NotNull Lava<U>> completed,
                @NotNull Function<@NotNull Throwable, @NotNull Lava<U>> failed,
                @NotNull Lava<T> supplier) {
            this.completed=Objects.requireNonNull(completed, "completed");
            this.failed=Objects.requireNonNull(failed, "failed");
            this.supplier=Objects.requireNonNull(supplier, "supplier");
        }

        @Override
        public void get(@NotNull Callback<U> callback, @NotNull Context context) {
            context.get(callback.patternMatch(completed, context, failed), supplier);

        }

        @Override
        public String toString() {
            return "InductionSupplier(completed: "+completed+", failed: "+failed+", supplier: "+supplier+")";
        }
    }

    class SetContextSupplier<T> implements Lava<T> {
        private final @NotNull Context context;
        private final @NotNull Lava<T> supplier;

        public SetContextSupplier(@NotNull Context context, @NotNull Lava<T> supplier) {
            this.context=Objects.requireNonNull(context, "context");
            this.supplier=Objects.requireNonNull(supplier, "supplier");
        }

        @Override
        public void get(@NotNull Callback<T> callback, @NotNull Context context) {
            callback=callback.context(context);
            this.context.get(callback, supplier);
        }

        @Override
        public String toString() {
            return "SetContextSupplier(context: "+context+", supplier: "+supplier+")";
        }
    }

    class SupplierSupplier<T> implements Lava<T>, PrettyPrinter.PrettyPrintable {
        private final @NotNull Supplier<@NotNull Lava<T>> supplier;

        public SupplierSupplier(@NotNull Supplier<@NotNull Lava<T>> supplier) {
            this.supplier=Objects.requireNonNull(supplier, "supplier");
        }

        @Override
        public void get(@NotNull Callback<T> callback, @NotNull Context context) throws Throwable {
            @NotNull Lava<T> supplier2=Objects.requireNonNull(supplier.get(), "supplier.get()");
            context.get(callback, supplier2);
        }

        @Override
        public void prettyPrint(PrettyPrinter printer) {
            printer.printInstance(this);
            printer.printObject("supplier", supplier);
        }

        @Override
        public String toString() {
            return "%s@%x(supplier: %s)".formatted(getClass(), System.identityHashCode(this), supplier);
        }
    }

    @NotNull Lava<Void> VOID=complete(null);

    static <T> @NotNull Lava<T> addDebugMagic(
            @NotNull String debugMagic, @NotNull Supplier<@NotNull Lava<T>> supplier) {
        Objects.requireNonNull(debugMagic, "debugMagic");
        Lava<T> supplier2=Lava.supplier(supplier);
        return Lava.context()
                .compose((context)->supplier2.context(context.addDebugMagic(debugMagic)));
    }

    static <E extends Throwable, T> @NotNull Lava<T> catchErrors(
            @NotNull Function<@NotNull E, @NotNull Lava<T>> function,
            @NotNull Supplier<@NotNull Lava<T>> supplier,
            @NotNull Class<E> type) {
        return new CatchErrorsSupplier<>(function, Lava.supplier(supplier), type);
    }

    static @NotNull Lava<@NotNull Long> checkEndNanos(String timeoutMessage) {
        return context()
                .compose((context)->complete(context.checkEndNanos(timeoutMessage)));
    }

    static @NotNull Lava<@NotNull Clock> clock() {
        return Lava.context()
                .compose((context)->Lava.complete(context.clock()));
    }

    static <T> @NotNull Lava<T> complete(T value) {
        return new CompleteSupplier<>(value);
    }

    default <U> @NotNull Lava<U> compose(@NotNull Function<T, @NotNull Lava<U>> function) {
        return new CompositionSupplier<>(function, this);
    }

    default <U> @NotNull Lava<U> composeIgnoreResult(@NotNull Supplier<@NotNull Lava<U>> supplier) {
        return new CompositionSupplier<>((value)->supplier.get(), this);
    }

    static @NotNull Lava<@NotNull Context> context() {
        return new GetContextSupplier();
    }

    default @NotNull Lava<T> context(@NotNull Context context) {
        return new SetContextSupplier<>(context, this);
    }

    static <T> @NotNull Lava<T> endNanos(
            long endNanos, @NotNull Supplier<@NotNull Lava<T>> supplier) {
        Lava<T> supplier2=Lava.supplier(supplier);
        return Lava.context()
                .compose((context)->supplier2.context(context.endNanos(endNanos)));
    }

    static <T> @NotNull Lava<T> fail(@NotNull Throwable throwable) {
        return new FailSupplier<>(throwable);
    }

    static <T> @NotNull Lava<T> finallyGet(
            @NotNull Supplier<@NotNull Lava<Void>> finallyBlock,
            @NotNull Supplier<@NotNull Lava<T>> tryBlock) {
        return new FinallyGetSupplier<>(Lava.supplier(finallyBlock), Lava.supplier(tryBlock));
    }

    static @NotNull Lava<Void> finallyList(
            @NotNull List<@NotNull Supplier<@NotNull Lava<Void>>> blocks) {
        return finallyList(blocks, 0, blocks.size());
    }

    static @NotNull Lava<Void> finallyList(
            @NotNull List<@NotNull Supplier<@NotNull Lava<Void>>> blocks, int from, int to) {
        Objects.requireNonNull(blocks, "blocks");
        return Lava.supplier(()->{
            if (from>=to) {
                return Lava.VOID;
            }
            if (from+1==to) {
                return Lava.supplier(
                        Objects.requireNonNull(blocks.get(from), "blocks.get(%d)".formatted(from)));
            }
            int middle=from+(to-from)/2;
            return finallyGet(
                    ()->finallyList(blocks, middle, to),
                    ()->finallyList(blocks, from, middle));
        });
    }

    static <T, U> @NotNull Lava<@NotNull Pair<T, U>> forkJoin(
            @NotNull Supplier<@NotNull Lava<T>> left,
            @NotNull Supplier<@NotNull Lava<U>> right) {
        return new ForkJoinCallback.Supplier<>(Lava.supplier(left), Lava.supplier(right));
    }

    static <T> @NotNull Lava<@NotNull List<T>> forkJoin(
            @NotNull List<@NotNull Supplier<@NotNull Lava<T>>> suppliers) {
        int size=suppliers.size();
        return switch (size) {
            case 0 -> complete(new ArrayList<>(0));
            case 1 -> supplier(()->suppliers.get(0)
                    .get()
                    .compose((result)->{
                        List<T> list=new ArrayList<>(1);
                        list.add(result);
                        return complete(list);
                    }));
            default -> {
                int half=size/2;
                yield forkJoin(
                        ()->forkJoin(suppliers.subList(0, half)),
                        ()->forkJoin(suppliers.subList(half, size)))
                        .compose((pair)->{
                            List<T> result=new ArrayList<>(pair.first().size()+pair.second().size());
                            result.addAll(pair.first());
                            result.addAll(pair.second());
                            return complete(result);
                        });
            }
        };
    }

    /**
     * most times you should call this through Context.get()
     */
    void get(@NotNull Callback<T> callback, @NotNull Context context) throws Throwable;

    static @NotNull Lava<@NotNull Long> nowNanos() {
        return Lava.context()
                .compose((context)->Lava.complete(context.clock().nowNanos()));
    }

    static <T, U> @NotNull Lava<U> patternMatch(
            @NotNull Function<T, @NotNull Lava<U>> completed,
            @NotNull Function<@NotNull Throwable, @NotNull Lava<U>> failed,
            @NotNull Supplier<@NotNull Lava<T>> supplier) {
        return new PatternMatchSupplier<>(completed, failed, Lava.supplier(supplier));
    }

    static <T> @NotNull Lava<T> supplier(@NotNull Supplier<@NotNull Lava<T>> supplier) {
        return new SupplierSupplier<>(supplier);
    }
}
