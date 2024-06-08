package hu.gds.ldap4j.reactor;

import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;

public class MonoLava<T> implements Lava<T> {
    private class SubscriberImpl implements Subscriber<T> {
        private final @NotNull Callback<T> callback;
        private boolean completed;
        private final @NotNull Context context;
        private boolean hasResult;
        private final Object lock=new Object();
        private T result;

        public SubscriberImpl(@NotNull Callback<T> callback, @NotNull Context context) {
            this.callback=Objects.requireNonNull(callback, "callback");
            this.context=Objects.requireNonNull(context, "context");
        }

        @Override
        public void onComplete() {
            synchronized (lock) {
                try {
                    if (completed) {
                        return;
                    }
                    T result2=result;
                    completed=true;
                    hasResult=false;
                    result=null;
                    context.complete(callback, result2);
                }
                catch (Throwable throwable) {
                    onError(throwable);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            synchronized (lock) {
                if (completed) {
                    return;
                }
                completed=true;
                hasResult=false;
                result=null;
                context.fail(callback, throwable);
            }
        }

        @Override
        public void onNext(T value) {
            synchronized (lock) {
                try {
                    if (completed) {
                        return;
                    }
                    if (hasResult) {
                        throw new RuntimeException(
                                "mono returned more than one result, 1. result: %s 2. result: %s, mono: %s"
                                        .formatted(result, value, mono));
                    }
                    hasResult=true;
                    result=value;
                }
                catch (Throwable throwable) {
                    onError(throwable);
                }
            }
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(2L);
        }
    }

    private final @NotNull Mono<T> mono;

    public MonoLava(@NotNull Mono<T> mono) {
        this.mono=mono;
    }

    public static <T> @NotNull Lava<T> create(@NotNull Mono<T> mono) {
        return new MonoLava<>(mono);
    }

    @Override
    public void get(@NotNull Callback<T> callback, @NotNull Context context) {
        mono.subscribe(new SubscriberImpl(callback, context));
    }
}
