insert into role (code, name, description, system_role, permissions)
values
    (
        'SYSTEM_ADMINISTRATOR',
        'System Administrator',
        'Full access to catalog, inventory, user, and role administration.',
        true,
        array[
            'product:view', 'product:create', 'product:update', 'product:delete', 'product:export',
            'category:view', 'category:create', 'category:update', 'category:delete', 'category:export',
            'supplier:view', 'supplier:create', 'supplier:update', 'supplier:delete', 'supplier:export',
            'stock-movement:view', 'stock-movement:create', 'stock-movement:export',
            'user:view', 'user:create', 'user:update', 'user:delete', 'user:assign',
            'role:view', 'role:create', 'role:update', 'role:delete', 'role:assign'
        ]::text[]
    ),
    (
        'INVENTORY_MANAGER',
        'Inventory Manager',
        'Manages products, categories, suppliers, stock movements, and exports.',
        true,
        array[
            'product:view', 'product:create', 'product:update', 'product:delete', 'product:export',
            'category:view', 'category:create', 'category:update', 'category:delete', 'category:export',
            'supplier:view', 'supplier:create', 'supplier:update', 'supplier:delete', 'supplier:export',
            'stock-movement:view', 'stock-movement:create', 'stock-movement:export'
        ]::text[]
    ),
    (
        'WAREHOUSE_OPERATOR',
        'Warehouse Operator',
        'Reviews catalog data and records stock movements.',
        true,
        array[
            'product:view',
            'category:view',
            'supplier:view',
            'stock-movement:view', 'stock-movement:create'
        ]::text[]
    ),
    (
        'INVENTORY_VIEWER',
        'Inventory Viewer',
        'Read-only access to products, categories, suppliers, and stock movements.',
        true,
        array[
            'product:view',
            'category:view',
            'supplier:view',
            'stock-movement:view'
        ]::text[]
    );
