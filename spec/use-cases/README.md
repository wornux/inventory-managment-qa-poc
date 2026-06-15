# Use Cases

This folder contains feature specifications for the Inventory Management application. Each use case describes the business goals, workflows, alternative flows, business rules, and acceptance tests.

## Use Cases

| ID | Title | Status | Actor | Notes |
|----|-------|--------|-------|-------|
| UC-001 | [User Signup](use-case-001-user-signup.md) | Implemented | Unauthenticated User | Account creation with default INVENTORY_VIEWER role |
| UC-002 | [User Login](use-case-002-user-login.md) | Implemented | Registered User | Authentication with username/email and password |
| UC-003 | [Manage Products](use-case-003-manage-products.md) | Implemented | Inventory Manager | CRUD operations for product catalog |
| UC-004 | [Manage Stock Movements](use-case-004-manage-stock-movements.md) | Pending | Warehouse Operator / Manager | Create and view append-only stock movement ledger |
| UC-005 | [Manage Users](use-case-005-manage-users.md) | Pending | System Administrator | CRUD and deactivate user accounts with role assignment |
| UC-006 | [Manage Categories](use-case-006-manage-categories.md) | Pending | Inventory Manager | CRUD and deactivate product categories |
| UC-007 | [Manage Suppliers](use-case-007-manage-suppliers.md) | Pending | Inventory Manager | CRUD and deactivate supplier records |
| UC-008 | [Manage Roles](use-case-008-manage-roles.md) | Pending | System Administrator | Create and manage roles with permission assignment |
| UC-009 | [Manage Permissions](use-case-009-manage-permissions.md) | Pending | System Administrator | Manage permission (resource-action) pairs |

## How to Use

1. **Read a use case** — Click the link in the table to view the full specification.
2. **Follow the main flow** — Understand the primary workflow and expected behavior.
3. **Review alternative flows** — Understand what happens when things go wrong or take different paths.
4. **Check business rules** — Ensure implementation enforces all BR-* rules.
5. **Verify tests** — Implement test coverage for all Main Flow, Alternative Flow, and Business Rule items.

## Status Legend

- **Pending** — Use case is drafted but not yet implemented.
- **In Progress** — Implementation is underway.
- **Implemented** — Code is complete and all tests pass.
- **Verified** — Use case has been reviewed and accepted.
