insert into role (code, name, description, system_role, active)
values ('WAREHOUSE_OPERATOR', 'Warehouse Operator', 'Can record and review stock movements.', true, true)
on conflict (code) do nothing;
