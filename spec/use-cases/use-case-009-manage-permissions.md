# UC-009: Manage Permissions

> Superseded by the fixed permission catalog used by UC-008.

**Status:** Superseded
**Date:** 2026-07-25

The inventory domain no longer exposes permission, resource, or action CRUD. Valid permissions are compile-time `AppPermission` values composed from `AppResource` and `AppAction`.

Administrators assign these fixed permissions while managing roles in UC-008. Adding a new permission requires adding the corresponding enum value with the business feature that consumes it; there is no `/permissions` route or permission table.

This keeps RBAC single-level and prevents runtime permission records that no service recognizes.
