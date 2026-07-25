---
name: writing-commit-messages
description: "Drafts and applies Conventional Commit messages from the actual Git diff while preserving unrelated changes. Use when asked to write a commit message, commit changes, split work into commits, or amend a commit."
---

# Writing Commit Messages

Use the repository's established Conventional Commit style and commit only the intended semantic change.

## Format

```text
<type>(<scope>): <imperative summary>

<optional explanation of why and behavior>

<optional issue reference>
```

Omit the scope when no single scope is useful. Common scopes in this repository include `ui`, `security`, `auth`, `api`, `catalog`, `persistence`, `observability`, `config`, and `test`.

Use `feat`, `fix`, `refactor`, `test`, `docs`, `perf`, `build`, `ci`, `style`, `chore`, or `revert` according to the change's purpose. Do not call behavior changes `chore` merely because they are small.

## Message rules

- Start the summary lowercase, use imperative mood, omit the trailing period, and keep the whole subject concise—ideally at most 72 characters.
- Describe one semantic change. If the subject needs “and” for unrelated outcomes, split the commit.
- Add a body only when the motivation, previous behavior, tradeoff, migration concern, or verification is not obvious from the subject.
- Explain why and observable behavior rather than listing files. Wrap body text near 72 characters.
- Include issue references only when they are present in the user request, branch context, or diff; never invent them.
- Mark breaking changes with `!` and a `BREAKING CHANGE:` footer only when compatibility actually breaks.

## Workflow

1. Run `git status --short --branch`, `git diff --stat`, and the relevant full diff. Inspect `git diff --cached` separately if anything is staged.
2. Identify semantic groups and distinguish the user's intended changes from unrelated work already in the tree.
3. Draft one message per group. If the user only asked for text, return the draft and do not mutate Git state.
4. If the user asked to commit, stage explicit files or hunks for that group. Do not use `git add -A`, `git add .`, or otherwise absorb unrelated changes.
5. Review `git diff --cached --check` and `git diff --cached` before committing. Ensure the staged diff matches the message and contains no secrets or generated output.
6. Run `git commit` with the finalized message. Use real line breaks for a multiline `-m` body.
7. Report the commit hash, subject, included scope, and verification status.

Do not amend, squash, rebase, push, switch branches, or delete branches unless the user explicitly requests that specific action. Never bypass hooks or discard changes to manufacture a clean commit.
