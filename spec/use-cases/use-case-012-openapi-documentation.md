# UC-012: OpenAPI Documentation

> API consumers need discoverable, executable documentation for the REST API without requiring prior authentication.

---

**Goal:** As an API Consumer, I want Swagger/OpenAPI documentation for all current REST endpoints so that I can understand and test the API contract safely.

**Status:** Implemented
**Date:** 2026-06-16

> A use case cannot be marked as **Implemented** unless all criteria in the use-case implementation workflow are fulfilled.

---

## Actors

- **Primary actor:** API Consumer
- **Secondary actors:** Springdoc OpenAPI, Spring Security API filter chain

---

## Preconditions

- UC-011 REST API Support is implemented.
- Current REST endpoints exist under `/api/**` for authentication and product CRUD.
- Application security uses a dedicated API `SecurityFilterChain`.
- Application configuration currently exists and can be migrated from `.properties` to YAML.

---

## Trigger

API Consumer opens the Swagger UI or fetches the OpenAPI JSON document.

---

## Main Flow

1. API Consumer navigates to the Swagger UI route.
2. System serves the Swagger UI without requiring authentication.
3. Swagger UI loads the OpenAPI document.
4. System exposes documentation for all current REST endpoints.
5. API Consumer reviews authentication and product CRUD endpoint contracts.
6. API Consumer can use the documented JWT bearer scheme for protected API calls.

---

## Alternative Flows

### AF-1: OpenAPI JSON Requested Directly

**Branches from:** Main Flow step 1
**Condition:** API Consumer requests the OpenAPI JSON route directly

1. System serves the OpenAPI JSON document without requiring authentication.
2. Use case ends.

### AF-2: Swagger Static Asset Requested

**Branches from:** Main Flow step 2
**Condition:** Swagger UI requests its supporting JavaScript, CSS, or webjar assets

1. API security filter chain permits the asset request without authentication.
2. Swagger UI finishes loading.
3. Returns to Main Flow step 3.

### AF-3: Protected API Requested Without JWT

**Branches from:** Main Flow step 6
**Condition:** API Consumer uses Swagger UI to call a protected product endpoint without a JWT

1. API security filter chain rejects the request.
2. System returns the existing standardized unauthorized API response.
3. Use case ends.

### AF-4: OpenAPI Dependency Missing or Misconfigured

**Branches from:** Main Flow step 2
**Condition:** Springdoc dependency or OpenAPI configuration is missing or invalid

1. Swagger UI or OpenAPI JSON route fails to load.
2. Automated tests fail.
3. Use case ends.

### AF-5: YAML Configuration Regression

**Branches from:** Main Flow step 2
**Condition:** Migrating `application.properties` to YAML drops or renames an existing setting

1. Application startup or existing use-case tests fail.
2. Developer restores the missing setting in YAML.
3. Returns to Main Flow step 2.

---

## Postconditions

- **On success:** Swagger UI and OpenAPI JSON are publicly reachable, current REST endpoints are documented, JWT bearer authentication is described, and existing application properties are preserved in YAML format.
- **On failure:** The application does not ship incomplete API documentation or broken configuration.

---

## Business Rules

| ID | Rule |
|----|------|
| BR-01 | Add `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3` as the OpenAPI/Swagger dependency. |
| BR-02 | Convert `src/main/resources/application.properties` to `src/main/resources/application.yml` without losing datasource, JPA, Flyway, Envers, or JWT-related settings. |
| BR-03 | Swagger UI must be reachable without authentication. |
| BR-04 | OpenAPI JSON must be reachable without authentication. |
| BR-05 | Swagger static assets and webjars required by the Swagger UI must be reachable without authentication. |
| BR-06 | API security must still require JWT authentication for protected `/api/products/**` endpoints. |
| BR-07 | Existing open API endpoints `POST /api/auth/login` and `POST /api/auth/signup` must remain anonymous. |
| BR-08 | OpenAPI documentation must include all current REST endpoints: `POST /api/auth/login`, `POST /api/auth/signup`, `GET /api/products`, `GET /api/products/{id}`, `POST /api/products`, `PUT /api/products/{id}`, and `DELETE /api/products/{id}`. |
| BR-09 | OpenAPI documentation must describe JWT bearer authentication for protected API endpoints. |
| BR-10 | OpenAPI documentation must use existing request/response DTOs and standardized API response wrapper schemas where practical. |
| BR-11 | Swagger/OpenAPI routes must not weaken authentication for non-documentation API endpoints. |

---

## Tests

> Tests verify the flows and business rules above. There is no separate acceptance-criteria list — the flows and rules *are* the acceptance criteria. The use case's test class, folder, and naming conventions are defined by the `/use-case-tests` skill — do not name a test class here.

- [x] Main Flow covered (steps 1-6)
- [x] AF-1, AF-2, AF-3, AF-4, AF-5 covered
- [x] BR-01 through BR-11 covered

---

## UI Surface

- Swagger UI page for browsing and trying REST endpoints.
- OpenAPI JSON document for machines, clients, and tooling.

| Page | Access |
|------|--------|
| `/swagger-ui.html` | Anonymous |
| `/swagger-ui/**` | Anonymous |
| `/v3/api-docs` | Anonymous |
| `/v3/api-docs/**` | Anonymous |
| `/webjars/swagger-ui/**` | Anonymous |
