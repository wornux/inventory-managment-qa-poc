# Architecture

> Technology stack and application structure. `pom.xml` is the source of truth for versions. Do not modify `pom.xml`, `vite.config.ts`, or `architecture.md` without asking.

---

## 1. Technology Stack

- **Web Framework:** Vaadin Flow — server-side Java UI with responsive Aura design system
- **Backend:** Spring Boot — auto-configuration, embedded Tomcat, dependency injection
- **Language:** Java 25 (or later as specified in pom.xml)
- **Build Tool:** Maven 3.8.1+ (Maven wrapper included)
- **Database:** PostgreSQL 18 (or compatible)
- **Database Migrations:** Flyway — version-controlled SQL migrations
- **Security:** Spring Security with database-backed user authentication and role-based access control (RBAC)
- **ORM:** Spring Data JPA with Hibernate
- **Validation:** Bean Validation (Jakarta Validation), Vaadin Binder
- **Password Hashing:** Spring Security's BCryptPasswordEncoder
- **UI Components:** Vaadin Grid, Vaadin Form, Vaadin Button, Vaadin Notification, LitRenderer for custom column content
- **Testing:** JUnit 5, Vaadin Browserless Tests

---

## 2. Application Structure

```
src/main/java/com/example/inventory/
  ├── InventoryApplication.java                    — Spring Boot entry point
  ├── config/
  │   └── SecurityConfig.java                      — Spring Security configuration
  ├── security/
  │   ├── CustomUserDetailsService.java            — Load users and role permissions for Spring Security
  │   ├── authorization/AuthorizationService.java  — Shared permission checks for services and UI
  │   └── permission/                              — Fixed AppResource, AppAction, and AppPermission enums
  ├── user/
  │   ├── AppUser.java                             — User entity
  │   ├── AppUserRepository.java                   — Database access (Spring Data)
  │   ├── AppUserService.java                      — Business logic
  │   ├── AppUserDto.java                          — Data transfer object
  │   └── AppUserView.java                         — Vaadin UI view
  ├── role/
  │   ├── Role.java                                — Role entity
  │   ├── RoleRepository.java                      — Database access
  │   ├── RoleService.java                         — Business logic
  │   ├── RoleDto.java                             — Data transfer object
  │   └── RoleView.java                            — Vaadin UI view
  ├── inventory/
  │   ├── category/
  │   │   ├── Category.java                        — Category entity
  │   │   ├── CategoryRepository.java              — Database access
  │   │   ├── CategoryService.java                 — Business logic
  │   │   ├── CategoryDto.java                     — Data transfer object
  │   │   └── CategoryView.java                    — Vaadin UI view
  │   ├── supplier/
  │   │   ├── Supplier.java                        — Supplier entity
  │   │   ├── SupplierRepository.java              — Database access
  │   │   ├── SupplierService.java                 — Business logic
  │   │   ├── SupplierDto.java                     — Data transfer object
  │   │   └── SupplierView.java                    — Vaadin UI view
  │   ├── product/
  │   │   ├── Product.java                         — Product entity
  │   │   ├── ProductRepository.java               — Database access
  │   │   ├── ProductService.java                  — Business logic
  │   │   ├── ProductDto.java                      — Data transfer object
  │   │   └── ProductView.java                     — Vaadin UI view
  │   └── movement/
  │       ├── StockMovement.java                   — Stock movement entity
  │       ├── StockMovementRepository.java         — Database access
  │       ├── StockMovementService.java            — Business logic (with transaction logic)
  │       ├── StockMovementDto.java                — Data transfer object
  │       └── StockMovementView.java               — Vaadin UI view
  ├── ui/
  │   ├── MainLayout.java                          — Main application layout with sidebar navigation
  │   ├── components/
  │   │   ├── SidebarForm.java                     — Reusable sidebar form component
  │   │   ├── StatusBadge.java                     — Status indicator component
  │   │   └── ConfirmDialogFactory.java            — Confirmation dialog factory
  │   └── views/
  │       ├── LoginView.java                       — Public login page
  │       └── SignupView.java                      — Public signup page
  └── shared/
      ├── AuditableEntity.java                     — Base entity with created_at/updated_at
      ├── NotFoundException.java                   — Custom exception
      └── ValidationException.java                 — Custom exception

src/main/resources/
  ├── application.yml                              — Spring Boot configuration
  ├── application-dev.yml                          — Development profile
  ├── application-prod.yml                         — Production profile
  ├── db/migration/
  │   ├── prod/V1__create_security_schema.sql      — Initial users, roles, and global assignments
  │   ├── prod/V7__create_permissions_and_role_assignments.sql — Historical normalized RBAC schema
  │   ├── prod/V12__simplify_rbac_permissions.sql  — Migrates roles to typed permission-code arrays
  │   └── dev/V10__seed_dev_dummy_data.sql         — Development demo data
  └── META-INF/resources/
      └── styles.css                               — Custom Vaadin Aura theme styles
```

---

## 3. Database Design

- **PostgreSQL 17** with Flyway migrations
- **Migrations location:** `src/main/resources/db/migration/`
- **Migration naming:** `V[N]__[description].sql` (Flyway standard)
- **Primary keys:** `bigint generated by default as identity`
- **Timestamps:** Java entities use `Instant`; PostgreSQL stores values as `timestamptz` with `default now()`
- **Constraints:** Check constraints, unique constraints, foreign keys with appropriate cascade rules
- **Indexes:** On frequently queried columns (foreign keys, active flags, dates)
- **Business rules:** Enforced at database level where practical (constraints); enforced at service layer for workflow rules

---

## 4. Security Architecture

### Authentication

- **Provider:** Spring Security
- **User store:** Database (AppUser table with hashed passwords)
- **Password hashing:** BCryptPasswordEncoder (Spring Security built-in)
- **Session management:** Server-side session via Spring Security
- **Login flow:** Custom or built-in Vaadin LoginForm at `/login`

### Authorization

- **Model:** Single-level RBAC adapted from Socratic Tutor without contexts or assignment levels
- **Permission structure:** Fixed `AppPermission` values composed from typed `AppResource` and `AppAction` enums
- **Role assignment:** Users → global Roles; roles store permission codes in PostgreSQL `text[]`
- **Authorization checks:** `AuthorizationService` resolves the current user's active global roles from the database on every protected operation
- **UI reflection:** Navigation and controls use the same typed checks as business services

### Public Routes

- `/login` — Login page (anonymous)
- `/signup` — Signup page (anonymous)
- `/styles/**` — Vaadin theme assets (anonymous)
- `/frontend/**` — Frontend assets (anonymous)

### Protected Routes

- All inventory views (`/products`, `/categories`, `/suppliers`, `/stock-movements`, etc.) — Require authentication + the matching VIEW permission
- Admin views (`/users`, `/roles`) — Require authentication + the matching VIEW permission

---

## 5. UI State Management

### Vaadin Flow Components

- **Grid:** `com.vaadin.flow.component.grid.Grid` for list views
- **Form:** Vaadin `TextField`, `ComboBox`, `NumberField`, etc., bound via Vaadin Binder
- **Dialogs:** Vaadin `Dialog` for confirmations
- **Notifications:** Vaadin `Notification` for feedback
- **Layouts:** Vaadin `VerticalLayout`, `HorizontalLayout` for responsive structure

### Local UI State (Non-Shared Signals Equivalent)

- **Sidebar visibility:** Component visibility flag on SidebarForm
- **Sidebar mode:** Enum (CREATE, EDIT, VIEW) stored in component state
- **Selected entity:** Currently selected row in Grid (via Grid's selection model)
- **Search text:** Form field value on the view
- **Filter state:** Filter component values (ComboBox, checkbox selections)
- **Form dirty state:** Binder dirty flag (automatic with Vaadin Binder)
- **Confirmation dialog:** Dialog open/close state

### No Shared Signals For

- Authentication state (managed by Spring Security)
- Persistent business data (stored in database)
- Cross-user data (not in scope)
- Security decisions (handled server-side)

---

## 6. Vaadin Grid Usage

- **Grid component:** Primary list view component for all entity CRUD pages
- **Data provider:** Spring Data repository or service-level filtering
- **Sorting:** Built-in column sorting enabled
- **Filtering:** Text search + status/category/type filters with real-time updates
- **Selection:** Single or multi-select as appropriate
- **Columns:**
  - Standard columns for entity fields
  - LitRenderer columns for badges (status, active/inactive, role type, movement type)
  - Action column (Edit, Delete, View buttons)
- **Lazy loading:** Optional for large datasets; load-on-demand pagination

### LitRenderer Usage

- **Product name/SKU:** Combine name and SKU in a single column with visual formatting
- **Stock status badge:** GREEN for OK, ORANGE for LOW_STOCK
- **Active/Inactive badge:** BLUE for active, GRAY for inactive
- **User active badge:** Similar to product status
- **Role type badge:** BLUE for System role, PURPLE for Custom role
- **Movement type badge:** Different color per movement type (PURCHASE, SALE, ADJUSTMENT, etc.)
- **Quantity delta:** Display with + for positive, − for negative

---

## 7. Form Validation

- **Bean Validation annotations:** `@NotBlank`, `@Email`, `@PositiveOrZero`, `@NotNull`, custom validators
- **Vaadin Binder:** Bind form fields to DTOs, validate on change and submit
- **Error display:** Field-level error messages below inputs
- **Feedback:** Validation errors prevent form submission; display error notification

---

## 8. Docker & Deployment

### Dockerfile

- Multi-stage build: compile with Maven, then package with JRE image
- Base image: `eclipse-temurin:25-jre` (or as specified in existing Dockerfile)
- Expose port `8080`
- Health check optional

### Docker Compose (compose.yml)

- **PostgreSQL service:** postgres:17 image with environment variables
- **Application service:** Built from Dockerfile, depends on PostgreSQL
- **Networking:** Services communicate via service names
- **Data persistence:** Named volume for PostgreSQL data
- **Environment variables:**
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
  - `SPRING_FLYWAY_ENABLED`

---

## 9. Spring Configuration (application.yml)

```yaml
spring:
  application:
    name: inventory-management

  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/inventory}
    username: ${SPRING_DATASOURCE_USERNAME:inventory}
    password: ${SPRING_DATASOURCE_PASSWORD:inventory}

  flyway:
    enabled: true
    locations: classpath:db/migration

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true

server:
  port: 8080
```

---

## 10. Testing Strategy

- **Unit tests:** Service and repository logic (JUnit 5)
- **Integration tests:** Database access, transaction behavior (Spring Boot Test + Testcontainers for PostgreSQL)
- **UI tests:** Vaadin Browserless Tests for view-level logic
- **Organization:** Tests organized by use case in `/use-case-tests`

---

## 11. Patterns & Conventions

### Service Layer

- All business logic, validation, and authorization checks in service layer
- Services are `@Transactional` where appropriate
- Service methods return DTOs, not entities

### Repository Layer

- Spring Data JPA repositories with custom queries as needed
- Named queries or JPQL for complex business logic
- Repositories handle all database access

### Entity Design

- Entities use `@Entity` and `@Table`
- Auditable entities extend `AuditableEntity` base class (created_at, updated_at)
- All required fields validated with constraints
- Relationships use appropriate cardinality annotations

### Exception Handling

- Custom exceptions: `NotFoundException`, `ValidationException`, `UnauthorizedException`
- Service layer throws checked or unchecked exceptions
- Controller/View layer catches and handles appropriately

---

## 12. Logging & Monitoring

- **Framework:** SLF4J + Logback (Spring Boot default)
- **Log level:** Debug in dev, Info in prod (via profiles)
- **Application logs:** Include relevant transaction IDs and user context

---

## 13. Performance Considerations

- **Database queries:** Use indexes on foreign keys, active flags, dates
- **Lazy loading:** Careful use of `@Lazy` and fetch strategies
- **Pagination:** Grid supports pagination for large datasets
- **RBAC:** Checks read current role state so deactivation and permission changes affect existing sessions immediately

---

## 14. Build & Run Locally

```bash
# Using Maven wrapper
./mvnw clean package

# Build Docker image
docker build -t inventory-app:latest .

# Start with compose.yml
docker compose up -d

# Access application at http://localhost:8080

# View logs
docker compose logs -f app
docker compose logs -f db
```

---

## 15. Deployment Checklist

- [ ] Database migrations pass on startup
- [ ] Security filter chain is configured correctly
- [ ] Password hashing is in place (BCrypt)
- [ ] All audit fields (created_at, updated_at) are persisted
- [ ] Transaction boundaries are correct (especially for stock movements)
- [ ] File upload directories are configured (if applicable)
- [ ] Logging is appropriately configured per environment
- [ ] Error pages are customized
- [ ] CORS is configured if needed
