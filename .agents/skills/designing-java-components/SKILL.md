---
name: designing-java-components
description: "Chooses the simplest maintainable Java design and applies a named design pattern only when its tradeoffs are justified. Use when designing an extension point, refactoring rigid code, or explicitly asked for Factory, Builder, Strategy, Observer, Decorator, or Adapter."
---

# Designing Java Components

Treat patterns as names for recurring forces, not implementation goals.

## Decision ladder

After tracing the current flow, stop at the first option that meets the real requirement:

1. existing code or framework feature;
2. standard library type or language feature;
3. direct construction, method, conditional, or collection lookup;
4. functional interface or composition using an existing project boundary;
5. named design pattern.

Do not add an interface for one implementation, a factory for a known concrete type, a builder for a small record or constructor, or an event for behavior that must complete atomically and synchronously.

## When a pattern earns its cost

- **Strategy:** multiple real algorithms vary behind one stable contract, or runtime substitution is required.
- **Factory:** construction selects among real implementations or centralizes genuinely complex setup.
- **Builder:** callers otherwise face many optional values that cannot be expressed clearly with records or named factories.
- **Adapter:** a third-party or legacy contract must be isolated from the domain.
- **Decorator:** independently composable behavior must wrap the same contract.
- **Observer/event:** publishers must not know subscribers and delayed or independent reactions are acceptable. Use transactional events when consumers require committed state.

Prefer Spring dependency injection for existing collaborators, but do not create Spring beans solely to label a pattern.

## Implementation

State the variation or coupling being solved in one sentence. Make the smallest change that proves the design, preserve behavior, and add one focused test covering implementation selection or composition. If the requested pattern adds more machinery than it removes, explain that and implement the simpler design unless the user explicitly requires the pattern as a learning exercise.
