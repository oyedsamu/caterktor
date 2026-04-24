# Module caterktor-logging

Structured request and response logging for CaterKtor.

This module provides logging interceptors and redaction helpers designed for API clients that need diagnostics without leaking sensitive headers, query parameters, or body fields.

# Package io.github.oyedsamu.caterktor.logging

Logging interceptor, logger contracts, and redaction utilities. Install these APIs near the edge of the interceptor pipeline when you need observable request behavior with explicit data masking.
