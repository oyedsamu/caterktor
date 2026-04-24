# Module caterktor-auth

Bearer token and token refresh interceptors for CaterKtor.

This module adds authentication behavior as ordinary CaterKtor interceptors, keeping token lookup, refresh, failure handling, and refresh budgets testable and independent from the terminal transport.

# Package io.github.oyedsamu.caterktor.auth

Auth DSL, bearer auth, refresh auth, refresh budgeting, and auth-specific exceptions. Use these APIs when requests need authorization headers and failed responses should trigger bounded token refresh.
