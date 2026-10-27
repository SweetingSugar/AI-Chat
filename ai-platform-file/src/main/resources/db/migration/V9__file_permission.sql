insert ignore into sys_permission
    (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'File read', 'file:read', 3, 200, 1),
    (0, 'File delete', 'file:delete', 3, 201, 1);
