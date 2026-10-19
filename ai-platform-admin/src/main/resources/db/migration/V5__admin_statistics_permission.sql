-- 管理首页和统计权限。
insert ignore into sys_permission
    (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'Admin dashboard read', 'admin:dashboard:read', 3, 100, 1),
    (0, 'Admin user statistics read', 'admin:statistics:user:read', 3, 101, 1),
    (0, 'Admin chat statistics read', 'admin:statistics:chat:read', 3, 102, 1),
    (0, 'Admin token statistics read', 'admin:statistics:token:read', 3, 103, 1);

-- 统计查询索引。明细表已存在时直接补充索引。
create index idx_sys_user_deleted_create_time
    on sys_user (deleted, create_time);

create index idx_chat_session_deleted_create_time
    on chat_session (deleted, create_time);

create index idx_chat_message_deleted_role_time_user
    on chat_message (deleted, role, create_time, user_id);

create index idx_ai_call_log_start_status_usage
    on ai_call_log (start_time, status, usage_source);
