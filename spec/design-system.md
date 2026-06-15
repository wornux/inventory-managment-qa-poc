# Design System

> Theme, component usage, and visual standards. Reference this when building or reviewing UI.

---

## 1. Theme

- **Base theme:** Vaadin Aura (modern, accessible design system)
- **Custom CSS:** `src/main/resources/META-INF/resources/styles.css`

**Aura and Lumo are two different, incompatible design systems.** This project uses **Aura**. Do not use `--lumo-*` CSS variables — they belong to the Lumo theme and must not be mixed with Aura. Use `--aura-*` variables for Aura-specific properties (typography, shadows) and `--vaadin-*` variables for base properties shared across all themes (spacing, radius, colors).

**Always use Aura theme variables instead of hard-coded values** (e.g., `--aura-font-size-xs` through `--aura-font-size-xl` for font sizes). Do not use hardcoded `px`, `rem`, or `em` values when an Aura variable exists. This ensures consistency with the Vaadin Aura theme and allows global adjustments through theme customization.

---

## 2. Color Palette

Aura computes all color variations automatically from a small set of base properties. Override these instead of hard-coding hex values.

| Token | Default | Usage |
|-------|---------|-------|
| `--aura-accent-color-light` | Blue | Primary actions, focus rings, selection highlights (light mode) |
| `--aura-accent-color-dark` | Blue | Primary actions, focus rings, selection highlights (dark mode) |
| `--aura-neutral` / `-light` / `-dark` | Dark gray / off-white | Text, borders, default UI chrome |
| `--aura-red` | Red | Error states, destructive actions |
| `--aura-orange` | Orange | Warnings |
| `--aura-green` | Green | Success states, confirmations |
| `--aura-blue` | Blue | Informational, links |
| `--aura-yellow` | Yellow | Caution, highlights |
| `--aura-purple` | Purple | Decorative accents |

Derived read-only tokens (do not override directly):
- `--aura-accent-contrast-color` — high-contrast text on accent backgrounds
- `--aura-accent-text-color` — accent-derived text color with good contrast
- `--aura-accent-border-color` — border tinted with accent color
- `--aura-accent-surface` — surface tinted with accent color
- `--aura-red-text`, `--aura-green-text`, etc. — palette text variants with better contrast

Base style tokens (shared across all themes):
- `--vaadin-text-color` — main text color
- `--vaadin-text-color-secondary` — secondary/muted text
- `--vaadin-text-color-disabled` — disabled state text
- `--vaadin-border-color` — prominent borders (3:1 contrast)
- `--vaadin-border-color-secondary` — subtle, non-essential borders
- `--vaadin-background-color` — base content background
- `--vaadin-background-container` — buttons, toolbars, highlighted areas
- `--vaadin-background-container-strong` — more prominent container background

Use accent class names (e.g. `.aura-accent-purple`) on `<html>` or individual components to swap accent color contextually.

---

## 3. Typography

Aura uses the **Instrument Sans** web font by default (`--aura-font-family-instrument-sans`), falling back to the system font stack.

| Token | Purpose |
|-------|---------| 
| `--aura-font-family` | App-wide font family (set on `<body>`) |
| `--aura-base-font-size` | Base size (unitless number, represents M size in px) |
| `--aura-font-size-xs` through `-xl` | Computed font sizes (rem, rounded to nearest px) |
| `--aura-base-line-height` | Base line height (unitless, relative to font size) |
| `--aura-line-height-xs` through `-xl` | Computed line heights (rem, rounded to nearest 2px) |
| `--aura-font-weight-regular` | Normal body text |
| `--aura-font-weight-medium` | Emphasis, subheadings |
| `--aura-font-weight-semibold` | Headings, strong emphasis |
| `--aura-font-smoothing` | Set to `auto` to disable grayscale anti-aliasing |

Use Aura font-size tokens (`--aura-font-size-s`, etc.) instead of hard-coded `px`/`rem` values.

### Text Hierarchy

- **Display / Page Title:** `--aura-font-size-xl` with `--aura-font-weight-semibold`
- **Section Heading:** `--aura-font-size-l` with `--aura-font-weight-semibold`
- **Subheading / Form Label:** `--aura-font-size-m` with `--aura-font-weight-medium`
- **Body Text:** `--aura-font-size-m` or `--aura-font-size-s` with `--aura-font-weight-regular`
- **Secondary / Helper Text:** `--aura-font-size-s` with `--vaadin-text-color-secondary`
- **Caption / Metadata:** `--aura-font-size-xs` with `--vaadin-text-color-secondary`

---

## 4. Spacing & Layout

Aura computes gap and padding from `--aura-base-size` (unitless, range 12–24). Use the resulting base style tokens:

| Token | Purpose |
|-------|---------| 
| `--vaadin-gap-xs` through `-xl` | Space between elements in flex/grid layouts |
| `--vaadin-padding-xs` through `-xl` | Internal padding for containers and content areas |
| `--vaadin-padding-inline-container` | Horizontal padding for single-line containers (buttons, inputs) |
| `--vaadin-padding-block-container` | Vertical padding for single-line containers |

**Border radius** (computed from `--aura-base-radius`, unitless, range 0–10):

| Token | Purpose |
|-------|---------| 
| `--vaadin-radius-s` | Small controls (should not become circles) |
| `--vaadin-radius-m` | Default component radius |
| `--vaadin-radius-l` | Large containers, cards, dialogs |

**Shadows** (Aura-specific):

| Token | Purpose |
|-------|---------| 
| `--aura-shadow-xs` | Subtle elevation — buttons, inputs, checkboxes |
| `--aura-shadow-s` | Slight elevation — primary buttons, selected controls, cards |
| `--aura-shadow-m` | Clear elevation — overlays, notifications, dialogs |

**Surface colors** for visual hierarchy (read-only, computed):
- `--aura-surface-color` — semi-transparent elevated background
- `--aura-surface-color-solid` — opaque version
- Control with `--aura-surface-level` (number, higher = more elevation) and `--aura-surface-opacity` (default 0.5)

**Layout approach:** Use Vaadin `VerticalLayout` / `HorizontalLayout` (Flow) or flexbox/grid with `--vaadin-gap-*` / `--vaadin-padding-*` tokens. No hard-coded spacing values.

---

## 5. Component Standards

> Vaadin components and usage patterns for the inventory management application.

| Component | When to Use | Notes |
|-----------|-------------|-------| 
| **Button** | Primary and secondary actions | Use `theme="primary"` for main CTA; `theme="secondary"` or default for others |
| **Grid** | Tabular data display (list views) | Always enable sorting; use LitRenderer for rich content |
| **TextField** | Text input (username, email, product name, etc.) | Always set placeholder; validate on blur or change |
| **NumberField** | Numeric input (prices, quantities) | Set min/max constraints; display currency symbol as prefix if applicable |
| **ComboBox** | Select from predefined list (categories, suppliers, roles) | Allow filtering/search; load options from database |
| **DatePicker** | Select date (stock movement filters) | Use for date range filtering in ledger views |
| **Checkbox** | Boolean toggle (active/inactive, system role flag) | Clear label; position label to the right of checkbox |
| **TextArea** | Multi-line text (description, reason) | Set reasonable row height; resize handle optional |
| **Dialog** | Confirmation, forms, details | Modal by default; title and clear action buttons (Save, Cancel, Confirm, Discard) |
| **Notification** | Success, error, warning feedback | Position at top-right; auto-close after 3–5 seconds (except errors) |
| **FormLayout** | Structure forms with labels | 1–2 columns depending on viewport; responsive |
| **VerticalLayout / HorizontalLayout** | Organize content flow | Use consistent gap/padding from theme tokens |
| **SidebarForm** (custom) | Create/edit/view forms (not full-page replacement) | Right-side panel, dismissible, shows list behind it |
| **StatusBadge** (custom) | Render status indicators | Use for active/inactive, stock status, role type, movement type |

---

## 6. Badge Styles

For LitRenderer and StatusBadge components:

### Product Status Badges

| Status | Background | Text Color | Semantics |
|--------|-----------|-----------|-----------|
| **OK** | `--aura-green` | `--aura-accent-contrast-color` | Stock is above minimum |
| **LOW_STOCK** | `--aura-orange` | `--aura-accent-contrast-color` | Stock at or below minimum |

### Active/Inactive Badges

| Status | Background | Text Color | Semantics |
|--------|-----------|-----------|-----------|
| **Active** | `--aura-blue` | `--aura-accent-contrast-color` | Enabled and available |
| **Inactive** | Gray (neutral secondary) | `--vaadin-text-color` | Disabled or archived |

### Role Type Badges

| Type | Background | Text Color | Semantics |
|------|-----------|-----------|-----------|
| **System** | `--aura-blue` | `--aura-accent-contrast-color` | Built-in role (non-editable) |
| **Custom** | `--aura-purple` | `--aura-accent-contrast-color` | User-defined role |

### Stock Movement Type Badges

| Type | Background | Semantics |
|------|-----------|-----------|
| **PURCHASE** | `--aura-green` | Inbound goods |
| **SALE** | `--aura-orange` | Outbound goods |
| **RETURN_IN** | `--aura-blue` | Inbound goods |
| **RETURN_OUT** | `--aura-blue` | Outbound goods |
| **ADJUSTMENT_IN** | `--aura-yellow` | Stock correction (inbound) |
| **ADJUSTMENT_OUT** | `--aura-yellow` | Stock correction (outbound) |
| **INITIAL_STOCK** | `--aura-neutral` | System initialization |
| **DAMAGED** | `--aura-red` | Damaged goods removed |
| **LOST** | `--aura-red` | Missing goods |

### Quantity Direction Indicators

| Direction | Prefix | Color | Semantics |
|-----------|--------|-------|-----------|
| **Positive** | `+` | `--aura-green` | Stock increase |
| **Negative** | `−` | `--aura-red` | Stock decrease |

---

## 7. Form Design

### Form Layout

- Use **FormLayout** or **VerticalLayout** with consistent spacing
- Labels above or to the left of inputs (responsive: above on mobile, left on desktop)
- Group related fields together
- Required fields marked with `*` or `(required)` next to label
- Error messages appear below the field in `--aura-red-text` color
- Helper text in `--vaadin-text-color-secondary` below label or field

### Sidebar Form Behavior

- **Width:** `min(480px, 100vw)` on desktop; `100vw` on mobile
- **Position:** Attached to right side of screen
- **Background:** Use `--vaadin-background-color` or slightly elevated surface
- **Header:** Title, optional breadcrumb, close button (X)
- **Content:** Form fields with labels
- **Footer:** Save and Cancel buttons
- **Dismissible:** Close button, Escape key, clicking outside (with dirty-state check)
- **Dirty state:** Visual indicator if form has unsaved changes; confirmation before close

### Form Validation

- **Real-time validation:** Validate on field blur or change (Vaadin Binder)
- **Error display:** Field-level errors below input, or notification at top
- **Submit validation:** Prevent save if form is invalid
- **Feedback:** Clear error message explaining the issue and how to fix it

---

## 8. List View Design

### Grid Layout

- **Toolbar above grid:**
  - Search field (text input)
  - Filter controls (combobox, checkbox, date picker)
  - "New [Entity]" button (if user has CREATE permission)
  - Refresh button (optional)

- **Grid columns:**
  - Identifier columns (SKU, name, code, username)
  - Key attributes (category, supplier, price, quantity)
  - Status columns (active/inactive, stock status, movement type)
  - Timestamps (created, updated) — optional, may be in details view
  - Action column (Edit, Delete/Deactivate, View)

- **LitRenderer usage:**
  - Identifier + details (product name + SKU)
  - Status badges (active, low-stock, role type, movement type)
  - Quantity formatting (positive/negative with +/− prefix)
  - Contact info (email, phone with icons/links if applicable)

### Grid Features

- **Sorting:** Enabled on all columns by default
- **Selection:** Single-row selection (highlight on click)
- **Pagination:** Server-side pagination for large datasets (default 50 rows)
- **Keyboard:** Arrow keys to navigate, Enter to select, Delete to confirm
- **Responsive:** Scrollable horizontally on mobile if needed

---

## 9. Navigation

### Main Layout

- **Header:** Application title/logo, user menu (username, logout)
- **Sidebar Navigation:** Collapsible on mobile
  - Inventory section: Products, Categories, Suppliers, Stock Movements
  - Admin section: Users, Roles, Permissions (visible only to admins)
  - Hierarchical with collapsible groups

- **Mobile behavior:** Hamburger menu, full-width sidebar on toggle

### Routing

- All routes use Vaadin Flow `@Route`
- Public routes: `/login`, `/signup`
- Protected routes: Require authentication + permission checks
- 404 handling: Custom "Not Found" page

---

## 10. Responsive Behavior

### Breakpoints

- **Mobile** (< 640px): Single column, stacked layouts, full-width sidebars
- **Tablet** (640–1024px): Two-column grids, wider sidebars (~40vw)
- **Desktop** (> 1024px): Multi-column layouts, sidebar forms (~480px fixed width)

### Mobile Optimizations

- Touch-friendly button sizes (minimum 44px x 44px)
- Larger input fields and labels
- Reduced whitespace for screen real estate
- Sidebar forms go full-width (100vw) with inset from top/bottom
- Grid columns reorder or collapse on narrow screens

### CSS Media Queries

Use Vaadin's `@media` queries or Vaadin layout responsive properties:

```css
@media (max-width: 640px) {
  .sidebar-form {
    width: 100vw;
  }
}

@media (min-width: 641px) and (max-width: 1024px) {
  .sidebar-form {
    width: 40vw;
  }
}

@media (min-width: 1025px) {
  .sidebar-form {
    width: 480px;
  }
}
```

---

## 11. Accessibility

- **Color contrast:** Use Aura's built-in tokens to ensure 4.5:1 contrast ratio for text
- **Keyboard navigation:** All interactive elements focusable with Tab
- **ARIA labels:** Add `aria-label` or `aria-labelledby` where needed (Vaadin components handle most automatically)
- **Form labels:** Always associate labels with inputs (Vaadin FormLayout does this)
- **Semantic HTML:** Use `<button>`, `<a>`, `<form>` appropriately
- **Skip links:** Optional; not required for admin app

---

## 12. Error & Confirmation UX

### Error States

- **Field-level errors:** Display below input in `--aura-red-text`, with clear message
- **Form-level errors:** Notification at top of view in red
- **Toasts:** Use Notification with error theme for transient messages

### Confirmation Dialogs

- **Destructive actions:** Show modal dialog before delete/deactivate
- **Title:** Clear question (e.g., "Delete Product?")
- **Message:** Explain consequences (e.g., "This cannot be undone")
- **Buttons:** "Cancel" (secondary) and "Confirm" (primary, red for destructive)
- **Keyboard:** Escape = Cancel, Enter = Confirm (if clear context)

### Success Feedback

- **Notification:** Brief message (e.g., "Product saved successfully")
- **Position:** Top-right corner
- **Duration:** 3–5 seconds, then auto-close
- **Icon:** Checkmark or success indicator

---

## 13. Dark Mode Support (Future)

Aura supports dark mode automatically via OS preference or user setting. No special styling required; theme variables adapt automatically. If dark mode toggle is added later, use standard Vaadin mechanism.

---

## 14. Custom Component Examples

### SidebarForm

```java
public class SidebarForm extends Div {
  private Dialog dialog;
  private VerticalLayout formLayout;
  private HorizontalLayout actions;
  private Button saveButton;
  private Button cancelButton;
  
  // Constructor and UI setup
  // Bind form fields
  // Handle save/cancel/dirty state
}
```

### StatusBadge

```java
public class StatusBadge extends Span {
  public StatusBadge(String text, String theme) {
    addClassName("status-badge");
    addClassNames("badge", "badge-" + theme);
    setText(text);
  }
}
```

### ConfirmDialogFactory

```java
public class ConfirmDialogFactory {
  public static Dialog createDeleteConfirm(Runnable onConfirm) {
    Dialog dialog = new Dialog();
    dialog.setHeaderTitle("Confirm Delete");
    dialog.add(new Paragraph("This action cannot be undone."));
    
    Button confirmBtn = new Button("Delete", e -> {
      onConfirm.run();
      dialog.close();
    });
    confirmBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
    
    Button cancelBtn = new Button("Cancel", e -> dialog.close());
    
    dialog.getFooter().add(cancelBtn, confirmBtn);
    return dialog;
  }
}
```

---

## 15. CSS Customization Example

In `styles.css`:

```css
:root {
  /* Optional: Override Aura defaults */
  --aura-base-font-size: 16;
  --aura-base-size: 16;
  --aura-base-radius: 4;
}

/* Custom component styles */
.status-badge {
  border-radius: var(--vaadin-radius-s);
  padding: var(--vaadin-padding-xs) var(--vaadin-padding-s);
  font-size: var(--aura-font-size-xs);
  font-weight: var(--aura-font-weight-medium);
  display: inline-block;
}

.status-badge.badge-ok {
  background-color: var(--aura-green);
  color: var(--aura-accent-contrast-color);
}

.status-badge.badge-low-stock {
  background-color: var(--aura-orange);
  color: var(--aura-accent-contrast-color);
}

/* Sidebar form on mobile */
@media (max-width: 640px) {
  .sidebar-form {
    width: 100vw;
  }
}
```

---

## 16. Design Review Checklist

Before submitting UI changes:

- [ ] All colors use `--aura-*` or `--vaadin-*` tokens (no hex values)
- [ ] All font sizes use `--aura-font-size-*` tokens (no hardcoded px/rem)
- [ ] All spacing uses `--vaadin-gap-*` or `--vaadin-padding-*` tokens
- [ ] All shadows use `--aura-shadow-*` tokens
- [ ] Border radius uses `--vaadin-radius-*` tokens
- [ ] Forms have clear labels and validation feedback
- [ ] Sidebar forms are dismissible and show dirty-state warnings
- [ ] List grids use LitRenderer for rich column content
- [ ] Confirmation dialogs exist for destructive actions
- [ ] Mobile responsiveness is tested (< 640px)
- [ ] Keyboard navigation works (Tab, Enter, Escape)
- [ ] Color contrast meets accessibility standards (WCAG AA)
- [ ] No hard-coded colors, sizes, or spacing values
