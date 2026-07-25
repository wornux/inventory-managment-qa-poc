---
name: reviewing-java-changes
description: "Reviews Java and Spring changes for correctness, security, persistence, API compatibility, and missing tests. Use for code reviews, refactoring reviews, pull requests, or pre-merge checks in this repository."
---

# Reviewing Java Changes

Review intent and behavior, not adherence to a generic checklist.

## Workflow

1. Read `AGENTS.md` and establish the requested behavior.
2. Start with the diff when reviewing changes. Read surrounding code only to verify a concrete uncertainty.
3. Trace changed public methods through all callers and into repositories, security checks, transactions, exception mapping, and serialization as applicable.
4. Compare with nearby code and tests to distinguish a defect from a deliberate project convention.
5. Run the smallest relevant test only when execution would resolve uncertainty.

## What deserves a finding

Report an issue only when it has a plausible failure mode:

- incorrect results, state transitions, or boundary conditions;
- authorization bypass, unsafe input handling, or sensitive-data exposure;
- broken API or database compatibility;
- transaction, concurrency, lazy-loading, query-count, or data-loss risk;
- missing regression coverage for changed non-trivial behavior.

Do not demand patterns, API versioning, `Optional`, DTO layers, logging, comments, or abstraction without showing why this change needs them. Do not report pre-existing issues unless the change worsens them or they block the requested behavior.

## Output

List findings first, ordered by impact. For each finding include:

- severity;
- exact file and line;
- the input or sequence that fails;
- why existing code or tests do not prevent it;
- the smallest viable correction.

If there are no findings, say so and identify any material checks not run. Keep praise and broad summaries out of the findings list.
