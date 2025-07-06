package hu.gds.ldap4j.net;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Function;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Supplier;
import hu.gds.ldap4j.lava.Context;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.net.mina.MinaConnection;
import hu.gds.ldap4j.net.netty.NettyConnection;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.net.SocketOption;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.mina.core.service.IoProcessor;
import org.apache.mina.transport.socket.nio.NioProcessor;
import org.apache.mina.transport.socket.nio.NioSession;
import org.jetbrains.annotations.NotNull;

public interface NetworkConnectionFactory extends AutoCloseable {
    @Override
    void close();

    @NotNull Function<@NotNull InetSocketAddress, Lava<@NotNull DuplexConnection>> factory(
            @NotNull Context blockingIoContext,
            @NotNull Log log,
            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions)
            throws Throwable;

    static @NotNull Supplier<@NotNull NetworkConnectionFactory> engineConnection() {
        return new Supplier<>() {
            @Override
            public NetworkConnectionFactory get() {
                return new NetworkConnectionFactory() {
                    @Override
                    public void close() {
                    }

                    @Override
                    public @NotNull Function<@NotNull InetSocketAddress, Lava<@NotNull DuplexConnection>> factory(
                            @NotNull Context blockingIoContext,
                            @NotNull Log log,
                            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
                        return TestEngineConnection.factory(
                                JavaAsyncChannelConnection.factory(null, socketOptions),
                                log);
                    }

                    @Override
                    public String toString() {
                        return "NetworkConnectionFactory.engineConnection()()";
                    }
                };
            }

            @Override
            public String toString() {
                return "NetworkConnectionFactory.engineConnection()";
            }
        };
    }

    static @NotNull Supplier<@NotNull NetworkConnectionFactory> javaAsyncChannel() {
        return new Supplier<>() {
            @Override
            public NetworkConnectionFactory get() {
                return new NetworkConnectionFactory() {
                    @Override
                    public void close() {
                    }

                    @Override
                    public @NotNull Function<@NotNull InetSocketAddress, Lava<@NotNull DuplexConnection>> factory(
                            @NotNull Context blockingIoContext,
                            @NotNull Log log,
                            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
                        return JavaAsyncChannelConnection.factory(null, socketOptions);
                    }

                    @Override
                    public String toString() {
                        return "NetworkConnectionFactory.javaAsyncChannel()()";
                    }
                };
            }

            @Override
            public String toString() {
                return "NetworkConnectionFactory.javaAsyncChannel()";
            }
        };
    }

    static @NotNull Supplier<@NotNull NetworkConnectionFactory> javaBlockingSocket() {
        return new Supplier<>() {
            @Override
            public NetworkConnectionFactory get() {
                return new NetworkConnectionFactory() {
                    @Override
                    public void close() {
                    }

                    @Override
                    public @NotNull Function<@NotNull InetSocketAddress, Lava<@NotNull DuplexConnection>> factory(
                            @NotNull Context blockingIoContext,
                            @NotNull Log log,
                            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
                        return JavaBlockingSocketConnection.factory(blockingIoContext, socketOptions);
                    }

                    @Override
                    public String toString() {
                        return "NetworkConnectionFactory.javaBlockingSocket()()";
                    }
                };
            }

            @Override
            public String toString() {
                return "NetworkConnectionFactory.javaBlockingSocket()";
            }
        };
    }

    static @NotNull Supplier<@NotNull NetworkConnectionFactory> javaChannelPoll() {
        return new Supplier<>() {
            @Override
            public NetworkConnectionFactory get() {
                return new NetworkConnectionFactory() {
                    @Override
                    public void close() {
                    }

                    @Override
                    public @NotNull Function<@NotNull InetSocketAddress, Lava<@NotNull DuplexConnection>> factory(
                            @NotNull Context blockingIoContext,
                            @NotNull Log log,
                            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
                        return JavaChannelPollConnection.factory(socketOptions);
                    }

                    @Override
                    public String toString() {
                        return "NetworkConnectionFactory.javaChannelPoll()";
                    }
                };
            }

            @Override
            public String toString() {
                return "NetworkConnectionFactory.javaChannelPoll()";
            }
        };
    }

    default boolean mayCloseOnEof() {
        return false;
    }

    static @NotNull Supplier<@NotNull NetworkConnectionFactory> mina() {
        return new Supplier<>() {
            @Override
            public NetworkConnectionFactory get() {
                return new NetworkConnectionFactory() {
                    private ScheduledExecutorService executor;
                    private IoProcessor<NioSession> processor;

                    @Override
                    public void close() {
                        try {
                            try {
                                if (null!=processor) {
                                    processor.dispose();
                                }
                            }
                            finally {
                                if (null!=executor) {
                                    executor.shutdownNow();
                                }
                            }
                        }
                        finally {
                            processor=null;
                            executor=null;
                        }
                    }

                    @Override
                    public @NotNull Function<@NotNull InetSocketAddress, Lava<@NotNull DuplexConnection>> factory(
                            @NotNull Context blockingIoContext,
                            @NotNull Log log,
                            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
                        if (null==processor) {
                            if (null==executor) {
                                executor=Executors.newScheduledThreadPool(AbstractTest.PARALLELISM);
                            }
                            processor=new NioProcessor(executor);
                        }
                        return MinaConnection.factory(processor, socketOptions);
                    }

                    @Override
                    public String toString() {
                        return "NetworkConnectionFactory.mina()(executor: %s, processor: %s)"
                                .formatted(executor, processor);
                    }
                };
            }

            @Override
            public String toString() {
                return "NetworkConnectionFactory.mina()";
            }
        };
    }

    private static @NotNull Supplier<@NotNull NetworkConnectionFactory> netty(
            Class<? extends DuplexChannel> channelType,
            IoHandlerFactory ioHandlerFactory,
            boolean mayCloseOnEof) {
        return new Supplier<>() {
            @Override
            public NetworkConnectionFactory get() {
                return new NetworkConnectionFactory() {
                    private EventLoopGroup eventLoopGroup;

                    @Override
                    public void close() {
                        if (null!=eventLoopGroup) {
                            try {
                                eventLoopGroup.shutdownGracefully(10L, 10L, TimeUnit.MILLISECONDS)
                                        .await(10L, TimeUnit.MILLISECONDS);
                            }
                            catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                            finally {
                                eventLoopGroup=null;
                            }
                        }
                    }

                    @Override
                    public @NotNull Function<@NotNull InetSocketAddress, Lava<@NotNull DuplexConnection>> factory(
                            @NotNull Context blockingIoContext,
                            @NotNull Log log,
                            @NotNull Map<@NotNull SocketOption<?>, @NotNull Object> socketOptions) {
                        if (null==eventLoopGroup) {
                            eventLoopGroup=new MultiThreadIoEventLoopGroup(8, ioHandlerFactory);
                        }
                        return NettyConnection.factory(channelType, eventLoopGroup, socketOptions);
                    }

                    @Override
                    public boolean mayCloseOnEof() {
                        return mayCloseOnEof;
                    }

                    @Override
                    public String toString() {
                        return "NetworkConnectionFactory.netty()("+eventLoopGroup+")";
                    }
                };
            }

            @Override
            public String toString() {
                return "NetworkConnectionFactory.netty(channelType: %s)".formatted(channelType);
            }
        };
    }

    static @NotNull Supplier<@NotNull NetworkConnectionFactory> nettyEpoll() {
        return netty(EpollSocketChannel.class, EpollIoHandler.newFactory(), true);
    }

    static @NotNull Supplier<@NotNull NetworkConnectionFactory> nettyNio() {
        return netty(NioSocketChannel.class, NioIoHandler.newFactory(), false);
    }
}
