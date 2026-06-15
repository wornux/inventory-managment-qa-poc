# UC-003: Manage Products

> Inventory managers view, search, filter, create, edit, and delete products in the catalog.

---

**Goal:** As an inventory manager, I want to manage the product catalog (view, search, create, edit, delete) so that the inventory system remains accurate and current.

**Status:** Pending
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Inventory Manager (or higher privilege user)
- **Secondary actors:** None

---

## Preconditions

- User is logged in and has PRODUCT:READ permission
- User has PRODUCT:CREATE permission to create, PRODUCT:UPDATE to edit, PRODUCT:DELETE to delete
- Products table exists in the database

---

## Trigger

User navigates to the Products view from the main navigation menu.

---

## Main Flow

### Main Flow: View and Search Products

1. System displays a grid containing all active products with columns: SKU, Name, Category, Supplier, Unit Price, Quantity on Hand, Minimum Stock, Stock Status, Active badge, Actions.
2. System displays a text search field above the grid.
3. System displays filter controls for Category, Supplier, Active/Inactive status, and Low-Stock filter.
4. User optionally enters search text (SKU or name) and filters are applied in real time.
5. Grid updates to show matching products sorted by SKU (default).
6. User can click on a row to view product details in a dismissible sidebar form (read-only).

### Main Flow: Create Product

7. User clicks the "New Product" button above the grid.
8. System opens a sidebar form with empty fields: SKU, Name, Description, Unit Price, Quantity on Hand, Minimum Stock, Category (required), Supplier (optional), Active checkbox.
9. User fills in all required fields and clicks "Save".
10. System validates the form (see Business Rules).
11. System creates the product record in the database.
12. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Edit Product

13. User clicks the "Edit" action on a product row.
14. System opens the sidebar form pre-populated with the product's current data.
15. User modifies one or more fields and clicks "Save".
16. System validates the form.
17. System updates the product record in the database.
18. System closes the sidebar form, refreshes the grid, and displays success notification.

### Main Flow: Delete Product

19. User clicks the "Delete" action on a product row.
20. System displays a confirmation dialog: "Are you sure you want to delete this product?"
21. User confirms the deletion.
22. System deletes the product record (or deactivates if it has stock movement history).
23. System closes the dialog, refreshes the grid, and displays success notification.

---

## Alternative Flows

### AF-1: Duplicate SKU

**Branches from:** Main Flow step 10 (Create) or 16 (Edit)
**Condition:** Entered SKU already exists in the database (excluding the current product during edit)

1. System displays error message: "SKU already exists. Please choose a different one."
2. User modifies the SKU and resubmits.
3. Returns to Main Flow step 10 or 16.

### AF-2: Missing Required Fields

**Branches from:** Main Flow step 10 (Create) or 16 (Edit)
**Condition:** One or more required fields are empty

1. System displays field-level validation errors.
2. User fills in all required fields and resubmits.
3. Returns to Main Flow step 10 or 16.

### AF-3: Invalid Unit Price

**Branches from:** Main Flow step 10 (Create) or 16 (Edit)
**Condition:** Unit Price is negative or not a valid decimal

1. System displays error message: "Unit price must be a positive number."
2. User corrects the value and resubmits.
3. Returns to Main Flow step 10 or 16.

### AF-4: Insufficient Permissions

**Branches from:** Main Flow step 7 (Create), 13 (Edit), or 19 (Delete)
**Condition:** User lacks the required permission (PRODUCT:CREATE, PRODUCT:UPDATE, or PRODUCT:DELETE)

1. System hides the "New Product", "Edit", and "Delete" buttons/actions.
2. User can only view the product grid in read-only mode.
3. Use case ends.

### AF-5: Product Has Stock Movements

**Branches from:** Main Flow step 22 (Delete)
**Condition:** The product has recorded stock movements

1. System deactivates the product instead of deleting it (sets active=false).
2. System closes the dialog, refreshes the grid, and displays success notification.
3. Use case ends.

### AF-6: Category or Supplier Does Not Exist

**Branches from:** Main Flow step 10 (Create) or 16 (Edit)
**Condition:** Selected Category or Supplier has been deleted or is unavailable

1. System displays error message: "Selected category/supplier is no longer available. Please refresh and try again."
2. User navigates back and tries again with available options.
3. Returns to Main Flow step 8 or 14.

### AF-7: Sidebar Form Dirty State

**Branches from:** Main Flow step 8 or 14 (Form opened)
**Condition:** User modifies form fields and attempts to close without saving

1. System displays a confirmation dialog: "You have unsaved changes. Discard them?"
2. User can choose to save, cancel, or discard.
3. Returns to Main Flow or use case ends based on choice.

### AF-8: Concurrent Edit Conflict

**Branches from:** Main Flow step 15 (Edit—database update)
**Condition:** Another user edited the same product between the time the form was opened and the save was submitted

1. System displays error message: "Product was updated by another user. Refresh the form and try again."
2. System reloads the form with the latest data.
3. User reviews and resubmits if desired.
4. Returns to Main Flow step 15.

---

## Postconditions

- **On success:** Product is created, updated, or deleted in the database; grid is refreshed; success notification is displayed.
- **On failure:** No changes are persisted; error message is displayed; user remains on the form or grid.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | SKU must be unique and not blank |
| BR-02 | Product name must be unique (within active products) and not blank |
| BR-03 | Unit price must be >= 0 |
| BR-04 | Quantity on hand must be >= 0 |
| BR-05 | Minimum stock must be >= 0 |
| BR-06 | Category is required; Supplier is optional |
| BR-07 | Stock status is LOW_STOCK if quantity_on_hand <= minimum_stock, else OK |
| BR-08 | Products with historical stock movements should be deactivated instead of deleted |
| BR-09 | Search filters by SKU and Name (case-insensitive partial match) |
| BR-10 | Low-Stock filter shows products where quantity_on_hand <= minimum_stock |

---

## Tests

- [ ] Main Flow: View, search, and filter products covered
- [ ] Main Flow: Create product covered
- [ ] Main Flow: Edit product covered
- [ ] Main Flow: Delete product covered
- [ ] AF-1 (duplicate SKU) covered
- [ ] AF-2 (missing required fields) covered
- [ ] AF-3 (invalid unit price) covered
- [ ] AF-4 (insufficient permissions) covered
- [ ] AF-5 (product has stock movements) covered
- [ ] AF-6 (category/supplier unavailable) covered
- [ ] AF-7 (dirty state) covered
- [ ] AF-8 (concurrent edit) covered
- [ ] BR-01 through BR-10 covered

---

## UI Surface

- **Product List Page:** Grid with search, filters, and action buttons.
- **Sidebar Form:** Create/edit/view form displayed in a dismissible right-side panel.
- **Confirmation Dialogs:** Delete confirmation and dirty-state warning.
- **Notifications:** Success and error messages displayed at top of view.
- **Stock Status Badge:** Visual indicator (e.g., green "OK" or orange "LOW STOCK") using LitRenderer.
- **Active/Inactive Badge:** Visual indicator using LitRenderer.

| Page | Access |
|------|--------|
| Product List | Authenticated (PRODUCT:READ) |
| Create/Edit/View Product | Authenticated (PRODUCT:CREATE/UPDATE) |
