package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.jetbrains.annotations.NotNull;

/*
Mergesort: cpu bound, lava shenanigans kept at minimum.
Quicksort: lava bound, negligible real work. No one should do quicksort like this.
*/
public class Benchmark {
    public static final int COLUMNS=78;
    public static final int ITERATIONS=5;
    public static final int MERGESORT_ITERATIONS=10;
    public static final int MERGESORT_SIZE=10_000_000;
    public static final int QUICKSORT_ITERATIONS=10;
    public static final int QUICKSORT_SIZE=10_000;
    public static final long SEED=345345L;
    public static final long TIMEOUT_NANOS=600_000_000_000L;

    private int line;

    public static void main(String[] args) throws Throwable {
        new Benchmark()
                .main2();
    }

    private void main2() throws Throwable {
        ScheduledExecutorService executor=Executors.newScheduledThreadPool(Context.defaultParallelism());
        try {
            @NotNull Supplier<@NotNull Context> scheduled=scheduled(executor);
            @NotNull Supplier<@NotNull Context> threadLocal16=threadLocal(executor, 16);
            @NotNull Supplier<@NotNull Context> threadLocal256=threadLocal(executor, 256);
            for (int ii=ITERATIONS; 0<ii; --ii) {
                System.out.println();
                line=0;
                measureAndPrint(scheduled, mergesort());
                measureAndPrint(threadLocal16, mergesort());
                measureAndPrint(threadLocal256, mergesort());
                measureAndPrint(scheduled, quicksort());
                measureAndPrint(threadLocal16, quicksort());
                measureAndPrint(threadLocal256, quicksort());
            }
        }
        finally {
            executor.shutdown();
        }
    }

    private void measureAndPrint(
            @NotNull Supplier<@NotNull Context> contextFactory,
            @NotNull Supplier<@NotNull Lava<Void>> lavaSupplier) throws Throwable {
        ++line;
        String name=lavaSupplier+" / "+contextFactory;
        System.out.print(name);
        System.out.flush();
        long executionNanos=measureExecutionNanos(contextFactory, lavaSupplier);
        String time="%,d ms".formatted(executionNanos/1_000_000L);
        int paddingLength=COLUMNS-name.length()-time.length();
        if (1>=paddingLength) {
            System.out.print(' ');
        }
        else {
            char paddingChar=(0==(line&1))?'.':' ';
            System.out.print(' ');
            for (int ii=paddingLength-2; 0<ii; --ii) {
                System.out.print(paddingChar);
            }
            System.out.print(' ');
        }
        System.out.println(time);
    }

    private long measureExecutionNanos(
            @NotNull Supplier<@NotNull Context> contextFactory,
            @NotNull Supplier<@NotNull Lava<Void>> supplier) throws Throwable {
        Context context=contextFactory.get();
        JoinCallback<Void> join=Callback.join(context);
        long startNanos=context.clock().nowNanos();
        context.get(join, supplier.get());
        join.joinEndNanos(context.endNanos());
        long endNanos=context.clock().nowNanos();
        return endNanos-startNanos;
    }

    private static @NotNull Supplier<@NotNull Lava<Void>> mergesort() {
        return new Supplier<>() {
            @Override
            public @NotNull Lava<Void> get() {
                return loop(MERGESORT_ITERATIONS, new Random(SEED));
            }

            private static @NotNull Lava<Void> loop(int iterations, @NotNull Random random) {
                if (0>=iterations) {
                    return Lava.VOID;
                }
                byte[] buffer=new byte[MERGESORT_SIZE];
                random.nextBytes(buffer);
                return Lava.context()
                        .compose((context)->sort(
                                buffer,
                                0,
                                Math.min(context.parallelism(), MERGESORT_SIZE),
                                MERGESORT_SIZE)
                                .composeIgnoreResult(()->loop(iterations-1, random)));
            }

            private static byte[] merge(byte[] buffer0, byte[] buffer1) {
                int l0=buffer0.length;
                int l1=buffer1.length;
                byte[] buffer=new byte[l0+l1];
                int ii=0;
                int i0=0;
                int i1=0;
                while ((l0>i0) && (l1>i1)) {
                    byte b0=buffer0[i0];
                    byte b1=buffer1[i1];
                    byte bb;
                    if (b0<=b1) {
                        bb=b0;
                        ++i0;
                    }
                    else {
                        bb=b1;
                        ++i1;
                    }
                    buffer[ii]=bb;
                    ++ii;
                }
                for (; l0>i0; ++i0, ++ii) {
                    buffer[ii]=buffer0[i0];
                }
                for (; l1>i1; ++i1, ++ii) {
                    buffer[ii]=buffer1[i1];
                }
                return buffer;
            }

            private static @NotNull Lava<byte[]> sort(byte[] buffer, int from, int threads, int to) {
                return Lava.supplier(()->{
                    int size=to-from;
                    if (size<threads) {
                        throw new IllegalStateException();
                    }
                    if (1==threads) {
                        return Lava.complete(sort(buffer, from, to));
                    }
                    int half=threads/2;
                    int split=from+size*half/threads;
                    return Lava.forkJoin(
                                    ()->sort(buffer, from, half, split),
                                    ()->sort(buffer, split, threads-half, to))
                            .compose((pair)->Lava.complete(merge(pair.first(), pair.second())));
                });
            }

            private static byte[] sort(byte[] buffer, int from, int to) {
                if (from>=to) {
                    return new byte[0];
                }
                if (from+1==to) {
                    return new byte[]{buffer[from]};
                }
                int middle=(from+to)/2;
                return merge(sort(buffer, from, middle), sort(buffer, middle, to));
            }

            @Override
            public String toString() {
                return "mergesort(%,d x %,d)".formatted(MERGESORT_ITERATIONS, MERGESORT_SIZE);
            }
        };
    }

    private static @NotNull Supplier<@NotNull Lava<Void>> quicksort() {
        return new Supplier<>() {
            @Override
            public @NotNull Lava<Void> get() {
                return loop(QUICKSORT_ITERATIONS, new Random(SEED));
            }

            private static @NotNull Lava<Void> loop(int iterations, @NotNull Random random) {
                if (0>=iterations) {
                    return Lava.VOID;
                }
                byte[] buffer=new byte[QUICKSORT_SIZE];
                random.nextBytes(buffer);
                return sort(buffer, 0, QUICKSORT_SIZE)
                        .composeIgnoreResult(()->loop(iterations-1, random));
            }

            private static void swap(byte[] buffer, int index0, int index1) {
                byte temp=buffer[index0];
                buffer[index0]=buffer[index1];
                buffer[index1]=temp;
            }

            private static @NotNull Lava<Void> sort(byte[] buffer, int from, int to) {
                return Lava.supplier(()->{
                    if (from+1>=to) {
                        return Lava.VOID;
                    }
                    return sortLoop(buffer, from, from+1, to, to-1);
                });
            }

            private static @NotNull Lava<Void> sortLoop(byte[] buffer, int from, int lower, int to, int upper) {
                return Lava.supplier(()->{
                    if (lower<=upper) {
                        if (buffer[from]>=buffer[lower]) {
                            return sortLoop(buffer, from, lower+1, to, upper);
                        }
                        if (buffer[from]<=buffer[upper]) {
                            return sortLoop(buffer, from, lower, to, upper-1);
                        }
                        if (lower<upper) {
                            swap(buffer, lower, upper);
                            return sortLoop(buffer, from, lower+1, to, upper-1);
                        }
                    }
                    if (from!=lower-1) {
                        swap(buffer, from, lower-1);
                    }
                    return Lava.forkJoin(
                                    ()->sort(buffer, from, upper),
                                    ()->sort(buffer, lower, to))
                            .composeIgnoreResult(()->Lava.VOID);
                });
            }

            @Override
            public String toString() {
                return "quicksort(%,d x %,d)".formatted(QUICKSORT_ITERATIONS, QUICKSORT_SIZE);
            }
        };
    }

    private static @NotNull Supplier<@NotNull Context> scheduled(@NotNull ScheduledExecutorService executor) {
        return new Supplier<>() {
            @Override
            public @NotNull Context get() {
                return ScheduledExecutorContext.createDelayNanos(
                        Benchmark.TIMEOUT_NANOS,
                        executor,
                        Log.systemErr(),
                        Context.defaultParallelism());
            }

            @Override
            public String toString() {
                return "scheduled";
            }
        };
    }

    private static @NotNull Supplier<@NotNull Context> threadLocal(
            @NotNull ScheduledExecutorService executor, int localSize) {
        return new Supplier<>() {
            @Override
            public @NotNull Context get() {
                return ThreadLocalScheduledExecutorContext.createDelayNanos(
                        Benchmark.TIMEOUT_NANOS,
                        executor,
                        localSize,
                        Log.systemErr(),
                        Context.defaultParallelism(),
                        new ThreadLocal<>());
            }

            @Override
            public String toString() {
                return "threadLocal(%,d)".formatted(localSize);
            }
        };
    }
}
