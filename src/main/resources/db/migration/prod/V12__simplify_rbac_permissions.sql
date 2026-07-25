alter table role
    add column permissions text[] not null default array[]::text[];

update role role_record
set permissions = coalesce((
    select array_agg(
        lower(replace(resource.code, '_', '-')) || ':' ||
        lower(case when action.code = 'READ' then 'VIEW' else action.code end)
        order by resource.code, action.code
    )
    from role_permission
    join permission on permission.id = role_permission.permission_id
    join resource on resource.id = permission.resource_id
    join action on action.id = permission.action_id
    where role_permission.role_id = role_record.id
      and (
          (resource.code in ('PRODUCT', 'CATEGORY', 'SUPPLIER')
              and action.code in ('CREATE', 'READ', 'UPDATE', 'DELETE', 'EXPORT'))
          or (resource.code = 'STOCK_MOVEMENT' and action.code in ('CREATE', 'READ', 'EXPORT'))
          or (resource.code in ('USER', 'ROLE') and action.code in ('CREATE', 'READ', 'UPDATE', 'DELETE', 'ASSIGN'))
      )
), array[]::text[]);

alter table role_log
    add column permissions text[];

drop table role_permission;
drop table permission;
drop table resource;
drop table action;

-- Keep the *_log tables so historical RBAC revisions remain reconstructable.
