alter table role add column priority integer;

update role
set priority = case code
    when 'SYSTEM_ADMINISTRATOR' then 100
    when 'INVENTORY_MANAGER' then 60
    when 'WAREHOUSE_OPERATOR' then 40
    when 'INVENTORY_VIEWER' then 20
    else 10
end;

alter table role alter column priority set default 10;
alter table role alter column priority set not null;
alter table role add constraint ck_role_priority_range check (priority between 0 and 100);
create unique index uq_role_single_priority_100 on role (priority) where priority = 100;
alter table role drop column system_role;

alter table role_log add column priority integer;
