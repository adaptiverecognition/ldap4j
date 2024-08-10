package hu.gds.ldap4j.samples;

import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.Clock;
import hu.gds.ldap4j.lava.JoinCallback;
import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.DerefAliases;
import hu.gds.ldap4j.ldap.Filter;
import hu.gds.ldap4j.ldap.Scope;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.net.netty.codec.NettyCodec;
import hu.gds.ldap4j.net.netty.codec.RequestResponse;
import hu.gds.ldap4j.net.netty.codec.Response;
import hu.gds.ldap4j.net.netty.codec.ResponseRequest;
import hu.gds.ldap4j.net.netty.codec.SearchRequest;
import hu.gds.ldap4j.net.netty.codec.SearchResponse;
import hu.gds.ldap4j.net.netty.codec.UnbindRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;

public class NettyCodecSample {
    private static class ChannelHandler extends SimpleChannelInboundHandler<Response> {
        public final @NotNull JoinCallback<Void> join=new JoinCallback<>(Clock.SYSTEM_NANO_TIME);

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("connected");
            // authenticate
            ctx.writeAndFlush(new ResponseRequest<>(
                    hu.gds.ldap4j.ldap.BindRequest.simple(
                                    "cn=read-only-admin,dc=example,dc=com",
                                    "password".toCharArray())
                            .controlsEmpty()));
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            join.completed(null);
            super.channelInactive(ctx);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response object) throws Exception {
            try {
                object.visit(new Response.Visitor<Void>() {
                    @Override
                    public Void requestResponse(@NotNull RequestResponse<?> requestResponse) throws Throwable {
                        System.out.println("bound");
                        // look up mathematicians
                        channelHandlerContext.writeAndFlush(
                                new SearchRequest(
                                        new hu.gds.ldap4j.ldap.SearchRequest(
                                                List.of("uniqueMember"), // attributes
                                                "ou=mathematicians,dc=example,dc=com", // base object
                                                DerefAliases.DEREF_ALWAYS,
                                                Filter.parse("(objectClass=*)"),
                                                Scope.WHOLE_SUBTREE,
                                                100, // size limit
                                                10, // time limit
                                                false) // types only
                                                .controlsEmpty()));
                        return null;
                    }

                    @Override
                    public Void searchResponse(@NotNull SearchResponse searchResponse) {
                        System.out.println("mathematicians:");
                        searchResponse.searchResults().stream()
                                .map(ControlsMessage::message)
                                .filter(SearchResult::isEntry)
                                .map(SearchResult::asEntry)
                                .flatMap((entry)->entry.attributes().stream())
                                .filter((attribute)->"uniqueMember".equals(attribute.type()))
                                .flatMap((attribute)->attribute.values().stream())
                                .forEach(System.out::println);
                        // closing connection
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
                ctx.channel().pipeline().addFirst(new NettyCodec(
                        10_000_000_000L, // connect timeout nanos
                        Log.systemErr(),
                        10_000_000_000L, // request timeout nanos
                        TlsSettings.NO_TLS));
                super.channelRegistered(ctx);
            }
            catch (Throwable throwable) {
                exceptionCaught(ctx, throwable);
            }
        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            Log.systemErr().error(getClass(), cause);
            join.failed(cause);
            super.exceptionCaught(ctx, cause);
        }
    }

    public static void main(String[] args) throws Throwable {
        System.out.println("netty codec sample");
        // new thread pool
        EventLoopGroup eventLoopGroup=new NioEventLoopGroup(8);
        try {
            ChannelHandler channelHandler=new ChannelHandler();
            DuplexChannel channel=(DuplexChannel)new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.AUTO_CLOSE, false)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(channelHandler)
                    .validate()
                    .connect(new InetSocketAddress("ldap.forumsys.com", 389))
                    .channel();
            try {
                channelHandler.join.joinDelayNanos(10_000_000_000L);
            }
            finally {
                channel.close().await();
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
