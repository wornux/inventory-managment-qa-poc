# Auditing coverage with JaCoCo

## Commands

- Focused feedback: `scripts/run-test.sh ClassName`
- Full suite, report, package, and configured gate: `scripts/verify-coverage.sh`
- Existing report only:
  ```bash
  python3 .agents/skills/testing-java-behavior/scripts/jacoco-report.py
  ```

The HTML report is `target/site/jacoco/index.html`; machine-readable data is in `jacoco.xml` and `jacoco.csv`.

## Read counters correctly

- **LINE:** source lines with executable bytecode.
- **BRANCH:** outcomes of conditionals and short-circuit expressions.
- **INSTRUCTION:** bytecode instructions; useful for partially covered lines and synthetic lambdas.
- **METHOD / CLASS:** whether executable methods/classes ran.
- **COMPLEXITY:** covered versus missed cyclomatic paths as represented by JaCoCo.

Audit line and branch coverage first, then inspect instruction/method misses. A green line can still contain a missed lambda or short-circuit operand.

## Gap loop

1. Run the full report from a clean test execution.
2. Use `jacoco-report.py` to list residual files and line numbers.
3. Open the source and translate each miss into domain behavior.
4. Add one assertion-bearing test for that behavior.
5. Re-run the focused test, then the full verification.

## Exclusions and gates

- Exclude generated code or explicitly out-of-scope modules only by repository policy.
- Keep exclusions narrow and visible in `pom.xml`.
- Never exclude a difficult class after seeing it red.
- Bind `jacoco:check` to `verify` so CI enforces the same target developers inspect.
- This repository requires 100% line and branch coverage outside `com/wornux/ui/views/**` and generated `**/*MapperImpl*` classes.

Coverage is evidence of exercised bytecode, not proof of correct assertions. Review test contracts even when every counter is green.
