# UC-002: User Login

> Registered users authenticate with username/email and password to access the system.

---

**Goal:** As a registered user, I want to log in with my credentials so that I can access the inventory management system.

**Status:** Implemented
**Date:** 2025-01-01

---

## Actors

- **Primary actor:** Registered user
- **Secondary actors:** None

---

## Preconditions

- User is not logged in
- User account exists and is active
- User has access to the login page

---

## Trigger

User navigates to the login page or is redirected there by Spring Security after attempting to access a protected route.

---

## Main Flow

1. User enters username (or email) and password.
2. System validates that the username/email exists and the account is active.
3. System validates that the provided password matches the stored hash.
4. System creates an authenticated session and stores user context.
5. System redirects the user to the home page (main inventory view).

---

## Alternative Flows

### AF-1: Invalid Credentials

**Branches from:** Main Flow step 2–3
**Condition:** Username does not exist, or password does not match

1. System displays generic error message: "Invalid username or password." (without indicating which is wrong).
2. User re-enters credentials and resubmits.
3. Returns to Main Flow step 1 / Use case ends if user gives up.

### AF-2: Account Inactive

**Branches from:** Main Flow step 2
**Condition:** User account exists but is marked inactive

1. System displays error message: "Account is inactive. Contact administrator."
2. User cannot proceed.
3. Use case ends.

### AF-3: Missing Required Fields

**Branches from:** Main Flow step 1
**Condition:** Username or password field is empty

1. System displays field-level validation error(s).
2. User fills in all required fields and resubmits.
3. Returns to Main Flow step 1.

### AF-4: Session Timeout

**Branches from:** After Main Flow completes
**Condition:** User's session expires due to inactivity

1. System invalidates the session and redirects user to login page.
2. User logs in again with their credentials.
3. Use case repeats from Main Flow step 1.

---

## Postconditions

- **On success:** User is authenticated, session is created, and user is redirected to the home page.
- **On failure:** No session is created; user remains on login page with error message displayed.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Login accepts username or email address |
| BR-02 | Password validation must use bcrypt hashing |
| BR-03 | Inactive user accounts must be rejected |
| BR-04 | Error message must not reveal whether username or password is incorrect (security) |
| BR-05 | Successful login must create a session and store authentication context |
| BR-06 | Session must be invalidated on logout or timeout |

---

## Tests

- [x] Main Flow covered (steps 1–5)
- [x] AF-1 (invalid credentials) covered
- [x] AF-2 (account inactive) covered
- [x] AF-3 (missing required fields) covered
- [x] AF-4 (session timeout) covered
- [x] BR-01 through BR-06 covered

---

## UI Surface

- **Login page:** Displays form with username/email and password fields, plus login button.
- **Link to signup:** User can navigate to signup page if they don't have an account.
- **Error feedback:** Generic error message displayed for invalid credentials.
- **Session management:** Session is managed server-side; user is redirected to protected routes after login.

| Page | Access |
|------|--------|
| Login | Anonymous |
