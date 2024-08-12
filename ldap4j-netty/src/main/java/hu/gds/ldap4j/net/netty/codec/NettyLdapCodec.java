package hu.gds.ldap4j.net.netty.codec;

import hu.gds.ldap4j.Either;
import hu.gds.ldap4j.Exceptions;
import hu.gds.ldap4j.Log;
import hu.gds.ldap4j.lava.JoinCallback;
import hu.gds.ldap4j.lava.Lava;
import hu.gds.ldap4j.ldap.AddRequest;
import hu.gds.ldap4j.ldap.BindRequest;
import hu.gds.ldap4j.ldap.CompareRequest;
import hu.gds.ldap4j.ldap.ControlsMessage;
import hu.gds.ldap4j.ldap.DeleteRequest;
import hu.gds.ldap4j.ldap.ExtendedRequest;
import hu.gds.ldap4j.ldap.LdapConnection;
import hu.gds.ldap4j.ldap.ModifyDNRequest;
import hu.gds.ldap4j.ldap.ModifyRequest;
import hu.gds.ldap4j.ldap.Response;
import hu.gds.ldap4j.ldap.SearchResult;
import hu.gds.ldap4j.net.ByteBuffer;
import hu.gds.ldap4j.net.ClosedException;
import hu.gds.ldap4j.net.TlsSettings;
import hu.gds.ldap4j.net.netty.NettyBuffers;
import hu.gds.ldap4j.trampoline.EngineConnection;
import hu.gds.ldap4j.trampoline.LavaEngine;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DuplexChannel;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NettyLdapCodec extends ChannelDuplexHandler {
    private static abstract class CallbackState<T> extends Connected {
        protected final @NotNull JoinCallback<T> callback;

        public CallbackState(
                @NotNull JoinCallback<T> callback,
                @NotNull EngineConnection engineConnection,
                @NotNull LavaEngine lavaEngine,
                @NotNull LdapConnection ldapConnection,
                @NotNull Deque<@NotNull Request> requests) {
            super(engineConnection, lavaEngine, ldapConnection, requests);
            this.callback=callback;
        }
    }

    private static class Closed extends State {
        @Override
        public @NotNull State channelActive(Channel channel, @NotNull List<@NotNull Event> events) {
            return this;
        }

        @Override
        public @NotNull State channelInactive(@NotNull List<@NotNull Event> events) {
            return this;
        }

        @Override
        public @NotNull State channelRead(@NotNull List<@NotNull Event> events, @NotNull ByteBuffer value) {
            return this;
        }

        @Override
        public @NotNull State crankEngine(@NotNull List<@NotNull Event> events) {
            return this;
        }

        @Override
        public @NotNull State exceptionCaught(@NotNull List<@NotNull Event> events, @NotNull Throwable throwable) {
            events.add((ctx)->ctx.fireExceptionCaught(throwable));
            return this;
        }

        @Override
        public void runLavaEngine() {
        }

        @Override
        public @NotNull State write(@NotNull List<@NotNull Event> events, @NotNull Request request) {
            throw new ClosedException();
        }

        @Override
        public void writeEngineConnection(ChannelHandlerContext ctx) {
        }
    }

    private static abstract class Connected extends Engine {
        protected final @NotNull LdapConnection ldapConnection;

        public Connected(
                @NotNull EngineConnection engineConnection,
                @NotNull LavaEngine lavaEngine,
                @NotNull LdapConnection ldapConnection,
                @NotNull Deque<@NotNull Request> requests) {
            super(engineConnection, lavaEngine, requests);
            this.ldapConnection=ldapConnection;
        }
    }

    private class Connecting extends Engine {
        private final @NotNull JoinCallback<@NotNull LdapConnection> callback;

        public Connecting(
                @NotNull JoinCallback<@NotNull LdapConnection> callback,
                @NotNull EngineConnection engineConnection,
                @NotNull LavaEngine lavaEngine,
                @NotNull Deque<@NotNull Request> requests) {
            super(engineConnection, lavaEngine, requests);
            this.callback=Objects.requireNonNull(callback, "callback");
        }

        @Override
        public @NotNull State crankEngine(@NotNull List<@NotNull Event> events) {
            if (callback.completed()) {
                @Nullable Either<@NotNull LdapConnection, @NotNull Throwable> either=callback.either();
                if (either.isRight()) {
                    throw new RuntimeException(either.right());
                }
                else {
                    events.add(ChannelHandlerContext::fireChannelActive);
                    return new WaitingForRequests(engineConnection, lavaEngine, either.left(), requests);
                }
            }
            else {
                events.add(ChannelHandlerContext::read);
                return this;
            }
        }
    }

    private static abstract class Engine extends NotError {
        protected final @NotNull EngineConnection engineConnection;
        protected final @NotNull LavaEngine lavaEngine;

        public Engine(
                @NotNull EngineConnection engineConnection,
                @NotNull LavaEngine lavaEngine,
                @NotNull Deque<@NotNull Request> requests) {
            super(requests);
            this.engineConnection=Objects.requireNonNull(engineConnection, "engineConnection");
            this.lavaEngine=Objects.requireNonNull(lavaEngine, "lavaEngine");
        }

        @Override
        public @NotNull State channelActive(Channel channel, @NotNull List<@NotNull Event> events) {
            throw new IllegalStateException("already active");
        }

        @Override
        public @NotNull State channelInactive(@NotNull List<@NotNull Event> events) throws Throwable {
            engineConnection.addReadBuffer(null);
            return this;
        }

        @Override
        public @NotNull State channelRead(
                @NotNull List<@NotNull Event> events, @NotNull ByteBuffer value) throws Throwable {
            engineConnection.addReadBuffer(value);
            return this;
        }

        @Override
        public void runLavaEngine() throws Throwable {
            lavaEngine.runAll();
        }

        @Override
        public void writeEngineConnection(ChannelHandlerContext ctx) {
            @Nullable ByteBuffer byteBuffer=engineConnection.removeWriteBuffer();
            if (null==byteBuffer) {
                ((DuplexChannel)ctx.channel()).shutdownOutput();
            }
            else if (!byteBuffer.isEmpty()) {
                ctx.writeAndFlush(NettyBuffers.toNetty(byteBuffer));
            }
        }
    }

    private static class Error extends State {
        private final @NotNull Throwable throwable;

        public Error(@NotNull Throwable throwable) {
            this.throwable=Objects.requireNonNull(throwable, "throwable");
        }

        @Override
        public @NotNull State channelActive(Channel channel, @NotNull List<@NotNull Event> events) {
            return this;
        }

        @Override
        public @NotNull State channelInactive(@NotNull List<@NotNull Event> events) {
            return this;
        }

        @Override
        public @NotNull State channelRead(@NotNull List<@NotNull Event> events, @NotNull ByteBuffer value) {
            return this;
        }

        @Override
        public @NotNull State crankEngine(@NotNull List<@NotNull Event> events) {
            return this;
        }

        @Override
        public @NotNull State exceptionCaught(@NotNull List<@NotNull Event> events, @NotNull Throwable throwable) {
            events.add((ctx)->ctx.fireExceptionCaught(throwable));
            return new Error(Exceptions.joinNotNull(this.throwable, throwable));
        }

        @Override
        public void runLavaEngine() {
        }

        @Override
        public @NotNull State write(@NotNull List<@NotNull Event> events, @NotNull Request request) {
            events.add((ctx)->ctx.fireExceptionCaught(throwable));
            return this;
        }

        @Override
        public void writeEngineConnection(ChannelHandlerContext ctx) {
        }
    }

    @FunctionalInterface
    private interface Event {
        void fire(ChannelHandlerContext context) throws Throwable;
    }

    private static abstract class NotError extends State {
        protected final @NotNull Deque<@NotNull Request> requests;

        public NotError(@NotNull Deque<@NotNull Request> requests) {
            this.requests=Objects.requireNonNull(requests, "request");
        }

        @Override
        public @NotNull State exceptionCaught(@NotNull List<@NotNull Event> events, @NotNull Throwable throwable) {
            events.add((ctx)->ctx.fireExceptionCaught(throwable));
            return new Error(throwable);
        }

        @Override
        public @NotNull State write(@NotNull List<@NotNull Event> events, @NotNull Request request) {
            Objects.requireNonNull(request, "request");
            requests.add(request);
            return this;
        }
    }

    private static abstract class Requesting<T> extends CallbackState<T> {
        public Requesting(
                @NotNull JoinCallback<T> callback,
                @NotNull EngineConnection engineConnection,
                @NotNull LavaEngine lavaEngine,
                @NotNull LdapConnection ldapConnection,
                @NotNull Deque<@NotNull Request> requests) {
            super(callback, engineConnection, lavaEngine, ldapConnection, requests);
        }

        protected abstract @NotNull State completed(@NotNull List<@NotNull Event> events, T response) throws Throwable;

        @Override
        public @NotNull State crankEngine(@NotNull List<@NotNull Event> events) throws Throwable {
            if (callback.completed()) {
                @Nullable Either<T, @NotNull Throwable> either=callback.either();
                if (either.isRight()) {
                    throw new RuntimeException(either.right());
                }
                else {
                    return completed(events, either.left());
                }
            }
            else {
                events.add(ChannelHandlerContext::read);
                return this;
            }
        }
    }

    private class ResponseRequesting<R extends Response> extends Requesting<@NotNull ControlsMessage<R>> {
        public ResponseRequesting(
                @NotNull JoinCallback<@NotNull ControlsMessage<R>> callback,
                @NotNull EngineConnection engineConnection,
                @NotNull LavaEngine lavaEngine,
                @NotNull LdapConnection ldapConnection,
                @NotNull Deque<@NotNull Request> requests) {
            super(callback, engineConnection, lavaEngine, ldapConnection, requests);
        }

        @Override
        protected @NotNull State completed(
                @NotNull List<@NotNull Event> events, @NotNull ControlsMessage<R> response) {
            events.add((ctx)->ctx.fireChannelRead(new RequestResponse<>(response)));
            return new WaitingForRequests(engineConnection, lavaEngine, ldapConnection, requests);
        }
    }

    private class Searching extends Requesting<@NotNull List<@NotNull ControlsMessage<SearchResult>>> {
        public Searching(
                @NotNull JoinCallback<@NotNull List<@NotNull ControlsMessage<SearchResult>>> callback,
                @NotNull EngineConnection engineConnection,
                @NotNull LavaEngine lavaEngine,
                @NotNull LdapConnection ldapConnection,
                @NotNull Deque<@NotNull Request> requests) {
            super(callback, engineConnection, lavaEngine, ldapConnection, requests);
        }

        @Override
        protected @NotNull State completed(
                @NotNull List<@NotNull Event> events, @NotNull List<@NotNull ControlsMessage<SearchResult>> response) {
            events.add((ctx)->ctx.fireChannelRead(new hu.gds.ldap4j.net.netty.codec.SearchResponse(response)));
            return new WaitingForRequests(engineConnection, lavaEngine, ldapConnection, requests);
        }
    }

    private static abstract class State {
        public abstract @NotNull State channelActive(
                Channel channel,
                @NotNull List<@NotNull Event> events)
                throws Throwable;

        public abstract @NotNull State channelInactive(
                @NotNull List<@NotNull Event> events)
                throws Throwable;

        public abstract @NotNull State channelRead(
                @NotNull List<@NotNull Event> events,
                @NotNull ByteBuffer value)
                throws Throwable;

        public abstract @NotNull State crankEngine(
                @NotNull List<@NotNull Event> events)
                throws Throwable;

        public abstract @NotNull State exceptionCaught(
                @NotNull List<@NotNull Event> events,
                @NotNull Throwable throwable)
                throws Throwable;

        public abstract void runLavaEngine() throws Throwable;

        public abstract @NotNull State write(
                @NotNull List<@NotNull Event> events,
                @NotNull Request request)
                throws Throwable;

        public abstract void writeEngineConnection(ChannelHandlerContext ctx) throws Throwable;
    }

    private class Unactivated extends NotError {
        public Unactivated() {
            super(new LinkedList<>());
        }

        @Override
        public @NotNull State channelActive(Channel channel, @NotNull List<@NotNull Event> events) {
            @NotNull InetSocketAddress remoteAddress=(InetSocketAddress)channel.remoteAddress();
            @NotNull EngineConnection engineConnection=new EngineConnection(
                    (InetSocketAddress)channel.localAddress(),
                    remoteAddress);
            @NotNull LavaEngine lavaEngine=new LavaEngine(log);
            @NotNull JoinCallback<@NotNull LdapConnection> callback
                    =lavaEngine.contextEndNanos(lavaEngine.clock().delayNanosToEndNanos(connectTimeoutNanos))
                    .get(LdapConnection.factory(
                            (remoteAddress2)->Lava.complete(engineConnection),
                            null,
                            remoteAddress,
                            tlsSettings));
            return new Connecting(callback, engineConnection, lavaEngine, requests);
        }

        @Override
        public @NotNull State channelInactive(@NotNull List<@NotNull Event> events) {
            throw new IllegalStateException("not activated yet");
        }

        @Override
        public @NotNull State channelRead(@NotNull List<@NotNull Event> events, @NotNull ByteBuffer value) {
            throw new IllegalStateException("not activated yet");
        }

        @Override
        public @NotNull State crankEngine(@NotNull List<@NotNull Event> events) {
            return this;
        }

        @Override
        public void runLavaEngine() {
        }

        @Override
        public void writeEngineConnection(ChannelHandlerContext ctx) {
        }
    }

    private static class Unbinding extends Requesting<Void> {
        public Unbinding(
                @NotNull JoinCallback<Void> callback,
                @NotNull EngineConnection engineConnection,
                @NotNull LavaEngine lavaEngine,
                @NotNull LdapConnection ldapConnection,
                @NotNull Deque<@NotNull Request> requests) {
            super(callback, engineConnection, lavaEngine, ldapConnection, requests);
        }

        @Override
        protected @NotNull State completed(@NotNull List<@NotNull Event> events, Void response) {
            events.add(ChannelHandlerContext::fireChannelInactive);
            return new Closed();
        }
    }

    private class WaitingForRequests extends Connected {
        public WaitingForRequests(
                @NotNull EngineConnection engineConnection,
                @NotNull LavaEngine lavaEngine,
                @NotNull LdapConnection ldapConnection,
                @NotNull Deque<@NotNull Request> requests) {
            super(engineConnection, lavaEngine, ldapConnection, requests);
        }

        @Override
        public @NotNull State crankEngine(@NotNull List<@NotNull Event> events) throws Throwable {
            if (requests.isEmpty()) {
                return this;
            }
            @NotNull Request request=requests.removeFirst();
            return request.visit(new Request.Visitor<>() {
                @Override
                public State responseRequest(@NotNull ResponseRequest<?, ?> responseRequest) throws Throwable {
                    return responseRequest.request()
                            .message()
                            .visit(new hu.gds.ldap4j.ldap.Request.Visitor<>() {
                                       @Override
                                       public @NotNull State addRequest(@NotNull AddRequest addRequest) {
                                           return request(addRequest);
                                       }

                                       @Override
                                       public @NotNull State bindRequest(@NotNull BindRequest bindRequest) {
                                           return request(bindRequest);
                                       }

                                       @Override
                                       public @NotNull State compareRequest(@NotNull CompareRequest compareRequest) {
                                           return request(compareRequest);
                                       }

                                       @Override
                                       public @NotNull State deleteRequest(@NotNull DeleteRequest deleteRequest) {
                                           return request(deleteRequest);
                                       }

                                       @Override
                                       public @NotNull State extendedRequest(@NotNull ExtendedRequest extendedRequest) {
                                           return request(extendedRequest);
                                       }

                                       @Override
                                       public @NotNull State modifyDNRequest(@NotNull ModifyDNRequest modifyDNRequest) {
                                           return request(modifyDNRequest);
                                       }

                                       @Override
                                       public @NotNull State modifyRequest(@NotNull ModifyRequest modifyRequest) {
                                           return request(modifyRequest);
                                       }

                                       private <M extends hu.gds.ldap4j.ldap.Request<M, R>, R extends Response>
                                       @NotNull State request(
                                               @NotNull M request) {
                                           @NotNull JoinCallback<@NotNull ControlsMessage<R>> callback
                                                   =lavaEngine.contextEndNanos(
                                                           lavaEngine.clock()
                                                                   .delayNanosToEndNanos(requestTimeoutNanos))
                                                   .get(ldapConnection.writeRequestReadResponseChecked(
                                                           request.controls(
                                                                   responseRequest.request().controls())));
                                           return new ResponseRequesting<>(
                                                   callback,
                                                   engineConnection,
                                                   lavaEngine,
                                                   ldapConnection,
                                                   requests);
                                       }
                                   }
                            );
                }

                @Override
                public @NotNull State searchRequest(@NotNull SearchRequest searchRequest) {
                    @NotNull JoinCallback<@NotNull List<@NotNull ControlsMessage<SearchResult>>> callback
                            =lavaEngine.contextEndNanos(lavaEngine.clock().delayNanosToEndNanos(requestTimeoutNanos))
                            .get(ldapConnection.search(searchRequest.searchRequest()));
                    return new Searching(
                            callback,
                            engineConnection,
                            lavaEngine,
                            ldapConnection,
                            requests);
                }

                @Override
                public State unbindRequest(@NotNull UnbindRequest unbindRequest) {
                    @NotNull JoinCallback<Void> callback
                            =lavaEngine.contextEndNanos(lavaEngine.clock().delayNanosToEndNanos(requestTimeoutNanos))
                            .get(ldapConnection.close());
                    return new Unbinding(
                            callback,
                            engineConnection,
                            lavaEngine,
                            ldapConnection,
                            requests);
                }
            });
        }
    }

    private final long connectTimeoutNanos;
    private final @NotNull Log log;
    private final long requestTimeoutNanos;
    private @NotNull State state=new Unactivated();
    private final @NotNull TlsSettings tlsSettings;

    public NettyLdapCodec(
            long connectTimeoutNanos,
            @NotNull Log log,
            long requestTimeoutNanos,
            @NotNull TlsSettings tlsSettings) {
        this.connectTimeoutNanos=connectTimeoutNanos;
        this.log=Objects.requireNonNull(log, "log");
        this.requestTimeoutNanos=requestTimeoutNanos;
        this.tlsSettings=Objects.requireNonNull(tlsSettings, "tlsSettings");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        try {
            @NotNull List<@NotNull Event> events=new ArrayList<>();
            state=state.channelActive(ctx.channel(), events);
            crankEngine(ctx, events);
        }
        catch (Throwable throwable) {
            exceptionCaught(ctx, throwable);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        try {
            @NotNull List<@NotNull Event> events=new ArrayList<>();
            state=state.channelInactive(events);
            crankEngine(ctx, events);
        }
        catch (Throwable throwable) {
            exceptionCaught(ctx, throwable);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            Objects.requireNonNull(msg);
            ByteBuf byteBuf=(ByteBuf)msg;
            @NotNull ByteBuffer value=NettyBuffers.fromNetty(byteBuf);
            @NotNull List<@NotNull Event> events=new ArrayList<>();
            state=state.channelRead(events, value);
            crankEngine(ctx, events);
        }
        catch (Throwable throwable) {
            exceptionCaught(ctx, throwable);
        }
    }

    private void crankEngine(ChannelHandlerContext ctx, @NotNull List<@NotNull Event> events) throws Throwable {
        Objects.requireNonNull(events, "events");
        while (true) {
            state.runLavaEngine();
            @NotNull State state2=state;
            state=state2.crankEngine(events);
            if (state.equals(state2)) {
                break;
            }
        }
        state.writeEngineConnection(ctx);
        for (@NotNull Event event: events) {
            event.fire(ctx);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        try {
            Objects.requireNonNull(cause);
            @NotNull List<@NotNull Event> events=new ArrayList<>();
            state=state.exceptionCaught(events, cause);
            crankEngine(ctx, events);
        }
        catch (Throwable throwable) {
            ctx.fireExceptionCaught(throwable);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        try {
            Objects.requireNonNull(msg);
            Request request=(Request)msg;
            @NotNull List<@NotNull Event> events=new ArrayList<>();
            state=state.write(events, request);
            crankEngine(ctx, events);
        }
        catch (Throwable throwable) {
            exceptionCaught(ctx, throwable);
        }
    }
}
