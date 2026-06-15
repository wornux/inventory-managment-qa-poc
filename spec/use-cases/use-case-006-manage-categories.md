# UC-006: Manage Categories

> Inventory managers view, search, create, edit, and deactivate product categories.

---

**Goal:** As an inventory manager, I want to manage product categories so that products can be properly organized and filtered.

**Status:** Pending
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Inventory Manager (or higher privilege user)
- **Secondary actors:** None

---

## Preconditions

- User is logged in and has CATEGORY:READ permission
- User has CATEGORY:CREATE, CATEGORY:UPDATE, CATEGORY:DELETE permissions for full management
- Categories table exists in the database

---

## Trigger

User navigates to the Categories view from the inventory management menu.

---

## Main Flow

### Main Flow: View and Search Categories

1. System displays a grid containing all categories with columns: Name, Description, Active badge, Product Count, Actions.
2. System displays a text search field above the grid.
3. System displays filter controls for Active/Inactive status.
4. User optionally enters search text (category name) and filters are applied in real time.
5. Grid updates to show matching categories.
6. User can click on a row to view category details in a dismissible sidebar form (read-only or edit mode).

### Main Flow: Create Category

7. User clicks the "New Category" button above the grid.
8. System opens a sidebar form with fields: Name (required), Description (optional), Active checkbox (default true).
9. User fills in the name, optionally enters a description, and clicks "Save".
10. System validates the form (see Business Rules).
11. System checks for duplicate category name.
12. System creates the category record in the database.
13. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Edit Category

14. User clicks the "Edit" action on a category row.
15. System opens the sidebar form pre-populated with the category's current data.
16. User modifies one or more fields and clicks "Save".
17. System validates the form.
18. System updates the category record in the database.
19. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Deactivate Category

20. User clicks the "Deactivate" action on an active category row.
21. System displays a confirmation dialog: "Deactivate this category? Products in this category will still exist but will be hidden from new assignments."
22. User confirms the deactivation.
23. System sets the category's active flag to false and persists the change.
24. System closes the dialog, refreshes the grid, and displays success notification.

---

## Alternative Flows

### AF-1: Duplicate Category Name

**Branches from:** Main Flow step 10 (Create) or 17 (Edit)
**Condition:** Entered category name already exists in the database (excluding the current category during edit)

1. System displays error message: "Category name already exists. Please choose a different one."
2. User modifies the name and resubmits.
3. Returns to Main Flow step 10 or 17.

### AF-2: Missing Required Fields

**Branches from:** Main Flow step 10 (Create) or 17 (Edit)
**Condition:** Category name is empty

1. System displays field-level validation error.
2. User fills in the required field and resubmits.
3. Returns to Main Flow step 10 or 17.

### AF-3: Insufficient Permissions

**Branches from:** Main Flow step 7 (New Category), 14 (Edit), or 20 (Deactivate)
**Condition:** User lacks the required permission (CATEGORY:CREATE, CATEGORY:UPDATE, or CATEGORY:DELETE)

1. System hides the "New Category", "Edit", and "Deactivate" buttons/actions.
2. User can only view the category grid in read-only mode.
3. Use case ends.

### AF-4: Category Has Products

**Branches from:** Main Flow step 20 (Deactivate)
**Condition:** The category has active products assigned to it

1. System displays warning dialog: "This category has [N] products. Deactivating the category will not affect existing products, but new products cannot be assigned to it."
2. User can confirm or cancel the deactivation.
3. If confirmed, use case continues to Main Flow step 23.
4. If cancelled, use case ends.

### AF-5: Sidebar Form Dirty State

**Branches from:** Main Flow step 8 or 15 (Form opened)
**Condition:** User modifies form fields and attempts to close without saving

1. System displays a confirmation dialog: "You have unsaved changes. Discard them?"
2. User can choose to save, cancel, or discard.
3. Returns to Main Flow or use case ends based on choice.

### AF-6: Concurrent Edit Conflict

**Branches from:** Main Flow step 16 (Edit—database update)
**Condition:** Another user edited the same category between the time the form was opened and the save was submitted

1. System displays error message: "Category was updated by another user. Refresh the form and try again."
2. System reloads the form with the latest data.
3. User reviews and resubmits if desired.
4. Returns to Main Flow step 16.

---

## Postconditions

- **On success:** Category is created, updated, or deactivated in the database; grid is refreshed; success notification is displayed.
- **On failure:** No changes are persisted; error message is displayed; user remains on the form or grid.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Category name must be unique and not blank |
| BR-02 | Description is optional |
| BR-03 | Categories are deactivated instead of deleted to preserve product history |
| BR-04 | Deactivating a category does not affect existing products assigned to it |
| BR-05 | New products cannot be assigned to inactive categories |
| BR-06 | Search filters by category name (case-insensitive partial match) |
| BR-07 | Grid displays count of products assigned to each category |

---

## Tests

- [ ] Main Flow: View and search categories covered
- [ ] Main Flow: Create category covered
- [ ] Main Flow: Edit category covered
- [ ] Main Flow: Deactivate category covered
- [ ] AF-1 (duplicate name) covered
- [ ] AF-2 (missing required fields) covered
- [ ] AF-3 (insufficient permissions) covered
- [ ] AF-4 (category has products) covered
- [ ] AF-5 (dirty state) covered
- [ ] AF-6 (concurrent edit) covered
- [ ] BR-01 through BR-07 covered

---

## UI Surface

- **Category List Page:** Grid with search, filters, and action buttons.
- **Sidebar Form:** Create/edit/view form displayed in a dismissible right-side panel.
- **Confirmation Dialogs:** Deactivate confirmation and dirty-state warning.
- **Notifications:** Success and error messages displayed at top of view.
- **Active/Inactive Badge:** Visual indicator using LitRenderer.
- **Product Count:** Display number of products assigned to each category.

| Page | Access |
|------|--------|
| Category List | Authenticated (CATEGORY:READ) |
| Create/Edit/View Category | Authenticated (CATEGORY:CREATE/UPDATE) |
