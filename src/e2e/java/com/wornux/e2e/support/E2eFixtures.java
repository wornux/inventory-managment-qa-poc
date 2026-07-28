package com.wornux.e2e.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class E2eFixtures {

    public static final String CATEGORY = "E2E Equipment";
    public static final String SUPPLIER = "E2E Supplies";

    private final JdbcTemplate jdbc;

    E2eFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void resetScenarioData() {
        jdbc.update("""
                delete from stock_movement
                where product_id in (select id from product where sku like 'E2E-%')
                """);
        jdbc.update("delete from product where sku like 'E2E-%'");
        jdbc.update("""
                insert into category (name, description)
                select ?, 'Stable category for browser scenarios.'
                where not exists (select 1 from category where name = ?)
                """, CATEGORY, CATEGORY);
        jdbc.update("""
                insert into supplier (name, contact_name, email, active)
                select ?, 'E2E Runner', 'e2e@example.test', true
                where not exists (select 1 from supplier where name = ?)
                """, SUPPLIER, SUPPLIER);
    }

    void giveAdministratorRole(String roleCode) {
        jdbc.update("""
                delete from user_role
                where user_id = (select id from app_user where username = ?)
                """, E2eEnvironment.ADMIN_USERNAME);
        jdbc.update("""
                insert into user_role (user_id, role_id)
                select app_user.id, role.id
                from app_user
                cross join role
                where app_user.username = ? and role.code = ?
                """, E2eEnvironment.ADMIN_USERNAME, roleCode);
    }

    void createProduct(String sku, String name, int quantity, int minimumStock) {
        jdbc.update("""
                insert into product (
                    sku, name, description, unit_price, quantity_on_hand,
                    minimum_stock, active, category_id, supplier_id
                )
                select ?, ?, 'Catalog fixture', 10.00, ?, ?, true, category.id, supplier.id
                from category
                cross join supplier
                where category.name = ? and supplier.name = ?
                """, sku, name, quantity, minimumStock, CATEGORY, SUPPLIER);
    }
}
