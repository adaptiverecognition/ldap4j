package hu.gds.ldap4j.reactor;

import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

public class LavaMono<T> extends Mono<T> {
    private class SubscriptionImpl extends Callback.AbstractSingleRunCallback<T> implements Subscription {
        private final AtomicBoolean requested=new AtomicBoolean();
        private final @NotNull CoreSubscriber<? super T> subscriber;

        public SubscriptionImpl(@NotNull CoreSubscriber<? super T> subscriber) {
            this.subscriber=Objects.requireNonNull(subscriber, "subscriber");
        }

        @Override
        public void cancel() {
        }

        @Override
        protected void completedImpl(T value) {
            context.execute(()->{
                try {
                    if (null!=value) {
                        subscriber.onNext(value);
                    }
                }
                finally {
                    subscriber.onComplete();
                }
            });
        }

        @Override
        protected void failedImpl(@NotNull Throwable throwable) {
            context.execute(()->subscriber.onError(throwable));
        }

        @Override
        public void request(long values) {
            if ((0L<values) && requested.compareAndSet(false, true)) {
                context.get(this, lava);
            }
        }
    }

    private final @NotNull Context context;
    private final @NotNull Lava<T> lava;

    public LavaMono(@NotNull Context context, @NotNull Lava<T> lava) {
        this.context=Objects.requireNonNull(context, "context");
        this.lava=Objects.requireNonNull(lava, "lava");
    }

    public static <T> @NotNull Mono<T> create(@NotNull Context context, @NotNull Lava<T> lava) {
        return new LavaMono<>(context, lava);
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> coreSubscriber) {
        coreSubscriber.onSubscribe(new SubscriptionImpl(coreSubscriber));
    }
}
