# Data Model

> Entity definitions and relationships. Evolves as features are added.

## Core Entities

Java entity timestamp fields use `Instant`. PostgreSQL persists them as `timestamptz` columns.

| Entity | Key Fields | Relationships | Purpose |
|--------|------------|---------------|---------|
| AppUser | id, username (UK), email (UK), oidc_issuer, oidc_subject, active, audit fields | Has many Roles through UserRole | User accounts for application access |
| Role | id, code (UK), name, description, system_role, active, permissions (`text[]`), audit fields | Has many AppUsers through UserRole | Global access profile with fixed application permission codes |
| UserRole | user_id (FK), role_id (FK) | Belongs to AppUser and Role | Global role assignment; there are no platform, tenant, or class levels |
| Category | id, name (UK), description, active, audit fields | Has many Products | Product categories |
| Supplier | id, name, contact fields, active, audit fields | Has many Products | Supplier information |
| Product | id, sku (UK), name, pricing and stock fields, category_id, supplier_id, active, audit fields | Belongs to Category and optional Supplier; has many StockMovements | Inventory items |
| StockMovement | id, product_id, user_id, movement_type, quantity_delta, reason, audit fields | Belongs to Product and AppUser | Append-only inventory ledger |

## Key Constraints

- Usernames, emails, role codes, category names, and product SKUs are unique.
- A role has at least one permission selected by application validation.
- Permission codes come from the compile-time `AppPermission` catalog; they are not user-created database records.
- A user can have multiple global roles, but the same role cannot be assigned twice.
- Product prices and stock thresholds cannot be negative.
- Stock movement quantity cannot be zero and must match its movement direction.

## Access Control Model

The application uses single-level RBAC:

1. Users receive one or more global roles through `user_role`.
2. Each active role stores permission codes such as `product:view` and `role:assign`.
3. `AppResource`, `AppAction`, and `AppPermission` define the valid catalog in Java.
4. Authentication exposes active permission codes as Spring Security authorities for interoperability.
5. Services and Vaadin navigation use the same `AuthorizationService`, which re-reads active roles so revocations affect existing sessions.
6. Any action for a resource implies that resource's `view` permission, matching the Socratic Tutor RBAC semantics.

Role namespaces, assignment levels, active contexts, and tenant/class role tables are intentionally omitted because this is a single-context inventory domain.
