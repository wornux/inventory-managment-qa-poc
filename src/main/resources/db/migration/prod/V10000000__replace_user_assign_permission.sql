update role
set permissions = array_remove(permissions, 'user:assign')
    || case
        when array_position(permissions, 'role:assign') is null then array['role:assign']::text[]
        else array[]::text[]
    end
where array_position(permissions, 'user:assign') is not null;
