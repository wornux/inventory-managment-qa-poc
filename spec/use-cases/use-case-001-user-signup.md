# UC-001: User Signup

> New users create an account with username, email, and password.

---

**Goal:** As an unauthenticated user, I want to create a new account so that I can access the inventory management system.

**Status:** Implemented
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Unauthenticated user
- **Secondary actors:** None

---

## Preconditions

- User is not logged in
- User has access to the signup page

---

## Trigger

User clicks the signup link on the login page or navigates directly to the signup view.

---

## Main Flow

1. User enters a unique username, valid email, and password (with confirmation).
2. System validates all fields and checks for duplicate username/email.
3. User clicks the "Sign up" button.
4. System hashes the password, creates the user account, assigns the default INVENTORY_VIEWER role, and stores the new user.
5. System redirects the user to the login page with a success message.

---

## Alternative Flows

### AF-1: Duplicate Username

**Branches from:** Main Flow step 2
**Condition:** Username already exists in the database

1. System displays error message: "Username already taken."
2. User corrects the username and resubmits.
3. Returns to Main Flow step 2.

### AF-2: Duplicate Email

**Branches from:** Main Flow step 2
**Condition:** Email already exists in the database

1. System displays error message: "Email already registered."
2. User corrects the email and resubmits.
3. Returns to Main Flow step 2.

### AF-3: Invalid Email Format

**Branches from:** Main Flow step 2
**Condition:** Email field does not match standard email pattern

1. System displays error message: "Invalid email address."
2. User corrects the email and resubmits.
3. Returns to Main Flow step 2.

### AF-4: Weak Password

**Branches from:** Main Flow step 2
**Condition:** Password does not meet minimum strength requirements (e.g., < 8 characters)

1. System displays error message: "Password must be at least 8 characters."
2. User provides a stronger password and resubmits.
3. Returns to Main Flow step 2.

### AF-5: Password Mismatch

**Branches from:** Main Flow step 2
**Condition:** Password and confirmation password do not match

1. System displays error message: "Passwords do not match."
2. User re-enters both password fields and resubmits.
3. Returns to Main Flow step 2.

### AF-6: Missing Required Fields

**Branches from:** Main Flow step 2
**Condition:** One or more required fields are empty

1. System displays field-level validation errors for all missing fields.
2. User fills in all required fields and resubmits.
3. Returns to Main Flow step 2.

---

## Postconditions

- **On success:** New user account is created, stored with hashed password, assigned the INVENTORY_VIEWER default role, and user is redirected to login page.
- **On failure:** No account is created; user remains on the signup form with error message displayed.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Username must be unique and not blank |
| BR-02 | Email must be unique, not blank, and in valid format |
| BR-03 | Password must be at least 8 characters |
| BR-04 | Password and confirmation must match exactly |
| BR-05 | Passwords must be stored as bcrypt hashes, never in plain text |
| BR-06 | All new users are assigned the INVENTORY_VIEWER role by default |

---

## Tests

- [x] Main Flow covered (steps 1–5)
- [x] AF-1 (duplicate username) covered
- [x] AF-2 (duplicate email) covered
- [x] AF-3 (invalid email format) covered
- [x] AF-4 (weak password) covered
- [x] AF-5 (password mismatch) covered
- [x] AF-6 (missing required fields) covered
- [x] BR-01 through BR-06 covered

---

## UI Surface

- **Signup page:** Accessible to unauthenticated users; displays form with username, email, password, and confirmation fields.
- **Form validation:** Real-time field validation with inline error messages.
- **Success feedback:** Message displayed on redirect to login page confirming account creation.

| Page | Access |
|------|--------|
| Signup | Anonymous |
