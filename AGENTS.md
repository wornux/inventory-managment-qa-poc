# Repository Guidance

## Project shape

- This is a Java 25 Spring Boot 4.1 application with Vaadin Flow, Spring Security, JPA/Hibernate, Flyway, PostgreSQL, and Maven Wrapper.
- Keep production code under `com.wornux`; follow the existing feature packages rather than adding generic `util`, `common`, or `manager` packages.
- Treat `src/main/frontend/generated/`, `src/main/bundles/`, and `target/` as generated output. Do not edit them by hand.

## Local runtime and hot swap

- The application defaults to `http://localhost:${PORT:-8080}`; Keycloak runs on port `7777`. PostgreSQL and Keycloak are defined in `compose.yaml`.
- Before starting, stopping, building, or compiling, check whether the application is already listening: `lsof -nP -iTCP:${PORT:-8080} -sTCP:LISTEN`.
- Never stop or restart a running application or debugger unless the user explicitly asks. Assume a running VS Code **Debug using Hotswap Agent** session owns the JVM.
- Java hot code replacement is enabled by `.vscode/launch.json`; Vaadin/Vite reloads frontend and CSS changes. After an edit, wait for reload and verify the running application before invoking Maven.
- Avoid `clean`, package, or broad compile commands while the debug JVM is running: generated frontend work and class replacement can cause a large reload. Prefer browser/runtime verification and focused tests that do not replace the running process.
- Structural Java changes may exceed hot-swap limits. If the running JVM reports linkage errors such as `NoSuchMethodError`, especially for synthetic lambda methods, do not distort correct source code to satisfy stale bytecode. Explain that the debug session needs a user-approved restart.
- If no application is running and interactive verification is required, start only the required dependencies from `compose.yaml`, then use `./mvnw spring-boot:run`. Keep the resulting process available for subsequent checks instead of repeatedly restarting it.
- Use the `debugging-vaadin-interfaces` skill for UI, layout, theme, or CSS debugging.

## Working rules

- Read the affected flow from entry point to persistence before editing. Search every caller of shared code and fix the narrowest source of truth.
- Match existing conventions before introducing a pattern, helper, DTO, exception type, or dependency. Prefer direct code while there is only one implementation.
- Do not use fully qualified class names in Java code; import the type instead. Qualify a type only when unavoidable because two imported types share the same simple name and neither type is under our control, or when both names are intentional, strong domain names such as `Client` or `Product` and renaming either would make the domain less clear.
- Within a method, separate distinct logical phases with one blank line—for example initialization, the action under test, conditional handling or assertions, and the return. Keep tightly related statements together; do not add a blank line between every statement.
- Keep authorization checks in the service layer so UI and REST callers enforce the same rule. Do not rely on hidden or disabled controls for security.
- Keep API entities behind request/response DTOs. Preserve the existing `ApiResponse` envelope and centralized exception handling.
- Validate untrusted input at the boundary and enforce business invariants in the service or domain model. Do not infer HTTP status from exception message text in new code.
- Never expose credentials, tokens, personal data, or full request bodies in source, tests, logs, screenshots, or responses.
- Do not create branches, commit, squash, push, delete branches, or clean the worktree unless the user explicitly asks. Never overwrite unrelated local changes.
- Always sign commits. Never bypass signing or create an unsigned commit; if signing fails, stop and ask the user to unlock, approve, or fix the signer instead of disabling signing.
- When the user asks for a commit or commit message, use the `writing-commit-messages` skill. Preserve unrelated working-tree changes and follow the repository's Conventional Commit history.

## Persistence

- Keep `spring.jpa.open-in-view=false`. Load exactly the associations needed by a use case inside a read-only service transaction, using a repository query or projection when appropriate.
- Put transaction boundaries on service operations. Use `@Transactional(readOnly = true)` for reads and `@Transactional` for atomic writes.
- Change the production schema only with a new, forward-only migration in `src/main/resources/db/migration/prod/`. Never edit a migration that may already have run. Keep development-only data in the dev migration location.
- Preserve optimistic locking for mutable shared entities and database constraints for invariants that must survive concurrent requests.

## Verification

- Use the narrowest check that exercises the changed behavior. Add or update a focused regression test for non-trivial behavior changes.
- Run one test class with `./mvnw -Dtest=ClassName test`; run the suite with `./mvnw test`; use `./mvnw verify` when Flyway, packaging, or cross-cutting configuration changes.
- Prefer Mockito unit tests for isolated service rules. Use Spring context or PostgreSQL/Testcontainers only when framework wiring, security filters, transactions, mappings, or database-specific SQL are part of the behavior.
- For UI changes, inspect the rendered page at the affected route and viewport. Do not require screenshots or exhaustive breakpoint checks for backend-only or invisible changes.
- Report checks that actually ran and any environment blocker; do not claim unrun checks passed.
