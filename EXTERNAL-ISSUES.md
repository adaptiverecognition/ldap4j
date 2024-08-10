# External issues

We expect these issues to go unresolved forever.
These are not all bugs.

## Table of contents

- [Clients](#clients)
  - [Apache LDAP API](#apache-ldap-api)
  - [JNDI](#jndi)
  - [Ldaptive](#ldaptive)
  - [Unboundid LDAP SDK](#unboundid-ldap-sdk)
- [Servers](#servers)
  - [Apache Directory Server](#apache-directory-server)
  - [MS Windows domain controller](#ms-windows-domain-controller)
  - [Unboundid LDAP SDK](#unboundid-ldap-sdk-1)
- [Transports](#transports)
  - [Apache MINA](#apache-mina)
  - [Java NIO](#java-nio)
  - [MS Windows](#ms-windows)
  - [Netty](#netty)

## Clients

### Apache LDAP API

`LdapNetworkConnection.connect()` blocks, and there's no async alternative.
Various async operations call `LdapNetworkConnection.connect()` in the calling thread.

Version 2.1.6.

### JNDI

JNDI is blocking.

Host name verification for LDAPS connections can only be turned on and off using a JVM property.

Trust and key managers for LDAPS connections can only be specified through a static method.

Version 17.

### Ldaptive

Bind blocks.

Async search requires a blocking workaround.

TLS handlers sometimes fail.

Version 2.2.0.

### Unboundid LDAP SDK

Unboundid LDAP SDK is blocking.

## Servers

There seems to be some misunderstanding how integers are represented.
Servers sometime fail to interpret the highest bit as a sign bit,
but it's not even consistent within a single implementation.
While there's only a handful of integers used directly in LDAP messages, integer-like values are ubiquitous.

### Apache Directory Server

There's no abandon (notice of disconnect), cancel (notice of disconnect), nor fast bind.

Approx match works like an equality match, while officially it's not even supported.

There's no fast bind.

Version 2.0.0.AM27.

### MS Windows domain controller

There's some integer parsing woes:
- search size limit,
- search time limit.

Closes socket on unbind without a TLS shutdown.

### Unboundid LDAP SDK

There's some integer parsing woes:
- search size limit,
- search time limit.

There's no abandon (notice of disconnect), approx match, cancel (unwilling), nor fast bind.

Version 6.0.11.

## Transports

### Apache MINA

`IoSession.write()` only supports complete writes.

There's no shut down output.

Read is broken, only auto read works reliably.
There's no way to limit how much Apache MINA will read ahead if you leave the worker thread.
Read issues:
- `IoSession.read()` won't return an EOF unless the session gets closed.
- `IoSession.resumeRead()/.suspendRead()` are ridiculously slow.
- Calls `IoHandler.inputClosed()` multiple times in a loop without sleeping until the session is closed.

Sessions are closed on connection reset.

Pending writes won't complete on session close.

Version 2.2.3.

### Java NIO

DNS lookups are blocking.

There's no way to get back error codes from I/O exceptions, like "broken pipe", or "connection reset".
As Apache MINA and Netty use the java NIO library the same issues apply.

TLS 1.3 post-handshake authentication is not supported.
See [JDK-8206923](https://bugs.openjdk.org/browse/JDK-8206923).

Version 17.

### MS Windows

While setting the socket send buffer size seems to have some effect,
the minimum effective size seems to be ridiculously high.

### Netty

`Channel.write()/.writeAndFlush()` only support complete writes.

Auto close is broken, and deprecated.
Channels are closed on EOF.
Transports:
- Epoll,
- Nio.

Auto read is broken.
`SimpleChannelInboundHandler.channelRead0()` gets called without a matching `Channel.read()` call.
There's no way to limit how much Netty will read ahead if you leave the worker thread.
Transports:
- Epoll.

Broken auto close combined with auto read means that the channel may get closed any time without warning.

Shut down output is not a pipeline event.

Version 4.1.106.Final.