---
name: debugging-vaadin-interfaces
description: "Debugs and visually verifies Vaadin Flow views, layouts, theme CSS, responsive behavior, shadow DOM parts, and stale selectors with agent-browser. Use for UI defects, CSS changes, visual regressions, or frontend review in this project."
---

# Debugging Vaadin Interfaces

Verify the rendered runtime. Source CSS alone does not prove that a selector matches, a token exists, or a shadow part is styleable.

## Setup

1. Read `AGENTS.md` and verify the Vaadin version in `pom.xml`; this project currently uses Vaadin 25.1.8.
2. Check `http://localhost:${PORT:-8080}` before starting anything. Never restart an existing app or debugger without approval.
3. Load the global `agent-browser` skill before browser interaction. Reuse a worktree-scoped session so Keycloak authentication survives reloads:

```bash
SESSION="$(agent-browser session id --scope worktree --prefix qa-final-project)"
agent-browser --session "$SESSION" --restore open "http://localhost:${PORT:-8080}"
```

If the restored session is unauthenticated, follow the application's Keycloak login flow using user-provided or already configured local credentials. Never copy credentials into this skill, source files, screenshots, or reports.

## Debugging loop

1. Reproduce the issue at the affected route and viewport. Record the exact state, interaction, and expected result.
2. Capture a semantic snapshot and, for a visual defect, a before screenshot.
3. Inspect the relevant Vaadin host, Java-added classes, state attributes, shadow parts, active theme variables, and computed styles.
4. Map the rendered element back to the owning Java component or stable CSS selector. Do not edit generated frontend files.
5. Make the smallest Java, TypeScript, or CSS correction.
6. Let Hotswap Agent or Vaadin/Vite reload it. Re-run the same interaction and probes; do not assume the source edit reached the browser.
7. Check the viewport where the issue occurred and one adjacent breakpoint only when responsive behavior is affected.
8. Remove a stale selector only when runtime inspection proves its host does not match, its part does not exist, or the changed UI made it obsolete.

Use Playwright instead of agent-browser only when animation timing, smooth scrolling, drag gestures, or frame-by-frame behavior requires it.

## Vaadin inspection

Inspect both light-DOM hosts such as `vaadin-button`, `vaadin-grid`, `vaadin-side-nav-item`, and `vaadin-text-field`, and their open shadow roots.

```bash
agent-browser --session "$SESSION" snapshot -i
```

```bash
agent-browser --session "$SESSION" eval '
[...document.querySelectorAll("vaadin-button,vaadin-grid,vaadin-side-nav-item,vaadin-text-field")].map(el => ({
  tag: el.tagName.toLowerCase(),
  text: el.textContent.trim().replace(/\s+/g, " ").slice(0, 80),
  class: el.className,
  theme: el.getAttribute("theme"),
  attrs: [...el.attributes].map(a => a.name),
  parts: el.shadowRoot
    ? [...el.shadowRoot.querySelectorAll("[part]")].map(p => p.getAttribute("part"))
    : []
}))
'
```

For each changed `::part(name)` rule, prove that the host selector matches and its shadow root exposes `name`. Inspect computed style on that exact part before and after the edit.

## Styling guardrails

- Prefer stable host classes added by Java, documented `::part(...)` names, component theme variants, and existing design tokens.
- This project uses Aura and base Vaadin tokens (`--aura-*` and `--vaadin-*`). Use `--lumo-*` only when runtime inspection proves the token is defined and intentional.
- Preserve hover, focus, selected, disabled, invalid, opened, loading, and keyboard-visible states affected by the rule.
- Do not reach into unsupported shadow internals, use temporary runtime attributes as production selectors, or add `!important` before understanding specificity and cascade order.
- Check contrast, focus visibility, clipping, overflow, labels, and interactive target clarity for the affected surface.

## Hot-swap failures

CSS and TypeScript should refresh through Vaadin/Vite; Java changes should update through the configured Hotswap Agent. If the JVM reports stale-bytecode linkage errors such as `NoSuchMethodError`, stop browser iteration and explain that the existing debug session needs a user-approved restart. Do not rewrite correct application code around a hot-swap limitation.

Report the reproduced state, root cause, source files changed, runtime evidence after the fix, viewport checked, and any verification blocked by authentication or unavailable services.
