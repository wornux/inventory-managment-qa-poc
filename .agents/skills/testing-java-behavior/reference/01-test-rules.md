# Test rules

## Small means one contract

- One test method proves one business rule, alternative, or failure boundary.
- Use a parameterized test when several inputs express the same rule.
- Keep setup local unless a fixture is genuinely shared and its defaults are obvious.
- Name tests `method_condition_outcome` or an equally explicit behavior sentence.

## Assert meaning, not execution

- Assert returned state, persisted state, emitted response, thrown domain error, and exact collaborator arguments.
- Return distinct sentinel values from mocks so dropped filters cannot pass through Mockito defaults.
- Stub exact permission sets; do not use `any()` when the set itself is the contract.
- At mapping boundaries, assert every public field in both directions.
- For HTTP clients, assert method, URI, authorization, content type, and the complete protocol-critical payload.
- For security configuration, assert the installed matchers, handlers, policies, converters, and login services.

## Do not game coverage

- Do not call a method without asserting its result or effect.
- Do not add production branches, reflection-only hooks, or broad exclusions to make JaCoCo green.
- Do not test repository interfaces merely because they appear in a report; test custom queries with a database slice when their semantics matter.
- Do not treat getter/setter execution as a domain test. Cover accessors naturally through aggregate or mapping behavior.
- Do not claim a Mockito test proves transaction rollback, JPA mapping, validation annotations, or filter-chain behavior that requires a framework/database test.

## Repository rules

- Prefer JUnit 6, AssertJ, and Mockito already provided by Spring Boot.
- Import Java types. Fully qualified class names are allowed only for the collisions documented in `AGENTS.md`.
- Do not introduce dependencies, production abstractions, or test-only production code for coverage.
- Preserve the explicit JaCoCo exclusion policy; this repository excludes `com/wornux/ui/views/**` and generated `**/*MapperImpl*` classes.
- Keep complete mapper contract tests even though generated MapStruct implementations are excluded from coverage.
