alter table app_user
    add column version bigint not null default 0;
