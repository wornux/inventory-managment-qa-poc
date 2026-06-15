# Project Context

> High-level context for the project: the problem being solved, who it's for, what's in scope, and what constraints apply.

## 1. Vision

Build a comprehensive inventory management web application that enables organizations to track products, suppliers, stock levels, and movements across their warehouse operations. The system provides secure role-based access to inventory data, allowing managers to make informed decisions while maintaining data integrity and audit trails. Success means users can quickly manage product catalogs, track stock movements, and generate reports with confidence that their data is accurate and secure.

## 2. Users

**Inventory Manager:** Administrative user who manages the complete inventory system. Can create, edit, and delete products, categories, suppliers, and stock movements. Can manage users and roles within the system.

**Warehouse Operator:** Warehouse staff who register stock movements (purchases, sales, returns, adjustments, damage, loss). Can view inventory data and product details but cannot modify master data.

**Inventory Viewer:** Read-only user with visibility into all inventory data, useful for reporting and analysis roles. Cannot modify any data.

**System Administrator:** Technical admin who manages user accounts, roles, permissions, resources, and actions. Can perform all administrative functions.

## 3. Constraints

- Platform: Web application using Vaadin Flow (server-side Java UI) and Spring Boot
- Database: PostgreSQL with Flyway migrations configured from the start
- Architecture: Single-tenant application, no multi-tenant modeling
- Deployment: Docker containers for local development and testing
- Authentication: Spring Security with database-backed user accounts and role-based access control
- Data Integrity: Append-only stock movement ledger; stock adjustments are immutable
- Performance: Support for typical warehouse operations without specific scaling requirements
- Security: Password hashing required; no raw password storage; public routes limited to login/signup

> For technology stack and application structure details, see [`architecture.md`](architecture.md).

---

# Related Documents

- [Spec README](README.md) — process overview and workflow
- [Architecture](architecture.md) — technology stack and application structure
- [Design System](design-system.md) — theme, component usage, and visual standards
- [Use Case Template](use-cases/use-case-template.md) — template for feature specifications
