update role
set permissions = permissions || array['report:view']::text[]
where system_role
    and array_position(permissions, 'report:view') is null;
