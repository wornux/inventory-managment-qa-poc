package com.wornux.usecases.uc010_entity_audit_logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.catalog.Category;
import com.wornux.catalog.CategoryRepository;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductRepository;
import com.wornux.catalog.Supplier;
import com.wornux.catalog.SupplierRepository;
import com.wornux.usecases.PostgresContainerConfig;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Permission;
import com.wornux.user.PermissionAction;
import com.wornux.user.PermissionActionRepository;
import com.wornux.user.PermissionRepository;
import com.wornux.user.ProtectedResource;
import com.wornux.user.ProtectedResourceRepository;
import com.wornux.user.Role;
import com.wornux.user.RoleRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@Import(PostgresContainerConfig.class)
class UC010EntityAuditLoggingTest {

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ProtectedResourceRepository resourceRepository;
    private final PermissionActionRepository actionRepository;

    @Autowired
    UC010EntityAuditLoggingTest(
            JdbcTemplate jdbcTemplate,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            ProtectedResourceRepository resourceRepository,
            PermissionActionRepository actionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.resourceRepository = resourceRepository;
        this.actionRepository = actionRepository;
    }

    @Test
    void mainFlow_applicationStartsWithAuditSchema() {
        assertThat(tableExists("revision")).isTrue();
        assertThat(tableExists("product_log")).isTrue();
        assertThat(tableExists("app_user_log")).isTrue();
        assertThat(tableExists("role_log")).isTrue();
        assertThat(tableExists("permission_log")).isTrue();
        assertThat(tableExists("user_role_log")).isTrue();
        assertThat(tableExists("role_permission_log")).isTrue();
    }

    @Test
    @WithMockUser(username = "audit-admin", roles = "SYSTEM_ADMINISTRATOR")
    void mainFlow_authenticatedUserWritesEntityLogsAndRevisionMetadata() {
        Product product = productRepository.saveAndFlush(product(uniqueSku()));

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        updated.applyQuantityDelta(3);
        productRepository.saveAndFlush(updated);

        assertThat(countRows("product_log", "id", product.getId())).isGreaterThanOrEqualTo(2);
        assertThat(modifierFor("product_log", product.getId())).isEqualTo("audit-admin");
        assertThat(jdbcTemplate.queryForObject(
                "select created_by from product where id = ?",
                String.class,
                product.getId())).isEqualTo("audit-admin");
        assertThat(jdbcTemplate.queryForObject(
                "select last_modified_by from product where id = ?",
                String.class,
                product.getId())).isEqualTo("audit-admin");
    }

    @Test
    @WithMockUser(username = "audit-admin", roles = "SYSTEM_ADMINISTRATOR")
    void mainFlow_userRolePermissionAndRelationshipTablesAreAudited() {
        AppUser user = appUserRepository.saveAndFlush(new AppUser(
                "audit-" + UUID.randomUUID().toString().substring(0, 8),
                "audit-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "hash"));
        Permission permission = permissionRepository.saveAndFlush(permission());
        Role role = new Role("AUDIT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "Audit Role", null, false);
        role.update(role.getName(), role.getDescription(), true, new LinkedHashSet<>(Set.of(permission)));
        Role savedRole = roleRepository.saveAndFlush(role);

        user.addRole(savedRole);
        appUserRepository.saveAndFlush(user);

        assertThat(countRows("app_user_log", "id", user.getId())).isGreaterThanOrEqualTo(2);
        assertThat(countRows("permission_log", "id", permission.getId())).isEqualTo(1);
        assertThat(countRows("role_log", "id", savedRole.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from role_permission_log where role_id = ? and permission_id = ?",
                Long.class,
                savedRole.getId(),
                permission.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user_role_log where user_id = ? and role_id = ?",
                Long.class,
                user.getId(),
                savedRole.getId())).isEqualTo(1);
    }

    @Test
    @WithAnonymousUser
    void af1_anonymousChangesUseAnonymousRevisionMetadata() {
        Category category = categoryRepository.saveAndFlush(new Category(
                "Audit Anonymous " + UUID.randomUUID().toString().substring(0, 8),
                null));

        assertThat(modifierFor("category_log", category.getId())).isEqualTo("ANONYMOUS");
        assertThat(jdbcTemplate.queryForObject(
                "select created_by from category where id = ?",
                String.class,
                category.getId())).isEqualTo("ANONYMOUS");
    }

    @Test
    @WithMockUser(username = "audit-admin", roles = "SYSTEM_ADMINISTRATOR")
    void af2_deleteStoresDeletedEntityData() {
        Product product = productRepository.saveAndFlush(product(uniqueSku()));
        String sku = product.getSku();

        productRepository.deleteById(product.getId());
        productRepository.flush();

        assertThat(jdbcTemplate.queryForObject(
                "select sku from product_log where id = ? and revtype = 2 order by rev desc limit 1",
                String.class,
                product.getId())).isEqualTo(sku);
    }

    @Test
    void br01_auditTablesUseLogSuffixOnly() {
        List<String> auditTables = jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                    and lower(table_name) like '%\\_aud' escape '\\'
                """, String.class);

        assertThat(auditTables).isEmpty();
        assertThat(tableExists("product_log")).isTrue();
    }

    @Test
    void br03_revisionMetadataIncludesUserAndIpAddress() {
        assertThat(hasColumn("revision", "modifier_user")).isTrue();
        assertThat(hasColumn("revision", "ip_address")).isTrue();
    }

    @Test
    void br05_liveEntitiesUseSpringAuditingColumns() {
        assertThat(hasColumn("product", "created_by")).isTrue();
        assertThat(hasColumn("product", "created_date")).isTrue();
        assertThat(hasColumn("product", "last_modified_by")).isTrue();
        assertThat(hasColumn("product", "last_modified_date")).isTrue();
        assertThat(hasColumn("product", "created_at")).isFalse();
        assertThat(hasColumn("product", "updated_at")).isFalse();
    }

    private Product product(String sku) {
        Category category = categoryRepository.findByNameIgnoreCase("General").orElseThrow();
        Supplier supplier = supplierRepository.findByActiveTrueOrderByNameAsc().getFirst();
        return new Product(sku, "Audit Product " + sku, null, BigDecimal.TEN, 10, 1, category, supplier, true);
    }

    private Permission permission() {
        MissingPair pair = missingPair();
        return new Permission(pair.resource(), pair.action(), "Audit permission", true);
    }

    private MissingPair missingPair() {
        List<ProtectedResource> resources = resourceRepository.findByActiveTrueOrderByCodeAsc();
        List<PermissionAction> actions = actionRepository.findByActiveTrueOrderByCodeAsc();
        return resources.stream()
                .flatMap(resource -> actions.stream().map(action -> new MissingPair(resource, action)))
                .filter(pair -> !permissionRepository.existsByResourceIdAndActionId(pair.resource().getId(), pair.action().getId()))
                .findFirst()
                .orElseThrow();
    }

    private String modifierFor(String logTable, Long entityId) {
        return jdbcTemplate.queryForObject(
                "select r.modifier_user from revision r join " + logTable
                        + " log on log.rev = r.id where log.id = ? order by r.id desc limit 1",
                String.class,
                entityId);
    }

    private long countRows(String table, String column, Long value) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?",
                Long.class,
                value);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                    and table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean hasColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                    and table_name = ?
                    and column_name = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private String uniqueSku() {
        return "AUD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private record MissingPair(ProtectedResource resource, PermissionAction action) {
    }
}
