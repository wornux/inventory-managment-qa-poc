alter table app_user
    add column oidc_issuer varchar(500),
    add column oidc_subject varchar(255);

create unique index uq_app_user_oidc_identity
    on app_user (oidc_issuer, oidc_subject)
    where oidc_issuer is not null and oidc_subject is not null;

alter table app_user
    drop column password_hash;

alter table app_user_log
    add column oidc_issuer varchar(500),
    add column oidc_subject varchar(255),
    drop column password_hash;
