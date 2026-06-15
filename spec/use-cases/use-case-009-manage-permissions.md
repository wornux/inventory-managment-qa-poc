# UC-009: Manage Permissions

> System administrators view, create, edit, and deactivate permissions (resource-action pairs).

---

**Goal:** As a system administrator, I want to manage permissions (resource-action pairs) so that the authorization model can be configured and maintained.

**Status:** Pending
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** System Administrator (user with PERMISSION:CREATE, PERMISSION:UPDATE, PERMISSION:DELETE permissions)
- **Secondary actors:** None

---

## Preconditions

- User is logged in and has PERMISSION:READ permission
- User has PERMISSION:CREATE, PERMISSION:UPDATE, PERMISSION:DELETE permissions for full management
- Resources and Actions exist in the database
- Permissions table exists in the database

---

## Trigger

User navigates to the Permissions view from the main administration menu.

---

## Main Flow

### Main Flow: View and Search Permissions

1. System displays a grid containing all permissions with columns: Resource, Action, Description, Active badge, Role Count, Actions.
2. System displays filter controls for Resource, Action, and Active/Inactive status.
3. User optionally filters by resource, action, or active status.
4. Grid updates to show matching permissions.
5. User can click on a row to view permission details in a dismissible sidebar form (read-only or edit mode).

### Main Flow: Create Permission

6. User clicks the "New Permission" button above the grid.
7. System opens a sidebar form with fields: Resource (required, dropdown), Action (required, dropdown), Description (optional), Active checkbox (default true).
8. User selects a resource and action that form a unique pair, optionally enters a description, and clicks "Save".
9. System validates the form (see Business Rules).
10. System checks for duplicate (resource, action) pair.
11. System creates the permission record in the database.
12. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Edit Permission

13. User clicks the "Edit" action on a permission row.
14. System opens the sidebar form pre-populated with the permission's current data.
15. User modifies the description and/or active status and clicks "Save".
16. System validates the form.
17. System updates the permission record in the database.
18. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Deactivate Permission

19. User clicks the "Deactivate" action on an active permission row.
20. System displays a confirmation dialog: "Deactivate this permission? Roles that have this permission will be unaffected, but new roles cannot be assigned this permission."
21. User confirms the deactivation.
22. System sets the permission's active flag to false and persists the change.
23. System closes the dialog, refreshes the grid, and displays success notification.

---

## Alternative Flows

### AF-1: Duplicate Permission (Resource-Action Pair)

**Branches from:** Main Flow step 10 (Create)
**Condition:** The selected (resource, action) pair already exists in the database

1. System displays error message: "This resource-action combination already exists."
2. User selects a different resource or action and resubmits.
3. Returns to Main Flow step 10.

### AF-2: Missing Required Fields

**Branches from:** Main Flow step 10 (Create) or 16 (Edit)
**Condition:** Resource or Action field is not selected

1. System displays field-level validation errors.
2. User selects both a resource and an action and resubmits.
3. Returns to Main Flow step 10 or 16.

### AF-3: Resource or Action Inactive

**Branches from:** Main Flow step 9 (Create)
**Condition:** Selected resource or action is inactive

1. System displays error message: "The selected resource and/or action is inactive. Please select active resources and actions only."
2. User selects different, active options and resubmits.
3. Returns to Main Flow step 9.

### AF-4: Insufficient Permissions

**Branches from:** Main Flow step 6 (New Permission), 13 (Edit), or 19 (Deactivate)
**Condition:** User lacks the required permission (PERMISSION:CREATE, PERMISSION:UPDATE, or PERMISSION:DELETE)

1. System hides the "New Permission", "Edit", and "Deactivate" buttons/actions.
2. User can only view the permission grid in read-only mode.
3. Use case ends.

### AF-5: Permission Has Role Assignments

**Branches from:** Main Flow step 19 (Deactivate)
**Condition:** One or more roles have this permission assigned

1. System displays warning dialog: "This permission has [N] role assignments. Deactivating it will not remove existing assignments, but new roles cannot be assigned this permission."
2. User can confirm or cancel the deactivation.
3. If confirmed, use case continues to Main Flow step 22.
4. If cancelled, use case ends.

### AF-6: Sidebar Form Dirty State

**Branches from:** Main Flow step 7 or 14 (Form opened)
**Condition:** User modifies form fields and attempts to close without saving

1. System displays a confirmation dialog: "You have unsaved changes. Discard them?"
2. User can choose to save, cancel, or discard.
3. Returns to Main Flow or use case ends based on choice.

### AF-7: Concurrent Edit Conflict

**Branches from:** Main Flow step 15 (Edit—database update)
**Condition:** Another admin edited the same permission between the time the form was opened and the save was submitted

1. System displays error message: "Permission was updated by another administrator. Refresh the form and try again."
2. System reloads the form with the latest data.
3. User reviews and resubmits if desired.
4. Returns to Main Flow step 15.

### AF-8: Resource or Action No Longer Active

**Branches from:** Main Flow step 14 (Edit)
**Condition:** The resource or action that make up this permission has been deactivated since the form was opened

1. System displays warning message: "The resource and/or action for this permission is no longer active."
2. User can view the current state and update if desired.
3. Returns to Main Flow step 15 or use case ends.

---

## Postconditions

- **On success:** Permission is created, updated, or deactivated in the database; grid is refreshed; success notification is displayed.
- **On failure:** No changes are persisted; error message is displayed; user remains on the form or grid.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | A permission must be a unique combination of (resource, action) |
| BR-02 | Both resource and action must be selected and active |
| BR-03 | Description is optional |
| BR-04 | Permissions are deactivated instead of deleted to preserve role assignment history |
| BR-05 | Deactivating a permission does not remove it from roles; existing role assignments remain |
| BR-06 | New roles cannot be assigned deactivated permissions |
| BR-07 | Resource and action dropdowns show only active resources and actions |
| BR-08 | Grid displays count of roles assigned to each permission |
| BR-09 | Permissions cannot have resource or action edited after creation; only description and active status can change |

---

## Tests

- [ ] Main Flow: View and filter permissions covered
- [ ] Main Flow: Create permission covered
- [ ] Main Flow: Edit permission covered
- [ ] Main Flow: Deactivate permission covered
- [ ] AF-1 (duplicate pair) covered
- [ ] AF-2 (missing required fields) covered
- [ ] AF-3 (resource or action inactive) covered
- [ ] AF-4 (insufficient permissions) covered
- [ ] AF-5 (permission has role assignments) covered
- [ ] AF-6 (dirty state) covered
- [ ] AF-7 (concurrent edit) covered
- [ ] AF-8 (resource/action no longer active) covered
- [ ] BR-01 through BR-09 covered

---

## UI Surface

- **Permission List Page:** Grid with filters for resource, action, and active status.
- **Sidebar Form:** Create/edit/view form displayed in a dismissible right-side panel.
- **Resource/Action Dropdowns:** Show only active resources and actions during create.
- **Confirmation Dialogs:** Deactivate confirmation and dirty-state warning.
- **Notifications:** Success and error messages displayed at top of view.
- **Active/Inactive Badge:** Visual indicator using LitRenderer.
- **Role Count:** Display number of roles assigned to each permission.

| Page | Access |
|------|--------|
| Permission List | Authenticated (PERMISSION:READ) |
| Create/Edit/View Permission | Authenticated (PERMISSION:CREATE/UPDATE) |
