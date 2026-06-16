alter table supplier
    add column version bigint not null default 0;
