package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.TestLog;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class PoolTest {
    private static class ObjectFactory {
        private final @NotNull Set<@NotNull PrivateObject> closedObjects=new HashSet<>();
        private final @NotNull Set<@NotNull PrivateObject> createdObjects=new HashSet<>();
        private final @NotNull List<@NotNull PublicObject> leasedObjects=new ArrayList<>();
        private final Object lock=new Object();

        public void assertObjects(int created, int leased) {
            synchronized (lock) {
                assertEquals(created, closedObjects.size());
                assertEquals(created, createdObjects.size());
                assertEquals(leased, leasedObjects.size());
                assertEquals(leased, new HashSet<>(leasedObjects).size());
            }
        }

        private @NotNull Lava<Void> closeObject(@NotNull PrivateObject object) {
            return Lava.supplier(()->{
                synchronized (lock) {
                    closedObjects.add(object);
                }
                return Lava.VOID;
            });
        }

        private @NotNull Lava<@NotNull PrivateObject> createObject() {
            return Lava.supplier(()->{
                PrivateObject object=new PrivateObject();
                synchronized (lock) {
                    createdObjects.add(object);
                }
                return Lava.complete(object);
            });
        }

        public @NotNull Pool<@NotNull PrivateObject, @NotNull PublicObject> createPool(
                long keepAlivePeriodNanos, long keepAliveTimeoutNanos, @NotNull Log log, int size) {
            return new Pool<>(
                    this::closeObject,
                    this::createObject,
                    PrivateObject::keepAlive,
                    keepAlivePeriodNanos,
                    keepAliveTimeoutNanos,
                    log,
                    size,
                    PublicObject::unwrap,
                    (object)->Lava.complete(new PublicObject(object)));
        }

        public void leased(@NotNull PublicObject object) {
            synchronized (lock) {
                leasedObjects.add(object);
            }
        }
    }

    private static class PrivateObject {
        public int keepAliveSuccesses=Integer.MAX_VALUE;

        public @NotNull Lava<Void> keepAlive() {
            return Lava.supplier(()->{
                --keepAliveSuccesses;
                if (0>keepAliveSuccesses) {
                    throw new RuntimeException("keep-alive failed");
                }
                return Lava.VOID;
            });
        }
    }

    private static class PublicObject {
        public final @NotNull PrivateObject object;
        public @NotNull Supplier<@NotNull Lava<@NotNull Boolean>> unwrap=()->Lava.complete(true);

        private PublicObject(@NotNull PrivateObject object) {
            this.object=Objects.requireNonNull(object, "object");
        }

        public @NotNull Lava<@NotNull Boolean> unwrap() {
            return Lava.supplier(unwrap);
        }

        public void unwrapFail() {
            unwrap=()->Lava.fail(new RuntimeException("unwrap failed"));
        }

        public void unwrapFalse() {
            unwrap=()->Lava.complete(false);
        }
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testKeepAliveAfter(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        testLeaseSequence(
                contextHolderFactory,
                2,
                (throwable)->throwable.toString().contains("keep-alive failed"),
                (object)->{
                    object.object.keepAliveSuccesses=0;
                    return Lava.VOID;
                },
                0L,
                2,
                true);
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testKeepAliveBackground(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        TestLog log=new TestLog();
        ObjectFactory objectFactory=new ObjectFactory();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            testWithPool(
                    context,
                    (pool)->pool.lease((object)->{
                                objectFactory.leased(object);
                                object.object.keepAliveSuccesses=1;
                                log.assertEmpty();
                                return Lava.VOID;
                            })
                            .composeIgnoreResult(new Supplier<>() {
                                @Override
                                public Lava<Void> get() {
                                    return Lava.checkEndNanos("keep-alive timeout")
                                            .composeIgnoreResult(()->{
                                                if (log.removeError(
                                                        (throwable)->throwable.toString()
                                                                .contains("keep-alive failed"))) {
                                                    return Lava.VOID;
                                                }
                                                return get();
                                            });
                                }
                            }),
                    0L,
                    log,
                    objectFactory,
                    1,
                    true);
        }
        objectFactory.assertObjects(1, 1);
        log.assertEmpty();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testKeepAliveBefore(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        testLeaseSequence(
                contextHolderFactory,
                1,
                (throwable)->throwable.toString().contains("keep-alive failed"),
                (object)->{
                    object.object.keepAliveSuccesses=1;
                    return Lava.VOID;
                },
                0L,
                1,
                false);
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testKeepAliveSuccess(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        testLeaseSequence(
                contextHolderFactory,
                1,
                null,
                (object)->Lava.VOID,
                0L,
                2,
                true);
    }

    private void testLeaseSequence(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory,
            int creates,
            @Nullable Predicate<@NotNull Throwable> error,
            @NotNull Function<@NotNull PublicObject, @NotNull Lava<Void>> function,
            long keepAlivePeriodNanos,
            int leased,
            boolean startKeepAlive) throws Throwable {
        TestLog log=new TestLog();
        ObjectFactory objectFactory=new ObjectFactory();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            try {
                testWithPool(
                        context,
                        (pool)->Lava.finallyGet(
                                ()->pool.lease((object)->{
                                    objectFactory.leased(object);
                                    return Lava.VOID;
                                }),
                                ()->pool.lease((object)->{
                                    objectFactory.leased(object);
                                    return function.apply(object);
                                })),
                        keepAlivePeriodNanos,
                        log,
                        objectFactory,
                        1,
                        startKeepAlive);
                if (null!=error) {
                    fail("should have failed");
                }
            }
            catch (Throwable throwable) {
                if ((null==error) || (!error.test(throwable))) {
                    throw throwable;
                }
            }
        }
        objectFactory.assertObjects(creates, leased);
        log.assertEmpty();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testLeaseTimeout(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        TestLog log=new TestLog();
        ObjectFactory objectFactory=new ObjectFactory();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            try {
                testWithPool(
                        context,
                        (pool)->pool.lease((object0)->{
                            objectFactory.leased(object0);
                            return Lava.clock()
                                    .compose((clock)->Lava.endNanos(
                                            clock.delayNanosToEndNanos(AbstractTest.TIMEOUT_NANOS_SMALL),
                                            ()->pool.lease((object1)->Lava.fail(
                                                    new RuntimeException("should have timed out")))));
                        }),
                        log,
                        objectFactory,
                        1);
                fail();
            }
            catch (Throwable throwable) {
                assertInstanceOf(TimeoutException.class, throwable.getCause(), throwable.toString());
            }
        }
        objectFactory.assertObjects(1, 1);
        log.assertEmpty();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testParallel(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        final int sizePool=5;
        final int sizeThreads=25;
        TestLog log=new TestLog();
        ObjectFactory objectFactory=new ObjectFactory();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            testWithPool(
                    context,
                    (pool)->{
                        List<@NotNull Supplier<@NotNull Lava<Void>>> suppliers
                                =new ArrayList<>(sizeThreads);
                        for (int ii=sizeThreads; 0<ii; --ii) {
                            suppliers.add(()->pool.lease((object)->{
                                objectFactory.leased(object);
                                return Lava.VOID;
                            }));
                        }
                        return Lava.forkJoin(suppliers)
                                .composeIgnoreResult(()->Lava.VOID);
                    },
                    log,
                    objectFactory,
                    sizePool);
        }
        objectFactory.assertObjects(sizePool, sizeThreads);
        log.assertEmpty();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testReuseAll(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        final int size=5;
        TestLog log=new TestLog();
        ObjectFactory objectFactory=new ObjectFactory();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            testWithPool(
                    context,
                    new Function<>() {
                        @Override
                        public Lava<Void> apply(Pool<PrivateObject, PublicObject> value) {
                            return loopOuter(size, value);
                        }

                        private @NotNull Lava<Void> loopInner(
                                int index, @NotNull Pool<@NotNull PrivateObject, @NotNull PublicObject> pool) {
                            if (0>=index) {
                                return Lava.VOID;
                            }
                            return pool.lease((object)->{
                                objectFactory.leased(object);
                                return loopInner(index-1, pool);
                            });
                        }

                        private @NotNull Lava<Void> loopOuter(
                                int index, @NotNull Pool<@NotNull PrivateObject, @NotNull PublicObject> pool) {
                            if (0>=index) {
                                return Lava.VOID;
                            }
                            return loopInner(size, pool)
                                    .composeIgnoreResult(()->loopOuter(index-1, pool));
                        }
                    },
                    log,
                    objectFactory,
                    size);
        }
        objectFactory.assertObjects(size, size*size);
        log.assertEmpty();
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testReuseOne(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        final int size=5;
        TestLog log=new TestLog();
        ObjectFactory objectFactory=new ObjectFactory();
        try (ContextHolder context=contextHolderFactory.apply(log)) {
            context.start();
            testWithPool(
                    context,
                    new Function<>() {
                        @Override
                        public Lava<Void> apply(Pool<PrivateObject, PublicObject> value) {
                            return loop(size, value);
                        }

                        private @NotNull Lava<Void> loop(
                                int index, @NotNull Pool<@NotNull PrivateObject, @NotNull PublicObject> pool) {
                            if (0>=index) {
                                return Lava.VOID;
                            }
                            return pool.lease((object)->{
                                        objectFactory.leased(object);
                                        return Lava.VOID;
                                    })
                                    .composeIgnoreResult(()->loop(index-1, pool));
                        }
                    },
                    log,
                    objectFactory,
                    size);
        }
        objectFactory.assertObjects(1, size);
        log.assertEmpty();
    }

    private void testUnwrap(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory,
            int creates,
            @Nullable Predicate<@NotNull Throwable> error,
            @NotNull Function<@NotNull PublicObject, @NotNull Lava<Void>> function) throws Throwable {
        testLeaseSequence(
                contextHolderFactory, creates, error, function,
                AbstractTest.TIMEOUT_NANOS, 2, true);
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testUnwrapCompletedFail(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        testUnwrap(
                contextHolderFactory,
                2,
                (throwable)->throwable.toString().contains("unwrap failed"),
                (object)->{
                    object.unwrapFail();
                    return Lava.VOID;
                });
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testUnwrapCompletedFalse(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        testUnwrap(
                contextHolderFactory,
                2,
                null,
                (object)->{
                    object.unwrapFalse();
                    return Lava.VOID;
                });
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testUnwrapCompletedTrue(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        testUnwrap(
                contextHolderFactory,
                1,
                null,
                (object)->Lava.VOID);
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testUnwrapFailedFail(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        testUnwrap(
                contextHolderFactory,
                2,
                (throwable)->{
                    StringWriter writer=new StringWriter();
                    throwable.printStackTrace(new PrintWriter(writer));
                    String string=writer.toString();
                    return string.contains("function failed")
                            && string.contains("unwrap failed");
                },
                (object)->{
                    object.unwrapFail();
                    return Lava.fail(new RuntimeException("function failed"));
                });
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testUnwrapFailedFalse(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        testUnwrap(
                contextHolderFactory,
                2,
                (throwable)->throwable.toString().contains("function failed"),
                (object)->{
                    object.unwrapFalse();
                    return Lava.fail(new RuntimeException("function failed"));
                });
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.TestParameters#contextHolderFactories")
    public void testUnwrapFailedTrue(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory) throws Throwable {
        testUnwrap(
                contextHolderFactory,
                1,
                (throwable)->throwable.toString().contains("function failed"),
                (object)->Lava.fail(new RuntimeException("function failed")));
    }

    private void testWithPool(
            @NotNull ContextHolder context,
            @NotNull Function<@NotNull Pool<@NotNull PrivateObject, @NotNull PublicObject>, @NotNull Lava<Void>> function,
            @NotNull Log log,
            @NotNull ObjectFactory objectFactory,
            int size) throws Throwable {
        testWithPool(context, function, AbstractTest.TIMEOUT_NANOS, log, objectFactory, size, true);
    }

    private void testWithPool(
            @NotNull ContextHolder context,
            @NotNull Function<@NotNull Pool<@NotNull PrivateObject, @NotNull PublicObject>, @NotNull Lava<Void>> function,
            long keepAlivePeriodNanos,
            @NotNull Log log,
            @NotNull ObjectFactory objectFactory,
            int size,
            boolean startKeepAlive) throws Throwable {
        context.getOrTimeoutDelayNanos(
                AbstractTest.TIMEOUT_NANOS,
                Closeable.withCloseable(
                        ()->Lava.complete(objectFactory.createPool(keepAlivePeriodNanos, 0L, log, size)),
                        (pool)->Lava.supplier(()->startKeepAlive
                                        ?pool.startKeepAlive()
                                        :Lava.VOID)
                                .composeIgnoreResult(()->function.apply(pool))));
    }
}
