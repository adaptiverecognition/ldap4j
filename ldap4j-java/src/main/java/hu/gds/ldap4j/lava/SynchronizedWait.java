package hu.gds.ldap4j.lava;

import hu.gds.ldap4j.DoublyLinkedList;
import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Function;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SynchronizedWait {
    private final DoublyLinkedList<@NotNull Runnable> deque=new DoublyLinkedList<>();
    public final @NotNull Object lock;

    public SynchronizedWait(@NotNull Object lock) {
        this.lock=lock;
    }

    public SynchronizedWait() {
        this(new Object());                                  
    }

    public <T> @NotNull Lava<T> await(@NotNull Function<@NotNull Context, @NotNull Either<T, Void>> function) {
        Objects.requireNonNull(function, "function");
        return (callback, context)->awaitLoop(callback, context, function);
    }

    private <T> void awaitLoop(
            @NotNull Callback<T> callback,
            @NotNull Context context,
            @NotNull Function<@NotNull Context, @NotNull Either<T, Void>> function) {
        try {
            synchronized (lock) {
                @NotNull Either<T, Void> either=function.apply(context);
                if (either.isLeft()) {
                    context.complete(callback, either.left());
                    return;
                }
                class AwaitCallback extends Callback.AbstractSingleRunCallback<Void> {
                    private @Nullable Runnable remove;
                    private @Nullable Runnable wakeUp;

                    @Override
                    protected void completedImpl(Void value) {
                        try {
                            remove();
                            awaitLoop(callback, context, function);
                        }
                        catch (Throwable throwable) {
                            context.fail(callback, throwable);
                        }
                    }

                    @Override
                    protected void failedImpl(@NotNull Throwable throwable) {
                        try {
                            remove();
                            context.fail(callback, throwable);
                        }
                        catch (Throwable throwable2) {
                            context.fail(callback, throwable2);
                        }
                    }

                    private void remove() {
                        synchronized (lock) {
                            if (null!=remove) {
                                try {
                                    remove.run();
                                }
                                finally {
                                    remove=null;
                                }
                            }
                        }
                    }
                }
                AwaitCallback callback2=new AwaitCallback();
                callback2.wakeUp=context.awaitEndNanos(callback2);
                callback2.remove=deque.addLast(callback2.wakeUp);
            }
        }
        catch (Throwable throwable) {
            context.fail(callback, throwable);
        }
    }

    public void signal() {
        synchronized (lock) {
            if (!deque.isEmpty()) {
                deque.removeFirst().run();
            }
        }
    }

    public void signalAll() {
        synchronized (lock) {
            while (!deque.isEmpty()) {
                deque.removeFirst().run();
            }
        }
    }
}
