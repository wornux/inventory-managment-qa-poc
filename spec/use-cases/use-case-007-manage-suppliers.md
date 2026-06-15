# UC-007: Manage Suppliers

> Inventory managers view, search, create, edit, and deactivate supplier records.

---

**Goal:** As an inventory manager, I want to manage supplier information so that products can be sourced from the correct suppliers and contact information is maintained.

**Status:** Pending
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Inventory Manager (or higher privilege user)
- **Secondary actors:** None

---

## Preconditions

- User is logged in and has SUPPLIER:READ permission
- User has SUPPLIER:CREATE, SUPPLIER:UPDATE, SUPPLIER:DELETE permissions for full management
- Suppliers table exists in the database

---

## Trigger

User navigates to the Suppliers view from the inventory management menu.

---

## Main Flow

### Main Flow: View and Search Suppliers

1. System displays a grid containing all suppliers with columns: Name, Contact Name, Email, Phone, Active badge, Product Count, Actions.
2. System displays a text search field above the grid.
3. System displays filter controls for Active/Inactive status.
4. User optionally enters search text (supplier name or contact name) and filters are applied in real time.
5. Grid updates to show matching suppliers.
6. User can click on a row to view supplier details in a dismissible sidebar form (read-only or edit mode).

### Main Flow: Create Supplier

7. User clicks the "New Supplier" button above the grid.
8. System opens a sidebar form with fields: Name (required), Contact Name (optional), Email (optional), Phone (optional), Active checkbox (default true).
9. User fills in the supplier name, optionally enters contact information, and clicks "Save".
10. System validates the form (see Business Rules).
11. System creates the supplier record in the database.
12. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Edit Supplier

13. User clicks the "Edit" action on a supplier row.
14. System opens the sidebar form pre-populated with the supplier's current data.
15. User modifies one or more fields and clicks "Save".
16. System validates the form.
17. System updates the supplier record in the database.
18. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Deactivate Supplier

19. User clicks the "Deactivate" action on an active supplier row.
20. System displays a confirmation dialog: "Deactivate this supplier? Products sourced from this supplier will still exist but this supplier will not be available for new product assignments."
21. User confirms the deactivation.
22. System sets the supplier's active flag to false and persists the change.
23. System closes the dialog, refreshes the grid, and displays success notification.

---

## Alternative Flows

### AF-1: Missing Required Fields

**Branches from:** Main Flow step 10 (Create) or 16 (Edit)
**Condition:** Supplier name is empty

1. System displays field-level validation error.
2. User fills in the required field and resubmits.
3. Returns to Main Flow step 10 or 16.

### AF-2: Invalid Email Format

**Branches from:** Main Flow step 10 (Create) or 16 (Edit)
**Condition:** Email field is provided but does not match standard email pattern

1. System displays error message: "Invalid email address."
2. User corrects the email and resubmits.
3. Returns to Main Flow step 10 or 16.

### AF-3: Invalid Phone Format

**Branches from:** Main Flow step 10 (Create) or 16 (Edit)
**Condition:** Phone field is provided but contains invalid characters

1. System displays error message: "Invalid phone number format."
2. User corrects the phone and resubmits.
3. Returns to Main Flow step 10 or 16.

### AF-4: Insufficient Permissions

**Branches from:** Main Flow step 7 (New Supplier), 13 (Edit), or 19 (Deactivate)
**Condition:** User lacks the required permission (SUPPLIER:CREATE, SUPPLIER:UPDATE, or SUPPLIER:DELETE)

1. System hides the "New Supplier", "Edit", and "Deactivate" buttons/actions.
2. User can only view the supplier grid in read-only mode.
3. Use case ends.

### AF-5: Supplier Has Products

**Branches from:** Main Flow step 19 (Deactivate)
**Condition:** The supplier has products assigned to it

1. System displays warning dialog: "This supplier has [N] products. Deactivating the supplier will not affect existing products, but new products cannot be assigned to this supplier."
2. User can confirm or cancel the deactivation.
3. If confirmed, use case continues to Main Flow step 22.
4. If cancelled, use case ends.

### AF-6: Sidebar Form Dirty State

**Branches from:** Main Flow step 8 or 14 (Form opened)
**Condition:** User modifies form fields and attempts to close without saving

1. System displays a confirmation dialog: "You have unsaved changes. Discard them?"
2. User can choose to save, cancel, or discard.
3. Returns to Main Flow or use case ends based on choice.

### AF-7: Concurrent Edit Conflict

**Branches from:** Main Flow step 15 (Edit—database update)
**Condition:** Another user edited the same supplier between the time the form was opened and the save was submitted

1. System displays error message: "Supplier was updated by another user. Refresh the form and try again."
2. System reloads the form with the latest data.
3. User reviews and resubmits if desired.
4. Returns to Main Flow step 15.

---

## Postconditions

- **On success:** Supplier is created, updated, or deactivated in the database; grid is refreshed; success notification is displayed.
- **On failure:** No changes are persisted; error message is displayed; user remains on the form or grid.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Supplier name is required and must not be blank |
| BR-02 | Contact name, email, and phone are optional |
| BR-03 | If provided, email must be in valid format |
| BR-04 | If provided, phone must contain only digits, spaces, dashes, plus, and parentheses |
| BR-05 | Suppliers are deactivated instead of deleted to preserve product sourcing history |
| BR-06 | Deactivating a supplier does not affect existing products sourced from it |
| BR-07 | New products cannot be assigned to inactive suppliers |
| BR-08 | Search filters by supplier name and contact name (case-insensitive partial match) |
| BR-09 | Grid displays count of products sourced from each supplier |

---

## Tests

- [ ] Main Flow: View and search suppliers covered
- [ ] Main Flow: Create supplier covered
- [ ] Main Flow: Edit supplier covered
- [ ] Main Flow: Deactivate supplier covered
- [ ] AF-1 (missing required fields) covered
- [ ] AF-2 (invalid email) covered
- [ ] AF-3 (invalid phone) covered
- [ ] AF-4 (insufficient permissions) covered
- [ ] AF-5 (supplier has products) covered
- [ ] AF-6 (dirty state) covered
- [ ] AF-7 (concurrent edit) covered
- [ ] BR-01 through BR-09 covered

---

## UI Surface

- **Supplier List Page:** Grid with search, filters, and action buttons.
- **Sidebar Form:** Create/edit/view form displayed in a dismissible right-side panel.
- **Contact Information:** Display contact details in LitRenderer for email and phone columns.
- **Confirmation Dialogs:** Deactivate confirmation and dirty-state warning.
- **Notifications:** Success and error messages displayed at top of view.
- **Active/Inactive Badge:** Visual indicator using LitRenderer.
- **Product Count:** Display number of products sourced from each supplier.

| Page | Access |
|------|--------|
| Supplier List | Authenticated (SUPPLIER:READ) |
| Create/Edit/View Supplier | Authenticated (SUPPLIER:CREATE/UPDATE) |
