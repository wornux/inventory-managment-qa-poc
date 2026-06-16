# UC-011: REST API Support

> External clients need a stable JSON API for authentication and product catalog operations without interfering with the Vaadin UI.

---

**Goal:** As an API Client, I want to authenticate with JWT and manage products through REST endpoints so that inventory data can be integrated with external systems.

**Status:** Implemented
**Date:** 2026-06-16

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** API Client
- **Secondary actors:** Spring Security, JWT authentication middleware

---

## Preconditions

- UC-001 user signup and UC-002 user login exist for account creation and credential validation.
- UC-003 product catalog exists with product, category, and supplier domain behavior.
- Spring Security uses database-backed users and role-based access control.
- Product persistence is managed by Spring Data JPA and Flyway-managed PostgreSQL schema.

---

## Trigger

An API client sends an HTTP request to an `/api/**` endpoint.

---

## Main Flow

1. API Client submits credentials to the REST login endpoint.
2. System validates the credentials and returns a standardized response containing a JWT.
3. API Client sends a product CRUD request to an `/api/products/**` endpoint with the JWT in the `Authorization: Bearer` header.
4. API security filter chain validates the JWT and sets the authenticated security context.
5. Product controller validates the request DTO with Jakarta Validation.
6. Product controller delegates to product application/service logic.
7. System persists or reads product data using the existing product domain model.
8. System returns a standardized response wrapper containing product response DTOs, pagination metadata when applicable, and helpful request outcome information.

---

## Alternative Flows

### AF-1: Open Endpoint Request

**Branches from:** Main Flow step 1
**Condition:** Request targets an explicitly open API endpoint such as login or signup

1. API security middleware permits the request without requiring a JWT.
2. System handles the request through the matching security endpoint.
3. Returns to Main Flow step 2 when login succeeds, or the use case ends for signup.

### AF-2: Missing or Invalid JWT

**Branches from:** Main Flow step 4
**Condition:** JWT is missing, expired, malformed, or fails signature validation

1. API security middleware rejects the request.
2. System returns a standardized unauthorized error response.
3. Use case ends.

### AF-3: Insufficient Permission

**Branches from:** Main Flow step 4
**Condition:** Authenticated user does not have the required permission for the requested product action

1. System denies the request.
2. System returns a standardized forbidden error response.
3. Use case ends.

### AF-4: Validation Failure

**Branches from:** Main Flow step 5
**Condition:** Request DTO violates Jakarta Validation constraints or references invalid category/supplier data

1. Rest controller advice converts validation details into the standardized response wrapper.
2. System returns a bad request response with field-level or business-rule details.
3. Use case ends.

### AF-5: Product Not Found

**Branches from:** Main Flow step 7
**Condition:** API Client requests, updates, deletes, or deactivates a product id that does not exist

1. Product controller or service raises a not-found error.
2. Rest controller advice converts the error into the standardized response wrapper.
3. System returns a not found response.
4. Use case ends.

### AF-6: Duplicate SKU Conflict

**Branches from:** Main Flow step 7
**Condition:** API Client creates or updates a product using a SKU already assigned to another product

1. Product service rejects the duplicate SKU.
2. Rest controller advice converts the error into the standardized response wrapper.
3. System returns a conflict response.
4. Use case ends.

---

## Postconditions

- **On success:** API clients can authenticate with JWT, call protected product REST endpoints, and receive standardized JSON responses without breaking Vaadin UI authentication.
- **On failure:** No invalid product change is persisted; API errors are returned through the standardized response wrapper.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | REST endpoints must live under `/api/**` so they do not compete with Vaadin UI routes. |
| BR-02 | API security must use a dedicated `SecurityFilterChain` responsible only for API paths. |
| BR-03 | Login and signup API endpoints must be explicitly open; all product CRUD endpoints require JWT authentication. |
| BR-04 | JWT authentication must be stateless and must not depend on the Vaadin session. |
| BR-05 | JWT middleware must reject missing, expired, malformed, or invalid tokens with a standardized unauthorized response. |
| BR-06 | REST responses must use a reusable record wrapper that includes success status, message, data, errors, and pagination metadata when applicable. |
| BR-07 | REST controllers must share an abstract base controller for consistent response construction. |
| BR-08 | A security controller must expose REST login and signup support for API clients. |
| BR-09 | Product controller must support product list, detail, create, update, and deactivate/delete operations. |
| BR-10 | Product REST DTOs must end with `Request` or `Response`. |
| BR-11 | JSON mapping between entities, requests, and responses must use Jackson 3 `JsonMapper`, not ad-hoc manual JSON construction. |
| BR-12 | Request DTO validation must use Jakarta Validation. |
| BR-13 | Product list endpoints must support Spring Data `Pageable`. |
| BR-14 | `RestControllerAdvice` must translate validation, authentication, authorization, not-found, conflict, and unexpected errors into the standard response wrapper. |
| BR-15 | This use case implements only REST security support and product CRUD; other domain APIs are out of scope. |

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [x] Main Flow covered (steps 1-8)
- [x] AF-1, AF-2, AF-3, AF-4, AF-5, AF-6 covered
- [x] BR-01 through BR-15 covered

---

## UI Surface

- No Vaadin UI route is introduced by this use case.
- REST API clients interact through JSON HTTP endpoints.

| Page | Access |
|------|--------|
| `POST /api/auth/login` | Anonymous |
| `POST /api/auth/signup` | Anonymous |
| `GET /api/products` | Authenticated |
| `GET /api/products/{id}` | Authenticated |
| `POST /api/products` | Authenticated |
| `PUT /api/products/{id}` | Authenticated |
| `DELETE /api/products/{id}` | Authenticated |
