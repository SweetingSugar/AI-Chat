insert ignore into sys_permission
    (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'File download', 'file:download', 3, 202, 1);
