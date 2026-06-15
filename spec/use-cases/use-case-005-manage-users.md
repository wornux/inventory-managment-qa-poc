# UC-005: Manage Users

> System administrators view, search, create, edit, and deactivate user accounts with role assignments.

---

**Goal:** As a system administrator, I want to manage user accounts and role assignments so that access control is properly configured and maintained.

**Status:** Pending
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** System Administrator (user with USER:CREATE, USER:UPDATE, USER:DELETE permissions)
- **Secondary actors:** None

---

## Preconditions

- User is logged in and has USER:READ permission
- User has USER:CREATE, USER:UPDATE, USER:DELETE, and USER:ASSIGN permissions for full management
- Roles exist in the database
- Users table exists in the database

---

## Trigger

User navigates to the Users view from the main administration menu.

---

## Main Flow

### Main Flow: View and Search Users

1. System displays a grid containing all users with columns: Username, Email, Created At, Active badge, Roles, Actions.
2. System displays a text search field above the grid.
3. System displays filter controls for Active/Inactive status.
4. User optionally enters search text (username or email) and filters are applied in real time.
5. Grid updates to show matching users.
6. User can click on a row to view user details in a dismissible sidebar form (read-only or edit mode depending on permissions).

### Main Flow: Create User

7. User clicks the "New User" button above the grid.
8. System opens a sidebar form with fields: Username (required), Email (required), Password (required), Confirm Password (required), Active checkbox (default true), Roles (required, multi-select).
9. User fills in all required fields, selects one or more roles, and clicks "Save".
10. System validates the form (see Business Rules).
11. System checks for duplicate username and email.
12. System hashes the password and creates the user account with selected roles.
13. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Edit User

14. User clicks the "Edit" action on a user row (or clicks the user row to open in read-only, then clicks "Edit").
15. System opens the sidebar form pre-populated with the user's current data (password field is blank for security).
16. User modifies fields (username, email, active status, role assignments) and clicks "Save".
17. System validates the form.
18. System updates the user record in the database.
19. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Deactivate User

20. User clicks the "Deactivate" action on an active user row.
21. System displays a confirmation dialog: "Deactivate this user? They will not be able to log in."
22. User confirms the deactivation.
23. System sets the user's active flag to false and persists the change.
24. System closes the dialog, refreshes the grid, and displays success notification.

---

## Alternative Flows

### AF-1: Duplicate Username

**Branches from:** Main Flow step 10 (Create) or 17 (Edit)
**Condition:** Entered username already exists in the database (excluding the current user during edit)

1. System displays error message: "Username already exists. Please choose a different one."
2. User modifies the username and resubmits.
3. Returns to Main Flow step 10 or 17.

### AF-2: Duplicate Email

**Branches from:** Main Flow step 10 (Create) or 17 (Edit)
**Condition:** Entered email already exists in the database (excluding the current user during edit)

1. System displays error message: "Email already registered. Please use a different one."
2. User modifies the email and resubmits.
3. Returns to Main Flow step 10 or 17.

### AF-3: Invalid Email Format

**Branches from:** Main Flow step 10 (Create) or 17 (Edit)
**Condition:** Email field does not match standard email pattern

1. System displays error message: "Invalid email address."
2. User corrects the email and resubmits.
3. Returns to Main Flow step 10 or 17.

### AF-4: Missing Required Fields

**Branches from:** Main Flow step 10 (Create) or 17 (Edit)
**Condition:** One or more required fields are empty (username, email, password during create, or roles)

1. System displays field-level validation errors.
2. User fills in all required fields and resubmits.
3. Returns to Main Flow step 10 or 17.

### AF-5: Weak Password

**Branches from:** Main Flow step 10 (Create)
**Condition:** Password does not meet minimum strength requirements (e.g., < 8 characters)

1. System displays error message: "Password must be at least 8 characters."
2. User provides a stronger password and resubmits.
3. Returns to Main Flow step 10.

### AF-6: Password Mismatch

**Branches from:** Main Flow step 10 (Create)
**Condition:** Password and confirmation password do not match

1. System displays error message: "Passwords do not match."
2. User re-enters both password fields and resubmits.
3. Returns to Main Flow step 10.

### AF-7: No Roles Selected

**Branches from:** Main Flow step 10 (Create) or 17 (Edit)
**Condition:** User attempts to create/save a user without selecting any roles

1. System displays error message: "At least one role must be selected."
2. User selects one or more roles and resubmits.
3. Returns to Main Flow step 10 or 17.

### AF-8: Insufficient Permissions

**Branches from:** Main Flow step 7 (New User), 14 (Edit), or 20 (Deactivate)
**Condition:** User lacks the required permission (USER:CREATE, USER:UPDATE, USER:DELETE, or USER:ASSIGN)

1. System hides the "New User", "Edit", and "Deactivate" buttons/actions.
2. User can only view the user grid in read-only mode.
3. Use case ends.

### AF-9: Cannot Deactivate Own Account

**Branches from:** Main Flow step 22 (Deactivate)
**Condition:** User attempts to deactivate their own account

1. System displays error message: "You cannot deactivate your own account."
2. User cancels the action.
3. Use case ends.

### AF-10: Sidebar Form Dirty State

**Branches from:** Main Flow step 8 or 15 (Form opened)
**Condition:** User modifies form fields and attempts to close without saving

1. System displays a confirmation dialog: "You have unsaved changes. Discard them?"
2. User can choose to save, cancel, or discard.
3. Returns to Main Flow or use case ends based on choice.

### AF-11: Concurrent Edit Conflict

**Branches from:** Main Flow step 16 (Edit—database update)
**Condition:** Another admin edited the same user between the time the form was opened and the save was submitted

1. System displays error message: "User was updated by another administrator. Refresh the form and try again."
2. System reloads the form with the latest data.
3. User reviews and resubmits if desired.
4. Returns to Main Flow step 16.

---

## Postconditions

- **On success:** User is created, updated, or deactivated in the database; grid is refreshed; success notification is displayed.
- **On failure:** No changes are persisted; error message is displayed; user remains on the form or grid.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Username must be unique and not blank |
| BR-02 | Email must be unique, not blank, and in valid format |
| BR-03 | Password must be at least 8 characters (on create) |
| BR-04 | Password and confirmation must match (on create) |
| BR-05 | Passwords must be stored as bcrypt hashes, never in plain text |
| BR-06 | A user must have at least one active role assigned |
| BR-07 | Users are deactivated instead of deleted to preserve audit trails |
| BR-08 | An admin cannot deactivate their own account |
| BR-09 | Search filters by username and email (case-insensitive partial match) |
| BR-10 | Active/Inactive filter shows current status of all users |

---

## Tests

- [ ] Main Flow: View and search users covered
- [ ] Main Flow: Create user covered
- [ ] Main Flow: Edit user covered
- [ ] Main Flow: Deactivate user covered
- [ ] AF-1 (duplicate username) covered
- [ ] AF-2 (duplicate email) covered
- [ ] AF-3 (invalid email format) covered
- [ ] AF-4 (missing required fields) covered
- [ ] AF-5 (weak password) covered
- [ ] AF-6 (password mismatch) covered
- [ ] AF-7 (no roles selected) covered
- [ ] AF-8 (insufficient permissions) covered
- [ ] AF-9 (cannot deactivate own account) covered
- [ ] AF-10 (dirty state) covered
- [ ] AF-11 (concurrent edit) covered
- [ ] BR-01 through BR-10 covered

---

## UI Surface

- **User List Page:** Grid with search, filters, and action buttons.
- **Sidebar Form:** Create/edit/view form displayed in a dismissible right-side panel.
- **Role Assignment:** Multi-select dropdown or checkbox list for role selection.
- **Confirmation Dialogs:** Deactivate confirmation and dirty-state warning.
- **Notifications:** Success and error messages displayed at top of view.
- **Active/Inactive Badge:** Visual indicator (e.g., green "Active", gray "Inactive") using LitRenderer.

| Page | Access |
|------|--------|
| User List | Authenticated (USER:READ) |
| Create/Edit/View User | Authenticated (USER:CREATE/UPDATE/ASSIGN) |
