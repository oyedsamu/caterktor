# Module caterktor-ktor

Ktor transport integration for CaterKtor.

This module adapts CaterKtor's transport-neutral request model onto Ktor `HttpClient`, maps Ktor failures into `NetworkError`, and exposes the builder hooks that let applications use Ktor engines without coupling the core pipeline to Ktor APIs.

# Package io.github.oyedsamu.caterktor

Ktor-backed transport and DSL extensions. Use this package when you want CaterKtor's interceptor, result, retry, timeout, and serialization model to execute over a Ktor client.

The APIs distinguish between clients owned by CaterKtor and caller-owned clients so lifecycle behavior stays explicit.
