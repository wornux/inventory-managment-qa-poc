# UC-008: Manage Roles

> System administrators view, create, edit, and deactivate global roles with typed permission assignments.

---

**Goal:** As a system administrator, I want to manage roles and assign permissions so that users have appropriate access levels based on their responsibilities.

**Status:** Implemented
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** System Administrator (user with ROLE:CREATE, ROLE:UPDATE, ROLE:DELETE, ROLE:ASSIGN permissions)
- **Secondary actors:** None

---

## Preconditions

- User is logged in and has ROLE:VIEW permission
- User has ROLE:CREATE, ROLE:UPDATE, ROLE:DELETE, and ROLE:ASSIGN permissions for full management
- Valid permissions exist in the compile-time `AppPermission` catalog
- Roles store permission codes in the database; there are no permission records or assignment levels

---

## Trigger

User navigates to the Roles view from the main administration menu.

---

## Main Flow

### Main Flow: View and Search Roles

1. System displays a master-detail workspace with a compact role column and a selected-role detail panel.
2. The role column shows each role's name, code, member count, a text search, and System/Custom and Active/Inactive filters.
3. User optionally enters search text (role code or name) and filters are applied in real time.
4. The role column updates to show matching roles and selects the first available role when needed.
5. The detail panel shows Information, Permissions, and Members tabs for the selected global role.
6. User selects a role to inspect its metadata, typed permissions grouped by resource, and assigned users; editable custom roles expose Edit and Deactivate actions.

### Main Flow: Create Custom Role

7. User clicks the "New Role" button above the grid.
8. System opens a sidebar form with fields: Code (required), Name (required), Description (optional), Active checkbox (default true), Permissions (multi-select, required).
9. User fills in the role code and name, optionally enters a description, and selects one or more permissions.
10. User clicks "Save".
11. System validates the form (see Business Rules).
12. System checks for duplicate role code.
13. System creates the global role with `system_role=false` and stores the selected permission codes.
14. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Edit Role

15. User selects a custom role and clicks the "Edit role" action in its detail header (system roles cannot be edited).
16. System opens the sidebar form pre-populated with the role's current data and permission assignments.
17. User modifies one or more fields (name, description, active status, permissions) and clicks "Save".
18. System validates the form.
19. System updates the role record and permission assignments in the database.
20. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Deactivate Role

21. User selects an active custom role and clicks the "Deactivate" action in its detail header.
22. System displays a confirmation dialog: "Deactivate this role? Users with this role will retain it, but new users cannot be assigned to it."
23. User confirms the deactivation.
24. System sets the role's active flag to false and persists the change.
25. System closes the dialog, refreshes the grid, and displays success notification.

---

## Alternative Flows

### AF-1: Duplicate Role Code

**Branches from:** Main Flow step 11 (Create)
**Condition:** Entered role code already exists in the database

1. System displays error message: "Role code already exists. Please choose a different one."
2. User modifies the code and resubmits.
3. Returns to Main Flow step 11.

### AF-2: Missing Required Fields

**Branches from:** Main Flow step 11 (Create) or 18 (Edit)
**Condition:** Role code, name, or permissions are missing

1. System displays field-level validation errors.
2. User fills in all required fields and resubmits.
3. Returns to Main Flow step 11 or 18.

### AF-3: Attempt to Edit System Role

**Branches from:** Main Flow step 15 (Edit)
**Condition:** User attempts to edit a role with system_role=true

1. System disables the edit button and displays message: "System roles cannot be edited."
2. User can view the role in read-only mode only.
3. Use case ends.

### AF-4: Insufficient Permissions

**Branches from:** Main Flow step 7 (New Role), 15 (Edit), or 21 (Deactivate)
**Condition:** User lacks the required permission (ROLE:CREATE, ROLE:UPDATE, ROLE:DELETE, or ROLE:ASSIGN)

1. System hides the "New Role", "Edit", and "Deactivate" buttons/actions.
2. User can only view the role grid and details in read-only mode.
3. Use case ends.

### AF-5: Role Has Users

**Branches from:** Main Flow step 21 (Deactivate)
**Condition:** The role has users assigned to it

1. System displays warning dialog: "This role has [N] users. Deactivating the role will not affect their existing assignments, but new users cannot be assigned to this role."
2. User can confirm or cancel the deactivation.
3. If confirmed, use case continues to Main Flow step 24.
4. If cancelled, use case ends.

### AF-6: No Permissions Selected

**Branches from:** Main Flow step 11 (Create) or 18 (Edit)
**Condition:** User attempts to create/save a role without selecting any permissions

1. System displays error message: "At least one permission must be selected."
2. User selects one or more permissions and resubmits.
3. Returns to Main Flow step 11 or 18.

### AF-7: Sidebar Form Dirty State

**Branches from:** Main Flow step 8 or 16 (Form opened)
**Condition:** User modifies form fields and attempts to close without saving

1. System displays a confirmation dialog: "You have unsaved changes. Discard them?"
2. User can choose to save, cancel, or discard.
3. Returns to Main Flow or use case ends based on choice.

### AF-8: Concurrent Edit Conflict

**Branches from:** Main Flow step 17 (Edit—database update)
**Condition:** Another admin edited the same role between the time the form was opened and the save was submitted

1. System displays error message: "Role was updated by another administrator. Refresh the form and try again."
2. System reloads the form with the latest data.
3. User reviews and resubmits if desired.
4. Returns to Main Flow step 17.

### AF-9: Actor Attempts to Grant an Unowned Permission

**Branches from:** Main Flow step 11 or 18
**Condition:** The administrator selects a permission they do not possess

1. System rejects the save with: "You cannot assign permissions that you do not have."
2. No role changes are persisted.
3. User removes the unavailable permission or asks a higher-privileged administrator to make the change.

---

## Postconditions

- **On success:** Role is created, updated, or deactivated in the database; permission assignments are updated; grid is refreshed; success notification is displayed.
- **On failure:** No changes are persisted; error message is displayed; user remains on the form or grid.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Role code must be unique, not blank, and immutable after creation |
| BR-02 | Role name is required and not blank |
| BR-03 | Description is optional |
| BR-04 | System roles (system_role=true) cannot be edited or deleted |
| BR-05 | Custom roles can be deactivated but not physically deleted |
| BR-06 | A role must have at least one permission assigned |
| BR-07 | Users assigned to a deactivated role retain their role assignment |
| BR-08 | New users cannot be assigned to deactivated roles |
| BR-09 | Search filters by role code and name (case-insensitive partial match) |
| BR-10 | Grid displays count of users assigned to each role and count of permissions |
| BR-11 | Permission values must come from `AppPermission`; resources, actions, and permissions are not runtime CRUD records |
| BR-12 | An administrator cannot grant a permission they do not possess |
| BR-13 | All user-role assignments are global; role namespaces and assignment levels are not used |
| BR-14 | Deactivated roles and users stop contributing permissions immediately, including in existing sessions |

---

## Tests

- [x] Main Flow: View and search roles covered
- [x] Main Flow: Create role covered
- [x] Main Flow: Edit role covered
- [x] Main Flow: Deactivate role covered
- [x] AF-1 (duplicate code) covered
- [x] AF-2 (missing required fields) covered
- [x] AF-3 (attempt to edit system role) covered
- [x] AF-4 (insufficient permissions) covered
- [x] AF-5 (role has users) covered
- [x] AF-6 (no permissions selected) covered
- [x] AF-7 (dirty state) covered
- [x] AF-8 (concurrent edit) covered
- [x] AF-9 (attempt to grant unowned permission) covered
- [x] BR-01 through BR-14 covered

---

## UI Surface

- **Role Management Workspace:** Responsive master-detail surface with a searchable/filterable role column and a persistent selected-role panel.
- **Role Detail Tabs:** Information, typed permissions grouped by resource, and read-only assigned-member list.
- **Sidebar Form:** Create/edit form displayed in a dismissible right-side panel.
- **Permission Selection:** Multi-select dropdown populated from the fixed `AppPermission` catalog.
- **Confirmation Dialogs:** Deactivate confirmation and dirty-state warning.
- **Notifications:** Success and error messages displayed at top of view.
- **System Role Badge:** Visual indicator (e.g., "System" or "Custom") using LitRenderer.
- **Active/Inactive Badge:** Visual indicator using LitRenderer.
- **User/Permission Count:** Display counts in grid columns.

| Page | Access |
|------|--------|
| Role List | Authenticated (ROLE:VIEW) |
| Create/Edit/View Role | Authenticated (ROLE:CREATE/UPDATE/ASSIGN) |
