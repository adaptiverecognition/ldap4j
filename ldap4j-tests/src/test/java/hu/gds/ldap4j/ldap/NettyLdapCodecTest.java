package hu.gds.ldap4j.ldap;

import hu.gds.ldap4j.AbstractTest;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.Pair;
import hu.gds.ldap4j.TestParameters;
import hu.gds.ldap4j.lava.Callback;
import hu.gds.ldap4j.lava.Clock;
import hu.gds.ldap4j.lava.JoinCallback;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.net.netty.codec.NettyLdapCodec;
import hu.gds.ldap4j.net.netty.codec.RequestResponse;
import hu.gds.ldap4j.net.netty.codec.Response;
import hu.gds.ldap4j.net.netty.codec.ResponseRequest;
import hu.gds.ldap4j.net.netty.codec.SearchResponse;
import hu.gds.ldap4j.net.netty.codec.UnbindRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyLdapCodecTest {
    public interface ChannelFactory {
        class Epoll implements ChannelFactory {
            @Override
            public @NotNull Class<? extends DuplexChannel> channelType() {
                return EpollSocketChannel.class;
            }

            @Override
            public @NotNull EventLoopGroup createEventLoopGroup(int threads) {
                return new MultiThreadIoEventLoopGroup(threads, EpollIoHandler.newFactory());
            }

            @Override
            public String toString() {
                return "Epoll";
            }
        }

        class Nio implements ChannelFactory {
            @Override
            public @NotNull Class<? extends DuplexChannel> channelType() {
                return NioSocketChannel.class;
            }

            @Override
            public @NotNull EventLoopGroup createEventLoopGroup(int threads) {
                return new MultiThreadIoEventLoopGroup(threads, NioIoHandler.newFactory());
            }

            @Override
            public String toString() {
                return "Nio";
            }
        }

        @NotNull
        Class<? extends DuplexChannel> channelType();

        @NotNull
        EventLoopGroup createEventLoopGroup(int threads);
    }

    private static class ChannelHandler extends SimpleChannelInboundHandler<Response> {
        public volatile boolean boundCompleted;
        public volatile boolean inactivated;
        public final @NotNull JoinCallback<Void> join=Callback.join(Clock.SYSTEM_NANO_TIME);
        public volatile boolean searchCompleted;
        private final @NotNull TlsSettings tlsSettings;

        public ChannelHandler(@NotNull TlsSettings tlsSettings) {
            this.tlsSettings=Objects.requireNonNull(tlsSettings, "tlsSettings");
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(
                    new ResponseRequest<>(
                            hu.gds.ldap4j.ldap.BindRequest.simple(
                                            UnboundidDirectoryServer.ADMIN_USER,
                                            UnboundidDirectoryServer.ADMIN_PASSWORD.toCharArray())
                                    .controlsEmpty()));
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            inactivated=true;
            join.completed(null);
            super.channelInactive(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response object) throws Exception {
            try {
                object.visit(new Response.Visitor<Void>() {
                    @Override
                    public Void requestResponse(@NotNull RequestResponse<?> requestResponse) throws Throwable {
                        @NotNull BindResponse bindResponse=(BindResponse)requestResponse.response().message();
                        assertEquals(
                                LdapResultCode.SUCCESS,
                                bindResponse.ldapResult().resultCode2());
                        boundCompleted=true;
                        channelHandlerContext.writeAndFlush(
                                new hu.gds.ldap4j.net.netty.codec.SearchRequest(
                                        new hu.gds.ldap4j.ldap.SearchRequest(
                                                List.of("uid"), // attributes
                                                "ou=users,ou=test,dc=ldap4j,dc=gds,dc=hu", // base object
                                                DerefAliases.DEREF_ALWAYS,
                                                Filter.parse("((objectClass=*)(uid=*))"),
                                                Scope.WHOLE_SUBTREE,
                                                100, // size limit
                                                10, // time limit
                                                false) // types only
                                                .controlsEmpty()));
                        return null;
                    }

                    @Override
                    public Void searchResponse(@NotNull SearchResponse searchResponse) {
                        assertEquals(3, searchResponse.searchResults().size());
                        assertTrue(searchResponse.searchResults().get(2).message().isDone());
                        @NotNull List<@NotNull String> entries=searchResponse.searchResults()
                                .stream()
                                .filter((result)->!result.message().isDone())
                                .map((result)->result.message().asEntry())
                                .map((entry)->{
                                    PartialAttribute uidAttribute=null;
                                    for (PartialAttribute attribute: entry.attributes()) {
                                        if ("uid".equals(attribute.type().utf8())) {
                                            assertNull(uidAttribute);
                                            uidAttribute=attribute;
                                        }
                                    }
                                    assertNotNull(uidAttribute);
                                    assertEquals(1, uidAttribute.values().size());
                                    return uidAttribute.valuesUtf8().get(0);
                                })
                                .sorted()
                                .toList();
                        assertEquals(List.of("user0", "user1"), entries);
                        searchCompleted=true;
                        channelHandlerContext.writeAndFlush(new UnbindRequest());
                        return null;
                    }
                });
            }
            catch (Throwable throwable) {
                exceptionCaught(channelHandlerContext, throwable);
            }
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            try {
                ctx.channel().pipeline().addFirst(new NettyLdapCodec(
                        10_000_000_000L, // connect timeout nanos
                        Log.systemErr(),
                        10_000_000_000L, // request timeout nanos
                        tlsSettings));
                super.channelRegistered(ctx);
            }
            catch (Throwable throwable) {
                exceptionCaught(ctx, throwable);
            }
        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            Objects.requireNonNull(cause, "cause");
            join.failed(cause);
            Log.systemErr().error(getClass(), cause);
            super.exceptionCaught(ctx, cause);
        }
    }

    public record Parameters(
            @NotNull ChannelFactory channelFactory,
            @NotNull LdapTestParameters.Tls tls) {
        public Parameters(@NotNull ChannelFactory channelFactory, @NotNull LdapTestParameters.Tls tls) {
            this.channelFactory=Objects.requireNonNull(channelFactory, "channelFactory");
            this.tls=Objects.requireNonNull(tls, "tls");
        }
    }

    public static @NotNull Stream<@NotNull Parameters> parameters() {
        @NotNull List<@NotNull ChannelFactory> channelFactories=new ArrayList<>();
        if (TestParameters.linux()) {
            channelFactories.add(new ChannelFactory.Epoll());
        }
        channelFactories.add(new ChannelFactory.Nio());
        return channelFactories.stream()
                .flatMap((channelFactory)->Arrays.stream(LdapTestParameters.Tls.values())
                        .map((tls)->new Parameters(channelFactory, tls)));
    }

    @ParameterizedTest
    @MethodSource("hu.gds.ldap4j.ldap.NettyLdapCodecTest#parameters")
    public void test(@NotNull Parameters parameters) throws Throwable {
        try (UnboundidDirectoryServer ldapServer=new UnboundidDirectoryServer(
                false, AbstractTest.SERVER_PORT_CLEAR_TEXT, AbstractTest.SERVER_PORT_TLS)) {
            ldapServer.start();
            EventLoopGroup eventLoopGroup=parameters.channelFactory().createEventLoopGroup(AbstractTest.PARALLELISM);
            try {
                @NotNull Pair<@NotNull InetSocketAddress, @NotNull TlsSettings> addressTlsSettings
                        =LdapTestParameters.addressTlsSettings(
                        ldapServer::localAddressClearText,
                        ldapServer::localAddressTls,
                        parameters.tls);
                ChannelHandler channelHandler=new ChannelHandler(addressTlsSettings.second());
                DuplexChannel channel=(DuplexChannel)new Bootstrap()
                        .group(eventLoopGroup)
                        .channel(parameters.channelFactory().channelType())
                        .option(ChannelOption.AUTO_CLOSE, false)
                        .option(ChannelOption.AUTO_READ, false)
                        .handler(channelHandler)
                        .validate()
                        .connect(addressTlsSettings.first())
                        .channel();
                try {
                    channelHandler.join.joinDelayNanos(AbstractTest.TIMEOUT_NANOS);
                    assertTrue(channelHandler.boundCompleted);
                    assertTrue(channelHandler.searchCompleted);
                    assertTrue(channel.isActive());
                    assertTrue(channel.isOpen());
                    assertTrue(channelHandler.inactivated);
                }
                finally {
                    channel.close().await(AbstractTest.TIMEOUT_NANOS, TimeUnit.NANOSECONDS);
                }
            }
            finally {
                eventLoopGroup.shutdownGracefully(
                        10_000_000L,
                        10_000_000L,
                        TimeUnit.NANOSECONDS).await();
            }
        }
    }
}
