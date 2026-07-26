---
name: creating-pull-requests
description: >-
  Creates GitHub pull requests for the Inventory Management QA POC using the
  complete remote branch diff, staging as the base, Conventional Commit titles,
  focused implementation details, optional Mermaid diagrams, and a review
  request for Manuel. Use when asked to create, open, or draft a pull request.
argument-hint: "[--draft]"
---

# Creating Pull Requests

Create a GitHub PR that matches this repository's review workflow.

## Workflow

### 1. Fetch first

```bash
git fetch origin
```

Always compare remote refs so the PR is based on current GitHub state.

### 2. Gather the complete branch state

Run these checks together:

```bash
git status --short --branch
git diff
git diff --staged
git branch --show-current
git rev-parse --abbrev-ref --symbolic-full-name @{u} 2>/dev/null || true
git log --oneline origin/staging..HEAD
git diff origin/staging...HEAD
```

The normal promotion PR is `development` into `staging`. Analyze every commit
and file in the three-dot diff, not only `HEAD`.

Before continuing, check:

- The branch has commits beyond `origin/staging`.
- The diff contains no credentials, tokens, `.env` files, personal data, or generated output.
- Uncommitted files are either intentionally excluded or committed separately at the user's request.
- An open PR for the same head and base does not already exist.

```bash
gh pr list --head "$(git branch --show-current)" --base staging --state open
```

If one exists, return its URL instead of creating a duplicate.

### 3. Write the title

Use the dominant Conventional Commit type and scope:

```text
<type>(<scope>): <imperative summary>
```

Omit the scope when no single scope fits. Do not add Jira references or ticket
identifiers unless the user explicitly supplies one.

### 4. Write the body

Use only sections that help reviewers:

````markdown
## Summary

- <one or two bullets explaining why the change exists>

## Business Logic

<product or operator value and behavior>

## Architecture/Flow Diagram

```mermaid
<small diagram when the change crosses components or has a non-trivial flow>
```

## Technical Implementation

### <area>

<focused explanation and, when useful, a short snippet from the diff>

## Verification

- `<command actually run>` - <result>

## Files Changed

- `path/to/file` - <purpose>
````

Rules:

- Base claims only on the full diff and checks that actually ran.
- Keep the summary focused on why, not a file dump.
- Include Mermaid only when it clarifies a workflow, integration, state change, or architecture.
- Include API examples only for API-facing behavior.
- List changed paths with a one-line purpose.
- Never add a `Benefits` section.

### 5. Push when needed

```bash
# No upstream
git push -u origin HEAD

# Existing upstream
git push origin HEAD
```

Never force-push unless the user explicitly requests it.

### 6. Create the PR against staging

Prefer a body file so Markdown remains literal:

```bash
gh pr create \
  --base staging \
  --head "$(git branch --show-current)" \
  --title "<title>" \
  --body-file /tmp/pr-body.md
```

Add `--draft` when requested.

### 7. Request review

For a ready PR, request Manuel directly:

```bash
gh pr edit <pr-number> --add-reviewer manuujrodcruz
```

Do not request reviewers on drafts. Do not tag Clima Call reviewers such as
Fred or Hector in this repository.

### 8. Verify and return

```bash
gh pr view <pr-number> --json url,title,state,isDraft,baseRefName,headRefName,reviewRequests
```

Return the PR URL, base/head branches, reviewer request, checks that ran, and
any blocker. Do not claim unreported CI or local checks passed.

## Guardrails

- Never update Git configuration.
- Never create an empty or duplicate PR.
- Never commit or publish secrets, runtime logs, generated output, or unrelated work.
- Never force-push without explicit approval.
- Never add agent authors, committers, co-author trailers, or agent attribution to commits.
- Stop and report authentication or permission failures instead of inventing a PR URL.
