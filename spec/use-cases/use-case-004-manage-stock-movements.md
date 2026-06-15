# UC-004: Manage Stock Movements

> Warehouse operators and managers view, filter, create, and trace stock movements in an append-only ledger.

---

**Goal:** As a warehouse operator or manager, I want to record and view stock movements so that inventory levels are accurate and all changes are traceable.

**Status:** Implemented
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Warehouse Operator or Inventory Manager
- **Secondary actors:** None

---

## Preconditions

- User is logged in and has STOCK_MOVEMENT:READ permission
- User has STOCK_MOVEMENT:CREATE permission to record new movements
- Products exist in the database
- User account exists for movement attribution

---

## Trigger

User navigates to the Stock Movements view from the main navigation menu or clicks "Record Movement" from a product detail page.

---

## Main Flow

### Main Flow: View and Filter Stock Movements

1. System displays a ledger grid with all stock movements in reverse chronological order (newest first).
2. Grid columns: Created At, Product (name + SKU), Movement Type, Quantity Delta, User, Reason.
3. System displays filter controls for Date Range, Product, Movement Type, and User.
4. User applies filters as needed.
5. Grid updates to show matching movements.
6. User can view movement details by clicking a row to open a read-only sidebar panel.

### Main Flow: Create Stock Movement

7. User clicks the "Record Movement" button.
8. System opens a sidebar form with fields: Product (required), Movement Type (required), Quantity Delta (auto-calculated if movement type is selected), Reason (required for adjustment, damage, loss; read-only for purchase/sale).
9. User selects a product from a dropdown or search field.
10. User selects a movement type (PURCHASE, SALE, RETURN_IN, RETURN_OUT, ADJUSTMENT_IN, ADJUSTMENT_OUT, INITIAL_STOCK, DAMAGED, LOST).
11. System displays the quantity delta field pre-configured for the selected type (positive or negative).
12. If movement type requires a reason (ADJUSTMENT_IN, ADJUSTMENT_OUT, DAMAGED, LOST), user enters a reason.
13. User clicks "Save".
14. System validates the form (see Business Rules).
15. System validates that the product exists and is active.
16. System checks that the resulting stock will not be negative (except for movements that allow it).
17. System atomically updates product quantity_on_hand and inserts the stock movement record in a single transaction.
18. System closes the sidebar form, refreshes the grid, and displays success notification.

---

## Alternative Flows

### AF-1: Missing Required Fields

**Branches from:** Main Flow step 14
**Condition:** Product, Movement Type, or required Reason field is empty

1. System displays field-level validation errors.
2. User fills in all required fields and resubmits.
3. Returns to Main Flow step 14.

### AF-2: Invalid Quantity Delta

**Branches from:** Main Flow step 14
**Condition:** Quantity Delta is zero or negative when positive is required (or vice versa)

1. System displays error message: "Quantity delta must be positive for this movement type" or "...negative..." as appropriate.
2. User corrects the value and resubmits.
3. Returns to Main Flow step 14.

### AF-3: Insufficient Stock

**Branches from:** Main Flow step 16
**Condition:** Outbound movement (SALE, RETURN_OUT, etc.) would result in negative stock

1. System displays error message: "Insufficient stock. Current stock: [N], requested: [M]."
2. User either adjusts the quantity or cancels the movement.
3. Returns to Main Flow step 14 or use case ends.

### AF-4: Product Inactive or Deleted

**Branches from:** Main Flow step 15
**Condition:** Selected product is inactive or has been deleted

1. System displays error message: "Product is no longer available."
2. User selects a different product and resubmits.
3. Returns to Main Flow step 9.

### AF-5: Insufficient Permissions

**Branches from:** Main Flow step 7 (Record Movement)
**Condition:** User lacks STOCK_MOVEMENT:CREATE permission

1. System hides the "Record Movement" button.
2. User can only view the ledger in read-only mode.
3. Use case ends.

### AF-6: Database Transaction Failure

**Branches from:** Main Flow step 17 (Database save)
**Condition:** Concurrent updates, database error, or constraint violation

1. System displays error message: "Failed to save movement. Please try again."
2. User retries or navigates away.
3. Use case ends.

### AF-7: Sidebar Form Dirty State

**Branches from:** Main Flow step 8 (Form opened)
**Condition:** User modifies form fields and attempts to close without saving

1. System displays a confirmation dialog: "You have unsaved changes. Discard them?"
2. User can choose to save, cancel, or discard.
3. Returns to Main Flow or use case ends based on choice.

### AF-8: No Products Available

**Branches from:** Main Flow step 9 (Select product)
**Condition:** No products exist in the system or user has no permission to view them

1. System displays message: "No products available."
2. User must create products first.
3. Use case ends; user navigates to Product management.

---

## Postconditions

- **On success:** Stock movement is recorded, product quantity is updated atomically, ledger is refreshed, success notification is displayed.
- **On failure:** No movement is recorded, product quantity is unchanged, error message is displayed.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Stock movements are append-only; no edits or deletes allowed from the UI |
| BR-02 | Movement type must be one of the approved types (PURCHASE, SALE, RETURN_IN, RETURN_OUT, ADJUSTMENT_IN, ADJUSTMENT_OUT, INITIAL_STOCK, DAMAGED, LOST) |
| BR-03 | Quantity delta must not be zero |
| BR-04 | Positive inbound types (PURCHASE, RETURN_IN, ADJUSTMENT_IN, INITIAL_STOCK) must have positive quantity delta |
| BR-05 | Negative outbound types (SALE, RETURN_OUT, ADJUSTMENT_OUT, DAMAGED, LOST) must have negative quantity delta |
| BR-06 | Reason is required for ADJUSTMENT_IN, ADJUSTMENT_OUT, DAMAGED, and LOST movements |
| BR-07 | Reason is not required for PURCHASE, SALE, RETURN_IN, RETURN_OUT, or INITIAL_STOCK movements |
| BR-08 | Outbound movements cannot reduce product stock below zero |
| BR-09 | Stock update and movement insert must occur atomically in the same transaction |
| BR-10 | Movement records product state at the time of creation (timestamps, user, quantity delta) |
| BR-11 | Correction of erroneous movements is done via compensating movements, not by editing history |

---

## Tests

- [x] Main Flow: View and filter movements covered
- [x] Main Flow: Create movement covered
- [x] AF-1 (missing required fields) covered
- [x] AF-2 (invalid quantity delta) covered
- [x] AF-3 (insufficient stock) covered
- [x] AF-4 (product inactive) covered
- [x] AF-5 (insufficient permissions) covered
- [x] AF-6 (database error) covered
- [x] AF-7 (dirty state) covered
- [x] AF-8 (no products) covered
- [x] BR-01 through BR-11 covered

---

## UI Surface

- **Stock Movement Ledger Page:** Grid displaying movements in reverse chronological order with filters.
- **Sidebar Form:** Create movement form with product selection, type, quantity, and reason fields.
- **Read-Only Detail Panel:** Display movement record in sidebar without edit option.
- **Notifications:** Success and error messages displayed at top of view.
- **Movement Type Badge:** Visual indicator (e.g., PURCHASE in green, SALE in blue) using LitRenderer.
- **Quantity Display:** Positive values shown with "+", negative with "−" using LitRenderer.

| Page | Access |
|------|--------|
| Stock Movement Ledger | Authenticated (STOCK_MOVEMENT:READ) |
| Create Movement | Authenticated (STOCK_MOVEMENT:CREATE) |
