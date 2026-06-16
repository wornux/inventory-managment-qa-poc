# UC-015: Keycloak OAuth Authentication

---

**Goal:** As an inventory user, I want to authenticate through Keycloak OpenID Connect instead of local passwords so that access is centralized, API calls use OAuth2 tokens, and local users are provisioned from trusted identity claims.

**Status:** Implemented
**Date:** 2026-06-16

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** Inventory user
- **Secondary actors:** Keycloak OpenID Connect provider, Keycloak Admin API

---

## Preconditions

- Keycloak realm and application client are configured.
- Keycloak realm requires `iss`, `sub`, `preferred_username`, and `email` claims for users authenticating into the application.
- Local RBAC roles and permissions exist.
- The `INVENTORY_VIEWER` role exists as the default low-privilege role for auto-provisioned users.
- Bootstrap configuration exists for `admin@wornux.com` as the system administrator.

---

## Trigger

The application starts and runs the admin bootstrap job, or a user opens a protected application route such as `/categories`.

---

## Main Flow

1. System starts the application.
2. System connects to the Keycloak Admin API.
3. System creates or verifies `admin@wornux.com` in Keycloak.
4. System creates or verifies the local `admin@wornux.com` user with the `SYSTEM_ADMINISTRATOR` role.
5. User opens a protected application route.
6. System detects that no authenticated Spring Security context exists.
7. System redirects the user to Keycloak login.
8. User authenticates successfully in Keycloak.
9. Keycloak redirects the user back to the application.
10. System validates the OIDC login and extracts `iss`, `sub`, `preferred_username`, and `email`.
11. System looks up the local `AppUser` by `(issuer, subject)`.
12. System creates a local `AppUser` without a password if no matching user exists.
13. System assigns the `INVENTORY_VIEWER` role to a newly auto-provisioned non-admin user.
14. System establishes the Spring Security context using an OIDC-compatible principal.
15. System renders the originally requested protected route without casting the OIDC principal to `UserDetails`.
16. API client calls an `/api/**` endpoint with a Keycloak OAuth2 bearer JWT.
17. System validates the API JWT through Spring Security OAuth2 resource server support.
18. User clicks logout in the application.
19. System clears the application Spring Security context.
20. System redirects through Keycloak logout with a post-logout return URL of the current application base URL plus `/login`.

---

## Alternative Flows

### AF-1: Keycloak Login Unavailable

**Branches from:** Main Flow step 7
**Condition:** Keycloak cannot serve the authorization/login flow.

1. System fails authentication.
2. System shows a login failure message or redirects to the login page with an error state.
3. System does not create or update a local user.
4. Use case ends.

### AF-2: Required OIDC Claim Missing

**Branches from:** Main Flow step 10
**Condition:** OIDC identity data is missing `iss`, `sub`, `preferred_username`, or `email`.

1. System rejects the authentication result.
2. System records a clear provisioning/authentication error.
3. System does not create or update a local user.
4. Use case ends.

### AF-3: Local User Is Inactive

**Branches from:** Main Flow step 11
**Condition:** A local `AppUser` exists for `(issuer, subject)` but is inactive.

1. System denies application access.
2. System keeps the user authenticated with Keycloak but does not establish an authorized local application context.
3. Use case ends.

### AF-4: Local Username Or Email Conflict

**Branches from:** Main Flow step 12
**Condition:** Auto-provisioning would violate the local unique username or email constraints.

1. System rejects local provisioning.
2. System logs the conflicting username or email condition for administrator action.
3. System does not create a duplicate local user.
4. Use case ends.

### AF-5: Admin Bootstrap Fails

**Branches from:** Main Flow step 2
**Condition:** System cannot connect to Keycloak Admin API or cannot create or verify `admin@wornux.com`.

1. System fails application startup.
2. System records the bootstrap failure.
3. Use case ends.

### AF-6: API Token Missing Or Invalid

**Branches from:** Main Flow step 16
**Condition:** API client omits the bearer token or sends a token that cannot be validated by the configured OAuth2 resource server.

1. System rejects the API request as unauthorized.
2. System does not execute the protected API operation.
3. Use case ends.

### AF-7: Legacy Password Auth Endpoint Requested

**Branches from:** Main Flow step 16
**Condition:** API client calls the old password-based `/api/auth/login` or `/api/auth/signup` endpoint.

1. System rejects the request because password-based API authentication and signup are unavailable.
2. System does not issue an application-managed JWT.
3. Use case ends.

---

## Postconditions

- **On success:** Application authentication uses Keycloak OIDC; local users are linked by issuer and subject; `admin@wornux.com` exists as system administrator; API access uses OAuth2 bearer JWT validation; local users do not use passwords.
- **On failure:** No unauthorized application access is granted; no partial local user is created for failed OIDC provisioning; application startup fails when admin bootstrap cannot create or verify the system administrator.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Local user identity lookup for OIDC users must use `(issuer, subject)`, not username or email. |
| BR-02 | Keycloak must provide `iss`, `sub`, `preferred_username`, and `email`; missing required claims must reject login. |
| BR-03 | Local `AppUser` records must no longer require or store a password field for authentication. |
| BR-04 | Auto-provisioned non-admin users must receive the `INVENTORY_VIEWER` role by default. |
| BR-05 | `admin@wornux.com` must exist in Keycloak and locally with the `SYSTEM_ADMINISTRATOR` role before application startup succeeds. |
| BR-06 | Application code must support OIDC principals and must not cast `DefaultOidcUser` to `UserDetails`. |
| BR-07 | `/api/**` endpoints must use Spring Security OAuth2 resource server JWT validation instead of application-issued password-login JWTs. |
| BR-08 | Legacy password-based `/api/auth/login` and `/api/auth/signup` endpoints must be removed or disabled. |
| BR-09 | The old self-service `/signup` UI must be removed. |
| BR-10 | Logout must clear the Spring Security context and redirect through Keycloak logout to the current application base URL plus `/login`. |

---

## Tests

- [x] Main Flow covered (steps 1-20)
- [x] AF-1, AF-2, AF-3, AF-4, AF-5, AF-6, and AF-7 covered
- [x] BR-01 through BR-10 covered

---

## UI Surface

- **Login page:** Shows a Keycloak login entry point only; no username/password form.
- **Protected application routes:** Require Keycloak-authenticated users and continue to use local RBAC permissions after OIDC-to-local-user resolution.
- **Logout action:** Clears local application security context and sends the user through Keycloak logout.
- **Removed signup surface:** The old self-service signup page is no longer available.
- **API surface:** Protected REST endpoints require OAuth2 bearer JWTs issued by Keycloak.

| Page | Access |
|------|--------|
| Login | Anonymous |
| Signup | Removed |
| Protected Vaadin routes | Authenticated |
| API endpoints under `/api/**` | OAuth2 bearer JWT |
| Swagger/OpenAPI documentation | Anonymous |
