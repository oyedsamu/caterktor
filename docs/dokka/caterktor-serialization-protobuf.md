# Module caterktor-serialization-protobuf

Protocol Buffers content negotiation for CaterKtor using kotlinx.serialization.

This module is intended for services that expose protobuf payloads while still using the same CaterKtor typed call and result APIs as JSON-backed clients.

# Package io.github.oyedsamu.caterktor.serialization.protobuf

Protobuf converter and DSL entry points. Install these APIs to register protobuf body conversion with CaterKtor's content negotiation registry.
