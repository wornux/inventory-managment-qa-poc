# UC-008: Manage Roles

> System administrators view, create, edit, and deactivate roles with permission assignments.

---

**Goal:** As a system administrator, I want to manage roles and assign permissions so that users have appropriate access levels based on their responsibilities.

**Status:** Pending
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** System Administrator (user with ROLE:CREATE, ROLE:UPDATE, ROLE:DELETE, ROLE:ASSIGN permissions)
- **Secondary actors:** None

---

## Preconditions

- User is logged in and has ROLE:READ permission
- User has ROLE:CREATE, ROLE:UPDATE, ROLE:DELETE, and ROLE:ASSIGN permissions for full management
- Permissions exist in the database
- Roles table exists in the database

---

## Trigger

User navigates to the Roles view from the main administration menu.

---

## Main Flow

### Main Flow: View and Search Roles

1. System displays a grid containing all roles with columns: Code, Name, Description, System Role badge, Active badge, User Count, Permission Count, Actions.
2. System displays a text search field above the grid.
3. System displays filter controls for System/Custom role type and Active/Inactive status.
4. User optionally enters search text (role code or name) and filters are applied in real time.
5. Grid updates to show matching roles.
6. User can click on a row to view role details in a dismissible sidebar form (read-only or edit mode).

### Main Flow: Create Custom Role

7. User clicks the "New Role" button above the grid.
8. System opens a sidebar form with fields: Code (required), Name (required), Description (optional), Active checkbox (default true), Permissions (multi-select, required).
9. User fills in the role code and name, optionally enters a description, and selects one or more permissions.
10. User clicks "Save".
11. System validates the form (see Business Rules).
12. System checks for duplicate role code.
13. System creates the role record with system_role=false and assigns the selected permissions.
14. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Edit Role

15. User clicks the "Edit" action on a custom role row (system roles cannot be edited).
16. System opens the sidebar form pre-populated with the role's current data and permission assignments.
17. User modifies one or more fields (name, description, active status, permissions) and clicks "Save".
18. System validates the form.
19. System updates the role record and permission assignments in the database.
20. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Deactivate Role

21. User clicks the "Deactivate" action on an active custom role row.
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

### AF-9: Permission No Longer Active

**Branches from:** Main Flow step 17 (Edit)
**Condition:** A permission that was assigned to the role has been deactivated since the form was opened

1. System displays warning message: "One or more permissions are no longer active and have been deselected."
2. User can reselect them or leave them deselected and save.
3. Returns to Main Flow step 17.

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

---

## Tests

- [ ] Main Flow: View and search roles covered
- [ ] Main Flow: Create role covered
- [ ] Main Flow: Edit role covered
- [ ] Main Flow: Deactivate role covered
- [ ] AF-1 (duplicate code) covered
- [ ] AF-2 (missing required fields) covered
- [ ] AF-3 (attempt to edit system role) covered
- [ ] AF-4 (insufficient permissions) covered
- [ ] AF-5 (role has users) covered
- [ ] AF-6 (no permissions selected) covered
- [ ] AF-7 (dirty state) covered
- [ ] AF-8 (concurrent edit) covered
- [ ] AF-9 (permission no longer active) covered
- [ ] BR-01 through BR-10 covered

---

## UI Surface

- **Role List Page:** Grid with search, filters, and action buttons.
- **Sidebar Form:** Create/edit/view form displayed in a dismissible right-side panel.
- **Permission Selection:** Multi-select dropdown or checkbox tree for permission selection.
- **Confirmation Dialogs:** Deactivate confirmation and dirty-state warning.
- **Notifications:** Success and error messages displayed at top of view.
- **System Role Badge:** Visual indicator (e.g., "System" or "Custom") using LitRenderer.
- **Active/Inactive Badge:** Visual indicator using LitRenderer.
- **User/Permission Count:** Display counts in grid columns.

| Page | Access |
|------|--------|
| Role List | Authenticated (ROLE:READ) |
| Create/Edit/View Role | Authenticated (ROLE:CREATE/UPDATE/ASSIGN) |
