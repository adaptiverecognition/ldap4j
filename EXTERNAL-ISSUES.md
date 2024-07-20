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

### JNDI

JNDI is blocking.

Host name verification for LDAPS connections can only be turned on and off using a JVM property.

Trust and key managers for LDAPS connections can only be specified through a static method.

### Ldaptive

Bind blocks.

Async search requires a blocking workaround.

TLS handlers sometimes fail.

### Unboundid LDAP SDK

Unboundid LDAP SDK is blocking.

## Servers

There seems to be some misunderstanding how integers are represented.
Servers sometime fail to interpret the highest bit as a sign bit,
but it's not even consistent within a single implementation.
While there's only a handful of integers used directly in LDAP messages, integer-like values are ubiquitous.

### Apache Directory Server

Approx match works like an equality match, while officially it's not even supported.

There's no fast bind.

### MS Windows domain controller

There's some integer parsing woes:
- search size limit,
- search time limit.

Closes socket on unbind without a TLS shutdown.

### Unboundid LDAP SDK

There's some integer parsing woes:
- search size limit,
- search time limit.

There's no approx match.

There's no fast bind.

## Transports

### Apache MINA

`IoSession.write()` only supports complete writes.

There's no shut down output.

Read is broken, only auto read works reliably.
There's no way to limit how much Apache MINA will read ahead.
Read issues:
- `IoSession.read()` won't return an EOF unless the session gets closed.
- `IoSession.resumeRead()/.suspendRead()` are ridiculously slow.
- Calls `IoHandler.inputClosed()` multiple times in a loop without sleeping until the session is closed.

Sessions are closed on connection reset.

Writes don't complete on session close.

### Java NIO

DNS lookups are blocking.

There's no way to get back error codes from I/O exceptions, like "broken pipe", or "connection reset".
As Apache MINA and Netty use the java NIO library the same issues apply.

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
There's no way to limit how much Netty will read ahead.
Transports:
- Epoll.

Broken auto close combined with auto read means that the channel may get closed any time without warning.
