# Module CaterKtor

CaterKtor is a Kotlin Multiplatform networking layer built on top of Ktor. The generated API reference is organized by published module so readers can start with the core pipeline and then add transports, serialization, auth, logging, and testing utilities as needed.

Use `caterktor-core` for the stable request, response, result, retry, timeout, and interceptor contracts. Add `caterktor-ktor` or one of the engine modules to connect the core client to a Ktor `HttpClient`, then choose optional modules for body conversion, authentication, logging, and test doubles.

The root documentation is generated as an aggregate Dokka publication. Release builds validate that this aggregate contains every published module before Maven publishing tasks can run.
