---
name: evolving-persistence
description: "Changes Spring Data JPA entities, repositories, transactions, queries, and Flyway migrations safely. Use for persistence features, schema changes, N+1 queries, lazy-loading failures, locking, or database performance work."
---

# Evolving Persistence

Keep database guarantees, transaction boundaries, and loaded object graphs explicit.

## Workflow

1. Trace the operation from controller or view through service, repository, entity, and current migration.
2. Identify the actual invariant and whether it belongs in validation, a database constraint, optimistic locking, or more than one layer.
3. Inspect the generated query or obtain evidence before treating N+1 or indexing as the cause of a performance problem.
4. Change the smallest persistence boundary that serves the use case.
5. Verify with the narrowest test that exercises the relevant JPA or PostgreSQL behavior.

## Rules

- Keep associations lazy by default; fetch only what the operation consumes through a targeted query, entity graph, or projection.
- Do not enable Open Session in View or switch an association to `EAGER` to hide a detached-access bug.
- Put atomic business operations in service transactions. Do not hold a transaction open across remote calls or user interaction.
- Beware pagination with collection fetch joins; use a two-step query or projection when row multiplication changes page semantics.
- Keep entities out of REST responses and avoid entity `equals`, `hashCode`, or `toString` implementations that traverse lazy relationships.
- Use `@Version` where concurrent updates must be detected. Back application-level uniqueness checks with a database constraint when races matter.
- Add a new forward-only Flyway migration. Do not edit an applied migration, use `ddl-auto` to evolve production, or put seed-only development data in production migrations.

Use a Mockito test for service decisions that do not depend on persistence behavior. Use a Spring/JPA test with PostgreSQL or Testcontainers for mappings, constraints, locking, transaction behavior, native SQL, or PostgreSQL-specific migrations.
