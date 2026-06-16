# UC-013: GitHub CI/CD

> The project needs automated validation on pull requests and a Docker image published after successful merges.

---

**Goal:** As a Maintainer, I want GitHub workflows for pull request validation and Docker image publishing so that code changes are tested automatically and merge results are available as a container image.

**Status:** Implemented
**Date:** 2026-06-16

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Maintainer
- **Secondary actors:** GitHub Actions, Docker registry

---

## Preconditions

- The repository is hosted on GitHub.
- The project builds with Maven.
- Unit and integration tests exist or can be selected through Maven test configuration.
- Docker image publishing credentials are available as GitHub Actions secrets.
- The target Docker image namespace is `wornux/qa-final-project`.

---

## Trigger

A pull request is opened or updated, or a pull request is merged into a publishable branch.

---

## Main Flow

1. Maintainer opens or updates a pull request.
2. GitHub Actions starts the pull request unit test workflow.
3. GitHub Actions runs automated unit tests.
4. GitHub Actions starts the pull request integration test workflow.
5. GitHub Actions runs automated integration tests.
6. GitHub Actions generates a JaCoCo coverage report during the test workflows.
7. GitHub Actions uploads the JaCoCo report as a workflow artifact.
8. Maintainer merges the pull request after required checks pass.
9. GitHub Actions starts the Docker build workflow for the merge result.
10. GitHub Actions builds the application Docker image.
11. GitHub Actions publishes the Docker image under `wornux/qa-final-project`.
12. GitHub records the workflow results on the pull request and merge commit.

---

## Alternative Flows

### AF-1: Unit Tests Fail

**Branches from:** Main Flow step 3
**Condition:** The unit test workflow exits with a failing status

1. GitHub Actions marks the unit test workflow as failed.
2. GitHub blocks or flags the pull request according to branch protection rules.
3. Use case ends.

### AF-2: Integration Tests Fail

**Branches from:** Main Flow step 5
**Condition:** The integration test workflow exits with a failing status

1. GitHub Actions marks the integration test workflow as failed.
2. GitHub blocks or flags the pull request according to branch protection rules.
3. Use case ends.

### AF-3: Docker Build Fails

**Branches from:** Main Flow step 10
**Condition:** The Docker build workflow cannot build the application image

1. GitHub Actions marks the Docker build workflow as failed.
2. No Docker image is published for that merge commit.
3. Use case ends.

### AF-4: Docker Publish Fails

**Branches from:** Main Flow step 11
**Condition:** Docker registry authentication, authorization, or push fails

1. GitHub Actions marks the Docker build workflow as failed.
2. The merge commit remains in GitHub, but no successful image publication is recorded.
3. Use case ends.

### AF-5: Non-Publishable Branch Updated

**Branches from:** Main Flow step 9
**Condition:** A branch update is not a merge into a configured publish branch

1. GitHub Actions does not publish a Docker image.
2. Use case ends.

---

## Postconditions

- **On success:** Pull requests have automated unit and integration test results, merges create a Docker image, the image is published under `wornux/qa-final-project`, and `staging` plus `development` branches exist on the remote repository.
- **On failure:** Failed checks or Docker publication are visible in GitHub Actions, and no successful Docker image is published for a failed build.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Implement exactly three GitHub workflows for now: pull request unit tests, pull request integration tests, and merge Docker build/publish. |
| BR-02 | Unit tests must run automatically when a pull request is opened, reopened, synchronized, or updated. |
| BR-03 | Integration tests must run automatically when a pull request is opened, reopened, synchronized, or updated. |
| BR-04 | Test workflows must run JaCoCo coverage reporting. |
| BR-05 | Test workflows must upload the JaCoCo report as a GitHub Actions artifact. |
| BR-06 | Docker build/publish must run only after a merge or push lands on a configured publish branch. |
| BR-07 | The Docker image must be published as `wornux/qa-final-project`. |
| BR-08 | The implementation must not add deployment, environment promotion, release tagging, or runtime orchestration beyond publishing the Docker image. |
| BR-09 | The repository must have remote `staging` and `development` branches published for future workflow expansion. |
| BR-10 | Workflow credentials must be read from GitHub Actions secrets and must not be committed to the repository. |
| BR-11 | Workflow files must live under `.github/workflows/`. |
| BR-12 | The workflows must be deterministic enough for branch protection: failed tests, failed coverage artifact upload, or failed Docker publication must produce failed GitHub checks. |

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [x] Main Flow covered (steps 1-12)
- [x] AF-1, AF-2, AF-3, AF-4, AF-5 covered
- [x] BR-01 through BR-12 covered

---

## UI Surface

- No application UI route is introduced by this use case.
- Maintainers interact with GitHub pull request checks, GitHub Actions workflow runs, remote branches, and the Docker image registry.

| Page | Access |
|------|--------|
| GitHub Pull Request checks | Maintainer |
| GitHub Actions workflow runs | Maintainer |
| Docker image `wornux/qa-final-project` | Registry-authorized user |
