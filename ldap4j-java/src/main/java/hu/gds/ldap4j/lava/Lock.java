package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.DoublyLinkedList;
import hu.gds.ldap4j.Supplier;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * As there are no threads yet this is not reentrant.
 */
public class Lock {
    public class Condition {
        private final DoublyLinkedList<@NotNull EnterCallback<Void>> conditionQueue=new DoublyLinkedList<>();

        private Condition() {
        }

        public @NotNull Lava<Void> awaitEndNanos() {
            return (callback, context)->{
                EnterCallback<Void> wait=new EnterCallback<>(callback, context);
                synchronized (lock) {
                    if (!locked) {
                        throw new IllegalStateException("should own this lock");
                    }
                    wait.remove=conditionQueue.addLast(wait);
                    wait.wakeUp=context.awaitEndNanos(wait);
                }
                leave();
            };
        }

        public @NotNull Lock lock() {
            return Lock.this;
        }

        public @NotNull Lava<Void> signal() {
            return Lava.supplier(()->{
                signalSync();
                return Lava.VOID;
            });
        }

        public void signalSync() {
            synchronized (lock) {
                if (!locked) {
                    throw new IllegalStateException("should own this lock");
                }
                if (!conditionQueue.isEmpty()) {
                    conditionQueue.removeFirst().wakeUpSynchronized();
                }
            }
        }

        public @NotNull Lava<Void> signalAll() {
            return Lava.supplier(()->{
                signalAllSync();
                return Lava.VOID;
            });
        }

        public void signalAllSync() {
            synchronized (lock) {
                if (!locked) {
                    throw new IllegalStateException("should own this lock");
                }
                while (!conditionQueue.isEmpty()) {
                    conditionQueue.removeFirst().wakeUpSynchronized();
                }
            }
        }
    }

    private class EnterCallback<T> extends Callback.AbstractSingleRunCallback<T> {
        private final @NotNull Callback<T> callback;
        private final @NotNull Context context;
        private @Nullable Runnable remove;
        private @Nullable Runnable wakeUp;

        private EnterCallback(@NotNull Callback<T> callback, @NotNull Context context) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
        }

        private void completed(@NotNull Runnable runnable) {
            synchronized (lock) {
                if (null!=remove) {
                    remove.run();
                }
            }
            enter(runnable);
        }

        @Override
        protected void completedImpl(T value) {
            completed(()->context.complete(callback, value));
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            completed(()->context.fail(callback, throwable));
        }

        private void wakeUpSynchronized() {
            if (null==wakeUp) {
                throw new IllegalStateException();
            }
            wakeUp.run();
            wakeUp=null;
        }
    }

    private final Object lock=new Object();
    private boolean locked;
    private final Deque<@NotNull Runnable> lockQueue=new LinkedList<>();

    private void enter(@NotNull Runnable wait) {
        Objects.requireNonNull(wait, "wait");
        synchronized (lock) {
            if (locked) {
                lockQueue.addLast(wait);
                return;
            }
            locked=true;
        }
        wait.run();
    }

    public <T> @NotNull Lava<T> enter(@NotNull Supplier<@NotNull Lava<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return (callback, context)->
                enter(()->context.get(
                        callback,
                        Lava.finallyGet(
                                ()->{
                                    leave();
                                    return Lava.VOID;
                                },
                                supplier)));
    }

    public void enterSync(@NotNull Context context, @NotNull Runnable runnable) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(runnable, "runnable");
        enter(()->context.execute(()->{
            try {
                runnable.run();
            }
            finally {
                leave();
            }
        }));
    }

    private void leave() {
        @NotNull Runnable wait;
        synchronized (lock) {
            if (!locked) {
                throw new IllegalStateException("should own this lock");
            }
            if (lockQueue.isEmpty()) {
                locked=false;
                return;
            }
            wait=lockQueue.removeFirst();
        }
        wait.run();
    }

    public <T> @NotNull Lava<T> leave(@NotNull Supplier<@NotNull Lava<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return (callback, context)->{
            leave();
            context.get(new EnterCallback<>(callback, context), Lava.supplier(supplier));
        };
    }

    public @NotNull Condition newCondition() {
        return new Condition();
    }
}
