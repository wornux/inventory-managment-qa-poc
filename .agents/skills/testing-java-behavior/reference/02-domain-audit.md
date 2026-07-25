# Domain audit before writing tests

Stop when these questions are answered; do not map the whole codebase.

1. **Meaning:** What real operation does the method represent?
2. **Inputs:** Which values are trusted, normalized, optional, or invalid?
3. **Invariants:** What must remain true before and after the call?
4. **Authorization:** Which exact permission or permission set is required?
5. **Persistence:** What is loaded, changed, saved, deleted, or expected to roll back?
6. **Alternatives:** List each `if`, short-circuit operand, ternary path, empty lookup, and translated exception in domain language.
7. **Callers:** Search every caller before changing shared behavior.
8. **Test boundary:** Decide whether plain unit testing is honest:
   - Mockito for service rules and collaborator contracts.
   - Validator tests for Jakarta constraints.
   - Mock REST server for outbound protocol contracts.
   - Spring Security/MockMvc for real filter behavior.
   - JPA/Testcontainers for queries, mappings, transactions, and database constraints.

Write the smallest matrix that covers the meaningful alternatives. Prefer one happy path plus one test per distinct failure rule; combine only inputs that prove the same rule.
