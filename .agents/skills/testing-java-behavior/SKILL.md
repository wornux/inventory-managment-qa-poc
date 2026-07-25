---
name: testing-java-behavior
description: "Creates small behavior-focused Java tests and audits JaCoCo coverage without gaming metrics. Use when writing unit tests, adding regression coverage, closing coverage gaps, or checking Maven JaCoCo reports."
---

# Testing Java Behavior

Tiny bitácora: understand the domain contract, test one observable rule at a time, then let JaCoCo identify what was missed.

## Route

1. Read [the test rules](reference/01-test-rules.md).
2. Trace the method with [the domain audit](reference/02-domain-audit.md).
3. Pick the narrowest pattern from [the test patterns](reference/03-test-patterns.md).
4. Run one test while iterating:
   ```bash
   bash .agents/skills/testing-java-behavior/scripts/run-test.sh ClassName
   ```
5. When the app is not running, audit the complete target:
   ```bash
   bash .agents/skills/testing-java-behavior/scripts/verify-coverage.sh
   ```
6. Interpret counters and residual lines with [the JaCoCo guide](reference/04-jacoco-audit.md). Never widen exclusions merely to reach a percentage.

## Definition of done

- Tests fail when the domain contract breaks, not only when implementation lines move.
- Exact filters, permission sets, mapped fields, request payloads, and persistence calls are asserted where they are the contract.
- The focused test passes; the complete coverage gate passes when the change affects the shared target.
- Fully qualified class names are absent except for unavoidable collisions allowed by `AGENTS.md`.
