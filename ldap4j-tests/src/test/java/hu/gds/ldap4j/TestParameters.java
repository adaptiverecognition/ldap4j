package hu.gds.ldap4j;

import hu.gds.ldap4j.lava.ContextHolder;
import hu.gds.ldap4j.lava.NewThreadContextHolder;
import hu.gds.ldap4j.lava.RandomTrampolineContextHolder;
import hu.gds.ldap4j.lava.ThreadPoolContextHolder;
import hu.gds.ldap4j.lava.TrampolineContextHolder;
import hu.gds.ldap4j.net.NetworkConnectionFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class TestParameters {
    public final @NotNull Function<@NotNull Log, @NotNull ContextHolder> blockingIoContextHolderFactory;
    public final @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory;
    public final @NotNull Supplier<@NotNull NetworkConnectionFactory> networkConnectionFactoryFactory;
    public final long timeoutNanos;

    public TestParameters(
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> blockingIoContextHolderFactory,
            @NotNull Function<@NotNull Log, @NotNull ContextHolder> contextHolderFactory,
            @NotNull Supplier<@NotNull NetworkConnectionFactory> networkConnectionFactoryFactory,
            long timeoutNanos) {
        if (0>timeoutNanos) {
            throw new IllegalArgumentException();
        }
        this.blockingIoContextHolderFactory
                =Objects.requireNonNull(blockingIoContextHolderFactory, "blockingIoContextHolderFactory");
        this.contextHolderFactory=Objects.requireNonNull(contextHolderFactory, "contextHolderFactory");
        this.networkConnectionFactoryFactory
                =Objects.requireNonNull(networkConnectionFactoryFactory, "networkConnectionFactoryFactory");
        this.timeoutNanos=timeoutNanos;
    }

    public static @NotNull Stream<@NotNull Function<@NotNull Log, @NotNull ContextHolder>> contextHolderFactories() {
        List<@NotNull Function<@NotNull Log, @NotNull ContextHolder>> contextHolderFactories=new ArrayList<>();
        contextHolderFactories.add(NewThreadContextHolder.factory(null));
        contextHolderFactories.add(RandomTrampolineContextHolder.factory(null));
        contextHolderFactories.add(RandomTrampolineContextHolder.factory(System.nanoTime()));
        contextHolderFactories.add(ThreadPoolContextHolder.factory(1, null));
        contextHolderFactories.add(ThreadPoolContextHolder.factory(8, null));
        contextHolderFactories.add(TrampolineContextHolder.factory());
        return contextHolderFactories.stream();
    }

    public static boolean linux() {
        return System.getProperty("os.name").toLowerCase().contains("linux");
    }

    public static @NotNull Stream<@NotNull TestParameters> stream() {
        List<@NotNull TestParameters> parameters=new ArrayList<>();
        List<@NotNull Function<@NotNull Log, @NotNull ContextHolder>> blockingIoContextHolderFactories
                =new ArrayList<>();
        blockingIoContextHolderFactories.add(NewThreadContextHolder.factory(null));
        blockingIoContextHolderFactories.add(ThreadPoolContextHolder.factory(8, null));
        List<@NotNull Supplier<@NotNull NetworkConnectionFactory>> networkConnectionFactories=new ArrayList<>();
        networkConnectionFactories.add(NetworkConnectionFactory.engineConnection());
        networkConnectionFactories.add(NetworkConnectionFactory.javaAsyncChannel());
        networkConnectionFactories.add(NetworkConnectionFactory.javaBlockingSocket());
        networkConnectionFactories.add(NetworkConnectionFactory.javaChannelPoll());
        networkConnectionFactories.add(NetworkConnectionFactory.mina());
        if (linux()) {
            networkConnectionFactories.add(NetworkConnectionFactory.nettyEpoll());
        }
        networkConnectionFactories.add(NetworkConnectionFactory.nettyNio());
        contextHolderFactories().forEach((contextHolderFactory)->{
            for (var blockingIoContextHolderFactory: blockingIoContextHolderFactories) {
                for (var networkConnectionFactory: networkConnectionFactories) {
                    parameters.add(new TestParameters(
                            blockingIoContextHolderFactory,
                            contextHolderFactory,
                            networkConnectionFactory,
                            AbstractTest.TIMEOUT_NANOS));
                }
            }
        });
        return parameters.stream();
    }

    @Override
    public String toString() {
        Map<@NotNull String, Object> map=new TreeMap<>();
        toString(map);
        StringBuilder sb=new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append('(');
        boolean first=true;
        for (Map.Entry<String, Object> entry: map.entrySet()) {
            if (first) {
                first=false;
            }
            else {
                sb.append(", ");
            }
            sb.append(entry.getKey());
            sb.append(": ");
            sb.append(entry.getValue());
        }
        sb.append(')');
        return sb.toString();
    }

    protected void toString(@NotNull Map<@NotNull String, Object> map) {
        map.put("blockingIoContextHolderFactory", blockingIoContextHolderFactory);
        map.put("contextHolderFactory", contextHolderFactory);
        map.put("networkConnectionFactoryFactory", networkConnectionFactoryFactory);
        map.put("timeoutNanos", timeoutNanos);
    }
}
