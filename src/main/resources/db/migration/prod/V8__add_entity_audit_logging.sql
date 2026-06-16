alter table app_user
    add column created_by varchar(255) not null default 'SYSTEM',
    add column created_date timestamptz not null default now(),
    add column last_modified_by varchar(255) not null default 'SYSTEM',
    add column last_modified_date timestamptz not null default now();
update app_user set created_date = created_at, last_modified_date = updated_at;
alter table app_user
    drop column created_at,
    drop column updated_at;

alter table role
    add column created_by varchar(255) not null default 'SYSTEM',
    add column created_date timestamptz not null default now(),
    add column last_modified_by varchar(255) not null default 'SYSTEM',
    add column last_modified_date timestamptz not null default now();
update role set created_date = created_at, last_modified_date = updated_at;
alter table role
    drop column created_at,
    drop column updated_at;

alter table category
    add column created_by varchar(255) not null default 'SYSTEM',
    add column created_date timestamptz not null default now(),
    add column last_modified_by varchar(255) not null default 'SYSTEM',
    add column last_modified_date timestamptz not null default now();
update category set created_date = created_at, last_modified_date = updated_at;
alter table category
    drop column created_at,
    drop column updated_at;

alter table supplier
    add column created_by varchar(255) not null default 'SYSTEM',
    add column created_date timestamptz not null default now(),
    add column last_modified_by varchar(255) not null default 'SYSTEM',
    add column last_modified_date timestamptz not null default now();
update supplier set created_date = created_at, last_modified_date = updated_at;
alter table supplier
    drop column created_at,
    drop column updated_at;

alter table product
    add column created_by varchar(255) not null default 'SYSTEM',
    add column created_date timestamptz not null default now(),
    add column last_modified_by varchar(255) not null default 'SYSTEM',
    add column last_modified_date timestamptz not null default now();
update product set created_date = created_at, last_modified_date = updated_at;
alter table product
    drop column created_at,
    drop column updated_at;

alter table stock_movement
    add column created_by varchar(255) not null default 'SYSTEM',
    add column created_date timestamptz not null default now(),
    add column last_modified_by varchar(255) not null default 'SYSTEM',
    add column last_modified_date timestamptz not null default now();
update stock_movement set created_date = created_at, last_modified_date = created_at;
alter table stock_movement
    drop column created_at;

alter table resource
    add column created_by varchar(255) not null default 'SYSTEM',
    add column created_date timestamptz not null default now(),
    add column last_modified_by varchar(255) not null default 'SYSTEM',
    add column last_modified_date timestamptz not null default now();
update resource set created_date = created_at, last_modified_date = updated_at;
alter table resource
    drop column created_at,
    drop column updated_at;

alter table action
    add column created_by varchar(255) not null default 'SYSTEM',
    add column created_date timestamptz not null default now(),
    add column last_modified_by varchar(255) not null default 'SYSTEM',
    add column last_modified_date timestamptz not null default now();
update action set created_date = created_at, last_modified_date = updated_at;
alter table action
    drop column created_at,
    drop column updated_at;

alter table permission
    add column created_by varchar(255) not null default 'SYSTEM',
    add column created_date timestamptz not null default now(),
    add column last_modified_by varchar(255) not null default 'SYSTEM',
    add column last_modified_date timestamptz not null default now();
update permission set created_date = created_at, last_modified_date = updated_at;
alter table permission
    drop column created_at,
    drop column updated_at;

create sequence revision_seq start with 1 increment by 50;

create table revision (
    id integer not null primary key,
    timestamp bigint not null,
    modifier_user varchar(255),
    ip_address varchar(255)
);

create table app_user_log (
    id bigint not null,
    rev integer not null references revision(id),
    revtype smallint,
    username varchar(80),
    email varchar(255),
    password_hash varchar(255),
    active boolean,
    created_by varchar(255),
    created_date timestamptz,
    last_modified_by varchar(255),
    last_modified_date timestamptz,
    primary key (id, rev)
);

create table role_log (
    id bigint not null,
    rev integer not null references revision(id),
    revtype smallint,
    code varchar(80),
    name varchar(120),
    description varchar(500),
    system_role boolean,
    active boolean,
    created_by varchar(255),
    created_date timestamptz,
    last_modified_by varchar(255),
    last_modified_date timestamptz,
    primary key (id, rev)
);

create table resource_log (
    id bigint not null,
    rev integer not null references revision(id),
    revtype smallint,
    code varchar(80),
    name varchar(120),
    description text,
    active boolean,
    created_by varchar(255),
    created_date timestamptz,
    last_modified_by varchar(255),
    last_modified_date timestamptz,
    primary key (id, rev)
);

create table action_log (
    id bigint not null,
    rev integer not null references revision(id),
    revtype smallint,
    code varchar(80),
    name varchar(120),
    description text,
    active boolean,
    created_by varchar(255),
    created_date timestamptz,
    last_modified_by varchar(255),
    last_modified_date timestamptz,
    primary key (id, rev)
);

create table permission_log (
    id bigint not null,
    rev integer not null references revision(id),
    revtype smallint,
    resource_id bigint,
    action_id bigint,
    description text,
    active boolean,
    created_by varchar(255),
    created_date timestamptz,
    last_modified_by varchar(255),
    last_modified_date timestamptz,
    primary key (id, rev)
);

create table category_log (
    id bigint not null,
    rev integer not null references revision(id),
    revtype smallint,
    name varchar(120),
    description varchar(500),
    active boolean,
    created_by varchar(255),
    created_date timestamptz,
    last_modified_by varchar(255),
    last_modified_date timestamptz,
    primary key (id, rev)
);

create table supplier_log (
    id bigint not null,
    rev integer not null references revision(id),
    revtype smallint,
    name varchar(160),
    contact_name varchar(160),
    email varchar(255),
    phone varchar(80),
    active boolean,
    created_by varchar(255),
    created_date timestamptz,
    last_modified_by varchar(255),
    last_modified_date timestamptz,
    primary key (id, rev)
);

create table product_log (
    id bigint not null,
    rev integer not null references revision(id),
    revtype smallint,
    sku varchar(80),
    name varchar(160),
    description varchar(1000),
    unit_price numeric(12, 2),
    quantity_on_hand integer,
    minimum_stock integer,
    active boolean,
    category_id bigint,
    supplier_id bigint,
    created_by varchar(255),
    created_date timestamptz,
    last_modified_by varchar(255),
    last_modified_date timestamptz,
    primary key (id, rev)
);

create table stock_movement_log (
    id bigint not null,
    rev integer not null references revision(id),
    revtype smallint,
    product_id bigint,
    user_id bigint,
    movement_type varchar(40),
    quantity_delta integer,
    reason varchar(500),
    created_by varchar(255),
    created_date timestamptz,
    last_modified_by varchar(255),
    last_modified_date timestamptz,
    primary key (id, rev)
);

create table user_role_log (
    rev integer not null references revision(id),
    user_id bigint not null,
    role_id bigint not null,
    revtype smallint,
    primary key (rev, user_id, role_id)
);

create table role_permission_log (
    rev integer not null references revision(id),
    role_id bigint not null,
    permission_id bigint not null,
    revtype smallint,
    primary key (rev, role_id, permission_id)
);

create index idx_app_user_log_rev on app_user_log(rev);
create index idx_role_log_rev on role_log(rev);
create index idx_resource_log_rev on resource_log(rev);
create index idx_action_log_rev on action_log(rev);
create index idx_permission_log_rev on permission_log(rev);
create index idx_category_log_rev on category_log(rev);
create index idx_supplier_log_rev on supplier_log(rev);
create index idx_product_log_rev on product_log(rev);
create index idx_stock_movement_log_rev on stock_movement_log(rev);
create index idx_user_role_log_rev on user_role_log(rev);
create index idx_role_permission_log_rev on role_permission_log(rev);
