package com.wornux.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.audit.AuditConfig;
import com.wornux.catalog.Category;
import com.wornux.catalog.CategoryRepository;
import com.wornux.catalog.MovementType;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductRepository;
import com.wornux.catalog.StockMovement;
import com.wornux.catalog.StockMovementRepository;
import com.wornux.catalog.StockMovementRequest;
import com.wornux.catalog.StockMovementService;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Role;
import com.wornux.user.RoleRepository;
import com.wornux.user.UserRequest;
import com.wornux.user.UserService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/prod"
})
@Import({AuditConfig.class, AuthorizationService.class, UserService.class, StockMovementService.class})
class AuthorizationWorkflowIT {

    private static final String SYSTEM_ADMINISTRATOR = "SYSTEM_ADMINISTRATOR";
    private static final String WAREHOUSE_OPERATOR = "WAREHOUSE_OPERATOR";
    private static final String INVENTORY_VIEWER = "INVENTORY_VIEWER";
    private static final String ADMINISTRATOR_USERNAME = "system-administrator";
    private static final String WAREHOUSE_OPERATOR_USERNAME = "warehouse-operator";
    private static final String INVENTORY_USER_USERNAME = "inventory-user";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");

    @Autowired
    UserService userService;

    @Autowired
    StockMovementService stockMovementService;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    StockMovementRepository stockMovementRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorProvisionsWarehouseOperator_assignedUserCanRecordStockMovement() {
        AppUser administrator = persistAdministrator();
        Product product = persistProduct(5);
        Role warehouseRole = requiredRole(WAREHOUSE_OPERATOR);
        authenticate(authentication(administrator.getUsername()));

        AppUser warehouseUser = userService.create(userRequest(
                WAREHOUSE_OPERATOR_USERNAME, "warehouse-operator@example.test", warehouseRole.getId()));
        Long warehouseUserId = warehouseUser.getId();
        Long productId = product.getId();

        flushAndClear();
        authenticate(authentication(WAREHOUSE_OPERATOR_USERNAME));

        StockMovement movement = stockMovementService.recordStockMovement(purchase(productId));
        Long movementId = movement.getId();

        flushAndClear();
        AppUser persistedUser = appUserRepository.findWithRolesById(warehouseUserId).orElseThrow();
        Product persistedProduct = productRepository.findById(productId).orElseThrow();
        StockMovement persistedMovement = stockMovementRepository.findById(movementId).orElseThrow();

        assertThat(persistedUser.getRoles())
                .extracting(Role::getCode)
                .containsExactlyInAnyOrder(WAREHOUSE_OPERATOR);
        assertThat(persistedProduct.getQuantityOnHand()).isEqualTo(8);
        assertThat(persistedMovement.getProduct().getId()).isEqualTo(productId);
        assertThat(persistedMovement.getUser().getId()).isEqualTo(warehouseUserId);
        assertThat(persistedMovement.getMovementType()).isEqualTo(MovementType.PURCHASE);
        assertThat(persistedMovement.getQuantityDelta()).isEqualTo(3);
    }

    @Test
    void administratorPromotesViewerToWarehouseOperator_userCanRecordMovementsImmediately() {
        AppUser administrator = persistAdministrator();
        Product product = persistProduct(5);
        Role viewerRole = requiredRole(INVENTORY_VIEWER);
        Authentication administratorAuthentication = authentication(administrator.getUsername());
        Authentication viewerAuthentication = authentication(INVENTORY_USER_USERNAME);
        authenticate(administratorAuthentication);

        AppUser viewer = userService.create(
                userRequest(INVENTORY_USER_USERNAME, "inventory-user@example.test", viewerRole.getId()));
        Long viewerId = viewer.getId();
        Long productId = product.getId();

        flushAndClear();
        authenticate(viewerAuthentication);

        assertThat(stockMovementService.canCreateMovements()).isFalse();
        assertThat(appUserRepository.findWithRolesById(viewerId).orElseThrow().getRoles())
                .extracting(Role::getCode)
                .containsExactlyInAnyOrder(INVENTORY_VIEWER);

        authenticate(administratorAuthentication);
        replaceRole(viewerId, WAREHOUSE_OPERATOR);

        flushAndClear();
        AppUser promotedUser = appUserRepository.findWithRolesById(viewerId).orElseThrow();

        assertThat(promotedUser.getRoles())
                .extracting(Role::getCode)
                .containsExactlyInAnyOrder(WAREHOUSE_OPERATOR);

        authenticate(viewerAuthentication);

        assertThat(stockMovementService.canCreateMovements()).isTrue();

        StockMovement movement = stockMovementService.recordStockMovement(purchase(productId));
        Long movementId = movement.getId();

        flushAndClear();
        Product persistedProduct = productRepository.findById(productId).orElseThrow();
        StockMovement persistedMovement = stockMovementRepository.findById(movementId).orElseThrow();

        assertThat(persistedProduct.getQuantityOnHand()).isEqualTo(8);
        assertThat(persistedMovement.getUser().getId()).isEqualTo(viewerId);
        assertThat(persistedMovement.getMovementType()).isEqualTo(MovementType.PURCHASE);
        assertThat(persistedMovement.getQuantityDelta()).isEqualTo(3);
    }

    @Test
    void administratorDeactivatesWarehouseOperator_userCanNoLongerRecordStockMovements() {
        AppUser administrator = persistAdministrator();
        Product product = persistProduct(5);
        Role warehouseRole = requiredRole(WAREHOUSE_OPERATOR);
        Authentication administratorAuthentication = authentication(administrator.getUsername());
        Authentication operatorAuthentication = authentication(WAREHOUSE_OPERATOR_USERNAME);
        authenticate(administratorAuthentication);

        AppUser operator = userService.create(userRequest(
                WAREHOUSE_OPERATOR_USERNAME, "warehouse-operator@example.test", warehouseRole.getId()));
        Long operatorId = operator.getId();
        Long productId = product.getId();

        flushAndClear();
        authenticate(operatorAuthentication);

        StockMovement firstMovement = stockMovementService.recordStockMovement(purchase(productId));
        Long firstMovementId = firstMovement.getId();

        flushAndClear();
        authenticate(administratorAuthentication);

        userService.deactivate(operatorId);

        flushAndClear();
        AppUser persistedOperator = appUserRepository.findWithRolesById(operatorId).orElseThrow();

        assertThat(persistedOperator.isActive()).isFalse();
        assertThat(persistedOperator.getRoles())
                .extracting(Role::getCode)
                .containsExactlyInAnyOrder(WAREHOUSE_OPERATOR);

        authenticate(operatorAuthentication);

        assertThat(stockMovementService.canCreateMovements()).isFalse();
        StockMovementRequest secondPurchase = purchase(productId);

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(secondPurchase))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Missing permission stock-movement:create");

        flushAndClear();
        Product persistedProduct = productRepository.findById(productId).orElseThrow();

        assertThat(persistedProduct.getQuantityOnHand()).isEqualTo(8);
        assertThat(stockMovementRepository.findAll())
                .extracting(StockMovement::getId)
                .containsExactly(firstMovementId);
    }

    private AppUser persistAdministrator() {
        AppUser administrator =
                new AppUser(ADMINISTRATOR_USERNAME, "system-administrator@example.test", null, null);
        administrator.addRole(requiredRole(SYSTEM_ADMINISTRATOR));

        return appUserRepository.saveAndFlush(administrator);
    }

    private Product persistProduct(int quantityOnHand) {
        Category category = categoryRepository.saveAndFlush(
                new Category("Power Tools", "Electric tools used in warehouse operations"));

        return productRepository.saveAndFlush(new Product(
                "TOOL-DRILL-001",
                "Cordless Drill",
                "18V cordless drill",
                new BigDecimal("25.00"),
                quantityOnHand,
                2,
                category,
                null,
                true));
    }

    private void replaceRole(Long userId, String roleCode) {
        AppUser user = appUserRepository.findWithRolesById(userId).orElseThrow();
        Role role = requiredRole(roleCode);
        UserRequest request = userRequest(user.getUsername(), user.getEmail(), role.getId());
        request.setActive(user.isActive());
        request.setVersion(user.getVersion());

        userService.update(userId, request);
    }

    private Role requiredRole(String code) {
        return roleRepository.findByCode(code).orElseThrow();
    }

    private static UserRequest userRequest(String username, String email, Long roleId) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setRoleIds(Set.of(roleId));

        return request;
    }

    private static StockMovementRequest purchase(Long productId) {
        StockMovementRequest request = new StockMovementRequest();
        request.setProductId(productId);
        request.setMovementType(MovementType.PURCHASE);
        request.setQuantityDelta(3);
        request.setReason("Supplier restock");

        return request;
    }

    private static Authentication authentication(String principal) {
        return new UsernamePasswordAuthenticationToken(principal, "N/A", List.of());
    }

    private static void authenticate(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
