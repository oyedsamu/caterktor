# Module caterktor-core

Core request execution, result modeling, body conversion, timeouts, retries, and interceptor contracts.

Start here when integrating CaterKtor. The module owns the transport-neutral `NetworkClient`, the immutable `NetworkRequest` and `NetworkResponse` models, typed request helpers, `NetworkResult`, error mapping primitives, query parameters, and the builder DSL used by the integration modules.

# Package io.github.oyedsamu.caterktor

The public CaterKtor core API. This package contains the client pipeline, request and response models, headers, attributes, result helpers, timeout configuration, retry and circuit-breaker interceptors, content negotiation contracts, and URL resolution helpers used by typed calls.

Most applications configure a client with `CaterKtor { ... }`, install a transport, register converters, and call `get`, `post`, `put`, `patch`, `delete`, or `head` helpers. Library authors usually depend on the lower-level `Transport`, `Interceptor`, `BodyConverter`, and body wrapper contracts.
