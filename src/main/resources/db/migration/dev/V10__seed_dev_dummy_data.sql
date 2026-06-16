insert into app_user (username, email, password_hash, active, created_by, created_date, last_modified_by, last_modified_date)
values
    ('dev.admin', 'dev.admin@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true, 'SYSTEM', now(), 'SYSTEM', now()),
    ('dev.manager', 'dev.manager@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true, 'SYSTEM', now(), 'SYSTEM', now()),
    ('dev.operator', 'dev.operator@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true, 'SYSTEM', now(), 'SYSTEM', now()),
    ('dev.viewer', 'dev.viewer@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', true, 'SYSTEM', now(), 'SYSTEM', now())
on conflict (username) do nothing;

insert into user_role (user_id, role_id)
select app_user.id, role.id
from app_user
join role on role.code = 'SYSTEM_ADMINISTRATOR'
where app_user.username = 'dev.admin'
on conflict do nothing;

insert into user_role (user_id, role_id)
select app_user.id, role.id
from app_user
join role on role.code = 'INVENTORY_MANAGER'
where app_user.username = 'dev.manager'
on conflict do nothing;

insert into user_role (user_id, role_id)
select app_user.id, role.id
from app_user
join role on role.code = 'WAREHOUSE_OPERATOR'
where app_user.username = 'dev.operator'
on conflict do nothing;

insert into user_role (user_id, role_id)
select app_user.id, role.id
from app_user
join role on role.code = 'INVENTORY_VIEWER'
where app_user.username = 'dev.viewer'
on conflict do nothing;

insert into category (name, description, active, created_by, created_date, last_modified_by, last_modified_date)
values
    ('Dev Electronics', 'Dummy electronics category for local development.', true, 'SYSTEM', now(), 'SYSTEM', now()),
    ('Dev Office Supplies', 'Dummy office supplies category for local development.', true, 'SYSTEM', now(), 'SYSTEM', now())
on conflict do nothing;

insert into supplier (name, contact_name, email, phone, active, created_by, created_date, last_modified_by, last_modified_date)
values
    ('Dev Global Supply', 'Dev Contact', 'dev-global-supply@example.com', '555-1000', true, 'SYSTEM', now(), 'SYSTEM', now()),
    ('Dev Warehouse Direct', 'Warehouse Contact', 'warehouse-direct@example.com', '555-2000', true, 'SYSTEM', now(), 'SYSTEM', now())
on conflict do nothing;

insert into product (
    sku,
    name,
    description,
    unit_price,
    quantity_on_hand,
    minimum_stock,
    active,
    category_id,
    supplier_id,
    created_by,
    created_date,
    last_modified_by,
    last_modified_date
)
select
    product_data.sku,
    product_data.name,
    product_data.description,
    product_data.unit_price,
    product_data.quantity_on_hand,
    product_data.minimum_stock,
    true,
    category.id,
    supplier.id,
    'SYSTEM',
    now(),
    'SYSTEM',
    now()
from (
    values
        ('DEV-LAPTOP-001', 'Dev Laptop', 'Dummy laptop for local development.', 1299.99, 12, 3, 'Dev Electronics', 'Dev Global Supply'),
        ('DEV-MOUSE-001', 'Dev Wireless Mouse', 'Dummy mouse for local development.', 29.99, 40, 10, 'Dev Electronics', 'Dev Global Supply'),
        ('DEV-PAPER-001', 'Dev Printer Paper', 'Dummy printer paper for local development.', 6.50, 100, 25, 'Dev Office Supplies', 'Dev Warehouse Direct')
) as product_data(sku, name, description, unit_price, quantity_on_hand, minimum_stock, category_name, supplier_name)
join category on category.name = product_data.category_name
join supplier on supplier.name = product_data.supplier_name
on conflict (sku) do nothing;

insert into stock_movement (product_id, user_id, movement_type, quantity_delta, reason, created_by, created_date, last_modified_by, last_modified_date)
select product.id, app_user.id, 'INITIAL_STOCK', product.quantity_on_hand, 'Dummy opening balance for local development.', 'SYSTEM', now(), 'SYSTEM', now()
from product
join app_user on app_user.username = 'dev.operator'
where product.sku in ('DEV-LAPTOP-001', 'DEV-MOUSE-001', 'DEV-PAPER-001')
  and not exists (
      select 1
      from stock_movement existing
      where existing.product_id = product.id
        and existing.movement_type = 'INITIAL_STOCK'
        and existing.reason = 'Dummy opening balance for local development.'
  );
