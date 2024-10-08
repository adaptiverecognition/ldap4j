Version 1.2.0:
- add event-driven LDAP engine support classes
- add extensions
  - absolute true and false filters
  - all operational attributes
  - assertion control
  - attributes by object class
  - don't use copy control
  - matched values control
  - modify-increment operation
  - password modify operation
  - read entry controls
  - server feature discovery
  - server side sorting controls
  - simple paged results control
  - transactions
  - "Who am I?" operation
- add Netty codec
- add optional TLS handshake executor
- add thread-local executor
- receive and send TLS renegotiations
- return TLS session

Version 1.1.0:
- add LDAP operations
  - abandon
  - add
  - cancel
  - compare
  - delete
  - modify
  - modify dn
  - SASL bind
- fix enumeration, integer, and length codecs
- support parallel operations

Version 1.0.0:
- first public release
