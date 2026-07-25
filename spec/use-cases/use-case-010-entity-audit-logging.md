# UC-010: Entity Audit Logging

> System administrators rely on entity-level audit history for inventory and security changes.

---

**Goal:** As a System Administrator, I want all domain entity changes audited so that inventory/security changes can be traced to revision metadata.

**Status:** Implemented
**Date:** 2026-06-16

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** System Administrator
- **Secondary actors:** Hibernate Envers

---

## Preconditions

- Application uses Spring Security authentication.
- Flyway manages schema changes.
- Domain entities exist.

---

## Trigger

Developer implements UC-010 and the application starts with validated JPA schema.

---

## Main Flow

1. System starts with Hibernate Envers enabled.
2. Authenticated user creates, updates, or deactivates any audited domain entity.
3. System persists the entity change.
4. Hibernate Envers creates a revision row with modifier user and IP address.
5. Hibernate Envers writes the entity snapshot into the matching `_log` table.
6. Spring Data auditing updates created/modified metadata on the live table.

---

## Alternative Flows

### AF-1: No Authenticated User

**Branches from:** Main Flow step 4
**Condition:** No authenticated user is available in the security context

1. System records `ANONYMOUS` as the modifier user.
2. System records `0.0.0.0` as the IP address.
3. Returns to Main Flow step 5.

### AF-2: Delete or Deactivate Event

**Branches from:** Main Flow step 2
**Condition:** Entity state is deleted or deactivated

1. System persists the state change.
2. Hibernate Envers stores entity data at delete time.
3. Returns to Main Flow step 4.

### AF-3: Audit Schema Missing

**Branches from:** Main Flow step 1
**Condition:** An expected audit table, revision table, or audit metadata column is missing

1. System startup fails because JPA schema validation cannot validate the model.
2. Use case ends.

### AF-4: Future JWT Context Unsupported

**Branches from:** Main Flow step 4
**Condition:** A future JWT-authenticated request is not yet mapped into the current-user helper

1. Current-user helper falls back to the existing Spring Security principal resolution.
2. If no principal can be resolved, system records `ANONYMOUS`.
3. Returns to Main Flow step 5.

---

## Postconditions

- **On success:** Every audited change has live audit metadata and an Envers revision history row in the matching `_log` table.
- **On failure:** Transaction rolls back; no partial entity or audit log write remains.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Audit tables must use the `_log` suffix, not the default Envers suffix. |
| BR-02 | Deleted entity data must be stored in audit rows. |
| BR-03 | Revision metadata must include modifier user and IP address. |
| BR-04 | Missing authenticated user must resolve to `ANONYMOUS`. |
| BR-05 | Live audited entities must use `createdBy`, `createdDate`, `lastModifiedBy`, and `lastModifiedDate`. |
| BR-06 | All persisted domain entities must be audited: users, roles, categories, suppliers, products, and stock movements. Fixed permission enums are not persisted entities. |
| BR-07 | Audited relationship tables must be created where Envers tracks many-to-many assignments. |

---

## Tests

- [x] Main Flow covered (steps 1-6)
- [x] AF-1, AF-2, AF-3, AF-4 covered
- [x] BR-01 through BR-07 covered

---

## UI Surface

- No user-facing UI is introduced by this use case.
- Audit logging is infrastructure-level behavior triggered by existing entity persistence flows.

| Page | Access |
|------|--------|
| None | Not applicable |
