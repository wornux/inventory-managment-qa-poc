# Data Model

> Entity definitions and relationships. Evolves as features are added.

## Core Entities

Java entity timestamp fields use `Instant`. PostgreSQL persists them as `timestamptz` columns with `default now()`.

| Entity | Key Fields | Relationships | Purpose |
|--------|-----------|---------------|---------| 
| AppUser | id, username (UK), email (UK), password_hash, active, created_at, updated_at | Has many UserRole | User accounts for application access |
| Role | id, code (UK), name, description, system_role, active, created_at, updated_at | Has many UserRole; Has many RolePermission | User roles for access control (system and custom) |
| UserRole | user_id (FK), role_id (FK) | Belongs to AppUser; Belongs to Role | Assignment of roles to users |
| Resource | id, code (UK), name, description, active, created_at, updated_at | Has many Permission | Named resources protected by permissions (e.g., PRODUCT, CATEGORY) |
| Action | id, code (UK), name, description, active, created_at, updated_at | Has many Permission | Actions that can be performed on resources (e.g., CREATE, READ, UPDATE, DELETE) |
| Permission | id, resource_id (FK), action_id (FK), description, active, created_at, updated_at | Belongs to Resource; Belongs to Action; Has many RolePermission | Unique resource-action pairs that define allowed operations |
| RolePermission | role_id (FK), permission_id (FK) | Belongs to Role; Belongs to Permission | Assignment of permissions to roles |
| Category | id, name (UK), description, active, created_at, updated_at | Has many Product | Product categories for classification |
| Supplier | id, name, contact_name, email, phone, active, created_at, updated_at | Has many Product | Supplier information for sourcing |
| Product | id, sku (UK), name, description, unit_price, quantity_on_hand, minimum_stock, active, category_id (FK), supplier_id (FK), created_at, updated_at | Belongs to Category; Belongs to Supplier; Has many StockMovement | Inventory items with pricing and stock tracking |
| StockMovement | id, product_id (FK), user_id (FK), movement_type, quantity_delta, reason, created_at | Belongs to Product; Belongs to AppUser | Append-only ledger of all inventory changes |

## Key Constraints

- **User:** username and email must be unique; password is always stored as hash
- **Role:** code must be unique; system_role flag indicates built-in vs custom roles
- **Resource:** code must be unique; represents a protected business entity
- **Action:** code must be unique; represents an operation type
- **Permission:** combination of (resource_id, action_id) must be unique
- **Category:** name must be unique
- **Product:** sku must be unique; unit_price >= 0; quantity_on_hand >= 0; minimum_stock >= 0
- **StockMovement:** quantity_delta cannot be zero; type must match direction (inbound/outbound); append-only (no edits or deletes)

## Stock Movement Types

| Type | Direction | Requires Reason | Purpose |
|------|-----------|-----------------|---------|
| PURCHASE | In (+) | No | Purchase orders received |
| SALE | Out (-) | No | Products sold |
| RETURN_IN | In (+) | No | Customer returns received |
| RETURN_OUT | Out (-) | No | Returned to supplier |
| ADJUSTMENT_IN | In (+) | Yes | Stock level corrections (additions) |
| ADJUSTMENT_OUT | Out (-) | Yes | Stock level corrections (removals) |
| INITIAL_STOCK | In (+) | No | Initial inventory load |
| DAMAGED | Out (-) | Yes | Damaged items removed from stock |
| LOST | Out (-) | Yes | Lost or missing items |

## Relationships

- One AppUser has many UserRole and many StockMovement
- One Role has many UserRole and many RolePermission
- One Resource has many Permission
- One Action has many Permission
- One Permission has many RolePermission
- One Category has many Product
- One Supplier has many Product
- One Product has many StockMovement

## Access Control Model

The system implements Role-Based Access Control (RBAC) using a normalized permission model:
- Users are assigned to Roles
- Roles are granted Permissions
- Permissions are defined as (Resource, Action) pairs
- Authorization is determined by checking if at least one of the user's active roles has an active permission for the requested resource and action
