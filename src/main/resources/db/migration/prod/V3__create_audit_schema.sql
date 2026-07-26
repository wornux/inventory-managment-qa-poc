create sequence revision_seq as integer start with 1 increment by 50 cache 50;

create table revision (
    id integer primary key,
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
    oidc_issuer varchar(500),
    oidc_subject varchar(255),
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
    permissions text[],
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

create index idx_app_user_log_rev on app_user_log (rev, id);
create index idx_role_log_rev on role_log (rev, id);
create index idx_category_log_rev on category_log (rev, id);
create index idx_supplier_log_rev on supplier_log (rev, id);
create index idx_product_log_rev on product_log (rev, id);
create index idx_stock_movement_log_rev on stock_movement_log (rev, id);
create index idx_user_role_log_user on user_role_log (user_id, rev);
create index idx_user_role_log_role on user_role_log (role_id, rev);
