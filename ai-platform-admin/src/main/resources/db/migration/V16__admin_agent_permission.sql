insert ignore into sys_permission
    (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'Agent read', 'agent:read', 3, 400, 1);
