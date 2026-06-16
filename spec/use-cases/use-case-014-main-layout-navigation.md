# UC-014: Main Layout Navigation

> Authenticated users navigate the inventory application through a polished Aura-style application shell with permission-aware drawer navigation and a proper home view.

---

**Goal:** As an authenticated inventory user, I want a polished Aura-style main layout with drawer navigation and a proper home view so that I can move through the inventory system quickly and consistently.

**Status:** Implemented
**Date:** 2026-06-16

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Authenticated inventory user (Inventory Manager, Warehouse Operator, Inventory Viewer, or System Administrator)
- **Secondary actors:** None

---

## Preconditions

- User is authenticated.
- Protected application views exist for home, products, categories, suppliers, stock movements, users, roles, and permissions.
- Role and permission data exists for protected resources and READ actions.
- Public login and signup routes remain available without the authenticated application layout.

---

## Trigger

User navigates to the home route or any protected application route.

---

## Main Flow

1. User opens the home route or a protected application route.
2. System renders the protected view inside `MainLayout`, implemented as `public class MainLayout extends AppLayout`.
3. System applies the main layout to protected views through route layout configuration.
4. System displays an Aura-style drawer with brand area, sectioned navigation groups, icons, active route highlighting, and current user context.
5. System checks the user's permissions for each protected resource before displaying the matching navigation item.
6. System displays only navigation items for resources where the user has the required READ permission.
7. User selects a visible navigation item in the drawer.
8. System navigates to the selected view and updates the active navigation state.
9. User navigates to the home route.
10. System displays a refactored home view with a polished pending/dashboard placeholder instead of the current raw list of links.

---

## Alternative Flows

### AF-1: Unauthenticated User

**Branches from:** Main Flow step 1
**Condition:** User is not authenticated.

1. System redirects the user to the login view.
2. Use case ends.

### AF-2: User Lacks View Permission

**Branches from:** Main Flow step 1
**Condition:** Authenticated user directly enters a protected route URL but lacks the required permission for that view.

1. System blocks access to the requested view.
2. System displays a forbidden access view or message explaining that the user does not have permission to view the page.
3. System keeps the user authenticated and preserves the main application shell when appropriate.
4. Use case ends.

### AF-3: Navigation Item Hidden By Permission

**Branches from:** Main Flow step 5
**Condition:** User lacks the READ permission for a protected resource represented in the drawer.

1. System omits the restricted navigation item from the drawer.
2. User continues navigating with the remaining permitted items.
3. Returns to Main Flow step 7.

### AF-4: No Business Navigation Items Available

**Branches from:** Main Flow step 6
**Condition:** User is authenticated but has no READ permissions for business or administration resources.

1. System displays the home navigation item and user context.
2. System displays an empty or limited-navigation state in the drawer.
3. System displays the home view with a message that no modules are currently available.
4. Use case ends.

### AF-5: Mobile Or Narrow Viewport

**Branches from:** Main Flow step 4
**Condition:** The viewport is too narrow for a persistent desktop drawer.

1. System presents the drawer in a responsive collapsed or overlay mode.
2. User opens the drawer with the menu control.
3. System preserves the same sectioning, active state, and permission filtering as desktop.
4. Returns to Main Flow step 7.

---

## Postconditions

- **On success:** Protected views render inside the Aura-style main layout; drawer navigation reflects the user's permissions; selected routes update active navigation state; home view is a polished pending/dashboard placeholder.
- **On failure:** Unauthenticated users are redirected to login; authenticated users without access see a forbidden access surface; restricted navigation items are not exposed.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | `MainLayout` must be implemented as `public class MainLayout extends AppLayout`. |
| BR-02 | Protected Vaadin Flow views must be configured to render inside `MainLayout`; public login and signup views must not use the main layout. |
| BR-03 | Drawer navigation must follow the provided Vaadin Aura example direction: left drawer, brand area, grouped navigation, icons, active item styling, subtle borders, and polished spacing. |
| BR-04 | Navigation items for Products, Categories, Suppliers, Stock Movements, Users, Roles, and Permissions must be visible only when the authenticated user has the matching resource READ permission. |
| BR-05 | Direct URL access must be authorized separately from drawer visibility; hiding a drawer item is not sufficient security. |
| BR-06 | Forbidden access must have a user-facing view or message for authenticated users who try to open a route without permission. |
| BR-07 | The home view must no longer be a raw list of anchors; it should become a polished pending/dashboard-style landing page. |
| BR-08 | Styling must use Aura-compatible theme variables and must not mix in Lumo-only variables. |
| BR-09 | The layout must remain usable on narrow screens through a responsive drawer behavior. |

---

## Tests

- [x] Main Flow covered (steps 1-10)
- [x] AF-1, AF-2, AF-3, AF-4, and AF-5 covered
- [x] BR-01 through BR-09 covered

---

## UI Surface

- **Main Application Layout:** Aura-style app shell with left drawer navigation, brand/header area, grouped navigation items, active route state, and user context.
- **Permission-Aware Drawer:** Navigation items are filtered by the authenticated user's resource READ permissions.
- **Forbidden Access Surface:** Authenticated users without route permission see a clear forbidden message instead of the target view.
- **Home View:** Refactored landing page that can show pending/dashboard placeholder content until real dashboard metrics are implemented.
- **Responsive Drawer:** Drawer remains usable on desktop and narrow screens.

| Page | Access |
|------|--------|
| Home | Authenticated |
| Products | Authenticated (PRODUCT:READ) |
| Categories | Authenticated (CATEGORY:READ) |
| Suppliers | Authenticated (SUPPLIER:READ) |
| Stock Movements | Authenticated (STOCK_MOVEMENT:READ) |
| Users | Authenticated (USER:READ) |
| Roles | Authenticated (ROLE:READ) |
| Permissions | Authenticated (PERMISSION:READ) |
| Forbidden Access | Authenticated |
