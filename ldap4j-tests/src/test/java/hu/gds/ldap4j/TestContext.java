package hu.gds.ldap4j;

import hu.gds.ldap4j.lava.ContextHolder;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.NetworkConnectionFactory;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public record TestContext<P extends TestParameters>(
        @NotNull ContextHolder blockingIoContextHolder,
        @NotNull ContextHolder contextHolder,
        long endNanos,
        @NotNull TestLog log,
        @NotNull P parameters,
        @NotNull NetworkConnectionFactory networkConnectionFactory)
        implements AutoCloseable {
    public TestContext(
            @NotNull ContextHolder blockingIoContextHolder,
            @NotNull ContextHolder contextHolder,
            long endNanos,
            @NotNull TestLog log,
            @NotNull P parameters,
            @NotNull NetworkConnectionFactory networkConnectionFactory) {
        this.blockingIoContextHolder=Objects.requireNonNull(blockingIoContextHolder, "blockingIoContextHolder");
        this.contextHolder=Objects.requireNonNull(contextHolder, "contextHolder");
        this.endNanos=endNanos;
        this.log=Objects.requireNonNull(log, "log");
        this.parameters=Objects.requireNonNull(parameters, "parameters");
        this.networkConnectionFactory=Objects.requireNonNull(networkConnectionFactory, "networkConnectionFactory");
    }

    public void assertSize(int size) {
        contextHolder.assertSize(size);
    }

    @Override
    public void close() {
        try {
            try {
                networkConnectionFactory.close();
            }
            finally {
                contextHolder.close();
            }
        }
        finally {
            try {
                blockingIoContextHolder.close();
            }
            finally {
                log.assertEmpty();
            }
        }
    }

    public static <P extends TestParameters> @NotNull TestContext<P> create(
            @NotNull P parameters) throws Throwable {
        TestLog log=new TestLog();
        boolean error=true;
        ContextHolder blockingIoContextHolder=parameters.blockingIoContextHolderFactory.apply(log);
        try {
            ContextHolder contextHolder=parameters.contextHolderFactory.apply(log);
            try {
                NetworkConnectionFactory networkConnectionFactory=parameters.networkConnectionFactoryFactory.get();
                try {
                    TestContext<P> context=new TestContext<>(
                            blockingIoContextHolder,
                            contextHolder,
                            contextHolder.clock().delayNanosToEndNanos(parameters.timeoutNanos),
                            log,
                            parameters,
                            networkConnectionFactory);
                    contextHolder.start();
                    blockingIoContextHolder.start();
                    error=false;
                    return context;
                }
                finally {
                    if (error) {
                        networkConnectionFactory.close();
                    }
                }
            }
            finally {
                if (error) {
                    contextHolder.close();
                }
            }
        }
        finally {
            if (error) {
                blockingIoContextHolder.close();
            }
        }
    }

    public <T> T get(@NotNull Lava<T> supplier) throws Throwable {
        return contextHolder.getOrTimeoutEndNanos(endNanos, supplier);
    }
}
