# Ldap4j

Ldap4j is a java LDAP client, which can be used to query directory services.
Its main goals are to be fully non-blocking, correct,
and host environment and transport agnostic.

Also check the [external issues file](https://github.com/adaptiverecognition/ldap4j/blob/master/EXTERNAL-ISSUES.md)
to help you choose an LDAP client implementation.

## Table of contents
 
- [Features](#features)
- [Maven](#maven)
- [How to use](#how-to-use)
  - [Future](#future)
  - [Lava](#lava)
  - [Netty codec](#netty-codec)
  - [Reactor](#reactor)
  - [Trampoline](#trampoline)
  - [Parallel operations](#parallel-operations)
  - [TLS renegotiation](#tls-renegotiation)
- [ldap4j.sh](#ldap4jsh)
- [Docs](#docs)
  - [DNS lookups](#dns-lookups)
  - [I/O exceptions](#io-exceptions)
  - [Lava](#lava-1)
    - [Executor](#executor)
    - [Lava engine](#lava-engine)
    - [Trampoline](#trampoline-1)
  - [Transports](#transports)
    - [Apache MINA](#apache-mina)
    - [Engine connection](#engine-connection)
    - [Java NIO channel, asynchronous](#java-nio-channel-asynchronous)
    - [Java NIO channel, polling](#java-nio-channel-polling)
    - [Netty](#netty)
- [License](#license)

## Features

Ldap4j is an [LDAP v3](https://www.ietf.org/rfc/rfc4511.txt) client.
It's fully non-blocking, and supports timeouts on all operations.

Ldap4j currently supports:
- all operations defined in [LDAP v3](https://www.ietf.org/rfc/rfc4511.txt), except removal of the TLS Layer,
- [absolute true and false filters](https://www.ietf.org/rfc/rfc4526.txt),
- [all operational attributes](https://www.ietf.org/rfc/rfc3673.txt),
- [assertion control](https://www.ietf.org/rfc/rfc4528.txt),
- [attributes by object class](https://www.ietf.org/rfc/rfc4529.txt),
- [cancel operation](https://www.ietf.org/rfc/rfc3909.txt),
- [don't use copy control](https://www.ietf.org/rfc/rfc6171.txt),
- [fast bind operation](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-adts/58bbd5c4-b5c4-41e2-b12c-cdaad1223d6a),
- [feature discovery](https://www.ietf.org/rfc/rfc3674.txt),
- [manage DSA IT control](https://www.ietf.org/rfc/rfc3296.txt),
- [matched values control](https://www.ietf.org/rfc/rfc3876.txt),
- [modify-increment operation](https://www.ietf.org/rfc/rfc4525.txt),
- [password modify operation](https://www.ietf.org/rfc/rfc3062.txt),
- [read entry controls](https://www.ietf.org/rfc/rfc4527.txt),
- [server side sorting control](https://www.ietf.org/rfc/rfc2891.txt),
- [simple paged results control](https://www.ietf.org/rfc/rfc2696.txt),
- [transactions](https://www.ietf.org/rfc/rfc5805.txt),
- ["Who am I?" operation](https://www.ietf.org/rfc/rfc4532.txt).

Ldap4j supports TLS, through the standard StartTLS operation, and it also supports the non-standard LDAPS protocol.
It supports an optional host name verification in both cases.
Ldap4j supports TLS renegotiations.

Ldap4j supports separate executors for TLS handshake tasks.

A connection pool is provided to support traditional parallelism, and amortize the cost of TCP and TLS handshakes.

Ldap4j supports parallel operations using a single connection.

All operations are non-blocking,
the client should never wait for parallel results by blocking the current thread.

All operations are subject to a timeout.
All operations return a neutral result on a timeout, or raise an exception.
The acquisitions and releases of system resources are not subject to timeouts.

Ldap4j is host environment agnostic,
it can be used in a wide variety of environments with some glue logic.
Glue has been written for:
- [CompletableFutures](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html)
- [Project Reactor](https://projectreactor.io/)
- [ScheduledExecutorServices](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ScheduledExecutorService.html)
- synchronous execution.

Ldap4j is also transport agnostic.
Currently, it supports the following libraries:
- [Apache MINA](https://mina.apache.org/)
- Java NIO
- [Netty](https://netty.io/).

## Maven

Various dependencies are neatly packaged into submodules. Choose according to your need.

```xml
    <dependency>
        <groupId>com.adaptiverecognition</groupId>
        <artifactId>ldap4j-java</artifactId>
        <version>1.2.2</version>
    </dependency>
```

```xml
    <dependency>
        <groupId>com.adaptiverecognition</groupId>
        <artifactId>ldap4j-mina</artifactId>
        <version>1.2.2</version>
    </dependency>
```

```xml
    <dependency>
        <groupId>com.adaptiverecognition</groupId>
        <artifactId>ldap4j-netty</artifactId>
        <version>1.2.2</version>
    </dependency>
```

```xml
    <dependency>
        <groupId>com.adaptiverecognition</groupId>
        <artifactId>ldap4j-reactor-netty</artifactId>
        <version>1.2.2</version>
    </dependency>
```

## How to use

The samples subproject contains several short examples how ldap4j can be used.
All samples do the simple task of:
- connecting to the public ldap server [ldap.forumsys.com:389](https://www.forumsys.com/2022/05/10/online-ldap-test-server/),
- authenticating itself to the server,
- and querying the users of the mathematicians group.

The result of executing one of the samples should look something like this:

    ldap4j trampoline sample
    connected
    bound
    mathematicians:
    uid=euclid,dc=example,dc=com
    uid=riemann,dc=example,dc=com
    uid=euler,dc=example,dc=com
    uid=gauss,dc=example,dc=com
    uid=test,dc=example,dc=com

### Future

CompletableFutures can be used with the ldap4j client.
This requires a thread pool.

```java
    // new thread pool
    ScheduledExecutorService executor=Executors.newScheduledThreadPool(Context.defaultParallelism());

    // connect
    CompletableFuture<Void> future=FutureLdapConnection.factoryJavaAsync(
                    null, // use the global asynchronous channel group
                    executor,
                    Log.systemErr(),
                    Context.defaultParallelism(),
                    new InetSocketAddress("ldap.forumsys.com", 389),
                    10_000_000_000L, // timeout
                    TlsSettings.noTls()) // plain-text connection
            .get()
            .thenCompose((connection)->{
                System.out.println("connected");

                // authenticate
                CompletableFuture<Void> rest=connection.writeRequestReadResponseChecked(
                                BindRequest.simple(
                                            "cn=read-only-admin,dc=example,dc=com",
                                            "password".toCharArray())
                                        .controlsEmpty())
                        .thenCompose((ignore)->{
                            System.out.println("bound");
                            try {

                                // look up mathematicians
                                return connection.search(
                                        new SearchRequest(
                                                    List.of("uniqueMember"), // attributes
                                                    "ou=mathematicians,dc=example,dc=com", // base object
                                                    DerefAliases.DEREF_ALWAYS,
                                                    Filter.parse("(objectClass=*)"),
                                                    Scope.WHOLE_SUBTREE,
                                                    100, // size limit
                                                    10, // time limit
                                                    false) // types only
                                                .controlsEmpty());
                            }
                            catch (Throwable throwable) {
                                return CompletableFuture.failedFuture(throwable);
                            }
                        })
                        .thenCompose((searchResults)->{
                            System.out.println("mathematicians:");
                            searchResults.stream()
                                    .map(ControlsMessage::message)
                                    .filter(SearchResult::isEntry)
                                    .map(SearchResult::asEntry)
                                    .flatMap((entry)->entry.attributes().stream())
                                    .filter((attribute)->"uniqueMember".equals(attribute.type().utf8()))
                                    .flatMap((attribute)->attribute.values().stream())
                                    .forEach(System.out::println);
                            return CompletableFuture.completedFuture(null);
                        });

                // release resources, timeout only affects the LDAP and TLS shutdown sequences
                return rest
                        .thenCompose((ignore)->connection.close())
                        .exceptionallyCompose((ignore)->connection.close());
            });

    //wait for the result in this thread
    future.get(10_000_000_000L, TimeUnit.NANOSECONDS);
```

### Lava

Lava is the internal language of ldap4j.
This is the most feature-rich way to use the client.
Lava can be used reactive-style.

```java
    private static @NotNull Lava<Void> main() {
        return Lava.supplier(()->{
            System.out.println("ldap4j lava sample");

            // create a connection, and guard the computation
            return Closeable.withCloseable(
                    ()->LdapConnection.factory(
                            // use the global asynchronous channel group
                            JavaAsyncChannelConnection.factory(null, Map.of()),
                            new InetSocketAddress("ldap.forumsys.com", 389),
                            TlsSettings.noTls()), // plain-text connection
                    (connection)->{
                        System.out.println("connected");

                        // authenticate
                        return connection.writeRequestReadResponseChecked(
                                        BindRequest.simple(
                                                    "cn=read-only-admin,dc=example,dc=com",
                                                    "password".toCharArray())
                                                .controlsEmpty())
                                .composeIgnoreResult(()->{
                                    System.out.println("bound");

                                    // look up mathematicians
                                    return connection.search(
                                            new SearchRequest(
                                                        List.of("uniqueMember"), // attributes
                                                        "ou=mathematicians,dc=example,dc=com", // base object
                                                        DerefAliases.DEREF_ALWAYS,
                                                        Filter.parse("(objectClass=*)"),
                                                        Scope.WHOLE_SUBTREE,
                                                        100, // size limit
                                                        10, // time limit
                                                        false) // types only
                                                    .controlsEmpty());
                                })
                                .compose((searchResults)->{
                                    System.out.println("mathematicians:");
                                    searchResults.stream()
                                            .map(ControlsMessage::message)
                                            .filter(SearchResult::isEntry)
                                            .map(SearchResult::asEntry)
                                            .flatMap((entry)->entry.attributes().stream())
                                            .filter((attribute)->"uniqueMember".equals(attribute.type().utf8()))
                                            .flatMap((attribute)->attribute.values().stream())
                                            .forEach(System.out::println);
                                    return Lava.VOID;
                                });
                    });
        });
    }

    public static void main(String[] args) throws Throwable {
        // new thread pool
        ScheduledExecutorService executor=Executors.newScheduledThreadPool(Context.defaultParallelism());
        try {
            Context context=ThreadLocalScheduledExecutorContext.createDelayNanos(
                    10_000_000_000L, // timeout
                    executor,
                    Log.systemErr(),
                    Context.defaultParallelism());

            // going to wait for the result in this thread
            JoinCallback<Void> join=Callback.join(context);

            // compute the result
            context.get(join, main());

            // wait for the result
            join.joinEndNanos(context.endNanos());
        }
        finally {
            executor.shutdown();
        }
    }
```

### Netty codec

Ldap4j can be used as a codec in a Netty pipeline, through the `NettyLdapCodec` class.
It handles Netty `ByteBuf`s on the channel side,
and accepts `Request` objects from the application side, and returns `Response`s.

Check out the
[`NettyLdapCodecSample`](https://github.com/adaptiverecognition/ldap4j/blob/master/ldap4j-samples/src/main/java/hu/gds/ldap4j/samples/NettyLdapCodecSample.java)
for details.
    
### Reactor

Glue is provided to use ldap4j as a Reactor publisher.
All asynchronous operations return a Mono object.
The transport is hardcoded to Netty.

A pool is provided to amortize the cost of repeated TCP and TLS negotiations.

After starting the sample, the application can be reached [here](http://127.0.0.1:8080/).

```java
    @Autowired
    public EventLoopGroup eventLoopGroup;
    @Autowired
    public ReactorLdapPool pool;

    public Mono<String> noPool() {
        StringBuilder output=new StringBuilder();
        output.append("<html><body>");
        output.append("ldap4j reactor no-pool sample<br>");

        // create a connection, and guard the computation
        return ReactorLdapConnection.withConnection(
                        (evenLoopGroup)->Mono.empty(), // event loop group close
                        ()->Mono.just(eventLoopGroup), // event loop group factory
                        (connection)->run(connection, output),
                        new InetSocketAddress("ldap.forumsys.com", 389),
                        10_000_000_000L, // timeout
                        TlsSettings.noTls()) // plaint-text connection
                .flatMap((ignore)->{
                    output.append("</body></html>");
                    return Mono.just(output.toString());
                });
    }

    @GetMapping(value = "/pool", produces = "text/html")
    public Mono<String> pool() {
        StringBuilder output=new StringBuilder();
        output.append("<html><body>");
        output.append("ldap4j reactor pool sample<br>");

        // lease a connection, and guard the computation
        return pool.lease((connection)->run(connection, output))
                .flatMap((ignore)->{
                    output.append("</body></html>");
                    return Mono.just(output.toString());
                });
    }

    private Mono<Object> run(ReactorLdapConnection connection, StringBuilder output) {
        output.append("connected<br>");

        // authenticate
        return connection.writeRequestReadResponseChecked(
                        BindRequest.simple(
                                    "cn=read-only-admin,dc=example,dc=com",
                                    "password".toCharArray())
                                .controlsEmpty())
                .flatMap((ignore)->{
                    output.append("bound<br>");
                    try {

                        // look up mathematicians
                        return connection.search(
                                        new SearchRequest(
                                                    List.of("uniqueMember"), // attributes
                                                    "ou=mathematicians,dc=example,dc=com", // base object
                                                    DerefAliases.DEREF_ALWAYS,
                                                    Filter.parse("(objectClass=*)"),
                                                    Scope.WHOLE_SUBTREE,
                                                    100, // size limit
                                                    10, // time limit
                                                    false) // types only
                                                .controlsEmpty());
                    }
                    catch (Throwable throwable) {
                        return Mono.error(throwable);
                    }
                })
                .flatMap((searchResults)->{
                    output.append("mathematicians:<br>");
                    searchResults.stream()
                              .map(ControlsMessage::message)
                              .filter(SearchResult::isEntry)
                              .map(SearchResult::asEntry)
                              .flatMap((entry)->entry.attributes().stream())
                              .filter((attribute)->"uniqueMember".equals(attribute.type().utf8()))
                              .flatMap((attribute)->attribute.values().stream())
                              .forEach((value)->{
                                  output.append(value);
                                  output.append("<br>");
                              });
                    return Mono.just(new Object());
                });
    }

    @Bean
    public EventLoopGroup evenLoopGroup() {
        return new MultiThreadIoEventLoopGroup(4, NioIoHandler.newFactory());
    }
    
    @Bean
    public ReactorLdapPool pool(@Autowired EventLoopGroup eventLoopGroup) {
        return ReactorLdapPool.create(
            eventLoopGroup,
            (eventLoopGroup2)->Mono.empty(), // event loop group close
            Log.slf4j(), // log to SLF4J
            4, // pool size
            new InetSocketAddress("ldap.forumsys.com", 389),
            10_000_000_000L, // timeout
            TlsSettings.noTls()); // plaint-text connection
    }
```

### Trampoline

A [trampoline](https://en.wikipedia.org/wiki/Trampoline_(computing))
is used to convert the asynchronous operations to synchronous ones.
This can be used in simple command line or desktop applications.
The transport is hardcoded to Java NIO polling.

```java
    // single timeout for all operations
    long endNanos=System.nanoTime()+10_000_000_000L;

    TrampolineLdapConnection connection=TrampolineLdapConnection.createJavaPoll(
            endNanos,
            Log.systemErr(), // log everything to the standard error
            new InetSocketAddress("ldap.forumsys.com", 389),
            TlsSettings.noTls()); // plain-text connection

    // authenticate
    connection.writeRequestReadResponseChecked(
            endNanos,
            BindRequest.simple(
                        "cn=read-only-admin,dc=example,dc=com",
                        "password".toCharArray())
                    .controlsEmpty());

    // look up mathematicians
    List<ControlsMessage<SearchResult>> searchResults=connection.search(
            endNanos,
            new SearchRequest(
                        List.of("uniqueMember"), // attributes
                        "ou=mathematicians,dc=example,dc=com", // base object
                        DerefAliases.DEREF_ALWAYS,
                        Filter.parse("(objectClass=*)"),
                        Scope.WHOLE_SUBTREE,
                        100, // size limit
                        10, // time limit
                        false) // types only
                    .controlsEmpty());
    System.out.println("mathematicians:");
    searchResults.stream()
            .map(ControlsMessage::message)
            .filter(SearchResult::isEntry)
            .map(SearchResult::asEntry)
            .flatMap((entry)->entry.attributes().stream())
            .filter((attribute)->"uniqueMember".equals(attribute.type().utf8()))
            .flatMap((attribute)->attribute.values().stream())
            .forEach(System.out::println);

    // release resources, timeout only affects the LDAP and TLS shutdown sequences
    connection.close(endNanos);
```

### Parallel operations

Operations can be issued concurrently on a single connection.

A `MessageIdGenerator` object is used to provide ids to new messages.
The default `MessageIdGenerator` cycles through the ids from 1 to 127.
This is acceptable when there are no parallel operations, or just some occasional abandon, or cancel.

While sending and receiving concurrent messages only
`LdapConnection.writeMessage(ControlsMessage<M>, MessageIdGenerator)`
and `LdapConnection.readMessageCheckedParallel(Function<Integer, ParallelMessageReader<?, T>>)`
can be used.

As usual, reads cannot be called concurrently with other reads,
and writes cannot be called concurrently with other writes.

The caller must also generate and track the message ids used throughout the computation.

`TrampolineParallelSample.java` contains a simple example of a parallel search.
It connects to the public ldap server [ldap.forumsys.com:389](https://www.forumsys.com/2022/05/10/online-ldap-test-server/),
and queries all the objects 10 times concurrently.

The result of running that should look like this:

    ldap4j trampoline parallel sample
    connected
    bound
    result size: 22
    parallel requests: 10
    inversions: 0

There are 22 objects in the directory, and by `inversions=0` we know that we received all the results
strictly ordered according to their respective request.
So we failed to detect any parallel execution of the parallel requests.

```java
    ControlsMessage<SearchRequest> searchRequest=new SearchRequest(
            List.of("uniqueMember"), // attributes
            "dc=example,dc=com", // base object
            DerefAliases.DEREF_ALWAYS,
            Filter.parse("(objectClass=*)"),
            Scope.WHOLE_SUBTREE,
            100, // size limit
            10, // time limit
            false) // types only
            .controlsEmpty();

    // count all the entries + done
    int resultSize=connection.search(endNanos, searchRequest).size();
    System.out.printf("result size: %,d%n", resultSize);

    // start requests in parallel
    int parallelRequests=10;
    System.out.printf("parallel requests: %,d%n", parallelRequests);
    for (int ii=0; parallelRequests>ii; ++ii) {
        connection.writeMessage(
                endNanos,
                searchRequest,
                MessageIdGenerator.constant(ii+1));
    }

    // read all result
    int[] counts=new int[parallelRequests];
    int inversions=0;
    while (true) {
        @NotNull Map<@NotNull Integer, ParallelMessageReader<?, @NotNull LdapMessage<SearchResult>>> readers
                =new HashMap<>(parallelRequests);
        for (int ii=parallelRequests-1; 0<=ii; --ii) {
            if (resultSize!=counts[ii]) {
                readers.put(ii+1, SearchResult.READER.parallel(Function::identity));
            }
        }
        if (readers.isEmpty()) {
            break;
        }
        @NotNull LdapMessage<SearchResult> searchResult
                =connection.readMessageCheckedParallel(endNanos, readers::get);
        int index=searchResult.messageId()-1;
        ++counts[index];
        for (int ii=index-1; 0<=ii; --ii) {
            inversions+=resultSize-counts[ii];
        }
    }
    System.out.printf("inversions: %,d%n", inversions);
```

### TLS renegotiation

Ldap4j can send and receive TLS renegotiation requests.

A TLS renegotiation can be started by methods `LdapConnection.restartTlsHandshake()`
and `LdapConnection.restartTlsHandshake(@NotNull Consumer<@NotNull SSLEngine> consumer)`.
A renegotiation cannot be run parallel with any read nor write,
as it will produce output, and may consume some input.

The reception of renegotiation requests can be controlled by the creation time parameter `explicitTlsRenegotiation`.

With implicit tls renegotiation a renegotiation request will trigger a handshake,
but no further action is needed. Subsequent reads and writes will complete the handshake.

With explicit tls renegotiation a renegotiation request will trigger a handshake,
and the read that received it will throw a `TlsHandshakeRestartNeededException`.
The handshake can be completed by calling one of the `LdapConnection.restartTlsHandshake()` methods.
Until the handshake completes all reads and writes will throw `TlsHandshakeRestartNeededException`.

As a consequence, anyone wishing to support TLS renegotiations must implement its own logic to
correctly sequence reads, writes, renegotiations, and to retry reads after a renegotiation.

## ldap4j.sh

Ldap4j contains a command line client.
Its main purpose is to facilitate field debugging.

It prints all options when it's started without any arguments.

    ./ldap4j.sh -plaintext ldap.forumsys.com \
        bind-simple cn=read-only-admin,dc=example,dc=com argument password \
        search -attribute uniqueMember ou=mathematicians,dc=example,dc=com '(objectClass=*)'
    
    connecting to ldap.forumsys.com/54.80.223.88:389
    connected
    Password for cn=read-only-admin,dc=example,dc=com: 
    bind simple, cn=read-only-admin,dc=example,dc=com
    bind successful
    search
        attributes: [uniqueMember]
        base object: ou=mathematicians,dc=example,dc=com
        deref. aliases: DEREF_ALWAYS
        filter: (objectClass=*)
        manage dsa it: false
        scope: WHOLE_SUBTREE
        size limit: 0 entries
        time limit: 0 sec
        types only: false
    search entry
        dn: ou=mathematicians,dc=example,dc=com
        uniqueMember: PartialAttribute[type=uniqueMember, values=[uid=euclid,dc=example,dc=com, uid=riemann,dc=example,dc=com, uid=euler,dc=example,dc=com, uid=gauss,dc=example,dc=com, uid=test,dc=example,dc=com]]
    search done

## Docs

### DNS lookups

Ldap4j expects a resolved `InetSocketAddress` to connect to a server.
This conveniently sidesteps the question of how to obtain a resolved address.

Standard java libraries can only resolve addresses through a blocking API.

### I/O exceptions

Java lacks the ability to get back exact error codes on I/O errors.
To classify an exception ldap4j first checks the type of the exception,
and when this fails, it checks the exception message.
There's some properties files in ldap4j-java resources to list known types and message patterns:
- `Exceptions.connection.closed.properties`,
- `Exceptions.timeout.properties`,
- `Exceptions.unknown.host.properties`.

### Lava

Lava is the monadic library used to implement ldap4j.
It abstracts java computations, and it carries around multiple objects:
- a clock to measure time,
- a suggested deadline for computations,
- an executor,
- a log,
- a timer, to wait for time to elapse.

There are three objects central to lava.

The `Callback` is the visitor of java computations.
A visitor can be used to pattern match.
This is not unlike a
[CompletionHandler](https://docs.oracle.com/en/java/javase/17/docs//api/java.base/java/nio/channels/CompletionHandler.html).

```java
    public interface Callback<T> {
        void completed(T value);

        void failed(@NotNull Throwable throwable);
    }
```

The `Context` groups together useful objects.
It also provides a few methods to uncouple the calling of
`Callback.completed()`, `Callback.failed()` and `Lava.get()`
from the current thread.

```java
    public interface Context extends Executor {
        @NotNull Runnable awaitEndNanos(@NotNull Callback<Void> callback);

        @NotNull Clock clock();

        default <T> void complete(@NotNull Callback<T> callback, T value);

        long endNanos();
    
        default <T> void fail(@NotNull Callback<T> callback, @NotNull Throwable throwable);
    
        default <T> void get(@NotNull Callback<T> callback, @NotNull Lava<T> supplier);

        @NotNull Log log();
    }
```

`Lava` is the monad.
The creation of a lava object should do nothing most of the time,
and a new computation should be started for every call of `Lava.get()`.
This class also provides some monadic constructors and compositions.

```java
    public interface Lava<T> {
        static <E extends Throwable, T> @NotNull Lava<T> catchErrors(
                @NotNull Function<@NotNull E, @NotNull Lava<T>> function,
                @NotNull Supplier<@NotNull Lava<T>> supplier,
                @NotNull Class<E> type);
    
        static <T> @NotNull Lava<T> complete(T value);

        default <U> @NotNull Lava<U> compose(@NotNull Function<T, @NotNull Lava<U>> function);
        
        static <T> @NotNull Lava<T> fail(@NotNull Throwable throwable);

        static <T> @NotNull Lava<T> finallyGet(
                @NotNull Supplier<@NotNull Lava<Void>> finallyBlock,
                @NotNull Supplier<@NotNull Lava<T>> tryBlock);

        static <T, U> @NotNull Lava<@NotNull Pair<T, U>> forkJoin(
                @NotNull Supplier<@NotNull Lava<T>> left,
                @NotNull Supplier<@NotNull Lava<U>> right);

        void get(@NotNull Callback<T> callback, @NotNull Context context) throws Throwable;
    }
```

#### Executor

Computing a lava monad requires a `Context`, which is mostly an
[Executor](https://docs.oracle.com/en/java/javase/17/docs//api/java.base/java/util/concurrent/Executor.html).
A `Context` implementation for
[ScheduledExecutorServices](https://docs.oracle.com/en/java/javase/17/docs//api/java.base/java/util/concurrent/ScheduledExecutorService.html)
is provided, it's called `ScheduledExecutorContext`.

`ThreadLocalScheduledExecutorContext` provides the same functionality as a `ScheduledExecutorContext`
and tries to exploit locality of reference and avoid context switches.

When the environment doesn't provide a scheduled executor, the `MinHeap` class can be used
to implement `Context.awaitEndNanos()`.

#### Lava engine

A `LavaEngine` is and event-driven executor, which evaluates lava objects in a single thread, completely synchronously.
It uses a queue to delay tasks, and consumes tasks in a loop until the result is produced,
or there's no more tasks that can be run immediately.

A new computation can be started by `JoinCallback<T> LdapEngine.get(Lava<T>)`.

Then `LdapEngine.runAll()` must be called repeatedly,
while the result is not yet produced, and sufficient time elapsed.

External events have to be delivered explicitly through helper classes, like `EngineConnection`,
or through starting new computations with `LdapEngine.get()`

#### Trampoline

A `Trampoline` evaluates lava objects in a single thread, completely synchronously.
It uses a queue to delay tasks, and consumes tasks in a loop until the result is produced.
It supports waits without busy-waiting, using Object.wait().

A new computation can be started by `T Trampoline.contextEndNanos(long).get(boolean, boolean, Lava<T>)`.

The main difference of an `LdapEngine` and a `Trampoline` is in how they handle waits.
An `LdapEngine` is used for polling, and it won't block the current thread,
only running tasks that are immediately available.
A `Trampoline` will block the current thread
when the result is not yet produced and there's no tasks that can be run immediately.

### Transports

Ldap4j is transport agnostic, at the lava level the network library can be chosen freely.
Glue logic for multiple libraries are provided.

#### Apache MINA

A `MinaConnection` requires an
[IoProcessor](https://nightlies.apache.org/mina/mina/2.2.0/apidocs/org/apache/mina/core/service/IoProcessor.html).

#### Engine connection

`EngineConnection` is an event-driven connection.
It's backed up by a read and a write buffer in memory, which can be queried and updated synchronously.

#### Java NIO channel, asynchronous

A `JavaAsyncChannelConnection` uses a
[AsynchronousSocketChannels](https://docs.oracle.com/en/java/javase/17/docs//api/java.base/java/nio/channels/AsynchronousSocketChannel.html).
If the given channel group is null, it will use the global JVM channel group.

#### Java NIO channel, polling

A `JavaChannelPollConnection` uses a
[SocketChannel](https://docs.oracle.com/en/java/javase/17/docs//api/java.base/java/nio/channels/SocketChannel.html).
This requires no external threads to work.
It polls repeatedly the underlying channel for the result, but uses
[exponential backoff](https://en.wikipedia.org/wiki/Exponential_backoff)
to limit the time increase to a small linear factor,
and the number of unsuccessful polls to logarithmic in time.

This is intended for the same use cases as the trampoline, single threaded single user applications.

#### Netty

A `NettyConnection` requires an
[EventLoopGroup](https://netty.io/4.2/api/io/netty/channel/EventLoopGroup.html),
and a matching
[DuplexChannel](https://netty.io/4.2/api/io/netty/channel/socket/DuplexChannel.html)
class.
In turn, an `EventLoopGroup` requires an
[IoHandler](https://netty.io/4.2/api/io/netty/channel/IoHandler.html)
factory.

- [NioIoHandler](https://netty.io/4.2/api/io/netty/channel/nio/NioIoHandler.html)
and 
[NioSocketChannel](https://netty.io/4.2/api/io/netty/channel/socket/nio/NioSocketChannel.html)
are supported.
These are available on all platforms.
- [EpollIoHandler](https://netty.io/4.2/api/io/netty/channel/epoll/EpollIoHandler.html)
and
[EpollSocketChannel](https://netty.io/4.2/api/io/netty/channel/epoll/EpollSocketChannel.html)
are supported.
These are available on linuxes.

## License

Ldap4j is licensed under the Apache License, Version 2.0.
