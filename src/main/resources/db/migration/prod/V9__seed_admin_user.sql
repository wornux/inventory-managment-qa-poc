insert into app_user (
    id,
    username,
    email,
    password_hash,
    active,
    version,
    created_by,
    created_date,
    last_modified_by,
    last_modified_date
)
values (
    1,
    'admin@wornux.com',
    'admin@wornux.com',
    '$2a$10$JzbrlHMobTaWbid6m.yjKeAebVHHN14J0/qFtsfVphCkp4qGLYsQG',
    true,
    0,
    'SYSTEM',
    now(),
    'SYSTEM',
    now()
)
on conflict (id) do update
set username = excluded.username,
    email = excluded.email,
    password_hash = excluded.password_hash,
    active = true,
    last_modified_by = 'SYSTEM',
    last_modified_date = now();

select setval(
    pg_get_serial_sequence('app_user', 'id'),
    greatest((select coalesce(max(id), 1) from app_user), 1)
);

insert into user_role (user_id, role_id)
select 1, role.id
from role
where role.active = true
on conflict do nothing;
