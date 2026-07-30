package com.wornux.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.audit.AuditConfig;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/prod"
        })
@Import(AuditConfig.class)
class RolePersistenceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");

    @Autowired
    RoleRepository roles;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationSeedsHierarchyAndAllowsOnlyOnePriorityOneHundredRole() {
        Map<String, Integer> priorities =
                roles.findAll().stream().collect(Collectors.toMap(Role::getCode, Role::getPriority));

        assertThat(priorities)
                .containsEntry("SYSTEM_ADMINISTRATOR", 100)
                .containsEntry("INVENTORY_MANAGER", 60)
                .containsEntry("WAREHOUSE_OPERATOR", 40)
                .containsEntry("INVENTORY_VIEWER", 20);
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.columns where table_name = 'role_log' and column_name = 'priority'",
                        Integer.class))
                .isEqualTo(1);

        Role duplicate = new Role("SECOND_TOP_ROLE", "Second top role", null);
        duplicate.update(duplicate.getName(), duplicate.getDescription(), 100, true, Set.of());

        assertThatThrownBy(() -> roles.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
