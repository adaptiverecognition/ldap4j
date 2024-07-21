package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.TestLog;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LockTest {
    private static final int SIZE=128;

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testEnter(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        TestLog log=new TestLog();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            context.getOrTimeoutDelayNanos(
                    AbstractTest.TIMEOUT_NANOS,
                    Lava.supplier(new Supplier<>() {
                        private final Lock lock=new Lock();
                        private int value;

                        @Override
                        public @NotNull Lava<Void> get() {
                            return Lava.supplier(()->{
                                context.assertSize(0);
                                return Lava.forkJoin(()->loop(SIZE), ()->loop(SIZE))
                                        .composeIgnoreResult(()->{
                                            context.assertSize(0);
                                            assertEquals(2*SIZE, value);
                                            return Lava.VOID;
                                        });
                            });
                        }

                        private @NotNull Lava<Void> loop(int remaining) {
                            return Lava.checkEndNanos("loop timeout")
                                    .composeIgnoreResult(()->{
                                        if (0>=remaining) {
                                            return Lava.VOID;
                                        }
                                        return lock.enter(()->{
                                                    context.assertSize(1, 0);
                                                    ++value;
                                                    return Lava.VOID;
                                                })
                                                .composeIgnoreResult(()->{
                                                    context.assertSize(1, 0);
                                                    return loop(remaining-1);
                                                });
                                    });
                        }
                    }));
        }
        log.assertEmpty();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testEnterLeave(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        TestLog log=new TestLog();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            context.getOrTimeoutDelayNanos(
                    AbstractTest.TIMEOUT_NANOS,
                    Lava.supplier(new Supplier<>() {
                        private final Lock lock=new Lock();
                        private int value;

                        @Override
                        public @NotNull Lava<Void> get() {
                            return Lava.supplier(()->{
                                context.assertSize(0);
                                return Lava.forkJoin(()->loop(SIZE), ()->loop(SIZE))
                                        .composeIgnoreResult(()->{
                                            context.assertSize(0);
                                            assertEquals(2*SIZE, value);
                                            return Lava.VOID;
                                        });
                            });
                        }

                        private @NotNull Lava<Void> loop(int remaining) {
                            return Lava.checkEndNanos("loop timeout")
                                    .composeIgnoreResult(()->{
                                        if (0>=remaining) {
                                            return Lava.VOID;
                                        }
                                        return lock.enter(()->{
                                                    context.assertSize(1, 0);
                                                    return Lava.complete(value);
                                                })
                                                .compose((value2)->{
                                                    context.assertSize(1, 0);
                                                    return lock.enter(()->{
                                                                context.assertSize(1, 0);
                                                                if (value==value2) {
                                                                    ++value;
                                                                    return Lava.complete(true);
                                                                }
                                                                else {
                                                                    return Lava.complete(false);
                                                                }
                                                            })
                                                            .compose((incremented)->{
                                                                context.assertSize(1, 0);
                                                                return loop(incremented?(remaining-1):remaining);
                                                            });
                                                });
                                    });
                        }
                    }));
        }
        log.assertEmpty();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testEnterLeaveTemporarily(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        TestLog log=new TestLog();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            context.getOrTimeoutDelayNanos(
                    AbstractTest.TIMEOUT_NANOS,
                    Lava.supplier(new Supplier<>() {
                        private final Lock lock=new Lock();
                        private int value;

                        @Override
                        public @NotNull Lava<Void> get() {
                            return Lava.supplier(()->{
                                context.assertSize(0);
                                return Lava.forkJoin(()->loop(SIZE), ()->loop(SIZE))
                                        .composeIgnoreResult(()->{
                                            context.assertSize(0);
                                            assertEquals(2*SIZE, value);
                                            return Lava.VOID;
                                        });
                            });
                        }

                        private @NotNull Lava<Void> loop(int remaining) {
                            return Lava.checkEndNanos("loop timeout")
                                    .composeIgnoreResult(()->{
                                        if (0>=remaining) {
                                            return Lava.VOID;
                                        }
                                        return lock.enter(()->{
                                                    context.assertSize(1, 0);
                                                    int value2=value;
                                                    return lock.leave(()->{
                                                                context.assertSize(1, 0);
                                                                return Lava.VOID;
                                                            })
                                                            .composeIgnoreResult(()->{
                                                                context.assertSize(1, 0);
                                                                if (value==value2) {
                                                                    ++value;
                                                                    return Lava.complete(true);
                                                                }
                                                                else {
                                                                    return Lava.complete(false);
                                                                }
                                                            });
                                                })
                                                .compose((incremented)->{
                                                    context.assertSize(1, 0);
                                                    return loop(incremented?(remaining-1):remaining);
                                                });
                                    });
                        }
                    }));
        }
        log.assertEmpty();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testSignal(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        TestLog log=new TestLog();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            context.getOrTimeoutDelayNanos(
                    AbstractTest.TIMEOUT_NANOS,
                    Lava.supplier(new Supplier<>() {
                        private final Lock lock=new Lock();
                        private final Lock.Condition lockCondition=lock.newCondition();
                        private int value;

                        @Override
                        public @NotNull Lava<Void> get() {
                            return Lava.supplier(()->{
                                context.assertSize(0);
                                return Lava.forkJoin(()->thread(0), ()->thread(1))
                                        .composeIgnoreResult(()->{
                                            context.assertSize(0);
                                            assertEquals(2*SIZE, value);
                                            return Lava.VOID;
                                        });
                            });
                        }

                        private @NotNull Lava<Void> loop(int remaining, int thread) {
                            return Lava.checkEndNanos("loop timeout")
                                    .composeIgnoreResult(()->{
                                        if (0>=remaining) {
                                            return Lava.VOID;
                                        }
                                        if (thread==(value%2)) {
                                            ++value;
                                            return lockCondition.signal()
                                                    .composeIgnoreResult(()->loop(remaining-1, thread));
                                        }
                                        else {
                                            return lockCondition.awaitEndNanos()
                                                    .composeIgnoreResult(()->loop(remaining, thread));
                                        }
                                    });
                        }

                        private @NotNull Lava<Void> thread(int thread) {
                            return lock.enter(()->loop(SIZE, thread));
                        }
                    }));
        }
        log.assertEmpty();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testSignalAll(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        TestLog log=new TestLog();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            context.getOrTimeoutDelayNanos(
                    AbstractTest.TIMEOUT_NANOS,
                    Lava.supplier(new Supplier<>() {
                        private final Lock lock=new Lock();
                        private final Lock.Condition lockCondition=lock.newCondition();
                        private int value;

                        @Override
                        public @NotNull Lava<Void> get() {
                            return Lava.supplier(()->{
                                context.assertSize(0);
                                return Lava.forkJoin(List.<@NotNull Supplier<@NotNull Lava<Void>>>of(
                                                ()->thread(0),
                                                ()->thread(1),
                                                ()->thread(2)))
                                        .composeIgnoreResult(()->{
                                            context.assertSize(0);
                                            assertEquals(3*SIZE, value);
                                            return Lava.VOID;
                                        });
                            });
                        }

                        private @NotNull Lava<Void> loop(int remaining, int thread) {
                            return Lava.checkEndNanos("loop timeout")
                                    .composeIgnoreResult(()->{
                                        if (0>=remaining) {
                                            return Lava.VOID;
                                        }
                                        if (thread==(value%3)) {
                                            ++value;
                                            return lockCondition.signalAll()
                                                    .composeIgnoreResult(()->loop(remaining-1, thread));
                                        }
                                        else {
                                            return lockCondition.awaitEndNanos()
                                                    .composeIgnoreResult(()->loop(remaining, thread));
                                        }
                                    });
                        }

                        private @NotNull Lava<Void> thread(int thread) {
                            return lock.enter(()->loop(SIZE, thread));
                        }
                    }));
        }
        log.assertEmpty();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testSignalMultipleConditions(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        TestLog log=new TestLog();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            context.getOrTimeoutDelayNanos(
                    AbstractTest.TIMEOUT_NANOS,
                    Lava.supplier(new Supplier<>() {
                        private final Lock lock=new Lock();
                        private final List<Lock.@NotNull Condition> lockConditions
                                =List.of(lock.newCondition(), lock.newCondition(), lock.newCondition());
                        private int value;

                        @Override
                        public @NotNull Lava<Void> get() {
                            return Lava.supplier(()->{
                                context.assertSize(0);
                                return Lava.forkJoin(List.<@NotNull Supplier<@NotNull Lava<Void>>>of(
                                                ()->thread(0),
                                                ()->thread(1),
                                                ()->thread(2)))
                                        .composeIgnoreResult(()->{
                                            context.assertSize(0);
                                            assertEquals(3*SIZE, value);
                                            return Lava.VOID;
                                        });
                            });
                        }

                        private @NotNull Lava<Void> loop(int remaining, int thread) {
                            return Lava.checkEndNanos("loop timeout")
                                    .composeIgnoreResult(()->{
                                        if (0>=remaining) {
                                            return Lava.VOID;
                                        }
                                        if (thread==(value%3)) {
                                            ++value;
                                            return lockConditions.get((thread+1)%3).signal()
                                                    .composeIgnoreResult(()->loop(remaining-1, thread));
                                        }
                                        else {
                                            return lockConditions.get(thread).awaitEndNanos()
                                                    .composeIgnoreResult(()->loop(remaining, thread));
                                        }
                                    });
                        }

                        private @NotNull Lava<Void> thread(int thread) {
                            return lock.enter(()->loop(SIZE, thread));
                        }
                    }));
        }
        log.assertEmpty();
    }
}
