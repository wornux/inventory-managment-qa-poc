---
name: instrumenting-applications
description: "Adds or reviews useful Spring Boot and SLF4J diagnostics without leaking data or creating noisy duplicate logs. Use for application logging, request correlation, structured logs, production diagnostics, or log-based debugging."
---

# Instrumenting Applications

Add a log only when it answers an operational question that existing errors, metrics, traces, or debugger output cannot answer more cheaply.

## Workflow

1. State the question being diagnosed, such as which request failed, which dependency was slow, or which state transition occurred.
2. Inspect current logging configuration and the call path before changing code.
3. Put one event at the ownership boundary with stable fields and an appropriate level.
4. Reproduce or run a focused test, then confirm the event contains enough context without secrets or unnecessary payloads.

## Event rules

- Use parameterized SLF4J calls; guard only genuinely expensive argument construction.
- `ERROR` means the current component cannot fulfill the operation, `WARN` means degraded or unexpected but handled, `INFO` means a low-volume business or lifecycle event, and `DEBUG` is diagnostic detail.
- Log an exception with its stack trace once at the boundary that handles it. Do not log and rethrow at every layer.
- Never log passwords, access or refresh tokens, cookies, authorization headers, client secrets, full request bodies, or unnecessary personal data.
- Use stable field names and identifiers. Avoid stringifying JPA entities or lazy collections.
- If using MDC, set values at the request or job boundary and clear them in `finally`; ensure propagation is deliberate for asynchronous work.
- Keep the repository's existing console format unless structured logging is requested or the deployment pipeline consumes it. Do not add a logging dependency when Spring Boot already supports the required format.

Prefer metrics for rates and latency distributions, tracing for cross-service causality, and audit records for durable security or compliance history. Logs are not a substitute for any of them.
