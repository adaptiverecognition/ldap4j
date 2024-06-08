package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.net.ClosedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Pool<T, U> implements Closeable {
    private class KeepAliveLease {
        boolean leased;
        @Nullable PooledObject object;

        @NotNull Lava<Void> keepAlive() {
            return Lava.finallyList(List.of(
                    ()->{
                        if (null==object) {
                            throw new IllegalStateException();
                        }
                        return object.keepAlive()
                                .composeIgnoreResult(()->lock.enter(()->{
                                    if (closed) {
                                        return Lava.VOID;
                                    }
                                    if (!leased) {
                                        throw new IllegalStateException();
                                    }
                                    if (null==object) {
                                        throw new IllegalStateException();
                                    }
                                    return Lava.nowNanos()
                                            .compose((nowNanos)->{
                                                leased=false;
                                                --leasedObjects;
                                                queue.add(nowNanos, object);
                                                object=null;
                                                return lockCondition.signalAll();
                                            });
                                }));
                    },
                    ()->{
                        if (null==object) {
                            return Lava.VOID;
                        }
                        T object2=object.object;
                        object=null;
                        return close.apply(object2);
                    },
                    ()->{
                        if (!leased) {
                            return Lava.VOID;
                        }
                        return lock.enter(()->{
                            leased=false;
                            --leasedObjects;
                            if (null!=object) {
                                throw new IllegalStateException();
                            }
                            return lockCondition.signalAll();
                        });
                    }));
        }
    }

    private class Lease implements Closeable {
        private boolean leased;
        private @Nullable PooledObject object;
        private @NotNull Either<U, Void> wrapped=Either.right(null);

        @Override
        public @NotNull Lava<Void> close() {
            return Lava.finallyList(List.of(
                    ()->{
                        if (wrapped.isRight()) {
                            return Lava.VOID;
                        }
                        return unwrap.apply(wrapped.left())
                                .compose((unwrapped)->{
                                    if (!Boolean.TRUE.equals(unwrapped)) {
                                        return Lava.VOID;
                                    }
                                    if (null==object) {
                                        throw new IllegalStateException();
                                    }
                                    return object.keepAlive()
                                            .composeIgnoreResult(()->lock.enter(()->{
                                                if (closed) {
                                                    return Lava.VOID;
                                                }
                                                if (!leased) {
                                                    throw new IllegalStateException();
                                                }
                                                if (null==object) {
                                                    throw new IllegalStateException();
                                                }
                                                return Lava.nowNanos()
                                                        .compose((nowNanos)->{
                                                            leased=false;
                                                            --leasedObjects;
                                                            queue.add(nowNanos, object);
                                                            object=null;
                                                            return lockCondition.signalAll();
                                                        });
                                            }));
                                });
                    },
                    ()->{
                        if (null==object) {
                            return Lava.VOID;
                        }
                        T object2=object.object;
                        object=null;
                        return close.apply(object2);
                    },
                    ()->{
                        if (!leased) {
                            return Lava.VOID;
                        }
                        return lock.enter(()->{
                            leased=false;
                            --leasedObjects;
                            if (null!=object) {
                                throw new IllegalStateException();
                            }
                            return lockCondition.signalAll();
                        });
                    }));
        }

        @NotNull Lava<@NotNull Lava<@NotNull PooledObject>> factoryCriticalSection() {
            if (closed) {
                throw new ClosedException();
            }
            return Lava.nowNanos()
                    .compose((nowNanos)->{
                        if (!queue.isEmpty()) {
                            leased=true;
                            ++leasedObjects;
                            return Lava.complete(Lava.complete(queue.removeMin(nowNanos)));
                        }
                        if (size>leasedObjects) {
                            leased=true;
                            ++leasedObjects;
                            return Lava.complete(
                                    Lava.supplier(create)
                                            .compose((object)->Lava.complete(new PooledObject(nowNanos, object))));
                        }
                        return Lava.checkEndNanos(Pool.class+".lease() factory timeout")
                                .composeIgnoreResult(lockCondition::awaitEndNanos)
                                .composeIgnoreResult(this::factoryCriticalSection);
                    });
        }

        <V> @NotNull Lava<V> lease(
                @NotNull Lava<@NotNull PooledObject> factory,
                @NotNull Function<U, @NotNull Lava<V>> function) {
            return factory.compose((factoryResult)->{
                        object=factoryResult;
                        return object.keepAlive();
                    })
                    .composeIgnoreResult(()->{
                        if (null==object) {
                            throw new IllegalStateException();
                        }
                        return wrap.apply(object.object);
                    })
                    .compose((wrapResult)->{
                        wrapped=Either.left(wrapResult);
                        return function.apply(wrapResult);
                    });
        }

        <V> @NotNull Lava<V> lease(@NotNull Function<U, @NotNull Lava<V>> function) {
            return finallyClose(
                    ()->lock.enter(this::factoryCriticalSection)
                            .compose((factory)->lease(factory, function)));
        }
    }

    private class PooledObject {
        public long nextKeepAliveNanos;
        public final T object;

        public PooledObject(long nextKeepAliveNanos, T object) {
            this.nextKeepAliveNanos=nextKeepAliveNanos;
            this.object=object;
        }

        public @NotNull Lava<Void> keepAlive() {
            return Lava.nowNanos()
                    .compose((nowNanos)->{
                        if (Clock.isEndNanosInTheFuture(nextKeepAliveNanos, nowNanos)) {
                            return Lava.VOID;
                        }
                        nextKeepAliveNanos=Clock.delayNanosToEndNanos(keepAlivePeriodNanos, nowNanos);
                        return Lava.endNanos(
                                Clock.delayNanosToEndNanos(keepAliveTimeoutNanos, nowNanos),
                                ()->keepAlive.apply(object));
                    });
        }
    }

    private final @NotNull Function<T, @NotNull Lava<Void>> close;
    private boolean closed;
    private final @NotNull Supplier<@NotNull Lava<T>> create;
    private final @NotNull Function<T, @NotNull Lava<Void>> keepAlive;
    private final long keepAlivePeriodNanos;
    private boolean keepAliveRunning;
    private final long keepAliveTimeoutNanos;
    private int leasedObjects;
    private final Lock lock=new Lock();
    private final Lock.Condition lockCondition=lock.newCondition();
    private final @NotNull Log log;
    private final @NotNull MinHeap<@NotNull PooledObject> queue;
    private final int size;
    private final @NotNull Function<U, @NotNull Lava<@NotNull Boolean>> unwrap;
    private final @NotNull Function<T, @NotNull Lava<U>> wrap;

    public Pool(
            @NotNull Function<T, @NotNull Lava<Void>> close,
            @NotNull Supplier<@NotNull Lava<T>> create,
            @NotNull Function<T, @NotNull Lava<Void>> keepAlive, long keepAlivePeriodNanos,
            long keepAliveTimeoutNanos, @NotNull Log log, int size,
            @NotNull Function<U, @NotNull Lava<@NotNull Boolean>> unwrap,
            @NotNull Function<T, @NotNull Lava<U>> wrap) {
        if (0L>keepAlivePeriodNanos) {
            throw new IllegalArgumentException("0 >= keepAlivePeriodNanos %,d".formatted(keepAlivePeriodNanos));
        }
        if (0L>keepAliveTimeoutNanos) {
            throw new IllegalArgumentException("0 >= keepAliveTimeoutNanos %,d".formatted(keepAliveTimeoutNanos));
        }
        if (0>=size) {
            throw new IllegalArgumentException("0 >= size %d".formatted(size));
        }
        this.close=Objects.requireNonNull(close, "close");
        this.create=Objects.requireNonNull(create, "create");
        this.keepAlive=Objects.requireNonNull(keepAlive, "keepAlive");
        this.keepAlivePeriodNanos=keepAlivePeriodNanos;
        this.keepAliveTimeoutNanos=keepAliveTimeoutNanos;
        this.log=Objects.requireNonNull(log, "log");
        this.size=size;
        this.unwrap=Objects.requireNonNull(unwrap, "unwrap");
        this.wrap=Objects.requireNonNull(wrap, "wrap");
        queue=new MinHeap<>(size, (pooledObject)->pooledObject.nextKeepAliveNanos);
    }

    @Override
    public @NotNull Lava<Void> close() {
        return lock.enter(()->{
                    closed=true;
                    return Lava.nowNanos()
                            .compose((nowNanos)->{
                                List<T> objects=new ArrayList<>(queue.size());
                                while (!queue.isEmpty()) {
                                    objects.add(queue.removeMin(nowNanos).object);
                                }
                                return lockCondition.signalAll()
                                        .composeIgnoreResult(()->Lava.complete(objects));
                            });
                })
                .compose(this::closePooled);
    }

    private @NotNull Lava<Void> closeKeepAliveLeasedCriticalSection() {
        if ((!keepAliveRunning) && (0>=leasedObjects)) {
            return Lava.VOID;
        }
        return Lava.checkEndNanos(Pool.class+".close() lease timeout")
                .composeIgnoreResult(lockCondition::awaitEndNanos)
                .composeIgnoreResult(this::closeKeepAliveLeasedCriticalSection);
    }

    private @NotNull Lava<Void> closePooled(@NotNull List<T> objects) {
        return Lava.finallyGet(
                ()->lock.enter(this::closeKeepAliveLeasedCriticalSection),
                ()->Lava.forkJoin(
                                objects.stream()
                                        .<@NotNull Supplier<@NotNull Lava<Void>>>map(
                                                (object)->()->close.apply(object))
                                        .toList())
                        .composeIgnoreResult(()->Lava.VOID));
    }

    private @NotNull Lava<Void> keepAlive() {
        return lock.enter(this::keepAliveCriticalSection)
                .compose(Function::identity);
    }

    private @NotNull Lava<@NotNull Lava<Void>> keepAliveCriticalSection() {
        if (closed) {
            return Lava.complete(Lava.VOID);
        }
        return Lava.checkEndNanos(Pool.class+".startKeepAlive() context timeout")
                .composeIgnoreResult(()->{
                    if (queue.isEmpty()) {
                        return lockCondition.awaitEndNanos()
                                .composeIgnoreResult(this::keepAliveCriticalSection);
                    }
                    return Lava.nowNanos()
                            .compose((nowNanos)->{
                                PooledObject object=queue.peekMin();
                                if (Clock.isEndNanosInTheFuture(object.nextKeepAliveNanos, nowNanos)) {
                                    return Lava.endNanos(
                                                    object.nextKeepAliveNanos,
                                                    lockCondition::awaitEndNanos)
                                            .composeIgnoreResult(this::keepAliveCriticalSection);
                                }
                                KeepAliveLease keepAliveLease=new KeepAliveLease();
                                keepAliveLease.leased=true;
                                keepAliveLease.object=queue.removeMin(nowNanos);
                                ++leasedObjects;
                                return Lava.complete(Lava.supplier(
                                        ()->Lava.catchErrors(
                                                        (throwable)->{
                                                            log.error(Pool.class, throwable);
                                                            return Lava.VOID;
                                                        },
                                                        keepAliveLease::keepAlive,
                                                        Throwable.class
                                                )
                                                .composeIgnoreResult(this::keepAlive)));
                            });
                });
    }

    public <V> @NotNull Lava<V> lease(@NotNull Function<U, @NotNull Lava<V>> function) {
        Objects.requireNonNull(function, "function");
        return new Lease()
                .lease(function);
    }

    public @NotNull Lava<Void> startKeepAlive() {
        return lock.enter(()->{
                    if (closed) {
                        throw new ClosedException();
                    }
                    if (keepAliveRunning) {
                        throw new RuntimeException("keep-alive already started");
                    }
                    keepAliveRunning=true;
                    return Lava.VOID;
                })
                .composeIgnoreResult(Lava::context)
                .compose((context)->{
                    context.get(
                            new Callback.AbstractSingleRunCallback<>() {
                                private void completed() {
                                    lock.enterSync(
                                            context,
                                            ()->{
                                                keepAliveRunning=false;
                                                lockCondition.signalAllSync();
                                            });
                                }

                                @Override
                                protected void completedImpl(Void value) {
                                    completed();
                                }

                                @Override
                                protected void failedImpl(@NotNull Throwable throwable) {
                                    try {
                                        log.error(getClass(), throwable);
                                    }
                                    finally {
                                        completed();
                                    }
                                }
                            },
                            Lava.supplier(this::keepAlive));
                    return Lava.VOID;
                });
    }
}
